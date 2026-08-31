package np.bill.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import np.bill.R
import np.bill.ui.customer.MyCardScreen
import np.bill.ui.customer.ScanScreen
import np.bill.ui.customer.WalletScreen
import np.bill.ui.settings.SettingsScreen

/**
 * Customer mode.
 *
 * A shopper does three things: look at bills they kept, scan a new one, and show their
 * card so a shop can bill them by name. Nothing here can reach a store.
 */
@Composable
fun CustomerHome(
  deepLinkToken: String?,
  onSwitchMode: () -> Unit,
  onSignedOut: () -> Unit,
) {
  var selected by rememberSaveable(deepLinkToken) {
    // A scanned bill link opens straight on the scanner, which files it and shows it.
    mutableStateOf(if (deepLinkToken != null) Tabs.scan.route else Tabs.wallet.route)
  }

  val title = when (selected) {
    Tabs.scan.route -> stringResource(R.string.scan_bill)
    Tabs.card.route -> stringResource(R.string.my_card_title)
    Tabs.more.route -> stringResource(R.string.settings)
    else -> stringResource(R.string.my_bills)
  }

  Shell(
    title = title,
    tabs = listOf(Tabs.wallet, Tabs.scan, Tabs.card, Tabs.more),
    selectedRoute = selected,
    onSelect = { selected = it },
    showStatus = false,
  ) { modifier ->
    when (selected) {
      Tabs.scan.route -> ScanScreen(
        initialToken = deepLinkToken,
        onDone = { selected = Tabs.wallet.route },
        modifier = modifier,
      )
      Tabs.card.route -> MyCardScreen(modifier = modifier)
      Tabs.more.route -> SettingsScreen(
        onSwitchMode = onSwitchMode,
        onSignedOut = onSignedOut,
        modifier = modifier,
      )
      else -> WalletScreen(onScan = { selected = Tabs.scan.route }, modifier = modifier)
    }
  }
}
