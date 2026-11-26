package com.toolex.pdf.model

/** Where an image came from. URI is a content:// / file:// string. */
data class SourceImage(
    val uri: String,
    val displayName: String,
    val byteCount: Long,
)

/** What we need to know about a decoded image for layout decisions. */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String? = null,
)

enum class TargetStatus { MET, MISSED, NOT_REQUESTED }

/** A file the converter had to skip (corrupt, unsupported, too big...). */
data class SkippedFile(val name: String, val reason: String)

/** Result of a finished conversion, ready for the share/save sheet. */
data class ConversionResult(
    val outputPath: String,
    val fileName: String,
    val mimeType: String,
    val byteCount: Long,
    val pageCount: Int,
    val targetStatus: TargetStatus,
    val requestedBytes: Long?,
    val dpiUsed: Int,
    val durationMs: Long,
    /** Bytes per PDF when separate mode was used. */
    val perFileBytes: List<Long> = emptyList(),
    val skipped: List<SkippedFile> = emptyList(),
)

/** Progress callback payload for long conversions. */
data class Progress(val percent: Int, val label: String)

/** Thrown for problems we want to show the user as a friendly message. */
class PdfException(message: String, cause: Throwable? = null) : Exception(message, cause)
