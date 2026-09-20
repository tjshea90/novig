package com.tjshea.vigilant.engine

/**
 * Ties [Consensus]'s fair-probability reference to a live Novig price and produces the EV
 * numbers a scanner would sort/filter on. Formulas: RESEARCH.md §6.
 *
 * Note `rawEdge` and `evPer1` are numerically identical here (both `fairProbability - novigPrice`)
 * because Novig's contracts always pay exactly $1 (RESEARCH.md §2) — they're kept as separate
 * fields for clarity and because they'd diverge for any non-$1-payout instrument.
 */
object EvCalculator {

    fun evaluate(
        eventName: String,
        marketDescription: String,
        outcomeLabel: String,
        outcomeIndex: Int,
        novig: NovigQuote,
        reference: ReferenceProbabilities,
    ): EvOpportunity {
        require(outcomeIndex in reference.fairProbabilityByOutcome.indices) {
            "outcomeIndex $outcomeIndex out of range for reference with " +
                "${reference.fairProbabilityByOutcome.size} outcomes"
        }

        val fairProbability = reference.fairProbabilityByOutcome[outcomeIndex]
        val rawEdge = fairProbability - novig.price
        val evPer1 = fairProbability * 1.0 - novig.price
        val roi = evPer1 / novig.price
        val fee = Fees.estimate(novig)
        val netEv = (fee as? FeeResult.Known)?.let { evPer1 - it.amountPerDollarStaked }

        return EvOpportunity(
            eventName = eventName,
            marketDescription = marketDescription,
            outcomeLabel = outcomeLabel,
            novigPrice = novig.price,
            fairProbability = fairProbability,
            devigMethod = reference.method,
            referenceBooks = reference.booksUsed,
            rawEdge = rawEdge,
            evPer1 = evPer1,
            roi = roi,
            fee = fee,
            netEv = netEv,
        )
    }
}
