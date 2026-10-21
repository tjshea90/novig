package com.tjshea.vigilant.data.kalshitrade

import org.junit.Assert.assertTrue
import org.junit.Test

class KalshiFunnelTest {
    private fun f(vararg rows: KalshiFunnel.Row, listed: Int = 0) = KalshiFunnel.Funnel(rows.toList(), listed)

    @Test
    fun `the verdict names the biggest loss`() {
        assertTrue(KalshiFunnel.verdict(f()).contains("no games"))
        assertTrue(KalshiFunnel.verdict(f(KalshiFunnel.Row("NBA", 10, 2, 40, 40, 5))).contains("Matching is the loss"))
        assertTrue(KalshiFunnel.verdict(f(KalshiFunnel.Row("NBA", 10, 9, 40, 10, 5))).contains("Fair prices are the loss"))
        assertTrue(KalshiFunnel.verdict(f(KalshiFunnel.Row("NBA", 10, 9, 40, 38, 0))).contains("not much opportunity"))
        assertTrue(KalshiFunnel.verdict(f(KalshiFunnel.Row("NBA", 10, 9, 40, 38, 6), listed = 2)).contains("filters"))
        assertTrue(KalshiFunnel.verdict(f(KalshiFunnel.Row("NBA", 10, 9, 40, 38, 6), listed = 6)).contains("healthy"))
    }

    @Test
    fun `the lines carry the chain and a row per league`() {
        val l = KalshiFunnel.lines(f(KalshiFunnel.Row("NBA", 10, 9, 40, 38, 6), KalshiFunnel.Row("NHL", 4, 4, 12, 12, 1), listed = 7))
        assertTrue(l[0].contains("games read 14") && l[0].contains("matched to a fair-odds game 13") && l[0].contains("listed 7"))
        assertTrue(l.any { it.startsWith("NHL: 4 games") })
    }
}
