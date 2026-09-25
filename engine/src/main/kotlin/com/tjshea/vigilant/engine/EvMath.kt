package com.tjshea.vigilant.engine

/**
 * One price level you could take right now: buy [contracts] of an outcome at [price] (a
 * probability, cost per $1 payout). On Novig this is `1 − a resting bid on the other outcome`
 * (NOVIG_API.md §7).
 */
data class TakeLevel(val price: Double, val contracts: Long)

/** The EV of buying one outcome at one price. All money is per $1 of payout. */
data class EvQuote(
    val fairProbability: Double,
    val price: Double,
    val fee: Double,
) {
    /** What one $1-payout contract really costs, fee included. */
    val cost: Double get() = price + fee

    /** Expected profit per $1 of payout. */
    val evPerDollarPayout: Double get() = fairProbability - cost

    /** Expected return on money staked. OddsJam's "EV %": `fair / cost − 1`. */
    val evPercent: Double get() = fairProbability / cost - 1.0

    /** Full-Kelly fraction of bankroll for this binary bet: `(p − c) / (1 − c)`, floored at 0. */
    val kellyFraction: Double get() = if (cost >= 1.0) 0.0 else ((fairProbability - cost) / (1.0 - cost)).coerceAtLeast(0.0)

    val fairAmerican: Int get() = Odds.probabilityToAmerican(fairProbability.coerceIn(0.0001, 0.9999))
    val priceAmerican: Int get() = Odds.probabilityToAmerican(cost.coerceIn(0.0001, 0.9999))
}

/** What the whole visible ladder offers while the bet stays +EV. */
data class PositiveDepth(
    val contracts: Long,
    /** Dollars it would cost to take all of it, fees included. A contract pays $0.01. */
    val dollarCost: Double,
    /** Expected profit in dollars if you took all of it. */
    val dollarEv: Double,
    val worstPrice: Double?,
)

object EvMath {

    /** A contract pays 1¢. */
    const val CONTRACT_PAYOUT_DOLLARS = 0.01

    fun quote(fairProbability: Double, price: Double, fee: MarketFee, eventLive: Boolean): EvQuote =
        EvQuote(fairProbability, price, Fees.takerFee(price, fee, eventLive))

    /**
     * Walks the take ladder (best price first) and sums every level whose EV% is still at or
     * above [minEvPercent]. This is how much you could actually bet at a positive edge, not just
     * the top-of-book size.
     */
    fun positiveDepth(
        levels: List<TakeLevel>,
        fairProbability: Double,
        fee: MarketFee,
        eventLive: Boolean,
        minEvPercent: Double = 0.0,
    ): PositiveDepth {
        var contracts = 0L
        var cost = 0.0
        var ev = 0.0
        var worst: Double? = null
        for (level in levels.sortedBy { it.price }) {
            val q = quote(fairProbability, level.price, fee, eventLive)
            if (q.evPercent < minEvPercent) break
            contracts += level.contracts
            cost += level.contracts * q.cost * CONTRACT_PAYOUT_DOLLARS
            ev += level.contracts * q.evPerDollarPayout * CONTRACT_PAYOUT_DOLLARS
            worst = level.price
        }
        return PositiveDepth(contracts, cost, ev, worst)
    }

    /**
     * Suggested stake in dollars: bankroll x kellyMultiplier x full Kelly, capped at what the
     * book can actually fill at a positive edge ([maxFillable]).
     */
    fun suggestedStake(quote: EvQuote, bankroll: Double, kellyMultiplier: Double, maxFillable: Double?): Double {
        val kelly = bankroll * kellyMultiplier * quote.kellyFraction
        return if (maxFillable == null) kelly else minOf(kelly, maxFillable)
    }
}
