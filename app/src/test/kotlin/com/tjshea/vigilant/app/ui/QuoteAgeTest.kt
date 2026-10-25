package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.engine.BookPrices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PB20 (Tj, 2026-10-25): each book on the bet sheet shows its quote's age and the feed that sent it, green fresh, yellow medium, red stale. */
class QuoteAgeTest {
    private val now = 1_800_000_000_000L
    private fun book(ageMs: Long?, source: String?) = BookPrices("draftkings", "DraftKings", listOf(1.9, 1.9), ageMs?.let { now - it }, source)

    @Test
    fun `the age and the feed in words, coloured by how old`() {
        assertEquals("4s · PropLine" to QuoteAge.Level.FRESH, QuoteAge.label(book(4_000, "propline_props"), now))
        assertEquals("2m 10s · ParlayAPI" to QuoteAge.Level.MEDIUM, QuoteAge.label(book(130_000, "parlay"), now))
        assertEquals("4m · The Odds API" to QuoteAge.Level.STALE, QuoteAge.label(book(240_000, "oddsapi"), now))
        assertEquals("1m" to QuoteAge.Level.FRESH, QuoteAge.label(book(60_000, null), now))
        assertNull(QuoteAge.label(book(null, null), now))
    }
}
