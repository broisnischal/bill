package np.bill.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.core.nepali.BsCalendar
import np.bill.core.nepali.BsDate

/**
 * A Bikram Sambat calendar.
 *
 * Every date a Nepali business writes down is BS, and asking someone to type `2080-05-10`
 * into a text field is how you get `2080/5/10`, `10-05-2080` and a registration date that
 * is off by a month. The grid is the actual Nepali calendar: months of 29 to 32 days, in
 * the right order, from the same table the bill's miti is computed with.
 */
@Composable
fun BsDateField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  error: String? = null,
) {
  var picking by remember { mutableStateOf(false) }
  val parsed = BsDate.parse(value)

  Box(modifier) {
    OutlinedTextField(
      value = parsed?.formatLong() ?: value,
      onValueChange = {},
      label = { Text(label) },
      readOnly = true,
      enabled = false,
      isError = error != null,
      supportingText = error?.let { { Text(it) } },
      colors = disabledLooksEnabled(),
      modifier = Modifier.fillMaxWidth(),
    )
    // The field is read-only, so the whole thing is the tap target for the calendar.
    Box(
      Modifier
        .matchParentSize()
        .clickable { picking = true },
    )
  }

  if (picking) {
    BsDatePickerDialog(
      initial = parsed ?: BsDate.now(),
      onPick = {
        onValueChange(it.toString())
        picking = false
      },
      onDismiss = { picking = false },
    )
  }
}

@Composable
fun BsDatePickerDialog(
  initial: BsDate,
  onPick: (BsDate) -> Unit,
  onDismiss: () -> Unit,
) {
  var year by remember { mutableStateOf(initial.year) }
  var month by remember { mutableStateOf(initial.month) }
  var selected by remember { mutableStateOf(initial) }

  val days = remember(year, month) { BsCalendar.daysInMonth(year, month) }

  // A sheet, not a dialog. A calendar is a grid of forty targets and a box floating in
  // the middle of the screen puts every one of them out of thumb reach.
  FormSheet(
    title = stringResource(R.string.pick_date),
    onDismiss = onDismiss,
    heightFraction = 0.62f,
    action = {
      PrimaryButton(text = stringResource(R.string.done), onClick = { onPick(selected) })
    },
  ) {
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = {
        if (month == 1) {
          if (year > BsCalendar.EPOCH_YEAR) { year--; month = 12 }
        } else {
          month--
        }
      }) {
        Icon(BillIcons.ChevronLeft, contentDescription = null)
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          "${BsCalendar.MONTHS_EN[month - 1]} $year",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          "${BsCalendar.MONTHS_NE[month - 1]} ${BsCalendar.toNepaliDigits(year.toString())}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      IconButton(onClick = {
        if (month == 12) {
          if (year < BsCalendar.LAST_YEAR) { year++; month = 1 }
        } else {
          month++
        }
      }) {
        Icon(BillIcons.ChevronRight, contentDescription = null)
      }
    }

    Spacer(Modifier.height(8.dp))
    LazyVerticalGrid(
      columns = GridCells.Fixed(7),
      modifier = Modifier.height(260.dp),
    ) {
      items((1..days).toList(), key = { it }) { day ->
        val isSelected = selected.year == year && selected.month == month && selected.day == day
        Box(
          Modifier
            .padding(3.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
              if (isSelected) {
                LocalTokens.current.ink
              } else {
                androidx.compose.ui.graphics.Color.Transparent
              },
            )
            .clickable { selected = BsDate(year, month, day) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            BsCalendar.toNepaliDigits(day.toString()),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (isSelected) {
              LocalTokens.current.onInk
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          )
        }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

/**
 * A disabled field that still reads as a live one. Compose has no read-only-but-tappable
 * text field, and a genuinely disabled-looking control invites nobody to tap it.
 */
@Composable
private fun disabledLooksEnabled() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
  disabledTextColor = MaterialTheme.colorScheme.onSurface,
  disabledBorderColor = MaterialTheme.colorScheme.outline,
  disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
  disabledSupportingTextColor = MaterialTheme.colorScheme.error,
)
