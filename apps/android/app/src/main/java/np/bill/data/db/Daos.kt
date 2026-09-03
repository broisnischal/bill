package np.bill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

  @Transaction
  suspend fun insertBill(bill: BillEntity, lines: List<BillLineEntity>) {
    insert(bill)
    insertLines(lines)
  }

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(bill: BillEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLines(lines: List<BillLineEntity>)

  @Query("SELECT * FROM bill ORDER BY issuedAt DESC, sequence DESC LIMIT :limit")
  fun recent(limit: Int = 200): Flow<List<BillEntity>>

  @Query(
    """
    SELECT * FROM bill
    WHERE invoiceNumber LIKE '%' || :term || '%' OR buyerName LIKE '%' || :term || '%'
       OR buyerPhone LIKE '%' || :term || '%'
    ORDER BY issuedAt DESC LIMIT 200
    """,
  )
  fun search(term: String): Flow<List<BillEntity>>

  @Query("SELECT * FROM bill WHERE id = :id")
  fun observe(id: String): Flow<BillEntity?>

  @Query("SELECT * FROM bill WHERE id = :id")
  suspend fun byId(id: String): BillEntity?

  @Query("SELECT * FROM bill_line WHERE billId = :billId ORDER BY lineNo")
  suspend fun linesOf(billId: String): List<BillLineEntity>

  @Query("SELECT * FROM bill_line WHERE billId = :billId ORDER BY lineNo")
  fun observeLines(billId: String): Flow<List<BillLineEntity>>

  @Query("SELECT * FROM bill WHERE syncState = 'PENDING' ORDER BY issuedAt LIMIT :limit")
  suspend fun pending(limit: Int = 50): List<BillEntity>

  @Query("SELECT COUNT(*) FROM bill WHERE syncState = 'PENDING'")
  fun pendingCount(): Flow<Int>

  @Query("SELECT * FROM bill WHERE cancelPending = 1 LIMIT 50")
  suspend fun pendingCancellations(): List<BillEntity>

  @Query("UPDATE bill SET syncState = :state, syncError = :error, syncedAt = :syncedAt WHERE id = :id")
  suspend fun markSync(id: String, state: SyncState, error: String?, syncedAt: Long?)

  @Query("UPDATE bill SET printCount = printCount + 1 WHERE id = :id")
  suspend fun countPrint(id: String)

  @Query("UPDATE bill SET status = 'cancelled', cancelReason = :reason, cancelPending = 1 WHERE id = :id")
  suspend fun cancelLocally(id: String, reason: String)

  @Query("UPDATE bill SET cancelPending = 0 WHERE id = :id")
  suspend fun clearCancelPending(id: String)

  @Upsert
  suspend fun savePayment(payment: PaymentEntity)

  @Query("SELECT * FROM payment WHERE billId = :billId ORDER BY receivedAt")
  fun paymentsOf(billId: String): Flow<List<PaymentEntity>>

  @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM payment WHERE billId = :billId")
  suspend fun paidSince(billId: String): Long

  @Query("SELECT * FROM payment WHERE syncState = 'PENDING' LIMIT 100")
  suspend fun pendingPayments(): List<PaymentEntity>

  @Query("UPDATE payment SET syncState = :state WHERE id = :id")
  suspend fun markPaymentSync(id: String, state: SyncState)

  /**
   * Bills with money still owed on them, biggest first.
   *
   * The outstanding amount is computed in the query rather than stored, because it is a
   * difference between rows that only ever get inserted, and a cached copy of it is the
   * kind of thing that goes stale without anyone noticing.
   */
  @Query(
    """
    SELECT b.*, (b.totalPaisa - b.paidAtIssuePaisa -
      COALESCE((SELECT SUM(p.amountPaisa) FROM payment p WHERE p.billId = b.id), 0)) AS duePaisa
    FROM bill b
    WHERE b.status = 'active'
      AND (b.totalPaisa - b.paidAtIssuePaisa -
        COALESCE((SELECT SUM(p.amountPaisa) FROM payment p WHERE p.billId = b.id), 0)) > 0
    ORDER BY b.issuedAt DESC
    """,
  )
  fun outstanding(): Flow<List<BillWithDue>>

  @Query(
    """
    SELECT COALESCE(SUM(b.totalPaisa - b.paidAtIssuePaisa -
      COALESCE((SELECT SUM(p.amountPaisa) FROM payment p WHERE p.billId = b.id), 0)), 0)
    FROM bill b
    WHERE b.status = 'active'
      AND (b.totalPaisa - b.paidAtIssuePaisa -
        COALESCE((SELECT SUM(p.amountPaisa) FROM payment p WHERE p.billId = b.id), 0)) > 0
    """,
  )
  fun totalOutstanding(): Flow<Long>

  /** Totals for the day's summary, which is the only number most shops look at. */
  @Query(
    """
    SELECT COALESCE(SUM(totalPaisa), 0) FROM bill
    WHERE status = 'active' AND miti = :miti
    """,
  )
  fun totalForMiti(miti: String): Flow<Long>

  @Query("SELECT COUNT(*) FROM bill WHERE status = 'active' AND miti = :miti")
  fun countForMiti(miti: String): Flow<Int>

  /** Every active bill in a fiscal year, for the reports screen. */
  @Query("SELECT * FROM bill WHERE fiscalYear = :fiscalYear ORDER BY issuedAt")
  fun forFiscalYear(fiscalYear: String): Flow<List<BillEntity>>

  @Query("SELECT * FROM bill_line ORDER BY lineNo")
  fun allLines(): Flow<List<BillLineEntity>>
}

