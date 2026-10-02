package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel
import java.util.Locale

/** One outcome of a market as the subaccount holds it: its contracts and every dollar spent on them (fills and fees). */
data class Held(val outcomeId: String, val contracts: Long, val spent: Double)

/**
 * A lock ready to place: buy [contracts] of [buyOutcomeId] with a fill-or-kill order at [limitPrice] (Novig's grid). Every profit here is the WORST
 * case: the whole order filled at the limit with the most fee it can be charged. A fill at better prices only adds to it.
 */
data class LockPlan(
    val buyOutcomeId: String,
    val contracts: Long,
    val limitPrice: Double,
    /** Dollars the order can cost at most (price and fee). */
    val worstCost: Double,
    /** Dollars it costs if it fills along the ladder as read (best levels first). */
    val expectedCost: Double,
    /** Profit if the side held wins, and if the other side wins, worst case (equal when the lock is even). */
    val ifHeldWins: Double,
    val ifOtherWins: Double,
    /** Every dollar already spent in this market (both sides, fees included). */
    val spent: Double,
    val feeCharged: Boolean,
) {
    /** The profit whichever side wins, worst case. */
    val guaranteed: Double get() = minOf(ifHeldWins, ifOtherWins)

    /** The profit whichever side wins if it fills as the ladder reads now. */
    val expected: Double get() = guaranteed + (worstCost - expectedCost)
}

sealed interface LockResult {
    data class Ready(val plan: LockPlan) : LockResult

    /** No lock now: [reason] in words; [needPrice]: the other side's price that would make one (null when none would). */
    data class None(val reason: String, val needPrice: Double? = null) : LockResult
}

/**
 * Locking in a profit on a bet already placed (Tj, 2026-10-02 ~18:50Z: "if I place a bet early and it significantly shifts a certain way, I could take
 * the other side of the bet later on and guarantee a profit no matter which side of the bet wins … It must guarantee profit because I will put real
 * money on it"). Pure. RESEARCH.md §67.
 *
 * In one two-outcome Novig market, a contract pays $0.01 whichever outcome it's on: holding as many contracts of each side pays the same whichever wins
 * (and on a fair-market-value void, whose prices sum to 1, the same again). So buying the side held less of, up to the side held more of, makes both
 * outcomes pay the same; it's a lock when that pays more than everything spent in the market, worked out at the order's limit price with the most fee
 * it can be charged (a fill-or-kill order can't fill worse). A push refunds the bets: break-even, less any fee paid, so where a fee is charged a lock
 * isn't offered on a market that can push.
 */
object LockIn {

    /** Dollars one contract pays. */
    private const val PAYOUT = EvMath.CONTRACT_PAYOUT_DOLLARS

    /** Novig's price grid (NOVIG_API.md §7), highest first: 0.001 steps at the ends, 0.005 between 0.055 and 0.945. */
    val GRID_DESC: List<Double> = buildList {
        for (m in 999 downTo 950) add(m / 1000.0)
        for (m in 945 downTo 55 step 5) add(m / 1000.0)
        for (m in 50 downTo 1) add(m / 1000.0)
    }

    /** The fee per $1 of payout a fill at [price] can be charged ([live]: the game is under way, or about to be). */
    fun feeRate(fee: MarketFee, live: Boolean, price: Double): Double =
        if (fee.charged == FeeCharge.ALWAYS || live) fee.coefficient * price * (1.0 - price) else 0.0

