package com.tjshea.vigilant.data.scanner

/**
 * How old another book's price may be and still be compared with Novig's (Tj, 2026-09-27: "The other
 * sports books odds MUST be current or at most a few minutes old", RESEARCH.md §24). Fixed: no
 * setting raises them. Since v0.19.3 the limit depends on how far off the game is ([maxAgeMs]; Tj,
 * 2026-09-28: "reconsider the 5 minute stale odds cutoff … It may be that odds do not change that
 * rapidly"; measured live, RESEARCH.md §30.2).
 */
object Freshness {
    /**
     * A quote prices a fair line only if its feed saw it this recently: when Vigilant fetched it
     * (Pinnacle, Kalshi, Polymarket), when The Odds API last saw that market at the book, or when
     * PropLine last saw both sides. Past it, an EV built on it is no longer shown. The limit for a game
     * within [FAR_OFF_MS] of its start (or live), when lines move most (lineups, injuries).
     */
    const val MAX_QUOTE_AGE_MS = 5 * 60_000L

    /**
     * The limit for a game more than [FAR_OFF_MS] away. Measured over 30 minutes (2026-09-28): 2.7% of Kalshi's
     * fair lines moved a point or more in 10 minutes (1.6% in 5), and moves bunch up near the start.
     */
    const val FAR_OFF_AGE_MS = 10 * 60_000L

    /** A game starting further off than this gets [FAR_OFF_AGE_MS]. */
    const val FAR_OFF_MS = 3 * 60 * 60_000L

    /** The rule in words, for the screens that explain why a bet left. */
    const val LIMIT_TEXT = "5 minutes (10 for games more than 3 hours away)"

    /** How old a quote on a game starting at [startsAtMs] may be at [now]; unknown start = the strict limit. */
    fun maxAgeMs(startsAtMs: Long?, now: Long): Long =
        if (startsAtMs != null && startsAtMs - now > FAR_OFF_MS) FAR_OFF_AGE_MS else MAX_QUOTE_AGE_MS

    /**
     * How long a bet a scan shows keeps being shown, at least: a scan prices only with quotes that are this far
     * inside [MAX_QUOTE_AGE_MS] (at most 3 minutes old), so no bet appears with seconds left before it's hidden
     * (Tj, 2026-09-28: "found several positive EV bets while scanning but they quickly disappeared"). Rechecks
     * and re-pricing keep the plain limit: they judge a scan's bets already shown.
     */
    const val MIN_SHOWN_MS = 2 * 60_000L

    /** The longest any feed's answer is re-used instead of asked again, so it's still fresh through a scan. */
    const val MAX_REUSE_MS = 2 * 60_000L

    /** Whether a quote the feed last saw at [seenAtMs], on a game starting at [startsAtMs], is still fresh at [now]; unknown counts as fresh. */
    fun fresh(seenAtMs: Long?, now: Long, startsAtMs: Long? = null): Boolean = seenAtMs == null || now - seenAtMs <= maxAgeMs(startsAtMs, now)
}
