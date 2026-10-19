package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.Opportunity
import kotlinx.serialization.Serializable

/**
 * Cross-exchange hedges, paper only (Tj, 2026-10-19, CH22b, idea #4: "Novig one side, Kalshi/SX the other, when both pay after fees"). On a two-way line, buying one side at Novig's ask and the OTHER side at
 * Kalshi's or SX Bet's ask costs less than the $1 either pays when the two costs plus fees sum under 1. Nothing is ordered here and the engine never has an order path to Kalshi or SX (their keys read only):
 * each pair found is a journal row (`crossx-<day>.jsonl`, archived with the other journals) with every number it was judged on, so its life span and whether it could have been filled are studied later.
 * The exchange's price is its reference quote (decimal odds from [com.tjshea.vigilant.engine.BookPrices]); how fresh and how deep it was is NOT known here, which is why this is a record and not an order.
 */
object CrossExchange {
    val EXCHANGES = mapOf("kalshi" to "Kalshi", "sxbet" to "SX Bet")

    /** A pair must leave at least this much of the $1 after fees to be recorded (a margin for the price's age and rounding). */
    const val MIN_PROFIT = 0.005

    @Serializable
    data class Row(
        val atMs: Long, val eventId: String, val event: String, val market: String, val live: Boolean,
        val novigSide: String, val novigCost: Double, val exchange: String, val otherSide: String, val exchangeCost: Double, val exchangeFee: Double,
        /** 1 - both costs with fees: the profit per $1 contract of each side, whichever side wins. */
        val profit: Double,
        /** Oldest book price behind the line's fair (ms), so the age of the exchange's price can be judged. */
        val fairAsOfMs: Long? = null,
    )

    /** Kalshi's taker fee: 0.07 x p x (1 - p) per contract (rounded up to the cent on its side; not modelled, hence [MIN_PROFIT]). SX Bet's taker fee is taken as 0 and the margin covers any change. */
    fun fee(exchange: String, p: Double): Double = if (exchange == "kalshi") 0.07 * p * (1.0 - p) else 0.0

    fun find(opps: List<Opportunity>, now: Long): List<Row> {
        val out = ArrayList<Row>()
        val byMarket = opps.groupBy { it.market.marketId }
        for ((_, group) in byMarket) {
            if (group.size != 2) continue
            for (o in group) {
                val q = o.quote ?: continue
                val idx = o.referenceIndex ?: continue
                val perBook = o.fair?.perBook ?: continue
                val other = group.first { it !== o }
                for (bf in perBook) {
                    val ex = EXCHANGES[bf.book.bookKey] ?: continue
                    val odds = bf.book.decimalOdds
                    if (odds.size != 2 || idx !in 0..1) continue
                    val d = odds[1 - idx]
                    if (d <= 1.0) continue
                    val p = 1.0 / d
                    val fee = fee(bf.book.bookKey, p)
                    val profit = 1.0 - q.cost - (p + fee)
                    if (profit < MIN_PROFIT) continue
                    out += Row(now, o.market.eventId, o.eventName, o.marketLabel, o.event.isLive, o.selection, q.cost, ex, other.selection, p, fee, profit, o.fairAsOfMs)
                }
            }
        }
        return out.sortedByDescending { it.profit }.take(MAX_PER_SCAN)
    }

    const val MAX_PER_SCAN = 100
}
