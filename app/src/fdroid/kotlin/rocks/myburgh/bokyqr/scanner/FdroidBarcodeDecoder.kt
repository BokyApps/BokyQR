package rocks.myburgh.bokyqr.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Flavor factory for the fdroid build. ZXing only: no ML Kit, no Play Services, no Firebase, no
 * closed components at all. `/api/v1` never reaches this tree.
 */
fun createBarcodeDecoder(context: Context): BarcodeDecoder = ZxingBarcodeDecoder()

class ZxingBarcodeDecoder : BarcodeDecoder {

    private val reader = QRCodeReader()
    private val hints: Map<DecodeHintType, Any> = mapOf(DecodeHintType.TRY_HARDER to true)

    override fun decodeFrame(image: ImageProxy): String? = decode(yuvLuminance(image))

    override fun decodeStill(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return decode(RGBLuminanceSource(width, height, pixels))
    }

    private fun decode(source: LuminanceSource): String? = try {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (_: Exception) {
        null
    }

    /** Copies the Y plane into a tightly packed array and rotates it upright for the decoder. */
    private fun yuvLuminance(image: ImageProxy): LuminanceSource {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        val gray = ByteArray(width * height)
        var index = 0
        for (y in 0 until height) {
            var offset = y * rowStride
            for (x in 0 until width) {
                gray[index++] = buffer.get(offset)
                offset += pixelStride
            }
        }
        val rotated = rotate(gray, width, height, image.imageInfo.rotationDegrees)
        return PlanarYUVLuminanceSource(
            rotated.data, rotated.width, rotated.height, 0, 0, rotated.width, rotated.height, false,
        )
    }

    private class Gray(val data: ByteArray, val width: Int, val height: Int)

    private fun rotate(src: ByteArray, width: Int, height: Int, degrees: Int): Gray = when (degrees) {
        90 -> {
            val dst = ByteArray(src.size)
            for (u in 0 until height) {
                for (v in 0 until width) {
                    dst[v * height + u] = src[(height - 1 - u) * width + v]
                }
            }
            Gray(dst, height, width)
        }
        180 -> {
            val dst = ByteArray(src.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    dst[y * width + x] = src[(height - 1 - y) * width + (width - 1 - x)]
                }
            }
            Gray(dst, width, height)
        }
        270 -> {
            val dst = ByteArray(src.size)
            for (u in 0 until height) {
                for (v in 0 until width) {
                    dst[v * height + u] = src[u * width + (width - 1 - v)]
                }
            }
            Gray(dst, height, width)
        }
        else -> Gray(src, width, height)
    }

    override fun close() = Unit
}
