package com.tjshea.vigilant.data.pinnodds

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json

/** Pinnodds frames for tests: small hand-made ones in the shape of the real feed (docs https://pinnodds.com/llms-full.txt) and the real ones captured 2026-10-08 (pinnodds-frames.jsonl). */
internal object PinnTestFrames {
    fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    fun money(version: Long, home: Int, away: Int, status: String = "open", limit: Int = 2000, draw: Int? = null) =
        """{"matchupId":1,"version":$version,"key":"s;0;m","period":0,"status":"$status","type":"moneyline","isAlternate":false,"prices":[{"designation":"home","price":$home},{"designation":"away","price":$away}${if (draw != null) ""","{"designation":"draw","price":$draw}""" else ""}],"limits":[{"type":"maxRiskStake","amount":$limit}]}"""

    fun spread(version: Long, points: Double, home: Int, away: Int, alt: Boolean = false, status: String = "open") =
        """{"matchupId":1,"version":$version,"key":"s;0;s;$points","period":0,"status":"$status","type":"spread","isAlternate":$alt,"prices":[{"designation":"home","price":$home,"points":$points},{"designation":"away","price":$away,"points":${-points}}],"limits":[{"type":"maxRiskStake","amount":1000}]}"""

    fun total(version: Long, points: Double, over: Int, under: Int, alt: Boolean = false) =
        """{"matchupId":1,"version":$version,"key":"s;0;ou;$points","period":0,"status":"open","type":"total","isAlternate":$alt,"prices":[{"designation":"over","price":$over,"points":$points},{"designation":"under","price":$under,"points":$points}],"limits":[{"type":"maxRiskStake","amount":1000}]}"""

    /** A `live` envelope for matchup [id]: [channel] is the topic's last segment (ld, dz, both, pre). */
    fun live(
        id: Long = 1, channel: String = "ld", isLive: Boolean = true, home: String = "Oklahoma City Thunder", away: String = "Milwaukee Bucks", start: String = "2026-10-07T23:25:00Z",
        score: Pair<Int, Int>? = null, periods: String = """[{"period":0,"status":"open"}]""", units: String = "Regular", op: String = "upd", vararg markets: String,
    ): String {
        val homeState = score?.let { ""","state":{"score":${it.first}}""" }.orEmpty()
        val awayState = score?.let { ""","state":{"score":${it.second}}""" }.orEmpty()
        return """{"type":"live","sport_id":3,"topic":"matchups/reg/sp/4/${if (isLive) "live/" else ""}$channel","op":"$op","ts":1,"rec":{"id":$id,"parentId":${id + 1000},"type":"matchup","status":"started","startTime":"$start","isLive":$isLive,"units":"$units","league":{"id":1,"name":"NBA Preseason"},"participants":[{"name":"$home","alignment":"home"$homeState},{"name":"$away","alignment":"away"$awayState}],"periods":$periods,"markets":[${markets.joinToString(",")}]}}"""
    }

    /** The real frames captured from the feed: name -> frame (see the file). */
    fun real(): Map<String, JsonObject> = javaClass.getResourceAsStream("/pinnodds-frames.jsonl")!!.bufferedReader().readLines().filter { it.isNotBlank() }.associate { line ->
        val o = Json.parseToJsonElement(line).jsonObject
        (o["name"] as kotlinx.serialization.json.JsonPrimitive).content to (o["frame"] as JsonObject)
    }
}
