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

/** SGO Pro mode with a Pinnodds key (Tj, 2026-10-09): Pinnodds' Pinnacle board is asked first; off, the feeds are exactly what they were. */
class PinnoddsFirstTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val nfl = Leagues.byNovigName("NFL")!!
    private val settings = ScanSettings(families = MarketFamily.entries.toSet())
    private val board = javaClass.classLoader!!.getResource("pinnwire-football.json")!!.readText()

    @Before fun up() { server.start() }
    @After fun down() { server.shutdown() }

    private fun meter() = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }, json))
    private fun base() = server.url("/kit/v1").toString().trimEnd('/')

    private fun client(on: () -> Boolean, m: UsageMeter = meter()) = PinnapiClient(
        OkHttpClient(), json,
        listOf(PinnapiClient.pinnwire(KeyPool(QuotaPolicy.PINNWIRE, { listOf("wire") }, m), base())),
        clock = { 9L },
        first = { if (on()) PinnapiClient.pinnodds(KeyPool(QuotaPolicy.PINNODDS, { listOf("pinn") }, m), base()) else null },
    )

    @Test fun on_pinnodds_answers_first_with_its_key_and_props() = runBlocking {
        server.enqueue(MockResponse().setBody(board))
        val c = client({ true })
        val snap = c.odds(nfl, settings)
        val r = server.takeRequest()
        assertEquals("pinn", r.getHeader("x-api-key"))
        assertEquals("1", r.requestUrl!!.queryParameter("include_specials"))
        assertEquals("Pinnodds", c.lastHost)
        assertEquals("pinnacle", snap.events.single().markets.first().bookKey)
    }

    @Test fun a_failing_pinnodds_falls_back_to_the_other_feeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(board))
        val c = client({ true })
        c.odds(nfl, settings)
        assertEquals("pinn", server.takeRequest().getHeader("x-api-key"))
        assertEquals("wire", server.takeRequest().getHeader("x-api-key"))
        assertEquals("PinnWire", c.lastHost)
    }

    @Test fun off_pinnodds_is_never_asked() = runBlocking {
        server.enqueue(MockResponse().setBody(board))
        val c = client({ false })
        c.odds(nfl, settings)
        assertEquals("wire", server.takeRequest().getHeader("x-api-key"))
        assertEquals(1, server.requestCount)
    }

    @Test fun its_board_is_reused_only_fifteen_seconds() = runBlocking {
        server.enqueue(MockResponse().setBody(board)); server.enqueue(MockResponse().setBody(board))
        var now = 1_000L
        val m = meter()
        val c = PinnapiClient(OkHttpClient(), json, emptyList(), clock = { now }, first = { PinnapiClient.pinnodds(KeyPool(QuotaPolicy.PINNODDS, { listOf("pinn") }, m), base()) })
        c.odds(nfl, settings); c.odds(nfl, settings)
        assertEquals(1, server.requestCount)
        now += 16_000L
        c.odds(nfl, settings)
        assertEquals(2, server.requestCount)
    }
}
