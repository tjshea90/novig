package com.tjshea.vigilant.data.tracker

import java.util.Locale

/**
 * The repeat and hedge guard (Tj, 2026-10-12: "When I live auto bet, a lot of the time it bets or bids the same side, player, team, or prop over and over many times repeatedly, because the odds change yet it
 * is still positive EV ... Bets and bids may be placed on both sides of a two sided bet, but only if it guarantees positive profit. It should never lock in a loss").
 *
 * Pure. Callers hand in what is held ([Pos]: open bets, resting bids and orders in flight, each with its dollars and contracts) and ask [check] before a bet or a bid:
 *  - **Same selection** ([Pos.outcomeId] is one side of one line, so a different price on it is the same selection): at most [Rules.maxBets] bets and bids and [Rules.maxDollars] dollars on it in all.
 *  - **The other side of the market**: allowed only while [Rules.hedge] is on AND, with every held position and this one counted (a resting bid as if it filled), every way the market can end pays a profit
 *    after every dollar spent (fees included). A market with an outcome nobody holds (a three-way) can't guarantee one, so it is refused. A push refunds, so it never makes a loss and is not tested.
 * A bet by hand is only warned (the caller decides what to do with the words).
 */
object RepeatGuard {

    /** [maxBets] and [maxDollars] of 0 = no limit on that. */
    data class Rules(val maxBets: Int = DEFAULT_MAX_BETS, val maxDollars: Double = DEFAULT_MAX_DOLLARS, val hedge: Boolean = true)

    /** One position on one outcome: what it cost (fees included) and what it pays if that outcome wins (a contract pays $1). A resting bid counts as if it filled. */
    data class Pos(val marketId: String, val outcomeId: String, val dollars: Double, val contracts: Double, val count: Int = 1)

    /**
     * Why a new bet or bid of [dollars] buying [contracts] on [outcomeId] of [marketId] must not go, in words; null when it may. [outcomes]: every outcome id of the market when known (so a three-way
     * market with a side nobody holds is seen); [held]: everything held, in any market.
     */
    fun check(rules: Rules, held: List<Pos>, marketId: String, outcomeId: String, dollars: Double, contracts: Double, outcomes: Collection<String>? = null): String? {
        if (outcomeId.isBlank()) return null
        val same = held.filter { it.outcomeId == outcomeId }
        val n = same.sumOf { it.count }
        if (rules.maxBets > 0 && n >= rules.maxBets) {
            return "Already $n bet${if (n == 1) "" else "s"} or bid${if (n == 1) "" else "s"} on this same side (limit ${rules.maxBets}): a new price on it is not a new bet."
        }
        val sameDollars = same.sumOf { it.dollars }
        if (rules.maxDollars > 0.0 && same.isNotEmpty() && sameDollars + dollars > rules.maxDollars + EPS) {
            return "${money(sameDollars)} is already on this same side; ${money(dollars)} more would pass your ${money(rules.maxDollars)} limit on one side."
        }
        val others = held.filter { it.marketId == marketId && it.outcomeId != outcomeId && marketId.isNotBlank() }
        if (others.isEmpty()) return null
        if (!rules.hedge) return "The other side of this market is already held, and betting both sides is switched off."
        val inMarket = held.filter { it.marketId == marketId } + Pos(marketId, outcomeId, dollars, contracts)
        val spent = inMarket.sumOf { it.dollars }
        val sides = (outcomes.orEmpty() + inMarket.map { it.outcomeId }).toSet()
        val worst = sides.minOf { side -> inMarket.filter { it.outcomeId == side }.sumOf { it.contracts } - spent }
        if (worst <= MIN_LOCKED_PROFIT) {
            return "Both sides of this market would not be a sure profit (the worst way it ends is ${if (worst < 0) "-" else ""}${money(kotlin.math.abs(worst))} after ${money(spent)} spent): a bet or bid on the other side is held back."
        }
        return null
    }

    /** The open bets of the Tracker as positions (only API bets with contracts; a ✓ mark has none and holds nothing here). */
    fun fromBets(bets: List<TrackedBet>): List<Pos> = bets
        .filter { it.status == BetStatus.PENDING && !it.isLock && it.outcomeId.isNotBlank() }
        .map { Pos(it.marketId, it.outcomeId, it.stake, (it.contracts ?: 0L).toDouble()) }

    /** A lock is a bet on the other side bought on purpose to even a market out; it is judged by its own rules, but it counts as held. */
    fun fromBetsWithLocks(bets: List<TrackedBet>): List<Pos> = bets
        .filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }
        .map { Pos(it.marketId, it.outcomeId, it.stake, (it.contracts ?: 0L).toDouble()) }

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

    private const val EPS = 1e-9

    /** Both sides must beat their cost by at least a cent. */
    const val MIN_LOCKED_PROFIT = 0.01

    const val DEFAULT_MAX_BETS = 2
    const val DEFAULT_MAX_DOLLARS = 100.0
}
