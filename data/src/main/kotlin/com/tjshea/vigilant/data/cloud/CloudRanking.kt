package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.SourceScore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * What each API was last measured to deliver ([SourceScore.Measure]: the 90th-percentile age of its quotes by the books' own stamps, its distance from Pinnacle), kept across the engine's 6-hour handovers
 * (Tj, 2026-10-15: "I want the GitHub engine to still rank apis by measured quality and freshness"). A job ranks the APIs from what its own scans measured; a new job used to start from the shipped order and
 * spend its first scan auditioning every feed. The engine writes this file next to the list (`ranking.json` on `cloud-feed`) and the next job reads it back before its first scan. A measurement older than
 * [KEEP_MS] is left out (a feed's speed changes; [SourceScore.AUDITION_MS] asks a feed again after 6 hours anyway).
 */
object CloudRanking {
    const val KEEP_MS = 24 * 3_600_000L

    fun encode(measures: Map<String, SourceScore.Measure>): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("v", 1)
            put("measures", JsonArray(measures.values.sortedBy { it.id }.map { m ->
                buildJsonObject {
                    put("id", m.id); put("q", m.quotes); put("at", m.atMs); put("al", m.agreementLines)
                    m.p90AgeMs?.let { put("p90", it) }
                    m.agreementPts?.let { put("pts", it) }
                }
            }))
        },
    )

    /** The measurements in [text] that are no older than [KEEP_MS] at [now]; empty for anything unreadable (the shipped order stands). */
    fun decode(text: String, now: Long): Map<String, SourceScore.Measure> = runCatching {
        Json.parseToJsonElement(text).jsonObject["measures"]!!.jsonArray.mapNotNull { e ->
            runCatching {
                val o = e.jsonObject
                SourceScore.Measure(
                    id = o["id"]!!.jsonPrimitive.content, quotes = o["q"]!!.jsonPrimitive.int, p90AgeMs = o["p90"]?.jsonPrimitive?.longOrNull,
                    agreementPts = o["pts"]?.jsonPrimitive?.doubleOrNull, agreementLines = o["al"]?.jsonPrimitive?.int ?: 0, atMs = o["at"]!!.jsonPrimitive.long,
                )
            }.getOrNull()?.takeIf { now - it.atMs <= KEEP_MS }
        }.associateBy { it.id }
    }.getOrDefault(emptyMap())
}
