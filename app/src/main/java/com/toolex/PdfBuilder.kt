package com.toolex

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Minimal, dependency-free PDF 1.4 writer for "one photo per page" documents.
 *
 * Each page is `q ... cm /Im0 Do Q` inside a content stream, the photo itself is an embedded JPEG
 * (`/DCTDecode`) — exactly the format Pillow produces when you save JPEG pages as PDF, so readers
 * see the same quality/size profile.
 *
 * Why hand-written instead of PdfDocument: PdfDocument re-encodes images and gives us no byte-size
 * reports, which the "fit under X MB" feature needs.
 */
class PdfBuilder(private val output: File) {

    private val out: OutputStream = BufferedOutputStream(FileOutputStream(output), 1 shl 16)
    private val counter = CountingOutputStream(out)
    private val offsets = mutableMapOf<Int, Long>()
    private var nextObject = 1
    private var pageCount = 0
    private val pageObjectNumbers = mutableListOf<Int>()

    /** Byte size of everything written so far (xref-safe, no extra seeking). */
    val bytesWritten: Long
        get() = counter.count

    private val catalogObj = nextObject++
    private val pagesObj = nextObject++

    fun open(title: String) {
        // Header + a binary comment so tools treat the file as binary.
        counter.write("%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1))
        counter.write(
            byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A)
        )
        val info = nextObject++
        infoObjectNumber = info
        infoTitle = title
    }

    private var finished = false
    private var infoObjectNumber = 0
    private var infoTitle = "Document"

    /** @param jpegBytes already JPEG-encoded page (RGB, 8 bit) */
    fun addPage(
        pageWidthPt: Double,
        pageHeightPt: Double,
        imageWidthPx: Int,
        imageHeightPx: Int,
        drawXpt: Double,
        drawYpt: Double,
        drawWidthPt: Double,
        drawHeightPt: Double,
        jpegFile: File,
        marginPt: Double = 0.0,
    ) {
        val pageObj = nextObject++
        val contentObj = nextObject++
        val imageObj = nextObject++

        // --- Page -------------------------------------------------------
        beginObject(pageObj)
        write(
            "<< /Type /Page /Parent $pagesObj 0 R " +
                "/MediaBox [0 0 ${fmt(pageWidthPt)} ${fmt(pageHeightPt)}] " +
                "/Resources << /XObject << /Im0 $imageObj 0 R >> /ProcSet [/PDF /ImageC] >> " +
                "/Contents $contentObj 0 R >>\nendobj\n"
        )

        // --- Content stream (PDF origin is bottom-left) ------------------
        val bottom = pageHeightPt - drawYpt - drawHeightPt
        val content = buildString {
            append("q\n")
            append(
                "${fmt(marginPt)} ${fmt(marginPt)} ${fmt(pageWidthPt-2*marginPt)} ${fmt(pageHeightPt-2*marginPt)} re W n\n"
            )
            append("${fmt(drawWidthPt)} 0 0 ${fmt(drawHeightPt)} ")
            append("${fmt(drawXpt)} ${fmt(bottom)} cm\n")
            append("/Im0 Do\n")
            append("Q\n")
        }
        val contentBytes = content.toByteArray(Charsets.ISO_8859_1)
        beginObject(contentObj)
        write("<< /Length ${contentBytes.size} >>\nstream\n")
        counter.write(contentBytes)
        write("\nendstream\nendobj\n")

        // --- Image XObject ----------------------------------------------
        beginObject(imageObj)
        write(
            "<< /Type /XObject /Subtype /Image " +
                "/Width $imageWidthPx /Height $imageHeightPx " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 " +
                "/Filter /DCTDecode /Length ${jpegFile.length()} >>\nstream\n"
        )
        jpegFile.inputStream().use { input -> input.copyTo(counter, 1 shl 16) }
        write("\nendstream\nendobj\n")

        pageObjectNumbers += pageObj
        pageCount++
    }

    /** Writes the page tree, catalog, xref table and trailer. */
    fun finish() {
        if (finished) return
        finished = true
        beginObject(pagesObj)
        val kids = pageObjectNumbers.joinToString(" ") { "$it 0 R" }
        write("<< /Type /Pages /Count $pageCount /Kids [$kids] >>\nendobj\n")

        beginObject(catalogObj)
        write("<< /Type /Catalog /Pages $pagesObj 0 R >>\nendobj\n")

        beginObject(infoObjectNumber)
        write(
            "<< /Producer (Toolex PDF) /Creator (Toolex Android) " +
                "/Title (${pdfString(infoTitle)}) /CreationDate (D:${pdfDate()}) >>\nendobj\n"
        )

        val xrefOffset = counter.count
        val maxObj = nextObject - 1
        val xref = StringBuilder()
        xref.append("xref\n0 ${maxObj + 1}\n")
        xref.append("0000000000 65535 f \n")
        for (i in 1..maxObj) {
            val offset = offsets[i] ?: 0L
            xref.append(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        xref.append(
            "trailer\n<< /Size ${maxObj + 1} /Root $catalogObj 0 R /Info $infoObjectNumber 0 R >>\n"
        )
        xref.append("startxref\n$xrefOffset\n%%EOF\n")
        write(xref.toString())

        counter.flush()
        out.flush()
        out.close()
    }

    fun abort() {
        runCatching { out.close() }
        output.delete()
        Unit
    }

    private fun beginObject(number: Int) {
        offsets[number] = counter.count
        write("$number 0 obj\n")
    }

    private fun write(text: String) = counter.write(text.toByteArray(Charsets.ISO_8859_1))

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)

    private fun pdfString(raw: String): String =
        raw.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    /** PDF date is `D:YYYYMMDDHHmmSS` in UTC-ish local time; fine for metadata. */
    private fun pdfDate(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d%02d%02d%02d%02d%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
        )
    }

    /** Counts every byte that reaches the file so xref offsets are exact. */
    private class CountingOutputStream(private val inner: OutputStream) : OutputStream() {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            inner.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            inner.write(b, off, len)
            count += len
        }

        override fun flush() = inner.flush()

        override fun close() = inner.close()
    }
}
