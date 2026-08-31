package np.bill.ui.payments

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import np.bill.R
import np.bill.data.repo.PaymentQrMethod
import np.bill.data.repo.SavedPaymentQr

@StringRes
fun PaymentQrMethod.labelRes(): Int = when (this) {
  PaymentQrMethod.FONEPAY -> R.string.qr_fonepay
  PaymentQrMethod.ESEWA -> R.string.qr_esewa
  PaymentQrMethod.KHALTI -> R.string.qr_khalti
  PaymentQrMethod.BANK -> R.string.qr_bank
}

/**
 * The saved image, decoded once per file.
 *
 * Keyed on the path and the moment it was written, so replacing a QR shows the new one
 * rather than the decoded copy of the file that used to be at that name.
 */
@Composable
fun QrThumbnail(
  qr: SavedPaymentQr,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Fit,
) {
  val bitmap = remember(qr.file.path, qr.file.lastModified()) {
    runCatching { BitmapFactory.decodeFile(qr.file.path) }.getOrNull()
  } ?: return

  Image(
    bitmap = bitmap.asImageBitmap(),
    contentDescription = null,
    modifier = modifier,
    contentScale = contentScale,
  )
}
