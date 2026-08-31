package np.bill.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ISO-8601 in UTC, the form the API speaks. Written by hand rather than with java.time
 * so the parsing cost stays trivial on a slow phone and the format is pinned.
 */
object Iso8601 {

  private val formatter = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
  }

  fun format(epochMillis: Long): String = formatter.get()!!.format(Date(epochMillis))

  /** Parses what the server sends, with or without fractional seconds. Null if unreadable. */
  fun parse(value: String): Long? {
    val normalised = value.trim().replace("+00:00", "Z")
    val withMillis = if (normalised.contains('.')) normalised else normalised.replace("Z", ".000Z")
    return runCatching { formatter.get()!!.parse(withMillis)?.time }.getOrNull()
  }
}
