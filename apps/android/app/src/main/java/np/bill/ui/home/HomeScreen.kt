package np.bill.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import np.bill.R
import np.bill.core.money.formatPaisa
import np.bill.core.nepali.BsDate
import np.bill.ui.common.Panel
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * The first screen.
 *
 * A shopkeeper opens this app to do one of four things, and this is those four things as
 * targets big enough to hit without looking. Making a bill is the reason the app exists,
 * so it takes the whole width and everything else shares the rows underneath.
 *
 * Above them is the only number anyone checks between customers: what has come in today.
 * Below, what is still owed, because that is the number people forget.
 */
@Composable
fun HomeScreen(
  miti: String,
  todayPaisa: Long,
  todayCount: Int,
  duePaisa: Long,
  pendingSync: Int,
  onNewBill: () -> Unit,
  onAddProduct: () -> Unit,
  onAddCustomer: () -> Unit,
  onSettings: () -> Unit,
  onDues: () -> Unit,
  onBills: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalTokens.current

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 12.dp),
  ) {
    Spacer(Modifier.height(8.dp))

    Panel {
      Column(Modifier.padding(16.dp)) {
        Text(
          "${stringResource(R.string.today)} · ${BsDate.parse(miti)?.formatLong() ?: miti}",
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
          Text(formatPaisa(todayPaisa), style = MaterialTheme.typography.displayLarge)
        }
        Text(
          stringResource(R.string.bills_today, todayCount),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Spacer(Modifier.height(12.dp))

    // The reason the app is open. Full width, and the only filled thing on the screen.
    Row(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.large))
        .background(MaterialTheme.colorScheme.primary)
        .clickable(onClick = onNewBill)
        .padding(horizontal = 20.dp, vertical = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.Add,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(26.dp),
      )
      Spacer(Modifier.size(12.dp))
      Text(
        stringResource(R.string.new_bill),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }

    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Tile(
        icon = Icons.Filled.Inventory2,
        label = stringResource(R.string.add_product),
        onClick = onAddProduct,
        modifier = Modifier.weight(1f),
      )
      Tile(
        icon = Icons.Filled.Groups,
        label = stringResource(R.string.add_customer),
        onClick = onAddCustomer,
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Tile(
        icon = Icons.AutoMirrored.Filled.ReceiptLong,
        label = stringResource(R.string.nav_bills),
        badge = pendingSync,
        onClick = onBills,
        modifier = Modifier.weight(1f),
      )
      Tile(
        icon = Icons.Filled.Tune,
        label = stringResource(R.string.settings),
        onClick = onSettings,
        modifier = Modifier.weight(1f),
      )
    }

    if (duePaisa > 0) {
      Spacer(Modifier.height(12.dp))
      Panel {
        Row(
          Modifier
            .fillMaxWidth()
            .clickable(onClick = onDues)
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(R.string.total_due),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
              "Rs ${formatPaisa(duePaisa)}",
              style = MaterialTheme.typography.headlineMedium,
              color = tokens.due,
            )
          }
          Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Spacer(Modifier.height(32.dp))
  }
}

/**
 * One square on the grid. Icon over label, both centred, and the whole tile is the
 * target — not the icon, which is what people aim at and miss.
 */
@Composable
private fun Tile(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badge: Int = 0,
) {
  val tokens = LocalTokens.current

  Column(
    modifier
      .clip(RoundedCornerShape(Radius.large))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(1.dp, tokens.border, RoundedCornerShape(Radius.large))
      .clickable(onClick = onClick)
      .padding(vertical = 20.dp, horizontal = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box {
      Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(26.dp),
      )
      if (badge > 0) {
        Box(
          Modifier
            .align(Alignment.TopEnd)
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tokens.warning),
        )
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(
      label,
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      maxLines = 2,
    )
  }
}
