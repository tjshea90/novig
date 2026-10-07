package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.live.FeedRace.NovigTick
import com.tjshea.vigilant.data.live.FeedRace.OddsTick
import com.tjshea.vigilant.data.live.FeedRace.Sighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The feed race's analysis (RESEARCH.md §99, §106): the research tool's own self-test, ported, plus what the in-app version adds. */
class FeedRaceTest {

    private fun s(src: String, tSec: Int, h: Int, a: Int, init: Boolean = false, id: String = "1", home: String = "Spain", away: String = "Croatia") =
        Sighting(src, id, home, away, h, a, tSec * 1000L, init = init)

    private fun tick(tSec: Int, o: String, p: Double, q: Long = 100, ev: String = "Croatia @ Spain") = NovigTick(ev, "m", o, p, q, tSec * 1000L)

    @Test
    fun `team names match across feeds in either order, accents and city words aside`() {
        assertTrue(FeedRace.sameGame("Los Angeles Dodgers" to "Atlanta Braves", "Atlanta Braves" to "Los Angeles Dodgers"))
        assertTrue(FeedRace.sameGame("Montréal Canadiens" to "Carolina Hurricanes", "Montreal" to "Hurricanes"))
        assertFalse(FeedRace.sameGame("Manchester United" to "Newcastle United", "Manchester City" to "Chelsea"))
        assertTrue(FeedRace.sameGame("Czechia" to "England", "England" to "Czechia"))
        // Tennis players: last names.
        assertTrue(FeedRace.sameGame("Marco Trungelliti" to "Rei Sakamoto", "Rei Sakamoto" to "Marco Trungelliti"))
    }

    @Test
    fun `the tracker records a baseline then only changes`() {
        val out = ArrayList<Sighting>()
        val tr = FeedRace.Tracker("x") { out += it }
        tr.see("1", "A", "B", 0, 0, 10_000)
        tr.see("1", "A", "B", 0, 0, 11_000)
        tr.see("1", "A", "B", 1, 0, 12_000)
        assertEquals(2, out.size)
        assertTrue(out[0].init && !out[1].init)
        assertEquals(12_000L, out[1].atMs)
    }

    @Test
    fun `two feeds, one goal, and Novig moves 5 seconds after the faster feed - lags, lead and what traded at the old price`() {
        val scores = listOf(s("fast", 100, 0, 0, true), s("slow", 100, 0, 0, true), s("fast", 200, 1, 0), s("slow", 207, 1, 0))
        val games = FeedRace.cluster(scores)
        assertEquals(1, games.size)
        assertEquals(mapOf((1 to 0) to mapOf("fast" to 200_000L, "slow" to 207_000L)), FeedRace.transitions(games.single()))
        val trades = buildList {
            for (i in 0 until 40 step 4) { add(tick(150 + i, "o1", 0.50)); add(tick(150 + i, "o2", 0.50)) }
            add(tick(205, "o1", 0.60)); add(tick(206, "o1", 0.62))
            // The other outcome falls from 0.50 to 0.40 at 205 s, but a stale bid at 0.50 is hit at 201 s and 203 s, 1,000 and 500 contracts.
            add(tick(201, "o2", 0.50, 1000)); add(tick(203, "o2", 0.50, 500)); add(tick(205, "o2", 0.40)); add(tick(206, "o2", 0.38))
        }
        val ser = FeedRace.novigSeries(trades, "Spain" to "Croatia")
        val mv = FeedRace.firstMove(ser, 200.0, 0.03)!!
        assertEquals(205.0, mv.atSec, 1e-6)
        val st = FeedRace.staleFills(ser, mv.refs, 200.0, mv.atSec, 0.03)
        assertEquals(2, st.trades); assertEquals(15.0, st.payout, 1e-9); assertTrue(st.gain > 0)
        // A slower feed (202 s) had only the later fill.
        val st2 = FeedRace.staleFills(ser, mv.refs, 202.0, mv.atSec, 0.03)
        assertEquals(1, st2.trades); assertEquals(5.0, st2.payout, 1e-9)

        val r = FeedRace.report(scores, trades)
        assertEquals(2, r.lags.size)
        val slow = r.lags.single { it.src == "slow" }
        assertEquals(7.0, slow.median, 1e-9)
        assertEquals(0, slow.first)
        assertEquals(1, r.lags.single { it.src == "fast" }.first)
        assertEquals(1, r.moved)
        assertEquals(5.0, r.leads.single { it.src == "fast" }.median, 1e-9)
        assertEquals(2.0, r.leads.single { it.src == "slow" }.median, 1e-9)
        assertEquals(1, r.leads.single { it.src == "fast" }.by3s)
        assertEquals(0, r.leads.single { it.src == "slow" }.by3s)
        assertEquals(2, r.stale.single { it.src == "fast" }.trades)
        assertEquals(1, r.stale.single { it.src == "slow" }.trades)
        assertTrue(r.lines().joinToString("\n"), r.lines().any { it.contains("fast") && it.contains("by 3 s+ 1") })
    }

