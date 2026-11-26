package com.toolex.pdf.model

import kotlin.math.max
import kotlin.math.min

/** PDF page sizes, in PostScript points (1/72 inch) — same numbers as the Flask version. */
enum class PageSize(val label: String, val shortLabel: String, val widthPt: Double, val heightPt: Double) {
    A4("A4", "A4", 595.276, 841.89),
    A5("A5", "A5", 419.528, 595.276),
    LETTER("Letter", "Letter", 612.0, 792.0),
    ORIGINAL("Original", "Orig", 0.0, 0.0),
    ;

    val isFixed: Boolean get() = this != ORIGINAL

    companion object {
        fun fromName(value: String?): PageSize =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) ?: false }
                ?: A4
    }
}

/** "Auto" follows the photo, portrait/landscape force the orientation. */
enum class Orientation(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
}

/**
 * FIT  = whole photo visible, white bars if the aspect ratio does not match
 * FILL = page fully covered, edges cropped (same as Pillow version's "Fill")
 */
enum class FitMode(val label: String, val hint: String) {
    FIT("Fit", "Poora photo dikhega, zarurat par white border"),
    FILL("Fill", "Page poora bharega, photo ke kinare crop honge"),
}

enum class MarginSize(val label: String, val points: Double) {
    NONE("None", 0.0),
    SMALL("Small", 18.0),
    MEDIUM("Medium", 36.0),
    LARGE("Large", 54.0),
}

enum class OutputMode(val label: String, val hint: String) {
    MERGED("One merged PDF", "Saari images ek hi multi-page PDF me"),
    SEPARATE("Separate PDFs (ZIP)", "Har image alag PDF, sab ek ZIP me"),
}

enum class SizeMode {
    /** Daily-use quality: fixed 200 DPI / JPEG q88, like "Normal" in PaperLite. */
    NORMAL,

    /** Hit the requested ceiling with the best quality that fits. */
    CUSTOM,
}

/**
 * Everything the user chose on the screen. Immutable so background work can
 * snapshot it safely.
 */
data class PdfOptions(
    val pageSize: PageSize = PageSize.A4,
    val orientation: Orientation = Orientation.AUTO,
    val fit: FitMode = FitMode.FIT,
    val margins: MarginSize = MarginSize.NONE,
    val outputMode: OutputMode = OutputMode.MERGED,
) {
    /**
     * Page box in points for one image — direct port of `page_points()`.
     *  - Original: image pixels assumed to be 96 DPI -> points.
     *  - Auto: landscape stays landscape, portrait stays portrait.
     *  - Landscape/Portrait: fixed.
     */
    fun pagePoints(imageWidth: Int, imageHeight: Int): Pair<Double, Double> {
        if (pageSize == PageSize.ORIGINAL) {
            return imageWidth * 72.0 / 96.0 to imageHeight * 72.0 / 96.0
        }
        val w = pageSize.widthPt
        val h = pageSize.heightPt
        val long = max(w, h)
        val short = min(w, h)
        return when (orientation) {
            Orientation.AUTO -> if (imageWidth > imageHeight) long to short else short to long
            Orientation.LANDSCAPE -> long to short
            Orientation.PORTRAIT -> short to long
        }
    }
}

/** Resolved, ready-to-use PDF settings for one image. */
data class PageLayout(
    val pageWidth: Double,
    val pageHeight: Double,
    val margin: Double,
    /** Area available for the picture once margins are removed. */
    val areaWidth: Double,
    val areaHeight: Double,
)

/** A page after rendering, at a concrete raster size. */
data class RasterPage(val widthPx: Int, val heightPx: Int, val jpeg: ByteArray) {
    // ByteArray needs structural equality by hand.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RasterPage) return false
        return widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            jpeg.contentEquals(other.jpeg)
    }

    override fun hashCode(): Int = 31 * (31 * widthPx + heightPx) + jpeg.contentHashCode()
}
