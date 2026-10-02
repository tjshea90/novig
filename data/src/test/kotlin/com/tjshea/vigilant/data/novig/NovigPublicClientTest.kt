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
    fun `a short 429 on the board is waited out instead of failing the scan`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"items":[{"eventId":"a"}]}"""))
        val paused = ArrayList<Long>()
        val c = NovigPublicClient(OkHttpClient(), json, server.url("").toString().trimEnd('/'), clock = { now }, sleep = { paused += it })
        val events = c.events(listOf("NFL"), emptyList())
        assertEquals(listOf("a"), events.map { it.eventId })
        assertEquals(2, server.requestCount)
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

    /** Novig's own example schedule (docs: GET /v3/limits). */
    private val limitsBody = """{"read":{"capacity":64,"refillPerSec":16},"account":{"capacity":64,"refillPerSec":8},
        "place":{"capacity":256,"refillPerSec":8},"cancel":{"capacity":256,"refillPerSec":16},
        "stream":{"capacity":512,"refillPerSec":4},"history":{"capacity":512,"refillPerSec":4},"maxWatchedMarkets":2048}"""

    private fun keyedDispatch(limits: String = limitsBody) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            if (request.requestUrl!!.encodedPath == "/v3/limits") MockResponse().setBody(limits) else bookFor(request)
    }

    private fun MockWebServer.requests(): List<RecordedRequest> = (0 until requestCount).map { takeRequest() }

    /**
     * Tj, 2026-09-28: "now it is reading the API very slow". With a key, a whole wave of books (10) is in flight
     * when Novig's `read` bucket runs dry, and every one comes back 429 together. That's one refusal: it used to
     * count as ten of the 8 allowed and stop the whole scan (and halve the pace ten times, to 1/s for a minute).
     */
    @Test
    fun `with a key, a refused wave is waited out once and every book still comes`() = runBlocking {
        val arrived = AtomicInteger()
        val refused = AtomicInteger()
        val seen = HashSet<String>()
        var retried = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath == "/v3/limits") return MockResponse().setBody(limitsBody)
                // One wave, as the edge sends it: every first read that arrives before the client's first retry is refused
                // (Retry-After: 1); once a retry arrives the pause has passed and everything is served. Neither timed nor a
                // fixed count: a busy CI runner spread the wave past a 300 ms window (2026-09-28), a book's retry landed
                // inside it (2026-09-30), and "the first 10 books whenever they arrive" refused late first reads the edge
                // would have served, as a second wave (CI run 36737500463, 2026-09-30).
                val book = request.requestUrl!!.encodedPath
                val refuse = synchronized(seen) {
                    val first = seen.add(book)
                    if (!first) retried = true
                    first && !retried
                }
                arrived.incrementAndGet()
                return if (refuse) {
                    refused.incrementAndGet()
                    MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
                        .setBody("""{"code":"RATE_LIMIT_EXCEEDED","message":"Rate limit exceeded. Please wait before retrying."}""")
                } else {
                    bookFor(request)
                }
            }
        }
        val batch = keyed(client()).books((1..16).map { "m$it" })
        // The wave is what was in flight: the key's 10 reads at most ("ten books are in flight at once"), fewer on a slow runner.
        assertTrue("refused ${refused.get()}", refused.get() in 1..10)
        assertEquals(0, batch.failed)
        assertEquals(16, batch.fetched)
        assertEquals(16, batch.viaKey)
        assertNull(batch.retryAfterSeconds)
    }

    @Test
    fun `a scan reads 30 books at a time with a key (10 in flight), 8 on the public routes`() {
        val c = client()
        assertEquals(8, c.batchSize())
        keyed(c)
        assertEquals(30, c.batchSize())
    }

    @Test
    fun `with a key, ten books are in flight at once`() = runBlocking {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath == "/v3/limits") return MockResponse().setBody(limitsBody)
                val n = inFlight.incrementAndGet()
                maxInFlight.accumulateAndGet(n) { a, b -> maxOf(a, b) }
                Thread.sleep(150)
                inFlight.decrementAndGet()
                return bookFor(request)
            }
        }
        val c = keyed(NovigPublicClient(com.tjshea.vigilant.data.vigilantHttpClient(), json, server.url("").toString().trimEnd('/'), clock = { now }))
        val batch = c.books((1..30).map { "m$it" })
        assertEquals(30, batch.viaKey)
        assertEquals(10, maxInFlight.get())
    }

    @Test
    fun `with a key, books come from the signed route and its own rate limit`() = runBlocking {
        server.dispatcher = keyedDispatch()
        val batch = keyed(client()).books(listOf("m1", "m2"))
        assertEquals(2, batch.viaKey)
        val books = server.requests().filter { it.requestUrl!!.encodedPath.startsWith("/v3/catalog/markets/") }
        assertEquals(2, books.size)
        assertEquals("kid-read", books.first().getHeader("Novig-Key-Id"))
        assertNull(batch.keyProblem)
    }

    @Test
    fun `the key's throttle schedule is read once, free, and paces the key and the websocket`() = runBlocking {
        // A roomier schedule than the documented defaults: Vigilant follows what Novig says.
        server.dispatcher = keyedDispatch(limitsBody.replace(""""read":{"capacity":64,"refillPerSec":16}""", """"read":{"capacity":128,"refillPerSec":40}""").replace("2048", "4096"))
        val push = FakePush(emptyMap())
        val c = keyed(client()).also { it.stream = push }
        c.books(listOf("m1"))
        c.books(listOf("m2"))
        val paths = server.requests().map { it.requestUrl!!.encodedPath }
        assertEquals(1, paths.count { it == "/v3/limits" })
        assertEquals("/v3/limits", paths.first())
        assertEquals(NovigLimits(128, 40.0, 512, 4.0, 4096), c.limits)
        assertEquals(listOf(Triple(512, 4.0, 4096)), push.tuned)
    }

    @Test
    fun `no schedule from Novig keeps the documented pace, and books still come`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestUrl!!.encodedPath == "/v3/limits") MockResponse().setResponseCode(500) else bookFor(request)
        }
        val c = keyed(client())
        assertEquals(1, c.books(listOf("m1")).viaKey)
        assertNull(c.limits)
    }

    @Test
    fun `with a key, the board is read through the key's signed catalog, pages and all`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when {
                    url.encodedPath == "/v3/limits" -> MockResponse().setBody(limitsBody)
                    url.encodedPath == "/v3/catalog/events" && url.queryParameter("after") == null ->
                        MockResponse().setBody("""{"items":[{"eventId":"a"}],"next":"c+1/2="}""")
                    url.encodedPath == "/v3/catalog/events" -> MockResponse().setBody("""{"items":[{"eventId":"b"}]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val events = keyed(client()).events(listOf("NFL", "NCAAF"), listOf("OPEN_PREGAME", "DELAYED"), null)
        assertEquals(listOf("a", "b"), events.map { it.eventId })
        val pages = server.requests().filter { it.requestUrl!!.encodedPath == "/v3/catalog/events" }
        assertEquals(2, pages.size)
        assertTrue(pages.all { it.getHeader("Novig-Signature") != null && it.getHeader("Novig-Key-Id") == "kid-read" })
        // Sent exactly as signed: every reserved character percent-encoded (the cursor included).
        assertEquals("league=NFL%2CNCAAF&status=OPEN_PREGAME%2CDELAYED&limit=1000", pages[0].requestUrl!!.encodedQuery)
        assertEquals("c+1/2=", pages[1].requestUrl!!.queryParameter("after"))
        assertTrue(pages[1].requestUrl!!.encodedQuery!!.endsWith("after=c%2B1%2F2%3D"))
    }

    @Test
    fun `a refused signed catalog falls back to the public board, and stays public for a while`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/v3/limits" -> MockResponse().setBody(limitsBody)
                    path.startsWith("/v3/public/") -> MockResponse().setBody("""{"items":[{"eventId":"p"}]}""")
                    else -> MockResponse().setResponseCode(451).setBody("""{"code":"ANONYMIZED_NETWORK","message":"vpn"}""")
                }
            }
        }
        val c = keyed(client())
        assertEquals(listOf("p"), c.events(listOf("NFL"), emptyList(), null).map { it.eventId })
        assertEquals(listOf("p"), c.events(listOf("NFL"), emptyList(), null).map { it.eventId })
        val paths = server.requests().map { it.requestUrl!!.encodedPath }
        assertEquals(1, paths.count { it == "/v3/catalog/events" }) // not asked again straight away
        assertEquals(2, paths.count { it == "/v3/public/catalog/events" })
    }

    @Test
    fun `query values are encoded the way NOVIG-V3 signs them`() {
        assertEquals("NFL%2CNCAAF", NovigPublicClient.queryEncode("NFL,NCAAF"))
        assertEquals("Serie%20A", NovigPublicClient.queryEncode("Serie A"))
        assertEquals("a-b.c_d~e", NovigPublicClient.queryEncode("a-b.c_d~e"))
        assertEquals("%2B%2F%3D%2A", NovigPublicClient.queryEncode("+/=*"))
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
        val next = c.books(listOf("m4"))
        assertEquals(before + 1, server.requestCount)
        // ...and, never having tried the key, it still has the reason to give (Tj, 2026-10-02: "the novig scanning was going very slow").
        assertNull(next.keyProblem)
        assertTrue(c.keyDown(now)!!.contains("VPN"))
    }

    @Test
    fun `Novig's account lock on the key is said by every scan it slows, until the key is tried again`() = runBlocking {
        // Tj's v0.44.1 Diagnostics: 423 on every signed route from 01:33, the public routes answering.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestUrl!!.encodedPath.startsWith("/v3/public/")) bookFor(request)
                else MockResponse().setResponseCode(423).setBody("""{"code":"ACCOUNT_LOCKED","message":"locked"}""")
        }
        now = 1_000_000L
        val c = keyed(client())
        assertNull(c.keyDown(now))
        c.books(listOf("m1"))
        val why = c.keyDown(now)!!
        assertTrue(why, why.contains("ACCOUNT_LOCKED"))
        // Ten minutes on, the key is tried again: nothing to say until it is refused again.
        now += 10 * 60_000L
        assertNull(c.keyDown(now))
        // No key at all: never a key problem.
        assertNull(client().keyDown(now))
    }

    /** The key's websocket, faked: it holds m1 and m2 (Tj, 2026-09-28: "taking full advantage of the novig API key"). */
    private class FakePush(val held: Map<String, NovigBook>) : com.tjshea.vigilant.data.novig.stream.PushedBooks {
        val watched = ArrayList<List<String>>()
        val tuned = ArrayList<Triple<Int, Double, Int>>()
        override fun watch(marketIds: Collection<String>) { watched += marketIds.toList() }
        override fun live(marketIds: Collection<String>) = held.filterKeys { it in marketIds }
        override fun problemSince(sinceMs: Long): String? = null
        override fun close() {}
        override fun tune(capacity: Int, refillPerSec: Double, maxWatchedMarkets: Int) { tuned += Triple(capacity, refillPerSec, maxWatchedMarkets) }
    }

    @Test
    fun `books the key's websocket holds are served with no request, the rest by the key's REST route`() = runBlocking {
        server.dispatcher = keyedDispatch()
        val held = listOf("m1", "m2").associateWith { NovigBook(it, 99, mapOf("x" to listOf(BidLevel(510, 7))), 0) }
        val push = FakePush(held)
        val c = keyed(client()).also { it.stream = push }
        val progress = ArrayList<Pair<Int, Int>>()
        val batch = c.books(listOf("m1", "m2", "m3")) { d, t -> progress += d to t }
        val books = server.requests().filter { it.requestUrl!!.encodedPath.endsWith("/book") }
        assertEquals(1, books.size) // m3 only
        assertTrue(books.single().requestUrl!!.encodedPath.endsWith("/m3/book"))
        assertEquals(setOf("m1", "m2", "m3"), batch.books.keys)
        assertEquals(99L, batch.books.getValue("m1").seq)
        assertEquals(2, batch.viaPush)
        assertEquals(3, batch.fetched)
        assertEquals(1, batch.viaKey)
        assertEquals(2 to 3, progress.first())
        assertEquals(3 to 3, progress.last())
        // Asked to watch, the client passes the plan on.
        c.watch(listOf("m1", "m9"))
        assertEquals(listOf(listOf("m1", "m9")), push.watched)
        assertEquals(held.keys, c.pushed(listOf("m1", "m2", "m3")).keys)
    }

    @Test
    fun `without a usable key the websocket is neither asked nor used`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = bookFor(request)
        }
        val push = FakePush(mapOf("m1" to NovigBook("m1", 99, emptyMap(), 0)))
        val c = client().also { it.stream = push } // a stream but no key
        c.watch(listOf("m1"))
        assertTrue(push.watched.isEmpty())
        val batch = c.books(listOf("m1"))
        assertEquals(0, batch.viaPush)
        assertEquals(1, server.requestCount)
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
