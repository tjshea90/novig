package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceScoreTest {
    private val now = 100_000_000L
    private fun m(id: String, p90Sec: Long?, quotes: Int = 500, pts: Double? = 1.0, lines: Int = 50, at: Long = now) = SourceScore.Measure(id, quotes, p90Sec?.times(1000), pts, lines, at)
    private val prior = listOf("sgo", "oddspapi", "parlay", "propline", "unabated")

    @Test fun `fresher feeds move ahead, a feed with no measurement counts as four minutes old, ties keep the shipped order`() {
        val r = SourceScore.rank(prior, mapOf("sgo" to m("sgo", 380), "propline" to m("propline", 70), "parlay" to m("parlay", 30)))
        assertEquals(listOf("parlay", "propline", "oddspapi", "unabated", "sgo"), r)   // a credit-metered but ample feed (ParlayAPI's paid keys) still ranks by freshness
    }

    @Test fun `a feed with a small monthly allowance never leads, however fresh (Tj 2026-10-16)`() {
        val r = SourceScore.rank(listOf("propline", "sgo", "oddspapi_free", "oddsapi", "unabated"), mapOf("oddsapi" to m("oddsapi", 46), "oddspapi_free" to m("oddspapi_free", 20), "sgo" to m("sgo", 600), "propline" to m("propline", 420), "unabated" to m("unabated", 7200)))
        assertEquals(listOf("propline", "sgo", "unabated", "oddspapi_free", "oddsapi"), r)
    }

    @Test fun `an inaccurate feed goes after every accurate one however fresh`() {
        val r = SourceScore.rank(prior, mapOf("propline" to m("propline", 5, pts = 6.5, lines = 80), "sgo" to m("sgo", 300)))
        assertEquals("propline", r.last())
        assertTrue(SourceScore.inaccurate(m("x", 5, pts = 6.5, lines = 80)))
        assertFalse("too few lines to judge", SourceScore.inaccurate(m("x", 5, pts = 6.5, lines = 5)))
    }

    @Test fun `with nothing measured the shipped order stands, and a thin measurement is ignored`() {
        assertEquals(prior, SourceScore.rank(prior, emptyMap()))
        assertEquals(prior, SourceScore.rank(prior, mapOf("propline" to m("propline", 1, quotes = 20))))
    }

    @Test fun `a feed with no recent measurement is due an audition except the scarce ones`() {
        val ms = mapOf("sgo" to m("sgo", 300, at = now))
        assertFalse(SourceScore.auditionDue("sgo", now + 1_000L, ms))
        assertTrue(SourceScore.auditionDue("sgo", now + SourceScore.AUDITION_MS + 1, ms))
        assertTrue(SourceScore.auditionDue("propline", now, ms))
        assertFalse("The Odds API credits are scarce", SourceScore.auditionDue("oddsapi", now, ms))
    }
}
