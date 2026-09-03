package np.bill.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import np.bill.data.prefs.AppMode
import np.bill.data.prefs.SessionStore
import np.bill.data.repo.AuthRepository

data class RootState(
  val ready: Boolean = false,
  val signedIn: Boolean = false,
  val modeChosen: Boolean = false,
  val mode: AppMode = AppMode.BUSINESS,
  val hasStore: Boolean = false,
  /**
   * Whether the business has been through review.
   *
   * Read from the store the device already holds, so a till with no signal still opens on
   * the right screen rather than on a biller that will be refused at the first sync.
   */
  val approved: Boolean = false,
)

/**
 * Decides the first screen. It reads what is already on the device and only then asks the
 * server, so a phone with no signal still opens straight into billing.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
  private val session: SessionStore,
  private val auth: AuthRepository,
) : ViewModel() {

  private val _state = MutableStateFlow(RootState())
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      session.deviceId()
      val current = session.current()
      _state.value = RootState(
        ready = true,
        signedIn = current.signedIn,
        modeChosen = current.hasStore || current.mode == AppMode.CUSTOMER,
        mode = current.mode,
        hasStore = current.hasStore,
        approved = current.store?.status == "approved",
      )

      // Refreshing in the background can only add a store we did not know about; it never
      // takes the person back to a screen they have already got past.
      if (current.signedIn) {
        val refreshed = auth.bootstrap()
        if (refreshed is np.bill.data.net.ApiResult.Ok && refreshed.value) {
          // The bootstrap also carries where review has got to, so an approval that
          // happened while the phone was in a pocket lands the shop on the biller.
          val latest = session.current()
          _state.value = _state.value.copy(
            hasStore = true,
            approved = latest.store?.status == "approved",
          )
        }
      }
    }
  }
}
