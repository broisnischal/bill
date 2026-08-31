package np.bill.core.text

/**
 * Roman to Devanagari, as you type.
 *
 * Most Nepali shopkeepers can read Devanagari fluently and type it slowly: the stock
 * Nepali keyboard is a layout nobody was taught, so item names end up in Roman script and
 * a bill that should read "चामल" reads "chamal". Transliterating what they already type
 * fast gets Devanagari onto the bill without asking them to learn a keyboard.
 *
 * The scheme is the informal one Nepalis already use in chat, not IAST: `aa` for आ,
 * `ee` for ई, doubled consonants for the retroflex series (`T` and `tt` both give ट).
 * Longest match wins, so `chh` beats `ch` beats `c`.
 *
 * This is a convenience, never a silent rewrite: the caller decides when to apply it, and
 * the original Roman text is always what the person typed.
 */
object Romanizer {

  /** Independent vowels, used at the start of a word. */
  private val VOWELS = linkedMapOf(
    "au" to "औ", "ai" to "ऐ", "aa" to "आ", "ee" to "ई", "ii" to "ई", "oo" to "ऊ",
    "uu" to "ऊ", "ri" to "ऋ",
    "a" to "अ", "i" to "इ", "u" to "उ", "e" to "ए", "o" to "ओ",
  )

  /** The same vowels as marks hung on a preceding consonant. आ becomes ा. */
  private val MATRAS = linkedMapOf(
    "au" to "ौ", "ai" to "ै", "aa" to "ा", "ee" to "ी", "ii" to "ी", "oo" to "ू",
    "uu" to "ू", "ri" to "ृ",
    "a" to "", "i" to "ि", "u" to "ु", "e" to "े", "o" to "ो",
  )

  /**
   * Consonants, longest first. Capitals mark the retroflex series the way Nepalis type
   * them when they bother to distinguish, and the doubled forms do the same job for
   * anyone typing in lower case.
   */
  private val CONSONANTS = linkedMapOf(
    "gyn" to "ज्ञ", "chh" to "छ", "shh" to "ष", "ksh" to "क्ष", "tra" to "त्र",
    "kha" to "ख", "gha" to "घ",
    "ch" to "च", "jh" to "झ", "th" to "थ", "dh" to "ध", "ph" to "फ", "bh" to "भ",
    "kh" to "ख", "gh" to "घ", "sh" to "श", "ny" to "ञ", "ng" to "ङ",
    "tt" to "ट", "dd" to "ड", "nn" to "ण", "ss" to "ष", "rr" to "ड़",
    "Th" to "ठ", "Dh" to "ढ", "T" to "ट", "D" to "ड", "N" to "ण", "S" to "ष",
    "k" to "क", "g" to "ग", "j" to "ज", "t" to "त", "d" to "द", "n" to "न",
    "p" to "प", "b" to "ब", "m" to "म", "y" to "य", "r" to "र", "l" to "ल",
    "w" to "व", "v" to "व", "s" to "स", "h" to "ह", "c" to "च", "f" to "फ",
    "z" to "ज", "x" to "क्स", "q" to "क",
  )

  private const val HALANTA = "्"

  private val DIGITS = charArrayOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')

  /**
   * Transliterates a whole string. Anything that is already Devanagari, or is punctuation,
   * passes through untouched, so a half-converted string can be fed back in safely.
   */
  fun toDevanagari(input: String, convertDigits: Boolean = false): String {
    val out = StringBuilder(input.length * 2)
    var index = 0
    // True when the last thing written was a consonant, so a vowel becomes a matra.
    var afterConsonant = false

    while (index < input.length) {
      val character = input[index]

      if (character.isDevanagari()) {
        out.append(character)
        index++
        afterConsonant = false
        continue
      }

      if (character.isDigit()) {
        out.append(if (convertDigits) DIGITS[character - '0'] else character)
        index++
        afterConsonant = false
        continue
      }

      if (!character.isLetter()) {
        // A consonant left hanging at a word boundary keeps its inherent 'a'.
        out.append(character)
        index++
        afterConsonant = false
        continue
      }

      val consonant = longestMatch(input, index, CONSONANTS)
      if (consonant != null) {
        // Two consonants in a row: the first loses its inherent vowel.
        if (afterConsonant) out.append(HALANTA)
        out.append(consonant.second)
        index += consonant.first
        afterConsonant = true
        continue
      }

      val vowel = longestMatch(input, index, if (afterConsonant) MATRAS else VOWELS)
      if (vowel != null) {
        out.append(vowel.second)
        index += vowel.first
        afterConsonant = false
        continue
      }

      out.append(character)
      index++
      afterConsonant = false
    }

    return out.toString()
  }

  /** The longest key in [table] that this position starts with, and how long it was. */
  private fun longestMatch(
    input: String,
    at: Int,
    table: Map<String, String>,
  ): Pair<Int, String>? {
    for ((key, value) in table) {
      if (input.startsWith(key, at, ignoreCase = false)) return key.length to value
      // Lower-case fallback, so someone typing everything in caps still gets a match.
      if (key.all(Char::isLowerCase) && input.startsWith(key, at, ignoreCase = true)) {
        return key.length to value
      }
    }
    return null
  }

  fun toNepaliDigits(value: String): String = buildString(value.length) {
    for (character in value) {
      append(if (character in '0'..'9') DIGITS[character - '0'] else character)
    }
  }

  /** True once there is something worth converting: at least one Roman letter. */
  fun looksRoman(value: String): Boolean = value.any { it in 'a'..'z' || it in 'A'..'Z' }
}

private fun Char.isDevanagari(): Boolean = code in 0x0900..0x097F
