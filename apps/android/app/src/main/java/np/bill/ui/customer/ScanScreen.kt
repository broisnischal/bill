package np.bill.ui.customer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.scan.CodeScanner
import np.bill.scan.ScanTarget
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.PrimaryButton

/**
 * Scanning the QR on a printed bill.
 *
 * Only QR codes are looked for, so the detector has less to do per frame, and analysis
 * keeps only the latest frame: on a cheap phone the camera can outrun the decoder, and
 * queueing frames would just add lag between pointing and recognising.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
  initialToken: String? = null,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: WalletViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val state by viewModel.scan.collectAsStateWithLifecycle()

  var granted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted = it }

  // A link opened from the phone's own camera app already carries the token.
  LaunchedEffect(initialToken) { initialToken?.let(viewModel::onScanned) }

  run {
    Box(modifier.fillMaxSize()) {
      when {
        state.savedNumber != null -> Saved(
          number = state.savedNumber!!,
          onDone = {
            viewModel.clearScan()
            onDone()
          },
        )

        !granted -> Column(
          Modifier.fillMaxSize().padding(24.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            stringResource(R.string.camera_permission),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(20.dp))
          PrimaryButton(
            text = stringResource(R.string.grant_permission),
            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
          )
        }

        else -> Box(Modifier.fillMaxSize()) {
          CodeScanner(target = ScanTarget.QR, onScanned = viewModel::onScanned)
          Notice(
            text = stringResource(R.string.scan_hint),
            modifier = Modifier.align(Alignment.TopCenter),
          )
        }
      }

      state.error?.let { error ->
        Notice(
          text = if (error == "offline") {
            stringResource(R.string.offline_banner)
          } else {
            stringResource(R.string.bill_not_found)
          },
          tone = NoticeTone.WARN,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }
  }
}

@Composable
private fun Saved(number: String, onDone: () -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.bill_saved), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(number, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(28.dp))
    PrimaryButton(text = stringResource(R.string.done), onClick = onDone)
  }
}
