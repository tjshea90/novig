package com.tjshea.vigilant.data.tracker

/**
 * True profit on the money actually put in (Tj, 2026-10-13: "I only deposit a set amount of money ... If I win bets, I use the same money from the winnings to bet more. The app should not treat this as
 * another deposit ... the stats should calculate how much total profit and percent of profit I made from the initial 200 dollar investment, not the 1000 dollars of bets"). Pure.
 *
 * The Tracker's old "Profit %" divided profit by every dollar STAKED, so a dollar that won and was bet again counted twice. Here the base is the money that had to come from outside:
 *  - [Result.deposited] is Tj's own figure when he gave one ([ScanSettings.moneyDeposited]); otherwise the least deposit that would have placed every bet in the order they happened with
 *    wins and refunds paid back into the same pot (a replay of the bets: a bet that finds too little cash in the pot adds exactly the shortfall as a deposit).
 *  - [Result.profit] is every settled bet's profit (outliers and locks included: it is the bankroll's real result).
 *  - [Result.percent] = profit / deposited, all time.
 * Money on open bets stays out of the profit (it is not won or lost yet) but is out of the pot while it rides.
 */
object MoneyIn {
    /** A bet settled with no recorded settle time is taken to have paid out this long after its start. */
    const val SETTLE_AFTER_START_MS = 3 * 3_600_000L

    data class Result(
        /** Money that came from outside: given by Tj, or the least the replay needed. */
        val deposited: Double,
        /** True when [deposited] is the replay's estimate, not Tj's own figure. */
        val estimated: Boolean,
        /** Every settled bet's profit. */
        val profit: Double,
        /** Every dollar staked on settled bets, counting each dollar each time it was bet. */
        val turnover: Double,
        /** Cash left in the pot plus money riding on open bets, as of the last bet: [deposited] + [profit] when nothing is lost to rounding. */
        val worthNow: Double,
        val firstBetAtMs: Long?,
    ) {
        /** Profit over the money put in, all time; null before any money went in. */
        val percent: Double? get() = if (deposited > 0) profit / deposited else null

        /** How many times each deposited dollar was bet. */
        val turns: Double? get() = if (deposited > 0) turnover / deposited else null
    }

    fun compute(all: List<TrackedBet>, given: Double? = null): Result {
        val bets = all.filter { it.status != BetStatus.VOID && it.stake > 0 }
        val decided = bets.filter { it.status != BetStatus.PENDING }
        val profit = decided.sumOf { it.profit ?: 0.0 }
        val turnover = decided.sumOf { it.stake }
        // Events in the order they happened: money out when the bet is placed, money back when it settles. At the same instant the money back comes first.
        data class Ev(val t: Long, val out: Double, val back: Double)
        val events = ArrayList<Ev>()
        for (b in bets) {
            events += Ev(b.createdAtMs, b.stake, 0.0)
            if (b.status != BetStatus.PENDING) {
                val at = (b.settledAtMs ?: (b.startsTs + SETTLE_AFTER_START_MS)).coerceAtLeast(b.createdAtMs)
                events += Ev(at, 0.0, b.stake + (b.profit ?: 0.0))
            }
        }
        events.sortWith(compareBy<Ev> { it.t }.thenByDescending { it.back })
        var cash = 0.0
        var needed = 0.0
        for (e in events) {
            if (e.back > 0) cash += e.back
            if (e.out > 0) {
                if (cash < e.out) { needed += e.out - cash; cash = e.out }
                cash -= e.out
            }
        }
        val open = bets.filter { it.status == BetStatus.PENDING }.sumOf { it.stake }
        val deposited = given?.takeIf { it > 0 } ?: needed
        return Result(deposited, given == null || given <= 0, profit, turnover, cash + open, bets.minOfOrNull { it.createdAtMs })
    }
}
