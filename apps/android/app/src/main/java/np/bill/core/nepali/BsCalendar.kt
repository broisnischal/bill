package np.bill.core.nepali

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * The Bikram Sambat calendar, on the device.
 *
 * A bill written with no network still has to carry the right miti and the right fiscal
 * year, so the conversion cannot be a server call. The month-length table and the epoch
 * are the same ones the web app converts with, which is what makes a bill numbered on a
 * phone land in the same series as one numbered in a browser.
 *
 * BS 2000/01/01 is 14 April 1943.
 */
object BsCalendar {
  const val EPOCH_YEAR = 2000
  const val LAST_YEAR = 2090

  /** Days in each BS month, Baisakh first, for years 2000 through 2090. */
  private val MONTH_DAYS = arrayOf(
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2000
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2001
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2002
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2003
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2004
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2005
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2006
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2007
    intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31), // 2008
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2009
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2010
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2011
    intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30), // 2012
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2013
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2014
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2015
    intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30), // 2016
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2017
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2018
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2019
    intArrayOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30), // 2020
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2021
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30), // 2022
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2023
    intArrayOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30), // 2024
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2025
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2026
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2027
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2028
    intArrayOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30), // 2029
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2030
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2031
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2032
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2033
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2034
    intArrayOf(30, 32, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31), // 2035
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2036
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2037
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2038
    intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30), // 2039
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2040
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2041
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2042
    intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30), // 2043
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2044
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2045
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2046
    intArrayOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30), // 2047
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2048
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30), // 2049
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2050
    intArrayOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30), // 2051
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2052
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30), // 2053
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2054
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2055
    intArrayOf(31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30), // 2056
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2057
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2058
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2059
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2060
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2061
    intArrayOf(30, 32, 31, 32, 31, 31, 29, 30, 29, 30, 29, 31), // 2062
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2063
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2064
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2065
    intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31), // 2066
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2067
    intArrayOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2068
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2069
    intArrayOf(31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30), // 2070
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2071
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30), // 2072
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2073
    intArrayOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30), // 2074
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2075
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30), // 2076
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2077
    intArrayOf(31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30), // 2078
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2079
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30), // 2080
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2081
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2082
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2083
    intArrayOf(31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31), // 2084
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31), // 2085
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30), // 2086
    intArrayOf(31, 31, 32, 31, 31, 31, 30, 30, 29, 30, 30, 30), // 2087
    intArrayOf(30, 31, 32, 32, 30, 31, 30, 30, 29, 30, 30, 30), // 2088
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30), // 2089
    intArrayOf(30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30), // 2090
  )

  val MONTHS_EN = arrayOf(
    "Baisakh", "Jestha", "Ashad", "Shrawan", "Bhadra", "Ashwin",
    "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra",
  )

  val MONTHS_NE = arrayOf(
    "बैशाख", "जेठ", "असार", "श्रावण", "भाद्र", "आश्विन",
    "कार्तिक", "मंसिर", "पौष", "माघ", "फाल्गुन", "चैत्र",
  )

  /** Nepal Standard Time is UTC+05:45 all year. A bill date is Kathmandu wall clock. */
  const val NPT_OFFSET_MINUTES = 5 * 60 + 45

  fun daysInMonth(year: Int, month: Int): Int {
    require(year in EPOCH_YEAR..LAST_YEAR) { "BS year $year is outside the calendar table" }
    require(month in 1..12) { "BS month $month is out of range" }
    return MONTH_DAYS[year - EPOCH_YEAR][month - 1]
  }

  /** Days from BS 2000/01/01 to this date, counting the first day as zero. */
  private fun daysFromEpoch(date: BsDate): Int {
    var days = 0
    for (year in EPOCH_YEAR until date.year) days += MONTH_DAYS[year - EPOCH_YEAR].sum()
    for (month in 1 until date.month) days += daysInMonth(date.year, month)
    return days + date.day - 1
  }

  private fun fromDaysSinceEpoch(daysPassed: Int): BsDate {
    require(daysPassed >= 0) { "Date is before the start of the BS calendar table" }
    var remaining = daysPassed
    var year = EPOCH_YEAR
    while (year <= LAST_YEAR) {
      val yearLength = MONTH_DAYS[year - EPOCH_YEAR].sum()
      if (remaining < yearLength) break
      remaining -= yearLength
      year++
    }
    require(year <= LAST_YEAR) { "Date is past the end of the BS calendar table" }

    var month = 1
    while (remaining >= daysInMonth(year, month)) {
      remaining -= daysInMonth(year, month)
      month++
    }
    return BsDate(year, month, remaining + 1)
  }

  /** Midnight UTC on 14 April 1943, the Gregorian day BS 2000/01/01 falls on. */
  private val epochUtcMillis: Long = utcMillis(1943, 4, 14)

  private fun utcMillis(year: Int, month: Int, day: Int): Long {
    val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
    calendar.clear()
    calendar.set(year, month - 1, day)
    return calendar.timeInMillis
  }

  private const val DAY_MILLIS = 24L * 60 * 60 * 1000

  /** The Kathmandu wall-clock date and time for an instant. */
  fun nptParts(epochMillis: Long): NptParts {
    val shifted = GregorianCalendar(TimeZone.getTimeZone("UTC"))
    shifted.timeInMillis = epochMillis + NPT_OFFSET_MINUTES * 60_000L
    return NptParts(
      year = shifted.get(Calendar.YEAR),
      month = shifted.get(Calendar.MONTH) + 1,
      day = shifted.get(Calendar.DAY_OF_MONTH),
      hour = shifted.get(Calendar.HOUR_OF_DAY),
      minute = shifted.get(Calendar.MINUTE),
      second = shifted.get(Calendar.SECOND),
    )
  }

  /** The Bikram Sambat date an instant falls on in Kathmandu. */
  fun toBs(epochMillis: Long): BsDate {
    val parts = nptParts(epochMillis)
    val days = ((utcMillis(parts.year, parts.month, parts.day) - epochUtcMillis) / DAY_MILLIS).toInt()
    return fromDaysSinceEpoch(days)
  }

  /** The Gregorian date a BS date starts on, as midnight UTC. */
  fun toAdUtcMillis(date: BsDate): Long = epochUtcMillis + daysFromEpoch(date) * DAY_MILLIS

  /**
   * The fiscal year an instant falls in, in IRD notation: "2082.083". The year runs
   * Shrawan 1 to the end of Ashad, so BS months 4-12 open it and 1-3 close the one before.
   */
  fun fiscalYearFor(epochMillis: Long): String = fiscalYearFor(toBs(epochMillis))

  fun fiscalYearFor(date: BsDate): String {
    val startYear = if (date.month >= 4) date.year else date.year - 1
    return "$startYear.${(startYear + 1).toString().substring(1)}"
  }

  private val NEPALI_DIGITS = charArrayOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')

  /** Rewrites ASCII digits as Devanagari, for the Nepali side of a printed bill. */
  fun toNepaliDigits(value: String): String = buildString(value.length) {
    for (character in value) {
      append(if (character in '0'..'9') NEPALI_DIGITS[character - '0'] else character)
    }
  }
}