    @Test
    fun `a feed with no move to compare is left out and the verdict says there is not enough yet`() {
        val r = FeedRace.report(listOf(s("a", 1, 0, 0, true), s("a", 50, 1, 0)), emptyList())
        assertEquals(1, r.newScores); assertEquals(0, r.seenByTwo); assertTrue(r.lags.isEmpty())
        assertTrue(r.verdict(), r.verdict().contains("Too few moves"))
        assertEquals("Live feed test: nothing recorded yet (it needs live games).", FeedRace.report(emptyList(), emptyList()).verdict())
        assertTrue(FeedRace.report(listOf(s("a", 1, 0, 0, true)), emptyList()).verdict().contains("no score has changed yet"))
    }

    @Test
    fun `the verdict names a feed only when it was ahead of Novig by 3 seconds or more in most of enough scores`() {
        // Eight games, each scores at t = 1000 + 100 g seconds; feed "early" sees it at t, feed "late" 10 s later; Novig's price moves 6 s after "early".
        val scores = ArrayList<Sighting>()
        val trades = ArrayList<NovigTick>()
        for (g in 0 until 8) {
            val id = "g$g"; val home = "Home$g"; val away = "Away$g"; val t = 1_000 + 100 * g
            scores += s("early", t - 60, 0, 0, true, id, home, away); scores += s("late", t - 60, 0, 0, true, id, home, away)
            scores += s("early", t, 1, 0, false, id, home, away); scores += s("late", t + 10, 1, 0, false, id, home, away)
            for (i in 0 until 40 step 4) trades += tick(t - 45 + i, "o1", 0.50, ev = "Away$g @ Home$g").copy(marketId = "m$g")
            trades += tick(t + 6, "o1", 0.58, ev = "Away$g @ Home$g").copy(marketId = "m$g")
        }
        val r = FeedRace.report(scores, trades)
        assertEquals(8, r.moved)
        assertEquals(6.0, r.leads.single { it.src == "early" }.median, 1e-9)
        assertEquals(-4.0, r.leads.single { it.src == "late" }.median, 1e-9)
        assertTrue(r.verdict(), r.verdict().contains("early showed the score before Novig's price moved in 8 of 8, by 6.0 s at the median"))
        // The same eight with Novig 1 s behind "early" and 9 s ahead of "late": no feed leads.
        val slowTrades = trades.map { if (it.price > 0.55) it.copy(tsMs = it.tsMs - 5_000) else it }
        assertTrue(FeedRace.report(scores, slowTrades).verdict(), FeedRace.report(scores, slowTrades).verdict().contains("No feed was ahead of Novig's price"))
    }

    @Test
    fun `tennis scores are told apart across sets by folding the set number into the games`() {
        // Set 1 ends 6-4 then set 2 starts 0-0: the totals only ever rise, so the new set is a play, not a repeat of 0-0.
        val a = FeedRace.Game("Marco Trungelliti" to "Rei Sakamoto")
        a.recs += Sighting("poly", "t", "Marco Trungelliti", "Rei Sakamoto", 5, 4, 1_000)
        a.recs += Sighting("poly", "t", "Marco Trungelliti", "Rei Sakamoto", 6, 4, 2_000)
        a.recs += Sighting("poly", "t", "Marco Trungelliti", "Rei Sakamoto", 100, 100, 3_000)
        assertEquals(setOf(6 to 4, 100 to 100), FeedRace.transitions(a).keys)
    }

    @Test
    fun `Polymarket's odds are compared with Novig - when its mid moved against when Novig's price did`() {
        val scores = listOf(s("sofa", 100, 0, 0, true, "1", "Zhizhen Zhang", "Tomas Machac"), s("sofa", 200, 1, 0, false, "1", "Zhizhen Zhang", "Tomas Machac"))
        val trades = buildList {
            for (i in 0 until 40 step 4) add(tick(150 + i, "o1", 0.50, ev = "Tomas Machac @ Zhizhen Zhang"))
            add(tick(206, "o1", 0.58, ev = "Tomas Machac @ Zhizhen Zhang"))
        }
        // Polymarket's mid for Zhang sat at 0.50 and jumped to 0.57 at 202 s (4 s before Novig's 206 s).
        val odds = buildList {
            for (i in 0 until 40 step 4) add(OddsTick("Shanghai Rolex Masters: Zhizhen Zhang vs Tomas Machac", "Zhizhen Zhang", 0.50, (150 + i) * 1000L))
            add(OddsTick("Shanghai Rolex Masters: Zhizhen Zhang vs Tomas Machac", "Zhizhen Zhang", 0.57, 202_000L))
        }
        assertEquals(202.0, FeedRace.polyMoveSec(odds, "Zhizhen Zhang" to "Tomas Machac", 200.0, 0.03)!!, 1e-9)
        assertNull(FeedRace.polyMoveSec(odds, "Other Player" to "Another One", 200.0, 0.03))
        val r = FeedRace.report(scores, trades, odds)
        val lead = r.odds
        assertNotNull(lead)
        assertEquals(4.0, lead!!.median, 1e-9)
        assertEquals(1, lead.before); assertEquals(1, lead.by3s)
        assertTrue(r.lines().joinToString("\n"), r.lines().any { it.startsWith("Polymarket odds against Novig: 1 scores") })
    }
}
