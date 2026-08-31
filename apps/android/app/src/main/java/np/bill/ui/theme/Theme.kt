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
 * The look, taken from Linear.
 *
 * Four things carry that feel, and none of them is decoration:
 *
 *  - **Depth is drawn with borders, not shadows.** Surfaces sit a shade apart and are
 *    separated by a hairline. Nothing floats.
 *  - **Colour is almost absent.** One accent — the blue of the flag's border — used only
 *    where something is actionable; everything else is a true neutral grey. Red is kept
 *    back for things that are actually wrong.
 *  - **Density.** Rows are tight and type is small, because a shopkeeper scanning a day's
 *    bills wants more of them on screen, not more air between them.
 *  - **Inter, tracked tight.** Negative letter-spacing at display sizes, near-zero at
 *    body, which is what stops small text looking loose.
 *
 * Nepali is not in Inter, so Devanagari falls through to the system's Noto by way of
 * Android's per-character font fallback. That is the reason the family is declared as a
 * plain FontFamily rather than a locked-down typeface.
 */

private val Inter = FontFamily(
  Font(R.font.inter_regular, FontWeight.Normal),
  Font(R.font.inter_medium, FontWeight.Medium),
  Font(R.font.inter_semibold, FontWeight.SemiBold),
  Font(R.font.inter_bold, FontWeight.Bold),
)

// -- neutrals ------------------------------------------------------------------------
//
// Deliberately free of any colour cast, so the one accent reads as the only colour on
// the screen rather than as the loudest of several.

private val DarkBackground = Color(0xFF0A0A0B)
private val DarkSurface = Color(0xFF121213)
private val DarkSurfaceRaised = Color(0xFF171718)
private val DarkSurfaceHigh = Color(0xFF1D1D1F)
private val DarkBorder = Color(0xFF262627)
private val DarkBorderStrong = Color(0xFF343436)
private val DarkText = Color(0xFFF6F6F6)
private val DarkTextMuted = Color(0xFF8E8E93)

private val LightBackground = Color(0xFFFFFFFF)
private val LightSurface = Color(0xFFFAFAFA)
private val LightSurfaceRaised = Color(0xFFF5F5F5)
private val LightSurfaceHigh = Color(0xFFEFEFEF)
private val LightBorder = Color(0xFFE4E4E5)
private val LightBorderStrong = Color(0xFFD2D2D4)
private val LightText = Color(0xFF0A0A0B)
private val LightTextMuted = Color(0xFF6E6E73)

/**
 * The blue from the flag's border.
 *
 * It started as the flag's crimson, and that was a mistake: crimson was also the error
 * colour, so "make the bill" and "cancel the bill" were the same shade, and a screen with
 * anything urgent on it read as alarming. Red now means one thing only — something is
 * wrong — and the everyday colour is the calmer half of the same flag.
 *
 * It also stays out of the way of the wallets a shop takes: eSewa is green, Khalti
 * purple, Fonepay red. A blue primary collides with none of them.
 */
private val Blue = Color(0xFF1B4D9B)
private val BlueDark = Color(0xFF7CA6F2)
private val BlueSoftDark = Color(0xFF11213A)
private val BlueSoftLight = Color(0xFFE8EFFB)

/** Money in. The colour of a total that has been settled. */
private val Positive = Color(0xFF1F7A4C)
private val PositiveDark = Color(0xFF4CC182)

/** Money still owed, and anything the shop should get to soon. Never alarming. */
private val Warning = Color(0xFF9A6B00)
private val WarningDark = Color(0xFFE0A800)

/** Reserved. Nothing routine is ever this colour. */
private val Negative = Color(0xFFC0392B)
private val NegativeDark = Color(0xFFFF6B5E)

private val DarkColors = darkColorScheme(
  primary = BlueDark,
  onPrimary = Color(0xFF06152B),
  primaryContainer = BlueSoftDark,
  onPrimaryContainer = Color(0xFFCFDFFB),
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
  surfaceContainerHighest = Color(0xFF1F2023),
  error = NegativeDark,
  onError = Color(0xFF3A0A05),
  errorContainer = Color(0xFF3A1512),
  onErrorContainer = Color(0xFFFFD3CD),
  outline = DarkBorderStrong,
  outlineVariant = DarkBorder,
  scrim = Color(0xCC000000),
)

private val LightColors = lightColorScheme(
  primary = Blue,
  onPrimary = Color.White,
  primaryContainer = BlueSoftLight,
  onPrimaryContainer = Color(0xFF0C2A57),
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
  surfaceContainerHighest = Color(0xFFEAEAEB),
  error = Negative,
  onError = Color.White,
  errorContainer = Color(0xFFFDECEA),
  onErrorContainer = Color(0xFF7A241B),
  outline = LightBorderStrong,
  outlineVariant = LightBorder,
  scrim = Color(0x99000000),
)

/**
 * Tight tracking at the top, neutral at the bottom. The negative letter-spacing on the
 * large sizes is most of what makes a heading look drawn rather than typed.
 */
private val BillTypography = Typography(
  displayLarge = TextStyle(fontFamily = Inter, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
  displayMedium = TextStyle(fontFamily = Inter, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
  displaySmall = TextStyle(fontFamily = Inter, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
  headlineLarge = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
  headlineMedium = TextStyle(fontFamily = Inter, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
  headlineSmall = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
  titleLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
  titleMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
  titleSmall = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
  bodyLarge = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp),
  bodyMedium = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 19.sp),
  bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
  labelLarge = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
  labelMedium = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
  labelSmall = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
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
  val isDark: Boolean,
)

val LocalTokens = staticCompositionLocalOf {
  BillTokens(DarkBorder, DarkBorderStrong, PositiveDark, WarningDark, NegativeDark, WarningDark, isDark = true)
}

/** Small radii. Linear's corners are barely rounded; anything softer reads as a toy. */
object Radius {
  val small = 4.dp
  val medium = 6.dp
  val large = 8.dp
  val sheet = 12.dp
}

/** A thumb at a counter is not precise, so nothing tappable is smaller than this. */
val TouchTarget = 44.dp

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
