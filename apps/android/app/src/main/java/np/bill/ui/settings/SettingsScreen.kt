package np.bill.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.ui.common.Hairline
import np.bill.ui.common.Notice
import np.bill.ui.common.Panel
import np.bill.ui.common.SecondaryButton
import np.bill.ui.common.SegmentedChoice
import np.bill.ui.theme.ThemeMode

/**
 * Preferences.
 *
 * Grouped into panels rather than run down the page as loose controls, because the three
 * things here — how it looks, what language it speaks, what it prints on — have nothing
 * to do with each other and reading them as one list makes all three harder to find.
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
      .padding(horizontal = 12.dp),
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

      Spacer(Modifier.height(16.dp))
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
      Spacer(Modifier.height(16.dp))
    }

    Spacer(Modifier.height(12.dp))

    Panel {
      Section(stringResource(R.string.printer))
      Text(
        stringResource(R.string.printer_pair_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      Spacer(Modifier.height(12.dp))

      when {
        !state.bluetoothAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
          SecondaryButton(
            text = stringResource(R.string.grant_permission),
            onClick = { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            modifier = Modifier.padding(horizontal = 16.dp),
          )

        state.printers.isEmpty() ->
          Text(
            stringResource(R.string.printer_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
          )

        else -> for (printer in state.printers) {
          Hairline()
          val chosen = printer.address == state.selectedPrinter
          Row(
            Modifier
              .fillMaxWidth()
              .clickable { viewModel.choosePrinter(printer) }
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(printer.name, style = MaterialTheme.typography.bodyLarge)
              Text(
                printer.address,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            // A tick on the chosen one rather than a radio on every row: the list is
            // short and the question is "which one", not "pick one of these".
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
      }
      Spacer(Modifier.height(12.dp))
    }

    Spacer(Modifier.height(12.dp))

    Panel {
      Section(stringResource(R.string.about))
      Text(
        stringResource(R.string.app_version, state.versionName, state.versionCode),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      state.updateMessage?.let {
        Spacer(Modifier.height(4.dp))
        Text(
          it,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp),
        )
      }
      Spacer(Modifier.height(12.dp))
      SecondaryButton(
        text = stringResource(R.string.update_check),
        onClick = viewModel::checkForUpdates,
        enabled = !state.checkingUpdate,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      Spacer(Modifier.height(16.dp))
    }

    Spacer(Modifier.height(12.dp))

    state.syncMessage?.let {
      Notice(it)
      Spacer(Modifier.height(12.dp))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      SecondaryButton(text = stringResource(R.string.sync_now), onClick = viewModel::syncNow)
      SecondaryButton(text = stringResource(R.string.switch_mode), onClick = onSwitchMode)
      SecondaryButton(
        text = stringResource(R.string.sign_out),
        onClick = { viewModel.signOut(onSignedOut) },
        destructive = true,
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
