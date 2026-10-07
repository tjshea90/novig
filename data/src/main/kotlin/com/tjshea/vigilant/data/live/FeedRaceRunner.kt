package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.scanner.TrapGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** A game that is live on Novig right now: [description] is Novig's "Away @ Home", [sport] Novig's sport name, [marketId] its moneyline (null when none is open). */
data class LiveGame(val eventId: String, val description: String, val league: String, val sport: String, val marketId: String?)

/** One reply from a feed: its body and how long the request took. */
class Fetched(val body: String, val rttMs: Long)

interface FeedSocket {
    fun send(text: String): Boolean
    fun close()
}

/** Opens a websocket; [onText] gets every text frame, [onClosed] is told once when it ends (a reason). The app's is OkHttp's ([OkHttpFeedSockets]); the tests' is a fake. */
fun interface SocketOpener {
    fun open(url: String, onText: (String) -> Unit, onClosed: (String) -> Unit): FeedSocket
}

data class FeedRaceStatus(
    val running: Boolean = false,
    val sinceMs: Long? = null,
    val liveGames: Int = 0,
    val sports: Set<String> = emptySet(),
    val readings: Long = 0,
    val novigTrades: Long = 0,
    val oddsTicks: Long = 0,
    val requests: Long = 0,
    val problem: String? = null,
    val verdict: String? = null,
    val reportAtMs: Long? = null,
)

/**
 * The feed race in the app (Tj, 2026-10-07: "test all available sources that can be used as a rapid source of odds or scores ... implemented in the app for live betting"; RESEARCH.md
 * §99, §106). While a game is live on Novig it holds every free feed of it open and stamps each reading on arrival: Sofascore's live REST (polled, only the sports Novig has live), Polymarket's
 * sports socket and its CLOB odds socket (push, no key), ESPN's scoreboard, the NHL's score route and MLB's schedule (polled with a cache-busting query), and Novig's own moneyline trades
 * (the engine's time on each: the instant its price moved). [FeedRace.report] says which was first and whether any beat Novig. **It places no order and is given no way to**: no trading client,
 * no key; the public routes only. Off by default; each feed is asked only while Novig has a live game it covers, so with nothing live it makes one catalog request a minute.
 */
