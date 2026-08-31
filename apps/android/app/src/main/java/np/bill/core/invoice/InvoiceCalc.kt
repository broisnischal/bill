package np.bill.core.invoice

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Invoice arithmetic, ported line for line from the server.
 *
 * This is the one piece of logic that genuinely has to exist twice. A till that is
 * offline prints a total the customer walks away with, and the server checks that total
 * when the bill finally syncs; if the two calculations ever disagree the bill is refused
 * and the shop has to raise a credit note. So the rounding, the discount split and the
 * order of operations here follow `src/lib/invoice/calc.ts` exactly, and the shared test
 * vectors in InvoiceCalcTest keep them that way.
 */

/** Half-up rounding, the convention Nepali VAT returns are filed with. */
fun roundPaisa(value: Double): Long =
  if (value < 0) -Math.round(-value) else Math.round(value)

data class LineInput(
  val itemId: String? = null,
  val description: String,
  val hsCode: String? = null,
  val unit: String = "pcs",
  val quantityMilli: Long,
  val unitPricePaisa: Long,
  val discountPaisa: Long = 0,
  val vatApplicable: Boolean = true,
)

data class ComputedLine(
  val lineNo: Int,
  val input: LineInput,
  val grossPaisa: Long,
  val lineTotalPaisa: Long,
)

data class InvoiceTotals(
  val lines: List<ComputedLine>,
  val subTotalPaisa: Long,
  val discountPaisa: Long,
  val taxableAmountPaisa: Long,
  val nonTaxableAmountPaisa: Long,
  val vatAmountPaisa: Long,
  val totalPaisa: Long,
)

/** Rule 18 caps an abbreviated tax invoice at NPR 10,000. */
const val ABBREVIATED_INVOICE_LIMIT_PAISA = 10_000L * 100

/**
 * Totals a bill.
 *
 * A line's gross is quantity times unit price with its own discount taken off first. An
 * invoice-level discount is then split across the lines in proportion to what is left,
 * the remainder landing on the last line so the parts always add back up to the whole.
 * VAT is charged on the discounted taxable value, which is what Rule 17 asks for.
 */
fun computeInvoice(
  lines: List<LineInput>,
  invoiceDiscountPaisa: Long = 0,
  vatRateBp: Int = 1300,
): InvoiceTotals {
  val computed = lines.mapIndexed { index, line ->
    val grossPaisa = roundPaisa(line.quantityMilli.toDouble() * line.unitPricePaisa / 1000.0)
    ComputedLine(
      lineNo = index + 1,
      input = line,
      grossPaisa = grossPaisa,
      lineTotalPaisa = max(0L, grossPaisa - line.discountPaisa),
    )
  }

  val subTotalPaisa = computed.sumOf { it.lineTotalPaisa }
  val discountPaisa = min(max(0L, invoiceDiscountPaisa), subTotalPaisa)

  var allocated = 0L
  val shares = computed.mapIndexed { index, line ->
    when {
      discountPaisa == 0L || subTotalPaisa == 0L -> 0L
      index == computed.lastIndex -> discountPaisa - allocated
      else -> {
        val share = floor(line.lineTotalPaisa.toDouble() * discountPaisa / subTotalPaisa).toLong()
        allocated += share
        share
      }
    }
  }

  var taxableAmountPaisa = 0L
  var nonTaxableAmountPaisa = 0L
  computed.forEachIndexed { index, line ->
    val net = line.lineTotalPaisa - shares[index]
    if (line.input.vatApplicable) taxableAmountPaisa += net else nonTaxableAmountPaisa += net
  }

  val vatAmountPaisa = roundPaisa(taxableAmountPaisa.toDouble() * vatRateBp / 10_000.0)

  return InvoiceTotals(
    lines = computed,
    subTotalPaisa = subTotalPaisa,
    discountPaisa = discountPaisa,
    taxableAmountPaisa = taxableAmountPaisa,
    nonTaxableAmountPaisa = nonTaxableAmountPaisa,
    vatAmountPaisa = vatAmountPaisa,
    totalPaisa = taxableAmountPaisa + nonTaxableAmountPaisa + vatAmountPaisa,
  )
}

/**
 * The printed number for a bill, formatted the way the server formats it. The device
 * builds this itself because the paper has to carry the final number while offline; the
 * server rebuilds it on sync and refuses anything that does not match.
 */
fun formatInvoiceNumber(
  prefix: String,
  fiscalYear: String,
  invoiceType: String,
  sequence: Int,
): String {
  val head = listOfNotNull(
    prefix.ifBlank { null },
    if (invoiceType == "credit_note") "CN" else null,
  ).joinToString("-")
  val body = "$fiscalYear-${sequence.toString().padStart(6, '0')}"
  return if (head.isEmpty()) body else "$head-$body"
}

/** Guards against a bill whose lines cancel out; a zero bill is never issued. */
fun InvoiceTotals.isIssuable(): Boolean = totalPaisa > 0 && abs(totalPaisa) < Long.MAX_VALUE / 2
