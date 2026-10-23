package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.tracker.GameExposure
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
    /** Placed by the auto-bet, nobody confirming (Tj, 2026-10-01): kept on the bet ([com.tjshea.vigilant.data.tracker.TrackedBet.auto]). */
    val auto: Boolean = false,
    /** The bet as decided ([com.tjshea.vigilant.data.tracker.AtBet]), kept on the bet once it fills. */
    val atBet: com.tjshea.vigilant.data.tracker.AtBet? = null,
    /** A lock's: the bet whose other side it buys ([com.tjshea.vigilant.data.tracker.TrackedBet.lockFor]). */
    val lockFor: String? = null,
    /**
     * Novig has this game in play (the card says LIVE). A game whose start has passed counts as live whatever this says ([ApiBetPlanner.plan]); this catches a game Novig put in play
     * before its listed start. A live bet pays Novig's taker fee and can only be placed by hand (Tj, 2026-10-11: "I want to bid and bet on live games").
     */
    val live: Boolean = false,
    /** Taken from the Feed's real-time live lane (Pinnacle's live fair, at most 5 s old when listed): the sheet re-reads that fair from the lane before pricing and before the order, and refuses once the row is off the list. */
    val liveLane: Boolean = false,
    /**
     * The Novig price the card listed when it was tapped (Tj, 2026-10-11: "if I bet any live game, it only places the bet if it can be filled at the odds listed in the scan"). On a live game
     * the plan refuses when Novig's best price is now above it and never reaches past it: the order is a fill-or-kill at the listed price or better.
     */
    val listedPrice: Double? = null,
    /** A sharp book prices both sides of this bet and says it is NOT +EV at Novig's price (Tj, 2026-10-11): nothing is bet on it, by hand or by the auto-bet. */
    val sharpAgainst: Boolean = false,
)

/**
 * The limits Tj sets in Settings, all in dollars except [minEv], [maxOdds] and [minOdds] (the longest and shortest American odds the order book's best price may be,
 * checked on the book read just before an order: only an auto-bet sets them, 0 = no limit).
 */
data class BetLimits(
    val maxStake: Double,
    val maxPerDay: Double,
    val minEv: Double = 0.0,
    val maxOdds: Int = 0,
    /** The shortest American odds the order book's best price may be, as [maxOdds] (only an auto-bet sets it, 0 = no limit; negative: no favorite shorter than it, positive: underdogs at least that long). */
    val minOdds: Int = 0,
    /** Where [minEv] is set, for a refusal that names it. */
    val minEvWhere: String = "Auto-bet tab › Smallest edge (EV) at Novig's price now",
    /**
     * A bet Tj places by hand from a Bet sheet (Tj, 2026-10-02: "Remove the restriction of minimum bet EV on bet slips in the app, I should be able to
     * bet on whatever I want manually. Only keep the hard restrictions on the auto bet function"): no minimum edge, the stake filled at the best
     * prices Novig offers whatever their EV, and old or unknown fair odds said beside the EV instead of refusing. The per-bet and per-day dollar
     * limits and the price he confirmed still apply. A bet by hand may also go on a live game (Tj, 2026-10-11); the auto-bet never can. Auto-bet never sets it.
     */
    val manual: Boolean = false,
    /**
     * The most that may be at risk on one game across every market of it (open bets and resting bids, [com.tjshea.vigilant.data.tracker.GameExposure]),
     * 0 = no limit (Tj, 2026-10-04). The auto-bet is refused past it; a bet by hand is only warned.
     */
    val maxPerGame: Double = 0.0,
    /** A minimum star rating decides which bets the auto-bet places: the profit profile no longer refuses a bet here (it lowered its stars instead), it is only said beside the plan. */
    val starsDecide: Boolean = false,
) {
    companion object {
        /** A hand-placed bet's limits: only Tj's own dollar limits. */
        fun manual(maxStake: Double, maxPerDay: Double, maxPerGame: Double = 0.0) = BetLimits(maxStake, maxPerDay, minEv = 0.0, manual = true, maxPerGame = maxPerGame)
    }
}

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
    /** The game is in play: the cost above includes Novig's taker fee ([feeDollars]) and the order takes about 5 seconds to land (NOVIG_API.md §22). */
    val live: Boolean = false,
    /** The taker fee inside [expectedCost] (0 pregame). */
    val feeDollars: Double = 0.0,
) {
    val profitIfWon: Double get() = payout - expectedCost
    val american: Int get() = Odds.probabilityToAmerican(averagePrice.coerceIn(0.001, 0.999))
}

