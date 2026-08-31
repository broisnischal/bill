package np.bill.ui.customer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PrimaryButton

/**
 * The shopper's own card.
 *
 * Handing over a name, a phone number and sometimes a PAN is the slowest part of being
 * billed. This is that information as a QR: the shop scans it, the bill is made out
 * correctly, and nobody spells anything twice at a counter.
 */
@Composable
fun MyCardScreen(
  modifier: Modifier = Modifier,
  viewModel: MyCardViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  // The card is only kept live while it is being looked at.
  DisposableEffect(Unit) {
    viewModel.start()
    onDispose(viewModel::stop)
  }

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .imePadding()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      stringResource(R.string.my_card_detail),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))

    when {
      state.loading -> CircularProgressIndicator()

      state.qr != null -> Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          // White behind the code whatever the theme: a dark QR on a dark card
          // does not scan.
          .background(Color.White)
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Image(
          bitmap = state.qr!!.asImageBitmap(),
          contentDescription = stringResource(R.string.my_card_title),
          modifier = Modifier.size(220.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
          state.name,
          style = MaterialTheme.typography.titleLarge,
          color = Color(0xFF111827),
        )
        state.phone?.let {
          Text(it, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF6B7280))
        }
      }

      else -> Notice(
        state.error ?: stringResource(R.string.offline_banner),
        tone = NoticeTone.WARN,
      )
    }

    Spacer(Modifier.height(28.dp))

    OutlinedTextField(
      value = state.name,
      onValueChange = viewModel::onName,
      label = { Text(stringResource(R.string.my_card_name)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = state.pan,
      onValueChange = viewModel::onPan,
      label = { Text(stringResource(R.string.buyer_pan)) },
      singleLine = true,
      isError = state.pan.isNotEmpty() && state.pan.length != 9,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = state.address,
      onValueChange = viewModel::onAddress,
      label = { Text(stringResource(R.string.address)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(20.dp))
    PrimaryButton(
      text = stringResource(R.string.save_card),
      onClick = viewModel::save,
      enabled = state.name.isNotBlank() && (state.pan.isEmpty() || state.pan.length == 9),
      loading = state.saving,
    )
    Spacer(Modifier.height(40.dp))
  }
}
