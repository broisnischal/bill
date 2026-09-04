package np.bill.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.ErrorSheet
import np.bill.ui.common.FieldHeight
import np.bill.ui.common.Notice
import np.bill.ui.common.PrimaryButton
import np.bill.ui.theme.Gutter
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/** What the server sends. The boxes below are built from it, so there is one number. */
private const val CODE_LENGTH = 6

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
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Gutter),
  ) {
    AuthHeader(
      title = stringResource(R.string.otp_title),
      subtitle = stringResource(R.string.otp_subtitle, phoneNumber),
    )

    CodeInput(
      value = state.code,
      onValueChange = { value ->
        viewModel.onCodeChanged(value)
        // Six digits is the whole code, so there is nothing left to wait for.
        if (value.length == CODE_LENGTH) viewModel.verify(phoneNumber, onVerified)
      },
      rejected = state.codeRejected,
      enabled = !state.verifying,
    )

    // Always here, empty or not. This line used to be a tinted strip inserted under the
    // button the moment a code was refused, which pushed both the actions below it down
    // while a thumb was already on its way to one of them.
    Box(Modifier.fillMaxWidth().height(22.dp).padding(start = 4.dp, top = 4.dp)) {
      if (state.codeRejected) {
        Text(
          stringResource(R.string.otp_wrong),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }

    if (state.prefilledFromDevServer) {
      Spacer(Modifier.height(4.dp))
      Notice(stringResource(R.string.dev_code_filled))
    }

    Spacer(Modifier.height(12.dp))
    PrimaryButton(
      text = stringResource(R.string.verify),
      onClick = { viewModel.verify(phoneNumber, onVerified) },
      enabled = state.code.length == CODE_LENGTH,
      loading = state.verifying,
    )

    // One row, because they are the same kind of thing: the two ways out of waiting for
    // a code. Stacked, a bordered button above a bare word read as two more decisions
    // under the one the screen is actually asking for.
    Spacer(Modifier.height(18.dp))
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextAction(
        text = stringResource(R.string.resend_code),
        onClick = { viewModel.sendCode(phoneNumber) },
        enabled = !state.sending,
      )
      Box(
        Modifier
          .size(3.dp)
          .clip(CircleShape)
          .background(LocalTokens.current.borderStrong),
      )
      TextAction(text = stringResource(R.string.change_number), onClick = onBack)
    }

    Spacer(Modifier.height(32.dp))
  }

  // Not in the column, so nothing moves when it arrives. Only what a person cannot fix
  // by retyping gets here: a refused code is handled against the boxes above.
  if (state.offline || state.error != null) {
    ErrorSheet(
      title = stringResource(if (state.offline) R.string.error_offline_title else R.string.error_title),
      message = state.error ?: stringResource(R.string.otp_offline),
      onDismiss = viewModel::clearProblem,
      action = stringResource(R.string.retry) to {
        viewModel.clearProblem()
        viewModel.verify(phoneNumber, onVerified)
      },
    )
  }
}

/** A quiet action: a word, a thumb-sized target round it, and no border competing. */
@Composable
private fun TextAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
  Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = if (enabled) {
      MaterialTheme.colorScheme.onSurface
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    },
    modifier = Modifier
      .clip(RoundedCornerShape(Radius.pill))
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
  )
}

/**
 * Six boxes, one field.
 *
 * This was a single Material text field with 12sp of letter-spacing holding all six
 * digits: they drifted away from the box that was meant to hold them, nothing said how
 * many were left to type, and a floated "6-digit code" label repeated the heading above
 * it. One box per digit answers how many without a word, and the box that is waiting is
 * the one that is lit.
 *
 * There is still only one text field behind them, and it draws nothing. That is what
 * keeps the number pad, a pasted code and the phone's own autofill working: six real
 * fields would need focus carried between them by hand and would each be a separate
 * thing for the system to try to fill.
 */
@Composable
private fun CodeInput(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  rejected: Boolean = false,
  enabled: Boolean = true,
) {
  val focusRequester = remember { FocusRequester() }

  // The screen exists to receive one code, so the keyboard is up before anyone asks.
  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  BasicTextField(
    value = value,
    // Digits only and never more than the code: a pasted "code: 123456" lands as the six
    // numbers rather than filling the boxes with the word.
    onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(CODE_LENGTH)) },
    enabled = enabled,
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.NumberPassword,
      imeAction = ImeAction.Done,
    ),
    modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
    // `innerTextField` is deliberately never called. The boxes are the field as far as
    // anyone looking at it is concerned, and a real one behind them would put a second
    // caret and a second copy of the digits on the screen.
    decorationBox = {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(CODE_LENGTH) { index ->
          CodeBox(
            digit = value.getOrNull(index),
            // The next one to fill. Once the code is complete nothing is waiting, so
            // nothing is lit.
            active = enabled && index == value.length,
            rejected = rejected,
            modifier = Modifier.weight(1f),
          )
        }
      }
    },
  )
}

@Composable
private fun CodeBox(
  digit: Char?,
  active: Boolean,
  rejected: Boolean,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalTokens.current
  val shape = RoundedCornerShape(Radius.large)

  Box(
    modifier
      .height(FieldHeight)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainer)
      // All six go red together, because the code was refused, not one digit of it.
      // The colour is on the thing that has to change, which is where somebody about to
      // retype is already looking.
      .border(
        width = if (active || rejected) 2.dp else 1.dp,
        color = when {
          rejected -> MaterialTheme.colorScheme.error
          active -> MaterialTheme.colorScheme.primary
          else -> tokens.borderStrong
        },
        shape = shape,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Text(digit?.toString().orEmpty(), style = MaterialTheme.typography.headlineMedium)
  }
}
