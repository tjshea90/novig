package com.tjshea.vigilant.data.diag

import java.util.concurrent.ConcurrentHashMap

/**
 * Timings the app takes of itself, in memory for this run of the process (Tj, 2026-10-02: Diagnostics should say what to make faster): how long background cycles
 * and scans take, how long the screen took to appear. Named series, each its last [CAP] values ([RollingSamples]).
 */
class PerfStats {
    private val series = ConcurrentHashMap<String, RollingSamples>()

    /** Time from the process starting to the first screen drawn, ms (null: not this run, or no screen yet). */
    @Volatile
    var coldStartMs: Long? = null

    fun add(name: String, value: Double) {
        series.getOrPut(name) { RollingSamples(CAP) }.add(value)
    }

    fun summary(name: String): SampleSummary = series[name]?.summary() ?: SampleSummary.EMPTY

    fun summaries(): Map<String, SampleSummary> = series.mapValues { it.value.summary() }.toSortedMap()

    companion object {
        const val CAP = 200
    }
}
