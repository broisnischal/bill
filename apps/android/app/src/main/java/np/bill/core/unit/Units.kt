package np.bill.core.unit

/**
 * How things are sold in a Nepali shop.
 *
 * A kirana counter sells rice by the kilo, oil by the litre, eggs by the piece and cloth
 * by the metre, and the unit is printed on the bill, so it cannot be a free-text field
 * that ends up holding "kg", "Kg", "kilo" and "के.जी." for the same thing. Anything not
 * on this list can still be typed; the list is there to stop the common cases drifting.
 */
data class Unit(val code: String, val english: String, val nepali: String) {
  /** Quantities in this unit are usually fractional, so the keypad offers a decimal. */
  val fractional: Boolean get() = code in FRACTIONAL

  companion object {
    private val FRACTIONAL = setOf("kg", "g", "ltr", "ml", "m", "ft", "sqft", "hr", "dozen")

    val all = listOf(
      Unit("pcs", "Piece", "थान"),
      Unit("kg", "Kilogram", "के.जी."),
      Unit("g", "Gram", "ग्राम"),
      Unit("ltr", "Litre", "लिटर"),
      Unit("ml", "Millilitre", "मि.लि."),
      Unit("pkt", "Packet", "प्याकेट"),
      Unit("box", "Box", "बाकस"),
      Unit("dozen", "Dozen", "दर्जन"),
      Unit("bottle", "Bottle", "बोतल"),
      Unit("bag", "Bag", "बोरा"),
      Unit("m", "Metre", "मिटर"),
      Unit("ft", "Foot", "फिट"),
      Unit("sqft", "Square foot", "वर्ग फिट"),
      Unit("set", "Set", "सेट"),
      Unit("pair", "Pair", "जोर"),
      Unit("roll", "Roll", "रोल"),
      Unit("hr", "Hour", "घण्टा"),
      Unit("day", "Day", "दिन"),
      Unit("service", "Service", "सेवा"),
    )

    val codes = all.map(Unit::code)

    fun of(code: String): Unit? = all.firstOrNull { it.code.equals(code, ignoreCase = true) }

    /** What to show in a list: the code is what prints, the name is what explains it. */
    fun label(code: String, nepali: Boolean = false): String {
      val unit = of(code) ?: return code
      return "${unit.code} · ${if (nepali) unit.nepali else unit.english}"
    }

    fun isFractional(code: String): Boolean = of(code)?.fractional ?: true
  }
}
