package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.NumberSpecs
import com.tjshea.vigilant.app.ui.isAmericanOdds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-07: "anywhere there are settings for … any number inputs that have options, also put a box where I can manually type in a number to set." What each box takes. */
class TypedNumberSpecTest {

    @Test
    fun `a percent box takes a decimal inside its range, a comma or a dot, and nothing else`() {
        val ev = NumberSpecs.percent("smallest edge", 0.1, 50.0)
        assertEquals(2.5, ev.parse("2.5")!!, 0.0)
        assertEquals(2.5, ev.parse("2,5")!!, 0.0)
        assertEquals(0.1, ev.parse("0.1")!!, 0.0)
        assertNull("under the range", ev.parse("0.05"))
        assertNull("over the range", ev.parse("51"))
        assertNull("a minus", ev.parse("-2"))
        assertNull("words", ev.parse("two"))
        assertNull("blank", ev.parse(""))
        assertNull("more places than it takes", ev.parse("2.555"))
        assertEquals("2.5", ev.show(2.5))
        assertEquals("3", ev.show(3.0))
    }

    @Test
    fun `a count or time box takes whole numbers only`() {
        val hours = NumberSpecs.time("hours", 1, 720)
        assertEquals(36.0, hours.parse("36")!!, 0.0)
        assertNull(hours.parse("36.5"))
        assertNull(hours.parse("0"))
        assertNull(hours.parse("721"))
        assertEquals("12", hours.filter("12h"))
    }

    @Test
    fun `the shortest odds box takes -100 or shorter, or +100 or longer, never -99 to +99`() {
        val spec = NumberSpecs.SHORTEST_ODDS
        assertEquals(-250.0, spec.parse("-250")!!, 0.0)
        assertEquals(-250.0, spec.parse("−250")!!, 0.0)
        assertEquals(-100.0, spec.parse("-100")!!, 0.0)
        assertEquals(110.0, spec.parse("110")!!, 0.0)
        assertEquals(110.0, spec.parse("+110".removePrefix("+"))!!, 0.0)
        assertNull(spec.parse("-99"))
        assertNull(spec.parse("99"))
        assertNull(spec.parse("0"))
        assertNull(spec.parse("-"))
        assertTrue(isAmericanOdds(-100) && isAmericanOdds(100) && !isAmericanOdds(99) && !isAmericanOdds(0))
        assertEquals("-250", spec.show(-250.0))
    }

    @Test
    fun `the longest odds box takes +100 or longer`() {
        val spec = NumberSpecs.LONGEST_ODDS
        assertEquals(140.0, spec.parse("140")!!, 0.0)
        assertNull(spec.parse("99"))
        assertNull(spec.parse("-150"))
    }

    @Test
    fun `a Kelly fraction is typed as a decimal from 0_01 to 1`() {
        val k = NumberSpecs.KELLY
        assertEquals(0.33, k.parse("0.33")!!, 0.0)
        assertEquals(1.0, k.parse("1")!!, 0.0)
        assertNull(k.parse("1.5"))
        assertNull(k.parse("0"))
        assertFalse(k.parse("0.2501") != null)
    }

    @Test
    fun `dollar boxes take cents`() {
        val d = NumberSpecs.dollars("amount")
        assertEquals(7.25, d.parse("7.25")!!, 0.0)
        assertNull(d.parse("0"))
        assertNull(d.parse("7.255"))
    }
}
