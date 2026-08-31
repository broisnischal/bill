package np.bill

import np.bill.core.geo.Nepal
import np.bill.core.nepali.BsCalendar
import np.bill.core.text.Romanizer
import np.bill.ui.common.convertFinishedWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typing Nepali without a Nepali keyboard.
 *
 * These are the words a shop actually types — what it is called, what it sells, who it
 * sells to — so the cases here are the ones that have to come out right on a bill.
 */
class RomanizerTest {

  @Test
  fun `writes the words a shop puts on a bill`() {
    assertEquals("पसल", Romanizer.toDevanagari("pasal"))
    assertEquals("दुध", Romanizer.toDevanagari("dudh"))
    assertEquals("दूध", Romanizer.toDevanagari("duudh"))
    assertEquals("तेल", Romanizer.toDevanagari("tel"))
    assertEquals("नुन", Romanizer.toDevanagari("nun"))
    assertEquals("किताब", Romanizer.toDevanagari("kitaab"))
  }

  @Test
  fun `a single a is the inherent vowel and aa is the long one`() {
    // This is the rule everyone typing Nepali phonetically already knows, and the one
    // thing about the scheme worth learning: "chamal" is चमल, "chaamal" is चामल.
    assertEquals("चमल", Romanizer.toDevanagari("chamal"))
    assertEquals("चामल", Romanizer.toDevanagari("chaamal"))
    assertEquals("आमा", Romanizer.toDevanagari("aamaa"))
    assertEquals("किराना", Romanizer.toDevanagari("kiraanaa"))
  }

  @Test
  fun `two consonants in a row join with a halanta`() {
    assertEquals("स्कूल", Romanizer.toDevanagari("skool"))
    // Without the halanta this would come out with a vowel between the k and the r.
    assertTrue(Romanizer.toDevanagari("krishna").startsWith("क्"))
  }

  @Test
  fun `the longest spelling wins`() {
    assertEquals("छ", Romanizer.toDevanagari("chha"))
    assertEquals("शहर", Romanizer.toDevanagari("shahar"))
  }

  @Test
  fun `the word being typed is left in Roman until it is finished`() {
    // This is what makes the field usable: you can see and correct what you pressed.
    assertEquals("pas", convertFinishedWords("pas"))
    assertEquals("pasal", convertFinishedWords("pasal"))
    // A space finishes it.
    assertEquals("पसल ", convertFinishedWords("pasal "))
    assertEquals("पसल ki", convertFinishedWords("pasal ki"))
    assertEquals("पसल किराना ", convertFinishedWords("pasal kiraanaa "))
  }

  @Test
  fun `converting twice changes nothing`() {
    // The field runs this on every keystroke over its own previous output, so it has to
    // be idempotent or the text degrades as you type.
    val once = convertFinishedWords("pasal ")
    assertEquals(once, convertFinishedWords(once))
    assertEquals("पसल nu", convertFinishedWords(convertFinishedWords("pasal nu")))
  }

  @Test
  fun `punctuation finishes a word too`() {
    assertEquals("पसल,", convertFinishedWords("pasal,"))
    // Digits stay as typed: an item called "500ml" should not become "५००ml" on a bill
    // whose amounts are all in ASCII.
    assertEquals("पसल-2", convertFinishedWords("pasal-2"))
  }

  @Test
  fun `text that is already Nepali is left alone`() {
    assertEquals("किराना पसल", Romanizer.toDevanagari("किराना पसल"))
  }

  @Test
  fun `digits and punctuation pass through untouched`() {
    assertEquals("पसल-2", Romanizer.toDevanagari("pasal-2"))
    assertEquals("पसल-२", Romanizer.toDevanagari("pasal-2", convertDigits = true))
  }
}

/** The address fields a bill prints are closed sets, and have to stay consistent. */
class NepalGeographyTest {

