package np.bill.ui.customer

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import np.bill.data.net.ApiResult
import np.bill.data.repo.ProfileRepository
import np.bill.print.ReceiptRenderer

@Immutable
data class MyCardState(
  val loading: Boolean = true,
  val saving: Boolean = false,
  val name: String = "",
  val phone: String? = null,
  val pan: String = "",
  val address: String = "",
  val qr: Bitmap? = null,
  val error: String? = null,
)

/** A card is refreshed a little before it expires, so it is never stale on screen. */
private const val REFRESH_MARGIN_MS = 20_000L

@HiltViewModel
class MyCardViewModel @Inject constructor(
  private val profiles: ProfileRepository,
) : ViewModel() {

  private val _state = MutableStateFlow(MyCardState())
  val state = _state.asStateFlow()

  private val renderer = ReceiptRenderer()

  private var refresher: kotlinx.coroutines.Job? = null

  /**
   * Keeps a live card on screen.
   *
   * The code expires in minutes, so this redraws before it does. Without that, a shopper
   * who opened the screen, queued, and then held up their phone would show a code the
   * shop's scanner rejects.
   */
  fun start() {
    if (refresher?.isActive == true) return
    refresher = viewModelScope.launch {
      while (true) {
        val waitFor = refresh()
        kotlinx.coroutines.delay(waitFor)
      }
    }
  }

  fun stop() {
    refresher?.cancel()
    refresher = null
  }

  private suspend fun refresh(): Long {
    when (val result = profiles.myCard()) {
      is ApiResult.Ok -> {
        val card = result.value
        val qr = withContext(Dispatchers.Default) {
          renderer.qr(ProfileRepository.cardUrl(card.code), 512)
        }
        _state.value = MyCardState(
          loading = false,
          name = card.profile.name,
          phone = card.profile.phone,
          pan = card.profile.pan.orEmpty(),
          address = card.profile.address.orEmpty(),
          qr = qr,
        )

        val expiry = np.bill.util.Iso8601.parse(card.expiresAt) ?: return 60_000
        return (expiry - System.currentTimeMillis() - REFRESH_MARGIN_MS).coerceIn(5_000, 300_000)
      }
      ApiResult.Offline -> {
        _state.update { it.copy(loading = false, error = null) }
        return 30_000
      }
      is ApiResult.Failed -> {
        _state.update { it.copy(loading = false, error = result.message) }
        return 60_000
      }
    }
  }

  fun onName(value: String) = _state.update { it.copy(name = value) }
  fun onPan(value: String) = _state.update { it.copy(pan = value.filter(Char::isDigit).take(9)) }
  fun onAddress(value: String) = _state.update { it.copy(address = value) }

  fun save() {
    val current = _state.value
    viewModelScope.launch {
      _state.update { it.copy(saving = true, error = null) }
      when (
        val result = profiles.saveProfile(
          name = current.name.trim(),
          phone = current.phone,
          pan = current.pan.ifBlank { null },
          address = current.address.trim().ifBlank { null },
        )
      ) {
        is ApiResult.Ok -> _state.update { it.copy(saving = false) }
        ApiResult.Offline -> _state.update { it.copy(saving = false, error = null) }
        is ApiResult.Failed -> _state.update { it.copy(saving = false, error = result.message) }
      }
    }
  }
}
