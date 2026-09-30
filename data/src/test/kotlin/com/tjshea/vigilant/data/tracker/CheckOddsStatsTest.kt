package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Tracker's "Check odds now" counter (Tj, 2026-09-29): open bets +EV and −EV now, the share that's +EV, and their average EV without the
 * ones over ±5%; only bets re-read since the check began, so each new check starts from 0.
 */
class CheckOddsStatsTest {

    private val start = 1_800_000_000_000L

    /** A few seconds into the check; every game below starts an hour after the check. */
    private val now = start + 5_000

    private fun bet(id: String, ev: Double?, readAt: Long? = start + 1_000, status: BetStatus = BetStatus.PENDING, startsTs: Long = start + 3_600_000L) = TrackedBet(
        id, start - 86_400_000L, "NBA", "A @ B", startsTs, "Moneyline", "A", "m-$id", "o-$id", 0.5, 0.5, 0.52, 0.04, 5.0,
        status = status, nowEv = ev, nowAtMs = readAt,
    )

    @Test
    fun `counts the open bets re-read in this check by +EV and −EV, with the share that's +EV`() {
        val s = CheckOddsStats.of(listOf(bet("a", 0.021), bet("b", 0.004), bet("c", -0.013), bet("d", 0.03)), start, now)
        assertEquals(3, s.positive)
        assertEquals(1, s.negative)
        assertEquals(0, s.even)
        assertEquals(4, s.priced)
        assertEquals(0.75, s.positiveShare!!, 1e-9)
    }

    @Test
    fun `a new check starts from zero, reads from before it began don't count`() {
        val bets = listOf(bet("old+", 0.02, readAt = start - 1), bet("old-", -0.02, readAt = start - 60_000), bet("never", null, readAt = null))
        val s = CheckOddsStats.of(bets, start, now)
        assertEquals(CheckOddsStats.EMPTY, s)
        assertNull(s.positiveShare)
        assertNull(s.averageEv)
        // As this check's reads are saved, the count goes up.
        val live = CheckOddsStats.of(bets + bet("new", 0.01, readAt = start), start, now)
        assertEquals(1, live.positive)
        assertEquals(1, live.priced)
    }

    @Test
    fun `settled bets never count, even when their odds were read in this check`() {
        val s = CheckOddsStats.of(
            listOf(bet("won", 0.02, status = BetStatus.WON), bet("lost", -0.03, status = BetStatus.LOST), bet("void", 0.01, status = BetStatus.VOID), bet("open", -0.01)),
            start, now,
        )
        assertEquals(0, s.positive)
        assertEquals(1, s.negative)
    }

    @Test
    fun `the average leaves out EVs over 5 percent either way, but they still count as + or −`() {
        val s = CheckOddsStats.of(listOf(bet("a", 0.02), bet("b", -0.01), bet("big+", 0.12), bet("big-", -0.08), bet("edge+", 0.05), bet("edge-", -0.05)), start, now)
        assertEquals(3, s.positive)
        assertEquals(3, s.negative)
        assertEquals(2, s.outliers)
        assertEquals(4, s.averaged)
        // (0.02 − 0.01 + 0.05 − 0.05) / 4: exactly 5% is not over 5%, so it stays in.
        assertEquals(0.0025, s.averageEv!!, 1e-12)
    }

    @Test
    fun `when every EV is an outlier there is no average, and a bet at exactly fair value is neither`() {
        val s = CheckOddsStats.of(listOf(bet("a", 0.2), bet("b", -0.3)), start, now)
        assertNull(s.averageEv)
        assertEquals(2, s.outliers)
        val even = CheckOddsStats.of(listOf(bet("fair", 0.0), bet("a", 0.01)), start, now)
        assertEquals(1, even.even)
        assertEquals(1, even.positive)
        assertEquals(0.5, even.positiveShare!!, 1e-9)
        assertEquals(0.005, even.averageEv!!, 1e-12)
    }

    @Test
    fun `a bet whose game has started is left out of every number, and counted apart`() {
        // Tj, 2026-09-30: "does not count any bets in which the game or bet is currently live. The odds move rapidly when a game is live".
        val underWay = bet("live", 0.30, startsTs = start - 60 * 60_000L)
        val startsNow = bet("atStart", -0.20, startsTs = now)
        val s = CheckOddsStats.of(listOf(bet("a", 0.02), bet("b", -0.01), underWay, startsNow), start, now)
        assertEquals(1, s.positive)
        assertEquals(1, s.negative)
        assertEquals(2, s.priced)
        assertEquals(0.5, s.positiveShare!!, 1e-9)
        assertEquals(0.005, s.averageEv!!, 1e-12)
        assertEquals(0, s.outliers)
        assertEquals(2, s.live)
        // Only live bets re-read: nothing to count, and it still says how many were left out.
        val onlyLive = CheckOddsStats.of(listOf(underWay), start, now)
        assertEquals(0, onlyLive.priced)
        assertNull(onlyLive.averageEv)
        assertEquals(1, onlyLive.live)
        // A game that starts after the check drops out of the counter from then on.
        val later = CheckOddsStats.of(listOf(bet("a", 0.02), bet("b", -0.01)), start, start + 3_600_000L)
        assertEquals(0, later.priced)
        assertEquals(2, later.live)
    }
}
