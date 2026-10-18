package com.tjshea.vigilant.data.cloud

/**
 * A few minutes of the live lane's counters for the Live dashboard's graphs (Tj, 2026-10-18, TCL122-4: "cool real time graphs and easy to see stats for both the GitHub engine and the phone engine"):
 * the lane counts pushes and judgments since it started ([LiveStats]), so a rate is the change between two readings over the time between them. Pure: a list in, a list out.
 */
object LiveTrend {
    data class Sample(val atMs: Long, val pushes: Long, val judgments: Long, val games: Int, val bidsUp: Int)

    /** How much history the graphs keep. */
    const val KEEP_MS = 5 * 60_000L

    /** [list] with [s] added: a reading no newer than the last is ignored, a counter that went backwards (the lane started again) starts the history over, and readings older than [keepMs] go. */
    fun add(list: List<Sample>, s: Sample, keepMs: Long = KEEP_MS): List<Sample> {
        val last = list.lastOrNull()
        if (last != null && s.atMs <= last.atMs) return list
        val base = if (last != null && (s.pushes < last.pushes || s.judgments < last.judgments)) emptyList() else list
        return (base + s).filter { s.atMs - it.atMs <= keepMs }
    }

    /** The rate a minute of one counter between each pair of readings, oldest first (one value fewer than readings). */
    fun perMinute(list: List<Sample>, pick: (Sample) -> Long): List<Float> = list.zipWithNext { a, b ->
        val dt = (b.atMs - a.atMs).coerceAtLeast(1L)
        ((pick(b) - pick(a)).coerceAtLeast(0L) * 60_000.0 / dt).toFloat()
    }

    /** The rate a minute over the last [windowMs] of readings; null with fewer than two in it. */
    fun recentPerMinute(list: List<Sample>, pick: (Sample) -> Long, windowMs: Long = 60_000L): Double? {
        val last = list.lastOrNull() ?: return null
        val first = list.firstOrNull { last.atMs - it.atMs <= windowMs } ?: return null
        if (first === last || last.atMs <= first.atMs) return null
        return (pick(last) - pick(first)).coerceAtLeast(0L) * 60_000.0 / (last.atMs - first.atMs)
    }

    fun of(st: LiveStats, atMs: Long) = Sample(atMs, st.pushes, st.judgments, st.games, st.bidsUp)
}
