package np.bill.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import np.bill.ui.payments.PaymentQrScreen
import np.bill.ui.payments.PaymentQrViewModel
import np.bill.ui.payments.ShowQrSheet
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
  onQuickBill: (templateId: String) -> Unit,
  onOpenBill: (String) -> Unit,
  onSwitchMode: () -> Unit,
  onSignedOut: () -> Unit,
  viewModel: BillingViewModel = hiltViewModel(),
) {
  val state by viewModel.home.collectAsStateWithLifecycle()
  val templates by viewModel.templates.collectAsStateWithLifecycle()
  // The book's total lives on the hub row, so it is read here rather than inside it.
  val credit: np.bill.ui.credit.CreditBookViewModel = hiltViewModel()
  val creditOutstanding by credit.outstanding.collectAsStateWithLifecycle()
  var selected by rememberSaveable { mutableStateOf(Tabs.home.route) }
  var addProduct by remember { mutableStateOf(false) }
  var addCustomer by remember { mutableStateOf(false) }
  var morePage by rememberSaveable { mutableStateOf(MorePage.HUB) }
  var showQr by remember { mutableStateOf(false) }

  // The sheet is hoisted here rather than inside the home tab: a shopkeeper reaches for
  // it while a customer is standing there, and losing it because a tab changed
  // underneath would mean starting again with the person still waiting.
  val paymentQr: PaymentQrViewModel = hiltViewModel()
  val paymentQrState by paymentQr.state.collectAsStateWithLifecycle()

  if (showQr) {
    ShowQrSheet(
      saved = paymentQrState.saved,
      amountPaisa = null,
      onDismiss = { showQr = false },
      onManage = {
        showQr = false
        selected = Tabs.more.route
        morePage = MorePage.PAYMENT_QR
      },
    )
  }

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
      MorePage.CREDIT -> stringResource(R.string.credit_title)
      MorePage.REPORTS -> stringResource(R.string.reports_title)
      MorePage.BUSINESS -> stringResource(R.string.business_settings)
      MorePage.PREFERENCES -> stringResource(R.string.settings)
      MorePage.WEB_LOGIN -> stringResource(R.string.web_login)
      MorePage.PAYMENT_QR -> stringResource(R.string.qr_payments)
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
    onShowQr = { showQr = true },
  ) { modifier ->
    // No transition between tabs. A crossfade of two whole screens reads as a flash,
    // because the two rarely have content in the same places, and it puts a frame of
    // half-drawn page under a thumb that is already moving. Tabs are meant to feel like
    // switching a light on.
    val tab = selected
    val page = morePage

    when (tab) {
      Tabs.home.route -> HomeScreen(
        miti = state.miti,
        todayPaisa = state.todayPaisa,
        todayCount = state.todayCount,
        duePaisa = creditOutstanding,
        pendingSync = state.pendingSync,
        recent = state.recent,
        templates = templates,
        onNewBill = { onNewBill(emptyList(), null) },
        onQuickBill = onQuickBill,
        onDeleteTemplate = viewModel::deleteTemplate,
        onOpenBill = onOpenBill,
        // Opened in place. Going to the Products tab to add one left the person on a
        // list they did not ask for once they were done.
        onAddProduct = { addProduct = true },
        onAddCustomer = { addCustomer = true },
        onSettings = {
          selected = Tabs.more.route
          morePage = MorePage.PREFERENCES
        },
        onDues = {
          selected = Tabs.more.route
          morePage = MorePage.CREDIT
        },
        onBills = { selected = Tabs.bills.route },
        modifier = modifier,
      )
      Tabs.products.route -> ItemsScreen(
        addRequested = addProduct && selected == Tabs.products.route,
        onAddHandled = { addProduct = false },
        onBillWithItems = { itemIds -> onNewBill(itemIds, null) },
        modifier = modifier,
      )
      Tabs.customers.route -> CustomersScreen(
        addRequested = addCustomer && selected == Tabs.customers.route,
        onAddHandled = { addCustomer = false },
        onBillFor = { customerId -> onNewBill(emptyList(), customerId) },
        modifier = modifier,
      )
      Tabs.more.route -> when (page) {
        MorePage.CREDIT -> np.bill.ui.credit.CreditBookScreen(modifier = modifier)
        MorePage.REPORTS -> ReportsScreen(modifier = modifier)
        MorePage.BUSINESS -> BusinessSettingsScreen(
          onBack = { morePage = MorePage.HUB },
          modifier = modifier,
        )
        MorePage.WEB_LOGIN -> WebLoginScreen(
          onDone = { morePage = MorePage.HUB },
          modifier = modifier,
        )
        MorePage.PAYMENT_QR -> PaymentQrScreen(modifier = modifier)
        MorePage.PREFERENCES -> SettingsScreen(
          onSwitchMode = onSwitchMode,
          onSignedOut = onSignedOut,
          modifier = modifier,
        )
        MorePage.HUB -> MoreScreen(
          onCreditBook = { morePage = MorePage.CREDIT },
          onReports = { morePage = MorePage.REPORTS },
          onBusiness = { morePage = MorePage.BUSINESS },
          onPreferences = { morePage = MorePage.PREFERENCES },
          onWebLogin = { morePage = MorePage.WEB_LOGIN },
          onPaymentQr = { morePage = MorePage.PAYMENT_QR },
          creditPaisa = creditOutstanding,
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

    // Both forms are hoisted to here so the home screen can open one without changing
    // tab. The catalogue view model is the tab host's, so a product added from home is
    // in the list behind it the moment the sheet closes.
    val catalog: np.bill.ui.catalog.CatalogViewModel = hiltViewModel()

    if (addProduct && selected == Tabs.home.route) {
      androidx.compose.runtime.LaunchedEffect(Unit) { catalog.editItem(null) }
      np.bill.ui.catalog.ItemSheet(
        viewModel = catalog,
        onDismiss = { addProduct = false },
      )
    }

    if (addCustomer && selected == Tabs.home.route) {
      androidx.compose.runtime.LaunchedEffect(Unit) { catalog.editCustomer(null) }
      np.bill.ui.catalog.CustomerSheet(
        viewModel = catalog,
        onDismiss = { addCustomer = false },
      )
    }
  }
}

/** Where the More tab currently is. It is a small stack, not a second navigation graph. */
private enum class MorePage {
  HUB, CREDIT, REPORTS, BUSINESS, PREFERENCES, WEB_LOGIN, PAYMENT_QR
}
