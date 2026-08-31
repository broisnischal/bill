package np.bill.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Field
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.Panel
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.SecondaryButton

/**
 * Letting a computer in.
 *
 * The browser shows six characters; they get typed here. What is really being checked is
 * that whoever is doing this has the shop's phone in their hand, which is why the screen
 * says which browser is asking before it offers to approve anything — approving something
 * unnamed is a habit worth not teaching.
 */
@Composable
fun WebLoginScreen(
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: WebLoginViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  Column(
    modifier
      .fillMaxSize()
      .imePadding()
      .padding(16.dp),
  ) {
    Text(
      stringResource(R.string.web_login_detail),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))

    Field(
      value = state.code,
      onValueChange = viewModel::onCode,
      label = stringResource(R.string.web_login_code),
      placeholder = "XXXXXX",
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
      textStyle = MaterialTheme.typography.displaySmall.copy(
        fontFamily = FontFamily.Monospace,
        letterSpacing = 8.sp,
        textAlign = TextAlign.Center,
      ),
      error = state.error,
    )

    state.browser?.let { browser ->
      Spacer(Modifier.height(8.dp))
      Panel {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
          Text(
            stringResource(R.string.web_login_asking),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(2.dp))
          Text(browser, style = MaterialTheme.typography.headlineSmall)
        }
      }

      Spacer(Modifier.height(16.dp))
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton(
          text = stringResource(R.string.web_login_approve),
          onClick = { viewModel.decide(approve = true, onDone = onDone) },
          loading = state.working,
        )
        SecondaryButton(
          text = stringResource(R.string.web_login_deny),
          onClick = { viewModel.decide(approve = false, onDone = onDone) },
          destructive = true,
        )
      }
    }

    if (state.approved) {
      Spacer(Modifier.height(16.dp))
      Notice(stringResource(R.string.web_login_done))
    }

    if (state.offline) {
      Spacer(Modifier.height(16.dp))
      Notice(stringResource(R.string.offline_banner), tone = NoticeTone.WARN)
    }
  }
}
