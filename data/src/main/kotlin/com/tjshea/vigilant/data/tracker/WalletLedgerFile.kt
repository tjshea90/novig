package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The Vigilant wallet's whole Novig ledger as one append-only file on GitHub (`wallet-ledger.json` on `cloud-feed`, DG5, Tj 2026-11-16: "rebuild the entire tracker system to use novig api wallet
 * data ... GitHub and Novig supply the tracker info"). The engine reads Novig's `/transactions` newest page first, stops at the first page it already holds, and carries the file job to job; the phone only
 * reads it. Rows are kept by transaction id, never dropped.
 */
object WalletLedgerFile {
    /** [complete]: every page back to the first row was read once (a file from the first version of this reader stopped at its newest pages: not complete). */
    data class Book(val rows: List<LedgerRow>, val atMs: Long, val fullAtMs: Long = 0, val complete: Boolean = false)

    private val json = Json { ignoreUnknownKeys = true }

    /** Known rows plus [fresh], one per transaction id, oldest first. */
    fun merge(known: List<LedgerRow>, fresh: List<LedgerRow>): List<LedgerRow> =
        (known + fresh).distinctBy { it.transactionId }.sortedWith(compareBy({ it.ts }, { it.transactionId }))

    fun encode(b: Book): String = buildJsonObject {
        put("v", 2); put("atMs", b.atMs); put("fullAtMs", b.fullAtMs); put("complete", b.complete)
        put("rows", buildJsonArray {
            b.rows.forEach { r ->
                add(buildJsonArray { add(JsonPrimitive(r.transactionId)); add(JsonPrimitive(r.kind)); add(JsonPrimitive(r.amount)); add(JsonPrimitive(r.ref ?: "")); add(JsonPrimitive(r.ts)) })
            }
        })
    }.toString()

    fun decode(text: String): Book? = runCatching {
        val o = json.parseToJsonElement(text) as JsonObject
        val rows = (o["rows"] as JsonArray).mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            val amount = (a[2] as JsonPrimitive).doubleOrNull ?: return@mapNotNull null
            LedgerRow((a[0] as JsonPrimitive).content, (a[1] as JsonPrimitive).content, amount, (a[3] as JsonPrimitive).contentOrNull?.takeIf { it.isNotEmpty() }, (a[4] as JsonPrimitive).longOrNull ?: 0L)
        }
        Book(rows, (o["atMs"] as? JsonPrimitive)?.longOrNull ?: 0L, (o["fullAtMs"] as? JsonPrimitive)?.longOrNull ?: 0L, (o["complete"] as? JsonPrimitive)?.content == "true")
    }.getOrNull()

    // ---- fills (wallet-fills.json): the fill id -> market link the ledger's FILL and FEE rows lack ----
    data class FillBook(val fills: List<com.tjshea.vigilant.data.novig.trading.NovigFill>, val atMs: Long, val complete: Boolean = false)

    fun mergeFills(known: List<com.tjshea.vigilant.data.novig.trading.NovigFill>, fresh: List<com.tjshea.vigilant.data.novig.trading.NovigFill>) =
        (known + fresh).distinctBy { it.fillId }.sortedWith(compareBy({ it.ts }, { it.fillId }))

    fun encodeFills(b: FillBook): String = buildJsonObject {
        put("v", 2); put("atMs", b.atMs); put("complete", b.complete)
        put("fills", buildJsonArray {
            b.fills.forEach { f ->
                add(buildJsonArray {
                    add(JsonPrimitive(f.fillId)); add(JsonPrimitive(f.orderId)); add(JsonPrimitive(f.marketId)); add(JsonPrimitive(f.outcomeId))
                    add(JsonPrimitive(f.qty)); add(JsonPrimitive(f.cost)); add(JsonPrimitive(f.fee)); add(JsonPrimitive(f.ts)); add(JsonPrimitive(f.taker))
                })
            }
        })
    }.toString()

    fun decodeFills(text: String): FillBook? = runCatching {
        val o = json.parseToJsonElement(text) as JsonObject
        val fills = (o["fills"] as JsonArray).mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            fun p(i: Int) = a[i] as JsonPrimitive
            com.tjshea.vigilant.data.novig.trading.NovigFill(p(0).content, p(1).content, null, p(2).content, p(3).content, p(4).longOrNull ?: return@mapNotNull null, p(5).doubleOrNull ?: return@mapNotNull null, p(8).content == "true", p(6).doubleOrNull ?: 0.0, p(7).longOrNull ?: 0L)
        }
        FillBook(fills, (o["atMs"] as? JsonPrimitive)?.longOrNull ?: 0L, (o["complete"] as? JsonPrimitive)?.content == "true")
    }.getOrNull()
}
