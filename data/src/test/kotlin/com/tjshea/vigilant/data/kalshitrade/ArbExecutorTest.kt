package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.reference.LineKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** KV5b: Novig first, Kalshi sized to Novig's fill, nothing sent when the pair stopped paying, a lone leg reported not hidden. */
class ArbExecutorTest {
    private fun arb(novigCost: Double = 0.33, kalshiPrice: Double = 0.62, payout: Double = 400.0): ArbOpportunity {
        val ev = NovigEvent("e", "NFL", "NFL", NovigEvent.STATUS_PREGAME, "A @ B", 0L)
        val m = NovigMarket("m", "e", "MONEY", "OPEN", "d", 0L, KalshiFees.TAKER, emptyList())
        return ArbOpportunity(
            ev, m, "Moneyline", ArbLeg("Novig", "A", novigCost, 0.0, payout, 0L), ArbLeg("Kalshi", "B", kalshiPrice, KalshiFees.takerPerContract(kalshiPrice), payout, 0L),
            "o", "T", "yes", LineKind.MONEYLINE, true,
        )
    }

    private val settings = ArbSettings(minProfit = 0.005)

    @Test
    fun `both legs fill - Novig first, then Kalshi for exactly the contracts Novig filled`() = runTest {
        val calls = ArrayList<String>()
        val ex = ArbExecutor(
            refresh = { it },
            novig = { _, payout, _ -> calls += "novig $payout"; LegResult.Filled(payout, payout * 0.33) },
            kalshi = { _, n, _ -> calls += "kalshi $n"; LegResult.Filled(n.toDouble(), n * (0.62 + KalshiFees.takerPerContract(0.62))) },
        )
        val r = ex.run(arb(), maxPayout = 50.0, clipPayout = 10.0, settings = settings) as ArbOutcome.Done
        assertEquals(listOf("novig 10.0", "kalshi 10"), calls)
        assertEquals(10.0, r.payout, 0.0)
        assertTrue(r.profit > 0.0)
    }

    @Test
    fun `when the prices moved so the pair no longer pays, nothing is sent`() = runTest {
        var sent = 0
        val ex = ArbExecutor(
            refresh = { arb(kalshiPrice = 0.70) },
            novig = { _, p, _ -> sent++; LegResult.Filled(p, p * 0.33) },
            kalshi = { _, n, _ -> sent++; LegResult.Filled(n.toDouble(), n * 0.7) },
        )
        val r = ex.run(arb(), 50.0, 10.0, settings)
        assertTrue(r.toString(), r is ArbOutcome.Skipped)
        assertEquals(0, sent)
    }

    @Test
    fun `Novig not filling sends nothing to Kalshi`() = runTest {
        var kalshiCalls = 0
        val ex = ArbExecutor({ it }, { _, _, _ -> LegResult.None("nobody was selling") }, { _, _, _ -> kalshiCalls++; LegResult.None("x") })
        assertTrue(ex.run(arb(), 50.0, 10.0, settings) is ArbOutcome.Skipped)
        assertEquals(0, kalshiCalls)
    }

    @Test
    fun `Kalshi failing after Novig filled is reported as unhedged after one more try at the break-even`() = runTest {
        var kalshiCalls = 0
        val ex = ArbExecutor({ it }, { _, p, _ -> LegResult.Filled(p, p * 0.33) }, { _, _, _ -> kalshiCalls++; LegResult.None("price moved") })
        val r = ex.run(arb(), 50.0, 10.0, settings) as ArbOutcome.Unhedged
        assertEquals("Novig", r.venue)
        assertEquals(10.0, r.payout, 0.0)
        assertEquals(2, kalshiCalls)
    }

    @Test
    fun `a partial Kalshi fill leaves only the rest unhedged`() = runTest {
        var n = 0
        val ex = ArbExecutor({ it }, { _, p, _ -> LegResult.Filled(p, p * 0.33) }, { _, c, _ -> n++; if (n == 1) LegResult.Filled(6.0, 6 * 0.64) else LegResult.None("gone") })
        val r = ex.run(arb(), 50.0, 10.0, settings) as ArbOutcome.Unhedged
        assertEquals(4.0, r.payout, 1e-9)
    }

    @Test
    fun `a lost answer stops everything and says which account to check`() = runTest {
        val ex = ArbExecutor({ it }, { _, p, _ -> LegResult.Filled(p, p * 0.33) }, { _, _, _ -> LegResult.Unknown("timeout") })
        val r = ex.run(arb(), 50.0, 10.0, settings)
        assertTrue(r.toString(), r is ArbOutcome.Unknown)
    }
}
