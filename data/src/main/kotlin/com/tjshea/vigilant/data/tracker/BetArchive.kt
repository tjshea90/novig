package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * The permanent day-by-day record of every bet and bid (Tj, 2026-10-11: "make the app auto save each day's bets into the vigilant folder in my local android downloads folder ... I do hundreds of bets a day
 * sometimes"). RESEARCH.md §132.
 *
 * Two files a day in the app's own `bet-archive/` folder, each written whole by streaming (one line at a time, never the day in memory as text), so a day is about 1 MB at hundreds of bets:
 *  - `bets-YYYY-MM-DD.jsonl`: the Tracker's bets and bid fills placed that (New York) day, each as a full [BetHistory.Row] with its [BetHistory.Detail] (every book's price, the rules in force);
 *  - `bids-YYYY-MM-DD.jsonl`: every bid posted that day once it has ended (filled or not), merged over what the file already held, because the desk forgets an unfilled bid after 2 days.
 * A day's file is rewritten only when its content changed (a settled result, a close found later), told by a SHA-256 kept in `index.json`, so the work after a quiet hour is a hash compare per recent day.
 * The app copies the files that changed to Downloads/Vigilant/bets ([pendingMirror], [markMirrored]). Nothing here ever deletes a day.
 */
class BetArchive(val dir: File) {

