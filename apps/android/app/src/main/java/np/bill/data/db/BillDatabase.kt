package np.bill.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
  @TypeConverter fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

  @TypeConverter fun fromSyncState(value: SyncState): String = value.name
}

/** A bill with what is still owed on it, computed by the query rather than stored. */
data class BillWithDue(
  @androidx.room.Embedded val bill: BillEntity,
  val duePaisa: Long,
)

@Database(
  entities = [
    BillEntity::class,
    BillLineEntity::class,
    PaymentEntity::class,
    LeaseEntity::class,
    ItemEntity::class,
    CustomerEntity::class,
    WalletBillEntity::class,
    PaymentQrEntity::class,
    BillTemplateEntity::class,
    BillTemplateLineEntity::class,
    KarobarEntryEntity::class,
  ],
  version = 11,
  exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BillDatabase : RoomDatabase() {
  abstract fun bills(): BillDao
  abstract fun leases(): LeaseDao
  abstract fun catalog(): CatalogDao
  abstract fun wallet(): WalletDao
  abstract fun storeData(): StoreDataDao
  abstract fun paymentQrs(): PaymentQrDao
  abstract fun templates(): TemplateDao
  abstract fun karobar(): KarobarDao

  companion object {
    /**
     * Products gained a barcode. Migrated rather than rebuilt: the database can be
     * holding bills that have not reached the server, and dropping it would lose them.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item ADD COLUMN barcode TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_barcode ON item (barcode)")
      }
    }

    /**
     * Removes bills numbered from a shop this device no longer bills for.
     *
     * Signing out used to erase the one value that could tell a later sign-in a
     * *different* business had taken the phone over, so the previous shop's number leases
     * stayed and bills were written against them. The server refuses every one: those
     * numbers belong to another store's series and always will.
     *
     * They are deleted rather than kept, because there is nothing to keep. A bill the
     * shop never issued has nothing to reverse and no place in its books, and leaving it
     * on the list only invites someone to try to fix it. Matched by the server's own
     * words, and only where the bill never synced — anything the office accepted stays.
     */
    /**
     * The shop's own payment QRs, so the counter can take digital payment without the
     * customer asking for a number to type. Additive: an existing database gains an
     * empty table and every bill in it is untouched.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS payment_qr (
            id TEXT NOT NULL PRIMARY KEY,
            method TEXT NOT NULL,
            label TEXT,
            imagePath TEXT NOT NULL,
            savedAt INTEGER NOT NULL
          )
          """.trimIndent(),
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS index_payment_qr_method ON payment_qr (method)",
        )
      }
    }

    /**
     * A payment QR remembers what it says, so a code that was scanned or typed can be
     * redrawn crisply instead of kept as someone's photograph of a card.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE payment_qr ADD COLUMN payload TEXT")
      }
    }

    /** Stock and labels: what is on the shelf, and how the shop groups what it sells. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item ADD COLUMN stockThousandths INTEGER")
        db.execSQL("ALTER TABLE item ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
      }
    }

    /** Bill templates: the baskets a shop rings up over and over. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS bill_template (" +
            "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
            "usedCount INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL)",
        )
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS bill_template_line (" +
            "rowId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, templateId TEXT NOT NULL, " +
            "lineNo INTEGER NOT NULL, itemId TEXT, description TEXT NOT NULL, " +
            "unit TEXT NOT NULL, quantityMilli INTEGER NOT NULL, " +
            "unitPricePaisa INTEGER NOT NULL, vatApplicable INTEGER NOT NULL, " +
            "FOREIGN KEY(templateId) REFERENCES bill_template(id) ON DELETE CASCADE)",
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_bill_template_line_templateId " +
            "ON bill_template_line (templateId)",
        )
      }
    }

    /** The credit book: what a customer took and has not paid for yet. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS karobar_entry (" +
            "id TEXT NOT NULL PRIMARY KEY, customerId TEXT, buyerName TEXT NOT NULL, " +
            "buyerPhone TEXT, description TEXT NOT NULL, amountPaisa INTEGER NOT NULL, " +
            "note TEXT, miti TEXT NOT NULL, createdAt INTEGER NOT NULL, settledAt INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_karobar_entry_settledAt ON karobar_entry (settledAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_karobar_entry_customerId ON karobar_entry (customerId)")
      }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        val orphaned = """
          syncState != 'SYNCED' AND syncError IS NOT NULL AND (
            syncError LIKE '%not issued to this device%'
            OR syncError LIKE '%outside the block%'
          )
        """
        db.execSQL("DELETE FROM bill_line WHERE billId IN (SELECT id FROM bill WHERE $orphaned)")
        db.execSQL("DELETE FROM payment WHERE billId IN (SELECT id FROM bill WHERE $orphaned)")
        db.execSQL("DELETE FROM bill WHERE $orphaned")
      }
    }

    /**
     * Repairs dates written in Devanagari digits.
     *
     * `String.format` renders `%d` in the default locale's numbering system, so a bill
     * written while the app was in Nepali was stored with a miti of "२०८३-०५-१३". The
     * date was right and the string matched nothing: those bills vanished from today's
     * takings and from every date filter, and the wrong value would have gone to the IRD.
     *
     * The formatter is fixed; this puts the rows already on the device back. Digit by
     * digit rather than cleverly, because SQLite has no transliteration and ten
     * substitutions on a handful of rows costs nothing.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
      private val digits = listOf(
        "०" to "0", "१" to "1", "२" to "2", "३" to "3", "४" to "4",
        "५" to "5", "६" to "6", "७" to "7", "८" to "8", "९" to "9",
      )

      private fun repair(column: String): String =
        digits.fold(column) { expression, (devanagari, ascii) ->
          "replace($expression, '$devanagari', '$ascii')"
        }

      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE bill SET miti = ${repair("miti")} WHERE miti GLOB '*[०-९]*'")
        db.execSQL(
          "UPDATE bill SET dueMiti = ${repair("dueMiti")} " +
            "WHERE dueMiti IS NOT NULL AND dueMiti GLOB '*[०-९]*'",
        )
        db.execSQL("UPDATE payment SET miti = ${repair("miti")} WHERE miti GLOB '*[०-९]*'")
      }
    }

    /** A bill can now name the shopper whose card was scanned for it. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bill ADD COLUMN shopperLink TEXT")
      }
    }

    /** Credit sales: what was paid at the counter, and the payments that followed. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bill ADD COLUMN paidAtIssuePaisa INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bill ADD COLUMN dueMiti TEXT")
        // Bills written before this migration were all settled at the counter.
        db.execSQL("UPDATE bill SET paidAtIssuePaisa = totalPaisa")
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS payment (
            id TEXT NOT NULL PRIMARY KEY,
            billId TEXT NOT NULL,
            amountPaisa INTEGER NOT NULL,
            method TEXT NOT NULL,
            receivedAt INTEGER NOT NULL,
            miti TEXT NOT NULL,
            note TEXT,
            syncState TEXT NOT NULL
          )
          """,
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_billId ON payment (billId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_syncState ON payment (syncState)")
      }
    }
  }
}
