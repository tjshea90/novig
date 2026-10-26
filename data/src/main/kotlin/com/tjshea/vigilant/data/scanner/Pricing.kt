package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairValue
import com.tjshea.vigilant.engine.MakerBid
import com.tjshea.vigilant.engine.PositiveDepth
import com.tjshea.vigilant.engine.TakeLevel
import java.util.concurrent.atomic.AtomicReferenceArray

/** One Novig outcome, priced. Every priced outcome is kept, +EV or not; the feed filters. */
data class Opportunity(
    val league: League,
    val event: NovigEvent,
    val market: NovigMarket,
    val outcome: NovigOutcome,
    val marketLabel: String,
    val kind: LineKind,
    val selection: String,
    val fair: FairLine?,
    val fairProbability: Double?,
    /** Null when nobody is offering this side right now. */
    val quote: EvQuote?,
    val ladder: List<TakeLevel>,
    val depth: PositiveDepth?,
    val suggestedStake: Double?,
    /** Novig's own spread on this market: best take price for both sides, minus 1. */
    val novigWidth: Double?,
    /** The best resting bid for this outcome itself: the price a new bid has to beat to lead. */
    val bestBid: Double? = null,
    /** Every resting bid on this outcome, best first (the book's own list, not a copy): what a bid of Vigilant's sits behind ([com.tjshea.vigilant.data.novig.trading.maker.MakerLines.withoutOwn]). */
    val bidLevels: List<com.tjshea.vigilant.data.novig.BidLevel> = emptyList(),
    val bookFetchedAtMs: Long?,
    /** Newest `last_update` among the books that fed the fair line. */
    val fairUpdatedMs: Long?,
    val refEvent: RefEvent?,
    val lineKey: LineKey?,
    /** Which reference side this outcome is priced from. */
    val target: OutcomeTarget?,
    /**
     * When the oldest book price behind [fair] was last seen by its feed (RESEARCH.md §24): past
     * [Freshness.MAX_QUOTE_AGE_MS] this EV is no longer current and isn't shown.
     */
    val fairAsOfMs: Long? = null,
    /** Only on a real-time live row ([com.tjshea.vigilant.data.pinnodds.LiveScanRows]): when each of the two feeds last said anything about this line. */
    val liveAges: LiveAges? = null,
) {
    /** Column of this outcome in [FairLine.perBook] odds. */
    val referenceIndex: Int?
        get() {
            val k = lineKey ?: return null
            val t = target as? OutcomeTarget.Is ?: return null
            return k.sides.indexOf(t.side).takeIf { it >= 0 }
        }

    val key: String get() = "${market.marketId}/${outcome.outcomeId}"

    /**
     * Where to post a resting order instead of taking: the highest price that still makes
     * [minEvPercent] if it fills. Makers pay no fee on Novig. Null when taking right now is
     * already as cheap: there's no reason to wait for a fill at a worse price.
     */
    fun makerBid(minEvPercent: Double): MakerBid? {
        val bid = fairProbability?.let { EvMath.makerBid(it, minEvPercent) } ?: return null
        val take = quote?.cost
        return bid.takeIf { take == null || take > bid.price + 1e-9 }
    }

    /** The other books' prices behind this EV are too old at [now] for its game ([Freshness.maxAgeMs]): don't offer it. */
    fun fairIsOld(now: Long): Boolean = fairAsOfMs != null && now - fairAsOfMs > Freshness.maxAgeMs(event.startsTs, now)

    /** Novig's price for this line was read more than [Pricing.OLD_PRICE_MS] before [now]. */
    fun priceIsOld(now: Long): Boolean = bookFetchedAtMs == null || now - bookFetchedAtMs > Pricing.OLD_PRICE_MS
    val evPercent: Double? get() = quote?.evPercent

    /** How many of the books pricing both sides back this bet, and whether a sharp one does ([Agreement]); worked out once per outcome. */
    val agreement: Agreement.Count by lazy(LazyThreadSafetyMode.PUBLICATION) { Agreement.of(this) }

    /** The 1-5 star rating of this bet ([BetRating]); null while it has no price or fair line. */
    val rating: BetRating.Rating? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val q = quote
        if (q == null || fair == null) null else {
            val a = agreement
            BetRating.rate(BetRating.Inputs(a.twoSided, a.agreeing, q.evPercent, a.sharpAgrees, a.sharpAgainst, a.spread,
                typePoints = ProfitProfile.typePointsOf(league.novigName, BetKind.of(marketLabel, selection).label, selection, event.startsTs, System.currentTimeMillis()),
                type = BetType.of(league.novigName, BetKind.of(marketLabel, selection), selection, q.price, event.startsTs, System.currentTimeMillis())))
        }
    }
    val eventName: String get() = event.description
    val isLive: Boolean get() = event.isLive
}

