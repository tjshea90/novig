package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossExchangeTest {
    private val start = 2_000_000_000_000L

    private fun pair(homeAsk: Double, awayAsk: Double, exBook: String, exHomeDec: Double, exAwayDec: Double): List<Opportunity> {
        val event = NovigEvent("e1", "BASEBALL", "MLB", NovigEvent.STATUS_PREGAME, "Cubs @ Padres", start)
        val market = NovigMarket("m1", "e1", "MONEYLINE", "OPEN", "Moneyline", start, MarketFee.GAME, listOf(NovigOutcome("oh", "Padres", "TBD"), NovigOutcome("oa", "Cubs", "TBD")))
        val bp = BookPrices(exBook, exBook, listOf(exHomeDec, exAwayDec))
        val fair = FairLine(listOf(0.5, 0.5), FairSource.SHARP, FairSource.SHARP, DevigMethod.POWER, listOf(BookFair(bp, listOf(0.5, 0.5), 0.0, true)), listOf(exBook), emptyList())
        fun o(i: Int, ask: Double, side: Side) = Opportunity(
            league = Leagues.byNovigName("MLB")!!, event = event, market = market, outcome = market.outcomes[i], marketLabel = "Moneyline", kind = LineKind.MONEYLINE,
            selection = market.outcomes[i].name, fair = fair, fairProbability = 0.5, quote = EvQuote(0.5, ask, 0.0), ladder = emptyList(), depth = null, suggestedStake = null,
            novigWidth = null, bookFetchedAtMs = start, fairUpdatedMs = start, refEvent = null, lineKey = LineKey("r1", LineKind.MONEYLINE, null), target = OutcomeTarget.Is(side), fairAsOfMs = start,
        )
        return listOf(o(0, homeAsk, Side.HOME), o(1, awayAsk, Side.AWAY))
    }

    @Test
    fun `Novig's home ask plus Kalshi's away ask under a dollar after the fee is a pair, the other way round is not`() {
        // home 0.45 at Novig + away 0.50 at Kalshi (fee 0.07 x .5 x .5 = 0.0175) = 0.9675 -> 3.25 cents on every dollar contract
        val rows = CrossExchange.find(pair(0.45, 0.60, "kalshi", 1.6, 2.0), 1L)
        assertEquals(1, rows.size)
        assertEquals("Padres", rows[0].novigSide); assertEquals("Cubs", rows[0].otherSide); assertEquals("Kalshi", rows[0].exchange)
        assertEquals(0.0325, rows[0].profit, 1e-9)
    }

    @Test
    fun `no profit after fees, a thin margin, other books and one-sided lines record nothing`() {
        assertTrue(CrossExchange.find(pair(0.50, 0.50, "kalshi", 1.9, 1.9), 1L).isEmpty())
        assertTrue("0.2 cents is under the margin", CrossExchange.find(pair(0.49, 0.60, "sxbet", 1.6, 2.0).map { it }, 1L).none { it.profit < CrossExchange.MIN_PROFIT })
        assertTrue("a sportsbook is not an exchange we pair with", CrossExchange.find(pair(0.45, 0.60, "pinnacle", 1.6, 2.0), 1L).isEmpty())
        assertTrue(CrossExchange.find(pair(0.45, 0.60, "kalshi", 1.6, 2.0).take(1), 1L).isEmpty())
    }

    @Test
    fun `SX Bet takes no fee in the model`() {
        assertEquals(0.05, CrossExchange.find(pair(0.45, 0.60, "sxbet", 1.6, 2.0), 1L).single().profit, 1e-9)
    }
}
