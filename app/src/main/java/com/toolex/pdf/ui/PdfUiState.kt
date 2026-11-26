package com.toolex.pdf.ui

import com.toolex.pdf.engine.SizeEstimate
import com.toolex.pdf.model.ConversionResult
import com.toolex.pdf.model.MarginSize
import com.toolex.pdf.model.OutputMode
import com.toolex.pdf.model.PdfOptions
import com.toolex.pdf.model.Progress
import com.toolex.pdf.model.SizeMode
import com.toolex.pdf.model.SourceImage

/** Which selector sheet is open (null = none). */
sealed interface Sheet {
    data object PageSize : Sheet
    data object Orientation : Sheet
    data object Fit : Sheet
    data object Margins : Sheet
    data object Output : Sheet
    data object CustomSize : Sheet
}

/** Everything the screen draws, in one immutable snapshot. */
data class PdfUiState(
    val images: List<SourceImage> = emptyList(),
    val options: PdfOptions = PdfOptions(),
    val sizeMode: SizeMode = SizeMode.NORMAL,
    val customTargetBytes: Long? = null,
    val estimate: SizeEstimate? = null,
    val estimating: Boolean = false,
    val running: Boolean = false,
    val progress: Progress? = null,
    val result: ConversionResult? = null,
    val error: String? = null,
    val skippedNotice: List<String> = emptyList(),
    val sheet: Sheet? = null,
) {
    val sourceBytesTotal: Long get() = images.sumOf { it.byteCount }
    val imageCount: Int get() = images.size
    val isSeparate: Boolean get() = options.outputMode == OutputMode.SEPARATE && images.size > 1

    /** Label for the size card's subtitle. */
    val sizeSummary: String
        get() = when {
            sizeMode == SizeMode.NORMAL -> "Normal · 200 DPI, best balance"
            customTargetBytes == null -> "Custom"
            else -> "Custom · max ${com.toolex.pdf.util.FileStore.formatBytes(customTargetBytes)}" +
                if (isSeparate) " per PDF" else ""
        }

    val pageSizeLabel: String get() = options.pageSize.label
    val orientationLabel: String get() = options.orientation.label
    val fitLabel: String get() = options.fit.label
    val marginLabel: String get() = if (options.margins == MarginSize.NONE) "No margin" else options.margins.label
}
