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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.core.nepali.BsCalendar
import np.bill.core.nepali.BsDate
import np.bill.ui.theme.Radius

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
  var choosing by remember { mutableStateOf(Choosing.NONE) }

  val days = remember(year, month) { BsCalendar.daysInMonth(year, month) }

  // Nepali months run 29 to 32 days, so jumping from a 32-day month to a 29-day one with
  // the 31st picked would leave a selection the grid no longer shows.
  LaunchedEffect(year, month, days) {
    if (selected.year == year && selected.month == month && selected.day > days) {
      selected = BsDate(year, month, days)
    }
  }

  when (choosing) {
    Choosing.MONTH -> ChoiceSheet(
      title = stringResource(R.string.pick_month),
      options = BsCalendar.MONTHS_EN.toList(),
      selected = BsCalendar.MONTHS_EN[month - 1],
      onPick = { picked ->
        month = BsCalendar.MONTHS_EN.indexOf(picked) + 1
        choosing = Choosing.NONE
      },
      onDismiss = { choosing = Choosing.NONE },
    )

    Choosing.YEAR -> ChoiceSheet(
      title = stringResource(R.string.pick_year),
      options = (BsCalendar.EPOCH_YEAR..BsCalendar.LAST_YEAR).map(Int::toString),
      selected = year.toString(),
      // Ninety-one of them. Searchable is the difference between typing 2045 and
      // scrolling for it.
      searchable = true,
      onPick = { picked ->
        year = picked.toInt()
        choosing = Choosing.NONE
      },
      onDismiss = { choosing = Choosing.NONE },
    )

    Choosing.NONE -> Unit
  }

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

      // The month and the year are each a target, not a caption.
      //
      // The chevrons step one month, which is right for "last month" and useless for
      // anything else: the calendar runs from 2000 to 2090, so a registration date a
      // shopkeeper is copying off a certificate could be four hundred taps away. Tapping
      // the year opens a searchable list, so typing the four digits gets there.
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        HeaderChip(
          primary = BsCalendar.MONTHS_EN[month - 1],
          secondary = BsCalendar.MONTHS_NE[month - 1],
          onClick = { choosing = Choosing.MONTH },
        )
        HeaderChip(
          primary = year.toString(),
          secondary = BsCalendar.toNepaliDigits(year.toString()),
          onClick = { choosing = Choosing.YEAR },
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

/** Which of the two header choosers is open, if either. */
private enum class Choosing { NONE, MONTH, YEAR }

/**
 * A tappable piece of the calendar's heading, in both scripts.
 *
 * Shaped like the chips elsewhere in the app so it reads as something to press. The
 * heading used to be plain text, which is the whole reason the only way to reach another
 * year was the chevron beside it.
 */
@Composable
private fun HeaderChip(primary: String, secondary: String, onClick: () -> Unit) {
  val tokens = LocalTokens.current
  val shape = RoundedCornerShape(Radius.pill)

  Column(
    Modifier
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(primary, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    Text(
      secondary,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}
