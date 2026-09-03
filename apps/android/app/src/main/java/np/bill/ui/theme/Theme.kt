package np.bill.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import np.bill.R

/**
 * The look.
 *
 * Five things carry it, and none of them is decoration:
 *
 *  - **Cards float on a tinted page.** White panels with a large radius sit on an
 *    off-white ground, separated by a soft shadow rather than a hairline. Nothing is
 *    boxed in.
 *  - **No colour, until something means something.** Every surface and every word is
 *    one of twelve true greys. Green appears on a total that has been settled, red on
 *    something cancelled, and nowhere else — which is the only reason either registers.
 *  - **The number is the screen.** Takings are set at display size with the paisa
 *    dropped to a muted grey, because the rupees are what anyone actually reads.
 *  - **Round.** Buttons and chips are full pills, cards are 24dp. A shopkeeper's thumb
 *    is not precise and the shapes should not pretend otherwise.
 *  - **Geist, tracked tight.** Inter is the safe choice and reads like one; Geist has
 *    the tighter, more mechanical figures a screen of money wants, and its digits line
 *    up in a column. Negative letter-spacing at display sizes, near-zero at body.
 *
 * Nepali is not in Geist, so Devanagari falls through to the system's Noto by way of
 * Android's per-character font fallback. That is the reason the family is declared as a
 * plain FontFamily rather than a locked-down typeface.
 */

private val Geist = FontFamily(
  Font(R.font.geist_regular, FontWeight.Normal),
  Font(R.font.geist_medium, FontWeight.Medium),
  Font(R.font.geist_semibold, FontWeight.SemiBold),
  Font(R.font.geist_bold, FontWeight.Bold),
)

// -- the ramp ------------------------------------------------------------------------
//
// Twelve steps of true grey, no hue in any of them, from an OKLCH scale so the jumps are
// even to the eye rather than even in hex. Every surface, border and piece of text in the
// app is one of these twelve, which is what makes the few places that do carry colour —
// a red on something cancelled, a green on something settled — impossible to miss.

private val Grey1 = Color(0xFF0E0E0E)
private val Grey2 = Color(0xFF151515)
private val Grey3 = Color(0xFF222222)
private val Grey4 = Color(0xFF2E2E2E)
private val Grey5 = Color(0xFF3A3A3A)
private val Grey7 = Color(0xFF585858)
private val Grey8 = Color(0xFF717171)
private val Grey9 = Color(0xFF8F8F8F)
private val Grey11 = Color(0xFFD2D2D2)
private val Grey12 = Color(0xFFE9E9E9)

// A card is white and the page behind it is not, which is the whole of the depth model.

private val LightBackground = Color(0xFFF4F4F4)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceRaised = Color(0xFFFFFFFF)
private val LightSurfaceHigh = Grey12
private val LightBorder = Grey12
private val LightBorderStrong = Grey11
private val LightText = Grey1
private val LightTextMuted = Grey8

private val DarkBackground = Grey1
private val DarkSurface = Grey2
private val DarkSurfaceRaised = Grey3
private val DarkSurfaceHigh = Grey4
private val DarkBorder = Grey4
private val DarkBorderStrong = Grey5
private val DarkText = Grey12
private val DarkTextMuted = Grey9

/**
 * Money in. The colour of a total that has been settled.
 *
 * One of only three colours in the app, and it never fills anything larger than a word
 * or a dot. A screen where the accent is everywhere has no accent.
 */
private val Positive = Color(0xFF1B7F4C)
private val PositiveDark = Color(0xFF4FC182)

/** Money still owed, and anything the shop should get to soon. Never alarming. */
private val Warning = Color(0xFF8A6B12)
private val WarningDark = Color(0xFFE0A800)

/** Reserved. Nothing routine is ever this colour. */
private val Negative = Color(0xFFC0392B)
private val NegativeDark = Color(0xFFFF7A6E)
private val NegativeTint = Color(0xFFF4DBD8)
private val NegativeTintDark = Color(0xFF33201E)

