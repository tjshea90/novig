package com.tjshea.vigilant.data.novig.signing

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * What Settings › Novig API › Test key measures beyond "the signature is accepted" (Tj, 2026-09-29: "Right now the novig scan is slow,
 * even though I tested my key and it says it works"). The signed REST route and the live feed (the websocket) were only ever checked
 * against mocks; this runs each once against the real API, from the phone, and says in numbers what the key gets:
 *
 *  1. `GET /v3/limits`: this key's own buckets (free, 0 tokens).
 *  2. The signed catalog: a page of open markets, timed (a `read` token each).
 *  3. Books, [samples] through the key and [samples] over the public route, timed one after another (what a scan's REST reads cost).
 *  4. The websocket: connect, subscribe to those markets on `book`, and time when the first books arrive and how many by the end
 *     (a fresh connection's first subscribe can't go until the 512-token `stream` bucket is full again, ~8 s: NOVIG_API.md §6).
 *
 * Read-only: no order is ever placed, no money moves. Every step fails on its own into a line saying why, so one broken part shows.
 */
class NovigLiveCheck(
    private val http: OkHttpClient,
    private val json: Json,
    private val signer: NovigSignedClient,
    /** A fresh live feed for [signer] to try (closed again when the check is done). */
    private val feed: (NovigSignedClient) -> PushedBooks,
    private val publicBaseUrl: String = "https://api.novig.com",
    private val clock: () -> Long = System::currentTimeMillis,
    private val waitMs: Long = WAIT_MS,
    private val samples: Int = SAMPLES,
    private val watchCount: Int = WATCH_COUNT,
) {
    /** The report's lines, in order; [onStep] hears each step's name as it starts. */
    suspend fun run(onStep: (String) -> Unit = {}): List<String> {
        val lines = ArrayList<String>()

        onStep("Reading the key's limits…")
        lines += step("Limits") {
            val root = json.parseToJsonElement(get(signer.signedRequest("GET", "/v3/limits"))).jsonObject
            fun bucket(name: String): String {
                val b = root[name]?.jsonObject ?: return "$name ?"
                return "$name ${b["capacity"]?.jsonPrimitive?.content ?: "?"} (${rate(b["refillPerSec"]?.jsonPrimitive?.content)} a second)"
            }
            "Limits: ${bucket("read")}, ${bucket("stream")}, up to ${root["maxWatchedMarkets"]?.jsonPrimitive?.content ?: "?"} markets watched."
        }

        onStep("Reading Novig's catalog through the key…")
        val ids = ArrayList<String>()
        val catalogMs = clock()
        lines += step("Signed catalog") {
            val body = get(signer.signedRequest("GET", "/v3/catalog/markets", "eventStatus=OPEN_PREGAME&limit=$watchCount&marketType=MONEY"))
            json.parseToJsonElement(body).jsonObject["items"]?.jsonArray?.forEach { m ->
                (m as? JsonObject)?.get("marketId")?.jsonPrimitive?.content?.let { ids += it }
            }
            if (ids.isEmpty()) "Signed catalog: answered, but no open game is listed right now, so the next two checks are skipped."
            else "Signed catalog: ${ids.size} open markets in ${seconds(clock() - catalogMs)}."
        }
        if (ids.isEmpty()) return lines

        onStep("Timing book reads…")
        val sample = ids.take(samples)
        lines += step("Books") {
            val key = timed { sample.forEach { get(signer.signedRequest("GET", "/v3/catalog/markets/$it/book")) } }
            val public = timed { sample.forEach { get(Request.Builder().url("$publicBaseUrl/v3/public/catalog/markets/$it/book").get().build()) } }
            "Books: ${sample.size} through the key ${key / sample.size} ms each, ${sample.size} public ${public / sample.size} ms each " +
                "(a scan reads them ${if (sample.size > 1) "several at a time" else "one by one"}, so its pace is set by the limit above, not by these)."
        }

        onStep("Trying the live feed (up to ${waitMs / 1000} s)…")
        lines += liveFeed(ids)
        return lines
    }

    private suspend fun liveFeed(ids: List<String>): String {
        val pushed = try {
            feed(signer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Live feed: couldn't start (${e.message ?: e.javaClass.simpleName})."
        }
        val t0 = clock()
        var first: Long? = null
        var got = 0
        var problem: String? = null
        try {
            pushed.watch(ids)
            while (clock() - t0 < waitMs) {
                got = pushed.live(ids).size
                if (got > 0 && first == null) first = clock() - t0
                if (got >= ids.size) break
                problem = pushed.problemSince(t0)
                if (problem != null) break
                delay(POLL_MS)
            }
            if (problem == null) problem = pushed.problemSince(t0)
        } finally {
            pushed.close()
        }
        val took = clock() - t0
        return when {
            got == 0 -> "Live feed: no book arrived in ${seconds(took)}. " +
                (problem?.let { "Novig's side of it: $it" } ?: "No error was reported: scans will read one price at a time through the key instead, at its `read` limit.")
            else -> "Live feed: first books after ${seconds(first ?: took)}, $got of ${ids.size} after ${seconds(took)}." +
                (problem?.let { " Then it failed: $it" } ?: if (got < ids.size) " The rest hadn't arrived when the check ended." else "")
        }
    }

    private suspend fun step(name: String, block: suspend () -> String): String = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: NovigApiException) {
        "$name: ${e.brief}"
    } catch (e: Exception) {
        "$name failed: ${e.message ?: e.javaClass.simpleName}"
    }

    private suspend fun get(request: Request): String = http.newCall(request).await().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val code = runCatching { json.parseToJsonElement(text).jsonObject["code"]?.jsonPrimitive?.content }.getOrNull()
            throw NovigApiException(response.code, code, text.take(120).takeIf { !it.trimStart().startsWith("<") })
        }
        text
    }

    private suspend fun timed(block: suspend () -> Unit): Long {
        val t = clock()
        block()
        return (clock() - t).coerceAtLeast(0)
    }

    private fun seconds(ms: Long) = String.format(Locale.US, "%.1f s", ms / 1000.0)

    private fun rate(raw: String?): String = raw?.toDoubleOrNull()?.let { if (it == Math.floor(it)) it.toLong().toString() else String.format(Locale.US, "%.1f", it) } ?: "?"

    companion object {
        const val WAIT_MS = 40_000L
        const val SAMPLES = 5
        const val WATCH_COUNT = 24
        private const val POLL_MS = 250L
    }
}
