package com.toolex.pdf.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.toolex.pdf.model.PdfException
import java.io.File

/** Share + "Save to Downloads" plumbing, kept out of the composables. */
class ExportHelper(private val context: Context) {

    fun share(file: File, mime: String) {
        val uri = uriFor(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share karo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(file: File, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(file), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { throw PdfException("Is file ke liye koi app nahi mila.") }
    }

    /**
     * Copies [file] into the public Downloads folder.
     * API 29+: MediaStore (no permission). Below: legacy file copy, which works
     * with WRITE_EXTERNAL_STORAGE on the older OS versions where it applies.
     */
    fun saveToDownloads(file: File, mime: String): String {
        val displayName = uniqueDownloadName(file.name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(file, mime, displayName)
            displayName
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val target = File(dir, displayName)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            target.absolutePath
        }
    }

    // --- API 29+ -------------------------------------------------------------
    // Split out so lint sees the version guard in one place.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(file: File, mime: String, displayName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw PdfException("Downloads me file nahi ban saki.")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: throw PdfException("Downloads me likh nahi paye.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun uniqueDownloadName(name: String): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "")
        if (!File(dir, name).exists()) return name
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "${stem}_$i" else "${stem}_$i.$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }
}
