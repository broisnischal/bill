package np.bill.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import np.bill.R
import np.bill.ui.theme.BillIcons
import np.bill.ui.theme.Radius
import np.bill.ui.theme.TouchTarget

/**
 * "You tapped this — what did you want?"
 *
 * A row that does one thing when tapped will do the wrong thing for half the people who
 * tap it. Two labelled buttons cost one extra tap and remove the guessing, which matters
 * more here than saving the tap: the people using this are not going to learn that a long
 * press means something different.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
  title: String,
  primary: Pair<String, () -> Unit>,
  secondary: Pair<String, () -> Unit>? = null,
  subtitle: String? = null,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(
      topStart = Radius.sheet,
      topEnd = Radius.sheet,
    ),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
      Text(title, style = MaterialTheme.typography.headlineSmall)
      subtitle?.let {
        Spacer(Modifier.height(2.dp))
        Text(
          it,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(20.dp))
      PrimaryButton(text = primary.first, onClick = primary.second)

      secondary?.let {
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = it.first, onClick = it.second)
      }
    }
  }
}

/**
 * One question with a list of answers.
 *
 * These were dialogs, which is the wrong shape twice over: a box in the middle of the
 * screen puts the options as far from the thumb as the layout allows, and it decides how
 * much of the list it is willing to show. A sheet arrives from the edge the hand rests
 * on and gives the list the room it needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(
  title: String,
  options: List<String>,
  selected: String?,
  onPick: (String) -> Unit,
  onDismiss: () -> Unit,
  searchable: Boolean = false,
) {
  var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
  val shown = androidx.compose.runtime.remember(query, options) {
    if (query.isBlank()) options else options.filter { it.contains(query, ignoreCase = true) }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(
      topStart = Radius.sheet,
      topEnd = Radius.sheet,
    ),
  ) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
      )

      if (searchable) {
        SearchBar(
          value = query,
          onValueChange = { query = it },
          placeholder = title,
          modifier = Modifier.padding(horizontal = 8.dp),
        )
      }

      Hairline()

      // Opens on the value already chosen rather than at the top of a list of twenty.
      // A picker that makes you scroll to find what it currently says is a picker that
      // has not told you anything.
      val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = shown.indexOf(selected).coerceAtLeast(0),
      )

      LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
        items(shown, key = { it }) { option ->
          Row(
            Modifier
              .fillMaxWidth()
              .heightIn(min = TouchTarget)
              .clickable { onPick(option) }
              .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              option,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.weight(1f),
            )
            // A tick on the one already chosen, nothing on the rest. A radio on every
            // row asks the reader to check twenty of them to find the filled one.
            if (option == selected) {
              Icon(
                BillIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Something went wrong, said once and got out of the way.
 *
 * This was a tinted strip pushed into the middle of the page, so the page grew a
 * paragraph the moment a code was refused and everything under it jumped down — on the
 * one screen where somebody's thumb is already moving toward the next thing. A sheet
 * costs the layout nothing because it is not in the layout, and it can be as long as the
 * thing that went wrong needs it to be.
 *
 * Only for what a person cannot fix by carrying on typing. A refused code is not this:
 * that belongs against the boxes it was typed into, where the correction happens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorSheet(
  title: String,
  message: String,
  onDismiss: () -> Unit,
  /** An action worth offering instead of only closing, such as sending the code again. */
  action: Pair<String, () -> Unit>? = null,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(
      topStart = Radius.sheet,
      topEnd = Radius.sheet,
    ),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
      IconTile(BillIcons.CircleAlert, tone = TileTone.NEGATIVE)
      Spacer(Modifier.height(14.dp))
      Text(title, style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(4.dp))
      Text(
        message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.height(20.dp))
      if (action != null) {
        PrimaryButton(text = action.first, onClick = action.second)
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = stringResource(R.string.close), onClick = onDismiss)
      } else {
        PrimaryButton(text = stringResource(R.string.close), onClick = onDismiss)
      }
    }
  }
}
