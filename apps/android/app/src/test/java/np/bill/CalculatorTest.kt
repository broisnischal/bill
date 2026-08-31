package np.bill

import np.bill.core.unit.Unit
import np.bill.ui.common.evaluate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Counter arithmetic.
 *
 * Everything is paisa, so the answers here are integers and there is no rounding drift to
 * argue about later — which is the whole reason the calculator lives in the app rather
 * than being the phone's.
 */
class CalculatorTest {

  @Test
  fun `adds a column of prices`() {
    assertEquals(30000L, evaluate("100+100+100"))
    assertEquals(31250L, evaluate("125.50+187"))
  }

  @Test
  fun `multiplies a price by a count`() {
    assertEquals(37500L, evaluate("125*3"))
    assertEquals(31375L, evaluate("125.50*2.5"))
  }

  @Test
  fun `runs left to right, the way a till does`() {
    // Algebra would say 200. A shopkeeper reading their own keystrokes expects 300.
    assertEquals(30000L, evaluate("100+50*2"))
  }

  @Test
  fun `divides, and refuses to divide by nothing`() {
    assertEquals(5000L, evaluate("150/3"))
    assertNull(evaluate("150/0"))
  }

  @Test
  fun `an unfinished expression has no answer yet`() {
    assertNull(evaluate(""))
    assertNull(evaluate("100+"))
    assertNull(evaluate("+"))
  }

  @Test
  fun `subtracting past zero is allowed, because a discount can`() {
    assertEquals(-5000L, evaluate("100-150"))
  }
}

/** The unit on a line is printed on the bill, so the set has to stay coherent. */
class UnitsTest {

  @Test
  fun `every unit code is unique`() {
    val codes = Unit.all.map(Unit::code)
    assertEquals(codes.size, codes.toSet().size)
  }

  @Test
  fun `the units a shop weighs or pours are fractional, pieces are not`() {
    assertEquals(true, Unit.isFractional("kg"))
    assertEquals(true, Unit.isFractional("ltr"))
    assertEquals(false, Unit.isFractional("pcs"))
    assertEquals(false, Unit.isFractional("pkt"))
  }

  @Test
  fun `an unknown unit still resolves to something printable`() {
    assertNull(Unit.of("furlong"))
    assertEquals("furlong", Unit.label("furlong"))
    assertNotNull(Unit.of("KG"))
  }
}
