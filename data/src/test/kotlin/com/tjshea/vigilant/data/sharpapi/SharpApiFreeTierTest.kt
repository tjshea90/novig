package com.tjshea.vigilant.data.sharpapi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** SharpAPI's Free tier (PB1, Tj 2026-10-25: "12 requests per minute, 2 books"): the plan is read from SharpAPI's own headers, the store then pages game lines only and dates rows by the data delay. */
class SharpApiFreeTierTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client() = SharpApiClient(OkHttpClient(), json, { "k" }, baseUrl = server.url("/api/v1").toString().trimEnd('/'))

    @Test fun theFreeTierIsReadFromTheHeadersAndAsksGameLinesOnly() = runBlocking {
        val c = client()
        assertFalse("unknown plan is not called lite", c.lite())
        server.enqueue(MockResponse().setBody("""{"data":[],"pagination":{"has_more":false}}""").setHeader("X-RateLimit-Limit", "12").setHeader("X-RateLimit-Remaining", "11").setHeader("X-Data-Delay", "60"))
        val feed = SharpApiFeed(c)
        assertTrue(feed.load("nfl"))
        assertTrue(c.lite())
        assertEquals(60_000L, c.dataDelayMs)
        server.enqueue(MockResponse().setBody("""{"data":[],"pagination":{"has_more":false}}""").setHeader("X-RateLimit-Limit", "12"))
        val t0 = System.currentTimeMillis()
        assertTrue(feed.load("nba"))
        assertTrue("calls are spaced under 12 a minute", System.currentTimeMillis() - t0 >= 5_000L)
        server.takeRequest()
        val second = server.takeRequest().requestUrl!!
        assertEquals(SharpApiFeed.MARKETS_LITE, second.queryParameter("market"))
    }

    @Test fun aPaidPlanKeepsEveryMarketAndNoSpacing() = runBlocking {
        val c = client()
        repeat(2) { server.enqueue(MockResponse().setBody("""{"data":[],"pagination":{"has_more":false}}""").setHeader("X-RateLimit-Limit", "1000")) }
        val feed = SharpApiFeed(c)
        assertTrue(feed.load("nfl"))
        val t0 = System.currentTimeMillis()
        assertTrue(feed.load("nba"))
        assertTrue(System.currentTimeMillis() - t0 < 3_000L)
        server.takeRequest()
        assertEquals(SharpApiFeed.MARKETS, server.takeRequest().requestUrl!!.queryParameter("market"))
        assertFalse(c.lite())
    }
}
