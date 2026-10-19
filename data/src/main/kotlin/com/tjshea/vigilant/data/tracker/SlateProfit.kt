package com.tjshea.vigilant.data.tracker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Profit and loss by SLATE, not by calendar day (Tj, 2026-10-18, TCL122-6: "each day does not start at 12am, it should start with that day's first games and end with the results of those games ... the
 * today graph should only show profit and loss for the game slate for that day ... the profit/loss should be calculated from a single start number and that number never changes. that number is the total
 * money staked on all bets for the first day logged. each subsequent day shows how much was made or lost compared to the balance from the day before").
 *
 *  - A bet belongs to the slate of its GAME's date in Eastern time (a 10 pm game is that day's slate even though it ends after midnight).
 *  - A slate begins at its first game's start and ends at its last result; the "current" slate is the newest one whose first game has started (before the next day's first game, today is still last
 *    night's slate).
 *  - [Book.start] is the money staked on every bet of the first slate logged: fixed, it never moves with later stakes (staking more is not depositing more).
 *  - Each slate's [Day.startBalance] is the start plus every earlier slate's profit; its change is its own profit, and [Day.changePct] is that over the balance it started from.
 *
 * Pure: the same bets give the same book, for tests.
 */
object SlateProfit {
    val ET: ZoneId = ZoneId.of("America/New_York")

    /** A result is placed at the moment it was graded when that is plausible (within [MAX_GAME_MS] of the start), else at the start plus [TYPICAL_GAME_MS] (a bet graded hours later, when the app opened). */
    const val TYPICAL_GAME_MS = 3L * 3_600_000L
    const val MAX_GAME_MS = 12L * 3_600_000L

    data class Day(
        val date: LocalDate,
        /** First game's start and the last result in (or now while bets are open): the slate's own span of time. */
        val firstStartMs: Long,
        val endMs: Long,
        val profit: Double,
        val staked: Double,
        val settled: Int,
        val open: Int,
        val won: Int,
        val lost: Int,
        /** The balance this slate started from (the fixed start plus every earlier slate's profit) and where it ended. */
        val startBalance: Double,
        /** Money staked on this slate's SETTLED bets (Tj, 2026-10-19: profit is winnings minus amount staked, and the percentage is over the amount staked). */
        val settledStaked: Double = 0.0,
    ) {
        /** Total paid back on the settled bets (stake plus profit). */
        val returned: Double get() = settledStaked + profit
        val endBalance: Double get() = startBalance + profit
        /** The slate's profit over the balance it started from; null when that balance is not positive. */
        val changePct: Double? get() = if (settledStaked > 0.0) profit / settledStaked else null
        val finished: Boolean get() = open == 0
    }

    data class Book(
        /** The fixed start: money staked on every bet of the first slate logged (0 with no bets). */
        val start: Double,
        val days: List<Day>,
    ) {
        val balance: Double get() = start + days.sumOf { it.profit }
        val profit: Double get() = days.sumOf { it.profit }
        /** Profit over the fixed start; null with no start. */
        val pct: Double? get() = if (settledStaked > 0.0) profit / settledStaked else null
        /** Money staked on every settled bet, all time, and what those bets paid back. */
        val settledStaked: Double get() = days.sumOf { it.settledStaked }
        val returned: Double get() = settledStaked + profit
    }

    /** One result on a slate's curve: [cum] is the slate's profit so far, as of this result. */
    data class Point(val tMs: Long, val cum: Double, val profit: Double, val stake: Double, val label: String)

    /** The game's start; a bet whose start was never known (0, an old import) counts from when it was placed, so it never makes a 1970 "first day" that would fix the start number. */
    fun gameMs(b: TrackedBet): Long = if (b.startsTs > 0L) b.startsTs else b.createdAtMs

    fun slateOf(b: TrackedBet): LocalDate = Instant.ofEpochMilli(gameMs(b)).atZone(ET).toLocalDate()

    /** When [b]'s result counts on the curve. */
    fun resultMs(b: TrackedBet): Long {
        val start = gameMs(b)
        val s = b.settledAtMs
        return if (s != null && s >= start && s - start <= MAX_GAME_MS) s else start + TYPICAL_GAME_MS
    }

    private fun counts(b: TrackedBet) = b.status != BetStatus.VOID

    /** Every slate with a bet in it, oldest first, with balances chained from the fixed start. */
    fun book(all: List<TrackedBet>, now: Long): Book {
        val bets = all.filter(::counts)
        if (bets.isEmpty()) return Book(0.0, emptyList())
        val bySlate = bets.groupBy(::slateOf).toSortedMap()
        val start = bySlate.values.first().sumOf { it.stake }
        var balance = start
        val days = bySlate.map { (date, list) ->
            val settled = list.filter { it.status != BetStatus.PENDING }
            val open = list.size - settled.size
            val profit = settled.sumOf { it.profit ?: 0.0 }
            val first = list.minOf(::gameMs)
            val end = if (open > 0) maxOf(now, first) else settled.maxOf(::resultMs)
            val d = Day(
                date, first, end, profit, list.sumOf { it.stake }, settled.size, open,
                settled.count { it.status == BetStatus.WON }, settled.count { it.status == BetStatus.LOST }, balance, settled.sumOf { it.stake },
            )
            balance += profit
            d
        }
        return Book(start, days)
    }

    /** The current slate: the newest whose first game has started by [now] (or the newest of all when none has). */
    fun current(book: Book, now: Long): Day? = book.days.lastOrNull { it.firstStartMs <= now } ?: book.days.lastOrNull()

    /** The slate before [day], or null. */
    fun previous(book: Book, day: Day?): Day? {
        if (day == null) return null
        val i = book.days.indexOfFirst { it.date == day.date }
        return if (i > 0) book.days[i - 1] else null
    }

    /** The last [n] slates up to and including the current one. */
    fun lastDays(book: Book, now: Long, n: Int): List<Day> {
        val cur = current(book, now) ?: return emptyList()
        val i = book.days.indexOfFirst { it.date == cur.date }
        return book.days.subList((i - n + 1).coerceAtLeast(0), i + 1)
    }

    /** The running profit of one slate, result by result in the order they came in. */
    fun curve(all: List<TrackedBet>, date: LocalDate): List<Point> {
        var cum = 0.0
        return all.filter { counts(it) && it.status != BetStatus.PENDING && slateOf(it) == date }
            .sortedBy(::resultMs)
            .map { b ->
                val p = b.profit ?: 0.0
                cum += p
                Point(resultMs(b), cum, p, b.stake, b.selection)
            }
    }
}
