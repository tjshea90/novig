package com.tjshea.vigilant.engine

/** One book's quoted decimal odds for every outcome of a single market, in a fixed outcome order. */
data class BookQuote(
    val bookName: String,
    val decimalOddsByOutcome: List<Double>,
)

/** The devigged fair probability per outcome, and which book(s) fed it — see [Consensus]. */
data class ReferenceProbabilities(
    val fairProbabilityByOutcome: List<Double>,
    val method: DevigMethod,
    val booksUsed: List<String>,
)

/**
 * Which Novig fee schedule applies (RESEARCH.md §3). PARLAY is included but not yet priced —
 * see [Fees.estimate].
 */
enum class TradeContext {
    PREGAME_STRAIGHT,
    LIVE_STRAIGHT,
    PARLAY,
}

/**
 * A single Novig outcome price to evaluate. `price` is Novig's own decimal-probability quote
 * (RESEARCH.md §2 — stake per $1.00 payout, in (0,1)), not American/decimal odds.
 */
data class NovigQuote(
    val price: Double,
    val isMaker: Boolean,
    val context: TradeContext,
)

/** The result of a Novig fee lookup — deliberately not defaulted to zero when unpriced. */
sealed interface FeeResult {
    data class Known(val amountPerDollarStaked: Double) : FeeResult
    data class Unknown(val reason: String) : FeeResult
}

/**
 * One evaluated Novig price against a fair-probability reference. `netEv` is null exactly when
 * [fee] is [FeeResult.Unknown] — never silently treated as zero (see [EvCalculator]).
 */
data class EvOpportunity(
    val eventName: String,
    val marketDescription: String,
    val outcomeLabel: String,
    val novigPrice: Double,
    val fairProbability: Double,
    val devigMethod: DevigMethod,
    val referenceBooks: List<String>,
    val rawEdge: Double,
    val evPer1: Double,
    val roi: Double,
    val fee: FeeResult,
    val netEv: Double?,
) {
    /** True only when net EV is both known and positive — the thing the UI should actually flag. */
    val isPositiveEv: Boolean get() = (netEv ?: Double.NEGATIVE_INFINITY) > 0.0
}
