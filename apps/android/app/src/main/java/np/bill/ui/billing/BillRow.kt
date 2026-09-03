package np.bill.ui.billing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.core.money.formatMoney
import np.bill.data.db.BillEntity
import np.bill.data.db.SyncState
import np.bill.ui.common.IconTile
import np.bill.ui.common.TileTone
import np.bill.ui.theme.LocalTokens

/** One line in the bill list: who, how much, and whether the office has it yet. */
@Composable
fun BillRow(bill: BillEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val cancelled = bill.status == "cancelled"

  Row(
    modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    IconTile(
      icon = BillIcons.ReceiptText,
      tone = when {
        cancelled -> TileTone.NEGATIVE
        bill.syncState == SyncState.REJECTED -> TileTone.NEGATIVE
        else -> TileTone.MINT
      },
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        bill.buyerName,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
      )
      Spacer(Modifier.height(1.dp))
      Text(
        bill.invoiceNumber,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Column(horizontalAlignment = Alignment.End) {
      Text(
        "Rs ${formatMoney(bill.totalPaisa)}",
        style = MaterialTheme.typography.titleLarge,
        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
      )
      SyncLabel(bill)
    }
  }
}

@Composable
private fun SyncLabel(bill: BillEntity) {
  val (text, colour) = when {
    bill.status == "cancelled" -> stringResource(R.string.cancelled) to MaterialTheme.colorScheme.onSurfaceVariant
    bill.syncState == SyncState.REJECTED -> stringResource(R.string.rejected) to MaterialTheme.colorScheme.error
    bill.syncState == SyncState.PENDING ->
      stringResource(R.string.not_synced) to LocalTokens.current.warning
    else -> stringResource(R.string.synced) to MaterialTheme.colorScheme.primary
  }
  Text(text, style = MaterialTheme.typography.labelMedium, color = colour)
}
