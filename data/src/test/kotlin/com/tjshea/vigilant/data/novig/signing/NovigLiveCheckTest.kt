package com.tjshea.vigilant.data.novig.signing

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/** Test key's speed check (Tj, 2026-09-29: "the novig scan is slow, even though I tested my key and it says it works"), against a mock Novig. */
class NovigLiveCheckTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"
    private val asked = CopyOnWriteArrayList<String>()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun base() = server.url("").toString().trimEnd('/')
    private fun signer() = NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid-1", pem), base())

    private fun novig(limits: MockResponse? = null, markets: String = """{"items":[{"marketId":"m1"},{"marketId":"m2"},{"marketId":"m3"}]}""") {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += request.path!!
                return when {
                    request.path == "/v3/limits" -> limits ?: MockResponse().setBody(
                        """{"read":{"capacity":64,"refillPerSec":16},"stream":{"capacity":512,"refillPerSec":4},"maxWatchedMarkets":2048}""",
                    )
                    request.path!!.startsWith("/v3/catalog/markets?") -> MockResponse().setBody(markets)
                    request.path!!.contains("/book") -> MockResponse().setBody("""{"marketId":"x","seq":1,"orders":{}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun book(id: String) = NovigBook(id, 1, mapOf("a" to listOf(BidLevel(500, 100))), 0)

    /** A feed that delivers every book [afterMs] after it's asked to watch them; [problem] is what it reports instead when set. */
    private class FakeFeed(val afterMs: Long, val problem: String? = null) : PushedBooks {
        var wanted: List<String> = emptyList()
        var since = 0L
        var closed = false
        override fun watch(marketIds: Collection<String>) { wanted = marketIds.toList(); since = System.currentTimeMillis() }
        override fun live(marketIds: Collection<String>): Map<String, NovigBook> =
            if (problem == null && System.currentTimeMillis() - since >= afterMs) marketIds.associateWith { NovigBook(it, 1, mapOf("a" to listOf(BidLevel(500, 100))), 0) } else emptyMap()
        override fun problemSince(sinceMs: Long): String? = problem
        override fun close() { closed = true }
    }

    private fun check(feed: FakeFeed, waitMs: Long = 3_000) = NovigLiveCheck(
        OkHttpClient(), json, signer(), feed = { feed }, publicBaseUrl = base(), waitMs = waitMs, samples = 2, watchCount = 3,
    )

    @Test
    fun `a healthy key reports its limits, the catalog, book speeds and the live feed`() = runBlocking {
        novig()
        val feed = FakeFeed(afterMs = 300)
        val steps = ArrayList<String>()
        val lines = check(feed).run { steps += it }
        assertEquals("Limits: read 64 (16 a second), stream 512 (4 a second), up to 2048 markets watched.", lines[0])
        assertTrue(lines[1], lines[1].startsWith("Signed catalog: 3 open markets in "))
        assertTrue(lines[2], lines[2].startsWith("Books: 2 through the key ") && lines[2].contains("2 public "))
        assertTrue(lines[3], lines[3].startsWith("Live feed: first books after ") && lines[3].contains("3 of 3 after "))
        assertEquals(4, lines.size)
        assertEquals(listOf("m1", "m2", "m3"), feed.wanted)
        assertTrue("the feed is closed again", feed.closed)
        assertEquals(4, steps.size)
        // The signed reads are signed; the public ones aren't asked for through the key.
        assertTrue(asked.any { it == "/v3/catalog/markets/m1/book" } && asked.any { it == "/v3/public/catalog/markets/m1/book" })
        assertTrue(asked.any { it.startsWith("/v3/catalog/markets?eventStatus=OPEN_PREGAME&limit=3&marketType=MONEY") })
    }

    @Test
    fun `a live feed that never delivers says so, and what Novig said about it`() = runBlocking {
        novig()
        val silent = check(FakeFeed(afterMs = Long.MAX_VALUE), waitMs = 600).run()
        assertTrue(silent.last(), silent.last().startsWith("Live feed: no book arrived in ") && silent.last().contains("one price at a time"))
        val refused = check(FakeFeed(afterMs = 0, problem = "Novig closed the live feed (1008 GEOLOCATION_EXPIRED)."), waitMs = 2_000).run()
        assertTrue(refused.last(), refused.last().contains("Novig's side of it: Novig closed the live feed (1008 GEOLOCATION_EXPIRED)."))
    }

    @Test
    fun `each step fails into its own line, and an empty catalog skips the rest`() = runBlocking {
        novig(limits = MockResponse().setResponseCode(451).setBody("""{"code":"ANONYMIZED_NETWORK","message":"x"}"""))
        val lines = check(FakeFeed(afterMs = 0)).run()
        assertTrue(lines[0], lines[0].startsWith("Limits: ") && lines[0].contains("ANONYMIZED_NETWORK"))
        assertTrue(lines[1].startsWith("Signed catalog: 3 open markets"))
        server.shutdown(); server = MockWebServer().also { it.start() }
        novig(markets = """{"items":[]}""")
        val empty = NovigLiveCheck(OkHttpClient(), json, signer(), feed = { FakeFeed(0) }, publicBaseUrl = base(), waitMs = 500, samples = 2, watchCount = 3).run()
        assertEquals(2, empty.size)
        assertTrue(empty.last(), empty.last().contains("no open game is listed"))
    }
}
