package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.live
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.money
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.parse
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule that decides a bet: every skip is pinned on its own (change one input and only that reason fires). */
class LiveEdgeTest {
    private val rules = LiveRules()
    private val fee = MarketFee.GAME

    /** Pinnacle moved the home moneyline from -110/-110 to -150/+125 at t=1,000; the book has been quiet since. */
    private fun moved(limit: Int = 2000, from: Pair<Int, Int> = -110 to -110, to: Pair<Int, Int> = -150 to 125, isLive: Boolean = true, method: DevigMethod = DevigMethod.MULTIPLICATIVE): Triple<PinnBook, PinnEvent, PinnLine> {
        val b = PinnBook(method)
        b.apply(parse(live(isLive = isLive, markets = arrayOf(money(1, from.first, from.second, limit = limit)))), 0)
        b.apply(parse(live(isLive = isLive, markets = arrayOf(money(2, to.first, to.second, limit = limit)))), 1_000)
        val e = b.events.getValue(1)
        return Triple(b, e, e.lines.getValue("s;0;m"))
    }

    private fun judge(e: PinnEvent, l: PinnLine, now: Long, ladder: List<TakeLevel>, r: LiveRules = rules, side: PinnSide = PinnSide.HOME, live: Boolean = true) =
        LiveEdge.judge(e, l, side, now, ladder, fee, live, r)

    private val stale = listOf(TakeLevel(0.50, 5_000), TakeLevel(0.52, 5_000))

    private fun assertSkip(reason: String, v: LiveVerdict) = assertEquals(reason, (v as? LiveVerdict.Skip)?.reason ?: "BET")

    @Test
    fun `a Pinnacle move that Novig has not followed is a bet, with the EV after the fee`() {
        val (_, e, l) = moved()
        val v = judge(e, l, 2_000, stale) as LiveVerdict.Bet
        val fair = l.fair.getValue(PinnSide.HOME)
        assertEquals(fair, v.fair, 0.0)
        assertEquals(0.50, v.ask, 0.0)
        assertEquals(EvMath.quote(fair, 0.50, fee, true).evPercent, v.ev, 1e-12)
        assertEquals(0.03 * 0.5 * 0.5, v.fee, 1e-12)
        assertTrue("the move is the fair now less the fair 20 s ago", v.move!! > 0.05)
        assertEquals(1_000L, v.stableMs)
        // Both levels are still +EV at the minimum, so both are offered, and the limit is the dearer one.
        assertEquals(10_000L, v.contracts)
        assertEquals(0.52, v.limitPrice, 0.0)
    }

    @Test
    fun `the depth stops where the EV drops under the minimum`() {
        val (_, e, l) = moved()
        val v = judge(e, l, 2_000, listOf(TakeLevel(0.50, 3_000), TakeLevel(0.60, 9_000))) as LiveVerdict.Bet
        assertEquals("the 0.60 level is not +EV enough", 3_000L, v.contracts)
        assertEquals(0.50, v.limitPrice, 0.0)
    }

    @Test
    fun `no recent Pinnacle move toward the side is no bet however good the price looks`() {
        val (_, e, l) = moved()
        // 30 s later the window (20 s) no longer reaches the move.
        assertSkip(LiveSkip.NO_MOVE, judge(e, l, 31_000, stale))
        // The side that Pinnacle moved away from.
        assertSkip(LiveSkip.NO_MOVE, judge(e, l, 2_000, stale, side = PinnSide.AWAY))
        // Standing disagreements are allowed only when Tj turns the requirement off.
        assertTrue(judge(e, l, 31_000, stale, rules.copy(requireMove = false)) is LiveVerdict.Bet)
    }

    @Test
    fun `a move smaller than the minimum is not a lag`() {
        val (_, e, l) = moved(from = -150 to 125, to = -152 to 127)
        assertSkip(LiveSkip.NO_MOVE, judge(e, l, 2_000, stale))
    }

