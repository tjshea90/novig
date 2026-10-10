package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.trading.maker.MakerQuote
import com.tjshea.vigilant.data.pinnodds.PinnBook
import com.tjshea.vigilant.data.pinnodds.PinnLine
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.engine.Devig
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.PriceGrid
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** The reasons a live bid is not posted or is pulled: fixed words, counted in the status and written on the bid. */
object LiveBidSkip {
    const val CLOSED = "Pinnacle line closed"
    const val NOT_LIVE = "game not live on Pinnacle"
    const val NOVIG_PAUSED = "Novig has the game paused"
    const val NO_LINE = "no Pinnacle line prices this market"
    const val NO_BOOK = "no current Novig book"
    const val QUIET = "Pinnacle silent too long"
    const val DANGER = "Pinnacle danger zone"
    const val SCORE_HOLD = "score just changed"
    const val SETTLING = "Pinnacle price not settled"
    const val FAIR_OLD = "Pinnacle price too old"
    const val OVERROUND = "Pinnacle margin too wide"
    const val LIMIT = "Pinnacle limit too low"
    const val LIMIT_UNKNOWN = "Pinnacle limit unknown"
    const val NO_FAIR = "no fair price"
    const val EXTREME = "fair price too extreme"
    const val PRICE_WINDOW = "bid price outside the window"
    const val WOULD_TAKE = "bid would take (Novig is already offering it)"
    const val MISMATCH = "Pinnacle and Novig disagree too much"
    const val LEADS = "bid would lead the book"
    const val EV = "EV too small"
    const val KIND_OFF = "this kind of line is off"
    const val LEAGUE_OFF = "league not picked"
    const val TENNIS_OFF = "tennis is off"
    const val SCORED = "the score changed"
    const val NOVIG_MOVED = "Novig's price moved against the bid"
    const val CLAIM_GONE = "no recent judgement"
}

/**
 * Everything the judge looks at for ONE side of ONE line at one moment, gathered by the runner from Pinnacle's book, the game and Novig's book. [problem] is a feed problem in words (the
 * Pinnodds socket down or quiet, Novig's live feed broken); [fair] is Pinnacle's devigged probability for the side by the rules' own method ([LiveBidFair]).
 */
data class LiveBidView(
    val nowMs: Long,
    val problem: String?,
    val pinnLive: Boolean,
    val novigLive: Boolean,
    val lineOpen: Boolean,
    val fair: Double?,
    val overround: Double,
    val limit: Double?,
    /** Since Pinnacle last said anything about the matchup (any frame). */
    val quietMs: Long,
    /** Since this line's price last changed. */
    val sinceChangeMs: Long,
    /** Since the game's score last changed; null = not seen to change. */
    val scoreAgeMs: Long?,
    /** Since the last Pinnacle danger-zone frame; null = none. */
    val dangerAgeMs: Long?,
    /** Others' best bid on this outcome (ours left out), and the price to buy it (1 - the best bid on the other outcome, ours left out); null = nothing there. */
    val bestBid: Double?,
    val offer: Double?,
    val fee: MarketFee?,
) {
    /** Novig's own middle for this outcome: halfway between the best bid and the offer; null when either is missing. */
    val mid: Double? get() = if (bestBid != null && offer != null) (bestBid + offer) / 2.0 else null
}

sealed interface LiveBidVerdict {
    /** Post a bid at [price]; [ev] is its edge at Pinnacle's fair (no credit), [credit] the maker credit it earns per $1 of price if filled in play. */
    data class Post(val price: Double, val fair: Double, val ev: Double, val credit: Double, val bestBid: Double?, val offer: Double?, val mid: Double?, val leads: Boolean, val overround: Double, val limit: Double?) : LiveBidVerdict

    data class Skip(val reason: String) : LiveBidVerdict
}

/** What the runner knows of a bid already up, for [LiveBidJudge.keep]. */
data class LiveBidHeld(val price: Double, val postedAtMs: Long, val midAtPost: Double?)

sealed interface LiveBidKeep {
    data object Keep : LiveBidKeep
    data class Pull(val reason: String) : LiveBidKeep
}

/**
 * The live bid rules, pure (the same inputs give the same answer). [want] decides whether a NEW bid goes up and at what price; [keep] decides whether one already up may stay. They differ on
 * purpose: a bid is posted only from a settled, quiet, held-off price, but once up it stays through a small move and comes down only for a reason (a score, a danger frame, the fair falling
 * toward its price, a stale feed), so a bid is not pulled and re-posted over nothing.
 */
