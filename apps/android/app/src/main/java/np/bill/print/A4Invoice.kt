package np.bill.print

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import np.bill.core.money.formatPaisa
import np.bill.core.money.formatQuantity
import np.bill.core.nepali.BsCalendar
import np.bill.core.nepali.BsDate
import np.bill.data.db.BillEntity
import np.bill.data.db.BillLineEntity
import np.bill.data.net.StoreDto

/**
 * The A4 tax invoice.
 *
 * Deliberately the same document the web app prints: same header, same column order, same
 * totals block, same signature lines, same legal footer. A shop that bills from a phone
 * and a browser on the same day must not hand out two different-looking bills for the
 * same PAN, and a tax officer comparing them should see one format.
 *
 * It draws onto whatever canvas it is given, which is how it ends up as vector text in
 * the PDF rather than a picture of text. There is no QR here: the QR belongs on the
 * thermal receipt the customer is handed at the counter, not on the archival document.
 */
class A4Invoice(private val pageWidth: Float = 595f) {

  /** A4 at 72dpi, the unit PdfDocument works in. */
  companion object {
    const val PAGE_WIDTH = 595f
    const val PAGE_HEIGHT = 842f
    private const val MARGIN = 34f
  }

  private val contentWidth = pageWidth - MARGIN * 2
  private val right = pageWidth - MARGIN

  private fun paint(
    size: Float,
    weight: Int = Typeface.NORMAL,
    align: Paint.Align = Paint.Align.LEFT,
    colour: Int = INK,
  ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = colour
    textSize = size
    textAlign = align
    typeface = Typeface.create(Typeface.SANS_SERIF, weight)
  }

  private val rule = Paint().apply {
    color = LINE
    strokeWidth = 0.6f
  }

  data class Input(
    val store: StoreDto,
    val bill: BillEntity,
    val lines: List<BillLineEntity>,
    val copyNumber: Int,
    val enteredByName: String,
  )

