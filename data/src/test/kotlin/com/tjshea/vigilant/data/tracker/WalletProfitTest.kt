package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tj, 2026-10-19 (PL): all-time profit is the wallet now (cash plus open bets at cost) minus the money put in, from Novig's ledger, never day 1's stakes; and "Make sure the wallet figure also takes out
 * withdrawals because sometimes I moved money back and forth".
 */
class WalletProfitTest {
    private fun row(id: String, kind: String, amount: Double) = LedgerRow(id, kind, amount, null, 1_000L)

    @Test
    fun `profit is cash plus open bets minus what was put in, net of what was taken out`() {
        val m = WalletProfit.of(
            198.63,
            listOf(NovigPosition("m1", "o1", 4_000, 20.00), NovigPosition("m2", "o2", 5_000, 22.55), NovigPosition("m3", "o3", 0, 0.0)),
            listOf(row("t1", "TRANSFER_IN", 200.0), row("t2", "TRANSFER_IN", 50.0), row("t3", "TRANSFER_OUT", -20.0), row("f1", "FILL", -5.0), row("s1", "SETTLEMENT", 9.0)),
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
        // Only the moves count as rows; betting rows (fills, settlements) are the profit itself.
        assertEquals(3, m.transfers)
        assertEquals("TRANSFER_IN +250.00 (2), TRANSFER_OUT -20.00 (1)", m.detail)
    }

    @Test
    fun `money moved out and back in again nets out, whatever kind of row moved it`() {
        // $100 in, $40 out, $40 back in, $10 withdrawn, a $5 promotional credit later reclaimed: $90 stayed in.
        val moves = listOf(
            row("a", "TRANSFER_IN", 100.0), row("b", "TRANSFER_OUT", -40.0), row("c", "TRANSFER_IN", 40.0), row("d", "WITHDRAWAL", -10.0),
            row("e", "CREDIT", 5.0), row("f", "CREDIT_RECLAIM", -5.0),
        )
        // The wallet holds $90 cash and $15 on an open bet: $15 up on the $90 that stayed in.
        val m = WalletProfit.of(90.0, listOf(NovigPosition("m", "o", 3_000, 15.0)), moves, 0L)!!
        assertEquals(145.0, m.putIn, 1e-9)
        assertEquals(55.0, m.takenOut, 1e-9)
        assertEquals(90.0, m.netIn, 1e-9)
        assertEquals(15.0, m.profit, 1e-9)
        // Moving $30 more out changes the cash and what was taken out alike: the profit does not move.
        val after = WalletProfit.of(60.0, listOf(NovigPosition("m", "o", 3_000, 15.0)), moves + row("g", "TRANSFER_OUT", -30.0), 0L)!!
        assertEquals(m.profit, after.profit, 1e-9)
    }

    @Test
    fun `an open bet is at stake, not lost, so placing one leaves the profit where it was`() {
        val deposit = listOf(row("t1", "TRANSFER_IN", 100.0))
        assertEquals(0.0, WalletProfit.of(100.0, emptyList(), deposit, 0L)!!.profit, 1e-9)
        assertEquals(0.0, WalletProfit.of(75.0, listOf(NovigPosition("m", "o", 5_000, 25.0)), deposit, 0L)!!.profit, 1e-9)
    }

    @Test
    fun `with no money moved on record the deposit is unknown, so there is no figure`() {
        assertNull(WalletProfit.of(198.63, emptyList(), listOf(row("f", "FILL", -5.0)), 0L))
        assertNull(WalletProfit.of(Double.NaN, emptyList(), listOf(row("t", "TRANSFER_IN", 5.0)), 0L))
        // A row read twice (two pages overlapping) counts once.
        assertEquals(5.0, WalletProfit.of(5.0, emptyList(), listOf(row("t", "TRANSFER_IN", 5.0), row("t", "TRANSFER_IN", 5.0)), 0L)!!.putIn, 1e-9)
        assertNull(WalletProfit.of(0.0, emptyList(), listOf(row("t", "TRANSFER_IN", 5.0), row("u", "TRANSFER_OUT", -5.0)), 0L)!!.pct)
    }
}
