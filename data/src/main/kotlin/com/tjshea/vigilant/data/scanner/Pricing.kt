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
import com.tjshea.vigilant.engine.FairValue
import com.tjshea.vigilant.engine.PositiveDepth
import com.tjshea.vigilant.engine.TakeLevel
import kotlin.math.abs

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
    val bookFetchedAtMs: Long?,
    /** Newest `last_update` among the books that fed the fair line. */
    val fairUpdatedMs: Long?,
    val refEvent: RefEvent?,
    val lineKey: LineKey?,
    /** Which reference side this outcome is priced from. */
    val target: OutcomeTarget?,
) {
    /** Column of this outcome in [FairLine.perBook] odds, or null for a 3-way "No" (a sum of sides). */
    val referenceIndex: Int?
        get() {
            val k = lineKey ?: return null
            return when (val t = target) {
                is OutcomeTarget.Is -> k.sides.indexOf(t.side).takeIf { it >= 0 }
                is OutcomeTarget.Yes -> k.sides.indexOf(t.side).takeIf { it >= 0 }
                else -> null
            }
        }

    val key: String get() = "${market.marketId}/${outcome.outcomeId}"
    val evPercent: Double? get() = quote?.evPercent
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
)

data class ScanResult(
    val games: List<PricedGame>,
    val opportunities: List<Opportunity>,
    val stats: ScanStats,
    val computedAtMs: Long,
) {
    /** The +EV feed: at or above the user's threshold, below the too-good-to-be-true cap, best first. */
    fun feed(settings: ScanSettings): List<Opportunity> = opportunities
        .filter { o ->
            val ev = o.evPercent ?: return@filter false
            ev >= settings.minEvPercent && ev <= settings.maxEvPercent &&
                (settings.includeLive || !o.isLive) &&
                MarketFamily.entries.any { it in settings.families && o.market.marketType in it.novigTypes }
        }
        .sortedByDescending { it.evPercent }
}

/**
 * Pure pricing: plan + books + settings in, priced outcomes out. No network, so changing a
 * setting (fair source, devig method, Kelly) re-prices instantly from what's already fetched.
 */
object Pricing {

    fun price(plan: Plan, books: Map<String, NovigBook>, settings: ScanSettings, now: Long): ScanResult {
        val fairSettings = settings.fairSettings()
        val fairCache = HashMap<LineKey, FairLine?>()
        val refById = plan.markets.mapNotNull { it.refEvent }.associateBy { it.id }

        fun fairFor(key: LineKey): FairLine? = fairCache.getOrPut(key) {
            val ref = refById[key.refEventId] ?: return@getOrPut null
            FairValue.compute(bookPrices(ref, key), fairSettings)
        }

        val all = ArrayList<Opportunity>()
        for (pm in plan.markets) {
            val fee = pm.market.fee ?: continue
            val book = books[pm.market.marketId]
            val fair = pm.lineKey?.let(::fairFor)
            val live = pm.event.isLive
            val ladders = pm.outcomes.associate { it.outcome.outcomeId to (book?.takeLadder(pm.market, it.outcome.outcomeId).orEmpty()) }
            val bestTakes = ladders.values.mapNotNull { it.firstOrNull()?.price }
            val width = if (bestTakes.size == 2) bestTakes.sum() - 1.0 else null

            for (po in pm.outcomes) {
                val ladder = ladders[po.outcome.outcomeId].orEmpty()
                val p = fair?.let { probabilityFor(po.target, pm.lineKey!!, it) }
                val quote = if (p != null && ladder.isNotEmpty()) EvMath.quote(p, ladder.first().price, fee, live) else null
                val depth = if (p != null && ladder.isNotEmpty()) EvMath.positiveDepth(ladder, p, fee, live) else null
                val stake = if (quote != null && quote.evPercent > 0) {
                    EvMath.suggestedStake(quote, settings.bankroll, settings.kellyMultiplier, depth?.dollarCost)
                } else {
                    null
                }
                all += Opportunity(
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
                    bookFetchedAtMs = book?.fetchedAtMs,
                    fairUpdatedMs = fair?.perBook?.filter { it.book.bookTitle in fair.booksUsed }?.mapNotNull { it.book.lastUpdateMs }?.maxOrNull(),
                    refEvent = pm.refEvent,
                    lineKey = pm.lineKey,
                    target = po.target,
                )
            }
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
            ),
            computedAtMs = now,
        )
    }

    /** Every book's odds for one line, in [LineKey.sides] order; books missing a side are dropped. */
    fun bookPrices(ref: RefEvent, key: LineKey): List<BookPrices> =
        ref.markets
            .filter { m ->
                m.kind == key.kind &&
                    (m.quotes.any { it.side == Side.DRAW }) == key.threeWay &&
                    (key.line == null || (m.line != null && abs(m.line!! - key.line) < 1e-9))
            }
            .mapNotNull { m ->
                val odds = key.sides.map { side -> m.quotes.firstOrNull { it.side == side }?.decimalOdds ?: return@mapNotNull null }
                BookPrices(m.bookKey, m.bookTitle, odds, m.lastUpdateMs)
            }
            .distinctBy { it.bookKey }

    fun probabilityFor(target: OutcomeTarget, key: LineKey, fair: FairLine): Double? {
        fun p(side: Side): Double? = key.sides.indexOf(side).takeIf { it >= 0 }?.let { fair.probabilities[it] }
        return when (target) {
            is OutcomeTarget.Is -> p(target.side)
            is OutcomeTarget.Yes -> p(target.side)
            // "No" = every other result. Summing the other sides (rather than 1 − p) stays
            // conservative under WORST_CASE devig, where the sides don't sum to 1.
            is OutcomeTarget.No -> key.sides.filter { it != target.side }.mapNotNull { p(it) }.takeIf { it.size == key.sides.size - 1 }?.sum()
        }
    }
}