/** One game with all its priced outcomes, for the Games screen. */
data class PricedGame(
    val league: League,
    val event: NovigEvent,
    val refEvent: RefEvent?,
    val outcomes: List<Opportunity>,
)

data class ScanStats(
    val novigEvents: Int,
    val matchedEvents: Int,
    val marketsPriced: Int,
    val outcomesWithFair: Int,
    val positiveEv: Int,
    /** Games Novig lists past "Days ahead" (not scanned): [Plan.laterGames]. */
    val laterGames: Int = 0,
)

/** What a live row's prices are as of, from the two sockets themselves: Pinnacle's line last CHANGED, Pinnacle's game feed last spoke, Novig's market last changed, Novig's game feed last spoke (phone clock, ms). */
data class LiveAges(val pinnLineChangedMs: Long, val pinnFeedMs: Long, val novigMarketMs: Long?, val novigFeedMs: Long)

/** The Feed's orders (Tj, 2026-10-11 added Stars: the 1-5 star rating, best first, the bigger edge first among equals). */
object FeedOrder {
    fun sort(list: List<Opportunity>, sort: FeedSort): List<Opportunity> = list.sortedWith(comparator(sort))

    /** The order of [sort], for lists that mix Novig's and Kalshi's rows (the Feed's merged list used to be EV-only whatever was picked). */
    fun comparator(sort: FeedSort): Comparator<Opportunity> = when (sort) {
        FeedSort.EV -> compareByDescending { it.evPercent }
        FeedSort.START -> compareBy<Opportunity> { it.event.startsTs }.thenByDescending { it.evPercent }
        FeedSort.STARS -> compareByDescending<Opportunity> { it.rating?.stars ?: 0.0 }.thenByDescending { it.evPercent }
    }
}

data class ScanResult(
    val games: List<PricedGame>,
    val opportunities: List<Opportunity>,
    val stats: ScanStats,
    val computedAtMs: Long,
    /**
     * Set on a partial result, mid-scan: the scan's start. Its feed shows only Novig prices read
     * since then, so a price from the last scan is never offered as a bet while the new one loads.
     */
    val freshSinceMs: Long? = null,
    /**
     * On a partial result: the [waitKey]s (a league's game lines, or its props) whose fair-odds sources haven't
     * all answered yet. Their bets are held back from [feed] until they have, so the fair line behind a bet shown
     * mid-scan is the one it ends with.
     */
    val waitingFor: Set<String> = emptySet(),
) {
    /** True while the scan that produced this is still reading prices. */
    val partial: Boolean get() = freshSinceMs != null

    /**
     * Cheap equality: the same result, or one computed at the same moment with the same shape. The generated one walked every opportunity and every reference event on the MAIN thread each time Compose compared the app's
     * state (Tj's 2026-10-14 file: "not responding ... Waited 10000ms for MotionEvent", main thread in `UiState.equals` > `ScanResult.equals` > `Opportunity.equals` > `RefEvent.equals`): a re-scan that priced the same
     * thousands of lines compared them all, every time. A result is a new value whenever it is recomputed ([computedAtMs]), so this never hides a change.
     */
    override fun equals(other: Any?): Boolean =
        this === other || (other is ScanResult && computedAtMs == other.computedAtMs && freshSinceMs == other.freshSinceMs && games.size == other.games.size &&
            opportunities.size == other.opportunities.size && waitingFor == other.waitingFor)

    override fun hashCode(): Int = (computedAtMs.hashCode() * 31 + opportunities.size) * 31 + games.size

    companion object {
        /** The least a bet may have fillable at +EV to stay on the list. */
        const val MIN_LIQUIDITY_DOLLARS = 0.10

        /** A league's game lines ([props] false) or its player props, as held back in [waitingFor]. */
        fun waitKey(league: String, props: Boolean): String = "$league|${if (props) "props" else "lines"}"
    }

    /** Less than this many dollars fillable at +EV and the bet is not worth listing (Tj, 2026-10-11: "automatically remove from the list any bet with less than 10 cents liquidity"). */
    private fun hasLiquidity(o: Opportunity): Boolean = o.depth?.let { it.dollarCost >= MIN_LIQUIDITY_DOLLARS - 1e-9 } ?: true

    /** The +EV feed: at or above the user's threshold, below the too-good-to-be-true cap, in the chosen order. */
    fun feed(settings: ScanSettings): List<Opportunity> = opportunities
        .filter { o ->
            val ev = o.evPercent ?: return@filter false
            (freshSinceMs == null || (o.bookFetchedAtMs ?: 0L) >= freshSinceMs) &&
                (waitingFor.isEmpty() || waitKey(o.league.novigName, o.kind == LineKind.PLAYER_PROP) !in waitingFor) &&
                o.league.novigName in settings.leagues &&
                ev >= settings.minEvPercent && ev <= settings.maxEvPercent && ev > 0.0 &&
                hasLiquidity(o) &&
                settings.withinOdds(o.quote!!.cost) &&
                // A live game's price is never taken from a scan (its other books are minutes old): "Include live games" lists live bets from the real-time lane only (LiveScanFeed, Tj 2026-10-11).
                !o.isLive &&
                MarketFamily.entries.any { it in settings.families && o.market.marketType in it.novigTypes }
        }
        .let { list ->
            FeedOrder.sort(list, settings.feedSort)
        }
}

