package rocks.myburgh.bokyqr.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
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

    /**
     * A [com.google.zxing.Reader] is stateful and `reset()` is part of its contract: it caches the
     * binarizer's bit matrix between calls. One reader per calling thread, each guarded, so the
     * analyzer executor and the gallery decode can never interleave inside one instance.
     */
    private val frameReader = QRCodeReader()
    private val frameLock = Any()
    private val stillReader = QRCodeReader()
    private val stillLock = Any()
    private val hints: Map<DecodeHintType, Any> = mapOf(DecodeHintType.TRY_HARDER to true)

    override fun decodeFrame(image: ImageProxy): DecodedQr? {
        val source = yuvLuminance(image) ?: return null
        // The luminance source is already upright, so its dimensions are the image size the
        // result points below are expressed in.
        return decode(source, source.width, source.height, frameReader, frameLock)
    }

    override fun decodeStill(bitmap: Bitmap): DecodedQr? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val source = runCatching {
            RGBLuminanceSource(width, height, IntArray(width * height).also {
                bitmap.getPixels(it, 0, width, 0, 0, width, height)
            })
        }.getOrNull() ?: return null
        return decode(source, width, height, stillReader, stillLock)
    }

    private fun decode(
        source: LuminanceSource,
        imageWidth: Int,
        imageHeight: Int,
        reader: QRCodeReader,
        lock: Any,
    ): DecodedQr? {
        synchronized(lock) {
            return try {
                val result = reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
                DecodedQr(
                    payload = result.text,
                    corners = result.resultPoints?.map { PointF(it.x, it.y) }.orEmpty(),
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                )
            } catch (_: Exception) {
                null
            } finally {
                // Required by the Reader contract and cheap; without it a failed decode leaves a
                // stale bit matrix behind that the next attempt decodes against.
                runCatching { reader.reset() }
            }
        }
    }

    /**
     * Copies the Y plane into a tightly packed array and rotates it upright for the decoder.
     *
     * Returns null for any frame this cannot walk safely. `rowStride` and `pixelStride` come from
     * the device's camera HAL and are not guaranteed to describe the buffer as tightly as this
     * loop assumes, and the plane's `limit()` is not guaranteed to cover `height * rowStride`, so
     * every read is bounded rather than trusted. A short or oddly strided plane is "no code in
     * this frame", which is exactly what an undecodable frame already means.
     */
    private fun yuvLuminance(image: ImageProxy): LuminanceSource? = runCatching {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) return null
        val gray = ByteArray(width * height)
        val limit = buffer.limit()
        var index = 0
        for (y in 0 until height) {
            var offset = y.toLong() * rowStride
            for (x in 0 until width) {
                if (offset < 0 || offset >= limit) return null
                gray[index++] = buffer.get(offset.toInt())
                offset += pixelStride
            }
        }
        val rotated = rotate(gray, width, height, image.imageInfo.rotationDegrees)
        PlanarYUVLuminanceSource(
            rotated.data, rotated.width, rotated.height, 0, 0, rotated.width, rotated.height, false,
        )
    }.getOrNull()

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
