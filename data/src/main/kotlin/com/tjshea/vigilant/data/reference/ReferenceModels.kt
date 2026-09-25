package com.tjshea.vigilant.data.reference

/** Which side of a line a quote is for, independent of any provider's naming. */
enum class Side { HOME, AWAY, DRAW, OVER, UNDER }

enum class LineKind { MONEYLINE, SPREAD, TOTAL }

/** One outcome price from one book. [point] is the handicap (spreads) or total (totals). */
data class RefQuote(val side: Side, val decimalOdds: Double, val point: Double?)

/** One book's quote for one market of one event. */
data class RefBookMarket(
    val bookKey: String,
    val bookTitle: String,
    val kind: LineKind,
    val quotes: List<RefQuote>,
    val lastUpdateMs: Long?,
) {
    /**
     * The line this quote is on: null for moneylines, the HOME side's handicap for spreads
     * (so "home -3.5 / away +3.5" is line -3.5), the total for totals.
     */
    val line: Double?
        get() = when (kind) {
            LineKind.MONEYLINE -> null
            LineKind.SPREAD -> quotes.firstOrNull { it.side == Side.HOME }?.point
            LineKind.TOTAL -> quotes.firstOrNull { it.side == Side.OVER }?.point
        }
}

/** One game from the reference feed, with every book's quotes. */
data class RefEvent(
    val id: String,
    val sportKey: String,
    val commenceMs: Long,
    val home: String,
    val away: String,
    val markets: List<RefBookMarket>,
)

/** One reference-feed call's result for one sport. */
data class RefSnapshot(
    val sportKey: String,
    val events: List<RefEvent>,
    val fetchedAtMs: Long,
    /** From The Odds API's `x-requests-remaining` header: credits left this billing period. */
    val creditsRemaining: Int? = null,
    val creditsUsed: Int? = null,
)

/** The fair-odds leg: sharp and market books to devig. */
interface ReferenceSource {
    suspend fun odds(sportKey: String, bookmakers: List<String>): RefSnapshot
}
