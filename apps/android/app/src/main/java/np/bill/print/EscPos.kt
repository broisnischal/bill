package np.bill.print

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * ESC/POS, the command language every cheap 58mm and 80mm thermal printer speaks.
 *
 * The receipt is sent as a raster image rather than as text. Nepali shops print bills
 * carrying Devanagari, and the character ROM in these printers has no Devanagari
 * codepage, so text mode would print the shop's own name as boxes. Rendering to a bitmap
 * and shipping the pixels prints exactly what was on screen, in any script.
 */
object EscPos {

  private val ESC = 0x1B.toByte()
  private val GS = 0x1D.toByte()

  /** Wakes the printer and clears whatever the last job left set. */
  fun initialise(): ByteArray = byteArrayOf(ESC, '@'.code.toByte())

  fun feed(lines: Int): ByteArray = byteArrayOf(ESC, 'd'.code.toByte(), lines.toByte())

  /** Full cut where the printer has a cutter; printers without one ignore it. */
  fun cut(): ByteArray = byteArrayOf(GS, 'V'.code.toByte(), 66, 0)

  /**
   * A monochrome bitmap as a GS v 0 raster.
   *
   * The bitmap is sent in horizontal strips so a long receipt does not have to fit in
   * one command, which is what stalls the cheaper controllers. Width is padded to a byte
   * boundary because the format packs eight pixels per byte, most significant bit first.
   */
  fun raster(bitmap: Bitmap, stripHeight: Int = 128): ByteArray {
    val width = bitmap.width
    val bytesPerRow = (width + 7) / 8
    val output = ByteArrayOutputStream(bytesPerRow * bitmap.height + 64)

    val pixels = IntArray(width * bitmap.height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.height)

    var top = 0
    while (top < bitmap.height) {
      val height = minOf(stripHeight, bitmap.height - top)

      output.write(byteArrayOf(GS, 'v'.code.toByte(), '0'.code.toByte(), 0))
      output.write(bytesPerRow and 0xFF)
      output.write((bytesPerRow shr 8) and 0xFF)
      output.write(height and 0xFF)
      output.write((height shr 8) and 0xFF)

      val strip = ByteArray(bytesPerRow * height)
      for (y in 0 until height) {
        val rowOffset = (top + y) * width
        for (x in 0 until width) {
          // Anything darker than mid grey prints. The renderer draws pure black on white,
          // so this only ever has to resolve antialiased edges.
          val pixel = pixels[rowOffset + x]
          val luminance = ((pixel shr 16 and 0xFF) * 299 +
            (pixel shr 8 and 0xFF) * 587 +
            (pixel and 0xFF) * 114) / 1000
          if (luminance < 128) {
            val index = y * bytesPerRow + (x shr 3)
            strip[index] = (strip[index].toInt() or (0x80 shr (x and 7))).toByte()
          }
        }
      }
      output.write(strip)
      top += height
    }

    return output.toByteArray()
  }

  /** Everything one receipt needs, in the order a printer expects it. */
  fun job(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().apply {
    write(initialise())
    write(raster(bitmap))
    write(feed(4))
    write(cut())
  }.toByteArray()
}
