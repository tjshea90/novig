package com.tjshea.vigilant.data.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/**
 * What the feed race saw, on disk: one JSON line a reading, one file a day (`files/race/race-<day>.jsonl`), appended to and never rewritten, the oldest days dropped after [KEEP_DAYS].
 * Numbers and names of games only: no key, no account, no bet. `s` = a score reading, `n` = a Novig moneyline trade, `o` = Polymarket's odds for an outcome.
 */
class FeedRaceJournal(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {

    class Tape(val scores: List<FeedRace.Sighting>, val trades: List<FeedRace.NovigTick>, val odds: List<FeedRace.OddsTick>)

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(atMs: Long): File = File(dir, "race-${Instant.ofEpochMilli(atMs).atZone(ZoneOffset.UTC).toLocalDate()}.jsonl")

    @Synchronized
    fun append(scores: List<FeedRace.Sighting> = emptyList(), trades: List<FeedRace.NovigTick> = emptyList(), odds: List<FeedRace.OddsTick> = emptyList()) {
        if (scores.isEmpty() && trades.isEmpty() && odds.isEmpty()) return
        dir.mkdirs()
        val byFile = LinkedHashMap<File, StringBuilder>()
        fun add(atMs: Long, line: JsonObject) { byFile.getOrPut(file(atMs)) { StringBuilder() }.append(json.encodeToString(JsonObject.serializer(), line)).append('\n') }
        for (s in scores) add(s.atMs, JsonObject(mapOf("k" to JsonPrimitive("s"), "src" to JsonPrimitive(s.src), "id" to JsonPrimitive(s.gameId), "home" to JsonPrimitive(s.home), "away" to JsonPrimitive(s.away), "h" to JsonPrimitive(s.h), "a" to JsonPrimitive(s.a), "t" to JsonPrimitive(s.atMs), "init" to JsonPrimitive(s.init), "live" to JsonPrimitive(s.live), "rtt" to JsonPrimitive(s.rttMs))))
        for (n in trades) add(n.tsMs, JsonObject(mapOf("k" to JsonPrimitive("n"), "ev" to JsonPrimitive(n.event), "mk" to JsonPrimitive(n.marketId), "o" to JsonPrimitive(n.outcomeId), "p" to JsonPrimitive(n.price), "q" to JsonPrimitive(n.qty), "t" to JsonPrimitive(n.tsMs))))
        for (o in odds) add(o.seenMs, JsonObject(mapOf("k" to JsonPrimitive("o"), "ev" to JsonPrimitive(o.event), "o" to JsonPrimitive(o.outcome), "m" to JsonPrimitive(o.mid), "t" to JsonPrimitive(o.seenMs), "sv" to (o.serverMs?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?)))))
        for ((f, text) in byFile) f.appendText((if (endsMidLine(f)) "\n" else "") + text.toString())   // a write cut off earlier must not swallow this one's first line
        prune()
    }

    /** Everything recorded since [sinceMs] (by the day files that can hold it; each line judged by its own time). */
    @Synchronized
    fun read(sinceMs: Long): Tape {
        val scores = ArrayList<FeedRace.Sighting>()
        val trades = ArrayList<FeedRace.NovigTick>()
        val odds = ArrayList<FeedRace.OddsTick>()
        val days = (sinceMs / DAY_MS..clock() / DAY_MS).map { file(it * DAY_MS) }.distinct()
        for (f in days) {
            if (!f.exists()) continue
            f.forEachLine { line ->
                val o = runCatching { json.parseToJsonElement(line) as JsonObject }.getOrNull() ?: return@forEachLine
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
                fun l(k: String) = (o[k] as? JsonPrimitive)?.longOrNull
                fun d(k: String) = (o[k] as? JsonPrimitive)?.doubleOrNull
                val t = l("t") ?: return@forEachLine
                if (t < sinceMs) return@forEachLine
                when (s("k")) {
                    "s" -> scores += FeedRace.Sighting(s("src") ?: return@forEachLine, s("id") ?: return@forEachLine, s("home") ?: "", s("away") ?: "", l("h")?.toInt() ?: return@forEachLine, l("a")?.toInt() ?: return@forEachLine, t, (o["init"] as? JsonPrimitive)?.booleanOrNull ?: false, (o["live"] as? JsonPrimitive)?.booleanOrNull ?: true, l("rtt") ?: 0L)
                    "n" -> trades += FeedRace.NovigTick(s("ev") ?: return@forEachLine, s("mk") ?: return@forEachLine, s("o") ?: return@forEachLine, d("p") ?: return@forEachLine, l("q") ?: 0L, t)
                    "o" -> odds += FeedRace.OddsTick(s("ev") ?: return@forEachLine, s("o") ?: return@forEachLine, d("m") ?: return@forEachLine, t, l("sv"))
                }
            }
        }
        return Tape(scores.sortedBy { it.atMs }, trades.sortedBy { it.tsMs }, odds.sortedBy { it.seenMs })
    }

    private fun endsMidLine(f: File): Boolean = f.length() > 0 && runCatching { java.io.RandomAccessFile(f, "r").use { it.seek(f.length() - 1); it.read() != '\n'.code } }.getOrDefault(false)

    /** Writes every recorded line at or after [sinceMs] to [w], as the files hold them (the share file's raw tape: what the report was made from). Returns how many. */
    @Synchronized
    fun copyRaw(w: java.io.Writer, sinceMs: Long): Int {
        var n = 0
        val days = (sinceMs / DAY_MS..clock() / DAY_MS).map { file(it * DAY_MS) }.distinct()
        for (f in days) {
            if (!f.exists()) continue
            f.forEachLine { line ->
                val t = Regex("\"t\":(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return@forEachLine
                if (t >= sinceMs && line.startsWith("{") && line.endsWith("}")) { w.write(line); w.write("\n"); n++ }
            }
        }
        return n
    }

    /** Day files older than [KEEP_DAYS]. */
    private fun prune() {
        val cutoff = clock() - KEEP_DAYS * DAY_MS
        dir.listFiles { f -> f.isFile && f.name.startsWith("race-") && f.name.endsWith(".jsonl") }?.forEach { f ->
            val day = runCatching { java.time.LocalDate.parse(f.name.removePrefix("race-").removeSuffix(".jsonl")) }.getOrNull() ?: return@forEach
            if (day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() < cutoff) runCatching { f.delete() }
        }
    }

    companion object {
        const val KEEP_DAYS = 7
        private const val DAY_MS = 24 * 3_600_000L
    }
}
