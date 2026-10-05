package rocks.myburgh.bokyqr.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes a still image chosen through the AndroidX photo picker.
 *
 * The picker hands back a `content://` URI for a single item, which is all the app ever needs: no
 * `READ_MEDIA_IMAGES`, no `READ_EXTERNAL_STORAGE`, no broad gallery access.
 *
 * Decoding happens off the main thread and is subsampled so the decoded bitmap is at most
 * [MAX_PIXELS]. A 108-megapixel phone photo is never materialised at full size.
 */
object GalleryLoader {

    private const val MAX_PIXELS = 8_000_000

    /**
     * Returns the decoded, upright bitmap, or null when the image cannot be read at all.
     *
     * Never throws. A picker URI is a promise made by another app, and that promise can be broken
     * between the moment the user picks the image and the moment it is opened here: the granting
     * app can be killed, the URI can be revoked, the item can be deleted by a sync client, or the
     * provider can simply be one this device does not understand. `openInputStream` throws
     * `SecurityException`, `FileNotFoundException` or `IllegalArgumentException` for those, and
     * `BitmapFactory` can throw `OutOfMemoryError` on a hostile file. All of them mean the same
     * thing to the caller: null, and a "that image could not be read" message.
     */
    suspend fun load(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(resolver, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_PIXELS)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = openStream(resolver, uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@runCatching null

            val orientation = openStream(resolver, uri)?.use {
                runCatching {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            rotateIfNeeded(decoded, orientation)
        }.getOrNull()
    }

    /** One guarded open per URI. Each of the three opens below is a separate trip to the provider. */
    private fun openStream(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): java.io.InputStream? = runCatching { resolver.openInputStream(uri) }.getOrNull()

    /** Smallest power-of-two sample whose output is within [maxPixels]. */
    internal fun calculateInSampleSize(width: Int, height: Int, maxPixels: Int): Int {
        var sample = 1
        while ((width.toLong() / sample) * (height.toLong() / sample) > maxPixels) {
            sample *= 2
        }
        return sample
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
