package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.scanner.PinnWebsiteSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Pinnacle website feed on a real answer (a live NCAA football game, trimmed): what it discovers, what frames it sends, and that the engine's own PinnBook reads them like the socket's. */
@OptIn(ExperimentalCoroutinesApi::class)
class PinnWebsiteFeedTest {
    private val fixture = Json.parseToJsonElement(javaClass.getResourceAsStream("/pinnacle-website-live.json")!!.bufferedReader().readText()).jsonObject
    private val related = fixture.getValue("related").jsonArray
    private val markets = fixture.getValue("markets").jsonArray
    private val gameId = 1637783509L

    private class Fake(val related: JsonArray, var markets: JsonArray, val listText: String) : WebsiteFetcher {
        val calls = ArrayList<String>()
        var status = 200
        var relatedOverride: ((JsonArray) -> JsonArray)? = null
        override suspend fun get(path: String): WebsiteReply {
            calls += path
            if (status != 200) return WebsiteReply(status, "")
            return when {
                path.startsWith("/sports/15/matchups") -> WebsiteReply(200, listText)
                path.startsWith("/sports/") -> WebsiteReply(200, "[]")
                path.endsWith("/related") -> WebsiteReply(200, (relatedOverride?.invoke(related) ?: related).toString())
                path.endsWith("/markets/related/straight") -> WebsiteReply(200, markets.toString())
                else -> WebsiteReply(404, "")
            }
        }
    }

    private fun listWithLiveGame(): String {
        val live = related.map { it.jsonObject }.first { (it["id"] as JsonPrimitive).longOrNull == gameId }
        val pre = JsonObject(live + mapOf("id" to JsonPrimitive(1L), "isLive" to JsonPrimitive(false)))
        val special = JsonObject(live + mapOf("id" to JsonPrimitive(2L), "units" to JsonPrimitive("Corners")))
        return JsonArray(listOf(pre, live, special)).toString()
    }

    private class Rig(val feed: PinnWebsiteFeed, val fake: Fake, val frames: MutableList<String>, val book: PinnBook)

    private fun rig(scope: kotlinx.coroutines.CoroutineScope, shadow: PinnBook? = null, fake: Fake = Fake(related, markets, listWithLiveGame())): Rig {
        val frames = ArrayList<String>()
        val feed = PinnWebsiteFeed(fake, scope, { t, _ -> frames += t }, { PinnWebsiteSettings() }, shadow, clock = { 1_800_000_000_000L })
        return Rig(feed, fake, frames, PinnBook())
    }

    @Test
    fun `discovery follows the live regular matchup only, not the prematch or the special book`() = runTest {
        val r = rig(backgroundScope)
        r.feed.discover(24)
        assertEquals(listOf(gameId), r.feed.games.keys.toList())
        assertEquals(PinnWebsiteFeed.Sport.FOOTBALL, r.feed.games.getValue(gameId).sport)
    }

    @Test
    fun `the first poll sends the game with its markets, and the engine's own book reads them like the socket's`() = runTest {
        val r = rig(backgroundScope)
        r.feed.discover(24)
        assertTrue(r.feed.pollOnce(0L))
        assertEquals(1, r.frames.size)
        for (f in r.frames) r.book.apply(Json.parseToJsonElement(f).jsonObject, 5_000L)
        val e = r.book.events.getValue(gameId)
        assertTrue(e.live)
        assertEquals("NC State", e.home)
        assertEquals("Wake Forest", e.away)
        assertEquals("NCAA", e.league)
        assertEquals(5, e.sportId)
        assertEquals(7 to 7, e.score)
        assertTrue("the clock (quarter, time left) is read from the parent", e.clock!!.contains("quarter"))
        assertTrue("the moneyline of period 1 and the alternates came through", e.lines.keys.any { it.startsWith("s;1;m") } && e.lines.isNotEmpty())
        assertTrue("a spread line has its points", e.lines.values.any { it.type == PinnLineType.SPREAD && it.points != null })
        assertTrue(r.feed.lastFrameAtMs > 0L)
    }

    @Test
    fun `an unchanged market is not sent twice, a market whose version rose is, and the score and clock ride every second poll`() = runTest {
        val r = rig(backgroundScope)
        r.feed.discover(24)
        r.feed.pollOnce(0L)
        r.frames.clear()
        r.feed.pollOnce(0L)   // odd poll: markets only, none changed
        assertTrue("nothing changed and no score read due", r.frames.isEmpty())
        r.feed.pollOnce(0L)   // even poll: the record (score, clock) again
        assertEquals(1, r.frames.size)
        assertEquals(0, Json.parseToJsonElement(r.frames[0]).jsonObject.getValue("rec").jsonObject.getValue("markets").jsonArray.size)
        r.frames.clear()
        // One market's version rises and its price moves.
        val bumped = markets.map { m ->
            val o = m.jsonObject
            if ((o["key"] as JsonPrimitive).content == "s;1;m") JsonObject(o + mapOf("version" to JsonPrimitive((o["version"] as JsonPrimitive).longOrNull!! + 5)))
            else o
        }
        r.fake.markets = JsonArray(bumped)
        r.feed.pollOnce(0L)
        val sent = Json.parseToJsonElement(r.frames.single()).jsonObject.getValue("rec").jsonObject.getValue("markets").jsonArray
        assertEquals(listOf("s;1;m"), sent.map { (it.jsonObject["key"] as JsonPrimitive).content })
    }

