package np.bill.ui.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import np.bill.R
import np.bill.core.money.formatMoney
import np.bill.data.repo.SavedPaymentQr
import np.bill.ui.common.EmptyState
import np.bill.ui.common.PrimaryButton
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * Taking a digital payment at the counter.
 *
 * Two steps, because a shop keeps more than one wallet and the customer names theirs
 * before anything is shown: pick the wallet, then the code fills the screen. It fills the
 * screen on purpose — a QR shown in a card inside a scrolling page is a QR someone has to
 * hold the shop's phone still to scan, and the counter is the wrong place for that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowQrSheet(
  saved: List<SavedPaymentQr>,
  amountPaisa: Long?,
  onDismiss: () -> Unit,
  onManage: () -> Unit,
) {
  var showing by remember { mutableStateOf<SavedPaymentQr?>(null) }

  showing?.let { chosen ->
    QrFullScreen(
      qr = chosen,
      amountPaisa = amountPaisa,
      onDismiss = {
        showing = null
        onDismiss()
      },
    )
    return
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp),
    ) {
      Text(stringResource(R.string.qr_which), style = MaterialTheme.typography.headlineSmall)

      amountPaisa?.let {
        Spacer(Modifier.height(2.dp))
        Text(
          stringResource(R.string.qr_amount, formatMoney(it)),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(20.dp))

      if (saved.isEmpty()) {
        EmptyState(stringResource(R.string.qr_none_saved))
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = stringResource(R.string.qr_add), onClick = onManage)
      } else {
        saved.forEach { qr ->
          MethodRow(qr = qr, onClick = { showing = qr })
          Spacer(Modifier.height(10.dp))
        }
      }
    }
  }
}

@Composable
private fun MethodRow(qr: SavedPaymentQr, onClick: () -> Unit) {
  val tokens = LocalTokens.current

  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.large))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(1.dp, tokens.border, RoundedCornerShape(Radius.large))
      .clickable(onClick = onClick)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(Radius.medium))
        .background(Color.White),
      contentAlignment = Alignment.Center,
    ) {
      QrThumbnail(qr)
    }
    Column(Modifier.weight(1f)) {
      Text(stringResource(qr.method.labelRes()), style = MaterialTheme.typography.titleMedium)
      qr.label?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * The code, as large as the screen allows, on white.
 *
 * White is not decoration: a scanner needs the quiet zone and the contrast the code was
 * generated with, and a QR tinted by a dark theme is a QR that reads slowly or not at all.
 */
@Composable
internal fun QrFullScreen(qr: SavedPaymentQr, amountPaisa: Long?, onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
      Modifier
        .fillMaxSize()
        .background(Color.White)
        .clickable(onClick = onDismiss)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        stringResource(qr.method.labelRes()),
        style = MaterialTheme.typography.headlineMedium,
        color = Color.Black,
      )
      qr.label?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.DarkGray)
      }

      Spacer(Modifier.height(20.dp))

      Box(
        Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .background(Color.White),
        contentAlignment = Alignment.Center,
      ) {
        QrThumbnail(qr, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
      }

      amountPaisa?.let {
        Spacer(Modifier.height(20.dp))
        Text(
          "Rs ${formatMoney(it)}",
          style = MaterialTheme.typography.displaySmall,
          color = Color.Black,
        )
      }

      Spacer(Modifier.height(12.dp))
      Text(
        stringResource(R.string.qr_tap_to_close),
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
        textAlign = TextAlign.Center,
      )
    }
  }
}
