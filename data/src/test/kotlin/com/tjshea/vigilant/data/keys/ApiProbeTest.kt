package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tj, 2026-10-11: "a small test button next to each api in the settings that tests whether the key is active and accepted" and "if the apis offer a prompt to see how much usage is left, use that for the
 * readings". Each API's check against an answer in the shape its docs publish, and what lands in the meter.
 */
class ApiProbeTest {
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var handler: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }
    private var clockMs = 1_790_000_000_000L

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse { requests += request; return handler(request) } }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { clockMs })
    private fun base(p: String) = server.url("/$p").toString().trimEnd('/')
    private val probe by lazy { ApiProbe(
        OkHttpClient(), meter, null, { clockMs },
        ApiProbe.Hosts(
            oddsApi = base("odds"), propLine = base("pl"), sgo = base("sgo"), op5 = base("op5"), op4 = base("op4"), unabated = base("un"), bolt = base("bolt"), pinnodds = base("pinnodds"),
            pinnwire = base("wire"), pinnapi = base("pinnapi"), kalshi = base("kalshi"), polymarket = base("poly"),
        ),
    ) }
    private fun ok(body: String = "{}") = MockResponse().setResponseCode(200).setBody(body)
    private suspend fun usage(policy: QuotaPolicy, key: String): KeyUsage { meter.load(); return meter.flow.value.providers[policy.id]!!.keys[key]!! }

    @Test fun theOddsApiKeyIsAcceptedAndItsOwnCreditsGoIntoTheMeter() = runBlocking {
        handler = { ok("[]").setHeader("x-requests-remaining", "412").setHeader("x-requests-used", "88") }
        val r = probe.check(ApiProvider.THE_ODDS_API, listOf("abcdefgh12345678")).single()
        assertTrue(r.message, r.ok); assertTrue(r.message, r.message.contains("412"))
        assertEquals("the key travels as a parameter and the call is the free /sports", "/odds/sports/", requests.single().requestUrl!!.encodedPath)
        val u = usage(QuotaPolicy.ODDS_API, "abcdefgh12345678")
        assertEquals(412, u.remaining); assertEquals(500, u.limit); assertEquals(88, u.used)
    }

    @Test fun aRefusedKeyIsMarkedRefusedSoTheRotationSkipsIt() = runBlocking {
        handler = { MockResponse().setResponseCode(401) }
        val r = probe.check(ApiProvider.THE_ODDS_API, listOf("badbadbadbad1234")).single()
        assertFalse(r.ok); assertTrue(r.message.contains("Refused"))
        assertTrue(usage(QuotaPolicy.ODDS_API, "badbadbadbad1234").refused)
    }

    @Test fun aSpentKeyIsAcceptedButHeldUntilItsReset() = runBlocking {
        handler = { MockResponse().setResponseCode(429) }
        val r = probe.check(ApiProvider.THE_ODDS_API, listOf("spentspentspent12")).single()
        assertTrue(r.message, r.ok && r.message.contains("used up"))
        assertNotNull(usage(QuotaPolicy.ODDS_API, "spentspentspent12").depletedUntil)
    }

    @Test fun everyKeyIsCheckedOneAtATimeAndEachGetsItsOwnAnswer() = runBlocking {
        handler = { r -> if (r.requestUrl!!.queryParameter("apiKey") == "goodgoodgood1234") ok("[]").setHeader("x-requests-remaining", "10").setHeader("x-requests-used", "490") else MockResponse().setResponseCode(401) }
        val rs = probe.check(ApiProvider.THE_ODDS_API, listOf("goodgoodgood1234", "wrongwrongwrong1"))
        assertEquals(listOf(true, false), rs.map { it.ok })
        assertEquals(2, requests.size)
        assertEquals(listOf(false), probe.check(ApiProvider.THE_ODDS_API, emptyList()).map { it.ok })
    }

    @Test fun propLineSaysItsDailyRequestsLeftOnEveryReply() = runBlocking {
        handler = { ok("[]").setHeader("X-Daily-Remaining", "640").setHeader("X-Daily-Used", "360") }
        val r = probe.check(ApiProvider.PROPLINE, listOf("plkeyplkeyplkey1")).single()
        assertTrue(r.message, r.ok && r.message.contains("640"))
        assertEquals("plkeyplkeyplkey1", requests.single().getHeader("X-API-Key"))
        assertEquals(640, usage(QuotaPolicy.PROPLINE, "plkeyplkeyplkey1").remaining)
    }

    @Test fun sgoReadsItsAccountUsageIncludingTheMonthlyObjects() = runBlocking {
        handler = { ok("""{"success":true,"data":{"tier":"amateur","rateLimits":{"per-minute":{"max-requests":10,"current-requests":1},"per-month":{"max-requests":"unlimited","max-entities":2500,"current-entities":100,"currentIntervalEndTime":"2026-11-01T00:00:00Z"}}}}""") }
        val r = probe.check(ApiProvider.SPORTSGAMEODDS, listOf("sgokeysgokey1234")).single()
        assertTrue(r.message, r.ok)
        assertEquals("sgokeysgokey1234", requests.single().getHeader("x-api-key"))
        assertEquals("/sgo/account/usage", requests.single().requestUrl!!.encodedPath)
        val u = usage(QuotaPolicy.SGO, "sgokeysgokey1234")
        assertEquals(2400, u.remaining); assertEquals(2500, u.limit)
    }

    @Test fun oddsPapiFreeReadsItsRealAllowanceFromTheUnmeteredAccount() = runBlocking {
        handler = { ok("""{"api_key":"X","current_subscription_id":"s","subscriptions":[{"subscription_id":"s","is_active":true,"valid_until":"2099-01-01T00:00:00.000Z","bookmakers":{"pinnacle":{}},"sport_ids":[14],"request_limit":500,"request_count":120}]}""") }
        val r = probe.check(ApiProvider.ODDSPAPI_FREE, listOf("op4keyop4key1234")).single()
        assertTrue(r.message, r.ok && r.message.contains("120/500"))
        val u = usage(QuotaPolicy.ODDSPAPI_FREE, "op4keyop4key1234")
        assertEquals(380, u.remaining); assertEquals(500, u.limit)
    }

    @Test fun aV4KeyPastedUnderV5AndAV5KeyPastedUnderV4AreEachRecognised() = runBlocking {
        handler = { r -> if (r.requestUrl!!.encodedPath.startsWith("/op5")) MockResponse().setResponseCode(401) else ok("{}") }
        val a = probe.check(ApiProvider.ODDSPAPI, listOf("v4keyv4keyv4key1")).single()
        assertFalse(a.ok); assertTrue(a.message, a.message.contains("OddsPapi Free (v4)"))
        handler = { r -> if (r.requestUrl!!.encodedPath.startsWith("/op4")) MockResponse().setResponseCode(401) else ok("[]") }
        val b = probe.check(ApiProvider.ODDSPAPI_FREE, listOf("v5keyv5keyv5key1")).single()
        assertFalse(b.ok); assertTrue(b.message, b.message.contains("v5 key"))
    }

    @Test fun unabatedAndOddsPapiV5SayAcceptedAndThatTheyPublishNoAllowance() = runBlocking {
        handler = { ok("""{"data":[],"success":true}""") }
        val u = probe.check(ApiProvider.UNABATED, listOf("unabatedunabated1")).single()
        assertTrue(u.message, u.ok && u.message.contains("no allowance"))
        assertEquals("unabatedunabated1", requests.single().getHeader("X-Api-Key"))
        val v5 = probe.check(ApiProvider.ODDSPAPI, listOf("op5keyop5keyop51")).single()
        assertTrue(v5.message, v5.ok && v5.message.contains("does not publish"))
    }

    @Test fun pinnwireAndPinnapiHaveNoPromptSoTheTestSpendsOneRequestAndCountsIt() = runBlocking {
        handler = { ok("{}") }
        val r = probe.check(ApiProvider.PINNWIRE, listOf("wirewirewire1234")).single()
        assertTrue(r.message, r.ok && r.message.contains("1 request"))
        assertEquals("wirewirewire1234", requests.single().getHeader("x-api-key"))
        assertEquals(1, usage(QuotaPolicy.PINNWIRE, "wirewirewire1234").used)
        requests.clear()
        probe.check(ApiProvider.PINNAPI, listOf("pinnapipinnapi123"))
        assertEquals("pinnapipinnapi123", requests.single().getHeader("x-portal-apikey"))
    }

    @Test fun theKeylessFeedsAreChecked() = runBlocking {
        handler = { ok("{}") }
        assertTrue(probe.reach(kalshi = true).ok); assertTrue(probe.reach(kalshi = false).ok)
        handler = { MockResponse().setResponseCode(503) }
        assertFalse(probe.reach(kalshi = true).ok)
    }

    @Test fun aPinnacleHostSwitchedOffInSettingsIsNeverAsked() = runBlocking {
        handler = { ok("[]") }
        val off = PinnapiClient.pinnwire(KeyPool(QuotaPolicy.PINNWIRE, { listOf("wirewirewire1234") }, meter), base("wire"), enabled = { false })
        val on = PinnapiClient.pinnapi(KeyPool(QuotaPolicy.PINNAPI, { listOf("pinnapipinnapi123") }, meter), base("pinnapi"), enabled = { true })
        val client = PinnapiClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, listOf(off, on), { clockMs })
        runCatching { client.odds(Leagues.byNovigName("NFL")!!, ScanSettings()) }
        assertTrue("only the host that is on was called", requests.all { it.requestUrl!!.encodedPath.startsWith("/pinnapi") } && requests.isNotEmpty())
        requests.clear()
        val allOff = PinnapiClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, listOf(off), { clockMs })
        val e = runCatching { allOff.odds(Leagues.byNovigName("NFL")!!, ScanSettings()) }.exceptionOrNull()
        assertTrue(e.toString(), e != null)
        assertEquals("nothing is sent when every host is off", 0, requests.size)
    }

    @Test fun boltOddsKeyIsCheckedWithGetInfoAndARefusalIsToldApartFromAnOutage() = runBlocking {
        handler = { r ->
            when {
                r.requestUrl!!.queryParameter("key") != "boltboltbolt1" -> MockResponse().setResponseCode(403).setBody("""{"detail":"Invalid API token"}""")
                r.requestUrl!!.encodedPath.endsWith("/odds/user") -> ok("""{"plan":"Pro","connections":2,"subscriptions":[{"subscribed":true},{"subscribed":true}],"snapshot":{"daily_tokens":300,"tokens_used":12,"resets_at":null}}""")
                else -> ok("""{"sports":["NBA","MLB"],"sportsbooks":["draftkings","fanduel","novig"]}""")
            }
        }
        val good = probe.check(ApiProvider.BOLTODDS, listOf("boltboltbolt1")).single()
        assertTrue(good.message, good.ok); assertTrue(good.message, good.message.contains("2 sports, 3 sportsbooks"))
        // The key's open sockets and snapshot tokens come from /api/odds/user (AF, 2026-10-22).
        assertTrue(good.message, good.message.contains("2 sockets open on this key · snapshot tokens 12 of 300 today"))
        assertEquals(listOf("/bolt/get_info", "/bolt/odds/user"), requests.takeLast(2).map { it.requestUrl!!.encodedPath })
        val bad = probe.check(ApiProvider.BOLTODDS, listOf("wrongwrongwrong")).single()
        assertFalse(bad.message, bad.ok); assertTrue(bad.message, bad.message.contains("Invalid API token"))
        assertTrue("a refused key rests", usage(QuotaPolicy.BOLTODDS, "wrongwrongwrong").refused)
        // a key that was refused and is now accepted is lifted
        handler = { ok("""{"sports":[],"sportsbooks":[]}""") }
        assertTrue(probe.check(ApiProvider.BOLTODDS, listOf("wrongwrongwrong")).single().ok)
        assertFalse(usage(QuotaPolicy.BOLTODDS, "wrongwrongwrong").refused)
    }

    @Test fun boltOddsOtherAnswersAreNamedNotMistakenForAnOutage() = runBlocking {
        handler = { MockResponse().setResponseCode(429) }
        assertTrue(probe.check(ApiProvider.BOLTODDS, listOf("boltboltbolt1")).single().message.contains("12 a minute"))
        handler = { MockResponse().setResponseCode(403).setBody("""{"detail":"Plan does not include this"}""") }
        val r = probe.check(ApiProvider.BOLTODDS, listOf("boltboltbolt1")).single()
        assertFalse(r.ok); assertTrue(r.message, r.message.contains("Plan does not include this"))
    }
}