@Dao
interface StoreDataDao {

  /**
   * Wipes everything that belongs to a shop.
   *
   * Used when a different business signs in on the same phone. The leases matter most:
   * they are blocks of numbers from one store's series, and printing from them under a
   * different PAN would produce bills that belong to nobody.
   */
  @Transaction
  suspend fun clearAll() {
    clearPayments()
    clearBillLines()
    clearBills()
    clearLeases()
    clearItems()
    clearCustomers()
  }

  @Query("DELETE FROM payment") suspend fun clearPayments()

  @Query("DELETE FROM bill_line") suspend fun clearBillLines()

  @Query("DELETE FROM bill") suspend fun clearBills()

  @Query("DELETE FROM lease") suspend fun clearLeases()

  @Query("DELETE FROM item") suspend fun clearItems()

  @Query("DELETE FROM customer") suspend fun clearCustomers()

  @Query("SELECT COUNT(*) FROM bill WHERE syncState = 'PENDING'")
  suspend fun unsyncedCount(): Int
}

@Dao
interface LeaseDao {

  @Upsert
  suspend fun upsert(leases: List<LeaseEntity>)

  /**
   * Takes the next number this device may print, oldest block first. Returns null when
   * the device has run out, which is the one condition that stops offline billing.
   */
  @Transaction
  suspend fun takeNext(fiscalYear: String, invoiceType: String, now: Long): Pair<LeaseEntity, Int>? {
    val lease = nextUsable(fiscalYear, invoiceType, now) ?: return null
    advance(lease.id)
    return lease to lease.nextSequence
  }

  @Query(
    """
    SELECT * FROM lease
    WHERE fiscalYear = :fiscalYear AND invoiceType = :invoiceType
      AND nextSequence <= endSequence AND expiresAt > :now
    ORDER BY startSequence LIMIT 1
    """,
  )
  suspend fun nextUsable(fiscalYear: String, invoiceType: String, now: Long): LeaseEntity?

  @Query("UPDATE lease SET nextSequence = nextSequence + 1 WHERE id = :id")
  suspend fun advance(id: String)

  @Query(
    """
    SELECT COALESCE(SUM(endSequence - nextSequence + 1), 0) FROM lease
    WHERE fiscalYear = :fiscalYear AND invoiceType = :invoiceType
      AND nextSequence <= endSequence AND expiresAt > :now
    """,
  )
  fun remaining(fiscalYear: String, invoiceType: String, now: Long): Flow<Int>

  @Query("DELETE FROM lease WHERE expiresAt <= :now OR nextSequence > endSequence")
  suspend fun prune(now: Long)
}

@Dao
interface KarobarDao {

  /** Open first and oldest first: the money that has been out longest is what to chase. */
  @Query("SELECT * FROM karobar_entry WHERE settledAt IS NULL ORDER BY createdAt")
  fun open(): Flow<List<KarobarEntryEntity>>

  @Query("SELECT * FROM karobar_entry WHERE settledAt IS NOT NULL ORDER BY settledAt DESC LIMIT 50")
  fun settled(): Flow<List<KarobarEntryEntity>>

  @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM karobar_entry WHERE settledAt IS NULL")
  fun outstanding(): Flow<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entry: KarobarEntryEntity)

  @Query("UPDATE karobar_entry SET settledAt = :now WHERE id = :id")
  suspend fun settle(id: String, now: Long)

  @Query("UPDATE karobar_entry SET settledAt = NULL WHERE id = :id")
  suspend fun reopen(id: String)

  @Query("DELETE FROM karobar_entry WHERE id = :id")
  suspend fun delete(id: String)
}

@Dao
interface TemplateDao {

  @Transaction
  @Query("SELECT * FROM bill_template ORDER BY usedCount DESC, name")
  fun observe(): Flow<List<BillTemplate>>

  @Transaction
  @Query("SELECT * FROM bill_template WHERE id = :id")
  suspend fun byId(id: String): BillTemplate?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTemplate(template: BillTemplateEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLines(lines: List<BillTemplateLineEntity>)

  @Query("DELETE FROM bill_template_line WHERE templateId = :id")
  suspend fun clearLines(id: String)

  @Query("DELETE FROM bill_template WHERE id = :id")
  suspend fun delete(id: String)

  /** Saving over a template replaces its lines rather than adding a second set. */
  @Transaction
  suspend fun save(template: BillTemplateEntity, lines: List<BillTemplateLineEntity>) {
    insertTemplate(template)
    clearLines(template.id)
    insertLines(lines)
  }

  @Query("UPDATE bill_template SET usedCount = usedCount + 1 WHERE id = :id")
  suspend fun markUsed(id: String)
}

@Dao
interface CatalogDao {

