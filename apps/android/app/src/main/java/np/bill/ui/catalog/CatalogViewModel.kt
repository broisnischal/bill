package np.bill.ui.catalog

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.core.money.parsePaisa
import np.bill.data.db.CustomerEntity
import np.bill.data.db.ItemEntity
import np.bill.data.repo.CatalogRepository
import np.bill.data.sync.SyncWorker
import np.bill.device.Contacts

@Immutable
data class ItemFormState(
  val id: String? = null,
  val name: String = "",
  val price: String = "",
  val unit: String = "pcs",
  val hsCode: String = "",
  val barcode: String = "",
  val vatApplicable: Boolean = true,
  val romanize: Boolean = false,
) {
  val valid: Boolean get() = name.trim().isNotEmpty() && parsePaisa(price) != null
}

@Immutable
data class CustomerFormState(
  val id: String? = null,
  val name: String = "",
  val phone: String = "",
  val pan: String = "",
  val address: String = "",
  val romanize: Boolean = false,
) {
  val valid: Boolean get() = name.trim().isNotEmpty() && (pan.isEmpty() || pan.length == 9)
}

/**
 * The shop's products and buyers.
 *
 * Both lists are read straight out of the device database, so they open instantly and
 * search without a network. Adding either writes locally and hands the upload to the sync
 * worker, which is why a product can be created mid-sale on a phone with no bars.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
  private val catalog: CatalogRepository,
  private val application: Application,
) : ViewModel() {

  private val itemQuery = MutableStateFlow("")
  private val customerQuery = MutableStateFlow("")

  /**
   * Search waits for a pause in typing.
   *
   * Without it every keystroke ran a fresh LIKE query and rebuilt the whole list, which
   * on a shop with a few hundred products meant the list re-laid-out faster than anyone
   * could type into the box above it.
   */
  val items = itemQuery
    .debounce { if (it.isEmpty()) 0 else SEARCH_DEBOUNCE_MS }
    .distinctUntilChanged()
    .flatMapLatest(catalog::searchItems)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val customers = customerQuery
    .debounce { if (it.isEmpty()) 0 else SEARCH_DEBOUNCE_MS }
    .distinctUntilChanged()
    .flatMapLatest(catalog::searchCustomers)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun searchItems(term: String) { itemQuery.value = term }
  fun searchCustomers(term: String) { customerQuery.value = term }

  // -- product form ------------------------------------------------------------------

  private val _itemForm = MutableStateFlow(ItemFormState())
  val itemForm = _itemForm.asStateFlow()

  fun editItem(item: ItemEntity?) {
    _itemForm.value = item?.let {
      ItemFormState(
        id = it.id,
        name = it.name,
        price = np.bill.core.money.paisaToDecimalString(it.unitPricePaisa),
        unit = it.unit,
        hsCode = it.hsCode.orEmpty(),
        barcode = it.barcode.orEmpty(),
        vatApplicable = it.vatApplicable,
      )
    } ?: ItemFormState()
  }

  fun onItemField(transform: (ItemFormState) -> ItemFormState) = _itemForm.update(transform)

  /** A scanned barcode fills the field; if the shop already sells it, the form fills too. */
  fun onBarcodeScanned(code: String) {
    viewModelScope.launch {
      val known = catalog.itemByBarcode(code)
      if (known != null) editItem(known) else _itemForm.update { it.copy(barcode = code) }
    }
  }

  fun saveItem(onSaved: () -> Unit) {
    val form = _itemForm.value
    if (!form.valid) return
    viewModelScope.launch {
      catalog.saveItem(
        id = form.id,
        name = form.name,
        unitPricePaisa = parsePaisa(form.price) ?: 0,
        unit = form.unit,
        vatApplicable = form.vatApplicable,
        hsCode = form.hsCode.ifBlank { null },
        barcode = form.barcode.ifBlank { null },
      )
      SyncWorker.runNow(application)
      _itemForm.value = ItemFormState()
      onSaved()
    }
  }

  fun retireItem(id: String) {
    viewModelScope.launch {
      catalog.retireItem(id)
      SyncWorker.runNow(application)
    }
  }

  // -- customer form -----------------------------------------------------------------

  private val _customerForm = MutableStateFlow(CustomerFormState())
  val customerForm = _customerForm.asStateFlow()

  fun editCustomer(customer: CustomerEntity?) {
    _customerForm.value = customer?.let {
      CustomerFormState(
        id = it.id,
        name = it.name,
        phone = it.phone.orEmpty(),
        pan = it.pan.orEmpty(),
        address = it.address.orEmpty(),
      )
    } ?: CustomerFormState()
  }

  fun onCustomerField(transform: (CustomerFormState) -> CustomerFormState) =
    _customerForm.update(transform)

  fun saveCustomer(onSaved: () -> Unit) {
    val form = _customerForm.value
    if (!form.valid) return
    viewModelScope.launch {
      catalog.saveCustomer(
        id = form.id,
        name = form.name,
        phone = form.phone.ifBlank { null },
        pan = form.pan.ifBlank { null },
        address = form.address.ifBlank { null },
      )
      SyncWorker.runNow(application)
      _customerForm.value = CustomerFormState()
      onSaved()
    }
  }

  // -- the phone's address book -------------------------------------------------------

  private val _contacts = MutableStateFlow<List<Contacts.Entry>>(emptyList())
  val phoneContacts = _contacts.asStateFlow()

  private val _imported = MutableStateFlow<Int?>(null)
  val imported = _imported.asStateFlow()

  private var contactSearch: kotlinx.coroutines.Job? = null

  /**
   * Reads the phone's address book once and filters in memory afterwards.
   *
   * A contacts query is a content-provider round trip; running one per keystroke on a
   * phone holding a thousand contacts is what made the importer stutter.
   */
  fun loadContacts(query: String = "") {
    contactSearch?.cancel()
    contactSearch = viewModelScope.launch {
      if (allContacts.isEmpty()) allContacts = catalog.phoneContacts()
      _contacts.value = if (query.isBlank()) {
        allContacts
      } else {
        allContacts.filter { it.name.contains(query, ignoreCase = true) || it.phone.contains(query) }
      }
    }
  }

  private var allContacts: List<Contacts.Entry> = emptyList()

  fun importContacts(entries: List<Contacts.Entry>) {
    viewModelScope.launch {
      _imported.value = catalog.importContacts(entries)
      SyncWorker.runNow(application)
    }
  }

  fun clearImported() { _imported.value = null }

  private companion object {
    const val SEARCH_DEBOUNCE_MS = 180L
  }
}
