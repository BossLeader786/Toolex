package com.toolex.pdf.util

import com.toolex.pdf.engine.PageGeometry
import com.toolex.pdf.engine.Rungs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathHeuristicsTest {

    @Test
    fun `kb values are parsed with the current unit`() {
        assertEquals(800L * 1024, MathHeuristics.parseSize("800", unitIsMb = false))
        assertEquals(1.5 * 1024 * 1024, MathHeuristics.parseSize("1.5", unitIsMb = true)!!.toDouble(), 1.0)
    }

    @Test
    fun `explicit units win over the selected unit`() {
        assertEquals(2L * 1024 * 1024, MathHeuristics.parseSize("2 MB", unitIsMb = false))
        assertEquals(900L * 1024, MathHeuristics.parseSize("900kb", unitIsMb = true))
        assertEquals(3L * 1024, MathHeuristics.parseSize("3K", unitIsMb = true))
    }

    @Test
    fun `comma decimals are accepted`() {
        assertEquals(1.5 * 1024 * 1024, MathHeuristics.parseSize("1,5mb", unitIsMb = true)!!.toDouble(), 1.0)
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(MathHeuristics.parseSize("", false))
        assertNull(MathHeuristics.parseSize("abc", false))
        assertNull(MathHeuristics.parseSize("0", false))
        assertNull(MathHeuristics.parseSize("-5", false))
    }

    @Test
    fun `nice rounding keeps readable values`() {
        assertEquals(10L * 1024 * 1024, MathHeuristics.roundNice(10_400_000))
        assertEquals(10L * 1024 * 1024, MathHeuristics.roundNice(10L * 1024 * 1024))
        assertEquals(980L * 1024, MathHeuristics.roundNice(980L * 1024))
        // nearest, not always down: 1003 KB -> 1000 KB, 1009 KB -> 1010 KB
        assertEquals(1000L * 1024, MathHeuristics.roundNice(1003L * 1024))
        assertEquals(1010L * 1024, MathHeuristics.roundNice(1009L * 1024))
        assertTrue(MathHeuristics.roundNice(980L * 1024) <= 980L * 1024)
    }

    @Test
    fun `aggressiveness is clamped`() {
        assertEquals(0f, MathHeuristics.aggressiveness(500, 500), 0.001f)
        assertEquals(0f, MathHeuristics.aggressiveness(900, 500), 0.001f)
        assertEquals(0.5f, MathHeuristics.aggressiveness(500, 1000), 0.001f)
        assertTrue(MathHeuristics.aggressiveness(1, 1_000_000) <= 0.95f)
    }

    @Test
    fun `feasibility hints get stricter as the target shrinks`() {
        val easy = MathHeuristics.feasibilityHint(980, 1000)!!
        val hard = MathHeuristics.feasibilityHint(100, 1000)!!
        assertTrue(easy != hard)
        assertNull(MathHeuristics.feasibilityHint(100, 0))
    }
}

class NamingTest {

    @Test
    fun `names are sanitised like the flask app`() {
        assertEquals("Holiday_Trip_1", FileStore.safeBaseName("Holiday Trip (1).jpg"))
        assertEquals("scan", FileStore.safeBaseName("../../scan.png"))
        assertEquals("image", FileStore.safeBaseName("   .jpg"))
        assertEquals("no_ext", FileStore.safeBaseName("no_ext"))
    }

    @Test
    fun `long names are truncated`() {
        val name = FileStore.safeBaseName("a".repeat(200) + ".png")
        assertTrue(name.length <= 70)
    }

    @Test
    fun `byte formatting is human readable`() {
        assertEquals("512 B", FileStore.formatBytes(512))
        assertEquals("900 KB", FileStore.formatBytes(900 * 1024))
        assertEquals("1.5 MB", FileStore.formatBytes((1.5 * 1024 * 1024).toLong()))
    }
}

class RungsTest {

    @Test
    fun `normal rung matches the web app default`() {
        assertEquals(200, Rungs.NORMAL.dpi)
        assertEquals(88, Rungs.NORMAL.quality)
    }

    @Test
    fun `ladder goes from best to smallest quality`() {
        val ladder = Rungs.LADDER
        assertEquals(10, ladder.size)
        assertTrue(ladder.first().dpi > ladder.last().dpi)
        assertEquals(ladder, ladder.sortedByDescending { it.dpi * 1000 + it.quality })
    }

    @Test
    fun `target floor and ceiling match the server rules`() {
        assertEquals(10L * 1024, PageGeometry.MIN_TARGET_BYTES)
        assertEquals(200L * 1024 * 1024, PageGeometry.MAX_TARGET_BYTES)
    }
}
