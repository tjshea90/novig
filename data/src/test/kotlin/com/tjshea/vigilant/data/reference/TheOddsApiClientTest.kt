package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyRotator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TheOddsApiClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client(keys: List<String> = listOf("test-key")) = TheOddsApiClient(
        httpClient = OkHttpClient(),
        keyRotator = KeyRotator(keys),
        json = json,
        baseUrl = server.url("/v4").toString().trimEnd('/'),
        clock = { 42L },
    )

    @Test
    fun `parses every book's moneyline, spread and total with points and sides`() = runTest {
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "497").setHeader("x-requests-used", "3"))

        val snap = client().odds("americanfootball_nfl", listOf("pinnacle", "draftkings"))

        assertEquals(497, snap.creditsRemaining)
        assertEquals(42L, snap.fetchedAtMs)
        val e = snap.events.single()
        assertEquals("Dallas Cowboys", e.home)
        assertEquals(1790540700000L, e.commenceMs)
        val pinSpread = e.markets.first { it.bookKey == "pinnacle" && it.kind == LineKind.SPREAD }
        // The line is the HOME side's handicap: Dallas (home) +3.5.
        assertEquals(3.5, pinSpread.line!!, 0.0)
        assertEquals(-3.5, pinSpread.quotes.first { it.side == Side.AWAY }.point!!, 0.0)
        val total = e.markets.first { it.kind == LineKind.TOTAL }
        assertEquals(47.5, total.line!!, 0.0)
        assertEquals(Side.OVER, total.quotes.first().side)
    }

    @Test
    fun `asks for named bookmakers, never regions, and never Novig itself`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        client().odds("americanfootball_nfl", listOf("pinnacle", "novig", "draftkings", "pinnacle"))
        val url = server.takeRequest().requestUrl!!
        assertEquals("pinnacle,draftkings", url.queryParameter("bookmakers"))
        assertEquals(null, url.queryParameter("regions"))
        assertEquals("h2h,spreads,totals", url.queryParameter("markets"))
        assertEquals("test-key", url.queryParameter("apiKey"))
    }

    @Test
    fun `caps bookmakers at ten so a refresh costs one region of credits`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        client().odds("basketball_nba", (1..14).map { "book$it" })
        assertEquals(10, server.takeRequest().requestUrl!!.queryParameter("bookmakers")!!.split(',').size)
    }

    @Test
    fun `an out of season sport is an empty result, not a dead key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Unknown sport"}"""))
        assertTrue(client().odds("basketball_wnba", listOf("pinnacle")).events.isEmpty())
    }

    @Test(expected = TheOddsApiException::class)
    fun `a server error throws instead of silently returning nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))
        client().odds("americanfootball_nfl", listOf("pinnacle"))
    }

    @Test
    fun `a 401 rotates to the next key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Usage quota has been reached"}"""))
        server.enqueue(MockResponse().setBody(Fixtures.oddsApi))
        val snap = client(keys = listOf("used-up", "fresh")).odds("americanfootball_nfl", listOf("pinnacle"))
        assertFalse(snap.events.isEmpty())
        assertEquals("used-up", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
        assertEquals("fresh", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
    }

    @Test
    fun `every key used up surfaces the quota reason`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Usage quota has been reached"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("Usage quota has been reached"))
        val e = assertThrows(AllKeysExhaustedException::class.java) {
            runBlocking { client(keys = listOf("a", "b")).odds("americanfootball_nfl", listOf("pinnacle")) }
        }
        assertTrue(e.message!!.contains("credits used up"))
    }

    @Test
    fun `outcomes that name neither team are dropped with their whole market`() {
        val body = """[{"id":"x","commence_time":"2026-09-27T20:25:00Z","home_team":"A","away_team":"B","bookmakers":[
            {"key":"k","title":"K","markets":[{"key":"h2h","outcomes":[{"name":"A","price":1.9},{"name":"Someone","price":1.9}]}]}]}]"""
        assertTrue(TheOddsApiClient.parseEvents(body, json).single().markets.isEmpty())
    }
}
