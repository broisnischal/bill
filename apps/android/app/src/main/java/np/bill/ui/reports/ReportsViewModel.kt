package np.bill.ui.reports

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import np.bill.core.nepali.BsCalendar
import np.bill.data.db.BillDao
import np.bill.data.db.BillEntity
import np.bill.data.repo.BillingRepository

@Immutable
data class DayTotal(val miti: String, val totalPaisa: Long)

@Immutable
data class ProductTotal(val name: String, val totalPaisa: Long)

@Immutable
data class ReportsState(
  val fiscalYear: String = "",
  val salesPaisa: Long = 0,
  val taxablePaisa: Long = 0,
  val exemptPaisa: Long = 0,
  val vatPaisa: Long = 0,
  val billCount: Int = 0,
  val averagePaisa: Long = 0,
  val recentDays: List<DayTotal> = emptyList(),
  val topProducts: List<ProductTotal> = emptyList(),
)

/**
 * The year's numbers, off the device.
 *
 * Everything is derived from bills already stored locally, so reports work with no
 * network and match exactly what the bill list shows — there is no second source of
 * truth to disagree with.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
  billing: BillingRepository,
  bills: BillDao,
) : ViewModel() {

  private val now = System.currentTimeMillis()
  private val fiscalYear = BsCalendar.fiscalYearFor(now)

  val state = combine(
    billing.forFiscalYear(fiscalYear),
    bills.allLines(),
  ) { yearBills, allLines ->
    val active = yearBills.filter { it.status == "active" }
    val sales = active.sumOf { it.totalPaisa }

    val byBill = allLines.groupBy { it.billId }
    val activeIds = active.mapTo(HashSet()) { it.id }

    val products = byBill
      .filterKeys(activeIds::contains)
      .values
      .flatten()
      .groupBy { it.description }
      .map { (name, lines) -> ProductTotal(name, lines.sumOf { it.lineTotalPaisa }) }
      .sortedByDescending(ProductTotal::totalPaisa)
      .take(5)

    ReportsState(
      fiscalYear = fiscalYear,
      salesPaisa = sales,
      taxablePaisa = active.sumOf { it.taxableAmountPaisa },
      exemptPaisa = active.sumOf { it.nonTaxableAmountPaisa },
      vatPaisa = active.sumOf { it.vatAmountPaisa },
      billCount = active.size,
      averagePaisa = if (active.isEmpty()) 0 else sales / active.size,
      recentDays = lastFortnight(active),
      topProducts = products,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState(fiscalYear))

  /** The last fourteen Bikram Sambat days, including the ones with nothing on them. */
  private fun lastFortnight(bills: List<BillEntity>): List<DayTotal> {
    val today = BsCalendar.toBs(now)
    val byMiti = bills.groupBy(BillEntity::miti)

    return (13 downTo 0).map { back ->
      val miti = BsCalendar.toBs(now - back * DAY_MILLIS).toString()
      DayTotal(miti, byMiti[miti].orEmpty().sumOf { it.totalPaisa })
    }.also { check(it.size == 14) { "expected a fortnight, got ${it.size} from $today" } }
  }

  private companion object {
    const val DAY_MILLIS = 24L * 60 * 60 * 1000
  }
}
