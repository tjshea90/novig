package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Fees
import com.tjshea.vigilant.engine.Odds
import java.util.Locale

/**
 * A bet ready to place through Novig's API (Tj, 2026-09-29: "Build the betting through the API function"): which Novig outcome, what it's
 * called in the Tracker, and the fair probability its EV rests on (Vigilant's scan or CrazyNinjaOdds' list), with when that fair price was read.
 */
data class BetTarget(
    val market: NovigMarket,
    val outcomeId: String,
    val league: String,
    val eventName: String,
    val startsTs: Long,
    val marketLabel: String,
    val selection: String,
    val fair: Double,
    /** When the fair price behind [fair] was last seen (null = unknown: judged by the strict limit). */
    val fairAsOfMs: Long?,
    val source: String,
    /** The list key the ✓ placed marks hide the bet by (an Undo never removes a bet placed through the API). */
    val placedKey: String? = null,
    val book: String = "Novig",
    val gameUrl: String? = null,
    val betUrl: String? = null,
    /** How [fair] was made, kept on the bet when it's placed ([com.tjshea.vigilant.data.tracker.FairBasis]). */
    val basis: com.tjshea.vigilant.data.tracker.FairBasis? = null,
)

/** The limits Tj sets in Settings, all in dollars except [minEv]. */
data class BetLimits(val maxStake: Double, val maxPerDay: Double, val minEv: Double = 0.0)

/** What a bet would do, worked out from a book read just now. */
data class BetPlan(
    /** The most to pay for a contract, on Novig's grid: the price of the deepest level the stake reaches. */
    val limitPrice: Double,
    val contracts: Long,
    /** Dollars if the visible book fills all of it (fees included). */
    val expectedCost: Double,
    val averagePrice: Double,
    /** Dollars a win pays (a contract pays 1¢). */
    val payout: Double,
    /** Expected return at the average price, against the fair probability. */
    val evPercent: Double,
    val bestPrice: Double,
    /** Set when the book can't fill the whole stake at a positive edge. */
    val note: String?,
) {
    val profitIfWon: Double get() = payout - expectedCost
    val american: Int get() = Odds.probabilityToAmerican(averagePrice.coerceIn(0.001, 0.999))
}

sealed interface PlanResult {
    data class Ready(val plan: BetPlan) : PlanResult

    /** Nothing is sent: [reason] says why, in words for the card. */
    data class Refused(val reason: String) : PlanResult
}

/**
 * The checks and the arithmetic before any order (pure; the placer reads the book and the day's total and hands them in). Every rule here
 * is a refusal, never a silent change: pregame only; the edge must still be positive at Novig's price now; the fair odds young enough to
 * bet on ([Freshness]); a book read within [MAX_BOOK_AGE_MS]; the per-bet and per-day caps.
 */
object ApiBetPlanner {

    /** A book older than this isn't the price now. */
    const val MAX_BOOK_AGE_MS = 15_000L

    fun plan(
        target: BetTarget,
        book: NovigBook?,
        stake: Double,
        now: Long,
        limits: BetLimits,
        spentToday: Double,
    ): PlanResult {
        fun no(reason: String) = PlanResult.Refused(reason)
        if (!(stake > 0.0)) return no("Pick an amount to bet.")
        if (stake > limits.maxStake + 1e-9) return no("That's over your ${money(limits.maxStake)} limit per bet (Settings › Novig API › Betting).")
        if (spentToday + stake > limits.maxPerDay + 1e-9) {
            return no("That would take today's API bets to ${money(spentToday + stake)}, over your ${money(limits.maxPerDay)} daily limit (${money(spentToday)} so far).")
        }
        // Pregame only: the taker fee that applies once a game is live isn't priced into a one-tap bet.
        if (target.startsTs <= now) return no("This game has started: bets through the API are pregame only.")
        if (target.market.status != "OPEN") return no("Novig has closed this market.")
        val fee = target.market.fee ?: return no("Novig's fee for this market couldn't be read, so its cost can't be worked out.")
        // Money is at stake: an unknown age isn't taken as fresh (the app's lists do, to show a bet; a bet placed needs the age).
        if (target.fairAsOfMs == null) return no("How old the fair odds behind this bet are isn't known: scan again first.")
        if (!Freshness.fresh(target.fairAsOfMs, now, target.startsTs)) {
            val minutes = target.fairAsOfMs?.let { (now - it) / 60_000L }
            return no("The fair odds behind this bet are ${minutes?.let { "$it minutes" } ?: "too"} old (${Freshness.LIMIT_TEXT}): scan again first.")
        }
        if (book == null) return no("Novig's order book couldn't be read just now: try again in a moment.")
        if (now - book.fetchedAtMs > MAX_BOOK_AGE_MS) return no("Novig's price is ${(now - book.fetchedAtMs) / 1000} seconds old: try again in a moment.")
        val levels = book.takeLadder(target.market, target.outcomeId).sortedBy { it.price }
        val best = levels.firstOrNull() ?: return no("Nobody is offering this side on Novig right now.")
        val bestQuote = EvMath.quote(target.fair, best.price, fee, eventLive = false)
        if (bestQuote.evPercent < limits.minEv - 1e-9) {
            return no(
                "The edge is gone: Novig's best price is now ${american(best.price)}, and the fair odds ${american(target.fair)} make that " +
                    "${percent(bestQuote.evPercent)} EV.",
            )
        }

        // Walk the ladder cheapest first, spending the stake while each level keeps the edge at or above the minimum.
        var dollarsLeft = stake
        var contracts = 0L
        var cost = 0.0
        var limit = best.price
        for (level in levels) {
            val q = EvMath.quote(target.fair, level.price, fee, eventLive = false)
            if (q.evPercent < limits.minEv - 1e-9) break
            val perContract = q.cost * EvMath.CONTRACT_PAYOUT_DOLLARS
            val take = minOf(level.contracts, Math.floor(dollarsLeft / perContract + 1e-9).toLong())
            if (take <= 0L) break
            contracts += take
            cost += take * perContract
            dollarsLeft -= take * perContract
            limit = level.price
            if (dollarsLeft < perContract) break
        }
        if (contracts <= 0L) return no("${money(stake)} is less than one contract at Novig's price (${american(best.price)}): bet more.")
        val average = averagePrice(levels, contracts)
        val avgQuote = EvMath.quote(target.fair, average, fee, eventLive = false)
        val note = if (stake - cost > stake * 0.05 + 0.01) {
            "Only ${money(cost)} of the ${money(stake)} is offered at a positive edge right now: this bets ${money(cost)}."
        } else {
            null
        }
        return PlanResult.Ready(
            BetPlan(
                limitPrice = limit,
                contracts = contracts,
                expectedCost = cost,
                averagePrice = average,
                payout = contracts * EvMath.CONTRACT_PAYOUT_DOLLARS,
                evPercent = avgQuote.evPercent,
                bestPrice = best.price,
                note = note,
            ),
        )
    }

    /** The average price paid when [contracts] are taken cheapest first from [levels]. */
    private fun averagePrice(levels: List<com.tjshea.vigilant.engine.TakeLevel>, contracts: Long): Double {
        var left = contracts
        var paid = 0.0
        for (l in levels) {
            val n = minOf(left, l.contracts)
            paid += n * l.price
            left -= n
            if (left <= 0) break
        }
        return paid / contracts
    }

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)
    private fun percent(v: Double) = String.format(Locale.US, "%+.1f%%", v * 100)
    private fun american(p: Double): String = Odds.probabilityToAmerican(p.coerceIn(0.001, 0.999)).let { if (it > 0) "+$it" else "$it" }
}