/**
 * Fair lines by [LineKey], kept for as long as the same [Plan] object and [FairSettings] are priced
 * (v0.18.0, Tj 2026-09-28: "make the scans … faster"). Every partial result of a scan used to devig
 * every line's books again (power devig: ~200 `pow` calls per book), though only Novig's books had
 * changed; with 1,200 prices a scan that was as slow as the reads themselves. A plan is re-made
 * whenever the fair odds behind it change, so a line is never priced from stale quotes.
 *
 * It also keeps the last plan's priced markets ([pricedFor]), so a partial result re-uses every market whose
 * Novig book hasn't changed instead of building all of its outcomes again (v0.39.1, Tj's Diagnostics 2026-10-01: an
 * OutOfMemoryError in a no-limit scan, where each partial built ~17,000 outcomes of which a batch changed a few hundred).
 */
class FairMemo(private val keep: Int = 3) {
    private val entries = ArrayList<Triple<Plan, FairSettings, HashMap<LineKey, FairLine?>>>()
    private var priced: PricedMarkets? = null

    @Synchronized
    fun linesFor(plan: Plan, settings: FairSettings): HashMap<LineKey, FairLine?> {
        entries.firstOrNull { it.first === plan && it.second == settings }?.let { return it.third }
        val fresh = HashMap<LineKey, FairLine?>()
        entries.add(0, Triple(plan, settings, fresh))
        while (entries.size > keep) entries.removeAt(entries.size - 1)
        return fresh
    }

    /**
     * The priced markets of [plan] (with [settings]' fair method). Only the one most recently asked for is kept: a second
     * plan's outcomes held beside it would be the memory this exists to save.
     */
    @Synchronized
    fun pricedFor(plan: Plan, settings: FairSettings): PricedMarkets {
        priced?.takeIf { it.plan === plan && it.settings == settings }?.let { return it }
        return PricedMarkets(plan, settings).also { priced = it }
    }

    /** Lets go of every plan's fair lines and priced markets (they're made again on the next pricing). */
    @Synchronized
    fun clear() {
        entries.clear()
        priced = null
    }

    /** How many markets' outcomes are held for re-use, for tests. */
    @Synchronized
    fun pricedCount(): Int = priced?.count() ?: 0
}

/**
 * One plan's priced markets, by the market's place in [Plan.markets]. A slot is good only while what priced it is still what
 * is asked for: the same Novig book (the very object: a re-read makes a new one), the same bankroll and Kelly multiplier
 * (a stake is worked from both). The plan and fair method are fixed for the object's life, and everything else an outcome
 * is made of (the market, its fee, whether it's live, the fair line) comes from them.
 */
class PricedMarkets internal constructor(internal val plan: Plan, internal val settings: FairSettings) {
    private class Slot(val book: NovigBook?, val bankroll: Double, val kelly: Double, val outcomes: List<Opportunity>)

    private val slots = AtomicReferenceArray<Slot?>(plan.markets.size)

    internal fun get(index: Int, book: NovigBook?, bankroll: Double, kelly: Double): List<Opportunity>? {
        val slot = slots.get(index) ?: return null
        return slot.outcomes.takeIf { slot.book === book && slot.bankroll == bankroll && slot.kelly == kelly }
    }

    internal fun put(index: Int, book: NovigBook?, bankroll: Double, kelly: Double, outcomes: List<Opportunity>) {
        slots.set(index, Slot(book, bankroll, kelly, outcomes))
    }

    internal fun count(): Int = (0 until slots.length()).count { slots.get(it) != null }
}

/**
 * Pure pricing: plan + books + settings in, priced outcomes out. No network, so changing a
 * setting (fair source, devig method, Kelly) re-prices instantly from what's already fetched.
 */
object Pricing {

    /** Past this, a Novig price is too old to bet on without a recheck (exchange prices move fast). */
    const val OLD_PRICE_MS = 10 * 60_000L

