package np.bill.ui.review

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import np.bill.R
import np.bill.ui.common.ActionSheet
import np.bill.ui.common.IconTile
import np.bill.ui.common.Notice
import np.bill.ui.common.NoticeTone
import np.bill.ui.common.Panel
import np.bill.ui.common.PrimaryButton
import np.bill.ui.common.SecondaryButton
import np.bill.ui.common.TileTone
import np.bill.ui.theme.BillIcons
import np.bill.ui.theme.LocalTokens
import np.bill.ui.theme.Radius
import np.bill.ui.theme.Gutter

/**
 * The wait between registering and billing.
 *
 * A person reads the PAN certificate before the shop can print that number on a document
 * a tax office will hold them to. The screen exists to make the wait honest: what is
 * still missing, what was asked for if it was refused, and nothing that pretends billing
 * is one tap away when it is not.
 *
 * The PAN certificate is the only compulsory paper. The other two are worth sending
 * because a reviewer usually wants them, and are never a reason to hold anybody up.
 */
@Composable
fun ReviewScreen(
  onApproved: () -> Unit,
  onSignedOut: () -> Unit,
  viewModel: ReviewViewModel = hiltViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  // Which paper is being sent. Set when a row is tapped, cleared once a source has been
  // chosen, because the launcher that comes back has no idea which slot it was for.
  var picking by remember { mutableStateOf<String?>(null) }
  var pendingKind by remember { mutableStateOf<String?>(null) }

  /**
   * The camera, the gallery, and a crop step, in one screen the library already provides.
   *
   * A certificate is photographed on a counter at arm's length, so what comes back is the
   * page plus a till, a hand and some glare, at whatever angle the phone was held. That is
   * what a reviewer had to read before: cropping is part of sending the paper, not a
   * nicety. It also brings the file down to something that fits under the 8 MB cap without
   * a shopkeeper being told their photo is too big.
   */
  val cropper = rememberLauncherForActivityResult(CropImageContract()) { result ->
    val kind = pendingKind
    pendingKind = null
    val uri = result.uriContent
    if (result.isSuccessful && uri != null && kind != null) viewModel.upload(kind, uri)
  }

  // A certificate that arrives as a PDF is already a scan, so it goes up untouched.
  // Narrowed from */*: the picker used to offer every file on the phone and the server
  // was left to refuse the ones it could not read.
  val pdfPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent(),
  ) { uri ->
    val kind = pendingKind
    pendingKind = null
    if (uri != null && kind != null) viewModel.upload(kind, uri)
  }

  // Read here rather than inside startPhoto: that is a plain function, and a plain
  // function cannot ask for a string resource.
  val cropTitle = stringResource(R.string.crop_title)
  val cropDone = stringResource(R.string.crop_done)

  fun startPhoto(kind: String) {
    pendingKind = kind
    picking = null
    cropper.launch(
      CropImageContractOptions(
        uri = null,
        cropImageOptions = CropImageOptions(
          imageSourceIncludeCamera = true,
          imageSourceIncludeGallery = true,
          // A page is whatever shape the page is, so nothing is locked here.
          outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG,
          outputCompressQuality = 85,
          // Enough to read a PAN number off, small enough to send on a shop's connection.
          outputRequestWidth = 2200,
          outputRequestHeight = 2200,
          outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
          // The library's own label is CROP, which names the tool rather than what the
          // tap does. Dark chrome to match Theme.Bill.Crop, so the bar is not a pale
          // strip above a photograph.
          activityTitle = cropTitle,
          cropMenuCropButtonTitle = cropDone,
          activityBackgroundColor = CROP_CHROME,
        ),
      ),
    )
  }

  fun startPdf(kind: String) {
    pendingKind = kind
    picking = null
    pdfPicker.launch("application/pdf")
  }

  picking?.let { kind ->
    ActionSheet(
      title = stringResource(documentLabel(kind)),
      subtitle = stringResource(R.string.doc_source_hint),
      primary = stringResource(R.string.doc_source_photo) to { startPhoto(kind) },
      secondary = stringResource(R.string.doc_source_pdf) to { startPdf(kind) },
      onDismiss = { picking = null },
    )
  }

  // Approved while the screen was open: get out of the way rather than making them find
  // the button that lets them start.
  if (state.status == "approved") {
    androidx.compose.runtime.LaunchedEffect(Unit) { onApproved() }
  }

  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Gutter),
  ) {
    Spacer(Modifier.height(28.dp))

    IconTile(
      when (state.stage) {
        ReviewStage.RETURNED -> BillIcons.CircleAlert
        ReviewStage.INCOMPLETE -> BillIcons.ReceiptText
        else -> BillIcons.Clock
      },
      tone = if (state.stage == ReviewStage.RETURNED) TileTone.NEGATIVE else TileTone.MINT,
      size = 56.dp,
    )
    Spacer(Modifier.height(18.dp))

    Text(
      stringResource(
        when (state.stage) {
          ReviewStage.RETURNED -> R.string.review_rejected_title
          ReviewStage.INCOMPLETE -> R.string.review_incomplete_title
          else -> R.string.review_title
        },
      ),
      style = MaterialTheme.typography.displaySmall,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      state.note ?: stringResource(
        when (state.stage) {
          ReviewStage.INCOMPLETE -> R.string.review_needs_pan
          else -> R.string.review_waiting
        },
      ),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Three stages, so the wait has a shape. A person is reading it today; when that
    // becomes automatic the middle stage keeps its name and only gets shorter.
    if (state.stage != ReviewStage.RETURNED) {
      Spacer(Modifier.height(22.dp))
      Stages(stage = state.stage)
    }

    Spacer(Modifier.height(20.dp))
    Panel {
      Column(Modifier.padding(16.dp)) {
        Text(state.businessName, style = MaterialTheme.typography.titleLarge)
        Text(
          stringResource(R.string.review_pan, state.pan),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Spacer(Modifier.height(24.dp))
    Text(
      stringResource(R.string.review_papers),
      style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(10.dp))

    for (kind in listOf("pan", "registration", "tax_clearance")) {
      DocumentRow(
        kind = kind,
        uploaded = state.documentFor(kind) != null,
        fileName = state.documentFor(kind)?.fileName,
        uploading = state.uploading == kind,
        onPick = { picking = kind },
      )
      Spacer(Modifier.height(10.dp))
    }

    state.error?.let {
      Spacer(Modifier.height(4.dp))
      Notice(it, tone = NoticeTone.ERROR)
    }

    Spacer(Modifier.height(24.dp))
    if (state.stage == ReviewStage.INCOMPLETE || state.stage == ReviewStage.RETURNED) {
      // The only thing worth a filled button is the paper that is missing.
      PrimaryButton(
        text = stringResource(R.string.review_send_pan),
        onClick = { picking = "pan" },
        loading = state.uploading == "pan",
      )
      Spacer(Modifier.height(10.dp))
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      Text(
        stringResource(if (state.loading) R.string.review_checking else R.string.review_refresh),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .clip(RoundedCornerShape(Radius.pill))
          .clickable(enabled = !state.loading, onClick = viewModel::refresh)
          .padding(horizontal = 14.dp, vertical = 10.dp),
      )
    }

    Spacer(Modifier.height(6.dp))
    SecondaryButton(
      text = stringResource(R.string.sign_out),
      onClick = { viewModel.signOut(onSignedOut) },
      destructive = true,
    )
    Spacer(Modifier.height(32.dp))
  }
}

/**
 * Submitted, under review, approved.
 *
 * A row of three rather than a spinner: a spinner says "wait" and says nothing about how
 * long or what for, and this is a wait measured in hours.
 */
@Composable
private fun Stages(stage: ReviewStage) {
  val tokens = LocalTokens.current
  val reached = when (stage) {
    ReviewStage.INCOMPLETE -> 0
    ReviewStage.UNDER_REVIEW -> 1
    else -> 2
  }

  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    listOf(R.string.stage_submitted, R.string.stage_review, R.string.stage_approved)
      .forEachIndexed { index, label ->
        val done = index <= reached
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
          Box(
            Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(Radius.pill))
              .background(
                if (done) tokens.ink else MaterialTheme.colorScheme.surfaceContainerHigh,
              ),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = if (done) {
              MaterialTheme.colorScheme.onSurface
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
          )
        }
      }
  }
}

/** One paper: what it is, whether it is on file, and the one button that changes that. */
@Composable
private fun DocumentRow(
  kind: String,
  uploaded: Boolean,
  fileName: String?,
  uploading: Boolean,
  onPick: () -> Unit,
) {
  Panel {
    // The whole row is the target. A word at the end of it is a 40dp hit area next to a
    // 300dp one that does nothing.
    Row(
      Modifier
        .fillMaxWidth()
        .clickable(enabled = !uploading, onClick = onPick)
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      IconTile(
        if (uploaded) BillIcons.Check else BillIcons.ReceiptText,
        tone = if (uploaded) TileTone.MINT else TileTone.NEUTRAL,
      )
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(documentLabel(kind)),
          // Not titleLarge. Three of these down the page at headline size read as three
          // headings, and there is now a button beside each one to leave room for.
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          fileName ?: stringResource(
            if (kind == "pan") R.string.doc_required else R.string.doc_optional,
          ),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          // One line. A phone gallery name is long enough to wrap and push the row to
          // three lines to say nothing anybody reads.
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (uploading) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
      } else {
        // A button, not a word in the accent colour. Upload is the only thing this row
        // does, and coloured text next to grey text is a guess about which part is live.
        SecondaryButton(
          text = stringResource(if (uploaded) R.string.doc_replace else R.string.doc_upload),
          onClick = onPick,
          compact = true,
        )
      }
    }
  }
}

/** The name of a paper, in one place, because the row and the sheet both say it. */
private fun documentLabel(kind: String) = when (kind) {
  "pan" -> R.string.doc_pan
  "registration" -> R.string.doc_registration
  else -> R.string.doc_tax_clearance
}

/** Grey1, the same ink as `@color/crop_chrome`. The crop screen is dark in both modes. */
private const val CROP_CHROME = 0xFF0E0E0E.toInt()
