package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigFill
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletBetMoneyTest {
    private fun fill(id: String, order: String, market: String, outcome: String) = NovigFill(id, order, null, market, outcome, 100, 0.5, true, 0.01, 10)
    private fun bet(id: String, market: String, outcome: String, status: BetStatus = BetStatus.PENDING) = TrackedBet(
        id, 1L, "NBA", "A @ B", 5L, "Total", "Over", market, outcome, 0.5, 0.5, null, null, 0.5, status,
    )

    @Test fun aWinIsTheSettlementLessTheFillsAndFees() {
        val fills = listOf(fill("f1", "o1", "m1", "x"))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "f1", 10), LedgerRow("b", "FEE", -0.01, "f1", 10), LedgerRow("c", "SETTLEMENT", 1.0, "m1", 99))
        val out = WalletBetMoney.apply(listOf(bet("b1", "m1", "x")), ledger, fills).single()
        assertEquals(BetStatus.WON, out.status)
        assertEquals(0.49, out.profit!!, 1e-9)
        assertEquals(99L, out.settledAtMs)
    }

    @Test fun aGradedLossIsWhatWasPaidFeesIncluded() {
        val fills = listOf(fill("f1", "o1", "m1", "x"))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "f1", 10), LedgerRow("b", "FEE", -0.01, "f1", 10))
        val out = WalletBetMoney.apply(listOf(bet("b1", "m1", "x", BetStatus.LOST)), ledger, fills).single()
        assertEquals(BetStatus.LOST, out.status)
        assertEquals(-0.51, out.profit!!, 1e-9)
    }

    @Test fun aGradedWinNovigHasNotPaidYetIsNotShownAsALoss() {
        val fills = listOf(fill("f1", "o1", "m1", "x"))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "f1", 10), LedgerRow("b", "FEE", -0.01, "f1", 10))
        val out = WalletBetMoney.apply(listOf(bet("b1", "m1", "x", BetStatus.WON)), ledger, fills).single()
        assertEquals(BetStatus.WON, out.status)
        org.junit.Assert.assertTrue(out.profit!! > 0.0)
    }

    @Test fun anOpenBetWithNoSettlementStaysOpen() {
        val fills = listOf(fill("f1", "o1", "m1", "x"))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "f1", 10))
        assertEquals(BetStatus.PENDING, WalletBetMoney.apply(listOf(bet("b1", "m1", "x")), ledger, fills).single().status)
    }

    @Test fun settlementNamedByOrderIdStillFindsItsMarket() {
        val fills = listOf(fill("f1", "o1", "m1", "x"))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "f1", 10), LedgerRow("c", "SETTLEMENT", 1.0, "o1", 99))
        assertEquals(BetStatus.WON, WalletBetMoney.apply(listOf(bet("b1", "m1", "x")), ledger, fills).single().status)
    }

    @Test fun twoBetsShareTheMarketsMoneyByStake() {
        val fills = listOf(fill("f1", "o1", "m1", "x"), fill("f2", "o2", "m1", "x"))
        val ledger = listOf(LedgerRow("a", "FILL", -0.5, "f1", 10), LedgerRow("b", "FILL", -0.5, "f2", 11), LedgerRow("c", "SETTLEMENT", 2.0, "m1", 99))
        val out = WalletBetMoney.apply(listOf(bet("b1", "m1", "x"), bet("b2", "m1", "x")), ledger, fills)
        assertEquals(1.0, out.sumOf { it.profit!! }, 1e-9)
    }
}
