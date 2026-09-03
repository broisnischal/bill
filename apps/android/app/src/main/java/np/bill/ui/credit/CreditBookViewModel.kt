package np.bill.ui.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.core.money.parsePaisa
import np.bill.data.db.CreditEntryEntity
import np.bill.data.repo.CreditRepository

/** What is being written into the book right now. */
data class CreditForm(
  val customerId: String? = null,
  val buyerName: String = "",
  val buyerPhone: String = "",
  val description: String = "",
  val amount: String = "",
  val note: String = "",
) {
  val valid: Boolean
    get() = buyerName.trim().length >= 2 &&
      description.trim().isNotEmpty() &&
      (parsePaisa(amount) ?: 0) > 0
}

@HiltViewModel
class CreditBookViewModel @Inject constructor(
  private val credit: CreditRepository,
  private val catalog: np.bill.data.repo.CatalogRepository,
  private val billing: np.bill.data.repo.BillingRepository,
) : ViewModel() {

  /**
   * The same two shortcuts a bill has.
   *
   * Writing an entry into the book is the same act as writing a line onto a bill — who,
   * what, how much — so it gets the same help: the regulars this counter has served, and
   * the products the shop already sells with their prices.
   */
  val recentBuyers: kotlinx.coroutines.flow.StateFlow<List<np.bill.ui.billing.RecentBuyer>> =
    billing.recent()
      .map { bills ->
        bills
          .filter { it.status == "active" && it.buyerName.isNotBlank() }
          .groupBy { it.buyerName.trim().lowercase() }
          .values
          .map { group -> group.firstOrNull { it.customerId != null } ?: group.first() }
          .sortedByDescending { it.issuedAt }
          .take(8)
          .map {
            np.bill.ui.billing.RecentBuyer(it.customerId, it.buyerName, it.buyerPhone, it.buyerPan)
          }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun itemSuggestions(term: String) = catalog.searchItems(term)

  fun useBuyer(buyer: np.bill.ui.billing.RecentBuyer) = _form.update {
    it.copy(
      customerId = buyer.customerId,
      buyerName = buyer.name,
      buyerPhone = buyer.phone.orEmpty(),
    )
  }

  /** A product the shop sells fills both what was taken and what it costs. */
  fun useItem(item: np.bill.data.db.ItemEntity) = _form.update {
    it.copy(
      description = item.name,
      amount = np.bill.core.money.paisaToInput(item.unitPricePaisa),
    )
  }

  val open = credit.open()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val settled = credit.settled()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val outstanding = credit.outstanding()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

  private val _form = MutableStateFlow(CreditForm())
  val form = _form.asStateFlow()

  fun onForm(transform: (CreditForm) -> CreditForm) = _form.update(transform)

  fun reset() = _form.update { CreditForm() }

  fun save(onSaved: () -> Unit) {
    val current = _form.value
    if (!current.valid) return

    viewModelScope.launch {
      credit.add(
        buyerName = current.buyerName,
        description = current.description,
        amountPaisa = parsePaisa(current.amount) ?: 0,
        buyerPhone = current.buyerPhone.ifBlank { null },
        customerId = current.customerId,
        note = current.note.ifBlank { null },
      )
      _form.value = CreditForm()
      onSaved()
    }
  }

  /**
   * Paid.
   *
   * The entry stays in the book, closed, so the shop can see what was settled and when.
   * Making the bill for it is a separate act, because that is what takes a number out of
   * the series.
   */
  fun settle(entry: CreditEntryEntity) {
    viewModelScope.launch { credit.settle(entry.id) }
  }

  fun reopen(entry: CreditEntryEntity) {
    viewModelScope.launch { credit.reopen(entry.id) }
  }

  fun delete(entry: CreditEntryEntity) {
    viewModelScope.launch { credit.delete(entry.id) }
  }
}
