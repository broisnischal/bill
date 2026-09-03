package np.bill.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import np.bill.data.db.CatalogDao
import np.bill.data.db.CustomerEntity
import np.bill.data.db.ItemEntity
import np.bill.device.Contacts

/**
 * The shop's products and buyers.
 *
 * Everything is written to the device first with an id generated here, so adding a
 * product mid-sale never waits for a network. The same id is what the server files it
 * under when the next sync runs, which is what makes an upload idempotent.
 */
@Singleton
class CatalogRepository @Inject constructor(
  private val catalog: CatalogDao,
  private val contacts: Contacts,
) {

  // -- products --------------------------------------------------------------------

  fun items(): Flow<List<ItemEntity>> = catalog.items()

  fun searchItems(term: String): Flow<List<ItemEntity>> =
    if (term.isBlank()) catalog.items() else catalog.searchItems(term)

  fun itemCount(): Flow<Int> = catalog.itemCount()

  /** The product a scanned barcode belongs to, or null if the shop has not added it yet. */
  suspend fun itemByBarcode(barcode: String): ItemEntity? = catalog.itemByBarcode(barcode.trim())

  suspend fun saveItem(
    id: String? = null,
    name: String,
    unitPricePaisa: Long,
    unit: String = "pcs",
    vatApplicable: Boolean = true,
    hsCode: String? = null,
    barcode: String? = null,
    sku: String? = null,
    description: String? = null,
    stockThousandths: Long? = null,
    tags: List<String> = emptyList(),
  ): ItemEntity {
    /**
     * A name the shop already sells is that product, not a new one.
     *
     * Without this, a line typed by hand rather than picked from the list wrote a second
     * "Masu" on every bill, and the suggestion dropdown filled up with the same product
     * at the same price. Saving an existing name now updates it, which is also what a
     * shopkeeper correcting a rate expects.
     */
    val existing = id?.let { null } ?: catalog.itemByName(name.trim())

    val item = ItemEntity(
      id = id ?: existing?.id ?: UUID.randomUUID().toString(),
      name = name.trim(),
      description = description?.trim()?.ifBlank { null },
      hsCode = hsCode?.trim()?.ifBlank { null },
      sku = sku?.trim()?.ifBlank { null },
      barcode = barcode?.trim()?.ifBlank { null },
      unit = unit.trim().ifBlank { "pcs" },
      unitPricePaisa = unitPricePaisa,
      stockThousandths = stockThousandths,
      // Lowercased and de-duplicated on the way in, so "Rice" and "rice" are one label
      // rather than two that look identical in a list.
      tags = tags.map { it.trim().lowercase() }.filter(String::isNotEmpty).distinct().joinToString(","),
      vatApplicable = vatApplicable,
      active = true,
      updatedAt = System.currentTimeMillis(),
      pendingUpload = true,
    )
    catalog.upsertItems(listOf(item))
    return item
  }

  /**
   * Removes products that are the same product.
   *
   * Run after a sync pull, because the duplicates on a device were made before the check
   * above existed and the server's copy of them arrives with the rest of the catalogue.
   * The oldest row of each name is the one bills point at, so that is the one kept.
   */
  suspend fun mergeDuplicates() {
    val extras = catalog.duplicateItemIds()
    if (extras.isNotEmpty()) catalog.deleteItems(extras)
  }

  /** Takes a sold quantity off the shelf count. See `CatalogDao.reduceStock`. */
  suspend fun reduceStock(itemId: String, quantityMilli: Long) =
    catalog.reduceStock(itemId, quantityMilli)

  /**
   * Products are retired, not deleted: a bill issued last week still points at one, and
   * the line on that bill has to keep meaning something.
   */
  suspend fun retireItem(id: String) = catalog.deactivateItem(id)

  // -- customers -------------------------------------------------------------------

  fun customers(): Flow<List<CustomerEntity>> = catalog.customers()

  fun searchCustomers(term: String): Flow<List<CustomerEntity>> =
    if (term.isBlank()) catalog.customers() else catalog.searchCustomers(term)

  fun customerCount(): Flow<Int> = catalog.customerCount()

  suspend fun saveCustomer(
    id: String? = null,
    name: String,
    phone: String? = null,
    pan: String? = null,
    address: String? = null,
    email: String? = null,
  ): CustomerEntity {
    val cleanedPhone = phone?.filter(Char::isDigit)?.takeLast(10)?.ifBlank { null }

    // A number the shop already has is the same person, whatever the contact was called.
    val existing = cleanedPhone?.let { catalog.customerByPhone(it) }
    val customer = CustomerEntity(
      id = id ?: existing?.id ?: UUID.randomUUID().toString(),
      name = name.trim(),
      pan = pan?.trim()?.ifBlank { null },
      address = address?.trim()?.ifBlank { null },
      phone = cleanedPhone,
      email = email?.trim()?.ifBlank { null },
      updatedAt = System.currentTimeMillis(),
      pendingUpload = true,
    )
    catalog.upsertCustomers(listOf(customer))
    return customer
  }

  // -- the phone's own address book -------------------------------------------------

  suspend fun phoneContacts(query: String = ""): List<Contacts.Entry> = contacts.load(query)

  /** Files people picked out of the phone's contacts. Returns how many were new. */
  suspend fun importContacts(entries: List<Contacts.Entry>): Int {
    var added = 0
    for (entry in entries) {
      val existing = catalog.customerByPhone(entry.phone)
      if (existing != null) continue
      saveCustomer(name = entry.name, phone = entry.phone)
      added++
    }
    return added
  }
}