private val DarkColors = darkColorScheme(
  primary = Grey12,
  onPrimary = Grey1,
  primaryContainer = Grey3,
  onPrimaryContainer = Grey12,
  secondary = DarkTextMuted,
  onSecondary = DarkBackground,
  secondaryContainer = DarkSurfaceHigh,
  onSecondaryContainer = DarkText,
  tertiary = PositiveDark,
  background = DarkBackground,
  onBackground = DarkText,
  surface = DarkSurface,
  onSurface = DarkText,
  surfaceVariant = DarkSurfaceRaised,
  onSurfaceVariant = DarkTextMuted,
  surfaceContainerLowest = DarkBackground,
  surfaceContainerLow = DarkSurface,
  surfaceContainer = DarkSurfaceRaised,
  surfaceContainerHigh = DarkSurfaceHigh,
  surfaceContainerHighest = Grey5,
  error = NegativeDark,
  onError = Color(0xFF3A0A05),
  errorContainer = Color(0xFF3A1512),
  onErrorContainer = Color(0xFFFFD3CD),
  outline = DarkBorderStrong,
  outlineVariant = DarkBorder,
  scrim = Color(0xCC000000),
)

private val LightColors = lightColorScheme(
  primary = Grey1,
  onPrimary = Color.White,
  primaryContainer = Grey12,
  onPrimaryContainer = Grey1,
  secondary = LightTextMuted,
  onSecondary = Color.White,
  secondaryContainer = LightSurfaceHigh,
  onSecondaryContainer = LightText,
  tertiary = Positive,
  background = LightBackground,
  onBackground = LightText,
  surface = LightSurface,
  onSurface = LightText,
  surfaceVariant = LightSurfaceRaised,
  onSurfaceVariant = LightTextMuted,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = LightSurface,
  surfaceContainer = LightSurfaceRaised,
  surfaceContainerHigh = LightSurfaceHigh,
  surfaceContainerHighest = Grey11,
  error = Negative,
  onError = Color.White,
  errorContainer = Color(0xFFFDECEA),
  onErrorContainer = Color(0xFF7A241B),
  outline = LightBorderStrong,
  outlineVariant = LightBorder,
  scrim = Color(0x99000000),
)

/**
 * Tight tracking at the top, neutral at the bottom, and tabular figures throughout.
 *
 * `tnum` is what makes a column of totals readable: with proportional digits a 1 is
 * narrower than a 7, so Rs 1,111.00 and Rs 7,777.00 do not line up and the eye cannot
 * compare them down a list. Geist carries the feature; every style here asks for it.
 * The negative letter-spacing on the
 * large sizes is most of what makes a heading look drawn rather than typed.
 */
private fun geist(
  size: Int,
  line: Int,
  weight: FontWeight = FontWeight.Normal,
  tracking: Float = 0f,
) = TextStyle(
  fontFamily = Geist,
  fontSize = size.sp,
  lineHeight = line.sp,
  fontWeight = weight,
  letterSpacing = tracking.sp,
  // Tabular figures, on every style. See the note above.
  fontFeatureSettings = "tnum",
)

private val BillTypography = Typography(
  displayLarge = geist(40, 44, FontWeight.SemiBold, -1.4f),
  displayMedium = geist(32, 38, FontWeight.SemiBold, -1.0f),
  displaySmall = geist(26, 32, FontWeight.SemiBold, -0.6f),
  headlineLarge = geist(22, 28, FontWeight.SemiBold, -0.4f),
  headlineMedium = geist(19, 25, FontWeight.SemiBold, -0.3f),
  headlineSmall = geist(17, 23, FontWeight.SemiBold, -0.2f),
  titleLarge = geist(16, 22, FontWeight.Medium, -0.2f),
  titleMedium = geist(14, 20, FontWeight.Medium, -0.1f),
  titleSmall = geist(13, 18, FontWeight.Medium),
  bodyLarge = geist(15, 21, tracking = -0.1f),
  bodyMedium = geist(13, 19),
  bodySmall = geist(12, 16),
  labelLarge = geist(14, 18, FontWeight.Medium, -0.1f),
  labelMedium = geist(12, 16, FontWeight.Medium),
  labelSmall = geist(11, 14, FontWeight.Medium, 0.2f),
)