  @Upsert
  suspend fun upsertItems(items: List<ItemEntity>)

  @Upsert
  suspend fun upsertCustomers(customers: List<CustomerEntity>)

  @Query("SELECT * FROM item WHERE active = 1 ORDER BY name")
  fun items(): Flow<List<ItemEntity>>

  @Query("SELECT * FROM item WHERE active = 1 AND (name LIKE '%' || :term || '%' OR sku LIKE '%' || :term || '%') ORDER BY name LIMIT 40")
  fun searchItems(term: String): Flow<List<ItemEntity>>

  @Query("SELECT * FROM customer ORDER BY name")
  fun customers(): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customer WHERE name LIKE '%' || :term || '%' OR phone LIKE '%' || :term || '%' ORDER BY name LIMIT 40")
  fun searchCustomers(term: String): Flow<List<CustomerEntity>>

  /**
   * Takes what was sold off the shelf, for the products the shop counts.
   *
   * `pendingUpload` is deliberately left alone: the server does the same subtraction when
   * the bill reaches it, and a device that pushed its own figure back would take the
   * quantity off twice. This is the optimistic copy, replaced by the server's on the next
   * sync.
   */
  @Query(
    "UPDATE item SET stockThousandths = max(0, stockThousandths - :quantityMilli) " +
      "WHERE id = :id AND stockThousandths IS NOT NULL",
  )
  suspend fun reduceStock(id: String, quantityMilli: Long)

  /**
   * The product with this name, whatever case it was typed in.
   *
   * A shop has one "Masu". Typing the name onto a bill instead of picking it from the
   * list used to create a second one, then a third, until the suggestion dropdown showed
   * the same product three times at the same price.
   */
  @Query("SELECT * FROM item WHERE active = 1 AND name = :name COLLATE NOCASE LIMIT 1")
  suspend fun itemByName(name: String): ItemEntity?

  /**
   * The duplicates already on the device, keeping the oldest of each name.
   *
   * The oldest is the one bills already point at, so it is the row that has to survive.
   */
  @Query(
    "SELECT id FROM item WHERE id NOT IN (" +
      "SELECT id FROM item GROUP BY lower(name) HAVING MIN(rowid) = rowid" +
      ")",
  )
  suspend fun duplicateItemIds(): List<String>

  @Query("DELETE FROM item WHERE id IN (:ids)")
  suspend fun deleteItems(ids: List<String>)

  @Query("SELECT * FROM item WHERE barcode = :barcode AND active = 1 LIMIT 1")
  suspend fun itemByBarcode(barcode: String): ItemEntity?

  @Query("SELECT * FROM customer WHERE phone = :phone LIMIT 1")
  suspend fun customerByPhone(phone: String): CustomerEntity?

  @Query("SELECT COUNT(*) FROM item WHERE active = 1")
  fun itemCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM customer")
  fun customerCount(): Flow<Int>

  @Query("UPDATE item SET active = 0, pendingUpload = 1 WHERE id = :id")
  suspend fun deactivateItem(id: String)

  @Query("SELECT * FROM item WHERE pendingUpload = 1")
  suspend fun pendingItems(): List<ItemEntity>

  @Query("SELECT * FROM customer WHERE pendingUpload = 1")
  suspend fun pendingCustomers(): List<CustomerEntity>

  @Query("UPDATE item SET pendingUpload = 0, id = :serverId WHERE id = :localId")
  suspend fun confirmItem(localId: String, serverId: String)

  @Query("UPDATE customer SET pendingUpload = 0, id = :serverId WHERE id = :localId")
  suspend fun confirmCustomer(localId: String, serverId: String)
}

@Dao
interface WalletDao {

  @Upsert
  suspend fun save(bill: WalletBillEntity)

  @Query("SELECT * FROM wallet_bill ORDER BY issuedAt DESC")
  fun bills(): Flow<List<WalletBillEntity>>

  @Query("SELECT * FROM wallet_bill WHERE shareToken = :token")
  fun observe(token: String): Flow<WalletBillEntity?>

  @Query("SELECT COALESCE(SUM(totalPaisa), 0) FROM wallet_bill WHERE issuedAt >= :since")
  fun spentSince(since: Long): Flow<Long>

  @Query("DELETE FROM wallet_bill WHERE shareToken = :token")
  suspend fun remove(token: String)
}

@Dao
interface PaymentQrDao {
  @Query("SELECT * FROM payment_qr ORDER BY savedAt")
  fun observeAll(): Flow<List<PaymentQrEntity>>

  @Query("SELECT * FROM payment_qr ORDER BY savedAt")
  suspend fun all(): List<PaymentQrEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(row: PaymentQrEntity)

  @Query("DELETE FROM payment_qr WHERE id = :id")
  suspend fun delete(id: String)

  @Query("DELETE FROM payment_qr")
  suspend fun clearAll()
}
