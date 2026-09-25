package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings

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

    /** The same quote seen from the other team's side (home and away swapped). */
    fun flipped(): RefBookMarket = copy(
        quotes = quotes.map {
            when (it.side) {
                Side.HOME -> it.copy(side = Side.AWAY)
                Side.AWAY -> it.copy(side = Side.HOME)
                else -> it
            }
        },
    )
}

/** One game from a reference feed, with every book's quotes. */
data class RefEvent(
    val id: String,
    val sportKey: String,
    val commenceMs: Long,
    val home: String,
    val away: String,
    val markets: List<RefBookMarket>,
    /**
     * Set when the provider only knows the game's date, not its start time (Kalshi tickers carry
     * an Eastern-time date). Matching then compares dates instead of start times.
     */
    val etDate: String? = null,
) {
    fun flipped(): RefEvent = copy(home = away, away = home, markets = markets.map { it.flipped() })
}

/** One reference-feed call's result for one sport. */
data class RefSnapshot(
    val sportKey: String,
    val events: List<RefEvent>,
    val fetchedAtMs: Long,
    /** From The Odds API's `x-requests-remaining` header: credits left this billing period. */
    val creditsRemaining: Int? = null,
    val creditsUsed: Int? = null,
    /** [ReferenceSource.id] of the provider that produced it. */
    val provider: String = "",
)

/** A fair-odds provider. Each call covers one league. */
interface ReferenceSource {
    /** Stable id: "pinnacle", "polymarket", "kalshi", "oddsapi". */
    val id: String
    val displayName: String

    /** True when each call spends a limited quota (credits), so callers should re-use results. */
    val metered: Boolean get() = false

    suspend fun odds(league: League, settings: ScanSettings): RefSnapshot
}

/**
 * Turns an exchange's two-sided quote into book-style decimal odds. Buying side A costs its ask;
 * buying side B costs `1 - bid(A)`. The gap between them plays the role of a book's vig, so the
 * normal devig math lands on the mid. Returns null for thin or lopsided markets that would only
 * add noise to a fair line.
 */
object ExchangeQuote {
    fun toDecimal(bid: Double?, ask: Double?, maxSpread: Double): Pair<Double, Double>? {
        if (bid == null || ask == null) return null
        if (bid <= 0.0 || ask >= 1.0 || ask <= bid) return null
        if (ask - bid > maxSpread + 1e-9) return null
        val buyA = ask
        val buyB = 1.0 - bid
        if (buyA !in MIN_PRICE..MAX_PRICE || buyB !in MIN_PRICE..MAX_PRICE) return null
        return (1.0 / buyA) to (1.0 / buyB)
    }

    const val MIN_PRICE = 0.02
    const val MAX_PRICE = 0.98
}
