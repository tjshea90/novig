package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.engine.FeeCharge
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NovigPublicClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private var now = 1_000L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client() = NovigPublicClient(OkHttpClient(), json, server.url("").toString().trimEnd('/'), clock = { now })

    @Test
    fun `lists events and markets with no key or signature`() = runBlocking {
        server.enqueue(MockResponse().setBody(Fixtures.novigEvents))
        server.enqueue(MockResponse().setBody(Fixtures.novigMarkets))

        val events = client().events(listOf("NFL"), listOf("OPEN_PREGAME"), startsBefore = 99L)
        val markets = client().markets(listOf("NFL", "Serie A"), listOf("MONEY", "SPREAD"), listOf("OPEN_PREGAME"))

        val e = events.single()
        assertEquals(Matchup("Baltimore Ravens", "Dallas Cowboys"), e.matchup)
        assertEquals(4, markets.size)
        val ml = markets.first { it.marketType == "MONEY" }
        assertEquals(0.03, ml.fee!!.coefficient, 0.0)
        assertEquals(FeeCharge.WHEN_LIVE, ml.fee!!.charged)

        val r1 = server.takeRequest()
        assertEquals("/v3/public/catalog/events", r1.requestUrl!!.encodedPath)
        assertEquals("99", r1.requestUrl!!.queryParameter("startsBefore"))
        assertNull(r1.getHeader("Novig-Signature"))
        val r2 = server.takeRequest().requestUrl!!
        assertEquals("NFL,Serie A", r2.queryParameter("league"))
        assertEquals("MONEY,SPREAD", r2.queryParameter("marketType"))
    }

    @Test
    fun `follows the next cursor across pages`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[{"eventId":"a"}],"next":"cursor-1"}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"eventId":"b"}]}"""))
        val events = client().events(listOf("NFL"), emptyList())
        assertEquals(listOf("a", "b"), events.map { it.eventId })
        server.takeRequest()
        assertEquals("cursor-1", server.takeRequest().requestUrl!!.queryParameter("after"))
    }

    @Test
    fun `book levels aggregate by price and flip into take prices for the other side`() = runBlocking {
        server.enqueue(MockResponse().setBody(Fixtures.novigMarkets))
        server.enqueue(MockResponse().setBody(Fixtures.mlBook).setHeader("ETag", "\"x-2020\""))
        val c = client()
        val market = c.markets(emptyList(), emptyList(), emptyList()).first { it.marketId == Fixtures.ML_MARKET }
        val book = c.books(listOf(Fixtures.ML_MARKET)).books.getValue(Fixtures.ML_MARKET)

        assertEquals(BidLevel(375, 350_000), book.bidsByOutcome.getValue(Fixtures.ML_DAL)[1])
        // Buying DAL means matching the BAL bids: 1 - 0.615 = 0.385, 248,541 contracts deep.
        val dal = book.takeLadder(market, Fixtures.ML_DAL)
        assertEquals(0.385, dal.first().price, 1e-12)
        assertEquals(248_541L, dal.first().contracts)
        assertEquals(0.62, book.takeLadder(market, Fixtures.ML_BAL).first().price, 1e-12)
    }

    @Test
    fun `an unchanged book is revalidated with its ETag and served from cache on 304`() = runBlocking {
        val hits = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (hits.getAndIncrement() == 0) {
                    MockResponse().setBody(Fixtures.mlBook).setHeader("ETag", "\"m-2020\"")
                } else {
                    assertEquals("\"m-2020\"", request.getHeader("If-None-Match"))
                    MockResponse().setResponseCode(304)
                }
        }
        val c = client()
        c.books(listOf(Fixtures.ML_MARKET))
        now = 5_000L
        val second = c.books(listOf(Fixtures.ML_MARKET))
        assertEquals(1, second.notModified)
        assertEquals(0, second.fetched)
        val book = second.books.getValue(Fixtures.ML_MARKET)
        assertEquals(2020L, book.seq)
        assertEquals(5_000L, book.fetchedAtMs)
    }

    @Test
    fun `a 429 stops the batch and keeps what was cached`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(429).setHeader("Retry-After", "7").setBody("""{"code":"RATE_LIMIT_EXCEEDED","message":"slow"}""")
        }
        val batch = client().books((1..20).map { "m$it" })
        assertEquals(7, batch.retryAfterSeconds)
        assertTrue(batch.failed in 1..4) // at most one wave of in-flight requests, then it stops
        assertTrue(batch.lastError!!.contains("429"))
    }

    @Test
    fun `a short Retry-After pauses and retries instead of dropping books`() = runBlocking {
        val calls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (calls.getAndIncrement() == 0) {
                    MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
                } else {
                    MockResponse().setBody(Fixtures.mlBook.replace(Fixtures.ML_MARKET, request.requestUrl!!.pathSegments[4]))
                }
        }
        val batch = client().books(listOf("m1", "m2", "m3"))
        assertEquals(0, batch.failed)
        assertEquals(3, batch.fetched)
        assertNull(batch.retryAfterSeconds)
    }

    @Test
    fun `the edge's bare HTML 403 reads as a slow-down, not a bad request`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("<html>Request blocked</html>"))
        val batch = client().books(listOf("m1"))
        assertEquals(30, batch.retryAfterSeconds)
        assertTrue(batch.lastError!!.contains("edge"))
    }

    @Test
    fun `a market with an unreadable fee keeps a null fee instead of pretending it's free`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[{"marketId":"m","fee":{"coefficient":"x","charged":"WHEN_LIVE"},"outcomes":[]}]}"""))
        assertNull(client().markets(emptyList(), emptyList(), emptyList()).single().fee)
    }

    private fun bookFor(request: RecordedRequest) =
        MockResponse().setBody(Fixtures.mlBook.replace(Fixtures.ML_MARKET, request.requestUrl!!.pathSegments.dropLast(1).last()))

    @Test
    fun `books are paced - never more than three in flight and no faster than the rate after the burst`() = runBlocking {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val n = inFlight.incrementAndGet()
                maxInFlight.accumulateAndGet(n) { a, b -> maxOf(a, b) }
                Thread.sleep(20)
                inFlight.decrementAndGet()
                return bookFor(request)
            }
        }
        // 4/s with a burst of 2: six books need at least one second.
        val c = NovigPublicClient(OkHttpClient(), json, server.url("").toString().trimEnd('/'), publicRate = 4.0, publicBurst = 2)
        val progress = ArrayList<Int>()
        val t0 = System.currentTimeMillis()
        val batch = c.books((1..6).map { "m$it" }) { done, _ -> synchronized(progress) { progress += done } }
        assertEquals(6, batch.fetched)
        assertTrue(maxInFlight.get() <= 3)
        assertTrue("took ${System.currentTimeMillis() - t0}ms", System.currentTimeMillis() - t0 >= 950)
        assertEquals(6, progress.max())
    }

    @Test
    fun `the catalog shares the same pace as books`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody("""{"items":[]}""")) }
        val c = NovigPublicClient(OkHttpClient(), json, server.url("").toString().trimEnd('/'), publicRate = 2.0, publicBurst = 1)
        val t0 = System.currentTimeMillis()
        repeat(3) { c.events(listOf("NFL"), emptyList()) }
        assertTrue(System.currentTimeMillis() - t0 >= 900)
    }

    private val fakeKey = object : com.tjshea.vigilant.data.novig.signing.NovigSigningKey {
        override val keyId = "kid-read"
        override val algorithm = com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm.P256
        override fun sign(message: ByteArray) = ByteArray(70)
    }

    private fun keyed(c: NovigPublicClient) = c.also {
        it.keyed = com.tjshea.vigilant.data.novig.signing.NovigSignedClient(OkHttpClient(), json, fakeKey, server.url("").toString().trimEnd('/'))
    }

    @Test
    fun `with a key, books come from the signed route and its own rate limit`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = bookFor(request)
        }
        val batch = keyed(client()).books(listOf("m1", "m2"))
        assertEquals(2, batch.viaKey)
        val r = server.takeRequest()
        assertTrue(r.requestUrl!!.encodedPath.startsWith("/v3/catalog/markets/"))
        assertEquals("kid-read", r.getHeader("Novig-Key-Id"))
        assertNull(batch.keyProblem)
    }

    @Test
    fun `a refused key finishes the scan on public routes and says why`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestUrl!!.encodedPath.startsWith("/v3/public/")) bookFor(request)
                else MockResponse().setResponseCode(451).setBody("""{"code":"ANONYMIZED_NETWORK","message":"vpn"}""")
        }
        val c = keyed(client())
        val batch = c.books(listOf("m1", "m2", "m3"))
        assertEquals(3, batch.fetched)
        assertEquals(0, batch.viaKey)
        assertTrue(batch.keyProblem!!.contains("VPN"))
        // The next scan doesn't keep knocking on the key route.
        val before = server.requestCount
        c.books(listOf("m4"))
        assertEquals(before + 1, server.requestCount)
    }

    @Test
    fun `a key that can't sign (missing from the phone's keystore) falls back to public prices`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = bookFor(request)
        }
        val broken = object : com.tjshea.vigilant.data.novig.signing.NovigSigningKey {
            override val keyId = "kid-gone"
            override val algorithm = com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray): ByteArray = error("Novig key alias is missing from the Keystore; run setup again")
        }
        val c = client().also {
            it.keyed = com.tjshea.vigilant.data.novig.signing.NovigSignedClient(OkHttpClient(), json, broken, server.url("").toString().trimEnd('/'))
        }
        val batch = c.books(listOf("m1", "m2"))
        assertEquals(2, batch.fetched)
        assertEquals(0, batch.viaKey)
        assertTrue(batch.keyProblem!!, batch.keyProblem!!.contains("connect it again"))
    }
}
