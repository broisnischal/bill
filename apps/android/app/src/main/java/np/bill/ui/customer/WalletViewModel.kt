package np.bill.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.data.db.WalletBillEntity
import np.bill.data.net.ApiResult
import np.bill.data.repo.WalletRepository

data class WalletState(
  val bills: List<WalletBillEntity> = emptyList(),
  val spentThisMonth: Long = 0,
)

data class ScanState(
  val looking: Boolean = false,
  val savedNumber: String? = null,
  val error: String? = null,
)

@HiltViewModel
class WalletViewModel @Inject constructor(private val wallet: WalletRepository) : ViewModel() {

  private val monthStart: Long = Calendar.getInstance().run {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    timeInMillis
  }

  val state = combine(wallet.bills(), wallet.spentSince(monthStart)) { bills, spent ->
    WalletState(bills = bills, spentThisMonth = spent)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletState())

  private val _scan = MutableStateFlow(ScanState())
  val scan = _scan.asStateFlow()

  /** Handles one scan. Repeated frames of the same code are ignored while one is in flight. */
  fun onScanned(raw: String) {
    if (_scan.value.looking) return
    val token = wallet.tokenFrom(raw)
    if (token == null) {
      _scan.update { it.copy(error = "not_found") }
      return
    }

    viewModelScope.launch {
      _scan.update { it.copy(looking = true, error = null) }
      when (val fetched = wallet.fetch(token)) {
        is ApiResult.Ok -> {
          wallet.save(token, fetched.value)
          _scan.update {
            it.copy(looking = false, savedNumber = fetched.value.invoice.invoiceNumber)
          }
        }
        ApiResult.Offline -> _scan.update { it.copy(looking = false, error = "offline") }
        is ApiResult.Failed -> _scan.update { it.copy(looking = false, error = "not_found") }
      }
    }
  }

  fun clearScan() = _scan.update { ScanState() }
}
