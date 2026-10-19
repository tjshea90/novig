package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tj, 2026-10-18 (TCL122-6) and 2026-10-19 (PL): a bet counts on its GAME's day (5 am to 5 am Eastern), only settled bets make profit or loss, "Today" is today's games ($0 until a result), and a stake on a
 * game days away is money at stake, never a loss.
 */
class SlateProfitTest {
    private fun et(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(SlateProfit.ET).toInstant().toEpochMilli()

    // cost 0.5: a win pays the stake again, a loss loses it.
    private fun bet(id: String, starts: Long, stake: Double, status: BetStatus, settledAt: Long? = null, placed: Long = starts - 3_600_000L) =
        TrackedBet(id, placed, "NBA", "A @ B", starts, "Moneyline", "Team $id", "m$id", "o$id", 0.5, 0.5, 0.52, 0.04, stake, status, settledAtMs = settledAt)

    private val oct17 = LocalDate.of(2026, 10, 17)
    private val oct18 = LocalDate.of(2026, 10, 18)
    private val oct19 = LocalDate.of(2026, 10, 19)

    private val bets = listOf(
        bet("a", et(2026, 10, 17, 19), 10.0, BetStatus.WON),
        bet("b", et(2026, 10, 17, 20), 10.0, BetStatus.LOST),
        // 10:30 pm Eastern, graded after midnight: still the 17th's.
        bet("c", et(2026, 10, 17, 22, 30), 10.0, BetStatus.WON, settledAt = et(2026, 10, 18, 1, 5)),
        bet("d", et(2026, 10, 18, 13), 20.0, BetStatus.LOST),
        bet("v", et(2026, 10, 18, 13), 50.0, BetStatus.VOID),
    )

    @Test
    fun `a day's profit is its settled bets', over what they staked, and a void counts nowhere`() {
        val book = SlateProfit.book(bets, et(2026, 10, 19, 9))
        assertEquals(listOf(oct17, oct18), book.days.map { it.date })
        val (d17, d18) = book.days
        assertEquals(10.0, d17.profit, 1e-9)
        assertEquals(30.0, d17.settledStaked, 1e-9)
        assertEquals(40.0, d17.returned, 1e-9)
        assertEquals(10.0 / 30.0, d17.changePct!!, 1e-9)
        assertEquals("2-1", "${d17.won}-${d17.lost}")
        assertEquals(-20.0, d18.profit, 1e-9)
        assertEquals(20.0, d18.staked, 1e-9)
        assertEquals(-1.0, d18.changePct!!, 1e-9)
        assertEquals(-10.0, book.profit, 1e-9)
        assertEquals(-10.0 / 50.0, book.pct!!, 1e-9)
        assertEquals(40.0, book.returned, 1e-9)
        assertTrue(SlateProfit.book(emptyList(), 0L).days.isEmpty())
    }

    @Test
    fun `a day runs 5 am to 5 am Eastern, so a late game and a 1 am match stay on the night they began`() {
        assertEquals(oct17, SlateProfit.slateOf(bets[2]))
        assertEquals(oct17, SlateProfit.dayOf(et(2026, 10, 18, 1)))
        assertEquals(oct17, SlateProfit.dayOf(et(2026, 10, 18, 4, 59)))
        assertEquals(oct18, SlateProfit.dayOf(et(2026, 10, 18, 5)))
        val d17 = SlateProfit.book(bets, et(2026, 10, 19, 9)).days.first()
        assertEquals(et(2026, 10, 17, 19), d17.firstStartMs)
        assertEquals(et(2026, 10, 18, 1, 5), d17.endMs)
    }

    @Test
    fun `today is today's games whether or not one has started, and $0 before its first result`() {
        // 11 am on the 19th, the first game at 1 pm (Tj's screenshot: the card showed the 18th then).
        val now = et(2026, 10, 19, 11)
        val book = SlateProfit.book(bets + bet("p", et(2026, 10, 19, 13), 15.0, BetStatus.PENDING), now)
        assertEquals(oct19, SlateProfit.today(now))
        val today = SlateProfit.day(book, SlateProfit.today(now))
        assertEquals(0.0, today.profit, 1e-9)
        assertEquals(1, today.open)
        assertEquals(15.0, today.openStaked, 1e-9)
        assertNull(today.changePct)
        // A day with no bets at all is a $0 day, not a missing one.
        val empty = SlateProfit.day(book, LocalDate.of(2026, 10, 10))
        assertTrue(empty.empty)
        assertEquals(0.0, empty.profit, 1e-9)
        // At 1 am on the 20th "today" is still the 19th's games.
        assertEquals(oct19, SlateProfit.today(et(2026, 10, 20, 1)))
    }

    @Test
    fun `the last days are calendar days up to today, empty ones included, and every one from the first with All`() {
        val now = et(2026, 10, 19, 11)
        val book = SlateProfit.book(bets, now)
        val week = SlateProfit.lastDays(book, now, 7)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 10, 13), week.first().date)
        assertEquals(oct19, week.last().date)
        assertEquals(-10.0, week.sumOf { it.profit }, 1e-9)
        assertEquals(listOf(oct17, oct18, oct19), SlateProfit.lastDays(book, now, Int.MAX_VALUE).map { it.date })
        assertEquals(listOf(oct19), SlateProfit.lastDays(book, now, 1).map { it.date })
    }

    @Test
    fun `a stake on a game days away is open money, in no day up to today, never a loss`() {
        val now = et(2026, 10, 19, 11)
        // Placed today on Saturday's game.
        val future = bet("f", et(2026, 10, 24, 19), 25.0, BetStatus.PENDING, placed = now - 60_000L)
        val book = SlateProfit.book(bets + future, now)
        assertEquals(-10.0, book.profit, 1e-9)
        assertEquals(1, book.open)
        assertEquals(25.0, book.openStaked, 1e-9)
        assertEquals(-10.0, SlateProfit.lastDays(book, now, Int.MAX_VALUE).sumOf { it.profit }, 1e-9)
        assertTrue(SlateProfit.lastDays(book, now, Int.MAX_VALUE).none { it.open > 0 })
        val later = SlateProfit.later(book, now)!!
        assertEquals(1, later.open)
        assertEquals(25.0, later.openStaked, 1e-9)
        assertNull(SlateProfit.later(SlateProfit.book(bets, now), now))
        for (n in listOf(1, 7, 30, Int.MAX_VALUE)) assertFalse(SlateProfit.inLastDays(future, now, n))
        assertTrue(SlateProfit.inLastDays(bets[3], now, 7))
        assertFalse(SlateProfit.inLastDays(bets[3], now, 1))
        assertTrue(SlateProfit.inLastDays(bets[3], now, 2))
    }

    @Test
    fun `an open bet is counted open, adds nothing to profit, and keeps the day running to now`() {
        val now = et(2026, 10, 18, 14)
        val open = bets + bet("p", et(2026, 10, 18, 13, 30), 20.0, BetStatus.PENDING)
        val d18 = SlateProfit.book(open, now).days.last()
        assertEquals(1, d18.open)
        assertEquals(-20.0, d18.profit, 1e-9)
        assertEquals(20.0, d18.openStaked, 1e-9)
        assertEquals(now, d18.endMs)
        assertFalse(d18.finished)
    }

    @Test
    fun `the curve runs result by result, results at one moment make one step, and a late grade sits at the start plus three hours`() {
        val late = bet("x", et(2026, 10, 17, 19), 10.0, BetStatus.WON, settledAt = et(2026, 10, 19, 12))
        assertEquals(et(2026, 10, 17, 22), SlateProfit.resultMs(late))
        val curve = SlateProfit.curve(bets, oct17)
        assertEquals(listOf(10.0, 0.0, 10.0), curve.map { it.cum })
        assertEquals(et(2026, 10, 18, 1, 5), curve.last().tMs)
        // Three late grades of games that started together: one step of their sum, never a spike up and back.
        val together = listOf(
            bet("g1", et(2026, 10, 18, 19), 10.0, BetStatus.WON, settledAt = et(2026, 10, 19, 12)),
            bet("g2", et(2026, 10, 18, 19), 10.0, BetStatus.LOST, settledAt = et(2026, 10, 19, 12)),
            bet("g3", et(2026, 10, 18, 19), 10.0, BetStatus.LOST, settledAt = et(2026, 10, 19, 12)),
        )
        val step = SlateProfit.curve(together, oct18)
        assertEquals(1, step.size)
        assertEquals(-10.0, step.single().cum, 1e-9)
        assertEquals(3, step.single().count)
        assertEquals("3 results", step.single().label)
        // A bet with no known start counts from when it was placed.
        val unknown = bet("u", et(2026, 10, 18, 13), 99.0, BetStatus.WON).copy(startsTs = 0L)
        assertEquals(oct18, SlateProfit.slateOf(unknown))
    }
}
