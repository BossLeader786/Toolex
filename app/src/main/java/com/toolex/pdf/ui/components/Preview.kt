package com.toolex.pdf.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.toolex.pdf.model.FitMode
import com.toolex.pdf.model.ImageInfo
import com.toolex.pdf.model.PdfOptions

/**
 * Live page preview: draws the chosen page box, the margin guide and where the
 * photo will sit. It is vector-only (no bitmap), so it updates instantly when
 * a setting changes — this is the thing that makes the options understandable.
 */
@Composable
fun PagePreview(
    options: PdfOptions,
    image: ImageInfo?,
    modifier: Modifier = Modifier,
) {
    val pageW: Double
    val pageH: Double
    if (image != null) {
        val (w, h) = options.pagePoints(image.width, image.height)
        pageW = w
        pageH = h
    } else {
        val (w, h) = options.pagePoints(1000, 1400)
        pageW = w
        pageH = h
    }

    // Colours must be read in a @Composable scope, never inside DrawScope.
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val guideColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val photoFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val photoStroke = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            val pageAspect = (pageW / pageH).toFloat()
            val canvasAspect = size.width / size.height
            val drawW = if (canvasAspect > pageAspect) size.height * pageAspect else size.width
            val drawH = if (canvasAspect > pageAspect) size.height else size.width / pageAspect
            val left = (size.width - drawW) / 2
            val top = (size.height - drawH) / 2

            // paper — always white, in light and dark mode (a PDF page is white)
            drawRect(Color.White, Offset(left, top), Size(drawW, drawH))
            drawRect(shadowColor, Offset(left, top), Size(drawW, drawH), style = Stroke(1.5f))

            val scale = (drawW / pageW).toFloat()

            // margin guides
            val m = options.margins.points.toFloat() * scale
            if (m > 1f) {
                drawRect(
                    guideColor,
                    Offset(left + m, top + m),
                    Size(drawW - 2 * m, drawH - 2 * m),
                    style = Stroke(1f),
                )
            }

            // photo placeholder, positioned exactly like the engine will place it
            if (image != null) {
                val areaW = pageW - 2 * options.margins.points
                val areaH = pageH - 2 * options.margins.points
                var drawWidthPt = areaW
                var drawHeightPt = areaH
                if (options.fit == FitMode.FIT) {
                    val imgAspect = image.width.toDouble() / image.height
                    if (imgAspect > areaW / areaH) {
                        drawHeightPt = areaW / imgAspect
                    } else {
                        drawWidthPt = areaH * imgAspect
                    }
                }
                val x = left + ((pageW - drawWidthPt) / 2 * scale).toFloat()
                val y = top + ((pageH - drawHeightPt) / 2 * scale).toFloat()
                drawRect(
                    photoFill,
                    Offset(x, y),
                    Size((drawWidthPt * scale).toFloat(), (drawHeightPt * scale).toFloat()),
                )
                drawRect(
                    photoStroke,
                    Offset(x, y),
                    Size((drawWidthPt * scale).toFloat(), (drawHeightPt * scale).toFloat()),
                    style = Stroke(1.2f),
                )
            }
        }
    }
}
