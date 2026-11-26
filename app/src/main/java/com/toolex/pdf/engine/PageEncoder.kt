package com.toolex.pdf.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Renders one source image into a white page and JPEG-encodes it.
 *
 * This is the Kotlin counterpart of `render_page()` + Pillow's PDF re-encoding
 * (`quality=` / `resolution=`), but it works one page at a time on disk so a
 * book of 100 photos never needs 100 bitmaps in RAM.
 */
class PageEncoder(private val loader: ImageLoader) {

    /**
     * @param rung dpi + JPEG quality to use
     * @param scratchDir directory for the temporary .jpg
     */
    fun encode(
        uri: String,
        sourceInfo: com.toolex.pdf.model.ImageInfo,
        options: com.toolex.pdf.model.PdfOptions,
        rung: Rung,
        scratchDir: File,
        pageIndex: Int,
    ): EncodedPage {
        val androidUri = android.net.Uri.parse(uri)
        val layout = PageGeometry.layout(options, sourceInfo.width, sourceInfo.height)
        val place = PageGeometry.placement(
            options = options,
            layout = layout,
            imageWidth = sourceInfo.width,
            imageHeight = sourceInfo.height,
            dpi = rung.dpi,
        )

        // How much of the *original* image we actually need: Fill mode crops, so
        // the whole photo must be decoded larger than the final page raster or
        // the crop would be soft. Never more than the photo has.
        val detailRatio = max(
            place.rasterWidth.toDouble() / place.srcWidth.coerceAtLeast(1),
            place.rasterHeight.toDouble() / place.srcHeight.coerceAtLeast(1),
        )
        val needed = max(
            1,
            (max(sourceInfo.width, sourceInfo.height) * detailRatio).toInt() + 2,
        )
        var source: Bitmap? = null
        var page: Bitmap? = null
        try {
            source = loader.decode(androidUri, needed)

            page = Bitmap.createBitmap(place.rasterWidth, place.rasterHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(page)
            canvas.drawColor(Color.WHITE) // transparent PNGs become white, like the web app

            // Map the placement's source crop onto the actual decoded bitmap
            // (the decode may be a few pixels smaller after downsampling).
            val scaleX = source.width.toDouble() / max(1, sourceInfo.width)
            val scaleY = source.height.toDouble() / max(1, sourceInfo.height)
            val srcRect = Rect(
                (place.srcLeft * scaleX).toInt().coerceIn(0, source.width - 1),
                (place.srcTop * scaleY).toInt().coerceIn(0, source.height - 1),
                ((place.srcLeft + place.srcWidth) * scaleX).toInt().coerceIn(1, source.width),
                ((place.srcTop + place.srcHeight) * scaleY).toInt().coerceIn(1, source.height),
            )
            val dstRect = RectF(0f, 0f, place.rasterWidth.toFloat(), place.rasterHeight.toFloat())
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(source, srcRect, dstRect, paint)

            val jpegFile = File(scratchDir, "page_${pageIndex}_${rung.dpi}_${rung.quality}.jpg")
            FileOutputStream(jpegFile).use { out ->
                page.compress(Bitmap.CompressFormat.JPEG, rung.quality, out)
            }

            return EncodedPage(
                pageWidthPt = place.pageWidthPt,
                pageHeightPt = place.pageHeightPt,
                offsetXpt = place.offsetXpt,
                offsetYpt = place.offsetYpt,
                drawWidthPt = place.drawWidthPt,
                drawHeightPt = place.drawHeightPt,
                imageWidthPx = place.rasterWidth,
                imageHeightPx = place.rasterHeight,
                effectiveDpi = place.effectiveDpi,
                jpeg = jpegFile,
            )
        } finally {
            source?.recycle()
            page?.recycle()
        }
    }

}
