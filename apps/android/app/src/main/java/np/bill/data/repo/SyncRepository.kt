package np.bill.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import np.bill.core.nepali.BsCalendar
import np.bill.data.db.BillDao
import np.bill.data.db.BillEntity
import np.bill.data.db.CatalogDao
import np.bill.data.db.CustomerEntity
import np.bill.data.db.ItemEntity
import np.bill.data.db.LeaseDao
import np.bill.data.db.LeaseEntity
import np.bill.data.db.SyncState
import np.bill.data.db.tagList
import np.bill.data.net.ApiResult
import np.bill.data.net.BillApi
import np.bill.data.net.CancellationDto
import np.bill.data.net.CatalogUpsertRequest
import np.bill.data.net.CustomerUpsert
import np.bill.data.net.ItemUpsert
import np.bill.data.net.DeviceInvoiceDto
import np.bill.data.net.LineDto
import np.bill.data.net.PaymentDto
import np.bill.data.net.RegisterDeviceRequest
import np.bill.data.net.SyncRequest
import np.bill.data.net.apiCall
import np.bill.data.prefs.SessionStore
import np.bill.util.Iso8601

/**
 * One conversation with the server, run whenever there is a network.
 *
 * Bills that were printed offline go up, numbers and the catalogue come back, and every
 * part is idempotent, so a sync interrupted halfway simply runs again. Nothing here can
 * change a bill that has already been printed.
 */
