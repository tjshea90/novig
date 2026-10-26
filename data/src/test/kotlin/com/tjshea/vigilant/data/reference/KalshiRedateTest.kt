package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.kalshi.KalshiTop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** PX13b: a Kalshi quote the socket still vouches for is current now; one whose price moved, or that the socket cannot vouch for, keeps its own age. */
class KalshiRedateTest {
    private val read = 1_000_000L
    private val now = read + 300_000L

    private fun event(bid: String = "0.4600", ask: String = "0.4700", book: String = "kalshi") = RefEvent("e", "americanfootball_nfl", now + 86_400_000L, "Denver Broncos", "Kansas City Chiefs", listOf(
        RefBookMarket(book, book, LineKind.MONEYLINE, listOf(
            RefQuote(Side.HOME, 2.13, null, feedOutcomeId = KalshiRedate.tag("KXNFLGAME-DEN", bid, ask)),
            RefQuote(Side.AWAY, 1.85, null, feedOutcomeId = KalshiRedate.tag("KXNFLGAME-KC", "0.5300", "0.5400")),
        ), read),
    ))

    private fun view(trusted: Boolean = true, tops: Map<String, KalshiTop> = emptyMap()) = object : KalshiRedate.StreamView {
        override fun trusted(ticker: String, readAtMs: Long) = trusted
        override fun top(ticker: String) = tops[ticker]
    }

    @Test fun `vouched and unmoved is current now`() {
        assertEquals(now, KalshiRedate.overlay(event(), now, view()).markets.single().lastUpdateMs)
        // a push after the read that left the top where it was (a trade) changes nothing
        val same = mapOf("KXNFLGAME-DEN" to KalshiTop(0.46, 0.47, 10.0, 10.0, read + 60_000L))
        assertEquals(now, KalshiRedate.overlay(event(), now, view(tops = same)).markets.single().lastUpdateMs)
    }

    @Test fun `a moved price or a socket that cannot vouch keeps the read's age`() {
        val moved = mapOf("KXNFLGAME-DEN" to KalshiTop(0.47, 0.48, 10.0, 10.0, read + 60_000L))
        val e = event()
        assertSame(e, KalshiRedate.overlay(e, now, view(tops = moved)))
        assertSame(e, KalshiRedate.overlay(e, now, view(trusted = false)))
        // a push from before the read is already in it
        val older = mapOf("KXNFLGAME-DEN" to KalshiTop(0.47, 0.48, 10.0, 10.0, read - 1))
        assertEquals(now, KalshiRedate.overlay(event(), now, view(tops = older)).markets.single().lastUpdateMs)
    }

    @Test fun `only Kalshi's tagged quotes are touched`() {
        val other = event(book = "pinnacle")
        assertSame(other, KalshiRedate.overlay(other, now, view()))
    }
}