object LiveBidJudge {

    /** Pinnacle's own danger-zone marker lasts this long; a hold shorter than it cannot be asked for. */
    private const val MIN_DANGER_MS = 3_000L

    fun want(v: LiveBidView, q: LiveBidQuality): LiveBidVerdict {
        common(v, q)?.let { return LiveBidVerdict.Skip(it) }
        if (v.scoreAgeMs != null && q.scoreHoldSec > 0 && v.scoreAgeMs < q.scoreHoldSec * 1000L) return LiveBidVerdict.Skip(LiveBidSkip.SCORE_HOLD)
        if (v.sinceChangeMs < q.settleSec * 1000L) return LiveBidVerdict.Skip(LiveBidSkip.SETTLING)
        val fair = v.fair ?: return LiveBidVerdict.Skip(LiveBidSkip.NO_FAIR)
        val price = PriceGrid.floor(fair / (1.0 + q.margin.coerceAtLeast(0.0))) ?: return LiveBidVerdict.Skip(LiveBidSkip.PRICE_WINDOW)
        if (price < q.minPrice - 1e-9 || price > q.maxPrice + 1e-9) return LiveBidVerdict.Skip(LiveBidSkip.PRICE_WINDOW)
        // Post-only: a bid at or over the price Novig is already offering would take, and is refused whole.
        if (v.offer != null && price >= v.offer - MakerQuote.step(price) + 1e-9) return LiveBidVerdict.Skip(LiveBidSkip.WOULD_TAKE)
        val leads = v.bestBid == null || price > v.bestBid + 1e-9
        if (q.neverLead && leads) return LiveBidVerdict.Skip(LiveBidSkip.LEADS)
        val ev = fair / price - 1.0
        if (ev < q.margin - 1e-9) return LiveBidVerdict.Skip(LiveBidSkip.EV)
        return LiveBidVerdict.Post(price, fair, ev, credit(v.fee, price), v.bestBid, v.offer, v.mid, leads, v.overround, v.limit)
    }

    fun keep(v: LiveBidView, q: LiveBidQuality, held: LiveBidHeld): LiveBidKeep {
        common(v, q)?.let { return LiveBidKeep.Pull(it) }
        // The score changed after this bid was decided on: its price was worked out before the play, and Pinnacle's reprice (a median 2 s later) has not necessarily arrived.
        if (q.pullOnScore && v.scoreAgeMs != null && v.nowMs - v.scoreAgeMs > held.postedAtMs) return LiveBidKeep.Pull(LiveBidSkip.SCORED)
        val fair = v.fair ?: return LiveBidKeep.Pull(LiveBidSkip.NO_FAIR)
        val ev = fair / held.price - 1.0 + (if (q.countCredit) credit(v.fee, held.price) else 0.0)
        if (ev < q.pullBelowEv - 1e-9) return LiveBidKeep.Pull(LiveBidSkip.EV)
        val mid = v.mid
        val midThen = held.midAtPost
        if (q.novigMovePull > 0.0 && midThen != null && mid != null && mid <= midThen - q.novigMovePull + 1e-9) return LiveBidKeep.Pull(LiveBidSkip.NOVIG_MOVED)
        return LiveBidKeep.Keep
    }

    /** The checks a new bid and a resting one both have to pass; the first that fails, in words, else null. */
    private fun common(v: LiveBidView, q: LiveBidQuality): String? {
        v.problem?.let { return it }
        if (!v.lineOpen) return LiveBidSkip.CLOSED
        if (!v.pinnLive) return LiveBidSkip.NOT_LIVE
        if (!v.novigLive) return LiveBidSkip.NOVIG_PAUSED
        if (q.maxQuietSec > 0 && v.quietMs > q.maxQuietSec * 1000L) return LiveBidSkip.QUIET
        if (v.dangerAgeMs != null && v.dangerAgeMs < max(q.dangerHoldSec * 1000L, MIN_DANGER_MS)) return LiveBidSkip.DANGER
        if (q.maxFairAgeSec > 0 && v.sinceChangeMs > q.maxFairAgeSec * 1000L) return LiveBidSkip.FAIR_OLD
        if (v.overround > q.maxOverround) return LiveBidSkip.OVERROUND
        if (q.minPinnLimit > 0.0) {
            val limit = v.limit ?: return LiveBidSkip.LIMIT_UNKNOWN
            if (limit < q.minPinnLimit) return LiveBidSkip.LIMIT
        }
        val fair = v.fair ?: return LiveBidSkip.NO_FAIR
        if (fair < q.minFair || fair > q.maxFair) return LiveBidSkip.EXTREME
        val mid = v.mid
        if (q.maxBookGap > 0.0 && mid != null && abs(fair - mid) > q.maxBookGap) return LiveBidSkip.MISMATCH
        return null
    }

