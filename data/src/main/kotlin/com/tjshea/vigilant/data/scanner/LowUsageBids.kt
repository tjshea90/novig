package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource

/**
 * Low-API-usage prop bids (Tj, 2026-10-05: "make an option for a low API usage auto bid feature. this will only scan for current odds on all prop bets available in
 * the games for the next six hours from 2 to 3 sharp books for props only … it should not waste api usage on scanning too frequently or scanning books other than the
 * sharp prop books"; RESEARCH.md §92). The settings half: which books, which feeds carry them, and the scan as the mode narrows it ([profile], applied by
 * [ScanSettings.effective]). The bid half is [com.tjshea.vigilant.data.novig.trading.maker.LowUsage].
 */
object LowUsageBids {

    /** Only games starting inside this many hours are read or bid on. */
    const val WINDOW_HOURS = 6

    /** Two or three sharp books: a fair needs at least [MIN_BOOKS] of them, and more than [MAX_BOOKS] would be more feeds to pay for. */
    const val MIN_BOOKS = 2
    const val MAX_BOOKS = 3

    /** A bid is posted at least this far under the fair (EV at the fair); the setting can raise it, never lower it. */
    const val MIN_MARGIN = 0.025

    /** The longest American odds a bid may be posted at (no long shots); a tighter [ScanSettings.makerMaxOdds] still wins. */
    const val MAX_ODDS = 130

    /** [ScanSettings.lowUsageMinutes]: Vigilant's own scan runs at most this often in the mode. 5 min is the shortest (the freshness limit inside 3 h of the start). */
    const val DEFAULT_MINUTES = 10
    const val MIN_MINUTES = 5
    val PACE_CHOICES = listOf(5, 8, 10, 15, 20, 30)

    /** [ScanSettings.lowUsageMargin]'s choices (2.5% is the floor). */
    val MARGIN_CHOICES = listOf(MIN_MARGIN, 0.03, 0.035, 0.04)

    /** The most Novig prices a mode scan reads at least: every prop the picked books quote in the window (a read per prop is a Novig call, never a credit). */
    const val MIN_NOVIG_READS = 1000

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
     * [s] as the scan reads it while the mode is on ([ScanSettings.effective]): props only; the next [WINDOW_HOURS] h (a shorter "starts within" is kept); the fair is the
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
            referenceBooks = picked.filter { it != FEED_KALSHI }, usePinnacle = FEED_PINNACLE in carried, usePolymarket = false, useKalshi = FEED_KALSHI in carried,
            useOddsApi = false, useParlay = FEED_PARLAY in carried, usePropLine = FEED_PROPLINE in carried, useBookProps = true,
            includeLive = false,
            startsWithinHours = if (s.startsWithinHours in 1..WINDOW_HOURS) s.startsWithinHours else WINDOW_HOURS,
            bookPropHours = minOf(s.bookPropHours, WINDOW_HOURS),
            propsPerGame = ScanSettings.NO_LIMIT, propLineGamesPerScan = ScanSettings.NO_LIMIT, maxBooksPerScan = maxOf(s.maxBooksPerScan, MIN_NOVIG_READS),
            lowUsageScan = true,
        )
    }
}
