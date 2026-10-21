package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BoltOdds' REST client, websocket lane and feed against a mock server speaking what BOLTODDS_API.md documents and what the real server was seen to do on 2026-10-12 (HTTP 403 `{"detail":"Invalid API token"}`
 * for a bad key; the socket accepted, then closed with code 1008 "Invalid token"). No live key has been used.
 */
class BoltOddsLiveTest {
    /** The fixtures play tomorrow (UTC), so the games are always upcoming whichever day the suite runs. */
    private val DAY: java.time.LocalDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1)

    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rest = CopyOnWriteArrayList<RecordedRequest>()
    private val upgrades = CopyOnWriteArrayList<RecordedRequest>()
    private val serverSockets = CopyOnWriteArrayList<WebSocket>()
    private val received = CopyOnWriteArrayList<JsonObject>()
    private val sleeps = CopyOnWriteArrayList<Long>()

    @Volatile private var snapshotBody = "[]"
    @Volatile private var snapshotCode = 200
    @Volatile private var infoBody = """{"sports":["NBA","MLB","Wimbledon (M)"],"sportsbooks":["draftkings","fanduel","novig","neobet"]}"""
    @Volatile private var marketsBody = """{"draftkings":{"NBA":["Moneyline","Spread","Total","Player Points","Fight To Not Go Distance"],"MLB":["Moneyline"]},"fanduel":{"NBA":["Moneyline","Rebounds"]}}"""
    @Volatile private var validKeys = setOf("good")
    @Volatile private var greet = true
    @Volatile private var answerSubscribe = true

    /** How the mock server packs its frames: text, a binary frame of UTF-8, gzip or zlib. */
    @Volatile private var packing = "text"

    private fun WebSocket.frame(text: String) {
        when (packing) {
            "utf8" -> send(text.encodeUtf8())
            "gzip" -> send(java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(text.toByteArray()) } }.toByteArray().toByteString())
            "zlib" -> send(java.io.ByteArrayOutputStream().also { o -> java.util.zip.DeflaterOutputStream(o).use { it.write(text.toByteArray()) } }.toByteArray().toByteString())
            else -> send(text)
        }
    }

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            serverSockets += webSocket
            if (greet) webSocket.frame("""{"action": "socket_connected"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = Json.parseToJsonElement(text).jsonObject
            received += o
            if (answerSubscribe && o["action"]?.jsonPrimitive?.content == "subscribe") webSocket.frame("""{"action": "subscription_updated", "message": "You have successfully updated your subscription filters."}""")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = request.requestUrl?.queryParameter("key")
                if (request.getHeader("Upgrade")?.equals("websocket", ignoreCase = true) == true) {
                    upgrades += request
                    return if (key in validKeys) MockResponse().withWebSocketUpgrade(serverListener)
                    else MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.close(1008, "Invalid token") }
                    })
                }
                rest += request
                if (key !in validKeys) return MockResponse().setResponseCode(403).setBody(if (key == null) """{"detail":"Missing API token"}""" else """{"detail":"Invalid API token"}""")
                return when (request.requestUrl!!.encodedPath) {
                    "/api/get_info" -> MockResponse().setBody(infoBody)
                    "/api/get_markets" -> MockResponse().setBody(marketsBody)
                    "/api/snapshot" -> MockResponse().setResponseCode(snapshotCode).setBody(snapshotBody)
                    "/api/get_games" -> MockResponse().setBody("""{"Boston Celtics vs New York Knicks, ${DAY}, 07":{"when":"${DAY}, 07:30 PM","game":"Boston Celtics vs New York Knicks, ${DAY}, 07","orig_teams":"Boston Celtics vs New York Knicks","universal_id":"abc123","sport":"NBA"}}""")
                    "/api/get_parlays" -> MockResponse().setBody("""[{"odds":"+1746","total_bets":3238,"type":"SGP_PLUS","legs":{}}]""")
                    "/api/playbyplay/user" -> MockResponse().setResponseCode(403).setBody("""{"detail":"Not available on your plan"}""")
                    "/api/livescores/user" -> MockResponse().setBody("""{"games":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { scope.cancel(); server.shutdown() }

    private val meter = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))
    private fun base() = server.url("/api").toString().trimEnd('/')
    private fun wsUrl() = base().replaceFirst("http", "ws")
    private fun client(keys: List<String> = listOf("good")) = BoltOddsClient(OkHttpClient(), { keys }, meter, base(), sleep = { sleeps += it })

    private fun stream(client: BoltOddsClient, name: String = "lines", keeps: (com.tjshea.vigilant.data.reference.LineKind) -> Boolean = { true }, greetingWaitMs: Long = 4_000L) =
        BoltOddsStream(
            name, BoltOddsStream.socketClient(OkHttpClient()), client, BoltBoard(keeps = keeps), scope, wsUrl(),
            backoffMinMs = 150, backoffMaxMs = 400, resubscribeGapMs = 250, resnapshotGapMs = 300, idleCloseMs = 60_000, checkEveryMs = 50, greetingWaitMs = greetingWaitMs,
        )

    private suspend fun until(timeoutMs: Long = 8_000, check: () -> Boolean) = withTimeout(timeoutMs) { while (!check()) delay(20) }

    private fun game(book: String, ml1: String = "-150", ml2: String = "+130", at: String = "${DAY}T14:00:00+00:00", sport: String = "NBA", action: String = "initial_state") =
        """{"timestamp":"$at","action":"$action","data":{"sport":"$sport","sportsbook":"$book","game":"Boston Celtics vs New York Knicks, ${DAY}, 07","home_team":"Boston Celtics","away_team":"New York Knicks","info":{"id":1,"game":"Boston Celtics vs New York Knicks, ${DAY}, 07","when":"${DAY}, 07:30 PM","link":"x"},"outcomes":{"Boston Celtics Moneyline":{"odds":"$ml1","outcome_name":"Moneyline","outcome_line":null,"outcome_over_under":null,"outcome_target":"Boston Celtics"},"New York Knicks Moneyline":{"odds":"$ml2","outcome_name":"Moneyline","outcome_line":null,"outcome_over_under":null,"outcome_target":"New York Knicks"}}}}"""

    private val filters = BoltFilters(sports = listOf("NBA"), sportsbooks = listOf("draftkings", "fanduel"), markets = listOf("Moneyline"))

    // ---- REST client ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test fun `get_info and get_markets read the key's lists, and the key travels as the query parameter`() = runBlocking {
        val c = client()
        val info = c.info()
        assertEquals(listOf("NBA", "MLB", "Wimbledon (M)"), info.sports)
        assertEquals(listOf("draftkings", "fanduel", "novig", "neobet"), info.sportsbooks)
        val m = c.markets(listOf("NBA", "MLB"), listOf("draftkings", "fanduel"))
        assertEquals(listOf("Moneyline", "Spread", "Total", "Player Points", "Fight To Not Go Distance"), m.getValue("draftkings").getValue("NBA"))
        val req = rest.last { it.requestUrl!!.encodedPath == "/api/get_markets" }
        assertEquals("NBA,MLB", req.requestUrl!!.queryParameter("sports"))
        assertEquals("draftkings,fanduel", req.requestUrl!!.queryParameter("sportsbooks"))
        assertEquals("good", req.requestUrl!!.queryParameter("key"))
        assertEquals(2, c.requests)
    }

    @Test fun `a refused key is skipped for the next, and with none left the message carries no key`() = runBlocking {
        val c = client(listOf("badbadbad", "good"))
        assertEquals(3, c.info().sports.size)
        assertEquals(listOf("badbadbad", "good"), rest.map { it.requestUrl!!.queryParameter("key") })
        // the refused key now rests: the next call goes straight to the good one
        rest.clear()
        c.info()
        assertEquals(listOf("good"), rest.map { it.requestUrl!!.queryParameter("key") })
        val only = client(listOf("justwrongkey"))
        try { only.info(); throw AssertionError("expected a refusal") } catch (e: BoltOddsException) {
            assertFalse("no key in ${e.message}", e.message!!.contains("justwrongkey"))
            assertTrue(e.message!!, e.message!!.contains("BoltOdds"))
        }
    }

    @Test fun `GETs keep under 12 a minute with 3 at once, the snapshot under 6 a minute`() = runBlocking {
        val c = client()
        repeat(3) { c.info() }
        assertTrue("the first three go at once: $sleeps", sleeps.none { it > 0 })
        c.info()
        assertTrue("the fourth waits for a token: $sleeps", sleeps.any { it in 3_000..5_300 })
        sleeps.clear()
        c.snapshotStream(filters) { }
        c.snapshotStream(filters) { }
        assertTrue("a second snapshot waits its own gap: $sleeps", sleeps.any { it >= 8_000 })
    }

    @Test fun `the snapshot is streamed with repeated filter parameters`() = runBlocking {
        snapshotBody = "[" + game("draftkings") + "," + game("fanduel") + "]"
        val got = ArrayList<String>()
        val n = client().snapshotStream(BoltFilters(sports = listOf("NBA", "MLB"), sportsbooks = listOf("draftkings"), markets = listOf("To Win By KO, TKO, DQ"))) { got += it }
        assertEquals(2, n)
        assertEquals(2, got.size)
        val url = rest.last { it.requestUrl!!.encodedPath == "/api/snapshot" }.requestUrl!!
        assertEquals(listOf("NBA", "MLB"), url.queryParameterValues("sports"))
        assertEquals(listOf("To Win By KO, TKO, DQ"), url.queryParameterValues("markets"))
    }

    @Test fun `a snapshot answered with an error object fails with its text, an invalid key is refused`() = runBlocking {
        snapshotBody = """{"detail":"Daily token quota exceeded"}"""
        try { client().snapshotStream(filters) { }; throw AssertionError("expected a failure") } catch (e: BoltOddsException) {
            assertTrue(e.message!!, e.message!!.contains("Daily token quota exceeded"))
        }
        snapshotCode = 429
        try { client().snapshotStream(filters) { }; throw AssertionError("expected a failure") } catch (e: BoltOddsException) {
            assertTrue(e.message!!, e.message!!.contains("429"))
        }
    }

    @Test fun `redact cuts every saved key out of a text`() {
        val c = client(listOf("abcdef123456", "short"))
        assertEquals("GET /x?key=…3456&a=1", c.redact("GET /x?key=abcdef123456&a=1"))
        assertEquals("short", c.redact("short"))
    }

    // ---- the websocket lane ----------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test fun `it connects with the key, subscribes with the filters, reads the snapshot and keeps the board current`() = runBlocking {
        snapshotBody = "[" + game("draftkings") + "," + game("fanduel", ml1 = "-155", ml2 = "+135") + "]"
        val s = stream(client())
        s.want(filters)
        assertTrue(s.awaitReady(8_000))
        assertEquals("good", upgrades.single().requestUrl!!.queryParameter("key"))
        val sub = received.first { it["action"]?.jsonPrimitive?.content == "subscribe" }["filters"]!!.jsonObject
        assertEquals(listOf("NBA"), sub["sports"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("draftkings", "fanduel"), sub["sportsbooks"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("Moneyline"), sub["markets"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertNull(sub["games"])
        val snap = rest.first { it.requestUrl!!.encodedPath == "/api/snapshot" }.requestUrl!!
        assertEquals(listOf("draftkings", "fanduel"), snap.queryParameterValues("sportsbooks"))
        assertEquals(2, s.board.gamesOf(listOf("NBA")).size)
        // a live line moves, a book removes a line
        serverSockets.last().frame(game("draftkings", ml1 = "-170", ml2 = "+148", at = "${DAY}T14:30:00+00:00", action = "game_update"))
        until { s.board.gamesOf(listOf("NBA")).any { g -> g.sportsbook == "draftkings" && g.outcomes.values.any { it.odds == -170 } } }
        assertTrue(s.live)
        assertNotNull("a book that spoke is vouched for", s.aliveAt("draftkings", "NBA"))
        assertNull("a book that never spoke is not", s.aliveAt("caesars", "NBA"))
        assertTrue(s.examples.containsKey("game_update") && s.examples.containsKey("socket_connected") && s.examples.containsKey("subscription_updated"))
        s.close()
        assertEquals(BoltOddsStream.State.OFF, s.state)
        assertTrue(s.board.gamesOf(listOf("NBA")).isEmpty())
    }

    @Test fun `a refused key closes with 1008, is set aside, and the lane does not hammer the server`() = runBlocking {
        validKeys = setOf("other")
        val c = client(listOf("badone"))
        val s = stream(c)
        s.want(filters)
        until { s.state == BoltOddsStream.State.DOWN && s.drops >= 1 }
        delay(900)
        assertEquals("one connection, then no key to try", 1, upgrades.size)
        assertTrue(s.lastError!!, s.lastError!!.contains("refused the key"))
        assertNull(c.nextKey())
        s.close()
    }

    @Test fun `a dropped connection empties the board and comes back, reading the snapshot again`() = runBlocking {
        snapshotBody = "[" + game("draftkings") + "]"
        val s = stream(client())
        s.want(filters)
        assertTrue(s.awaitReady(8_000))
        assertEquals(1, s.board.gamesOf(listOf("NBA")).size)
        serverSockets.last().close(1001, "going away")
        until { s.drops >= 1 }
        assertNull("not vouched for while it is down", s.aliveAt("draftkings", "NBA"))
        until { upgrades.size >= 2 && s.live && s.readyAtMs > 0 }
        assertEquals(2, rest.count { it.requestUrl!!.encodedPath == "/api/snapshot" })
        assertEquals(1, s.board.gamesOf(listOf("NBA")).size)
        s.close()
    }

    @Test fun `a server that never greets is subscribed anyway after the greeting wait, and says so`() = runBlocking {
        greet = false
        snapshotBody = "[" + game("draftkings") + "]"
        val s = stream(client(), greetingWaitMs = 300)
        s.want(filters)
        assertTrue("subscribed without a greeting", s.awaitReady(8_000))
        assertTrue(received.any { it["action"]?.jsonPrimitive?.content == "subscribe" })
        assertEquals(1, s.greetingFallbacks)
        assertEquals(1, s.board.gamesOf(listOf("NBA")).size)
        assertTrue(s.openInfo!!, s.openInfo!!.startsWith("HTTP 101"))
        // the server answered the subscription, so the connection has spoken and is vouched for
        until { s.framesThisConn > 0 }
        assertNotNull(s.aliveAt("draftkings", "NBA"))
        s.close()
    }

    @Test fun `a connection that has not sent one frame is not vouched for, whatever its state`() = runBlocking {
        greet = false
        answerSubscribe = false
        snapshotBody = "[" + game("draftkings") + "]"
        val s = stream(client(), greetingWaitMs = 300)
        s.want(filters)
        assertTrue(s.awaitReady(8_000))
        assertEquals(0L, s.framesThisConn)
        assertNull("silent socket", s.aliveAt("draftkings", "NBA"))
        serverSockets.last().frame("""{"action": "ping"}""")
        until { s.framesThisConn > 0 }
        assertNotNull("it spoke", s.aliveAt("draftkings", "NBA"))
        s.close()
    }

    @Test fun `binary frames, plain UTF-8, gzip or zlib, are read like text ones`() = runBlocking {
        for (kind in listOf("utf8", "gzip", "zlib")) {
            packing = kind
            received.clear()
            snapshotBody = "[" + game("draftkings") + "]"
            val s = stream(client())
            s.want(filters)
            assertTrue("$kind: ready", s.awaitReady(8_000))
            serverSockets.last().frame(game("draftkings", ml1 = "-170", ml2 = "+148", at = "${DAY}T14:30:00+00:00", action = "game_update"))
            until { s.board.gamesOf(listOf("NBA")).any { g -> g.outcomes.values.any { it.odds == -170 } } }
            assertTrue("$kind: counted as binary (${s.binaryFrames} binary, ${s.textFrames} text)", s.binaryFrames >= 2 && s.textFrames == 0L)
            assertTrue("$kind: raw frames kept for Diagnostics", s.rawFrames.isNotEmpty() && s.rawFrames.first().startsWith("binary"))
            assertTrue("$kind: subscribed after the binary greeting", received.any { it["action"]?.jsonPrimitive?.content == "subscribe" })
            assertEquals("$kind: no fallback needed", 0, s.greetingFallbacks)
            s.close()
        }
    }

    @Test fun `a changed want re-subscribes on the same connection no sooner than the gap, and only a growing one re-reads the snapshot`() = runBlocking {
        snapshotBody = "[" + game("draftkings") + "]"
        val s = stream(client())
        s.want(filters)
        assertTrue(s.awaitReady(8_000))
        val subs = { received.count { it["action"]?.jsonPrimitive?.content == "subscribe" } }
        assertEquals(1, subs())
        // fewer books: re-subscribed, no new snapshot
        s.want(filters.copy(sportsbooks = listOf("draftkings")))
        until { subs() == 2 }
        assertEquals(1, rest.count { it.requestUrl!!.encodedPath == "/api/snapshot" })
        // more sports: re-subscribed and the snapshot is read again
        s.want(filters.copy(sportsbooks = listOf("draftkings"), sports = listOf("NBA", "MLB")))
        until { subs() == 3 && rest.count { it.requestUrl!!.encodedPath == "/api/snapshot" } == 2 }
        assertEquals("still one connection", 1, upgrades.size)
        s.close()
    }

    @Test fun `a re-subscription refusal is asked again after the gap`() = runBlocking {
        val s = stream(client())
        s.want(filters)
        until { s.live && received.any { it["action"]?.jsonPrimitive?.content == "subscribe" } }
        val before = received.count { it["action"]?.jsonPrimitive?.content == "subscribe" }
        serverSockets.last().send("""{"action": "error", "message": "Re-subscription rate limit hit. Please wait 5 seconds before re-subscribing."}""")
        until { received.count { it["action"]?.jsonPrimitive?.content == "subscribe" } > before }
        s.close()
    }

    @Test fun `a refusal that says wait minutes is not asked again within seconds`() = runBlocking {
        val s = stream(client())
        s.want(filters)
        until { s.live && received.any { it["action"]?.jsonPrimitive?.content == "subscribe" } }
        val before = received.count { it["action"]?.jsonPrimitive?.content == "subscribe" }
        serverSockets.last().send("""{"action": "error", "message": "Re-subscription rate limit hit. Please wait 5 minutes before re-subscribing."}""")
        kotlinx.coroutines.delay(7_000)
        assertEquals(before, received.count { it["action"]?.jsonPrimitive?.content == "subscribe" })
        s.close()
    }

    @Test fun `a book_clear empties that book and the snapshot is read again after the gap`() = runBlocking {
        snapshotBody = "[" + game("draftkings") + "," + game("fanduel") + "]"
        val s = stream(client())
        s.want(filters)
        assertTrue(s.awaitReady(8_000))
        serverSockets.last().send("""{"timestamp":"${DAY}T14:40:00+00:00","action":"book_clear","data":{"sport":{},"sportsbook":"fanduel","game":{},"home_team":{},"away_team":{},"info":{},"outcomes":{}}}""")
        until { rest.count { it.requestUrl!!.encodedPath == "/api/snapshot" } == 2 }
        until { s.board.gamesOf(listOf("NBA")).size == 2 }
        s.close()
    }

    // ---- the feed ------------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test fun `with the socket switch off the feed reads the REST snapshot, opens no socket, and reads it at most every 12 seconds`() = runBlocking {
        val c = client()
        val feed = BoltOddsFeed(c, OkHttpClient(), scope, wsUrl = wsUrl())
        snapshotBody = "[" + game("draftkings") + "," + game("fanduel", ml1 = "-155", ml2 = "+135") + "]"
        val off = ScanSettings(leagues = setOf("NBA", "MLB"), referenceBooks = listOf("draftkings", "fanduel")).let { it.copy(gates = it.gates.copy(boltSocket = false)) }
        val events = feed.events(Leagues.byNovigName("NBA")!!, off, props = false)
        assertEquals(setOf("draftkings", "fanduel"), events.single().markets.map { it.bookKey }.toSet())
        assertEquals("no socket was opened", 0, upgrades.size)
        assertFalse(feed.connected)
        val snaps = rest.count { it.requestUrl!!.encodedPath == "/api/snapshot" }
        assertEquals(1, snaps)
        // The same scan again within the gap spends no request; the quotes are only as old as the read (no socket vouches for them).
        feed.events(Leagues.byNovigName("NBA")!!, off, props = false)
        assertEquals(1, rest.count { it.requestUrl!!.encodedPath == "/api/snapshot" })
        // Switching the socket on again opens it.
        val on = off.copy(gates = off.gates.copy(boltSocket = true))
        feed.events(Leagues.byNovigName("NBA")!!, on, props = false)
        assertTrue("the socket is opened once the switch is on", upgrades.size >= 1)
        feed.close()
    }

    @Test fun `the feed subscribes the sports, books and market names it can use and serves the scan from the board`() = runBlocking {
        val c = client()
        val feed = BoltOddsFeed(c, OkHttpClient(), scope, wsUrl = wsUrl())
        snapshotBody = "[" + game("draftkings") + "," + game("fanduel", ml1 = "-155", ml2 = "+135") + "]"
        val settings = ScanSettings(leagues = setOf("NBA", "MLB"), referenceBooks = listOf("draftkings", "fanduel"))
        val events = feed.events(Leagues.byNovigName("NBA")!!, settings, props = false)
        val subs = received.filter { it["action"]?.jsonPrimitive?.content == "subscribe" }.map { it["filters"]!!.jsonObject }
        val sub = subs.first { f -> f["markets"]!!.jsonArray.any { it.jsonPrimitive.content == "Moneyline" } }
        assertEquals(listOf("MLB", "NBA"), sub["sports"]!!.jsonArray.map { it.jsonPrimitive.content })
        // novig and neobet are not the fair line's books (novig is excluded, neobet is not among the picked)
        assertEquals(listOf("draftkings", "fanduel"), sub["sportsbooks"]!!.jsonArray.map { it.jsonPrimitive.content })
        // only market names the table can use as game lines; the prop names go to the props socket
        assertEquals(listOf("Moneyline", "Spread", "Total"), sub["markets"]!!.jsonArray.map { it.jsonPrimitive.content })
        // the props socket asks for the prop names the table can use (Rebounds, Player Points), never the lines or a prop with no Novig market
        val propsSub = subs.first { f -> f["markets"]!!.jsonArray.any { it.jsonPrimitive.content == "Rebounds" } }
        assertEquals(listOf("Player Points", "Rebounds"), propsSub["markets"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(1, events.size)
        assertEquals(setOf("draftkings", "fanduel"), events.single().markets.map { it.bookKey }.toSet())
        // the same call again reads the board and spends no request
        val requestsBefore = c.requests
        feed.events(Leagues.byNovigName("NBA")!!, settings, props = false)
        assertEquals(requestsBefore, c.requests)
        // MLB has no game on the board
        assertTrue(feed.events(Leagues.byNovigName("MLB")!!, settings, props = false).isEmpty())
        feed.close()
    }

    @Test fun `a socket that cannot connect fails the read with the reason, and a league BoltOdds lacks is not asked`() = runBlocking {
        validKeys = setOf("nope")
        val feed = BoltOddsFeed(client(listOf("good")), OkHttpClient(), scope, wsUrl = wsUrl())
        val settings = ScanSettings(leagues = setOf("NBA"), referenceBooks = listOf("draftkings"))
        try { feed.events(Leagues.byNovigName("NBA")!!, settings, props = false); throw AssertionError("expected a failure") } catch (e: com.tjshea.vigilant.data.reference.ReferenceException) {
            assertTrue(e.message!!, e.message!!.contains("BoltOdds"))
        }
        assertTrue(BoltGamesSource(feed).supports(Leagues.byNovigName("NBA")!!))
        assertFalse(BoltGamesSource(feed).supports(Leagues.byNovigName("ATP")!!))
        feed.close()
    }

    @Test fun `the games source and the props source answer for their own socket`() = runBlocking {
        val feed = BoltOddsFeed(client(), OkHttpClient(), scope, wsUrl = wsUrl())
        val props = """{"timestamp":"${DAY}T14:00:00+00:00","action":"initial_state","data":{"sport":"NBA","sportsbook":"draftkings","game":"Boston Celtics vs New York Knicks, ${DAY}, 07","home_team":"Boston Celtics","away_team":"New York Knicks","info":{"id":1,"game":"g","when":"${DAY}, 07:30 PM","link":"x"},"outcomes":{
            "o":{"odds":"-115","outcome_name":"Player Points","outcome_line":"27.5","outcome_over_under":"Over","outcome_target":"Jayson Tatum"},
            "u":{"odds":"-105","outcome_name":"Player Points","outcome_line":"27.5","outcome_over_under":"Under","outcome_target":"Jayson Tatum"}}}}"""
        snapshotBody = "[$props]"
        val settings = ScanSettings(leagues = setOf("NBA"), referenceBooks = listOf("draftkings"))
        val nba = Leagues.byNovigName("NBA")!!
        val p = BoltPropsSource(feed).odds(nba, settings)
        assertEquals("bolt-props", p.provider)
        assertEquals("POINTS", p.events.single().markets.single().stat)
        assertTrue("the lines socket has no props", BoltGamesSource(feed).odds(nba, settings).events.isEmpty())
        feed.close()
    }

    @Test fun `Test key reads the lists, the markets and a small snapshot, listens on a socket of its own and shares no key`() = runBlocking {
        snapshotBody = "[" + game("draftkings") + "]"
        val feed = BoltOddsFeed(client(listOf("good")), OkHttpClient(), scope, wsUrl = wsUrl())
        // the app's own sockets were open before the test: it closes them, so the probes have Pro's connections to themselves
        feed.lines.want(filters)
        until { feed.lines.live }
        val r = BoltKeyTest.run(feed, ScanSettings(leagues = setOf("NBA"), referenceBooks = listOf("draftkings", "fanduel")), listenMs = 600, scoresListenMs = 500)
        assertEquals("the lines socket was closed for the test", BoltOddsStream.State.OFF, feed.lines.state)
        assertTrue(r.summary, r.ok)
        assertTrue(r.summary, r.summary.contains("3 sports, 4 sportsbooks, 2 of them"))
        assertTrue(r.summary, r.summary.contains("Sharp books: none"))
        assertTrue(r.summary, r.summary.contains("1 games listed"))
        assertTrue(r.summary, r.summary.contains("Snapshot of NBA at 1 books"))
        assertTrue(r.summary, r.summary.contains("Odds socket: live"))
        assertTrue(r.summary, r.summary.contains("Scores socket /livescores"))
        for (piece in listOf(
            "===== get_info =====", "novig [never the fair line]", "neobet", "===== get_markets", "Player Points [prop POINTS]", "Moneyline [line]", "Fight To Not Go Distance [—]",
            "===== snapshot (NBA · 3 books: draftkings, fanduel, novig", "games per book: draftkings 1", "--- draftkings", "===== get_parlays", "GET /playbyplay/user", "Not available on your plan",
            "===== websocket probe", "socket_connected", "connection: HTTP 101", "===== websocket /livescores", "opened: HTTP 101", "sending {\"action\":\"subscribe\"", "===== websocket /playbyplay",
        )) {
            assertTrue("missing '$piece' in:\n${r.sample}", r.sample.contains(piece))
        }
        assertFalse("the key is not in the sample", r.sample.contains("good") && r.sample.contains("key=good"))
        assertTrue("the probe used a socket of its own and closed it", feed.probeStream().state == BoltOddsStream.State.OFF)
        feed.close()
    }

    @Test fun `a bad key fails the test in one line`() = runBlocking {
        val feed = BoltOddsFeed(client(listOf("nope")), OkHttpClient(), scope, wsUrl = wsUrl())
        val r = BoltKeyTest.run(feed, ScanSettings(leagues = setOf("NBA")), listenMs = 100)
        assertFalse(r.ok)
        assertTrue(r.summary, r.summary.startsWith("BoltOdds:"))
        assertFalse(r.summary.contains("nope"))
    }

    @Test fun `seen at the start the two sockets are the lines and the props and the third connection stays free`() {
        val feed = BoltOddsFeed(client(), OkHttpClient(), scope, wsUrl = wsUrl())
        assertEquals(listOf("lines", "props"), listOf(feed.lines.name, feed.props.name))
        assertFalse(feed.connected)
        assertEquals(Instant.parse("2026-10-20T23:30:00Z").toEpochMilli(), BoltParser.whenMs("2026-10-20, 07:30 PM"))
    }
}
