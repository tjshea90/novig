package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * A bet Tj marked "placed" in the widget or the CNO tab (Tj, 2026-09-26: "so I don't place them
 * twice"). It's hidden from both lists from then on, through every refresh and restart, until the
 * game is long over.
 */
@Serializable
data class PlacedBet(
    /** The list item's key: `cno:<CNO row key>` for a CNO bet, `<marketId>/<outcomeId>` for Vigilant's. */
    val key: String,
    /** "Dalton Schultz Over 5.5". */
    val title: String,
    /** "Player Receptions · Houston Texans @ Indianapolis Colts". */
    val detail: String = "",
    /** The same bet at any line (see [com.tjshea.vigilant.data.match.Picks.family]), within one game and market. */
    val family: String = "",
    /** The price it showed when marked ("+141"). */
    val odds: String = "",
    val placedAtMs: Long,
    val startsAtMs: Long? = null,
)

@Serializable
data class PlacedBook(val bets: List<PlacedBet> = emptyList())

/**
 * The placed bets, on disk (placed.json, backed up like the tracker: it's Tj's own record). Old
 * ones drop off by themselves: [KEEP_AFTER_START_MS] after their game starts, or
 * [KEEP_WITHOUT_START_MS] after they were marked when the start isn't known.
 */
class PlacedBets(
    private val store: JsonFileStore<PlacedBook>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Null until [load]. */
    val flow: StateFlow<PlacedBook?> = store.flow

    /** Reads the file and drops what has expired. */
    suspend fun load(): PlacedBook {
        val now = clock()
        val book = store.read()
        return if (book.bets.any { expired(it, now) }) store.update { prune(it, now) } else book
    }

    /** Marks [bet] placed (replacing an earlier mark of the same bet). */
    suspend fun mark(bet: PlacedBet): PlacedBook =
        store.update { b -> prune(PlacedBook(b.bets.filter { it.key != bet.key } + bet), clock()) }

    /** Takes the mark off (Undo, or "not placed after all"). */
    suspend fun unmark(key: String): PlacedBook =
        store.update { b -> if (b.bets.none { it.key == key }) b else PlacedBook(b.bets.filter { it.key != key }) }

    companion object {
        /** A placed bet stays hidden this long after its game starts (overtime, extra innings, live lines). */
        const val KEEP_AFTER_START_MS = 12 * 60 * 60_000L

        /** …and this long after it was marked when its start isn't known. */
        const val KEEP_WITHOUT_START_MS = 3 * 24 * 60 * 60_000L

        fun expired(bet: PlacedBet, now: Long): Boolean =
            if (bet.startsAtMs != null) now > bet.startsAtMs + KEEP_AFTER_START_MS else now > bet.placedAtMs + KEEP_WITHOUT_START_MS

        fun prune(book: PlacedBook, now: Long): PlacedBook =
            if (book.bets.none { expired(it, now) }) book else PlacedBook(book.bets.filterNot { expired(it, now) })
    }
}
