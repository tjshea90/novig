package com.tjshea.vigilant.data.novig.stream

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

sealed interface StreamState {
    data object Off : StreamState
    data object Connecting : StreamState
    data class Live(val sinceMs: Long, val markets: Int) : StreamState
    data class Failed(val message: String, val atMs: Long) : StreamState
}

/**
 * Order books a source keeps current by push, so a scan reads them without a request each: Novig's
 * websocket with the connected key ([NovigStream]); tests fake it.
 */
interface PushedBooks {
    /**
     * The markets a scan wants, most important first. New ones are subscribed (in bulk, as the
     * throttle allows), ones no longer wanted are dropped. Connects on first use.
     */
    fun watch(marketIds: Collection<String>)

    /** Connects (if it isn't already) without changing what is watched. */
    fun open() {}

    /**
     * The books held current right now among [marketIds]: only those already pushed, and only COMPLETE ones (a full ladder). A market the feed holds only as its best bid
     * ([NovigBook.partial]) is not returned, and is moved to the full `book` channel for the next read ([deepen]).
     */
    fun live(marketIds: Collection<String>): Map<String, NovigBook>

    /**
     * Like [live], but a market held only as its best bid is returned too ([NovigBook.partial]): enough to screen prices, never to size a bet across levels. Same as [live] for a feed with
     * no best-bid channel.
     */
    fun liveTop(marketIds: Collection<String>): Map<String, NovigBook> = live(marketIds)

    /** Moves these markets to the full `book` channel (and keeps them there a while): the scan found a price worth a bet on them, which needs the ladder. Nothing for a feed with no best-bid channel. */
    fun deepen(marketIds: Collection<String>) {}

    /** How many markets this feed can be asked to hold at once (the key's watch cap less a margin; lower when the feed is all `book`). */
    fun room(): Int = NovigStream.MAX_MARKETS

    /** Sets the feed's options from Settings: the best-bid channel on or off, keeping the connection open between scans, and the private `orders` stream (an order's end, pushed). A no-op for a feed with none. */
    fun configureFeed(bbo: Boolean, keepOpen: Boolean, orders: Boolean = false) {}

    /** The feed's state in words, line by line, for Diagnostics. */
    fun feedReport(): List<String> = emptyList()

    /** Why [marketId] has no pushed book right now, in fixed words (null = no reason known, or it has one). The live bids count these, so a page full of "no book" says which. */
    fun bookGap(marketId: String): String? = null

    /**
     * The live engines' own markets, kept apart from [watch]'s list: a scan's [watch] REPLACES what is wanted every cycle and would drop the live games' books. These are
     * subscribed first and stay until the next [watchLive], whatever [watch] says.
     */
    fun watchLive(marketIds: Collection<String>) = watch(marketIds)

    /** Why pushing stopped, if it did at or after [sinceMs]. */
    fun problemSince(sinceMs: Long): String?

    fun close()

    /** The key's own `stream` bucket and watch cap, from `GET /v3/limits` (the documented values until then). */
    fun tune(capacity: Int, refillPerSec: Double, maxWatchedMarkets: Int) {}
}

/**
 * Novig's signed websocket (`GET /v3/ws`, NOVIG_API.md §6) carrying the `book` or `bbo` channel for exactly
 * the markets a scan prices (Tj, 2026-09-28: "Make sure the app is taking full advantage of the novig
 * API key").
 *
 * Why it's fast: a request is charged `weight × subjects` but **never more than the 512-token `stream`
 * bucket**, and one over that passes whenever the bucket is full (docs.novig.com/api/streaming/connection).
 * So once the bucket is full again after the upgrade (32 tokens, ~1 s at 32/s, ~8 s at 4/s), ONE `subscribe` covers
 * every planned market, up to the connection's watch cap, and their snapshots arrive together;
 * after that each change is pushed. A 1,200-price scan takes that instead of ~90 s of REST reads.
 * Additions that fit the tokens left go at once; bigger ones wait for a full bucket. Events aren't
 * subscribed: an event counts as all its markets (500+ for an NFL game), which would crowd out the
 * cap with lines no fair source quotes.
 *
 * **Two channels** (NOVIG_API.md §26, Tj 2026-10-12: "Build the novig bbo feature with a backup to default toggle"): `book` is every resting order (16 tokens a market, a message for every order that
 * joins or leaves anywhere in the ladder); `bbo` is each outcome's best bid and its size (8 tokens, a message only when the top moves). A taker's price is `1 − best bid of the other outcome`, so `bbo`
 * is all a SCREENING read needs, at half the tokens and a fraction of the messages, parsing and memory. With [configureFeed] `bbo` on, every market is held on `bbo` except those that need the ladder: the
 * live lane's pinned markets, and any market someone asks a complete book of ([live], [deepen]: the scan deepens each +EV market, the bet planner and the bid desk read full books). A market's
 * channel is part of what is wanted, and a subscribe sets it. Off, or after a fallback, every market is on `book` exactly as before.
 *
 * **Fallback** (sticky until the switch is turned off and on): Novig refusing a `bbo` subscribe by naming the channel, no `bbo` snapshot arriving for markets subscribed on it, snapshots that cannot be
 * read, or the **canary**: once per connection a few `bbo` markets are also asked for with the `snapshot` verb on `book` (which leaves the subscription alone) and their best bids compared. Any of
 * them puts every market back on `book` and says so in Diagnostics ([feedReport]).
 *
 * It lives while scans use it: closed [idleCloseMs] after the last [watch] or [live] (a long time with [configureFeed] keepOpen: a scan every two minutes used to open a new connection each time,
 * wait for a full bucket and close it again), and on any failure (then not retried for [retryAfterFailureMs], or for [networkRetryMs] when Novig judged the network's address, the same 2 minutes the
 * key's REST route waits; a 429 on the upgrade, an empty `stream` bucket, waits the `Retry-After` it names; the REST route reads meanwhile). With keepOpen a plain drop is retried by the stream itself
 * after a few seconds, backing off. OkHttp answers Novig's 15 s pings and pings back, so a dead connection fails instead of serving old books.
 */
