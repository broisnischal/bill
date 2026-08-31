package np.bill.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import np.bill.R
import np.bill.ui.common.StatusIcons

/**
 * The frame every day-to-day screen sits in.
 *
 * A shopkeeper's four jobs — write a bill, manage what they sell, manage who they sell
 * to, and change a setting — are one tap apart rather than buried in a drawer. The top
 * bar carries only what they glance at mid-sale: whether the printer is there and whether
 * the office has today's bills.
 */
object Tabs {
  val home = ShellTab("tab/home", R.string.nav_home, Icons.Filled.Home)
  val bills = ShellTab("tab/bills", R.string.nav_bills, Icons.AutoMirrored.Filled.ReceiptLong)
  val products = ShellTab("tab/products", R.string.nav_products, Icons.Filled.Inventory2)
  val customers = ShellTab("tab/customers", R.string.nav_customers, Icons.Filled.Groups)
  val more = ShellTab("tab/more", R.string.nav_more, Icons.Filled.Tune)

  val wallet = ShellTab("tab/wallet", R.string.nav_wallet, Icons.AutoMirrored.Filled.ReceiptLong)
  val scan = ShellTab("tab/scan", R.string.scan_bill, Icons.Filled.QrCodeScanner)
  val card = ShellTab("tab/card", R.string.nav_card, Icons.Filled.QrCode2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Shell(
  title: String,
  tabs: List<ShellTab>,
  selectedRoute: String,
  onSelect: (String) -> Unit,
  printerConnected: Boolean = false,
  printerName: String? = null,
  pendingSync: Int = 0,
  offline: Boolean = false,
  showStatus: Boolean = true,
  floatingActionButton: @Composable () -> Unit = {},
  content: @Composable (Modifier) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title, maxLines = 1) },
        actions = {
          if (showStatus) {
            StatusIcons(
              printerConnected = printerConnected,
              printerName = printerName,
              pendingSync = pendingSync,
              offline = offline,
              modifier = Modifier.padding(end = 16.dp),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
      )
    },
    bottomBar = {
      NavigationBar {
        for (tab in tabs) {
          NavigationBarItem(
            selected = tab.route == selectedRoute,
            onClick = { onSelect(tab.route) },
            icon = {
              BadgedBox(
                badge = {
                  if (tab.badge > 0) Badge { Text(if (tab.badge > 99) "99+" else "${tab.badge}") }
                },
              ) {
                Icon(tab.icon, contentDescription = null)
              }
            },
            label = { Text(stringResource(tab.labelRes), maxLines = 1) },
            alwaysShowLabel = true,
          )
        }
      }
    },
    floatingActionButton = floatingActionButton,
  ) { padding ->
    Box(Modifier.fillMaxSize()) {
      content(Modifier.padding(padding))
    }
  }
}

data class ShellTab(
  val route: String,
  val labelRes: Int,
  val icon: ImageVector,
  val badge: Int = 0,
)
