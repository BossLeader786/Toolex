package com.toolex.pdf.engine

import com.toolex.pdf.model.PdfException
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a stored (no re-compression) ZIP of already-compressed PDFs.
 * PDFs are mostly JPEG data, so deflating again just burns CPU for ~0% gain.
 */
object ZipWriter {

    fun write(zipFile: File, entries: List<Pair<String, File>>) {
        if (entries.isEmpty()) throw PdfException("ZIP ke liye koi PDF nahi bani.")
        val used = mutableSetOf<String>()
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), 1 shl 16)).use { zip ->
                entries.forEach { (rawName, file) ->
                    val name = uniqueEntry(rawName, used)
                    val entry = ZipEntry(name).apply {
                        method = ZipEntry.STORED
                        size = file.length()
                        compressedSize = file.length()
                        crc = crc32(file)
                        time = file.lastModified()
                    }
                    zip.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zip, 1 shl 16) }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            zipFile.delete()
            throw PdfException("ZIP banane me problem: ${e.message ?: "unknown"}", e)
        }
    }

    private fun uniqueEntry(name: String, used: MutableSet<String>): String {
        var candidate = name
        var index = 2
        while (!used.add(candidate)) {
            candidate = name.removeSuffix(".pdf") + "_$index.pdf"
            index++
        }
        return candidate
    }

    private fun crc32(file: File): Long {
        val crc = java.util.zip.CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }
}
