package com.tjshea.vigilant.data.sharpapi

import com.tjshea.vigilant.data.boltodds.BoltLiveConsensus
import com.tjshea.vigilant.data.pinnodds.PinnFeedSource
import com.tjshea.vigilant.data.pinnodds.PinnSocketState
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

/**
 * The live engine's Pinnacle feed from SharpAPI (Tj, 2026-10-21, SA; [com.tjshea.vigilant.data.scanner.PinnFeedChoice.SHARPAPI]): SharpAPI collects Pinnacle by Pinnacle's own MQTT push (p50 0.8 s, p95 ~4 s,
 * SHARPAPI.md §2) and [SharpApiFeed] polls the changes of a league in play every [SharpApiFeed.LIVE_EVERY_MS], so this is a REAL Pinnacle price a second or two behind Pinnacle, spoken in the frames the Pinnodds socket
 * sends: the whole live lane (the matcher, the 5-second rule, the taker fee, the lag trader, live bids and every guard) runs on it unchanged. Pinnacle's raw prices are sent, as the socket sends them; the runner devigs.
 *
 * Every [TICK_MS] the live Pinnacle rows of the picked leagues are turned into lines ([SharpApiConvert], live only, full game moneyline / spread / total); a line that changed is sent, a line SharpAPI took off the board
 * (removed or suspended) is sent CLOSED, and a league whose store was not brought up to date within [STALE_MS] sends nothing (its lines close and the runner's 5 s rule takes the rest). Real orders on it stay behind
 * their own gate (`gates.sharpApiLiveReal`, default off = paper) until its paper results are read.
 */
