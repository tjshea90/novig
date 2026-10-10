package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel
import kotlin.math.abs

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
    /**
     * An edge this large is not a lag, it is a mismatch (the wrong side, the wrong line, a one-sided book): refused. On the 52-minute tape the real lags were 3-20%, while mismatched quotes read
     * 100-800%. Applied to every trigger.
     */
    val maxEv: Double = 0.40,
    /**
     * Hold-off: no live bet for this long after the game's score changes (0 = off). Novig pauses live betting after a score (Tj, 2026-10-08), and an order sent into the pause finds
     * nothing; the Diagnostics' "Orders by timing" says how long it lasts for this phone, and this is that number.
     */
    val holdoffMs: Long = 0L,
    /** Pregame ("steam"): Pinnacle's prematch fair for the side must have risen by this much within [preMoveWindowMs]. Prematch moves are slow and the books have no score. */
    val preMinMove: Double = 0.02,
    val preMoveWindowMs: Long = 900_000L,
    /** Pregame: Pinnacle's price must have sat still this long. */
    val preSettleMs: Long = 3_000L,
    /** Pregame: no bet in the last [preMinLeadMs] before the start (the book is about to go live and its quotes change) or more than [preMaxLeadMs] ahead (a far-off game's price is not settled). */
    val preMinLeadMs: Long = 300_000L,
    val preMaxLeadMs: Long = 6 * 3_600_000L,
    /** [LiveTrigger.STALE]: how near (probability) Novig's ask must sit to an EARLIER Pinnacle fair for the ask to count as an order left up from before the move. */
    val staleTolerance: Double = 0.015,
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

    /**
     * An order left up from before the move: Pinnacle's fair for the side has risen by the minimum move since [LiveRules]' lookback (the book's whole recent history, about two minutes, not just
     * 20 s), and Novig's ask still sits at the price Pinnacle itself had before it moved. Needs no score, so it also covers sports whose feed carries none (tennis), and an order that stays up
     * after the 20-second window. The ask matching an earlier Pinnacle price is the evidence it is stale rather than just a wide book.
     */
    STALE("Stale orders", "Novig's ask is still at the price Pinnacle had before it moved (a resting order nobody has taken or pulled)");

    /** Triggers with no short arming window: judged on every Novig change and on a regular sweep, not only just after a Pinnacle change. */
    val sweeps: Boolean get() = this == STANDING || this == STALE
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
    const val NOT_STALE = "ask is not an old Pinnacle price"
    const val TOO_GOOD = "edge too large to be real (probable mismatch)"
    const val HOLD_OFF = "held off: the score just changed"
    const val NO_START = "no start time to judge a prematch bet by"
    const val TOO_CLOSE = "game about to start"
    const val TOO_FAR = "game too far off"
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
     * A prematch bet ("steam"): Pinnacle's prematch fair for the side rose by [LiveRules.preMinMove] within [LiveRules.preMoveWindowMs], the game is between [LiveRules.preMinLeadMs] and
     * [LiveRules.preMaxLeadMs] from its start, and Novig's ask is still at a price Pinnacle itself had BEFORE the move (an order left up: the only evidence that separates a stale quote
     * from a wide book). Novig charges no taker fee before the game starts, so [fee] is not applied. Every other guard is the live one.
     */
    private fun pregame(event: PinnEvent, line: PinnLine, side: PinnSide, nowMs: Long, ladder: List<TakeLevel>, fee: MarketFee, rules: LiveRules): LiveVerdict {
        if (event.startMs <= 0L) return LiveVerdict.Skip(LiveSkip.NO_START)
        val lead = event.startMs - nowMs
        if (lead < rules.preMinLeadMs) return LiveVerdict.Skip(LiveSkip.TOO_CLOSE)
        if (lead > rules.preMaxLeadMs) return LiveVerdict.Skip(LiveSkip.TOO_FAR)
        val stable = nowMs - line.changedAtMs
        if (stable < rules.preSettleMs) return LiveVerdict.Skip(LiveSkip.SETTLING)
        if (line.overround > rules.maxOverround) return LiveVerdict.Skip(LiveSkip.OVERROUND)
        val limit = line.maxRisk
        if (limit != null && limit < rules.minPinnLimit) return LiveVerdict.Skip(LiveSkip.LIMIT)
        val fair = line.fair[side] ?: return LiveVerdict.Skip(LiveSkip.NO_FAIR)
        if (fair < rules.minFair || fair > rules.maxFair) return LiveVerdict.Skip(LiveSkip.EXTREME)
        val move = line.moveOver(side, rules.preMoveWindowMs, nowMs)
        if (move == null || move < rules.preMinMove) return LiveVerdict.Skip(LiveSkip.NO_MOVE)
        val levels = ladder.sortedBy { it.price }.filter { it.price > 0.0 && it.price < 1.0 && it.contracts > 0L }
        val best = levels.firstOrNull() ?: return LiveVerdict.Skip(LiveSkip.NO_OFFER)
        if (!staleAsk(line, side, best.price, fair, rules.copy(minMove = rules.preMinMove))) return LiveVerdict.Skip(LiveSkip.NOT_STALE)
        val quote = EvMath.quote(fair, best.price, fee, false)
        if (quote.evPercent < rules.minEv) return LiveVerdict.Skip(LiveSkip.EV)
        if (quote.evPercent > rules.maxEv) return LiveVerdict.Skip(LiveSkip.TOO_GOOD)
        val depth = EvMath.positiveDepth(levels, fair, fee, false, rules.minEv)
        if (depth.contracts < rules.minContracts) return LiveVerdict.Skip(LiveSkip.THIN)
        return LiveVerdict.Bet(
            side = side, fair = fair, ask = best.price, fee = quote.fee, ev = quote.evPercent, move = move, stableMs = stable, overround = line.overround,
            contracts = depth.contracts, limitPrice = reachPrice(fair, depth.worstPrice ?: best.price, fee, event.live && novigLive, rules.minEv),
        )
    }

    /** True when [ask] sits within the tolerance of a Pinnacle fair the line HAD earlier, and the fair has since risen by at least the minimum move: an order left up from before the move. */
    internal fun staleAsk(line: PinnLine, side: PinnSide, ask: Double, fair: Double, rules: LiveRules): Boolean =
        line.history.any { snap -> snap.fair[side]?.let { old -> abs(ask - old) <= rules.staleTolerance && fair - old >= rules.minMove } == true }

    /**
     * Judges buying [side] of [line] on Novig, where [ladder] is the cheapest-first take ladder of the matching Novig outcome (`1 - the opposing bids`), [fee] the market's own schedule and
     * [novigLive] whether Novig charges it now. Pure: the same inputs give the same verdict.
     */
    fun judge(event: PinnEvent, line: PinnLine, side: PinnSide, nowMs: Long, ladder: List<TakeLevel>, fee: MarketFee, novigLive: Boolean, rules: LiveRules): LiveVerdict {
        if (!line.open) return LiveVerdict.Skip(LiveSkip.CLOSED)
        if (!event.live && !rules.pregame) return LiveVerdict.Skip(LiveSkip.PREGAME)
        if (!event.live) return pregame(event, line, side, nowMs, ladder, fee, rules)
        if (rules.holdoffMs > 0 && event.scoreAtMs > 0 && nowMs - event.scoreAtMs < rules.holdoffMs) return LiveVerdict.Skip(LiveSkip.HOLD_OFF)
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
            // Judged against the ask below.
            LiveTrigger.STALE -> {}
        }
        val levels = ladder.sortedBy { it.price }.filter { it.price > 0.0 && it.price < 1.0 && it.contracts > 0L }
        val best = levels.firstOrNull() ?: return LiveVerdict.Skip(LiveSkip.NO_OFFER)
        if (rules.trigger == LiveTrigger.STALE && !staleAsk(line, side, best.price, fair, rules)) return LiveVerdict.Skip(LiveSkip.NOT_STALE)
        val quote = EvMath.quote(fair, best.price, fee, novigLive)
        if (quote.evPercent < rules.minEv) return LiveVerdict.Skip(LiveSkip.EV)
        if (quote.evPercent > rules.maxEv) return LiveVerdict.Skip(LiveSkip.TOO_GOOD)
        val depth = EvMath.positiveDepth(levels, fair, fee, novigLive, rules.minEv)
        if (depth.contracts < rules.minContracts) return LiveVerdict.Skip(LiveSkip.THIN)
        return LiveVerdict.Bet(
            side = side, fair = fair, ask = best.price, fee = quote.fee, ev = quote.evPercent, move = move, stableMs = stable, overround = line.overround,
            contracts = depth.contracts, limitPrice = reachPrice(fair, depth.worstPrice ?: best.price, fee, event.live && novigLive, rules.minEv),
        )
    }
}
