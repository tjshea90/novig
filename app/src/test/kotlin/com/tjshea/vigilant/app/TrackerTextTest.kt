package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.TrackerText
import com.tjshea.vigilant.app.ui.parseAmerican
import com.tjshea.vigilant.data.tracker.BetInsight
import com.tjshea.vigilant.data.tracker.BetStatus
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
        assertEquals("3 open · $30.00 at risk · pays $30.00 · 2 started · 1 need a tap", TrackerText.openSummary(open, now))
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
