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
    val LIMIT_TEXT: String get() = if (sgoMode) "over ${sgoMaxAgeMs / 60_000L} minutes" else "over 5 minutes, or 10 for games more than 3 hours away"

    /**
     * SportsGameOdds Pro mode (Tj, 2026-10-09: "match what sgo offers without dropping bets, but keep a guard that drops bets if the odds are 10 minutes old or older"). SGO refreshes pregame prices
     * about every 5 minutes (measured, SPORTSGAMEODDS_API.md §10), so its quotes are 3-6 minutes old when read: the 5-minute limit for a game inside 3 hours would drop them. While SGO Pro is on,
     * every game gets [FAR_OFF_AGE_MS] (10 minutes) as the limit, and a quote older than that is dropped (a scan prices only with quotes under 8 minutes old, [MIN_SHOWN_MS] of headroom). Set from `ScanSettings.sgoPro` at the start of each scan and when the switch
     * changes; off, every function here is exactly what it was.
     */
    @Volatile var sgoMode: Boolean = false

    /** The guard in SGO Pro mode ([ScanSettings.sgoMaxAgeMinutes]; 10 minutes unless Tj picks longer): a quote older than this is dropped, for every game. */
    @Volatile var sgoMaxAgeMs: Long = FAR_OFF_AGE_MS

    /**
     * The feeds [sgoMode] is for: SportsGameOdds refreshes pregame prices about every 5 minutes, so ITS quotes are 3-7 minutes old when read (feed ids `sgo`, `sgo-props`). Every other feed keeps the plain limit
     * whatever the switch says (PX9, Tj 2026-10-25: "Why are the odds this stale if they are streamed?": with SGO Pro on, the engine let PropLine's and ParlayAPI's quotes up to 15 minutes old price a line,
     * median 9 minutes behind a listed bet's fair odds, because the relaxed limit was one global number; Tj's own words of 2026-10-09 were "match what sgo offers without dropping bets").
     */
    fun slowFeed(source: String?): Boolean = source != null && source.startsWith("sgo")

    /**
     * How old a quote on a game starting at [startsAtMs] may be at [now]; unknown start = the strict limit. In [sgoMode] SportsGameOdds' quotes ([slowFeed]) and callers that name no [source] (the whole
     * fair, a bid's life: it may hold SGO's) get [sgoMaxAgeMs]; a named fast feed keeps the plain 5 minutes (10 for a game over 3 hours away).
     */
    fun maxAgeMs(startsAtMs: Long?, now: Long, source: String? = null): Long =
        if (sgoMode && (source == null || slowFeed(source))) sgoMaxAgeMs else if (startsAtMs != null && startsAtMs - now > FAR_OFF_MS) FAR_OFF_AGE_MS else MAX_QUOTE_AGE_MS

    /**
     * How long a bet a scan shows keeps being shown, at least: a scan prices only with quotes that are this far
     * inside [MAX_QUOTE_AGE_MS] (at most 3 minutes old), so no bet appears with seconds left before it's hidden
     * (Tj, 2026-09-28: "found several positive EV bets while scanning but they quickly disappeared"). Rechecks
     * and re-pricing keep the plain limit: they judge a scan's bets already shown.
     */
    val MIN_SHOWN_MS: Long get() = if (continuous) 0L else if (sgoMode) 60_000L else 2 * 60_000L   // SGO Pro mode: 1 minute (its prices are already 3-7 minutes old when read)

    /** The longest any feed's answer is re-used instead of asked again, so it's still fresh through a scan. */
    const val MAX_REUSE_MS = 2 * 60_000L

    /** A quote this fresh counts as current for [freshFirst]. */
    const val FRESH_MS = 2 * 60_000L

    /** Beside fresh quotes, a quote older than this is left out of the fair odds ([freshFirst]). */
    val STALE_BESIDE_FRESH_MS: Long get() = if (continuous) MAX_QUOTE_AGE_MS else 3 * 60_000L

    /**
     * The GitHub engine re-prices its list every 3 s on the streams (RealtimeList): a bet is re-judged continuously, so a scan need not keep [MIN_SHOWN_MS] in hand (it shrank the 5-minute limit to 3), and
     * beside fresh quotes a book is left out only past the plain [MAX_QUOTE_AGE_MS] (PX, Tj 2026-10-25: "All scanned bets should have as many books for the fair odds as possible": PropLine re-reads a
     * book every 60-190 s, so the 3-minute rules dropped about a third of its quotes, measured by [QuoteCensus]). Set by the engine; the phone keeps the old rules.
     */
    @Volatile var continuous: Boolean = false

    /**
     * The books a line's fair odds are worked out from, the stale ones left out when fresh ones price it (Tj, 2026-10-25, PB20: "If the odds are very stale, do not use them at all for the fair odds
     * calculation"; "Why are the odds 7-10 minutes old if they are streaming?": one slow book of ten dated the whole line). With two or more books quoted within [FRESH_MS], a book older than
     * [STALE_BESIDE_FRESH_MS] is dropped, except a sharp book while no other sharp book is fresh (the sharp anchor is never lost to a soft book's speed). A book with no stamp is kept.
     */
    fun <T> freshFirst(books: List<T>, now: Long, stamp: (T) -> Long?, sharp: (T) -> Boolean): List<T> {
        val fresh = books.filter { b -> stamp(b)?.let { now - it <= FRESH_MS } == true }
        if (fresh.size < 2) return books
        val freshSharp = fresh.any(sharp)
        return books.filter { b ->
            val at = stamp(b) ?: return@filter true
            now - at <= STALE_BESIDE_FRESH_MS || (sharp(b) && !freshSharp)
        }
    }

    /** Whether a quote the feed last saw at [seenAtMs], on a game starting at [startsAtMs], is still fresh at [now]; unknown counts as fresh. */
    fun fresh(seenAtMs: Long?, now: Long, startsAtMs: Long? = null, source: String? = null): Boolean = seenAtMs == null || now - seenAtMs <= maxAgeMs(startsAtMs, now, source)
}
