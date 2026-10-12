package com.tjshea.vigilant.data.boltodds

/**
 * One priced outcome of one game at one book, as BoltOdds sends it (BOLTODDS_API.md §3): `outcomes` is a map keyed by a readable label ("New York Mets Moneyline"); each value carries the American
 * price as a string and the structured fields Vigilant classifies by. [atMs] is when the message that set it was sent (its `timestamp`, UTC), or the read time of the snapshot it came in.
 */
data class BoltOutcome(
    val label: String,
    /** American odds, e.g. -110 or 900. A suspended, deleted or unavailable line has none and is not kept. */
    val odds: Int,
    /** `outcome_name`: the market ("Moneyline", "Spread", "Goals", "Home Runs"). */
    val name: String,
    /** `outcome_line`: the handicap or the over/under number; null on a moneyline. */
    val line: Double?,
    /** `outcome_over_under`: "Over" / "Under" (as BoltOdds spells it), null when the outcome is a team side. */
    val overUnder: String?,
    /** `outcome_target`: the team or the player the outcome is about. */
    val target: String?,
    val atMs: Long,
) {
    val isOver: Boolean get() = overUnder?.trim()?.startsWith("o", ignoreCase = true) == true
    val isUnder: Boolean get() = overUnder?.trim()?.startsWith("u", ignoreCase = true) == true
}

/** What identifies a game at one book on the board. BoltOdds names a game once for every book (`get_games`: "Team A vs Team B, YYYY-MM-DD, HH"). */
data class BoltGameKey(val sportsbook: String, val game: String)

/** One game's outcomes at one book: what a `game_update` replaces whole and a `line_update` edits. */
data class BoltBookGame(
    val sport: String,
    val sportsbook: String,
    val game: String,
    val home: String,
    val away: String,
    /** `info.when`, read as US Eastern time (BOLTODDS_API.md §3): null when the message carried none. */
    val startMs: Long?,
    val outcomes: Map<String, BoltOutcome>,
    /** The newest message time folded into this entry: a snapshot never overwrites a newer live update. */
    val updatedAtMs: Long,
    /** `info.is_live` as the book last said it (BOLTODDS_API.md §9, sample #2): the game is in play. */
    val live: Boolean = false,
) {
    val key: BoltGameKey get() = BoltGameKey(sportsbook, game)

    /** The converted markets of this entry by what they were converted for ([BoltConvert]); not part of the entry's identity. */
    internal val marketsCache = java.util.concurrent.ConcurrentHashMap<String, List<com.tjshea.vigilant.data.reference.RefBookMarket>>()
}

/** The `data` object of a message, parsed. [outcomes] holds only the outcomes that carry a price; [removed] the labels sent with no price (suspended / deleted). */
data class BoltData(
    val sport: String?,
    val sportsbook: String?,
    val game: String?,
    val home: String?,
    val away: String?,
    val startMs: Long?,
    val infoId: String?,
    val outcomes: List<BoltOutcome>,
    val removed: List<String>,
    /** `info.is_live`: the game is in play at this book (null when the message did not say). */
    val live: Boolean? = null,
)

/** One message of the odds socket or one element of the snapshot (BOLTODDS_API.md §3). */
sealed interface BoltMessage {
    object SocketConnected : BoltMessage
    object Ping : BoltMessage
    data class SubscriptionUpdated(val text: String) : BoltMessage
    data class Error(val text: String) : BoltMessage

    /** A whole game at a book (`game_update`, `game_added`, or a snapshot's `initial_state`). */
    data class GameUpdate(val atMs: Long, val data: BoltData, val initial: Boolean = false) : BoltMessage

    /** Some outcomes of a game changed (`line_update`). */
    data class LineUpdate(val atMs: Long, val data: BoltData) : BoltMessage
    data class GameRemoved(val atMs: Long, val data: BoltData) : BoltMessage
    data class SportClear(val atMs: Long, val sport: String, val sportsbook: String) : BoltMessage
    data class BookClear(val atMs: Long, val sportsbook: String) : BoltMessage

    /** An `action` Vigilant does not know. Kept so a new message type shows in Diagnostics instead of vanishing. */
    data class Unknown(val action: String) : BoltMessage
}
