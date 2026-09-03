package np.bill.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.ui.common.Panel

/**
 * Everything a shop needs occasionally.
 *
 * Kept off the bottom bar deliberately: reports, dues and settings matter, but not fifty
 * times a day, and a shopkeeper's four thumb-reachable slots belong to the things that do.
 */
@Composable
fun MoreScreen(
  onCreditBook: () -> Unit,
  onDues: () -> Unit,
  onReports: () -> Unit,
  onBusiness: () -> Unit,
  onPreferences: () -> Unit,
  onWebLogin: () -> Unit,
  onPaymentQr: () -> Unit,
  duesPaisa: Long,
  creditPaisa: Long,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
  ) {
    Panel {
      // First in the hub: on most counters the credit book is opened more often than
      // anything else here.
      Entry(
        icon = BillIcons.Wallet,
        label = stringResource(R.string.credit_title),
        detail = if (creditPaisa > 0) {
          "Rs ${np.bill.core.money.formatMoney(creditPaisa)}"
        } else {
          stringResource(R.string.credit_detail)
        },
        onClick = onCreditBook,
      )
      Hairline()
      Entry(
        icon = BillIcons.Clock,
        label = stringResource(R.string.dues_title),
        detail = if (duesPaisa > 0) {
          "Rs ${np.bill.core.money.formatMoney(duesPaisa)}"
        } else {
          null
        },
        onClick = onDues,
      )
      Hairline()
      Entry(
        icon = BillIcons.ChartColumn,
        label = stringResource(R.string.reports_title),
        onClick = onReports,
      )
    }

    Spacer(Modifier.height(16.dp))

    Panel {
      Entry(
        icon = BillIcons.Store,
        label = stringResource(R.string.business_settings),
        onClick = onBusiness,
      )
      Hairline()
      Entry(
        icon = BillIcons.Settings,
        label = stringResource(R.string.settings),
        onClick = onPreferences,
      )
      Hairline()
      Entry(
        icon = BillIcons.QrCode,
        label = stringResource(R.string.qr_payments),
        onClick = onPaymentQr,
      )
      Hairline()
      Entry(
        icon = BillIcons.Monitor,
        label = stringResource(R.string.web_login),
        onClick = onWebLogin,
      )
    }

    Spacer(Modifier.height(32.dp))
  }
}

@Composable
private fun Entry(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  detail: String? = null,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 15.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(14.dp))
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    detail?.let {
      Text(
        it,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.width(8.dp))
    }
    Icon(
      BillIcons.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
