package com.toolex.pdf.util

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * All files stay inside the app's private storage (no runtime permissions).
 * The user decides what to keep via "Save to Downloads" / "Share".
 */
class FileStore(private val context: Context) {

    private val cacheRoot: File
        get() = File(context.cacheDir, "toolex").apply { mkdirs() }

    private val outputRoot: File
        get() = File(context.filesDir, "outputs").apply { mkdirs() }

    fun newScratchDir(): File {
        val dir = File(cacheRoot, "job_${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }

    fun newOutputFile(name: String): File {
        // A fresh output means older results are no longer reachable from the UI.
        purgeOutputs(keep = name)
        return File(outputRoot, name).apply { parentFile?.mkdirs() }
    }

    fun purgeOutputs(keep: String? = null) {
        outputRoot.listFiles()?.forEach { file ->
            if (keep == null || file.name != keep) runCatching { file.delete() }
        }
    }

    fun purgeCache() {
        runCatching { cacheRoot.deleteRecursively() }
    }

    companion object {
        private val UNSAFE = Regex("[^A-Za-z0-9._-]+")

        /** Same sanitising as `safe_name()` in the Flask app. */
        fun safeBaseName(rawName: String): String {
            val withoutPath = rawName.substringAfterLast('/').substringAfterLast('\\')
            val stem = withoutPath.substringBeforeLast('.', withoutPath)
            val cleaned = UNSAFE.replace(stem, "_").trim('.', '_')
            return (cleaned.ifEmpty { "image" }).take(70)
        }

        /** "1.4 MB" style, used for both source and output sizes. */
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
            val mb = kb / 1024.0
            return if (mb < 100) String.format(Locale.US, "%.1f MB", mb)
            else String.format(Locale.US, "%.0f MB", mb)
        }
    }
}
