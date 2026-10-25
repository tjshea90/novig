package com.tjshea.vigilant.data.parlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PB17b (2026-10-25): ParlayAPI's held Pinnacle prices of a game under way become the Pinnodds socket's frames for the live lane; a line ParlayAPI stops confirming is closed. */
class ParlayLiveFeedTest {
    private val start = 1_792_940_000_000L
    private var now = start + 30 * 60_000L
    private val live = ParlayLive { now }
    private val frames = ArrayList<JsonObject>()
    private val feed = ParlayLiveFeed(live, { setOf("NBA") }, { f, _ -> frames += Json.parseToJsonElement(f).jsonObject }, CoroutineScope(Dispatchers.Unconfined), { now })

    private fun row(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun heard(at: Long) {
        val base = """"home_team":"Brooklyn Nets","away_team":"Indiana Pacers","commence_time":"${java.time.Instant.ofEpochMilli(start)}","bookmaker":"pinnacle","last_update":$at"""
        live.onRow("basketball_nba", row("""{$base,"kind":"game","market_key":"h2h","home_ml":150,"away_ml":-170}"""), now)
        live.onRow("basketball_nba", row("""{$base,"kind":"prop","player":"Total","market_key":"totals","line":221.5,"over_price":-105,"under_price":-115}"""), now)
        live.onRow("basketball_nba", row("""{$base,"kind":"game","market_key":"spreads","team":"Brooklyn Nets","line":3.5,"price":-110}"""), now)
        live.onRow("basketball_nba", row("""{$base,"kind":"game","market_key":"spreads","team":"Indiana Pacers","line":-3.5,"price":-110}"""), now)
    }

    private fun keys(f: JsonObject): Map<String, String> =
        (f["rec"]!!.jsonObject["markets"] as JsonArray).associate { it.jsonObject["key"]!!.jsonPrimitive.content to it.jsonObject["status"]!!.jsonPrimitive.content }

    @Test
    fun `a game under way sends Pinnacle's moneyline, spread and total as socket frames`() {
        heard(now - 1_000)
        feed.update(now)
        val f = frames.single()
        assertEquals("live", f["type"]!!.jsonPrimitive.content)
        assertEquals("NBA", f["rec"]!!.jsonObject["league"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(mapOf("s;0;m" to "open", "s;0;ou;221.5" to "open", "s;0;s;3.5" to "open"), keys(f))
        assertEquals(1, feed.liveGames)
        // Nothing moved: the next frame names the game with no market (the runner hears it each second).
        feed.update(now + 1_000)
        assertTrue(keys(frames.last()).isEmpty())
    }

    @Test
    fun `lines ParlayAPI stopped confirming are closed, and a game not started is not sent`() {
        heard(now - 1_000)
        feed.update(now)
        now += ParlayLive.LIVE_LINE_MS + 5_000
        live.heard(now)
        feed.update(now)
        assertEquals(setOf("closed"), keys(frames.last()).values.toSet())
        frames.clear()
        now = start - 60_000
        val l2 = ParlayLive { now }
        l2.onRow("basketball_nba", row("""{"home_team":"A","away_team":"B","commence_time":"${java.time.Instant.ofEpochMilli(start)}","bookmaker":"pinnacle","last_update":$now,"kind":"game","market_key":"h2h","home_ml":100,"away_ml":-120}"""), now)
        ParlayLiveFeed(l2, { setOf("NBA") }, { f, _ -> frames += Json.parseToJsonElement(f).jsonObject }, CoroutineScope(Dispatchers.Unconfined), { now }).update(now)
        assertTrue(frames.isEmpty())
    }
}
