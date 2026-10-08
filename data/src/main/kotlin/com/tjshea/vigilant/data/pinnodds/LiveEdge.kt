package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel

/**
 * What the live engine demands before it calls a Novig price a bet (Tj, 2026-10-08). Every number is a margin against being wrong about Pinnacle's fair price: Pinnacle is the sharpest
 * book, but its devigged price is an ESTIMATE of the true chance, so a bet needs the edge to survive the estimate being a little off, the price being a little old, and Novig's fee.
 */
data class LiveRules(
    /** The least expected return on the money staked, AFTER Novig's in-play taker fee, against Pinnacle's devigged fair. */
    val minEv: Double = 0.03,
    /** The lag test: Pinnacle's fair for this side must have RISEN by at least this (probability, 0.015 = 1.5 points) within [moveWindowMs]. A standing disagreement is not a lag. */
    val minMove: Double = 0.015,
    val moveWindowMs: Long = 20_000L,
    /** Pinnacle's price must have sat unchanged this long (a spike that reverts inside it is not a price). */
    val settleMs: Long = 500L,
    /** Fair prices outside this are left alone: the devig is least reliable in the tails and a nearly decided game has nothing to find. */
    val minFair: Double = 0.08,
    val maxFair: Double = 0.92,
    /** Pinnacle's margin on the line; a wider one means a thin or uncertain market, where the devig guesses more. */
    val maxOverround: Double = 0.09,
    /** Pinnacle's own limit on the line (its `maxRiskStake`, dollars): a market it caps low is one it is unsure of. */
    val minPinnLimit: Double = 100.0,
    val minContracts: Long = 20L,
    /** Also act on prematch lines (Novig charges no fee before the game starts). */
    val pregame: Boolean = false,
    /** What counts as a lag (see [LiveTrigger]). */
    val trigger: LiveTrigger = LiveTrigger.SCORE,
    /** [LiveTrigger.SCORE]: the game's score must have changed within this long (Pinnacle's score frame comes a median 2.1 s before its reprice). */
    val scoreWindowMs: Long = 20_000L,
    /** [LiveTrigger.STANDING]: Pinnacle's price must have sat unchanged this long (a price still in flux is not a standing view). */
    val standingMs: Long = 30_000L,
)

/**
 * What makes a Pinnacle/Novig difference a lag worth betting (RESEARCH.md §116; measured 2026-10-08 on a 9-minute tape): a move with NO score behind it often reverted within two minutes
 * (Pinnacle's own spikes: the same ask was -13.8% against Pinnacle's fair 120 s later, n=10), so the default needs a score.
 */
enum class LiveTrigger(val label: String, val blurb: String) {
    /** Pinnacle's fair for the side rose by the minimum move within the window AND the score changed in the last [LiveRules.scoreWindowMs]: a real event, repriced. */
    SCORE("After a score", "Pinnacle repriced because the score changed, and Novig has not followed"),

    /** Pinnacle's fair for the side rose by the minimum move within the window, whatever caused it (a spike included). */
    MOVE("Any Pinnacle move", "Pinnacle moved toward this side, and Novig has not followed (spikes that revert are included)"),

    /** No move needed: Pinnacle's price has been stable for [LiveRules.standingMs] and Novig's ask is still above it by the minimum edge. */
    STANDING("Any edge", "Novig's ask is off a steady Pinnacle price by the minimum edge, moved or not"),
}

/** The reasons a line is passed over: fixed words, counted in the status. */
object LiveSkip {
    const val CLOSED = "Pinnacle line closed"
    const val VOLATILE = "Pinnacle volatile (danger zone)"
    const val SETTLING = "Pinnacle price not settled"
    const val PREGAME = "game not live"
    const val OVERROUND = "Pinnacle margin too wide"
    const val EXTREME = "price too extreme"
    const val LIMIT = "Pinnacle limit too low"
    const val NO_MOVE = "no recent Pinnacle move toward this side"
    const val NO_SCORE = "no score behind the move"
    const val IN_FLUX = "Pinnacle price still changing"
    const val NO_OFFER = "no offer on Novig"
    const val EV = "EV too small"
    const val THIN = "too thin"
    const val STALE_BOOK = "Novig book not current"
    const val NO_FAIR = "no fair price"
}

