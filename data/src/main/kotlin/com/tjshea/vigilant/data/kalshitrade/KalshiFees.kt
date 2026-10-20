package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import kotlin.math.ceil

/**
 * Kalshi's trading fee (KALSHI_API.md, TRADING): for a fill of [count] contracts at price P (dollars), the fee is
 * `ceil_to_cent(rate x count x P x (1 - P))` with rate 0.07 for a taker; makers pay 0.0175 on the series that charge maker fees
 * (`fee_type quadratic_with_maker_fees`) and nothing elsewhere. Kalshi's own page also carries an accumulator that gives rounding overpayment
 * back across fills ("fee_rounding"), so a plan prices the UNROUNDED per-contract fee ([MarketFee], the same `coefficient x P x (1 - P)` shape as Novig's
 * taker fee: the scan's pricing and the EV maths are shared) and an order about to be sent is checked against the ROUNDED fee ([takerFeeDollars]).
 * Always charged: Kalshi has no free pregame.
 */
object KalshiFees {
    const val TAKER_RATE = 0.07
    const val MAKER_RATE = 0.0175

    /** Kalshi's fee as a [MarketFee] the shared EV maths read: `0.07 x P x (1 - P)` per contract, always charged. */
    val TAKER = MarketFee(coefficient = TAKER_RATE, makerCredit = 0.0, charged = FeeCharge.ALWAYS)

    /** The taker fee in dollars for [count] contracts at [price] (dollars), rounded UP to the cent as the order is billed. */
    fun takerFeeDollars(count: Double, price: Double, multiplier: Double = 1.0): Double = roundUpCent(TAKER_RATE * multiplier * count * price * (1.0 - price))

    /** The maker fee in dollars on a resting order that fills: 0.0175 rate, on series that charge makers; [chargesMakers] false = nothing. */
    fun makerFeeDollars(count: Double, price: Double, chargesMakers: Boolean, multiplier: Double = 1.0): Double =
        if (!chargesMakers) 0.0 else roundUpCent(MAKER_RATE * multiplier * count * price * (1.0 - price))

    /** Unrounded taker fee per contract (dollars) at [price]: what the shared pricing subtracts. */
    fun takerPerContract(price: Double): Double = TAKER_RATE * price * (1.0 - price)

    /** Unrounded maker fee per contract. */
    fun makerPerContract(price: Double, chargesMakers: Boolean): Double = if (chargesMakers) MAKER_RATE * price * (1.0 - price) else 0.0

    internal fun roundUpCent(x: Double): Double = ceil(x * 100.0 - 1e-9) / 100.0
}
