package rocks.myburgh.bokyqr.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.TimeUnit

/**
 * Flavor factory. The play flavor bundles ML Kit's on-device barcode model, so it works with no
 * Play Services installed and with no network access.
 *
 * It does still carry one Google runtime dependency: `play-services-tasks`, for
 * `com.google.android.gms.tasks.Tasks.await` below. That library is the Task API only, not the
 * Play Services runtime. Firebase and the ClearCut transport (datatransport) are forbidden on
 * this flavor, are excluded in app/build.gradle.kts, and are enforced by verifyPlayClasspath.
 *
 * Defined once per flavor on purpose: `main` refers to `createBarcodeDecoder` and each flavor
 * supplies exactly one.
 */
fun createBarcodeDecoder(context: Context): BarcodeDecoder = MlKitBarcodeDecoder()

class MlKitBarcodeDecoder : BarcodeDecoder {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    override fun decodeFrame(image: ImageProxy): String? {
        val mediaImage = image.image ?: return null
        return run(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees), 5)
    }

    override fun decodeStill(bitmap: Bitmap): String? = run(InputImage.fromBitmap(bitmap, 0), 10)

    private fun run(input: InputImage, timeoutSeconds: Long): String? = try {
        val task = scanner.process(input)
        Tasks.await(task, timeoutSeconds, TimeUnit.SECONDS)
        task.result?.firstNotNullOfOrNull { it.rawValue }
    } catch (_: Exception) {
        // Any decode failure is simply "no code in this frame"; frames keep coming.
        null
    }

    override fun close() {
        scanner.close()
    }
}
