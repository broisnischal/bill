package np.bill.ui.catalog

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import np.bill.ui.common.InitialTile
import np.bill.ui.common.Hairline
import np.bill.data.db.CustomerEntity
import np.bill.device.Contacts
import np.bill.ui.common.ActionSheet
import np.bill.ui.common.EmptyState
import np.bill.ui.common.Notice
import np.bill.ui.common.BottomAction
import np.bill.ui.common.Field
import np.bill.ui.common.FormSheet
import np.bill.ui.common.PrimaryButton
import np.bill.ui.theme.Radius
import np.bill.ui.common.SearchBar
import np.bill.ui.common.RomanizedField
import np.bill.ui.common.SecondaryButton

/**
 * Who the shop sells to.
 *
 * Most of a shopkeeper's regulars are already in their phone, so the fastest way to build
 * this list is to import rather than retype. Nothing leaves the device until a specific
 * person is picked: the importer reads contacts, shows them, and files only what was
 * ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
  addRequested: Boolean = false,
  onAddHandled: () -> Unit = {},
  onBillFor: (String) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: CatalogViewModel = hiltViewModel(),
) {
  val customers by viewModel.customers.collectAsStateWithLifecycle()
  val imported by viewModel.imported.collectAsStateWithLifecycle()
  var query by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf(false) }
  var importing by remember { mutableStateOf(false) }
  var chosen by remember { mutableStateOf<CustomerEntity?>(null) }
  val context = LocalContext.current

  var contactsGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  val contactsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    contactsGranted = granted
    if (granted) {
      viewModel.loadContacts()
      importing = true
    }
  }

  // The add button lives in the shell, where it stays clear of the navigation bar.
  LaunchedEffect(addRequested) {
    if (addRequested) {
      viewModel.editCustomer(null)
      editing = true
      onAddHandled()
    }
  }

  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      imported?.let { count ->
        Notice(stringResource(R.string.contacts_imported, count))
      }

      SearchBar(
        value = query,
        onValueChange = {
          query = it
          viewModel.searchCustomers(it)
        },
        placeholder = stringResource(R.string.search_customers),
        trailing = {
          IconButton(
            onClick = {
              viewModel.clearImported()
              if (contactsGranted) {
                viewModel.loadContacts()
                importing = true
              } else {
                contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
              }
            },
          ) {
            Icon(
              BillIcons.ContactRound,
              contentDescription = stringResource(R.string.import_contacts),
            )
          }
        },
      )

      // The same white ground the other lists stand on, with a rounded head so rows sit
      // on a surface rather than loose on the page.
      Box(
        Modifier
          .weight(1f)
          .padding(top = 4.dp)
          .clip(RoundedCornerShape(topStart = Radius.card, topEnd = Radius.card))
          .background(MaterialTheme.colorScheme.surface),
      ) {
      if (customers.isEmpty()) {
        EmptyState(stringResource(R.string.no_customers_yet))
      } else {
        LazyColumn(Modifier.fillMaxSize()) {
          items(customers, key = { it.id }, contentType = { "customer" }) { customer ->
            CustomerRow(customer = customer, onClick = { chosen = customer })
            Hairline(Modifier.padding(start = 68.dp))
          }
        }
      }
      }

      BottomAction(
        text = stringResource(R.string.add_customer),
        onClick = {
          viewModel.editCustomer(null)
          editing = true
        },
      )
    }
  }

  // Billing a regular is why their name is on this list, so it is the first option.
  chosen?.let { customer ->
    ActionSheet(
      title = customer.name,
      subtitle = customer.phone,
      primary = stringResource(R.string.bill_this_customer) to {
        onBillFor(customer.id)
        chosen = null
      },
      secondary = stringResource(R.string.edit_customer) to {
        viewModel.editCustomer(customer)
        editing = true
        chosen = null
      },
      onDismiss = { chosen = null },
    )
  }

  if (editing) {
    CustomerSheet(viewModel = viewModel, onDismiss = { editing = false })
  }

  if (importing) {
    ContactImportSheet(
      viewModel = viewModel,
      onDismiss = { importing = false },
    )
  }
}

@Composable
private fun CustomerRow(customer: CustomerEntity, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    InitialTile(customer.name)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(customer.name, style = MaterialTheme.typography.bodyLarge)
      Text(
        listOfNotNull(customer.phone, customer.pan?.let { "PAN $it" })
          .joinToString(" · ")
          .ifBlank { stringResource(R.string.no_phone) },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSheet(viewModel: CatalogViewModel, onDismiss: () -> Unit) {
  val form by viewModel.customerForm.collectAsStateWithLifecycle()
  FormSheet(
    title = stringResource(if (form.id == null) R.string.add_customer else R.string.edit_customer),
    onDismiss = onDismiss,
    heightFraction = 0.7f,
    action = {
      PrimaryButton(
        text = stringResource(R.string.save),
        onClick = { viewModel.saveCustomer(onDismiss) },
        enabled = form.valid,
      )
    },
  ) {
    RomanizedField(
      value = form.name,
      onValueChange = { value -> viewModel.onCustomerField { it.copy(name = value) } },
      label = stringResource(R.string.buyer_name),
      romanize = form.romanize,
      onToggleRomanize = { on -> viewModel.onCustomerField { it.copy(romanize = on) } },
    )

    Field(
      value = form.phone,
      onValueChange = { value ->
        viewModel.onCustomerField { it.copy(phone = value.filter(Char::isDigit).take(10)) }
      },
      label = stringResource(R.string.buyer_phone),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )

    Field(
      value = form.pan,
      onValueChange = { value ->
        viewModel.onCustomerField { it.copy(pan = value.filter(Char::isDigit).take(9)) }
      },
      label = stringResource(R.string.buyer_pan),
      error = if (form.pan.isNotEmpty() && form.pan.length != 9) {
        stringResource(R.string.pan_invalid)
      } else {
        null
      },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )

    Field(
      value = form.address,
      onValueChange = { value -> viewModel.onCustomerField { it.copy(address = value) } },
      label = stringResource(R.string.address),
    )
    Spacer(Modifier.height(8.dp))
  }
}

/** The phone's address book, with tick boxes. Only what is ticked is filed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactImportSheet(viewModel: CatalogViewModel, onDismiss: () -> Unit) {
  val contacts by viewModel.phoneContacts.collectAsStateWithLifecycle()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  // Keyed by phone number rather than held as a list: `contains` on a list is a scan, and
  // running one per visible row per frame is what made a long address book crawl.
  val picked = remember { mutableStateMapOf<String, Contacts.Entry>() }
  var search by remember { mutableStateOf("") }

  FormSheet(
    title = stringResource(R.string.import_contacts),
    subtitle = stringResource(R.string.import_contacts_detail),
    onDismiss = onDismiss,
    action = {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(
          text = stringResource(R.string.cancel),
          onClick = onDismiss,
          modifier = Modifier.weight(1f),
        )
        PrimaryButton(
          text = stringResource(R.string.import_selected, picked.size),
          onClick = {
            viewModel.importContacts(picked.values.toList())
            onDismiss()
          },
          enabled = picked.isNotEmpty(),
          modifier = Modifier.weight(1f),
        )
      }
    },
  ) {
    OutlinedTextField(
      value = search,
      onValueChange = {
        search = it
        viewModel.loadContacts(it)
      },
      placeholder = { Text(stringResource(R.string.search)) },
      leadingIcon = { Icon(BillIcons.Search, contentDescription = null) },
      singleLine = true,
      shape = androidx.compose.foundation.shape.RoundedCornerShape(np.bill.ui.theme.Radius.large),
      modifier = Modifier.fillMaxWidth(),
    )

    // A shop importing its address book wants all of it, not forty taps.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        checked = picked.size == contacts.size && contacts.isNotEmpty(),
        onCheckedChange = { checked ->
          picked.clear()
          if (checked) contacts.forEach { picked[it.phone] = it }
        },
      )
      Text(
        stringResource(R.string.select_all),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
      )
      Text(
        "${picked.size} / ${contacts.size}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Hairline()

    for (entry in contacts) {
      val checked = picked.containsKey(entry.phone)
      Row(
        Modifier
          .fillMaxWidth()
          .clickable {
            if (checked) picked.remove(entry.phone) else picked[entry.phone] = entry
          }
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = checked,
          onCheckedChange = {
            if (checked) picked.remove(entry.phone) else picked[entry.phone] = entry
          },
        )
        Column {
          Text(entry.name, style = MaterialTheme.typography.bodyLarge)
          Text(
            entry.phone,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}
