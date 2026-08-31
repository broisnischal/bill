package np.bill.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.data.net.ApiResult
import np.bill.data.net.BillApi
import np.bill.data.net.WebLoginApproveRequest
import np.bill.data.net.apiCall

@Immutable
data class WebLoginState(
  val code: String = "",
  val browser: String? = null,
  val working: Boolean = false,
  val approved: Boolean = false,
  val offline: Boolean = false,
  val error: String? = null,
)

/** How long a code is. Looking it up the moment it is complete saves a button. */
private const val CODE_LENGTH = 6

@HiltViewModel
class WebLoginViewModel @Inject constructor(private val api: BillApi) : ViewModel() {

  private val _state = MutableStateFlow(WebLoginState())
  val state = _state.asStateFlow()

  fun onCode(value: String) {
    // The code has no vowels and no look-alikes, so anything else was a typo.
    val cleaned = value.uppercase().filter(Char::isLetterOrDigit).take(CODE_LENGTH)
    _state.update { it.copy(code = cleaned, error = null, browser = null, approved = false) }
    if (cleaned.length == CODE_LENGTH) lookup(cleaned)
  }

  private fun lookup(code: String) {
    viewModelScope.launch {
      _state.update { it.copy(working = true, offline = false) }
      when (val result = apiCall { api.lookupWebLogin(code) }) {
        is ApiResult.Ok ->
          _state.update { it.copy(working = false, browser = result.value.browser) }
        ApiResult.Offline ->
          _state.update { it.copy(working = false, offline = true) }
        is ApiResult.Failed ->
          _state.update { it.copy(working = false, error = result.message) }
      }
    }
  }

  fun decide(approve: Boolean, onDone: () -> Unit) {
    val code = _state.value.code
    if (code.length != CODE_LENGTH) return

    viewModelScope.launch {
      _state.update { it.copy(working = true, error = null, offline = false) }
      when (
        val result = apiCall { api.approveWebLogin(WebLoginApproveRequest(code, approve)) }
      ) {
        is ApiResult.Ok -> {
          _state.update { WebLoginState(approved = approve) }
          onDone()
        }
        ApiResult.Offline -> _state.update { it.copy(working = false, offline = true) }
        is ApiResult.Failed -> _state.update { it.copy(working = false, error = result.message) }
      }
    }
  }
}
