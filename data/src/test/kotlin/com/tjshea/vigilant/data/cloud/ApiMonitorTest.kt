package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.keys.ApiProbe
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CH48: the engine checks only what is on and keyed, retries a failure on its schedule, and leaves an off API alone. */
class ApiMonitorTest {
    private val server = MockWebServer()
    @After fun tearDown() { server.shutdown() }

    private fun monitor(clock: () -> Long, keys: Map<ApiProvider, List<String>>): ApiMonitor {
        val store = JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() })
        val base = server.url("").toString().trimEnd('/')
        val probe = ApiProbe(OkHttpClient(), UsageMeter(store), null, hosts = ApiProbe.Hosts(propLine = base, oddsApi = base), clock = clock)
        return ApiMonitor(probe, { keys[it].orEmpty() }, clock)
    }

    @Test
    fun `an API that is off is listed and never called, one that is on is checked, a failure is retried after its wait`() = runBlocking {
        var now = 1_800_000_000_000L
        val m = monitor({ now }, mapOf(ApiProvider.PROPLINE to listOf("k1"), ApiProvider.THE_ODDS_API to listOf("k2")))
        val s = ScanSettings().let { ApiToggles.setOn(ApiToggles.setOn(it, "propline", true), "odds_api", false) }
        server.enqueue(MockResponse().setResponseCode(500))
        m.tick(s)
        val prop = m.lines().first { it.id == "propline" }
        assertTrue("on $prop", prop.on); assertFalse("ok $prop", prop.ok); assertEquals("streak $prop", 1, prop.failStreak)
        assertFalse(m.lines().first { it.id == "odds_api" }.on)
        assertEquals("requests", 1, server.requestCount)   // only PropLine was asked
        m.tick(s)                               // the first retry is due at once, the second only after 30 s
        assertEquals("retry ${m.lines()}", 2, m.lines().first { it.id == "propline" }.failStreak)
        server.enqueue(MockResponse().setHeader("X-Daily-Remaining", "900").setHeader("X-Daily-Used", "100").setHeader("X-Daily-Limit", "1000").setBody("[]"))
        m.tick(s); assertEquals(2, server.requestCount)   // waiting
        now += 31_000L
        m.tick(s)
        val ok = m.lines().first { it.id == "propline" }
        assertTrue(ok.ok); assertEquals(0, ok.failStreak); assertEquals(3, server.requestCount)
    }
}
