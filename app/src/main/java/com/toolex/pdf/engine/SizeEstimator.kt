package com.toolex.pdf.engine

import com.toolex.pdf.model.PdfException
import com.toolex.pdf.model.Progress
import com.toolex.pdf.model.SourceImage
import com.toolex.pdf.model.PdfOptions
import com.toolex.pdf.model.ImageInfo
import com.toolex.pdf.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** Result of the "show both numbers before converting" step. */
data class SizeEstimate(
    val sourceBytes: Long,
    val estimatedPdfBytes: Long,
    val pageCount: Int,
    /** Suggested ceilings shown as dropdown chips (replaces the web app's % list). */
    val suggestions: List<Long>,
)

/**
 * Kotlin answer to `/api/analyze`.
 *
 * The web app built a throwaway PDF at 200 DPI to estimate. On a phone that
 * would mean two full render passes, so instead we *measure* the real output:
 * each page is JPEG-encoded once at the normal rung and the exact bytes are
 * summed. On most images JPEG is ~90% of a PDF's size, and the remaining
 * overhead is tiny, so the estimate lands within a few percent — then the
 * conversion itself re-uses the same encoder.
 */
class SizeEstimator(
    private val loader: ImageLoader,
    private val encoder: PageEncoder = PageEncoder(loader),
    private val fileStore: FileStore,
) {

    suspend fun estimate(
        sources: List<SourceImage>,
        options: PdfOptions,
        onProgress: (Progress) -> Unit = {},
    ): SizeEstimate = withContext(Dispatchers.IO) {
        val scratch = fileStore.newScratchDir()
        try {
            val infos = mutableListOf<Pair<SourceImage, ImageInfo>>()
            sources.forEachIndexed { index, source ->
                currentCoroutineContext().ensureActive()
                try {
                    infos += source to loader.inspect(android.net.Uri.parse(source.uri))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Bad file: excluded from the estimate, reported by convert().
                }
                onProgress(Progress((10.0 * (index + 1) / sources.size).toInt(), "Sizes padh rahe hain…"))
            }
            if (infos.isEmpty()) throw PdfException("Koi image readable nahi hai.")

            // Hard cap, same as the web app's 200 MB request limit.
            val sourceBytes = sources.sumOf { it.byteCount }
            if (sourceBytes > PageGeometry.MAX_INPUT_BYTES) {
                throw PdfException(
                    "Total ${FileStore.formatBytes(sourceBytes)} hai — maximum " +
                        FileStore.formatBytes(PageGeometry.MAX_INPUT_BYTES) + " allowed.",
                )
            }

            // For a very long job, measuring every page would cost as much as the
            // conversion itself. Measure a representative sample and scale up.
            val sampleSize = if (infos.size <= SAMPLE_LIMIT) infos.size else SAMPLE_LIMIT
            val step = if (sampleSize == infos.size) 1 else (infos.size / sampleSize).coerceAtLeast(1)
            val sample = (0 until infos.size step step).take(sampleSize).map { infos[it] }

            var jpegTotal = 0L
            sample.forEachIndexed { index, (source, info) ->
                currentCoroutineContext().ensureActive()
                val page = encoder.encode(source.uri, info, options, Rungs.NORMAL, scratch, index)
                jpegTotal += page.jpeg.length()
                page.jpeg.delete()
                onProgress(
                    Progress(
                        (10 + 80.0 * (index + 1) / sample.size).toInt(),
                        "Estimate ${index + 1}/${sample.size}",
                    ),
                )
            }
            if (sample.size < infos.size) {
                jpegTotal = (jpegTotal.toDouble() * infos.size / sample.size).toLong()
            }

            // Per page: image stream + dictionary + content stream + page object.
            val perPageOverhead = 900L
            var estimated = jpegTotal + perPageOverhead * infos.size + 1400L
            if (options.outputMode == com.toolex.pdf.model.OutputMode.SEPARATE && infos.size > 1) {
                // Each PDF needs its own header/xref/trailer.
                estimated += 1200L * infos.size
            }

            SizeEstimate(
                sourceBytes = sourceBytes,
                estimatedPdfBytes = estimated,
                pageCount = infos.size,
                suggestions = suggestTargets(estimated),
            )
        } finally {
            runCatching { scratch.deleteRecursively() }
        }
    }

    /**
     * Ceilings that make sense for *this* job, smallest first. Mirrors the web
     * app's "dropdown calculated from the estimate, with Custom for manual".
     */
    private fun suggestTargets(estimated: Long): List<Long> {
        val raw = listOf(0.25, 0.4, 0.55, 0.7, 0.85, 1.0).map { (estimated * it).toLong() }
        val floor = PageGeometry.MIN_TARGET_BYTES
        return raw.map { it.coerceAtLeast(floor) }.distinct().sorted()
    }

    private companion object {
        /** Jobs bigger than this are estimated from a sample. */
        const val SAMPLE_LIMIT = 40
    }
}
