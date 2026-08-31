package np.bill.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PrintDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import np.bill.R

/**
 * The two things a shopkeeper glances at between customers: is the printer there, and has
 * the office got today's bills. Both live in the top bar as icons, because reading a word
 * takes longer than seeing a colour and neither is worth a screen of its own.
 */
@Composable
fun StatusIcons(
  printerConnected: Boolean,
  printerName: String?,
  pendingSync: Int,
  offline: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    val printerLabel = when {
      printerName != null -> stringResource(R.string.printer_ready, printerName)
      else -> stringResource(R.string.printer_none)
    }
    Icon(
      imageVector = if (printerConnected) Icons.Filled.Print else Icons.Filled.PrintDisabled,
      contentDescription = printerLabel,
      tint = if (printerConnected) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier.size(22.dp),
    )

    Spacer(Modifier.width(14.dp))

    val syncLabel = when {
      offline -> stringResource(R.string.offline_banner)
      pendingSync > 0 -> stringResource(R.string.pending_sync, pendingSync)
      else -> stringResource(R.string.synced)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = when {
          offline -> Icons.Filled.CloudOff
          pendingSync > 0 -> Icons.Filled.CloudQueue
          else -> Icons.Filled.CloudDone
        },
        contentDescription = syncLabel,
        tint = when {
          offline -> MaterialTheme.colorScheme.onSurfaceVariant
          pendingSync > 0 -> Amber
          else -> MaterialTheme.colorScheme.primary
        },
        modifier = Modifier.size(22.dp),
      )
      if (pendingSync > 0) {
        Spacer(Modifier.width(4.dp))
        Text(
          pendingSync.toString(),
          style = MaterialTheme.typography.labelMedium,
          color = Amber,
          modifier = Modifier.semantics { contentDescription = syncLabel },
        )
      }
    }
  }
}

/** A count on a bottom-navigation item, for bills still waiting to go up. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
  AnimatedVisibility(
    visible = count > 0,
    enter = expandVertically(),
    exit = shrinkVertically(),
    modifier = modifier,
  ) {
    Row(
      Modifier
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.error)
        .size(width = if (count > 9) 22.dp else 18.dp, height = 18.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        if (count > 99) "99+" else count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onError,
      )
    }
  }
}

/** Warm amber for "not finished yet", which neither green nor red says. */
val Amber = Color(0xFF8A6D00)
