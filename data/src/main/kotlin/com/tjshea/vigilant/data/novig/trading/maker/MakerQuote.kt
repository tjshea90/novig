package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.PriceGrid
import java.util.Locale
import kotlin.math.floor

/**
 * How bids are posted (Tj, 2026-10-03: "figure out the optimal bets and math for make bets with the highest chance of beating clv and profiting, build
 * the system in the app"; RESEARCH.md §70), from [ScanSettings]: [margin] under the fair, [stake] a bid, at most [maxBids] and [maxDollars] resting,
 * on [kinds], each resting [ttlMs] at most, none within [stopMs] of the start, bid prices in [minPrice]..[maxPrice].
 */
data class MakerRules(
    val margin: Double,
    val stake: Double,
    val maxBids: Int,
    val maxDollars: Double,
    val kinds: Set<BetKind>,
    val ttlMs: Long,
    val stopMs: Long,
    val minPrice: Double,
    val maxPrice: Double,
    val bothSides: Boolean,
    val minBooks: Int,
    /** A resting bid is moved up only when the bid wanted is at least this many grid steps higher (moving loses its place in the queue). */
    val requoteSteps: Int = 2,
    /** A bid this close to expiring is re-posted now (so a bid that's still good is always up). */
    val refreshBeforeMs: Long = 2 * 60_000L,
) {
    companion object {
        fun of(s: ScanSettings) = MakerRules(
            margin = s.makerMargin.coerceIn(0.005, 0.5),
            // A filled bid is a bet: never more than the per-bet limit.
            stake = minOf(s.makerStake, s.apiMaxStake).coerceAtLeast(0.01),
            maxBids = s.makerMaxBids.coerceAtLeast(0),
            maxDollars = s.makerMaxDollars.coerceAtLeast(0.0),
            kinds = s.makerKinds,
            ttlMs = s.makerTtlMinutes.coerceIn(1, 24 * 60) * 60_000L,
            stopMs = s.makerStopMinutes.coerceAtLeast(0) * 60_000L,
            minPrice = s.makerMinPrice.coerceIn(0.001, 0.999),
            maxPrice = s.makerMaxPrice.coerceIn(0.001, 0.999),
            bothSides = s.makerBothSides,
            minBooks = s.makerMinBooks.coerceAtLeast(1),
        )
    }
}

/** One side of one Novig market Vigilant has priced: what a bid on it is judged from. */
data class MakerLine(
    val market: NovigMarket,
    val outcomeId: String,
    val league: String,
    val eventName: String,
    val marketLabel: String,
    val selection: String,
    val kind: BetKind,
    /** Vigilant's fair probability for this side; null when it has none. */
    val fair: Double?,
    /** When the oldest book price behind [fair] was seen. */
    val fairAsOfMs: Long?,
    /** The fair is too old to bet on now (the app's freshness rule for this game). */
    val fairOld: Boolean,
    /** Books behind [fair]. */
    val books: Int,
    /** Novig's price to take this side now (1 − the best bid on the other side); null when nothing is offered. */
    val offer: Double?,
    /** The best resting bid on this side now (what a new bid has to beat to lead). */
    val bestBid: Double?,
    val live: Boolean,
    val source: String,
    val basis: FairBasis? = null,
    val gameUrl: String? = null,
) {
    val marketId: String get() = market.marketId
    val startsTs: Long get() = market.startsTs
}

/** What [MakerQuote.decide] made of a line: a bid to post, or why not. */
sealed interface MakerDecision {
    val line: MakerLine

    /** Post [contracts] at [price] (what they cost: [cost] dollars); [evAtFair] = fair / price − 1. */
    data class Post(override val line: MakerLine, val price: Double, val contracts: Long, val evAtFair: Double) : MakerDecision {
        val cost: Double get() = contracts * price * EvMath.CONTRACT_PAYOUT_DOLLARS
    }

    data class Skip(override val line: MakerLine, val why: String) : MakerDecision
}

object MakerQuote {

    /**
     * The bid for [line] under [rules] at [now], or why there's none. [held]: outcomes already held or bet and still open (a filled bid is a bet;
     * the same side is never bought twice, like the auto-bet). The bid is fair / (1 + margin) floored to Novig's grid; it must stay under Novig's offer
     * (a post-only bid at or over it would be refused: that side is a bet to take now, the +EV feed's).
     */
    fun decide(line: MakerLine, rules: MakerRules, now: Long, held: Set<String> = emptySet()): MakerDecision {
        fun skip(why: String) = MakerDecision.Skip(line, why)
        if (line.live || now >= line.startsTs) return skip("The game has started (Novig cancels resting bids at the start)")
        if (now >= line.startsTs - rules.stopMs) return skip("Starts within ${rules.stopMs / 60_000} min: no bids this close")
        if (line.market.status != "OPEN") return skip("Novig isn't taking orders on this market")
        if (line.kind !in rules.kinds) return skip("${line.kind.label} are off for bids")
        val fair = line.fair ?: return skip("No fair price")
        if (fair <= 0.0 || fair >= 1.0) return skip("No fair price")
        if (line.fairOld) return skip("The fair price is too old to bid on")
        if (line.books < rules.minBooks) return skip("Only ${line.books} book${if (line.books == 1) "" else "s"} behind the fair price (fewest: ${rules.minBooks})")
        if (line.outcomeId in held) return skip("Already bet or bid on this side")
        val price = PriceGrid.floor(fair / (1.0 + rules.margin)) ?: return skip("The fair price is too small to bid under")
        if (price < rules.minPrice - 1e-9 || price > rules.maxPrice + 1e-9) {
            return skip("A bid at ${percent(price)} is outside the price window (${percent(rules.minPrice)}-${percent(rules.maxPrice)})")
        }
        val offer = line.offer
        if (offer != null && price >= offer - 1e-9) return skip("Novig already offers it at ${percent(offer)}, at or under this bid: take it instead")
        val contracts = floor(rules.stake / (price * EvMath.CONTRACT_PAYOUT_DOLLARS) + 1e-9).toLong()
        if (contracts < 1) return skip("The stake is too small for one contract")
        return MakerDecision.Post(line, price, contracts, fair / price - 1.0)
    }

