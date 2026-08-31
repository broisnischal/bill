package np.bill.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import np.bill.data.db.BillDatabase
import np.bill.data.db.PaymentQrEntity
import np.bill.payments.QrCodec

/** The wallets a Nepali counter is actually asked for, in the order they get asked. */
enum class PaymentQrMethod(val id: String) {
  FONEPAY("fonepay"),
  ESEWA("esewa"),
  KHALTI("khalti"),
  BANK("bank"),
}

data class SavedPaymentQr(
  val id: String,
  val method: PaymentQrMethod,
  val label: String?,
  val file: File,
  /** What the code says, when it was scanned or typed rather than photographed. */
  val payload: String?,
)

/**
 * The shop's payment QRs, kept as images inside the app's own storage.
 *
 * The picked image is copied rather than referenced. A content URI from the gallery is
 * a permission grant that dies with the process, so a QR that still showed on the screen
 * after a restart would be a QR the shop could not display when a customer was waiting.
 */
@Singleton
class PaymentQrRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  database: BillDatabase,
) {

  private val dao = database.paymentQrs()

  private val directory: File
    get() = File(context.filesDir, "payment-qr").apply { mkdirs() }

  fun observe(): Flow<List<SavedPaymentQr>> = dao.observeAll().map { rows ->
    rows.mapNotNull(::toSaved)
  }

  /**
   * Copies the picked image in and records it against the method, replacing whatever was
   * there. One QR per method: a till showing two eSewa codes is a till whose owner cannot
   * say which account the money went to.
   */
  suspend fun save(method: PaymentQrMethod, source: Uri, label: String?) =
    withContext(Dispatchers.IO) {
      val target = File(directory, "${method.id}.png")
      context.contentResolver.openInputStream(source)?.use { input ->
        target.outputStream().use(input::copyTo)
      } ?: error("Could not read that image")

      dao.upsert(
        PaymentQrEntity(
          id = method.id,
          method = method.name,
          label = label?.takeIf(String::isNotBlank),
          imagePath = target.absolutePath,
          payload = null,
          savedAt = System.currentTimeMillis(),
        ),
      )
    }

  /**
   * Records a code whose contents are known, from the camera or from a typed number, and
   * draws it fresh.
   *
   * Rendering rather than storing the camera frame is the point of this path: what goes
   * on screen at the counter is a clean code at full contrast, not a photograph of a
   * laminated card under a tube light.
   */
  suspend fun saveFromPayload(method: PaymentQrMethod, payload: String, label: String?) =
    withContext(Dispatchers.IO) {
      val target = File(directory, "${method.id}.png")
      QrCodec.render(payload).let { bitmap ->
        target.outputStream().use { out ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
      }

      dao.upsert(
        PaymentQrEntity(
          id = method.id,
          method = method.name,
          label = label?.takeIf(String::isNotBlank),
          imagePath = target.absolutePath,
          payload = payload,
          savedAt = System.currentTimeMillis(),
        ),
      )
    }

  suspend fun remove(method: PaymentQrMethod) = withContext(Dispatchers.IO) {
    dao.delete(method.id)
    File(directory, "${method.id}.png").delete()
    Unit
  }

  /** Rows whose image has gone are dropped rather than shown as a blank square. */
  private fun toSaved(row: PaymentQrEntity): SavedPaymentQr? {
    val method = runCatching { PaymentQrMethod.valueOf(row.method) }.getOrNull() ?: return null
    val file = File(row.imagePath)
    if (!file.exists()) return null
    return SavedPaymentQr(row.id, method, row.label, file, row.payload)
  }
}