    fun price(
        plan: Plan,
        books: Map<String, NovigBook>,
        settings: ScanSettings,
        now: Long,
        /**
         * Fair lines already worked out for this same [plan] and fair settings ([FairMemo]): a scan
         * re-prices after every few Novig books, and only the books change between those, so each
         * line is devigged once per plan instead of once per partial result.
         */
        memo: FairMemo? = null,
    ): ScanResult {
        val fairSettings = settings.fairSettings()
        val fairCache = memo?.linesFor(plan, fairSettings) ?: HashMap()
        val refById = plan.markets.mapNotNull { it.refEvent }.associateBy { it.id }

        fun fairFor(key: LineKey): FairLine? = fairCache.getOrPut(key) {
            val ref = refById[key.refEventId] ?: return@getOrPut null
            // Low-usage bids: a book's quote past the freshness limit is left out BEFORE the devig, so a stale third book can't spoil a line two fresh sharp books price,
            // and a book with no stamp is not known to be current (RESEARCH.md §92). Everywhere else the oldest quote only dates the whole line ([Opportunity.fairIsOld]).
            val all = bookPrices(ref, key, settings.gates.freshestWins).let { all ->
                if (settings.lowUsageScan) all.filter { b -> b.lastUpdateMs != null && Freshness.fresh(b.lastUpdateMs, now, ref.commenceMs) } else all
            }
            ref.markets.filter { key.matches(it) }.let { atLine ->
                atLine.groupBy { it.source }.forEach { (src, l) -> QuoteCensus.add("atLine", src, l.size) }
                crossCensus(atLine, near = ref.commenceMs - now <= Freshness.FAR_OFF_MS, label = "${ref.away} @ ${ref.home} ${key.kind} ${key.line ?: ""}${if (key.period != 0) " p${key.period}" else ""}${key.subject?.let { " $it" } ?: ""}", now = now)
            }
            val prices = Freshness.freshFirst(all, now, { b -> b.lastUpdateMs }, { b -> b.bookKey in fairSettings.sharpBooks })
            if (Freshness.sgoMode) linesCensus(prices, now, ref.commenceMs)
            pinnacleCensus(atLine = ref.markets.filter { key.matches(it) && it.bookKey == PINNACLE }, chosen = prices.firstOrNull { it.bookKey == PINNACLE }?.source, now = now, sport = ref.sportKey.substringAfter('_'))
            if (prices.size < all.size) (all - prices.toSet()).groupBy { it.source }.forEach { (src, l) -> QuoteCensus.add("staleBesideFresh", src, l.size) }
            FairValue.compute(prices, fairSettings).also { f ->
                val used = f?.perBook?.map { it.book }?.toSet().orEmpty()
                prices.groupBy { it.source }.forEach { (src, l) -> QuoteCensus.add("devigLeftOut", src, l.count { it !in used }); QuoteCensus.add("priced", src, l.count { it in used }) }
                used.forEach { b -> QuoteCensus.add(QuoteCensus.BOOK_PRICED, b.bookKey) }
                // Books [com.tjshea.vigilant.engine.BadOdds] dropped as outliers or inverted, by sharp book or soft (PX9: with dozens of soft books the median is theirs, and a sharp book that moves first on news looks like an outlier).
                // PX11d (Tj 2026-10-25: "Do an audit to see if any book consistently posts outlier odds"): which book, so a day of logs names a book that is out of line again and again.
                f?.excluded?.forEach { b -> QuoteCensus.add("badOdds", if (b.bookKey in fairSettings.sharpBooks) "SHARP ${b.bookKey}" else "soft"); QuoteCensus.add(QuoteCensus.BOOK_OUT, b.bookKey) }
            }
        }

        val kept = memo?.pricedFor(plan, fairSettings)
        val all = ArrayList<Opportunity>()
        for ((index, pm) in plan.markets.withIndex()) {
            val fee = pm.market.fee ?: continue
            val book = books[pm.market.marketId]
            // What Novig's live feed says about the market NOW is ahead of the catalog (minutes old): a market it has closed has nothing to bet, and one it has put in play is not a pregame bet (its
            // taker fee is on, its resting orders are void, and the REST scan never lists live games: the live lane has them when Include live games is on). NOVIG_API.md §27.
            book?.lifecycle?.let { if (it.closed || it.live == true) continue }
            val reused = kept?.get(index, book, settings.bankroll, settings.kellyMultiplier)
            if (reused != null) {
                all += reused
                continue
            }
            val fair = pm.lineKey?.let(::fairFor)
            val live = pm.event.isLive
            val ladders = pm.outcomes.associate { it.outcome.outcomeId to (book?.takeLadder(pm.market, it.outcome.outcomeId).orEmpty()) }
            val bestTakes = ladders.values.mapNotNull { it.firstOrNull()?.price }
            val width = if (bestTakes.size == 2) bestTakes.sum() - 1.0 else null

            val outcomes = ArrayList<Opportunity>(pm.outcomes.size)
            for (po in pm.outcomes) {
                val ladder = ladders[po.outcome.outcomeId].orEmpty()
                val p = fair?.let { chance(po.target, pm.lineKey, it, pm.league.novigName, pm.event.startsTs, pm.label, po.selection, settings, now) }
                val quote = if (p != null && ladder.isNotEmpty()) EvMath.quote(p, ladder.first().price, fee, live) else null
                val depth = if (p != null && ladder.isNotEmpty()) EvMath.positiveDepth(ladder, p, fee, live) else null
                val stake = if (quote != null && quote.evPercent > 0) {
                    EvMath.suggestedStake(quote, settings.bankroll, settings.kellyMultiplier, depth?.dollarCost, (pm.event.startsTs - now) / 3_600_000.0)
                } else {
                    null
                }
                outcomes += Opportunity(
                    league = pm.league,
                    event = pm.event,
                    market = pm.market,
                    outcome = po.outcome,
                    marketLabel = pm.label,
                    kind = pm.kind,
                    selection = po.selection,
                    fair = fair,
                    fairProbability = p,
                    quote = quote,
                    ladder = ladder,
                    depth = depth,
                    suggestedStake = stake,
                    novigWidth = width,
                    bestBid = book?.bestBid(po.outcome.outcomeId)?.price,
                    bidLevels = book?.bidsByOutcome?.get(po.outcome.outcomeId).orEmpty(),
                    bookFetchedAtMs = book?.fetchedAtMs,
                    fairUpdatedMs = fair?.usedUpdates?.first,
                    fairAsOfMs = fair?.usedUpdates?.second,
                    refEvent = pm.refEvent,
                    lineKey = pm.lineKey,
                    target = po.target,
                )
            }
            kept?.put(index, book, settings.bankroll, settings.kellyMultiplier, outcomes)
            all += outcomes
        }

        val games = all.groupBy { it.event.eventId }.map { (_, list) ->
            val first = list.first()
            PricedGame(first.league, first.event, first.refEvent, list)
        }.sortedBy { it.event.startsTs }

        return ScanResult(
            games = games,
            opportunities = all,
            stats = ScanStats(
                novigEvents = plan.events.size,
                matchedEvents = plan.matchedEvents,
                marketsPriced = plan.markets.size,
                outcomesWithFair = all.count { it.fairProbability != null },
                positiveEv = all.count { (it.evPercent ?: -1.0) > 0 },
                laterGames = plan.laterGames,
            ),
            computedAtMs = now,
        )
    }

