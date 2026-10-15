package de.privat.kalorienzaehler.ui.scanner

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.privat.kalorienzaehler.domain.barcode.BarcodeFormat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import zxingcpp.BarcodeReader

/**
 * CameraX shows the picture and hands every frame to zxing-cpp, which reads EAN-13, EAN-8, UPC-A and UPC-E on the
 * device (no network, no Google services). The only place that touches the camera libraries.
 */
object CameraXBarcodeCamera : BarcodeCamera {

    @Composable
    override fun Preview(
        scanning: Boolean,
        torchOn: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        onCode: (text: String, format: BarcodeFormat) -> Unit,
        modifier: Modifier,
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember {
            PreviewView(context).apply {
                // A TextureView: composes with the frame drawn on top of it and with the scroll of the screen.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
        val reading = remember { AtomicBoolean(scanning) }.also { it.set(scanning) }
        val latestOnCode by rememberUpdatedState(onCode)
        val latestOnTorchAvailable by rememberUpdatedState(onTorchAvailable)
        var camera by remember { mutableStateOf<Camera?>(null) }

        DisposableEffect(lifecycleOwner) {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val analysisExecutor = Executors.newSingleThreadExecutor()
            val reader = retailBarcodeReader()
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            var disposed = false

            providerFuture.addListener(
                {
                    if (disposed) return@addListener
                    val cameraProvider = providerFuture.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        // Barcodes need more pixels than the default 640x480 analysis size.
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .setResolutionStrategy(
                                    ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                                )
                                .build(),
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { useCase ->
                            useCase.setAnalyzer(analysisExecutor) { image ->
                                analyze(image, reader, reading) { text, format ->
                                    mainExecutor.execute { latestOnCode(text, format) }
                                }
                            }
                        }
                    cameraProvider.unbindAll()
                    val bound = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    camera = bound
                    latestOnTorchAvailable(bound.cameraInfo.hasFlashUnit())
                },
                mainExecutor,
            )

            onDispose {
                disposed = true
                provider?.unbindAll()
                analysisExecutor.shutdown()
                camera = null
            }
        }

        LaunchedEffect(camera, torchOn) {
            camera?.cameraControl?.enableTorch(torchOn)
        }

        AndroidView(factory = { previewView }, modifier = modifier)
    }

    private fun analyze(
        image: ImageProxy,
        reader: BarcodeReader,
        reading: AtomicBoolean,
        report: (String, BarcodeFormat) -> Unit,
    ) {
        try {
            if (!reading.get()) return
            for (result in reader.read(image)) {
                val format = result.format.toBarcodeFormat() ?: continue
                val text = result.text ?: continue
                report(text, format)
                return
            }
        } finally {
            image.close()
        }
    }
}

/** The library's format as the app's: only the four retail formats count. */
fun BarcodeReader.Format.toBarcodeFormat(): BarcodeFormat? = when (this) {
    BarcodeReader.Format.EAN_13 -> BarcodeFormat.EAN_13
    BarcodeReader.Format.EAN_8 -> BarcodeFormat.EAN_8
    BarcodeReader.Format.UPC_A -> BarcodeFormat.UPC_A
    BarcodeReader.Format.UPC_E -> BarcodeFormat.UPC_E
    else -> null
}

/** The reader the camera uses: the four retail formats only. */
fun retailBarcodeReader(): BarcodeReader = BarcodeReader(
    BarcodeReader.Options().apply {
        formats = setOf(
            BarcodeReader.Format.EAN_13,
            BarcodeReader.Format.EAN_8,
            BarcodeReader.Format.UPC_A,
            BarcodeReader.Format.UPC_E,
        )
    },
)
