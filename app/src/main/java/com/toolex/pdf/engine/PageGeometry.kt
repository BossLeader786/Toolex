package com.toolex.pdf.engine

import com.toolex.pdf.model.FitMode
import com.toolex.pdf.model.PageLayout
import com.toolex.pdf.model.PageSize
import com.toolex.pdf.model.PdfOptions
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure math, no Android types — all of this is unit-testable on the JVM.
 *
 * Direct port of the Flask/Pillow helpers:
 *   page_points() -> [layout]
 *   render_page() -> [placement]
 */
object PageGeometry {

    /** Port of `page_points()`; dimensions must already be EXIF-rotated. */
    fun layout(options: PdfOptions, imageWidth: Int, imageHeight: Int): PageLayout {
        val (pw, ph) = options.pagePoints(imageWidth, imageHeight)
        val margin = options.margins.points
        val aw = max(1.0, pw - 2 * margin) // Pillow: max(1, pw - 2*margin)
        val ah = max(1.0, ph - 2 * margin)
        return PageLayout(
            pageWidth = pw,
            pageHeight = ph,
            margin = margin,
            areaWidth = aw,
            areaHeight = ah,
        )
    }

    /**
     * Where the picture lands inside one page.
     *
     * Points are the PDF truth. Pixels are what we actually encode, capped to
     * the source detail so we never upscale pixels for no visible gain.
     */
    data class Placement(
        val pageWidthPt: Double,
        val pageHeightPt: Double,
        /** Top-down offset of the picture box on the page, in points. */
        val offsetXpt: Double,
        val offsetYpt: Double,
        val drawWidthPt: Double,
        val drawHeightPt: Double,
        /** Pixels we encode (<= source detail, <= dpi-derived box). */
        val rasterWidth: Int,
        val rasterHeight: Int,
        /** Crop of the oriented source, in source pixels (Fill mode crops). */
        val srcLeft: Int,
        val srcTop: Int,
        val srcWidth: Int,
        val srcHeight: Int,
        /** Effective sampling DPI actually achieved for this picture. */
        val effectiveDpi: Int,
    )

    fun placement(
        options: PdfOptions,
        layout: PageLayout,
        imageWidth: Int,
        imageHeight: Int,
        dpi: Int,
    ): Placement {
        val pw = layout.pageWidth
        val ph = layout.pageHeight
        val aw = layout.areaWidth
        val ah = layout.areaHeight

        var srcLeft = 0
        var srcTop = 0
        var srcWidth = imageWidth
        var srcHeight = imageHeight

        if (options.fit == FitMode.FILL) {
            // Center-crop to the page's aspect ratio, exactly like Pillow.
            val ratio = aw / ah
            if (imageWidth.toDouble() / imageHeight > ratio) {
                val cw = max(1, (imageHeight * ratio).roundToInt())
                srcLeft = (imageWidth - cw) / 2
                srcWidth = cw
            } else {
                val ch = max(1, (imageWidth / ratio).roundToInt())
                srcTop = (imageHeight - ch) / 2
                srcHeight = ch
            }
        }

        val scale = when {
            options.fit == FitMode.FILL -> min(aw / srcWidth, ah / srcHeight)
            // Original page + small photo: never stretch beyond 100%.
            options.pageSize == PageSize.ORIGINAL -> min(1.0, min(aw / srcWidth, ah / srcHeight))
            else -> min(aw / srcWidth, ah / srcHeight)
        }

        var drawWidthPt = max(1, (srcWidth * scale).roundToInt()).toDouble()
        var drawHeightPt = max(1, (srcHeight * scale).roundToInt()).toDouble()

        // Safety net (Pillow's `im.thumbnail(canvas.size)` equivalent): round()
        // can push the box a fraction of a point past its limit, so clamp it to
        // the printable area — never the page — to keep margins exact and the
        // picture fully inside the paper, even on Original size.
        val areaClamp = min(1.0, min(aw / drawWidthPt, ah / drawHeightPt))
        if (areaClamp < 1.0) {
            drawWidthPt *= areaClamp
            drawHeightPt *= areaClamp
        }

        val offsetXpt = max(0.0, (pw - drawWidthPt) / 2)
        val offsetYpt = max(0.0, (ph - drawHeightPt) / 2)

        // Pixels the dpi would ask for...
        val wantWidth = max(1, (drawWidthPt * dpi / 72.0).roundToInt())
        val wantHeight = max(1, (drawHeightPt * dpi / 72.0).roundToInt())
        // ...but never encode more detail than the source has.
        val detailFactor = min(
            1.0,
            min(srcWidth.toDouble() / wantWidth, srcHeight.toDouble() / wantHeight),
        )
        val rasterWidth = max(1, (wantWidth * detailFactor).roundToInt())
        val rasterHeight = max(1, (wantHeight * detailFactor).roundToInt())
        val effectiveDpi = max(1, (rasterWidth * 72.0 / max(1.0, drawWidthPt)).roundToInt())

        return Placement(
            pageWidthPt = pw,
            pageHeightPt = ph,
            offsetXpt = offsetXpt,
            offsetYpt = offsetYpt,
            drawWidthPt = drawWidthPt,
            drawHeightPt = drawHeightPt,
            rasterWidth = rasterWidth,
            rasterHeight = rasterHeight,
            srcLeft = srcLeft,
            srcTop = srcTop,
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            effectiveDpi = effectiveDpi,
        )
    }

    /** Matches the Flask floor: 10 KB. Upper bound is 200 MB. */
    const val MIN_TARGET_BYTES = 10L * 1024
    const val MAX_TARGET_BYTES = 200L * 1024 * 1024

    /** Flask's `MAX_CONTENT_LENGTH` (200 MB) for the whole job. */
    const val MAX_INPUT_BYTES = 200L * 1024 * 1024
}
