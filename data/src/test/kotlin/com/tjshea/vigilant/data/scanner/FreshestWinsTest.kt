package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import org.junit.Assert.assertEquals
import org.junit.Test

class FreshestWinsTest {
    private fun ml(book: String, home: Double, away: Double, at: Long?, source: String? = null) =
        RefBookMarket(book, book, LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, home, null), RefQuote(Side.AWAY, away, null)), at, source = source)

    private fun ref(vararg m: RefBookMarket) = RefEvent("e", "basketball_nba", 10_000_000L, "H", "A", m.toList())

    private val key = LineKey("e", LineKind.MONEYLINE, null)

    @Test fun `a clearly newer quote of the same book replaces the first feed's`() {
        val r = ref(ml("draftkings", 1.90, 2.00, 1_000_000L), ml("draftkings", 1.80, 2.10, 1_000_000L + 60_000L))
        assertEquals(1.80, Pricing.bookPrices(r, key, freshest = true).single().decimalOdds[0], 1e-9)
    }

    @Test fun `within the margin or without a time the feed order stands`() {
        val within = ref(ml("draftkings", 1.90, 2.00, 1_000_000L), ml("draftkings", 1.80, 2.10, 1_000_000L + 10_000L))
        assertEquals(1.90, Pricing.bookPrices(within, key).single().decimalOdds[0], 1e-9)
        val undated = ref(ml("draftkings", 1.90, 2.00, 1_000_000L), ml("draftkings", 1.80, 2.10, null))
        assertEquals(1.90, Pricing.bookPrices(undated, key).single().decimalOdds[0], 1e-9)
    }

    /** PB21b: PropLine and ParlayAPI quote one book at the same moment 3+ points apart: neither is trusted for that line; a move (one clearly newer) is not a disagreement. */
    @Test fun `two same-time quotes of a book far apart leave the book out`() {
        val apart = ref(ml("draftkings", 1.90, 2.00, 1_000_000L, "propline"), ml("draftkings", 1.70, 2.25, 1_000_000L + 10_000L, "parlay"), ml("fanduel", 1.95, 1.95, 1_000_000L))
        assertEquals(listOf("fanduel"), Pricing.bookPrices(apart, key).map { it.bookKey })
        // A book's own feed is the book: Pinnacle's host beside PropLine's copy of Pinnacle keeps the feed order.
        val own = ref(ml("pinnacle", 1.80, 2.10, 1_000_000L, "pinnacle"), ml("pinnacle", 1.91, 1.91, 1_000_000L + 5_000L, "propline"))
        assertEquals(1.80, Pricing.bookPrices(own, key).single().decimalOdds[0], 1e-9)
        val moved = ref(ml("draftkings", 1.90, 2.00, 1_000_000L), ml("draftkings", 1.70, 2.25, 1_000_000L + 60_000L))
        assertEquals(1.70, Pricing.bookPrices(moved, key).single().decimalOdds[0], 1e-9)
    }

    @Test fun `switched off the first feed always wins`() {
        val r = ref(ml("draftkings", 1.90, 2.00, 1_000_000L), ml("draftkings", 1.80, 2.10, 1_000_000L + 60_000L))
        assertEquals(1.90, Pricing.bookPrices(r, key, freshest = false).single().decimalOdds[0], 1e-9)
    }
}
