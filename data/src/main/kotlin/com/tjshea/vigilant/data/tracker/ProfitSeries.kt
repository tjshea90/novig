package com.tjshea.vigilant.data.tracker

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The profit graph's data (Tj, 2026-10-10: "let me filter the graph ... today only, yesterday, last 2 days ... like stock market graphs ... pinch zoom ... scroll left to right"). Running profit
 * over TIME, not over bet number: one point per settled bet at its game's start (the order the results arrive in, and the same bets and money as the Tracker's Profit: every settled bet,
 * outliers and locks too, voids never), with a value that is the profit since the very first bet. A view (a window of time) then answers "what happened in here": the change across it, the bets
 * in it, the money staked on them.
 */
object ProfitSeries {

    /** One settled bet on the curve: [cum] is the profit since the first bet, as of this one. */
    data class Point(val tMs: Long, val cum: Double, val profit: Double, val stake: Double)

    /** What the bets inside a window of time came to. */
    data class Window(val profit: Double, val staked: Double, val bets: Int) {
        /** Profit over what was staked in the window (ROI), null with nothing staked. */
        val roi: Double? get() = if (staked > 0) profit / staked else null
    }

    /** The curve of [all], oldest first; empty with nothing settled. */
    fun points(all: List<TrackedBet>): List<Point> {
        val settled = all.filter { it.status != BetStatus.PENDING && it.status != BetStatus.VOID }.sortedBy { it.startsTs }
        var cum = 0.0
        return settled.map { b ->
            val p = b.profit ?: 0.0
            cum += p
            Point(b.startsTs, cum, p, b.stake)
        }
    }

    /**
     * One day of play, the way Tj counts it (Tj, 2026-10-18, TCL122-6: "each day does not start at 12am, it should start with that day's first games and end with the results of those games"): a run of games
     * with no gap of more than [SLATE_GAP_MS] between one start and the next; it begins at its first game and ends [GAME_MS] after its last start (when the last game is about final). A late slate that runs
     * past midnight is still one day.
     */
    data class Slate(val startMs: Long, val endMs: Long, val profit: Double, val staked: Double, val bets: Int)

    /** The slates of [points], oldest first. */
    fun slates(points: List<Point>): List<Slate> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<Slate>()
        var from = 0
        for (i in 1..points.size) {
            if (i == points.size || points[i].tMs - points[i - 1].tMs > SLATE_GAP_MS) {
                val part = points.subList(from, i)
                out += Slate(part.first().tMs, part.last().tMs + GAME_MS, part.sumOf { it.profit }, part.sumOf { it.stake }, part.size)
                from = i
            }
        }
        return out
    }

    /**
     * The one number the profit and loss is measured from and that never changes (Tj, 2026-10-18: "the total money staked on all bets for the first day logged"): the stake of the first slate. Staking more
     * later is not depositing more, so it never moves. Null with nothing settled.
     */
    fun startingBalance(slates: List<Slate>): Double? = slates.firstOrNull()?.staked?.takeIf { it > 0 }

    /** Each slate's change against the balance the day before: its profit over (start number + every earlier profit). Null when the start number is unknown or the balance is not above zero. */
    fun dayChange(slates: List<Slate>, index: Int): Double? {
        val start = startingBalance(slates) ?: return null
        val before = start + slates.take(index).sumOf { it.profit }
        return if (before > 0) slates[index].profit / before else null
    }

    /** The profit value of the curve at [t]: the cumulative profit of the last bet at or before it (0 before the first). Binary search. */
    fun valueAt(points: List<Point>, t: Long): Double {
        var lo = 0
        var hi = points.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (points[mid].tMs <= t) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (best < 0) 0.0 else points[best].cum
    }

    /** The bets whose point lies in [from, to] (both inclusive: a view's edges). */
    fun window(points: List<Point>, from: Long, to: Long): Window {
        var profit = 0.0
        var staked = 0.0
        var n = 0
        for (p in points) if (p.tMs in from..to) { profit += p.profit; staked += p.stake; n++ }
        return Window(profit, staked, n)
    }

    /** The first index of [points] at or after [t] (points.size when none). */
    fun firstAtOrAfter(points: List<Point>, t: Long): Int {
        var lo = 0
        var hi = points.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (points[mid].tMs < t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** The quick ranges, as a stock chart has them. Calendar days are the phone's own (midnight to midnight); "this week" starts on Monday. */
    enum class Range(val label: String, val long: String) {
        // Short, stock-chart labels: the words Today, 7 days, 30 days and All time are already chips for the Tracker's period and the closing-line card, and two chips with one name and two meanings would
        // be a trap. [long] is what a screen reader says and what the readout line spells out.
        TODAY("1D", "Today"), YESTERDAY("Yest", "Yesterday"), TWO_DAYS("2D", "Yesterday and today"), THREE_DAYS("3D", "The last 3 days"), THIS_WEEK("Wk", "This week, from Monday"),
        WEEK("7D", "The last 7 days"), MONTH("30D", "The last 30 days"), ALL("Max", "All time");

        /** The window this range shows at [now], with [firstMs] the time of the first bet (what All time starts at). Ends are exclusive of the next period. */
        fun bounds(now: Long, firstMs: Long, zone: ZoneId = ZoneId.systemDefault(), slates: List<Slate> = emptyList()): Pair<Long, Long> {
            if (slates.isNotEmpty() && (this == TODAY || this == YESTERDAY)) {
                // Today's slate: the latest one if it began today or is still going; yesterday's is the one before it (or, with nothing today, the latest).
                val startOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                val last = slates.last()
                val todayIdx = if (last.startMs >= startOfToday || last.endMs > now) slates.lastIndex else -1
                val idx = if (this == TODAY) todayIdx else if (todayIdx >= 0) todayIdx - 1 else slates.lastIndex
                return if (idx < 0) now to now else slates[idx].startMs to minOf(slates[idx].endMs, now).coerceAtLeast(slates[idx].startMs)
            }
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            fun day(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
            return when (this) {
                TODAY -> day(today) to now
                YESTERDAY -> day(today.minusDays(1)) to day(today) - 1
                TWO_DAYS -> day(today.minusDays(1)) to now
                THREE_DAYS -> day(today.minusDays(2)) to now
                THIS_WEEK -> day(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) to now
                WEEK -> now - 7 * DAY_MS to now
                MONTH -> now - 30 * DAY_MS to now
                ALL -> minOf(firstMs, now - DAY_MS) to now
            }
        }
    }

    const val DAY_MS = 24L * 3_600_000L

    /** More than this between two game starts ends a slate (a late game ends well inside it; the next morning's first game starts after it). */
    const val SLATE_GAP_MS = 7L * 3_600_000L

    /** A game is final about this long after it starts (the slate's end). */
    const val GAME_MS = 4L * 3_600_000L
}