    /** The fair chance of [target] on [key]'s line, with the haircuts every price takes (one rule for [price] and [refair]). */
    fun chance(target: OutcomeTarget, key: LineKey, fair: FairLine, league: String, startsTs: Long, label: String, selection: String, settings: ScanSettings, now: Long): Double? =
        probabilityFor(target, key, fair)?.let { raw ->
            // Thin soft-book fairs far from the start run high (ThinEarlyHaircut): cut before anything reads the chance.
            val thin = if (settings.thinEarlyHaircut) (raw - ThinEarlyHaircut.points(league, key.stat, fair.booksUsed.size, (startsTs - now) / 3_600_000.0)).coerceAtLeast(0.0) else raw
            // Player-prop fairs run high, Overs most (PropHaircut): the history's Overs won 39.7% against a 49.4% fair.
            if (settings.propHaircut) (thin - PropHaircut.points(BetKind.of(label, selection), selection)).coerceAtLeast(0.0) else thin
        }

    /**
     * [o] with its fair odds worked out again from [ref] (its game's boards with the live streams laid over them, PB19: "all bets listed in the scanner should be real time information"), priced at the Novig
     * ladder it already holds; [requote] then moves it to a newer Novig book. The same arithmetic as [price] for one outcome. A line no book prices any more has no fair (and so no EV: it leaves the list).
     */
    fun refair(o: Opportunity, ref: RefEvent, settings: ScanSettings, now: Long): Opportunity {
        val key = o.lineKey ?: return o
        val target = o.target ?: return o
        val fs = settings.fairSettings()
        val prices = bookPrices(ref, key, settings.gates.freshestWins).let { all ->
            if (settings.lowUsageScan) all.filter { b -> b.lastUpdateMs != null && Freshness.fresh(b.lastUpdateMs, now, ref.commenceMs) } else all
        }.let { Freshness.freshFirst(it, now, { b -> b.lastUpdateMs }, { b -> b.bookKey in fs.sharpBooks }) }
        val fair = FairValue.compute(prices, fs)
        val p = fair?.let { chance(target, key, it, o.league.novigName, o.event.startsTs, o.marketLabel, o.selection, settings, now) }
        val fee = o.market.fee
        val ladder = o.ladder
        val live = o.event.isLive
        val quote = if (p != null && fee != null && ladder.isNotEmpty()) EvMath.quote(p, ladder.first().price, fee, live) else null
        val depth = if (p != null && fee != null && ladder.isNotEmpty()) EvMath.positiveDepth(ladder, p, fee, live) else null
        val stake = if (quote != null && quote.evPercent > 0) EvMath.suggestedStake(quote, settings.bankroll, settings.kellyMultiplier, depth?.dollarCost, (o.event.startsTs - now) / 3_600_000.0) else null
        return o.copy(
            fair = fair, fairProbability = p, quote = quote, depth = depth, suggestedStake = stake,
            fairUpdatedMs = fair?.usedUpdates?.first, fairAsOfMs = fair?.usedUpdates?.second, refEvent = ref,
        )
    }

