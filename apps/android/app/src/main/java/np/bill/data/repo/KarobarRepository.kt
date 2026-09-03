package np.bill.data.repo

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import np.bill.core.nepali.BsCalendar
import np.bill.data.db.KarobarDao
import np.bill.data.db.KarobarEntryEntity

/**
 * The credit book.
 *
 * Deliberately device-only and deliberately not a bill. A bill takes a number out of a
 * government series and cannot be edited once issued; an entry here is a note that Ram
 * took two kilos and will settle on Friday, and notes get corrected. When the money
 * arrives the shop makes a bill for it and closes the entry.
 */
@Singleton
class KarobarRepository @Inject constructor(private val karobar: KarobarDao) {

  fun open(): Flow<List<KarobarEntryEntity>> = karobar.open()

  fun settled(): Flow<List<KarobarEntryEntity>> = karobar.settled()

  fun outstanding(): Flow<Long> = karobar.outstanding()

  suspend fun add(
    buyerName: String,
    description: String,
    amountPaisa: Long,
    buyerPhone: String? = null,
    customerId: String? = null,
    note: String? = null,
  ): KarobarEntryEntity {
    val now = System.currentTimeMillis()
    val entry = KarobarEntryEntity(
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
    karobar.upsert(entry)
    return entry
  }

  /** Paid. The entry stays in the book so the shop can see what was settled and when. */
  suspend fun settle(id: String) = karobar.settle(id, System.currentTimeMillis())

  suspend fun reopen(id: String) = karobar.reopen(id)

  suspend fun delete(id: String) = karobar.delete(id)
}
