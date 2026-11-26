/*
 * JVM harness: runs the REAL Toolex engine (PageGeometry + PdfBuilder) with fake
 * page bitmaps and prints the resulting PDF paths, so the output can be checked
 * with an independent PDF reader.
 *
 * Not part of the app. Built by tools/verify_engine_kotlin.sh
 */
package harness

import com.toolex.pdf.engine.PageGeometry
import com.toolex.pdf.engine.PdfBuilder
import com.toolex.pdf.model.FitMode
import com.toolex.pdf.model.MarginSize
import com.toolex.pdf.model.Orientation
import com.toolex.pdf.model.PageSize
import com.toolex.pdf.model.PdfOptions
import java.io.File
import java.io.FileOutputStream

private fun jpegPlaceholder(file: File, tag: String) {
    // A "JPEG" of known content: enough to verify stream lengths and offsets.
    val bytes = (tag + ":" + "x".repeat(1000)).toByteArray()
    FileOutputStream(file).use { it.write(bytes) }
}

private fun buildPdf(
    out: File,
    images: List<Pair<Int, Int>>,
    options: PdfOptions,
    dpi: Int,
    caption: String,
): Long {
    val builder = PdfBuilder(out)
    builder.open(caption)
    images.forEachIndexed { index, (w, h) ->
        val layout = PageGeometry.layout(options, w, h)
        val place = PageGeometry.placement(options, layout, w, h, dpi)
        val jpeg = File(out.parentFile, "tmp_$index.jpg")
        jpegPlaceholder(jpeg, caption)
        builder.addPage(
            pageWidthPt = place.pageWidthPt,
            pageHeightPt = place.pageHeightPt,
            imageWidthPx = place.rasterWidth,
            imageHeightPx = place.rasterHeight,
            drawXpt = place.offsetXpt,
            drawYpt = place.offsetYpt,
            drawWidthPt = place.drawWidthPt,
            drawHeightPt = place.drawHeightPt,
            jpegFile = jpeg,
        )
        // Print machine-readable facts for the Python checker.
        println(
            "PAGE caption=$caption index=$index pageW=${place.pageWidthPt} pageH=${place.pageHeightPt} " +
                "offX=${place.offsetXpt} offY=${place.offsetYpt} drawW=${place.drawWidthPt} " +
                "drawH=${place.drawHeightPt} raster=${place.rasterWidth}x${place.rasterHeight} " +
                "src=${place.srcLeft},${place.srcTop},${place.srcWidth},${place.srcHeight} dpi=${place.effectiveDpi}",
        )
        jpeg.delete()
    }
    builder.finish()
    println("FILE caption=$caption path=${out.absolutePath} bytes=${out.length()} xref=${builder.bytesWritten}")
    return out.length()
}

fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: ".").apply { mkdirs() }
    val images = listOf(1200 to 1600, 2000 to 1000, 600 to 600)

    val normal = buildPdf(
        File(dir, "merged_A4_auto_fit.pdf"),
        images.take(2),
        PdfOptions(),
        200,
        "merged",
    )

    buildPdf(
        File(dir, "landscape_fill_margins.pdf"),
        images,
        PdfOptions(
            pageSize = PageSize.A5,
            orientation = Orientation.LANDSCAPE,
            fit = FitMode.FILL,
            margins = MarginSize.MEDIUM,
        ),
        144,
        "landscapefill",
    )

    buildPdf(
        File(dir, "original_portrait.pdf"),
        listOf(3024 to 4032),
        PdfOptions(pageSize = PageSize.ORIGINAL, orientation = Orientation.PORTRAIT),
        240,
        "original",
    )

    // An empty document must still be a valid (0-page) PDF — guards the writer.
    val empty = PdfBuilder(File(dir, "empty.pdf"))
    empty.open("empty")
    empty.finish()
    println("FILE caption=empty path=${File(dir, "empty.pdf").absolutePath} bytes=${File(dir, "empty.pdf").length()}")
    println("SUMMARY normalPdfBytes=$normal")
}
