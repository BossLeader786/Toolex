package com.toolex.pdf.engine

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.toolex.pdf.model.ImageInfo
import com.toolex.pdf.model.PdfException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decoding + EXIF handling.
 *
 * Mirrors `ImageOps.exif_transpose()` + the white background flattening from
 * the Flask app, but streams and downsamples so a 50 MP phone photo does not
 * blow up the heap.
 */
class ImageLoader(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** Reads only the header: dimensions after EXIF rotation + mime type. */
    fun inspect(uri: Uri): ImageInfo {
        val (rawW, rawH) = readBounds(uri) ?: throw PdfException("Image open nahi ho payi.")
        val rotation = readRotationDegrees(uri)
        val (w, h) = if (rotation % 180 == 0) rawW to rawH else rawH to rawW
        return ImageInfo(width = w, height = h, mimeType = readMime(uri))
    }

    /**
     * Decodes [uri] rotated upright and scaled so its longest side is at most
     * [maxSide] pixels. Transparency is flattened onto white, like the PDF will
     * show it.
     *
     * @return pair of bitmap + its upright size (which may be smaller than the
     *         original: callers use [inspect] for layout).
     */
    fun decode(uri: Uri, maxSide: Int): Bitmap {
        val bounds = readBounds(uri) ?: throw PdfException("Image decode nahi hui.")
        val longest = max(bounds.first, bounds.second)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw PdfException("Ye image format support nahi karta.")

        // EXIF (camera shots are often stored rotated).
        val rotation = readRotationDegrees(uri)
        var bitmap = decoded
        if (rotation % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
                bitmap = rotated
            }
        }

        // Final resize down to the requested detail.
        val side = max(bitmap.width, bitmap.height)
        if (side > maxSide) {
            val ratio = maxSide.toDouble() / side
            val targetW = max(1, (bitmap.width * ratio).roundToInt())
            val targetH = max(1, (bitmap.height * ratio).roundToInt())
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled != bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }
        return bitmap
    }

    /** Cheap byte counter for the total-size guard. */
    fun byteSize(uri: Uri): Long = try {
        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            ?: openStream(uri).use { it.available().toLong() }
    } catch (_: Exception) {
        0L
    }

    fun displayName(uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
        return try {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun openStream(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw PdfException("File read nahi ho payi.")

    private fun readBounds(uri: Uri): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        return opts.outWidth to opts.outHeight
    }

    private fun readMime(uri: Uri): String? = try {
        resolver.getType(uri) ?: "image/*"
    } catch (_: Exception) {
        null
    }

    /** Degrees needed to make the image upright (only multiples of 90). */
    private fun readRotationDegrees(uri: Uri): Int {
        val orientation = try {
            openStream(uri).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }
}