    /**
     * The lock for a market where the subaccount holds [a] and [b] (its two outcomes; either may be zero), buying the side held less of from
     * [ladder] (what that side can be bought for now, any order). [minProfit]: the least it must pay whichever side wins, in dollars (more than 0).
     * [live]: fees as if the game were under way. [pushable]: the market can push (a whole-number line, or a tie).
     */
    fun plan(a: Held, b: Held, ladder: List<TakeLevel>, fee: MarketFee?, live: Boolean, pushable: Boolean, minProfit: Double): LockResult {
        val (held, short) = if (a.contracts >= b.contracts) a to b else b to a
        val x = held.contracts - short.contracts
        if (held.contracts <= 0L) return LockResult.None("Nothing is held in this market.")
        if (x <= 0L) return LockResult.None("Already locked: both sides are held equally.")
        if (fee == null) return LockResult.None("Novig's fee for this market couldn't be read, so a lock can't be worked out exactly.")
        val charged = fee.charged == FeeCharge.ALWAYS || live
        if (charged && fee.coefficient > 0.0 && pushable) {
            return LockResult.None("This line can push, and a push would refund the bets but not the fee: no lock while a fee is charged.")
        }
        val minimum = minProfit.coerceAtLeast(MIN_PROFIT)
        val spent = a.spent + b.spent
        val payout = held.contracts * PAYOUT
        // Both outcomes pay held.contracts × $0.01 once the short side is topped up to it: a lock when that beats all spent by the minimum.
        fun worst(q: Double) = x * PAYOUT * (q + feeRate(fee, live, q))
        val ceiling = GRID_DESC.firstOrNull { q -> payout - spent - worst(q) >= minimum - 1e-12 }
            ?: return LockResult.None("No price for the other side would lock a profit: more has been spent than the bet can pay.")
        val levels = ladder.filter { it.contracts > 0L && it.price > 0.0 && it.price < 1.0 }.sortedBy { it.price }
        val best = levels.firstOrNull() ?: return LockResult.None("Nobody is offering the other side on Novig right now.", ceiling)
        if (best.price > ceiling + 1e-9) {
            return LockResult.None(
                "The other side is ${pct(best.price)} on Novig now; a lock needs ${pct(ceiling)} or longer.",
                ceiling,
            )
        }
        // Take the cheapest levels until x contracts; the limit is the deepest level needed (never above the ceiling).
        var left = x
        var expectedCost = 0.0
        var limit = best.price
        for (level in levels) {
            if (left <= 0L) break
            if (level.price > ceiling + 1e-9) break
            val take = minOf(left, level.contracts)
            expectedCost += take * PAYOUT * (level.price + feeRate(fee, live, level.price))
            limit = level.price
            left -= take
        }
        if (left > 0L) {
            return LockResult.None("Novig has only ${x - left} of the $x contracts needed at a locking price (${pct(ceiling)} or longer).", ceiling)
        }
        val worstCost = worst(limit)
        val ifHeld = payout - spent - worstCost
        val ifOther = (short.contracts + x) * PAYOUT - spent - worstCost
        // Belt and braces: never a plan where either outcome could pay less than the minimum.
        if (ifHeld < minimum - 1e-9 || ifOther < minimum - 1e-9) return LockResult.None("The lock wouldn't clear the minimum profit.", ceiling)
        return LockResult.Ready(
            LockPlan(
                buyOutcomeId = short.outcomeId, contracts = x, limitPrice = limit, worstCost = worstCost, expectedCost = expectedCost,
                ifHeldWins = ifHeld, ifOtherWins = ifOther, spent = spent, feeCharged = charged && fee.coefficient > 0.0,
            ),
        )
    }

    /** What holding is worth against a fair chance [fairHeld] of the held side winning (for "lock or let it ride"). */
    fun holdValue(a: Held, b: Held, heldOutcomeId: String, fairHeld: Double): Double {
        val (held, short) = if (a.outcomeId == heldOutcomeId) a to b else b to a
        return fairHeld * held.contracts * PAYOUT + (1.0 - fairHeld) * short.contracts * PAYOUT - (a.spent + b.spent)
    }

    /** Whether a market can push: a whole-number line (spread, total, prop) or a moneyline in a sport that can tie. */
    fun pushable(marketType: String, strike: Double?, league: String): Boolean {
        val type = marketType.uppercase(Locale.US)
        if (type.contains("MONEYLINE")) {
            val l = league.uppercase(Locale.US)
            return !type.contains("3_WAY") && (l.contains("NFL") || l.contains("NCAAF") || l.contains("CFB"))
        }
        val line = strike ?: return true // a line Novig didn't state: assume it can
        return kotlin.math.abs(line - Math.round(line)) < 1e-9
    }

    /** The least a lock is worth placing for: a cent. */
    const val MIN_PROFIT = 0.01

    /** A price as American odds ("+120"), as Tj reads them. */
    private fun pct(p: Double) = com.tjshea.vigilant.engine.Odds.formatAmerican(com.tjshea.vigilant.engine.Odds.probabilityToAmerican(p.coerceIn(0.001, 0.999)))
}
