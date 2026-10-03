package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tj, 2026-10-02: "connection speed and issues, API usage and issues". Every call through the shared client is one record by host: how many, how many failed and how, the
 * status codes, how long to the first byte and to the end, how fast bodies arrive, which endpoints, which hours, which network, and never a key.
 */
class NetStatsTest {

    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_800_000_000_000L
    private fun stats() = NetStats(JsonFileStore(File(tmp.root, "net.json"), NetBook.serializer(), { NetBook() }), { now })

    @Test
    fun `endpoints are shapes, never a query, an id, a token or a key in a path`() {
        fun shape(url: String) = NetShape.of(url.toHttpUrl())
        assertEquals("/v1/sports/americanfootball_nfl/odds", shape("https://api.example.com/v1/sports/americanfootball_nfl/odds?apiKey=SECRETKEY123&markets=h2h"))
        assertEquals("/v1/events/{id}/markets", shape("https://x.com/v1/events/1636872090/markets"))
        assertEquals("/kit/v1/markets", shape("https://pinnwire.com/kit/v1/markets?sport_id=5&key=demo"))
        assertEquals("/a/{id}", shape("https://x.com/a/3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
        assertEquals("/a/{id}", shape("https://x.com/a/sk-ABCDEFGHIJKLMNOPQRSTUVWXYZ123456")) // FAKE
        assertEquals("/a/{id}", shape("https://x.com/a/0123456789abcdef0123"))
        assertEquals("/a/b/c/d/…", shape("https://x.com/a/b/c/d/e/f"))
        assertEquals("/", shape("https://x.com/"))
        // A short word with a digit is kept (a version, a short name).
        assertEquals("/v3/sports", shape("https://x.com/v3/sports"))
        assertFalse(shape("https://x.com/a?token=abc").contains("token"))
    }

    @Test
    fun `a call adds to its host's calls, bytes, statuses, endpoints and hour, and a failure to errors and kinds`() {
        val s = stats()
        s.record("crazyninjaodds.com", "/site/tools/positive-ev.aspx", 200, null, 700, 1_900, 76_000, "Wi-Fi")
        s.record("crazyninjaodds.com", "/site/tools/positive-ev.aspx", 200, null, 900, 2_100, 75_000, "mobile")
        s.record("crazyninjaodds.com", "/site/browse/game.aspx", null, "timeout", 20_000, 20_000, 0, "mobile", error = "SocketTimeoutException: timeout")
        s.record("crazyninjaodds.com", "/site/browse/game.aspx", 429, null, 300, 310, 120, "Wi-Fi", limit = "Retry-After: 30")
        val h = s.snapshot().hosts.getValue("crazyninjaodds.com")
        assertEquals(4L, h.calls)
        assertEquals(2L, h.errors)
        assertEquals(0.5, h.errorRate, 0.0)
        assertEquals(76_000L + 75_000L + 120L, h.bytes)
        assertEquals(mapOf("200" to 2L, "429" to 1L), h.status)
        assertEquals(mapOf("timeout" to 1L), h.kinds)
        assertEquals(mapOf("Wi-Fi" to 2L, "mobile" to 2L), h.byNet)
        assertEquals(PathStat(2, 0, 1_600, 200), h.paths.getValue("/site/tools/positive-ev.aspx"))
        // Each endpoint keeps how its calls failed (v0.54.0 file: "NFL props 41 of 94 failed" said nothing of how).
        assertEquals(PathStat(2, 2, 20_300, 429, mapOf("timeout" to 1L, "429" to 1L)), h.paths.getValue("/site/browse/game.aspx"))
        assertEquals(1L, h.limits)
        assertEquals("Retry-After: 30", h.lastLimit)
        // The last failure is the 429 (its text is its status); the timeout's text was an earlier one.
        assertNotNull(h.lastErrorAtMs)
        assertEquals("HTTP 429", h.lastError)
        assertEquals(1, h.hours.size)
        assertEquals(HourStat(4, 2, 700 + 900 + 20_000 + 300, h.bytes), h.hours.values.single())
    }

    @Test
    fun `latency and speed are percentiles of the recent calls, and a small body's speed isn't counted`() {
        val s = stats()
        (1..100).forEach { i -> s.record("h", "/p", 200, null, i * 10L, i * 10L + 100, 100_000, null) }
        s.record("h", "/p", 200, null, 5_000, 5_000, 100, null)
        val h = s.snapshot().hosts.getValue("h")
        assertEquals(NetStats.RECENT, h.recentMs.size)
        // The last 100: 20 … 1000 ms, then 5000: p95 over them.
        assertEquals(5_000.0, h.latency.max, 0.0)
        assertTrue(h.latency.p50 in 500.0..600.0)
        assertTrue(h.speed.count > 0)
        assertTrue(h.recentBps.size <= NetStats.RECENT_BPS)
        s.record("small", "/p", 200, null, 10, 50, 500, null)
        assertTrue(s.snapshot().hosts.getValue("small").recentBps.isEmpty())
    }

    @Test
    fun `only the busiest endpoints and the last two days of hours are kept`() {
        val s = stats()
        repeat(NetStats.MAX_PATHS + 6) { i -> repeat(i + 1) { s.record("h", "/p$i", 200, null, 10, 10, 0, null) } }
        assertEquals(NetStats.MAX_PATHS, s.snapshot().hosts.getValue("h").paths.size)
        assertTrue(s.snapshot().hosts.getValue("h").paths.containsKey("/p${NetStats.MAX_PATHS + 5}"))
        repeat(60) { _ ->
            now += NetStats.HOUR_MS
            s.record("h", "/old", 200, null, 10, 10, 0, null)
        }
        assertTrue(s.snapshot().hosts.getValue("h").hours.size <= NetStats.HOURS_KEPT)
    }

    @Test
    fun `it is saved across restarts, added to what the new run recorded first, and started again after two weeks`() = runBlocking {
        val first = stats()
        first.record("h", "/p", 200, null, 100, 150, 10_000, "Wi-Fi")
        first.flush(force = true)
        val second = stats()
        second.record("h", "/p", 500, null, 200, 250, 0, "mobile")
        second.load()
        val h = second.snapshot().hosts.getValue("h")
        assertEquals(2L, h.calls)
        assertEquals(1L, h.errors)
        assertEquals(mapOf("200" to 1L, "500" to 1L), h.status)
        assertEquals(PathStat(2, 1, 300, 500, mapOf("500" to 1L)), h.paths.getValue("/p"))
        assertEquals(2, h.recentMs.size)
        second.load()
        assertEquals(2L, second.snapshot().hosts.getValue("h").calls)
        now += NetStats.WINDOW_MS + 1
        val fresh = stats()
        fresh.load()
        assertTrue(fresh.snapshot().hosts.isEmpty())
    }
}

/** The interceptor on a real (mock) server: what the shared client records for a call. */
class NetInterceptorTest {

    private lateinit var server: MockWebServer
    private var now = 1_800_000_000_000L
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var stats: NetStats
    private lateinit var events: EventLog
    private lateinit var client: OkHttpClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        stats = NetStats(JsonFileStore(File(tmp.root, "net.json"), NetBook.serializer(), { NetBook() }))
        events = EventLog(JsonFileStore(File(tmp.root, "ev.json"), EventBook.serializer(), { EventBook() }))
        client = OkHttpClient.Builder().addInterceptor(NetInterceptor(stats, events, network = { "Wi-Fi" })).readTimeout(300, TimeUnit.MILLISECONDS).build()
    }

    @After fun tearDown() { server.shutdown() }

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    private val host get() = server.hostName

    @Test
    fun `a call is recorded when its body is read with host, endpoint shape without the query, status, bytes and network`() {
        server.enqueue(MockResponse().setBody("x".repeat(20_000)))
        val body = get("/v1/sports/nfl/odds?apiKey=SECRETKEY1234567890ABCDEF&x=1").use { it.body!!.string() }
        assertEquals(20_000, body.length)
        val h = stats.snapshot().hosts.getValue(host)
        assertEquals(1L, h.calls)
        assertEquals(0L, h.errors)
        assertEquals(20_000L, h.bytes)
        assertEquals(mapOf("200" to 1L), h.status)
        assertEquals(mapOf("Wi-Fi" to 1L), h.byNet)
        assertEquals(setOf("/v1/sports/nfl/odds"), h.paths.keys)
        assertFalse(stats.snapshot().toString().contains("SECRETKEY"))
        assertTrue(events.events().isEmpty())
    }

    @Test
    fun `the body arrives untouched, a closed unread body is still recorded, and the call is recorded once`() {
        server.enqueue(MockResponse().setBody("hello"))
        server.enqueue(MockResponse().setBody("y".repeat(5_000)))
        assertEquals("hello", get("/a").use { it.body!!.string() })
        get("/b").close()
        val h = stats.snapshot().hosts.getValue(host)
        assertEquals(2L, h.calls)
        assertEquals(2L, h.paths.values.sumOf { it.calls })
    }

    @Test
    fun `an HTTP error is an error with its status, a 429 says what the host asked, a 500 is an ERROR event`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("slow down"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
        server.enqueue(MockResponse().setResponseCode(404).setBody("none"))
        get("/limited").use { it.body!!.string() }
        get("/busy").use { it.body!!.string() }
        get("/gone").use { it.body!!.string() }
        val h = stats.snapshot().hosts.getValue(host)
        assertEquals(3L, h.errors)
        assertEquals(1L, h.limits)
        assertEquals("Retry-After: 30", h.lastLimit)
        val e = events.events().map { it.level to it.msg }
        assertTrue(e.toString(), e.any { it.first == Level.WARN && it.second.contains("/limited rate-limited (429, Retry-After: 30)") })
        assertTrue(e.toString(), e.any { it.first == Level.ERROR && it.second.contains("/busy server error 503") })
        assertTrue(e.toString(), e.any { it.first == Level.WARN && it.second.contains("/gone refused with 404") })
    }

    @Test
    fun `a call that fails before an answer is recorded with how it failed, and a call cancelled on purpose is not an event`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try { get("/slow") } catch (e: java.io.IOException) { }
        val h = stats.snapshot().hosts.getValue(host)
        assertEquals(1L, h.errors)
        assertEquals(mapOf("timeout" to 1L), h.kinds)
        assertTrue(events.events().any { it.msg.contains("/slow failed: timeout") })
        // Cancelled by the caller.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val call = client.newCall(Request.Builder().url(server.url("/cancelled")).build())
        Thread { Thread.sleep(50); call.cancel() }.start()
        try { call.execute() } catch (e: java.io.IOException) { }
        assertEquals(events.events().toString(), 1, events.events().count { it.level == Level.WARN && it.msg.contains("failed") })
        assertEquals(mapOf("timeout" to 1L, "cancelled" to 1L), stats.snapshot().hosts.getValue(host).kinds)
    }

