package com.tjshea.vigilant.data.novig.trading.maker

/**
 * Spreading bids over many games and kinds of bid (Tj, 2026-10-14: "I only want so many bids/money on each game. Make it diversify bids among many games and bid types"). Pure.
 * [interleave] puts each game's best bid first for every game, then each game's second, and so on; within a game the kinds of market take turns (a spread, then a total, then a prop, then a
 * second spread). The order each game's own bids came in (the caller's priority) is kept, and so is the popular-before-small-market split the planner makes before calling it.
 */
object BidSpread {

    /** [bids] (in priority order) ordered so that every game's first choice comes before any game's second. */
    fun interleave(bids: List<MakerDecision.Post>): List<MakerDecision.Post> {
        if (bids.size < 2) return bids
        val position = HashMap<MakerDecision.Post, Int>(bids.size * 2)
        for ((_, ofGame) in bids.groupBy { it.line.market.eventId }) {
            // The game's kinds in the order each first appears, then round after round: one bid of each kind per round.
            val byKind = ofGame.groupBy { it.line.kind }.values.toList()
            var next = 0
            var round = 0
            while (next < ofGame.size) {
                for (kindBids in byKind) if (round < kindBids.size) position[kindBids[round]] = next++
                round++
            }
        }
        return bids.withIndex().sortedWith(compareBy({ position[it.value] ?: 0 }, { it.index })).map { it.value }
    }

    /** Bids up on each game (by event id). */
    fun bidsPerGame(bids: List<RestingBid>): Map<String, Int> =
        bids.mapNotNull { b -> b.game?.eventId }.groupingBy { it }.eachCount()

    /** Dollars up on each game (by event id). */
    fun dollarsPerGame(bids: List<RestingBid>): Map<String, Double> =
        bids.filter { it.game != null }.groupBy { it.game!!.eventId }.mapValues { (_, v) -> v.sumOf { it.restingDollars } }
}