/** What the app draws with beyond Material's own tokens. */
data class BillTokens(
  val border: Color,
  val borderStrong: Color,
  val positive: Color,
  val warning: Color,
  val negative: Color,
  /** Money owed. Distinct from an error: a due is normal, it just is not settled. */
  val due: Color,
  /** Fill for the small square behind an icon, and for a pill around a number. */
  val mint: Color,
  /** What is legible on that fill. */
  val onMint: Color,
  /** The same idea for something that went the other way. */
  val negativeTint: Color,
  /** What marks the one thing you are on. Ink, so nothing has to compete with it. */
  val accent: Color,
  /** Filled actions, and the colour a screen's heading is set in. */
  val ink: Color,
  val onInk: Color,
  /** The top of the gradient a screen's head fades out of. */
  val sage: Color,
  /** What a card casts. Soft and low, never a drop shadow you can name. */
  val shadow: Color,
  val isDark: Boolean,
)

val LocalTokens = staticCompositionLocalOf {
  BillTokens(
    border = DarkBorder,
    borderStrong = DarkBorderStrong,
    positive = PositiveDark,
    warning = WarningDark,
    negative = NegativeDark,
    due = WarningDark,
    mint = Grey3,
    onMint = Grey12,
    negativeTint = NegativeTintDark,
    accent = Grey12,
    ink = Grey12,
    onInk = Grey1,
    sage = Grey2,
    shadow = Color(0x66000000),
    isDark = true,
  )
}

/**
 * Round, and deliberately so.
 *
 * The corners were 4dp when the app was built to look like a desktop tool. Everything a
 * thumb touches is a pill now and a card is 24dp, which is the single change that most
 * separates something made for a phone from something ported onto one.
 */
object Radius {
  /** A mark inside a pill: a brand logo on a payment chip. */
  val small = 8.dp

  /** The tinted square behind an icon. */
  val medium = 12.dp

  /** A field, and any surface nested inside a card. */
  val large = 16.dp

  /** A floating bar whose contents are pills inset by 6dp: 10 + 6 rounds to this. */
  val bar = 20.dp

  /** A card. */
  val card = 24.dp

  /** A sheet's top corners. */
  val sheet = 28.dp

  val pill = 999.dp
}

/** A thumb at a counter is not precise, so nothing tappable is smaller than this. */
val TouchTarget = 44.dp

/** What a screen's own action stands at. Taller than a row, and always a pill. */
val ActionHeight = 54.dp

/**
 * The margin every screen keeps from the edge of the phone.
 *
 * One number, because the screens used four between them — 12, 14, 16 and 20 — and
 * moving between tabs made the app look assembled from parts.
 */
val Gutter = 14.dp

/** What the person chose in settings, against what the system is doing. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun BillTheme(
  mode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val dark = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

  val colors = if (dark) DarkColors else LightColors
  val tokens = BillTokens(
    border = if (dark) DarkBorder else LightBorder,
    borderStrong = if (dark) DarkBorderStrong else LightBorderStrong,
    positive = if (dark) PositiveDark else Positive,
    warning = if (dark) WarningDark else Warning,
    negative = if (dark) NegativeDark else Negative,
    due = if (dark) WarningDark else Warning,
    mint = if (dark) Grey3 else Grey12,
    onMint = if (dark) Grey12 else Grey1,
    negativeTint = if (dark) NegativeTintDark else NegativeTint,
    accent = if (dark) Grey12 else Grey1,
    ink = if (dark) Grey12 else Grey1,
    onInk = if (dark) Grey1 else Color.White,
    sage = if (dark) Grey2 else Grey12,
    // Cast by a white card onto an off-white page, so it has to be soft or it reads as
    // a border that someone forgot to align.
    shadow = if (dark) Color(0x99000000) else Color(0x1A101010),
    isDark = dark,
  )

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
      }
    }
  }

  CompositionLocalProvider(LocalTokens provides tokens) {
    MaterialTheme(colorScheme = colors, typography = BillTypography, content = content)
  }
}
