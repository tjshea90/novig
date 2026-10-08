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
    /** Off = a bet may rest on a standing disagreement with no recent Pinnacle move. Left on, the lag is required. */
    val requireMove: Boolean = true,
)

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
        if (rules.requireMove && (move == null || move < rules.minMove)) return LiveVerdict.Skip(LiveSkip.NO_MOVE)
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
