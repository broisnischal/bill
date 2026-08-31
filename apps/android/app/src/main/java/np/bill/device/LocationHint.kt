package np.bill.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import np.bill.core.geo.Nepal

/**
 * Where the shop is, offered as a suggestion.
 *
 * Registering a business means typing an address, a ward, a municipality, a district and
 * a province, which is five fields of something the phone already knows. This fills them
 * in; the shopkeeper corrects whatever is wrong. It is never required — the permission is
 * asked for at the point it helps and declining costs nothing but typing.
 */
@Singleton
class LocationHint @Inject constructor(@ApplicationContext private val context: Context) {

  data class Suggestion(
    val address: String?,
    val municipality: String?,
    val district: String?,
    val province: String?,
  ) {
    val isEmpty: Boolean
      get() = address == null && municipality == null && district == null && province == null
  }

  fun hasPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED

  suspend fun suggest(): Suggestion? {
    if (!hasPermission()) return null
    val location = currentLocation() ?: return null
    return reverseGeocode(location)
  }

  /**
   * The last known fix rather than a fresh one: registering a shop does not need metre
   * accuracy, and asking for a new fix costs seconds and battery for the same answer.
   */
  @SuppressLint("MissingPermission")
  private suspend fun currentLocation(): Location? = withContext(Dispatchers.IO) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      ?: return@withContext null

    runCatching {
      manager.getProviders(true)
        .mapNotNull { manager.getLastKnownLocation(it) }
        .maxByOrNull { it.time }
    }.getOrNull()
  }

  private suspend fun reverseGeocode(location: Location): Suggestion? {
    val geocoder = runCatching { Geocoder(context) }.getOrNull() ?: return null

    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // The callback form is the only one that is not deprecated, and it can hang, so it
      // gets a deadline rather than blocking the form behind a network geocoder.
      withTimeoutOrNull(6_000) {
        suspendCancellableCoroutine { continuation ->
          geocoder.getFromLocation(location.latitude, location.longitude, 1) { result ->
            if (continuation.isActive) continuation.resume(result)
          }
        }
      }
    } else {
      withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1) }
          .getOrNull()
      }
    }

    val first = addresses?.firstOrNull() ?: return null

    // Nominatim-style results put the local level in locality or subAdminArea and the
    // district in subAdminArea or adminArea, and which one varies by provider. Take the
    // district from whichever field actually names one we recognise.
    val district = Nepal.matchDistrict(first.subAdminArea)
      ?: Nepal.matchDistrict(first.adminArea)
      ?: Nepal.matchDistrict(first.locality)

    val municipality = first.locality?.takeIf { !it.equals(district, true) }
      ?: first.subAdminArea?.takeIf { !it.equals(district, true) }

    val address = listOfNotNull(
      first.subLocality,
      first.thoroughfare,
    ).distinct().joinToString(", ").ifBlank { first.featureName }

    val suggestion = Suggestion(
      address = address?.takeIf { it.isNotBlank() && it.none(Char::isDigit) },
      municipality = municipality,
      district = district,
      province = Nepal.provinceOf(district),
    )
    return suggestion.takeUnless(Suggestion::isEmpty)
  }
}
