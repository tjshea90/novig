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
    MARKET_CONSENSUS(4, "UMC-WC", "Market consensus"),
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
    /** Fewest books behind CNO's fair price (1–2-book markets are too thin to trust). */
    val minBooks: Int = 5,
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
)

/** What's kept on disk between launches, so the last list shows before the first re-read. */
@Serializable
data class CnoCache(val snapshot: CnoSnapshot? = null)

/** CNO couldn't be read or its page wasn't understood. [message] is written for the screen. */
class CnoException(message: String, val retryAfterSeconds: Int? = null, cause: Throwable? = null) : Exception(message, cause)
