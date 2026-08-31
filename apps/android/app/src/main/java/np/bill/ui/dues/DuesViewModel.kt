package np.bill.ui.dues

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import np.bill.data.db.BillWithDue
import np.bill.data.repo.BillingRepository
import np.bill.data.sync.SyncWorker

@Immutable
data class DuesState(
  val outstanding: List<BillWithDue> = emptyList(),
  val totalDuePaisa: Long = 0,
)

@HiltViewModel
class DuesViewModel @Inject constructor(
  private val billing: BillingRepository,
  private val application: Application,
) : ViewModel() {

  val state = combine(
    billing.outstanding(),
    billing.totalOutstanding(),
  ) { outstanding, total ->
    DuesState(outstanding, total)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuesState())

  fun collect(billId: String, amountPaisa: Long, method: String) {
    viewModelScope.launch {
      billing.recordPayment(billId, amountPaisa, method)
      // The money is already recorded on the till; this only decides how soon the office
      // sees it.
      SyncWorker.runNow(application)
    }
  }
}
