package np.bill.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.data.net.ApiResult
import np.bill.data.repo.AuthRepository

data class AuthState(
  val phoneInput: String = "",
  val code: String = "",
  val sending: Boolean = false,
  val verifying: Boolean = false,
  val error: String? = null,
  val offline: Boolean = false,
  val sentTo: String? = null,
  val verified: Boolean = false,
  /** True when the code was filled in from a local dev server rather than an SMS. */
  val prefilledFromDevServer: Boolean = false,
  /**
   * The code was refused, as opposed to something breaking.
   *
   * Held apart from [error] because the two want different answers on screen. A wrong
   * code is corrected by typing, so it belongs against the boxes; anything else is a
   * problem the person cannot type their way out of.
   */
  val codeRejected: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

  private val _state = MutableStateFlow(AuthState())
  val state = _state.asStateFlow()

  /** The number in E.164, or null while what has been typed is not yet a valid mobile. */
  val normalised: String? get() = auth.normalise(_state.value.phoneInput)

  fun onPhoneChanged(value: String) {
    // Ten digits is the whole of a Nepali mobile; anything longer is a typo, not a number.
    _state.update { it.copy(phoneInput = value.filter(Char::isDigit).take(13), error = null) }
  }

  fun onCodeChanged(value: String) {
    _state.update {
      it.copy(code = value.filter(Char::isDigit).take(6), error = null, codeRejected = false)
    }
  }

  /**
   * Sends a code.
   *
   * The number is passed in rather than read from the field, because resending happens on
   * the code screen where the field is no longer on show. A resend invalidates whatever
   * was sent before, so the code on screen is cleared with it.
   */
  fun sendCode(phoneNumber: String? = null, onSent: (String) -> Unit = {}) {
    val phone = phoneNumber ?: normalised ?: return
    viewModelScope.launch {
      _state.update {
        it.copy(sending = true, error = null, offline = false, code = "", prefilledFromDevServer = false)
      }
      when (val result = auth.sendOtp(phone)) {
        is ApiResult.Ok -> {
          _state.update { it.copy(sending = false, sentTo = phone) }
          onSent(phone)
        }
        ApiResult.Offline ->
          _state.update { it.copy(sending = false, offline = true) }
        is ApiResult.Failed ->
          _state.update { it.copy(sending = false, error = result.message) }
      }
    }
  }

  /**
   * Against a local server with no SMS gateway the code is logged rather than sent, so it
   * is filled in instead of making a developer read the server output. Fetched on the code
   * screen rather than at send time so a resend always shows the newest one.
   */
  fun fillDevCode(phoneNumber: String) {
    viewModelScope.launch {
      val code = auth.devOtp(phoneNumber) ?: return@launch
      _state.update {
        // Never overwrite something already being typed.
        if (it.code.isEmpty()) it.copy(code = code, prefilledFromDevServer = true) else it
      }
    }
  }

  /** Dismisses whatever the error sheet is showing, without touching the typed code. */
  fun clearProblem() {
    _state.update { it.copy(error = null, offline = false) }
  }

  fun verify(phoneNumber: String, onVerified: () -> Unit) {
    val code = _state.value.code
    if (code.length < 6) return
    viewModelScope.launch {
      _state.update { it.copy(verifying = true, error = null, offline = false, codeRejected = false) }
      when (val result = auth.verifyOtp(phoneNumber, code)) {
        is ApiResult.Ok -> {
          _state.update { it.copy(verifying = false, verified = true) }
          onVerified()
        }
        ApiResult.Offline ->
          _state.update { it.copy(verifying = false, offline = true) }
        // The server refusing the code is the ordinary outcome of mistyping one, not a
        // failure of the app. Any other status is something the person cannot fix here.
        is ApiResult.Failed -> _state.update {
          if (result.status in 400..499) {
            it.copy(verifying = false, codeRejected = true)
          } else {
            it.copy(verifying = false, error = result.message)
          }
        }
      }
    }
  }
}
