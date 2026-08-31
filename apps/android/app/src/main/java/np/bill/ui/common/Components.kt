package np.bill.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius
import np.bill.ui.theme.TouchTarget

/**
 * The pieces every screen is built from.
 *
 * Depth here is a border and a shade, never a shadow, and every control reserves the
 * space it will need when it has something to say — an error that appears by pushing the
 * rest of the form down is the layout shift people notice most.
 */

/** The one action a screen is built around. */
@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  loading: Boolean = false,
) {
  Button(
    onClick = onClick,
    enabled = enabled && !loading,
    modifier = modifier.fillMaxWidth().heightIn(min = TouchTarget),
    shape = RoundedCornerShape(Radius.large),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
  ) {
    // The spinner replaces the label rather than sitting beside it, so the button does
    // not change width the moment it is pressed.
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    } else {
      Text(text, style = MaterialTheme.typography.labelLarge)
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
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.fillMaxWidth().heightIn(min = TouchTarget),
    shape = RoundedCornerShape(Radius.large),
    border = BorderStroke(1.dp, tokens.border),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      contentColor = if (destructive) tokens.negative else MaterialTheme.colorScheme.onSurface,
    ),
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge)
  }
}

/** A panel. One shade up from the background, hairline around it, nothing floating. */
@Composable
fun Panel(
  modifier: Modifier = Modifier,
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
  val tokens = LocalTokens.current
  Column(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.large))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(1.dp, tokens.border, RoundedCornerShape(Radius.large)),
    content = content,
  )
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
    NoticeTone.INFO -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
    NoticeTone.WARN -> tokens.warning.copy(alpha = 0.12f) to tokens.warning
    NoticeTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
  }

  Row(
    modifier
      .fillMaxWidth()
      .background(background)
      .padding(horizontal = 16.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(5.dp)
        .clip(CircleShape)
        .background(foreground),
    )
    Spacer(Modifier.width(9.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = foreground)
  }
}

enum class NoticeTone { INFO, WARN, ERROR }

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
  textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
  Column(modifier) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
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
        unfocusedBorderColor = LocalTokens.current.border,
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
 * The wallets a Nepali shop takes are recognised by colour before they are read, so each
 * carries a coloured monogram. These are deliberately plain marks rather than the
 * companies' logos: they identify a payment method without passing themselves off as
 * anything issued by eSewa, Khalti or Fonepay.
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
    Box(
      Modifier
        .size(18.dp)
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
    Spacer(Modifier.width(7.dp))
    Text(
      brand.label,
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

data class PaymentBrand(val label: String, val mark: String, val tint: Color)

fun paymentBrand(method: String): PaymentBrand = when (method) {
  "esewa" -> PaymentBrand("eSewa", "e", Color(0xFF60BB46))
  "khalti" -> PaymentBrand("Khalti", "K", Color(0xFF5C2D91))
  "fonepay" -> PaymentBrand("Fonepay", "F", Color(0xFFE2231A))
  "connectips" -> PaymentBrand("ConnectIPS", "C", Color(0xFF0C4DA2))
  "card" -> PaymentBrand("Card", "▭", Color(0xFF4A5568))
  "bank" -> PaymentBrand("Bank", "B", Color(0xFF2C5282))
  "cheque" -> PaymentBrand("Cheque", "✓", Color(0xFF6B46C1))
  "credit" -> PaymentBrand("Credit", "→", Color(0xFFB7791F))
  else -> PaymentBrand("Cash", "₨", Color(0xFF2F855A))
}
