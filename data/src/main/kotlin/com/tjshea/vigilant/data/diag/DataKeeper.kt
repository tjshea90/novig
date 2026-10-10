package com.tjshea.vigilant.data.diag

import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * The app's housekeeping for everything it records (Tj, 2026-10-10: "it should never get too big that it can't load or it crashes … maybe it should automatically clear logs over 2 days old").
 *
 * Every recorder writes one file a day (`name-YYYY-MM-DD.jsonl`, Eastern like the journals). [sweep] deletes a folder's files older than its [Rule.keepDays] and then, if the folder is still over
 * its [Rule.maxBytes], the oldest files until it is under (never the newest one, which is being written). It also clears the leftovers a crashed save leaves (`*.tmp`, `*.corrupt-*` older
 * than a day). Run at start and every few hours ([everyMs]). Pure file work: nothing here reads a journal, so a huge one costs nothing to sweep.
 *
 * What is NOT swept: the Tracker's bets, the settings, the keys (they are the record of Tj's money and choices). What Claude already analysed is kept in the repo's research/ folder, not on the phone.
 */
object DataKeeper {

    /** One folder of day files: how many calendar days (today counting as one) to keep, and the most bytes it may hold. */
    data class Rule(val dir: String, val keepDays: Int, val maxBytes: Long)

    /** What a sweep removed. */
    data class Result(val files: Int = 0, val bytes: Long = 0, val byDir: Map<String, Long> = emptyMap()) {
        operator fun plus(o: Result) = Result(files + o.files, bytes + o.bytes, byDir + o.byDir.mapValues { (k, v) -> v + (byDir[k] ?: 0L) })
    }

    const val JOURNAL_DAYS = 2
    const val STUDY_DAYS = 7
    const val MB = 1_048_576L
    const val EVERY_MS = 6 * 3_600_000L

    /** The recorders' folders under the app's files directory. The scan study is longer (its bets are graded days after they are logged) but capped. */
    val RULES: List<Rule> = listOf(
        Rule("lab", JOURNAL_DAYS, 40 * MB),
        Rule("pinn-live", JOURNAL_DAYS, 20 * MB),
        Rule("live-bids", JOURNAL_DAYS, 20 * MB),
        Rule("burst", JOURNAL_DAYS, 20 * MB),
        Rule("burst-trades", JOURNAL_DAYS, 10 * MB),
        Rule("race", JOURNAL_DAYS, 20 * MB),
        Rule("study", STUDY_DAYS, 24 * MB),
    )

    private val ET = ZoneId.of("America/New_York")
    private val DAY = Regex("(\\d{4}-\\d{2}-\\d{2})")

    /** The date in a day file's name, or null. */
    fun dayOf(name: String): LocalDate? = DAY.find(name)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun sweep(filesDir: File, nowMs: Long, rules: List<Rule> = RULES): Result {
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(ET).toLocalDate()
        var out = Result()
        for (rule in rules) out += sweepDir(File(filesDir, rule.dir), rule, today)
        out += strays(filesDir, nowMs)
        return out
    }

    private fun sweepDir(dir: File, rule: Rule, today: LocalDate): Result {
        val files = dir.listFiles { f -> f.isFile && dayOf(f.name) != null }?.sortedWith(compareBy({ dayOf(it.name) }, { it.name })).orEmpty()
        if (files.isEmpty()) return Result()
        val oldest = today.minusDays((rule.keepDays - 1).coerceAtLeast(0).toLong())
        val doomed = LinkedHashSet<File>()
        files.filter { dayOf(it.name)!! < oldest }.forEach { doomed += it }
        var left = files.filter { it !in doomed }
        var size = left.sumOf { it.length() }
        // Over its cap: the oldest day goes, one at a time, until it fits; the newest file stays whatever its size (it is being written, and a cap that deletes today is a recorder that records nothing).
        while (size > rule.maxBytes && left.size > 1) {
            val f = left.first()
            doomed += f
            size -= f.length()
            left = left.drop(1)
        }
        var bytes = 0L
        var n = 0
        for (f in doomed) { val len = f.length(); if (f.delete()) { n++; bytes += len } }
        return Result(n, bytes, if (n > 0) mapOf(rule.dir to bytes) else emptyMap())
    }

    /** `*.tmp` and `*.corrupt-*` copies in the files directory that are over a day old: a save that died, an unreadable file set aside. */
    private fun strays(filesDir: File, nowMs: Long): Result {
        var n = 0
        var bytes = 0L
        filesDir.listFiles { f -> f.isFile && (f.name.endsWith(".tmp") || f.name.contains(".corrupt-")) && nowMs - f.lastModified() > 24 * 3_600_000L }?.forEach { f ->
            val len = f.length()
            if (f.delete()) { n++; bytes += len }
        }
        return Result(n, bytes, if (n > 0) mapOf("leftovers" to bytes) else emptyMap())
    }

    /** The recorders' journals that are not the scan study: what "Reset diagnostics" empties. */
    val RECORDER_DIRS: List<String> = RULES.map { it.dir }.filter { it != "study" }

    /** Deletes every day file in [dirs] (all days, today's too). */
    fun clear(filesDir: File, dirs: List<String> = RECORDER_DIRS): Result {
        var n = 0
        var bytes = 0L
        val by = HashMap<String, Long>()
        for (d in dirs) File(filesDir, d).listFiles { f -> f.isFile && dayOf(f.name) != null }?.forEach { f ->
            val len = f.length()
            if (f.delete()) { n++; bytes += len; by.merge(d, len, Long::plus) }
        }
        return Result(n, bytes, by)
    }

    /** One line for the event log and Diagnostics: "swept 12 files (31 MB): lab 20 MB, study 11 MB". */
    fun line(r: Result): String =
        if (r.files == 0) "nothing to sweep" else "swept ${r.files} file${if (r.files == 1) "" else "s"} (${r.bytes / MB} MB): " + r.byDir.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value / MB} MB" }
}
