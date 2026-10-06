package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.diag.EventLog
import com.tjshea.vigilant.data.diag.NetStats
import com.tjshea.vigilant.data.diag.PerfStats
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanTiming
import kotlinx.coroutines.delay

/**
 * What the app tells its flight recorder about itself as it runs (Tj, 2026-10-02: a diagnostics file Claude can improve the app from): the start of a process, a
 * finished Vigilant scan, CNO asking the app to wait, and Tj's switches that decide what runs by itself. Each is a pure function of what happened, so a test says
 * what the timeline reads; [AppContainer] only connects them to the app's flows.
 */
class AppRecorder(private val events: EventLog, private val net: NetStats, private val perf: PerfStats) {

    /**
     * What earlier runs kept comes back first, the start is noted, then events and connection stats are written out every [everyMs] (and at once after an error),
     * for as long as the caller's scope lives.
     */
    suspend fun run(version: String?, everyMs: Long) {
        events.load()
        net.load()
        events.info("APP", "process started: ${AppBook.name} ${version ?: "?"}")
        while (true) {
            delay(everyMs)
            events.flush()
            net.flush()
        }
    }

    /** One line for a finished Vigilant scan (its length, what it read, what failed), a WARN when it had errors, and its time in the performance block. */
    fun scanFinished(r: ScanReport, lowUsage: Boolean = false) {
        val ms = r.timing?.totalMs
        ms?.let { perf.add("scan.ms", it.toDouble()) }
        val line = scanLine(r, lowUsage)
        if (r.errors.isEmpty()) events.info("SCAN", line, ms) else events.warn("SCAN", line, ms)
    }

    /** CNO's 403/429 backoff: when it asked, how long, and a count. */
    fun cnoPaused(untilMs: Long, nowMs: Long) {
        events.warn("CNO", "CrazyNinjaOdds asked the app to wait ${((untilMs - nowMs) / 1000).coerceAtLeast(0)} s")
        events.count("cno.pauses")
    }

    /** The switches that decide what runs by itself, as Tj turns them: the timeline says what was on when something happened. */
    fun settingsChanged(before: ScanSettings, after: ScanSettings) {
        fun flip(name: String, was: Any?, now: Any?) { if (was != now) events.info("SETTINGS", "$name: $was → $now") }
        flip("auto-bet", before.autoBet, after.autoBet)
        flip("background auto-scan", before.autoScan, after.autoScan)
        flip("auto-scan every (s)", before.autoScanSeconds, after.autoScanSeconds)
        flip("keep awake", before.autoScanKeepAwake, after.autoScanKeepAwake)
        flip("sharp books for auto-bet", before.sharpAutoBet, after.sharpAutoBet)
        flip("sharp books for alerts", before.sharpAlerts, after.sharpAlerts)
        flip("preset", before.presetName, after.presetName)
        flip("paused", before.pausedByHand, after.pausedByHand)
        flip("kill switch", before.killed, after.killed)
        flip("scanner", before.scanner, after.scanner)
        flip("Pinnacle only", before.pinnacleOnly, after.pinnacleOnly)
        flip("Pinnacle only age limit (s)", before.pinnacleMaxAgeSeconds, after.pinnacleMaxAgeSeconds)
        flip("which bids go up", before.makerFocus, after.makerFocus)
        flip("low API usage books", before.lowUsageBooks, after.lowUsageBooks)
        flip("low API usage scan pace (min, 0 = Auto)", before.lowUsagePace, after.lowUsagePace)
        flip("low API usage margin", before.lowUsageMargin, after.lowUsageMargin)
        flip("auto-bet halted", before.autoBetHalted != null, after.autoBetHalted != null)
    }

    companion object {
        fun scanLine(r: ScanReport, lowUsage: Boolean = false): String {
            val ms = r.timing?.totalMs
            return "Vigilant scan finished" + (ms?.let { " in ${it / 1000} s" } ?: "") + ": ${r.booksFetched} Novig prices (${r.booksViaKey} through the key), " +
                "${r.errors.size} error${if (r.errors.size == 1) "" else "s"}" + (r.timing?.refused?.takeIf { it > 0 }?.let { ", Novig refused $it" } ?: "") +
                r.timing?.let { speedNote(it, r) }.orEmpty() + emptyNote(r, lowUsage)
        }

        /**
         * A scan with nothing in its window to price says so (Tj, 2026-10-05 20:44, v0.68.0 file: scans "finished in 0 s: 0 Novig prices" for twenty minutes looked like a broken
         * scan): with Low API usage on, no league had a game with a prop market on Novig in the next 6 h, so no feed was asked and no credit spent (RESEARCH.md §93.3).
         */
        private fun emptyNote(r: ScanReport, lowUsage: Boolean): String {
            val stats = r.result?.stats ?: return ""
            if (stats.marketsPriced > 0 || r.errors.isNotEmpty()) return ""
            return if (lowUsage) " · Low API usage: no game with a prop market on Novig in the next ${com.tjshea.vigilant.data.scanner.LowUsageBids.WINDOW_HOURS} h, so no feed was asked and nothing was spent"
            else " · no market in the scan's window to price"
        }

        /**
         * How fast the Novig reads went and what paced them, for every scan on the timeline (Tj, 2026-10-04: "a lot of times the vigilant scanner slows
         * down significantly when it is scanning novig prices, maybe down to 2 per second. Other times it is very fast"): the file used to say it only for
         * the last scan. " · 14.5 a second (315 live feed, 270 public) · public route at 4 a second, down to 2 after a refusal · key route …".
         */
        private fun speedNote(t: ScanTiming, r: ScanReport): String {
            val public = (r.booksFetched - r.booksViaKey - r.booksViaPush).coerceAtLeast(0)
            val pace = t.novigMs.takeIf { it > 0 && r.booksFetched > 0 }?.let { String.format(java.util.Locale.US, "%.1f a second", r.booksFetched * 1000.0 / it) }
            val ways = listOfNotNull("${r.booksViaPush} live feed".takeIf { r.booksViaPush > 0 }, "$public public".takeIf { public > 0 }).joinToString(", ")
            // A scan that read no Novig prices has no pace to speak of.
            val head = pace?.let { listOfNotNull(it, ways.takeIf { w -> w.isNotEmpty() }?.let { w -> "($w)" }).joinToString(" ") }
            return (listOfNotNull(head) + ScanTiming.routeNotes(t, public, keyed = r.booksViaKey + r.booksViaPush > 0)).joinToString("") { " · $it" }
        }
    }
}
