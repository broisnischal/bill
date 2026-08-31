package np.bill.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import np.bill.data.net.ApiResult
import np.bill.data.net.BillApi
import np.bill.data.net.SaveProfileRequest
import np.bill.data.net.ScannedProfileDto
import np.bill.data.net.ShopperProfileDto
import np.bill.data.net.apiCall

/**
 * A shopper's card, and resolving one held up at a counter.
 *
 * Both directions of the same idea: a customer shows a QR, a shop scans it, and the bill
 * gets a name and a number without either of them typing.
 */
@Singleton
class ProfileRepository @Inject constructor(private val api: BillApi) {

  data class Card(val profile: ShopperProfileDto, val code: String, val expiresAt: String)

  /**
   * The shopper's card, with the code it should be showing right now.
   *
   * The code rotates every few minutes, so this is fetched rather than derived: the
   * signing key stays on the server, which is the whole reason a photographed card stops
   * working.
   */
  suspend fun myCard(): ApiResult<Card> =
    when (val result = apiCall { api.myProfile() }) {
      is ApiResult.Ok -> {
        val card = result.value.card
        if (card == null) {
          ApiResult.Failed("no_card", "Could not get your card. Try again.", 200)
        } else {
          ApiResult.Ok(Card(result.value.profile, card.code, card.expiresAt))
        }
      }
      ApiResult.Offline -> ApiResult.Offline
      is ApiResult.Failed -> result
    }

  suspend fun saveProfile(
    name: String,
    phone: String?,
    pan: String?,
    address: String?,
  ): ApiResult<ShopperProfileDto> =
    when (
      val result = apiCall { api.saveProfile(SaveProfileRequest(name, phone, pan, address)) }
    ) {
      is ApiResult.Ok -> ApiResult.Ok(result.value.profile)
      ApiResult.Offline -> ApiResult.Offline
      is ApiResult.Failed -> result
    }

  /** Looks up the shopper behind a scanned card. */
  suspend fun resolve(scanned: String): ApiResult<ScannedProfileDto> {
    val token = tokenFrom(scanned)
      ?: return ApiResult.Failed("bad_code", "That is not a customer card", 0)

    return when (val result = apiCall { api.scannedProfile(token) }) {
      is ApiResult.Ok -> ApiResult.Ok(result.value.profile)
      ApiResult.Offline -> ApiResult.Offline
      is ApiResult.Failed -> result
    }
  }

  /**
   * The code out of a scanned card.
   *
   * A card carries `token.window.signature`, which is what makes it expire; a bill's QR
   * carries a bare token under /b/. The two are told apart by shape, so scanning the
   * wrong thing fails cleanly instead of half-working.
   */
  fun tokenFrom(scanned: String): String? {
    val trimmed = scanned.trim()
    if (trimmed.contains("/b/")) return null
    val candidate = trimmed.substringAfterLast('/').substringBefore('?')
    return candidate.takeIf(CARD_CODE::matches)
  }

  companion object {
    private val CARD_CODE = Regex("^[a-f0-9]{32}\\.\\d+\\.[A-Za-z0-9_-]{20,}$")

    const val CARD_BASE = "https://bill.np/c/"

    fun cardUrl(code: String) = CARD_BASE + code
  }
}