    private val index = File(dir, "index.json")
    private val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))
    private val json = Json { prettyPrint = false }

    /** name -> sha256 for "written" (what the archive holds) and "mirrored" (what was last copied out). */
    private fun load(): MutableMap<String, MutableMap<String, String>> = runCatching {
        json.decodeFromString(serializer, index.readText()).mapValues { it.value.toMutableMap() }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun save(m: Map<String, Map<String, String>>) {
        dir.mkdirs()
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(json.encodeToString(serializer, m))
        tmp.renameTo(index)
    }

    fun betFile(day: String) = File(dir, "bets-$day.jsonl")
    fun bidFile(day: String) = File(dir, "bids-$day.jsonl")

    /** Writes [lines] to [target] through a temp file; replaces it (and says so) only when the SHA-256 differs from [known]. Returns the new hash, or null when unchanged. */
    private fun replaceIfChanged(target: File, known: String?, lines: Sequence<String>): String? {
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        val md = MessageDigest.getInstance("SHA-256")
        var any = false
        DigestOutputStream(tmp.outputStream().buffered(), md).bufferedWriter(Charsets.UTF_8).use { w ->
            for (l in lines) { w.write(l); w.write("\n"); any = true }
        }
        if (!any) { tmp.delete(); return null }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        if (sha == known && target.exists()) { tmp.delete(); return null }
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        return sha
    }

    /** What one [sync] did. */
    data class Synced(val days: Int, val betFiles: Int, val bidFiles: Int, val changed: List<File>)

    /**
     * Brings the archive up to date with [bets] and [makerBids] at [now]. Only days that can still change are rewritten: the last [RECENT_DAYS], any day with an open bet, and (with [full]) every day,
     * which the app does about once a day. A day's bid file is the union of what it held and the bids now ended.
     */
    @Synchronized
    fun sync(bets: List<TrackedBet>, makerBids: List<MakerBid>, now: Long, full: Boolean = false): Synced {
        val state = load()
        val written = state.getOrPut("written") { mutableMapOf() }
        val changed = ArrayList<File>()
        val byBetId = HashMap<String, MakerBid>()
        makerBids.forEach { m -> m.betId?.let { byBetId[it] = m } }
        val today = BetHistory.day(now)
        val recentFrom = java.time.LocalDate.parse(today).minusDays(RECENT_DAYS.toLong()).toString()

        val byDay = bets.groupBy { BetHistory.day(it.createdAtMs) }
        var betFiles = 0
        for ((day, list) in byDay.toSortedMap()) {
            val f = betFile(day)
            val open = list.any { it.status == BetStatus.PENDING || it.startsTs > now - 3 * 3_600_000L }
            if (!(full || !f.exists() || day >= recentFrom || open)) continue
            val sha = replaceIfChanged(f, written[f.name], list.sortedBy { it.createdAtMs }.asSequence().map { BetHistory.line(BetHistory.row(it, byBetId[it.id], now, detail = true)) })
            if (sha != null) { written[f.name] = sha; changed += f; betFiles++ }
        }

        var bidFiles = 0
        val endedByDay = makerBids.filter { it.status.ended }.groupBy { BetHistory.day(it.postedAtMs) }
        for ((day, list) in endedByDay.toSortedMap()) {
            val f = bidFile(day)
            val merged = LinkedHashMap<String, String>()
            if (f.exists()) f.useLines { ls -> ls.forEach { l -> runCatching { BetHistory.json.decodeFromString<BetHistory.BidRow>(l) }.getOrNull()?.let { r -> merged[r.clientId] = l } } }
            list.forEach { m -> merged[m.clientId.take(12)] = BetHistory.line(BetHistory.bidRow(m, com.tjshea.vigilant.data.scanner.SharpVeto.sportOf(m.league).name.lowercase())) }
            val lines = merged.values.sortedBy { l -> runCatching { BetHistory.json.decodeFromString<BetHistory.BidRow>(l).postedMs }.getOrDefault(0L) }
            val sha = replaceIfChanged(f, written[f.name], lines.asSequence())
            if (sha != null) { written[f.name] = sha; changed += f; bidFiles++ }
        }
        save(state)
        return Synced(byDay.size, betFiles, bidFiles, changed)
    }

    /** The archive's files whose current content has not been copied out yet (to Downloads), oldest first. */
    @Synchronized
    fun pendingMirror(): List<File> {
        val state = load()
        val written = state["written"].orEmpty()
        val mirrored = state["mirrored"].orEmpty()
        return written.filter { (name, sha) -> mirrored[name] != sha && File(dir, name).exists() }.keys.sorted().map { File(dir, it) }
    }

    /** [file] was copied out as it is now. */
    @Synchronized
    fun markMirrored(file: File) {
        val state = load()
        val sha = state["written"]?.get(file.name) ?: return
        state.getOrPut("mirrored") { mutableMapOf() }[file.name] = sha
        save(state)
    }

    /**
     * Every bid ever posted as JSON lines: the day files, then the bids [current] on the desk that are not in a day file yet (still active, or ended since the last sync). A bid
     * is listed once (its day file's line wins over a live one only when the live one has not ended). Streams; never holds the history.
     */
    fun bidLines(current: List<MakerBid>): Sequence<String> = sequence {
        val seen = HashSet<String>()
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("bids-") && f.name.endsWith(".jsonl") }?.sortedBy { it.name }.orEmpty()
        val live = current.associateBy { it.clientId.take(12) }
        for (f in files) {
            for (l in f.useLines { it.toList() }) {
                val id = runCatching { BetHistory.json.decodeFromString<BetHistory.BidRow>(l).clientId }.getOrNull() ?: continue
                // A bid that was active when archived and ended since is better told by the live record; ended ones are final.
                if (seen.add(id)) yield(live[id]?.takeIf { it.status.ended }?.let { m -> BetHistory.line(BetHistory.bidRow(m, com.tjshea.vigilant.data.scanner.SharpVeto.sportOf(m.league).name.lowercase())) } ?: l)
            }
        }
        for (m in current.sortedBy { it.postedAtMs }) if (seen.add(m.clientId.take(12))) yield(BetHistory.line(BetHistory.bidRow(m, com.tjshea.vigilant.data.scanner.SharpVeto.sportOf(m.league).name.lowercase())))
    }

    /** Files in the archive (days), for the settings line. */
    fun counts(): Pair<Int, Long> {
        val fs = dir.listFiles { f -> f.isFile && f.name.endsWith(".jsonl") }.orEmpty()
        return fs.size to fs.sumOf { it.length() }
    }

    companion object {
        /** Days back that are always re-checked: results and closes arrive up to a few days after the bet. */
        const val RECENT_DAYS = 4
    }
}
