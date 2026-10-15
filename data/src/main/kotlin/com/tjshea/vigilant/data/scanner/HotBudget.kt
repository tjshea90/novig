package com.tjshea.vigilant.data.scanner

/**
 * How many Novig books one hot step ([ScanGuards.hotRefresh]) may read, sized to the route's measured speed (found in the engine's own scan notes, 2026-10-15 03:51Z: on a runner Novig's region screen refused, the scan
 * read the public routes at 1-2 books a second, and the hot step's fixed 120 books took 60-80 seconds of every 45, so a pass read 1,154 hot re-reads and only 228 other books, against 1,442 in a pass with 84 hot ones). The
 * hot refresh is for the few games about to start; it must never take more than [SHARE] of the reads the route can make, so the far games keep the scan's own pace on a slow route as on a fast one. Pure.
 */
object HotBudget {
    /** The most of the route's reads the hot refresh may use. */
    const val SHARE = 0.25

    /** The first step, before any speed is known: small, so a slow route is found out cheaply. */
    const val FIRST = 10

    /** Never fewer than this, so a very slow route still refreshes the likeliest bet of the soonest game. */
    const val MIN = 5

    /** On the key's 16 a second this is about 8 seconds a step. */
    const val MAX = 120

    /** Books one step may read when the route did [ratePerSecond] books a second at the last one (0 = not measured yet), the steps being [everySeconds] apart. */
    fun reads(ratePerSecond: Double, everySeconds: Int): Int =
        if (ratePerSecond <= 0.0 || ratePerSecond.isNaN()) FIRST else (ratePerSecond * everySeconds.coerceAtLeast(1) * SHARE).toInt().coerceIn(MIN, MAX)

    /** The speed a step of [books] books that took [tookMs] showed, or null when it says nothing (nothing read, no time passed). */
    fun rate(books: Int, tookMs: Long): Double? = if (books <= 0 || tookMs <= 0L) null else books * 1000.0 / tookMs
}
