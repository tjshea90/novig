package com.tjshea.vigilant.data.sxbet

import com.tjshea.vigilant.data.boltodds.BoltLiveConsensus
import com.tjshea.vigilant.data.pinnodds.PinnFeedSource
import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.pinnodds.PinnSocketState
import com.tjshea.vigilant.data.reference.SxBetClient
import com.tjshea.vigilant.data.reference.SxBetConvert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs

/**
 * The live engine's fourth price feed (Tj, 2026-10-18, TCL118: "build the sx live feed now"; [com.tjshea.vigilant.data.scanner.PinnFeedChoice.SX]): SX Bet's exchange book as the live fair, spoken in the same frames the
 * Pinnodds socket sends, so the whole live lane (the matcher, the 5-second rule, the taker fee, the lag trader, live bids and every guard) runs on it unchanged. It is PUSH: a book push for a watched market, a score or a
 * fixture status wakes the worker, which waits [DEBOUNCE_MS] and re-reads just that game ([SxBetStream.addListener]); a once-a-second look only keeps lines honest (a line that lost its trusted book is CLOSED, a line that holds
 * is re-stated so the engine's "heard from this game" time is the time the book was last seen).
 *
 * Games are found by what SX itself says is in play ([SxBetStream.fixtureStatus] 2, or a live unfinished score frame), their markets read once over the public `eventId` route and kept ([SxBetClient.marketsOfEvent]),
 * their books seeded over REST and then kept by the socket. The fair per line is [SxLiveBuild]'s: the mid of the exchange spread, trusted only when pushed after the last score change. Unlike the BoltOdds average it
 * is a sharp book, but it is still one venue: real orders stay behind their own gate (`gates.sxLiveReal`, default off = paper).
 */
