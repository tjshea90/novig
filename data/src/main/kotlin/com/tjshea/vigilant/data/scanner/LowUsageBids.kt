package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource

/**
 * Low-API-usage prop bids (Tj, 2026-10-05: "make an option for a low API usage auto bid feature. this will only scan for current odds on all prop bets available in
 * the games for the next six hours from 2 to 3 sharp books for props only … it should not waste api usage on scanning too frequently or scanning books other than the
 * sharp prop books"; RESEARCH.md §92). The settings half: which books, which feeds carry them, and the scan as the mode narrows it ([profile], applied by
 * [ScanSettings.effective]). The bid half is [com.tjshea.vigilant.data.novig.trading.maker.LowUsage].
 */
object LowUsageBids {

    /**
     * The window a fresh install reads and bids on: the next 6 hours, which is [TrapGuard.DEFAULT_EARLY_HOURS]. It is only the default: the window the mode really uses is
     * [windowHours], which follows the trap guard Tj sets (Tj, 2026-10-07: "I changed the trap guard setting from 6 hours to 8 hours and then to no trap guard at all, but it is
     * hard set at 6 hours trap guard no matter what I select").
     */
    const val WINDOW_HOURS = TrapGuard.DEFAULT_EARLY_HOURS

    /**
     * The hours ahead the mode reads, and bids may go up on: the trap guard's hours ([ScanSettings.trapEarlyHours]; 6 h by default), never past the ordinary reach of Vigilant's scan
     * ([ScanSettings.scanWindowHours]: Days ahead, or Starts within when that is shorter). The trap guard Off (0) means no limit of its own, so the scan reads as far as the ordinary
     * reach says. A scan reads exactly what a bid could be posted on, so no feed is asked for a game no bid may go on, and the trap guard Tj sets is the one that runs.
     */
    fun windowHours(s: ScanSettings): Int =
        (if (s.trapEarlyHours > 0) minOf(s.trapEarlyHours, s.scanWindowHours) else s.scanWindowHours).coerceAtLeast(1)

    /** Two or three sharp books: a fair needs at least [MIN_BOOKS] of them, and more than [MAX_BOOKS] would be more feeds to pay for. */
    const val MIN_BOOKS = 2
    const val MAX_BOOKS = 3

    /**
     * The lowest margin the setting allows: a bid is posted at least this far under the fair (EV at the fair). Tj, 2026-10-06: "add options for minimum 1.5% positive EV or an
     * amount I type in" (until v0.70.3 the floor was [DEFAULT_MARGIN]: "at least 2.5% below"). 0.5% is the floor of what is typed: under it the bid's edge is inside the fair's own error.
     */
    const val MIN_MARGIN = 0.005

    /** The margin a fresh install posts at (Tj's first spec, 2026-10-05: "at least 2.5% below (positive EV) the fair odds"). */
    const val DEFAULT_MARGIN = 0.025

    /** The most a typed margin can be (half the fair: a bid that far under almost never fills). */
    const val MAX_MARGIN = 0.5

    /** The longest American odds a bid may be posted at (no long shots); a tighter [ScanSettings.makerMaxOdds] still wins. */
    const val MAX_ODDS = 130

    /**
     * [ScanSettings.lowUsagePace] (minutes): how often Vigilant's own scan runs while the mode is on. [AUTO] (the default since v0.68.1) scans again just before the bids' fair
     * prices go old, so a bid is rolled forward instead of ending and leaving a gap ([autoGapSeconds]; Tj, 2026-10-05: "it will put up many bids, then leave them a couple
     * minutes, then cancel all of them at the same time"); a number is a fixed pace, at most that often: slower than the freshness limit allows leaves the bids down part of the
     * time (RESEARCH.md §93). 5 min is the shortest fixed pace.
     */
    const val AUTO = 0
    const val MIN_MINUTES = 5
    val PACE_CHOICES = listOf(AUTO, 5, 8, 10, 15, 20, 30)

