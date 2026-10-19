package com.tjshea.vigilant.data.tracker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Profit and loss by game day (Tj, 2026-10-18, TCL122-6: "each day does not start at 12am, it should start with that day's first games and end with the results of those games"; and 2026-10-19, PL:
 * "Today's slate has 0 settled bets and should show 0 profit or loss because it is only around 11am ... make a chart which shows daily profit/loss and total all time profit/loss that is logical and shows an
 * accurate number").
 *
 *  - A bet belongs to the day of its GAME in Eastern time, and a day runs from 5 am to 5 am ([DAY_STARTS_HOUR]): a 10 pm game that ends after midnight is that night's, and so is a 1 am tennis match; a bet
 *    placed today on Saturday's game is Saturday's.
 *  - A day's profit is what its SETTLED bets paid back minus what they cost. A bet that has not settled is neither profit nor loss, only money at stake ([Day.openStaked]): a stake on a game days away never
 *    shows as a loss.
 *  - "Today" is today's game day ([today]), started or not: $0 until its first result (it used to be the newest day whose first game had begun, which at 11 am was the day before).
 *  - The all-time money figure is the wallet's ([WalletProfit]: cash plus open bets minus what was put in); this book's total is the settled bets' (what the tracker saw), shown beside it.
 *
 * Pure: the same bets give the same book, for tests.
 */
object SlateProfit {
    val ET: ZoneId = ZoneId.of("America/New_York")

    /** A game day starts at 5 am Eastern, so a night's late games and their results stay on the day they began (nothing a US slate holds starts between midnight and 5 am). */
    const val DAY_STARTS_HOUR = 5

    /** A result is placed at the moment it was graded when that is plausible (within [MAX_GAME_MS] of the start), else at the start plus [TYPICAL_GAME_MS] (a bet graded hours later, when the app opened). */
    const val TYPICAL_GAME_MS = 3L * 3_600_000L
    const val MAX_GAME_MS = 12L * 3_600_000L

    data class Day(
        val date: LocalDate,
        /** First game's start and the last result in (or now while bets are open): the day's own span of time. Both 0 for a day with no bets. */
        val firstStartMs: Long,
        val endMs: Long,
        /** What the settled bets paid back minus what they cost. */
        val profit: Double,
        /** Money on every bet of the day, settled or not. */
        val staked: Double,
        val settled: Int,
        val open: Int,
        val won: Int,
        val lost: Int,
        /** Money staked on the day's SETTLED bets (Tj, 2026-10-19: profit is winnings minus amount staked, and the percentage is over the amount staked). */
        val settledStaked: Double = 0.0,
    ) {
        /** Total paid back on the settled bets (stake plus profit). */
        val returned: Double get() = settledStaked + profit
        /** The day's profit over the money staked on its settled bets; null before any settled. */
        val changePct: Double? get() = if (settledStaked > 0.0) profit / settledStaked else null
        val finished: Boolean get() = open == 0
        /** Money on this day's bets that have not settled yet: at stake, not lost. */
        val openStaked: Double get() = (staked - settledStaked).coerceAtLeast(0.0)
        val empty: Boolean get() = settled == 0 && open == 0
    }

    /** Every game day with a bet in it, oldest first (later days too: their bets are open). */
    data class Book(val days: List<Day>) {
        val profit: Double get() = days.sumOf { it.profit }
        /** Money staked on every settled bet, all time, and what those bets paid back. */
        val settledStaked: Double get() = days.sumOf { it.settledStaked }
        val returned: Double get() = settledStaked + profit
        /** Profit over the money staked on the settled bets; null before any settled. */
        val pct: Double? get() = if (settledStaked > 0.0) profit / settledStaked else null
        val settled: Int get() = days.sumOf { it.settled }
        val open: Int get() = days.sumOf { it.open }
        val openStaked: Double get() = days.sumOf { it.openStaked }
    }

    /** One step of a day's curve: [cum] is the day's profit so far, as of this moment's results ([count] of them, [profit] between them). */
    data class Point(val tMs: Long, val cum: Double, val profit: Double, val stake: Double, val label: String, val count: Int = 1)

    /** The game's start; a bet whose start was never known (0, an old import) counts from when it was placed. */
    fun gameMs(b: TrackedBet): Long = if (b.startsTs > 0L) b.startsTs else b.createdAtMs

    /** The game day of the moment [ms]: its Eastern date, the hours before [DAY_STARTS_HOUR] counted with the day before. */
    fun dayOf(ms: Long): LocalDate {
        val z = Instant.ofEpochMilli(ms).atZone(ET)
        return if (z.hour < DAY_STARTS_HOUR) z.toLocalDate().minusDays(1) else z.toLocalDate()
    }

    fun slateOf(b: TrackedBet): LocalDate = dayOf(gameMs(b))

    /** Today's game day: what "Today" shows, started or not (until 5 am, last night's). */
    fun today(now: Long): LocalDate = dayOf(now)

    /** When [b]'s result counts on the curve. */
    fun resultMs(b: TrackedBet): Long {
        val start = gameMs(b)
        val s = b.settledAtMs
        return if (s != null && s >= start && s - start <= MAX_GAME_MS) s else start + TYPICAL_GAME_MS
    }

    private fun counts(b: TrackedBet) = b.status != BetStatus.VOID

    /** Every game day with a bet in it, oldest first. */
    fun book(all: List<TrackedBet>, now: Long): Book {
        val bets = all.filter(::counts)
        if (bets.isEmpty()) return Book(emptyList())
        return Book(
            bets.groupBy(::slateOf).toSortedMap().map { (date, list) ->
                val settled = list.filter { it.status != BetStatus.PENDING }
                val open = list.size - settled.size
                val first = list.minOf(::gameMs)
                Day(
                    date, first, if (open > 0) maxOf(now, first) else settled.maxOf(::resultMs), settled.sumOf { it.profit ?: 0.0 }, list.sumOf { it.stake }, settled.size, open,
                    settled.count { it.status == BetStatus.WON }, settled.count { it.status == BetStatus.LOST }, settled.sumOf { it.stake },
                )
            },
        )
    }

    /** The day [date] of [book], or an empty one (no bets that day: $0, nothing open). */
    fun day(book: Book, date: LocalDate): Day = book.days.firstOrNull { it.date == date } ?: Day(date, 0L, 0L, 0.0, 0.0, 0, 0, 0, 0)

    /** Every calendar day from [from] to [to], the empty ones included (a day with no result is a $0 day, not a missing one). */
    fun days(book: Book, from: LocalDate, to: LocalDate): List<Day> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<Day>()
        var d = from
        while (!d.isAfter(to)) { out += day(book, d); d = d.plusDays(1) }
        return out
    }

    /** The first day of the last [n] game days up to today ([Int.MAX_VALUE]: the first day with a bet, or today). */
    fun firstOf(book: Book, now: Long, n: Int): LocalDate {
        val t = today(now)
        return if (n == Int.MAX_VALUE) minOf(book.days.firstOrNull()?.date ?: t, t) else t.minusDays((n - 1).toLong().coerceAtLeast(0L))
    }

    /** The last [n] game days up to and including today ([Int.MAX_VALUE]: from the first day with a bet). */
    fun lastDays(book: Book, now: Long, n: Int): List<Day> = days(book, firstOf(book, now, n), today(now))

    /** Whether [b]'s game day is within the last [n] game days up to today (a game after today is in none of them). */
    fun inLastDays(b: TrackedBet, now: Long, n: Int): Boolean {
        val d = slateOf(b)
        val t = today(now)
        return !d.isAfter(t) && (n == Int.MAX_VALUE || !d.isBefore(t.minusDays((n - 1).toLong().coerceAtLeast(0L))))
    }

    /** The open bets on games after today: money at stake that no day up to today shows. */
    fun later(book: Book, now: Long): Day? {
        val t = today(now)
        val ahead = book.days.filter { it.date.isAfter(t) && it.open > 0 }
        if (ahead.isEmpty()) return null
        return Day(ahead.last().date, ahead.first().firstStartMs, ahead.last().endMs, 0.0, ahead.sumOf { it.openStaked }, 0, ahead.sumOf { it.open }, 0, 0)
    }

    /**
     * The running profit of one day, result by result. Results that count at the same moment (late grades all placed at their game's start plus three hours) are ONE step: the line never spikes through
     * the order they happened to be listed in.
     */
    fun curve(all: List<TrackedBet>, date: LocalDate): List<Point> {
        var cum = 0.0
        return all.filter { counts(it) && it.status != BetStatus.PENDING && slateOf(it) == date }
            .groupBy(::resultMs).toSortedMap()
            .map { (t, group) ->
                val p = group.sumOf { it.profit ?: 0.0 }
                cum += p
                Point(t, cum, p, group.sumOf { it.stake }, if (group.size == 1) group.single().selection else "${group.size} results", group.size)
            }
    }
}
