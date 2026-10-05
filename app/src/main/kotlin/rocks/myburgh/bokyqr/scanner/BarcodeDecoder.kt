package rocks.myburgh.bokyqr.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * The barcode decoding seam.
 *
 * This interface lives in `main`; a real implementation is provided once per product flavor:
 *
 *  * `src/play`  — bundled ML Kit (`com.google.mlkit:barcode-scanning`).
 *  * `src/fdroid` — ZXing (`com.google.zxing:core`) only, no ML Kit, no Play Services, no Firebase.
 *
 * It reports a single scalar payload and nothing else. The scanner must not be able to hand the
 * rest of the app anything richer than "the text that was in the code", because everything that
 * happens next is decided by `:core.PayloadPolicy`, never by the decoder.
 */
interface BarcodeDecoder {

    /**
     * Decodes the first QR payload in a live camera frame, or null when the frame holds no code.
     * Runs on the single background analyzer executor, never on the main thread. Implementations
     * block until they have an answer but must never touch the network.
     */
    fun decodeFrame(image: ImageProxy): String?

    /** Decodes the first QR payload in a still image from the gallery, or null. */
    fun decodeStill(bitmap: Bitmap): String?

    /** Releases native resources. Safe to call once. */
    fun close()
}