    /** Novig's maker credit per $1 of price for a fill at [price] in play: half the taker's fee `coefficient x P x (1 - P)`, over the price; 0 with no fee data. */
    fun credit(fee: MarketFee?, price: Double): Double =
        if (fee == null || price <= 0.0 || price >= 1.0) 0.0 else fee.makerCredit * fee.coefficient * (1.0 - price)
}

/** Pinnacle's devigged fair for one side of a line by the rules' own method, and the line's margin. Computed from its American prices, so the choice of method is the rules', not the book's. */
object LiveBidFair {
    data class Fair(val fair: Double, val overround: Double)

    fun of(line: PinnLine, side: PinnSide, method: DevigMethod): Fair? {
        val sides = line.american
        if (side !in sides) return null
        val order = PinnSide.entries.filter { it in sides }
        val raw = order.map { PinnBook.impliedProbability(sides.getValue(it)) ?: return null }
        val devigged = runCatching { Devig.devig(raw, method) }.getOrNull() ?: return null
        val fair = order.zip(devigged).toMap()[side] ?: return null
        return Fair(fair, raw.sum() - 1.0)
    }
}

/** Novig's book for one outcome, as a bid sees it: the others' orders only (ours are taken out by price and size), so "does this lead" and "what is the offer" are not about ourselves. */
object LiveBidBookView {
    data class Sides(val bestBid: Double?, val offer: Double?)

    /** [own] are our resting contracts by price in thousandths, per outcome id. */
    fun of(book: NovigBook, market: NovigMarket, outcomeId: String, own: Map<String, Map<Int, Long>>): Sides {
        val other = market.otherOutcome(outcomeId) ?: return Sides(null, null)
        val mine = best(book.bidsByOutcome[outcomeId].orEmpty(), own[outcomeId].orEmpty())
        val theirs = best(book.bidsByOutcome[other.outcomeId].orEmpty(), own[other.outcomeId].orEmpty())
        return Sides(mine?.let { it / 1000.0 }, theirs?.let { (1000 - it) / 1000.0 })
    }

    /** The highest price (thousandths) with contracts left after taking ours out. */
    private fun best(levels: List<BidLevel>, ours: Map<Int, Long>): Int? =
        levels.filter { it.contracts - (ours[it.priceMilli] ?: 0L) > 0L }.maxOfOrNull { it.priceMilli }
}

/** What one live bid stakes. */
object LiveBidStake {

    /**
     * Dollars one bid costs if it fills: a fraction of full Kelly on [bankroll] for this bid's own price and fair (`(fair − price) / (1 − price)`, makers pay no fee), a dollar, or the set amount;
     * raised to [LiveBidLimits.minStake] when Kelly asks for less (and the bankroll is set), and held to [LiveBidLimits.maxStake] and [apiMaxStake] (Settings' per-bet maximum, 0 = none).
     * Null when there is nothing to stake (no bankroll for Kelly, no edge).
     */
    fun dollars(limits: LiveBidLimits, fair: Double, price: Double, bankroll: Double, apiMaxStake: Double): Double? {
        if (price <= 0.0 || price >= 1.0) return null
        val wanted = when (limits.stakeMode) {
            AutoBetStake.ONE_DOLLAR -> 1.0
            AutoBetStake.CUSTOM -> limits.customStake
            else -> {
                val f = limits.stakeMode.kelly ?: return null
                if (!(bankroll > 0.0) || fair <= price) return null
                f * (fair - price) / (1.0 - price) * bankroll
            }
        }
        val raised = max(wanted, limits.minStake)
        val cap = listOf(limits.maxStake, if (apiMaxStake > 0.0) apiMaxStake else Double.MAX_VALUE).min()
        return minOf(raised, cap).takeIf { it > 0.0 }
    }

    /** Whole 1¢ contracts that cost at most [dollars] at [price]. */
    fun contracts(dollars: Double, price: Double): Long = floor(dollars / (price * EvMath.CONTRACT_PAYOUT_DOLLARS) + 1e-9).toLong()
}
