package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel

/**
 * Novig's official v3 catalog, as returned by the public routes (NOVIG_API.md §5). Hierarchy:
 * event -> markets -> outcomes, and each outcome has its own order book.
 */
data class NovigEvent(
    val eventId: String,
    val sport: String,
    val league: String,
    /** OPEN_PREGAME, CLOSED_PREGAME, OPEN_INGAME, SETTLED, FINAL, DELAYED, CANCELED. */
    val status: String,
    /** e.g. "Baltimore Ravens @ Dallas Cowboys": away @ home, Novig's full names. */
    val description: String,
    val startsTs: Long,
) {
    val isLive: Boolean get() = status == STATUS_LIVE
    val matchup: Matchup? by lazy { NovigText.parseMatchup(description) }

    companion object {
        const val STATUS_PREGAME = "OPEN_PREGAME"
        const val STATUS_LIVE = "OPEN_INGAME"
    }
}

data class NovigOutcome(
    val outcomeId: String,
    /** Display text, e.g. "DAL", "DAL +20.5", "Over 47.5", "Yes". Never read a side from array position. */
    val name: String,
    /** TBD, WIN, LOSS, PUSH, or a decimal price string for a fair-market-value settlement. */
    val status: String,
)

data class NovigMarket(
    val marketId: String,
    val eventId: String,
    val marketType: String,
    val status: String,
    val description: String,
    val startsTs: Long,
    /** Null only if Novig sent a fee object we couldn't read. Such a market is skipped, never assumed free. */
    val fee: MarketFee?,
    val outcomes: List<NovigOutcome>,
) {
    val isOpen: Boolean get() = status == "OPEN"

    fun otherOutcome(outcomeId: String): NovigOutcome? =
        if (outcomes.size == 2) outcomes.firstOrNull { it.outcomeId != outcomeId } else null
}

/** One aggregated price level of resting bids: every order at [priceMilli]/1000 for one outcome. */
data class BidLevel(val priceMilli: Int, val contracts: Long) {
    val price: Double get() = priceMilli / 1000.0
}

/**
 * A market's order book. **Every order is a buy** (NOVIG_API.md §7): each outcome's list holds
 * resting bids for that outcome, best first. A bid at P on one outcome is liquidity for the other
 * outcome at `1 − P`.
 */
data class NovigBook(
    val marketId: String,
    val seq: Long,
    val bidsByOutcome: Map<String, List<BidLevel>>,
    val fetchedAtMs: Long,
) {
    /**
     * What you could buy [outcomeId] for right now, best (cheapest) first: every resting bid on
     * the other outcome, flipped to `1 − bid`.
     */
    fun takeLadder(market: NovigMarket, outcomeId: String): List<TakeLevel> {
        val other = market.otherOutcome(outcomeId) ?: return emptyList()
        return bidsByOutcome[other.outcomeId].orEmpty().map { TakeLevel((1000 - it.priceMilli) / 1000.0, it.contracts) }
    }

    /** The best resting bid for [outcomeId] itself: what you could sell it back for. */
    fun bestBid(outcomeId: String): BidLevel? = bidsByOutcome[outcomeId]?.firstOrNull()
}

/** "Away @ Home" split out of an event description. */
data class Matchup(val away: String, val home: String)