sealed interface PlanResult {
    data class Ready(val plan: BetPlan) : PlanResult

    /** Nothing is sent: [reason] says why, in words for the card. [gameLimit]: it's the per-game limit ([BetLimits.maxPerGame]), which the auto-bet counts under one reason. */
    data class Refused(val reason: String, val gameLimit: Boolean = false, /** The bet is not +EV at Novig's price now: the lists drop it (Tj, 2026-10-14). */ val negativeEv: Boolean = false, /** The listed price is not on offer any more (it moved, or nobody sells): the lists drop the row until it is priced again (Tj, 2026-10-22). */ val gone: Boolean = false) : PlanResult
}

/**
 * The checks and the arithmetic before any order (pure; the placer reads the book and the day's total and hands them in). Every rule here
 * is a refusal, never a silent change: pregame only for the auto-bet (a bet by hand may go on a live game, with Novig's taker fee in its price); the
 * edge must still be positive at Novig's price now; the fair odds young enough to bet on ([Freshness]); a book read within [MAX_BOOK_AGE_MS]; the
 * per-bet and per-day caps.
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
        /** What this bet does to its game's exposure ([com.tjshea.vigilant.data.tracker.GameExposure.check]); null = not asked (no limit set). */
        game: GameExposure.Check? = null,
        /** Settings › Profit-proven only: what Tj's own bet history says about this TYPE of bet. The auto-bet is refused a type that has lost money; a bet by hand only gets a note. Null = off. */
        profit: com.tjshea.vigilant.data.scanner.ProfitProfile? = null,
    ): PlanResult {
        fun no(reason: String, gone: Boolean = false) = PlanResult.Refused(reason, gone = gone)
        if (!(stake > 0.0)) return no("Pick an amount to bet.")
        if (stake > limits.maxStake + 1e-9) return no("That's over your ${money(limits.maxStake)} limit per bet (Settings › Betting & Novig account).")
        if (spentToday + stake > limits.maxPerDay + 1e-9) {
            return no("That would take today's API bets to ${money(spentToday + stake)}, over your ${money(limits.maxPerDay)} daily limit (${money(spentToday)} so far).")
        }
        // One game is one event: this bet and the others on it win and lose together. The auto-bet never takes a game past its limit; by hand it's said
        // beside the bet (Tj, 2026-10-02: he decides what he bets by hand).
        val gameNote = game?.takeIf { it.blocked }?.let {
            if (!limits.manual) return PlanResult.Refused(it.words(), gameLimit = true)
            it.words() + " You can still place it."
        }
        // The auto-bet is pregame only. By hand a live game may be bet (Tj, 2026-10-11: "I want to bid and bet on live games"; Novig's API takes in-play orders, NOVIG_API.md §18): its taker fee is
        // priced into every number below, and the sheet says what in play means (a stale fair, ~5 seconds for the order to land).
        // Novig's live feed knows a market's state as it changes (the catalog's is minutes old): CLOSED is no bet at all, and GOLIVE (Novig can put a game in play before its listed start) makes it a live
        // bet with the taker fee on, whatever the clock says (NOVIG_API.md §27).
        val life = book?.lifecycle
        val wentLive = life?.live == true
        val live = target.live || target.startsTs <= now || wentLive
        if (life != null && life.closed) return no("Novig has closed this market (its live feed says ${life.status?.lowercase() ?: "closed"}).", gone = true)
        if (live && !limits.manual) {
            return no(
                if (wentLive && !target.live && target.startsTs > now) "Novig has put this game in play (its live feed says so): the auto-bet is pregame only (a live game can only be bet by hand, with the Bet button)."
                else "This game is live: the auto-bet is pregame only (a live game can only be bet by hand, with the Bet button).",
            )
        }
        if (target.market.status != "OPEN") return no("Novig has closed this market.", gone = true)
        val fee = target.market.fee ?: return no("Novig's fee for this market couldn't be read, so its cost can't be worked out.")
        // Money is at stake: an unknown age isn't taken as fresh (the app's lists do, to show a bet; a bet placed needs the age). By hand, Tj
        // decides: the age is said beside the EV.
        val fairNote = when {
            live -> liveNote(target, now)
            limits.manual && target.fairAsOfMs == null -> "How old the fair odds behind this EV are isn't known."
            limits.manual && !Freshness.fresh(target.fairAsOfMs, now, target.startsTs) ->
                "The fair odds behind this EV are ${(now - target.fairAsOfMs!!) / 60_000L} minutes old: the EV may have moved since."
            else -> null
        }
        if (!limits.manual) {
            if (target.fairAsOfMs == null) return no("How old the fair odds behind this bet are isn't known: scan again first.")
            if (!Freshness.fresh(target.fairAsOfMs, now, target.startsTs)) {
                val minutes = target.fairAsOfMs.let { (now - it) / 60_000L }
                return no("The fair odds behind this bet are $minutes minutes old (${Freshness.LIMIT_TEXT}): scan again first.")
            }
        }
        if (book == null) return no("Novig's order book couldn't be read just now: try again in a moment.")
        if (now - book.fetchedAtMs > MAX_BOOK_AGE_MS) return no("Novig's price is ${(now - book.fetchedAtMs) / 1000} seconds old: try again in a moment.")
        val levels = book.takeLadder(target.market, target.outcomeId).sortedBy { it.price }
        val best = levels.firstOrNull() ?: return no("Nobody is offering this side on Novig right now.", gone = true)
        if (AutoBet.tooLong(limits.maxOdds, Odds.probabilityToAmerican(best.price.coerceIn(0.001, 0.999)))) {
            return no("Novig's best price is now ${american(best.price)}, longer than your ${Odds.formatAmerican(limits.maxOdds)} limit.")
        }
        if (AutoBet.tooShort(limits.minOdds, Odds.probabilityToAmerican(best.price.coerceIn(0.001, 0.999)))) {
            return no("Novig's best price is now ${american(best.price)}, shorter than your ${Odds.formatAmerican(limits.minOdds)} limit.")
        }
        if (target.sharpAgainst) {
            return no("A sharp book prices both sides of this bet and says it is not +EV at Novig's price, so nothing is bet on it.", gone = true)
        }
        val listed = target.listedPrice.takeIf { live }
        if (listed != null && best.price > listed + 1e-9) {
            return no("Novig's price moved: it was listed at ${american(listed)} and the best now is ${american(best.price)}. Nothing is bet unless it can fill at the listed odds: look at the list again.", gone = true)
        }
        val bestQuote = EvMath.quote(target.fair, best.price, fee, eventLive = live)
        // No money ever goes on a negative-EV bet (Tj, 2026-10-11: "If a bet ever becomes negative EV anywhere in the app on any scanner, prevent me from betting it at all ... No money should ever
        // go on a negative ev bet"): by hand too. This supersedes the 2026-10-02 "no minimum edge by hand" for a NEGATIVE edge only: a hand bet still needs no minimum, but not less than zero.
        if (limits.manual && bestQuote.evPercent < -1e-9) {
            return PlanResult.Refused(
                "This bet is negative EV now: Novig's best price is ${american(best.price)} against fair odds of ${american(target.fair)}, " +
                    "${percent(bestQuote.evPercent)} EV${if (live) " with Novig's live fee" else ""}. Nothing is bet on a negative-EV price.",
                negativeEv = true,
            )
        }
        if (!limits.manual && bestQuote.evPercent < limits.minEv - 1e-9) {
            // Still +EV, only under Tj's own minimum: say that, and where it's set (Tj, 2026-10-02: "Why is this saying the edge is gone? It's
            // the same odds, and they are positive ev": +2.4% under his +3% read as "The edge is gone").
            return PlanResult.Refused(
                if (bestQuote.evPercent > 0.0) {
                    "${percent(bestQuote.evPercent)} EV at Novig's best price now (${american(best.price)}, fair ${american(target.fair)}) is under your " +
                        "${percent(limits.minEv)} minimum (${limits.minEvWhere}). Lower it there to bet this one."
                } else {
                    "The edge is gone: Novig's best price is now ${american(best.price)}, and the fair odds ${american(target.fair)} make that " +
                        "${percent(bestQuote.evPercent)} EV."
                },
                negativeEv = bestQuote.evPercent <= 0.0,
            )
        }

        // Walk the ladder cheapest first, spending the stake while each level keeps the edge at or above the minimum (by hand: every level, as
        // Novig's own bet slip would).
        var dollarsLeft = stake
        var contracts = 0L
        var cost = 0.0
        var feeTotal = 0.0
        var limit = best.price
        for (level in levels) {
            // Live: never reach past the listed price (the order is placed to fill at those odds or better, or not at all).
            if (listed != null && level.price > listed + 1e-9) break
            val q = EvMath.quote(target.fair, level.price, fee, eventLive = live)
            // Never past a level that is negative EV, by hand included.
            if (q.evPercent < -1e-9) break
            if (!limits.manual && q.evPercent < limits.minEv - 1e-9) break
            val perContract = q.cost * EvMath.CONTRACT_PAYOUT_DOLLARS
            val take = minOf(level.contracts, Math.floor(dollarsLeft / perContract + 1e-9).toLong())
            if (take <= 0L) break
            contracts += take
            cost += take * perContract
            feeTotal += take * q.fee * EvMath.CONTRACT_PAYOUT_DOLLARS
            dollarsLeft -= take * perContract
            limit = level.price
            if (dollarsLeft < perContract) break
        }
        if (contracts <= 0L) return no("${money(stake)} is less than one contract at Novig's price (${american(best.price)}): bet more.")
        val average = averagePrice(levels, contracts)
        val avgQuote = EvMath.quote(target.fair, average, fee, eventLive = live)
        var profitNote: String? = null
        profit?.let { pf ->
            val j = pf.judge(com.tjshea.vigilant.data.scanner.ProfitProfile.Instrument.BET, target.league, BetKind.of(target.marketLabel, target.selection).label, target.selection, bestQuote.evPercent, (target.startsTs - now) / 60_000L)
            j.blocked?.let { why -> if (!limits.manual && !limits.starsDecide) return no("Not bet: $why.") else profitNote = "Heads up: $why." }
        }
        val shortNote = if (stake - cost > stake * 0.05 + 0.01) {
            "Only ${money(cost)} of the ${money(stake)} is offered " + when {
                limits.manual -> "on Novig's book"
                limits.minEv > 1e-9 -> "at your ${percent(limits.minEv)} minimum edge or better"
                else -> "at a positive edge"
            } + " right now: this bets ${money(cost)}."
        } else {
            null
        }
        val note = listOfNotNull(shortNote, fairNote, gameNote, profitNote).joinToString(" ").ifEmpty { null }
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
                live = live,
                feeDollars = feeTotal,
            ),
        )
    }

    /** What a live bet's sheet says beside the numbers: how old the fair is (in play a line moves in seconds) and what in play costs and risks. */
    internal fun liveNote(target: BetTarget, now: Long): String {
        val age = target.fairAsOfMs?.let { (now - it).coerceAtLeast(0L) / 1000L }
        val fairAge = when {
            age == null -> "How old the fair odds behind this EV are isn't known"
            age < 120 -> "The fair odds behind this EV are $age seconds old"
            else -> "The fair odds behind this EV are ${age / 60} minutes old"
        }
        return "LIVE: $fairAge, and in play a line can move a lot in seconds (a score, an injury), so the edge may already be gone. " +
            "Novig takes about 5 seconds to answer an order in play: if the price moves against you first, nothing is bought."
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