class NovigStream(
    http: OkHttpClient,
    private val signer: NovigSignedClient,
    private val scope: CoroutineScope,
    private val wsUrl: String = "wss://api.novig.com/v3/ws",
    private val clock: () -> Long = System::currentTimeMillis,
    capacity: Double = STREAM_CAPACITY.toDouble(),
    refillPerSec: Double = REFILL_PER_SEC,
    maxMarkets: Int = MAX_MARKETS,
    private val idleCloseMs: Long = IDLE_CLOSE_MS,
    private val retryAfterFailureMs: Long = RETRY_AFTER_FAILURE_MS,
    /** After a plain drop (ping timeout, network blip) while the live lane has markets pinned: it reconnects this soon, not after [retryAfterFailureMs] (Tj, 2026-10-11: "a disconnect on live betting"). */
    private val liveRetryMs: Long = LIVE_RETRY_MS,
    /** After a 451 about the network's address: the key's REST route is back after this, and a live feed that waited longer left two scans 16-29 s long (v0.70.1 diagnostics). */
    private val networkRetryMs: Long = NovigPublicClient.NETWORK_RETRY_MS,
    /** Told of every book change as it arrives: the burst recorder's own connection (RESEARCH.md §95). A scan's stream has none. */
    bookListener: BookListener? = null,
    /** The best-bid channel for markets that need no ladder. Off here (every market on `book`, as it always was); the app turns it on from Settings ([configureFeed]). */
    bbo: Boolean = false,
    /** How long a market asked a complete book of stays on `book` ([deepen]). */
    private val deepHoldMs: Long = DEEP_HOLD_MS,
    /** How long subscribed `bbo` markets may go with not one `bbo` snapshot before the channel is given up on. */
    private val bboDataWaitMs: Long = BBO_DATA_WAIT_MS,
    /** How many `bbo` markets the canary asks the `book` of. */
    private val canaryMarkets: Int = CANARY_MARKETS,
    /** How often the check against the book is repeated on a connection that stays up (a drift in the best bids would otherwise go unseen for as long as it lives). */
    private val canaryEveryMs: Long = CANARY_EVERY_MS,
) : PushedBooks {
    @Volatile
    private var capacity = capacity

    @Volatile
    private var refillPerSec = refillPerSec

    @Volatile
    private var maxMarkets = maxMarkets

    /** True once `GET /v3/limits` has told this connection the key's own allowance (Diagnostics says which numbers it prints: this key's or the documented defaults). */
    @Volatile
    private var tuned = false

    override fun tune(capacity: Int, refillPerSec: Double, maxWatchedMarkets: Int) {
        synchronized(this) {
            tuned = true
            this.capacity = capacity.toDouble()
            this.refillPerSec = refillPerSec
            // A margin under the cap, as with the documented 2,048.
            val before = this.maxMarkets
            this.maxMarkets = (maxWatchedMarkets - (MAX_WATCHED - MAX_MARKETS)).coerceAtLeast(1)
            // A key that holds more than the default (8,192 on the keys that report it) is given the room at once, on this connection; one that holds less is held to it.
            limit = if (this.maxMarkets > before) this.maxMarkets else minOf(limit, this.maxMarkets)
            tokens = minOf(tokens, this.capacity)
        }
    }
    private val http = http.newBuilder().pingInterval(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    val books = StreamBooks(clock).also { it.listener = bookListener }

    private val _state = MutableStateFlow<StreamState>(StreamState.Off)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private var socket: WebSocket? = null
    private val nonce = AtomicLong(0)

    /** Wanted markets, most important first. */
    private var wanted: List<String> = emptyList()

    /** The live engines' markets ([watchLive]): always ahead of [wanted] in what is subscribed. */
    private var livePinned: List<String> = emptyList()
    private var pinnedSet: Set<String> = emptySet()
    private val pinOwners = LinkedHashMap<Any, List<String>>()

    /** The live markets of a lane that just stopped ([watchLive] with none): kept subscribed [LINGER_MS] so a restart of the lane does not pay 16 tokens a market again (the bucket refills 4 a second). */
    private var lingering: List<String> = emptyList()
    private var lingerUntilMs = 0L

    private fun allWanted(): List<String> {
        val keep = if (lingering.isNotEmpty() && clock() < lingerUntilMs) lingering else emptyList()
        return if (livePinned.isEmpty() && keep.isEmpty()) wanted else (livePinned + wanted + keep).distinct()
    }

    /**
     * Markets Novig refused for a reason other than a limit, and until when they are not asked for again; [chunk] is how many one subscribe may carry after such a refusal (halved each
     * time, so a request poisoned by one unresolvable market is narrowed down to it instead of shutting the whole connection out, as `limit = subscribed.size` did: Tj, 2026-10-10,
     * live bids with "no current Novig book" on every main line).
     */
    private val refusedUntil = HashMap<String, Long>()
    private var chunk = Int.MAX_VALUE
    private var lastRefusal: String? = null

    /** Asked for on this connection (acknowledged or not). */
    private val subscribed = HashSet<String>()

    /** The channel each of [subscribed] was last asked for on. */
    private val subscribedOn = HashMap<String, BookChannel>()

    /** Subscribes not yet answered, by nonce: a refusal takes them back out of [subscribed]. */
    private val unanswered = HashMap<Long, List<String>>()

    /** The most markets held at once: the documented 2,048 (less a margin), lowered if Novig says so. */
    private var limit = maxMarkets

    private var syncJob: Job? = null

    /** A [scheduleSync] that arrived while a pass was running: that pass goes round once more. */
    private var syncAgain = false

    /** Test seam: called as a pass finds nothing left to subscribe, just before it ends. */
    @Volatile
    internal var onSyncTail: (() -> Unit)? = null

    private var idleJob: Job? = null
    private var reconnectJob: Job? = null
    private var tokens = 0.0
    private var tokensAt = 0L
    private var lastUsedMs = 0L
    private var failedAtMs: Long? = null

    /** How long after [failedAtMs] the feed isn't tried again: the last failure's own wait. */
    private var retryDelayMs: Long = retryAfterFailureMs
    private var problem: String? = null

    /**
     * Whether this connection's first subscribe went out. It waits for a full bucket: a small early
     * one (the first few planned lines) would spend the tokens the whole plan's single request needs,
     * and the bucket takes two minutes to refill.
     */
    private var firstSent = false

    /** Subscribe requests sent on the current connection (for tests and the scan report). */
    @Volatile
    var subscribeRequests = 0
        private set

    // ---- the options (Settings) -------------------------------------------------------------------------------------------------------------------------------------------

    @Volatile private var bboOn = bbo

    /** The subaccount's own orders, pushed: `private: [orders]` subscribed on this connection ([configureFeed]). */
    val orderEvents = OrderEvents(clock)
    @Volatile private var ordersOn = false
    private var privateSent = false
    private var privateNonce = 0L
    @Volatile private var privateError: String? = null
    @Volatile private var keepOpen = false
    @Volatile private var idleMs = idleCloseMs

    /** Why `bbo` was given up on, or null (sticky until [configureFeed] turns the channel off and on again). */
    @Volatile private var bboFallback: String? = null
    private var bboFallbackAtMs = 0L

    private fun bboActive() = bboOn && bboFallback == null

    /** Market → the time until which it must be held on `book` ([deepen]). */
    private val deepUntil = HashMap<String, Long>()

    // ---- diagnostics ------------------------------------------------------------------------------------------------------------------------------------------------------

    @Volatile private var connects = 0
    @Volatile private var drops = 0
    @Volatile private var upgrade429s = 0
    @Volatile private var lastDrop: String? = null
    @Volatile private var openedAtMs = 0L
    @Volatile private var bboSnapshots = 0
    @Volatile private var bboUnreadable = 0
    @Volatile private var bboRefusals = 0
    @Volatile private var bboFirstSentAtMs = 0L
    @Volatile private var bboNonEmpty = 0
    @Volatile private var bboDeltaOk = 0
    @Volatile private var bboDeltaOdd = 0
    @Volatile private var deepened = 0
    private var quickFails = 0

    /** What the canary last found (see [feedReport]): null until one has finished on any connection. */
    class Canary(val atMs: Long, val checked: Int, val matched: Int, val skipped: Int, val detail: String)

    @Volatile var canary: Canary? = null
        private set
    private var canaryNonce = 0L
    private var canarySentAtMs = 0L
    private var canaryIds: List<String> = emptyList()
    private var canarySeqs: Map<String, Long> = emptyMap()
    private var canaryDone = false
    private var canaryStrikes = 0
    private var canaryNotBeforeMs = 0L
    private val canaryTried = HashSet<String>()

    /** Applies the Settings options: the best-bid channel on or off (turning it off and on again clears a fallback, so it is tried afresh), and keeping the connection open between scans. */
    override fun configureFeed(bbo: Boolean, keepOpen: Boolean, orders: Boolean) {
        var resync = false
        synchronized(this) {
            if (orders != ordersOn) {
                ordersOn = orders
                privateError = null
                // Turned on: subscribe (afresh). Turned off: what was subscribed stays marked, so the next pass unsubscribes it.
                if (orders) privateSent = false else orderEvents.reset()
                resync = true
            }
            if (bbo != bboOn) {
                bboOn = bbo
                bboFallback = null
                resync = true
            }
            this.keepOpen = keepOpen
            idleMs = if (keepOpen) maxOf(idleCloseMs, KEEP_OPEN_MS) else idleCloseMs
            if (!keepOpen) reconnectJob?.cancel()
        }
        if (resync && _state.value is StreamState.Live) scheduleSync()
    }

    override fun room(): Int = synchronized(this) { if (bboActive()) limit else minOf(limit, MAX_MARKETS) }

    /** The channel [id] should be held on right now. Under the lock. */
    private fun channelFor(id: String, now: Long): BookChannel {
        if (!bboActive()) return BookChannel.BOOK
        if (id in pinnedSet) return BookChannel.BOOK
        if (lingering.isNotEmpty() && now < lingerUntilMs && id in lingering) return BookChannel.BOOK
        val until = deepUntil[id]
        return if (until != null && until > now) BookChannel.BOOK else BookChannel.BBO
    }

    private fun fallBackFromBbo(reason: String) {
        synchronized(this) {
            if (bboFallback != null || !bboOn) return
            bboFallback = reason
            bboFallbackAtMs = clock()
        }
        scheduleSync()
    }

    // ---- what is wanted ---------------------------------------------------------------------------------------------------------------------------------------------------

    override fun watch(marketIds: Collection<String>) {
        val start: Boolean
        synchronized(this) {
            lastUsedMs = clock()
            wanted = marketIds.distinct()
            start = socket == null && failedAtMs.let { it == null || clock() - it >= retryDelayMs }
        }
        if (start) connect() else if (_state.value is StreamState.Live) scheduleSync()
    }

    override fun watchLive(marketIds: Collection<String>) = pin(DEFAULT_OWNER, marketIds)

    /**
     * The live markets of one [owner] (the live lane, the burst recorder): each owner's list replaces only its own, and the stream holds the union, all on `book`. Before there were two owners one
     * list was enough; now two lanes share the one connection (a second connection of the key spent the same `stream` bucket and starved the first: Tj's v0.89.34 file).
     */
    fun pin(owner: Any, marketIds: Collection<String>) {
        val start: Boolean
        synchronized(this) {
            lastUsedMs = clock()
            val next = marketIds.distinct()
            val before = pinOwners[owner].orEmpty()
            if (next.isEmpty()) {
                if (before.isNotEmpty()) { lingering = before; lingerUntilMs = clock() + LINGER_MS }
                pinOwners.remove(owner)
            } else {
                pinOwners[owner] = next
            }
            livePinned = pinOwners.values.flatten().distinct()
            pinnedSet = livePinned.toHashSet()
            start = socket == null && failedAtMs.let { it == null || clock() - it >= retryDelayMs }
        }
        if (start) connect() else if (_state.value is StreamState.Live) scheduleSync()
    }

    override fun open() {
        val start = synchronized(this) {
            lastUsedMs = clock()
            (socket == null && failedAtMs.let { it == null || clock() - it >= retryDelayMs }).also { fresh ->
                // A new connection wants nothing until a scan hands it its markets: the last scan's list (kept after an idle close) would
                // spend the one bulk subscribe the bucket allows on lines that scan already read.
                if (fresh) wanted = emptyList()
            }
        }
        if (start) connect()
    }

    override fun deepen(marketIds: Collection<String>) {
        var changed = false
        synchronized(this) {
            lastUsedMs = clock()
            if (!bboActive()) return
            val now = clock()
            val until = now + deepHoldMs
            for (id in marketIds) {
                if (subscribedOn[id] == BookChannel.BBO || deepUntil[id].let { it == null || it <= now }) changed = true
                deepUntil[id] = until
            }
            deepened += marketIds.size
            // Bounded: expired entries go once the table outgrows the watch cap.
            if (deepUntil.size > limit * 2) deepUntil.values.removeAll { it <= now }
        }
        if (changed && _state.value is StreamState.Live) scheduleSync()
    }

    override fun live(marketIds: Collection<String>): Map<String, NovigBook> = read(marketIds, needDepth = true)

    override fun liveTop(marketIds: Collection<String>): Map<String, NovigBook> = read(marketIds, needDepth = false)

    private fun read(marketIds: Collection<String>, needDepth: Boolean): Map<String, NovigBook> {
        synchronized(this) { lastUsedMs = clock() }
        if (_state.value !is StreamState.Live) return emptyMap()
        val now = clock()
        val out = HashMap<String, NovigBook>()
        var deep: ArrayList<String>? = null
        for (id in marketIds) {
            if (!synchronized(this) { id in subscribed }) continue
            // Current as of now: every change since its snapshot has been applied.
            val b = books.book(id) ?: continue
            if (b.partial && needDepth) {
                // A complete book was asked of a market held as its best bid: it goes to `book` for the next read, and this one is read the long way.
                (deep ?: ArrayList<String>().also { deep = it }) += id
                continue
            }
            out[id] = b.copy(fetchedAtMs = now)
        }
        deep?.let { deepen(it) }
        return out
    }

    override fun bookGap(marketId: String): String? = synchronized(this) {
        when {
            _state.value !is StreamState.Live -> "Novig live feed is not connected"
            marketId in refusedUntil -> "Novig refused the live book (${lastRefusal ?: "unknown"})"
            marketId !in subscribed -> {
                refill()
                val held = livePinned.count { it in subscribed }
                "Novig live book not subscribed yet: $held of ${livePinned.size} live markets are, ${(tokens / BookChannel.BOOK.weight).toInt().coerceAtLeast(0)} more affordable now (stream allowance ${tokens.toInt().coerceAtLeast(0)} of ${capacity.toInt()}, refills ${refillPerSec.toInt()} a second)"
            }
            books.channelOf(marketId) == BookChannel.BBO -> "Novig live book is held as its best bid only: the full book is being subscribed"
            else -> "Novig live book subscribed, no snapshot yet"
        }
    }

    override fun problemSince(sinceMs: Long): String? = synchronized(this) { problem?.takeIf { (failedAtMs ?: return null) >= sinceMs } }

    fun book(marketId: String): NovigBook? = live(listOf(marketId))[marketId]

    @Synchronized
    fun connect() {
        if (socket != null) return
        _state.value = StreamState.Connecting
        val request = try {
            signer.signedRequest("GET", "/v3/ws").newBuilder().url(wsUrl).build()
        } catch (e: Exception) {
            // The Keystore key is gone (e.g. settings restored onto a new phone) or unusable.
            fail("This phone can't sign with the saved Novig key. Reconnect it in Settings.")
            return
        }
        // The upgrade costs 32; the bucket is assumed full before it (a fresh or long-idle key).
        tokens = capacity - UPGRADE_COST
        tokensAt = clock()
        subscribeRequests = 0
        chunk = Int.MAX_VALUE
        refusedUntil.clear()
        firstSent = false
        limit = maxMarkets
        bboSnapshots = 0
        bboUnreadable = 0
        bboRefusals = 0
        bboFirstSentAtMs = 0L
        bboNonEmpty = 0
        bboDeltaOk = 0
        bboDeltaOdd = 0
        canaryNonce = 0L
        canaryDone = false
        privateSent = false
        orderEvents.reset()
        canaryStrikes = 0
        canaryNotBeforeMs = 0L
        canaryTried.clear()
        connects++
        socket = http.newWebSocket(request, Listener())
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(minOf(idleMs, IDLE_CHECK_MS, maxOf(bboDataWaitMs / 2, 250L)))
                val idle = synchronized(this@NovigStream) { clock() - lastUsedMs >= idleMs }
                if (idle) {
                    close()
                    break
                }
                watchdog()
            }
        }
    }

    override fun close() {
        val (sync, idle, ws) = synchronized(this) {
            val t = Triple(syncJob, idleJob, socket)
            syncJob = null
            idleJob = null
            reconnectJob?.cancel()
            reconnectJob = null
            socket = null
            subscribed.clear()
            subscribedOn.clear()
            unanswered.clear()
            t
        }
        sync?.cancel()
        ws?.close(1000, "idle")
        books.clear()
        orderEvents.reset()
        _state.value = StreamState.Off
        // Last: the idle watchdog may be the caller.
        idle?.cancel()
    }

    private fun scheduleSync() {
        synchronized(this) {
            if (syncJob?.isActive == true) {
                syncAgain = true
                return
            }
            syncAgain = false
            syncJob = scope.launch {
                val me = coroutineContext[Job]
                // A request that lands while a pass is running makes it look again before it ends, and the pass lets go of [syncJob] under the same lock that decides
                // that: a refusal arriving in a pass's last moments (its job still "active") was dropped, and the refused batch was never asked for again (CI, 2026-10-12).
                do {
                    sync()
                } while (synchronized(this@NovigStream) {
                        val again = syncAgain && syncJob === me && scope.isActive
                        syncAgain = false
                        if (!again && syncJob === me) syncJob = null
                        again
                    })
            }
        }
    }

    /** Subscribes what's wanted and not held, moves what is on the wrong channel, drops what isn't wanted, and re-snapshots gaps. */
    private suspend fun sync() {
        privateStep()
        while (scope.isActive) {
            val (drop, add0) = synchronized(this) {
                if (socket == null) return
                val now = clock()
                refusedUntil.values.removeAll { it <= now }
                val want = allWanted().filter { it !in refusedUntil }.take(limit.coerceAtLeast(0))
                val wantSet = want.toHashSet()
                val drop = subscribed.filter { it !in wantSet }
                val room = limit - (subscribed.size - drop.size)
                // Not held yet, or held on a channel it should no longer be on (a subscribe sets the channel); in priority order. Only new markets count against the room.
                val need = ArrayList<String>()
                var added = 0
                for (id in want) {
                    if (need.size >= chunk) break
                    if (id !in subscribed) {
                        if (added >= room.coerceAtLeast(0)) continue
                        added++
                        need += id
                    } else if (subscribedOn[id] != channelFor(id, now)) {
                        need += id
                    }
                }
                drop to need
            }
            if (drop.isNotEmpty()) {
                // 1 token a subject, never more than the bucket (like any request): uncapped, a big drop waited minutes and left the
                // bucket deep in debt, holding back every subscribe after it.
                val dropCost = minOf(drop.size.toDouble(), capacity)
                waitFor(dropCost)
                send(buildJsonObject {
                    put("nonce", nonce.incrementAndGet())
                    putJsonArray("unsubscribe") { drop.forEach { add(JsonPrimitive("market:$it")) } }
                })
                synchronized(this) { subscribed.removeAll(drop.toSet()); drop.forEach { subscribedOn.remove(it); deepUntil.remove(it) } }
                books.forget(drop)
                spend(dropCost)
            }
            if (add0.isEmpty()) {
                onSyncTail?.invoke()
                break
            }
            // The live lane's new markets go in a request of their own, ahead of a scan's bulk one: that one waits for a full bucket (up to two minutes), and a live game
            // cannot (the banner read "Novig live book not subscribed yet" through it).
            val pinnedNow = synchronized(this) { pinnedSet }
            val liveAdd = add0.filter { it in pinnedNow }
            val add: List<String>
            val cost: Double
            val need: Double
            val now = clock()
            val channels: Map<String, BookChannel>
            if (liveAdd.isNotEmpty()) {
                // As many of them as the bucket pays for RIGHT NOW (moneylines first: [allWanted] is in priority order), the rest as tokens return, one market per 4 s at worst. Waiting for the whole
                // request's price left a live game with no book for minutes after a scan (or an earlier subscribe) had emptied the bucket (Tj, 2026-10-11: "connects for a few seconds, matches games,
                // then says Novig live book not subscribed yet").
                refill()
                val afford = synchronized(this) { (tokens / BookChannel.BOOK.weight).toInt() }
                if (afford < 1) {
                    waitFor(BookChannel.BOOK.weight.toDouble())
                    continue
                }
                add = liveAdd.take(afford)
                channels = add.associateWith { BookChannel.BOOK }
                cost = add.size * BookChannel.BOOK.weight.toDouble()
                need = cost
            } else {
                add = add0
                channels = synchronized(this) { add.associateWith { channelFor(it, now) } }
                // Charged weight × subjects, never over the bucket; a request at the cap needs it full.
                cost = minOf(channels.values.sumOf { it.weight }.toDouble(), capacity)
                need = if (synchronized(this) { firstSent }) cost else capacity
            }
            if (!haveTokens(need)) {
                // Wait for enough, then look again: more markets may be wanted by then, and go in the same request.
                waitFor(need)
                continue
            }
            val n = nonce.incrementAndGet()
            synchronized(this) {
                if (socket == null) return
                subscribed.addAll(add)
                channels.forEach { (id, ch) -> subscribedOn[id] = ch }
                unanswered[n] = add
                firstSent = true
                if (bboFirstSentAtMs == 0L && channels.values.any { it == BookChannel.BBO }) bboFirstSentAtMs = clock()
            }
            send(buildJsonObject {
                put("nonce", n)
                putJsonObject("subscribe") { putJsonObject("markets") { channels.forEach { (id, ch) -> put(id, ch.wire) } } }
            })
            subscribeRequests++
            spend(cost)
            _state.value = (state.value as? StreamState.Live)?.copy(markets = synchronized(this) { subscribed.size }) ?: state.value
        }
        // Books whose seq skipped are re-read with `snapshot` (it keeps the subscriptions), each on the channel it is held on.
        val gaps = books.needsSnapshot.filter { synchronized(this) { it in subscribed } }
        if (gaps.isNotEmpty()) {
            val cost = minOf(gaps.sumOf { (synchronized(this) { subscribedOn[it] } ?: BookChannel.BOOK).weight }.toDouble(), capacity)
            waitFor(cost)
            send(buildJsonObject {
                put("nonce", nonce.incrementAndGet())
                putJsonObject("snapshot") { putJsonObject("markets") { gaps.forEach { put(it, (synchronized(this@NovigStream) { subscribedOn[it] } ?: BookChannel.BOOK).wire) } } }
            })
            spend(cost)
        }
        maybeCanary()
    }

    /** The private `orders` channel (1 token): subscribed once a connection, asked again after a gap, dropped when the switch goes off. */
    private suspend fun privateStep() {
        val (want, sent) = synchronized(this) { if (socket == null) return; ordersOn to privateSent }
        if (want && !sent) {
            waitFor(1.0)
            val n = nonce.incrementAndGet()
            synchronized(this) {
                if (socket == null) return
                privateSent = true
                privateNonce = n
            }
            send(buildJsonObject {
                put("nonce", n)
                putJsonObject("subscribe") { putJsonArray("private") { add(JsonPrimitive("orders")) } }
            })
            spend(1.0)
        } else if (!want && sent) {
            synchronized(this) { privateSent = false }
            send(buildJsonObject {
                put("nonce", nonce.incrementAndGet())
                putJsonArray("unsubscribe") { add(JsonPrimitive("PRIVATE")) }
            })
            spend(1.0)
        }
    }

    // ---- the canary and the watchdog --------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Once per connection, when `bbo` snapshots are in: a few `bbo` markets are asked for with the `snapshot` verb on `book` (which leaves the subscription alone) and the best bids compared. The
     * reply is routed by its nonce ([finishCanary]) and never applied to the books.
     */
    private suspend fun maybeCanary() {
        val ids: List<String>
        val seqs: Map<String, Long>
        synchronized(this) {
            if (canaryDone || canaryNonce != 0L || socket == null || !bboActive() || bboSnapshots < minOf(canaryMarkets, 3) || clock() < canaryNotBeforeMs) return
            val picked = ArrayList<String>()
            val sq = HashMap<String, Long>()
            for (id in allWanted()) {
                if (picked.size >= canaryMarkets) break
                if (subscribedOn[id] != BookChannel.BBO || books.channelOf(id) != BookChannel.BBO || id in canaryTried) continue
                val b = books.book(id) ?: continue
                if (b.bidsByOutcome.isEmpty()) continue
                picked += id
                sq[id] = b.seq
            }
            if (picked.size < minOf(canaryMarkets, 3)) return
            ids = picked
            seqs = sq
            canaryDone = true
            canaryTried += picked
        }
        val cost = minOf(ids.size * BookChannel.BOOK.weight.toDouble(), capacity)
        waitFor(cost)
        val n = nonce.incrementAndGet()
        synchronized(this) {
            canaryNonce = n
            canarySentAtMs = clock()
            canaryIds = ids
            canarySeqs = seqs
        }
        send(buildJsonObject {
            put("nonce", n)
            putJsonObject("snapshot") { putJsonObject("markets") { ids.forEach { put(it, BookChannel.BOOK.wire) } } }
        })
        spend(cost)
    }

    /** The canary's reply: the best resting bid of each outcome from the `book` snapshot against the `bbo` state held for the same market. */
    private fun finishCanary(snapshot: JsonObject) {
        val ids: List<String>
        val seqs: Map<String, Long>
        synchronized(this) {
            ids = canaryIds
            seqs = canarySeqs
            canaryNonce = 0L
        }
        var checked = 0
        var matched = 0
        var skipped = 0
        val bad = ArrayList<String>()
        for (id in ids) {
            val full = ((snapshot[id] as? JsonObject)?.get("book") as? JsonObject)?.get("orders") as? JsonObject
            val held = books.book(id)
            if (full == null || held == null || !held.partial) { skipped++; continue }
            // The state moved between the two replies: the comparison would be of two different moments.
            if (held.seq != seqs[id]) { skipped++; continue }
            checked++
            var same = true
            val outcomes = (full.keys + held.bidsByOutcome.keys)
            for (o in outcomes) {
                val levels = (full[o] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { el ->
                    val ob = el as? JsonObject ?: return@mapNotNull null
                    val price = ob["price"]?.jsonPrimitive?.content?.let(com.tjshea.vigilant.data.novig.NovigText::priceMilli) ?: return@mapNotNull null
                    val qty = ob["qty"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    price to qty
                }
                val best = levels.maxOfOrNull { it.first }
                val bestQty = if (best == null) 0L else levels.filter { it.first == best }.sumOf { it.second }
                val top = held.bidsByOutcome[o]?.firstOrNull()
                if (best == null && top == null) continue
                if (best == null || top == null || top.priceMilli != best || top.contracts != bestQty) {
                    same = false
                    if (bad.size < 3) bad += "$id/${o.take(8)}: bbo ${top?.let { "${it.priceMilli}×${it.contracts}" } ?: "none"} vs book ${best?.let { "$it×$bestQty" } ?: "none"}"
                }
            }
            if (same) matched++
        }
        val detail = "$matched of $checked markets' best bids matched the book" + (if (skipped > 0) " ($skipped skipped: moved or unreadable)" else "") + (if (bad.isNotEmpty()) "; " + bad.joinToString("; ") else "")
        canary = Canary(clock(), checked, matched, skipped, detail)
        if (checked >= 3 && matched * 3 >= checked * 2) {
            // Verified: again on other markets after [canaryEveryMs].
            synchronized(this) { canaryStrikes = 0; canaryDone = false; canaryNotBeforeMs = clock() + canaryEveryMs; canaryTried.clear() }
        }
        if (checked >= 3 && matched * 3 < checked * 2) {
            // A reply can overtake a market push still queued behind it (the docs send replies first), so one disagreement is asked again on other markets a few seconds on; two give `bbo` up.
            val strikes = synchronized(this) { ++canaryStrikes }
            if (strikes >= 2) {
                fallBackFromBbo("the best-bid feed disagreed with the book ($detail)")
            } else {
                synchronized(this) { canaryDone = false; canaryNotBeforeMs = clock() + CANARY_RETRY_MS }
            }
        }
    }

    /** Run from the idle loop: gives up on `bbo` when markets were subscribed on it and not one snapshot came, and drops a canary that was never answered. */
    private fun watchdog() {
        val now = clock()
        val reason = synchronized(this) {
            if (canaryNonce != 0L && now - canarySentAtMs > CANARY_WAIT_MS) {
                canaryNonce = 0L
                canary = Canary(now, 0, 0, canaryIds.size, "no reply to the book check in ${CANARY_WAIT_MS / 1000} s (inconclusive)")
            }
            if (bboActive() && bboFirstSentAtMs != 0L && bboSnapshots == 0 && now - bboFirstSentAtMs > bboDataWaitMs && subscribedOn.values.count { it == BookChannel.BBO } >= 3) {
                "Novig sent no best-bid snapshot in ${bboDataWaitMs / 1000} s"
            } else {
                null
            }
        }
        if (reason != null) fallBackFromBbo(reason)
        else if (synchronized(this) { bboActive() && !canaryDone && canaryNonce == 0L && canaryNotBeforeMs > 0L && now >= canaryNotBeforeMs }) scheduleSync()
    }

    private fun refill() = synchronized(this) {
        val now = clock()
        tokens = minOf(capacity, tokens + (now - tokensAt).coerceAtLeast(0) / 1000.0 * refillPerSec)
        tokensAt = now
    }

    private fun haveTokens(cost: Double): Boolean {
        refill()
        return synchronized(this) { tokens + 1e-9 >= cost }
    }

    private suspend fun waitFor(cost: Double) {
        refill()
        val short = synchronized(this) { cost - tokens }
        if (short > 0) delay((short / refillPerSec * 1000).toLong() + 50)
    }

    private fun spend(cost: Double) {
        refill()
        synchronized(this) { tokens -= cost }
    }

    private fun send(message: JsonObject) {
        synchronized(this) { socket }?.send(message.toString())
    }

    /**
     * Whether [webSocket] is the connection in use. A socket [close] let go of (or one replaced by
     * a reconnect) still gets its last callbacks on OkHttp's thread: they must change nothing,
     * or a deliberate close reads "Failed" and a quick reconnect is torn down by the old socket's
     * goodbye (full test, 2026-09-27).
     */
    private fun current(webSocket: WebSocket): Boolean = synchronized(this) { socket === webSocket }

    private fun held(marketId: String): Boolean = synchronized(this) { marketId in subscribed }

    /** A reply's error code, top level or under `error` (Novig's frames don't document one shape). */
    private fun errorCode(msg: JsonObject): String? =
        (msg["code"] as? JsonPrimitive)?.content ?: ((msg["error"] as? JsonObject)?.get("code") as? JsonPrimitive)?.content

    private fun errorText(msg: JsonObject): String =
        listOfNotNull(
            errorCode(msg), (msg["message"] as? JsonPrimitive)?.content,
            ((msg["error"] as? JsonObject)?.get("message") as? JsonPrimitive)?.content,
        ).joinToString(" ")

    private fun refused(msg: JsonObject, code: String) {
        val n = (msg["nonce"] as? JsonPrimitive)?.longOrNull
        var bboLost = false
        synchronized(this) {
            // A throttled frame is answered with no nonce: every unanswered subscribe may be the one.
            val lost = if (n != null) unanswered.remove(n).orEmpty() else unanswered.values.flatten().also { unanswered.clear() }
            bboLost = lost.any { subscribedOn[it] == BookChannel.BBO }
            subscribed.removeAll(lost.toSet())
            lost.forEach { subscribedOn.remove(it) }
            when (code) {
                // Our token model ran ahead of Novig's: start it from empty.
                "RATE_LIMIT_EXCEEDED" -> { tokens = 0.0; tokensAt = clock() }
                // Held fewer than we thought possible: ask for half of what was refused next time.
                "SUBSCRIPTION_LIMIT_EXCEEDED" -> limit = subscribed.size + lost.size / 2
                // Anything else: one market (or all of them) was refused. A lone market is left alone for a while; a batch is asked for again in halves until the bad one stands alone.
                else -> if (lost.isNotEmpty()) {
                    lastRefusal = code
                    if (lost.size == 1) lost.forEach { refusedUntil[it] = clock() + REFUSED_PAUSE_MS } else chunk = maxOf(1, lost.size / 2)
                }
            }
        }
        // A refusal that names the best-bid channel, or three refused requests of it with not one snapshot, is Novig saying `bbo` is not for this key: back to `book`.
        if (bboLost && code != "RATE_LIMIT_EXCEEDED" && code != "SUBSCRIPTION_LIMIT_EXCEEDED") {
            val text = errorText(msg)
            val names = text.contains("bbo", ignoreCase = true) || text.contains("channel", ignoreCase = true)
            val strikes = synchronized(this) { ++bboRefusals }
            if (names) fallBackFromBbo("Novig refused the best-bid channel ($text)")
            else if (strikes >= 3 && bboSnapshots == 0) fallBackFromBbo("Novig refused every best-bid subscribe ($text)")
        }
        scheduleSync()
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current(webSocket)) return
            openedAtMs = clock()
            _state.value = StreamState.Live(clock(), 0)
            scheduleSync()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!current(webSocket)) return
            val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            errorCode(msg)?.let { code ->
                val n = (msg["nonce"] as? JsonPrimitive)?.longOrNull
                if (n != null && n == synchronized(this@NovigStream) { privateNonce }) {
                    // The private stream refused: orders are then read the way they always were (REST), and the stream says why.
                    privateError = errorText(msg)
                    synchronized(this@NovigStream) { ordersOn = false }
                    return
                }
                refused(msg, code)
                return
            }
            val replyTo = (msg["nonce"] as? JsonPrimitive)?.longOrNull
            replyTo?.let { n -> synchronized(this@NovigStream) { unanswered.remove(n) } }
            // The canary's answer is a comparison, not state: it must never reach the books (a `book` snapshot would turn a `bbo` market into a `book` one).
            if (replyTo != null && replyTo == synchronized(this@NovigStream) { canaryNonce }) {
                (msg["snapshot"] as? JsonObject)?.let { finishCanary(it) }
                return
            }
            (msg["snapshot"] as? JsonObject)?.forEach { (marketId, body) ->
                if (!held(marketId)) return@forEach
                val b = body as? JsonObject ?: return@forEach
                (b["book"] as? JsonObject)?.let { books.applySnapshot(marketId, it) }
                (b["bbo"] as? JsonObject)?.let {
                    if (books.applyBboSnapshot(marketId, it)) {
                        bboSnapshots++
                        if (books.book(marketId)?.bidsByOutcome?.isNotEmpty() == true) bboNonEmpty++
                    } else {
                        bboUnreadable++
                    }
                }
                (b["lifecycle"] as? JsonObject)?.let { books.applyLifecycleSnapshot(marketId, it) }
            }
            if (bboUnreadable >= 3 && bboSnapshots == 0) fallBackFromBbo("Novig's best-bid snapshots could not be read")
            // Every snapshot empty is not a quiet slate, it is a shape read wrong: a pregame board always has bids somewhere.
            if (bboSnapshots >= EMPTY_CHECK_MIN && bboNonEmpty == 0) fallBackFromBbo("all $bboSnapshots best-bid snapshots had no bid in them")
            // Enough best-bid snapshots are in for the once-a-connection check against the book: a sync pass ends with it.
            if (!canaryDone && bboSnapshots >= minOf(canaryMarkets, 3)) scheduleSync()
            var gap = false
            // The subaccount's own orders: a snapshot answers the subscribe, deltas follow; a heartbeat ahead of the last seq says a message was lost, and the subscribe is sent again.
            (msg["orders"] as? JsonObject)?.let { if (!orderEvents.apply(it)) { synchronized(this@NovigStream) { privateSent = false }; gap = true } }
            ((msg["heartbeat"] as? JsonObject)?.get("orders") as? JsonPrimitive)?.longOrNull?.let { hb ->
                if (!orderEvents.heartbeat(hb)) { synchronized(this@NovigStream) { privateSent = false }; gap = true }
            }
            (msg["delta"] as? JsonObject)?.forEach { (marketId, body) ->
                // Pushes for a market just dropped are ignored (its unsubscribe may still be on the way).
                if (!held(marketId)) return@forEach
                val b = body as? JsonObject ?: return@forEach
                (b["book"] as? JsonObject)?.let { if (!books.applyDelta(marketId, it)) gap = true }
                (b["bbo"] as? JsonObject)?.let {
                    // A delta that names no outcome anywhere is a shape read wrong: the best bids would go stale without a word.
                    val readable = (it["deltas"] as? kotlinx.serialization.json.JsonArray)?.any { d -> d is JsonObject && (d["outcomeId"] != null || d["outcome"] != null) } == true
                    if (readable) bboDeltaOk++ else bboDeltaOdd++
                    if (bboDeltaOdd >= 5 && bboDeltaOk == 0) fallBackFromBbo("Novig's best-bid updates could not be read")
                    if (!books.applyBboDelta(marketId, it)) gap = true
                }
                (b["lifecycle"] as? JsonObject)?.let { if (!books.applyLifecycleDelta(marketId, it)) gap = true }
            }
            if (gap) scheduleSync()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(
            webSocket,
            when {
                reason.startsWith("GEOLOCATION") -> "Novig wants your location confirmed: open the Novig app."
                reason == "SLOW_CONSUMER" -> "Novig closed the live feed (the phone fell behind)."
                else -> "Novig closed the live feed ($code${if (reason.isNotBlank()) " $reason" else ""})."
            },
            quick = !reason.startsWith("GEOLOCATION"),
        )

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val status = response?.code
            // Novig's code says whether it judged the network's address or the phone's location check (the body can be read once).
            val refusal = if (status != null && (status == 451 || status == 423)) NovigApiException(status, refusalCode(response), null) else null
            // 429 on the upgrade: the `stream` bucket is empty (another connection of the key, or the last bulk subscribe, spent it). The docs: wait the seconds in Retry-After, then retry.
            val throttledFor = if (status == 429) (response.header("Retry-After")?.trim()?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_S) else null
            if (throttledFor != null) upgrade429s++
            dropped(
                webSocket,
                when {
                    status == 401 -> "Novig refused the key for its live feed (401)."
                    status == 403 -> "This key can't open Novig's live feed (403)."
                    throttledFor != null -> "Novig's live feed throttle was empty (429): trying again in ${throttledFor.coerceIn(1, 30)} s."
                    refusal != null && status == 451 -> refusal.brief
                    refusal != null -> refusal.advice
                    else -> "Novig's live feed dropped (${t.message ?: t.javaClass.simpleName})."
                },
                // The key's REST route comes back after a network refusal in 2 minutes; the feed waits the same, not the 5 of a feed that broke some other way.
                retryMs = when {
                    throttledFor != null -> (throttledFor.coerceIn(1, 30) * 1000 + 500)
                    refusal?.networkRefusal == true -> networkRetryMs
                    else -> retryAfterFailureMs
                },
                quick = status == null || status >= 500,
                exact = throttledFor != null,
            )
        }
    }

    /** The `code` in a refused upgrade's JSON body, if it has one. */
    private fun refusalCode(response: Response?): String? = runCatching {
        (json.parseToJsonElement(response?.body?.string().orEmpty()) as? JsonObject)?.get("code")?.jsonPrimitive?.content
    }.getOrNull()

    private fun fail(message: String, retryMs: Long = retryAfterFailureMs) {
        synchronized(this) {
            failedAtMs = clock()
            retryDelayMs = retryMs
            problem = message
        }
        _state.value = StreamState.Failed(message, clock())
    }

    /**
     * [exact]: [retryMs] is the wait Novig itself named (a 429's Retry-After), kept as it is. With the connection kept open ([configureFeed]) a plain drop or a throttled upgrade is retried
     * by the stream itself, not at the next scan: after [liveRetryMs], doubling to a minute while connections keep failing quickly.
     */
    private fun dropped(webSocket: WebSocket, message: String, retryMs: Long = retryAfterFailureMs, quick: Boolean = false, exact: Boolean = false) {
        val jobs = synchronized(this) {
            // Not the connection in use (closed on purpose, or replaced): nothing was dropped.
            if (socket !== webSocket) return
            socket = null
            subscribed.clear()
            subscribedOn.clear()
            unanswered.clear()
            canaryNonce = 0L
            privateSent = false
            listOf(syncJob, idleJob).also { syncJob = null; idleJob = null }
        }
        books.clear()
        orderEvents.reset()
        jobs.forEach { it?.cancel() }
        drops++
        lastDrop = message
        val lived = if (openedAtMs > 0L) clock() - openedAtMs else 0L
        openedAtMs = 0L
        if (lived >= STABLE_MS) quickFails = 0 else if (quick || exact) quickFails++
        // A plain drop with the live lane waiting on this feed is tried again in seconds; a refused key or location is not.
        val live = quick && retryMs == retryAfterFailureMs && synchronized(this) { livePinned.isNotEmpty() }
        val keepTrying = (quick || exact) && synchronized(this) { keepOpen && clock() - lastUsedMs < idleMs }
        val wait = when {
            exact -> retryMs
            keepTrying -> minOf(liveRetryMs shl minOf(quickFails - 1, 5).coerceAtLeast(0), MAX_RECONNECT_MS)
            live -> liveRetryMs
            else -> retryMs
        }
        fail(message, wait)
        if (keepTrying) scheduleReconnect(wait)
    }

    private fun scheduleReconnect(delayMs: Long) {
        synchronized(this) {
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(delayMs)
                val go = synchronized(this@NovigStream) { socket == null && keepOpen && clock() - lastUsedMs < idleMs }
                if (go) connect()
            }
        }
    }

    // ---- diagnostics ------------------------------------------------------------------------------------------------------------------------------------------------------

    override fun feedReport(): List<String> {
        val now = clock()
        return synchronized(this) {
            val onBbo = subscribedOn.values.count { it == BookChannel.BBO }
            val onBook = subscribedOn.values.count { it == BookChannel.BOOK }
            val mode = when {
                !bboOn -> "full book on every market (the best-bid feed is switched off)"
                bboFallback != null -> "full book on every market: the best-bid feed was given up on ${(now - bboFallbackAtMs) / 1000} s ago, ${bboFallback}"
                else -> "best-bid feed (bbo) for screening, full book where depth is needed"
            }
            buildList {
                add("feed: $mode")
                add("held: ${subscribed.size} markets of room ${if (bboActive()) limit else minOf(limit, MAX_MARKETS)} ($onBbo on bbo, $onBook on book, ${deepUntil.count { it.value > now }} deepened) · stream allowance ${capacity.toInt()} refilling ${refillPerSec.toInt()} a second (${if (tuned) "this key's own /v3/limits" else "documented default: /v3/limits not read yet"}), ${tokens.toInt().coerceAtLeast(0)} spare · ${if (keepOpen) "kept open between scans" else "closed ${idleMs / 1000} s after the last scan"}")
                add("connection: ${when (val st = _state.value) { is StreamState.Live -> "live since ${(now - st.sinceMs) / 1000} s"; is StreamState.Connecting -> "connecting"; is StreamState.Failed -> "failed: ${st.message}"; StreamState.Off -> "off" }} · connects $connects, drops $drops, upgrade 429s $upgrade429s" + (lastDrop?.let { " · last drop: $it" } ?: ""))
                add("bbo: ${bboSnapshots} snapshots this connection, ${bboUnreadable} unreadable, ${deepened} markets deepened in all")
                add("canary: " + (canary?.let { "${(now - it.atMs) / 1000} s ago: ${it.detail}" } ?: "not run yet"))
                add("orders: " + (if (!ordersOn) (privateError?.let { "refused by Novig ($it): results are read by request" } ?: "pushed order results are switched off: results are read by request") else if (orderEvents.live) "pushed (${orderEvents.events} events heard, ${orderEvents.gaps} gaps)" else "subscribing / not live: results are read by request"))
            }
        }
    }

    companion object {
        private val DEFAULT_OWNER = Any()
        const val STREAM_CAPACITY = 512
        const val REFILL_PER_SEC = 4.0
        const val UPGRADE_COST = 32
        const val BOOK_WEIGHT = 16

        /** Documented: 2,048 markets watched per connection (8,192 on the keys that report it: `GET /v3/limits`). */
        const val MAX_WATCHED = 2_048

        /** A margin under [MAX_WATCHED]. */
        /** A market Novig refused (not for a limit) is left alone this long. */
        const val REFUSED_PAUSE_MS = 10 * 60_000L
        const val MAX_MARKETS = 2_000

        /** Closed this long after a scan last used it: rechecks right after a scan are instant. */
        const val IDLE_CLOSE_MS = 2 * 60_000L

        /** With the connection kept open: closed this long after the last use (the scan, auto-scan and the lanes all touch it). */
        const val KEEP_OPEN_MS = 20 * 60_000L
        private const val IDLE_CHECK_MS = 15_000L

        /** After a failure, scans read by REST this long before the feed is tried again. */
        const val RETRY_AFTER_FAILURE_MS = 5 * 60_000L

        /** How long a stopped live lane's markets stay subscribed. */
        const val LINGER_MS = 3 * 60_000L

        /** After a plain drop with live markets pinned. */
        const val LIVE_RETRY_MS = 3_000L

        /** A 429 without a Retry-After. */
        const val DEFAULT_RETRY_AFTER_S = 5L

        /** A connection that lived this long starts the reconnect back-off over. */
        const val STABLE_MS = 30_000L
        const val MAX_RECONNECT_MS = 60_000L

        /** A market asked a complete book of stays on `book` this long. */
        const val DEEP_HOLD_MS = 10 * 60_000L

        /** Markets subscribed on `bbo` with not one snapshot after this long: Novig does not serve it to this key. */
        const val BBO_DATA_WAIT_MS = 12_000L
        const val CANARY_MARKETS = 6
        const val CANARY_WAIT_MS = 10_000L
        const val CANARY_RETRY_MS = 3_000L
        const val CANARY_EVERY_MS = 10 * 60_000L
        const val EMPTY_CHECK_MIN = 25
    }
}


