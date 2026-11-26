package com.toolex.pdf.util

import kotlin.math.abs

/**
 * Small, testable helpers used by the custom-size sheet.
 * Kept separate from Compose so they can be unit tested on the JVM.
 */
object MathHeuristics {

    /**
     * Parses what the user typed, e.g. "800" (KB), "1.5 MB", "900kb", "2".
     * A bare number is interpreted with [unitIsMb]. Returns null if garbage.
     */
    fun parseSize(text: String, unitIsMb: Boolean): Long? {
        val cleaned = text.trim().replace(',', '.').replace(" ", "")
        if (cleaned.isEmpty()) return null
        val match = Regex("^([0-9]*\\.?[0-9]+)(kb|mb|k|m)?$", RegexOption.IGNORE_CASE)
            .find(cleaned) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        if (number <= 0) return null
        val unit = match.groupValues[2].lowercase()
        val mb = when (unit) {
            "mb", "m" -> true
            "kb", "k" -> false
            else -> unitIsMb
        }
        val bytes = if (mb) number * 1024 * 1024 else number * 1024
        return bytes.toLong().coerceAtLeast(1)
    }

    /** Nicely rounded ceilings: 10.4 MB -> 10 MB, 980 KB -> 1 MB. */
    fun roundNice(bytes: Long): Long {
        val mb = 1024L * 1024L
        val kb = 1024L
        return when {
            bytes >= mb -> (bytes / mb) * mb
            bytes >= 100 * kb -> (bytes / (10 * kb)) * 10 * kb
            bytes >= 10 * kb -> (bytes / kb) * kb
            else -> bytes
        }
    }

    /**
     * How much the target is below the natural size, 0f..1f.
     * 0 = same size (easy), close to 1 = very aggressive compression.
     */
    fun aggressiveness(targetBytes: Long, naturalBytes: Long): Float {
        if (naturalBytes <= 0L) return 0f
        val ratio = 1.0 - (targetBytes.toDouble() / naturalBytes)
        return ratio.coerceIn(0.0, 0.95).toFloat()
    }

    /** Human hint about feasibility, mirroring the app's honest "not a promise". */
    fun feasibilityHint(targetBytes: Long, naturalBytes: Long): String? {
        val a = aggressiveness(targetBytes, naturalBytes)
        return when {
            naturalBytes <= 0L -> null
            a <= 0.05f -> "Aaram se fit hoga, quality almost same rahegi."
            a <= 0.45f -> "Fit ho jana chahiye, halki quality drop ke saath."
            a <= 0.7f -> "Kaafi compression lagega — text thoda soft ho sakta hai."
            else -> "Bahut zyada compression: readability gir sakti hai. Target badhane ki salah."
        }
    }

    fun approximatelyEqual(a: Long, b: Long, tolerance: Long = 1024): Boolean =
        abs(a - b) <= tolerance
}
