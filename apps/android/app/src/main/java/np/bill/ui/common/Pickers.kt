package np.bill.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
) {
  var open by remember { mutableStateOf(false) }

  Box(modifier) {
    OutlinedTextField(
      value = value.orEmpty(),
      onValueChange = {},
      label = { Text(label) },
      readOnly = true,
      enabled = false,
      trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
      colors = if (enabled) {
        OutlinedTextFieldDefaults.colors(
          disabledTextColor = MaterialTheme.colorScheme.onSurface,
          disabledBorderColor = MaterialTheme.colorScheme.outline,
          disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        OutlinedTextFieldDefaults.colors()
      },
      modifier = Modifier.fillMaxWidth(),
    )
    if (enabled) {
      Box(
        Modifier
          .matchParentSize()
          .clickable { open = true },
      )
    }
  }

  if (open) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, options) {
      if (query.isBlank()) options else options.filter { it.contains(query, ignoreCase = true) }
    }

    AlertDialog(
      onDismissRequest = { open = false },
      title = { Text(label) },
      confirmButton = {
        TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
      },
      text = {
        Column {
          if (searchable) {
            OutlinedTextField(
              value = query,
              onValueChange = { query = it },
              label = { Text(stringResource(R.string.search)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(shown, key = { it }) { option ->
              Text(
                option,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    onPick(option)
                    open = false
                  }
                  .padding(vertical = 14.dp, horizontal = 4.dp),
              )
            }
          }
        }
      },
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
