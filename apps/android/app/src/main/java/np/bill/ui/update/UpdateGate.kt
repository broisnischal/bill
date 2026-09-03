package np.bill.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.ui.theme.BillIcons
import np.bill.R
import np.bill.data.net.AppReleaseResponse
import np.bill.data.repo.UpdateStatus
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.SecondaryButton
import np.bill.ui.theme.Radius

/**
 * Sits over the whole app and says when the build on the phone is behind.
 *
 * Two shapes for two different things. An optional update is a sheet the shopkeeper can
 * push away mid-morning and be shown again tomorrow. A required one is a screen with no
 * way past it, because the server has stopped accepting what this build sends and every
 * bill written on it would be refused on sync.
 */
@Composable
fun UpdateGate(content: @Composable () -> Unit) {
  val viewModel: UpdateViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  Box(Modifier.fillMaxSize()) {
    content()

    when (val status = state.status) {
      UpdateStatus.UpToDate -> Unit

      is UpdateStatus.Optional -> OptionalUpdateSheet(
        release = status.release,
        state = state,
        onUpdate = { viewModel.download(status.release) },
        onLater = { viewModel.later(status.release) },
      )

      is UpdateStatus.Required -> RequiredUpdateScreen(
        release = status.release,
        state = state,
        onUpdate = { viewModel.download(status.release) },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionalUpdateSheet(
  release: AppReleaseResponse,
  state: UpdateUiState,
  onUpdate: () -> Unit,
  onLater: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onLater,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(
      topStart = Radius.sheet,
      topEnd = Radius.sheet,
    ),
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
      Text(stringResource(R.string.update_available), style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(2.dp))
      VersionLine(release, state)

      Spacer(Modifier.height(12.dp))
      Text(
        release.notes.ifBlank { stringResource(R.string.update_optional_body) },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Progress(state)
      state.message?.let {
        Spacer(Modifier.height(12.dp))
        Notice(it, tone = NoticeTone.WARN)
      }

      Spacer(Modifier.height(20.dp))
      PrimaryButton(
        text = updateLabel(state),
        onClick = onUpdate,
        enabled = !state.downloading,
        loading = state.downloading && state.progress <= 0f,
      )
      Spacer(Modifier.height(10.dp))
      SecondaryButton(
        text = stringResource(R.string.update_later),
        onClick = onLater,
        enabled = !state.downloading,
      )
    }
  }
}

@Composable
private fun RequiredUpdateScreen(
  release: AppReleaseResponse,
  state: UpdateUiState,
  onUpdate: () -> Unit,
) {
  // There is nothing behind this to go back to. Swallowing the gesture rather than
  // letting it close the app, which is what back would otherwise do from the root.
  BackHandler(enabled = true) {}

  Surface(Modifier.fillMaxSize()) {
    Column(
      Modifier.fillMaxSize().padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.Start,
    ) {
      Icon(
        BillIcons.Download,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
      Spacer(Modifier.height(20.dp))

      Text(stringResource(R.string.update_required), style = MaterialTheme.typography.headlineMedium)
      Spacer(Modifier.height(6.dp))
      VersionLine(release, state)

      Spacer(Modifier.height(14.dp))
      Text(
        stringResource(R.string.update_required_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (release.notes.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(
          release.notes,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Progress(state)
      state.message?.let {
        Spacer(Modifier.height(16.dp))
        Notice(it, tone = NoticeTone.WARN)
      }

      Spacer(Modifier.height(28.dp))
      PrimaryButton(
        text = updateLabel(state),
        onClick = onUpdate,
        enabled = !state.downloading,
        loading = state.downloading && state.progress <= 0f,
      )
    }
  }
}

@Composable
private fun VersionLine(release: AppReleaseResponse, state: UpdateUiState) {
  Text(
    stringResource(R.string.update_version_line, release.versionName, state.installedVersion),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Only drawn once bytes are arriving; a bar at zero says less than no bar at all. */
@Composable
private fun Progress(state: UpdateUiState) {
  if (!state.downloading || state.progress <= 0f) return
  Spacer(Modifier.height(20.dp))
  LinearProgressIndicator(
    progress = { state.progress },
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun updateLabel(state: UpdateUiState): String = when {
  state.downloading && state.progress > 0f ->
    stringResource(R.string.update_downloading, (state.progress * 100).toInt())
  else -> stringResource(R.string.update_now)
}
