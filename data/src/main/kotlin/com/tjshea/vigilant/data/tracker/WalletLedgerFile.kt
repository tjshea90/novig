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
    data class Book(val rows: List<LedgerRow>, val atMs: Long, val fullAtMs: Long = 0)

    private val json = Json { ignoreUnknownKeys = true }

    /** Known rows plus [fresh], one per transaction id, oldest first. */
    fun merge(known: List<LedgerRow>, fresh: List<LedgerRow>): List<LedgerRow> =
        (known + fresh).distinctBy { it.transactionId }.sortedWith(compareBy({ it.ts }, { it.transactionId }))

    fun encode(b: Book): String = buildJsonObject {
        put("v", 1); put("atMs", b.atMs); put("fullAtMs", b.fullAtMs)
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
        Book(rows, (o["atMs"] as? JsonPrimitive)?.longOrNull ?: 0L, (o["fullAtMs"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }.getOrNull()
}
