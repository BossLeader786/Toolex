package com.toolex.pdf.engine

import com.toolex.pdf.model.FitMode
import com.toolex.pdf.model.MarginSize
import com.toolex.pdf.model.Orientation
import com.toolex.pdf.model.PageSize
import com.toolex.pdf.model.PdfOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the page math to the values the original Flask app produced.
 * Run with: ./gradlew :app:testDebugUnitTest
 */
class PageGeometryTest {

    private fun options(
        size: PageSize = PageSize.A4,
        orientation: Orientation = Orientation.AUTO,
        fit: FitMode = FitMode.FIT,
        margins: MarginSize = MarginSize.NONE,
    ) = PdfOptions(pageSize = size, orientation = orientation, fit = fit, margins = margins)

    @Test
    fun `A4 auto keeps portrait photo portrait`() {
        val layout = PageGeometry.layout(options(), 1200, 1600)
        assertEquals(595.276, layout.pageWidth, 0.001)
        assertEquals(841.89, layout.pageHeight, 0.001)
    }

    @Test
    fun `A4 auto turns landscape photo`() {
        val layout = PageGeometry.layout(options(), 2000, 1000)
        assertEquals(841.89, layout.pageWidth, 0.001)
        assertEquals(595.276, layout.pageHeight, 0.001)
    }

    @Test
    fun `forced orientation ignores the photo`() {
        val landscape = PageGeometry.layout(options(orientation = Orientation.LANDSCAPE), 1200, 1600)
        assertEquals(841.89, landscape.pageWidth, 0.001)
        val portrait = PageGeometry.layout(options(orientation = Orientation.PORTRAIT), 2000, 1000)
        assertEquals(841.89, portrait.pageHeight, 0.001)
    }

    @Test
    fun `original page follows the photo at 96 dpi`() {
        val layout = PageGeometry.layout(options(size = PageSize.ORIGINAL), 1200, 1600)
        assertEquals(1200 * 72.0 / 96.0, layout.pageWidth, 0.001)
        assertEquals(1600 * 72.0 / 96.0, layout.pageHeight, 0.001)
    }

    @Test
    fun `letter and A5 dimensions`() {
        assertEquals(612.0, PageGeometry.layout(options(size = PageSize.LETTER), 10, 10).pageWidth, 0.001)
        assertEquals(419.528, PageGeometry.layout(options(size = PageSize.A5), 10, 10).pageWidth, 0.001)
    }

    @Test
    fun `fit keeps the whole photo inside the printable area`() {
        val opts = options(margins = MarginSize.MEDIUM)
        val layout = PageGeometry.layout(opts, 2000, 1000)
        val place = PageGeometry.placement(opts, layout, 2000, 1000, 200)

        assertEquals(0, place.srcLeft)
        assertEquals(2000, place.srcWidth)
        assertTrue(place.drawWidthPt <= layout.areaWidth + 0.01)
        assertTrue(place.drawHeightPt <= layout.areaHeight + 0.01)
        assertTrue(place.offsetXpt >= layout.margin - 0.01)
        assertTrue(place.offsetYpt >= layout.margin - 0.01)
    }

    @Test
    fun `fill crops the photo instead of leaving white bars`() {
        val opts = options(fit = FitMode.FILL)
        val layout = PageGeometry.layout(opts, 2000, 1000)
        val place = PageGeometry.placement(opts, layout, 2000, 1000, 200)

        assertTrue("source must be cropped", place.srcWidth < 2000)
        assertEquals(1000, place.srcHeight)
        // drawn area matches the page aspect ratio
        val pageRatio = layout.areaWidth / layout.areaHeight
        val drawRatio = place.drawWidthPt / place.drawHeightPt
        assertEquals(pageRatio, drawRatio, 0.02)
    }

    @Test
    fun `small photo is never upscaled on Original page size`() {
        val opts = options(size = PageSize.ORIGINAL)
        val layout = PageGeometry.layout(opts, 400, 300)
        val place = PageGeometry.placement(opts, layout, 400, 300, 200)
        // 400 px at 96 dpi = 300 pt, and the raster never exceeds the source.
        assertEquals(300.0, place.drawWidthPt, 0.01)
        assertEquals(225.0, place.drawHeightPt, 0.01)
        assertTrue(place.rasterWidth <= 400)
        assertTrue(place.rasterHeight <= 300)
    }

    @Test
    fun `picture always respects the margin box`() {
        for (size in PageSize.entries) {
            for (orientation in Orientation.entries) {
                for (fit in FitMode.entries) {
                    for (iv in listOf(2000 to 1000, 1200 to 1600, 300 to 900)) {
                        val opts = options(
                            size = size,
                            orientation = orientation,
                            fit = fit,
                            margins = MarginSize.MEDIUM,
                        )
                        val layout = PageGeometry.layout(opts, iv.first, iv.second)
                        val place = PageGeometry.placement(opts, layout, iv.first, iv.second, 200)
                        val m = opts.margins.points
                        assertTrue(
                            "left margin $size/$orientation/$fit",
                            place.offsetXpt >= m - 0.01,
                        )
                        assertTrue("top margin", place.offsetYpt >= m - 0.01)
                        assertTrue(
                            "right margin",
                            place.offsetXpt + place.drawWidthPt <= layout.pageWidth - m + 0.01,
                        )
                        assertTrue(
                            "bottom margin",
                            place.offsetYpt + place.drawHeightPt <= layout.pageHeight - m + 0.01,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `picture never sticks out of the page even with margins on Original size`() {
        val opts = options(size = PageSize.ORIGINAL, margins = MarginSize.LARGE)
        val layout = PageGeometry.layout(opts, 1200, 1600)
        val place = PageGeometry.placement(opts, layout, 1200, 1600, 200)
        assertTrue(place.offsetXpt + place.drawWidthPt <= layout.pageWidth + 0.01)
        assertTrue(place.offsetYpt + place.drawHeightPt <= layout.pageHeight + 0.01)
        assertTrue(place.rasterWidth <= 1200)
        assertTrue(place.rasterHeight <= 1600)
    }

    @Test
    fun `lowering dpi never increases the raster size`() {
        val opts = options()
        val layout = PageGeometry.layout(opts, 3024, 4032)
        val sizes = listOf(240, 200, 150, 96, 80).map { dpi ->
            PageGeometry.placement(opts, layout, 3024, 4032, dpi).rasterWidth
        }
        assertEquals(sizes, sizes.sortedDescending())
    }

    @Test
    fun `every page size and orientation produces a valid box`() {
        for (size in PageSize.entries) {
            for (orientation in Orientation.entries) {
                for (fit in FitMode.entries) {
                    val opts = options(size = size, orientation = orientation, fit = fit)
                    val layout = PageGeometry.layout(opts, 1200, 1600)
                    val place = PageGeometry.placement(opts, layout, 1200, 1600, 144)
                    assertTrue(layout.pageWidth > 0)
                    assertTrue(layout.pageHeight > 0)
                    assertTrue(place.rasterWidth >= 1 && place.rasterHeight >= 1)
                    assertTrue(place.drawWidthPt > 0 && place.drawHeightPt > 0)
                }
            }
        }
    }
}
