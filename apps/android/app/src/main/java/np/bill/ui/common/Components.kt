package np.bill.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.ActionHeight
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius
import np.bill.ui.theme.TouchTarget

/**
 * The pieces every screen is built from.
 *
 * Depth is a soft shadow under a white card, not a hairline around a grey one. Every
 * control a thumb lands on is a pill. And every field reserves the space its error will
 * need, because a form that grows by a line the moment something is wrong shoves the
 * rest of itself under the keyboard.
 */

/**
 * A press dips the control by 4%.
 *
 * A spring rather than a tween, so a second tap catches the first mid-flight instead of
 * queueing behind it. 0.96 is the whole range worth using: below 0.95 a control looks
 * squashed rather than pressed.
 */
@Composable
fun Modifier.pressScale(interaction: androidx.compose.foundation.interaction.InteractionSource): Modifier {
  val pressed by interaction.collectIsPressedAsState()
  val scale by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (pressed) 0.96f else 1f,
    animationSpec = androidx.compose.animation.core.spring(
      dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
      stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
    ),
    label = "press",
  )
  return graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

/** The one action a screen is built around. Black, full width, pill. */
@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  loading: Boolean = false,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
  val tokens = LocalTokens.current
  val interaction = remember { MutableInteractionSource() }
  Button(
    onClick = onClick,
    interactionSource = interaction,
    enabled = enabled && !loading,
    modifier = modifier.pressScale(interaction).fillMaxWidth().heightIn(min = ActionHeight),
    shape = RoundedCornerShape(Radius.pill),
    colors = ButtonDefaults.buttonColors(
      containerColor = tokens.ink,
      contentColor = tokens.onInk,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
  ) {
    // The spinner replaces the label rather than sitting beside it, so the button does
    // not change width the moment it is pressed.
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = tokens.onInk,
      )
    } else {
      icon?.let {
        Icon(it, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text(text, style = MaterialTheme.typography.titleLarge)
    }
  }
}

@Composable
fun SecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  destructive: Boolean = false,
) {
  val tokens = LocalTokens.current
  val interaction = remember { MutableInteractionSource() }
  Button(
    onClick = onClick,
    interactionSource = interaction,
    enabled = enabled,
    modifier = modifier.pressScale(interaction).fillMaxWidth().heightIn(min = ActionHeight),
    shape = RoundedCornerShape(Radius.pill),
    border = BorderStroke(1.dp, tokens.borderStrong),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = if (destructive) tokens.negative else MaterialTheme.colorScheme.onSurface,
    ),
  ) {
    Text(text, style = MaterialTheme.typography.titleLarge)
  }
}

/** The same shape as the primary action, in the colour of something you cannot undo. */
@Composable
fun DangerButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val interaction = remember { MutableInteractionSource() }
  Button(
    onClick = onClick,
    interactionSource = interaction,
    enabled = enabled,
    modifier = modifier.pressScale(interaction).fillMaxWidth().heightIn(min = ActionHeight),
    shape = RoundedCornerShape(Radius.pill),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.error,
      contentColor = MaterialTheme.colorScheme.onError,
    ),
  ) {
    Text(text, style = MaterialTheme.typography.titleLarge)
  }
}

/**
 * A card. White, generously rounded, lifted off the page by a shadow.
 *
 * Dark mode keeps a hairline as well: a shadow under a near-black card on a near-black
 * page is not visible, and something has to say where one ends.
 */
@Composable
fun Panel(
  modifier: Modifier = Modifier,
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
  val tokens = LocalTokens.current
  val shape = RoundedCornerShape(Radius.card)
  Column(
    modifier
      .fillMaxWidth()
      .shadow(
        elevation = if (tokens.isDark) 0.dp else 10.dp,
        shape = shape,
        ambientColor = tokens.shadow,
        spotColor = tokens.shadow,
      )
      .clip(shape)
      .background(MaterialTheme.colorScheme.surface)
      .then(if (tokens.isDark) Modifier.border(1.dp, tokens.border, shape) else Modifier),
  ) {
    // The card carries what is legible on it, so text inside one never inherits a colour
    // from wherever it happens to have been placed.
    androidx.compose.runtime.CompositionLocalProvider(
      androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
      content = { content() },
    )
  }
}

