package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tj, 2026-10-19 (PL): all-time profit is the wallet now (cash plus open bets at cost) minus the money put in, from Novig's ledger, never day 1's stakes. */
class WalletProfitTest {
    private fun row(id: String, kind: String, amount: Double) = LedgerRow(id, kind, amount, null, 1_000L)

    @Test
    fun `profit is cash plus open bets minus what was put in, net of what was taken out`() {
        val m = WalletProfit.of(
            198.63,
            listOf(NovigPosition("m1", "o1", 4_000, 20.00), NovigPosition("m2", "o2", 5_000, 22.55), NovigPosition("m3", "o3", 0, 0.0)),
            listOf(row("t1", "TRANSFER_IN", 200.0), row("t2", "TRANSFER_IN", 50.0), row("t3", "TRANSFER_OUT", -20.0), row("f1", "FILL", -5.0)),
            7L,
        )!!
        assertEquals(42.55, m.openCost, 1e-9)
        assertEquals(2, m.openBets)
        assertEquals(241.18, m.value, 1e-9)
        assertEquals(250.0, m.putIn, 1e-9)
        assertEquals(20.0, m.takenOut, 1e-9)
        assertEquals(230.0, m.netIn, 1e-9)
        assertEquals(11.18, m.profit, 1e-9)
        assertEquals(11.18 / 230.0, m.pct!!, 1e-9)
        assertEquals(3, m.transfers)
    }

    @Test
    fun `an open bet is at stake, not lost: placing one leaves the profit where it was`() {
        val deposit = listOf(row("t1", "TRANSFER_IN", 100.0))
        val before = WalletProfit.of(100.0, emptyList(), deposit, 0L)!!
        val after = WalletProfit.of(75.0, listOf(NovigPosition("m", "o", 5_000, 25.0)), deposit, 0L)!!
        assertEquals(0.0, before.profit, 1e-9)
        assertEquals(0.0, after.profit, 1e-9)
    }

    @Test
    fun `with no transfer on record the deposit is unknown, so there is no figure`() {
        assertNull(WalletProfit.of(198.63, emptyList(), listOf(row("f", "FILL", -5.0)), 0L))
        assertNull(WalletProfit.of(Double.NaN, emptyList(), listOf(row("t", "TRANSFER_IN", 5.0)), 0L))
        // A row read twice (two pages overlapping) counts once.
        assertEquals(5.0, WalletProfit.of(5.0, emptyList(), listOf(row("t", "TRANSFER_IN", 5.0), row("t", "TRANSFER_IN", 5.0)), 0L)!!.putIn, 1e-9)
        val nothingIn = WalletProfit.of(0.0, emptyList(), listOf(row("t", "TRANSFER_IN", 5.0), row("u", "TRANSFER_OUT", -5.0)), 0L)!!
        assertNull(nothingIn.pct)
    }
}
