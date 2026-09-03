package np.bill.ui.billing

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import np.bill.core.invoice.InvoiceTotals
import np.bill.core.invoice.LineInput
import np.bill.core.invoice.computeInvoice
import np.bill.core.money.formatQuantity
import np.bill.core.money.parsePaisa
import np.bill.core.money.parseQuantityMilli
import np.bill.core.nepali.BsCalendar
import np.bill.data.db.BillEntity
import np.bill.data.db.ItemEntity
import np.bill.data.net.ApiResult
import np.bill.data.prefs.SessionStore
import np.bill.data.repo.BillingRepository
import np.bill.data.repo.CatalogRepository
import np.bill.data.sync.SyncWorker

@Immutable
data class HomeState(
  val storeName: String = "",
  val miti: String = "",
  val todayPaisa: Long = 0,
  val todayCount: Int = 0,
  /** The list after filters, which is what the screen draws. */
  val visible: List<BillEntity> = emptyList(),
  /** The newest few, whatever the Bills tab is filtered to. Home shows these. */
  val recent: List<BillEntity> = emptyList(),
  val pendingSync: Int = 0,
  val numbersLeft: Int = 0,
  val printerName: String? = null,
  val printerConnected: Boolean = false,
  val duePaisa: Long = 0,
  /** What the filtered list adds up to. Computed here, not while the screen is drawing. */
  val visiblePaisa: Long = 0,
) {
  val outOfNumbers: Boolean get() = numbersLeft == 0
}

/** A buyer this counter has billed before, as much of them as a bill records. */
@Immutable
data class RecentBuyer(
  val customerId: String?,
  val name: String,
  val phone: String?,
  val pan: String?,
)

@Immutable
data class BillFilters(
  val search: String = "",
  val status: BillStatusFilter = BillStatusFilter.ALL,
  val fromMiti: String? = null,
  val toMiti: String? = null,
) {
  val isActive: Boolean
    get() = search.isNotBlank() || status != BillStatusFilter.ALL ||
      fromMiti != null || toMiti != null
}

/** A line as the person is entering it, before it is worth turning into money. */
@Immutable
data class DraftLine(
  val id: Long,
  val description: String = "",
  val quantity: String = "1",
  val rate: String = "",
  val unit: String = "pcs",
  val vatApplicable: Boolean = true,
  val itemId: String? = null,
) {
  fun toInput(): LineInput? {
    val quantityMilli = parseQuantityMilli(quantity) ?: return null
    val unitPricePaisa = parsePaisa(rate) ?: return null
    if (description.isBlank() || quantityMilli <= 0) return null
    return LineInput(
      itemId = itemId,
      description = description.trim(),
      unit = unit,
      quantityMilli = quantityMilli,
      unitPricePaisa = unitPricePaisa,
      vatApplicable = vatApplicable,
    )
  }
}

/**
 * The bill being written.
 *
 * `totals` is a stored field rather than a computed property on purpose: Compose reads it
 * several times a frame, and totalling the lines on every read showed up as jank on the
 * line editor. It is recomputed once, in [recalculate], whenever an edit changes it.
 */
@Immutable
data class NewBillState(
  val buyerName: String = "",
  val buyerPhone: String = "",
  val buyerPan: String = "",
  val customerId: String? = null,
  val paymentMethod: String = "cash",
  val buyerAddress: String = "",
  val discount: String = "",
  val notes: String = "",
  val invoiceType: String = "tax_invoice",
  /**
   * Whether the money changed hands.
   *
   * Starts at PAID, because that is what almost every counter sale is and defaulting it
   * saves a tap on every single bill. The other card sits right beside it, so a credit
   * sale is still one tap — and choosing it is what puts the bill in the dues list.
   */
  val settlement: Settlement = Settlement.PAID,
  /** Signed handle from a scanned customer card. Sent with the bill, never shown. */
  val shopperLink: String? = null,
  /** What was handed over on a part payment. Blank means nothing yet. */
  val paidNow: String = "",
  val dueMiti: String = "",
  val lines: List<DraftLine> = listOf(DraftLine(id = 1)),
  val vatRateBp: Int = 1300,
  /** The shop, for the copy shown before the bill is made. */
  val storeName: String = "",
  val storePan: String = "",
  val miti: String = "",
  val totals: InvoiceTotals = computeInvoice(emptyList()),
  val saving: Boolean = false,
  val error: String? = null,
) {
  val onCredit: Boolean get() = settlement == Settlement.OWED

  /**
   * A bill needs a buyer. It is on the paper, it is what the shop searches its own
   * history by, and "Cash customer" on every second bill is the same as no record.
   */
  val canSave: Boolean get() = totals.totalPaisa > 0 && buyerName.isNotBlank() && !saving

  /** What is still owed once this bill is written, in paisa. */
  val owedPaisa: Long
    get() = if (settlement == Settlement.OWED) {
      (totals.totalPaisa - (parsePaisa(paidNow) ?: 0)).coerceIn(0, totals.totalPaisa)
    } else {
      0
    }
}

