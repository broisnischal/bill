package np.bill.ui.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import np.bill.R
import np.bill.ui.common.pressScale
import np.bill.ui.theme.BillIcons
import np.bill.ui.theme.Gutter
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * The one fork in the app: a shopkeeper making bills, or a shopper keeping them. Asked
 * once, changeable in settings, and never asked again.
 *
 * The two are not equal weight. Almost everyone here runs a shop, so that card is the
 * filled one and it is first — a screen that presents two identical options makes people
 * stop and read both, and one of them is for a different kind of person entirely.
 */
@Composable
fun ModePickerScreen(
  onBusiness: (hasStore: Boolean) -> Unit,
  onCustomer: () -> Unit,
  viewModel: ModeViewModel = hiltViewModel(),
) {
  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Gutter),
  ) {
    // The same 64dp lead-in the sign-in and code screens keep, so the heading does not
    // move once across the whole of first run. Centred, its height depended on how tall
    // the cards under it happened to be, and the two cards floated in the middle of a
    // screen that was mostly empty above and below them.
    Spacer(Modifier.height(64.dp))
    Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(R.string.mode_subtitle),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))

    ModeCard(
      icon = BillIcons.Store,
      title = stringResource(R.string.mode_business),
      detail = stringResource(R.string.mode_business_detail),
      primary = true,
      onClick = { viewModel.chooseBusiness(onBusiness) },
    )
    Spacer(Modifier.height(12.dp))
    ModeCard(
      icon = BillIcons.ScanLine,
      title = stringResource(R.string.mode_customer),
      detail = stringResource(R.string.mode_customer_detail),
      primary = false,
      onClick = { viewModel.chooseCustomer(onCustomer) },
    )

    Spacer(Modifier.height(32.dp))
  }
}

/**
 * One of the two, stacked rather than laid out as a row.
 *
 * The mark, the name and the line explaining it were in a row with a chevron on the end,
 * which left the text about half the card's width: both descriptions broke across two
 * lines in the middle of a phrase and the card read as crowded. Down the card instead,
 * each line gets the full width and neither wraps.
 *
 * No chevron. There are two of these and the card is the choice, so an arrow on it
 * promises a screen behind the card that does not exist.
 */
@Composable
private fun ModeCard(
  icon: ImageVector,
  title: String,
  detail: String,
  primary: Boolean,
  onClick: () -> Unit,
) {
  val tokens = LocalTokens.current
  val shape = RoundedCornerShape(Radius.card)
  val interaction = remember { MutableInteractionSource() }

  Column(
    Modifier
      .fillMaxWidth()
      // The same press the rest of the app answers a tap with.
      .pressScale(interaction)
      .shadow(
        elevation = if (tokens.isDark || primary) 0.dp else 10.dp,
        shape = shape,
        ambientColor = tokens.shadow,
        spotColor = tokens.shadow,
      )
      .clip(shape)
      .background(if (primary) tokens.ink else MaterialTheme.colorScheme.surface)
      .then(if (primary) Modifier else Modifier.border(1.dp, tokens.border, shape))
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 22.dp),
  ) {
    // The tile inside the filled card is the card's own colour lightened rather than
    // mint: one accent per screen, and here the card itself is wearing it.
    Box(
      Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(Radius.medium))
        .background(
          if (primary) {
            tokens.onInk.copy(alpha = 0.14f)
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        tint = if (primary) tokens.onInk else MaterialTheme.colorScheme.onSurface,
      )
    }

    Spacer(Modifier.height(16.dp))
    Text(
      title,
      style = MaterialTheme.typography.headlineSmall,
      color = if (primary) tokens.onInk else MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(3.dp))
    Text(
      detail,
      style = MaterialTheme.typography.bodyMedium,
      color = if (primary) {
        tokens.onInk.copy(alpha = 0.72f)
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
  }
}
