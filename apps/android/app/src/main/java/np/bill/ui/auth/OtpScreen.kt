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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PrimaryButton

@Composable
fun OtpScreen(
  phoneNumber: String,
  onVerified: () -> Unit,
  onBack: () -> Unit,
  viewModel: AuthViewModel,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Debug builds against a local server fill the code in; anywhere else this does nothing.
  LaunchedEffect(phoneNumber) { viewModel.fillDevCode(phoneNumber) }

  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .imePadding()
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(stringResource(R.string.otp_title), style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(R.string.otp_subtitle, phoneNumber),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(32.dp))

    OutlinedTextField(
      value = state.code,
      onValueChange = { value ->
        viewModel.onCodeChanged(value)
        // Six digits is the whole code, so there is nothing left to wait for.
        if (value.filter(Char::isDigit).length == 6) viewModel.verify(phoneNumber, onVerified)
      },
      label = { Text(stringResource(R.string.otp_label)) },
      singleLine = true,
      textStyle = MaterialTheme.typography.displaySmall.copy(
        textAlign = TextAlign.Center,
        letterSpacing = 12.sp,
      ),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
      ),
      modifier = Modifier.fillMaxWidth(),
    )

    if (state.prefilledFromDevServer) {
      Spacer(Modifier.height(12.dp))
      Notice(stringResource(R.string.dev_code_filled))
    }

    Spacer(Modifier.height(24.dp))
    PrimaryButton(
      text = stringResource(R.string.verify),
      onClick = { viewModel.verify(phoneNumber, onVerified) },
      enabled = state.code.length == 6,
      loading = state.verifying,
    )

    if (state.offline || state.error != null) {
      Spacer(Modifier.height(16.dp))
      Notice(
        text = state.error ?: stringResource(R.string.offline_banner),
        tone = if (state.error != null) NoticeTone.ERROR else NoticeTone.WARN,
      )
    }

    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      TextButton(onClick = { viewModel.sendCode(phoneNumber) }) {
        Text(stringResource(R.string.resend_code))
      }
      TextButton(onClick = onBack) { Text(stringResource(R.string.change_number)) }
    }
  }
}
