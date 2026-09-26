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
) {
    /** One side at one book. CNO's game link names the side (`side_id`). */
    val key: String get() = (gameUrl ?: "$event|$market|$bet") + "|" + book
}

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
) {
    /** When CNO's odds were last updated on CNO's side. */
    val dataAtMs: Long get() = fetchedAtMs - (cnoAgeSeconds ?: 0) * 1000L
}

/** What's kept on disk between launches, so the last list shows before the first re-read. */
@Serializable
data class CnoCache(val snapshot: CnoSnapshot? = null)

/** CNO couldn't be read or its page wasn't understood. [message] is written for the screen. */
class CnoException(message: String, val retryAfterSeconds: Int? = null, cause: Throwable? = null) : Exception(message, cause)
