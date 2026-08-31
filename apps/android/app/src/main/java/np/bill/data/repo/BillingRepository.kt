package np.bill.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import np.bill.core.invoice.ABBREVIATED_INVOICE_LIMIT_PAISA
import np.bill.core.invoice.LineInput
import np.bill.core.invoice.computeInvoice
import np.bill.core.invoice.formatInvoiceNumber
import np.bill.core.money.amountInWords
import np.bill.core.nepali.BsCalendar
import np.bill.data.db.BillDao
import np.bill.data.db.BillEntity
import np.bill.data.db.BillLineEntity
import np.bill.data.db.LeaseDao
import np.bill.data.prefs.SessionStore

/**
 * Writing a bill.
 *
 * The bill is finished here — number, miti, totals, words — before anything touches the
 * network, because the customer is waiting at the counter and the paper has to be right
 * whether or not there is a signal. Sync happens afterwards and changes nothing about
 * what was printed.
 */
@Singleton
class BillingRepository @Inject constructor(
  private val bills: BillDao,
  private val leases: LeaseDao,
  private val session: SessionStore,
) {

  /** Why a bill could not be written. Each one is something the shopkeeper can act on. */
  sealed interface Failure {
    data object NoLines : Failure
    data object ZeroTotal : Failure
    data object AbbreviatedLimit : Failure

    /** The device has printed every number it was leased and has to reach the server. */
    data object OutOfNumbers : Failure
  }

  sealed interface Result {
    data class Written(val bill: BillEntity) : Result
    data class Refused(val reason: Failure) : Result
  }

  data class Draft(
    val invoiceType: String = "tax_invoice",
    val buyerName: String,
    val buyerPan: String? = null,
    val buyerAddress: String? = null,
    val buyerPhone: String? = null,
    val customerId: String? = null,
    val paymentMethod: String = "cash",
    val notes: String? = null,
    val discountPaisa: Long = 0,
    /** What was handed over. Null means the whole bill was settled at the counter. */
    val paidAtIssuePaisa: Long? = null,
    val dueMiti: String? = null,
    /** From a scanned customer card, so the bill lands in that shopper's own app. */
    val shopperLink: String? = null,
    val lines: List<LineInput>,
  )

  suspend fun write(draft: Draft, now: Long = System.currentTimeMillis()): Result {
    if (draft.lines.isEmpty()) return Result.Refused(Failure.NoLines)

    val current = session.current()
    // A PAN-only taxpayer charges no VAT, whatever the form was showing.
    val vatRateBp = current.vatRateBp
    val totals = computeInvoice(draft.lines, draft.discountPaisa, vatRateBp)

    if (totals.totalPaisa <= 0) return Result.Refused(Failure.ZeroTotal)
    if (draft.invoiceType == "abbreviated_tax_invoice" &&
      totals.totalPaisa > ABBREVIATED_INVOICE_LIMIT_PAISA
    ) {
      return Result.Refused(Failure.AbbreviatedLimit)
    }

    val bs = BsCalendar.toBs(now)
    val fiscalYear = BsCalendar.fiscalYearFor(bs)
    val (lease, sequence) = leases.takeNext(fiscalYear, draft.invoiceType, now)
      ?: return Result.Refused(Failure.OutOfNumbers)

    val id = UUID.randomUUID().toString()
    val bill = BillEntity(
      id = id,
      // The QR has to print offline, so its token is minted here too.
      shareToken = UUID.randomUUID().toString().replace("-", ""),
      invoiceNumber = formatInvoiceNumber(
        prefix = current.store?.invoicePrefix.orEmpty(),
        fiscalYear = fiscalYear,
        invoiceType = draft.invoiceType,
        sequence = sequence,
      ),
      fiscalYear = fiscalYear,
      invoiceType = draft.invoiceType,
      sequence = sequence,
      leaseId = lease.id,
      buyerName = draft.buyerName,
      buyerPan = draft.buyerPan,
      buyerAddress = draft.buyerAddress,
      buyerPhone = draft.buyerPhone,
      customerId = draft.customerId,
      issuedAt = now,
      miti = bs.toString(),
      subTotalPaisa = totals.subTotalPaisa,
      discountPaisa = totals.discountPaisa,
      taxableAmountPaisa = totals.taxableAmountPaisa,
      nonTaxableAmountPaisa = totals.nonTaxableAmountPaisa,
      vatRateBp = vatRateBp,
      vatAmountPaisa = totals.vatAmountPaisa,
      totalPaisa = totals.totalPaisa,
      amountInWords = amountInWords(totals.totalPaisa),
      paymentMethod = draft.paymentMethod,
      paidAtIssuePaisa = (draft.paidAtIssuePaisa ?: totals.totalPaisa)
        .coerceIn(0, totals.totalPaisa),
      dueMiti = draft.dueMiti,
      shopperLink = draft.shopperLink,
      notes = draft.notes,
    )

    val lines = totals.lines.map { line ->
      BillLineEntity(
        billId = id,
        lineNo = line.lineNo,
        itemId = line.input.itemId,
        description = line.input.description,
        hsCode = line.input.hsCode,
        unit = line.input.unit,
        quantityMilli = line.input.quantityMilli,
        unitPricePaisa = line.input.unitPricePaisa,
        discountPaisa = line.input.discountPaisa,
        vatApplicable = line.input.vatApplicable,
        lineTotalPaisa = line.lineTotalPaisa,
      )
    }

    bills.insertBill(bill, lines)
    return Result.Written(bill)
  }

  fun recent(): Flow<List<BillEntity>> = bills.recent()

  fun search(term: String): Flow<List<BillEntity>> = bills.search(term)

  fun observe(id: String): Flow<BillEntity?> = bills.observe(id)

  fun observeLines(id: String): Flow<List<BillLineEntity>> = bills.observeLines(id)

  suspend fun load(id: String): Pair<BillEntity, List<BillLineEntity>>? {
    val bill = bills.byId(id) ?: return null
    return bill to bills.linesOf(id)
  }

  fun pendingCount(): Flow<Int> = bills.pendingCount()

  fun numbersLeft(fiscalYear: String, invoiceType: String = "tax_invoice"): Flow<Int> =
    leases.remaining(fiscalYear, invoiceType, System.currentTimeMillis())

  suspend fun countPrint(id: String) = bills.countPrint(id)

  // -- money owed -------------------------------------------------------------------

  fun outstanding(): Flow<List<np.bill.data.db.BillWithDue>> = bills.outstanding()

  fun totalOutstanding(): Flow<Long> = bills.totalOutstanding()

  fun paymentsOf(billId: String): Flow<List<np.bill.data.db.PaymentEntity>> =
    bills.paymentsOf(billId)

  /** What is still owed on a bill, as the device knows it. */
  suspend fun dueOn(billId: String): Long {
    val bill = bills.byId(billId) ?: return 0
    if (bill.status == "cancelled") return 0
    return bill.totalPaisa - bill.paidAtIssuePaisa - bills.paidSince(billId)
  }

  /**
   * Takes money against a bill. Written locally with its own id so the push is idempotent,
   * and capped at what is actually owed so a shop cannot record itself into a credit.
   */
  suspend fun recordPayment(
    billId: String,
    amountPaisa: Long,
    method: String = "cash",
    note: String? = null,
    now: Long = System.currentTimeMillis(),
  ): np.bill.data.db.PaymentEntity? {
    val owed = dueOn(billId)
    if (owed <= 0 || amountPaisa <= 0) return null

    val payment = np.bill.data.db.PaymentEntity(
      id = UUID.randomUUID().toString(),
      billId = billId,
      amountPaisa = minOf(amountPaisa, owed),
      method = method,
      receivedAt = now,
      miti = BsCalendar.toBs(now).toString(),
      note = note,
    )
    bills.savePayment(payment)
    return payment
  }

  fun forFiscalYear(fiscalYear: String): Flow<List<BillEntity>> = bills.forFiscalYear(fiscalYear)

  /**
   * Cancels a bill on this device and queues the cancellation. The row is kept and marked
   * cancelled, never deleted: an issued bill is a permanent record even when it is void.
   */
  suspend fun cancel(id: String, reason: String) = bills.cancelLocally(id, reason)
}
