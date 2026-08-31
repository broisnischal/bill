package np.bill.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import np.bill.data.net.ApiResult
import np.bill.data.net.BillApi
import np.bill.data.net.RegisterStoreRequest
import np.bill.data.net.SendOtpRequest
import np.bill.data.net.VerifyOtpRequest
import np.bill.data.net.apiCall
import np.bill.data.net.parseError
import np.bill.data.prefs.SessionStore

/**
 * Signing in with a Nepali mobile number.
 *
 * There is no password and no email: a shopkeeper types their number, gets a code by
 * SMS, and is in. Verifying a number nobody has used before creates the account, so
 * signing up and signing in are the same two screens.
 */
@Singleton
class AuthRepository @Inject constructor(
  private val api: BillApi,
  private val session: SessionStore,
  private val storeData: np.bill.data.db.StoreDataDao,
) {

  /** Normalises what was typed to E.164, or null when it is not a Nepali mobile. */
  fun normalise(input: String): String? {
    val digits = input.filter(Char::isDigit)
    val national = (if (digits.startsWith("977")) digits.drop(3) else digits).trimStart('0')
    return if (national.length == 10 && national.startsWith("9")) "+977$national" else null
  }

  suspend fun sendOtp(phoneNumber: String): ApiResult<Unit> =
    when (val response = runCatching { api.sendOtp(SendOtpRequest(phoneNumber)) }.getOrNull()) {
      null -> ApiResult.Offline
      else -> if (response.isSuccessful) ApiResult.Ok(Unit) else parseError(response)
    }

  suspend fun verifyOtp(phoneNumber: String, code: String): ApiResult<Unit> {
    val response = runCatching { api.verifyOtp(VerifyOtpRequest(phoneNumber, code)) }.getOrNull()
      ?: return ApiResult.Offline

    if (!response.isSuccessful) return parseError(response)

    // Better Auth returns the session token in a header for native clients, and repeats
    // it in the body; either is fine, the header is the documented one.
    val token = response.headers()["set-auth-token"]?.substringBefore(".")
      ?: response.body()?.token
      ?: return ApiResult.Failed("no_token", "Sign-in did not complete. Try again.", 200)

    val user = response.body()?.user
      ?: return ApiResult.Failed("no_user", "Sign-in did not complete. Try again.", 200)

    session.signIn(token, user)

    // Pick up the shop in the same breath as the session. Nothing else asks for it until
    // the app is next launched, so without this a shopkeeper who already has a store
    // signs in and is sent straight to the registration form, which then refuses them
    // because the store they are being asked to create is already there.
    runCatching { bootstrap() }

    return ApiResult.Ok(Unit)
  }

  /**
   * The code the server held instead of sending it, so signing in needs no SMS account.
   *
   * Gated on its own flag rather than on the debug build, because the builds being handed
   * round for people to try are release builds against the real server and they still have
   * no gateway behind them. The route answers only while that server keeps OTP_DEBUG on,
   * so turning the server flag off disables this everywhere at once.
   */
  suspend fun devOtp(phoneNumber: String): String? {
    if (!np.bill.BuildConfig.OTP_AUTOFILL) return null
    return runCatching { api.devOtp(phoneNumber).body()?.code }.getOrNull()
  }

  suspend fun bootstrap(): ApiResult<Boolean> =
    when (val response = apiCall { api.bootstrap() }) {
      ApiResult.Offline -> ApiResult.Offline
      is ApiResult.Failed -> response
      is ApiResult.Ok -> {
        response.value.store?.let { adoptStore(it) }
        ApiResult.Ok(response.value.store != null)
      }
    }

  suspend fun registerStore(request: RegisterStoreRequest): ApiResult<Unit> =
    when (val response = apiCall { api.registerStore(request) }) {
      ApiResult.Offline -> ApiResult.Offline
      is ApiResult.Failed -> response
      is ApiResult.Ok -> {
        adoptStore(response.value.store)
        ApiResult.Ok(Unit)
      }
    }

  /**
   * Takes on a store, clearing the device first if it is a different one.
   *
   * A phone that billed for one shop and is now billing for another must not keep the
   * first shop's number leases: those are blocks from a series belonging to another PAN,
   * and a bill printed from one would carry a number that store never issued.
   */
  private suspend fun adoptStore(store: np.bill.data.net.StoreDto) {
    // Against the remembered id, not the current session's: signing out clears the
    // session, and comparing against something sign-out just erased never fires.
    val previous = session.lastStoreId()
    if (previous != null && previous != store.id) storeData.clearAll()
    session.setStore(store)
  }

  suspend fun signOut() {
    runCatching { api.signOut() }
    session.signOut()
  }
}
