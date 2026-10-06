package rocks.myburgh.bokyqr.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
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

    override fun decodeFrame(image: ImageProxy): DecodedQr? {
        val mediaImage = image.image ?: return null
        val rotation = image.imageInfo.rotationDegrees
        // ML Kit reports barcode coordinates in the coordinate space of the input image as it is
        // meant to be displayed, i.e. after this rotation is applied. For a 90/270 frame that is
        // the upright image, whose width and height are the ImageProxy's the other way round.
        val rotated = rotation == 90 || rotation == 270
        val width = if (rotated) image.height else image.width
        val height = if (rotated) image.width else image.height
        return run(InputImage.fromMediaImage(mediaImage, rotation), width, height, 5)
    }

    override fun decodeStill(bitmap: Bitmap): DecodedQr? =
        run(InputImage.fromBitmap(bitmap, 0), bitmap.width, bitmap.height, 10)

    private fun run(input: InputImage, imageWidth: Int, imageHeight: Int, timeoutSeconds: Long): DecodedQr? = try {
        val task = scanner.process(input)
        Tasks.await(task, timeoutSeconds, TimeUnit.SECONDS)
        val barcode = task.result?.firstOrNull { it.rawValue != null } ?: return null
        val payload = barcode.rawValue ?: return null
        DecodedQr(
            payload = payload,
            corners = barcode.corners(),
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )
    } catch (_: Exception) {
        // Any decode failure is simply "no code in this frame"; frames keep coming.
        null
    }

    /**
     * The code's corners as [PointF], or its bounding box when ML Kit did not report corner
     * points. An empty list is "decoded but no geometry", which the overlay treats as "keep
     * searching" rather than drawing a shape at the origin.
     */
    private fun Barcode.corners(): List<PointF> {
        val points = cornerPoints
        if (points != null && points.isNotEmpty()) {
            return points.map { PointF(it.x.toFloat(), it.y.toFloat()) }
        }
        val box = boundingBox ?: return emptyList()
        return listOf(
            PointF(box.left.toFloat(), box.top.toFloat()),
            PointF(box.right.toFloat(), box.top.toFloat()),
            PointF(box.right.toFloat(), box.bottom.toFloat()),
            PointF(box.left.toFloat(), box.bottom.toFloat()),
        )
    }

    override fun close() {
        scanner.close()
    }
}
