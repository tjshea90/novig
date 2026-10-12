package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import org.junit.Assert.assertEquals
import org.junit.Test

class FreshestWinsTest {
    private fun ml(book: String, home: Double, away: Double, at: Long?) =
        RefBookMarket(book, book, LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, home, null), RefQuote(Side.AWAY, away, null)), at)

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

    @Test fun `switched off the first feed always wins`() {
        val r = ref(ml("draftkings", 1.90, 2.00, 1_000_000L), ml("draftkings", 1.80, 2.10, 1_000_000L + 60_000L))
        assertEquals(1.90, Pricing.bookPrices(r, key, freshest = false).single().decimalOdds[0], 1e-9)
    }
}
