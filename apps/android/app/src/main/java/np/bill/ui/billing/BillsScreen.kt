package np.bill.ui.billing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.core.money.formatMoney
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import np.bill.core.nepali.BsDate
import np.bill.ui.common.BsDateField
import np.bill.ui.common.ChoiceChip
import androidx.compose.foundation.layout.Box
import np.bill.ui.common.BottomAction
import np.bill.ui.common.DeltaPill
import np.bill.ui.common.MoneyDisplay
import np.bill.ui.common.EmptyState
import np.bill.ui.common.Panel
import np.bill.ui.theme.Radius
import np.bill.ui.common.SearchBar
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone

/**
 * The bill history.
 *
 * Today's takings sit at the top, because that is the number a shopkeeper checks between
 * customers. Filters are folded away until asked for: most days nobody needs them, and
 * the ones who do are looking for a specific bill from a specific week.
 */
@Composable
fun BillsScreen(
  onNewBill: () -> Unit,
  onOpenBill: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: BillingViewModel,
) {
  val state by viewModel.home.collectAsStateWithLifecycle()
  val filters by viewModel.filters.collectAsStateWithLifecycle()
  var showFilters by remember { mutableStateOf(false) }
  val hint by viewModel.numbersHint.collectAsStateWithLifecycle()

  Column(modifier.fillMaxSize()) {
    when {
      // An empty counter asks for numbers here rather than waiting on the schedule,
      // and says which of the three reasons it is actually looking at.
      state.outOfNumbers -> {
        androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.fetchNumbers() }
        when (hint) {
          BillingViewModel.NumbersHint.OFFLINE ->
            Notice(stringResource(R.string.out_of_numbers), tone = NoticeTone.ERROR)
          BillingViewModel.NumbersHint.REFUSED ->
            Notice(stringResource(R.string.numbers_refused), tone = NoticeTone.ERROR)
          BillingViewModel.NumbersHint.FETCHING ->
            Notice(stringResource(R.string.numbers_fetching), tone = NoticeTone.WARN)
        }
      }
      state.numbersLeft in 1..10 -> Notice(stringResource(R.string.numbers_low), tone = NoticeTone.WARN)
    }

    // The day's takings, in the card the gradient sits behind. It is the number a
    // shopkeeper checks between customers, so it gets display size and the paisa go grey.
    Panel(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
      Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text(
          "${stringResource(R.string.today)} · ${BsDate.parse(state.miti)?.formatLong() ?: state.miti}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          MoneyDisplay(state.todayPaisa, style = MaterialTheme.typography.displayMedium)
          Spacer(Modifier.weight(1f))
          DeltaPill(
            text = pluralStringResource(R.plurals.bills_count, state.todayCount, state.todayCount),
            positive = state.todayCount > 0,
          )
        }
      }
    }

    SearchBar(
      value = filters.search,
      onValueChange = viewModel::onSearch,
      placeholder = stringResource(R.string.search),
      trailing = {
        // A circle to match the tabs, so the one control beside the search box does not
        // read as a stray glyph floating next to it.
        Box(
          Modifier
            .size(46.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { showFilters = !showFilters },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            BillIcons.SlidersHorizontal,
            contentDescription = stringResource(R.string.filters),
            tint = if (filters.isActive) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          )
        }
      },
    )

    AnimatedVisibility(visible = showFilters) {
      // One panel rather than three loose rows. Filters are a single question about the
      // list below them, and laid out flat they read as unrelated controls that happen
      // to sit near each other.
      Panel(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        Column(Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              stringResource(R.string.filters),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            // Only offered once there is something to clear, so the row is not carrying
            // a permanently dead control.
            if (filters.isActive) {
              Text(
                stringResource(R.string.clear_filters),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                  .clickable(onClick = viewModel::clearFilters)
                  .padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }

          Spacer(Modifier.height(10.dp))
          Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            for (status in BillStatusFilter.entries) {
              ChoiceChip(
                text = stringResource(status.labelRes),
                selected = filters.status == status,
                onClick = { viewModel.onStatusFilter(status) },
              )
            }
          }

          Spacer(Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            BsDateField(
              value = filters.fromMiti.orEmpty(),
              onValueChange = viewModel::onFromMiti,
              label = stringResource(R.string.filter_from),
              modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            BsDateField(
              value = filters.toMiti.orEmpty(),
              onValueChange = viewModel::onToMiti,
              label = stringResource(R.string.filter_to),
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    if (filters.isActive) {
      Text(
        stringResource(R.string.showing_count, state.visible.size, formatMoney(state.visiblePaisa)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
      )
    }

    Box(
      Modifier
        .weight(1f)
        .padding(top = 4.dp)
        .clip(RoundedCornerShape(topStart = Radius.card, topEnd = Radius.card))
        .background(MaterialTheme.colorScheme.surface),
    ) {
      if (state.visible.isEmpty()) {
        EmptyState(stringResource(R.string.no_bills_yet))
      } else {
        LazyColumn(Modifier.fillMaxSize()) {
          items(state.visible, key = { it.id }, contentType = { "bill" }) { bill ->
            BillRow(bill = bill, onClick = { onOpenBill(bill.id) })
            Hairline(Modifier.padding(start = 68.dp))
          }
        }
      }
    }

    BottomAction(text = stringResource(R.string.new_bill), onClick = onNewBill)
  }
}

enum class BillStatusFilter(val labelRes: Int) {
  ALL(R.string.filter_all),
  ACTIVE(R.string.filter_active),
  CANCELLED(R.string.filter_cancelled),
  UNSYNCED(R.string.filter_unsynced),
}