    /**
     * [ref] without the quotes that have aged past their limit by [now] ([Freshness.fresh], by each quote's own feed). The scan's plan keeps only young quotes, but a row the engine's real-time list holds
     * keeps the game it was priced from, and nothing took a quote out of it as it aged: far games' rows (read only by the full scan) showed fair odds 17 to 25 minutes old (PX9, Tj 2026-10-25: "Why are the
     * odds this stale?"). Returns [ref] itself when nothing has aged out.
     */
    fun dropOld(ref: RefEvent, now: Long): RefEvent {
        val commence = ref.commenceMs.takeIf { ref.etDate == null }
        var aged = false
        val kept = ref.markets.filter { m -> Freshness.fresh(m.lastUpdateMs, now, commence, m.source).also { if (!it) aged = true } }
        return if (aged) ref.copy(markets = kept) else ref
    }

    /**
     * [o] priced again against a newer Novig [book] with the fair it already carries (Tj, 2026-10-14: a listed bet at +125 was +113 and -3.7% EV on Novig while a long scan held the list). Pure: the same
     * arithmetic as [price] for one outcome, no scan state. A market Novig has closed or put in play has nothing to bet pregame, so its quote goes.
     */
    fun requote(o: Opportunity, book: NovigBook, settings: ScanSettings, now: Long): Opportunity {
        val fee = o.market.fee ?: return o
        val closed = book.lifecycle?.let { it.closed || it.live == true } == true
        val ladder = if (closed) emptyList() else book.takeLadder(o.market, o.outcome.outcomeId)
        val p = o.fairProbability
        val live = o.event.isLive
        val quote = if (p != null && ladder.isNotEmpty()) EvMath.quote(p, ladder.first().price, fee, live) else null
        val depth = if (p != null && ladder.isNotEmpty()) EvMath.positiveDepth(ladder, p, fee, live) else null
        val stake = if (quote != null && quote.evPercent > 0) {
            EvMath.suggestedStake(quote, settings.bankroll, settings.kellyMultiplier, depth?.dollarCost, (o.event.startsTs - now) / 3_600_000.0)
        } else {
            null
        }
        return o.copy(
            quote = quote, ladder = ladder, depth = depth, suggestedStake = stake,
            bestBid = book.bestBid(o.outcome.outcomeId)?.price,
            bidLevels = book.bidsByOutcome[o.outcome.outcomeId].orEmpty(),
            bookFetchedAtMs = book.fetchedAtMs,
        )
    }

    /** A feed's quote replaces an earlier feed's for the same book only when its time is newer by more than this (the feeds' clocks differ by seconds; below it the feed order stands). */
    const val FRESHER_BY_MS = 20_000L

    /**
     * Every book's odds for one line, in [LineKey.sides] order; books missing a side are dropped. Where more than one feed sent the book, the first (the feeds' order) stands unless [freshest] and a
     * later feed's quote is newer by more than [FRESHER_BY_MS]; a quote with no time never replaces one.
     */
    fun bookPrices(ref: RefEvent, key: LineKey, freshest: Boolean = true): List<BookPrices> =
        chosenMarkets(ref, key, freshest).map { (m, odds) -> BookPrices(m.bookKey, m.bookTitle, odds, m.lastUpdateMs, m.source) }

    /** The quotes [bookPrices] uses for [key], each with its odds in [LineKey.sides] order: one per book, the freshest feed's where two sent it ([freshest]). */
    fun chosenMarkets(ref: RefEvent, key: LineKey, freshest: Boolean = true): List<Pair<com.tjshea.vigilant.data.reference.RefBookMarket, List<Double>>> {
        val all = ref.markets
            .filter { key.matches(it) }
            .mapNotNull { m ->
                val odds = key.sides.map { side -> m.quotes.firstOrNull { it.side == side }?.decimalOdds ?: return@mapNotNull null }
                m to odds
            }
        if (!freshest) return all.distinctBy { it.first.bookKey }
        val chosen = LinkedHashMap<String, Pair<com.tjshea.vigilant.data.reference.RefBookMarket, List<Double>>>()
        var doubted: MutableSet<String>? = null
        for (b in all) {
            val have = chosen[b.first.bookKey]
            val mine = b.first.lastUpdateMs
            val theirs = have?.first?.lastUpdateMs
            val newer = mine != null && theirs != null && mine - theirs > FRESHER_BY_MS
            // Two feeds dated within FRESHER_BY_MS of each other quote one book 3+ points apart: one of them has the wrong line (a mapping or a stale copy), and nothing says which (PB21b).
            // Only two aggregators' copies are weighed against each other: a book's own feed (Pinnacle's hosts, the exchanges) is the book itself.
            if (have != null && mine != null && theirs != null && kotlin.math.abs(mine - theirs) <= FRESHER_BY_MS && aggregator(have.first.source) && aggregator(b.first.source) &&
                disagree(have.second, b.second)) {
                (doubted ?: HashSet<String>().also { doubted = it }) += b.first.bookKey
            }
            if (have == null || newer) chosen[b.first.bookKey] = b
        }
        val out = doubted?.let { d -> crossDropped.addAndGet(d.size.toLong()); chosen.filterKeys { it !in d } } ?: chosen
        return out.values.toList()
    }

