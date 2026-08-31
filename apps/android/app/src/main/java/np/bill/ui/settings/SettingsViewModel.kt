package np.bill.ui.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.data.prefs.SessionStore
import np.bill.data.repo.AuthRepository
import np.bill.data.repo.SyncRepository
import np.bill.print.ThermalPrinter

data class SettingsState(
  val language: String = "en",
  val themeMode: np.bill.ui.theme.ThemeMode = np.bill.ui.theme.ThemeMode.SYSTEM,
  val printers: List<ThermalPrinter.Printer> = emptyList(),
  val selectedPrinter: String? = null,
  val bluetoothAllowed: Boolean = false,
  val syncMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val session: SessionStore,
  private val auth: AuthRepository,
  private val sync: SyncRepository,
  private val printer: ThermalPrinter,
  private val application: Application,
) : ViewModel() {

  private val _state = MutableStateFlow(SettingsState())
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      val current = session.current()
      _state.update {
        it.copy(
          themeMode = current.themeMode,
          selectedPrinter = current.printerAddress,
          language = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2).ifEmpty { "en" },
        )
      }
    }
  }

  /**
   * Per-app language, so a shopkeeper can run the app in Nepali on a phone that is set to
   * English. On Android 13 and up the system remembers the choice itself.
   */
  fun setLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    _state.update { it.copy(language = tag) }
  }

  fun setThemeMode(mode: np.bill.ui.theme.ThemeMode) {
    viewModelScope.launch {
      session.setThemeMode(mode)
      _state.update { it.copy(themeMode = mode) }
    }
  }

  fun refreshPrinters() {
    _state.update {
      it.copy(bluetoothAllowed = printer.hasPermission(), printers = printer.paired())
    }
  }

  fun choosePrinter(printer: ThermalPrinter.Printer) {
    viewModelScope.launch {
      session.setPrinter(printer.address, printer.name)
      _state.update { it.copy(selectedPrinter = printer.address) }
    }
  }

  fun syncNow() {
    viewModelScope.launch {
      _state.update { it.copy(syncMessage = null) }
      val outcome = sync.sync()
      _state.update {
        it.copy(
          syncMessage = when {
            outcome.offline -> application.getString(np.bill.R.string.offline_banner)
            outcome.error != null -> outcome.error
            else -> "${outcome.filed} synced · ${outcome.numbersLeft} numbers ready"
          },
        )
      }
    }
  }

  fun signOut(onSignedOut: () -> Unit) {
    viewModelScope.launch {
      auth.signOut()
      onSignedOut()
    }
  }
}
