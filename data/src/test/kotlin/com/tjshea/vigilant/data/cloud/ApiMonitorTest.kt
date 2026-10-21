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
        server.enqueue(MockResponse().setResponseCode(500))
        m.tick(s)                               // the first retry is due at once, the second only after 30 s
        assertEquals("retry ${m.lines()}", 2, m.lines().first { it.id == "propline" }.failStreak)
        m.tick(s); assertEquals(2, server.requestCount)   // waiting
        now += 31_000L
        server.enqueue(MockResponse().setHeader("X-Daily-Remaining", "900").setHeader("X-Daily-Used", "100").setHeader("X-Daily-Limit", "1000").setBody("[]"))
        m.tick(s)
        val ok = m.lines().first { it.id == "propline" }
        assertTrue(ok.ok); assertEquals(0, ok.failStreak); assertEquals(3, server.requestCount)
    }

    @Test
    fun `a check says why it failed, keeps the keys that answered, and a recheck request makes every API due at once`() = runBlocking {
        var now = 1_800_000_000_000L
        val m = monitor({ now }, mapOf(ApiProvider.PROPLINE to listOf("k1abcdefghijkl", "k2abcdefghijkl", "k3abcdefghijkl")))
        val s = ScanSettings().let { ApiToggles.setOn(it, "propline", true) }
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when (request.getHeader("X-API-Key")) {
                "k1abcdefghijkl" -> MockResponse().setResponseCode(401)
                "k2abcdefghijkl" -> MockResponse().setResponseCode(401)
                else -> MockResponse().setHeader("X-Daily-Remaining", "900").setHeader("X-Daily-Limit", "1000").setBody("[]")
            }
        }
        m.tick(s)
        val a = m.lines().first { it.id == "propline" }
        assertTrue("one key answered: the API is up", a.ok); assertEquals(1, a.keysOk); assertEquals(3, a.keys)
        assertEquals("refused", a.kind); assertEquals(2, a.refused); assertEquals(0, a.unreachable); assertEquals(0, a.failStreak)
        val before = server.requestCount
        now += 60_000L
        m.tick(s); assertEquals("not due yet (checked every 5 minutes)", before, server.requestCount)
        m.recheckNow()
        m.tick(s); assertEquals("a pull to refresh makes it due now", before + 3, server.requestCount)
        // all keys refused: not ok, kind refused, streak counts
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() { override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse().setResponseCode(403) }
        now += 400_000L
        m.tick(s)
        val b = m.lines().first { it.id == "propline" }
        assertFalse(b.ok); assertEquals("refused", b.kind); assertEquals(1, b.failStreak)
    }
}
