package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetsScope
import com.tjshea.vigilant.data.tracker.TrackedBet

/**
 * One Novig market Tj holds through the API (the Vigilant subaccount), from the Tracker's open API bets in it: the contracts and dollars on each outcome
 * ([held], fills and fees), and the first bet placed in it ([first]: what a lock is "for").
 */
data class MarketHolding(val marketId: String, val bets: List<TrackedBet>, val held: Map<String, Held>) {
    val first: TrackedBet get() = bets.filterNot { it.isLock }.minByOrNull { it.createdAtMs } ?: bets.minBy { it.createdAtMs }

    /** Every dollar spent in this market (both sides, fees included). */
    val spent: Double get() = held.values.sumOf { it.spent }

    /** The two outcomes' holdings, the one held more of first; null unless exactly the market's two outcomes are known. */
    fun pair(outcomeIds: List<String>): Pair<Held, Held>? {
        if (outcomeIds.size != 2) return null
        if (held.keys.any { it !in outcomeIds }) return null
        val a = held[outcomeIds[0]] ?: Held(outcomeIds[0], 0, 0.0)
        val b = held[outcomeIds[1]] ?: Held(outcomeIds[1], 0, 0.0)
        return if (a.contracts >= b.contracts) a to b else b to a
    }
}

/** Which markets can be locked, and whether Novig's own positions agree with the Tracker (RESEARCH.md §67). Pure. */
object LockPositions {

    /**
     * Every market with an open API bet whose odds can still be read ([BetsScope.readable]): the bets placed through the API (their real fills), never
     * a ✓ mark (a bet placed in the Novig app: its contracts can't be confirmed, NOVIG_API.md §14.2).
     */
    fun of(bets: List<TrackedBet>, now: Long): List<MarketHolding> = bets
        .filter { it.status == BetStatus.PENDING && it.orderId != null && (it.contracts ?: 0L) > 0L && it.marketId.isNotBlank() && it.outcomeId.isNotBlank() }
        .filter { BetsScope.readable(it, now) }
        .groupBy { it.marketId }
        .map { (marketId, inMarket) ->
            val held = inMarket.groupBy { it.outcomeId }.mapValues { (outcome, bs) -> Held(outcome, bs.sumOf { it.contracts ?: 0L }, bs.sumOf { it.stake }) }
            MarketHolding(marketId, inMarket.sortedBy { it.createdAtMs }, held)
        }

    /**
     * Null when Novig's [positions] in this market hold exactly what the Tracker says on every outcome; else why not (a lock is only worked out from
     * holdings Novig confirms: a fill the Tracker hasn't synced, or one already settled, would make the profit wrong).
     */
    fun mismatch(h: MarketHolding, positions: List<NovigPosition>): String? {
        val novig = positions.filter { it.marketId == h.marketId }.groupBy { it.outcomeId }.mapValues { (_, ps) -> ps.sumOf { it.qty } }
        val outcomes = (novig.keys + h.held.keys).toSet()
        for (o in outcomes) {
            val have = novig[o] ?: 0L
            val think = h.held[o]?.contracts ?: 0L
            if (have != think) {
                return "Novig holds $have contracts on one side where the Tracker has $think: tap Sync with Novig in the Tracker first (no lock without an exact match)."
            }
        }
        return null
    }
}
