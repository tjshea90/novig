package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.boltodds.BoltLiveConsensus
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.League
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Pinnodds socket's `live` frames, built from Vigilant's own lines, for a live feed that is not the socket ([com.tjshea.vigilant.data.sharpapi.SharpApiLiveFeed],
 * [com.tjshea.vigilant.data.parlay.ParlayLiveFeed]): the runner, the matcher, the 5-second rule and every guard read them exactly as the socket's. Full-game moneyline, spread and total only.
 */
object PinnFrames {
    /** Pinnodds' market key of a line, null for a kind the live lane does not trade. */
    fun keyOf(m: RefBookMarket): String? = when (m.kind) {
        LineKind.MONEYLINE -> "s;0;m"
        LineKind.SPREAD -> m.line?.let { "s;0;s;$it" }
        LineKind.TOTAL -> m.line?.let { "s;0;ou;$it" }
        else -> null
    }

    private fun typeOf(k: String) = when {
        k.endsWith(";m") -> "moneyline"
        ";s;" in k -> "spread"
        else -> "total"
    }

    /** One game's frame; empty when there is nothing to say (no game details and no market). */
    fun frame(topic: String, id: Long, league: League?, home: String?, away: String?, startMs: Long?, markets: List<JsonObject>): JsonObject {
        if (league == null && markets.isEmpty()) return JsonObject(emptyMap())
        return buildJsonObject {
            put("type", "live")
            put("sport_id", league?.pinnacleSportId ?: 0)
            put("topic", topic)
            put("rec", buildJsonObject {
                put("id", id)
                put("isLive", true)
                put("units", if (league?.tennis == true) "Sets" else "Regular")
                if (league != null && home != null && away != null) {
                    startMs?.takeIf { it > 0 }?.let { put("startTime", java.time.Instant.ofEpochMilli(it).toString()) }
                    put("league", buildJsonObject { put("name", league.novigName) })
                    put("participants", buildJsonArray {
                        add(buildJsonObject { put("name", home); put("alignment", "home") })
                        add(buildJsonObject { put("name", away); put("alignment", "away") })
                    })
                }
                put("markets", JsonArray(markets))
            })
        }
    }

    fun closed(key: String, v: Long): JsonObject = buildJsonObject {
        put("key", key); put("status", "closed"); put("type", typeOf(key)); put("period", 0); put("version", v)
    }

    fun market(key: String, m: RefBookMarket, v: Long, limit: Double = LIMIT): JsonObject = buildJsonObject {
        put("key", key)
        put("status", "open")
        put("type", typeOf(key))
        put("period", 0)
        put("version", v)
        put("isAlternate", false)
        put("limits", buildJsonArray { add(buildJsonObject { put("amount", limit) }) })
        put("prices", buildJsonArray {
            for (q in m.quotes) add(buildJsonObject {
                put("designation", when (q.side) { Side.HOME -> "home"; Side.AWAY -> "away"; Side.OVER -> "over"; Side.UNDER -> "under" })
                put("price", BoltLiveConsensus.american(1.0 / q.decimalOdds))
                if (q.point != null) put("points", q.point) else put("points", JsonNull)
            })
        })
    }

    const val LIMIT = 100_000.0
}