    /**
     * The gap that keeps bids up (Auto pace): a bid is re-posted from a fresher fair once it is within [MakerRules.REFRESH_BEFORE_MS] of its end, and its end is the oldest quote's
     * stamp plus the freshness limit, so a scan must start that long before the limit: 5 min - 2 = 3 min for a game inside [Freshness.FAR_OFF_MS] of its start, 10 - 2 = 8 min
     * beyond it. A feed's quote is already some seconds old when it is read, and the bids of a scan are posted as its Novig reads reach them, so no longer gap is safe.
     */
    const val NEAR_GAP_SECONDS = ((Freshness.MAX_QUOTE_AGE_MS - MakerRules.REFRESH_BEFORE_MS) / 1_000L).toInt()
    const val FAR_GAP_SECONDS = ((Freshness.FAR_OFF_AGE_MS - MakerRules.REFRESH_BEFORE_MS) / 1_000L).toInt()

    /**
     * The Auto gap now: [NEAR_GAP_SECONDS] when a game a bid could still be posted on ([stopMs] before its start) starts inside the 3-hour limit's reach (a game just over 3 h
     * away gets the short limit within one far gap, so it counts), else [FAR_GAP_SECONDS]. [startsMs] null = the last scan isn't known (just started, or it failed): the short gap.
     */
    fun autoGapSeconds(startsMs: Collection<Long>?, now: Long, stopMs: Long): Int {
        if (startsMs == null) return NEAR_GAP_SECONDS
        val reach = Freshness.FAR_OFF_MS + FAR_GAP_SECONDS * 1_000L
        return if (startsMs.any { it - stopMs > now && it - now <= reach }) NEAR_GAP_SECONDS else FAR_GAP_SECONDS
    }

    /** The shortest gap Vigilant's own scan keeps in the mode at [s]' pace: a fixed pace, or the Auto pace's short gap. */
    fun shortestGapSeconds(s: ScanSettings): Int = if (s.lowUsagePace == AUTO) NEAR_GAP_SECONDS else s.lowUsagePace.coerceAtLeast(MIN_MINUTES) * 60

    /** The gap Vigilant's own scan keeps in the mode right now: [s]' fixed pace, or the Auto gap for the games the last scan saw ([startsMs], their start times). */
    fun gapSeconds(s: ScanSettings, startsMs: Collection<Long>?, now: Long): Int =
        if (s.lowUsagePace == AUTO) autoGapSeconds(startsMs, now, s.makerStopMinutes.coerceAtLeast(0) * 60_000L) else shortestGapSeconds(s)

    /** [ScanSettings.lowUsageMargin]'s chips (the field beside them takes any other margin from [MIN_MARGIN] to [MAX_MARGIN]). */
    val MARGIN_CHOICES = listOf(0.015, DEFAULT_MARGIN, 0.03, 0.035, 0.04)

    /**
     * A margin typed as a percent ("1.5", "1,5", " 2.25 %") as a fraction (0.015), at most two decimals of a percent, or null when it isn't a number or is outside [MIN_MARGIN]..[MAX_MARGIN]
     * (the field says why and saves nothing).
     */
    fun parseMargin(text: String): Double? {
        val t = text.trim().removeSuffix("%").trim().replace(',', '.')
        if (t.isEmpty() || !t.all { it.isDigit() || it == '.' } || t.count { it == '.' } > 1) return null
        val percent = t.toDoubleOrNull() ?: return null
        val m = Math.round(percent * 100) / 10_000.0
        return m.takeIf { it >= MIN_MARGIN - 1e-12 && it <= MAX_MARGIN + 1e-12 }
    }

    /** [m] as the field shows it: a percent with no trailing zeros ("2.5", "3", "1.55"). */
    fun marginText(m: Double): String = String.format(java.util.Locale.US, "%.2f", m * 100).trimEnd('0').trimEnd('.')

    /** The most Novig prices a mode scan reads at least: every prop the picked books quote in the window (a read per prop is a Novig call, never a credit). */
    const val MIN_NOVIG_READS = 1000

