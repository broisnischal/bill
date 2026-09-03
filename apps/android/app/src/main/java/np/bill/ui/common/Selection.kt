package np.bill.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius
import np.bill.ui.theme.TouchTarget

/**
 * The bar that appears when rows are ticked.
 *
 * It slides up over the list rather than pushing it, so nothing moves under a thumb that
 * is mid-tap, and it says how many are selected in words — a bare number beside an icon
 * is exactly the sort of thing that only makes sense to whoever built it.
 */
@Composable
fun SelectionBar(
  count: Int,
  actionLabel: String,
  onAction: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalTokens.current

  AnimatedVisibility(
    visible = count > 0,
    enter = slideInVertically { it },
    exit = slideOutVertically { it },
    modifier = modifier,
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(12.dp)
        .clip(RoundedCornerShape(Radius.large))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(1.dp, tokens.border, RoundedCornerShape(Radius.large))
        .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onClear) {
        Icon(BillIcons.X, contentDescription = stringResource(R.string.clear_selection))
      }
      Text(
        stringResource(R.string.selected_count, count),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      Button(
        onClick = onAction,
        shape = RoundedCornerShape(Radius.medium),
        modifier = Modifier.heightIn(min = 40.dp),
      ) {
        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

/**
 * The search box, one shape everywhere.
 *
 * Each list had grown its own, with different corners and different padding, so moving
 * between Bills, Products and Customers felt like moving between three apps.
 */
@Composable
fun SearchBar(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  val tokens = LocalTokens.current

  Row(
    modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = {
        Text(placeholder, style = MaterialTheme.typography.bodyMedium)
      },
      leadingIcon = {
        Icon(
          BillIcons.Search,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyLarge,
      shape = RoundedCornerShape(Radius.pill),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = tokens.borderStrong,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
      ),
      modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
    )
    trailing?.let {
      Spacer(Modifier.width(2.dp))
      it()
    }
  }
}

/**
 * One of a few options, as a single connected control.
 *
 * Loose chips scattered across a row read as several independent switches; a pill inside
 * a track reads as one question with one answer, which is what these actually are. The
 * chosen half is the one that looks lifted, and it is the only one carrying a shadow.
 */
@Composable
fun <T> SegmentedChoice(
  options: List<Pair<T, String>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalTokens.current
  val pill = RoundedCornerShape(Radius.pill)

  Row(
    modifier
      .fillMaxWidth()
      .clip(pill)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    for ((value, label) in options) {
      val active = value == selected
      Row(
        Modifier
          .weight(1f)
          .then(
            if (active && !tokens.isDark) {
              Modifier.shadow(4.dp, pill, ambientColor = tokens.shadow, spotColor = tokens.shadow)
            } else {
              Modifier
            },
          )
          .clip(pill)
          .background(
            if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
          )
          .clickable { onSelect(value) }
          .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelLarge,
          color = if (active) {
            MaterialTheme.colorScheme.onSurface
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          maxLines = 1,
        )
      }
    }
  }
}
