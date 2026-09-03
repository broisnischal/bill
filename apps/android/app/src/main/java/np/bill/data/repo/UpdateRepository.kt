package np.bill.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import np.bill.BuildConfig
import np.bill.data.net.ApiResult
import np.bill.data.net.AppReleaseResponse
import np.bill.data.net.BillApi
import np.bill.data.net.apiCall
import np.bill.data.prefs.UpdateStore

/** Where this install stands against what the server is handing out. */
sealed interface UpdateStatus {
  data object UpToDate : UpdateStatus

  /**
   * A newer build exists and the shopkeeper may take it or leave it. Leaving it is
   * remembered against that version, so saying no once is not being asked every launch.
   */
  data class Optional(val release: AppReleaseResponse) : UpdateStatus

  /** Below the floor the server publishes. This build does not go on billing. */
  data class Required(val release: AppReleaseResponse) : UpdateStatus
}

/**
 * Whether the app on this phone is still the app the server expects.
 *
 * One status for the whole process rather than one per screen: settings can ask for a
 * check and the update that turns up is shown by the same gate that shows it at launch,
 * which is the only way the two cannot disagree about what is installed.
 */
@Singleton
class UpdateRepository @Inject constructor(
  private val api: BillApi,
  private val store: UpdateStore,
) {

  private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.UpToDate)
  val status: StateFlow<UpdateStatus> = _status.asStateFlow()

  /**
   * Asks the server what the newest build is, at most every few hours unless something
   * asked directly.
   *
   * A check that cannot reach the server falls back to the last answer rather than
   * assuming all is well: a build already told it is too old stays held while the shop
   * has no signal, which is the entire point of holding it.
   */
  suspend fun refresh(force: Boolean = false): UpdateStatus {
    val stored = store.lastSeen()
    val stale = System.currentTimeMillis() - store.checkedAt() >= CHECK_EVERY_MS

    // A phone already being held asks every time rather than on the usual schedule. The
    // server's answer is the only thing that can let it bill again, and a floor that was
    // raised by mistake would otherwise hold a shop for the rest of the day.
    val held = stored != null && BuildConfig.VERSION_CODE < stored.minimumVersionCode

    val release = if (force || stale || held) fetch() ?: stored else stored
    val status = if (release == null) UpdateStatus.UpToDate else judge(release)
    _status.value = status
    return status
  }

  /** Stops an optional update asking again until there is a newer one to ask about. */
  suspend fun skip(release: AppReleaseResponse) {
    store.skip(release.versionCode)
    _status.value = UpdateStatus.UpToDate
  }

  private suspend fun fetch(): AppReleaseResponse? =
    when (val result = apiCall { api.appRelease() }) {
      is ApiResult.Ok -> result.value.also { store.remember(it) }
      else -> null
    }

  private suspend fun judge(release: AppReleaseResponse): UpdateStatus {
    val installed = BuildConfig.VERSION_CODE
    return when {
      installed < release.minimumVersionCode -> UpdateStatus.Required(release)
      installed >= release.versionCode -> UpdateStatus.UpToDate
      store.skipped() >= release.versionCode -> UpdateStatus.UpToDate
      else -> UpdateStatus.Optional(release)
    }
  }

  private companion object {
    /** Often enough that a fix reaches a till the same day, seldom enough to be free. */
    const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000
  }
}
