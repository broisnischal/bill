package np.bill.ui.catalog

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.ui.common.IconTile
import np.bill.ui.common.TileTone
import np.bill.ui.common.Hairline
import np.bill.core.money.formatMoney
import np.bill.core.money.formatQuantity
import np.bill.data.db.ItemEntity
import np.bill.data.db.tagList
import np.bill.scan.CodeScanner
import np.bill.scan.ScanTarget
import np.bill.ui.common.ActionSheet
import np.bill.ui.common.ChoiceChip
import np.bill.ui.common.BottomFade
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
import np.bill.ui.theme.Radius
import np.bill.ui.theme.LocalTokens

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

      // The list gets its own white ground with a rounded head, so rows sit on a surface
      // rather than loose on the page.
      Box(
        Modifier
          .weight(1f)
          .padding(top = 4.dp)
          .clip(RoundedCornerShape(Radius.card))
          .background(MaterialTheme.colorScheme.surface),
      ) {
      if (items.isEmpty()) {
        EmptyState(stringResource(R.string.no_items_yet))
      } else {
        LazyColumn(
          Modifier.fillMaxSize(),
          // The list scrolls the height of the fade further, so the last row can come
          // out from under the ramp rather than sitting half dissolved forever.
          contentPadding = PaddingValues(bottom = 28.dp),
        ) {
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
            Hairline(Modifier.padding(start = 68.dp))
          }
        }
      }

      // The rows fade into the foot of the card rather than stopping at a straight
      // edge above the tab bar, which read as content someone had cut off.
      BottomFade()
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
      subtitle = "Rs ${formatMoney(item.unitPricePaisa)} per ${item.unit}",
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
    } else {
      IconTile(
        icon = BillIcons.Package,
        tone = if (item.stockThousandths != null && item.stockThousandths <= 0) {
          TileTone.NEGATIVE
        } else {
          TileTone.MINT
        },
      )
      Spacer(Modifier.width(12.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(item.name, style = MaterialTheme.typography.bodyLarge)
      Text(
        listOfNotNull(
          "per ${item.unit}",
          item.tagList.takeIf { it.isNotEmpty() }?.joinToString(" ") { "· $it" },
          if (!item.vatApplicable) "· exempt" else null,
        ).joinToString(" "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Column(horizontalAlignment = Alignment.End) {
      Text("Rs ${formatMoney(item.unitPricePaisa)}", style = MaterialTheme.typography.titleLarge)
      // Only for the products the shop counts. Out is said in words and in the warning
      // colour, because it is the one a shopkeeper needs to catch mid-sale.
      item.stockThousandths?.let { stock ->
        Text(
          if (stock <= 0) {
            stringResource(R.string.stock_out)
          } else {
            stringResource(R.string.stock_left, formatQuantity(stock), item.unit)
          },
          style = MaterialTheme.typography.labelMedium,
          color = if (stock <= 0) LocalTokens.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/**
 * The product form.
 *
 * Public because the home screen opens it too. Adding a product from home used to switch
 * to the Products tab first, which put a list nobody asked for behind the sheet and left
 * them on the wrong tab afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(
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

  val shopTags by viewModel.tags.collectAsStateWithLifecycle()
  var addingTag by remember { mutableStateOf(false) }
  var newTag by remember { mutableStateOf("") }
  // Everything past the name, the rate and the count is folded away. A shopkeeper adding
  // a product mid-sale fills three fields; the barcode and the HS code are for the
  // evening, and putting all six on screen at once made the quick job look like the slow
  // one.
  var showMore by remember { mutableStateOf(false) }

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

    Row(verticalAlignment = Alignment.Top) {
      Field(
        value = form.price,
        onValueChange = { value -> viewModel.onItemField { it.copy(price = value) } },
        label = stringResource(R.string.rate),
        prefix = {
          Text(
            "Rs ",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(12.dp))
      PickerField(
        value = form.unit,
        options = np.bill.core.unit.Unit.codes,
        onPick = { unit -> viewModel.onItemField { it.copy(unit = unit) } },
        label = stringResource(R.string.unit),
        modifier = Modifier.width(112.dp),
      )
    }

    Field(
      value = form.stock,
      onValueChange = { value -> viewModel.onItemField { it.copy(stock = value) } },
      label = stringResource(R.string.in_stock),
      hint = stringResource(R.string.in_stock_hint),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )

    Text(
      stringResource(R.string.labels),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      for (tag in (form.tags + shopTags).distinct()) {
        ChoiceChip(
          text = tag,
          selected = tag in form.tags,
          onClick = {
            viewModel.onItemField {
              it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
            }
          },
        )
      }
      ChoiceChip(
        text = stringResource(R.string.new_label),
        selected = false,
        onClick = { addingTag = !addingTag },
      )
    }

    if (addingTag) {
      Spacer(Modifier.height(10.dp))
      Field(
        value = newTag,
        onValueChange = { newTag = it },
        label = stringResource(R.string.new_label),
        trailingIcon = {
          IconButton(
            onClick = {
              val cleaned = newTag.trim().lowercase()
              if (cleaned.isNotEmpty()) {
                viewModel.onItemField { it.copy(tags = (it.tags + cleaned).distinct()) }
              }
              newTag = ""
              addingTag = false
            },
          ) {
            Icon(
              BillIcons.Plus,
              contentDescription = stringResource(R.string.new_label),
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        },
      )
    }

    Spacer(Modifier.height(6.dp))
    Hairline()
    Row(
      Modifier
        .fillMaxWidth()
        .clickable { showMore = !showMore }
        .padding(vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.more_details),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.weight(1f),
      )
      Icon(
        if (showMore) BillIcons.ChevronUp else BillIcons.ChevronDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (showMore) {
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
              BillIcons.ScanLine,
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

      if (np.bill.BuildConfig.VAT_ENABLED) {
        ChoiceChip(
          text = stringResource(R.string.vat_exempt),
          selected = !form.vatApplicable,
          onClick = { viewModel.onItemField { it.copy(vatApplicable = !it.vatApplicable) } },
        )
      }
    }
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
