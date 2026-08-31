package np.bill.ui.catalog

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.core.money.formatPaisa
import np.bill.data.db.ItemEntity
import np.bill.scan.CodeScanner
import np.bill.scan.ScanTarget
import np.bill.ui.common.ActionSheet
import np.bill.ui.common.EmptyState
import np.bill.ui.common.BottomAction
import np.bill.ui.common.Field
import np.bill.ui.common.FormSheet
import np.bill.ui.common.PickerField
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.SecondaryButton
import np.bill.ui.common.SearchBar
import np.bill.ui.common.SelectionBar
import np.bill.ui.common.RomanizedField

/**
 * What the shop sells.
 *
 * A product added here turns three taps on the bill screen into one, and a barcode turns
 * it into none: point the camera at the packet and the line is written. Prices are what
 * change most often, so they are on the row rather than behind an edit screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
  addRequested: Boolean = false,
  onAddHandled: () -> Unit = {},
  onBillWithItems: (List<String>) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: CatalogViewModel = hiltViewModel(),
) {
  val items by viewModel.items.collectAsStateWithLifecycle()
  var query by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf(false) }
  var chosen by remember { mutableStateOf<ItemEntity?>(null) }
  var scanningToAdd by remember { mutableStateOf(false) }
  // Keyed rather than listed: membership is checked once per visible row, per frame.
  val selected = remember { mutableStateMapOf<String, kotlin.Unit>() }
  val selecting = selected.isNotEmpty()

  // The add button lives in the shell, where it stays clear of the navigation bar.
  LaunchedEffect(addRequested) {
    if (addRequested) {
      viewModel.editItem(null)
      editing = true
      onAddHandled()
    }
  }

  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      SearchBar(
        value = query,
        onValueChange = {
          query = it
          viewModel.searchItems(it)
        },
        placeholder = stringResource(R.string.search_items),
      )

      if (items.isEmpty()) {
        Box(Modifier.weight(1f)) { EmptyState(stringResource(R.string.no_items_yet)) }
      } else {
        LazyColumn(Modifier.weight(1f)) {
          items(items, key = { it.id }, contentType = { "item" }) { item ->
            ItemRow(
              item = item,
              selecting = selecting,
              selected = selected.containsKey(item.id),
              onClick = {
                if (selecting) {
                  if (selected.remove(item.id) == null) selected[item.id] = kotlin.Unit
                } else {
                  chosen = item
                }
              },
              onLongClick = { selected[item.id] = kotlin.Unit },
            )
            Hairline(Modifier.padding(start = 16.dp))
          }
        }
      }

      BottomAction(
        text = stringResource(R.string.add_product),
        onClick = {
          viewModel.editItem(null)
          editing = true
        },
      )
    }

    // Appears only once something is ticked, so it costs nothing the rest of the time.
    SelectionBar(
      count = selected.size,
      actionLabel = stringResource(R.string.put_on_bill, selected.size),
      onAction = {
        onBillWithItems(selected.keys.toList())
        selected.clear()
      },
      onClear = selected::clear,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }

  if (editing) {
    ItemSheet(
      viewModel = viewModel,
      startScanning = scanningToAdd,
      onDismiss = {
        editing = false
        scanningToAdd = false
      },
    )
  }

  // Tapping a product asks what for, rather than assuming. Selling it is the common
  // answer and sits first; editing the price is the other one.
  chosen?.let { item ->
    ActionSheet(
      title = item.name,
      subtitle = "Rs ${formatPaisa(item.unitPricePaisa)} per ${item.unit}",
      primary = stringResource(R.string.sell_this) to {
        onBillWithItems(listOf(item.id))
        chosen = null
      },
      secondary = stringResource(R.string.edit_product) to {
        viewModel.editItem(item)
        editing = true
        chosen = null
      },
      onDismiss = { chosen = null },
    )
  }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ItemRow(
  item: ItemEntity,
  selecting: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (selecting) {
      Checkbox(checked = selected, onCheckedChange = { onClick() })
      Spacer(Modifier.width(4.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(item.name, style = MaterialTheme.typography.bodyLarge)
      Text(
        listOfNotNull(
          "per ${item.unit}",
          item.barcode?.let { "· $it" },
          if (!item.vatApplicable) "· exempt" else null,
        ).joinToString(" "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text("Rs ${formatPaisa(item.unitPricePaisa)}", style = MaterialTheme.typography.titleMedium)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(
  viewModel: CatalogViewModel,
  startScanning: Boolean = false,
  onDismiss: () -> Unit,
) {
  val form by viewModel.itemForm.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var scanning by remember { mutableStateOf(startScanning) }
  var torch by remember { mutableStateOf(false) }

  var cameraGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  val cameraLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    cameraGranted = granted
    scanning = granted
  }

  if (scanning) {
    ScanForBarcodeSheet(
      torch = torch,
      onTorch = { torch = !torch },
      onScanned = { code ->
        viewModel.onBarcodeScanned(code)
        scanning = false
      },
      onDismiss = { scanning = false },
    )
    return
  }

  FormSheet(
    title = stringResource(if (form.id == null) R.string.add_product else R.string.edit_product),
    onDismiss = onDismiss,
    action = {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        form.id?.let { id ->
          SecondaryButton(
            text = stringResource(R.string.remove),
            onClick = {
              viewModel.retireItem(id)
              onDismiss()
            },
            destructive = true,
            modifier = Modifier.weight(1f),
          )
        }
        PrimaryButton(
          text = stringResource(R.string.save),
          onClick = { viewModel.saveItem(onDismiss) },
          enabled = form.valid,
          modifier = Modifier.weight(1f),
        )
      }
    },
  ) {
    RomanizedField(
      value = form.name,
      onValueChange = { value -> viewModel.onItemField { it.copy(name = value) } },
      label = stringResource(R.string.item_name),
      romanize = form.romanize,
      onToggleRomanize = { on -> viewModel.onItemField { it.copy(romanize = on) } },
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      Field(
        value = form.price,
        onValueChange = { value -> viewModel.onItemField { it.copy(price = value) } },
        label = stringResource(R.string.rate),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(12.dp))
      PickerField(
        value = form.unit,
        options = np.bill.core.unit.Unit.codes,
        onPick = { unit -> viewModel.onItemField { it.copy(unit = unit) } },
        label = stringResource(R.string.unit),
        modifier = Modifier.width(118.dp),
      )
    }

    Spacer(Modifier.height(12.dp))
    Field(
      value = form.barcode,
      onValueChange = { value -> viewModel.onItemField { it.copy(barcode = value) } },
      label = stringResource(R.string.barcode),
      hint = stringResource(R.string.scan_to_add),
      trailingIcon = {
        IconButton(
          onClick = {
            if (cameraGranted) scanning = true else cameraLauncher.launch(Manifest.permission.CAMERA)
          },
        ) {
          Icon(
            Icons.Filled.QrCodeScanner,
            contentDescription = stringResource(R.string.scan_barcode),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      },
    )

    Field(
      value = form.hsCode,
      onValueChange = { value -> viewModel.onItemField { it.copy(hsCode = value) } },
      label = stringResource(R.string.hs_code),
    )

    FilterChip(
      selected = !form.vatApplicable,
      onClick = { viewModel.onItemField { it.copy(vatApplicable = !it.vatApplicable) } },
      label = { Text(stringResource(R.string.vat_exempt)) },
    )
    Spacer(Modifier.height(8.dp))
  }
}

/** The camera, for reading a barcode off a packet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanForBarcodeSheet(
  torch: Boolean,
  onTorch: () -> Unit,
  onScanned: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(
      topStart = np.bill.ui.theme.Radius.sheet,
      topEnd = np.bill.ui.theme.Radius.sheet,
    ),
  ) {
    Text(
      stringResource(R.string.scan_product_hint),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    Box(Modifier.fillMaxWidth().height(340.dp)) {
      CodeScanner(target = ScanTarget.PRODUCT, torch = torch, onScanned = onScanned)
    }
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
      TextButton(onClick = onTorch) { Text(stringResource(R.string.torch)) }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
    Spacer(Modifier.height(24.dp))
  }
}
