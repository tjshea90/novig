package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.burst.Ladders
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ladder math of Live light (Tj, 2026-10-14): a ladder is a normal curve, and an alternate line that is off it is found. */
class LiveLightCurveTest {
    private val mu = 220.0
    private val sigma = 12.0
    private fun pOver(t: Double) = 1.0 - TailModel.phi((t - mu) / sigma)

    private fun total(id: String, strike: Double) = NovigMarket(
        id, "ev", "TOTAL", "OPEN", "d", 0L, MarketFee.GAME,
        listOf(NovigOutcome("$id-u", "Under $strike", "TBD"), NovigOutcome("$id-o", "Over $strike", "TBD")), strike,
    )

    /** A strike whose Over is offered at [overAsk] and Under at [underAsk]: the bids on the other side are 1 - the ask. */
    private fun point(id: String, strike: Double, overAsk: Double, underAsk: Double) =
        LadderPoint(
            Ladders.line(total(id, strike))!!,
            NovigBook(id, 1L, mapOf("$id-u" to listOf(BidLevel(((1 - overAsk) * 1000).toInt(), 5_000)), "$id-o" to listOf(BidLevel(((1 - underAsk) * 1000).toInt(), 5_000))), 0L),
        )

    /** Honest strikes on the curve: both sides 0.01 over the middle. */
    private fun honest(strike: Double): LadderPoint { val p = pOver(strike); return point("t${strike.toInt()}", strike, p + 0.01, 1 - p + 0.01) }

    private val state = GameState(TailSport.BASKETBALL, 60, 60, 0.40)   // an NBA game with 40% left: sigma 19 x sqrt(.4) = 12
    private val strikes = listOf(204.5, 208.5, 212.5, 216.5, 224.5, 228.5, 232.5, 236.5)

    @Test
    fun `probit inverts the normal curve and the fit recovers where the ladder is centred and how wide`() {
        assertEquals(0.0, LadderFit.probit(0.5), 1e-9)
        assertEquals(1.6449, LadderFit.probit(0.95), 1e-3)
        assertEquals(-2.3263, LadderFit.probit(0.01), 1e-3)
        val fit = LadderFit.of(strikes.map { it to pOver(it) })!!
        assertEquals(mu, fit.mu, 0.2)
        assertEquals(sigma, fit.sigma, 0.2)
        assertTrue(fit.rms < 0.005)
        assertNull("too few liquid lines", LadderFit.of(strikes.take(3).map { it to pOver(it) }))
        assertNull("a ladder that rises is not a ladder", LadderFit.of(strikes.map { it to 1 - pOver(it) }))
        assertNull("prices off a curve are not fitted", LadderFit.of(listOf(204.5 to 0.9, 208.5 to 0.2, 212.5 to 0.8, 216.5 to 0.3, 224.5 to 0.7)))
    }

    @Test
    fun `an alternate total priced far under its neighbours is found, from a fit that leaves it out`() {
        val ladder = strikes.map { honest(it) } + point("alt", 210.5, overAsk = 0.60, underAsk = 0.43)   // true P(Over 210.5) is 0.78
        val c = LiveLightCurve.scan(ladder, "NBA", state).single()
        assertEquals("alt-o", c.outcomeId)
        assertEquals("OVER", c.side)
        assertEquals(LiveLightCurve.RULE, c.rule)
        assertTrue("cautious fair ${c.fair}", c.fair in 0.65..0.78)
        assertTrue("edge ${c.edge}", c.edge > 0.08)
    }

    @Test
    fun `an honest ladder, a thin one, and a fit nowhere near the game's own spread find nothing`() {
        assertTrue(LiveLightCurve.scan(strikes.map { honest(it) } + honest(210.5), "NBA", state).isEmpty())
        val thin = listOf(honest(208.5), honest(216.5), honest(224.5), point("alt", 210.5, 0.60, 0.43))
        assertTrue(LiveLightCurve.scan(thin, "NBA", state).isEmpty())
        // The same ladder in a game with 2% left: a sigma of 12 is far wider than 19 x sqrt(.02) = 2.7 allows: the ladder is not believed.
        val late = GameState(TailSport.BASKETBALL, 60, 60, 0.02)
        assertTrue(LiveLightCurve.scan(strikes.map { honest(it) } + point("alt", 210.5, 0.60, 0.43), "NBA", late).isEmpty())
    }

    @Test
    fun `the score model vetoes the curve, and a strike beyond the liquid lines is not extrapolated`() {
        val ladder = strikes.map { honest(it) } + point("alt", 210.5, overAsk = 0.60, underAsk = 0.43)
        assertTrue(LiveLightCurve.scan(ladder, "NBA", state, scoreFair = mapOf("alt-o" to 0.01)).isEmpty())
        assertEquals(1, LiveLightCurve.scan(ladder, "NBA", state, scoreFair = mapOf("alt-o" to 0.55)).size)
        val far = strikes.map { honest(it) } + point("far", 150.5, overAsk = 0.60, underAsk = 0.43)
        assertTrue(LiveLightCurve.scan(far, "NBA", state).isEmpty())
        assertNotNull(LiveLightCurve.expectedSigma(com.tjshea.vigilant.data.novig.burst.LadderKind.TOTAL, "NBA", state))
        assertNull(LiveLightCurve.expectedSigma(com.tjshea.vigilant.data.novig.burst.LadderKind.TOTAL, "CRICKET", state))
    }
}