class SxBetLiveFeed(
    private val stream: SxBetStream,
    private val client: SxBetClient,
    private val ensureOpen: suspend () -> Unit,
    private val sink: (String, Long) -> Unit,
    private val scope: CoroutineScope,
    private val rules: () -> SxLiveBuild.Rules = { SxLiveBuild.Rules() },
    private val clock: () -> Long = System::currentTimeMillis,
) : PinnFeedSource {

    private val _state = MutableStateFlow<PinnSocketState>(PinnSocketState.Off)
    override val state: StateFlow<PinnSocketState> = _state.asStateFlow()

    @Volatile override var note: String? = null
        private set

    @Volatile override var lastFrameAtMs: Long = 0L
        private set

    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val dirty = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val events = java.util.concurrent.ConcurrentHashMap<String, SxLiveBuild.EventInfo>()
    private val readAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val eventOf = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val listener: (String) -> Unit = { key -> dirty += (eventOf[key] ?: key); signal.trySend(Unit) }
    private var worker: Job? = null
    private var ticker: Job? = null

    private class Said(val fair: Double, val points: Double?, val version: Long)
    private class GameState(val eventId: Long) { val lines = HashMap<String, Said>(); var lastLineAtMs = 0L }
    private val games = HashMap<String, GameState>()
    private var version = 1L

    @Volatile var framesSent = 0L
        private set
    @Volatile var linesClosed = 0L
        private set
    @Volatile var liveGames = 0
        private set
    @Volatile var linesLive = 0
        private set

    @Synchronized
    override fun start() {
        if (worker?.isActive == true) return
        _state.value = PinnSocketState.Connecting
        stream.addListener(listener)
        worker = scope.launch {
            runCatching { ensureOpen() }
            while (isActive) {
                signal.receive()
                delay(DEBOUNCE_MS)
                val ids = HashSet<String>()
                val it = dirty.iterator()
                while (it.hasNext()) { ids += it.next(); it.remove() }
                if (ids.isNotEmpty()) update(ids)
            }
        }
        ticker = scope.launch {
            var sinceOpen = 0L
            var sinceDiscover = DISCOVER_MS
            while (isActive) {
                delay(TICK_MS)
                sinceOpen += TICK_MS; sinceDiscover += TICK_MS
                if (sinceOpen >= KEEP_OPEN_MS) { sinceOpen = 0L; runCatching { ensureOpen() } }
                if (sinceDiscover >= DISCOVER_MS) { sinceDiscover = 0L; runCatching { discover() } }
                _state.value = if (stream.live) (_state.value as? PinnSocketState.Live ?: PinnSocketState.Live(clock())) else PinnSocketState.Connecting
                if (stream.live && clock() - stream.lastMessageMs < ALIVE_MS) lastFrameAtMs = clock()
                update(null)
            }
        }
    }

    @Synchronized
    override fun stop() {
        stream.removeListener(listener)
        worker?.cancel(); worker = null
        ticker?.cancel(); ticker = null
        dirty.clear(); games.clear(); events.clear(); eventOf.clear(); readAt.clear()
        _state.value = PinnSocketState.Off
    }

    /** SX's own list of games in play: a fixture status of 2, or a live unfinished score frame heard in the last [FRESH_SCORE_MS]. */
    internal fun liveIds(now: Long): Set<String> {
        val out = LinkedHashSet<String>()
        for ((id, st) in stream.fixtureStatus) if (st == 2) out += id
        for ((id, sc) in stream.scores) if (!sc.finished && now - sc.atMs <= FRESH_SCORE_MS && stream.fixtureStatus[id].let { it == null || it == 2 }) out += id
        return out
    }

    /** Reads the markets of every game that went live and is not known yet (and refreshes a known game's list every [REFRESH_MS]), watches their hashes and seeds their books. */
    private suspend fun discover() {
        val now = clock()
        for (id in liveIds(now)) {
            if (now - (readAt[id] ?: 0L) < REFRESH_MS) continue
            readAt[id] = now
            val markets = client.marketsOfEvent(id)
            val first = markets.firstOrNull { SxBetConvert.leagueOf(it) != null } ?: continue
            val league = SxBetConvert.leagueOf(first) ?: continue
            val info = SxLiveBuild.EventInfo(id, league, first.teamOneName, first.teamTwoName, first.gameTime * 1000L, markets.filter { SxBetConvert.leagueOf(it) == league })
            val hashes = info.markets.map { it.marketHash }
            stream.watch(hashes)
            hashes.forEach { eventOf[it] = id }
            events[id] = info
            val missing = hashes.filter { stream.books[it] == null }
            if (missing.isNotEmpty()) stream.seed(client.books(missing, viaStream = false).values)
            dirty += id; signal.trySend(Unit)
        }
    }

    /** A game's markets known to the feed (what [discover] does after its REST read); also how the tests give it a game. */
    internal fun adopt(info: SxLiveBuild.EventInfo) {
        stream.watch(info.markets.map { it.marketHash })
        info.markets.forEach { eventOf[it.marketHash] = info.eventId }
        events[info.eventId] = info
    }

    @Synchronized
    internal fun update(ids: Set<String>?) {
        val now = clock()
        val r = rules()
        val look = LinkedHashSet<String>().apply { if (ids == null) { addAll(events.keys); addAll(games.keys.map { it.removePrefix("sx:") }) } else addAll(ids) }
        var inPlay = 0
        for (eventId in look) {
            val info = events[eventId]
            val key = "sx:$eventId"
            val st = games[key]
            val g = info?.let { SxLiveBuild.game(it, { h -> stream.books[h] }, stream.scores[eventId], stream.fixtureStatus[eventId], stream.lastMessageMs, now, r) }
            if (g != null) inPlay++
            if (g == null) {
                if (st != null) {
                    val closing = st.lines.keys.toList()
                    if (closing.isNotEmpty()) { send(frame(st.eventId, null, closing.map { closed(it, ++version) })); linesClosed += closing.size; st.lines.clear() }
                    if (now - st.lastLineAtMs > FORGET_MS) { send(delFrame(st.eventId)); games.remove(key) }
                }
                continue
            }
            val state = games.getOrPut(key) { GameState(idOf(key)) }
            val markets = ArrayList<JsonObject>()
            val open = HashSet<String>()
            for (line in g.lines) {
                open += line.key
                val said = state.lines[line.key]
                val p = line.fair.getValue(if (line.type == PinnLineType.TOTAL) PinnSide.OVER else PinnSide.HOME)
                if (said == null || abs(said.fair - p) >= MIN_STEP || said.points != line.points) {
                    val v = ++version
                    state.lines[line.key] = Said(p, line.points, v)
                    markets += market(line, v)
                }
            }
            for (k in state.lines.keys.toList()) if (k !in open) { markets += closed(k, ++version); state.lines.remove(k); linesClosed++ }
            state.lastLineAtMs = now
            send(frame(state.eventId, g, markets))
        }
        liveGames = games.count { it.value.lines.isNotEmpty() }
        linesLive = games.values.sumOf { it.lines.size }
        if (ids == null) note = explain(now, inPlay)
    }

    private fun explain(now: Long, inPlay: Int): String {
        val flagged = liveIds(now)
        if (!stream.live) return "SX Bet's socket is not live (${stream.health()})"
        if (flagged.isEmpty()) return "SX Bet flags no game as in play right now (${stream.scores.size} scores and ${stream.fixtureStatus.size} fixture statuses heard)"
        val known = flagged.count { events.containsKey(it) }
        return "SX Bet flags ${flagged.size} games in play; $known are in Vigilant's leagues with their markets read; $inPlay have a trusted exchange price right now"
    }

    private fun send(frame: JsonObject) {
        if (frame.isEmpty()) return
        val now = clock()
        framesSent++
        lastFrameAtMs = now
        sink(frame.toString(), now)
    }

    private fun idOf(gameKey: String): Long = (gameKey.hashCode().toLong() and 0x7fffffffL) + ID_BASE

    private fun frame(eventId: Long, g: SxLiveBuild.Game?, markets: List<JsonObject>): JsonObject {
        if (g == null && markets.isEmpty()) return JsonObject(emptyMap())
        return buildJsonObject {
            put("type", "live")
            put("sport_id", g?.sportId ?: 0)
            put("topic", "sx/ld")
            put("rec", buildJsonObject {
                put("id", eventId)
                put("isLive", true)
                put("units", if (g?.tennis == true) "Sets" else "Regular")
                if (g != null) {
                    put("startTime", java.time.Instant.ofEpochMilli(g.startMs).toString())
                    put("league", buildJsonObject { put("name", g.league) })
                    put("participants", buildJsonArray {
                        add(buildJsonObject { put("name", g.home); put("alignment", "home") })
                        add(buildJsonObject { put("name", g.away); put("alignment", "away") })
                    })
                }
                put("markets", JsonArray(markets))
            })
        }
    }

    private fun delFrame(eventId: Long): JsonObject = buildJsonObject {
        put("type", "live"); put("op", "del"); put("topic", "sx/ld")
        put("rec", buildJsonObject { put("id", eventId) })
    }

    private fun closed(key: String, v: Long): JsonObject = buildJsonObject {
        put("key", key); put("status", "closed"); put("type", typeOfKey(key)); put("period", 0); put("version", v)
    }

    private fun typeOfKey(key: String): String = when {
        key.endsWith(";m") -> "moneyline"
        ";s;" in key -> "spread"
        else -> "total"
    }

    private fun market(line: SxLiveBuild.Line, v: Long): JsonObject = buildJsonObject {
        put("key", line.key)
        put("status", "open")
        put("type", typeOfKey(line.key))
        put("period", 0)
        put("version", v)
        put("isAlternate", false)
        put("limits", buildJsonArray { add(buildJsonObject { put("amount", LIMIT) }) })
        put("prices", buildJsonArray {
            for ((side, p) in line.fair) {
                add(buildJsonObject {
                    put("designation", side.name.lowercase())
                    put("price", BoltLiveConsensus.american(p))
                    val pts = when (side) {
                        PinnSide.HOME -> line.points
                        PinnSide.AWAY -> line.points?.let { -it }
                        else -> line.points
                    }
                    if (pts != null) put("points", pts) else put("points", JsonNull)
                })
            }
        })
    }

    companion object {
        const val DEBOUNCE_MS = 100L
        const val TICK_MS = 1_000L
        const val KEEP_OPEN_MS = 30_000L
        const val DISCOVER_MS = 10_000L
        const val REFRESH_MS = 5 * 60_000L
        const val FRESH_SCORE_MS = 3 * 60_000L
        const val ALIVE_MS = 4_000L
        const val FORGET_MS = 60_000L
        const val MIN_STEP = 0.0005
        /** SX's depth is real money but not Pinnacle's limit: a generous number so the engine's thin-market rule is not about this feed. */
        const val LIMIT = 100_000.0
        const val ID_BASE = 6_000_000_000L
    }
}
