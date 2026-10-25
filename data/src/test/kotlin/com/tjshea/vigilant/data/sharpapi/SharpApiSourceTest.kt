package com.tjshea.vigilant.data.sharpapi

import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** PX (Tj 2026-10-25: "Why are these odds so old if they are from a socket?"): a slow SharpAPI plan never holds a scan; the scan takes what the store holds and the store fills in the background. */
class SharpApiSourceTest {
    private val server = MockWebServer()

    @After fun done() = server.shutdown()

    @Test
    fun `a league read that takes minutes does not hold the scan`() = runBlocking {
        server.enqueue(MockResponse().setHeader("X-RateLimit-Limit", "12").setBody("""{"data":[],"pagination":{"has_more":false}}""").setBodyDelay(20, TimeUnit.SECONDS))
        server.start()
        val client = SharpApiClient(OkHttpClient(), Json, { "sk_test_FAKE" }, baseUrl = server.url("/api/v1").toString().trimEnd('/'))
        val source = SharpApiSource(SharpApiFeed(client), client)
        val t0 = System.currentTimeMillis()
        val snap = source.odds(Leagues.byNovigName("NFL")!!, ScanSettings())
        assertTrue("answered in ${System.currentTimeMillis() - t0} ms", System.currentTimeMillis() - t0 < 3_000)
        assertTrue(snap.events.isEmpty())
    }
}