class SharpApiLiveFeed(
    private val feed: SharpApiFeed,
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

    private class Said(val prices: String, val version: Long)
    private class GameState(val id: Long) { val lines = HashMap<String, Said>() }
    private val games = HashMap<String, GameState>()
    private var version = 1L
    private var job: Job? = null
    private var ensuredAtMs = 0L

    @Synchronized
    override fun start() {
        if (job?.isActive == true) return
        _state.value = PinnSocketState.Connecting
        job = scope.launch {
            while (isActive) {
                try { tick() } catch (e: CancellationException) { throw e } catch (e: Exception) { note = "SharpAPI live: ${e.message?.take(120) ?: e.javaClass.simpleName}" }
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

    private suspend fun tick() {
        val now = clock()
        val picked = leagues().mapNotNull { Leagues.byNovigName(it) }.mapNotNull { l -> SharpApiBooks.leagueSlug(l)?.let { l to it } }
        // Keep the picked leagues asked for (so the store holds and polls them), at most every 30 s.
        // Each league on its own coroutine: a big cold read (NFL is 74k rows) must not hold tennis, which is small and lives now.
        if (now - ensuredAtMs > ENSURE_MS) { ensuredAtMs = now; for ((_, slug) in picked) scope.launch { runCatching { feed.ensure(slug) } } }
        update(picked, now)
    }

    /** One pass over [picked]'s live Pinnacle rows; internal for the tests. */
    @Synchronized
    internal fun update(picked: List<Pair<League, String>>, now: Long) {
        val open = HashSet<String>()
        var live = 0
        var fresh = false
        val why = ArrayList<String>()
        for ((league, slug) in picked) {
            val stale = now - feed.freshAt(slug) > STALE_MS
            if (!stale) fresh = true
            val rows = feed.rows(slug)
            val hasLive = rows.any { it.live && it.book == PINNACLE }
            feed.markLive(slug, hasLive)
            if (hasLive && stale) why += "${slug}: live rows but the store is ${(now - feed.freshAt(slug)) / 1000}s old"
            if (stale || !hasLive) continue
            val ctx = SharpApiConvert.Context(league, setOf(PINNACLE), FAMILIES, includeLive = true, nowMs = now, liveOnly = true)
            val before = live
            for (e in SharpApiConvert.events(rows.filter { it.book == PINNACLE }, ctx)) {
                val key = e.id
                open += key
                live++
                val st = games.getOrPut(key) { GameState(idOf(key)) }
                val markets = ArrayList<JsonObject>()
                val here = HashSet<String>()
                for (m in e.markets) {
                    if (m.period != 0) continue
                    val k = keyOf(m) ?: continue
                    here += k
                    val prices = m.quotes.joinToString(";") { "${it.side}:${it.decimalOdds}:${it.point}" }
                    val said = st.lines[k]
                    if (said == null || said.prices != prices) {
                        val v = ++version
                        st.lines[k] = Said(prices, v)
                        markets += market(k, m, v)
                    }
                }
                for (k in st.lines.keys.toList()) if (k !in here) { markets += closed(k, ++version); st.lines.remove(k) }
                send(frame(st.id, league, e.home, e.away, e.commenceMs, markets))
            }
            if (live == before) why += "$slug: live Pinnacle rows gave no game (" + SharpApiConvert.census(rows.filter { it.book == PINNACLE && it.live }, ctx).take(300) + ")"
        }
        // A game that left: its lines close, then it is forgotten.
        for ((key, st) in games.entries.toList()) if (key !in open) {
            if (st.lines.isNotEmpty()) send(frame(st.id, null, null, null, null, st.lines.keys.map { closed(it, ++version) }))
            games.remove(key)
        }
        liveGames = live
        if (fresh) { lastFrameAtMs = now; _state.value = _state.value as? PinnSocketState.Live ?: PinnSocketState.Live(now) } else _state.value = PinnSocketState.Connecting
        note = when {
            picked.isEmpty() -> "SharpAPI live: none of the picked leagues is one SharpAPI carries"
            !fresh -> "SharpAPI live: the store is not current (${feed.lastNote ?: "waiting for SharpAPI"})"
            live == 0 -> "SharpAPI live: no game in play with Pinnacle prices in the picked leagues (rows held / live Pinnacle rows: " + picked.joinToString(", ") { (_, slug) -> "$slug ${feed.rows(slug).size}/${feed.rows(slug).count { it.live && it.book == PINNACLE }}" } + ")" + why.joinToString("") { " | $it" }
            else -> "SharpAPI live: $live games in play with Pinnacle prices (store ${feed.describe(now)})"
        }
    }

    private fun send(frame: JsonObject) {
        if (frame.isEmpty()) return
        framesSent++
        sink(frame.toString(), clock())
    }

    private fun idOf(key: String): Long = (key.hashCode().toLong() and 0x7fffffffL) + ID_BASE

    private fun keyOf(m: RefBookMarket): String? = when (m.kind) {
        LineKind.MONEYLINE -> "s;0;m"
        LineKind.SPREAD -> m.line?.let { "s;0;s;$it" }
        LineKind.TOTAL -> m.line?.let { "s;0;ou;$it" }
        else -> null
    }

    private fun typeOf(k: String) = when {
        k.endsWith(";m") -> "moneyline"
        ";s;" in k -> "spread"
        else -> "total"
    }

    private fun frame(id: Long, league: League?, home: String?, away: String?, startMs: Long?, markets: List<JsonObject>): JsonObject {
        if (league == null && markets.isEmpty()) return JsonObject(emptyMap())
        return buildJsonObject {
            put("type", "live")
            put("sport_id", league?.pinnacleSportId ?: 0)
            put("topic", "sharpapi/ld")
            put("rec", buildJsonObject {
                put("id", id)
                put("isLive", true)
                put("units", if (league?.tennis == true) "Sets" else "Regular")
                if (league != null && home != null && away != null) {
                    startMs?.let { put("startTime", java.time.Instant.ofEpochMilli(it).toString()) }
                    put("league", buildJsonObject { put("name", league.novigName) })
                    put("participants", buildJsonArray {
                        add(buildJsonObject { put("name", home); put("alignment", "home") })
                        add(buildJsonObject { put("name", away); put("alignment", "away") })
                    })
                }
                put("markets", JsonArray(markets))
            })
        }
    }

    private fun closed(key: String, v: Long): JsonObject = buildJsonObject {
        put("key", key); put("status", "closed"); put("type", typeOf(key)); put("period", 0); put("version", v)
    }

    private fun market(key: String, m: RefBookMarket, v: Long): JsonObject = buildJsonObject {
        put("key", key)
        put("status", "open")
        put("type", typeOf(key))
        put("period", 0)
        put("version", v)
        put("isAlternate", false)
        put("limits", buildJsonArray { add(buildJsonObject { put("amount", LIMIT) }) })
        put("prices", buildJsonArray {
            for (q in m.quotes) add(buildJsonObject {
                put("designation", when (q.side) { Side.HOME -> "home"; Side.AWAY -> "away"; Side.OVER -> "over"; Side.UNDER -> "under" })
                put("price", BoltLiveConsensus.american(1.0 / q.decimalOdds))
                if (q.point != null) put("points", q.point) else put("points", JsonNull)
            })
        })
    }

    companion object {
        /** The leagues the live lane reads: the picked ones, plus tennis whenever live tennis is wanted (Tj, 2026-10-22: the app's league list had no ATP/WTA, and the phone's copy of the settings kept taking them out). */
        fun leaguesFor(s: com.tjshea.vigilant.data.scanner.ScanSettings): Set<String> = if (s.liveBidQuality.tennis) s.leagues + setOf("ATP", "WTA") else s.leagues
        const val PINNACLE = "pinnacle"
        const val TICK_MS = 1_000L
        const val ENSURE_MS = 30_000L
        /** A league whose store was last brought up to date longer ago than this is not current enough to price a live line. */
        const val STALE_MS = 4_000L
        const val LIMIT = 100_000.0
        const val ID_BASE = 7_000_000_000L
        private val FAMILIES = setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD, MarketFamily.TOTAL)
    }
}
