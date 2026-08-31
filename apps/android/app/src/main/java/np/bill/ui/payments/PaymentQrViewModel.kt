package np.bill.ui.payments

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.data.repo.PaymentQrMethod
import np.bill.data.repo.PaymentQrRepository
import np.bill.data.repo.SavedPaymentQr

data class PaymentQrState(
  val saved: List<SavedPaymentQr> = emptyList(),
  val error: String? = null,
)

@HiltViewModel
class PaymentQrViewModel @Inject constructor(
  private val repository: PaymentQrRepository,
) : ViewModel() {

  private val _state = MutableStateFlow(PaymentQrState())
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      repository.observe().collect { saved -> _state.update { it.copy(saved = saved) } }
    }
  }

  fun save(method: PaymentQrMethod, source: Uri, label: String?) {
    viewModelScope.launch {
      runCatching { repository.save(method, source, label) }
        .onFailure { error -> _state.update { it.copy(error = error.message) } }
    }
  }

  /** A code read off the shop's own printed QR, or built from the number they typed. */
  fun savePayload(method: PaymentQrMethod, payload: String, label: String?) {
    viewModelScope.launch {
      runCatching { repository.saveFromPayload(method, payload.trim(), label) }
        .onFailure { error -> _state.update { it.copy(error = error.message) } }
    }
  }

  fun remove(method: PaymentQrMethod) {
    viewModelScope.launch { repository.remove(method) }
  }

  fun clearError() = _state.update { it.copy(error = null) }
}
