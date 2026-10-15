package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.engine.EvMath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The results of the engine's own orders (TCL20e). The daily grader grades every bet the engine's scan study logged; an order of [CloudTrader] is the same Novig outcome, so its result is that bet's result and its
 * profit follows from what the order really cost: a win pays 1¢ a contract, a loss pays nothing, a push or a void gives the cost back, a fair-market settlement pays its value per contract. Pure.
 */
object TradeSettle {
    /** What the grader found for one Novig outcome: its status and, for a fair-market settlement, the value per $1 contract. */
    data class Outcome(val status: BetStatus, val settleValue: Double? = null)

    @Serializable
    data class Settled(val id: String, val status: String, val pnl: Double)

    @Serializable
    data class File(val atMs: Long, val settled: List<Settled>) {
        companion object {
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            fun parse(text: String): File? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
            fun text(f: File): String = json.encodeToString(serializer(), f)
        }
    }

    /**
     * The settlements of every entry whose game has a result, given [outcome] for a (market, outcome) pair; entries with no result yet are left out. An entry the engine has already marked is settled again to the same
     * answer: the file is a standing list, so an engine that was off when it was written still finds every result on its next start.
     */
    fun settle(entries: List<TradeEntry>, outcome: (marketId: String, outcomeId: String) -> Outcome?): List<Settled> = entries.mapNotNull { e ->
        val o = outcome(e.marketId, e.outcomeId) ?: return@mapNotNull null
        when (o.status) {
            BetStatus.WON -> Settled(e.id, "WON", round(e.payout - e.cost))
            BetStatus.LOST -> Settled(e.id, "LOST", round(-e.cost))
            BetStatus.PUSH, BetStatus.VOID -> Settled(e.id, o.status.name, 0.0)
            BetStatus.FMV -> o.settleValue?.let { v -> Settled(e.id, "FMV", round(e.contracts * EvMath.CONTRACT_PAYOUT_DOLLARS * v - e.cost)) }
            BetStatus.PENDING -> null
        }
    }

    private fun round(v: Double) = Math.round(v * 100.0) / 100.0
}
