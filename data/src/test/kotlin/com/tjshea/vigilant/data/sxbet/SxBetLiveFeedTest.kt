package com.tjshea.vigilant.data.sxbet

import com.tjshea.vigilant.data.reference.SxBetClient
import com.tjshea.vigilant.data.reference.SxBetConvert
import com.tjshea.vigilant.data.scanner.Leagues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TCL118: the feed turns SX's pushes (the shapes of the engine's real `sxlive` journal) into Pinnodds frames, and closes lines when a score moves until the exchange re-quotes. */
class SxBetLiveFeedTest {
    private val json = Json { ignoreUnknownKeys = true }
    private var now = 10_000_000L
    private val http = OkHttpClient()
    private val scope = CoroutineScope(Job())
    private val stream = SxBetStream(http, json, { "k" }, scope, { now })
    private val client = SxBetClient(http, json, { "k" }, clock = { now })
    private val frames = ArrayList<JsonObject>()
    private val feed = SxBetLiveFeed(stream, client, ensureOpen = {}, sink = { f, _ -> frames += json.parseToJsonElement(f).jsonObject }, scope = scope, clock = { now })

    private fun market(hash: String, type: Int, one: String, two: String, line: Double? = null) =
        SxBetConvert.Market(marketHash = hash, status = "ACTIVE", outcomeOneName = one, outcomeTwoName = two, teamOneName = "Philadelphia Eagles", teamTwoName = "Dallas Cowboys", type = type, gameTime = 1_700_000_000L,
            line = line, sportXeventId = "L9", sportId = 8, leagueId = 243, leagueLabel = "NFL", mainLine = true)

    /** The maker-frame push SX sends for a market whose TAKER asks are [a1] and [a2] with $200 of depth each. */
    private fun push(hash: String, a1: Double, a2: Double) {
        fun level(maker: Double) = """{"percentageOdds":"${java.math.BigDecimal(maker).multiply(java.math.BigDecimal("1e20")).toBigInteger()}","size":"${(200_000_000.0 * maker / (1.0 - maker)).toLong()}"}"""
        stream.apply("best_odds_v3:global", json.parseToJsonElement("""{"marketHash":"$hash","outcomeOne":${level(1.0 - a2)},"outcomeTwo":${level(1.0 - a1)}}"""))
    }

    private fun score(one: Int, two: Int) = stream.apply("fixtures:live_scores", json.parseToJsonElement("""{"teamOneScore":$one,"teamTwoScore":$two,"sportXeventId":"L9","currentPeriod":"2nd Quarter","periodTime":"300","sportId":8,"leagueId":243,"periods":[{"label":"1st Quarter","isFinished":true,"teamOneScore":"7","teamTwoScore":"0"},{"label":"2nd Quarter","isFinished":false,"teamOneScore":"0","teamTwoScore":"3"}]}"""))

    private fun setUp() {
        feed.adopt(
            SxLiveBuild.EventInfo("L9", Leagues.byNovigName("NFL")!!, "Philadelphia Eagles", "Dallas Cowboys", 1_700_000_000_000L,
                listOf(market("ml", 226, "Philadelphia Eagles", "Dallas Cowboys"), market("tot", 28, "Over 44.5", "Under 44.5", line = 44.5))),
        )
        stream.apply("fixtures:global", json.parseToJsonElement("""{"sportXeventId":"L9","status":2}"""))
        score(7, 3)
        now += 10
        push("ml", 0.62, 0.40); push("tot", 0.50, 0.52)
    }

    @Test
    fun `a live game's first look sends the game and its lines in Pinnodds frames`() {
        setUp(); now += 50
        feed.update(null)
        val rec = frames.last()["rec"]!!.jsonObject
        assertEquals("live", frames.last()["type"]!!.jsonPrimitive.content)
        assertEquals(5, frames.last()["sport_id"]!!.jsonPrimitive.content.toInt())
        val parts = rec["participants"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["alignment"]!!.jsonPrimitive.content }
        assertEquals(listOf("Dallas Cowboys" to "home", "Philadelphia Eagles" to "away"), parts)
        val keys = rec["markets"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content }
        assertEquals(setOf("s;0;m", "s;0;ou;44.5"), keys.toSet())
        assertEquals(1, feed.liveGames)
        assertEquals(2, feed.linesLive)
    }

    @Test
    fun `a score change closes the lines until the exchange quotes again`() {
        setUp(); now += 50
        feed.update(null)
        frames.clear()
        now += 1_000; score(14, 3)
        now += 200
        feed.update(null)
        val closed = frames.flatMap { it["rec"]!!.jsonObject["markets"]!!.jsonArray }.map { it.jsonObject }.filter { it["status"]!!.jsonPrimitive.content == "closed" }
        assertEquals("both lines closed the moment the score moved", 2, closed.size)
        assertEquals(0, feed.linesLive)
        frames.clear()
        now += 300; push("ml", 0.50, 0.52); push("tot", 0.49, 0.53)
        now += 50
        feed.update(null)
        val open = frames.flatMap { it["rec"]!!.jsonObject["markets"]!!.jsonArray }.map { it.jsonObject }.filter { it["status"]!!.jsonPrimitive.content == "open" }
        assertEquals("a push after the score reopens them at the new price", 2, open.size)
        assertEquals(2, feed.linesLive)
    }

    @Test
    fun `a game that finishes is closed and then forgotten`() {
        setUp(); now += 50
        feed.update(null)
        frames.clear()
        stream.apply("fixtures:global", json.parseToJsonElement("""{"sportXeventId":"L9","status":3}"""))
        now += 100
        feed.update(null)
        assertTrue(frames.any { f -> f["rec"]!!.jsonObject["markets"]!!.jsonArray.any { it.jsonObject["status"]!!.jsonPrimitive.content == "closed" } })
        frames.clear()
        now += SxBetLiveFeed.FORGET_MS + 1_000
        feed.update(null)
        assertTrue("then a delete frame", frames.any { it["op"]?.jsonPrimitive?.content == "del" })
        assertFalse(feed.liveGames > 0)
    }

    @Test
    fun `the live list is what SX itself flags in play`() {
        setUp()
        assertEquals(setOf("L9"), feed.liveIds(now))
        stream.apply("fixtures:global", json.parseToJsonElement("""{"sportXeventId":"L9","status":3}"""))
        assertTrue("finished leaves the list", feed.liveIds(now).isEmpty())
    }
}
