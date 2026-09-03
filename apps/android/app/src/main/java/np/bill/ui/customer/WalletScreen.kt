package np.bill.ui.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.core.money.formatMoney
import np.bill.core.nepali.BsDate
import np.bill.ui.common.EmptyState

/** The bills a shopper has kept, newest first, with what they have spent this month. */
@Composable
fun WalletScreen(
  onScan: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: WalletViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  Column(modifier.fillMaxSize()) {
    Surface(tonalElevation = 1.dp) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(
          stringResource(R.string.spent_this_month),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text("Rs ", style = MaterialTheme.typography.titleLarge)
          Text(formatMoney(state.spentThisMonth), style = MaterialTheme.typography.displaySmall)
        }
      }
    }

    if (state.bills.isEmpty()) {
      EmptyState(stringResource(R.string.no_bills_yet))
    } else {
      LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(state.bills, key = { it.shareToken }) { bill ->
          Row(
            Modifier
              .fillMaxWidth()
              .clickable(onClick = onScan)
              .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(
                bill.sellerName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                "${BsDate.parse(bill.miti)?.formatLong() ?: bill.miti} · ${bill.invoiceNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              "Rs ${formatMoney(bill.totalPaisa)}",
              style = MaterialTheme.typography.titleMedium,
              textDecoration = if (bill.status == "cancelled") TextDecoration.LineThrough else null,
            )
          }
          Hairline(Modifier.padding(start = 20.dp))
        }
      }
    }
  }
}