  @Test
  fun `seven provinces and seventy-seven districts`() {
    assertEquals(7, Nepal.provinces.size)
    assertEquals(77, Nepal.provinces.sumOf { it.districts.size })
  }

  @Test
  fun `no district belongs to two provinces`() {
    val all = Nepal.provinces.flatMap { it.districts }
    assertEquals(all.size, all.toSet().size)
  }

  @Test
  fun `a district names its own province`() {
    assertEquals("Bagmati", Nepal.provinceOf("Kathmandu"))
    assertEquals("Gandaki", Nepal.provinceOf("Kaski"))
    assertEquals("Koshi", Nepal.provinceOf("Morang"))
    assertNull(Nepal.provinceOf("Nowhere"))
  }

  @Test
  fun `what a geocoder hands back is matched to a district we know`() {
    assertEquals("Kathmandu", Nepal.matchDistrict("Kathmandu District"))
    assertEquals("Lalitpur", Nepal.matchDistrict("  lalitpur "))
    assertNull(Nepal.matchDistrict("Bengaluru"))
  }
}

/**
 * A miti is stored, compared and filed. It has to be the same string on every phone.
 *
 * This is a regression test for a bug found on a real device: with the app in Nepali,
 * `String.format` rendered `%d` in Devanagari and a bill was saved with a miti of
 * "२०८३-०५-१३". It was the right date and a string nothing could match, so the bill
 * vanished from today's takings and from every date filter.
 */
class MitiFormattingTest {

  @Test
  fun `a miti is ASCII whatever language the phone is in`() {
    val original = java.util.Locale.getDefault()
    try {
      for (tag in listOf("ne", "ne-NP", "hi", "ar", "en")) {
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
        assertEquals(
          "in locale $tag",
          "2083-05-13",
          np.bill.core.nepali.BsDate(2083, 5, 13).toString(),
        )
      }
    } finally {
      java.util.Locale.setDefault(original)
    }
  }

  @Test
  fun `a miti round-trips through parse in any locale`() {
    val original = java.util.Locale.getDefault()
    try {
      java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ne"))
      val date = np.bill.core.nepali.BsDate(2083, 5, 13)
      assertEquals(date, np.bill.core.nepali.BsDate.parse(date.toString()))
    } finally {
      java.util.Locale.setDefault(original)
    }
  }

  @Test
  fun `the readable form does follow the language`() {
    assertEquals(
      "13 Bhadra 2083",
      np.bill.core.nepali.BsDate(2083, 5, 13).formatLong(nepali = false),
    )
    assertEquals(
      "१३ भाद्र २०८३",
      np.bill.core.nepali.BsDate(2083, 5, 13).formatLong(nepali = true),
    )
  }
}

/** The calendar the date picker walks through has to match the one bills are dated with. */
class BsCalendarMonthsTest {

  @Test
  fun `every month is between 29 and 32 days`() {
    for (year in BsCalendar.EPOCH_YEAR..BsCalendar.LAST_YEAR) {
      for (month in 1..12) {
        val days = BsCalendar.daysInMonth(year, month)
        assertTrue("BS $year-$month had $days days", days in 29..32)
      }
    }
  }

  @Test
  fun `a year is 365 or 366 days`() {
    for (year in BsCalendar.EPOCH_YEAR..BsCalendar.LAST_YEAR) {
      val total = (1..12).sumOf { BsCalendar.daysInMonth(year, it) }
      assertTrue("BS $year had $total days", total in 365..366)
    }
  }

  @Test
  fun `converting to a BS date and back lands on the same day`() {
    // A day in each season, across a decade, through the epoch's own boundaries.
    var millis = BsCalendar.toAdUtcMillis(np.bill.core.nepali.BsDate(2075, 1, 1))
    repeat(400) {
      val bs = BsCalendar.toBs(millis)
      assertEquals(millis, BsCalendar.toAdUtcMillis(bs))
      millis += 9L * 24 * 60 * 60 * 1000
    }
  }
}
