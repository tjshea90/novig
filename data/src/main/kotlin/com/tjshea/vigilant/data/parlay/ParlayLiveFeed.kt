package com.tjshea.vigilant.data.parlay

import com.tjshea.vigilant.data.pinnodds.PinnFeedSource
import com.tjshea.vigilant.data.pinnodds.PinnFrames
import com.tjshea.vigilant.data.pinnodds.PinnSocketState
import com.tjshea.vigilant.data.scanner.Leagues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The live engine's Pinnacle feed from ParlayAPI's Business streams (PB17b, Tj 2026-10-25: recover what the paused Pinnodds socket and SharpAPI's Sharp plan gave; research/api_recovery_2026-10-25.md):
 * `/v1/sse/hot/{sport}` pushes Pinnacle (and FanDuel) "sub-3s" and `/v1/sse/odds` every book, both held by [ParlayLive]; every [TICK_MS] each game of the picked leagues under way is turned into
 * the Pinnodds socket's frames ([PinnFrames]), so the whole live lane (matcher, 5-second rule, taker fee, trader, live bids and every guard) runs on it unchanged, exactly as on SharpAPI's feed.
 *
 * A line is sent only while ParlayAPI confirmed it within [ParlayLive.LIVE_LINE_MS]; a line it stops confirming is sent CLOSED; nothing is sent while the stream is silent (the runner's 5 s rule
 * closes the rest). Real orders on it stay behind their own gate (`gates.parlayLiveReal`, default off = paper) until its paper results are read.
 */
class ParlayLiveFeed(
    private val live: ParlayLive,
    private val leagues: () -> Set<String>,
    private val sink: (String, Long) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : PinnFeedSource {
    private val _state = MutableStateFlow<PinnSocketState>(PinnSocketState.Off)
    override val state: StateFlow<PinnSocketState> = _state.asStateFlow()

    @Volatile override var note: String? = null
        private set
    @Volatile override var lastFrameAtMs: Long = 0L
        private set
    @Volatile var framesSent = 0L
        private set
    @Volatile var liveGames = 0
        private set

    private class GameState(val id: Long) { val lines = HashMap<String, String>() }
    private val games = HashMap<String, GameState>()
    private var version = 1L
    private var job: Job? = null

    @Synchronized
    override fun start() {
        if (job?.isActive == true) return
        _state.value = PinnSocketState.Connecting
        job = scope.launch {
            while (isActive) {
                try { update(clock()) } catch (e: CancellationException) { throw e } catch (e: Exception) { note = "ParlayAPI live: ${e.message?.take(120) ?: e.javaClass.simpleName}" }
                delay(TICK_MS)
            }
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel(); job = null
        games.clear()
        _state.value = PinnSocketState.Off
    }

    /** One pass; internal for the tests. */
    @Synchronized
    internal fun update(now: Long) {
        val picked = leagues().mapNotNull { Leagues.byNovigName(it) }
        val bySport = picked.associateBy { it.oddsApiSportKey }
        if (!live.live(now)) {
            for ((_, st) in games) if (st.lines.isNotEmpty()) send(PinnFrames.frame(TOPIC, st.id, null, null, null, null, st.lines.keys.map { PinnFrames.closed(it, ++version) }))
            games.clear()
            liveGames = 0
            _state.value = PinnSocketState.Connecting
            note = "ParlayAPI live: the stream is silent"
            return
        }
        val open = HashSet<String>()
        var withLines = 0
        for ((key, g) in live.gamesInPlay(bySport.keys, now)) {
            val league = bySport[g.sport] ?: continue
            val lines = live.linesOf(key, PINNACLE, now)
            val st = games[key]
            if (lines.isEmpty() && st == null) continue
            open += key
            val state = st ?: GameState(idOf(key)).also { games[key] = it }
            val markets = ArrayList<JsonObject>()
            val here = HashSet<String>()
            for (m in lines) {
                val k = PinnFrames.keyOf(m) ?: continue
                here += k
                val prices = m.quotes.joinToString(";") { "${it.side}:${it.decimalOdds}:${it.point}" }
                if (state.lines[k] != prices) { state.lines[k] = prices; markets += PinnFrames.market(k, m, ++version) }
            }
            for (k in state.lines.keys.toList()) if (k !in here) { markets += PinnFrames.closed(k, ++version); state.lines.remove(k) }
            if (here.isNotEmpty()) withLines++
            send(PinnFrames.frame(TOPIC, state.id, league, g.home, g.away, g.commenceMs, markets))
        }
        for ((key, st) in games.entries.toList()) if (key !in open) {
            if (st.lines.isNotEmpty()) send(PinnFrames.frame(TOPIC, st.id, null, null, null, null, st.lines.keys.map { PinnFrames.closed(it, ++version) }))
            games.remove(key)
        }
        liveGames = withLines
        lastFrameAtMs = now
        _state.value = _state.value as? PinnSocketState.Live ?: PinnSocketState.Live(now)
        note = if (withLines == 0) "ParlayAPI live: no game in play with Pinnacle prices from the hot stream (leagues ${bySport.keys.sorted()})"
        else "ParlayAPI live: $withLines games in play with Pinnacle prices"
    }

    private fun send(frame: JsonObject) {
        if (frame.isEmpty()) return
        framesSent++
        sink(frame.toString(), clock())
    }

    private fun idOf(key: String): Long = (key.hashCode().toLong() and 0x7fffffffL) + ID_BASE

    companion object {
        const val PINNACLE = "pinnacle"
        const val TICK_MS = 1_000L
        const val TOPIC = "parlay/ld"
        const val ID_BASE = 8_000_000_000L
    }
}