    /** A book left out of a line because two feeds quoted it at the same time [CROSS_CHECK_POINTS] or more apart ([chosenMarkets]); counted for the status. */
    val crossDropped = java.util.concurrent.atomic.AtomicLong()

    /**
     * PX9 (Tj 2026-10-25: "Why are the odds this stale if they are streamed?"): how many companies stand behind each line under the limit in force and under the plain one for fast feeds ([Freshness.plainFastFeeds]),
     * near games (within 3 hours of the start) and far ones apart. The engine logs it with the quote census: the plain limit is turned on only when it leaves lines well covered.
     */
    private fun linesCensus(prices: List<BookPrices>, now: Long, commenceMs: Long) {
        fun bucket(n: Int) = when { n <= 0 -> "0"; n == 1 -> "1"; n == 2 -> "2"; n <= 4 -> "3-4"; n <= 7 -> "5-7"; else -> "8+" }
        fun companies(l: List<BookPrices>) = l.distinctBy { com.tjshea.vigilant.engine.BookCompany.of(it.bookKey) }.size
        val plain = prices.filter { b -> b.lastUpdateMs == null || now - b.lastUpdateMs!! <= plainLimit(commenceMs, now, b.source) }
        val where = if (commenceMs - now <= Freshness.FAR_OFF_MS) "near" else "far"
        QuoteCensus.add("linesNow", "$where/${bucket(companies(prices))}")
        QuoteCensus.add("linesPlain", "$where/${bucket(companies(plain))}")
    }

    /** [Freshness.maxAgeMs] with the plain limit for every feed but SportsGameOdds' (what [Freshness.plainFastFeeds] would make it). */
    private fun plainLimit(commenceMs: Long, now: Long, source: String?): Long =
        if (Freshness.slowFeed(source)) Freshness.sgoMaxAgeMs else if (commenceMs - now > Freshness.FAR_OFF_MS) Freshness.FAR_OFF_AGE_MS else Freshness.MAX_QUOTE_AGE_MS


    /** The books PropLine ingests from their own push feeds (its docs: "treat the five push books as a real-time feed and the polled books as a 30-90s one"; Kalshi and Polymarket have feeds of their own here). */
    private val PUSH_BOOKS = setOf("draftkings", "fanduel", "fanatics")

    /**
     * PX9 (Tj 2026-10-25: "why are the odds this stale if they are streamed"): where PropLine and ParlayAPI both quote one book at one line, whether they say the same price, by how much fresher ParlayAPI's quote is
     * (its polls and stream confirm a book within ~30 s), and whether PropLine ingests the book by push or polls it. A PropLine quote that is minutes older than ParlayAPI's yet always agrees is current, whatever
     * its stamp says; one that disagrees is stale. Counted in [QuoteCensus] (`crossSame` / `crossDiff`, "push|polled/how much older").
     */
    private const val PINNACLE = "pinnacle"

    /**
     * PX10d (Tj 2026-10-25: "Use the best source of any public source, propline, or parlayapi for the best Pinnacle freshest odds and use them in combination if needed for coverage of markets"): per line
     * Pinnacle prices, which feed's Pinnacle quote was used (`pinnWin`, `none` = every copy too old), which feeds quoted it (`pinnFeeds`: one alone is coverage only that feed gives) and how old the used one
     * was (`pinnAge`). The engine logs it with the quote census.
     */
    private fun pinnacleCensus(atLine: List<com.tjshea.vigilant.data.reference.RefBookMarket>, chosen: String?, now: Long, sport: String = "") {
        if (atLine.isEmpty()) return
        QuoteCensus.add("pinnWin", chosen ?: "none")
        val feeds = atLine.mapNotNull { it.source }.distinct().sorted()
        QuoteCensus.add("pinnFeeds", feeds.joinToString("+"))
        // PX13c (Tj 2026-10-26: "I will soon not have the sgo api ... the other apis need to fill in"): the lines whose Pinnacle comes from SportsGameOdds alone, by league and kind, so PropLine and ParlayAPI are asked for those.
        if (feeds.isNotEmpty() && feeds.all { it.startsWith("sgo") }) atLine.first().let { m -> QuoteCensus.add("pinnSgoOnly", "$sport/${m.kind}${if (m.period != 0) " p${m.period}" else ""}${m.stat?.let { " $it" } ?: ""}") }
        val age = atLine.firstOrNull { it.source == chosen }?.lastUpdateMs?.let { (now - it) / 1000 } ?: return
        QuoteCensus.add("pinnAge", "$chosen ${when { age < 30 -> "<30s"; age < 120 -> "<2m"; age < 300 -> "<5m"; else -> "5m+" }}")
    }

