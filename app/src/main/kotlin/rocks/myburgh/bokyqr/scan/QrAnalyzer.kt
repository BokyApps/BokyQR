package rocks.myburgh.bokyqr.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import rocks.myburgh.bokyqr.scanner.BarcodeDecoder
import rocks.myburgh.bokyqr.scanner.DecodedQr

/**
 * Feeds camera frames to the flavor decoder on the analyzer's single background executor.
 *
 * It never touches the network and never launches anything: it only hands the decoded text to
 * [onPayload], which runs it through `:core.PayloadPolicy`, and the decoded geometry to
 * [onFrameDecoded] for the preview overlay. While a result sheet is on screen the analyzer is
 * paused, so a code sitting in front of the camera does not decode over and over.
 *
 * [onFrameDecoded] runs on the analyzer thread for every analyzed frame, with null when the frame
 * held no code, so the UI can track and clear its border. It must not touch Compose state from
 * there; see `CameraScreen`.
 *
 * The decoder is attached with [setDecoder] rather than passed to the constructor, because it is
 * built off the main thread and may fail; see `CameraScreen`. Until one arrives, frames are simply
 * not decoded.
 */
class QrAnalyzer(
    private val onPayload: (String) -> Unit,
    private val onFrameDecoded: (DecodedQr?) -> Unit = {},
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
            val decoded = runCatching { decoder?.decodeFrame(image) }.getOrNull()
            val payload = decoded?.payload
            // The sheet opens once per distinct code; the overlay is fed every frame so it can
            // follow the code and disappear when it leaves the frame.
            if (payload != null && payload != lastPayload) {
                lastPayload = payload
                onPayload(payload)
            }
            onFrameDecoded(decoded)
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
