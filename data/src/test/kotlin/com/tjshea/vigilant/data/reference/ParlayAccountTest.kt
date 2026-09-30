package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.keys.CreditHeaders
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * ParlayAPI telling the app how many credits a key has left (Tj, 2026-09-30: "It allows the API key to tell the app how many credits I have
 * left. Add this to the app so the meter is accurate"), and its best practices (parlay-api.com/docs/best-practices): the key in the
 * X-API-Key header, `X-RateLimit-Remaining`/`X-RateLimit-Reset` read, one retry for a 502, none for a 4xx, the request id in errors.
 */
class ParlayAccountTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private var now = Instant.parse("2026-09-30T03:00:00Z").toEpochMilli()
    private val resetSec = Instant.parse("2026-10-29T23:00:00Z").epochSecond

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })

    private fun key(k: String = "pk") = meter.flow.value.providers.getValue("parlay").keys.getValue(k)

    // ---- the headers --------------------------------------------------------------------------------------------------

    @Test
    fun `the credit headers are read in every name a reply uses, and a per-second window is never taken for the month`() {
        val paid = mapOf("x-ratelimit-limit" to "unlimited", "x-ratelimit-remaining" to "19974", "x-ratelimit-reset" to "$resetSec", "x-request-id" to "81b5f3bd")
        val h = CreditHeaders.read({ paid[it.lowercase()] }, now)
        assertEquals(19_974, h.remaining)
        assertEquals(resetSec * 1000, h.resetAtMs)
        assertEquals("81b5f3bd", h.requestId)
        // The free tier's X-RateLimit-* is its 60-a-second cap: not the month's credits.
        val free = mapOf("x-ratelimit-limit" to "60", "x-ratelimit-remaining" to "59", "x-ratelimit-reset" to "${now / 1000 + 1}", "x-credits-remaining" to "950")
        val f = CreditHeaders.read({ free[it.lowercase()] }, now)
        assertEquals(950, f.remaining)
        assertNull(f.resetAtMs)
        // The Odds API's names still win where they're sent.
        val odds = mapOf("x-requests-remaining" to "488", "x-requests-used" to "12", "x-requests-last" to "3")
        assertEquals(CreditHeaders(488, 12, 3), CreditHeaders.read({ odds[it.lowercase()] }, now))
    }

    @Test
    fun `the provider's reset time becomes the key's month, for the meter and the pace`() = runBlocking<Unit> {
        meter.recordCall(QuotaPolicy.PARLAY, "pk", 5, serverRemaining = 19_995, serverUsed = 5, resetAtMs = resetSec * 1000)
        val u = key()
        assertEquals(resetSec * 1000, u.nextReset(QuotaPolicy.PARLAY))
        assertEquals(Instant.parse("2026-09-29T23:00:00Z").toEpochMilli(), u.periodStart)
        // Past it, a new month begins from that moment, not the 1st.
        now = resetSec * 1000 + 60_000
        val rolled = QuotaPolicy.PARLAY.roll(u, now)
        assertEquals(resetSec * 1000, rolled.periodStart)
        assertEquals(0, rolled.used)
        assertNull(rolled.resetAtMs)
    }

    // ---- the key's own account ------------------------------------------------------------------------------------------

    private fun account(keys: List<String> = listOf("pk")) =
        ParlayAccount(OkHttpClient(), { keys }, meter, json, server.url("/v1").toString().trimEnd('/'), clock = { now })

    /** `/v1/usage` as Tj's Starter key answered it on 2026-09-30 (email and key fingerprint left out). */
    private val usage = """{"credits_used":86,"credits_granted":0,"credits_remaining":19914,"credits_total":20000,"tier":"starter",
        "period_start":"2026-09-01T00:00:00+00:00","period_end":"2026-10-01T00:00:00+00:00",
        "plan":{"tier":"starter","name":"Starter","monthly_price_usd":5.0,"credits_per_month":20000,"rate_limit_per_sec":10000}}"""

    /** `/v1/meta/api-key-check` as the same key answered it (Stripe ids left out): its `period_end_iso` is the billing date. */
    private val keyCheck = """{"valid":true,"tier":"starter","credits_used":86,"credits_total":20000,"credits_remaining":19914,"credits_used_pct":0.43,
        "subscription":{"state":"active","period_end_iso":"2026-10-30T02:51:30Z"},"last_request_at_iso":"2026-09-30T04:12:58Z"}"""

    @Test
    fun `the key says what it has left, for free, and the meter shows exactly that`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody(usage).setHeader("x-ratelimit-limit", "unlimited").setHeader("x-ratelimit-remaining", "unlimited"))
        assertEquals(1, account().refresh())
        val req = server.takeRequest()
        assertEquals("/v1/usage", req.requestUrl!!.encodedPath)
        assertEquals("pk", req.getHeader("X-API-Key"))
        assertNull(req.requestUrl!!.queryParameter("apiKey"))
        assertEquals(1, server.requestCount) // one free call when /v1/usage answers
        val u = key()
        assertEquals(19_914, u.remaining)
        assertEquals(20_000, u.limit)
        assertEquals(86, u.used)
        // The credits month (the 1st to the 1st, UTC), not Stripe's billing date.
        assertEquals(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), u.resetAtMs)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli(), u.periodStart)
        assertEquals("plan: starter", u.lastNote)
        val view = com.tjshea.vigilant.data.keys.UsageViews.build(QuotaPolicy.PARLAY, listOf("pk"), meter.flow.value.providers["parlay"], now)
        assertEquals(19_914, view.totalLeft)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), view.nextReset)
        assertEquals("usage", account().let { a -> server.enqueue(MockResponse().setBody(usage)); a.refresh(); a.last.getValue("pk").source })
        // The plan's closing-lines history (/v1/meta/limits): Starter reaches back 7 days.
        assertEquals(7, account().let { a -> server.enqueue(MockResponse().setBody(usage)); a.refresh(); a.historyDays() })
    }

    @Test
    fun `when usage can't be read the key check answers, and its billing date is never taken for the credits reset`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(keyCheck))
        val a = account()
        assertEquals(1, a.refresh())
        assertEquals("/v1/usage", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals("/v1/meta/api-key-check", server.takeRequest().requestUrl!!.encodedPath)
        val u = key()
        assertEquals(19_914, u.remaining)
        assertEquals(20_000, u.limit)
        assertNull(u.resetAtMs)
        assertEquals("api-key-check", a.last.getValue("pk").source)
    }

    @Test
    fun `a check sent before a call's answer landed doesn't wind the meter back`() = runBlocking<Unit> {
        meter.recordCall(QuotaPolicy.PARLAY, "pk", 3, serverRemaining = 19_900, serverUsed = 100)
        now += 5_000
        meter.recordBalance(QuotaPolicy.PARLAY, "pk", remaining = 19_914, limit = 20_000, used = 86)
        assertEquals(19_900, key().remaining)
        // Minutes later the account's word goes (a new month's lower count, or our count was off).
        now += 3 * 60_000
        meter.recordBalance(QuotaPolicy.PARLAY, "pk", remaining = 19_914, limit = 20_000, used = 86)
        assertEquals(19_914, key().remaining)
    }

    @Test
    fun `asked at most every few minutes unless forced, and a spent or deactivated key is marked so`() = runBlocking<Unit> {
        val a = account()
        server.enqueue(MockResponse().setBody("""{"valid":true,"tier":"starter","credits_remaining":19000}"""))
        a.refresh()
        a.refresh()
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setBody("""{"valid":false,"reason":"credit_exhausted","tier":"starter","credits_remaining":0}"""))
        a.refresh(force = true)
        assertEquals(2, server.requestCount)
        assertTrue(key().depletedUntil != null)
        server.enqueue(MockResponse().setBody("""{"valid":false,"reason":"key_inactive"}"""))
        now += ParlayAccount.REFRESH_MS
        a.refresh()
        assertTrue(key().refused)
    }

    @Test
    fun `a check that can't be read changes nothing`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(0, account().refresh())
        assertTrue(meter.flow.value.providers["parlay"]?.keys?.get("pk") == null)
    }

    // ---- best practices on the scan's calls ------------------------------------------------------------------------------

    private fun parlay() = TheOddsApiClient(
        OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter), json,
        baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
    )

    @Test
    fun `a gateway blip is retried once, the answer's credits and reset recorded`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(
            MockResponse().setBody(Fixtures.oddsApi).setHeader("x-requests-remaining", "19970").setHeader("x-requests-used", "30")
                .setHeader("x-ratelimit-limit", "unlimited").setHeader("x-ratelimit-reset", "$resetSec"),
        )
        val snap = parlay().fetch("americanfootball_nfl", listOf("pinnacle"))
        assertTrue(snap.events.isNotEmpty())
        assertEquals(2, server.requestCount)
        assertEquals(resetSec * 1000, key().resetAtMs)
    }

    @Test
    fun `a 4xx is never retried, and a failure names ParlayAPI's request id`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(400).setHeader("X-Request-ID", "req-77").setBody("""{"error":"BAD_PARAM"}"""))
        val e = runCatching { parlay().fetch("americanfootball_nfl", listOf("pinnacle")) }.exceptionOrNull()
        assertEquals(1, server.requestCount)
        assertTrue(e?.message, e?.message?.contains("request req-77") == true)
    }

    @Test
    fun `a scan asks ParlayAPI only for the games in its window`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("[]"))
        parlay().odds(com.tjshea.vigilant.data.scanner.Leagues.byNovigName("NFL")!!, com.tjshea.vigilant.data.scanner.ScanSettings(daysAhead = 2))
        val until = Instant.parse(server.takeRequest().requestUrl!!.queryParameter("commenceTimeTo")!!).toEpochMilli()
        assertEquals(now + (48 + 24) * 3_600_000L, until, 1_000.0)
    }

    @Test
    fun `with live games on, ParlayAPI is asked for them (its odds leave them out by default)`() = runBlocking<Unit> {
        val nfl = com.tjshea.vigilant.data.scanner.Leagues.byNovigName("NFL")!!
        server.enqueue(MockResponse().setBody("[]"))
        parlay().odds(nfl, com.tjshea.vigilant.data.scanner.ScanSettings(includeLive = true))
        assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("include_live"))
        server.enqueue(MockResponse().setBody("[]"))
        parlay().odds(nfl, com.tjshea.vigilant.data.scanner.ScanSettings())
        assertNull(server.takeRequest().requestUrl!!.queryParameter("include_live"))
    }

    private fun assertEquals(expected: Long, actual: Long, tolerance: Double) = assertTrue("$expected vs $actual", kotlin.math.abs(expected - actual) <= tolerance)

    // ---- degraded-mode handling ---------------------------------------------------------------------------------------

    @Test
    fun `a book ParlayAPI says has gone stale is left out of its quotes, one a minute behind stays`() = runBlocking<Unit> {
        val quality = """{"sources":[
            {"source":"pinnacle","sla":"ok","age_s":2.1,"thresholds_s":{"tight":10,"stale":120}},
            {"source":"betonline","sla":"degraded","age_s":42.9,"thresholds_s":{"tight":30,"stale":600}},
            {"source":"caesars","sla":"breach","age_s":55.0,"thresholds_s":{"tight":10,"stale":120}},
            {"source":"draftkings","sla":"breach","age_s":900.0,"thresholds_s":{"tight":10,"stale":120}},
            {"source":"fanduel","sla":"stale","age_s":2000}]}"""
        assertEquals(setOf("draftkings", "fanduel"), ParlaySourceQuality.parse(json.parseToJsonElement(quality)))
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                if (request.path!!.contains("source-quality")) MockResponse().setBody(quality) else MockResponse().setBody(Fixtures.oddsApi)
        }
        val client = TheOddsApiClient(
            OkHttpClient(), KeyPool(QuotaPolicy.PARLAY, { listOf("pk") }, meter), json,
            baseUrl = server.url("/v1").toString().trimEnd('/'), clock = { now }, minIntervalMs = 0, feed = OddsFeed.PARLAY,
            quality = ParlaySourceQuality(OkHttpClient(), json, server.url("/v1").toString().trimEnd('/'), clock = { now }),
        )
        val snap = client.odds(com.tjshea.vigilant.data.scanner.Leagues.byNovigName("NFL")!!, com.tjshea.vigilant.data.scanner.ScanSettings())
        val books = snap.events.flatMap { e -> e.markets.map { it.bookKey } }.toSet()
        assertTrue(books.toString(), "draftkings" !in books && "pinnacle" in books)
    }
}
