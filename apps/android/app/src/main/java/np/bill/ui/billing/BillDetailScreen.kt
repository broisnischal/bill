package np.bill.ui.billing

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.core.money.formatPaisa
import np.bill.core.money.formatQuantity
import np.bill.core.nepali.BsDate
import np.bill.data.db.SyncState
import np.bill.ui.common.EmptyState
import np.bill.ui.common.Notice
import np.bill.ui.common.EmptyState
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.SecondaryButton
import np.bill.ui.common.TotalsRow

/**
 * A bill after it is made.
 *
 * The QR is the first thing on screen because the customer is still standing there: they
 * scan it and walk away with the bill on their phone whether or not anything was printed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
  billId: String,
  onBack: () -> Unit,
  viewModel: BillDetailViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var cancelling by remember { mutableStateOf(false) }
  var choosingPaper by remember { mutableStateOf(false) }
  var reason by remember { mutableStateOf("") }

  LaunchedEffect(billId) { viewModel.load(billId) }

  val bill = state.bill

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(bill?.invoiceNumber ?: "") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
        },
        actions = {
          IconButton(onClick = { choosingPaper = true }) {
            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_pdf))
          }
        },
      )
    },
  ) { padding ->
    if (bill == null) {
      Box(
        Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
      ) {
        if (state.loading) {
          CircularProgressIndicator()
        } else {
          EmptyState(stringResource(R.string.bill_not_found))
        }
      }
      return@Scaffold
    }

    Column(
      Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState()),
    ) {
      if (bill.syncState == SyncState.REJECTED) {
        Notice(stringResource(R.string.rejected_detail), tone = NoticeTone.ERROR)
      } else if (bill.syncState == SyncState.PENDING) {
        Notice(stringResource(R.string.not_synced), tone = NoticeTone.WARN)
      }

      state.qr?.let { qr ->
        Column(
          Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Image(
            bitmap = qr.asImageBitmap(),
            contentDescription = stringResource(R.string.show_qr),
            // Big enough to scan from across a counter, small enough that the bill's
            // numbers are still the first thing on screen.
            modifier = Modifier.size(148.dp),
          )
          Spacer(Modifier.height(10.dp))
          Text(
            stringResource(R.string.qr_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          PrimaryButton(
            text = if (bill.printCount > 0) {
              stringResource(R.string.reprint)
            } else {
              stringResource(R.string.print)
            },
            onClick = { viewModel.print(context) },
            loading = state.printing,
            modifier = Modifier.weight(1f),
          )
        }

        state.message?.let {
          Spacer(Modifier.height(12.dp))
          Notice(it, tone = if (state.messageIsError) NoticeTone.ERROR else NoticeTone.INFO)
        }

        Spacer(Modifier.height(24.dp))
        Text(bill.buyerName, style = MaterialTheme.typography.titleLarge)
        Text(
          "${BsDate.parse(bill.miti)?.formatLong() ?: bill.miti} · ${bill.paymentMethod.replaceFirstChar(Char::uppercase)}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        for (line in state.lines) {
          Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.weight(1f)) {
              Text(line.description, style = MaterialTheme.typography.bodyLarge)
              Text(
                "${formatQuantity(line.quantityMilli)} ${line.unit} × ${formatPaisa(line.unitPricePaisa)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(formatPaisa(line.lineTotalPaisa), style = MaterialTheme.typography.bodyLarge)
          }
        }

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        TotalsRow(stringResource(R.string.sub_total), formatPaisa(bill.subTotalPaisa))
        if (bill.discountPaisa > 0) {
          TotalsRow(stringResource(R.string.discount), "-${formatPaisa(bill.discountPaisa)}")
        }
        if (bill.nonTaxableAmountPaisa > 0) {
          TotalsRow(stringResource(R.string.exempt), formatPaisa(bill.nonTaxableAmountPaisa))
        }
        if (bill.vatRateBp > 0) {
          TotalsRow(stringResource(R.string.taxable), formatPaisa(bill.taxableAmountPaisa))
          TotalsRow(
            stringResource(R.string.vat_at, bill.vatRateBp / 100),
            formatPaisa(bill.vatAmountPaisa),
          )
        }
        TotalsRow(
          stringResource(R.string.total),
          "Rs ${formatPaisa(bill.totalPaisa)}",
          emphasised = true,
        )
        Text(
          bill.amountInWords,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        if (bill.status != "cancelled") {
          SecondaryButton(
            text = stringResource(R.string.cancel_bill),
            onClick = { cancelling = true },
          )
        }
        Spacer(Modifier.height(40.dp))
      }
    }
  }

  if (choosingPaper) {
    // Both papers, because a Nepali shop uses both: the roll goes to the customer on
    // Viber, the sheet goes to the accountant.
    AlertDialog(
      onDismissRequest = { choosingPaper = false },
      title = { Text(stringResource(R.string.choose_paper)) },
      text = {
        Column {
          TextButton(
            onClick = {
              viewModel.share(context, BillDetailViewModel.PrintFormat.RECEIPT_80MM)
              choosingPaper = false
            },
          ) { Text(stringResource(R.string.share_80mm)) }
          TextButton(
            onClick = {
              viewModel.share(context, BillDetailViewModel.PrintFormat.A4)
              choosingPaper = false
            },
          ) { Text(stringResource(R.string.share_a4)) }
        }
      },
      confirmButton = {
        TextButton(onClick = { choosingPaper = false }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }

  if (cancelling) {
    AlertDialog(
      onDismissRequest = { cancelling = false },
      title = { Text(stringResource(R.string.cancel_bill)) },
      text = {
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text(stringResource(R.string.cancel_reason)) },
          singleLine = false,
        )
      },
      confirmButton = {
        TextButton(
          enabled = reason.trim().length >= 5,
          onClick = {
            viewModel.cancel(reason.trim())
            cancelling = false
          },
        ) { Text(stringResource(R.string.cancel_bill)) }
      },
      dismissButton = {
        TextButton(onClick = { cancelling = false }) { Text(stringResource(R.string.done)) }
      },
    )
  }
}