/** A strip across the top of a screen. Reserves nothing when there is nothing to say. */
@Composable
fun Notice(
  text: String,
  modifier: Modifier = Modifier,
  tone: NoticeTone = NoticeTone.INFO,
) {
  val tokens = LocalTokens.current
  val (background, foreground) = when (tone) {
    NoticeTone.INFO -> tokens.mint to tokens.onMint
    NoticeTone.WARN -> tokens.warning.copy(alpha = 0.16f) to tokens.warning
    NoticeTone.ERROR -> tokens.negativeTint to MaterialTheme.colorScheme.error
  }

  Row(
    modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(Radius.large))
      .background(background)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(6.dp)
        .clip(CircleShape)
        .background(foreground),
    )
    Spacer(Modifier.width(9.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = foreground)
  }
}

enum class NoticeTone { INFO, WARN, ERROR }

/**
 * The small tinted square an icon sits in.
 *
 * It is what makes a list of bills readable at arm's length: the colour says which way
 * the money went before a single word has been read.
 */
@Composable
fun IconTile(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
  tone: TileTone = TileTone.MINT,
  size: androidx.compose.ui.unit.Dp = 44.dp,
) {
  val tokens = LocalTokens.current
  val (background, foreground) = when (tone) {
    TileTone.MINT -> tokens.mint to tokens.onMint
    TileTone.NEGATIVE -> tokens.negativeTint to MaterialTheme.colorScheme.error
    TileTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
  }
  Box(
    modifier
      .size(size)
      .clip(RoundedCornerShape(Radius.medium))
      .background(background),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(size * 0.52f))
  }
}

enum class TileTone { MINT, NEGATIVE, NEUTRAL }

/** A person, as the letter their name starts with. Recognised faster than an icon. */
@Composable
fun InitialTile(
  name: String,
  modifier: Modifier = Modifier,
  size: androidx.compose.ui.unit.Dp = 44.dp,
) {
  val tokens = LocalTokens.current
  Box(
    modifier
      .size(size)
      .clip(CircleShape)
      .background(tokens.mint),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      name.trim().take(1).uppercase().ifEmpty { "?" },
      style = MaterialTheme.typography.titleLarge,
      color = tokens.onMint,
    )
  }
}

/**
 * Money, at the size the only number on the screen deserves.
 *
 * The paisa drop to a muted grey a size down. Nobody reads them and at display size they
 * take up as much room as the rupees, which is what made the takings hard to read at a
 * glance rather than easy.
 */
