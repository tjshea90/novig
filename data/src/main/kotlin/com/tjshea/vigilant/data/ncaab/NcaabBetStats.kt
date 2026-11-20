package com.tjshea.vigilant.data.ncaab

import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.data.tracker.SlateProfit
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.tracker.TrackerBreakdown
import java.time.LocalDate

/**
 * The record of the bets placed by hand from the NCAAB tab (NT1, Tj 2026-11-20, verbatim: "tracks bets i hand bet on the ncaab tab and tracks relevant information such as amount of bets, total money bet all
 * time and daily, total profit and loss all time and daily, total wins and losses, percent of bets won, etc. ... Make sure all of the information is for the ncaab tab bets").
 *
 *  - **Which bets:** a bet placed (or ✓ logged) from the NCAAB tab, by hand: [isNcaabTab] ([TrackedBet.origin], or the NCAAB fair basis a tab bet carried before the origin was kept), never an automatic bet
 *    or a bid, never a voided one (a refund is no bet). Nothing from the other tabs is in any number here.
 *  - **Money bet** is what the bets cost (stake plus fee), open or settled; **profit** is what the SETTLED bets paid back minus what they cost ([TrackedBet.profit]: Novig's own money when the bet has it);
 *    an open bet is money at stake, never a loss. Every bet counts, whatever its EV when placed (the Tracker's outlier rule does not apply: this is a record of what was bet, not a test of an edge).
 *  - **Days** are the Tracker's: the day of the GAME, Eastern, 5 am to 5 am ([SlateProfit.slateOf]); "today" is today's games.
 *  - **Percent won** is wins over wins plus losses (a push is neither); **ROI** is profit over the money on the settled bets.
 *
 * Pure: the same bets give the same numbers, for tests.
 */
object NcaabBetStats {
    /** A bet that was placed or logged from the NCAAB tab. */
    fun isNcaabTab(b: TrackedBet): Boolean = b.origin == BetTracker.ORIGIN_NCAAB || b.fairBasis?.source == FairBasis.SOURCE_NCAAB

    /** The bets this record counts. */
    fun mine(all: List<TrackedBet>): List<TrackedBet> = all.filter { isNcaabTab(it) && !it.auto && !it.isBid && it.status != BetStatus.VOID }

    enum class Result { WON, LOST, PUSHED, OPEN }

    /** A settled bet's result; a bet settled at a fair market value counts as won or lost by its profit. */
    fun resultOf(b: TrackedBet): Result = when (b.status) {
        BetStatus.PENDING -> Result.OPEN
        BetStatus.WON -> Result.WON
        BetStatus.LOST -> Result.LOST
        BetStatus.PUSH, BetStatus.VOID -> Result.PUSHED
        BetStatus.FMV -> (b.profit ?: 0.0).let { if (it > 0.005) Result.WON else if (it < -0.005) Result.LOST else Result.PUSHED }
    }

    data class Totals(
        val bets: Int = 0,
        val won: Int = 0,
        val lost: Int = 0,
        val pushed: Int = 0,
        val open: Int = 0,
        /** Money on every bet, settled or not. */
        val staked: Double = 0.0,
        /** Money on the settled bets only. */
        val settledStaked: Double = 0.0,
        /** What the settled bets paid back minus what they cost. */
        val profit: Double = 0.0,
        val biggestWin: Double? = null,
        val biggestLoss: Double? = null,
    ) {
        val settled: Int get() = won + lost + pushed
        /** Money on the bets that have not settled: at stake, not lost. */
        val openStaked: Double get() = (staked - settledStaked).coerceAtLeast(0.0)
        /** What the settled bets paid back (their cost plus their profit). */
        val returned: Double get() = settledStaked + profit
        /** Wins over wins plus losses; null before one is decided. */
        val winPct: Double? get() = if (won + lost > 0) won.toDouble() / (won + lost) else null
        /** Profit over the money on the settled bets; null before one settled. */
        val roi: Double? get() = if (settledStaked > 0.0) profit / settledStaked else null
        val avgStake: Double? get() = if (bets > 0) staked / bets else null
        /** "3-2" or "3-2-1" with pushes. */
        val record: String get() = "$won-$lost" + if (pushed > 0) "-$pushed" else ""
    }

    data class DayRow(val date: LocalDate, val totals: Totals)
    data class MarketRow(val market: String, val totals: Totals)

    data class Summary(
        val all: Totals,
        /** Today's games (the Tracker's "Today"): zeros until a bet is on one. */
        val today: Totals,
        /** Every game day with a bet, newest first. */
        val days: List<DayRow>,
        /** By kind of market (Spread, Total, Moneyline, Team total...), the most bets first. */
        val markets: List<MarketRow>,
        val best: TrackedBet?,
        val worst: TrackedBet?,
        /** First game day with a bet, for "since". */
        val since: LocalDate?,
        /** Bets of this tab that were voided (refunded): in no number above. */
        val voided: Int,
    ) {
        val isEmpty: Boolean get() = all.bets == 0
    }

    fun totalsOf(bets: List<TrackedBet>): Totals {
        var won = 0; var lost = 0; var pushed = 0; var open = 0
        var staked = 0.0; var settledStaked = 0.0; var profit = 0.0
        var bigWin: Double? = null; var bigLoss: Double? = null
        for (b in bets) {
            staked += b.stake
            when (resultOf(b)) {
                Result.OPEN -> open++
                else -> {
                    val p = b.profit ?: 0.0
                    settledStaked += b.stake; profit += p
                    when (resultOf(b)) { Result.WON -> won++; Result.LOST -> lost++; else -> pushed++ }
                    if (p > 0.0 && (bigWin == null || p > bigWin)) bigWin = p
                    if (p < 0.0 && (bigLoss == null || p < bigLoss)) bigLoss = p
                }
            }
        }
        return Totals(bets.size, won, lost, pushed, open, staked, settledStaked, profit, bigWin, bigLoss)
    }

    fun summarize(all: List<TrackedBet>, nowMs: Long): Summary {
        val bets = mine(all)
        val today = SlateProfit.today(nowMs)
        val days = bets.groupBy(SlateProfit::slateOf).toSortedMap(compareByDescending { it }).map { (d, list) -> DayRow(d, totalsOf(list)) }
        val markets = bets.groupBy(TrackerBreakdown::marketOf).map { (m, list) -> MarketRow(m, totalsOf(list)) }.sortedWith(compareByDescending<MarketRow> { it.totals.bets }.thenBy { it.market })
        val settled = bets.filter { resultOf(it) != Result.OPEN }
        return Summary(
            all = totalsOf(bets),
            today = totalsOf(bets.filter { SlateProfit.slateOf(it) == today }),
            days = days, markets = markets,
            best = settled.filter { (it.profit ?: 0.0) > 0.0 }.maxByOrNull { it.profit ?: 0.0 },
            worst = settled.filter { (it.profit ?: 0.0) < 0.0 }.minByOrNull { it.profit ?: 0.0 },
            since = days.lastOrNull()?.date,
            voided = all.count { isNcaabTab(it) && !it.auto && !it.isBid && it.status == BetStatus.VOID },
        )
    }
}