  /** Draws the invoice, or measures it when canvas is null. Returns the height used. */
  fun draw(canvas: Canvas?, input: Input): Float {
    val (store, bill, lines, copyNumber, enteredBy) = input

    val h1 = paint(15f, Typeface.BOLD)
    val h2 = paint(10.5f, Typeface.BOLD)
    val body = paint(8.5f)
    val bodyBold = paint(8.5f, Typeface.BOLD)
    val bodyRight = paint(8.5f, align = Paint.Align.RIGHT)
    val bodyBoldRight = paint(8.5f, Typeface.BOLD, Paint.Align.RIGHT)
    val muted = paint(8f, colour = MUTED)
    val mutedRight = paint(8f, align = Paint.Align.RIGHT, colour = MUTED)
    val label = paint(6.8f, Typeface.BOLD, colour = MUTED)
    val titleRight = paint(12.5f, Typeface.BOLD, Paint.Align.RIGHT)
    val titleRightNp = paint(9.5f, Typeface.BOLD, Paint.Align.RIGHT)

    var y = MARGIN + 12f

    if (bill.status == "cancelled") {
      val band = paint(11f, Typeface.BOLD, Paint.Align.CENTER, VOID)
      canvas?.drawRect(MARGIN, y - 10f, right, y + 5f, Paint().apply {
        style = Paint.Style.STROKE
        color = VOID
        strokeWidth = 0.8f
      })
      canvas?.drawText("CANCELLED / रद्द गरिएको", pageWidth / 2f, y + 1f, band)
      y += 24f
    }

    // ---- header: who issued it, and what this document is -------------------------
    val headTop = y
    canvas?.drawText(store.name, MARGIN, y, h1)
    y += 14f
    store.nameNepali?.takeIf(String::isNotBlank)?.let {
      canvas?.drawText(it, MARGIN, y, h2)
      y += 12f
    }
    y = wrap(canvas, storeAddress(store), muted, MARGIN, y, contentWidth * 0.6f, 10f)
    listOfNotNull(
      store.phone?.let { "Tel: $it" },
      store.email,
    ).joinToString("  ·  ").takeIf(String::isNotBlank)?.let {
      canvas?.drawText(it, MARGIN, y, muted)
      y += 10f
    }
    val panLabel = if (store.taxpayerType == "vat") "PAN / VAT No" else "PAN No"
    canvas?.drawText("$panLabel: ${store.pan}", MARGIN, y, bodyBold)
    y += 12f

    // The document title sits opposite the shop name, as it does on the web sheet.
    val title = documentTitle(bill.invoiceType, store.taxpayerType == "vat")
    canvas?.drawText(title.first, right, headTop, titleRight)
    canvas?.drawText(title.second, right, headTop + 13f, titleRightNp)
    val copy = if (copyNumber <= 1) "ORIGINAL" else "COPY OF ORIGINAL (#$copyNumber)"
    canvas?.drawText(copy, right, headTop + 26f, paint(7f, Typeface.BOLD, Paint.Align.RIGHT, MUTED))

    y = maxOf(y, headTop + 34f) + 4f
    canvas?.drawLine(MARGIN, y, right, y, Paint().apply { color = INK; strokeWidth = 1.2f })
    y += 12f

    // ---- who it is for, and its identifiers ---------------------------------------
    val metaTop = y
    canvas?.drawText("BILL TO / खरिदकर्ता", MARGIN, y, label)
    y += 11f
    canvas?.drawText(bill.buyerName, MARGIN, y, paint(10f, Typeface.BOLD))
    y += 11f
    bill.buyerAddress?.takeIf(String::isNotBlank)?.let {
      canvas?.drawText(it, MARGIN, y, body); y += 10f
    }
    bill.buyerPhone?.takeIf(String::isNotBlank)?.let {
      canvas?.drawText("Tel: $it", MARGIN, y, body); y += 10f
    }
    canvas?.drawText("PAN: ${bill.buyerPan ?: "-"}", MARGIN, y, body)
    y += 10f

    var metaY = metaTop + 6f
    fun kv(key: String, value: String) {
      canvas?.drawText(key, right - 150f, metaY, mutedRight)
      canvas?.drawText(value, right, metaY, bodyBoldRight)
      metaY += 11f
    }
    kv("Invoice No.", bill.invoiceNumber)
    kv("Miti (BS)", BsDate.parse(bill.miti)?.formatLong() ?: bill.miti)
    kv("Date (AD)", "${adDate(bill.issuedAt)}  ${adTime(bill.issuedAt)}")
    kv("Fiscal Year", bill.fiscalYear)
    kv("Payment", paymentLabel(bill.paymentMethod))

    y = maxOf(y, metaY) + 4f
    canvas?.drawLine(MARGIN, y, right, y, rule)
    y += 14f

    // ---- lines --------------------------------------------------------------------
    val cSn = MARGIN
    val cDesc = MARGIN + contentWidth * 0.06f
    val cHs = MARGIN + contentWidth * 0.44f
    val cQty = MARGIN + contentWidth * 0.62f
    val cRate = MARGIN + contentWidth * 0.75f
    val cDisc = MARGIN + contentWidth * 0.86f
    val cAmt = right

    canvas?.drawText("S.N.", cSn, y, label)
    canvas?.drawText("PARTICULARS / विवरण", cDesc, y, label)
    canvas?.drawText("HS CODE", cHs, y, label)
    canvas?.drawText("QTY", cQty, y, paint(6.8f, Typeface.BOLD, Paint.Align.RIGHT, MUTED))
    canvas?.drawText("RATE", cRate, y, paint(6.8f, Typeface.BOLD, Paint.Align.RIGHT, MUTED))
    canvas?.drawText("DISC.", cDisc, y, paint(6.8f, Typeface.BOLD, Paint.Align.RIGHT, MUTED))
    canvas?.drawText("AMOUNT", cAmt, y, paint(6.8f, Typeface.BOLD, Paint.Align.RIGHT, MUTED))
    y += 5f
    canvas?.drawLine(MARGIN, y, right, y, Paint().apply { color = INK; strokeWidth = 0.8f })
    y += 12f

    for (line in lines) {
      canvas?.drawText(line.lineNo.toString(), cSn, y, body)
      val description = if (line.vatApplicable) line.description else "${line.description}  (exempt)"
      canvas?.drawText(clip(description, body, cHs - cDesc - 6f), cDesc, y, body)
      canvas?.drawText(line.hsCode ?: "-", cHs, y, muted)
      canvas?.drawText("${formatQuantity(line.quantityMilli)} ${line.unit}", cQty, y, bodyRight)
      canvas?.drawText(formatPaisa(line.unitPricePaisa), cRate, y, bodyRight)
      canvas?.drawText(
        if (line.discountPaisa > 0) formatPaisa(line.discountPaisa) else "-",
        cDisc, y, bodyRight,
      )
      canvas?.drawText(formatPaisa(line.lineTotalPaisa), cAmt, y, bodyBoldRight)
      y += 5f
      canvas?.drawLine(MARGIN, y, right, y, rule)
      y += 12f
    }

    y += 6f

    // ---- totals, with the words and notes beside them ------------------------------
    val footTop = y
    var totalsY = footTop
    fun total(key: String, value: String, strong: Boolean = false) {
      val keyPaint = if (strong) paint(9.5f, Typeface.BOLD) else body
      val valuePaint = if (strong) paint(11f, Typeface.BOLD, Paint.Align.RIGHT) else bodyRight
      canvas?.drawText(key, right - 165f, totalsY, keyPaint)
      canvas?.drawText(value, right, totalsY, valuePaint)
      totalsY += if (strong) 16f else 12f
    }

    total("Sub Total", formatPaisa(bill.subTotalPaisa))
    if (bill.discountPaisa > 0) total("Discount", "- ${formatPaisa(bill.discountPaisa)}")
    if (bill.nonTaxableAmountPaisa > 0) {
      total("Non-taxable / Exempt", formatPaisa(bill.nonTaxableAmountPaisa))
    }
    if (bill.vatRateBp > 0) {
      total("Taxable Amount", formatPaisa(bill.taxableAmountPaisa))
      total("VAT @ ${vatRate(bill.vatRateBp)}%", formatPaisa(bill.vatAmountPaisa))
    }
    canvas?.drawLine(right - 168f, totalsY - 8f, right, totalsY - 8f, rule)
    total("Grand Total (NPR)", formatPaisa(bill.totalPaisa), strong = true)

    var wordsY = footTop
    canvas?.drawText("AMOUNT IN WORDS", MARGIN, wordsY, label)
    wordsY += 11f
    wordsY = wrap(canvas, bill.amountInWords, bodyBold, MARGIN, wordsY, contentWidth * 0.5f, 11f)

    bill.notes?.takeIf(String::isNotBlank)?.let {
      wordsY += 4f
      wordsY = wrap(canvas, "Note: $it", body, MARGIN, wordsY, contentWidth * 0.5f, 10f)
    }
    if (bill.status == "cancelled") {
      bill.cancelReason?.takeIf(String::isNotBlank)?.let {
        wordsY += 4f
        wordsY = wrap(canvas, "Reason: $it", body, MARGIN, wordsY, contentWidth * 0.5f, 10f)
      }
    }
    store.bankDetails?.takeIf(String::isNotBlank)?.let {
      wordsY += 4f
      wordsY = wrap(canvas, it, muted, MARGIN, wordsY, contentWidth * 0.5f, 10f)
    }

    y = maxOf(totalsY, wordsY) + 34f

    // ---- signatures ----------------------------------------------------------------
    val signWidth = contentWidth * 0.32f
    canvas?.drawLine(MARGIN, y, MARGIN + signWidth, y, rule)
    canvas?.drawLine(right - signWidth, y, right, y, rule)
    y += 11f
    canvas?.drawText("Received by / प्राप्त गर्ने", MARGIN, y, muted)
    canvas?.drawText("For ${store.name} / अधिकृत हस्ताक्षर", right, y, mutedRight)
    y += 22f

    // ---- legal footer ---------------------------------------------------------------
    canvas?.drawLine(MARGIN, y - 8f, right, y - 8f, rule)
    canvas?.drawText("Prepared by: $enteredBy", MARGIN, y, muted)
    canvas?.drawText("Prints: ${maxOf(bill.printCount, 1)}", MARGIN + contentWidth * 0.35f, y, muted)
    // Errors and Omissions Excepted: the standard reservation on a Nepali invoice.
    canvas?.drawText("E. & O.E.", right, y, mutedRight)
    y += 10f
    y = wrap(
      canvas,
      store.printFooterNote?.takeIf(String::isNotBlank)
        ?: "This is a computer generated invoice.",
      muted,
      MARGIN,
      y,
      contentWidth,
      10f,
    )

    return y + MARGIN
  }

