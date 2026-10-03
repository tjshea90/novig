package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.PriceGrid
import java.util.Locale
import kotlin.math.floor

/**
 * How bids are posted (Tj, 2026-10-03: "figure out the optimal bets and math for make bets with the highest chance of beating clv and profiting, build
 * the system in the app"; then "it only will make bets which are positive EV, aiming for as much profit as possible … Make sure to implement the
 * strategies of proven professional bettors"; RESEARCH.md §69-§70), from [ScanSettings]: [margin] under the fair, sized by [stakeMode] (fractional
 * Kelly on [bankroll] like the auto-bet, a dollar, or [customStake]) and never over [maxStake], at most [maxBids] and [maxDollars] resting, on
 * [kinds], each resting [ttlMs] at most and never past the fair's own freshness, none within [stopMs] of the start, bid prices in
 * [minPrice]..[maxPrice], at least [minBooks] books each pricing the bid +EV on their own, and (with [sharpVeto]) no sharp book saying it isn't.
 */
data class MakerRules(
    val margin: Double,
    /** The amount [AutoBetStake.CUSTOM] bids. */
    val customStake: Double,
    val maxBids: Int,
    val maxDollars: Double,
    val kinds: Set<BetKind>,
    val ttlMs: Long,
    val stopMs: Long,
    val minPrice: Double,
    val maxPrice: Double,
    val bothSides: Boolean,
    val minBooks: Int,
    val stakeMode: com.tjshea.vigilant.data.scanner.AutoBetStake = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM,
    /** The most one bid may cost (Tj's per-bet limit is the ceiling). */
    val maxStake: Double = customStake,
    /** What a Kelly stake is a fraction of (Settings' bankroll, as the auto-bet uses). */
    val bankroll: Double = 0.0,
    /** Skip a bid that a sharp book in the fair, devigged on its own, says isn't +EV (the auto-bet's sharp veto, RESEARCH.md §66). */
    val sharpVeto: Boolean = true,
    /** A resting bid is moved up only when the bid wanted is at least this many grid steps higher (moving loses its place in the queue). */
    val requoteSteps: Int = 2,
    /** A bid this close to expiring is re-posted now (so a bid that's still good is always up). */
    val refreshBeforeMs: Long = 2 * 60_000L,
    /** No bid is posted for less time than this (its fair about to go old, the start or the stop window too near). */
    val minLifeMs: Long = 60_000L,
) {
    companion object {
        fun of(s: ScanSettings) = MakerRules(
            margin = s.makerMargin.coerceIn(0.005, 0.5),
            customStake = s.makerStake.coerceAtLeast(0.01),
            maxBids = s.makerMaxBids.coerceAtLeast(0),
            maxDollars = s.makerMaxDollars.coerceAtLeast(0.0),
            kinds = s.makerKinds,
            ttlMs = s.makerTtlMinutes.coerceIn(1, 24 * 60) * 60_000L,
            stopMs = s.makerStopMinutes.coerceAtLeast(0) * 60_000L,
            minPrice = s.makerMinPrice.coerceIn(0.001, 0.999),
            maxPrice = s.makerMaxPrice.coerceIn(0.001, 0.999),
            bothSides = s.makerBothSides,
            minBooks = s.makerMinBooks.coerceAtLeast(1),
            stakeMode = s.makerStakeMode,
            // A filled bid is a bet: never more than the per-bet limit.
            maxStake = minOf(s.makerMaxStake, s.apiMaxStake).coerceAtLeast(0.01),
            bankroll = s.bankroll,
            sharpVeto = s.makerSharpVeto,
        )

        /** Game lines (moneylines, spreads, game totals): bid on only with a sharp book in the fair (RESEARCH.md §70.2). */
        val GAME_LINES = setOf(BetKind.MONEYLINE, BetKind.SPREAD, BetKind.TOTAL)
    }
}

