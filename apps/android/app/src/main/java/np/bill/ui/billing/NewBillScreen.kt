package np.bill.ui.billing

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import np.bill.ui.theme.BillIcons
import np.bill.scan.CodeScanner
import np.bill.scan.ScanTarget
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.core.money.formatMoney
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import np.bill.ui.common.Notice
import np.bill.ui.common.PaymentChip
import np.bill.core.money.formatQuantity
import np.bill.ui.common.BsDateField
import np.bill.ui.common.CalculatorSheet
import np.bill.ui.common.ChoiceChip
import np.bill.ui.common.CompactPicker
import np.bill.ui.common.Field
import np.bill.ui.common.Hairline
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.InitialTile
import np.bill.ui.common.Panel
import np.bill.ui.common.QuantityStepper
import np.bill.ui.theme.LocalTokens
import np.bill.ui.common.PickerField
import np.bill.ui.common.PrimaryButton
import np.bill.ui.theme.Radius
import np.bill.ui.common.TotalsRow

/**
 * Making a bill.
 *
 * The whole screen is built around getting a customer out of the shop: the first line is
 * already there when it opens, the customer's name is optional, and the total is on
 * screen the entire time so the shopkeeper can read it out while still typing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBillScreen(
  onDone: (String) -> Unit,
  onBack: () -> Unit,
  startItemIds: List<String> = emptyList(),
  startCustomerId: String? = null,
  startTemplateId: String? = null,
  viewModel: BillingViewModel = hiltViewModel(),
) {
  val state by viewModel.newBill.collectAsStateWithLifecycle()
  val totals = state.totals
  val context = LocalContext.current

  var scanning by remember { mutableStateOf(ScanMode.NONE) }
  var pickingFor by remember { mutableStateOf<Long?>(null) }
  var pickingCustomer by remember { mutableStateOf(false) }
  var calculatingFor by remember { mutableStateOf<Long?>(null) }
  val savedQrs by viewModel.paymentQrs.collectAsStateWithLifecycle()
  val regulars by viewModel.recentBuyers.collectAsStateWithLifecycle()
  var showingQr by remember { mutableStateOf<np.bill.data.repo.SavedPaymentQr?>(null) }
  var previewing by remember { mutableStateOf(false) }

  var cameraGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  val cameraLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted -> cameraGranted = granted }

  // Opened from a product or a customer row: the bill starts with them already on it.
  LaunchedEffect(startItemIds, startCustomerId) {
    viewModel.startWith(startItemIds, startCustomerId)
  }

  // Opened from a quick-bill chip: the basket is already on the bill and the only thing
  // left is usually the weight.
  LaunchedEffect(startTemplateId) {
    startTemplateId?.let(viewModel::startFromTemplate)
  }

  // Asking for the camera the moment a scan is wanted, not at startup.
  LaunchedEffect(scanning) {
    if (scanning != ScanMode.NONE && !cameraGranted) {
      cameraLauncher.launch(Manifest.permission.CAMERA)
      scanning = ScanMode.NONE
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.new_bill)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(BillIcons.ArrowLeft, contentDescription = null)
          }
        },
        actions = {
          IconButton(onClick = { scanning = ScanMode.PRODUCT }) {
            Icon(
              BillIcons.ScanLine,
              contentDescription = stringResource(R.string.scan_barcode),
            )
          }
          IconButton(onClick = { scanning = ScanMode.CUSTOMER }) {
            Icon(
              BillIcons.IdCard,
              contentDescription = stringResource(R.string.scan_customer),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = androidx.compose.ui.graphics.Color.Transparent,
          scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
      )
    },
    bottomBar = {
      // The total and the one action stay pinned: the shopkeeper never scrolls to finish.
      Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp)) {
          TotalsRow(
            label = stringResource(R.string.total),
            value = "Rs ${formatMoney(totals.totalPaisa)}",
            emphasised = true,
          )
          Spacer(Modifier.height(12.dp))
          PrimaryButton(
            text = stringResource(R.string.save_bill),
            // Shows the paper first. A bill cannot be edited once it is issued — the
            // only way back is a credit note in its own series — so the last thing
            // before that door closes is a look at what will print.
            onClick = { previewing = true },
            enabled = state.canSave,
            loading = state.saving,
          )

        }
      }
    },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize().padding(padding),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
      state.error?.let { message ->
        item { Notice(message, tone = NoticeTone.ERROR) }
      }

      items(state.lines, key = { it.id }, contentType = { "line" }) { line ->
        val handlers = remember(line.id) { viewModel.handlersFor(line.id) }
        LineEditor(
          line = line,
          canRemove = state.lines.size > 1,
          suggestions = viewModel.itemSuggestions(line.description),
          onChange = handlers.onChange,
          onRemove = handlers.onRemove,
          onPick = { pickingFor = line.id },
          onUse = { viewModel.pickItems(line.id, listOf(it)) },
          onCalculate = { calculatingFor = line.id },
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
      }

      item {
        TextButton(
          onClick = viewModel::addLine,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
          Icon(BillIcons.Plus, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.add_item))
        }
      }

      item {
        Column(Modifier.padding(20.dp)) {
          Hairline()
          Spacer(Modifier.height(16.dp))

          TotalsRow(stringResource(R.string.sub_total), formatMoney(totals.subTotalPaisa))
          if (totals.discountPaisa > 0) {
            TotalsRow(stringResource(R.string.discount), "-${formatMoney(totals.discountPaisa)}")
          }
          if (totals.nonTaxableAmountPaisa > 0) {
            TotalsRow(stringResource(R.string.exempt), formatMoney(totals.nonTaxableAmountPaisa))
          }
          if (state.vatRateBp > 0) {
            TotalsRow(stringResource(R.string.taxable), formatMoney(totals.taxableAmountPaisa))
            TotalsRow(
              stringResource(R.string.vat_at, state.vatRateBp / 100),
              formatMoney(totals.vatAmountPaisa),
            )
          }

          Spacer(Modifier.height(16.dp))
          Field(
            value = state.discount,
            onValueChange = viewModel::onDiscount,
            label = stringResource(R.string.discount),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          )

          Spacer(Modifier.height(12.dp))

          // The faces this counter sees. One tap fills the name, the phone and the PAN
          // from the last bill they were on, which is the whole of "choosing a
          // customer" for a shop that serves the same people every week. Taken from the
          // bills rather than the customer list, so a walk-in typed once counts.
          if (regulars.isNotEmpty() && state.buyerName.isBlank()) {
            Row(
              Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              for (buyer in regulars) {
                Row(
                  Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                      1.dp,
                      LocalTokens.current.borderStrong,
                      RoundedCornerShape(Radius.pill),
                    )
                    .clickable { viewModel.useRecentBuyer(buyer) }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  InitialTile(buyer.name, size = 30.dp)
                  Spacer(Modifier.width(8.dp))
                  Text(
                    buyer.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                  )
                }
              }
            }
            Spacer(Modifier.height(10.dp))
          }

          // Required, and said so before it is refused rather than after. A shop that
          // cannot name half its bills cannot search its own history, and "Cash
          // customer" printed forty times is the same as nothing written down.
          Field(
            value = state.buyerName,
            onValueChange = viewModel::onBuyerName,
            label = stringResource(R.string.buyer_name),
            placeholder = stringResource(R.string.buyer_name_default),
            hint = stringResource(R.string.required),
            trailingIcon = {
              IconButton(onClick = { pickingCustomer = true }) {
                Icon(
                  BillIcons.Users,
                  contentDescription = stringResource(R.string.search_customers),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            },
          )
          Field(
            value = state.buyerPhone,
            onValueChange = viewModel::onBuyerPhone,
            label = stringResource(R.string.buyer_phone),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          )
          Field(
            value = state.buyerPan,
            onValueChange = viewModel::onBuyerPan,
            label = stringResource(R.string.buyer_pan),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          )
          Field(
            value = state.notes,
            onValueChange = viewModel::onNotes,
            label = stringResource(R.string.notes),
            singleLine = false,
            minLines = 2,
          )

          // Tax invoice against abbreviated tax invoice is a VAT distinction — the
          // abbreviated form exists because a VAT taxpayer may issue it below a
          // threshold. With VAT off there is one kind of bill, so asking is noise.
          if (np.bill.BuildConfig.VAT_ENABLED) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.bill_type), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ChoiceChip(
                text = stringResource(R.string.type_tax_invoice),
                selected = state.invoiceType == "tax_invoice",
                onClick = { viewModel.onInvoiceType("tax_invoice") },
              )
              ChoiceChip(
                text = stringResource(R.string.type_abbreviated),
                selected = state.invoiceType == "abbreviated_tax_invoice",
                onClick = { viewModel.onInvoiceType("abbreviated_tax_invoice") },
              )
            }
          }

          // Paid or owed is gone from here. A bill is the record of a sale that was
          // settled: it carries a number from a government series, it is immutable once
          // issued, and it can only be undone with a credit note. Money somebody owes is
          // not that — it is a running account, and it lives in the credit book until it is
          // paid, at which point a bill is made for it.

          Spacer(Modifier.height(8.dp))
          Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.labelLarge)
          Spacer(Modifier.height(8.dp))
          Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            for (method in listOf("cash", "esewa", "khalti", "fonepay", "bank", "card", "credit")) {
              PaymentChip(
                method = method,
                selected = state.paymentMethod == method,
                onClick = {
                  viewModel.onPaymentMethod(method)
                  // The customer is standing there waiting to pay: choosing the wallet
                  // and showing them its code is one action, not two screens apart.
                  showingQr = savedQrs.firstOrNull { it.method.id == method }
                },
              )
            }
          }
        }
      }
    }
  }

  if (previewing) {
    BillPreviewSheet(
      state = state,
      onConfirm = {
        previewing = false
        viewModel.save(onDone)
      },
      onDismiss = { previewing = false },
    )
  }

  showingQr?.let { qr ->
    np.bill.ui.payments.QrFullScreen(
      qr = qr,
      amountPaisa = state.totals.totalPaisa,
      onDismiss = { showingQr = null },
    )
  }

  if (scanning != ScanMode.NONE && cameraGranted) {
    ScanSheet(
      mode = scanning,
      onDismiss = { scanning = ScanMode.NONE },
      onScanned = { code ->
        when (scanning) {
          ScanMode.PRODUCT -> viewModel.onProductScanned(code)
          ScanMode.CUSTOMER -> viewModel.onCustomerCardScanned(code) {}
          ScanMode.NONE -> Unit
        }
        scanning = ScanMode.NONE
      },
    )
  }

  pickingFor?.let { lineId ->
    ProductPickerSheet(
      viewModel = viewModel,
      onPickMany = { items ->
        viewModel.pickItems(lineId, items)
        pickingFor = null
      },
      onCreate = { name ->
        // Written straight onto the line: naming it is the whole of adding it, and the
        // price is about to be typed anyway.
        viewModel.updateLine(lineId) { it.copy(description = name, itemId = null) }
        pickingFor = null
      },
      onDismiss = { pickingFor = null },
    )
  }

  calculatingFor?.let { lineId ->
    CalculatorSheet(
      initial = state.lines.firstOrNull { it.id == lineId }?.rate.orEmpty(),
      onResult = { value -> viewModel.updateLine(lineId) { it.copy(rate = value) } },
      onDismiss = { calculatingFor = null },
    )
  }

  if (pickingCustomer) {
    CustomerPickerSheet(
      viewModel = viewModel,
      onPick = { customer ->
        viewModel.pickCustomer(customer)
        pickingCustomer = false
      },
      onCreate = { name ->
        viewModel.onBuyerName(name)
        pickingCustomer = false
      },
      onDismiss = { pickingCustomer = false },
    )
  }
}

private enum class ScanMode { NONE, PRODUCT, CUSTOMER }

/**
 * One of two answers, as a card rather than a switch.
 *
 * A switch has a default position, and a default here is the thing that loses a shop
 * money. Two cards, neither selected, force the question to be answered.
 */