    @Test
    fun `a danger-zone game sends a dz signal with no prices`() = runTest {
        val r = rig(backgroundScope)
        r.fake.relatedOverride = { arr -> JsonArray(arr.map { el -> val o = el.jsonObject; if ((o["id"] as JsonPrimitive).longOrNull == gameId) JsonObject(o + ("liveMode" to JsonPrimitive("danger_zone"))) else o }) }
        r.feed.discover(24)
        r.feed.pollOnce(0L)
        val f = Json.parseToJsonElement(r.frames.single()).jsonObject
        assertTrue((f["topic"] as JsonPrimitive).content.endsWith("/live/dz"))
        assertEquals(0, f.getValue("rec").jsonObject.getValue("markets").jsonArray.size)
        r.book.apply(f, 1_000L)
        assertTrue("the book marks the game volatile", r.book.events.getValue(gameId).volatileUntilMs > 1_000L)
    }

    @Test
    fun `a game that is no longer live is dropped with a del, and the period list is never sent`() = runTest {
        val r = rig(backgroundScope)
        r.feed.discover(24)
        r.feed.pollOnce(0L)
        assertFalse("periods read closed on a game in play: not passed on", r.frames[0].contains("\"periods\""))
        r.frames.clear()
        r.fake.relatedOverride = { arr -> JsonArray(arr.map { el -> val o = el.jsonObject; if ((o["id"] as JsonPrimitive).longOrNull == gameId) JsonObject(o + ("isLive" to JsonPrimitive(false))) else o }) }
        r.feed.pollOnce(0L)
        r.feed.pollOnce(0L)
        val del = Json.parseToJsonElement(r.frames.last()).jsonObject
        assertEquals("del", (del["op"] as JsonPrimitive).content)
        assertTrue(r.feed.games.isEmpty())
    }

    @Test
    fun `shadow mode applies the frames to its own book and sends the engine nothing`() = runTest {
        val shadow = PinnBook()
        val seen = ArrayList<Triple<Long, String, Long>>()
        shadow.versionListener = { id, key, v, _ -> seen += Triple(id, key, v) }
        val r = rig(backgroundScope, shadow = shadow)
        r.feed.discover(24)
        r.feed.pollOnce(0L)
        assertTrue(r.frames.isEmpty())
        assertNotNull(shadow.events[gameId])
        assertTrue("every new market version is reported to the race", seen.isNotEmpty() && seen.all { it.first == gameId })
    }

    @Test
    fun `a refused key or too many requests is counted and said, and a 429 slows the polling`() = runTest {
        val r = rig(backgroundScope)
        r.fake.status = 429
        r.feed.discover(24)
        assertTrue(r.feed.games.isEmpty())
        r.feed.publishForTest()
        assertTrue(r.feed.stats.value.rateLimited > 0)
        assertTrue(r.feed.stats.value.lastError!!.contains("429"))
        r.fake.status = 403
        r.feed.discover(24)
        r.feed.publishForTest()
        assertTrue(r.feed.stats.value.lastError!!.contains("refused"))
        assertTrue(r.feed.stats.value.failures >= 2)
    }

    @Test
    fun `the race pairs the same version from both feeds and reports the gap, signed website minus socket`() {
        val race = VersionRace { 1_000L }
        race.note(VersionRace.Source.SOCKET, 1, "s;0;m", 10, 1_000)
        race.note(VersionRace.Source.WEBSITE, 1, "s;0;m", 10, 3_500)   // website 2.5 s later
        race.note(VersionRace.Source.WEBSITE, 1, "s;0;m", 11, 4_000)
        race.note(VersionRace.Source.SOCKET, 1, "s;0;m", 11, 4_800)    // socket later: website first
        race.note(VersionRace.Source.SOCKET, 1, "s;0;m", 12, 5_000)    // the website skipped it
        val r = race.report()
        assertEquals(2, r.paired)
        assertEquals(1, r.websiteLater)
        assertEquals(1, r.websiteFirst)
        assertEquals(2_500L, r.medianLagMs)
        assertTrue(race.lines().first().contains("2 price versions"))
        race.reset()
        assertEquals(0, race.report().paired)
    }
}
