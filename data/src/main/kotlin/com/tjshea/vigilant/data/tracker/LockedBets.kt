package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.engine.EvMath

/**
 * One Novig market locked in: both of its sides held equally through the API (a bet and the lock bought against it, [TrackedBet.lockFor]), so it pays the
 * same whichever side wins. [contracts] on each side, [spent] every dollar on both (fills and fees).
 */
data class LockedMarket(val marketId: String, val bets: List<TrackedBet>, val contracts: Long, val spent: Double) {
    /** What it pays whichever side wins, less everything spent on both sides. */
    val lockedProfit: Double get() = contracts * EvMath.CONTRACT_PAYOUT_DOLLARS - spent

    /** Every bet in it graded: its profit is what the results really paid (a push or a void refunds both sides). */
    val settled: Boolean get() = bets.all { it.status != BetStatus.PENDING }

    /** What it made: the results' profit once graded, the locked profit until then. */
    val profit: Double get() = if (settled) bets.sumOf { it.profit ?: 0.0 } else lockedProfit

    /** The picks it locks (not the lock bets bought against them). */
    val picks: List<TrackedBet> get() = bets.filterNot { it.isLock }
}

/**
 * The Tracker's locked-in markets (Tj, 2026-10-02 20:06Z: "Add options to remove arbitraged locked bets out of stats and bet trackers. It makes no sense
 * for me to track a bet that is already cashed out. Maybe a stat tracker for amount and percentage of bets locked in and the total profit and
 * percentage of profit for those bets"). Worked out from the bets alone (their real fills), so it covers settled markets too. Pure.
 */
object LockedBets {

    /**
     * Every market where API bets hold exactly two sides, the same number of contracts on each ([LockedMarket]), by market id. A ✓ mark has no
     * confirmed contracts, so it never counts; a market held unequally is still riding ([partly]).
     */
    fun markets(bets: List<TrackedBet>): Map<String, LockedMarket> = held(bets).filterValues { (sides, _) -> sides.size == 2 && sides.values.distinct().size == 1 }
        .mapValues { (marketId, v) -> LockedMarket(marketId, v.second, v.first.values.first(), v.second.sumOf { it.stake }) }

    /** Markets with both sides held, but not equally: part locked, part still riding on the side held more of. */
    fun partly(bets: List<TrackedBet>): Int = held(bets).count { (_, v) -> v.first.size == 2 && v.first.values.distinct().size == 2 }

    /** API bets with contracts, by market: contracts per side, and the bets. */
    private fun held(bets: List<TrackedBet>): Map<String, Pair<Map<String, Long>, List<TrackedBet>>> = bets
        .filter { it.orderId != null && (it.contracts ?: 0L) > 0L && it.marketId.isNotBlank() && it.outcomeId.isNotBlank() }
        .groupBy { it.marketId }
        .mapValues { (_, inMarket) -> inMarket.groupBy { it.outcomeId }.mapValues { (_, side) -> side.sumOf { it.contracts ?: 0L } } to inMarket }
        .filterValues { (sides, _) -> sides.values.all { it > 0L } }

    /** The ids of every bet in a locked market ([markets]), picks and locks. */
    fun ids(bets: List<TrackedBet>): Set<String> = markets(bets).values.flatMapTo(HashSet()) { m -> m.bets.map { it.id } }

    /** [shown] without the bets of markets locked in, judged over [all] (a lock and the bet it locks can fall in different periods). */
    fun hide(shown: List<TrackedBet>, all: List<TrackedBet> = shown): List<TrackedBet> {
        val locked = ids(all)
        return if (locked.isEmpty()) shown else shown.filterNot { it.id in locked }
    }

    /**
     * The lock numbers for [shown] (a period's bets), judged over [all]: the picks locked in and their share of the picks, the markets they're in, and
     * what those markets made on what was staked in them (both sides).
     */
    fun stats(shown: List<TrackedBet>, all: List<TrackedBet> = shown): LockStats {
        val markets = markets(all)
        val picks = shown.filterNot { it.isLock || it.status == BetStatus.VOID }
        val lockedPicks = picks.filter { it.marketId in markets && it.orderId != null }
        val inShown = lockedPicks.mapTo(LinkedHashSet()) { it.marketId }.map { markets.getValue(it) }
        return LockStats(
            lockedBets = lockedPicks.size,
            bets = picks.size,
            markets = inShown.size,
            staked = inShown.sumOf { it.spent },
            profit = inShown.sumOf { it.profit },
            paid = inShown.filter { it.settled }.sumOf { it.profit },
            hiddenBets = shown.count { b -> markets[b.marketId]?.bets?.any { it.id == b.id } == true },
            partly = partly(all.filter { b -> shown.any { it.marketId == b.marketId } }),
        )
    }
}

/**
 * The Tracker's lock numbers ([LockedBets.stats]): [lockedBets] of [bets] picks locked in, across [markets] markets; [profit] made on [staked] in those
 * markets (both sides, fees included), [paid] of it already graded; [hiddenBets] bets (picks and locks) a "hide locked" leaves out; [partly] markets
 * with both sides held unequally (still riding).
 */
data class LockStats(
    val lockedBets: Int,
    val bets: Int,
    val markets: Int,
    val staked: Double,
    val profit: Double,
    val paid: Double,
    val hiddenBets: Int,
    val partly: Int,
) {
    /** The share of the picks locked in. */
    val share: Double? get() = if (bets > 0) lockedBets.toDouble() / bets else null

    /** Profit over what was staked in the locked markets. */
    val roi: Double? get() = if (staked > 0.0) profit / staked else null

    val any: Boolean get() = markets > 0 || partly > 0
}
