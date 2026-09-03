package np.bill.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.ui.common.StatusIcons
import np.bill.ui.theme.Gutter
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * The frame every day-to-day screen sits in.
 *
 * A shopkeeper's four jobs — write a bill, manage what they sell, manage who they sell
 * to, and change a setting — are one tap apart rather than buried in a drawer. The top
 * bar carries only what they glance at mid-sale: whether the printer is there and whether
 * the office has today's bills.
 *
 * Two things make it look like a phone app rather than a desktop tool ported onto one.
 * The head of the screen fades out of sage, so the first thing under the thumb has colour
 * behind it. And the tabs float as pills over the page instead of sitting in a bar bolted
 * to the bottom edge, which is what stops five equal grey icons reading as a toolbar.
 */
object Tabs {
  val home = ShellTab("tab/home", R.string.nav_home, BillIcons.House)
  val bills = ShellTab("tab/bills", R.string.nav_bills, BillIcons.ReceiptText)
  val products = ShellTab("tab/products", R.string.nav_products, BillIcons.Package)
  val customers = ShellTab("tab/customers", R.string.nav_customers_tab, BillIcons.Users)
  val more = ShellTab("tab/more", R.string.nav_more, BillIcons.Settings)

  val wallet = ShellTab("tab/wallet", R.string.nav_wallet, BillIcons.ReceiptText)
  val scan = ShellTab("tab/scan", R.string.scan_bill, BillIcons.ScanLine)
  val card = ShellTab("tab/card", R.string.nav_card, BillIcons.QrCode)
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
  /** The payment code, as the one action in the middle of the tabs. Null hides it. */
  onShowQr: (() -> Unit)? = null,
  floatingActionButton: @Composable () -> Unit = {},
  content: @Composable (Modifier) -> Unit,
) {
  val tokens = LocalTokens.current

  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Behind everything, and only at the top. Colour covering area is what a screen of
    // white cards needs to stop reading as a form.
    Box(
      Modifier
        .fillMaxWidth()
        .height(GradientHeight)
        .background(
          Brush.verticalGradient(
            listOf(tokens.sage, MaterialTheme.colorScheme.background),
          ),
        ),
    )

    Scaffold(
      containerColor = Color.Transparent,
      // A transparent container resolves to no content colour at all, which leaves every
      // unstyled Text falling back to black. Said explicitly instead.
      contentColor = MaterialTheme.colorScheme.onBackground,
      topBar = {
        TopAppBar(
          title = {
            Text(title, maxLines = 1, style = MaterialTheme.typography.headlineMedium)
          },
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
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
          ),
        )
      },
      bottomBar = {
        FloatingNav(
          tabs = tabs,
          selectedRoute = selectedRoute,
          onSelect = onSelect,
          onShowQr = onShowQr,
        )
      },
      floatingActionButton = floatingActionButton,
    ) { padding ->
      Box(Modifier.fillMaxSize()) {
        content(Modifier.padding(padding))
      }
    }
  }
}

/**
 * The tabs, as one bar floating over the page.
 *
 * Five separate floating circles read as five loose buttons somebody scattered along the
 * bottom edge; one container reads as a control. No labels: five words under five icons
 * is a second row of type competing with the screen above it, and the icons are the same
 * five in the same order every time, which is how people navigate this. Every slot is the
 * same width, so choosing one moves nothing.
 */
@Composable
private fun FloatingNav(
  tabs: List<ShellTab>,
  selectedRoute: String,
  onSelect: (String) -> Unit,
  onShowQr: (() -> Unit)?,
) {
  val tokens = LocalTokens.current

  // Concentric: the circle behind the chosen icon is 44dp across, so its radius is 22,
  // and it sits 6dp in from the edge of the bar. 22 + 6 is Radius.bar.
  val shape = RoundedCornerShape(Radius.bar)

  Row(
    Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = Gutter, vertical = 8.dp)
      .shadow(
        elevation = if (tokens.isDark) 0.dp else 14.dp,
        shape = shape,
        ambientColor = tokens.shadow,
        spotColor = tokens.shadow,
      )
      .clip(shape)
      // No outline. The bar sat on a near-black page with a hairline round it, and a ring
      // that thin at that radius reads as a seam rather than an edge. One shade up from
      // the page separates it, which is how every other surface here does it.
      .background(
        if (tokens.isDark) {
          MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
          MaterialTheme.colorScheme.surface
        },
      )
      .padding(6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    /**
     * The tabs in two groups with the payment code between them.
     *
     * Taking money is not a place in the app, it is the thing a shopkeeper does with a
     * customer standing there, so it sits where a thumb already rests rather than on a
     * screen they have to be on first. Two groups each taking half the width put it dead
     * centre whichever way the tabs divide.
     */
    val half = tabs.size / 2

    Row(
      Modifier.weight(1f),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      for (tab in tabs.take(half)) {
        NavCircle(tab = tab, active = tab.route == selectedRoute, onSelect = onSelect)
      }
    }

    onShowQr?.let { show ->
      Box(
        Modifier
          .size(NavItem)
          .clip(CircleShape)
          .background(tokens.accent)
          .clickable(onClick = show),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          BillIcons.QrCode,
          contentDescription = stringResource(R.string.qr_show),
          tint = tokens.onInk,
          modifier = Modifier.size(NavIcon),
        )
      }
    }

    Row(
      Modifier.weight(1f),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      for (tab in tabs.drop(half)) {
        NavCircle(tab = tab, active = tab.route == selectedRoute, onSelect = onSelect)
      }
    }
  }
}

/**
 * One tab: a circle that fills its slot, so the ring around it is even.
 *
 * It used to be centred inside a weight(1f) slot, which left it about 16dp from the
 * bar's end while being 6dp from its top and bottom. The radius arithmetic was right and
 * it still looked wrong, because concentricity is the gap being equal all the way round.
 */
@Composable
private fun NavCircle(tab: ShellTab, active: Boolean, onSelect: (String) -> Unit) {
  val tokens = LocalTokens.current

  Box(
    Modifier
      .size(NavItem)
      .clip(CircleShape)
      .background(if (active) tokens.ink else Color.Transparent)
      .clickable { onSelect(tab.route) },
    contentAlignment = Alignment.Center,
  ) {
    BadgedBox(
      badge = {
        if (tab.badge > 0) Badge { Text(if (tab.badge > 99) "99+" else "${tab.badge}") }
      },
    ) {
      Icon(
        tab.icon,
        // The label is gone, so the icon carries the name for a screen reader.
        contentDescription = stringResource(tab.labelRes),
        tint = if (active) tokens.onInk else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(NavIcon),
      )
    }
  }
}

data class ShellTab(
  val route: String,
  val labelRes: Int,
  val icon: ImageVector,
  val badge: Int = 0,
)

/** Tall enough that the card under it has something behind it, short enough to be a hint. */
private val GradientHeight = 210.dp
/**
 * The circle behind the chosen icon, and the height of every slot.
 *
 * Half of it (22) plus the bar's 6dp padding is Radius.bar (28), which is what keeps the
 * gap between the two curves even all the way round the corner.
 */
private val NavItem = 44.dp
private val NavIcon = 24.dp
