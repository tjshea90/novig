package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigRepository
import com.tjshea.vigilant.data.reference.ReferenceOddsRepository
import com.tjshea.vigilant.engine.Consensus
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvCalculator
import com.tjshea.vigilant.engine.EvOpportunity
import com.tjshea.vigilant.engine.NovigQuote
import com.tjshea.vigilant.engine.TradeContext

/**
 * Ties a live Novig board to a devigged reference line and produces the ranked opportunity list
 * the UI shows. This is the "act like OddsJam" piece Tj asked for (2026-09-20): pull both legs,
 * devig the reference side (preferring a sharp book, else averaging — [Consensus]), compare
 * against Novig's price, net out Novig's own fee ([com.tjshea.vigilant.engine.Fees]), rank by
 * net EV.
 *
 * Market-type mapping is intentionally narrow for this first pass (moneyline/spread/total only —
 * see [novigMarketTypeToReferenceMarketKey]); player props aren't wired yet. Event matching is
 * [EventMatcher]'s simple normalized-name comparison, a known simplification (see its own doc
 * comment) — real production event reconciliation is a later problem, not a v0.1 one.
 */
class EvScanner(
    private val novigRepository: NovigRepository,
    private val referenceOddsRepository: ReferenceOddsRepository,
    private val sportKey: String,
    private val devigMethod: DevigMethod = DevigMethod.MULTIPLICATIVE,
) {

    suspend fun scan(): List<EvOpportunity> {
        val novigEvents = novigRepository.getOpenMarkets()
        val referenceEvents = referenceOddsRepository.getOddsForSport(sportKey)

        val opportunities = mutableListOf<EvOpportunity>()
        for (novigEvent in novigEvents) {
            val matchedRef = referenceEvents.firstOrNull {
                EventMatcher.matches(novigEvent.homeTeam, novigEvent.awayTeam, it.homeTeam, it.awayTeam)
            } ?: continue

            opportunities += evaluateEvent(novigEvent, matchedRef.quotesByMarket)
        }
        return opportunities.sortedByDescending { it.netEv ?: Double.NEGATIVE_INFINITY }
    }

    private fun evaluateEvent(
        novigEvent: NovigEvent,
        quotesByMarket: Map<String, List<com.tjshea.vigilant.engine.BookQuote>>,
    ): List<EvOpportunity> {
        val results = mutableListOf<EvOpportunity>()

        for (market in novigEvent.markets) {
            val marketKey = novigMarketTypeToReferenceMarketKey(market.marketType) ?: continue
            val refQuotes = quotesByMarket[marketKey]
            if (refQuotes.isNullOrEmpty()) continue

            val reference = try {
                Consensus.computeReferenceProbabilities(refQuotes, devigMethod)
            } catch (e: IllegalArgumentException) {
                continue // e.g. outcome-count mismatch between providers — skip, don't crash the scan
            }
            if (reference.fairProbabilityByOutcome.size != market.outcomes.size) continue

            val context = if (novigEvent.isLive) TradeContext.LIVE_STRAIGHT else TradeContext.PREGAME_STRAIGHT
            market.outcomes.forEachIndexed { index, outcome ->
                val novigQuote = NovigQuote(price = outcome.price, isMaker = false, context = context)
                results += EvCalculator.evaluate(
                    eventName = "${novigEvent.awayTeam} @ ${novigEvent.homeTeam}",
                    marketDescription = market.description,
                    outcomeLabel = outcome.label,
                    outcomeIndex = index,
                    novig = novigQuote,
                    reference = reference,
                )
            }
        }
        return results
    }

    companion object {
        fun novigMarketTypeToReferenceMarketKey(marketType: String): String? = when (marketType) {
            "MONEY" -> "h2h"
            "SPREAD" -> "spreads"
            "TOTAL" -> "totals"
            else -> null
        }
    }
}
