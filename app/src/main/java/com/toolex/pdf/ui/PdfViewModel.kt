package com.toolex.pdf.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toolex.pdf.engine.ImageLoader
import com.toolex.pdf.engine.PageEncoder
import com.toolex.pdf.engine.PageGeometry
import com.toolex.pdf.engine.PdfConverter
import com.toolex.pdf.engine.SizeEstimator
import com.toolex.pdf.model.MarginSize
import com.toolex.pdf.model.Orientation
import com.toolex.pdf.model.FitMode
import com.toolex.pdf.model.OutputMode
import com.toolex.pdf.model.PageSize
import com.toolex.pdf.model.PdfException
import com.toolex.pdf.model.PdfOptions
import com.toolex.pdf.model.SizeMode
import com.toolex.pdf.model.SourceImage
import com.toolex.pdf.util.ExportHelper
import com.toolex.pdf.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single source of truth for the screen. Owns the engine, so the composables
 * stay dumb and recomposition-safe.
 */
class PdfViewModel(app: Application) : AndroidViewModel(app) {

    private val loader = ImageLoader(app)
    private val fileStore = FileStore(app)
    private val encoder = PageEncoder(loader)
    private val converter = PdfConverter(loader, fileStore, encoder)
    private val estimator = SizeEstimator(loader, encoder, fileStore)
    private val exporter = ExportHelper(app)

    private val _state = MutableStateFlow(PdfUiState())
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var estimateJob: Job? = null

    // ------------------------------------------------------------------ images

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val existing = _state.value.images.map { it.uri }.toSet()
        val incoming = uris
            .map { it.toString() }
            .distinct()
            .filterNot { it in existing }
        if (incoming.isEmpty()) return

