package np.bill.ui.payments

import androidx.activity.compose.rememberLauncherForActivityResult
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.data.repo.PaymentQrMethod
import np.bill.ui.common.Notice
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius

/**
 * Where the shop keeps its payment QRs.
 *
 * One row per wallet, each either holding the shop's code or offering to take it. The
 * picture comes from the phone's own gallery, because the code the shop hands people is
 * already a photo on that phone or a card on the counter someone snaps once.
 */
@Composable
fun PaymentQrScreen(
  modifier: Modifier = Modifier,
  viewModel: PaymentQrViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Which method the shop is adding a code for, and how far into adding it they are.
  var target by remember { mutableStateOf<PaymentQrMethod?>(null) }
  var route by remember { mutableStateOf(AddQrRoute.CHOOSE) }

  // The gallery hands back a photograph of a card, which is never square and usually has
  // the counter in it. Cropping is part of adding, not a tidy-up afterwards: an image
  // with half a till in it scans slowly if it scans at all.
  val cropper = rememberLauncherForActivityResult(CropImageContract()) { result ->
    val method = target
    target = null
    route = AddQrRoute.CHOOSE
    val uri = result.uriContent
    if (result.isSuccessful && uri != null && method != null) viewModel.save(method, uri, null)
  }

  fun startCrop() {
    cropper.launch(
      CropImageContractOptions(
        uri = null,
        cropImageOptions = CropImageOptions(
          imageSourceIncludeCamera = true,
          imageSourceIncludeGallery = true,
          // A payment code is square, and locking the ratio stops someone cropping the
          // quiet zone off one side, which is the usual reason a saved code stops reading.
          fixAspectRatio = true,
          aspectRatioX = 1,
          aspectRatioY = 1,
          outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG,
        ),
      ),
    )
  }

  target?.let { method ->
    when (route) {
      AddQrRoute.CHOOSE -> AddQrSheet(
        method = method,
        onScan = { route = AddQrRoute.SCAN },
        onNumber = { route = AddQrRoute.NUMBER },
        onPhoto = { startCrop() },
        onDismiss = { target = null },
      )
      AddQrRoute.SCAN -> ScanQrScreen(
        onScanned = { payload ->
          viewModel.savePayload(method, payload, null)
          target = null
          route = AddQrRoute.CHOOSE
        },
        onCancel = {
          target = null
          route = AddQrRoute.CHOOSE
        },
      )
      AddQrRoute.NUMBER -> NumberQrDialog(
        method = method,
        onConfirm = { number ->
          viewModel.savePayload(method, number, null)
          target = null
          route = AddQrRoute.CHOOSE
        },
        onDismiss = {
          target = null
          route = AddQrRoute.CHOOSE
        },
      )
    }
  }

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 12.dp),
  ) {
    Spacer(Modifier.height(8.dp))

    Text(
      stringResource(R.string.qr_settings_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp),
    )

    Spacer(Modifier.height(16.dp))

    state.error?.let {
      Notice(text = it)
      Spacer(Modifier.height(12.dp))
    }

    PaymentQrMethod.entries.forEach { method ->
      val saved = state.saved.firstOrNull { it.method == method }

      MethodCard(
        title = stringResource(method.labelRes()),
        hasQr = saved != null,
        thumbnail = {
          saved?.let { QrThumbnail(it, modifier = Modifier.fillMaxSize()) }
        },
        onPick = {
          target = method
          route = AddQrRoute.CHOOSE
        },
        onRemove = { viewModel.remove(method) },
      )
      Spacer(Modifier.height(10.dp))
    }

    Spacer(Modifier.height(32.dp))
  }
}

@Composable
private fun MethodCard(
  title: String,
  hasQr: Boolean,
  thumbnail: @Composable () -> Unit,
  onPick: () -> Unit,
  onRemove: () -> Unit,
) {
  val tokens = LocalTokens.current

  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.large))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(1.dp, tokens.border, RoundedCornerShape(Radius.large))
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      Modifier
        .size(52.dp)
        .clip(RoundedCornerShape(Radius.medium))
        .background(if (hasQr) Color.White else MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      thumbnail()
    }

    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(
        stringResource(if (hasQr) R.string.qr_saved else R.string.qr_not_set),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Text(
      stringResource(if (hasQr) R.string.qr_replace else R.string.qr_add_short),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .clip(RoundedCornerShape(Radius.medium))
        .clickable(onClick = onPick)
        .padding(horizontal = 8.dp, vertical = 6.dp),
    )

    if (hasQr) {
      Text(
        stringResource(R.string.qr_remove),
        style = MaterialTheme.typography.labelLarge,
        color = tokens.due,
        modifier = Modifier
          .clip(RoundedCornerShape(Radius.medium))
          .clickable(onClick = onRemove)
          .padding(horizontal = 8.dp, vertical = 6.dp),
      )
    }
  }
}
