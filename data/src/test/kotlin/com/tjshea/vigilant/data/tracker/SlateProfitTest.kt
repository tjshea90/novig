package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Tj, 2026-10-18 (TCL122-6): a day is a slate of games (first game to last result), and profit runs from ONE fixed start, the first day's stakes. */
class SlateProfitTest {
    private fun et(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(SlateProfit.ET).toInstant().toEpochMilli()

    // cost 0.5: a win pays the stake again, a loss loses it.
    private fun bet(id: String, starts: Long, stake: Double, status: BetStatus, settledAt: Long? = null) =
        TrackedBet(id, starts - 3_600_000L, "NBA", "A @ B", starts, "Moneyline", "Team $id", "m$id", "o$id", 0.5, 0.5, 0.52, 0.04, stake, status, settledAtMs = settledAt)

    private val bets = listOf(
        bet("a", et(2026, 10, 17, 19), 10.0, BetStatus.WON),
        bet("b", et(2026, 10, 17, 20), 10.0, BetStatus.LOST),
        // 10:30 pm Eastern: past midnight UTC, still the 17th's slate.
        bet("c", et(2026, 10, 17, 22, 30), 10.0, BetStatus.WON, settledAt = et(2026, 10, 18, 1, 5)),
        bet("d", et(2026, 10, 18, 13), 20.0, BetStatus.LOST),
        bet("v", et(2026, 10, 18, 13), 50.0, BetStatus.VOID),
    )

    @Test
    fun `the start is the first slate's stakes and never moves, and each day chains from the one before`() {
        val book = SlateProfit.book(bets, et(2026, 10, 19, 9))
        assertEquals(30.0, book.start, 1e-9)
        assertEquals(2, book.days.size)
        val (d17, d18) = book.days
        assertEquals(LocalDate.of(2026, 10, 17), d17.date)
        assertEquals(10.0, d17.profit, 1e-9)
        assertEquals(30.0, d17.startBalance, 1e-9)
        assertEquals(40.0, d17.endBalance, 1e-9)
        assertEquals(10.0 / 30.0, d17.changePct!!, 1e-9)
        // The second day: -$20 on a $40 start = -50%; the void counts nowhere (not even in the stakes).
        assertEquals(-20.0, d18.profit, 1e-9)
        assertEquals(40.0, d18.startBalance, 1e-9)
        assertEquals(-0.5, d18.changePct!!, 1e-9)
        assertEquals(20.0, d18.staked, 1e-9)
        assertEquals(20.0, book.balance, 1e-9)
        assertEquals(-10.0 / 30.0, book.pct!!, 1e-9)
        // Staking more on day 2 did not move the start.
        val more = SlateProfit.book(bets + bet("e", et(2026, 10, 18, 15), 500.0, BetStatus.PUSH), et(2026, 10, 19, 9))
        assertEquals(30.0, more.start, 1e-9)
    }

    @Test
    fun `a slate starts at its first game and ends at its last result, and today is last night's slate until the next first game`() {
        val book = SlateProfit.book(bets, et(2026, 10, 19, 9))
        val d17 = book.days.first()
        assertEquals(et(2026, 10, 17, 19), d17.firstStartMs)
        assertEquals(et(2026, 10, 18, 1, 5), d17.endMs)
        // 10 am on the 18th: the 18th's first game (1 pm) has not started, so "today" is still the 17th's slate.
        assertEquals(LocalDate.of(2026, 10, 17), SlateProfit.current(SlateProfit.book(bets, et(2026, 10, 18, 10)), et(2026, 10, 18, 10))!!.date)
        assertEquals(LocalDate.of(2026, 10, 18), SlateProfit.current(book, et(2026, 10, 18, 14))!!.date)
        assertEquals(LocalDate.of(2026, 10, 17), SlateProfit.previous(book, book.days.last())!!.date)
        assertNull(SlateProfit.previous(book, d17))
        assertEquals(2, SlateProfit.lastDays(book, et(2026, 10, 19, 9), 7).size)
    }

    @Test
    fun `an open bet is counted open, adds nothing to profit, and keeps the slate running to now`() {
        val now = et(2026, 10, 18, 14)
        val open = bets + bet("p", et(2026, 10, 18, 13, 30), 20.0, BetStatus.PENDING)
        val d18 = SlateProfit.book(open, now).days.last()
        assertEquals(1, d18.open)
        assertEquals(-20.0, d18.profit, 1e-9)
        assertEquals(now, d18.endMs)
        assertTrue(!d18.finished)
    }

    @Test
    fun `the curve of a slate runs result by result, a late grade placed at the game's start plus three hours`() {
        val late = bet("x", et(2026, 10, 17, 19), 10.0, BetStatus.WON, settledAt = et(2026, 10, 19, 12))
        assertEquals(et(2026, 10, 17, 22), SlateProfit.resultMs(late))
        val curve = SlateProfit.curve(bets, LocalDate.of(2026, 10, 17))
        assertEquals(listOf(10.0, 0.0, 10.0), curve.map { it.cum })
        assertEquals(et(2026, 10, 18, 1, 5), curve.last().tMs)
        assertTrue(SlateProfit.book(emptyList(), 0L).days.isEmpty())
        // A bet with no known start counts from when it was placed, never as a 1970 first day that would fix the start number.
        val unknown = bet("u", et(2026, 10, 18, 13), 99.0, BetStatus.WON).copy(startsTs = 0L)
        assertEquals(30.0, SlateProfit.book(bets + unknown, et(2026, 10, 19, 9)).start, 1e-9)
    }
}