    /**
     * Every line's decision, with one side per market when [MakerRules.bothSides] is off: the cheaper side (the underdog's bid earns the most per bid,
     * RESEARCH.md §70.2).
     */
    fun decideAll(lines: List<MakerLine>, rules: MakerRules, now: Long, held: Set<String>): List<MakerDecision> {
        val all = lines.distinctBy { it.outcomeId }.map { decide(it, rules, now, held) }
        if (rules.bothSides) return all
        val keep = all.filterIsInstance<MakerDecision.Post>().groupBy { it.line.marketId }.values.mapTo(HashSet()) { posts -> posts.minBy { it.price }.line.outcomeId }
        return all.map { d ->
            if (d is MakerDecision.Post && d.line.outcomeId !in keep) MakerDecision.Skip(d.line, "One side per market (both sides is off): the other side's bid is cheaper") else d
        }
    }

    /** One grid step at [price] (NOVIG_API.md §7). */
    fun step(price: Double): Double = if (price <= 0.050 + 1e-9 || price >= 0.950 - 1e-9) 0.001 else 0.005

    private fun percent(p: Double) = String.format(Locale.US, "%.1f%%", p * 100)
}

/** One of Vigilant's bids resting on Novig now (from [MakerStore] and Novig's open orders). */
data class RestingBid(
    val orderId: String,
    val marketId: String,
    val outcomeId: String,
    val price: Double,
    /** Contracts still resting. */
    val remaining: Long,
    /** Contracts already filled (a partly filled bid is part bet). */
    val filled: Long,
    val expiresAtMs: Long?,
) {
    val restingDollars: Double get() = remaining * price * EvMath.CONTRACT_PAYOUT_DOLLARS
}

/** What one cycle does: cancel these (with why), then place these. */
data class MakerActions(val cancels: List<Pair<RestingBid, String>>, val places: List<MakerDecision.Post>, val kept: List<RestingBid>)

object MakerPlan {

    /**
     * Resting bids against the bids wanted now. [stopAll]: every bid comes down (paused, wallet empty, daily limit, bids switched off). A resting bid is
     * cancelled when its line is no longer wanted (with that line's reason from [skips]), moved when the fair fell under it (it would now be over the
     * fair minus the margin: the bid that gets picked off, §70.3) or rose by [MakerRules.requoteSteps] steps or more, and re-posted when it's about to
     * expire; a partly filled bid isn't re-posted (that side is now a bet). New bids go in cheapest first (the underdog side earns the most per bid),
     * within [MakerRules.maxBids], [MakerRules.maxDollars] and [budget] (the wallet and the day's limit).
     */
    fun plan(
        wanted: List<MakerDecision.Post>,
        resting: List<RestingBid>,
        rules: MakerRules,
        now: Long,
        skips: Map<String, String> = emptyMap(),
        stopAll: String? = null,
        budget: Double = Double.MAX_VALUE,
    ): MakerActions {
        if (stopAll != null) return MakerActions(resting.map { it to stopAll }, emptyList(), emptyList())
        val byOutcome = wanted.associateBy { it.line.outcomeId }
        val cancels = ArrayList<Pair<RestingBid, String>>()
        val kept = ArrayList<RestingBid>()
        val noRepost = HashSet<String>()
        for (r in resting) {
            val w = byOutcome[r.outcomeId]
            val why = when {
                w == null -> skips[r.outcomeId] ?: "No longer a bid to post"
                w.price < r.price - 1e-9 -> "The fair price fell: re-posted lower"
                w.price >= r.price + rules.requoteSteps * MakerQuote.step(r.price) - 1e-9 -> "The fair price rose: re-posted higher"
                r.expiresAtMs != null && r.expiresAtMs - now <= rules.refreshBeforeMs -> "About to expire: re-posted"
                else -> null
            }
            if (why == null) {
                kept += r
                continue
            }
            cancels += r to why
            if (r.filled > 0) noRepost += r.outcomeId
        }
        val covered = kept.mapTo(HashSet()) { it.outcomeId } + noRepost
        var bids = kept.size
        var dollars = kept.sumOf { it.restingDollars }
        var spend = budget
        val places = ArrayList<MakerDecision.Post>()
        for (w in wanted.filter { it.line.outcomeId !in covered }.sortedWith(compareBy<MakerDecision.Post> { it.price }.thenByDescending { it.evAtFair })) {
            if (bids >= rules.maxBids) break
            if (dollars + w.cost > rules.maxDollars + 1e-9 || w.cost > spend + 1e-9) continue
            places += w
            bids++
            dollars += w.cost
            spend -= w.cost
        }
        return MakerActions(cancels, places, kept)
    }
}