data class NptParts(
  val year: Int,
  val month: Int,
  val day: Int,
  val hour: Int,
  val minute: Int,
  val second: Int,
)

/** A Bikram Sambat date. Month is 1-12 with Baisakh first, matching `invoice.miti`. */
data class BsDate(val year: Int, val month: Int, val day: Int) : Comparable<BsDate> {

  /**
   * `YYYY-MM-DD`, the form stored on a bill and compared against.
   *
   * Formatted against ROOT, not the phone's locale. `String.format` renders digits in the
   * default locale's numbering system, so with the app in Nepali this produced
   * "२०८३-०५-१३" — which is the right date and the wrong string: it never matches
   * today's miti, never falls inside a date filter, and is not what the IRD expects on a
   * bill. Anything a machine reads is formatted here; the Devanagari version is a
   * display concern and lives in [formatLong].
   */
  override fun toString(): String =
    String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month, day)

  /**
   * The date as a person reads it. Follows the app's language, so a Nepali UI gets
   * Nepali month names and Devanagari digits and an English one does not.
   */
  fun formatLong(nepali: Boolean = nepaliByDefault()): String {
    val name = if (nepali) BsCalendar.MONTHS_NE[month - 1] else BsCalendar.MONTHS_EN[month - 1]
    val text = "$day $name $year"
    return if (nepali) BsCalendar.toNepaliDigits(text) else text
  }

  private fun nepaliByDefault(): Boolean =
    java.util.Locale.getDefault().language == "ne"

  override fun compareTo(other: BsDate): Int =
    compareValuesBy(this, other, BsDate::year, BsDate::month, BsDate::day)

  companion object {
    fun now(epochMillis: Long = System.currentTimeMillis()): BsDate = BsCalendar.toBs(epochMillis)

    /** Parses `YYYY-MM-DD`. Returns null rather than throwing, since users type these. */
    fun parse(value: String): BsDate? {
      val parts = value.trim().split("-")
      if (parts.size != 3) return null
      val year = parts[0].toIntOrNull() ?: return null
      val month = parts[1].toIntOrNull() ?: return null
      val day = parts[2].toIntOrNull() ?: return null
      if (year !in BsCalendar.EPOCH_YEAR..BsCalendar.LAST_YEAR) return null
      if (month !in 1..12) return null
      if (day < 1 || day > BsCalendar.daysInMonth(year, month)) return null
      return BsDate(year, month, day)
    }
  }
}