class FeedRaceRunner(
    private val scope: CoroutineScope,
    private val fetch: suspend (url: String) -> Fetched?,
    private val sockets: SocketOpener,
    private val liveGames: suspend () -> List<LiveGame>,
    private val novigTrades: suspend (marketId: String) -> List<TrapGuard.Trade>,
    private val journal: FeedRaceJournal,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pollMs: Long = POLL_MS,
    private val discoverMs: Long = DISCOVER_MS,
    private val reportMs: Long = REPORT_MS,
) {
    private val _status = MutableStateFlow(FeedRaceStatus())
    val status: StateFlow<FeedRaceStatus> = _status.asStateFlow()

    @Volatile private var job: Job? = null
    @Volatile private var games: List<LiveGame> = emptyList()
    @Volatile var lastReport: FeedRace.Report? = null
        private set

    val running: Boolean get() = job?.isActive == true

    private val out = Channel<Any>(Channel.UNLIMITED)
    private val readings = AtomicLong()
    private val trades = AtomicLong()
    private val odds = AtomicLong()
    private val requests = AtomicLong()
    private val polyGames = ConcurrentHashMap<String, FeedParsers.Reading>()

    @Synchronized
    fun start() {
        if (running) return
        readings.set(0); trades.set(0); odds.set(0); requests.set(0)
        _status.value = FeedRaceStatus(running = true, sinceMs = clock())
        job = scope.launch {
            launch { writer() }
            launch { guarded("catalog") { discover() } }
            launch { guarded("Sofascore") { sofascoreLoop() } }
            launch { guarded("league feeds") { leagueFeedsLoop() } }
            launch { guarded("Polymarket scores") { polymarketScores() } }
            launch { guarded("Polymarket odds") { polymarketOdds() } }
            launch { guarded("Novig trades") { novigTradesLoop() } }
            launch { guarded("report") { reportLoop() } }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _status.value = _status.value.copy(running = false)
    }

    /** One part's loop, kept going: a failure is the status's problem line, never an end to the others. */
    private suspend fun guarded(what: String, body: suspend () -> Unit) {
        while (scope.isActive) {
            try {
                body()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = _status.value.copy(problem = "$what: ${e.message ?: e.javaClass.simpleName}")
                delay(RETRY_MS)
            }
        }
    }

    // ---- what is live ------------------------------------------------------------------------------------------------------------

    private suspend fun discover() {
        while (true) {
            try {
                games = liveGames()
                _status.value = _status.value.copy(liveGames = games.size, sports = games.mapNotNull { sofaSport(it.sport) }.toSortedSet())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = _status.value.copy(problem = "Novig's catalog: ${e.message ?: e.javaClass.simpleName}")
            }
            delay(discoverMs)
        }
    }

    // ---- polled feeds ------------------------------------------------------------------------------------------------------------

    private fun tracker(src: String) = FeedRace.Tracker(src) { out.trySend(it) }

    private suspend fun get(url: String): Fetched? {
        requests.incrementAndGet()
        return try {
            fetch(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun see(tr: FeedRace.Tracker, readings: List<FeedParsers.Reading>, f: Fetched, sentMs: Long) {
        val mid = sentMs + f.rttMs / 2
        for (r in readings) tr.see(r.gameId, r.home, r.away, r.h, r.a, mid, f.rttMs, r.live)
    }

    private suspend fun sofascoreLoop() {
        val tr = tracker("sofa")
        while (true) {
            val t = clock()
            for (sport in games.mapNotNull { sofaSport(it.sport) }.distinct()) {
                val sent = clock()
                val f = get("https://api.sofascore.com/api/v1/sport/$sport/events/live") ?: continue
                see(tr, FeedParsers.sofascore(f.body, sport), f, sent)
                delay(GAP_MS)
            }
            delay((pollMs - (clock() - t)).coerceAtLeast(MIN_WAIT_MS))
        }
    }

    private suspend fun leagueFeedsLoop() {
        val espn = tracker("espn")
        val nhl = tracker("nhl")
        val mlb = tracker("mlb")
        while (true) {
            val t = clock()
            val leagues = games.map { it.league }.toSet()
            for (lg in leagues) {
                val path = ESPN_PATHS[lg] ?: continue
                val sent = clock()
                val f = get("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?_cb=${bust()}") ?: continue
                see(espn, FeedParsers.espn(f.body), f, sent)
                delay(GAP_MS)
            }
            if ("NHL" in leagues) {
                val day = java.time.Instant.ofEpochMilli(clock() - 5 * 3_600_000L).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                val sent = clock()
                get("https://api-web.nhle.com/v1/score/$day?_cb=${bust()}")?.let { see(nhl, FeedParsers.nhl(it.body), it, sent) }
                delay(GAP_MS)
            }
            if ("MLB" in leagues) {
                val today = java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                val sent = clock()
                get("https://statsapi.mlb.com/api/v1/schedule?sportId=1&hydrate=linescore&startDate=${today.minusDays(1)}&endDate=$today&_cb=${bust()}")?.let { see(mlb, FeedParsers.mlb(it.body), it, sent) }
                delay(GAP_MS)
            }
            delay((pollMs - (clock() - t)).coerceAtLeast(MIN_WAIT_MS))
        }
    }

    private fun bust() = "${clock()}${(0..999).random()}"

    // ---- push feeds --------------------------------------------------------------------------------------------------------------

    private suspend fun polymarketScores() {
        val tr = tracker("poly")
        while (true) {
            if (games.isEmpty()) { delay(discoverMs); continue }
            val closed = CompletableDeferred<String>()
            var sock: FeedSocket? = null
            sock = sockets.open(POLY_SCORES_URL, onText = { text ->
                if (text.trim() == "ping") {
                    sock?.send("pong")
                } else {
                    FeedParsers.polymarketScore(text)?.let { r ->
                        polyGames[r.gameId] = r
                        tr.see(r.gameId, r.home, r.away, r.h, r.a, clock(), 0, r.live)
                    }
                }
            }, onClosed = { closed.complete(it) })
            try {
                while (!closed.isCompleted && games.isNotEmpty()) delay(SOCKET_CHECK_MS)
            } finally {
                runCatching { sock.close() }
            }
            delay(RETRY_MS)
        }
    }

    private suspend fun polymarketOdds() {
        val tokens = ConcurrentHashMap<String, Pair<String, String>>()   // token -> (event title, outcome)
        val resolved = HashSet<String>()
        val last = ConcurrentHashMap<String, Double>()

        /** Looks up the match-winner market of each Polymarket game that is one of Novig's live games, once each. */
        suspend fun resolveNew() {
            for ((gid, r) in polyGames.entries.toList()) {
                if (gid in resolved) continue
                if (games.none { g -> FeedRace.sameGame(r.home to r.away, novigNames(g.description)) }) continue
                resolved += gid
                val f = get("https://gamma-api.polymarket.com/events?game_id=$gid") ?: continue
                FeedParsers.polymarketMarket(f.body)?.let { m -> for ((tk, o) in m.tokens) tokens[tk] = m.title to o }
                delay(GAP_MS)
            }
        }
        while (true) {
            if (games.isEmpty()) { delay(discoverMs); continue }
            resolveNew()
            if (tokens.isEmpty()) { delay(ODDS_LOOK_MS); continue }
            val subscribed = tokens.keys.toSet()
            val closed = CompletableDeferred<String>()
            val sock = sockets.open(POLY_ODDS_URL, onText = { text ->
                for (q in FeedParsers.polymarketQuotes(text)) {
                    val who = tokens[q.assetId] ?: continue
                    if (q.ask - q.bid > MAX_SPREAD) continue   // an empty book's 0.01 / 0.99 is not a price
                    val mid = (q.bid + q.ask) / 2.0
                    val prev = last.put(q.assetId, mid)
                    if (prev == null || Math.abs(mid - prev) >= MIN_ODDS_MOVE) out.trySend(FeedRace.OddsTick(who.first, who.second, mid, clock(), q.serverMs))
                }
            }, onClosed = { closed.complete(it) })
            sock.send("""{"assets_ids":[${subscribed.joinToString(",") { "\"$it\"" }}],"type":"market"}""")
            try {
                // Until it closes, the games go, or a game with new tokens turns up (then it is opened again with all of them).
                while (!closed.isCompleted && games.isNotEmpty() && tokens.keys.toSet() == subscribed) {
                    delay(ODDS_LOOK_MS)
                    resolveNew()
                }
            } finally {
                runCatching { sock.close() }
            }
            delay(RETRY_MS)
        }
    }

    // ---- Novig -------------------------------------------------------------------------------------------------------------------

    private suspend fun novigTradesLoop() {
        val seen = HashSet<String>()
        while (true) {
            val t = clock()
            for (g in games.filter { it.marketId != null }.take(MAX_TRADE_GAMES)) {
                val ticks = try {
                    requests.incrementAndGet()
                    novigTrades(g.marketId!!)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                for (tr in ticks) {
                    if (seen.add("${g.marketId}|${tr.outcomeId}|${tr.price}|${tr.contracts}|${tr.atMs}")) out.trySend(FeedRace.NovigTick(g.description, g.marketId!!, tr.outcomeId, tr.price, tr.contracts, tr.atMs))
                }
                if (seen.size > SEEN_KEEP) seen.clear()
                delay(TRADES_GAP_MS)
            }
            delay((pollMs - (clock() - t)).coerceAtLeast(MIN_WAIT_MS))
        }
    }

    // ---- writing and reporting --------------------------------------------------------------------------------------------------

    /** One consumer: everything the feeds saw goes to the journal in small batches and into the counts. */
    private suspend fun writer() {
        val s = ArrayList<FeedRace.Sighting>()
        val n = ArrayList<FeedRace.NovigTick>()
        val o = ArrayList<FeedRace.OddsTick>()
        fun add(x: Any) {
            when (x) {
                is FeedRace.Sighting -> s += x
                is FeedRace.NovigTick -> n += x
                is FeedRace.OddsTick -> o += x
            }
        }
        while (true) {
            add(out.receive())
            while (true) add(out.tryReceive().getOrNull() ?: break)
            readings.addAndGet(s.size.toLong()); trades.addAndGet(n.size.toLong()); odds.addAndGet(o.size.toLong())
            withContext(Dispatchers.IO) { runCatching { journal.append(s.toList(), n.toList(), o.toList()) } }
            s.clear(); n.clear(); o.clear()
            _status.value = _status.value.copy(readings = readings.get(), novigTrades = trades.get(), oddsTicks = odds.get(), requests = requests.get())
            delay(WRITE_EVERY_MS)
        }
    }

    private suspend fun reportLoop() {
        while (true) {
            delay(reportMs)
            makeReport()
        }
    }

    /** The report over the last [WINDOW_MS] of the journal, now (off the main thread); also what Diagnostics and the share file print. */
    suspend fun makeReport(): FeedRace.Report = withContext(Dispatchers.Default) {
        val tape = journal.read(clock() - WINDOW_MS)
        FeedRace.report(tape.scores, tape.trades, tape.odds).also {
            lastReport = it
            _status.value = _status.value.copy(verdict = it.verdict(), reportAtMs = clock(), requests = requests.get())
        }
    }

    companion object {
        const val POLL_MS = 2_500L
        const val DISCOVER_MS = 60_000L
        const val REPORT_MS = 120_000L
        const val RETRY_MS = 5_000L
        const val GAP_MS = 300L
        const val TRADES_GAP_MS = 350L
        const val MIN_WAIT_MS = 200L
        const val SOCKET_CHECK_MS = 2_000L
        const val ODDS_LOOK_MS = 20_000L
        const val WRITE_EVERY_MS = 1_000L
        const val MAX_TRADE_GAMES = 10
        const val SEEN_KEEP = 50_000
        const val MAX_SPREAD = 0.10
        const val MIN_ODDS_MOVE = 0.01

        /** The report covers this much of the journal. */
        const val WINDOW_MS = 24 * 3_600_000L

        const val POLY_SCORES_URL = "wss://sports-api.polymarket.com/ws"
        const val POLY_ODDS_URL = "wss://ws-subscriptions-clob.polymarket.com/ws/market"

        /** Novig's sport name to Sofascore's `sport/<x>/events/live`; null = not read. */
        fun sofaSport(novigSport: String): String? = when (novigSport.uppercase()) {
            "TENNIS" -> "tennis"
            "FOOTBALL" -> "american-football"
            "SOCCER" -> "football"
            "BASKETBALL" -> "basketball"
            "BASEBALL" -> "baseball"
            "HOCKEY", "ICE_HOCKEY" -> "ice-hockey"
            else -> null
        }

        /** Novig league to ESPN's scoreboard path. */
        val ESPN_PATHS = mapOf(
            "NFL" to "football/nfl", "NCAAF" to "football/college-football", "NBA" to "basketball/nba", "WNBA" to "basketball/wnba", "NHL" to "hockey/nhl", "MLB" to "baseball/mlb",
        )

        /** Novig's "Away @ Home" as (home, away) for [FeedRace.sameGame]. */
        fun novigNames(description: String): Pair<String, String> = description.substringAfter(" @ ", "") to description.substringBefore(" @ ", "")
    }
}

/** OkHttp's websocket as a [SocketOpener]. */
class OkHttpFeedSockets(private val http: okhttp3.OkHttpClient) : SocketOpener {
    override fun open(url: String, onText: (String) -> Unit, onClosed: (String) -> Unit): FeedSocket {
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        fun end(why: String) {
            if (done.compareAndSet(false, true)) onClosed(why)
        }
        val ws = http.newWebSocket(
            okhttp3.Request.Builder().url(url).build(),
            object : okhttp3.WebSocketListener() {
                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) = onText(text)
                override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    end("closed $code")
                }
                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) = end("closed $code")
                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) = end(t.message ?: t.javaClass.simpleName)
            },
        )
        return object : FeedSocket {
            override fun send(text: String) = ws.send(text)
            override fun close() {
                ws.close(1000, null)
                end("closed by the app")
            }
        }
    }
}
