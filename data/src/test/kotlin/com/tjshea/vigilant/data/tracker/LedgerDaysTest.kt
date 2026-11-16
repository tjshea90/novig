package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigFill
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerDaysTest {
    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L // game start
    private fun fill(id: String, market: String, ts: Long) = NovigFill(id, "o$id", null, market, "x", 100, 0.5, true, 0.01, ts)
    private fun bet(id: String, market: String, status: BetStatus) = TrackedBet(id, t0 - 3_600_000L, "NBA", "A @ B", t0, "Total", "Over", market, "x", 0.5, 0.5, null, null, 0.5, status)
    @After fun reset() = LedgerDays.set(emptyList())

    @Test fun aDaysProfitIsNovigsMoneyNotTheBetsOwnProfit() {
        val fills = listOf(fill("1", "m1", t0 - 1000), fill("2", "m2", t0 - 1000))
        val ledger = listOf(
            LedgerRow("a", "FILL", -0.5, "1", t0), LedgerRow("b", "FEE", -0.01, "1", t0), LedgerRow("c", "SETTLEMENT", 1.0, "m1", t0 + 1000),
            LedgerRow("d", "FILL", -0.5, "2", t0), LedgerRow("e", "MAKER_CREDIT", 0.02, "2", t0),
        )
        val bets = listOf(bet("b1", "m1", BetStatus.WON), bet("b2", "m2", BetStatus.LOST))
        val d = LedgerDays.build(bets, ledger, fills, t0 + day).single()
        assertEquals(0.49 - 0.48, d.profit, 1e-9)
        assertEquals(2, d.settled); assertEquals(1, d.won); assertEquals(1, d.lost)
        val book = SlateProfit.book(bets, t0 + day, emptyList(), listOf(d))
        assertEquals(0.01, book.profit, 1e-9)
    }

    @Test fun anOpenMarketIsNotProfitOrLoss() {
        val fills = listOf(fill("1", "m1", t0))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "1", t0))
        assertEquals(0, LedgerDays.build(listOf(bet("b1", "m1", BetStatus.PENDING)), ledger, fills, t0 + 1000).size)
    }

    @Test fun anUntrackedOldMarketWithNoPayoutIsALoss() {
        val fills = listOf(fill("1", "mx", t0))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "1", t0))
        assertEquals(-0.5, LedgerDays.build(emptyList(), ledger, fills, t0 + 4 * day).single().profit, 1e-9)
    }

    @Test fun aRecentUntrackedMarketNovigNoLongerHoldsIsALossButAHeldOneIsNot() {
        val fills = listOf(fill("1", "lost", t0), fill("2", "held", t0))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "1", t0), LedgerRow("b", "FILL", -0.4, "2", t0))
        val d = LedgerDays.build(emptyList(), ledger, fills, t0 + day, open = setOf("held")).single()
        assertEquals(-0.5, d.profit, 1e-9)
        assertEquals(0, LedgerDays.build(emptyList(), ledger, fills, t0 + day, open = null).size)
    }
}
