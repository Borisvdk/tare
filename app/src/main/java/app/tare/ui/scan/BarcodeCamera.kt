package app.tare.ui.scan

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

/**
 * Live camera preview that reports product barcodes (EAN/UPC) via ML Kit's on-device model.
 * [onBarcode] may fire repeatedly for the same code; callers de-duplicate. Anything that goes
 * wrong while starting the camera goes to [onError] instead of taking the app down.
 */
@Composable
fun BarcodeCamera(
    onBarcode: (String) -> Unit,
    onError: (Throwable) -> Unit,
    torch: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnError by rememberUpdatedState(onError)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        var scanner: BarcodeScanner? = null
        try {
            val detector = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                    .build(),
            )
            scanner = detector
            provider = ProcessCameraProvider.awaitInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider { request -> surfaceRequest = request }
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(
                executor,
                MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                    result.getValue(detector)?.firstNotNullOfOrNull { it.rawValue }?.let { currentOnBarcode(it) }
                },
            )
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Errors too: a native library that won't load is an UnsatisfiedLinkError.
            CameraScannerGuard.failed(context)
            currentOnError(e)
        } finally {
            runCatching { provider?.unbindAll() }
            scanner?.close()
        }
    }

    LaunchedEffect(torch, camera) {
        runCatching { camera?.cameraControl?.enableTorch(torch) }
    }

    Box(modifier) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.matchParentSize()) }
    }
}
