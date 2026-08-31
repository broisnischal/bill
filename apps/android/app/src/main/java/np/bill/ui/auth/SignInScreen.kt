package np.bill.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PrimaryButton

/**
 * Signing in is one field. A shopkeeper types the number they already know and gets a
 * code; there is no password to invent, forget or reset.
 */
@Composable
fun SignInScreen(onCodeSent: (String) -> Unit, viewModel: AuthViewModel) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val valid = viewModel.normalised != null

  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .imePadding()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(R.string.sign_in_subtitle),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(32.dp))

    OutlinedTextField(
      value = state.phoneInput,
      onValueChange = viewModel::onPhoneChanged,
      label = { Text(stringResource(R.string.phone_label)) },
      placeholder = { Text(stringResource(R.string.phone_hint)) },
      prefix = { Text("+977 ") },
      singleLine = true,
      textStyle = MaterialTheme.typography.headlineSmall,
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Done,
      ),
      isError = state.phoneInput.length >= 10 && !valid,
      supportingText = if (state.phoneInput.length >= 10 && !valid) {
        { Text(stringResource(R.string.phone_invalid)) }
      } else {
        null
      },
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    PrimaryButton(
      text = stringResource(R.string.send_code),
      onClick = { viewModel.sendCode(onSent = onCodeSent) },
      enabled = valid,
      loading = state.sending,
    )

    if (state.offline || state.error != null) {
      Spacer(Modifier.height(16.dp))
      Notice(
        text = state.error ?: stringResource(R.string.offline_banner),
        tone = if (state.error != null) NoticeTone.ERROR else NoticeTone.WARN,
      )
    }
  }
}
