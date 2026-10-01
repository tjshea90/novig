package com.tjshea.vigilant.data

/**
 * The app's own heap, watched (Tj's v0.38.0 Diagnostics, 2026-10-01: an `OutOfMemoryError` at the 256 MB limit during a scan with no limit on
 * anything). Android gives each app a fixed heap and ends it the moment a request can't fit, whatever the phone has free, so a scan that
 * keeps taking on more must notice and stop on its own: [pressing] says "drop what can be rebuilt", [critical] says "stop taking on more".
 * Both read the JVM's own numbers, so they work the same in tests with a fake [Probe].
 */
object MemoryGuard {

    /** The heap's numbers, and a way to collect garbage. */
    interface Probe {
        /** Bytes in use right now (including garbage not collected yet). */
        fun used(): Long

        /** The most the app may use. */
        fun max(): Long

        fun collect()
    }

    private object Real : Probe {
        private val runtime get() = Runtime.getRuntime()
        override fun used() = runtime.totalMemory() - runtime.freeMemory()
        override fun max() = runtime.maxMemory()
        override fun collect() = runtime.gc()
    }

    /** Tests replace this ([useRealProbe] puts the real heap back). */
    @Volatile
    var probe: Probe = Real

    fun useRealProbe() {
        probe = Real
        lastCollectMs = Long.MIN_VALUE / 2
    }

    /** Above this share of the heap, caches that can be rebuilt are dropped. */
    const val TRIM_AT = 0.75

    /** Above this share after a collection, a scan stops reading more (it would crash the app instead). */
    const val STOP_AT = 0.90

    /** A collection is forced at most this often by [critical]. */
    private const val COLLECT_GAP_MS = 10_000L

    @Volatile
    private var lastCollectMs = Long.MIN_VALUE / 2

    fun fraction(): Double = probe.used().toDouble() / probe.max().coerceAtLeast(1L)

    fun usedMb(): Long = probe.used() / MB

    fun maxMb(): Long = probe.max() / MB

    /** "heap 142 of 512 MB (28%)". */
    fun text(): String = "heap ${usedMb()} of ${maxMb()} MB (${Math.round(fraction() * 100)}%)"

    /** The heap is over [TRIM_AT]: garbage counts too, so this only says "worth dropping what can be rebuilt". */
    fun pressing(): Boolean = fraction() >= TRIM_AT

    /**
     * The heap is still over [STOP_AT] after a collection (forced, at most every 10 s, and only once it's over the line): garbage that hasn't
     * been collected yet looks like use, and must not stop a scan that is fine.
     */
    fun critical(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (fraction() < STOP_AT) return false
        if (nowMs - lastCollectMs >= COLLECT_GAP_MS) {
            lastCollectMs = nowMs
            probe.collect()
        }
        return fraction() >= STOP_AT
    }

    private const val MB = 1024L * 1024L
}
