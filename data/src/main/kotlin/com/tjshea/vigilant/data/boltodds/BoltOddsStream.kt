package com.tjshea.vigilant.data.boltodds

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One BoltOdds odds websocket and the [BoltBoard] it keeps (BOLTODDS_API.md §2, §3). Pro allows 3 connections at once; Vigilant uses two, one per kind of data (the lines, the player props), because a game
 * update REPLACES a game's outcomes at a book and the two kinds of market must not wipe each other, and because each socket then parses half the traffic. This class owns one: its connection, its
 * subscription, its snapshot, its reconnects.
 *
 * Life: it runs only while a scan uses it ([want]/[use]); [idleCloseMs] after the last use it closes and stays closed. Opening: connect to `wss://spro.agency/api?key=`, wait for `socket_connected`, send the
 * subscription (`sports`, `sportsbooks`, `markets`), then read the SNAPSHOT (the initial state, which the socket does not send) as a stream into the board; [awaitReady] is true once that has been read (or has
 * failed: the socket then fills the board by itself). A changed [want] re-sends the subscription on the same connection, never more than once per [resubscribeGapMs] (the server refuses sooner: "Re-subscription
 * rate limit hit. Please wait 5 seconds"). When a book or sport is cleared on BoltOdds' side (`book_clear`, `sport_clear`: "connection drops on our backend") the snapshot is read again, at most once per
 * [resnapshotGapMs].
 *
 * Failure: any close or failure empties the board (a quote from before the gap is never served as current), waits [backoffMinMs] doubling to [backoffMaxMs] (12 new connections a minute per IP are allowed; a
 * connection that lived a minute starts the wait over), and tries again. A close with code 1008 "Invalid token" refuses that key ([BoltOddsClient.refuseKey]) and the next saved key is used. OkHttp pings the
 * server every 20 s and drops a connection that does not answer, so a dead link ends here as a failure, not as silence.
 */
class BoltOddsStream(
    val name: String,
    private val http: OkHttpClient,
    private val client: BoltOddsClient,
    val board: BoltBoard,
    private val scope: CoroutineScope,
    private val wsUrl: String = DEFAULT_WS_URL,
    private val clock: () -> Long = System::currentTimeMillis,
    private val backoffMinMs: Long = 6_000L,
    private val backoffMaxMs: Long = 120_000L,
    private val resubscribeGapMs: Long = 5_600L,
    private val resnapshotGapMs: Long = 5 * 60_000L,
    private val idleCloseMs: Long = 10 * 60_000L,
    /** A book that has said nothing about a sport for this long is not vouched for: its quotes age by their own times. */
    private val quietBookMs: Long = 30 * 60_000L,
    private val checkEveryMs: Long = 2_000L,
    /** How long an opened connection waits for `socket_connected` before it subscribes anyway. */
    private val greetingWaitMs: Long = 4_000L,
) {
    enum class State { OFF, CONNECTING, LIVE, DOWN }

    private class Closed(val code: Int, val reason: String)

    @Volatile var state: State = State.OFF
        private set
    @Volatile private var socket: WebSocket? = null
    @Volatile private var desired: BoltFilters? = null
    @Volatile private var subscribed: BoltFilters? = null
    @Volatile private var lastUsedMs = 0L
    @Volatile private var lastSubscribeAt = 0L
    @Volatile private var resubscribePending = false
    private val subLock = Mutex()
    @Volatile private var loop: Job? = null
    @Volatile private var snapshotJob: Job? = null
    @Volatile private var needSnapshot = false
    @Volatile private var lastSnapshotAt = 0L

    /** When the current connection's snapshot was read (or failed); 0 until then, and again 0 after a drop. */
    @Volatile var readyAtMs = 0L
        private set

    // Diagnostics
    @Volatile var connects = 0
        private set
    @Volatile var drops = 0
        private set
    @Volatile var lastClose: String? = null
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile private var lastRefusal: String? = null
    @Volatile var snapshotError: String? = null
        private set
    @Volatile var snapshots = 0
        private set
    @Volatile var snapshotGames = 0
        private set
    @Volatile var snapshotMs = 0L
        private set
    @Volatile var liveSinceMs = 0L
        private set
    @Volatile var lastFrameMs = 0L
        private set
    @Volatile var frames = 0L
        private set

    /**
     * What the first sample showed (BOLTODDS_API.md §6, 2026-10-12: a connection that opened and then said nothing for 30 s): how many frames were text and how many binary, how many arrived on THIS connection, when it
     * opened and what the server answered the upgrade with, and the first few raw frames (kind, size, a preview). For Test key and Diagnostics.
     */
    @Volatile var textFrames = 0L
        private set
    @Volatile var binaryFrames = 0L
        private set
    @Volatile var framesThisConn = 0L
        private set
    @Volatile var openedAtMs = 0L
        private set
    @Volatile var openInfo: String? = null
        private set
    @Volatile var greetingFallbacks = 0
        private set
    val rawFrames = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** Frames that were a JSON array of messages, and the biggest frame (characters): what the docs leave open (BOLTODDS_API.md §8). */
    @Volatile var arrayFrames = 0L
        private set
    @Volatile var biggestFrame = 0
        private set

    /** When the first [MAX_PINGS] `ping` messages arrived (ms): the keep-alive interval, for Test key. */
    val pings = java.util.concurrent.CopyOnWriteArrayList<Long>()

    /** The first example of every `action` the socket sent (200 characters), for Test key and Diagnostics. */
    val examples = ConcurrentHashMap<String, String>()

    /** One line for Diagnostics and Test key: how the upgrade was answered and what kinds of frames came (null before the first connection). */
    fun connectionNotes(): String? {
        if (connects == 0) return null
        return "connection: ${openInfo ?: "not opened"} · frames text $textFrames, binary $binaryFrames, ${framesThisConn} on this connection" +
            (if (greetingFallbacks > 0) " · subscribed without a greeting $greetingFallbacks time(s)" else "")
    }

    /** Registers the filters this socket should hold and wakes it. Safe to call on every scan. */
    fun want(filters: BoltFilters) {
        lastUsedMs = clock()
        val changed = desired != filters
        desired = filters
        ensureRunning()
        if (changed && state == State.LIVE) scope.launch { syncSubscription() }
    }

    fun use() { lastUsedMs = clock() }

    /**
     * Whether this process may hold the socket at all (Tj, 2026-10-20, CH34: BoltOdds refused connections as "too many": the plan allows 3 and the runner holds 2, so a phone socket on top is refused).
     * The phone sets it false while the GitHub engine holds the sockets ([ScanSettings.phoneSockets]); then no path (a scan, the streaming engine, the live tennis lane) can open one.
     */
    @Volatile var allowed: () -> Boolean = { true }

    val live: Boolean get() = state == State.LIVE

    /** What the REST snapshot reads while the socket switch is off (Settings › APIs › BoltOdds › Socket): the filters [BoltOddsFeed.arrange] worked out, and when they were last read. */
    @Volatile var restFilters: BoltFilters? = null
    @Volatile private var lastRestAt = 0L
    private val restLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The regular API without the socket: one REST snapshot of [f] into the board, at most once every [minGapMs] (the snapshot is 6 a minute per IP). Nothing is held open and the board's quotes count
     * only as old as this read ([aliveAt] is null off the socket). A failed read keeps what the board has and says why in [snapshotError].
     */
    suspend fun readOnce(f: BoltFilters, minGapMs: Long = REST_GAP_MS) = restLock.withLock {
        if (clock() - lastRestAt < minGapMs && f == restFilters) return@withLock
        val asked = f.copy(markets = if (f.markets.size > MAX_SNAPSHOT_MARKETS) emptyList() else f.markets)
        val t0 = clock()
        var games = 0
        try {
            client.snapshotStream(asked) { text ->
                BoltParser.message(parseObject(text) ?: return@snapshotStream, clock(), initial = true)?.let { board.apply(it) }
                games++
            }
            snapshots++
            snapshotGames = games
            snapshotError = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            snapshotError = client.redact(e.message ?: e.javaClass.simpleName).take(200)
        } finally {
            snapshotMs = clock() - t0
            lastRestAt = clock()
            restFilters = f
        }
    }

    /** True once the current connection's snapshot is in the board; waits up to [timeoutMs] for it. */
    suspend fun awaitReady(timeoutMs: Long): Boolean {
        val until = clock() + timeoutMs
        while (true) {
            if (state == State.LIVE && readyAtMs > 0L) return true
            if (clock() >= until || !currentCoroutineContext().isActive) return false
            delay(100)
        }
    }

    /**
     * When [book]'s quotes about [sport] may be taken as current at this moment (the instant itself): the socket is live, its snapshot was read, and the book has spoken about the sport within [quietBookMs].
     * Null otherwise: a quote then counts only as old as the message that set it.
     */
    fun aliveAt(book: String, sport: String): Long? {
        // A connection that has not sent one frame since it opened (not even a ping) is not vouched for, whatever state it claims.
        if (state != State.LIVE || readyAtMs == 0L || framesThisConn == 0L) return null
        val now = clock()
        val heard = board.heardAt(book, sport) ?: return null
        return if (now - heard <= quietBookMs) now else null
    }

    fun close() {
        loop?.cancel()
        snapshotJob?.cancel()
        socket?.close(1000, "closed")
        socket = null
        board.clear()
        readyAtMs = 0L
        subscribed = null
        state = State.OFF
    }

    private fun ensureRunning() {
        if (!allowed()) return
        synchronized(this) { if (loop?.isActive != true) loop = scope.launch { run() } }
    }

    private suspend fun run() {
        var backoff = backoffMinMs
        while (currentCoroutineContext().isActive) {
            if (clock() - lastUsedMs > idleCloseMs || !allowed()) break
            val key = client.nextKey()
            if (key == null) {
                down("no usable key (every saved key is refused or resting)" + (lastRefusal?.let { ": $it" } ?: ""))
                delay(30_000)
                continue
            }
            val closed = CompletableDeferred<Closed>()
            val startedAt = clock()
            state = State.CONNECTING
            connects++
            openedAtMs = 0L
            openInfo = null
            framesThisConn = 0L
            val ws = http.newWebSocket(Request.Builder().url("$wsUrl?key=$key").build(), Listener(closed))
            socket = ws
            var idle = false
            while (!closed.isCompleted) {
                withTimeoutOrNull(checkEveryMs) { closed.await() }
                if (closed.isCompleted) break
                if (clock() - lastUsedMs > idleCloseMs) { idle = true; ws.close(1000, "idle"); withTimeoutOrNull(3_000) { closed.await() }; break }
                // Opened but not greeted with `socket_connected` within [greetingWaitMs]: the docs' troubleshooting says to send the subscription soon after connecting, and the first sample (2026-10-12) saw a connection
                // that opened and said nothing for 30 s. Go on as if greeted: subscribe now (a greeting that comes later changes nothing), and read the snapshot.
                if (state == State.CONNECTING && openedAtMs > 0L && clock() - openedAtMs > greetingWaitMs) {
                    greetingFallbacks++
                    state = State.LIVE
                    lastError = "no socket_connected within ${greetingWaitMs / 1000}s of opening: subscribed anyway"
                    scope.launch { syncSubscription() }
                }
                // Never opened at all: not a connection to wait on.
                if (state == State.CONNECTING && openedAtMs == 0L && clock() - startedAt > CONNECT_TIMEOUT_MS) { ws.cancel(); closed.complete(Closed(-1, "the upgrade did not finish within ${CONNECT_TIMEOUT_MS / 1000}s")); break }
                maybeResnapshot()
            }
            val c = if (closed.isCompleted) closed.await() else Closed(1000, "idle")
            socket = null
            snapshotJob?.cancel()
            board.clear()
            readyAtMs = 0L
            subscribed = null
            val lived = clock() - startedAt
            if (idle) { state = State.OFF; break }
            drops++
            lastClose = "code ${c.code}${if (c.reason.isNotBlank()) " ${client.redact(c.reason)}" else ""} after ${lived / 1000}s"
            val tooMany = c.reason.contains("concurrent", ignoreCase = true) || c.reason.contains("too many", ignoreCase = true)
            if (tooMany) {
                // A connection-count refusal says nothing about the key (BC4): the key stays good, the socket backs off and says why.
                lastRefusal = "BoltOdds refused the connection: ${c.reason.ifBlank { "too many concurrent connections" }}"
                down(lastRefusal!!)
                // Who holds the plan's sockets (`/api/odds/user`, AF 2026-10-22): an earlier runner's half-open socket shows here until BoltOdds times it out.
                val refusal = lastRefusal
                scope.launch { client.oddsUser()?.let { u -> if (lastRefusal == refusal && u.connections != null) { lastRefusal = "$refusal (BoltOdds counts ${u.summary()})"; lastError = lastRefusal } } }
            } else if (c.code == 1008 || c.reason.contains("invalid token", ignoreCase = true)) {
                client.refuseKey(key, "BoltOdds closed the socket: ${c.reason.ifBlank { "policy violation" }}")
                lastRefusal = "BoltOdds refused the key (${c.reason.ifBlank { "Invalid token" }})"
                down(lastRefusal!!)
            } else {
                down(lastClose ?: "connection lost")
            }
            if (lived >= 60_000L) backoff = backoffMinMs
            delay(backoff)
            backoff = minOf(backoff * 2, backoffMaxMs)
        }
        if (clock() - lastUsedMs > idleCloseMs) state = State.OFF
    }

    private fun down(why: String) {
        lastError = why
        state = State.DOWN
    }

    /** Sends the subscription for [desired] now, or when the 5-second re-subscription gap allows. */
    private suspend fun syncSubscription() {
        val d = desired ?: return
        if (state != State.LIVE) return
        if (subscribed == d) return
        val wait = resubscribeGapMs - (clock() - lastSubscribeAt)
        if (wait > 0) {
            if (resubscribePending) return
            resubscribePending = true
            try { delay(wait) } finally { resubscribePending = false }
        }
        // One at a time: a frame that wakes the socket (any frame, and the greeting itself) starts a sync of its own, and two of them must not both send the same subscription.
        subLock.withLock {
            val ws = socket ?: return
            val f = desired ?: return
            if (state != State.LIVE || subscribed == f) return
            val old = subscribed
            if (!ws.send(subscribeFrame(f))) return
            lastSubscribeAt = clock()
            subscribed = f
            // New sports or books are not in the board yet: the snapshot has to be read again for them (a narrower subscription needs none).
            if (f.addsTo(old)) startSnapshot(f)
        }
    }

    private fun maybeResnapshot() {
        val f = subscribed ?: return
        if (state == State.LIVE && needSnapshot && snapshotJob?.isActive != true && clock() - lastSnapshotAt >= resnapshotGapMs) startSnapshot(f)
    }

    private fun startSnapshot(f: BoltFilters) {
        if (snapshotJob?.isActive == true) { needSnapshot = true; return }
        needSnapshot = false
        // A long market list would make the URL too long: the board drops what it cannot use anyway.
        val asked = f.copy(markets = if (f.markets.size > MAX_SNAPSHOT_MARKETS) emptyList() else f.markets)
        snapshotJob = scope.launch {
            val t0 = clock()
            var games = 0
            var failed = false
            try {
                client.snapshotStream(asked) { text ->
                    val now = clock()
                    BoltParser.message(parseObject(text) ?: return@snapshotStream, now, initial = true)?.let { board.apply(it) }
                    games++
                }
                snapshots++
                snapshotGames = games
                snapshotError = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                snapshotError = client.redact(e.message ?: e.javaClass.simpleName).take(200)
                failed = true
            } finally {
                snapshotMs = clock() - t0
                // A failed snapshot (a 429 from a shared runner address, a daily quota) is asked again in a minute, not five: the board stays empty until it lands (Tj, 2026-10-16: his BoltOdds works on the phone, silent on GitHub).
                lastSnapshotAt = if (failed) clock() - resnapshotGapMs + 60_000L else clock()
                if (failed) needSnapshot = true
                // Ready either way: a failed snapshot leaves the socket to fill the board, and a scan must not wait on it for ever.
                if (state == State.LIVE) readyAtMs = clock()
            }
        }
    }

    private fun parseObject(text: String): kotlinx.serialization.json.JsonObject? =
        try { kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject } catch (e: Exception) { null }

    private fun subscribeFrame(f: BoltFilters): String = buildJsonObject {
        put("action", "subscribe")
        putJsonObject("filters") {
            if (f.sports.isNotEmpty()) put("sports", buildJsonArray { f.sports.forEach { add(JsonPrimitive(it)) } })
            if (f.sportsbooks.isNotEmpty()) put("sportsbooks", buildJsonArray { f.sportsbooks.forEach { add(JsonPrimitive(it)) } })
            if (f.games.isNotEmpty()) put("games", buildJsonArray { f.games.forEach { add(JsonPrimitive(it)) } })
            if (f.markets.isNotEmpty()) put("markets", buildJsonArray { f.markets.forEach { add(JsonPrimitive(it)) } })
        }
    }.toString()

    private fun onFrame(text: String, binary: Boolean = false, raw: String? = null) {
        val now = clock()
        lastFrameMs = now
        frames++
        framesThisConn++
        if (binary) binaryFrames++ else textFrames++
        if (rawFrames.size < MAX_RAW_FRAMES) rawFrames += "${if (binary) "binary ${raw ?: ""}" else "text"} · ${text.length} chars · ${client.redact(text.take(160)).replace('\n', ' ')}"
        // A frame of any kind proves the connection is talking: a missing greeting does not keep it from being live.
        if (state == State.CONNECTING) {
            state = State.LIVE
            liveSinceMs = now
            scope.launch { syncSubscription() }
        }
        if (text.trimStart().startsWith("[")) arrayFrames++
        if (text.length > biggestFrame) biggestFrame = text.length
        for (m in BoltParser.frame(text, now)) {
            when (m) {
                is BoltMessage.SocketConnected -> {
                    examples.putIfAbsent("socket_connected", text.take(200))
                    liveSinceMs = now
                    state = State.LIVE
                    lastError = null
                    scope.launch { syncSubscription() }
                }
                is BoltMessage.Error -> {
                    lastError = m.text.take(200)
                    examples.putIfAbsent("error", text.take(200))
                    // Re-subscription too soon: ask again once the gap has passed.
                    if (m.text.contains("rate limit", ignoreCase = true)) {
                        subscribed = null
                        // The server now says how long ("Please wait 5 minutes"): honour it, and do not hammer it every few seconds meanwhile.
                        val minutes = Regex("(\\d+)\\s*minute", RegexOption.IGNORE_CASE).find(m.text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        lastSubscribeAt = now + minutes * 60_000L
                        scope.launch { syncSubscription() }
                    }
                }
                is BoltMessage.SubscriptionUpdated -> examples.putIfAbsent("subscription_updated", text.take(200))
                is BoltMessage.Ping -> { examples.putIfAbsent("ping", text.take(200)); if (pings.size < MAX_PINGS) pings += now; board.apply(m) }
                is BoltMessage.SportClear, is BoltMessage.BookClear -> { needSnapshot = true; board.apply(m); examples.putIfAbsent(if (m is BoltMessage.SportClear) "sport_clear" else "book_clear", text.take(200)) }
                is BoltMessage.Unknown -> { examples.putIfAbsent("other:" + m.action.take(40), text.take(200)); board.apply(m) }
                is BoltMessage.GameUpdate -> { examples.putIfAbsent("game_update", text.take(200)); board.apply(m) }
                is BoltMessage.LineUpdate -> { examples.putIfAbsent("line_update", text.take(200)); board.apply(m) }
                is BoltMessage.GameRemoved -> { examples.putIfAbsent("game_removed", text.take(200)); board.apply(m) }
            }
        }
    }

    private inner class Listener(private val closed: CompletableDeferred<Closed>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            openedAtMs = clock()
            openInfo = "HTTP ${response.code} · extensions ${response.header("Sec-WebSocket-Extensions") ?: "none"} · server ${response.header("Server") ?: "?"}"
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket === socket) onFrame(text)
        }

        /** The docs' own client reads `recv()` whatever the frame type: a binary frame carrying JSON (possibly gzip or zlib) is read like a text one. */
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (webSocket === socket) onFrame(BoltFrames.decode(bytes), binary = true, raw = "${bytes.size} bytes, starts ${bytes.substring(0, minOf(8, bytes.size)).hex()}")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            closed.complete(Closed(code, reason))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            closed.complete(Closed(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val http = response?.let { "HTTP ${it.code}" }
            closed.complete(Closed(-1, listOfNotNull(http, t.message?.take(120)).joinToString(": ")))
        }
    }

    companion object {
        const val DEFAULT_WS_URL = "wss://spro.agency/api"
        const val MAX_SNAPSHOT_MARKETS = 60

        /** The least time between two REST snapshots of one stream when the socket is off (the snapshot allows 6 a minute per IP; the lines and props streams are two). */
        const val REST_GAP_MS = 12_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val MAX_RAW_FRAMES = 6
        const val MAX_PINGS = 20

        /** The OkHttp client for a socket: it pings every 20 s and drops a connection that does not answer. */
        fun socketClient(base: OkHttpClient): OkHttpClient = base.newBuilder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    }
}

/** Whether these filters take in something [old] did not (a sport, a book, a market, a game): what a snapshot read for [old] lacks. An empty list means "all of that kind". */
internal fun BoltFilters.addsTo(old: BoltFilters?): Boolean {
    if (old == null) return true
    fun grows(o: List<String>, n: List<String>) = if (o.isEmpty()) false else n.isEmpty() || !o.containsAll(n)
    return grows(old.sports, sports) || grows(old.sportsbooks, sportsbooks) || grows(old.markets, markets) || grows(old.games, games)
}