/**
 * The live engine's handle on the app's ONE stream, found fresh on every call (a reconnected key makes a new one): the key's `stream` bucket is per key, so a second connection of
 * its own starved on it ("Novig live book not subscribed yet" on every main line, Tj 2026-10-10). Its markets are pinned ahead of a scan's, [listener] hears every change, and
 * [close] takes both away without closing the stream.
 */
class SharedStreamView(private val provider: () -> NovigStream?, private val listener: BookListener) : PushedBooks {
    private var bound: NovigStream? = null
    private var pinned: List<String> = emptyList()

    @Synchronized
    private fun stream(): NovigStream? {
        val now = provider()
        if (now !== bound) {
            bound?.books?.removeListener(listener)
            now?.books?.addListener(listener)
            bound = now
            if (now != null && pinned.isNotEmpty()) now.pin(this, pinned)
        }
        return now
    }

    override fun watch(marketIds: Collection<String>) = watchLive(marketIds)

    override fun watchLive(marketIds: Collection<String>) {
        pinned = marketIds.distinct()
        stream()?.pin(this, pinned)
    }

    override fun open() { stream()?.open() }
    override fun live(marketIds: Collection<String>): Map<String, NovigBook> = stream()?.live(marketIds).orEmpty()
    override fun liveTop(marketIds: Collection<String>): Map<String, NovigBook> = stream()?.liveTop(marketIds).orEmpty()
    override fun deepen(marketIds: Collection<String>) { stream()?.deepen(marketIds) }
    override fun room(): Int = stream()?.room() ?: NovigStream.MAX_MARKETS
    override fun feedReport(): List<String> = stream()?.feedReport().orEmpty()
    override fun bookGap(marketId: String): String? = stream()?.bookGap(marketId) ?: "Novig live feed is not connected (no key)"
    override fun problemSince(sinceMs: Long): String? = stream()?.problemSince(sinceMs)

    @Synchronized
    override fun close() {
        bound?.books?.removeListener(listener)
        bound?.pin(this, emptyList())
        bound = null
        pinned = emptyList()
    }
}
