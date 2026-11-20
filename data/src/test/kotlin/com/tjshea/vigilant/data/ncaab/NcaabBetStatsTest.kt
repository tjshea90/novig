package com.tjshea.vigilant.data.ncaab

import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.data.tracker.SlateProfit
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** NT1 (Tj 2026-11-20): the record of the bets placed by hand from the NCAAB tab, and only those. */
class NcaabBetStatsTest {
    private fun et(d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(2026, 11, d, h, mi).atZone(SlateProfit.ET).toInstant().toEpochMilli()
    private val now = et(20, 12)

    // cost 0.5: a win pays the stake again, a loss loses it.
    private fun bet(id: String, starts: Long, stake: Double, status: BetStatus, label: String = "Moneyline", sel: String = "Duke", origin: String? = BetTracker.ORIGIN_NCAAB, auto: Boolean = false, maker: Boolean = false,
                    basis: FairBasis? = null, ev: Double = 0.04, settleValue: Double? = null) =
        TrackedBet(id, starts - 3_600_000L, "NCAAB", "A @ B", starts, label, sel, "m$id", "o$id", 0.5, 0.5, 0.52, ev, stake, status, settledAtMs = starts + 2 * 3_600_000L, origin = origin, auto = auto, maker = maker,
            fairBasis = basis, settleValue = settleValue)

    @Test fun aBetIsTheNcaabTabsByItsOriginOrByTheFairBasisTabBetsCarriedBeforeIt() {
        assertTrue(NcaabBetStats.isNcaabTab(bet("a", et(20, 19), 5.0, BetStatus.PENDING)))
        assertTrue("placed from the tab before the origin was kept", NcaabBetStats.isNcaabTab(bet("b", et(20, 19), 5.0, BetStatus.PENDING, origin = null, basis = FairBasis(FairBasis.SOURCE_NCAAB))))
        assertFalse("a Vigilant +EV card bet on a college game is not a tab bet", NcaabBetStats.isNcaabTab(bet("c", et(20, 19), 5.0, BetStatus.PENDING, origin = null, basis = FairBasis("PROPLINE"))))
        assertFalse(NcaabBetStats.isNcaabTab(bet("d", et(20, 19), 5.0, BetStatus.PENDING, origin = null)))
    }

    @Test fun anAutomaticBetABidAndAVoidAreNotCounted() {
        val all = listOf(bet("a", et(20, 19), 5.0, BetStatus.PENDING), bet("auto", et(20, 19), 5.0, BetStatus.PENDING, auto = true), bet("bid", et(20, 19), 5.0, BetStatus.PENDING, maker = true),
            bet("void", et(20, 19), 5.0, BetStatus.VOID), bet("other", et(20, 19), 5.0, BetStatus.PENDING, origin = null))
        assertEquals(listOf("a"), NcaabBetStats.mine(all).map { it.id })
        assertEquals(1, NcaabBetStats.summarize(all, now).voided)
    }

    @Test fun theTotalsAreTheMoneyBetTheProfitTheRecordAndThePercentWon() {
        val bets = listOf(
            bet("w1", et(19, 19), 10.0, BetStatus.WON), bet("w2", et(19, 20), 20.0, BetStatus.WON), bet("l1", et(19, 21), 10.0, BetStatus.LOST),
            bet("p1", et(19, 21), 5.0, BetStatus.PUSH), bet("o1", et(21, 19), 15.0, BetStatus.PENDING),
        )
        val t = NcaabBetStats.totalsOf(bets)
        assertEquals(5, t.bets); assertEquals(2, t.won); assertEquals(1, t.lost); assertEquals(1, t.pushed); assertEquals(1, t.open)
        assertEquals(60.0, t.staked, 1e-9); assertEquals(45.0, t.settledStaked, 1e-9); assertEquals(15.0, t.openStaked, 1e-9)
        assertEquals("a win pays the stake again, a loss loses it, a push returns it: 10 + 20 - 10 + 0", 20.0, t.profit, 1e-9)
        assertEquals("2-1-1", t.record); assertEquals(2.0 / 3.0, t.winPct!!, 1e-9)
        assertEquals(20.0 / 45.0, t.roi!!, 1e-9); assertEquals(65.0, t.returned, 1e-9)
        assertEquals(12.0, t.avgStake!!, 1e-9); assertEquals(20.0, t.biggestWin!!, 1e-9); assertEquals(-10.0, t.biggestLoss!!, 1e-9)
    }

    @Test fun anOpenBetIsMoneyAtStakeNeverALossAndNothingDecidedHasNoPercent() {
        val t = NcaabBetStats.totalsOf(listOf(bet("o", et(21, 19), 25.0, BetStatus.PENDING)))
        assertEquals(1, t.bets); assertEquals(25.0, t.staked, 1e-9); assertEquals(25.0, t.openStaked, 1e-9)
        assertEquals(0.0, t.profit, 1e-9); assertNull(t.winPct); assertNull(t.roi); assertNull(t.biggestWin)
        val none = NcaabBetStats.totalsOf(emptyList())
        assertEquals(0, none.bets); assertNull(none.avgStake)
    }

    @Test fun daysAreTheGameDaysOfTheTrackerNewestFirstAndTodayIsTodaysGames() {
        val all = listOf(
            bet("a", et(18, 19), 10.0, BetStatus.WON),
            // 10:30 pm Eastern on the 19th, graded after midnight: still the 19th's
            bet("b", et(19, 22, 30), 10.0, BetStatus.LOST),
            bet("c", et(20, 19), 10.0, BetStatus.PENDING), bet("d", et(20, 15), 30.0, BetStatus.WON),
            bet("far", et(22, 19), 5.0, BetStatus.PENDING),
        )
        val s = NcaabBetStats.summarize(all, now)
        assertEquals(listOf(LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 18)), s.days.map { it.date })
        assertEquals(LocalDate.of(2026, 11, 18), s.since)
        assertEquals("today's games: the 3 pm win and the open 7 pm bet", 2, s.today.bets); assertEquals(30.0, s.today.profit, 1e-9); assertEquals(40.0, s.today.staked, 1e-9)
        assertEquals(5, s.all.bets); assertEquals(30.0, s.all.profit, 1e-9)
        assertEquals("the days add up to the all-time figure", s.all.profit, s.days.sumOf { it.totals.profit }, 1e-9)
        assertEquals(s.all.staked, s.days.sumOf { it.totals.staked }, 1e-9)
        assertEquals(30.0, s.best!!.profit!!, 1e-9); assertEquals(-10.0, s.worst!!.profit!!, 1e-9)
    }

