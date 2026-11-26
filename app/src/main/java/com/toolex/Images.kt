package com.toolex

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.*

data class Photo(
    val uri: Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val rotation: Int,
    val mirror: Boolean,
)

data class Crop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
)

data class Edit(
    val crop: Crop = Crop(),
    val rotation: Int = 0,
    val flip: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val filter: String = "Natural",
    val text: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val locked: Boolean = true,
    val format: String = "JPG",
    val quality: Int = 88,
)

data class Product(
    val file: File,
    val ext: String,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
)

class Studio(val context: Context) {
    private val resolver = context.contentResolver
    private val folder = File(context.cacheDir, "toolex").apply { mkdirs() }

    fun temp(ext: String): File = File.createTempFile("work-", ".$ext", folder)

    private fun stream(uri: Uri) =
        requireNotNull(resolver.openInputStream(uri)) { "Image is unavailable." }

    fun inspect(uri: Uri): Photo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported or damaged image." }
        val exif = runCatching { stream(uri).use { ExifInterface(it) } }.getOrNull()
        val rotation = exif?.rotationDegrees ?: 0
        val swap = rotation % 180 != 0
        var name = "Image"
        var bytes = -1L
        resolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )
            ?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0) ?: name
                    if (!it.isNull(1)) bytes = it.getLong(1)
                }
            }
        if (bytes < 0)
            bytes =
                stream(uri).use {
                    it.copyTo(
                        object : java.io.OutputStream() {
                            override fun write(b: Int) {}

                            override fun write(b: ByteArray, off: Int, len: Int) {}
                        }
                    )
                }
        return Photo(
            uri,
            name,
            if (swap) options.outHeight else options.outWidth,
            if (swap) options.outWidth else options.outHeight,
            bytes,
            rotation,
            exif?.isFlipped ?: false,
        )
    }

    fun decode(photo: Photo, side: Int): Bitmap {
        var sample = 1
        val maxPixels = min(6_000_000L, Runtime.getRuntime().maxMemory() / 40)
        while (
            max(photo.width, photo.height).toLong() / sample > side * 1.5 ||
                photo.width.toLong() * photo.height / sample / sample > maxPixels
        ) sample *= 2
        val bitmap =
            stream(photo.uri).use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            } ?: error("Could not decode image.")
        if (!photo.mirror && photo.rotation == 0) return bitmap
        val m =
            Matrix().apply {
                if (photo.mirror) postScale(-1f, 1f)
                postRotate(photo.rotation.toFloat())
            }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    fun dimensions(photo: Photo, edit: Edit): Pair<Int, Int> {
        val w = max(1, (photo.width * (edit.crop.right - edit.crop.left)).roundToInt())
        val h = max(1, (photo.height * (edit.crop.bottom - edit.crop.top)).roundToInt())
        val (a, b) = if (edit.rotation % 180 == 0) w to h else h to w
        val limit = min(8_000_000L, Runtime.getRuntime().maxMemory() / 32)
        val scale = minOf(1.0, sqrt(limit.toDouble() / (a.toLong() * b)), 8192.0 / max(a, b))
        val width = if (edit.width > 0) edit.width else max(1, (a * scale).roundToInt())
        val height = if (edit.height > 0) edit.height else max(1, (b * scale).roundToInt())
        require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= limit + 8192) {
            "Choose a smaller resolution (up to ${limit / 1_000_000} MP and 8192 px per side)."
        }
        return width to height
    }

    fun encode(photo: Photo, edit: Edit): Product {
        val (w, h) = dimensions(photo, edit)
        val crop = edit.crop
        val needed =
            (max(w, h) / min(crop.right - crop.left, crop.bottom - crop.top))
                .toInt()
                .coerceIn(1, 8192)
        val src = decode(photo, needed)
        val bitmap =
            try {
                render(src, edit, w, h)
            } finally {
                src.recycle()
            }
        val jpg = edit.format == "JPG"
        val ext = if (jpg) "jpg" else "png"
        val file = temp(ext)
        try {
            file.outputStream().buffered().use {
                check(
                    bitmap.compress(
                        if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,
                        edit.quality,
                        it,
                    )
                )
            }
            return Product(file, ext, if (jpg) "image/jpeg" else "image/png", w, h)
        } catch (e: Throwable) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
    }

    fun render(src: Bitmap, edit: Edit, w: Int, h: Int): Bitmap {
        val crop = edit.crop
        val jpg = edit.format == "JPG"
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            if (jpg) canvas.drawColor(Color.WHITE)
            val matrix =
                ColorMatrix().apply {
                    setSaturation(if (edit.filter == "Mono") 0f else edit.saturation)
                }
            val k = edit.contrast
            val v = (1 - k) * 128 + edit.brightness * 100
            val warm = if (edit.filter == "Warm") 15f else 0f
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        k,
                        0f,
                        0f,
                        0f,
                        v + warm,
                        0f,
                        k,
                        0f,
                        0f,
                        v,
                        0f,
                        0f,
                        k,
                        0f,
                        v - warm,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                    )
                )
            )
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(matrix)
                }
            canvas.save()
            canvas.translate(w / 2f, h / 2f)
            if (edit.flip) canvas.scale(-1f, 1f)
            canvas.rotate(edit.rotation.toFloat())
            val (dw, dh) = if (edit.rotation % 180 == 0) w to h else h to w
            val left = (crop.left * src.width).toInt().coerceIn(0, src.width - 1)
            val top = (crop.top * src.height).toInt().coerceIn(0, src.height - 1)
            val rect =
                Rect(
                    left,
                    top,
                    (crop.right * src.width).roundToInt().coerceIn(left + 1, src.width),
                    (crop.bottom * src.height).roundToInt().coerceIn(top + 1, src.height),
                )
            canvas.drawBitmap(src, rect, RectF(-dw / 2f, -dh / 2f, dw / 2f, dh / 2f), paint)
            canvas.restore()
            if (edit.text.isNotBlank()) {
                val label =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = min(w, h) * 0.075f
                        if (measureText(edit.text) > w * .9f)
                            textSize *= w * .9f / measureText(edit.text)
                        setShadowLayer(max(1f, textSize * .07f), 1f, 1f, Color.BLACK)
                    }
                canvas.drawText(edit.text, w * .05f, h * .92f, label)
            }
            return bitmap
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
    }
}
