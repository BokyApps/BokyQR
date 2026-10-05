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
 */
class QrAnalyzer(
    private val decoder: BarcodeDecoder,
    private val onPayload: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    var paused: Boolean = false

    @Volatile
    private var lastPayload: String? = null

    override fun analyze(image: ImageProxy) {
        try {
            if (paused) return
            val payload = decoder.decodeFrame(image) ?: return
            if (payload != lastPayload) {
                lastPayload = payload
                onPayload(payload)
            }
        } finally {
            image.close()
        }
    }

    /** Allows the same code to be reported again after a sheet was dismissed. */
    fun reset() {
        lastPayload = null
    }
}
