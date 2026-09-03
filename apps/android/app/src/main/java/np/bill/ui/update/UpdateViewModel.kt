package np.bill.ui.update

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import np.bill.BuildConfig
import np.bill.R
import np.bill.data.net.AppReleaseResponse
import np.bill.data.repo.UpdateRepository
import np.bill.data.repo.UpdateStatus
import np.bill.update.ApkInstaller

data class UpdateUiState(
  val status: UpdateStatus = UpdateStatus.UpToDate,
  val downloading: Boolean = false,
  val progress: Float = 0f,
  val message: String? = null,
) {
  val installedVersion: String get() = BuildConfig.VERSION_NAME
}

/**
 * The update the shopkeeper is being shown, and how far its download has got.
 *
 * The status itself belongs to the repository, not here: settings can ask for a check
 * and this is what puts the answer on screen, wherever in the app they happen to be.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
  private val updates: UpdateRepository,
  private val installer: ApkInstaller,
  private val application: Application,
) : ViewModel() {

  private val _state = MutableStateFlow(UpdateUiState())
  val state = _state.asStateFlow()

  init {
    viewModelScope.launch {
      updates.status.collect { status -> _state.update { it.copy(status = status) } }
    }
    viewModelScope.launch { updates.refresh() }
  }

  fun download(release: AppReleaseResponse) {
    if (_state.value.downloading) return

    viewModelScope.launch {
      if (!installer.canInstall()) {
        // Nothing can be installed until this is granted, and the switch is two screens
        // deep in system settings, so the app opens it rather than describing where it is.
        installer.requestInstallPermission()
        _state.update { it.copy(message = application.getString(R.string.update_permission)) }
        return@launch
      }

      _state.update { it.copy(downloading = true, progress = 0f, message = null) }

      // Reported per whole percent. A frame per 8KB block would repaint the bar a few
      // thousand times for a file this size and show exactly the same thing.
      var shown = -1
      val downloaded = installer.download(release.apkUrl, release.versionName) { progress ->
        val percent = (progress * 100).toInt()
        if (percent != shown) {
          shown = percent
          _state.update { it.copy(progress = progress) }
        }
      }

      _state.update { it.copy(downloading = false) }
      downloaded
        .onSuccess(installer::install)
        .onFailure {
          // Handing it to the browser rather than dead-ending. A shop stuck on a version
          // it cannot leave is worse than one that has to finish the job in Downloads.
          installer.openInBrowser(release.apkUrl)
          _state.update { it.copy(message = application.getString(R.string.update_failed)) }
        }
    }
  }

  fun later(release: AppReleaseResponse) {
    viewModelScope.launch { updates.skip(release) }
  }
}
