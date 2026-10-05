package rocks.myburgh.bokyqr.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import rocks.myburgh.bokyqr.scanner.BarcodeDecoder

/**
 * Feeds camera frames to the flavor decoder on the analyzer's single background executor.
 *
 * It never touches the network and never launches anything: it only hands the decoded text to
 * [onPayload], which runs it through `:core.PayloadPolicy`. While a result sheet is on screen the
 * analyzer is paused, so a code sitting in front of the camera does not decode over and over.
 *
 * The decoder is attached with [setDecoder] rather than passed to the constructor, because it is
 * built off the main thread and may fail; see `CameraScreen`. Until one arrives, frames are simply
 * not decoded.
 */
class QrAnalyzer(
    private val onPayload: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var decoder: BarcodeDecoder? = null

    @Volatile
    var paused: Boolean = false

    @Volatile
    private var lastPayload: String? = null

    /** Attaches (or, with null, detaches) the decoder. Safe to call from any thread. */
    fun setDecoder(decoder: BarcodeDecoder?) {
        this.decoder = decoder
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (paused) return
            // A decoder fault is "no code in this frame", never a crash. ZXing walks raw buffers
            // and ML Kit throws on a closed or malformed frame; either way the next frame is
            // 16 ms away and there is nothing useful to show the user about it.
            val payload = decoder?.decodeFrame(image) ?: return
            if (payload != lastPayload) {
                lastPayload = payload
                onPayload(payload)
            }
        } catch (_: Exception) {
            // Swallowed on purpose. ImageAnalysis.Analyzer runs on a CameraX-owned thread and an
            // exception escaping it tears down the analysis stream.
        } finally {
            runCatching { image.close() }
        }
    }

    /** Allows the same code to be reported again after a sheet was dismissed. */
    fun reset() {
        lastPayload = null
    }
}