    @Test
    fun `an edge under the minimum after the fee is no bet`() {
        val (_, e, l) = moved()
        val fair = l.fair.getValue(PinnSide.HOME)
        // A price whose EV is just under 3% and one just over it, on the same line.
        fun askFor(ev: Double): Double = (1..999).map { it / 1000.0 }.last { EvMath.quote(fair, it, fee, true).evPercent >= ev }
        val ok = askFor(0.031)
        val bad = ok + 0.002
        assertTrue(judge(e, l, 2_000, listOf(TakeLevel(ok, 1_000))) is LiveVerdict.Bet)
        assertSkip(LiveSkip.EV, judge(e, l, 2_000, listOf(TakeLevel(bad, 1_000))))
    }

    @Test
    fun `the fee is part of the price - the same ask is a bet pregame and not in play`() {
        val (_, e, l) = moved(isLive = false)
        val fair = l.fair.getValue(PinnSide.HOME)
        // An ask whose EV is 3.2% with no fee and under 2% with the in-play fee.
        val ask = fair / 1.032
        val r = rules.copy(pregame = true)
        assertTrue(judge(e, l, 2_000, listOf(TakeLevel(ask, 1_000)), r, live = false) is LiveVerdict.Bet)
        assertSkip(LiveSkip.EV, judge(e, l, 2_000, listOf(TakeLevel(ask, 1_000)), r, live = true))
    }

    @Test
    fun `a game that is not live is left alone unless pregame is on`() {
        val (_, e, l) = moved(isLive = false)
        assertSkip(LiveSkip.PREGAME, judge(e, l, 2_000, stale))
        assertTrue(judge(e, l, 2_000, stale, rules.copy(pregame = true), live = false) is LiveVerdict.Bet)
    }

    @Test
    fun `a price that has not settled, a danger zone, a closed line and a wide margin each stop it`() {
        val (b, e, l) = moved()
        assertSkip(LiveSkip.SETTLING, judge(e, l, 1_300, stale))
        b.apply(parse(live(channel = "dz", markets = emptyArray<String>())), 2_000)
        assertSkip(LiveSkip.VOLATILE, judge(e, l, 3_000, stale))
        assertTrue("the danger zone passes", judge(e, l, 2_000 + PinnBook.VOLATILE_MS + 1, stale) is LiveVerdict.Bet)
        val (_, e2, l2) = moved(from = -110 to -110, to = -190 to 150)
        l2.overround.let { assertTrue(it < 0.09) }
        val wide = rules.copy(maxOverround = 0.01)
        assertSkip(LiveSkip.OVERROUND, judge(e2, l2, 2_000, stale, wide))
        l.open = false
        assertSkip(LiveSkip.CLOSED, judge(e, l, 2_000, stale))
    }

    @Test
    fun `extreme prices and a low Pinnacle limit are left alone`() {
        val (_, e, l) = moved(from = -800 to 600, to = -3000 to 1500)
        assertSkip(LiveSkip.EXTREME, judge(e, l, 2_000, listOf(TakeLevel(0.80, 1_000)), side = PinnSide.HOME))
        val (_, e2, l2) = moved(limit = 40)
        assertSkip(LiveSkip.LIMIT, judge(e2, l2, 2_000, stale))
    }

    @Test
    fun `no offer and a thin offer are named`() {
        val (_, e, l) = moved()
        assertSkip(LiveSkip.NO_OFFER, judge(e, l, 2_000, emptyList()))
        assertSkip(LiveSkip.THIN, judge(e, l, 2_000, listOf(TakeLevel(0.50, 10))))
    }

    @Test
    fun `the worst-case devig is never kinder than the others`() {
        val (_, _, lw) = moved(method = DevigMethod.WORST_CASE)
        val (_, _, lm) = moved(method = DevigMethod.MULTIPLICATIVE)
        assertTrue(lw.fair.getValue(PinnSide.HOME) <= lm.fair.getValue(PinnSide.HOME) + 1e-12)
    }
}