sealed interface LiveVerdict {
    /** A bet worth making: [contracts] is what is on offer at or under [limitPrice] while the EV stays at the minimum. */
    data class Bet(
        val side: PinnSide,
        val fair: Double,
        val ask: Double,
        val fee: Double,
        val ev: Double,
        val move: Double?,
        val stableMs: Long,
        val overround: Double,
        val contracts: Long,
        val limitPrice: Double,
    ) : LiveVerdict

    data class Skip(val reason: String) : LiveVerdict
}

object LiveEdge {
    /**
     * Judges buying [side] of [line] on Novig, where [ladder] is the cheapest-first take ladder of the matching Novig outcome (`1 - the opposing bids`), [fee] the market's own schedule and
     * [novigLive] whether Novig charges it now. Pure: the same inputs give the same verdict.
     */
    fun judge(event: PinnEvent, line: PinnLine, side: PinnSide, nowMs: Long, ladder: List<TakeLevel>, fee: MarketFee, novigLive: Boolean, rules: LiveRules): LiveVerdict {
        if (!line.open) return LiveVerdict.Skip(LiveSkip.CLOSED)
        if (!event.live && !rules.pregame) return LiveVerdict.Skip(LiveSkip.PREGAME)
        if (event.live && nowMs < event.volatileUntilMs) return LiveVerdict.Skip(LiveSkip.VOLATILE)
        val stable = nowMs - line.changedAtMs
        if (stable < rules.settleMs) return LiveVerdict.Skip(LiveSkip.SETTLING)
        if (line.overround > rules.maxOverround) return LiveVerdict.Skip(LiveSkip.OVERROUND)
        val limit = line.maxRisk
        if (limit != null && limit < rules.minPinnLimit) return LiveVerdict.Skip(LiveSkip.LIMIT)
        val fair = line.fair[side] ?: return LiveVerdict.Skip(LiveSkip.NO_FAIR)
        if (fair < rules.minFair || fair > rules.maxFair) return LiveVerdict.Skip(LiveSkip.EXTREME)
        val move = line.moveOver(side, rules.moveWindowMs, nowMs)
        when (rules.trigger) {
            LiveTrigger.SCORE -> {
                if (move == null || move < rules.minMove) return LiveVerdict.Skip(LiveSkip.NO_MOVE)
                if (event.scoreAtMs <= 0L || nowMs - event.scoreAtMs > rules.scoreWindowMs) return LiveVerdict.Skip(LiveSkip.NO_SCORE)
            }
            LiveTrigger.MOVE -> if (move == null || move < rules.minMove) return LiveVerdict.Skip(LiveSkip.NO_MOVE)
            LiveTrigger.STANDING -> if (stable < rules.standingMs) return LiveVerdict.Skip(LiveSkip.IN_FLUX)
        }
        val levels = ladder.sortedBy { it.price }.filter { it.price > 0.0 && it.price < 1.0 && it.contracts > 0L }
        val best = levels.firstOrNull() ?: return LiveVerdict.Skip(LiveSkip.NO_OFFER)
        val quote = EvMath.quote(fair, best.price, fee, novigLive)
        if (quote.evPercent < rules.minEv) return LiveVerdict.Skip(LiveSkip.EV)
        val depth = EvMath.positiveDepth(levels, fair, fee, novigLive, rules.minEv)
        if (depth.contracts < rules.minContracts) return LiveVerdict.Skip(LiveSkip.THIN)
        return LiveVerdict.Bet(
            side = side, fair = fair, ask = best.price, fee = quote.fee, ev = quote.evPercent, move = move, stableMs = stable, overround = line.overround,
            contracts = depth.contracts, limitPrice = depth.worstPrice ?: best.price,
        )
    }
}
