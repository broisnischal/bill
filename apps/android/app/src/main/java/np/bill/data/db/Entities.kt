package np.bill.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The device's own copy of the shop.
 *
 * Everything the biller touches lives here first and reaches the server later, so the
 * app behaves the same whether or not there is a signal. Ids are the ones the server
 * will use, generated here, which is what makes a push idempotent.
 */

/** Where a bill has got to on its way to the server. */
enum class SyncState {
  /** Written and printed here; the server has not seen it. */
  PENDING,

  /** Filed on the server. */
  SYNCED,

  /**
   * The server refused it for good: the paper disagrees with what the bill adds up to.
   * It stays visible so the shop can raise a credit note against it.
   */
  REJECTED,
}

@Entity(
  tableName = "bill",
  indices = [Index("issuedAt"), Index("syncState"), Index("invoiceNumber", unique = true)],
)
data class BillEntity(
  @PrimaryKey val id: String,
  val shareToken: String,
  val invoiceNumber: String,
  val fiscalYear: String,
  val invoiceType: String,
  val sequence: Int,
  val leaseId: String,

  val buyerName: String,
  val buyerPan: String?,
  val buyerAddress: String?,
  val buyerPhone: String?,
  val customerId: String?,

  /** Epoch millis. The miti is the Kathmandu date this instant falls on. */
  val issuedAt: Long,
  val miti: String,

  val subTotalPaisa: Long,
  val discountPaisa: Long,
  val taxableAmountPaisa: Long,
  val nonTaxableAmountPaisa: Long,
  val vatRateBp: Int,
  val vatAmountPaisa: Long,
  val totalPaisa: Long,
  val amountInWords: String,

  val paymentMethod: String,
  /** Handed over at the counter. Less than the total means the rest is owed. */
  val paidAtIssuePaisa: Long = 0,
  /** When the shop expects the rest, in Bikram Sambat. Credit sales only. */
  val dueMiti: String? = null,
  /** Signed handle from a scanned customer card; sent on sync so the bill reaches them. */
  val shopperLink: String? = null,
  val notes: String?,

  val status: String = "active",
  val cancelReason: String? = null,
  /** Set when the shop cancels offline; cleared once the server has accepted it. */
  val cancelPending: Boolean = false,

  val printCount: Int = 0,
  val syncState: SyncState = SyncState.PENDING,
  val syncError: String? = null,
  val syncedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "bill_line",
  primaryKeys = ["billId", "lineNo"],
  foreignKeys = [
    ForeignKey(
      entity = BillEntity::class,
      parentColumns = ["id"],
      childColumns = ["billId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class BillLineEntity(
  val billId: String,
  val lineNo: Int,
  val itemId: String?,
  val description: String,
  val hsCode: String?,
  val unit: String,
  val quantityMilli: Long,
  val unitPricePaisa: Long,
  val discountPaisa: Long,
  val vatApplicable: Boolean,
  val lineTotalPaisa: Long,
)

/**
 * A block of invoice numbers this device may print from while offline. `nextSequence` is
 * the local cursor; the server keeps its own watermark and rejects anything outside the
 * block, so a corrupted cursor cannot mint a number that was never leased.
 */
@Entity(tableName = "lease", indices = [Index("fiscalYear", "invoiceType")])
data class LeaseEntity(
  @PrimaryKey val id: String,
  val fiscalYear: String,
  val invoiceType: String,
  val startSequence: Int,
  val endSequence: Int,
  val nextSequence: Int,
  val expiresAt: Long,
)

@Entity(tableName = "item", indices = [Index("name"), Index("barcode")])
data class ItemEntity(
  @PrimaryKey val id: String,
  val name: String,
  val description: String?,
  val hsCode: String?,
  val sku: String?,
  /** What the packet carries, scanned with the camera. Finds the product on a bill. */
  val barcode: String? = null,
  val unit: String,
  val unitPricePaisa: Long,
  /**
   * What is on the shelf, in thousandths of a unit, or null where the shop does not
   * count this one. Zero and null are different answers: one says it is out, the other
   * says nobody is keeping track.
   */
  val stockThousandths: Long? = null,
  /**
   * The shop's own labels, comma separated. Kept as one column rather than a join table
   * because nothing queries across them: they group a list of a few hundred products on
   * a phone, and a shop that needs more than that needs a different app.
   */
  val tags: String = "",
  val vatApplicable: Boolean,
  val active: Boolean,
  val updatedAt: Long,
  /** True for a row created here that the server has not been told about yet. */
  val pendingUpload: Boolean = false,
)

/**
 * A basket the shop bills over and over.
 *
 * A meat counter rings up "khasi ko masu" forty times a day with only the weight
 * changing; a kirana has four things that go together. A template is that basket with its
 * usual quantity already in it, so the shopkeeper edits one number instead of typing the
 * whole bill again.
 *
 * `usedCount` orders them. The row on the home screen is the shop's own muscle memory,
 * and the one they reach for most should be the one on the left.
 */
@Entity(tableName = "bill_template")
data class BillTemplateEntity(
  @PrimaryKey val id: String,
  val name: String,
  val usedCount: Int = 0,
  val updatedAt: Long,
)

@Entity(
  tableName = "bill_template_line",
  foreignKeys = [
    ForeignKey(
      entity = BillTemplateEntity::class,
      parentColumns = ["id"],
      childColumns = ["templateId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("templateId")],
)
data class BillTemplateLineEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val templateId: String,
  val lineNo: Int,
  /** Null for a line typed by hand, which is most of them on a meat counter. */
  val itemId: String? = null,
  val description: String,
  val unit: String,
  val quantityMilli: Long,
  val unitPricePaisa: Long,
  val vatApplicable: Boolean,
)

/** A template and its lines, read in one go. */
data class BillTemplate(
  @androidx.room.Embedded val template: BillTemplateEntity,
  @androidx.room.Relation(parentColumn = "id", entityColumn = "templateId")
  val lines: List<BillTemplateLineEntity>,
)

/** The labels on a product, as a list. Empty rather than a list holding one blank. */
val ItemEntity.tagList: List<String>
  get() = tags.split(",").map(String::trim).filter(String::isNotEmpty)

@Entity(tableName = "customer", indices = [Index("name"), Index("phone")])
data class CustomerEntity(
  @PrimaryKey val id: String,
  val name: String,
  val pan: String?,
  val address: String?,
  val phone: String?,
  val email: String?,
  val updatedAt: Long,
  val pendingUpload: Boolean = false,
)

/**
 * Money received against a bill after it was issued.
 *
 * A bill is never edited, so a part payment on a credit sale is a row here rather than a
 * changed total. What a customer still owes is every bill's total, less what was handed
 * over at the counter, less these.
 */
@Entity(
  tableName = "payment",
  indices = [Index("billId"), Index("syncState")],
)
data class PaymentEntity(
  @PrimaryKey val id: String,
  val billId: String,
  val amountPaisa: Long,
  val method: String,
  val receivedAt: Long,
  val miti: String,
  val note: String?,
  val syncState: SyncState = SyncState.PENDING,
)

/** A bill the person holding this phone kept after scanning it. Customer mode. */
@Entity(tableName = "wallet_bill")
data class WalletBillEntity(
  @PrimaryKey val shareToken: String,
  val invoiceNumber: String,
  val sellerName: String,
  val sellerPan: String,
  val sellerAddress: String?,
  val issuedAt: Long,
  val miti: String,
  val totalPaisa: Long,
  val status: String,
  val paymentMethod: String,
  /** The whole bill as it came back, so the wallet reads offline once it is saved. */
  val payloadJson: String,
  val savedAt: Long = System.currentTimeMillis(),
)

/**
 * A payment QR the shop already has, photographed once and shown at the counter.
 *
 * The image is the shop's own: the one printed on the card beside the till, or the one
 * eSewa and Khalti generate in their merchant apps. Storing a picture rather than
 * building the code ourselves is deliberate. A QR that moves money carries a merchant
 * identifier issued by the payment provider, and one assembled from guesses would scan
 * cleanly and pay nobody.
 */
@Entity(tableName = "payment_qr", indices = [Index("method", unique = true)])
data class PaymentQrEntity(
  @PrimaryKey val id: String,
  /** One of PaymentQrMethod. Stored as a name so an unknown one survives a downgrade. */
  val method: String,
  /** What the shop calls it, when the method's own name is not enough. */
  val label: String?,
  /** File inside the app's own storage. The picked image is copied, never referenced. */
  val imagePath: String,
  /**
   * What the code says, when it is known: scanned off the shop's own printed QR, or
   * built from the number they typed. Null for a QR that only exists as a photograph,
   * which is the one case where the picture is the original and cannot be redrawn.
   */
  val payload: String? = null,
  val savedAt: Long,
)