    private fun crossCensus(atLine: List<com.tjshea.vigilant.data.reference.RefBookMarket>, near: Boolean, label: String, now: Long) {
        if (atLine.size < 2) return
        for (g in atLine.groupBy { it.bookKey }.values) {
            if (g.size < 2) continue
            val pl = g.filter { it.source?.startsWith("propline") == true && it.lastUpdateMs != null }.minByOrNull { it.lastUpdateMs!! } ?: continue
            val pa = g.filter { it.source?.startsWith("parlay") == true && it.lastUpdateMs != null }.maxByOrNull { it.lastUpdateMs!! } ?: continue
            val sides = pl.quotes.mapNotNull { q -> pa.quotes.firstOrNull { it.side == q.side }?.let { q to it } }
            if (sides.isEmpty()) continue
            val same = sides.all { (a, b) -> kotlin.math.abs(a.decimalOdds - b.decimalOdds) < 0.0015 && (a.point == null || b.point == null || kotlin.math.abs(a.point!! - b.point!!) < 1e-9) }
            val gap = pa.lastUpdateMs!! - pl.lastUpdateMs!!
            val older = when { gap < 20_000L -> "within 20s"; gap < 60_000L -> "20-60s older"; gap < 180_000L -> "1-3 min older"; else -> "3+ min older" }
            // Near games (within 3 hours of the start) and far ones are counted apart: a far game's line rarely moves, so old stamps agree there whatever they are worth.
            val kind = "${if (pl.bookKey in PUSH_BOOKS) "push" else "polled"}/${if (near) "near" else "far"}"
            QuoteCensus.add(if (same) "crossSame" else "crossDiff", "$kind/$older")
            if (!same) {
                // Which books differ and by how much (basis points of implied chance, summed: divide by the crossDiff count).
                QuoteCensus.add("crossDiffBook", "${pl.bookKey} ${if (near) "near" else "far"}")
                val bp = sides.maxOf { (a, b) -> kotlin.math.abs(1.0 / a.decimalOdds - 1.0 / b.decimalOdds) * 10_000.0 }.toInt()
                QuoteCensus.add("crossDiffBp", kind, bp)
                // Worked examples of the big ones (a point or more of chance), near games only: the two prices and how old each is.
                if (near && bp >= 100) {
                    fun age(m: com.tjshea.vigilant.data.reference.RefBookMarket) = ((now - (m.lastUpdateMs ?: now)) / 1000L).toString() + "s"
                    QuoteCensus.sample("crossDiff", "${pl.bookKey} $label ${sides.first().first.side}: ${pl.source} ${"%.3f".format(sides.first().first.decimalOdds)}@${sides.first().first.point ?: ""} ${age(pl)} vs ${pa.source} ${"%.3f".format(sides.first().second.decimalOdds)}@${sides.first().second.point ?: ""} ${age(pa)} (${bp / 100.0} pts)")
                }
            }
        }
    }

    /** PB21b (Tj, 2026-10-25: "use them to check each other's odds accuracy"): two same-time quotes of one book this far apart in no-vig chance (first side) are not both right. */
    const val CROSS_CHECK_POINTS = 0.03

    /** Feeds that copy many books' prices (PropLine, ParlayAPI, SGO, The Odds API, SharpAPI, BoltOdds, OddsPapi): the ones [chosenMarkets] checks against each other. */
    private val AGGREGATORS = listOf("propline", "parlay", "sgo", "oddsapi", "sharpapi", "bolt", "oddspapi", "unabated")

    private fun aggregator(source: String?): Boolean = source != null && AGGREGATORS.any { source.startsWith(it) }

    private fun disagree(a: List<Double>, b: List<Double>): Boolean {
        if (a.size != b.size || a.isEmpty()) return false
        fun first(o: List<Double>): Double? { val inv = o.map { if (it > 1.0) 1.0 / it else return null }; return inv[0] / inv.sum() }
        val x = first(a) ?: return false
        val y = first(b) ?: return false
        return kotlin.math.abs(x - y) >= CROSS_CHECK_POINTS
    }

    fun probabilityFor(target: OutcomeTarget, key: LineKey, fair: FairLine): Double? = when (target) {
        is OutcomeTarget.Is -> key.sides.indexOf(target.side).takeIf { it >= 0 }?.let { fair.probabilities[it] }
    }
}