/** One side of one Novig market Vigilant has priced: what a bid on it is judged from. */
data class MakerLine(
    val market: NovigMarket,
    val outcomeId: String,
    /** The game's start: the earlier of the event's and the market's. */
    val startsTs: Long,
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
    /** Each book's own fair for this side, devigged worst case (one per book): what "books agree" counts. Empty = not known. */
    val bookFairs: List<Double> = emptyList(),
    /** The same for the sharp books in the fair (Pinnacle, Circa, the exchanges Settings calls sharp). */
    val sharpFairs: List<Double> = emptyList(),
    /** Novig's price to take this side now (1 − the best bid on the other side); null when nothing is offered. */
    val offer: Double?,
    /** The best resting bid on this side now (what a new bid has to beat to lead). */
    val bestBid: Double?,
    val live: Boolean,
    val source: String,
    val basis: FairBasis? = null,
    val gameUrl: String? = null,
    /** When Novig's book behind [offer] and [bestBid] was read (it can be the last scan's: [MakerLines.MAX_BOOK_AGE_MS]). */
    val bookAtMs: Long? = null,
) {
    val marketId: String get() = market.marketId
}

/** The lines a bid can be judged on: every side Vigilant's scan priced ([com.tjshea.vigilant.data.scanner.Opportunity] keeps them all, +EV or not). */
object MakerLines {

    /** The kind of bet an outcome is, as [MakerRules.kinds] and the auto-bet name them. */
    fun kindOf(o: com.tjshea.vigilant.data.scanner.Opportunity): BetKind = when {
        o.kind == com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP -> BetKind.PROP
        o.kind == com.tjshea.vigilant.data.reference.LineKind.TEAM_TOTAL -> BetKind.TEAM_TOTAL
        o.market.marketType in com.tjshea.vigilant.data.scanner.MarketFamily.FIRST_HALF.novigTypes -> BetKind.PERIOD
        o.kind == com.tjshea.vigilant.data.reference.LineKind.MONEYLINE -> BetKind.MONEYLINE
        o.kind == com.tjshea.vigilant.data.reference.LineKind.SPREAD -> BetKind.SPREAD
        o.kind == com.tjshea.vigilant.data.reference.LineKind.TOTAL -> BetKind.TOTAL
        else -> BetKind.OTHER
    }

    /**
     * The oldest Novig book a line may be judged with. A bid's price comes from the fair alone; Novig's book only says whether the bid would cross
     * its offer (a post-only bid that would is refused whole, nothing fills) and what's already bid. So a scan still reading Novig's books can bid on
     * every line whose fair odds are in, with the book its last scan read, instead of waiting minutes for each book to be read again (Tj, 2026-10-03:
     * "I had auto make bids turned on, but it didn't actually make any bids by itself": a scan took 8 minutes and bids waited for its end).
     */
    const val MAX_BOOK_AGE_MS = 20 * 60_000L

    /**
     * [result]'s priced sides in [settings]' leagues at [now], pregame, as [MakerLine]s: on a scan still running, the leagues whose fair-odds sources
     * have all answered (as the feed holds them back), each with the newest Novig book read in the last [MAX_BOOK_AGE_MS]. Each book's own fair
     * ([bookFairs], a devig per book) is worked out only for the kinds of bet bids are on (a busy slate prices 13,000 sides).
     */
    fun from(result: com.tjshea.vigilant.data.scanner.ScanResult?, settings: ScanSettings, now: Long): List<MakerLine> {
        result ?: return emptyList()
        return result.opportunities.filter { o ->
            o.league.novigName in settings.leagues && !o.isLive && o.fairProbability != null &&
                o.bookFetchedAtMs != null && now - o.bookFetchedAtMs <= MAX_BOOK_AGE_MS &&
                (result.waitingFor.isEmpty() || com.tjshea.vigilant.data.scanner.ScanResult.waitKey(o.league.novigName, o.kind == com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP) !in result.waitingFor)
        }.map { o ->
            val kind = kindOf(o)
            val judged = kind in settings.makerKinds
            MakerLine(
                market = o.market, outcomeId = o.outcome.outcomeId, startsTs = minOf(o.event.startsTs, o.market.startsTs), league = o.league.displayName,
                eventName = o.event.description, marketLabel = o.marketLabel, selection = o.selection, kind = kind, fair = o.fairProbability,
                fairAsOfMs = o.fairAsOfMs, fairOld = o.fairIsOld(now), books = o.fair?.booksUsed?.size ?: 0, offer = o.quote?.price,
                bestBid = o.bestBid, live = o.isLive, source = com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_VIGILANT,
                basis = FairBasis.of(o), bookFairs = if (judged) bookFairs(o, sharpOnly = false) else emptyList(),
                sharpFairs = if (judged) bookFairs(o, sharpOnly = true) else emptyList(), bookAtMs = o.bookFetchedAtMs,
            )
        }
    }