        val room = MAX_IMAGES - _state.value.images.size
        if (room <= 0) {
            _state.update { it.copy(error = "Ek baar me maximum $MAX_IMAGES images add kar sakte ho.") }
            return
        }
        val accepted = incoming.take(room)
        val added = accepted.map { uriString ->
            val uri = Uri.parse(uriString)
            SourceImage(uri = uriString, displayName = loader.displayName(uri), byteCount = 0L)
        }
        // Metadata (name + size) needs disk/ContentResolver reads.
        viewModelScope.launch(Dispatchers.IO) {
            val filled = added.map { image ->
                runCatching { image.copy(byteCount = loader.byteSize(Uri.parse(image.uri))) }
                    .getOrDefault(image)
            }
            _state.update { s ->
                val byUri = filled.associateBy { it.uri }
                s.copy(images = s.images.map { byUri[it.uri] ?: it })
            }
        }
        val overflow = incoming.size - accepted.size
        _state.update {
            it.copy(
                images = it.images + added,
                result = null,
                error = if (overflow > 0) "Sirf $MAX_IMAGES images ek job me. $overflow chhod diye." else it.error,
            )
        }
        estimateNow()
    }

    /** Handles a SEND / SEND_MULTIPLE intent coming from the gallery or a file manager. */
    fun consumeShareIntent(intent: Intent?) {
        if (intent == null) return
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
            )
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            ) ?: emptyList()
            else -> return
        }
        addImages(uris)
    }

    fun removeImage(uri: String) {
        _state.update { s -> s.copy(images = s.images.filterNot { it.uri == uri }, result = null) }
        estimateNow()
    }

    fun moveImage(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.images.indices || to !in s.images.indices || from == to) return@update s
            val list = s.images.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            s.copy(images = list, result = null)
        }
    }

    fun clearImages() {
        _state.update { it.copy(images = emptyList(), result = null, estimate = null) }
    }

    // ----------------------------------------------------------------- options

    fun setPageSize(size: PageSize) = updateOptions { it.copy(pageSize = size) }
    fun setOrientation(orientation: Orientation) = updateOptions { it.copy(orientation = orientation) }
    fun setFit(fit: FitMode) = updateOptions { it.copy(fit = fit) }
    fun setMargins(margins: MarginSize) = updateOptions { it.copy(margins = margins) }
    fun setOutputMode(mode: OutputMode) = updateOptions { it.copy(outputMode = mode) }

    private fun updateOptions(block: (PdfOptions) -> PdfOptions) {
        _state.update { it.copy(options = block(it.options), result = null) }
        estimateNow()
    }

    fun setSizeNormal() {
        _state.update { it.copy(sizeMode = SizeMode.NORMAL, customTargetBytes = null, result = null) }
    }

    fun setCustomTarget(bytes: Long) {
        val clamped = bytes.coerceIn(PageGeometry.MIN_TARGET_BYTES, PageGeometry.MAX_TARGET_BYTES)
        _state.update { it.copy(sizeMode = SizeMode.CUSTOM, customTargetBytes = clamped, result = null) }
    }

    fun openSheet(sheet: Sheet?) = _state.update { it.copy(sheet = sheet) }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissResult() = _state.update { it.copy(result = null) }

    // ---------------------------------------------------------------- estimate

    fun estimateNow() {
        val snapshot = _state.value
        if (snapshot.images.isEmpty()) {
            _state.update { it.copy(estimate = null, estimating = false) }
            return
        }
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            _state.update { it.copy(estimating = true) }
            // Debounce: tapping through page sizes should not queue five render
            // passes; the last choice wins.
            delay(450)
            try {
                val estimate = estimator.estimate(snapshot.images, snapshot.options)
                _state.update { s ->
                    // Keep a custom target that the user picked; never lower it silently.
                    s.copy(estimate = estimate, estimating = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(estimating = false) }
            }
        }
    }

    // ---------------------------------------------------------------- convert

    fun convert() {
        val snapshot = _state.value
        if (snapshot.images.isEmpty()) {
            _state.update { it.copy(error = "Pehle kam se kam ek image add karo.") }
            return
        }
        if (snapshot.running) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(running = true, progress = com.toolex.pdf.model.Progress(0, "Shuru…"), result = null, error = null) }
            try {
                val result = converter.convert(
                    sources = snapshot.images,
                    options = snapshot.options,
                    sizeMode = snapshot.sizeMode,
                    targetBytes = if (snapshot.sizeMode == SizeMode.CUSTOM) snapshot.customTargetBytes else null,
                    onProgress = { progress ->
                        _state.update { it.copy(progress = progress) }
                    },
                )
                _state.update {
                    it.copy(
                        running = false,
                        progress = null,
                        result = result,
                        skippedNotice = result.skipped.map { f -> "${f.name}: ${f.reason}" },
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update { it.copy(running = false, progress = null) }
                throw e
            } catch (e: PdfException) {
                _state.update { it.copy(running = false, progress = null, error = e.message) }
            } catch (e: OutOfMemoryError) {
                _state.update {
                    it.copy(
                        running = false,
                        progress = null,
                        error = "Memory kam pad gayi. Kam images ke saath try karo, ya chhoti page size chuno.",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(running = false, progress = null, error = "Conversion nahi hui: ${e.message ?: "unknown error"}")
                }
            }
        }
    }

    fun cancelConversion() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, progress = null) }
    }

    // ------------------------------------------------------------------ export

    fun shareResult() {
        val result = _state.value.result ?: return
        runCatching { exporter.share(File(result.outputPath), result.mimeType) }
            .onFailure { _state.update { s -> s.copy(error = "Share nahi ho paya.") } }
    }

    fun openResult() {
        val result = _state.value.result ?: return
        runCatching { exporter.open(File(result.outputPath), result.mimeType) }
            .onFailure { e -> _state.update { s -> s.copy(error = e.message ?: "Open nahi ho paya.") } }
    }

    fun saveResultToDownloads(onSaved: (String) -> Unit) {
        val result = _state.value.result ?: return
        viewModelScope.launch {
            try {
                val location = exporter.saveToDownloads(File(result.outputPath), result.mimeType)
                onSaved(location)
            } catch (e: Exception) {
                _state.update { s -> s.copy(error = "Download folder me save nahi ho paya: ${e.message}") }
            }
        }
    }

    fun useEstimateAsTarget() {
        val estimate = _state.value.estimate ?: return
        setCustomTarget((estimate.estimatedPdfBytes * 0.75).toLong())
    }

    override fun onCleared() {
        super.onCleared()
        fileStore.purgeCache()
    }

    companion object {
        /** Same ceiling as the web app. */
        const val MAX_IMAGES = 100
        const val MAX_TOTAL_BYTES = PageGeometry.MAX_INPUT_BYTES
    }
}
