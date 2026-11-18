package com.tjshea.vigilant.data.rfq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The parlay maker's own fills and how they settled (`GET /rfq/v3/executions`, docs api-reference/rfq/executions), read so the loss stop has the REAL result of every quote (MKV, Tj 2026-11-18: LIVE at
 * small limits). One item: `price` (the user's win probability), `cost` (the maker's collateral), `userCost` (the user's stake), `status` open|settled, `result` win|loss|push|fmv, `fmvValue`.
 */
object RfqLedger {
    data class Exec(val rfqId: String, val price: Double, val cost: Double, val userCost: Double, val settled: Boolean, val result: String?, val fmv: Double?, val ts: Long, val settledTs: Long?)

    private val json = Json { ignoreUnknownKeys = true }

    /** The items of one page of the listing; an answer that is not the documented object reads as none. */
    fun parse(text: String): List<Exec> {
        val items = ((runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("items") as? JsonArray) ?: return emptyList()
        return items.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun d(k: String) = (o[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
            Exec(
                rfqId = (o["rfqId"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null, price = d("price") ?: return@mapNotNull null, cost = d("cost") ?: return@mapNotNull null,
                userCost = d("userCost") ?: return@mapNotNull null, settled = (o["status"] as? JsonPrimitive)?.contentOrNull == "settled", result = (o["result"] as? JsonPrimitive)?.contentOrNull,
                fmv = d("fmvValue"), ts = (o["ts"] as? JsonPrimitive)?.longOrNull ?: 0L, settledTs = (o["settledTs"] as? JsonPrimitive)?.longOrNull,
            )
        }
    }

    /**
     * The maker's profit on one settled parlay, dollars: the pot is the stake plus the collateral, the user gets the pot times the scalar (win 1, loss 0, fmv its value) and the maker keeps the rest, less the
     * collateral it put up. A push (a void) returns both sides' money: 0. Null while open or when the result is not one of the documented four.
     */
    fun pnl(e: Exec): Double? {
        if (!e.settled) return null
        val scalar = when (e.result) { "win" -> 1.0; "loss" -> 0.0; "fmv" -> e.fmv ?: return null; "push" -> return 0.0; else -> return null }
        return (e.userCost + e.cost) * (1.0 - scalar) - e.cost
    }

    data class Summary(val settled: Int, val makerWon: Int, val makerLost: Int, val open: Int, val openCollateral: Double, val pnlTotal: Double, val pnl24h: Double)

    fun summarize(execs: List<Exec>, now: Long): Summary {
        var won = 0; var lost = 0; var open = 0; var openCol = 0.0; var total = 0.0; var day = 0.0
        for (e in execs) {
            val p = pnl(e)
            if (p == null) { if (!e.settled) { open++; openCol += e.cost }; continue }
            total += p; if (p > 0.005) won++ else if (p < -0.005) lost++
            if (now - (e.settledTs ?: e.ts) <= 24 * 3_600_000L) day += p
        }
        return Summary(execs.count { it.settled }, won, lost, open, openCol, total, day)
    }
}
