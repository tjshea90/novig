package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Tj, 2026-10-18 ("use the pinnacle feed first then pinnwire for backup"): pinnapi answers game lines first, PinnWire backs it up and alone sends props. */
class PinnapiFirstTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val nfl = Leagues.byNovigName("NFL")!!
    private val board = javaClass.classLoader!!.getResource("pinnwire-football.json")!!.readText()

    @Before fun up() { server.start() }
    @After fun down() { server.shutdown() }

    private fun meter() = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }, json))
    private fun base() = server.url("/kit/v1").toString().trimEnd('/')
    private fun client(): PinnapiClient {
        val m = meter()
        return PinnapiClient(
            OkHttpClient(), json,
            listOf(
                PinnapiClient.pinnapi(KeyPool(QuotaPolicy.PINNAPI, { listOf("api") }, m), base()),
                PinnapiClient.pinnwire(KeyPool(QuotaPolicy.PINNWIRE, { listOf("wire") }, m), base()),
            ),
            clock = { 9L },
        )
    }

    @Test fun game_lines_come_from_pinnapi_first() = runBlocking {
        server.enqueue(MockResponse().setBody(board))
        val c = client()
        c.odds(nfl, ScanSettings(families = setOf(MarketFamily.GAME_LINES)))
        assertEquals("api", server.takeRequest().getHeader("x-portal-apikey"))
        assertEquals(1, server.requestCount)
    }

    @Test fun props_skip_pinnapi_for_pinnwire() = runBlocking {
        server.enqueue(MockResponse().setBody(board))
        val c = client()
        c.odds(nfl, ScanSettings(families = MarketFamily.entries.toSet()))
        assertEquals("wire", server.takeRequest().getHeader("x-api-key"))
        assertEquals("PinnWire", c.lastHost)
    }

    @Test fun a_failing_pinnapi_falls_back_to_pinnwire() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(board))
        val c = client()
        c.odds(nfl, ScanSettings(families = setOf(MarketFamily.GAME_LINES)))
        server.takeRequest()
        assertEquals("wire", server.takeRequest().getHeader("x-api-key"))
    }
}
