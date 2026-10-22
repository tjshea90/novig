package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/** One live game's state as Kalshi's live data reports it (one row a game a read), for the journal the score model is built from (AF6). */
@Serializable
data class KalshiLiveRow(
    val atMs: Long,
    val milestoneId: String,
    val type: String,
    val title: String,
    val competition: String? = null,
    val startMs: Long? = null,
    /** `details.status` of the live data (the milestone's own lags: Kalshi changelog 2026-10-22). */
    val status: String? = null,
    /** True when Kalshi marks the board as significantly stale (`details.live_data_delayed`); absent does not prove it is current. */
    val delayed: Boolean = false,
    /** The whole `details` object as Kalshi sent it (sport-specific score fields), so the model is built on real shapes. */
    val details: String,
)

/**
 * Kalshi's public live data (AF6, docs.kalshi.com live-data, changelog 2026-10-22: "For game status, prefer live data"): `GET /milestones?category=Sports` names the games and their
 * milestone ids, `GET /live_data/batch?milestone_ids=` (up to 100 a call) returns each game's `details` (status and the sport's score fields). Keyless; both reads count against the
 * anonymous read budget, so the engine asks the game list every few minutes and the live data every [LIVE_EVERY_MS]. Read-only research: journaled, not yet a score source.
 */
class KalshiLiveData(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://api.elections.kalshi.com/trade-api/v2",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Game(val id: String, val type: String, val title: String, val competition: String?, val startMs: Long?, val endMs: Long?)

    @Volatile private var games: List<Game> = emptyList()
    @Volatile private var gamesAtMs = 0L

    /** Reads and journal rows so far (the engine's log line). */
    @Volatile var reads = 0
        private set

    /** Sports milestones that started within [lookBackMs] and have not ended: the games that can be live now. Re-listed every [LIST_EVERY_MS]. */
    suspend fun liveGames(now: Long = clock()): List<Game> {
        if (now - gamesAtMs < LIST_EVERY_MS) return games.filter { started(it, now) }
        val out = ArrayList<Game>()
        var cursor: String? = null
        var pages = 0
        do {
            val url = "$baseUrl/milestones".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "500").addQueryParameter("category", "Sports")
                .addQueryParameter("minimum_start_date", Instant.ofEpochMilli(now - LOOK_BACK_MS).toString())
                .apply { cursor?.let { addQueryParameter("cursor", it) } }.build()
            val body = get(url.toString()) ?: break
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: break
            out += parseMilestones(root)
            cursor = (root["cursor"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
            pages++
        } while (cursor != null && pages < MAX_PAGES)
        games = out
        gamesAtMs = now
        return out.filter { started(it, now) }
    }

    private fun started(g: Game, now: Long) = (g.startMs ?: Long.MAX_VALUE) <= now && (g.endMs == null || g.endMs > now)

    /** The live data of [ids], [BATCH] at a time; a call that fails is skipped (the next read asks again). */
    suspend fun read(ids: List<Game>, now: Long = clock()): List<KalshiLiveRow> {
        val byId = ids.associateBy { it.id }
        val out = ArrayList<KalshiLiveRow>()
        for (part in ids.chunked(BATCH)) {
            val url = "$baseUrl/live_data/batch".toHttpUrl().newBuilder().apply { part.forEach { addQueryParameter("milestone_ids", it.id) } }.build()
            val body = get(url.toString()) ?: continue
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: continue
            out += parseLive(root, byId, now)
        }
        reads++
        return out
    }

    private suspend fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).get().build()).await().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    companion object {
        const val BATCH = 100
        const val MAX_PAGES = 6
        const val LIST_EVERY_MS = 5 * 60_000L
        const val LIVE_EVERY_MS = 30_000L
        /** Games are listed from this long ago (a game started earlier than this is over for every sport Vigilant prices). */
        const val LOOK_BACK_MS = 6 * 3_600_000L

        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
        private fun time(s: String?): Long? = s?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        fun parseMilestones(root: JsonObject): List<Game> = (root["milestones"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if (o.s("category")?.equals("Sports", ignoreCase = true) == false) return@mapNotNull null
            Game(o.s("id") ?: return@mapNotNull null, o.s("type").orEmpty(), o.s("title").orEmpty(), (o["details"] as? JsonObject)?.s("competition") ?: o.s("competition"),
                time(o.s("start_date")), time(o.s("end_date")))
        }

        fun parseLive(root: JsonObject, games: Map<String, Game>, now: Long): List<KalshiLiveRow> = (root["live_datas"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.s("milestone_id") ?: return@mapNotNull null
            val d = o["details"] as? JsonObject ?: return@mapNotNull null
            val g = games[id]
            KalshiLiveRow(
                atMs = now, milestoneId = id, type = o.s("type") ?: g?.type.orEmpty(), title = g?.title.orEmpty(), competition = g?.competition, startMs = g?.startMs,
                status = d.s("status"), delayed = d.s("live_data_delayed") == "true", details = d.toString(),
            )
        }
    }
}
