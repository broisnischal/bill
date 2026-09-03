package np.bill.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import np.bill.core.money.formatPaisa
import np.bill.core.money.formatQuantity
import np.bill.core.nepali.BsDate
import np.bill.data.db.BillEntity
import np.bill.data.db.BillLineEntity

/**
 * Draws the 80mm receipt.
 *
 * The layout is the one Rule 17 asks for, in the order a customer reads it: who issued
 * the bill and their PAN, the number and the miti, the lines, the tax split, the total in
 * figures and in words, and the QR that files the bill on the buyer's phone.
 *
 * It renders to a bitmap so it can go to a thermal printer as pixels, which is the only
 * way Devanagari survives the trip.
 */
class ReceiptRenderer(private val paperWidthPx: Int = 576) {

  /** The seller's details as they must appear on the paper. */
  data class Seller(
    val name: String,
    val nameNepali: String?,
    val pan: String,
    val vatRegistered: Boolean,
    val address: String,
    val phone: String?,
    val footerNote: String?,
  )

  private val margin = 12f
  private val contentWidth get() = paperWidthPx - margin * 2

  private fun paint(size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = size
      textAlign = align
      // The system font carries Devanagari on every Android the app supports.
      typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

  fun render(
    seller: Seller,
    bill: BillEntity,
    lines: List<BillLineEntity>,
    copyNumber: Int,
    qrPayload: String,
  ): Bitmap {
    // Two passes: measure with a throwaway canvas, then draw at the exact height, so a
    // long bill never gets clipped and a short one wastes no paper.
    val height = draw(null, seller, bill, lines, copyNumber, qrPayload)
    val bitmap = createBitmap(paperWidthPx, height.toInt() + 24)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    draw(canvas, seller, bill, lines, copyNumber, qrPayload)
    return bitmap
  }

  /** Draws when given a canvas, measures when not. Returns the height used. */
  private fun draw(
    canvas: Canvas?,
    seller: Seller,
    bill: BillEntity,
    lines: List<BillLineEntity>,
    copyNumber: Int,
    qrPayload: String,
  ): Float {
    val big = paint(30f, bold = true, align = Paint.Align.CENTER)
    val heading = paint(22f, bold = true, align = Paint.Align.CENTER)
    val body = paint(20f)
    val bodyRight = paint(20f, align = Paint.Align.RIGHT)
    val bodyBold = paint(20f, bold = true)
    val bodyBoldRight = paint(20f, bold = true, align = Paint.Align.RIGHT)
    val small = paint(17f)
    val smallCentre = paint(17f, align = Paint.Align.CENTER)

    var y = margin + 30f
    val centre = paperWidthPx / 2f
    val right = paperWidthPx - margin

    fun centred(text: String, p: Paint, gap: Float = 26f) {
      canvas?.drawText(text, centre, y, p)
      y += gap
    }

    fun row(left: String, rightText: String, p: Paint = body, pRight: Paint = bodyRight, gap: Float = 24f) {
      canvas?.drawText(left, margin, y, p)
      canvas?.drawText(rightText, right, y, pRight)
      y += gap
    }

    fun rule(gap: Float = 16f) {
      canvas?.drawLine(margin, y - 8f, right, y - 8f, paint(1f).apply { strokeWidth = 1.5f })
      y += gap
    }

    seller.nameNepali?.takeIf { it.isNotBlank() }?.let { centred(it, big, 34f) }
    centred(seller.name, if (seller.nameNepali.isNullOrBlank()) big else heading, 26f)
    centred(seller.address, small, 22f)
    seller.phone?.takeIf { it.isNotBlank() }?.let { centred("Tel: $it", small, 22f) }
    centred("${if (seller.vatRegistered) "VAT" else "PAN"}: ${seller.pan}", small, 26f)

    rule()
    centred(
      when (bill.invoiceType) {
        "credit_note" -> "CREDIT NOTE"
        "abbreviated_tax_invoice" ->
          if (seller.vatRegistered) "ABBREVIATED TAX INVOICE" else "ABBREVIATED INVOICE"
        // Not a tax invoice unless the shop is registered to charge tax.
        else -> if (seller.vatRegistered) "TAX INVOICE" else "INVOICE"
      },
      heading,
      24f,
    )
    centred(
      when (bill.invoiceType) {
        "credit_note" -> "क्रेडिट नोट"
        "abbreviated_tax_invoice" ->
          if (seller.vatRegistered) "संक्षिप्त कर बीजक" else "संक्षिप्त बीजक"
        else -> if (seller.vatRegistered) "कर बीजक" else "बीजक"
      },
      smallCentre,
      26f,
    )
    centred(if (copyNumber <= 1) "Original" else "Copy of Original (#$copyNumber)", small, 24f)
    rule()

    row("No.", bill.invoiceNumber, bodyBold, bodyBoldRight)
    val bs = BsDate.parse(bill.miti)
    row("Miti", bs?.formatLong() ?: bill.miti)
    row("Buyer", bill.buyerName.take(24))
    bill.buyerPan?.takeIf { it.isNotBlank() }?.let { row("Buyer PAN", it) }
    bill.buyerPhone?.takeIf { it.isNotBlank() }?.let { row("Phone", it) }
    rule()

    // Each line prints on two rows: the description, then quantity, rate and amount.
    // A single row cannot hold a Nepali item name and three numbers at 80mm.
    for (line in lines) {
      canvas?.drawText(line.description.take(34), margin, y, bodyBold)
      y += 22f
      val quantity = "${formatQuantity(line.quantityMilli)} ${line.unit}"
      val rate = formatPaisa(line.unitPricePaisa)
      canvas?.drawText("$quantity x $rate${if (!line.vatApplicable) "  (exempt)" else ""}", margin, y, small)
      canvas?.drawText(formatPaisa(line.lineTotalPaisa), right, y, bodyRight)
      y += 26f
    }

    rule()
    row("Sub total", formatPaisa(bill.subTotalPaisa))
    if (bill.discountPaisa > 0) row("Discount", "-${formatPaisa(bill.discountPaisa)}")
    if (bill.nonTaxableAmountPaisa > 0) row("Exempt", formatPaisa(bill.nonTaxableAmountPaisa))
    if (bill.vatRateBp > 0) {
      row("Taxable", formatPaisa(bill.taxableAmountPaisa))
      row("VAT @ ${bill.vatRateBp / 100}%", formatPaisa(bill.vatAmountPaisa))
    }
    rule()
    row("TOTAL", "Rs ${formatPaisa(bill.totalPaisa)}", paint(26f, bold = true), paint(26f, bold = true, align = Paint.Align.RIGHT), 32f)

    // The amount in words wraps; a tax invoice has to carry it in full.
    y = wrap(canvas, bill.amountInWords, small, margin, y, contentWidth, 20f)
    y += 6f
    row("Paid by", bill.paymentMethod.replaceFirstChar(Char::uppercase), small, paint(17f, align = Paint.Align.RIGHT), 24f)

    if (bill.status == "cancelled") {
      rule()
      centred("*** CANCELLED ***", heading, 28f)
    }

    rule()
    // Time as well as date: Rule 17 asks for when a bill was issued, not just the day.
    row("Time", timeOf(bill.issuedAt), small, paint(17f, align = Paint.Align.RIGHT), 24f)
    canvas?.drawText("E. & O.E.", margin, y, small)
    y += 24f

    rule()
    centred("Scan to save this bill", small, 24f)

    val qrSize = (paperWidthPx * 0.52f).toInt()
    if (canvas != null) {
      qr(qrPayload, qrSize)?.let { canvas.drawBitmap(it, centre - qrSize / 2f, y, null) }
    }
    y += qrSize + 20f

    seller.footerNote?.takeIf { it.isNotBlank() }?.let {
      y = wrap(canvas, it, smallCentre, centre, y, contentWidth, 20f)
    }
    centred("धन्यवाद / Thank you", small, 26f)

    return y
  }

  private fun timeOf(epochMillis: Long): String {
    val parts = np.bill.core.nepali.BsCalendar.nptParts(epochMillis)
    return String.format(java.util.Locale.ROOT, "%02d:%02d", parts.hour, parts.minute)
  }

  /** Wraps text to the paper width, word by word. Returns the y it finished on. */
  private fun wrap(
    canvas: Canvas?,
    text: String,
    paint: Paint,
    x: Float,
    startY: Float,
    width: Float,
    lineHeight: Float,
  ): Float {
    var y = startY
    val words = text.split(' ')
    var line = StringBuilder()
    for (word in words) {
      val candidate = if (line.isEmpty()) word else "$line $word"
      if (paint.measureText(candidate) > width && line.isNotEmpty()) {
        canvas?.drawText(line.toString(), x, y, paint)
        y += lineHeight
        line = StringBuilder(word)
      } else {
        line = StringBuilder(candidate)
      }
    }
    if (line.isNotEmpty()) {
      canvas?.drawText(line.toString(), x, y, paint)
      y += lineHeight
    }
    return y
  }

  /**
   * The QR a buyer scans. Error correction is set high because it is printed on thermal
   * paper that fades and is often scanned in poor shop light.
   */
  fun qr(payload: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
      payload,
      BarcodeFormat.QR_CODE,
      size,
      size,
      mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
      ),
    )
    val bitmap = createBitmap(size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
      for (x in 0 until size) {
        pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
      }
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    bitmap
  }.getOrNull()
}
