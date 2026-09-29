package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.TrackerText
import com.tjshea.vigilant.app.ui.parseAmerican
import com.tjshea.vigilant.data.tracker.BetInsight
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.tracker.TrackerStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Tracker's sentences: what an open bet is waiting for, what results mean (Tj, 2026-09-29). */
class TrackerTextTest {

    private val now = 1_790_400_000_000L
    private val hour = 3_600_000L

    private fun bet(
        id: String = "b", startsTs: Long = now + 3 * hour, status: BetStatus = BetStatus.PENDING, note: String? = null, manual: Boolean = false,
        noteAt: Long? = null, settledBy: String? = null, american: Int = 150, cost: Double = 0.4, nowFair: Double? = null, fairAtBet: Double? = 0.42,
    ) = TrackedBet(
        id, now - hour, "NFL", "A @ B", startsTs, "Moneyline", "A", "m", "o", cost, cost, fairAtBet, 0.05, 10.0, status,
        american = american, gradeNote = note, gradeManual = manual, gradeAtMs = noteAt, settledBy = settledBy, nowFair = nowFair,
    )

    @Test
    fun `when a game starts, in words`() {
        assertEquals("Starts in 1m", TrackerText.startsIn(now + 10_000, now))
        assertEquals("Starts in 45m", TrackerText.startsIn(now + 45 * 60_000L, now))
        assertEquals("Starts in 3h 20m", TrackerText.startsIn(now + 3 * hour + 20 * 60_000L, now))
        assertEquals(true, TrackerText.startsIn(now + 30 * hour, now).startsWith("Starts "))
        assertEquals("Started 2h ago", TrackerText.startsIn(now - 2 * hour, now))
    }

    @Test
    fun `what an open bet is waiting for, and when it needs Tj`() {
        assertNull(TrackerText.awaiting(bet(), now)) // hasn't started
        assertNull(TrackerText.awaiting(bet(status = BetStatus.WON, startsTs = now - hour), now)) // settled
        assertEquals(TrackerText.Status("In progress: graded from the final score once it's over", TrackerText.Tone.WAITING), TrackerText.awaiting(bet(startsTs = now - 20 * 60_000L), now))
        assertEquals(TrackerText.Status("Waiting for the final score…", TrackerText.Tone.WAITING), TrackerText.awaiting(bet(startsTs = now - 5 * hour), now))
        val over = TrackerText.awaiting(bet(startsTs = now - 5 * hour, note = "The game isn't over yet", noteAt = now - 5 * 60_000L), now)!!
        assertEquals("The game isn't over yet · checked 5m ago", over.text)
        assertEquals(TrackerText.Tone.WAITING, over.tone)
        val manual = TrackerText.awaiting(bet(startsTs = now - 5 * hour, note = "Player X isn't in the box score: mark it yourself", manual = true, noteAt = now), now)!!
        assertEquals(TrackerText.Tone.ATTENTION, manual.tone)
        assertTrue(manual.text.startsWith("Player X isn't in the box score"))
        val undone = TrackerText.awaiting(bet(startsTs = now - 5 * hour, settledBy = "you"), now)!!
        assertEquals(TrackerText.Tone.ATTENTION, undone.tone)
        assertTrue(undone.text.startsWith("Auto-grading is off"))
    }

    @Test
    fun `open bets that need a look come first, then the rest that started, then the next games soonest first`() {
        val upcomingSoon = bet("soon", startsTs = now + hour)
        val upcomingLater = bet("later", startsTs = now + 30 * hour)
        val over = bet("over", startsTs = now - 5 * hour, note = "The game isn't over yet")
        val needsTap = bet("tap", startsTs = now - 2 * hour, note = "no box score", manual = true)
        val undone = bet("undone", startsTs = now - 8 * hour, settledBy = "you")
        val order = TrackerText.openOrder(listOf(upcomingLater, over, upcomingSoon, needsTap, undone), now).map { it.id }
        assertEquals(listOf("undone", "tap", "over", "soon", "later"), order)
    }

