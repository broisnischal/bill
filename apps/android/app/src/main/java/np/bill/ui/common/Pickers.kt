package np.bill.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.core.text.Romanizer
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * A field you tap to choose from a list rather than type into.
 *
 * Provinces, districts, units and wards are all closed sets. Typing them invites
 * "Kathmandu", "Kathamandu" and "KTM" into the same column, which nobody notices until
 * they try to filter a year's bills by district.
 */
@Composable
fun PickerField(
  value: String?,
  options: List<String>,
  onPick: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  searchable: Boolean = false,
  hint: String? = null,
  /**
   * Whether the label floats above the box.
   *
   * Off in a tight row, where three controls sharing one line read better with none of
   * them labelled than with one of them notched and the others not. The sheet still uses
   * the label as its title, so nothing is lost by hiding it here.
   */
  showLabel: Boolean = true,
) {
  var open by remember { mutableStateOf(false) }

  Column(modifier) {
    Box {
      OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = {},
        label = if (showLabel) {
          { Text(label) }
        } else {
          null
        },
        readOnly = true,
        singleLine = true,
        enabled = false,
        shape = RoundedCornerShape(Radius.large),
        trailingIcon = { Icon(BillIcons.ChevronDown, contentDescription = null) },
        colors = OutlinedTextFieldDefaults.colors(
          disabledTextColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          // Filled and borderless like every other field. It was the one outlined control
          // left on the screen, which made it read as broken rather than as a picker.
          disabledBorderColor = LocalTokens.current.borderStrong,
          disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      if (enabled) {
        // Clipped to the field's own shape, or the press highlight is a square that
        // overhangs the rounded corners it is meant to be inside.
        Box(
          Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(Radius.large))
            .clickable { open = true },
        )
      }
    }

    // The same line every text field reserves for its error or hint, kept empty when
    // there is nothing to say. Without it a picker beside a field is half that line
    // taller and sits below it in a centred row, which is the shift that showed up on
    // the Qty / Unit / Rate row of a bill.
    Box(Modifier.fillMaxWidth().height(18.dp).padding(start = 14.dp, top = 2.dp)) {
      hint?.let {
        Text(
          it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  if (open) {
    ChoiceSheet(
      title = label,
      options = options,
      selected = value,
      onPick = {
        onPick(it)
        open = false
      },
      onDismiss = { open = false },
      searchable = searchable,
    )
  }
}

/**
 * A picker for a tight row: the value, a chevron, nothing else.
 *
 * `PickerField` is a Material outlined field, and its internal padding plus a 48dp
 * trailing icon leaves about 30dp for the text — which is why "pcs" was arriving clipped
 * in a 96dp box next to the quantity. This draws the box itself, so the width goes to
 * the word instead of to the chrome around it.
 */
@Composable
fun CompactPicker(
  value: String,
  options: List<String>,
  onPick: (String) -> Unit,
  title: String,
  modifier: Modifier = Modifier,
) {
  var open by remember { mutableStateOf(false) }
  val tokens = LocalTokens.current
  val shape = RoundedCornerShape(Radius.large)

  Column(modifier) {
    Row(
      Modifier
        .height(np.bill.ui.common.FieldHeight)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, tokens.borderStrong, shape)
        .clickable { open = true }
        .padding(start = 12.dp, end = 8.dp),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
      Text(
        value,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        modifier = Modifier.weight(1f),
      )
      Icon(
        BillIcons.ChevronDown,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    }

    // The reserved line every field carries, so a row of them stays aligned.
    Box(Modifier.height(18.dp))
  }

  if (open) {
    ChoiceSheet(
      title = title,
      options = options,
      selected = value,
      onPick = {
        onPick(it)
        open = false
      },
      onDismiss = { open = false },
    )
  }
}

/**
 * A text field that writes Nepali from Roman letters.
 *
 * Converting on every keystroke was unusable: the field's own output was fed back in on
 * the next character, so `pasal` became `पsal`, then `पसl`, and there was no way to
 * correct a letter because the letters were no longer there. Nobody could type a whole
 * word.
 *
 * So conversion happens at a word boundary instead. You see the Roman you typed —
 * editable, backspaceable, exactly what you pressed — and the word turns into Devanagari
 * when you finish it with a space or punctuation, or when you leave the field. That is
 * how every phonetic input method people already use behaves, and it is the only version
 * where a typo can be fixed.
 */
@Composable
fun RomanizedField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  romanize: Boolean,
  onToggleRomanize: (Boolean) -> Unit,
  imeAction: ImeAction = ImeAction.Next,
  supporting: String? = null,
) {
  var focused by remember { mutableStateOf(false) }

  Column(modifier) {
    OutlinedTextField(
      value = value,
      onValueChange = { typed ->
        onValueChange(if (romanize) convertFinishedWords(typed) else typed)
      },
      label = { Text(label) },
      singleLine = true,
      shape = RoundedCornerShape(Radius.large),
      trailingIcon = {
        // A toggle that says what it does: क means the next word comes out in Nepali.
        TextButton(onClick = { onToggleRomanize(!romanize) }) {
          Text(
            if (romanize) "क" else "A",
            style = MaterialTheme.typography.titleMedium,
            color = if (romanize) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          )
        }
      },
      keyboardOptions = KeyboardOptions(imeAction = imeAction),
      colors = fieldColors(),
      modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { focus ->
          val leaving = focused && !focus.isFocused
          focused = focus.isFocused
          // Leaving the field finishes whatever word was still being typed.
          if (leaving && romanize && value.isNotBlank()) {
            onValueChange(Romanizer.toDevanagari(value))
          }
        },
    )

    Box(Modifier.fillMaxWidth().height(18.dp).padding(start = 14.dp, top = 2.dp)) {
      Text(
        supporting ?: if (romanize) {
          stringResource(R.string.romanize_on)
        } else {
          stringResource(R.string.romanize_off)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Converts every word that has been finished, and leaves the one still being typed alone.
 *
 * A word is finished when something that is not a letter follows it. Anything already in
 * Devanagari passes straight through, so re-running this over its own output is a no-op —
 * which is what makes it safe to call on every keystroke.
 */
internal fun convertFinishedWords(text: String): String {
  if (text.isEmpty()) return text

  val lastBoundary = text.indexOfLast { !it.isLetter() }
  if (lastBoundary < 0) return text

  val finished = text.substring(0, lastBoundary + 1)
  val inProgress = text.substring(lastBoundary + 1)
  return Romanizer.toDevanagari(finished) + inProgress
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
  focusedBorderColor = MaterialTheme.colorScheme.primary,
  unfocusedBorderColor = LocalTokens.current.border,
  unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
  focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)
