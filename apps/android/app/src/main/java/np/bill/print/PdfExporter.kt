package np.bill.print

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The A4 invoice as a PDF, for a shop with no thermal printer and for sending on WhatsApp
 * or Viber, which is how most Nepali shops actually pass a copy on.
 *
 * The document is drawn straight onto the page canvas, so the text stays vector: it is
 * searchable, it prints crisply at any size, and the file is a few kilobytes rather than
 * a megabyte of pixels. The archived copy an auditor reads is still the one the server
 * renders; this is the same layout, produced on the device so it works with no network.
 */
@Singleton
class PdfExporter @Inject constructor(@ApplicationContext private val context: Context) {

  /**
   * The 80mm roll, as a PDF.
   *
   * The same receipt the thermal printer produces, on a page the width of the paper and
   * as long as the bill needs. A shop with no printer sends this on Viber and the
   * customer sees exactly what they would have been handed; printing it from a phone's
   * print dialogue onto a roll comes out right too, because the page is the roll.
   *
   * 80mm at 72dpi is 226.8pt. The printable width is a little under the paper width,
   * which is why the renderer works to 576 dots and this scales that onto the page.
   */
  suspend fun exportReceipt(fileName: String, bitmap: android.graphics.Bitmap): Uri =
    withContext(Dispatchers.IO) {
      val pageWidth = RECEIPT_WIDTH_PT
      val scale = pageWidth / bitmap.width
      val pageHeight = bitmap.height * scale

      val document = PdfDocument()
      val page = document.startPage(
        PdfDocument.PageInfo.Builder(
          pageWidth.toInt(),
          kotlin.math.ceil(pageHeight).toInt(),
          1,
        ).create(),
      )
      page.canvas.save()
      page.canvas.scale(scale, scale)
      page.canvas.drawBitmap(bitmap, 0f, 0f, null)
      page.canvas.restore()
      document.finishPage(page)

      write(document, fileName)
    }

  suspend fun export(fileName: String, input: A4Invoice.Input): Uri = withContext(Dispatchers.IO) {
    val renderer = A4Invoice()
    val document = PdfDocument()

    // Measure first: a bill with thirty lines runs onto a second page.
    val height = renderer.draw(null, input)
    val pages = maxOf(1, kotlin.math.ceil(height / A4Invoice.PAGE_HEIGHT).toInt())

    for (index in 0 until pages) {
      val page = document.startPage(
        PdfDocument.PageInfo.Builder(
          A4Invoice.PAGE_WIDTH.toInt(),
          A4Invoice.PAGE_HEIGHT.toInt(),
          index + 1,
        ).create(),
      )
      page.canvas.save()
      // Each page shows its own slice of one continuous drawing.
      page.canvas.translate(0f, -index * A4Invoice.PAGE_HEIGHT)
      renderer.draw(page.canvas, input)
      page.canvas.restore()
      document.finishPage(page)
    }

    write(document, fileName)
  }

  private fun write(document: PdfDocument, fileName: String): Uri {
    val directory = File(context.cacheDir, "bills").apply { mkdirs() }
    val file = File(directory, fileName)
    file.outputStream().use(document::writeTo)
    document.close()
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
  }

  private companion object {
    /** 80mm in PostScript points, the unit PdfDocument pages are measured in. */
    const val RECEIPT_WIDTH_PT = 226.8f
  }
}