    @Test
    fun `the open list's summary counts everything Tj has open`() {
        assertEquals("No open bets", TrackerText.openSummary(emptyList(), now))
        val open = listOf(
            bet("a", cost = 0.5, startsTs = now + hour), bet("b", cost = 0.5, startsTs = now - hour), bet("c", cost = 0.5, startsTs = now - 5 * hour, note = "x", manual = true),
        )
        assertEquals("3 open · $30.00 at risk · pays $30.00 · 2 started · 1 need a tap · current EV on 0 of 1 upcoming", TrackerText.openSummary(open, now))
        // Once its odds are read, the upcoming bet counts as current (a started game never counts: it waits for a result, not odds).
        val read = listOf(open[0].copy(nowAtMs = now - 60_000L, nowFair = 0.5, nowEv = 0.1), open[1].copy(nowAtMs = now - 60_000L, nowFair = 0.5, nowEv = 0.1))
        assertEquals("2 open · $20.00 at risk · pays $20.00 · 1 started · current EV on 1 of 1 upcoming", TrackerText.openSummary(read, now))
        // A read older than the fair odds' own age limit is no longer current: 5 minutes for a game within 3 hours, 10 for a far-off one.
        val old = listOf(open[0].copy(nowAtMs = now - 6 * 60_000L, nowFair = 0.5, nowEv = 0.1))
        assertEquals("1 open · $5.00 at risk · pays $5.00 · current EV on 0 of 1 upcoming", TrackerText.openSummary(old, now))
        val farOff = listOf(open[0].copy(startsTs = now + 30 * hour, nowAtMs = now - 6 * 60_000L, nowFair = 0.5, nowEv = 0.1))
        assertEquals("1 open · $5.00 at risk · pays $5.00 · current EV on 1 of 1 upcoming", TrackerText.openSummary(farOff, now))
    }

    @Test
    fun `an upcoming bet with no EV says why - not priced yet, or the reason the last try found no fair price`() {
        val vigilant = bet("v")
        val cno = bet("c").copy(gameUrl = "https://crazyninjaodds.com/site/browse/game.aspx?side_id=1")
        assertEquals(true, TrackerText.oddsNote(vigilant, now)!!.startsWith("Not priced yet: tap Check odds now"))
        assertEquals("Odds not read yet: tap Check odds now", TrackerText.oddsNote(cno, now))
        // The reason the last pricing pass found none, with when it tried; a newer number replaces it, an older one is kept beside it.
        val tried = vigilant.copy(nowNote = "No fair-odds source lists this game", nowNoteAtMs = now - 2 * 60_000L)
        assertEquals("Not priced: No fair-odds source lists this game (tried 2m ago)", TrackerText.oddsNote(tried, now))
        val stale = tried.copy(nowFair = 0.5, nowEv = 0.1, nowAtMs = now - 3 * hour)
        assertEquals("Not priced: No fair-odds source lists this game (tried 2m ago)", TrackerText.oddsNote(stale, now))
        assertNull(TrackerText.oddsNote(tried.copy(nowFair = 0.5, nowEv = 0.1, nowAtMs = now), now))
        // Read already, started, or settled: nothing to explain.
        assertNull(TrackerText.oddsNote(cno.copy(nowFair = 0.5, nowEv = 0.1, nowAtMs = now), now))
        assertNull(TrackerText.oddsNote(cno.copy(startsTs = now - hour), now))
        assertNull(TrackerText.oddsNote(cno.copy(status = BetStatus.WON), now))
    }

