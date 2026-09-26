package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vigilant's math against an independent calculator: CrazyNinjaOdds' devigger, worst-case method,
 * read 2026-09-26 from `sportsbook_devigger.aspx?autofill=1&LegOdds=…&FinalOdds=…` (RESEARCH.md §16).
 * It prints one decimal, so the checks allow half a tenth of a point.
 */
class CrossCheckTest {

    private data class Case(val leg: Pair<Int, Int>, val finalOdds: Int, val fairPercent: Double, val evPercent: Double)

    private val cases = listOf(
        Case(120 to -140, 130, fairPercent = 43.4, evPercent = -0.1),
        Case(330 to -450, 400, fairPercent = 19.9, evPercent = -0.4),
        Case(-110 to -110, 105, fairPercent = 50.0, evPercent = 2.5),
        Case(250 to -300, 275, fairPercent = 26.4, evPercent = -1.1),
    )

    @Test
    fun `worst-case fair odds and EV match CrazyNinjaOdds' devigger`() {
        for (c in cases) {
            val raw = listOf(c.leg.first, c.leg.second).map { Odds.impliedProbability(Odds.americanToDecimal(it)) }
            val fair = Devig.devig(raw, DevigMethod.WORST_CASE)[0]
            assertEquals("fair for ${c.leg}", c.fairPercent, fair * 100, 0.051)
            val price = 1.0 / Odds.americanToDecimal(c.finalOdds)
            val ev = EvMath.quote(fair, price, MarketFee.GAME, eventLive = false).evPercent
            assertEquals("EV for ${c.leg} at ${c.finalOdds}", c.evPercent, ev * 100, 0.051)
        }
    }
}