    @Test fun todayIsZeroWhenNoTabBetIsOnTodaysGames() {
        val s = NcaabBetStats.summarize(listOf(bet("a", et(18, 19), 10.0, BetStatus.WON)), now)
        assertEquals(0, s.today.bets); assertEquals(0.0, s.today.profit, 1e-9); assertEquals(0.0, s.today.staked, 1e-9)
        assertTrue(NcaabBetStats.summarize(emptyList(), now).isEmpty)
    }

    @Test fun everyBetCountsWhateverItsEvWasWhenPlacedAndAFairValueSettlementIsWonOrLostByItsProfit() {
        val outlier = bet("x", et(19, 19), 10.0, BetStatus.WON, ev = 0.9)           // an EV so far out that the Tracker's other stats leave it out
        assertEquals(1, NcaabBetStats.totalsOf(NcaabBetStats.mine(listOf(outlier))).won)
        val up = bet("f1", et(19, 19), 10.0, BetStatus.FMV, settleValue = 0.8)      // 10 / 0.5 x 0.8 - 10 = +6
        val down = bet("f2", et(19, 19), 10.0, BetStatus.FMV, settleValue = 0.2)
        assertEquals(NcaabBetStats.Result.WON, NcaabBetStats.resultOf(up)); assertEquals(NcaabBetStats.Result.LOST, NcaabBetStats.resultOf(down))
    }

    @Test fun marketsSplitTheTabBetsByKindMostBetsFirst() {
        val all = listOf(
            bet("m1", et(19, 19), 10.0, BetStatus.WON, label = "Moneyline", sel = "Duke"), bet("m2", et(19, 20), 10.0, BetStatus.LOST, label = "Moneyline", sel = "Kansas"),
            bet("s1", et(19, 21), 10.0, BetStatus.WON, label = "Spread", sel = "Duke -3.5"),
        )
        val m = NcaabBetStats.summarize(all, now).markets
        assertEquals(listOf("Moneyline", "Spread"), m.map { it.market })
        assertEquals(2, m[0].totals.bets); assertEquals("1-1", m[0].totals.record); assertEquals(1, m[1].totals.bets)
    }
}
