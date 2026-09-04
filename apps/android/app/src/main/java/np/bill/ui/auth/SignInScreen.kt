package np.bill.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import np.bill.ui.common.Field
import np.bill.ui.common.ErrorSheet
import np.bill.ui.common.PrimaryButton
import np.bill.ui.theme.Gutter

/**
 * Signing in is one field. A shopkeeper types the number they already know and gets a
 * code; there is no password to invent, forget or reset.
 */
@Composable
fun SignInScreen(onCodeSent: (String) -> Unit, viewModel: AuthViewModel) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val valid = viewModel.normalised != null

  // Only once there are enough digits for it to be a number at all. Saying a half-typed
  // number is wrong is telling somebody off for not having finished.
  val wrong = state.phoneInput.length >= 10 && !valid

  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .imePadding()
      // Scrollable because the keyboard takes half a short phone, and a form that cannot
      // scroll under one is a form with its own button off the bottom of the screen.
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Gutter),
  ) {
    AuthHeader(
      title = stringResource(R.string.sign_in_title),
      subtitle = stringResource(R.string.sign_in_subtitle),
    )

    Field(
      value = state.phoneInput,
      onValueChange = viewModel::onPhoneChanged,
      // The heading is the label. Floated, it read "Mobile number" directly beneath a
      // heading that already said it, and spent a line of the screen doing so.
      label = stringResource(R.string.phone_label),
      showLabel = false,
      placeholder = stringResource(R.string.phone_hint),
      prefix = {
        Text(
          "+977 ",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      error = if (wrong) stringResource(R.string.phone_invalid) else null,
      textStyle = MaterialTheme.typography.headlineSmall,
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Done,
      ),
      modifier = Modifier.fillMaxWidth(),
    )

    // 8, not 24: Field already keeps a line under itself for the error, so the old gap
    // was that line plus a gap and the button sat adrift of the thing it acts on.
    Spacer(Modifier.height(8.dp))
    PrimaryButton(
      text = stringResource(R.string.send_code),
      onClick = { viewModel.sendCode(onSent = onCodeSent) },
      enabled = valid,
      loading = state.sending,
    )

    Spacer(Modifier.height(32.dp))
  }

  // Outside the column: a message that arrives after Send code was pressed must not move
  // the button that was just pressed, and the offline line here runs to three of them.
  if (state.offline || state.error != null) {
    ErrorSheet(
      title = stringResource(
        if (state.offline) R.string.error_offline_title else R.string.error_title,
      ),
      message = state.error ?: stringResource(R.string.sign_in_offline),
      onDismiss = viewModel::clearProblem,
      action = stringResource(R.string.retry) to {
        viewModel.clearProblem()
        viewModel.sendCode(onSent = onCodeSent)
      },
    )
  }
}
