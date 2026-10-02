package com.tjshea.vigilant.data.diag

import kotlin.math.ceil

/** What [RollingSamples] says of its window: how many it holds, the median, the 95th percentile, the largest and the mean. */
data class SampleSummary(val count: Int, val p50: Double, val p95: Double, val max: Double, val mean: Double) {
    companion object {
        val EMPTY = SampleSummary(0, 0.0, 0.0, 0.0, 0.0)

        /** Percentiles of [xs] by nearest rank (no interpolation: a figure the report shows is a sample that happened). */
        fun of(xs: List<Double>): SampleSummary {
            if (xs.isEmpty()) return EMPTY
            val s = xs.sorted()
            fun at(p: Double) = s[(ceil(p * s.size).toInt() - 1).coerceIn(0, s.size - 1)]
            return SampleSummary(s.size, at(0.50), at(0.95), s.last(), s.average())
        }
    }
}

/** The last [cap] numbers of something measured over and over (a call's time, a cycle's length), safe to add to from any thread. */
class RollingSamples(private val cap: Int = 200) {
    private val xs = ArrayDeque<Double>()

    @Synchronized
    fun add(v: Double) {
        if (xs.size >= cap) xs.removeFirst()
        xs.addLast(v)
    }

    @Synchronized
    fun values(): List<Double> = xs.toList()

    fun summary(): SampleSummary = SampleSummary.of(values())
}