@Singleton
class SyncRepository @Inject constructor(
  private val api: BillApi,
  private val bills: BillDao,
  private val leases: LeaseDao,
  private val catalog: CatalogDao,
  private val catalogRepo: CatalogRepository,
  private val session: SessionStore,
  private val json: kotlinx.serialization.json.Json,
) {

  data class Outcome(
    val filed: Int = 0,
    val rejected: Int = 0,
    val numbersLeft: Int = 0,
    val offline: Boolean = false,
    val error: String? = null,
  )

  suspend fun sync(): Outcome {
    val current = session.current()
    if (!current.signedIn || !current.hasStore) return Outcome()

    if (!current.deviceRegistered) {
      when (val registered = registerDevice()) {
        is ApiResult.Ok -> session.setDeviceRegistered(true)
        ApiResult.Offline -> return Outcome(offline = true)
        is ApiResult.Failed -> return Outcome(error = registered.message)
      }
    }

    // Products and buyers created on the till go first: a bill can reference one, and the
    // server has to know the id before it sees a line pointing at it.
    pushCatalog()

    val pending = bills.pending()
    val cancellations = bills.pendingCancellations()
    val now = System.currentTimeMillis()
    val fiscalYear = BsCalendar.fiscalYearFor(now)

    val duePayments = bills.pendingPayments()
    val autoSaveCustomer = current.autoSaveCustomer

    val request = SyncRequest(
      invoices = pending.map { toDto(it, bills.linesOf(it.id), autoSaveCustomer) },
      payments = duePayments.map {
        PaymentDto(
          id = it.id,
          invoiceId = it.billId,
          amountPaisa = it.amountPaisa,
          method = it.method,
          receivedAt = Iso8601.format(it.receivedAt),
          miti = it.miti,
          note = it.note,
        )
      },
      cancellations = cancellations.map {
        CancellationDto(it.id, it.cancelReason ?: "Cancelled on the till")
      },
      // Ask for enough numbers that a day's billing survives a day without signal.
      want = mapOf("tax_invoice" to 60, "abbreviated_tax_invoice" to 20),
      catalogSince = current.catalogCursor,
    )

    return when (val response = apiCall { api.sync(request) }) {
      ApiResult.Offline -> Outcome(offline = true)
      is ApiResult.Failed -> Outcome(error = response.message)
      is ApiResult.Ok -> {
        val body = response.value
        var filed = 0
        var rejected = 0

        for (result in body.results) {
          when (result.status) {
            "filed", "duplicate" -> {
              filed++
              bills.markSync(result.id, SyncState.SYNCED, null, now)
            }
            "rejected" -> {
              rejected++
              bills.markSync(result.id, SyncState.REJECTED, result.error?.message, now)
            }
            // "failed" is transient; the bill stays pending and goes again next time.
            else -> bills.markSync(result.id, SyncState.PENDING, result.error?.message, null)
          }
        }

        for (entry in body.cancellations) {
          if (entry.status == "cancelled") bills.clearCancelPending(entry.invoiceId)
        }

        for (entry in body.payments) {
          // "failed" leaves it pending so the next sync tries again.
          if (entry.status == "filed" || entry.status == "duplicate") {
            bills.markPaymentSync(entry.id, SyncState.SYNCED)
          }
        }

        leases.prune(now)
        leases.upsert(body.leases.map(::toLease))

        catalog.upsertItems(body.catalog.items.map(::toItem))
        // Anything the shop duplicated before names were checked collapses here.
        catalogRepo.mergeDuplicates()
        catalog.upsertCustomers(body.catalog.customers.map(::toCustomer))
        session.setCatalogCursor(body.serverTime)

        body.store?.let { session.setStore(it) }

        Outcome(
          filed = filed,
          rejected = rejected,
          numbersLeft = body.leases.sumOf { it.endSequence - maxOf(it.usedThrough, it.startSequence - 1) },
        )
      }
    }
  }

  /**
   * Uploads products and buyers added on this device. Each carries the id it was written
   * with, so the server files it under the same one and a repeat push changes nothing.
   */
  private suspend fun pushCatalog() {
    for (item in catalog.pendingItems()) {
      val upsert = ItemUpsert(
        id = item.id,
        name = item.name,
        description = item.description,
        hsCode = item.hsCode,
        sku = item.sku,
        barcode = item.barcode,
        unit = item.unit,
        unitPricePaisa = item.unitPricePaisa,
        stockThousandths = item.stockThousandths,
        tags = item.tagList,
        vatApplicable = item.vatApplicable,
        active = item.active,
      )
      val body = CatalogUpsertRequest("item", json.encodeToJsonElement(ItemUpsert.serializer(), upsert))
      if (apiCall { api.upsertCatalog(body) } is ApiResult.Ok) catalog.confirmItem(item.id, item.id)
    }

    for (customer in catalog.pendingCustomers()) {
      val upsert = CustomerUpsert(
        id = customer.id,
        name = customer.name,
        pan = customer.pan,
        address = customer.address,
        phone = customer.phone,
        email = customer.email,
      )
      val body = CatalogUpsertRequest("customer", json.encodeToJsonElement(CustomerUpsert.serializer(), upsert))
      if (apiCall { api.upsertCatalog(body) } is ApiResult.Ok) {
        catalog.confirmCustomer(customer.id, customer.id)
      }
    }
  }

  private suspend fun registerDevice(): ApiResult<*> {
    val deviceId = session.deviceId()
    return apiCall {
      api.registerDevice(
        RegisterDeviceRequest(
          id = deviceId,
          name = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
          appVersion = np.bill.BuildConfig.VERSION_NAME,
        ),
      )
    }
  }

  private fun toDto(
    bill: BillEntity,
    lines: List<np.bill.data.db.BillLineEntity>,
    autoSaveCustomer: Boolean,
  ) =
    DeviceInvoiceDto(
      id = bill.id,
      shareToken = bill.shareToken,
      leaseId = bill.leaseId,
      sequence = bill.sequence,
      fiscalYear = bill.fiscalYear,
      issuedAt = Iso8601.format(bill.issuedAt),
      queuedAt = Iso8601.format(bill.createdAt),
      totalPaisa = bill.totalPaisa,
      invoiceType = bill.invoiceType,
      customerId = bill.customerId,
      buyerName = bill.buyerName,
      buyerPan = bill.buyerPan,
      buyerAddress = bill.buyerAddress,
      buyerPhone = bill.buyerPhone,
      paymentMethod = bill.paymentMethod,
      notes = bill.notes,
      discountPaisa = bill.discountPaisa,
      // Honours the shop's setting rather than guessing from whether a phone was typed:
      // a regular who always pays cash and never gives a number was invisible to the old
      // rule, which is exactly the customer a shop most wants on the list.
      saveCustomer = bill.customerId == null && bill.buyerName.isNotBlank() && autoSaveCustomer,
      lines = lines.map {
        LineDto(
          itemId = it.itemId,
          description = it.description,
          hsCode = it.hsCode,
          unit = it.unit,
          quantityMilli = it.quantityMilli,
          unitPricePaisa = it.unitPricePaisa,
          discountPaisa = it.discountPaisa,
          vatApplicable = it.vatApplicable,
        )
      },
    )

  private fun toLease(dto: np.bill.data.net.LeaseDto) = LeaseEntity(
    id = dto.id,
    fiscalYear = dto.fiscalYear,
    invoiceType = dto.invoiceType,
    startSequence = dto.startSequence,
    endSequence = dto.endSequence,
    // The server's watermark wins: a number it has already seen is never printed again.
    nextSequence = maxOf(dto.usedThrough + 1, dto.startSequence),
    expiresAt = Iso8601.parse(dto.expiresAt) ?: (System.currentTimeMillis() + 86_400_000L),
  )

  private fun toItem(dto: np.bill.data.net.ItemDto) = ItemEntity(
    id = dto.id,
    name = dto.name,
    description = dto.description,
    hsCode = dto.hsCode,
    sku = dto.sku,
    barcode = dto.barcode,
    unit = dto.unit,
    unitPricePaisa = dto.unitPricePaisa,
    stockThousandths = dto.stockThousandths,
    tags = dto.tags.joinToString(","),
    vatApplicable = dto.vatApplicable,
    active = dto.active,
    updatedAt = Iso8601.parse(dto.updatedAt) ?: System.currentTimeMillis(),
  )

  private fun toCustomer(dto: np.bill.data.net.CustomerDto) = CustomerEntity(
    id = dto.id,
    name = dto.name,
    pan = dto.pan,
    address = dto.address,
    phone = dto.phone,
    email = dto.email,
    updatedAt = Iso8601.parse(dto.updatedAt) ?: System.currentTimeMillis(),
  )
}
