package com.toolex.pdf.engine

import java.io.File

/** One rendered page: the JPEG we will embed, plus its PDF placement. */
data class EncodedPage(
    val pageWidthPt: Double,
    val pageHeightPt: Double,
    val offsetXpt: Double,
    val offsetYpt: Double,
    val drawWidthPt: Double,
    val drawHeightPt: Double,
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val effectiveDpi: Int,
    val jpeg: File,
)
