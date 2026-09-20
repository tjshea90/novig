package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyRotator
import com.tjshea.vigilant.engine.Odds
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

class SharpApiClientTest {

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

    private fun client(keys: List<String> = listOf("key-1")) = SharpApiClient(
        httpClient = OkHttpClient(),
        keyRotator = KeyRotator(keys),
        json = json,
        baseUrl = server.url("").toString().trimEnd('/'),
    )

    // Real SharpAPI /odds response shape (docs.sharpapi.io, confirmed 2026-09-20).
    private val moneylineBody = """
        {
          "data": [
            {
              "id": "novig_1_moneyline_MIA",
              "sportsbook": "novig",
              "sport": "football",
              "home_team": "San Francisco 49ers",
              "away_team": "Miami Dolphins",
              "market_type": "moneyline",
              "selection": "Miami Dolphins",
              "odds_american": 360,
              "odds_decimal": 4.6,
              "odds_probability": 0.2174,
              "is_live": false
            },
            {
              "id": "novig_1_moneyline_SF",
              "sportsbook": "novig",
              "sport": "football",
              "home_team": "San Francisco 49ers",
              "away_team": "Miami Dolphins",
              "market_type": "moneyline",
              "selection": "San Francisco 49ers",
              "odds_american": -450,
              "odds_decimal": 1.2222,
              "odds_probability": 0.8182,
              "is_live": false
            }
          ],
          "meta": {"count": 2, "total": 2, "books_available": ["novig"], "books_returned": ["novig"]}
        }
    """.trimIndent()

    @Test
    fun `parses a two-outcome moneyline market into one NovigEvent`() = runTest {
        server.enqueue(MockResponse().setBody(moneylineBody))

        val events = client().getOpenMarkets()

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("San Francisco 49ers", event.homeTeam)
        assertEquals("Miami Dolphins", event.awayTeam)
        assertEquals(1, event.markets.size)
        val market = event.markets[0]
        assertEquals("MONEY", market.marketType)
        assertEquals(2, market.outcomes.size)
        val dolphins = market.outcomes.first { it.label == "Miami Dolphins" }
        assertEquals(0.2174, dolphins.price, 1e-9)
    }

    @Test
    fun `falls back to computing probability from decimal odds when odds_probability is absent`() = runTest {
        val bodyWithoutProbability = moneylineBody.replace(""""odds_probability": 0.2174,""", "")
            .replace(""""odds_probability": 0.8182,""", "")
        server.enqueue(MockResponse().setBody(bodyWithoutProbability))

        val events = client().getOpenMarkets()
        val dolphins = events[0].markets[0].outcomes.first { it.label == "Miami Dolphins" }
        assertEquals(Odds.impliedProbability(4.6), dolphins.price, 1e-9)
    }

    @Test
    fun `merges multiple market types for the same event into one NovigEvent`() = runTest {
        val multiMarketBody = """
            {
              "data": [
                {"id": "a", "sportsbook": "novig", "sport": "football", "home_team": "H", "away_team": "A",
                 "market_type": "moneyline", "selection": "A", "odds_decimal": 2.0, "odds_probability": 0.5, "is_live": false},
                {"id": "b", "sportsbook": "novig", "sport": "football", "home_team": "H", "away_team": "A",
                 "market_type": "moneyline", "selection": "H", "odds_decimal": 2.0, "odds_probability": 0.5, "is_live": false},
                {"id": "c", "sportsbook": "novig", "sport": "football", "home_team": "H", "away_team": "A",
                 "market_type": "spread", "selection": "A -3.5", "odds_decimal": 1.91, "odds_probability": 0.52, "is_live": false},
                {"id": "d", "sportsbook": "novig", "sport": "football", "home_team": "H", "away_team": "A",
                 "market_type": "spread", "selection": "H +3.5", "odds_decimal": 1.91, "odds_probability": 0.48, "is_live": false}
              ],
              "meta": {"count": 4, "total": 4, "books_available": ["novig"], "books_returned": ["novig"]}
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(multiMarketBody))

        val events = client().getOpenMarkets()

        assertEquals(1, events.size)
        assertEquals(2, events[0].markets.size)
        assertEquals(setOf("MONEY", "SPREAD"), events[0].markets.map { it.marketType }.toSet())
    }

    @Test
    fun `skips a market group that does not have exactly two outcomes`() = runTest {
        val threeRowBody = """
            {
              "data": [
                {"id": "a", "sportsbook": "novig", "sport": "football", "home_team": "H", "away_team": "A",
                 "market_type": "moneyline", "selection": "A", "odds_decimal": 2.0, "odds_probability": 0.5, "is_live": false},
                {"id": "b", "sportsbook": "novig", "sport": "football", "home_team": "H", "away_team": "A",
                 "market_type": "moneyline", "selection": "H", "odds_decimal": 2.0, "odds_probability": 0.5, "is_live": false},
                {"id": "c", "sportsbook": "novig", "sport": "football", "home_team": "H", "away_team": "A",
                 "market_type": "moneyline", "selection": "Draw", "odds_decimal": 5.0, "odds_probability": 0.2, "is_live": false}
              ],
              "meta": {"count": 3, "total": 3, "books_available": ["novig"], "books_returned": ["novig"]}
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(threeRowBody))

        val events = client().getOpenMarkets()
        assertTrue("a 3-outcome group should be skipped, not guessed at", events.isEmpty())
    }

    @Test
    fun `sends the sportsbook=novig filter and the API key header`() = runTest {
        server.enqueue(MockResponse().setBody(moneylineBody))

        client(keys = listOf("my-secret-key")).getOpenMarkets()

        val request = server.takeRequest()
        assertEquals("novig", request.requestUrl!!.queryParameter("sportsbook"))
        assertEquals("my-secret-key", request.getHeader("X-API-Key"))
    }

    @Test
    fun `rotates to a second key on 429 and succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        server.enqueue(MockResponse().setBody(moneylineBody))

        val events = client(keys = listOf("key-1", "key-2")).getOpenMarkets()

        assertEquals(1, events.size)
        assertEquals(2, server.requestCount)
        assertEquals("key-1", server.takeRequest().getHeader("X-API-Key"))
        assertEquals("key-2", server.takeRequest().getHeader("X-API-Key"))
    }

    @Test
    fun `rotates to a second key on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody(moneylineBody))

        val events = client(keys = listOf("bad-key", "good-key")).getOpenMarkets()
        assertEquals(1, events.size)
    }

    @Test
    fun `throws AllKeysExhaustedException when every key is rate limited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429))

        assertThrows(AllKeysExhaustedException::class.java) {
            kotlinx.coroutines.runBlocking { client(keys = listOf("key-1", "key-2")).getOpenMarkets() }
        }
    }

    @Test
    fun `a non-rate-limit server error throws instead of silently returning nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        assertThrows(SharpApiException::class.java) {
            kotlinx.coroutines.runBlocking { client().getOpenMarkets() }
        }
    }
}
