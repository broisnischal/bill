package np.bill.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.billing.BillingViewModel
import np.bill.ui.billing.BillsScreen
import np.bill.ui.catalog.CustomersScreen
import np.bill.ui.catalog.ItemsScreen
import np.bill.ui.home.HomeScreen
import np.bill.ui.dues.DuesScreen
import np.bill.ui.reports.ReportsScreen
import np.bill.ui.settings.BusinessSettingsScreen
import np.bill.ui.settings.MoreScreen
import np.bill.ui.settings.WebLoginScreen
import np.bill.ui.settings.SettingsScreen
import np.bill.ui.theme.Radius

/**
 * Everything a shop does, behind four tabs.
 *
 * The tabs share one activity and one back stack entry, so switching between them keeps
 * scroll position and costs no recreation — which is most of why moving around the app
 * feels immediate rather than like loading a page.
 */
@Composable
fun BusinessHome(
  onNewBill: (itemIds: List<String>, customerId: String?) -> Unit,
  onOpenBill: (String) -> Unit,
  onSwitchMode: () -> Unit,
  onSignedOut: () -> Unit,
  viewModel: BillingViewModel = hiltViewModel(),
) {
  val state by viewModel.home.collectAsStateWithLifecycle()
  var selected by rememberSaveable { mutableStateOf(Tabs.home.route) }
  var addProduct by remember { mutableStateOf(false) }
  var addCustomer by remember { mutableStateOf(false) }
  var morePage by rememberSaveable { mutableStateOf(MorePage.HUB) }

  // Leaving the More tab puts it back at the hub, so returning never lands mid-form.
  LaunchedEffect(selected) { if (selected != Tabs.more.route) morePage = MorePage.HUB }

  // Inside More, back steps up to the hub before it leaves the app.
  BackHandler(enabled = selected == Tabs.more.route && morePage != MorePage.HUB) {
    morePage = MorePage.HUB
  }

  val tabs = remember(state.pendingSync) {
    listOf(
      Tabs.home,
      Tabs.bills.copy(badge = state.pendingSync),
      Tabs.products,
      Tabs.customers,
      Tabs.more,
    )
  }

  val title = when {
    selected == Tabs.home.route -> state.storeName.ifBlank { stringResource(R.string.app_name) }
    selected == Tabs.bills.route -> stringResource(R.string.nav_bills)
    selected == Tabs.products.route -> stringResource(R.string.nav_products)
    selected == Tabs.customers.route -> stringResource(R.string.nav_customers)
    selected == Tabs.more.route -> when (morePage) {
      MorePage.DUES -> stringResource(R.string.dues_title)
      MorePage.REPORTS -> stringResource(R.string.reports_title)
      MorePage.BUSINESS -> stringResource(R.string.business_settings)
      MorePage.PREFERENCES -> stringResource(R.string.settings)
      MorePage.WEB_LOGIN -> stringResource(R.string.web_login)
      MorePage.HUB -> stringResource(R.string.nav_more)
    }
    else -> state.storeName.ifBlank { stringResource(R.string.app_name) }
  }

  Shell(
    title = title,
    tabs = tabs,
    selectedRoute = selected,
    onSelect = { selected = it },
    printerConnected = state.printerConnected,
    printerName = state.printerName,
    pendingSync = state.pendingSync,
  ) { modifier ->
    // A short crossfade between tabs. Swapping instantly on a dark background reads as a
    // flash, because the two screens rarely have content in the same places.
    Crossfade(
      targetState = selected to morePage,
      animationSpec = tween(140),
      label = "tab",
      // Named apart from the state they mirror: the callbacks below still assign to the
      // outer values, and shadowing them here would make those assignments illegal.
    ) { (tab, page) ->
    when (tab) {
      Tabs.home.route -> HomeScreen(
        miti = state.miti,
        todayPaisa = state.todayPaisa,
        todayCount = state.todayCount,
        duePaisa = state.duePaisa,
        pendingSync = state.pendingSync,
        onNewBill = { onNewBill(emptyList(), null) },
        onAddProduct = {
          selected = Tabs.products.route
          addProduct = true
        },
        onAddCustomer = {
          selected = Tabs.customers.route
          addCustomer = true
        },
        onSettings = {
          selected = Tabs.more.route
          morePage = MorePage.PREFERENCES
        },
        onDues = {
          selected = Tabs.more.route
          morePage = MorePage.DUES
        },
        onBills = { selected = Tabs.bills.route },
        modifier = modifier,
      )
      Tabs.products.route -> ItemsScreen(
        addRequested = addProduct,
        onAddHandled = { addProduct = false },
        onBillWithItems = { itemIds -> onNewBill(itemIds, null) },
        modifier = modifier,
      )
      Tabs.customers.route -> CustomersScreen(
        addRequested = addCustomer,
        onAddHandled = { addCustomer = false },
        onBillFor = { customerId -> onNewBill(emptyList(), customerId) },
        modifier = modifier,
      )
      Tabs.more.route -> when (page) {
        MorePage.DUES -> DuesScreen(modifier = modifier)
        MorePage.REPORTS -> ReportsScreen(modifier = modifier)
        MorePage.BUSINESS -> BusinessSettingsScreen(
          onBack = { morePage = MorePage.HUB },
          modifier = modifier,
        )
        MorePage.WEB_LOGIN -> WebLoginScreen(
          onDone = { morePage = MorePage.HUB },
          modifier = modifier,
        )
        MorePage.PREFERENCES -> SettingsScreen(
          onSwitchMode = onSwitchMode,
          onSignedOut = onSignedOut,
          modifier = modifier,
        )
        MorePage.HUB -> MoreScreen(
          onDues = { morePage = MorePage.DUES },
          onReports = { morePage = MorePage.REPORTS },
          onBusiness = { morePage = MorePage.BUSINESS },
          onPreferences = { morePage = MorePage.PREFERENCES },
          onWebLogin = { morePage = MorePage.WEB_LOGIN },
          duesPaisa = state.duePaisa,
          modifier = modifier,
        )
      }
      else -> BillsScreen(
        onNewBill = { onNewBill(emptyList(), null) },
        onOpenBill = onOpenBill,
        modifier = modifier,
        viewModel = viewModel,
      )
    }
    }
  }
}

/** Where the More tab currently is. It is a small stack, not a second navigation graph. */
private enum class MorePage { HUB, DUES, REPORTS, BUSINESS, PREFERENCES, WEB_LOGIN }
