package np.bill.ui.karobar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.core.money.parsePaisa
import np.bill.data.db.KarobarEntryEntity
import np.bill.data.repo.KarobarRepository

/** What is being written into the book right now. */
data class KarobarForm(
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
class KarobarViewModel @Inject constructor(
  private val karobar: KarobarRepository,
) : ViewModel() {

  val open = karobar.open()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val settled = karobar.settled()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val outstanding = karobar.outstanding()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

  private val _form = MutableStateFlow(KarobarForm())
  val form = _form.asStateFlow()

  fun onForm(transform: (KarobarForm) -> KarobarForm) = _form.update(transform)

  fun reset() = _form.update { KarobarForm() }

  fun save(onSaved: () -> Unit) {
    val current = _form.value
    if (!current.valid) return

    viewModelScope.launch {
      karobar.add(
        buyerName = current.buyerName,
        description = current.description,
        amountPaisa = parsePaisa(current.amount) ?: 0,
        buyerPhone = current.buyerPhone.ifBlank { null },
        note = current.note.ifBlank { null },
      )
      _form.value = KarobarForm()
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
  fun settle(entry: KarobarEntryEntity) {
    viewModelScope.launch { karobar.settle(entry.id) }
  }

  fun reopen(entry: KarobarEntryEntity) {
    viewModelScope.launch { karobar.reopen(entry.id) }
  }

  fun delete(entry: KarobarEntryEntity) {
    viewModelScope.launch { karobar.delete(entry.id) }
  }
}