@Composable
fun MoneyDisplay(
  paisa: Long,
  modifier: Modifier = Modifier,
  style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayLarge,
  prefix: String = "Rs ",
) {
  val formatted = np.bill.core.money.formatMoney(paisa)
  val rupees = formatted.substringBefore(".")
  val decimals = formatted.substringAfter(".", "")

  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Text(
      prefix,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(rupees, style = style)
    // No ".00": on a day of round sales that is two grey characters saying nothing.
    if (decimals.isNotEmpty() && decimals != "00") {
      Text(
        ".$decimals",
        style = style.copy(fontSize = style.fontSize * 0.62f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** A number worth calling out, in a pill the colour of which way it went. */
@Composable
fun DeltaPill(text: String, modifier: Modifier = Modifier, positive: Boolean = true) {
  val tokens = LocalTokens.current
  Box(
    modifier
      .clip(RoundedCornerShape(Radius.pill))
      .background(if (positive) tokens.mint else tokens.negativeTint)
      .padding(horizontal = 12.dp, vertical = 5.dp),
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelLarge,
      color = if (positive) tokens.onMint else MaterialTheme.colorScheme.error,
    )
  }
}

/**
 * One option among a handful, tapped on or off.
 *
 * Material's own FilterChip carries a tick that shifts the label sideways as it appears,
 * so a row of them jumps every time one is chosen. This says the same thing by filling
 * in, which costs no width.
 */
@Composable
fun ChoiceChip(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalTokens.current
  val shape = RoundedCornerShape(Radius.pill)
  val interaction = remember { MutableInteractionSource() }
  Box(
    modifier
      .pressScale(interaction)
      .clip(shape)
      .background(if (selected) tokens.ink else MaterialTheme.colorScheme.surface)
      .border(1.dp, if (selected) tokens.ink else tokens.borderStrong, shape)
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelLarge,
      color = if (selected) tokens.onInk else MaterialTheme.colorScheme.onSurface,
    )
  }
}

/**
 * A quantity, with a button on each side of it.
 *
 * Typing "2" into a field is three taps and a keyboard that covers half the bill; on a
 * counter the quantity is nearly always small and nearly always changes by one. The field
 * stays editable for the shop selling 1.75 kg, and the buttons carry the other ninety
 * per cent.
 *
 * Minus stops at one. A line with nothing on it is not a line, and a shopkeeper who wants
 * it gone reaches for the cross at the end of the row.
 */
@Composable
fun QuantityStepper(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalTokens.current
  val shape = RoundedCornerShape(Radius.large)
  val milli = np.bill.core.money.parseQuantityMilli(value)

  fun step(by: Long) {
    val next = ((milli ?: 1000L) + by).coerceAtLeast(1000L)
    onValueChange(np.bill.core.money.formatQuantity(next))
  }

  Column(modifier) {
    // The same height and the same corner as the unit and the rate next to it. It was a
    // shorter pill sitting higher than both, which made a row of three controls read as
    // three unrelated ones.
    Row(
      Modifier
        .height(FieldHeight)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, tokens.borderStrong, shape),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      StepButton(
        icon = np.bill.ui.theme.BillIcons.Minus,
        enabled = (milli ?: 0) > 1000L,
        onClick = { step(-1000L) },
      )

      androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        // titleMedium, not titleLarge: a reverse-calculated quantity is "0.076", and at
        // display weight that does not fit a box this row can spare the width for.
        textStyle = MaterialTheme.typography.titleMedium.copy(
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = Modifier.width(56.dp),
      )

      StepButton(
        icon = np.bill.ui.theme.BillIcons.Plus,
        enabled = true,
        onClick = { step(1000L) },
      )
    }

    // The same reserved line every field carries, so the row it sits in stays aligned.
    Box(Modifier.height(18.dp))
  }
}

@Composable
private fun StepButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Box(
    Modifier
      .size(width = 36.dp, height = FieldHeight)
      .clip(RoundedCornerShape(Radius.medium))
      .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
      },
      modifier = Modifier.size(18.dp),
    )
  }
}

/**
 * What every field in a row stands at.
 *
 * Material's outlined field is 56dp, and anything sitting beside one has to match or the
 * row looks assembled rather than laid out.
 */
val FieldHeight = 56.dp

