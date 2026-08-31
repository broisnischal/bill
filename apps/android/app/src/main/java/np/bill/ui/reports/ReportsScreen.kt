package np.bill.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.core.money.formatPaisa
import np.bill.ui.common.EmptyState
import np.bill.ui.common.Hairline
import np.bill.ui.common.Panel
import np.bill.ui.theme.LocalTokens

/**
 * What the shop has actually done this year.
 *
 * Deliberately four numbers and one chart, not a dashboard: a shopkeeper wants to know
 * what came in, what the tax office is owed, and what is selling. Everything here is
 * computed from bills already on the device, so it opens with no network.
 */
@Composable
fun ReportsScreen(
  modifier: Modifier = Modifier,
  viewModel: ReportsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
  ) {
    Text(
      stringResource(R.string.this_fiscal_year, state.fiscalYear),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    if (state.billCount == 0) {
      EmptyState(stringResource(R.string.no_sales_yet))
      return@Column
    }

    Panel {
      Column(Modifier.padding(16.dp)) {
        Text(
          stringResource(R.string.sales_total),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text("Rs ", style = MaterialTheme.typography.titleLarge)
          Text(formatPaisa(state.salesPaisa), style = MaterialTheme.typography.displayMedium)
        }
      }
      Hairline()
      Row(Modifier.fillMaxWidth()) {
        Metric(
          label = stringResource(R.string.bill_count),
          value = state.billCount.toString(),
          modifier = Modifier.weight(1f),
        )
        Metric(
          label = stringResource(R.string.average_bill),
          value = "Rs ${formatPaisa(state.averagePaisa)}",
          modifier = Modifier.weight(1f),
        )
      }
      Hairline()
      Row(Modifier.fillMaxWidth()) {
        Metric(
          label = stringResource(R.string.taxable_sales),
          value = "Rs ${formatPaisa(state.taxablePaisa)}",
          modifier = Modifier.weight(1f),
        )
        Metric(
          label = stringResource(R.string.vat_collected),
          value = "Rs ${formatPaisa(state.vatPaisa)}",
          modifier = Modifier.weight(1f),
        )
      }
      if (state.exemptPaisa > 0) {
        Hairline()
        Metric(
          label = stringResource(R.string.exempt_sales),
          value = "Rs ${formatPaisa(state.exemptPaisa)}",
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    Spacer(Modifier.height(16.dp))

    Panel {
      Column(Modifier.padding(16.dp)) {
        Text(
          stringResource(R.string.last_days),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        DailyBars(state.recentDays)
      }
    }

    if (state.topProducts.isNotEmpty()) {
      Spacer(Modifier.height(16.dp))
      Panel {
        Column(Modifier.padding(16.dp)) {
          Text(
            stringResource(R.string.top_products),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        for (product in state.topProducts) {
          Hairline()
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              product.name,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.weight(1f),
              maxLines = 1,
            )
            Text(
              "Rs ${formatPaisa(product.totalPaisa)}",
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      }
    }

    Spacer(Modifier.height(32.dp))
  }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier.padding(16.dp)) {
    Text(
      label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(2.dp))
    Text(value, style = MaterialTheme.typography.headlineSmall)
  }
}

/**
 * Fourteen bars, scaled to the busiest of them.
 *
 * Drawn rather than charted: a shopkeeper is reading the shape of a fortnight, not
 * values off an axis, and a charting library would be a megabyte for this.
 */
@Composable
private fun DailyBars(days: List<DayTotal>) {
  val tokens = LocalTokens.current
  val accent = MaterialTheme.colorScheme.primary
  val peak = days.maxOfOrNull { it.totalPaisa }?.coerceAtLeast(1) ?: 1

  Canvas(Modifier.fillMaxWidth().height(96.dp)) {
    if (days.isEmpty()) return@Canvas
    val gap = 6.dp.toPx()
    val barWidth = (size.width - gap * (days.size - 1)) / days.size

    days.forEachIndexed { index, day ->
      val fraction = day.totalPaisa.toFloat() / peak
      // Every day gets a visible stub, so an empty day reads as "nothing sold" rather
      // than as a gap in the chart.
      val height = (size.height * fraction).coerceAtLeast(2.dp.toPx())
      drawRoundRect(
        color = if (day.totalPaisa > 0) accent else tokens.border,
        topLeft = Offset(index * (barWidth + gap), size.height - height),
        size = Size(barWidth, height),
        cornerRadius = CornerRadius(2.dp.toPx()),
      )
    }
  }
}
