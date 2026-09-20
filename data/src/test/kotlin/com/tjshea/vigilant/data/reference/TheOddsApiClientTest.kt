package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyRotator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TheOddsApiClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(keys: List<String> = listOf("test-key")) = TheOddsApiClient(
        httpClient = OkHttpClient(),
        keyRotator = KeyRotator(keys),
        json = json,
        baseUrl = server.url("/v4").toString().trimEnd('/'),
    )

    // Real The Odds API v4 response shape (RESEARCH.md §4.3).
    private val sampleBody = """
        [
          {
            "id": "evt-1",
            "sport_key": "americanfootball_nfl",
            "commence_time": "2026-09-20T20:25:00Z",
            "home_team": "San Francisco 49ers",
            "away_team": "Miami Dolphins",
            "bookmakers": [
              {
                "key": "pinnacle",
                "title": "Pinnacle",
                "last_update": "2026-09-20T18:00:00Z",
                "markets": [
                  {
                    "key": "h2h",
                    "outcomes": [
                      {"name": "Miami Dolphins", "price": 4.35},
                      {"name": "San Francisco 49ers", "price": 1.29}
                    ]
                  }
                ]
              },
              {
                "key": "draftkings",
                "title": "DraftKings",
                "last_update": "2026-09-20T18:00:00Z",
                "markets": [
                  {
                    "key": "h2h",
                    "outcomes": [
                      {"name": "Miami Dolphins", "price": 4.10},
                      {"name": "San Francisco 49ers", "price": 1.33}
                    ]
                  }
                ]
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `fetches and parses real-shaped odds into BookQuotes per event`() = runTest {
        server.enqueue(MockResponse().setBody(sampleBody))

        val events = client().getOddsForSport("americanfootball_nfl")

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("San Francisco 49ers", event.homeTeam)
        assertEquals("Miami Dolphins", event.awayTeam)

        val h2h = event.quotesByMarket.getValue("h2h")
        assertEquals(2, h2h.size)
        val pinnacle = h2h.first { it.bookName == "Pinnacle" }
        assertEquals(listOf(4.35, 1.29), pinnacle.decimalOddsByOutcome)
    }

    @Test
    fun `sends the api key and requested markets as query parameters`() = runTest {
        server.enqueue(MockResponse().setBody(sampleBody))

        client(keys = listOf("test-key")).getOddsForSport("americanfootball_nfl", marketKeys = listOf("h2h", "spreads"))

        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("test-key", url.queryParameter("apiKey"))
        assertEquals("h2h,spreads", url.queryParameter("markets"))
    }

    @Test(expected = TheOddsApiException::class)
    fun `a non-2xx, non-rate-limit, non-quota response throws instead of silently returning nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))
        client().getOddsForSport("americanfootball_nfl")
    }

    @Test
    fun `a 401 (quota exhausted or bad key) rotates to the next key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody(sampleBody))

        val events = client(keys = listOf("exhausted-key", "fresh-key")).getOddsForSport("americanfootball_nfl")

        assertEquals(1, events.size)
        assertEquals("exhausted-key", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
        assertEquals("fresh-key", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
    }

    @Test
    fun `a 429 rotates to the next key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        server.enqueue(MockResponse().setBody(sampleBody))

        val events = client(keys = listOf("key-1", "key-2")).getOddsForSport("americanfootball_nfl")
        assertEquals(1, events.size)
    }

    @Test
    fun `throws AllKeysExhaustedException once every key is out of quota`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        assertThrows(AllKeysExhaustedException::class.java) {
            kotlinx.coroutines.runBlocking { client(keys = listOf("key-1", "key-2")).getOddsForSport("americanfootball_nfl") }
        }
    }

    @Test
    fun `parseEvents handles multiple bookmakers and markets standalone`() {
        val events = TheOddsApiClient.parseEvents(sampleBody, json)
        assertTrue(events.isNotEmpty())
        assertEquals(2, events[0].quotesByMarket.getValue("h2h").size)
    }
}
