package com.toolex.pdf.engine

/**
 * The quality ladder from `optimize_pdf()` in the Flask app:
 * first rung that fits the ceiling wins, so the highest detail is kept.
 */
data class Rung(val dpi: Int, val quality: Int)

object Rungs {
    val LADDER: List<Rung> = listOf(
        Rung(240, 92),
        Rung(220, 90),
        Rung(200, 88),
        Rung(180, 86),
        Rung(160, 84),
        Rung(150, 80),
        Rung(130, 76),
        Rung(110, 70),
        Rung(96, 64),
        Rung(80, 58),
    )

    /** "Normal" mode in PaperLite = 200 DPI / q88. */
    val NORMAL: Rung = LADDER[2]

    /**
     * Phone-specific adaptation: a 100-page job must not start by rendering a
     * hundred 240-DPI pages. Big jobs start lower on the ladder; the first rung
     * that fits still wins, so nothing changes for small/medium jobs.
     */
    fun ladder(pageCount: Int): List<Rung> = when {
        pageCount <= 20 -> LADDER
        pageCount <= 50 -> LADDER.drop(2) // start at 200 DPI
        else -> LADDER.drop(4)            // start at 160 DPI
    }
}
