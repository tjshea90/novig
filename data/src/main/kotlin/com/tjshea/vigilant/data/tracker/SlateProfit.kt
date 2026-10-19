package com.tjshea.vigilant.data.tracker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Profit and loss by game day (Tj, 2026-10-18, TCL122-6: "each day does not start at 12am, it should start with that day's first games and end with the results of those games"; and 2026-10-19, PL:
 * "Today's slate has 0 settled bets and should show 0 profit or loss because it is only around 11am ... make a chart which shows daily profit/loss and total all time profit/loss that is logical").
 *
 *  - A bet belongs to the day of its GAME in Eastern time (a 10 pm game is that day's, even though it ends after midnight; a bet placed today on Saturday's game is Saturday's).
 *  - A day's profit is its SETTLED bets' winnings minus their stakes; a bet not settled yet is neither profit nor loss, only money at stake ([Day.openStaked]).
 *  - "Today" is today's date in Eastern time, whether or not its first game has started: before the first result it is $0 (2026-10-19: it used to show the newest slate that had begun, which at
 *    11 am was the day before).
 *  - The all-time money figure is the wallet's ([WalletProfit]: cash plus open bets minus what was put in); this book's own total is the settled bets' (what the tracker saw), shown beside it.
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
        /** The day's profit over the money staked on its settled bets; null before any settled. */
        val changePct: Double? get() = if (settledStaked > 0.0) profit / settledStaked else null
        val finished: Boolean get() = open == 0
        /** Money on this day's bets that have not settled yet: at stake, not lost. */
        val openStaked: Double get() = (staked - settledStaked).coerceAtLeast(0.0)
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

    /** Today's date in Eastern time: the day "Today" shows, started or not. */
    fun today(now: Long): LocalDate = Instant.ofEpochMilli(now).atZone(ET).toLocalDate()

    /** The day [date] of [book], or an empty one (no bets that day: $0, nothing open). */
    fun day(book: Book, date: LocalDate): Day = book.days.firstOrNull { it.date == date } ?: Day(date, 0L, 0L, 0.0, 0.0, 0, 0, 0, 0, book.balance, 0.0)

    /** Every calendar day from [from] to [to], the empty ones included (a day with no result is a $0 day, not a missing one). */
    fun days(book: Book, from: LocalDate, to: LocalDate): List<Day> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<Day>()
        var d = from
        while (!d.isAfter(to)) { out += day(book, d); d = d.plusDays(1) }
        return out
    }

    /** The last [n] calendar days up to and including today. */
    fun lastDays(book: Book, now: Long, n: Int): List<Day> {
        val t = today(now)
        val first = book.days.firstOrNull()?.date ?: t
        val from = if (n == Int.MAX_VALUE) minOf(first, t) else t.minusDays((n - 1).toLong())
        return days(book, from, t)
    }

    /**
     * The running profit of one day, result by result. Results that count at the same moment (late grades all placed at their game's start plus three hours) are ONE step: the line never
     * spikes through the order they happened to be listed in.
     */
    fun curve(all: List<TrackedBet>, date: LocalDate): List<Point> {
        var cum = 0.0
        return all.filter { counts(it) && it.status != BetStatus.PENDING && slateOf(it) == date }
            .groupBy(::resultMs).toSortedMap()
            .map { (t, group) ->
                val p = group.sumOf { it.profit ?: 0.0 }
                cum += p
                Point(t, cum, p, group.sumOf { it.stake }, if (group.size == 1) group.single().selection else "${group.size} results")
            }
    }
}
