package np.bill.ui.billing

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.TopAppBarDefaults
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
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.core.money.formatMoney
import np.bill.core.money.formatQuantity
import np.bill.core.nepali.BsDate
import np.bill.data.db.SyncState
import np.bill.ui.common.ActionSheet
import np.bill.ui.common.ChoiceChip
import np.bill.ui.common.DangerButton
import np.bill.ui.common.EmptyState
import np.bill.ui.common.Field
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
  var naming by remember { mutableStateOf(false) }
  var choosingPaper by remember { mutableStateOf(false) }
  var reason by remember { mutableStateOf("") }

  LaunchedEffect(billId) { viewModel.load(billId) }

  val bill = state.bill

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text(bill?.invoiceNumber ?: "") },
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(BillIcons.ArrowLeft, contentDescription = null) }
        },
        actions = {
          IconButton(onClick = { choosingPaper = true }) {
            Icon(BillIcons.Share, contentDescription = stringResource(R.string.share_pdf))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = androidx.compose.ui.graphics.Color.Transparent,
          scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
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
                "${formatQuantity(line.quantityMilli)} ${line.unit} × ${formatMoney(line.unitPricePaisa)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(formatMoney(line.lineTotalPaisa), style = MaterialTheme.typography.bodyLarge)
          }
        }

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))

        TotalsRow(stringResource(R.string.sub_total), formatMoney(bill.subTotalPaisa))
        if (bill.discountPaisa > 0) {
          TotalsRow(stringResource(R.string.discount), "-${formatMoney(bill.discountPaisa)}")
        }
        if (bill.nonTaxableAmountPaisa > 0) {
          TotalsRow(stringResource(R.string.exempt), formatMoney(bill.nonTaxableAmountPaisa))
        }
        if (bill.vatRateBp > 0) {
          TotalsRow(stringResource(R.string.taxable), formatMoney(bill.taxableAmountPaisa))
          TotalsRow(
            stringResource(R.string.vat_at, bill.vatRateBp / 100),
            formatMoney(bill.vatAmountPaisa),
          )
        }
        TotalsRow(
          stringResource(R.string.total),
          "Rs ${formatMoney(bill.totalPaisa)}",
          emphasised = true,
        )
        Text(
          bill.amountInWords,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The two afterthoughts, small and side by side. Neither is what the screen is
        // for, and a full-width button says otherwise.
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          SecondaryButton(
            text = stringResource(R.string.template_save),
            onClick = { naming = true },
            compact = true,
          )
          if (bill.status != "cancelled") {
            SecondaryButton(
              text = stringResource(R.string.cancel_bill),
              onClick = { cancelling = true },
              destructive = true,
              compact = true,
            )
          }
        }


        Spacer(Modifier.height(40.dp))
      }
    }
  }

  if (choosingPaper) {
    // Both papers, because a Nepali shop uses both: the roll goes to the customer on
    // Viber, the sheet goes to the accountant.
    ActionSheet(
      title = stringResource(R.string.choose_paper),
      primary = stringResource(R.string.share_80mm) to {
        viewModel.share(context, BillDetailViewModel.PrintFormat.RECEIPT_80MM)
        choosingPaper = false
      },
      secondary = stringResource(R.string.share_a4) to {
        viewModel.share(context, BillDetailViewModel.PrintFormat.A4)
        choosingPaper = false
      },
      onDismiss = { choosingPaper = false },
    )
  }

  if (naming) {
    TemplateNameSheet(
      suggestion = bill?.buyerName.orEmpty(),
      onSave = { name ->
        viewModel.saveAsTemplate(name)
        naming = false
      },
      onDismiss = { naming = false },
    )
  }

  if (cancelling) {
    CancelBillSheet(
      invoiceNumber = bill?.invoiceNumber.orEmpty(),
      reason = reason,
      onReason = { reason = it },
      onConfirm = {
        viewModel.cancel(reason.trim())
        cancelling = false
      },
      onDismiss = { cancelling = false },
    )
  }
}

/**
 * Cancelling a bill, which is not the same as deleting one and must not read like it.
 *
 * The sheet says what cancelling actually does — the bill stays in the register and in
 * the return, marked cancelled — because the shopkeeper reaching for it usually wants a
 * credit note instead, and finding that out afterwards is expensive.
 *
 * The reason is required and offered as the four that account for nearly every
 * cancellation, since it is written into the audit trail and read by an auditor who was
 * not there. Typing one by hand at a counter produces "mistake", which tells them
 * nothing.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CancelBillSheet(
  invoiceNumber: String,
  reason: String,
  onReason: (String) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val offered = listOf(
    stringResource(R.string.cancel_reason_pan),
    stringResource(R.string.cancel_reason_amount),
    stringResource(R.string.cancel_reason_duplicate),
    stringResource(R.string.cancel_reason_no_sale),
  )
  var writingOwn by remember { mutableStateOf(reason.isNotBlank() && reason !in offered) }

  androidx.compose.material3.ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(
      topStart = np.bill.ui.theme.Radius.sheet,
      topEnd = np.bill.ui.theme.Radius.sheet,
    ),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
      Text(
        stringResource(R.string.cancel_bill_title, invoiceNumber),
        style = MaterialTheme.typography.headlineSmall,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        stringResource(R.string.cancel_bill_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.height(18.dp))
      Text(
        stringResource(R.string.cancel_reason_required),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(8.dp))

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in offered) {
          ChoiceChip(
            text = option,
            selected = !writingOwn && reason == option,
            onClick = {
              writingOwn = false
              onReason(option)
            },
          )
        }
        ChoiceChip(
          text = stringResource(R.string.cancel_reason_other),
          selected = writingOwn,
          onClick = {
            writingOwn = true
            onReason("")
          },
        )
      }

      if (writingOwn) {
        Spacer(Modifier.height(12.dp))
        Field(
          value = reason,
          onValueChange = onReason,
          label = stringResource(R.string.cancel_reason),
          hint = stringResource(R.string.cancel_reason_hint),
          singleLine = false,
        )
      }

      Spacer(Modifier.height(20.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(
          text = stringResource(R.string.keep),
          onClick = onDismiss,
          modifier = Modifier.weight(1f),
        )
        DangerButton(
          text = stringResource(R.string.cancel),
          onClick = onConfirm,
          enabled = reason.trim().length >= 5,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

/** Names a basket so it can be billed again from the home screen in one tap. */
@Composable
private fun TemplateNameSheet(
  suggestion: String,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }

  np.bill.ui.common.FormSheet(
    title = stringResource(R.string.template_save),
    onDismiss = onDismiss,
    heightFraction = 0.5f,
    action = {
      PrimaryButton(
        text = stringResource(R.string.save),
        onClick = { onSave(name.trim()) },
        enabled = name.trim().length >= 2,
      )
    },
  ) {
    Field(
      value = name,
      onValueChange = { name = it },
      label = stringResource(R.string.template_name),
      hint = stringResource(R.string.template_name_hint),
      placeholder = suggestion.ifBlank { null },
    )
  }
}
