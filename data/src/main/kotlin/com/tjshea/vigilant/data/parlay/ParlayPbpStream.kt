package com.tjshea.vigilant.data.parlay

import com.tjshea.vigilant.data.novig.lab.AltScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One raw SSE event kept for research (`pbplive-<day>.jsonl`): the sport, the event name, the data exactly as sent. */
@kotlinx.serialization.Serializable
data class PbpRow(val atMs: Long, val sport: String, val event: String, val data: String)

/**
 * One ParlayAPI live play-by-play SSE connection per sport that the live lane asks about (`GET /v1/sports/{sport}/live/sse`, key in the X-API-Key header, Starter and up: Starter 3 connections at once,
 * Pro 25; 5 credits per connection, a reconnect is billed as a new one, so a dropped connection waits [BACKOFF_MS] first and an idle one is closed). Demand-driven: [scoreFor] is what the lane calls, and
 * it opens the sport's stream the first time and keeps it while the lane keeps asking ([IDLE_MS]). The board it fills is [ParlayPbp]'s; nothing here prices or orders anything.
 */
class ParlayPbpStream(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val key: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val baseUrl: String = "https://parlay-api.com",
    private val backoffMs: Long = BACKOFF_MS,
    private val onNote: (String) -> Unit = {},
) {
    val board = ParlayPbpBoard()

    /** Sees every complete event as received (sport, event name, data text, arrival ms): the engine's recorder for sports whose frames have not been verified (tennis) keeps them raw. */
    @Volatile var tap: ((sport: String, event: String, data: String, atMs: Long) -> Unit)? = null
    private val wanted = ConcurrentHashMap<String, Long>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val stoppedUntil = ConcurrentHashMap<String, Long>()
    @Volatile var frames = 0L
        private set
    @Volatile var connects = 0L
        private set

    /** Keeps [sport]'s stream open (for recording) though no game is asked about; call it every minute or so. */
    fun watch(sport: String) { wanted[sport] = clock(); ensure(sport) }

    /** The score of [home] v [away] in [league] from the stream, opening the sport's stream when it is not open; null when nothing live matches. */
    fun scoreFor(league: String, home: String, away: String): AltScore? {
        val sport = ParlayPbpSports.KEYS[league] ?: return null
        wanted[sport] = clock()
        ensure(sport)
        return ParlayPbp.scoreFor(board.all(sport), home, away, board.heard[sport] ?: 0L)
    }

    private fun ensure(sport: String) {
        if (jobs[sport]?.isActive == true) return
        if (clock() < (stoppedUntil[sport] ?: 0L) || key() == null) return
        synchronized(this) {
            if (jobs[sport]?.isActive == true) return
            jobs[sport] = scope.launch(Dispatchers.IO) { run(sport) }
        }
    }

    private suspend fun run(sport: String) {
        while (scope.isActive && clock() - (wanted[sport] ?: 0L) < IDLE_MS) {
            val k = key() ?: return
            try {
                connects++
                connect(sport, k)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: HttpRefused) {
                onNote("ParlayAPI live stream $sport refused: HTTP ${e.code}")
                stoppedUntil[sport] = clock() + if (e.code == 429) 60_000L else REFUSED_MS
                return
            } catch (e: IOException) {
                onNote("ParlayAPI live stream $sport dropped: ${e.message}")
            }
            delay(backoffMs)
        }
    }

    class HttpRefused(val code: Int) : IOException("HTTP $code")

    private suspend fun connect(sport: String, apiKey: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/v1/sports/$sport/live/sse").header("X-API-Key", apiKey).header("Accept", "text/event-stream").build()
        val call = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build().newCall(req)
        val cancel = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw HttpRefused(resp.code)
                board.heard[sport] = clock()
                val src = (resp.body ?: throw IOException("empty body")).source()
                var event = ""
                val data = StringBuilder()
                while (true) {
                    ensureActive()
                    val line = src.readUtf8Line() ?: break
                    board.heard[sport] = clock()
                    when {
                        line.isEmpty() -> { if (data.isNotEmpty()) accept(sport, event, data.toString()); event = ""; data.setLength(0) }
                        line.startsWith(":") -> {}
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.removePrefix("data:").trimStart()) }
                    }
                    if (clock() - (wanted[sport] ?: 0L) >= IDLE_MS) return@use
                }
            }
        } finally {
            cancel?.dispose()
        }
    }

    /** One complete SSE event: `initial_state` fills the board, `pbp_event` updates one match. */
    internal fun accept(sport: String, event: String, data: String) {
        val now = clock()
        frames++
        runCatching { tap?.invoke(sport, event, data, now) }
        when (event) {
            "initial_state" -> ParlayPbp.parseInitial(data, now).forEach { board.put(it.copy(sport = it.sport.ifEmpty { sport })) }
            "pbp_event" -> ParlayPbp.parseEvent(data, now) { board.get(it) }?.let { board.put(it.copy(sport = it.sport.ifEmpty { sport })) }
        }
        board.prune(now, PRUNE_MS)
    }

    fun close() { jobs.values.forEach { it.cancel() }; jobs.clear() }

    companion object {
        /** Wait before reconnecting (a reconnect costs 5 credits). */
        const val BACKOFF_MS = 20_000L
        /** A stream nobody has asked about for this long is closed. */
        const val IDLE_MS = 15 * 60_000L
        /** After a refusal (key out of credits, no plan for streams) the sport is left alone this long. */
        const val REFUSED_MS = 10 * 60_000L
        /** Matches nobody has spoken about for this long leave the board. */
        const val PRUNE_MS = 6 * 3_600_000L
    }
}
