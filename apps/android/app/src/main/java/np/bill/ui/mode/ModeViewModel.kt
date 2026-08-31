package np.bill.ui.mode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import np.bill.data.prefs.AppMode
import np.bill.data.prefs.SessionStore

@HiltViewModel
class ModeViewModel @Inject constructor(private val session: SessionStore) : ViewModel() {

  fun chooseBusiness(onChosen: (hasStore: Boolean) -> Unit) {
    viewModelScope.launch {
      session.setMode(AppMode.BUSINESS)
      onChosen(session.current().hasStore)
    }
  }

  fun chooseCustomer(onChosen: () -> Unit) {
    viewModelScope.launch {
      session.setMode(AppMode.CUSTOMER)
      onChosen()
    }
  }
}
