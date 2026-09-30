package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.awaitText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * ParlayAPI's degraded-mode check (its best practices, Tj 2026-09-30: "Make sure the app follows these best practices"): `GET
 * /v1/meta/source-quality` (free, no key) says, per book it collects, whether it's keeping up. A book that's `stale` or `missing`, or in
 * `breach` and past its own stale threshold, is left out of ParlayAPI's quotes for the scan (another feed's copy of it, or the other
 * books, price instead). A book a minute behind stays: Vigilant's own rule drops any quote older than 5-10 minutes anyway (RESEARCH.md
 * §24), and "breach" there means "not as fresh as ParlayAPI aims for", not "wrong". Read at most every [KEEP_MS]; a failed read leaves
 * nothing out.
 */
class ParlaySourceQuality(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val base: String = OddsFeed.PARLAY.base,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var readAt = Long.MIN_VALUE / 2
    private var unsafe: Set<String> = emptySet()

    /** Books (The Odds API's keys, [TheOddsApiClient.canonicalBook]) whose ParlayAPI quotes shouldn't price now. */
    suspend fun unsafeBooks(): Set<String> = mutex.withLock {
        val now = clock()
        if (now - readAt < KEEP_MS) return@withLock unsafe
        readAt = now
        unsafe = try {
            val reply = http.newCall(Request.Builder().url("$base/meta/source-quality".toHttpUrl()).get().build()).awaitText()
            if (reply.isSuccessful) parse(json.parseToJsonElement(reply.body)) else unsafe
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
        unsafe
    }

    companion object {
        const val KEEP_MS = 5 * 60_000L

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        private fun JsonObject.num(k: String) = str(k)?.toDoubleOrNull()

        fun parse(root: JsonElement): Set<String> {
            val sources = ((root as? JsonObject)?.get("sources") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            return sources.filter { s ->
                when (s.str("sla")?.lowercase()) {
                    "stale", "missing" -> true
                    "breach" -> {
                        val age = s.num("age_s")
                        val stale = (s["thresholds_s"] as? JsonObject)?.num("stale")
                        age != null && stale != null && age > stale
                    }
                    else -> false
                }
            }.mapNotNull { it.str("source")?.let(TheOddsApiClient::canonicalBook) }.toSet()
        }

        /** [snap] without the quotes of [unsafe] books. */
        fun without(snap: RefSnapshot, unsafe: Set<String>): RefSnapshot =
            if (unsafe.isEmpty()) snap else snap.copy(events = snap.events.map { e -> e.copy(markets = e.markets.filter { it.bookKey !in unsafe }) })
    }
}
