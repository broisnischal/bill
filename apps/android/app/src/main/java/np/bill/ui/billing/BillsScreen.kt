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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.core.money.formatPaisa
import np.bill.ui.common.BsDateField
import androidx.compose.foundation.layout.Box
import np.bill.ui.common.BottomAction
import np.bill.ui.common.EmptyState
import np.bill.ui.common.Panel
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

  Column(modifier.fillMaxSize()) {
    when {
      state.outOfNumbers -> Notice(stringResource(R.string.out_of_numbers), tone = NoticeTone.ERROR)
      state.numbersLeft in 1..10 -> Notice(stringResource(R.string.numbers_low), tone = NoticeTone.WARN)
    }

    // The day's takings, as one panel rather than a banded strip: the number is the
    // thing a shopkeeper looks at, and a band draws a line through the middle of it.
    Panel(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
          "${stringResource(R.string.today)} · ${state.miti}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            "Rs ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(formatPaisa(state.todayPaisa), style = MaterialTheme.typography.displayMedium)
          Spacer(Modifier.weight(1f))
          Text(
            stringResource(R.string.bills_today, state.todayCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    SearchBar(
      value = filters.search,
      onValueChange = viewModel::onSearch,
      placeholder = stringResource(R.string.search),
      trailing = {
        IconButton(onClick = { showFilters = !showFilters }) {
          Icon(
            Icons.Filled.FilterList,
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
      Column(Modifier.padding(horizontal = 12.dp)) {
        Row(
          Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (status in BillStatusFilter.entries) {
            FilterChip(
              selected = filters.status == status,
              onClick = { viewModel.onStatusFilter(status) },
              label = { Text(stringResource(status.labelRes)) },
            )
          }
        }
        Spacer(Modifier.height(10.dp))
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
        TextButton(onClick = viewModel::clearFilters) {
          Text(stringResource(R.string.clear_filters))
        }
      }
    }

    if (filters.isActive) {
      Text(
        stringResource(R.string.showing_count, state.visible.size, formatPaisa(state.visiblePaisa)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
      )
    }

    Hairline()

    if (state.visible.isEmpty()) {
      Box(Modifier.weight(1f)) { EmptyState(stringResource(R.string.no_bills_yet)) }
    } else {
      LazyColumn(Modifier.weight(1f)) {
        items(state.visible, key = { it.id }, contentType = { "bill" }) { bill ->
          BillRow(bill = bill, onClick = { onOpenBill(bill.id) })
          Hairline(Modifier.padding(start = 16.dp))
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
