package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * ParlayAPI's own usage log under its meter (Tj, 2026-09-30, PARLAY_API.md §6.2): `/v1/meta/usage?days=30` (free), its day-by-day credits
 * and top endpoints read (its `credits_*` totals read 0 and are ignored), several keys added together, asked at most once a minute.
 */
class ParlayUsageHistoryTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private var now = Instant.parse("2026-09-30T06:00:00Z").toEpochMilli()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun account(keys: List<String> = listOf("pk")) =
        ParlayAccount(OkHttpClient(), { keys }, meter, json, server.url("/v1").toString().trimEnd('/'), clock = { now })

    @Test
    fun `the real answer's days and top endpoints are read, its zero totals aren't`() {
        val h = ParlayAccount.parseHistory(json.parseToJsonElement(res("parlay-meta-usage.json")), now)!!
        assertEquals(listOf(ParlayAccount.Day("2026-09-30", 112, 34)), h.days)
        assertEquals(112, h.total)
        assertEquals(30, h.windowDays)
        assertEquals(10, h.top.size)
        assertEquals(ParlayAccount.Endpoint("props:baseball_mlb", 15, 5), h.top.first())
        // Something that isn't a usage log: nothing.
        assertNull(ParlayAccount.parseHistory(json.parseToJsonElement("""{"error":"nope"}"""), now))
    }

    @Test
    fun `endpoints read as words`() {
        assertEquals("Props · MLB", ParlayAccount.endpointName("props:baseball_mlb"))
        assertEquals("Game lines · NFL", ParlayAccount.endpointName("odds:americanfootball_nfl"))
        assertEquals("Closing lines · history file", ParlayAccount.endpointName("closing-lines:json"))
        assertEquals("Best bets · NFL", ParlayAccount.endpointName("best_bets:americanfootball_nfl"))
        assertEquals("Second opinion", ParlayAccount.endpointName("verdict"))
        assertEquals("Something new · soccer_epl", ParlayAccount.endpointName("something_new:soccer_epl"))
    }

    @Test
    fun `two keys' logs add up, day by day and endpoint by endpoint`() {
        val a = ParlayAccount.History(listOf(ParlayAccount.Day("2026-09-29", 10, 2), ParlayAccount.Day("2026-09-30", 5, 1)), listOf(ParlayAccount.Endpoint("props:baseball_mlb", 15, 3)), 30, 1)
        val b = ParlayAccount.History(listOf(ParlayAccount.Day("2026-09-30", 7, 2)), listOf(ParlayAccount.Endpoint("props:baseball_mlb", 3, 1), ParlayAccount.Endpoint("odds:baseball_mlb", 4, 1)), 30, 2)
        val h = ParlayAccount.combine(listOf(a, b))!!
        assertEquals(listOf(ParlayAccount.Day("2026-09-29", 10, 2), ParlayAccount.Day("2026-09-30", 12, 3)), h.days)
        assertEquals(listOf(ParlayAccount.Endpoint("props:baseball_mlb", 18, 4), ParlayAccount.Endpoint("odds:baseball_mlb", 4, 1)), h.top)
        assertNull(ParlayAccount.combine(emptyList()))
    }

    @Test
    fun `the log is asked for 30 days with the key in its header, at most once a minute unless forced`() = runBlocking<Unit> {
        val account = account()
        server.enqueue(MockResponse().setBody(res("parlay-meta-usage.json")))
        assertEquals(1, account.refreshHistory())
        val req = server.takeRequest()
        assertEquals("/v1/meta/usage", req.requestUrl!!.encodedPath)
        assertEquals("30", req.requestUrl!!.queryParameter("days"))
        assertEquals("pk", req.getHeader("X-API-Key"))
        assertEquals(112, account.history.value!!.total)
        // Within the minute: not asked again.
        now += 30_000
        assertEquals(0, account.refreshHistory())
        assertEquals(1, server.requestCount)
        // Forced (a key just added), or a minute on: asked.
        server.enqueue(MockResponse().setBody(res("parlay-meta-usage.json")))
        assertEquals(1, account.refreshHistory(force = true))
        // A failed read changes nothing shown.
        now += ParlayAccount.REFRESH_MS
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(0, account.refreshHistory())
        assertEquals(112, account.history.value!!.total)
    }
}