    /**
     * Each book behind [o]'s fair, devigged on its own the worst way for this side (the lowest of multiplicative, additive, power and Shin, as the
     * app's agreement check does: [com.tjshea.vigilant.data.scanner.Agreement]), one per book; only the sharp ones with [sharpOnly].
     */
    fun bookFairs(o: com.tjshea.vigilant.data.scanner.Opportunity, sharpOnly: Boolean): List<Double> {
        val fair = o.fair ?: return emptyList()
        val side = o.referenceIndex ?: return emptyList()
        val used = fair.booksUsed.toHashSet()
        return fair.perBook.asSequence()
            .filter { it.book.bookTitle in used && (!sharpOnly || it.isSharp) }
            .distinctBy { it.book.bookKey }
            .mapNotNull { b ->
                val odds = b.book.decimalOdds
                if (odds.size < 2 || !odds.all { it > 1.0 && it.isFinite() }) return@mapNotNull null
                val raw = odds.map { 1.0 / it }
                val sum = raw.sum()
                runCatching { if (sum <= 1.0) raw.map { it / sum } else com.tjshea.vigilant.engine.Devig.worstCase(raw) }.getOrNull()?.getOrNull(side)
            }
            .toList()
    }
}

/** What [MakerQuote.decide] made of a line: a bid to post, or why not. */
sealed interface MakerDecision {
    val line: MakerLine

    /**
     * Post [contracts] at [price] (what they cost: [cost] dollars); [evAtFair] = fair / price − 1; [restUntilMs]: when it must be down (the expiry, the
     * fair's freshness and the stop window before the start, whichever comes first).
     */
    data class Post(override val line: MakerLine, val price: Double, val contracts: Long, val evAtFair: Double, val restUntilMs: Long = Long.MAX_VALUE) : MakerDecision {
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
        val seen = line.fairAsOfMs ?: return skip("The fair price's age isn't known: no bid on it")
        // A bid never outlives what it was priced from: the fair's own freshness, the stop window before the start, and the expiry setting.
        val until = minOf(now + rules.ttlMs, line.startsTs - rules.stopMs, seen + Freshness.maxAgeMs(line.startsTs, now))
        if (until - now < rules.minLifeMs) return skip("The fair price goes old within a minute: re-priced at the next scan")
        if (line.kind in MakerRules.GAME_LINES && line.sharpFairs.isEmpty()) return skip("Game lines need a sharp book (Pinnacle, Circa …) in the fair")
        if (line.outcomeId in held) return skip("Already bet or bid on this side")
        val price = PriceGrid.floor(fair / (1.0 + rules.margin)) ?: return skip("The fair price is too small to bid under")
        if (price < rules.minPrice - 1e-9 || price > rules.maxPrice + 1e-9) {
            return skip("A bid at ${percent(price)} is outside the price window (${percent(rules.minPrice)}-${percent(rules.maxPrice)})")
        }
        val offer = line.offer
        if (offer != null && price >= offer - 1e-9) return skip("Novig already offers it at ${percent(offer)}, at or under this bid: take it instead")
        // Books agree: each one's own fair (worst case) must put this bid at +EV, at least [minBooks] of them (the auto-bet's "books agree").
        val agreeing = if (line.bookFairs.isEmpty()) line.books else line.bookFairs.count { it > price + 1e-9 }
        if (agreeing < rules.minBooks) return skip("Only $agreeing book${if (agreeing == 1) "" else "s"} price this bid +EV on their own (fewest: ${rules.minBooks})")
        if (rules.sharpVeto && line.sharpFairs.any { it <= price + 1e-9 }) return skip("A sharp book's own price says this bid isn't +EV")
        val stake = stake(fair, price, rules) ?: return skip("No stake: ${rules.stakeMode.label} has nothing to bid here (no bankroll set?)")
        val contracts = floor(stake / (price * EvMath.CONTRACT_PAYOUT_DOLLARS) + 1e-9).toLong()
        if (contracts < 1) return skip("The stake is too small for one contract")
        return MakerDecision.Post(line, price, contracts, fair / price - 1.0, until)
    }

