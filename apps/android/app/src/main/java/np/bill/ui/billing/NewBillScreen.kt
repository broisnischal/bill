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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import np.bill.scan.CodeScanner
import np.bill.scan.ScanTarget
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.core.money.formatPaisa
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import np.bill.ui.common.Notice
import np.bill.ui.common.PaymentChip
import np.bill.ui.common.BsDateField
import np.bill.ui.common.CalculatorSheet
import np.bill.ui.common.Field
import np.bill.ui.common.Hairline
import np.bill.ui.common.NoticeTone
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
  viewModel: BillingViewModel = hiltViewModel(),
) {
  val state by viewModel.newBill.collectAsStateWithLifecycle()
  val totals = state.totals
  val context = LocalContext.current

  var scanning by remember { mutableStateOf(ScanMode.NONE) }
  var pickingFor by remember { mutableStateOf<Long?>(null) }
  var pickingCustomer by remember { mutableStateOf(false) }
  var calculatingFor by remember { mutableStateOf<Long?>(null) }

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

  // Asking for the camera the moment a scan is wanted, not at startup.
  LaunchedEffect(scanning) {
    if (scanning != ScanMode.NONE && !cameraGranted) {
      cameraLauncher.launch(Manifest.permission.CAMERA)
      scanning = ScanMode.NONE
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.new_bill)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null)
          }
        },
        actions = {
          IconButton(onClick = { scanning = ScanMode.PRODUCT }) {
            Icon(
              Icons.Filled.QrCodeScanner,
              contentDescription = stringResource(R.string.scan_barcode),
            )
          }
          IconButton(onClick = { scanning = ScanMode.CUSTOMER }) {
            Icon(
              Icons.Filled.Badge,
              contentDescription = stringResource(R.string.scan_customer),
            )
          }
        },
      )
    },
    bottomBar = {
      // The total and the one action stay pinned: the shopkeeper never scrolls to finish.
      Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp)) {
          TotalsRow(
            label = stringResource(R.string.total),
            value = "Rs ${formatPaisa(totals.totalPaisa)}",
            emphasised = true,
          )
          Spacer(Modifier.height(12.dp))
          PrimaryButton(
            text = stringResource(R.string.save_bill),
            onClick = { viewModel.save(onDone) },
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
          onChange = handlers.onChange,
          onRemove = handlers.onRemove,
          onPick = { pickingFor = line.id },
          onCalculate = { calculatingFor = line.id },
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
      }

      item {
        TextButton(
          onClick = viewModel::addLine,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.add_item))
        }
      }

      item {
        Column(Modifier.padding(20.dp)) {
          Hairline()
          Spacer(Modifier.height(16.dp))

          TotalsRow(stringResource(R.string.sub_total), formatPaisa(totals.subTotalPaisa))
          if (totals.discountPaisa > 0) {
            TotalsRow(stringResource(R.string.discount), "-${formatPaisa(totals.discountPaisa)}")
          }
          if (totals.nonTaxableAmountPaisa > 0) {
            TotalsRow(stringResource(R.string.exempt), formatPaisa(totals.nonTaxableAmountPaisa))
          }
          if (state.vatRateBp > 0) {
            TotalsRow(stringResource(R.string.taxable), formatPaisa(totals.taxableAmountPaisa))
            TotalsRow(
              stringResource(R.string.vat_at, state.vatRateBp / 100),
              formatPaisa(totals.vatAmountPaisa),
            )
          }

          Spacer(Modifier.height(20.dp))
          OutlinedTextField(
            value = state.discount,
            onValueChange = viewModel::onDiscount,
            label = { Text(stringResource(R.string.discount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(Modifier.height(20.dp))
          OutlinedTextField(
            value = state.buyerName,
            onValueChange = viewModel::onBuyerName,
            label = { Text(stringResource(R.string.buyer_name)) },
            placeholder = { Text(stringResource(R.string.buyer_name_default)) },
            singleLine = true,
            trailingIcon = {
              IconButton(onClick = { pickingCustomer = true }) {
                Icon(
                  Icons.Filled.Groups,
                  contentDescription = stringResource(R.string.search_customers),
                )
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = state.buyerPhone,
            onValueChange = viewModel::onBuyerPhone,
            label = { Text(stringResource(R.string.buyer_phone)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = state.buyerPan,
            onValueChange = viewModel::onBuyerPan,
            label = { Text(stringResource(R.string.buyer_pan)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotes,
            label = { Text(stringResource(R.string.notes)) },
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(Modifier.height(20.dp))
          Text(stringResource(R.string.bill_type), style = MaterialTheme.typography.labelLarge)
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = state.invoiceType == "tax_invoice",
              onClick = { viewModel.onInvoiceType("tax_invoice") },
              label = { Text(stringResource(R.string.type_tax_invoice)) },
            )
            FilterChip(
              selected = state.invoiceType == "abbreviated_tax_invoice",
              onClick = { viewModel.onInvoiceType("abbreviated_tax_invoice") },
              label = { Text(stringResource(R.string.type_abbreviated)) },
            )
          }

          Spacer(Modifier.height(20.dp))
          Text(stringResource(R.string.how_paid), style = MaterialTheme.typography.labelLarge)
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceCard(
              label = stringResource(R.string.paid_full),
              selected = state.settlement == Settlement.PAID,
              onClick = { viewModel.onSettlement(Settlement.PAID) },
              modifier = Modifier.weight(1f),
            )
            ChoiceCard(
              label = stringResource(R.string.owed),
              selected = state.settlement == Settlement.OWED,
              onClick = { viewModel.onSettlement(Settlement.OWED) },
              modifier = Modifier.weight(1f),
            )
          }

          if (state.onCredit) {
            Spacer(Modifier.height(8.dp))
            Field(
              value = state.paidNow,
              onValueChange = viewModel::onPaidNow,
              label = stringResource(R.string.paid_now),
              placeholder = "0",
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              hint = stringResource(R.string.owes_now, formatPaisa(state.owedPaisa)),
            )
            BsDateField(
              value = state.dueMiti,
              onValueChange = viewModel::onDueMiti,
              label = stringResource(R.string.due_date),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
          }

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
                onClick = { viewModel.onPaymentMethod(method) },
              )
            }
          }
        }
      }
    }
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
  onChange: ((DraftLine) -> DraftLine) -> Unit,
  onRemove: () -> Unit,
  onPick: () -> Unit,
  onCalculate: () -> Unit,
) {
  val input = line.toInput()
  val amount = input?.let { it.quantityMilli * it.unitPricePaisa / 1000 } ?: 0L

  Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = line.description,
        onValueChange = { value -> onChange { it.copy(description = value) } },
        label = { Text(stringResource(R.string.item_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        trailingIcon = {
          IconButton(onClick = onPick) {
            Icon(
              Icons.Filled.Inventory2,
              contentDescription = stringResource(R.string.pick_product),
            )
          }
        },
        modifier = Modifier.weight(1f),
      )
      if (canRemove) {
        IconButton(onClick = onRemove) {
          Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
        }
      }
    }

    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = line.quantity,
        onValueChange = { value -> onChange { it.copy(quantity = value) } },
        label = { Text(stringResource(R.string.quantity)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.width(88.dp),
      )
      Spacer(Modifier.width(8.dp))
      PickerField(
        value = line.unit,
        options = np.bill.core.unit.Unit.codes,
        onPick = { unit -> onChange { it.copy(unit = unit) } },
        label = stringResource(R.string.unit),
        modifier = Modifier.width(104.dp),
      )
      Spacer(Modifier.width(8.dp))
      OutlinedTextField(
        value = line.rate,
        onValueChange = { value -> onChange { it.copy(rate = value) } },
        label = { Text(stringResource(R.string.rate)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        trailingIcon = {
          IconButton(onClick = onCalculate) {
            Icon(
              Icons.Filled.Calculate,
              contentDescription = stringResource(R.string.calculator),
            )
          }
        },
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      FilterChip(
        selected = !line.vatApplicable,
        onClick = { onChange { it.copy(vatApplicable = !it.vatApplicable) } },
        label = { Text(stringResource(R.string.vat_exempt)) },
      )
      Spacer(Modifier.weight(1f))
      Text(formatPaisa(amount), style = MaterialTheme.typography.titleMedium)
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
        Text(formatPaisa(item.unitPricePaisa), style = MaterialTheme.typography.titleMedium)
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
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
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
      Icons.Filled.Add,
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
