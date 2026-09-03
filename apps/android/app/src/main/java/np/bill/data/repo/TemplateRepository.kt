package np.bill.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import np.bill.data.db.BillDao
import np.bill.data.db.BillTemplate
import np.bill.data.db.BillTemplateEntity
import np.bill.data.db.BillTemplateLineEntity
import np.bill.data.db.TemplateDao

/**
 * The baskets a shop bills over and over.
 *
 * Made from a bill that has already been written rather than from a form: a shop knows
 * what it sells together by selling it, and asking someone to build a template from
 * scratch before they have billed once gets a screen nobody opens. Bill it, save it, and
 * the next one is a tap.
 *
 * These live on the device only. Nothing about a template is a tax record — it is a
 * shortcut for this counter — so it does not go through the sync outbox with the bills.
 * A second till builds its own.
 */
@Singleton
class TemplateRepository @Inject constructor(
  private val templates: TemplateDao,
  private val bills: BillDao,
) {

  fun observe(): Flow<List<BillTemplate>> = templates.observe()

  suspend fun byId(id: String): BillTemplate? = templates.byId(id)

  suspend fun markUsed(id: String) = templates.markUsed(id)

  suspend fun delete(id: String) = templates.delete(id)

  /**
   * Keeps a bill's lines under a name.
   *
   * The quantity comes along because most templates have a usual one, and it is the
   * field a shopkeeper edits at the counter anyway. Nothing about the buyer is kept: a
   * template is what was sold, not who it was sold to.
   */
  suspend fun saveFromBill(name: String, billId: String): Boolean {
    val lines = bills.linesOf(billId)
    if (lines.isEmpty()) return false

    val id = UUID.randomUUID().toString()
    templates.save(
      template = BillTemplateEntity(
        id = id,
        name = name.trim(),
        updatedAt = System.currentTimeMillis(),
      ),
      lines = lines.map { line ->
        BillTemplateLineEntity(
          templateId = id,
          lineNo = line.lineNo,
          itemId = line.itemId,
          description = line.description,
          unit = line.unit,
          quantityMilli = line.quantityMilli,
          unitPricePaisa = line.unitPricePaisa,
          vatApplicable = line.vatApplicable,
        )
      },
    )
    return true
  }
}
