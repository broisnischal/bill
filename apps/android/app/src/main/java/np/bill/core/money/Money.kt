package np.bill.core.money

import kotlin.math.abs

/**
 * Money on the device.
 *
 * Amounts are integer paisa and quantities are thousandths of a unit, exactly as on the
 * server, so a total shown on the phone, printed on the roll and filed by the API is the
 * same number. No float ever touches an amount.
 */

/** Parses what a shopkeeper types, "1,250.50", into paisa. Null when it is not money. */
fun parsePaisa(input: String): Long? {
  val text = input.replace(",", "").trim()
  if (text.isEmpty() || text == "-") return null
  if (!PAISA_PATTERN.matches(text)) return null

  val negative = text.startsWith("-")
  val body = text.removePrefix("-")
  val rupees = body.substringBefore('.').ifEmpty { "0" }
  val paisa = body.substringAfter('.', "").padEnd(2, '0')
  val value = rupees.toLong() * 100 + paisa.toLong()
  return if (negative) -value else value
}

/** Parses a quantity like "2.5" into thousandths. Null when it is not a quantity. */
fun parseQuantityMilli(input: String): Long? {
  val text = input.replace(",", "").trim()
  if (text.isEmpty()) return null
  if (!QUANTITY_PATTERN.matches(text)) return null

  val whole = text.substringBefore('.').ifEmpty { "0" }
  val fraction = text.substringAfter('.', "").padEnd(3, '0')
  return whole.toLong() * 1000 + fraction.toLong()
}

private val PAISA_PATTERN = Regex("""^-?\d*(\.\d{0,2})?$""")
private val QUANTITY_PATTERN = Regex("""^\d*(\.\d{0,3})?$""")

/**
 * "1,25,050.50" — grouped the Nepali way, in lakh and crore, which is how a bill reads
 * to the person holding it.
 */
fun formatPaisa(paisa: Long): String {
  val negative = paisa < 0
  val absolute = abs(paisa)
  val rupees = absolute / 100
  val remainder = absolute % 100

  val digits = rupees.toString()
  val grouped = if (digits.length <= 3) {
    digits
  } else {
    val last3 = digits.takeLast(3)
    val rest = digits.dropLast(3)
    buildString {
      // Everything above the last three digits groups in twos: 1,23,45,678.
      val head = rest.reversed().chunked(2).joinToString(",").reversed()
      append(head)
      append(',')
      append(last3)
    }
  }
  return buildString {
    if (negative) append('-')
    append(grouped)
    append('.')
    append(remainder.toString().padStart(2, '0'))
  }
}

/** The plain decimal string the API and CBMS expect, e.g. "1250.50". */
fun paisaToDecimalString(paisa: Long): String {
  val negative = paisa < 0
  val absolute = abs(paisa)
  return buildString {
    if (negative) append('-')
    append(absolute / 100)
    append('.')
    append((absolute % 100).toString().padStart(2, '0'))
  }
}

fun formatQuantity(quantityMilli: Long): String {
  val whole = quantityMilli / 1000
  val fraction = (quantityMilli % 1000).toString().padStart(3, '0').trimEnd('0')
  return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

private val ONES = arrayOf(
  "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
  "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
  "Eighteen", "Nineteen",
)
private val TENS = arrayOf(
  "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety",
)

private fun twoDigits(value: Long): String {
  if (value < 20) return ONES[value.toInt()]
  val tens = TENS[(value / 10).toInt()]
  val ones = ONES[(value % 10).toInt()]
  return if (ones.isEmpty()) tens else "$tens $ones"
}

private fun threeDigits(value: Long): String {
  val hundreds = value / 100
  val rest = value % 100
  val parts = mutableListOf<String>()
  if (hundreds > 0) parts += "${ONES[hundreds.toInt()]} Hundred"
  if (rest > 0) parts += twoDigits(rest)
  return parts.joinToString(" ")
}

/** Spells a rupee amount on the Nepali scale: crore, lakh, thousand, hundred. */
private fun rupeesToWords(rupees: Long): String {
  if (rupees == 0L) return "Zero"
  val parts = mutableListOf<String>()
  var remaining = rupees
  for ((size, name) in listOf(10_000_000L to "Crore", 100_000L to "Lakh", 1_000L to "Thousand")) {
    val count = remaining / size
    if (count > 0) {
      // Above 99 crore the count itself is spelled out, the way Nepali accounting reads it.
      val head = if (size == 10_000_000L && count > 99) rupeesToWords(count) else threeDigits(count)
      parts += "$head $name"
      remaining %= size
    }
  }
  if (remaining > 0) parts += threeDigits(remaining)
  return parts.joinToString(" ")
}

/**
 * "Rupees One Thousand Two Hundred Fifty and Fifty Paisa Only" — the amount in words a
 * tax invoice has to carry. Written on the device so an offline bill prints complete.
 */
fun amountInWords(paisa: Long): String {
  val negative = paisa < 0
  val absolute = abs(paisa)
  val rupees = absolute / 100
  val remainder = absolute % 100
  val head = "${if (negative) "Minus " else ""}Rupees ${rupeesToWords(rupees)}"
  return if (remainder > 0) "$head and ${twoDigits(remainder)} Paisa Only" else "$head Only"
}
