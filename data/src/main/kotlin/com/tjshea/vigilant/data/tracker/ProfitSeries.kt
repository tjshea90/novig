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
        fun bounds(now: Long, firstMs: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
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
}
