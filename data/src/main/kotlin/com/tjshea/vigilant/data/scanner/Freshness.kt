package com.tjshea.vigilant.data.scanner

/**
 * How old another book's price may be and still be compared with Novig's (Tj, 2026-09-27: "The other
 * sports books odds MUST be current or at most a few minutes old", RESEARCH.md §24). Fixed: no
 * setting raises them.
 */
object Freshness {
    /**
     * A quote prices a fair line only if its feed saw it this recently: when Vigilant fetched it
     * (Pinnacle, Kalshi, Polymarket), when The Odds API last saw that market at the book, or when
     * PropLine last saw both sides. Past it, an EV built on it is no longer shown.
     */
    const val MAX_QUOTE_AGE_MS = 5 * 60_000L

    /** The longest any feed's answer is re-used instead of asked again, so it's still fresh through a scan. */
    const val MAX_REUSE_MS = 2 * 60_000L

    /** Whether a quote the feed last saw at [seenAtMs] is still fresh at [now]; unknown counts as fresh. */
    fun fresh(seenAtMs: Long?, now: Long): Boolean = seenAtMs == null || now - seenAtMs <= MAX_QUOTE_AGE_MS
}
