package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * PX9 (Tj 2026-10-25, "Why are the odds this stale?"): the engine's real-time list holds a row's game for as long as it is listed, so a quote that was young when the scan priced it kept pricing the row while
 * it aged (far games' rows showed fair odds 17 to 25 minutes old). [Pricing.dropOld] takes the aged quotes out each pass, by each quote's own feed.
 */
class HeldRowsAgeTest {
    private val now = 1_800_000_000_000L
    private val near = now + 2 * 3_600_000L
    private val far = now + 30 * 3_600_000L

    @After fun reset() { Freshness.sgoMode = false; Freshness.plainFastFeeds = false; Freshness.sgoMaxAgeMs = 10 * 60_000L }

    private fun quote(book: String, source: String, ageMin: Double?) = RefBookMarket(
        bookKey = book, bookTitle = book, kind = LineKind.TOTAL, quotes = listOf(RefQuote(Side.OVER, 1.9, 44.5), RefQuote(Side.UNDER, 1.9, 44.5)),
        lastUpdateMs = ageMin?.let { now - (it * 60_000L).toLong() }, source = source,
    )

    private fun game(start: Long, vararg m: RefBookMarket) = RefEvent("g", "americanfootball_nfl", start, "Home", "Away", m.toList())

    @Test fun `a near game keeps five minutes and a far one ten, an unstamped quote stays, and nothing aged is the same object`() {
        val e = game(near, quote("a", "propline", 2.0), quote("b", "propline", 7.0), quote("c", "parlay", 4.9), quote("d", "kalshi", null))
        val out = Pricing.dropOld(e, now)
        assertEquals(listOf("a", "c", "d"), out.markets.map { it.bookKey })
        val f = game(far, quote("a", "propline", 9.0), quote("b", "propline", 11.0))
        assertEquals(listOf("a"), Pricing.dropOld(f, now).markets.map { it.bookKey })
        val fresh = game(near, quote("a", "propline", 1.0))
        assertSame(fresh, Pricing.dropOld(fresh, now))
    }

    @Test fun `with SGO Pro on a quote keeps the long limit until the plain limit for fast feeds is switched on`() {
        Freshness.sgoMode = true
        Freshness.sgoMaxAgeMs = 15 * 60_000L
        val e = game(near, quote("a", "propline", 12.0), quote("s", "sgo", 12.0), quote("t", "sgo-props", 16.0))
        // As it was: every feed up to the guard.
        assertEquals(listOf("a", "s"), Pricing.dropOld(e, now).markets.map { it.bookKey })
        // Plain limit for the fast feeds: only SportsGameOdds' own quotes keep the guard.
        Freshness.plainFastFeeds = true
        assertEquals(listOf("s"), Pricing.dropOld(e, now).markets.map { it.bookKey })
    }
}
