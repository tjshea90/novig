package com.tjshea.vigilant.data.cno

import kotlinx.serialization.Serializable

/**
 * One row of CrazyNinjaOdds' "Positive EV" table (RESEARCH.md §18.1), as CNO printed it: CNO's
 * EV and fair odds, not Vigilant's. Prices are American odds.
 */
@Serializable
data class CnoRow(
    /** EV as a fraction (0.0405 = 4.05%), from CNO's EV column. */
    val ev: Double,
    val startsAtMs: Long? = null,
    val sport: String = "",
    val league: String = "",
    val event: String,
    val market: String,
    /** "Brock Bowers Under 4.5". */
    val bet: String,
    val odds: Int,
    /** Dollars available at [odds] on an exchange ("+100 ($109)"); null where CNO shows none. */
    val available: Double? = null,
    val book: String,
    val fairOdds: Int? = null,
    /** CNO's fair probability for this side (`data-fairpercentage`). */
    val fairProbability: Double? = null,
    /** How many books CNO's fair price came from. */
    val books: Int? = null,
    /** CNO's page for this game and side (every book's price). */
    val gameUrl: String? = null,
    /** CNO's deeplink to the bet at [book] (a consent page first, then the book). */
    val betUrl: String? = null,
    /** CNO marked it ⚠️: devigged from one-way lines with an estimated juice (less reliable). */
    val oneWay: Boolean = false,
    /**
     * Every column CNO printed for the row, by its header, and every `data-*` attribute of the row (`@data-fairpercentage`), as text: kept only by the
     * scan study's wide read ([CnoSource.fetchWide]), so a column CNO adds is logged without the app knowing it. Empty on the list's own reads.
     */
    val cols: Map<String, String> = emptyMap(),
) {
    /** One side at one book. CNO's game link names the side (`side_id`); the devig in it is dropped. */
    val key: String get() = ((gameUrl?.replace(DEVIG_PARAM, "")) ?: "$event|$market|$bet") + "|" + book

    /** CNO's `side_id` for this bet (the row id on its game page). */
    val sideId: String? get() = gameUrl?.let { SIDE_ID.find(it)?.groupValues?.get(1) }
}

private val DEVIG_PARAM = Regex("[&?]devig_method=\\d+")
private val SIDE_ID = Regex("[?&]side_id=(\\d+)")

/**
 * CNO's devig choices that are worst-case (RESEARCH.md §19): the longest fair value of
 * multiplicative, additive/Shin and power. [code] is CNO's dropdown value, [label] its EV column.
 */
enum class CnoDevig(val code: Int, val label: String, val displayName: String) {
    /** The worse of the other two: CNO's most cautious setting (Tj's default, 2026-09-26). */
    CONSERVATIVE(8, "C-WC", "Conservative"),
    LIQUIDITY_WEIGHTED(0, "LW-WC", "Liquidity-weighted"),
    /** CNO labels its column "UW-WC" (seen live 2026-09-26). */
    MARKET_CONSENSUS(4, "UW-WC", "Market consensus"),
}

/**
 * The CNO scanner's filters, posted in CNO's own form on every read (so CNO ranks and trims its
 * list by them) and enforced again in the app ([CnoChecks]).
 */
@Serializable
data class CnoFilters(
    val devig: CnoDevig = CnoDevig.CONSERVATIVE,
    /** Longest American odds shown (+150 = negative odds up to +150); 0 = no limit. */
    val maxOdds: Int = 150,
    /** Fewest books behind CNO's fair price, 1-4 ([ScanSettings.CNO_MIN_BOOKS_CHOICES]; 1-2-book markets are thin). */
    val minBooks: Int = 4,
    /** Smallest EV shown, as a fraction. */
    val minEv: Double = 0.01,
    /** How many rows CNO sends (best EV first); fewer rows = a smaller download per refresh. */
    val rows: Int = 50,
    /** CNO's "Require a Complete Sportsbook": at least one book prices every side. */
    val completeBook: Boolean = true,
    /** Sides a market needs at a book (2 = both sides priced, so the vig can be removed honestly). */
    val minSides: Int = 2,
)

/** One read of Tj's CNO view. */
@Serializable
data class CnoSnapshot(
    /** The view that was read (after [CnoView.normalize]). */
    val url: String,
    val rows: List<CnoRow>,
    val fetchedAtMs: Long,
    /** CNO's own "Last Updated: 27 seconds ago" at the time of the read. */
    val cnoAgeSeconds: Int? = null,
    /** The EV column's method label ("LW-WC" = liquidity-weighted, worst case). */
    val evLabel: String? = null,
    /** CNO's red message under the table, when it shows one. */
    val note: String? = null,
    /** The filters it was read with (null in lists saved before v0.14.0). */
    val filters: CnoFilters? = null,
    /** True for the scan study's wide read ([CnoSource.fetchWide]): CNO's numeric filters opened right up, so [filters] is what the app's own list would apply to it. */
    val wide: Boolean = false,
    /** For a wide read: the filter fields as they were posted (`TextBoxMinimumEVPercentage=0%, …`), so the first log says what CNO was asked. */
    val asked: String? = null,
    /** For a wide read: the most rows it asked CNO for; a read with that many rows may have left some out. */
    val limit: Int? = null,
) {
    /** When CNO's odds were last updated on CNO's side. */
    val dataAtMs: Long get() = fetchedAtMs - (cnoAgeSeconds ?: 0) * 1000L
}

/** One book's prices for a bet and its other side, from CNO's game page. */
@Serializable
data class CnoBookPrice(
    /** CNO's column code ("PN", "DK", "NV"). */
    val code: String,
    val odds: Int? = null,
    val available: Double? = null,
    val otherOdds: Int? = null,
    val otherAvailable: Double? = null,
    /**
     * When the book's own quote was made, when the source says (a feed's per-market time; ParlayAPI's `last_update`); null where it doesn't
     * (CNO's game page: only the whole page has a "Last Updated"). What a sharp-book confirmation judges freshness by ([com.tjshea.vigilant.data.scanner.SharpConfirm]).
     */
    val atMs: Long? = null,
) {
    val name: String get() = CnoBooks.name(code)
    val twoSided: Boolean get() = odds != null && otherOdds != null
}

/** Every book's price for one bet (and its other side), as CNO's game page listed them. */
@Serializable
data class CnoBooksView(
    val bet: String,
    val otherBet: String? = null,
    /** CNO's fair odds for this side on the game page (the devig of the link). */
    val cnoFair: Int? = null,
    val cnoFairOneWay: Boolean = false,
    val prices: List<CnoBookPrice>,
    val fetchedAtMs: Long,
    /** CNO's own "Last Updated: 27 seconds ago" on the game page at the read, when the page said ([dataAtMs]); null otherwise. */
    val cnoAgeSeconds: Int? = null,
) {
    /** When CNO's odds on this page were last updated on CNO's side (the read's time when the page didn't say). */
    val dataAtMs: Long get() = fetchedAtMs - (cnoAgeSeconds ?: 0) * 1000L
}

/** What's kept on disk between launches, so the last list shows before the first re-read. */
@Serializable
data class CnoCache(val snapshot: CnoSnapshot? = null)

/** Bets' Novig app links by CNO deeplink (cno_links.json): looked up once, good for the line's life. */
@Serializable
data class CnoLinks(val links: Map<String, String> = emptyMap())

/** CNO couldn't be read or its page wasn't understood. [message] is written for the screen. */
class CnoException(message: String, val retryAfterSeconds: Int? = null, cause: Throwable? = null) : Exception(message, cause)