    /** Tj, 2026-09-29: "the current, up to date EV, which is devigged and compared to the actual odds that I placed the bet at". */
    @Test
    fun `the EV line says now only while the read is young, against the price the bet was placed at`() {
        // Bet at +150 (cost 0.40); the devigged fair now is 0.44: EV = 0.44 / 0.40 - 1 = +10%.
        val fresh = bet(nowFair = 0.44).copy(nowEv = 0.10, nowAtMs = now - 3 * 60_000L, nowVia = BetTracker.VIA_VIGILANT, nowBooks = 5)
        val line = TrackerText.nowLine(fresh, now)!!
        assertEquals("now +10.0% EV at your +150", line.headline)
        assertEquals("fair now +127 · Vigilant's fair odds · 5 books · read 3m ago", line.detail)
        assertEquals(false, line.stale)
        val old = TrackerText.nowLine(fresh.copy(nowAtMs = now - 2 * hour, nowVia = BetTracker.VIA_CNO), now)!!
        assertEquals("+10.0% EV at your +150", old.headline)
        assertEquals("fair then +127 · CNO's books · 5 books · as of 2h ago · tap Check odds now", old.detail)
        assertEquals(true, old.stale)
        assertNull(TrackerText.nowLine(bet(), now))
        // A game far off keeps its read "now" for 10 minutes, one about to start for 5.
        assertEquals(false, TrackerText.nowLine(fresh.copy(startsTs = now + 30 * hour, nowAtMs = now - 8 * 60_000L), now)!!.stale)
        assertEquals(true, TrackerText.nowLine(fresh.copy(nowAtMs = now - 8 * 60_000L), now)!!.stale)
    }

    @Test
    fun `luck says what the gap between results and edges means`() {
        fun stats(n: Int, vs: Double, sd: Double) = TrackerStats(
            bets = n, pending = 0, settled = n, staked = n * 10.0, profit = vs, roi = null, expectedProfit = 0.0, averageEv = null, averageClv = null, beatClosePercent = null,
            profitWithEv = vs, settledWithEv = n, expectedSd = sd,
        )
        assertTrue(TrackerText.luckMessage(stats(4, 5.0, 10.0)).startsWith("Needs 10 settled bets"))
        assertTrue(TrackerText.luckMessage(stats(50, 3.0, 20.0)).startsWith("Right around expectation (+0.2σ)"))
        assertTrue(TrackerText.luckMessage(stats(50, -30.0, 20.0)).startsWith("1.5σ below expectation"))
        assertTrue(TrackerText.luckMessage(stats(50, 60.0, 20.0)).startsWith("3.0σ above expectation: running hot"))
        assertTrue(TrackerText.luckMessage(stats(50, -60.0, 20.0)).contains("unusually cold"))
    }

    @Test
    fun `the price bet at against the fair price now, in a sentence`() {
        val better = BetInsight.of(bet(nowFair = 0.44))
        assertEquals(
            "You bet +150 (40.0% implied). Fair now +127 (44.0%): 4.0 points better than fair, +10.0% EV.",
            TrackerText.edgeSentence(better),
        )
        assertEquals("The market has moved 2.0 points toward your bet since you placed it.", TrackerText.moveSentence(better))
        assertEquals("Break-even against today's fair price is +127: your +150 still clears it.", TrackerText.breakEvenSentence(better))
        val worse = BetInsight.of(bet(nowFair = 0.38))
        assertTrue(TrackerText.edgeSentence(worse).contains("2.0 points worse than fair"))
        assertEquals("The market has moved 4.0 points against your bet since you placed it.", TrackerText.moveSentence(worse))
        assertTrue(TrackerText.breakEvenSentence(worse)!!.contains("no longer clears it"))
        assertEquals("The fair price hasn't moved since you placed it.", TrackerText.moveSentence(BetInsight.of(bet(nowFair = 0.42))))
        assertTrue(TrackerText.edgeSentence(BetInsight.of(bet(fairAtBet = null))).endsWith("No fair price read yet: tap Re-read books."))
        assertNull(TrackerText.moveSentence(BetInsight.of(bet(fairAtBet = null, nowFair = 0.44))))
    }

    @Test
    fun `typed odds are American, with either minus sign, and nothing inside ±100`() {
        assertEquals(150, parseAmerican("+150"))
        assertEquals(150, parseAmerican(" 150 "))
        assertEquals(-110, parseAmerican("-110"))
        assertEquals(-110, parseAmerican("−110"))
        assertNull(parseAmerican("50"))
        assertNull(parseAmerican("-99"))
        assertNull(parseAmerican("abc"))
        assertNull(parseAmerican(""))
    }
}
