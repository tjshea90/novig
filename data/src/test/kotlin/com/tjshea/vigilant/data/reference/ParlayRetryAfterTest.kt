package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ParlayAPI's best practices (parlay-api.com/docs/best-practices, re-read 2026-09-30 for Tj's "make sure the apis are being used to their full
 * potential"): "503 with Retry-After: explicit wait then retry. Honor the header; don't poll faster." A scan's call waits what the header
 * says (capped) before its one retry; a busy /verdict or /line-movement answer carries the wait (header, else its body's
 * `retry_after_seconds`) to the caller.
 */
class ParlayRetryAfterTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val now = 1_790_000_000_000L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun client() = TheOddsApiClient(
        OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter),
        json, baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
    )

    @Test
    fun `a Retry-After reads as milliseconds, capped, and nonsense as none`() {
        assertEquals(15_000L, TheOddsApiClient.retryAfterMs("15"))
        assertEquals(TheOddsApiClient.MAX_RETRY_AFTER_MS, TheOddsApiClient.retryAfterMs("600"))
        assertNull(TheOddsApiClient.retryAfterMs("soon"))
        assertNull(TheOddsApiClient.retryAfterMs(null))
    }

    @Test
    fun `a scan's 503 waits what Retry-After says before its one retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "7").setBody("{}"))
        server.enqueue(MockResponse().setBody("{\"props\":[]}"))
        val start = currentTime
        ParlayPropsSource(client()).odds(Leagues.byNovigName("NFL")!!, ScanSettings())
        assertEquals(2, server.requestCount)
        assertTrue("waited ${currentTime - start} ms", currentTime - start >= 7_000L)
    }

    @Test
    fun `a busy answer carries its wait, from the header or else the body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "15").setBody("{}"))
        assertEquals(15_000L, client().parlayGet("/sports/americanfootball_nfl/line-movement", emptyList(), cost = 2, what = "x", busyCost = 2).value.retryAfterMs)
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"LINE_MOVEMENT_TIMEOUT","retry_after_seconds":15}"""))
        assertEquals(15_000L, client().parlayGet("/sports/americanfootball_nfl/line-movement", emptyList(), cost = 2, what = "x", busyCost = 2).value.retryAfterMs)
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"props_temporarily_busy"}"""))
        assertNull(client().parlayGet("/verdict", emptyList(), cost = 5, what = "x").value.retryAfterMs)
    }

    @Test
    fun `a busy verdict is asked again after the wait it named`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "6").setBody("""{"error":"props_temporarily_busy"}"""))
        server.enqueue(MockResponse().setBody("""{"verdict":"FAIR","fair":{"price":138,"implied_prob":42.0,"source":"pinnacle"}}"""))
        val start = currentTime
        val r = ParlayVerdicts(client(), json, active = { true })
            .ask(VerdictQuery("americanfootball_nfl", "h2h", "Cleveland Browns", "Cleveland Browns", "Pittsburgh Steelers", price = 131))
        assertTrue(r is ParlayVerdicts.Result.Answered)
        assertTrue(currentTime - start >= 6_000L)
    }
}