/** The two answers to "was this paid?". */
enum class Settlement { PAID, OWED }

/** Callbacks belonging to one line, held so they stay the same object between frames. */
@androidx.compose.runtime.Stable
class LineHandlers(
  val onChange: ((DraftLine) -> DraftLine) -> Unit,
  val onRemove: () -> Unit,
)

private fun NewBillState.recalculate(): NewBillState = copy(
  totals = computeInvoice(lines.mapNotNull(DraftLine::toInput), parsePaisa(discount) ?: 0, vatRateBp),
)

@HiltViewModel
class BillingViewModel @Inject constructor(
  private val billing: BillingRepository,
  private val catalog: CatalogRepository,
  private val profiles: np.bill.data.repo.ProfileRepository,
  private val paymentQr: np.bill.data.repo.PaymentQrRepository,
  private val templateRepo: np.bill.data.repo.TemplateRepository,
  private val sync: np.bill.data.repo.SyncRepository,
  private val session: SessionStore,
  private val application: Application,
) : ViewModel() {

  private val fiscalYear = BsCalendar.fiscalYearFor(System.currentTimeMillis())
  private val miti = BsCalendar.toBs(System.currentTimeMillis()).toString()

  private val _filters = MutableStateFlow(BillFilters())
  val filters = _filters.asStateFlow()

  fun onSearch(value: String) = _filters.update { it.copy(search = value) }
  fun onStatusFilter(value: BillStatusFilter) = _filters.update { it.copy(status = value) }
  fun onFromMiti(value: String) = _filters.update { it.copy(fromMiti = value.ifBlank { null }) }
  fun onToMiti(value: String) = _filters.update { it.copy(toMiti = value.ifBlank { null }) }
  fun clearFilters() = _filters.update { BillFilters() }

  /**
   * Today's takings come out of the same list the screen is already showing rather than
   * a second query, so the screen holds one subscription to the database. Filtering
   * happens here too: the list is capped at a couple of hundred rows, and a WHERE clause
   * per keystroke would cost more than filtering what is already in memory.
   */
  val home: kotlinx.coroutines.flow.StateFlow<HomeState> = combine(
    session.session,
    billing.recent(),
    billing.pendingCount(),
    billing.numbersLeft(fiscalYear),
    _filters,
  ) { current, recent, pending, numbers, filters ->
    val today = recent.filter { it.miti == miti && it.status == "active" }
    val visible = recent.filter { filters.matches(it) }
    HomeState(
      storeName = current.store?.name.orEmpty(),
      miti = miti,
      todayPaisa = today.sumOf { it.totalPaisa },
      todayCount = today.size,
      visible = visible,
      recent = recent.take(5),
      pendingSync = pending,
      numbersLeft = numbers,
      visiblePaisa = visible.filter { it.status == "active" }.sumOf { it.totalPaisa },
      printerName = current.printerName,
      printerConnected = current.printerAddress != null,
    )
  }
    // Chained rather than a sixth argument: combine only has typed overloads to five.
    .combine(billing.totalOutstanding()) { state, due -> state.copy(duePaisa = due) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(miti = miti))

  // -- New bill ------------------------------------------------------------------

  private val _newBill = MutableStateFlow(NewBillState())
  val newBill = _newBill.asStateFlow()

  private var nextLineId = 2L

  /** One number request at a time. The banner recomposes more often than it should. */
  private var fetching = false

  init {
    viewModelScope.launch {
      val current = session.current()
      _newBill.update {
        it.copy(
          vatRateBp = current.vatRateBp,
          storeName = current.store?.name.orEmpty(),
          storePan = current.store?.pan.orEmpty(),
          miti = miti,
        ).recalculate()
      }
    }
  }

  fun onBuyerName(value: String) = _newBill.update { it.copy(buyerName = value, error = null) }
  fun onBuyerPhone(value: String) = _newBill.update { it.copy(buyerPhone = value.filter(Char::isDigit).take(10)) }
  fun onBuyerPan(value: String) = _newBill.update { it.copy(buyerPan = value.filter(Char::isDigit).take(9)) }
  fun onPaymentMethod(value: String) = _newBill.update { it.copy(paymentMethod = value) }
  fun onDiscount(value: String) = _newBill.update { it.copy(discount = value).recalculate() }
  fun onNotes(value: String) = _newBill.update { it.copy(notes = value) }

  /**
   * Switching to credit clears what was paid, so a bill does not quietly carry a part
   * payment nobody typed. Switching back settles it in full again.
   */
  fun onSettlement(value: Settlement) = _newBill.update {
    it.copy(
      settlement = value,
      paidNow = "",
      paymentMethod = if (value == Settlement.OWED) "credit" else "cash",
      error = null,
    )
  }

  fun onPaidNow(value: String) = _newBill.update { it.copy(paidNow = value) }
  fun onDueMiti(value: String) = _newBill.update { it.copy(dueMiti = value) }
  fun onInvoiceType(value: String) = _newBill.update { it.copy(invoiceType = value) }

  /**
   * A stable set of callbacks per line.
   *
   * Building `{ viewModel.updateLine(line.id, it) }` inside the list meant a fresh lambda
   * for every line on every keystroke, so Compose could not skip a single one: typing a
   * digit into line one relaid out all of them. Cached by line id, the lambdas stay equal
   * across recompositions and only the line being edited redraws.
   */
  fun handlersFor(id: Long): LineHandlers = handlers.getOrPut(id) {
    LineHandlers(
      onChange = { transform -> updateLine(id, transform) },
      onRemove = { removeLine(id) },
    )
  }

  private val handlers = mutableMapOf<Long, LineHandlers>()

  fun updateLine(id: Long, transform: (DraftLine) -> DraftLine) = _newBill.update { state ->
    state.copy(
      lines = state.lines.map { if (it.id == id) transform(it) else it },
      error = null,
    ).recalculate()
  }

  fun addLine() = _newBill.update { state ->
    state.copy(lines = state.lines + DraftLine(id = nextLineId++)).recalculate()
  }

  fun removeLine(id: Long) = _newBill.update { state ->
    // Never leave the form with nothing to type into.
    val remaining = state.lines.filterNot { it.id == id }
    state.copy(lines = remaining.ifEmpty { listOf(DraftLine(id = nextLineId++)) }).recalculate()
  }

  fun pickItem(lineId: Long, item: ItemEntity) = updateLine(lineId) {
    it.copy(
      description = item.name,
      rate = np.bill.core.money.paisaToInput(item.unitPricePaisa),
      unit = item.unit,
      vatApplicable = item.vatApplicable,
      itemId = item.id,
    )
  }

  /**
   * Several products at once: the first fills the line the picker was opened from, the
   * rest get lines of their own.
   */
  fun pickItems(lineId: Long, items: List<ItemEntity>) {
    items.forEachIndexed { index, item ->
      val target = if (index == 0) lineId else addLineReturningId()
      pickItem(target, item)
    }
  }

  /**
   * A scanned packet becomes a line. Scanning the same product twice bumps the quantity
   * rather than adding a second line, which is what a shopkeeper ringing up three tins of
   * the same thing actually wants.
   */
  fun onProductScanned(barcode: String) {
    viewModelScope.launch {
      val item = catalog.itemByBarcode(barcode)
      if (item == null) {
        _newBill.update {
          it.copy(error = application.getString(np.bill.R.string.product_not_found))
        }
        return@launch
      }

      val existing = _newBill.value.lines.firstOrNull { it.itemId == item.id }
      if (existing != null) {
        updateLine(existing.id) { line ->
          val next = (parseQuantityMilli(line.quantity) ?: 1000L) + 1000L
          line.copy(quantity = np.bill.core.money.formatQuantity(next))
        }
        return@launch
      }

      // Fill the first blank line before adding another one.
      val blank = _newBill.value.lines.firstOrNull { it.description.isBlank() }
      val target = blank?.id ?: run {
        val id = nextLineId++
        _newBill.update { state -> state.copy(lines = state.lines + DraftLine(id = id)) }
        id
      }
      pickItem(target, item)
    }
  }

  /** The buyer, from the shop's own list or from a card held up at the counter. */
  fun pickCustomer(customer: np.bill.data.db.CustomerEntity) = _newBill.update {
    it.copy(
      buyerName = customer.name,
      buyerPhone = customer.phone.orEmpty(),
      buyerPan = customer.pan.orEmpty(),
      customerId = customer.id,
      error = null,
    )
  }

  fun onCustomerCardScanned(scanned: String, onResolved: (String) -> Unit) {
    viewModelScope.launch {
      when (val result = profiles.resolve(scanned)) {
        is ApiResult.Ok -> {
          val profile = result.value
          _newBill.update {
            it.copy(
              buyerName = profile.name,
              buyerPhone = profile.phone.orEmpty(),
              buyerPan = profile.pan.orEmpty(),
              buyerAddress = profile.address.orEmpty(),
              shopperLink = profile.link,
              error = null,
            )
          }
          onResolved(profile.name)
        }
        ApiResult.Offline -> _newBill.update {
          it.copy(error = application.getString(np.bill.R.string.offline_banner))
        }
        is ApiResult.Failed -> _newBill.update { it.copy(error = result.message) }
      }
    }
  }

  private var seeded = false

  /**
   * Seeds a bill opened from the products or customers list.
   *
   * Guarded so a recomposition cannot add the same products twice, which would be a
   * silent doubling of a line on a bill someone is about to print.
   */
  fun startWith(itemIds: List<String>, customerId: String?) {
    if (seeded || (itemIds.isEmpty() && customerId == null)) return
    seeded = true

    viewModelScope.launch {
      if (itemIds.isNotEmpty()) {
        val byId = catalog.items().first().associateBy { it.id }
        // The blank first line takes the first product; the rest get their own.
        itemIds.mapNotNull(byId::get).forEachIndexed { index, item ->
          val target = if (index == 0) _newBill.value.lines.first().id else addLineReturningId()
          pickItem(target, item)
        }
      }
      customerId?.let { id ->
        catalog.customers().first().firstOrNull { it.id == id }?.let(::pickCustomer)
      }
    }
  }

  private fun addLineReturningId(): Long {
    val id = nextLineId++
    _newBill.update { it.copy(lines = it.lines + DraftLine(id = id)) }
    return id
  }

  /**
   * The codes the shop has saved. Picking a wallet at the counter shows the customer the
   * code to scan, which is the whole reason the method is chosen before the bill is
   * saved rather than after.
   */
  val paymentQrs = paymentQr.observe()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  // -- templates and regulars --------------------------------------------------------

  /** The shop's baskets, most-used first. Drawn as one row of taps on the home screen. */
  val templates = templateRepo.observe()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /**
   * Who this counter has billed lately.
   *
   * Taken from the bills themselves rather than from the customer list, so a walk-in
   * typed onto one bill is one tap on the next. Keyed by customer where there is one and
   * by name where there is not, which is what stops the same regular appearing twice.
   */
  val recentBuyers: kotlinx.coroutines.flow.StateFlow<List<RecentBuyer>> = billing.recent()
    .map { bills ->
      bills
        .filter { it.status == "active" && it.buyerName.isNotBlank() }
        // Keyed on the name alone. Keying on the customer id as well listed the same
        // regular twice: once from the bill that picked them out of the list, and once
        // from the bill where the name was typed by hand.
        .groupBy { it.buyerName.trim().lowercase() }
        .values
        .map { group -> group.firstOrNull { it.customerId != null } ?: group.first() }
        .sortedByDescending { it.issuedAt }
        .take(8)
        .map { RecentBuyer(it.customerId, it.buyerName, it.buyerPhone, it.buyerPan) }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  /**
   * Fills the draft from a template.
   *
   * Counts the use as it goes, so the front of the row on the home screen is whatever
   * the shop actually reaches for rather than whatever was made first.
   */
  fun startFromTemplate(templateId: String) {
    viewModelScope.launch {
      val template = templateRepo.byId(templateId) ?: return@launch
      templateRepo.markUsed(templateId)

      var next = 1L
      val lines = template.lines.sortedBy { it.lineNo }.map { line ->
        DraftLine(
          id = next++,
          description = line.description,
          quantity = formatQuantity(line.quantityMilli),
          rate = np.bill.core.money.paisaToInput(line.unitPricePaisa),
          unit = line.unit,
          vatApplicable = line.vatApplicable,
          itemId = line.itemId,
        )
      }
      nextLineId = next
      _newBill.update { it.copy(lines = lines.ifEmpty { listOf(DraftLine(id = 1)) }).recalculate() }
    }
  }

  /**
   * Why the counter is empty.
   *
   * The banner used to blame the connection for every empty counter, which sends a shop
   * that is perfectly online to go and look at its router. A till that has just been
   * approved has no numbers yet and only needs one sync; one that cannot reach the
   * office needs to hear that instead.
   */
  enum class NumbersHint { FETCHING, OFFLINE, REFUSED }

  private val _numbersHint = MutableStateFlow(NumbersHint.FETCHING)
  val numbersHint = _numbersHint.asStateFlow()

  /**
   * Asks the office for numbers now.
   *
   * Called by the banner that says the counter is empty, so the shop is not left waiting
   * on the periodic sync while looking at a message about it. Guarded against firing
   * repeatedly while one is already in flight.
   */
  fun fetchNumbers() {
    if (fetching) return
    fetching = true
    viewModelScope.launch {
      _numbersHint.value = NumbersHint.FETCHING
      val outcome = sync.sync()
      _numbersHint.value = when {
        outcome.offline -> NumbersHint.OFFLINE
        outcome.error != null -> NumbersHint.REFUSED
        else -> NumbersHint.FETCHING
      }
      fetching = false
    }
  }

  fun deleteTemplate(id: String) {
    viewModelScope.launch { templateRepo.delete(id) }
  }

  /** A regular, straight onto the bill. Everything the last bill knew about them. */
  fun useRecentBuyer(buyer: RecentBuyer) = _newBill.update {
    it.copy(
      customerId = buyer.customerId,
      buyerName = buyer.name,
      buyerPhone = buyer.phone.orEmpty(),
      buyerPan = buyer.pan.orEmpty(),
      error = null,
    )
  }

  fun itemSuggestions(term: String) = catalog.searchItems(term)

  fun customerSuggestions(term: String) = catalog.searchCustomers(term)

  fun save(onSaved: (String) -> Unit) {
    val state = _newBill.value
    if (!state.canSave) return

    viewModelScope.launch {
      _newBill.update { it.copy(saving = true, error = null) }

      val draft = BillingRepository.Draft(
        invoiceType = state.invoiceType,
        buyerName = state.buyerName.trim().ifBlank { "Cash customer" },
        buyerPhone = state.buyerPhone.ifBlank { null },
        buyerPan = state.buyerPan.ifBlank { null },
        buyerAddress = state.buyerAddress.ifBlank { null },
        customerId = state.customerId,
        paymentMethod = state.paymentMethod,
        notes = state.notes.trim().ifBlank { null },
        discountPaisa = parsePaisa(state.discount) ?: 0,
        // A cash sale settles in full; a credit sale carries only what was handed over.
        paidAtIssuePaisa = if (state.onCredit) parsePaisa(state.paidNow) ?: 0 else null,
        dueMiti = state.dueMiti.ifBlank { null }.takeIf { state.onCredit },
        shopperLink = state.shopperLink,
        lines = state.lines.mapNotNull(DraftLine::toInput),
      )

      when (val result = billing.write(draft)) {
        is BillingRepository.Result.Written -> {
          // The bill is already safe on the device; this only decides how soon the
          // office sees it.
          SyncWorker.runNow(application)
          _newBill.value = NewBillState(vatRateBp = state.vatRateBp).recalculate()
          nextLineId = 2
          onSaved(result.bill.id)
        }
        is BillingRepository.Result.Refused -> {
          val message = when (result.reason) {
            BillingRepository.Failure.NoLines -> application.getString(np.bill.R.string.error_no_lines)
            BillingRepository.Failure.ZeroTotal -> application.getString(np.bill.R.string.error_zero_total)
            BillingRepository.Failure.AbbreviatedLimit ->
              application.getString(np.bill.R.string.error_abbreviated_limit)
            BillingRepository.Failure.OutOfNumbers ->
              application.getString(np.bill.R.string.out_of_numbers)
          }
          _newBill.update { it.copy(saving = false, error = message) }
        }
      }
    }
  }
}

/** Miti strings sort lexically because they are zero-padded, so a range is a comparison. */
private fun BillFilters.matches(bill: BillEntity): Boolean {
  if (fromMiti != null && bill.miti < fromMiti) return false
  if (toMiti != null && bill.miti > toMiti) return false

  val statusOk = when (status) {
    BillStatusFilter.ALL -> true
    BillStatusFilter.ACTIVE -> bill.status == "active"
    BillStatusFilter.CANCELLED -> bill.status == "cancelled"
    BillStatusFilter.UNSYNCED -> bill.syncState != np.bill.data.db.SyncState.SYNCED
  }
  if (!statusOk) return false

  if (search.isBlank()) return true
  val term = search.trim()
  return bill.invoiceNumber.contains(term, ignoreCase = true) ||
    bill.buyerName.contains(term, ignoreCase = true) ||
    bill.buyerPhone.orEmpty().contains(term)
}
