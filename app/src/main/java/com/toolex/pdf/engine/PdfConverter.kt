package com.toolex.pdf.engine

import com.toolex.pdf.model.ConversionResult
import com.toolex.pdf.model.ImageInfo
import com.toolex.pdf.model.OutputMode
import com.toolex.pdf.model.PdfException
import com.toolex.pdf.model.PdfOptions
import com.toolex.pdf.model.Progress
import com.toolex.pdf.model.SizeMode
import com.toolex.pdf.model.SkippedFile
import com.toolex.pdf.model.SourceImage
import com.toolex.pdf.model.TargetStatus
import com.toolex.pdf.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.min

/**
 * Orchestrates a whole job — the Kotlin answer to `/api/convert`:
 *
 *  - merged  -> one multi-page PDF
 *  - separate-> one PDF per image, packed into a ZIP
 *  - normal  -> 200 DPI / q88
 *  - custom  -> walk the quality ladder, keep the best version under the cap
 *
 * Runs on [Dispatchers.IO], cooperatively cancellable, page by page on disk so
 * memory stays flat no matter how many photos are queued.
 */
class PdfConverter(
    private val loader: ImageLoader,
    private val fileStore: FileStore,
    private val encoder: PageEncoder = PageEncoder(loader),
) {

    suspend fun convert(
        sources: List<SourceImage>,
        options: PdfOptions,
        sizeMode: SizeMode,
        targetBytes: Long?,
        onProgress: (Progress) -> Unit,
    ): ConversionResult = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) throw PdfException("Pehle kam se kam ek image add karo.")
        if (sources.size > MAX_IMAGES) throw PdfException("Ek baar me maximum $MAX_IMAGES images.")
        // 200 MB guard, identical to MAX_CONTENT_LENGTH in the Flask app. Only
        // enforced when we actually know the sizes (share intents may not).
        val inputBytes = sources.sumOf { it.byteCount }
        if (inputBytes > PageGeometry.MAX_INPUT_BYTES) {
            throw PdfException(
                "Total ${FileStore.formatBytes(inputBytes)} hai — maximum " +
                    "${FileStore.formatBytes(PageGeometry.MAX_INPUT_BYTES)} allowed.",
            )
        }
        val started = System.currentTimeMillis()
        val scratch = fileStore.newScratchDir()
        val skipped = mutableListOf<SkippedFile>()

        try {
            // Read headers first: bad files are skipped gracefully instead of
            // killing the whole job (same philosophy as the web version).
            val valid = mutableListOf<Pair<SourceImage, ImageInfo>>()
            sources.forEach { source ->
                currentCoroutineContext().ensureActive()
                try {
                    valid += source to loader.inspect(android.net.Uri.parse(source.uri))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    skipped += SkippedFile(source.displayName, e.message ?: "Image kharab hai")
                }
            }
            if (valid.isEmpty()) throw PdfException("Koi bhi image read nahi ho payi.")

            val cap = if (sizeMode == SizeMode.CUSTOM) targetBytes else null

            if (options.outputMode == OutputMode.MERGED || valid.size == 1) {
                convertMerged(valid, options, cap, scratch, skipped, started, onProgress)
            } else {
                convertSeparate(valid, options, cap, scratch, skipped, started, onProgress)
            }
        } finally {
            runCatching { scratch.deleteRecursively() }
        }
    }

    // ------------------------------------------------------------------ merged

    private suspend fun convertMerged(
        items: List<Pair<SourceImage, ImageInfo>>,
        options: PdfOptions,
        cap: Long?,
        scratch: File,
        skipped: MutableList<SkippedFile>,
        started: Long,
        onProgress: (Progress) -> Unit,
    ): ConversionResult {
        val base = FileStore.safeBaseName(items.first().first.displayName)
        val fileName = if (items.size > 1) "${base}_merged.pdf" else "$base.pdf"
        val output = fileStore.newOutputFile(fileName)

        val ladder = if (cap != null) Rungs.ladder(items.size) else listOf(Rungs.NORMAL)
        var best: Pair<File, Int>? = null
        var chosenPages = items.size
        var attemptIndex = 0

        while (attemptIndex < ladder.size) {
            val rung = ladder[attemptIndex]
            currentCoroutineContext().ensureActive()
            val attempt = File(scratch, "merged_${rung.dpi}.pdf")
            val pages = renderPages(items, options, rung, scratch) { done, total ->
                // First attempt owns 3%..80%; cheaper retries share the rest so
                // the bar always moves forward.
                val span = if (attemptIndex == 0) 77.0 else 18.0 / (ladder.size - 1).coerceAtLeast(1)
                val start = if (attemptIndex == 0) 3.0 else 80.0 + span * (attemptIndex - 1)
                onProgress(
                    Progress(
                        (start + span * done.toDouble() / total).toInt().coerceIn(0, 99),
                        if (attemptIndex == 0) "Page $done/$total • ${rung.dpi} DPI"
                        else "Quality adjust • ${rung.dpi} DPI",
                    ),
                )
            }
            writePdf(attempt, FileStore.safeBaseName(fileName), pages)
            chosenPages = pages.size

            if (best == null || attempt.length() < best!!.first.length()) {
                best?.first?.delete()
                best = attempt to rung.dpi
            } else {
                attempt.delete()
            }

            if (cap == null || attempt.length() <= cap) {
                // The first rung that fits is the highest-quality one — done.
                return publish(best!!, output, fileName, "application/pdf", chosenPages, targetMet = true, cap, started, skipped)
            }
            onProgress(Progress(80, "Thoda bada hai — quality kam kar rahe hain…"))
            attemptIndex++
        }

        // Even the lowest rung did not fit: deliver the smallest, honestly flagged.
        onProgress(Progress(96, "Target size nahi mila, sabse chhoti file de rahe hain…"))
        return publish(best!!, output, fileName, "application/pdf", chosenPages, targetMet = false, cap, started, skipped)
    }

    // ---------------------------------------------------------------- separate

    private suspend fun convertSeparate(
        items: List<Pair<SourceImage, ImageInfo>>,
        options: PdfOptions,
        cap: Long?,
        scratch: File,
        skipped: MutableList<SkippedFile>,
        started: Long,
        onProgress: (Progress) -> Unit,
    ): ConversionResult {
        onProgress(Progress(2, "Har image ka PDF ban raha hai…"))

        val usedNames = mutableMapOf<String, Int>()
        val built = mutableListOf<Pair<String, File>>()

        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val name = uniqueName(FileStore.safeBaseName(item.first.displayName) + ".pdf", usedNames)
            val file = File(scratch, "sep_$index.pdf")
            val pages = renderPages(listOf(item), options, Rungs.NORMAL, scratch) { _, _ -> }
            writePdf(file, name.removeSuffix(".pdf"), pages)
            built += name to file
            onProgress(
                Progress(
                    (5 + 65.0 * (index + 1) / items.size).toInt(),
                    "PDF ${index + 1}/${items.size} ban gaya",
                ),
            )
        }

        // Per-file ceiling: proportional to each PDF's natural size, with the
        // 10 KB floor from the web app.
        val targets: List<Long?> = if (cap == null) {
            built.map { null }
        } else {
            val total = max(1L, built.sumOf { it.second.length() })
            built.map { (_, file) ->
                min(cap, max(PageGeometry.MIN_TARGET_BYTES, cap * file.length() / total))
            }
        }

        val finalFiles = mutableListOf<Pair<String, File>>()
        var allMet = true
        var index = 0
        while (index < built.size) {
            currentCoroutineContext().ensureActive()
            val (name, file) = built[index]
            val target = targets[index]
            val shrunk = if (target != null && file.length() > target) {
                shrinkPdf(items[index], options, file, target, scratch, index)
            } else {
                file
            }
            if (target != null && shrunk.length() > target) allMet = false
            finalFiles += name to shrunk
            onProgress(Progress((72 + 18.0 * (index + 1) / built.size).toInt(), "Size check ${index + 1}/${built.size}"))
            index++
        }

        val zipName = "Toolex_Separate_PDFs.zip"
        val zipFile = fileStore.newOutputFile(zipName)
        onProgress(Progress(94, "ZIP pack ho raha hai…"))
        ZipWriter.write(zipFile, finalFiles)

        return ConversionResult(
            outputPath = zipFile.absolutePath,
            fileName = zipName,
            mimeType = "application/zip",
            byteCount = zipFile.length(),
            pageCount = finalFiles.size,
            targetStatus = when {
                cap == null -> TargetStatus.NOT_REQUESTED
                allMet -> TargetStatus.MET
                else -> TargetStatus.MISSED
            },
            requestedBytes = cap,
            dpiUsed = Rungs.NORMAL.dpi,
            durationMs = System.currentTimeMillis() - started,
            perFileBytes = finalFiles.map { it.second.length() },
            skipped = skipped,
        )
    }

    /** Re-renders one image down the ladder until it fits [cap]. */
    private suspend fun shrinkPdf(
        item: Pair<SourceImage, ImageInfo>,
        options: PdfOptions,
        current: File,
        cap: Long,
        scratch: File,
        index: Int,
    ): File {
        var best = current
        for (rung in Rungs.LADDER) {
            val pages = renderPages(listOf(item), options, rung, scratch) { _, _ -> }
            val attempt = File(scratch, "shrink_${index}_${rung.dpi}.pdf")
            writePdf(attempt, "page", pages)
            if (attempt.length() < best.length()) best = attempt
            if (attempt.length() <= cap) return attempt
        }
        return best
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun renderPages(
        items: List<Pair<SourceImage, ImageInfo>>,
        options: PdfOptions,
        rung: Rung,
        scratch: File,
        onStep: (done: Int, total: Int) -> Unit,
    ): List<EncodedPage> {
        val pages = ArrayList<EncodedPage>(items.size)
        items.forEachIndexed { index, (source, info) ->
            currentCoroutineContext().ensureActive()
            pages += encoder.encode(source.uri, info, options, rung, scratch, index)
            onStep(index + 1, items.size)
        }
        return pages
    }

    private fun publish(
        source: Pair<File, Int>,
        output: File,
        fileName: String,
        mime: String,
        pageCount: Int,
        targetMet: Boolean,
        cap: Long?,
        started: Long,
        skipped: List<SkippedFile>,
    ): ConversionResult {
        val (file, dpi) = source
        if (file.absolutePath != output.absolutePath) {
            file.copyTo(output, overwrite = true)
            file.delete()
        }
        return ConversionResult(
            outputPath = output.absolutePath,
            fileName = fileName,
            mimeType = mime,
            byteCount = output.length(),
            pageCount = pageCount,
            targetStatus = when {
                cap == null -> TargetStatus.NOT_REQUESTED
                targetMet -> TargetStatus.MET
                else -> TargetStatus.MISSED
            },
            requestedBytes = cap,
            dpiUsed = dpi,
            durationMs = System.currentTimeMillis() - started,
            skipped = skipped,
        )
    }

    private fun writePdf(destination: File, title: String, pages: List<EncodedPage>) {
        val builder = PdfBuilder(destination)
        try {
            builder.open(title)
            pages.forEach { page ->
                builder.addPage(
                    pageWidthPt = page.pageWidthPt,
                    pageHeightPt = page.pageHeightPt,
                    imageWidthPx = page.imageWidthPx,
                    imageHeightPx = page.imageHeightPx,
                    drawXpt = page.offsetXpt,
                    drawYpt = page.offsetYpt,
                    drawWidthPt = page.drawWidthPt,
                    drawHeightPt = page.drawHeightPt,
                    jpegFile = page.jpeg,
                )
                page.jpeg.delete()
            }
            builder.finish()
        } catch (e: Exception) {
            builder.abort()
            throw e
        }
    }

    private companion object {
        const val MAX_IMAGES = 100
    }

    private fun uniqueName(base: String, used: MutableMap<String, Int>): String {
        val count = (used[base] ?: 0) + 1
        used[base] = count
        return if (count == 1) base else base.removeSuffix(".pdf") + "_$count.pdf"
    }
}