    private const val KALSHI = "kalshi"

    // The ids of the feeds ([com.tjshea.vigilant.data.reference.ReferenceSource.id]) that carry the picked books.
    const val FEED_KALSHI = "kalshi"
    const val FEED_PINNACLE = "pinnacle"
    const val FEED_PROPLINE = "propline_props"
    const val FEED_PARLAY = "parlay_props"

    /**
     * A book the mode can price from. [key] is the book's key in the feeds' answers (what [com.tjshea.vigilant.engine.FairSettings.sharpBooks] is matched against),
     * [parlayKey] the name ParlayAPI's `bookmakers` filter knows it by, [feeds] who carries its PROPS, cheapest first.
     */
    data class Book(val key: String, val title: String, val parlayKey: String?, val feeds: List<String>, val note: String)

    /**
     * In the order of this repo's own ranking of the sharpest prop books (RESEARCH.md §66.2, §76.4: the exchanges Kalshi and ProphetX lead, then FanDuel and Caesars;
     * Pinnacle's prop prices are among the softest, so it is last and not a default): the first two or three that are picked are the mode's fair.
     */
    val BOOKS: List<Book> = listOf(
        Book("kalshi", "Kalshi", null, listOf(FEED_KALSHI), "an exchange, read free and direct"),
        Book("prophetx", "ProphetX", "prophetx", listOf(FEED_PARLAY), "an exchange; ParlayAPI only (3 credits a league)"),
        Book("fanduel", "FanDuel", "fanduel", listOf(FEED_PROPLINE, FEED_PARLAY), "originates its own numbers; PropLine (free requests) or ParlayAPI"),
        Book("williamhill_us", "Caesars", "caesars", listOf(FEED_PARLAY), "takes high limits on props; ParlayAPI only"),
        Book("draftkings", "DraftKings", "draftkings", listOf(FEED_PROPLINE, FEED_PARLAY), "sharp on MLB props; PropLine or ParlayAPI"),
        Book("pinnacle", "Pinnacle", "pinnacle", listOf(FEED_PINNACLE, FEED_PROPLINE, FEED_PARLAY), "low prop limits, so a softer reference; PinnWire / pinnapi, PropLine or ParlayAPI"),
    )

    private val BY_KEY = BOOKS.associateBy { it.key }

    /** Kalshi, ProphetX and FanDuel: the three the research ranks first, and one metered call (ParlayAPI's) plus one free read (Kalshi) carry them. */
    val DEFAULT_BOOKS: Set<String> = linkedSetOf("kalshi", "prophetx", "fanduel")

    /**
     * The books [s] picks, as the mode uses them: only known books, two or three, in the ranking's order. Fewer than two valid ones (a hand-edited or damaged file) is
     * topped up from the defaults; more than three keeps the three ranked highest.
     */
    fun books(s: ScanSettings): Set<String> {
        val known = BOOKS.map { it.key }.filter { it in s.lowUsageBooks }
        val topped = if (known.size >= MIN_BOOKS) known else known + DEFAULT_BOOKS.filter { it !in known }.take(MIN_BOOKS - known.size)
        return BOOKS.map { it.key }.filter { it in topped }.take(MAX_BOOKS).toCollection(LinkedHashSet())
    }

    /** [s]' picks after Tj taps [key]: added up to [MAX_BOOKS], removed down to [MIN_BOOKS] (a tap that would break either does nothing). */
    fun toggled(s: ScanSettings, key: String): Set<String> {
        val now = books(s)
        return when {
            key !in BY_KEY -> now
            key in now -> if (now.size > MIN_BOOKS) now - key else now
            now.size < MAX_BOOKS -> BOOKS.map { it.key }.filter { it in now || it == key }.toCollection(LinkedHashSet())
            else -> now
        }
    }

    /** The books' names, ranked, for the screens ("Kalshi, ProphetX, FanDuel"). */
    fun names(books: Set<String>): String = BOOKS.filter { it.key in books }.joinToString(", ") { it.title }

