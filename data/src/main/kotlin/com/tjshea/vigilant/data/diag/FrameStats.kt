package com.tjshea.vigilant.data.diag

import java.util.concurrent.ConcurrentHashMap

/**
 * The screen's frames as Android drew them, by what the app was doing at the time (Tj, 2026-10-02: "When I scan with vigilant scanner, the entire
 * app becomes laggy still"): a lag Tj feels on his phone, measured on his phone, so Diagnostics can say whether a scan makes the screen stutter and
 * by how much, instead of a guess from the code. In memory for this run of the process.
 *
 * A frame is slow when it took more than [SLOW_MULTIPLIER] of its deadline (a hitch the eye sees, as androidx JankStats counts it) and frozen past
 * [FROZEN_MS] (Android vitals' frozen frame).
 */
class FrameStats {

    /** One activity's frames: how many, slow and frozen ones, and the durations of the last [SAMPLES] (ms). */
    data class Bucket(val frames: Long, val slow: Long, val frozen: Long, val durations: SampleSummary) {
        val slowShare: Double get() = if (frames == 0L) 0.0 else slow.toDouble() / frames
    }

    private class Counter {
        var frames = 0L
        var slow = 0L
        var frozen = 0L
        val samples = RollingSamples(SAMPLES)
    }

    private val counters = ConcurrentHashMap<String, Counter>()

    /** A frame of [durationMs] whose deadline was [deadlineMs], drawn while the app was doing [activity]. */
    fun add(activity: String, durationMs: Double, deadlineMs: Double) {
        val c = counters.getOrPut(activity) { Counter() }
        synchronized(c) {
            c.frames++
            if (durationMs > deadlineMs * SLOW_MULTIPLIER) c.slow++
            if (durationMs > FROZEN_MS) c.frozen++
        }
        c.samples.add(durationMs)
    }

    fun snapshot(): Map<String, Bucket> = counters.mapValues { (_, c) -> synchronized(c) { Bucket(c.frames, c.slow, c.frozen, c.samples.summary()) } }.toSortedMap()

    companion object {
        const val SLOW_MULTIPLIER = 2.0
        const val FROZEN_MS = 700.0
        const val SAMPLES = 2_000

        /** What the app was doing ([FrameStats.add]'s activity), most telling first. */
        const val SCAN = "a Vigilant scan running"
        const val CHECK = "Check odds now running"
        const val CNO = "CNO's list being read"
        const val QUIET = "none of those running"

        fun activity(scanning: Boolean, checking: Boolean, cnoReading: Boolean): String = when {
            scanning -> SCAN
            checking -> CHECK
            cnoReading -> CNO
            else -> QUIET
        }
    }
}