    @Test
    fun `a call whose body takes eight seconds is a slow-call event with its time, and a quick one is not`() {
        // A clock that moves 5 s every time it is read: the call is 0 at the start, 5 s to the headers, 10 s at the end of the body.
        var t = -5_000L
        val slow = OkHttpClient.Builder().addInterceptor(NetInterceptor(stats, events, clock = { t += 5_000L; t })).build()
        server.enqueue(MockResponse().setBody("z".repeat(40_000)))
        slow.newCall(Request.Builder().url(server.url("/big")).build()).execute().use { it.body!!.string() }
        val e = events.events().single()
        assertEquals(Level.WARN, e.level)
        assertTrue(e.msg, e.msg.contains("/big slow: 10000 ms for 39 KB"))
        assertEquals(10_000L, e.ms)
        server.enqueue(MockResponse().setBody("quick"))
        get("/quick").use { it.body!!.string() }
        assertEquals(1, events.events().size)
    }

    @Test
    fun `a call cancelled with a socket error that does not say cancelled is still not reported`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val quiet = OkHttpClient.Builder()
            .addInterceptor(NetInterceptor(stats, events))
            .addInterceptor { chain -> chain.call().cancel(); throw java.io.IOException("Socket closed") }
            .build()
        try { quiet.newCall(Request.Builder().url(server.url("/x")).build()).execute() } catch (e: java.io.IOException) { }
        assertEquals(mapOf("cancelled" to 1L), stats.snapshot().hosts.getValue(host).kinds)
        assertTrue(events.events().toString(), events.events().isEmpty())
    }

    @Test
    fun `no URL query, header or body text reaches the stats or the events`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{\"error\":\"key sk-LIVEKEYLIVEKEYLIVEKEY1234 is invalid\"}"))
        get("/p?apiKey=sk-LIVEKEYLIVEKEYLIVEKEY1234").use { it.body!!.string() }
        val all = stats.snapshot().toString() + events.events().toString()
        assertFalse(all, all.contains("LIVEKEY"))
        assertNull(stats.snapshot().hosts.getValue(host).lastError.takeIf { it?.contains("LIVEKEY") == true })
    }

    @Test
    fun `a failure is classified by how it failed`() {
        assertEquals("dns", NetInterceptor.kindOf(java.net.UnknownHostException("x")))
        assertEquals("timeout", NetInterceptor.kindOf(java.net.SocketTimeoutException("x")))
        assertEquals("connect", NetInterceptor.kindOf(java.net.ConnectException("refused")))
        assertEquals("reset", NetInterceptor.kindOf(java.io.IOException("Connection reset by peer")))
        assertEquals("cancelled", NetInterceptor.kindOf(java.io.IOException("Canceled")))
        assertEquals("tls", NetInterceptor.kindOf(javax.net.ssl.SSLHandshakeException("bad cert")))
        assertEquals("other", NetInterceptor.kindOf(java.io.IOException("weird")))
    }
}