/** A label on the left, a value on the right. The shape a bill's totals are read in. */
@Composable
fun TotalsRow(
  label: String,
  value: String,
  emphasised: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier.fillMaxWidth().padding(vertical = if (emphasised) 6.dp else 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      label,
      style = if (emphasised) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
      color = if (emphasised) {
        MaterialTheme.colorScheme.onSurface
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
    Text(
      value,
      style = if (emphasised) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
      fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
    )
  }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
  Column(
    modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

/** The hairline that separates rows. Thinner and quieter than Material's default. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
  HorizontalDivider(modifier, thickness = 1.dp, color = LocalTokens.current.border)
}

/**
 * A text field whose supporting line is always there.
 *
 * Material only draws supporting text when there is some, so a field grows by a line the
 * moment it becomes invalid and shoves the rest of the form down. Reserving the row keeps
 * the form still while someone is typing in it.
 */
@Composable
fun Field(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  error: String? = null,
  hint: String? = null,
  singleLine: Boolean = true,
  minLines: Int = 1,
  enabled: Boolean = true,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  prefix: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  /**
   * Whether the label floats above the box.
   *
   * Off inside a tight row: a floated label adds height above the input, so a labelled
   * field beside an unlabelled one sits lower than it even though both are 56dp.
   */
  showLabel: Boolean = true,
  textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
  Column(modifier) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = if (showLabel) {
        { Text(label) }
      } else {
        null
      },
      placeholder = placeholder?.let { { Text(it) } },
      prefix = prefix,
      trailingIcon = trailingIcon,
      singleLine = singleLine,
      minLines = minLines,
      enabled = enabled,
      isError = error != null,
      textStyle = textStyle,
      keyboardOptions = keyboardOptions,
      shape = RoundedCornerShape(Radius.large),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = LocalTokens.current.borderStrong,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledBorderColor = LocalTokens.current.border,
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
      ),
      modifier = Modifier.fillMaxWidth(),
    )

    // Always one line high, whether or not there is anything in it.
    Box(Modifier.fillMaxWidth().height(18.dp).padding(start = 14.dp, top = 2.dp)) {
      val message = error ?: hint
      if (message != null) {
        Text(
          message,
          style = MaterialTheme.typography.labelSmall,
          color = if (error != null) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
    }
  }
}

/**
 * A payment method, with its own colour.
 *
 * The wallets a Nepali shop takes are recognised by their mark before they are read, so
 * each chip carries one.
 *
 * The artwork is looked up by name: a file at `res/drawable/brand_esewa.xml` is used for
 * eSewa the moment it exists, and until then the chip falls back to a monogram in the
 * brand's colour. That is deliberate. Shipping a company's logo is the shop's call and
 * their licence to make, and the app has to look right either way.
 */
@Composable
fun PaymentChip(
  method: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalTokens.current
  val brand = paymentBrand(method)

  Row(
    modifier
      .clip(RoundedCornerShape(Radius.large))
      .background(
        if (selected) brand.tint.copy(alpha = if (tokens.isDark) 0.20f else 0.13f)
        else MaterialTheme.colorScheme.surfaceContainer,
      )
      .border(
        1.dp,
        if (selected) brand.tint.copy(alpha = 0.55f) else tokens.border,
        RoundedCornerShape(Radius.large),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val logo = brandLogo(method)
    if (logo != null) {
      androidx.compose.foundation.Image(
        painter = logo,
        contentDescription = null,
        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(Radius.small)),
      )
    } else {
      Box(
        Modifier
          .size(20.dp)
          .clip(RoundedCornerShape(Radius.small))
          .background(brand.tint),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          brand.mark,
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    Spacer(Modifier.width(7.dp))
    Text(
      brand.label,
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * The brand's own artwork, if this build carries it.
 *
 * Found by name rather than by a mapping in code, so adding `brand_khalti.xml` to the
 * drawables is the whole of adding Khalti's logo. `res/raw/keep.xml` stops the resource
 * shrinker removing files nothing references by symbol.
 */
@Composable
private fun brandLogo(method: String): androidx.compose.ui.graphics.painter.Painter? {
  val context = androidx.compose.ui.platform.LocalContext.current
  val id = androidx.compose.runtime.remember(method) {
    @Suppress("DiscouragedApi")
    context.resources.getIdentifier("brand_$method", "drawable", context.packageName)
  }
  return if (id == 0) null else androidx.compose.ui.res.painterResource(id)
}

data class PaymentBrand(val label: String, val mark: String, val tint: Color)

fun paymentBrand(method: String): PaymentBrand = when (method) {
  "esewa" -> PaymentBrand("eSewa", "e", Color(0xFF60BB46))
  "khalti" -> PaymentBrand("Khalti", "K", Color(0xFFDC0019))
  "fonepay" -> PaymentBrand("Fonepay", "F", Color(0xFFCF2027))
  "connectips" -> PaymentBrand("ConnectIPS", "C", Color(0xFF0C4DA2))
  "card" -> PaymentBrand("Card", "▭", Color(0xFF4A5568))
  "bank" -> PaymentBrand("Bank", "B", Color(0xFF2C5282))
  "cheque" -> PaymentBrand("Cheque", "✓", Color(0xFF6B46C1))
  "credit" -> PaymentBrand("Credit", "→", Color(0xFFB7791F))
  else -> PaymentBrand("Cash", "₨", Color(0xFF2F855A))
}
