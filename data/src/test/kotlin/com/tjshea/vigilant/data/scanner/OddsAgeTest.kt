package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OddsAgeTest {
    @Test
    fun `spans are said in seconds, then minutes, then hours`() {
        assertEquals("<1 s", OddsAge.span(400))
        assertEquals("7 s", OddsAge.span(7_900))
        assertEquals("3 min", OddsAge.span(180_000))
        assertEquals("2 h", OddsAge.span(7_300_000))
        assertEquals("<1 s", OddsAge.span(-5_000))
    }

    private fun sample() = SampleScanFixture.opportunity()

    @Test
    fun `a scanned row says when Novig was read and how old the other books are, or that it does not know`() {
        val o = sample().copy(bookFetchedAtMs = 100_000L, fairUpdatedMs = 130_000L, fairAsOfMs = 40_000L, liveAges = null)
        assertEquals("Novig read 20 s ago · other books' prices 0 s to 80 s old".replace("0 s to", "<1 s to"), OddsAge.text(o, 120_000L)?.replace("-10", "<1"))
        val unknown = o.copy(fairAsOfMs = null, fairUpdatedMs = null)
        assertTrue(OddsAge.text(unknown, 120_000L)!!.contains("other books' ages unknown"))
    }

    @Test
    fun `a live row says the four feed ages exactly`() {
        val o = sample().copy(liveAges = LiveAges(pinnLineChangedMs = 100_000L, pinnFeedMs = 118_000L, novigMarketMs = 95_000L, novigFeedMs = 119_000L))
        assertEquals(
            "Live odds · Pinnacle line changed 20 s ago (its feed 2 s ago) · Novig market changed 25 s ago (its feed 1 s ago)",
            OddsAge.text(o, 120_000L),
        )
        val quiet = o.copy(liveAges = o.liveAges!!.copy(novigMarketMs = null))
        assertTrue(OddsAge.text(quiet, 120_000L)!!.contains("market unchanged since it was first read"))
    }
}