@Composable
private fun ChoiceCard(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = np.bill.ui.theme.LocalTokens.current
  Box(
    modifier
      .clip(RoundedCornerShape(np.bill.ui.theme.Radius.large))
      .background(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
      )
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else tokens.border,
        shape = RoundedCornerShape(np.bill.ui.theme.Radius.large),
      )
      .clickable(onClick = onClick)
      .padding(vertical = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.titleMedium,
      color = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
  }
}

/**
 * One line. Description on its own row so a Nepali item name has room, then quantity,
 * rate and the running amount side by side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineEditor(
  line: DraftLine,
  canRemove: Boolean,
  suggestions: kotlinx.coroutines.flow.Flow<List<np.bill.data.db.ItemEntity>>,
  onChange: ((DraftLine) -> DraftLine) -> Unit,
  onRemove: () -> Unit,
  onPick: () -> Unit,
  onUse: (np.bill.data.db.ItemEntity) -> Unit,
  onCalculate: () -> Unit,
) {
  val input = line.toInput()
  val amount = input?.let { it.quantityMilli * it.unitPricePaisa / 1000 } ?: 0L

  // What the shop already sells, matched against what is being typed. Only while the
  // line is still loose: once a product is on it the list has served its purpose, and
  // leaving it open pushes the rate field under the thumb about to reach for it.
  val matches by suggestions.collectAsStateWithLifecycle(emptyList())
  val offered = if (line.itemId == null && line.description.trim().length >= 2) {
    matches.take(4)
  } else {
    emptyList()
  }

  /**
   * What is in the amount box while it is being typed into.
   *
   * Null means the box shows quantity times rate, which is what will print. It is set
   * while the shopkeeper is typing an amount and cleared the moment they touch the
   * quantity or the rate, so whichever end they work from, the other follows.
   */
  var typed by remember(line.id) { mutableStateOf<String?>(null) }

  Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Field(
        value = line.description,
        onValueChange = { value -> onChange { it.copy(description = value, itemId = null) } },
        label = stringResource(R.string.item_name),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        trailingIcon = {
          IconButton(onClick = onPick) {
            Icon(
              BillIcons.Package,
              contentDescription = stringResource(R.string.pick_product),
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        },
        modifier = Modifier.weight(1f),
      )
      if (canRemove) {
        IconButton(onClick = onRemove) {
          Icon(
            BillIcons.X,
            contentDescription = stringResource(R.string.remove),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    if (offered.isNotEmpty()) {
      Panel {
        for ((index, match) in offered.withIndex()) {
          if (index > 0) Hairline()
          Row(
            Modifier
              .fillMaxWidth()
              .clickable { onUse(match) }
              .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(match.name, style = MaterialTheme.typography.bodyLarge)
              match.stockThousandths?.let { stock ->
                Text(
                  if (stock <= 0) {
                    stringResource(R.string.stock_out)
                  } else {
                    stringResource(R.string.stock_left, formatQuantity(stock), match.unit)
                  },
                  style = MaterialTheme.typography.labelMedium,
                  color = if (stock <= 0) {
                    LocalTokens.current.warning
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                )
              }
            }
            Text(
              "Rs ${formatMoney(match.unitPricePaisa)}",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(Modifier.height(10.dp))
    }

    Row(verticalAlignment = Alignment.Top) {
      QuantityStepper(
        value = line.quantity,
        onValueChange = { value ->
          // Editing the quantity releases the amount field, so the amount follows the
          // kilos as readily as the kilos follow the amount.
          typed = null
          onChange { it.copy(quantity = value) }
        },
      )
      Spacer(Modifier.width(8.dp))
      CompactPicker(
        value = line.unit,
        options = np.bill.core.unit.Unit.codes,
        onPick = { unit -> onChange { it.copy(unit = unit) } },
        title = stringResource(R.string.unit),
        modifier = Modifier.width(88.dp),
      )
      Spacer(Modifier.width(8.dp))
      Field(
        value = line.rate,
        onValueChange = { value ->
          typed = null
          onChange { it.copy(rate = value) }
        },
        label = stringResource(R.string.rate),
        // Label-free like the two beside it: a floated label pushes its box down inside
        // the row, which is what left the rate sitting lower than the quantity.
        showLabel = false,
        placeholder = stringResource(R.string.rate),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.weight(1f),
      )
    }

    Row(verticalAlignment = Alignment.Top) {
      // Nothing is exempt from a tax nobody is charging. Hidden with the rest of VAT.
      if (np.bill.BuildConfig.VAT_ENABLED) {
        ChoiceChip(
          text = stringResource(R.string.vat_exempt),
          selected = !line.vatApplicable,
          onClick = { onChange { it.copy(vatApplicable = !it.vatApplicable) } },
        )
        Spacer(Modifier.width(8.dp))
      }

      /**
       * The amount, and it works backwards.
       *
       * "Give me two hundred rupees of masu" is how a counter actually works, so typing
       * 200 against a rate of 1300 a kilo sets the quantity to 0.154 rather than making
       * the shopkeeper do the division. With no rate yet but a quantity on the line it
       * goes the other way and sets the rate, which is the "250 for three of these"
       * case.
       *
       * The quantity stays the source of truth: what prints is always quantity times
       * rate, so the field snaps to the nearest achievable amount when it loses focus
       * rather than promising a total the line cannot add up to.
       */
      Field(
        value = typed ?: formatMoney(amount),
        onValueChange = { value ->
          typed = value
          // Commas come from the formatted value the field was showing a moment ago,
          // and from anything pasted in. They are not part of a number.
          val wanted = np.bill.core.money.parsePaisa(value.replace(",", "")) ?: return@Field
          val rate = np.bill.core.money.parsePaisa(line.rate)
          val quantity = np.bill.core.money.parseQuantityMilli(line.quantity)

          when {
            // Rounded, not truncated. Truncating 100 at 1300 a kilo printed 98.80,
            // which is a rupee and twenty off what the customer asked for; rounding
            // lands on the nearest amount the line can actually add up to.
            rate != null && rate > 0 -> onChange {
              it.copy(
                quantity = np.bill.core.money.formatQuantity(
                  np.bill.core.money.roundQuantityMilli((wanted * 1000 + rate / 2) / rate),
                ),
              )
            }
            quantity != null && quantity > 0 -> onChange {
              it.copy(
                rate = np.bill.core.money.paisaToInput(
                  (wanted * 1000 + quantity / 2) / quantity,
                ),
              )
            }
            else -> Unit
          }
        },
        label = stringResource(R.string.amount),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.titleLarge,
        placeholder = formatMoney(amount),
        modifier = Modifier
          .weight(1f)
          .onFocusChanged { focus ->
            // Clears on the way in, because "Rs 200 of masu" is typed from scratch and
            // appending 200 to a formatted 1,300.00 is what everybody actually did.
            // Snaps back on the way out to what will really print, which is quantity
            // times rate and not always the round number that was asked for.
            typed = if (focus.isFocused) "" else null
          },
      )

      Spacer(Modifier.width(4.dp))
      IconButton(onClick = onCalculate, modifier = Modifier.padding(top = 6.dp)) {
        Icon(
          BillIcons.Calculator,
          contentDescription = stringResource(R.string.calculator),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** The camera, in a sheet, for a packet barcode or a customer's card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanSheet(
  mode: ScanMode,
  onScanned: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Text(
      stringResource(
        if (mode == ScanMode.PRODUCT) R.string.scan_product_hint else R.string.scan_hint,
      ),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Box(Modifier.fillMaxWidth().height(360.dp)) {
      CodeScanner(
        target = if (mode == ScanMode.PRODUCT) ScanTarget.PRODUCT else ScanTarget.QR,
        onScanned = onScanned,
      )
    }
    Spacer(Modifier.height(24.dp))
  }
}

/**
 * What the shop sells.
 *
 * Ticking several and adding them in one go is the common case — a customer puts four
 * things on the counter, not one — so the sheet stays open until it is told to add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductPickerSheet(
  viewModel: BillingViewModel,
  onPickMany: (List<np.bill.data.db.ItemEntity>) -> Unit,
  onCreate: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val items by viewModel.itemSuggestions(query)
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val picked = remember { mutableStateMapOf<String, np.bill.data.db.ItemEntity>() }

  PickerSheet(
    title = stringResource(R.string.pick_product),
    query = query,
    onQuery = { query = it },
    onDismiss = onDismiss,
    emptyAction = {
      // Searching for something the shop does not sell yet is the moment to add it,
      // not a dead end that sends someone to another tab and back.
      val exact = items.any { it.name.equals(query.trim(), ignoreCase = true) }
      if (query.isNotBlank() && !exact) {
        AddNewRow(
          label = stringResource(R.string.add_named_product, query.trim()),
          onClick = { onCreate(query.trim()) },
        )
      }
    },
    footer = {
      PrimaryButton(
        text = stringResource(R.string.add_selected, picked.size),
        onClick = { onPickMany(picked.values.toList()) },
        enabled = picked.isNotEmpty(),
      )
    },
  ) {
    items(items, key = { it.id }, contentType = { "item" }) { item ->
      val checked = picked.containsKey(item.id)
      Row(
        Modifier
          .fillMaxWidth()
          .clickable {
            if (checked) picked.remove(item.id) else picked[item.id] = item
          }
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = checked,
          onCheckedChange = { if (checked) picked.remove(item.id) else picked[item.id] = item },
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
          Text(item.name, style = MaterialTheme.typography.bodyLarge)
          Text(
            "per ${item.unit}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(formatMoney(item.unitPricePaisa), style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}

/** Regulars, so a repeat customer is one tap rather than three fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerPickerSheet(
  viewModel: BillingViewModel,
  onPick: (np.bill.data.db.CustomerEntity) -> Unit,
  onCreate: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val customers by viewModel.customerSuggestions(query)
    .collectAsStateWithLifecycle(initialValue = emptyList())

  PickerSheet(
    title = stringResource(R.string.search_customers),
    query = query,
    onQuery = { query = it },
    onDismiss = onDismiss,
    emptyAction = {
      val exact = customers.any { it.name.equals(query.trim(), ignoreCase = true) }
      if (query.isNotBlank() && !exact) {
        AddNewRow(
          label = stringResource(R.string.use_named_buyer, query.trim()),
          onClick = { onCreate(query.trim()) },
        )
      }
    },
  ) {
    items(customers, key = { it.id }) { customer ->
      Column(
        Modifier
          .fillMaxWidth()
          .clickable { onPick(customer) }
          .padding(vertical = 12.dp),
      ) {
        Text(customer.name, style = MaterialTheme.typography.bodyLarge)
        customer.phone?.let {
          Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

/**
 * A sheet that does not fight the keyboard.
 *
 * The old one let the list grow until the search box and the action button were both
 * under the keyboard, which is the "covering other inputs" problem: you could type but
 * not see what you were typing into, and not reach the button that used it.
 *
 * So it is a fixed frame instead. The search stays pinned at the top, the action stays
 * pinned above the keyboard, and only the list between them scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
  title: String,
  query: String,
  onQuery: (String) -> Unit,
  onDismiss: () -> Unit,
  footer: (@Composable () -> Unit)? = null,
  emptyAction: (@Composable () -> Unit)? = null,
  content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    // The sheet owns the keyboard inset; Compose would otherwise pad it twice.
    contentWindowInsets = { WindowInsets(0) },
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        // A fraction of the screen rather than "as tall as the content": a shop with two
        // products and a shop with two hundred should get the same shaped sheet.
        .fillMaxHeight(0.82f)
        .imePadding(),
    ) {
      Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
      )

      OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.search)) },
        leadingIcon = { Icon(BillIcons.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(Radius.large),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )

      emptyAction?.let {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { it() }
      }

      Spacer(Modifier.height(4.dp))
      Hairline()

      LazyColumn(
        Modifier.weight(1f).padding(horizontal = 16.dp),
        content = content,
      )

      footer?.let {
        Hairline()
        Box(
          Modifier
            .padding(16.dp)
            .navigationBarsPadding(),
        ) { it() }
      }
    }
  }
}

/**
 * "Not on the list? Add it."
 *
 * A dashed outline rather than a filled button: it is an offer, not the thing the sheet
 * is for, and it should not compete with the rows underneath it.
 */
@Composable
private fun AddNewRow(label: String, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.large))
      .border(
        1.dp,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        RoundedCornerShape(Radius.large),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      BillIcons.Plus,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(10.dp))
    Text(
      label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.primary,
      maxLines = 1,
      overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
  }
}