  private fun storeAddress(store: StoreDto) = listOfNotNull(
    store.address.takeIf(String::isNotBlank),
    store.ward?.let { "Ward $it" },
    store.municipality,
    store.district,
    store.province,
  ).joinToString(", ")

  /**
   * A shop that is not registered for VAT does not issue a *tax* invoice.
   *
   * "कर बीजक" is the VAT document Rule 17 describes, and printing it over a PAN-only
   * shop's bill claims a registration they do not have.
   */
  private fun documentTitle(type: String, vatRegistered: Boolean): Pair<String, String> =
    when {
      type == "credit_note" -> "CREDIT NOTE" to "क्रेडिट नोट"
      type == "abbreviated_tax_invoice" && vatRegistered ->
        "ABBREVIATED TAX INVOICE" to "संक्षिप्त कर बीजक"
      type == "abbreviated_tax_invoice" -> "ABBREVIATED INVOICE" to "संक्षिप्त बीजक"
      vatRegistered -> "TAX INVOICE" to "कर बीजक"
      else -> "INVOICE" to "बीजक"
    }

  private fun paymentLabel(method: String) = when (method) {
    "bank" -> "Bank Transfer"
    "esewa" -> "eSewa"
    "connectips" -> "ConnectIPS"
    else -> method.replaceFirstChar(Char::uppercase)
  }

  private fun vatRate(bp: Int) = if (bp % 100 == 0) (bp / 100).toString() else (bp / 100f).toString()

  private fun adDate(epochMillis: Long): String {
    val parts = BsCalendar.nptParts(epochMillis)
    // ROOT: a bill's dates are read by a tax officer and a machine, never localised.
    return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", parts.year, parts.month, parts.day)
  }

  /** The time a bill was issued, which Rule 17 expects alongside the date. */
  private fun adTime(epochMillis: Long): String {
    val parts = BsCalendar.nptParts(epochMillis)
    return String.format(
      java.util.Locale.ROOT,
      "%02d:%02d:%02d",
      parts.hour,
      parts.minute,
      parts.second,
    )
  }

  private fun clip(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end) + "…"
  }

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
    var line = StringBuilder()
    for (word in text.split(' ')) {
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
}

private const val INK = 0xFF111827.toInt()
private const val MUTED = 0xFF6B7280.toInt()
private const val LINE = 0xFFD1D5DB.toInt()
private const val VOID = 0xFFB91C1C.toInt()
