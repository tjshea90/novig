package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.diag.EventLog
import com.tjshea.vigilant.data.diag.NetStats
import com.tjshea.vigilant.data.diag.PerfStats
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanSettings
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
    fun scanFinished(r: ScanReport) {
        val ms = r.timing?.totalMs
        ms?.let { perf.add("scan.ms", it.toDouble()) }
        val line = scanLine(r)
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
        flip("paused", before.paused, after.paused)
        flip("scanner", before.scanner, after.scanner)
        flip("auto-bet halted", before.autoBetHalted != null, after.autoBetHalted != null)
    }

    companion object {
        fun scanLine(r: ScanReport): String {
            val ms = r.timing?.totalMs
            return "Vigilant scan finished" + (ms?.let { " in ${it / 1000} s" } ?: "") + ": ${r.booksFetched} Novig prices (${r.booksViaKey} through the key), " +
                "${r.errors.size} error${if (r.errors.size == 1) "" else "s"}" + (r.timing?.refused?.takeIf { it > 0 }?.let { ", Novig refused $it" } ?: "")
        }
    }
}
