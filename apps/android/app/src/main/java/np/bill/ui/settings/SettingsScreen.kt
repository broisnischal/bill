package np.bill.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.ui.common.Panel
import np.bill.ui.common.SecondaryButton
import np.bill.ui.common.SegmentedChoice
import np.bill.ui.theme.BillIcons
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.ThemeMode

/**
 * Preferences.
 *
 * Grouped into panels rather than run down the page as loose controls, because the things
 * here — how it looks, what language it speaks, what it prints on — have nothing to do
 * with each other and reading them as one list makes all of them harder to find.
 *
 * Everything that does something is a row, the same row the More hub uses. It used to be
 * a column of identical full-width pills: sync, switch mode, check for updates and sign
 * out all drawn at the weight of a primary action, which made a page of settings look
 * like four things the shop was being asked to do.
 */
@Composable
fun SettingsScreen(
  onSwitchMode: () -> Unit,
  onSignedOut: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  val bluetoothLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { viewModel.refreshPrinters() }

  LaunchedEffect(Unit) { viewModel.refreshPrinters() }

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      // 16, matching the More hub this page is opened from. At 12 the cards here grew
      // wider than the ones on the screen before, which read as a layout shift.
      .padding(horizontal = 16.dp),
  ) {
    Spacer(Modifier.height(8.dp))

    Panel {
      Section(stringResource(R.string.appearance))
      SegmentedChoice(
        options = ThemeMode.entries.map {
          it to stringResource(
            when (it) {
              ThemeMode.SYSTEM -> R.string.theme_system
              ThemeMode.LIGHT -> R.string.theme_light
              ThemeMode.DARK -> R.string.theme_dark
            },
          )
        },
        selected = state.themeMode,
        onSelect = viewModel::setThemeMode,
        modifier = Modifier.padding(horizontal = 16.dp),
      )

      Spacer(Modifier.height(18.dp))
      Hairline()

      Section(stringResource(R.string.language))
      SegmentedChoice(
        options = listOf(
          "en" to stringResource(R.string.language_english),
          "ne" to stringResource(R.string.language_nepali),
        ),
        selected = state.language,
        onSelect = viewModel::setLanguage,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      Spacer(Modifier.height(18.dp))
    }

    Spacer(Modifier.height(16.dp))

    Panel {
      Section(stringResource(R.string.printer))

      when {
        // The permission is Bluetooth, and the button used to say "Allow camera": it was
        // borrowed from the QR scanner, so the one screen that talks about a printer asked
        // for the wrong thing by name.
        !state.bluetoothAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          Hint(stringResource(R.string.printer_permission_hint))
          Spacer(Modifier.height(14.dp))
          SecondaryButton(
            text = stringResource(R.string.printer_permission),
            onClick = { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            compact = true,
            modifier = Modifier.padding(horizontal = 16.dp),
          )
          Spacer(Modifier.height(18.dp))
        }

        // Pairing advice only when there is nothing to pick. Once a printer is in the
        // list it is a sentence about a thing that has already happened.
        state.printers.isEmpty() -> {
          Hint(stringResource(R.string.printer_none))
          Spacer(Modifier.height(6.dp))
          Hint(stringResource(R.string.printer_pair_hint))
          Spacer(Modifier.height(18.dp))
        }

        else -> state.printers.forEachIndexed { index, printer ->
          // Between rows, not above the first: a line directly under the section label
          // reads as an underline on the heading.
          if (index > 0) Hairline()
          PrinterRow(
            name = printer.name,
            address = printer.address,
            chosen = printer.address == state.selectedPrinter,
            onClick = { viewModel.choosePrinter(printer) },
          )
        }
      }
    }

    Spacer(Modifier.height(16.dp))

    Panel {
      Section(stringResource(R.string.about))
      Hint(stringResource(R.string.app_version, state.versionName, state.versionCode))
      Spacer(Modifier.height(14.dp))
      Hairline()
      SettingRow(
        icon = BillIcons.Download,
        label = stringResource(R.string.update_check),
        subtitle = state.updateMessage,
        onClick = viewModel::checkForUpdates,
        loading = state.checkingUpdate,
      )
    }

    Spacer(Modifier.height(16.dp))

    Panel {
      SettingRow(
        icon = BillIcons.Cloud,
        label = stringResource(R.string.sync_now),
        // What the sync said, under the thing that said it. It used to be a banner above
        // the button, far enough from the tap to look like a notice about something else.
        subtitle = state.syncMessage,
        onClick = viewModel::syncNow,
      )
      Hairline()
      SettingRow(
        icon = BillIcons.Users,
        label = stringResource(R.string.switch_mode),
        onClick = onSwitchMode,
        chevron = true,
      )
    }

    Spacer(Modifier.height(16.dp))

    // On its own, because it is the one thing on this page that ends the session.
    Panel {
      SignOutRow(
        label = stringResource(R.string.sign_out),
        onClick = { viewModel.signOut(onSignedOut) },
      )
    }

    Spacer(Modifier.height(40.dp))
  }
}

@Composable
private fun Section(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp),
  )
}

/** A sentence inside a panel: an explanation, never something to tap. */
@Composable
private fun Hint(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp),
  )
}

/**
 * A row that does something, at the metrics the More hub uses so the two screens agree on
 * what a row is.
 *
 * The chevron is only for a row that opens something else. Sync and update check happen
 * here, and an arrow on them would promise a screen that never arrives.
 */
@Composable
private fun SettingRow(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  subtitle: String? = null,
  chevron: Boolean = false,
  loading: Boolean = false,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(enabled = !loading, onClick = onClick)
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
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      subtitle?.let {
        Text(
          it,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    when {
      loading -> CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      chevron -> Icon(
        BillIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun PrinterRow(
  name: String,
  address: String,
  chosen: Boolean,
  onClick: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(name, style = MaterialTheme.typography.bodyLarge)
      Text(
        address,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    // A tick on the chosen one rather than a radio on every row: the list is short and
    // the question is "which one", not "pick one of these".
    if (chosen) {
      Icon(
        BillIcons.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

/** Centred and in the colour of a thing you cannot take back, with no icon to soften it. */
@Composable
private fun SignOutRow(label: String, onClick: () -> Unit) {
  Text(
    label,
    style = MaterialTheme.typography.bodyLarge,
    color = LocalTokens.current.negative,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 16.dp),
  )
}
