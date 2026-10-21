package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.reference.ParlayAccount
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * XO1 (Tj, 2026-10-21: "some of the apis claim to be failing, but when I test the keys they are green and accepted"): the engine's check of ParlayAPI's ten keys read every key's account once per key (100 reads, past
 * the engine's 20 s allowance), so the card said "no answer within 20 s" while the scans used ParlayAPI fine. A check is now one read per key, run a few at a time, each with a deadline of its own, and says why it failed.
 */
class ApiProbeKeysTest {
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var handler: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse { requests += request; return handler(request) } }
        server.start()
    }
    @After fun tearDown() { server.shutdown() }

    private val clockMs = 1_790_000_000_000L
    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clockMs })
    private fun base(p: String) = server.url("/$p").toString().trimEnd('/')
    private fun probe(keys: List<String> = emptyList()): ApiProbe {
        val http = OkHttpClient()
        val parlay = ParlayAccount(http, { keys }, meter, base = base("parlay"), clock = { clockMs })
        return ApiProbe(http, meter, parlay, { clockMs }, ApiProbe.Hosts(propLine = base("pl"), oddsApi = base("odds"), unabated = base("un")))
    }
    private val usageBody = """{"credits_remaining": 900, "credits_total": 1000, "credits_used": 100, "tier": "starter", "period_end": "2026-11-01T00:00:00Z"}"""

    @Test fun parlayApiTenKeysAreTenReadsNotAHundred() = runBlocking {
        val keys = (1..10).map { "parlaykey${it}abcdefgh" }
        handler = { MockResponse().setResponseCode(200).setBody(usageBody) }
        val r = probe(keys).check(ApiProvider.PARLAY, keys)
        assertEquals(10, r.size)
        assertTrue(r.joinToString { it.message }, r.all { it.ok })
        assertEquals("one account read per key (it was one per key per key)", 10, requests.size)
        assertTrue(requests.all { it.requestUrl!!.encodedPath.endsWith("/usage") })
        assertEquals(keys.toSet(), requests.map { it.getHeader("X-API-Key") }.toSet())
    }

    @Test fun oneSilentKeyDoesNotTakeTheOthersAnswersWithIt() = runBlocking {
        val keys = listOf("fast1abcdefghijk", "slow1abcdefghijk", "fast2abcdefghijk")
        handler = { req -> if (req.getHeader("X-API-Key") == "slow1abcdefghijk") MockResponse().setResponseCode(200).setBody("[]").setBodyDelay(5, TimeUnit.SECONDS) else MockResponse().setResponseCode(200).setBody("[]").setHeader("X-Daily-Remaining", "10") }
        val t0 = System.currentTimeMillis()
        val r = probe().check(ApiProvider.PROPLINE, keys, keyTimeoutMs = 1_000L)
        assertTrue("the whole check waited for the slow key only once, not 5 s: ${System.currentTimeMillis() - t0} ms", System.currentTimeMillis() - t0 < 4_000L)
        assertEquals(listOf(true, false, true), r.map { it.ok })
        assertEquals(ApiProbe.Check.Kind.UNREACHABLE, r[1].kind)
        assertTrue(r[1].message, r[1].message.contains("no answer within 1 s"))
        assertEquals("the order of the keys is kept", listOf(UsageViews.mask(keys[0]), UsageViews.mask(keys[1]), UsageViews.mask(keys[2])), r.map { it.masked })
    }

    @Test fun eachFailureSaysWhyInTheManagersTerms() = runBlocking {
        val k = listOf("refusedabcdefghij", "spentabcdefghijkl", "brokenabcdefghijk", "goodabcdefghijklm")
        handler = { req -> when (req.getHeader("X-API-Key")) {
            k[0] -> MockResponse().setResponseCode(401)
            k[1] -> MockResponse().setResponseCode(429)
            k[2] -> MockResponse().setResponseCode(503)
            else -> MockResponse().setResponseCode(200).setBody("[]").setHeader("X-Daily-Remaining", "5")
        } }
        val r = probe().check(ApiProvider.PROPLINE, k)
        assertEquals(listOf(ApiProbe.Check.Kind.REFUSED, ApiProbe.Check.Kind.SPENT, ApiProbe.Check.Kind.ERROR, ApiProbe.Check.Kind.OK), r.map { it.kind })
        assertFalse(r[0].ok); assertTrue("a spent key is accepted", r[1].ok); assertFalse(r[2].ok); assertTrue(r[3].ok)
    }

    @Test fun kindsFromWords() {
        assertEquals(ApiProbe.Check.Kind.UNREACHABLE, ApiProbe.Check.Kind.of(false, "Could not be reached (timeout)."))
        assertEquals(ApiProbe.Check.Kind.REFUSED, ApiProbe.Check.Kind.of(false, "Refused (HTTP 403): this key is not accepted."))
        assertEquals(ApiProbe.Check.Kind.REFUSED, ApiProbe.Check.Kind.of(false, "Not valid: key_inactive."))
        assertEquals(ApiProbe.Check.Kind.THROTTLED, ApiProbe.Check.Kind.of(true, "Accepted, rate limited for the moment (HTTP 429)."))
        assertEquals(ApiProbe.Check.Kind.SPENT, ApiProbe.Check.Kind.of(true, "Accepted, but PinnWire says today's requests are used up (back in 8 h)."))
        assertEquals(ApiProbe.Check.Kind.ERROR, ApiProbe.Check.Kind.of(false, "Answered HTTP 502."))
        assertEquals(ApiProbe.Check.Kind.OK, ApiProbe.Check.Kind.of(true, "Active and accepted · 5 left"))
    }
}