    /**
     * What one bid stakes (dollars it costs if it fills), held to [MakerRules.maxStake]: a fraction of full Kelly on the bankroll for this bid's own
     * price and fair (`(fair − price) / (1 − price)`: makers pay no fee), a dollar, or the set amount. Null when Kelly has nothing to stake.
     */
    fun stake(fair: Double, price: Double, rules: MakerRules): Double? {
        val wanted = when (rules.stakeMode) {
            com.tjshea.vigilant.data.scanner.AutoBetStake.ONE_DOLLAR -> 1.0
            com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM -> rules.customStake
            else -> {
                val f = rules.stakeMode.kelly ?: return null
                if (!(rules.bankroll > 0.0) || fair <= price || price >= 1.0) return null
                f * (fair - price) / (1.0 - price) * rules.bankroll
            }
        }
        return minOf(wanted, rules.maxStake).takeIf { it > 0.0 }
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

/**
 * What one cycle does: cancel these (with why), then place these. [waiting]: bids wanted that weren't posted this pass, by why (the most bids or
 * dollars up at once, the wallet or the day's limit), so the tab can say why auto-make isn't posting them.
 */
data class MakerActions(
    val cancels: List<Pair<RestingBid, String>>,
    val places: List<MakerDecision.Post>,
    val kept: List<RestingBid>,
    val waiting: Map<String, Int> = emptyMap(),
)

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
        /**
         * False with auto-make off: bids Tj approved by hand are only taken down when they stop being worth it (the fair fell under the bid, its line
         * isn't wanted any more); nothing new is posted, nothing is moved up or re-posted (each bid is the one he approved).
         */
        repost: Boolean = true,
        /**
         * The lines come from a scan still running (its leagues arrive one at a time): a resting bid whose line this pass didn't judge (neither
         * [wanted] nor in [skips]) is left up; its `ttl` already ends it when its fair goes old, and a later pass judges it. On a finished scan a bid
         * whose line is gone comes down.
         */
        partial: Boolean = false,
    ): MakerActions {
        if (stopAll != null) return MakerActions(resting.map { it to stopAll }, emptyList(), emptyList())
        val byOutcome = wanted.associateBy { it.line.outcomeId }
        val cancels = ArrayList<Pair<RestingBid, String>>()
        val kept = ArrayList<RestingBid>()
        val noRepost = HashSet<String>()
        for (r in resting) {
            val w = byOutcome[r.outcomeId]
            if (w == null && partial && r.outcomeId !in skips) {
                kept += r
                continue
            }
            val why = when {
                w == null -> skips[r.outcomeId] ?: "No longer a bid to post"
                w.price < r.price - 1e-9 -> if (repost) "The fair price fell: re-posted lower" else "The fair price fell under the bid: taken down"
                repost && w.price >= r.price + rules.requoteSteps * MakerQuote.step(r.price) - 1e-9 -> "The fair price rose: re-posted higher"
                repost && r.expiresAtMs != null && r.expiresAtMs - now <= rules.refreshBeforeMs -> "About to expire: re-posted"
                else -> null
            }
            if (why == null) {
                kept += r
                continue
            }
            cancels += r to why
            if (r.filled > 0) noRepost += r.outcomeId
        }
        if (!repost) return MakerActions(cancels, emptyList(), kept)
        val covered = kept.mapTo(HashSet()) { it.outcomeId } + noRepost
        var bids = kept.size
        var dollars = kept.sumOf { it.restingDollars }
        var spend = budget
        val places = ArrayList<MakerDecision.Post>()
        val waiting = HashMap<String, Int>()
        fun wait(why: String) = waiting.merge(why, 1, Int::plus)
        for (w in wanted.filter { it.line.outcomeId !in covered }.sortedWith(compareBy<MakerDecision.Post> { it.price }.thenByDescending { it.evAtFair })) {
            when {
                bids >= rules.maxBids -> wait(MAX_BIDS_REACHED.format(rules.maxBids))
                dollars + w.cost > rules.maxDollars + 1e-9 -> wait(MAX_DOLLARS_REACHED.format(money(rules.maxDollars)))
                w.cost > spend + 1e-9 -> wait(BUDGET_REACHED)
                else -> {
                    places += w
                    bids++
                    dollars += w.cost
                    spend -= w.cost
                }
            }
        }
        return MakerActions(cancels, places, kept, waiting)
    }

    /** Why a wanted bid waits (the tab and Diagnostics say how many each). */
    const val MAX_BIDS_REACHED = "the most bids up at once (%d) is reached"
    const val MAX_DOLLARS_REACHED = "the most dollars up at once (%s) is reached"
    const val BUDGET_REACHED = "the wallet (or today's limit for API bets) can't cover it beside the bids already up"

    private fun money(v: Double) = String.format(Locale.US, "$%,.2f", v)
}