    /** The names ParlayAPI's `bookmakers` filter takes for the picked books it carries (Kalshi is read direct, never through it). */
    fun parlayBooks(s: ScanSettings): List<String> = books(s).mapNotNull { BY_KEY[it] }.filter { FEED_PARLAY in it.feeds }.mapNotNull { it.parlayKey }

    /**
     * Which feeds the picked books need, the fewest that carry them (RESEARCH.md §92.2): a book carried by one available feed takes that feed; a book several carry
     * takes a feed already needed for another book if there is one (ProphetX needs ParlayAPI's call, so FanDuel comes in the same call, not a second feed), else the
     * cheapest available. [available]: the feed ids with a key and a switch on. [Plan.unreachable]: picked books no available feed carries (the mode then has fewer
     * than two books on lines that need them, and says so).
     */
    data class Plan(val feeds: List<String>, val assigned: Map<String, String>, val unreachable: List<String>)

    fun feedsFor(books: Set<String>, available: Set<String>): Plan {
        val picked = BOOKS.filter { it.key in books }
        val reach = picked.associate { it.key to it.feeds.filter { f -> f in available } }
        val needed = LinkedHashSet<String>()
        // Books that one feed alone can carry go first: those feeds are needed whatever else is picked.
        picked.forEach { b -> reach.getValue(b.key).singleOrNull()?.let { needed += it } }
        picked.forEach { b ->
            val options = reach.getValue(b.key)
            if (options.size > 1 && options.none { it in needed }) needed += options.first()
        }
        val assigned = LinkedHashMap<String, String>()
        picked.forEach { b -> reach.getValue(b.key).firstOrNull { it in needed }?.let { assigned[b.key] = it } }
        val order = listOf(FEED_KALSHI, FEED_PINNACLE, FEED_PROPLINE, FEED_PARLAY)
        return Plan(order.filter { it in needed }, assigned, picked.map { it.key }.filter { it !in assigned })
    }

    /**
     * [s] as the scan reads it while the mode is on ([ScanSettings.effective]): props only; the next [windowHours] h (the trap guard's hours, 6 by default; a shorter "starts within" is kept); the fair is the
     * picked sharp books' alone, each devigged the worst way, at least two of them, quotes past the freshness limit dropped before the devig; Polymarket, The Odds API and
     * the soft books are off, the feeds that carry the picked books on (the app offers only the cheapest that cover them, [feedsFor]); live games off; every prop the books
     * quote is priced (Novig's own reads are not a credit). The scanner choice, the leagues, and every betting setting are Tj's own.
     */
    fun profile(s: ScanSettings): ScanSettings {
        val picked = books(s)
        val carried = BOOKS.filter { it.key in picked }.flatMapTo(HashSet()) { it.feeds }
        return s.copy(
            families = setOf(MarketFamily.PLAYER_PROPS),
            fairSource = FairSource.SHARP, devigMethod = DevigMethod.WORST_CASE, sharpBooks = picked, fallbackToAverage = false,
            minBooks = MIN_BOOKS, minSharpBooks = MIN_BOOKS,
            referenceBooks = picked.filter { it != KALSHI }, usePinnacle = FEED_PINNACLE in carried, usePolymarket = false, useKalshi = FEED_KALSHI in carried,
            useOddsApi = false, useParlay = FEED_PARLAY in carried, usePropLine = FEED_PROPLINE in carried, useBookProps = true,
            includeLive = false,
            startsWithinHours = windowHours(s),
            // The sportsbook-props horizon is Tj's own ("Games within"); [ScanSettings.bookPropWindowHours] already never goes past the scan's window.
            bookPropHours = s.bookPropHours,
            propsPerGame = ScanSettings.NO_LIMIT, propLineGamesPerScan = ScanSettings.NO_LIMIT, maxBooksPerScan = maxOf(s.maxBooksPerScan, MIN_NOVIG_READS),
            lowUsageScan = true,
        )
    }
}
