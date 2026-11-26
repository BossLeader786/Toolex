package com.toolex

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.File
import kotlin.math.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class Paper(
    val size: String = "A4",
    val orientation: String = "Auto",
    val margin: Int = 16,
    val fit: String = "Fit",
    val quality: Int = 88,
    val target: String = "",
    val unit: String = "KB",
) {
    fun limit(): Long? {
        if (target.isBlank()) return null
        val value = target.toDoubleOrNull()?.times(if (unit == "KB") 1000 else 1_000_000)
        require(value != null && value.isFinite() && value >= 1 && value < Long.MAX_VALUE) {
            "Enter a positive target size."
        }
        return value.toLong()
    }
}

data class Layout(
    val width: Double,
    val height: Double,
    val margin: Double,
    val dw: Double,
    val dh: Double,
)

fun layout(w: Int, h: Int, paper: Paper): Layout {
    val pair =
        when (paper.size) {
            "A4" -> 595.28 to 841.89
            "A5" -> 419.53 to 595.28
            "Letter" -> 612.0 to 792.0
            else -> w * .75 to h * .75
        }
    val landscape = paper.orientation == "Landscape" || (paper.orientation == "Auto" && w > h)
    val (pw, ph) = if ((pair.first > pair.second) == landscape) pair else pair.second to pair.first
    val m = min(paper.margin.toDouble(), min(pw, ph) * .45)
    val sx = (pw - 2 * m) / w
    val sy = (ph - 2 * m) / h
    val scale = if (paper.fit == "Fit") min(sx, sy) else max(sx, sy)
    return Layout(pw, ph, m, w * scale, h * scale)
}

suspend fun Studio.document(
    photos: List<Photo>,
    paper: Paper,
    progress: (String) -> Unit,
): Product {
    require(photos.isNotEmpty())
    val target = paper.limit()
    suspend fun attempt(side: Int, quality: Int): File {
        val file = temp("pdf")
        val writer = PdfBuilder(file)
        try {
            writer.open("Toolex")
            photos.forEachIndexed { index, photo ->
                currentCoroutineContext().ensureActive()
                progress("Page ${index+1} of ${photos.size} · quality $quality")
                val raw = decode(photo, side)
                val jpeg = temp("jpg")
                try {
                    val s = min(1.0, side.toDouble() / max(raw.width, raw.height))
                    val w = max(1, (raw.width * s).roundToInt())
                    val h = max(1, (raw.height * s).roundToInt())
                    val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(flat)
                        canvas.drawColor(Color.WHITE)
                        canvas.drawBitmap(
                            raw,
                            null,
                            android.graphics.Rect(0, 0, w, h),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                        )
                        jpeg.outputStream().buffered().use {
                            check(flat.compress(Bitmap.CompressFormat.JPEG, quality, it))
                        }
                        val g = layout(photo.width, photo.height, paper)
                        writer.addPage(
                            g.width,
                            g.height,
                            w,
                            h,
                            (g.width - g.dw) / 2,
                            (g.height - g.dh) / 2,
                            g.dw,
                            g.dh,
                            jpeg,
                            g.margin,
                        )
                    } finally {
                        flat.recycle()
                    }
                } finally {
                    raw.recycle()
                    jpeg.delete()
                }
            }
            writer.finish()
            return file
        } catch (e: Throwable) {
            writer.abort()
            throw e
        }
    }
    var best: File? = null
    try {
        for (side in listOf(3072, 2048, 1440, 960, 640, 384, 192, 96)) {
            val high = attempt(side, paper.quality)
            if (target == null || high.length() <= target)
                return Product(high, "pdf", "application/pdf")
            high.delete()
            val floor = min(32, paper.quality)
            val low = attempt(side, floor)
            best?.delete()
            best = low
            if (low.length() <= target) {
                var a = floor + 1
                var b = paper.quality - 1
                while (a <= b) {
                    val mid = (a + b) / 2
                    val trial = attempt(side, mid)
                    if (trial.length() <= target) {
                        best?.delete()
                        best = trial
                        a = mid + 1
                    } else {
                        trial.delete()
                        b = mid - 1
                    }
                }
                return Product(requireNotNull(best), "pdf", "application/pdf").also { best = null }
            }
        }
        return Product(requireNotNull(best), "pdf", "application/pdf").also { best = null }
    } finally {
        best?.delete()
    }
}
