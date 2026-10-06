package rocks.myburgh.bokyqr.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageProxy

/**
 * A decoded QR payload together with the geometry it was found at.
 *
 * [corners] and the image size exist for one reason only: drawing a tracking overlay on the
 * camera preview. They describe where the code was in the decoded image and not what it says,
 * they are never persisted, and they never leave the scanner UI.
 *
 * The payload is still just "the text that was in the code": everything that happens after a
 * decode is decided by `:core.PayloadPolicy`, never by the decoder.
 */
data class DecodedQr(
    val payload: String,
    val corners: List<PointF>,
    val imageWidth: Int,
    val imageHeight: Int,
)

/**
 * The barcode decoding seam.
 *
 * This interface lives in `main`; a real implementation is provided once per product flavor:
 *
 *  * `src/play`  — bundled ML Kit (`com.google.mlkit:barcode-scanning`).
 *  * `src/fdroid` — ZXing (`com.google.zxing:core`) only, no ML Kit, no Play Services, no Firebase.
 *
 * It reports a single scalar payload plus its geometry and nothing else. The scanner must not be
 * able to hand the rest of the app anything richer than "the text that was in the code", because
 * everything that happens next is decided by `:core.PayloadPolicy`, never by the decoder.
 */
interface BarcodeDecoder {

    /**
     * Decodes the first QR code in a live camera frame, or null when the frame holds no code.
     * Runs on the single background analyzer executor, never on the main thread. Implementations
     * block until they have an answer but must never touch the network. Corner points are in the
     * coordinate space of the upright image [imageWidth] x [imageHeight] describe.
     */
    fun decodeFrame(image: ImageProxy): DecodedQr?

    /** Decodes the first QR code in a still image from the gallery, or null. */
    fun decodeStill(bitmap: Bitmap): DecodedQr?

    /** Releases native resources. Safe to call once. */
    fun close()
}
