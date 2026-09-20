package com.tjshea.vigilant.data.novig

import kotlinx.serialization.Serializable

/**
 * Mirrors Novig's own documented data model (RESEARCH.md §4.1): Events contain Markets, and
 * every Market has exactly two mutually exclusive, exhaustive Outcomes. Prices are decimal
 * probabilities in (0,1) — see RESEARCH.md §2 — not American/decimal odds.
 *
 * This is a deliberately small slice of what Novig's real API returns (full field-level schemas
 * are still an open item — RESEARCH.md §10 item 5) — enough to drive the scanner, not a full
 * mirror of every field Novig exposes.
 */
@Serializable
data class NovigOutcome(
    val outcomeId: String,
    val label: String,
    val price: Double,
)

@Serializable
data class NovigMarket(
    val marketId: String,
    /** e.g. MONEY, SPREAD, TOTAL — see RESEARCH.md §4.1's "Data Model" note. */
    val marketType: String,
    val description: String,
    val outcomes: List<NovigOutcome>,
) {
    init {
        require(outcomes.size == 2) {
            "Every Novig market has exactly two outcomes (RESEARCH.md §4.1); got ${outcomes.size} for $marketId"
        }
    }
}

@Serializable
data class NovigEvent(
    val eventId: String,
    val homeTeam: String,
    val awayTeam: String,
    val startTimeIso: String,
    val isLive: Boolean,
    val markets: List<NovigMarket>,
)
