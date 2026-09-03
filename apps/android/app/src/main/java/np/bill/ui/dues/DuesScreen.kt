package np.bill.ui.dues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.core.money.formatMoney
import np.bill.core.nepali.BsDate
import np.bill.ui.common.EmptyState
import np.bill.ui.common.Field
import np.bill.ui.common.Hairline
import np.bill.ui.common.PaymentChip
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.SecondaryButton
import np.bill.ui.theme.LocalTokens

/**
 * Who owes the shop money.
 *
 * A kirana counter runs on credit, and the thing a shopkeeper needs is not a ledger but
 * an answer to "has this person paid": the list is bills with something still on them,
 * newest first, and taking money is two taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuesScreen(
  modifier: Modifier = Modifier,
  viewModel: DuesViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val tokens = LocalTokens.current
  var collecting by remember { mutableStateOf<String?>(null) }

  Column(modifier.fillMaxSize()) {
    Surface(tonalElevation = 1.dp) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(
          stringResource(R.string.total_due),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text("Rs ", style = MaterialTheme.typography.titleLarge, color = tokens.due)
          Text(
            formatMoney(state.totalDuePaisa),
            style = MaterialTheme.typography.displaySmall,
            color = tokens.due,
          )
        }
      }
    }
    Hairline()

    if (state.outstanding.isEmpty()) {
      EmptyState(stringResource(R.string.no_dues))
    } else {
      LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(state.outstanding, key = { it.bill.id }) { entry ->
          Row(
            Modifier
              .fillMaxWidth()
              .clickable { collecting = entry.bill.id }
              .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(entry.bill.buyerName, style = MaterialTheme.typography.bodyLarge)
              Text(
                listOfNotNull(
                  entry.bill.invoiceNumber,
                  entry.bill.dueMiti?.let { due ->
                    BsDate.parse(due)?.formatLong() ?: due
                  },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                "Rs ${formatMoney(entry.duePaisa)}",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.due,
              )
              if (entry.duePaisa != entry.bill.totalPaisa) {
                Text(
                  "of ${formatMoney(entry.bill.totalPaisa)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          Hairline(Modifier.padding(start = 20.dp))
        }
      }
    }
  }

  collecting?.let { billId ->
    val entry = state.outstanding.firstOrNull { it.bill.id == billId }
    if (entry == null) {
      collecting = null
    } else {
      CollectSheet(
        buyerName = entry.bill.buyerName,
        duePaisa = entry.duePaisa,
        onCollect = { amount, method ->
          viewModel.collect(billId, amount, method)
          collecting = null
        },
        onDismiss = { collecting = null },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectSheet(
  buyerName: String,
  duePaisa: Long,
  onCollect: (Long, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var amount by remember { mutableStateOf(np.bill.core.money.paisaToInput(duePaisa)) }
  var method by remember { mutableStateOf("cash") }
  val parsed = np.bill.core.money.parsePaisa(amount)
  val valid = parsed != null && parsed > 0 && parsed <= duePaisa

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 32.dp),
    ) {
      Text(buyerName, style = MaterialTheme.typography.headlineSmall)
      Text(
        stringResource(R.string.due_amount, formatMoney(duePaisa)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(16.dp))

      Field(
        value = amount,
        onValueChange = { amount = it },
        label = stringResource(R.string.amount_received),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        error = if (parsed != null && parsed > duePaisa) {
          stringResource(R.string.due_amount, formatMoney(duePaisa))
        } else {
          null
        },
        textStyle = MaterialTheme.typography.headlineMedium,
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in listOf("cash", "esewa", "khalti", "fonepay", "bank")) {
          PaymentChip(
            method = option,
            selected = method == option,
            onClick = { method = option },
          )
        }
      }

      Spacer(Modifier.height(20.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(
          text = stringResource(R.string.pay_full),
          onClick = { onCollect(duePaisa, method) },
          modifier = Modifier.weight(1f),
        )
        PrimaryButton(
          text = stringResource(R.string.record_payment),
          onClick = { parsed?.let { onCollect(it, method) } },
          enabled = valid,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}
