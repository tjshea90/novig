package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.pinnodds.PinnFeedSource
import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.pinnodds.PinnSocketState
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs

/**
 * The live engine's third source of fair prices (Tj, 2026-10-12): the soft-book consensus of BoltOdds' live games ([BoltLiveConsensus]), spoken in the same frames the Pinnodds socket sends, so the whole live lane
 * (the matcher, the 5-second rule, the taker fee, the live trader and live bids, every guard) runs on it unchanged. It is PUSH: a board change for a game ([BoltBoard.addGameListener]) wakes the worker, which waits
 * [DEBOUNCE_MS] (a burst of books repricing together is one update) and re-reads just that game; nothing polls for a price and nothing scans. A once-a-second look only keeps lines honest: a line whose companies
 * stopped pushing, or disagree, is CLOSED in the next frame, and a line that still holds is re-stated so the engine's "heard from this game" time is the time the quorum was last seen.
 *
 * It has no scores or clock (BoltOdds' /livescores socket is not read yet), so the engine must not wait for a score: the app forces the "Any Pinnacle move"/"Any edge" trigger when this feed is the source.
 */
class BoltLiveFeed(
    private val board: BoltBoard,
    private val socketLive: () -> Boolean,
    /** Opens and subscribes the odds socket (idempotent); called when the feed starts and every [KEEP_OPEN_MS]. */
    private val ensureOpen: suspend () -> Unit,
    private val sink: (String, Long) -> Unit,
    private val scope: CoroutineScope,
    private val rules: () -> BoltLiveConsensus.Rules,
    private val clock: () -> Long = System::currentTimeMillis,
) : PinnFeedSource {

    private val _state = MutableStateFlow<PinnSocketState>(PinnSocketState.Off)
    override val state: StateFlow<PinnSocketState> = _state.asStateFlow()

    /** Why the live lane has no game from this feed, in words (updated each look): what BoltOdds flags in play, and how many of those have a quorum. */
    @Volatile override var note: String? = null
        private set

    @Volatile override var lastFrameAtMs: Long = 0L
        private set

    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val dirty = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val listener: (String) -> Unit = { g -> dirty += g; signal.trySend(Unit) }
    private var worker: Job? = null
    private var ticker: Job? = null

    /** What was last said about a line of a game, so an unchanged line is not re-sent as news. */
    private class Said(val fair: Double, val points: Double?, val version: Long)

    private class GameState(val eventId: Long) {
        val lines = HashMap<String, Said>()
        var lastLineAtMs = 0L
    }

    private val games = HashMap<String, GameState>()
    private var version = 1L

    /** Counters for Diagnostics. */
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
        board.addGameListener(listener)
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
            while (isActive) {
                delay(TICK_MS)
                sinceOpen += TICK_MS
                if (sinceOpen >= KEEP_OPEN_MS) { sinceOpen = 0L; runCatching { ensureOpen() } }
                _state.value = if (socketLive()) (_state.value as? PinnSocketState.Live ?: PinnSocketState.Live(clock())) else PinnSocketState.Connecting
                // A lively board is "heard from" now: the engine reads this as the feed being alive.
                if (socketLive() && clock() - board.lastMessageMs < ALIVE_MS) lastFrameAtMs = clock()
                update(null)
            }
        }
    }

    @Synchronized
    override fun stop() {
        board.removeGameListener(listener)
        worker?.cancel(); worker = null
        ticker?.cancel(); ticker = null
        dirty.clear()
        games.clear()
        _state.value = PinnSocketState.Off
    }

    /**
     * Re-reads [ids] (null: every game that was said or is live now) and sends what changed: a new or moved line, a closed line, and for a line that holds a restatement of its game (so the engine hears from it). One
     * coroutine at a time ([update] runs on the worker or the ticker, serialised here).
     */
    @Synchronized
    private fun update(ids: Set<String>?) {
        val now = clock()
        var only = ids
        var entries = board.liveEntries(only)
        // A game that has left the board (removed, cleared, no longer live) cannot say which league it was: look at everything.
        if (only != null && only.any { id -> entries.none { it.game == id } }) { only = null; entries = board.liveEntries(null) }
        val built = BoltLiveConsensus.build(entries, now, rules())
        if (only == null) note = explain(entries, built, now)
        val byGame = built.associateBy { it.id }
        // Games to look at: the ones just built, and (a game that lost its quorum must close its lines) the ones just read, or every game already said.
        val look = LinkedHashSet<String>().apply {
            addAll(byGame.keys)
            if (only == null) addAll(games.keys)
            else entries.forEach { e -> (BoltBooks.leagueOfSport(e.sport) ?: BoltBooks.tennisLeagueOf(e.sport))?.let { add(BoltConvert.eventId(it, e.game)) } }
        }
        for (key in look) {
            val g = byGame[key]
            val st = games[key]
            if (g == null) {
                if (st != null) {
                    // No quorum on any line now: close what was open; forget the game when it has been silent a minute.
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
            // Lines of this game that lost their quorum, or whose number moved away (the old number closes).
            for (k in state.lines.keys.toList()) if (k !in open) { markets += closed(k, ++version); state.lines.remove(k); linesClosed++ }
            state.lastLineAtMs = now
            // Always speak for the game (the engine's "heard from this game"), with prices only for what changed.
            send(frame(state.eventId, g, markets))
        }
        liveGames = games.count { it.value.lines.isNotEmpty() }
        linesLive = games.values.sumOf { it.lines.size }
    }

    private fun explain(entries: List<BoltBookGame>, built: List<BoltLiveConsensus.Game>, now: Long): String {
        val games = entries.map { it.game }.distinct()
        val r = rules()
        if (games.isEmpty()) return "BoltOdds flags no game as in play right now (it carries only the leagues picked in Settings and tennis; ${board.summary().games} games are on its boards)"
        fun companies(l: List<BoltBookGame>, within: Long) = l.filter { now - it.updatedAtMs <= within }.map { com.tjshea.vigilant.engine.BookCompany.of(it.sportsbook) }.distinct().size
        val perGame = entries.groupBy { it.game }.entries.sortedByDescending { companies(it.value, r.maxAgeMs * 6) }
        val best = perGame.firstOrNull()?.let { companies(it.value, r.maxAgeMs) } ?: 0
        // The three busiest flagged games: sport, quotes, and companies heard within the window / 6x the window / 5 minutes (shows whether the window or the flag is the problem).
        val detail = perGame.take(3).joinToString("; ") { (g, l) ->
            "${l.first().sport} ${g.substringBefore(',').take(40)}: ${l.size} quotes, companies ${companies(l, r.maxAgeMs)}/${companies(l, r.maxAgeMs * 6)}/${companies(l, 300_000)} within ${r.maxAgeMs / 1000}s/${r.maxAgeMs * 6 / 1000}s/5min"
        }
        return "BoltOdds flags ${games.size} games in play; ${built.size} have a quorum of ${r.minCompanies} companies pushed within ${r.maxAgeMs / 1000} s and agreeing" + if (built.isEmpty()) " (the best game has $best fresh companies) · $detail" else ""
    }

    private fun send(frame: JsonObject) {
        if (frame.isEmpty()) return
        val now = clock()
        framesSent++
        lastFrameAtMs = now
        sink(frame.toString(), now)
    }

    private fun idOf(gameId: String): Long = (gameId.hashCode().toLong() and 0x7fffffffL) + ID_BASE

    private fun frame(eventId: Long, g: BoltLiveConsensus.Game?, markets: List<JsonObject>): JsonObject {
        if (g == null && markets.isEmpty()) return JsonObject(emptyMap())
        return buildJsonObject {
            put("type", "live")
            put("sport_id", g?.sportId ?: 0)
            put("topic", "bolt/ld")
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
        put("type", "live"); put("op", "del"); put("topic", "bolt/ld")
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

    private fun market(line: BoltLiveConsensus.Line, v: Long): JsonObject = buildJsonObject {
        put("key", line.key)
        put("status", "open")
        put("type", typeOfKey(line.key))
        put("period", 0)
        put("version", v)
        put("isAlternate", false)
        // A soft book's limit is not a Pinnacle limit; the engine's "thin market" rule is not about this feed.
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
        const val ALIVE_MS = 4_000L
        const val FORGET_MS = 60_000L

        /** A consensus fair that moved less than this is not news (a rounding of the same prices). */
        const val MIN_STEP = 0.0005
        const val LIMIT = 100_000.0
        const val ID_BASE = 5_000_000_000L
    }
}
