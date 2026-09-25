package com.tjshea.vigilant.engine

/** When a market charges its taker (NOVIG_API.md §8). */
enum class FeeCharge { ALWAYS, WHEN_LIVE }

/**
 * A market's own fee schedule, read from its `fee` object on every Novig market. Novig says to
 * read it per market and never derive it from a league table (NOVIG_API.md §8). That's why this
 * is data, not a constant.
 */
data class MarketFee(
    val coefficient: Double,
    val makerCredit: Double,
    val charged: FeeCharge,
) {
    companion object {
        /** Novig's documented game-market schedule, used only by tests and sample data. */
        val GAME = MarketFee(coefficient = 0.03, makerCredit = 0.5, charged = FeeCharge.WHEN_LIVE)

        /** Novig's documented NFL/MLB/NCAAF futures schedule: charged pregame too. */
        val FUTURES = MarketFee(coefficient = 0.06, makerCredit = 0.7, charged = FeeCharge.ALWAYS)
    }
}

/**
 * Novig's taker fee: `coefficient x P x (1 - P)` per contract, in dollars per $1 of payout
 * (a contract pays 1¢; everything here is scaled to a $1 payout). Makers never pay.
 */
object Fees {

    fun isCharged(fee: MarketFee, eventLive: Boolean): Boolean =
        fee.charged == FeeCharge.ALWAYS || eventLive

    /** Taker fee per $1 of payout for a fill at [price]. */
    fun takerFee(price: Double, fee: MarketFee, eventLive: Boolean): Double {
        require(price > 0.0 && price < 1.0) { "price must be in (0,1), got $price" }
        return if (isCharged(fee, eventLive)) fee.coefficient * price * (1.0 - price) else 0.0
    }
}
