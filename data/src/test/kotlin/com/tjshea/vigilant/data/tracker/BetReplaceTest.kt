package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.SlipStake
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Replace button: the exact bet in Novig's bet slip, with the amount Settings asks for (Tj, 2026-09-29). */
class BetReplaceTest {

    private fun bet(outcomeId: String = "out-1", nowFair: Double? = null, nowAmerican: Int? = null, cost: Double = 0.4, price: Double = 0.4) = TrackedBet(
        "b", 0, "NFL", "A @ B", 1_000, "Moneyline", "Dallas Cowboys", "mkt-1", outcomeId, price = price, cost = cost,
        fairAtBet = 0.44, evPercentAtBet = 0.1, stake = 10.0, american = 150, nowFair = nowFair, nowAmerican = nowAmerican,
    )

    @Test
    fun `the link is the bet's own outcome in Novig's bet slip, with Settings' amount`() {
        assertEquals("novigapp://events/out-1", BetReplace.link(bet(), ScanSettings(slipStake = SlipStake.OFF)))
        assertEquals("novigapp://events/out-1/novig/1", BetReplace.link(bet(), ScanSettings(slipStake = SlipStake.ONE_DOLLAR)))
        assertEquals("novigapp://events/out-1/novig/12.5", BetReplace.link(bet(), ScanSettings(slipStake = SlipStake.CUSTOM, slipCustomStake = 12.5)))
    }

    @Test
    fun `a bet whose outcome isn't known has no link yet, and a link found later gets the same amount`() {
        val s = ScanSettings(slipStake = SlipStake.ONE_DOLLAR)
        assertNull(BetReplace.link(bet(outcomeId = ""), s))
        assertEquals("novigapp://events/x/cno/1", BetReplace.withStake("novigapp://events/x/cno", bet(outcomeId = ""), s))
        // A game link (no bet slip) is left as it is.
        assertEquals("novigapp://event-markets/g", BetReplace.withStake("novigapp://event-markets/g", bet(), s))
    }

    @Test
    fun `Kelly is worked from the fair price now against the price now, and has nothing to say without a fair price`() {
        val s = ScanSettings(slipStake = SlipStake.KELLY, bankroll = 1000.0, kellyMultiplier = 0.25)
        // Fair 44% against a 40% cost: Kelly fraction (0.44 - 0.4) / 0.6 = 6.67%; a quarter of $1000 x that = $16.67.
        assertEquals(16.67, BetReplace.stake(bet(nowFair = 0.44), s)!!, 0.01)
        // Novig's price now is longer (+200 = 33.3%): a bigger edge, a bigger stake.
        assertEquals(true, BetReplace.stake(bet(nowFair = 0.44, nowAmerican = 200), s)!! > 16.67)
        // No edge now: nothing to bet, so nothing filled in.
        assertNull(BetReplace.stake(bet(nowFair = 0.35), s))
        // No fair price at all (an imported bet): the fair price when bet is used if it has one.
        assertEquals(16.67, BetReplace.stake(bet(), s)!!, 0.01)
        assertNull(BetReplace.stake(bet().copy(fairAtBet = null), s))
    }
}
