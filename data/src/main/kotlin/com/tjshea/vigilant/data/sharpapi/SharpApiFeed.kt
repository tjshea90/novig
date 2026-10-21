package com.tjshea.vigilant.data.sharpapi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

/** One SharpAPI odds row, the fields Vigilant uses (SHARPAPI.md §2). [atMs] is SharpAPI's delivery stamp (`timestamp`), not when the price moved. */
data class SaRow(
    val id: String,
    val book: String,
    val eventId: String,
    val league: String,
    val home: String,
    val away: String,
    val startMs: Long?,
    val marketType: String,
    val segment: String?,
    val selection: String,
    val selectionType: String,
    val teamSide: String?,
    val player: String?,
    val line: Double?,
    val decimal: Double,
    val atMs: Long,
    val live: Boolean,
    val active: Boolean,
    val stalePregame: Boolean,
    val maxBet: Double?,
) {
    companion object {
        private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
        private fun JsonObject.d(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
        private fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

        fun time(s: String?): Long? = s?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

        /** A row of `/odds` or `/odds/delta`; null when it lacks what a price needs (an id, a book, an event, a market, a decimal price above 1). */
        fun parse(o: JsonObject): SaRow? {
            val id = o.s("id") ?: return null
            val book = o.s("sportsbook") ?: return null
            val event = o.s("event_id") ?: return null
            val market = o.s("market_type") ?: return null
            val dec = o.d("odds_decimal")?.takeIf { it > 1.0 } ?: return null
            return SaRow(
                id = id, book = book, eventId = event, league = o.s("league").orEmpty(), home = o.s("home_team").orEmpty(), away = o.s("away_team").orEmpty(),
                startMs = time(o.s("event_start_time")), marketType = market, segment = o.s("market_segment"), selection = o.s("selection").orEmpty(),
                selectionType = o.s("selection_type").orEmpty().lowercase(), teamSide = o.s("team_side")?.lowercase(), player = o.s("player_name"), line = o.d("line"),
                decimal = dec, atMs = time(o.s("timestamp")) ?: 0L, live = o.b("is_live") ?: false, active = o.b("is_active") ?: true,
                stalePregame = o.b("is_stale_pregame_price") ?: false, maxBet = o.d("max_bet"),
            )
        }
    }
}

/**
 * The engine's copy of SharpAPI's odds store (Tj, 2026-10-21, SA: "use full advantage of the features and speed of the sharp plan. I have no websocket or sse addon"). Without the stream add-on, the fastest
 * thing the plan allows is to read each league once ([load]: `/odds` by cursor pages) and then ask only for what changed ([deltaStep]: `/odds/delta`, upserts and explicit removals, SHARPAPI.md §2): a scan
 * then reads the store from memory, seconds old, the way it reads BoltOdds' socket board.
 *
 * Each league keeps its own `since` watermark, so one busy league never makes another's window too large to drain; a league whose window cannot be drained (offset paging ends while more is left) is read
 * again whole. Leagues with a game within [HOT_HOURS] are polled every [HOT_EVERY_MS], the rest every [COLD_EVERY_MS]. A league nobody asked for in [IDLE_MS] stops being polled and is dropped.
 */
class SharpApiFeed(
    private val client: SharpApiClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private class LeagueState(@Volatile var since: String? = null, @Volatile var loadedAtMs: Long = 0L, @Volatile var polledAtMs: Long = 0L, @Volatile var askedAtMs: Long = 0L, @Volatile var soonestMs: Long = Long.MAX_VALUE)

    private val rows = ConcurrentHashMap<String, SaRow>()
    private val byLeague = ConcurrentHashMap<String, MutableSet<String>>()
    private val leagues = ConcurrentHashMap<String, LeagueState>()
    private val loadMutex = ConcurrentHashMap<String, Mutex>()
    private var poller: Job? = null

    @Volatile var deltaCalls = 0L
        private set
    @Volatile var reloads = 0L
        private set
    @Volatile var lastDeltaAtMs = 0L
        private set
    @Volatile var lastNote: String? = null
        private set

    /** Every row held for [league] (SharpAPI's slug). */
    fun rows(league: String): List<SaRow> = byLeague[league]?.mapNotNull { rows[it] }.orEmpty()

    fun size(): Int = rows.size

    /** When [league] was last read whole or brought up to date by a delta (0 = never). */
    fun freshAt(league: String): Long = leagues[league]?.let { maxOf(it.loadedAtMs, it.polledAtMs) } ?: 0L

    /** The leagues held, with their row counts and how old each is (Diagnostics, the engine's log). */
    fun describe(now: Long = clock()): String = leagues.entries.sortedBy { it.key }.joinToString(", ") { (k, st) -> "$k ${byLeague[k]?.size ?: 0} rows ${(now - maxOf(st.loadedAtMs, st.polledAtMs)) / 1000}s" }.ifBlank { "nothing held yet" }

    /**
     * Makes sure [league] is held and current: read whole the first time (and again when its delta broke or the store went [STALE_MS] without one), else nothing. Returns false when it could not be read.
     * While the poller runs a held league is kept current by it; without the poller (a phone, a test) a league older than [reuseMs] is read whole again.
     */
    suspend fun ensure(league: String, reuseMs: Long = REUSE_MS): Boolean {
        val now = clock()
        val st = leagues.getOrPut(league) { LeagueState() }
        st.askedAtMs = now
        val fresh = freshAt(league)
        val polled = poller?.isActive == true
        if (st.since != null && fresh > 0L && (if (polled) now - fresh < STALE_MS else now - fresh < reuseMs)) return true
        return load(league)
    }

    /** Reads [league] whole (cursor pages of 200) and replaces what was held for it. */
    suspend fun load(league: String): Boolean = loadMutex.getOrPut(league) { Mutex() }.withLock {
        val st = leagues.getOrPut(league) { LeagueState() }
        try {
            val seen = HashSet<String>()
            var cursor: String? = null
            var firstUpdated: String? = null
            var soonest = Long.MAX_VALUE
            var pages = 0
            do {
                val params = mutableListOf("league" to league, "market" to MARKETS, "limit" to "200")
                cursor?.let { params += "cursor" to it }
                val page = client.get("/odds", params)
                if (firstUpdated == null) firstUpdated = (page["updated_at"] as? JsonPrimitive)?.content
                for (e in page.arr("data")) {
                    val r = (e as? JsonObject)?.let(SaRow::parse) ?: continue
                    put(league, r)
                    seen += r.id
                    r.startMs?.let { if (it > clock() - 4 * 3_600_000L) soonest = minOf(soonest, it) }
                }
                val p = page["pagination"]?.jsonObject
                cursor = if ((p?.get("has_more") as? JsonPrimitive)?.booleanOrNull == true) (p["next_cursor"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content else null
                pages++
            } while (cursor != null && pages < MAX_PAGES)
            // What the read did not carry is gone from the board.
            byLeague[league]?.toList()?.forEach { id -> if (id !in seen) remove(league, id) }
            st.since = firstUpdated
            st.loadedAtMs = clock()
            st.soonestMs = soonest
            reloads++
            lastNote = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: SharpApiException) {
            lastNote = "$league: ${e.message}"
            // A league SharpAPI does not know (invalid_filter) is remembered as empty, so it is not asked again for a while.
            if (e.code == "invalid_filter") { st.loadedAtMs = clock(); st.since = null }
            false
        }
    }

    /** One delta window for [league]: every changed row upserted, every removed one deleted; the watermark moves on from the terminal page only. False = the window could not be drained (read the league again). */
    suspend fun deltaStep(league: String): Boolean {
        val st = leagues[league] ?: return false
        val since = st.since ?: return false
        var offset = 0
        while (true) {
            val page = client.get("/odds/delta", listOf("since" to since, "league" to league, "market" to MARKETS, "limit" to "500", "offset" to offset.toString()))
            deltaCalls++
            for (e in page.arr("data")) (e as? JsonObject)?.let(SaRow::parse)?.let { put(league, it) }
            for (e in page.arr("removed")) ((e as? JsonObject)?.get("id") as? JsonPrimitive)?.content?.let { remove(league, it) }
            val p = page["pagination"]?.jsonObject
            val hasMore = (p?.get("has_more") as? JsonPrimitive)?.booleanOrNull == true
            val overflow = (page["overflow"] as? JsonPrimitive)?.booleanOrNull == true
            val next = (p?.get("next_offset") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toIntOrNull()
            if (!hasMore) {
                if (overflow) return false
                st.since = (page["meta"]?.jsonObject?.get("server_time") as? JsonPrimitive)?.content ?: (page["updated_at"] as? JsonPrimitive)?.content ?: since
                st.polledAtMs = clock()
                lastDeltaAtMs = st.polledAtMs
                return true
            }
            if (next == null) return false
            offset = next
        }
    }

    /**
     * Keeps every held league current until [scope] ends: each league's delta when its turn comes (a league with a game within [HOT_HOURS] every [HOT_EVERY_MS], else every [COLD_EVERY_MS]), a league whose
     * delta broke read whole again, and a league nobody asked for in [IDLE_MS] dropped.
     */
    @Synchronized
    fun startPolling(scope: CoroutineScope) {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                val now = clock()
                for ((league, st) in leagues.entries.toList()) {
                    if (now - st.askedAtMs > IDLE_MS) { drop(league); continue }
                    if (st.since == null) continue
                    val hot = st.soonestMs - now < HOT_HOURS * 3_600_000L
                    if (now - maxOf(st.polledAtMs, st.loadedAtMs) < (if (hot) HOT_EVERY_MS else COLD_EVERY_MS)) continue
                    val ok = try { deltaStep(league) } catch (e: CancellationException) { throw e } catch (e: SharpApiException) { lastNote = "$league delta: ${e.message}"; true }
                    if (!ok) { log("SharpAPI: $league's changes were too many to page; reading it again whole"); load(league) }
                }
                delay(TICK_MS)
            }
        }
    }

    fun stopPolling() { poller?.cancel(); poller = null }

    val polling: Boolean get() = poller?.isActive == true

    private fun put(league: String, r: SaRow) {
        val old = rows.put(r.id, r)
        if (old != null && old.league != r.league) byLeague[old.league]?.remove(r.id)
        byLeague.getOrPut(r.league.ifBlank { league }) { ConcurrentHashMap.newKeySet() } += r.id
    }

    private fun remove(league: String, id: String) {
        val r = rows.remove(id)
        byLeague[r?.league?.ifBlank { league } ?: league]?.remove(id)
    }

    private fun drop(league: String) {
        byLeague.remove(league)?.forEach { rows.remove(it) }
        leagues.remove(league)
    }

    private fun JsonObject.arr(k: String): JsonArray = (this[k] as? JsonArray) ?: (this[k]?.let { runCatching { it.jsonArray }.getOrNull() }) ?: JsonArray(emptyList())

    companion object {
        /** The markets Vigilant prices: moneylines, every spread and total (full game and periods, team totals inside `total`), and every `player_*` prop. */
        const val MARKETS = "moneyline,spread,total,props"
        const val TICK_MS = 500L
        const val HOT_EVERY_MS = 2_000L
        const val COLD_EVERY_MS = 15_000L
        const val HOT_HOURS = 12L
        /** Without a poller, how long a whole read of a league is used before it is read again. */
        const val REUSE_MS = 60_000L
        /** With a poller, a league with no successful delta for this long is read whole again. */
        const val STALE_MS = 120_000L
        const val IDLE_MS = 30 * 60_000L
        /** A league's whole read stops after this many pages (200 rows each). */
        const val MAX_PAGES = 1_500
    }
}

