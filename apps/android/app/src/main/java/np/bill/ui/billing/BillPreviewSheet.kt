package np.bill.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import np.bill.R
import np.bill.core.money.formatMoney
import np.bill.core.money.formatQuantity
import np.bill.core.nepali.BsDate
import np.bill.ui.common.FormSheet
import np.bill.ui.common.Hairline
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.SecondaryButton

/**
 * The bill, before it exists.
 *
 * An issued bill cannot be edited. It takes a number out of a government series, it is
 * immutable from that moment, and the only way to undo one is a credit note in its own
 * series that points back at it. So the last thing between the draft and that door is a
 * look at the paper: the shop's name, the lines, what it adds up to, and who it is for.
 *
 * Drawn from the draft rather than rendered as a PDF on purpose. The PDF needs a saved
 * bill with a number on it, and a number cannot be spent on a preview somebody is about
 * to cancel. This is the same content in the same order as the receipt it will print.
 */
@Composable
fun BillPreviewSheet(
  state: NewBillState,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val totals = state.totals

  FormSheet(
    title = stringResource(R.string.preview_title),
    subtitle = stringResource(R.string.preview_subtitle),
    onDismiss = onDismiss,
    heightFraction = 0.9f,
    action = {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(
          text = stringResource(R.string.preview_back),
          onClick = onDismiss,
          modifier = Modifier.weight(1f),
        )
        PrimaryButton(
          text = stringResource(R.string.preview_confirm),
          onClick = onConfirm,
          enabled = state.canSave,
          loading = state.saving,
          modifier = Modifier.weight(1f),
        )
      }
    },
  ) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(state.storeName, style = MaterialTheme.typography.headlineSmall)
      Text(
        stringResource(R.string.review_pan, state.storePan),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        BsDate.parse(state.miti)?.formatLong() ?: state.miti,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(Modifier.height(14.dp))
    Hairline()
    Spacer(Modifier.height(10.dp))

    for (line in totals.lines) {
      Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.weight(1f)) {
          Text(
            line.input.description,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            "${formatQuantity(line.input.quantityMilli)} ${line.input.unit} " +
              "× ${formatMoney(line.input.unitPricePaisa)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          formatMoney(line.lineTotalPaisa),
          style = MaterialTheme.typography.titleLarge,
          textAlign = TextAlign.End,
        )
      }
    }

    Spacer(Modifier.height(10.dp))
    Hairline()
    Spacer(Modifier.height(10.dp))

    PreviewRow(stringResource(R.string.sub_total), formatMoney(totals.subTotalPaisa))
    if (totals.discountPaisa > 0) {
      PreviewRow(stringResource(R.string.discount), "-${formatMoney(totals.discountPaisa)}")
    }
    if (state.vatRateBp > 0) {
      PreviewRow(
        stringResource(R.string.vat_at, state.vatRateBp / 100),
        formatMoney(totals.vatAmountPaisa),
      )
    }

    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.total),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.weight(1f),
      )
      Text("Rs ${formatMoney(totals.totalPaisa)}", style = MaterialTheme.typography.headlineMedium)
    }

    Spacer(Modifier.height(14.dp))
    Text(
      stringResource(R.string.preview_for, state.buyerName.ifBlank { "—" }),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun PreviewRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(value, style = MaterialTheme.typography.bodyLarge)
  }
}
