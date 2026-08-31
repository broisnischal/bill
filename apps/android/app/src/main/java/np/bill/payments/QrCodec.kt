package np.bill.payments

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Drawing a QR from a payload.
 *
 * The shop's code is rendered rather than kept as a photograph wherever the payload is
 * known, because a photograph of a code carries the glare, the crease and the angle of
 * the paper it was taken from, and a scanner at arm's length across a counter has to work
 * on the first try.
 */
object QrCodec {

  /** Quiet zone in modules. Below four, scanners start missing the code entirely. */
  private const val MARGIN = 4

  fun render(payload: String, size: Int = 900): Bitmap {
    val matrix = QRCodeWriter().encode(
      payload,
      BarcodeFormat.QR_CODE,
      size,
      size,
      mapOf(
        // High correction: this ends up on a counter, where the code gets smudged, taped
        // over at a corner and photographed by a cracked camera.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to MARGIN,
        EncodeHintType.CHARACTER_SET to "UTF-8",
      ),
    )

    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
      val row = y * matrix.width
      for (x in 0 until matrix.width) {
        pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
      }
    }
    bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    return bitmap
  }
}
