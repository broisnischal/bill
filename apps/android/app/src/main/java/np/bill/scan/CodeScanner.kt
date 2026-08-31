package np.bill.scan

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import android.util.Size
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * The camera, reading codes.
 *
 * One component serves three jobs: a shopper scanning the QR on a bill, a shop scanning a
 * shopper's card, and a shop scanning the barcode on a packet. Only the formats differ,
 * and narrowing them matters — asking ML Kit for every symbology it knows costs frames on
 * a phone that has none to spare.
 */
enum class ScanTarget(internal val formats: IntArray) {
  /** Bills and customer cards. */
  QR(intArrayOf(Barcode.FORMAT_QR_CODE)),

  /** What is printed on a packet: EAN and UPC, plus Code 128 for shelf labels. */
  PRODUCT(
    intArrayOf(
      Barcode.FORMAT_EAN_13,
      Barcode.FORMAT_EAN_8,
      Barcode.FORMAT_UPC_A,
      Barcode.FORMAT_UPC_E,
      Barcode.FORMAT_CODE_128,
      Barcode.FORMAT_CODE_39,
      Barcode.FORMAT_ITF,
    ),
  ),
}

@Composable
fun CodeScanner(
  target: ScanTarget,
  onScanned: (String) -> Unit,
  modifier: Modifier = Modifier,
  torch: Boolean = false,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var boundCamera by remember { mutableStateOf<Camera?>(null) }
  // Held across recomposition so a changing callback never restarts the camera.
  val callback by rememberUpdatedState(onScanned)
  val executor = remember { Executors.newSingleThreadExecutor() }
  val scanner = remember(target) {
    BarcodeScanning.getClient(
      BarcodeScannerOptions.Builder()
        .setBarcodeFormats(target.formats.first(), *target.formats.drop(1).toIntArray())
        .build(),
    )
  }

  DisposableEffect(scanner) {
    onDispose {
      scanner.close()
      executor.shutdown()
    }
  }

  // A shop counter is often the darkest corner of the room, and a thermal-printed code
  // on a fading receipt needs light before it needs resolution.
  LaunchedEffect(torch, boundCamera) {
    boundCamera?.cameraControl?.enableTorch(torch)
  }

  Box(modifier.fillMaxSize()) {
    AndroidView(
      factory = { context ->
        @Suppress("ClickableViewAccessibility")
        val previewView = PreviewView(context).apply {
          // PERFORMANCE puts the preview on a SurfaceView, which the display composites
          // directly. COMPATIBLE routes every frame through a TextureView and the GPU,
          // and on a 120Hz panel that is the difference between a smooth preview and a
          // visibly stuttering one.
          implementationMode = PreviewView.ImplementationMode.PERFORMANCE
          scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
          val provider = runCatching { providerFuture.get() }.getOrNull()
            ?: return@addListener

          // The preview is what the shopkeeper aims with, so it gets a full-resolution
          // 16:9 stream. Only the frames handed to the decoder are shrunk.
          val preview = Preview.Builder()
            .setResolutionSelector(
              ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build(),
            )
            .build()
            .apply { surfaceProvider = previewView.surfaceProvider }

          val analysis = ImageAnalysis.Builder()
            // The camera can outrun the decoder on a cheap phone; queueing frames would
            // only add lag between pointing and recognising.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // A barcode is legible well below sensor resolution, and decoding a 12MP
            // frame is what actually eats the frame budget. 1280x720 finds a code from
            // the same distance for a fraction of the work.
            .setResolutionSelector(
              ResolutionSelector.Builder()
                .setResolutionStrategy(
                  ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                  ),
                )
                .build(),
            )
            .build()

          analysis.setAnalyzer(executor) { proxy ->
            val image = proxy.image
            if (image == null) {
              proxy.close()
              return@setAnalyzer
            }
            scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
              .addOnSuccessListener { codes ->
                codes.firstNotNullOfOrNull { it.rawValue }?.let(callback)
              }
              .addOnCompleteListener { proxy.close() }
          }

          runCatching {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
              lifecycleOwner,
              CameraSelector.DEFAULT_BACK_CAMERA,
              preview,
              analysis,
            )
            boundCamera = camera

            // Continuous autofocus on the middle of the frame. Without this the camera
            // focuses once on whatever it saw first and a barcode held closer stays
            // blurred, which is most of what makes a scanner feel slow.
            val point = previewView.meteringPointFactory.createPoint(
              previewView.width / 2f,
              previewView.height / 2f,
            )
            camera.cameraControl.startFocusAndMetering(
              FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
            )
          }
        }, ContextCompat.getMainExecutor(context))

        previewView
      },
      modifier = Modifier.fillMaxSize(),
    )
  }
}
