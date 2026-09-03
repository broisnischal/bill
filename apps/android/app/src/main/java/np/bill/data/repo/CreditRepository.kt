package np.bill.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import np.bill.core.nepali.BsCalendar
import np.bill.data.db.CreditDao
import np.bill.data.db.CreditEntryEntity

/**
 * The credit book.
 *
 * Deliberately device-only and deliberately not a bill. A bill takes a number out of a
 * government series and cannot be edited once issued; an entry here is a note that Ram
 * took two kilos and will settle on Friday, and notes get corrected. When the money
 * arrives the shop makes a bill for it and closes the entry.
 */
@Singleton
class CreditRepository @Inject constructor(private val credit: CreditDao) {

  fun open(): Flow<List<CreditEntryEntity>> = credit.open()

  fun settled(): Flow<List<CreditEntryEntity>> = credit.settled()

  fun outstanding(): Flow<Long> = credit.outstanding()

  suspend fun add(
    buyerName: String,
    description: String,
    amountPaisa: Long,
    buyerPhone: String? = null,
    customerId: String? = null,
    note: String? = null,
  ): CreditEntryEntity {
    val now = System.currentTimeMillis()
    val entry = CreditEntryEntity(
      id = UUID.randomUUID().toString(),
      customerId = customerId,
      buyerName = buyerName.trim(),
      buyerPhone = buyerPhone?.trim()?.ifBlank { null },
      description = description.trim(),
      amountPaisa = amountPaisa,
      note = note?.trim()?.ifBlank { null },
      miti = BsCalendar.toBs(now).toString(),
      createdAt = now,
    )
    credit.upsert(entry)
    return entry
  }

  /** Paid. The entry stays in the book so the shop can see what was settled and when. */
  suspend fun settle(id: String) = credit.settle(id, System.currentTimeMillis())

  suspend fun reopen(id: String) = credit.reopen(id)

  suspend fun delete(id: String) = credit.delete(id)
}
