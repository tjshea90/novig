package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.data.diag.Event
import com.tjshea.vigilant.data.diag.FrameStats
import com.tjshea.vigilant.data.diag.HostStat
import com.tjshea.vigilant.data.diag.Level
import com.tjshea.vigilant.data.diag.Snap
import com.tjshea.vigilant.data.diag.Trend
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The file Tj sends to Claude (Tj, 2026-10-02: "a file that I can send directly to Claude which Claude can understand and easily diagnose and improve the app … comprehensive
 * … log all types of events, code, failures, connection speed and issues, API usage and issues … signal to Claude what to optimize, what bugs or failures there are to fix, how
 * to make features smarter or faster or better coded"). One text file, in the order a reader needs it:
 *
 *  1. READ ME FIRST: what it is, how to work from it, the rules of this project, what is never in it.
 *  2. WHAT TO DO: the [Advisor]'s ranked findings (BUG, FAILURE, OPTIMIZE, IMPROVE, WATCH), each with its evidence, the code to open and what to try.
 *  3. SINCE THE PREVIOUS REPORT: what got better, worse, new or resolved ([Trend]): whether the last change worked.
 *  4. The existing Diagnostics report ([Diagnostics.report]): health checks, settings, scans, APIs, CNO, background, tracker, accuracy, memory, phone, exits, problems.
 *  5. The flight recorder: connections by host, API issues, timings, counters, the event timeline, the app's own log lines, its files.
 *  6. The code map, and a JSON block of the numbers (for comparing reports).
 *
 * Never a key, a token, an account id or a full URL: events and log lines are masked ([com.tjshea.vigilant.data.diag.ProblemLog.clean]), endpoints are shapes
 * ([com.tjshea.vigilant.data.diag.NetShape]), and the existing report has only ever named a key by its last four characters.
 */
object DiagnosticsFile {

    /** The message that goes with the file in the share sheet: what Claude should do with it. */
    const val PROMPT =
        "This is my Vigilant app's diagnostics file. Please read the READ ME FIRST section, then work through WHAT TO DO from the top: fix the bugs and failures, " +
            "make the slow parts faster, and improve what the data says could be smarter, following the project's own rules (CLAUDE.md, BRIEF.md). " +
            "Tell me what you found, what you changed, and the version to install."

    /** "vigilant-diagnostics-v0.43.0-2026-10-02-0912.txt". */
    fun fileName(versionName: String, now: Long, zone: TimeZone = TimeZone.getDefault()): String =
        "vigilant-diagnostics-v$versionName-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).apply { timeZone = zone }.format(Date(now)) + ".txt"

    /** Where each part of the app lives, for whoever opens the repo (github.com/tjshea90/novig). A test checks every path exists. */
    val CODE_MAP: List<Pair<String, String>> = listOf(
        "Background auto-scan, the cycle" to "app/src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt, AutoScanService.kt (keep awake, alarms)",
        "Auto-bet (places real bets)" to "app/src/main/kotlin/com/tjshea/vigilant/app/AutoBettor.kt, data/src/main/kotlin/com/tjshea/vigilant/data/novig/trading/AutoBet.kt, ApiBetPlacer.kt, ApiBetPlanner.kt",
        "Sharp-book confirmation" to "data/src/main/kotlin/com/tjshea/vigilant/data/scanner/SharpConfirm.kt, data/src/main/kotlin/com/tjshea/vigilant/data/reference/SharpBooks.kt, app/src/main/kotlin/com/tjshea/vigilant/app/SharpGate.kt",
        "Sharp veto (the sharpest book per kind of bet)" to "data/src/main/kotlin/com/tjshea/vigilant/data/scanner/SharpVeto.kt",
        "Presets (built-in and Tj's own)" to "data/src/main/kotlin/com/tjshea/vigilant/data/scanner/Presets.kt, app/src/main/kotlin/com/tjshea/vigilant/app/ui/PresetsUi.kt",
        "Each bet's record as placed, and EVERY BET" to "data/src/main/kotlin/com/tjshea/vigilant/data/tracker/AtBet.kt, BetLedger.kt, app/src/main/kotlin/com/tjshea/vigilant/app/BetRecord.kt",
        "CrazyNinjaOdds (CNO) reads and checks" to "data/src/main/kotlin/com/tjshea/vigilant/data/cno/CnoClient.kt, CnoFeed.kt, CnoBooks.kt, CnoChecks.kt, NovigBetFinder.kt, NovigLive.kt",
        "Novig API (public and signed)" to "data/src/main/kotlin/com/tjshea/vigilant/data/novig/NovigPublicClient.kt, RateGate.kt, signing/, stream/",
        "Vigilant's own scan and fair odds" to "data/src/main/kotlin/com/tjshea/vigilant/data/scanner/Scanner.kt, Planner.kt, Pricing.kt, Freshness.kt; data/src/main/kotlin/com/tjshea/vigilant/data/reference/ (the fair-odds sources)",
        "Odds math (devig, EV, fees)" to "engine/src/main/kotlin/com/tjshea/vigilant/engine/Devig.kt, EvMath.kt, Fees.kt, Odds.kt",
        "API keys, usage meters, credit pacing" to "data/src/main/kotlin/com/tjshea/vigilant/data/keys/",
        "Tracker, grading, closing lines (CLV)" to "data/src/main/kotlin/com/tjshea/vigilant/data/tracker/",
        "Alerts and notifications" to "app/src/main/kotlin/com/tjshea/vigilant/app/EvAlerts.kt, data/src/main/kotlin/com/tjshea/vigilant/data/alerts/",
        "Screens and settings" to "app/src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt, MainActivity.kt; app/src/main/kotlin/com/tjshea/vigilant/app/ui/ (SettingsScreen.kt: the home list and pages; SettingsIndex.kt: search; AutoBetScreen.kt + AutoBetUi.kt: the Auto-bet tab; ReportDialog.kt)",
        "This report (diagnostics)" to "app/src/main/kotlin/com/tjshea/vigilant/app/Diagnostics.kt, HealthChecks.kt, Advisor.kt, DiagnosticsFile.kt; data/src/main/kotlin/com/tjshea/vigilant/data/diag/ (the recorder)",
        "Settings (every switch and default)" to "data/src/main/kotlin/com/tjshea/vigilant/data/scanner/ScanSettings.kt",
        "Project rules and history" to "CLAUDE.md, BRIEF.md, TASKS.md, RESEARCH.md, NOVIG_API.md, PARLAY_API.md, BUILDLOG.md (repo root)",
    )

    /** The READ ME section. */
    fun readMe(x: Diagnostics.Extras, now: Long, zone: TimeZone): List<String> {
        val clock = SimpleDateFormat("MMM d, yyyy h:mm a z", Locale.US).apply { timeZone = zone }
        return listOf(
            "This file was made by the Vigilant app (Android, Kotlin + Compose; modules engine, data, app) on ${clock.format(Date(now))}, version ${x.versionName} (code ${x.versionCode}), on ${x.device}.",
            "Vigilant finds +EV bets on the Novig sportsbook. Tj owns it; it is built across Claude sessions. Repo: github.com/tjshea90/novig (public), branch main.",
            "",
            "HOW TO WORK FROM THIS FILE",
            "1. Read WHAT TO DO from the top. Each finding has a KIND (BUG, FAILURE, OPTIMIZE, IMPROVE, WATCH), the evidence this phone recorded, the code to open and what to try. BUG and FAILURE first; a WATCH is not a request.",
            "2. SINCE THE PREVIOUS REPORT says what changed since the last file Tj sent: use it to check that a fix worked (a finding RESOLVED, a number down) before starting new work.",
            "3. The sections after it are the evidence: health checks, settings, scans, APIs, CNO, background, tracker, accuracy (with 'The bet as placed': CLV and results split by agreement, dissent, the sharp veto, time to the start, the book check's EV, kind, sport, preset, liquidity), memory, phone, how the app last ended, recent problems, then the flight recorder (CONNECTIONS, API ISSUES, PERFORMANCE, COUNTERS, EVENT TIMELINE, APP LOG, STORAGE) and EVERY BET (one JSON line per bet with its full record as placed).",
            "   THE GOAL IS PROFIT: positive EV that the close confirms (CLV) and wins over time. Tune the presets (data/scanner/Presets.kt), the sharp veto's rankings (data/scanner/SharpVeto.kt) and the auto-bet's rules from the splits and EVERY BET; a group needs ~200+ bets with a close before its CLV says much, so weigh small groups against RESEARCH.md §65-§66.",
            "4. Make changes the way the repo asks (CLAUDE.md): write the request into TASKS.md first, load the matching skill, test (mutation-check new tests), run `bash tools/test.sh`, ship with `bash ship.sh`, release via the workflow, and send Tj the Release link.",
            "5. Everything Claude changes should make the NEXT file better: if the data you needed wasn't here, add it to the recorder (data/src/main/kotlin/com/tjshea/vigilant/data/diag/) and say so.",
            "",
            "RULES THAT APPLY TO ANY CHANGE (details in CLAUDE.md and BRIEF.md)",
            "- Every task is for Vigilant (Novig) only; the Vigilant MGM module is frozen.",
            "- Mobile data and phone storage are NOT constraints (Tj: choose accuracy and speed always). Do not 'optimize' them. API credits and rate limits ARE a budget.",
            "- Auto-bet places REAL money bets. Never loosen a safety limit (daily limit, price tolerance, pregame only, halt on a lost order, the sharp check) because a finding suggests more bets.",
            "- Auto-bet and background auto-scan are switched off by the app only after a phone restart (LaunchReset, LaunchGate; Tj 2026-10-02: never otherwise); do not add another reset.",
            "- The repo is public: never commit a key, token or account id. This file contains none.",
            "- Evidence here is from one phone (a Moto G, Android 16) over the period each section names; percentiles are over the last 100-200 samples, so say 'the data suggests', and ask Tj when a fix needs a decision.",
            "",
            "WHAT IS NEVER IN THIS FILE: API keys (only their last four characters), tokens, account or wallet ids, URL query strings, request or response bodies.",
        )
    }

    /** Everything, in order. */
    fun build(s: UiState, x: Diagnostics.Extras, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val findings = Advisor.findings(s, x, now)
        val snap = Advisor.snap(s, x, now, findings)
        val o = StringBuilder()
        o.appendLine("VIGILANT DIAGNOSTICS FILE · version ${x.versionName} · ${fileName(x.versionName, now, zone)}")
        o.appendLine()
        o.appendLine("== READ ME FIRST (for Claude) ==")
        readMe(x, now, zone).forEach { o.appendLine(it) }

        o.appendLine()
        o.appendLine("== WHAT TO DO (ranked findings) ==")
        if (findings.isEmpty()) {
            o.appendLine("No findings: nothing the rules look for was wrong in this data. If Tj reported a problem, it is not in what the recorder keeps: say what data would show it and add that to the recorder.")
        } else {
            o.appendLine(Trend.KINDS.joinToString(" · ") { k -> "${findings.count { it.kind == k }} $k" } + " (${findings.size} in all; the top $MAX_FINDINGS are listed)")
            findings.take(MAX_FINDINGS).forEachIndexed { i, f -> o.appendLine(mask(f.text(i + 1))) }
            if (findings.size > MAX_FINDINGS) o.appendLine("… and ${findings.size - MAX_FINDINGS} more, the lowest ranked, in the JSON block's findings list.")
        }

        o.appendLine()
        o.appendLine("== SINCE THE PREVIOUS REPORT ==")
        Trend.lines(x.previous, snap) { Format.age(it, now) }.forEach { o.appendLine(it) }

        o.appendLine()
        o.append(Diagnostics.report(s, x, now, zone))
        if (!o.endsWith("\n")) o.appendLine()

        connections(x, now, zone, o)
        apiIssues(x, now, o)
        performance(s, x, o)
        counters(x, now, o)
        timeline(x, now, zone, o)
        appLog(x, o)
        storage(x, o)
        everyBet(s, now, o)

        o.appendLine()
        o.appendLine("== CODE MAP ==")
        CODE_MAP.forEach { (area, where) -> o.appendLine("$area: $where") }

        o.appendLine()
        o.appendLine("== MACHINE-READABLE (the numbers and finding keys, for comparing reports) ==")
        o.appendLine("<<<JSON")
        o.appendLine(json(snap, findings).toString())
        o.appendLine(">>>")
        o.appendLine("== END OF FILE ==")
        return o.toString()
    }

    // ---- the flight recorder's sections -----------------------------------------------------------------------------------

    /**
     * Every bet, newest first, one JSON object a line ([com.tjshea.vigilant.data.tracker.BetLedger.Row]): the bet, its record as placed (`atBet`: odds,
     * books and their prices, agreement, dissent, the sharp veto, minutes to the start, EV by CNO and by the book check, stake, preset), its close
     * (`clv`, `closeFair`, `closeVia`) and its result (`status`, `profit`). As long as the Tracker is: the file has no size limit (Tj, 2026-10-02).
     */
    private fun everyBet(s: UiState, now: Long, o: StringBuilder) {
        o.appendLine()
        o.appendLine("== EVERY BET (JSON lines, newest first: the bet, atBet = its record as placed, its close and its result; ${s.bets.size} bets) ==")
        o.appendLine("<<<JSONL")
        s.bets.sortedByDescending { it.createdAtMs }.forEach { o.appendLine(mask(com.tjshea.vigilant.data.tracker.BetLedger.line(it, now))) }
        o.appendLine(">>>")
    }

    private fun connections(x: Diagnostics.Extras, now: Long, zone: TimeZone, o: StringBuilder) {
        val since = x.net.sinceMs?.let { SimpleDateFormat("MMM d, h:mm a", Locale.US).apply { timeZone = zone }.format(Date(it)) } ?: "the app opened"
        o.appendLine()
        o.appendLine("== CONNECTIONS (every call the app made, by host, since $since) ==")
        if (x.net.hosts.isEmpty()) {
            o.appendLine("No calls recorded yet.")
            return
        }
        o.appendLine("host · calls · errors · to first byte p50/p95/max ms · body speed p50 KB/s · downloaded · networks · statuses")
        for ((host, h) in x.net.hosts.entries.sortedByDescending { it.value.calls }) {
            val l = h.latency
            val sp = h.speed
            o.appendLine(
                "$host · ${h.calls} · ${h.errors} (${pctOf(h.errorRate)}) · " + (if (l.count > 0) "${l.p50.toLong()}/${l.p95.toLong()}/${l.max.toLong()}" else "n/a") + " · " +
                    (if (sp.count > 0) "${(sp.p50 / 1024).toLong()}" else "n/a") + " · ${h.bytes / 1024} KB · " +
                    (h.byNet.entries.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "?" }) + " · " +
                    (h.status.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}×${it.value}" } + h.kinds.entries.joinToString("") { ", ${it.key}×${it.value}" }).ifEmpty { "none" },
            )
            h.paths.entries.sortedByDescending { it.value.calls }.take(MAX_PATHS).forEach { (path, p) ->
                o.appendLine(
                    "    $path · ${p.calls} calls · ${p.errors} failed" +
                        (p.fails.takeIf { it.isNotEmpty() }?.entries?.sortedByDescending { it.value }?.joinToString(", ", " (", ")") { (k, n) -> if (k.toIntOrNull() != null) "HTTP $k×$n" else "$k×$n" } ?: "") +
                        " · ${p.totalMs / maxOf(p.calls, 1)} ms average to first byte" + (p.lastStatus?.let { " · last HTTP $it" } ?: ""),
                )
            }
            h.lastError?.let { o.appendLine("    last failure ${h.lastErrorAtMs?.let { t -> Format.age(t, now) } ?: ""}: ${clean(it)}") }
            h.lastLimit?.let { o.appendLine("    last rate limit ${h.lastLimitAtMs?.let { t -> Format.age(t, now) } ?: ""}: ${clean(it)} (${h.limits} in all)") }
            val recent = h.hours.entries.sortedByDescending { it.key.toLongOrNull() ?: 0 }.take(6)
            if (recent.isNotEmpty()) o.appendLine("    last hours (calls/failed): " + recent.joinToString(" ") { (hr, v) -> "${hourLabel(hr, zone)} ${v.calls}/${v.errors}" })
        }
    }

    private fun apiIssues(x: Diagnostics.Extras, now: Long, o: StringBuilder) {
        o.appendLine()
        o.appendLine("== API ISSUES (what the providers said back) ==")
        val hosts = x.net.hosts
        val limited = hosts.filter { it.value.limits > 0 }
        if (limited.isEmpty()) o.appendLine("No host has asked the app to slow down (no 429, no 403 with a Retry-After).")
        limited.forEach { (h, v) -> o.appendLine("$h: ${v.limits} rate-limit answers; last ${v.lastLimitAtMs?.let { Format.age(it, now) } ?: "?"}: ${v.lastLimit?.let(::clean)}") }
        val kinds = hosts.values.flatMap { it.kinds.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        o.appendLine("Calls that failed before an answer, by kind: " + kinds.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "none" })
        val statuses = hosts.values.flatMap { it.status.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        o.appendLine("HTTP statuses, all hosts: " + statuses.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}×${it.value}" }.ifEmpty { "none" })
        // When the failures happened (by hour of day on this phone): a pattern says the provider, the phone's sleep or the network.
        val byHour = HashMap<Int, Int>()
        hosts.values.forEach { h -> h.hours.forEach { (hr, v) -> if (v.errors > 0) byHour.merge(((hr.toLongOrNull() ?: 0L) % 24).toInt(), v.errors, Int::plus) } }
        if (byHour.isNotEmpty()) o.appendLine("Failures by hour of day (UTC): " + byHour.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}h:${it.value}" })
        o.appendLine("API credits and key state: see 'API usage', 'Runway' and 'Last rounds' above (each provider's own count); feed calls for the sharp check: ${x.sharpCalls} (${x.sharpFailures} failed).")
    }

    private fun performance(s: UiState, x: Diagnostics.Extras, o: StringBuilder) {
        o.appendLine()
        o.appendLine("== PERFORMANCE (this run of the app) ==")
        x.coldStartMs?.let { o.appendLine("Screen appeared $it ms after the process started.") } ?: o.appendLine("Cold start: not measured this run (the process was started by a service or alarm, or no screen opened yet).")
        if (x.perf.isEmpty()) o.appendLine("No timings yet.")
        x.perf.forEach { (name, v) -> o.appendLine("$name: ${v.count} samples · p50 ${v.p50.toLong()} · p95 ${v.p95.toLong()} · max ${v.max.toLong()} · mean ${v.mean.toLong()}") }
        frameLines(x.frames).forEach(o::appendLine)
        o.appendLine(x.scanCpu?.let { ThreadCpu.text(it, "the last Vigilant scan") } ?: "CPU during a Vigilant scan: no scan has ended in this run of the app yet.")
        o.appendLine("Heap ${x.memory.usedMb} of ${x.memory.maxMb} MB (${Math.round(x.memory.fraction * 100)}%).")
        val p = x.phone
        o.appendLine("Battery ${p.batteryPct?.let { "$it%" } ?: "?"}${if (p.charging == true) " (charging)" else if (p.charging == false) " (not charging)" else ""} · thermal ${p.thermal ?: "?"} · Doze now ${p.dozing ?: "?"} · standby bucket ${p.standbyBucket ?: "?"}")
        s.status.timing?.let { o.appendLine("Last Vigilant scan, where the time went: " + com.tjshea.vigilant.data.scanner.ScanTiming.text(it, s.status.booksFetched, s.status.booksViaKey, s.status.booksViaPush, s.status.keyReadPerSec)) }
    }

    /**
     * The frame meter's lines (Tj, 2026-10-02: "the entire app becomes laggy" during a Vigilant scan): the screen's frames this run, by what the app
     * was doing, so a stutter that comes with a scan (or a Check odds now, or CNO's reads) shows against the frames drawn with none of them running.
     */
    internal fun frameLines(frames: Map<String, com.tjshea.vigilant.data.diag.FrameStats.Bucket>): List<String> {
        if (frames.values.none { it.frames > 0 }) return listOf("Screen frames: none timed yet this run (timed while Vigilant is in front).")
        val order = listOf(FrameStats.SCAN, FrameStats.CHECK, FrameStats.CNO, FrameStats.QUIET)
        return listOf("Screen frames this run, by what was running (slow: over twice the frame's deadline; frozen: over ${FrameStats.FROZEN_MS.toLong()} ms):") +
            frames.entries.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) order.size else i } }.filter { it.value.frames > 0 }.map { (what, b) ->
                "  $what: ${b.frames} frames · ${Format.percent(b.slowShare, 1)} slow (${b.slow}) · ${b.frozen} frozen · " +
                    "p50 ${b.durations.p50.toLong()} ms · p95 ${b.durations.p95.toLong()} ms · worst ${b.durations.max.toLong()} ms (of the last ${b.durations.count})"
            }
    }

    private fun counters(x: Diagnostics.Extras, now: Long, o: StringBuilder) {
        val since = x.eventsSinceMs?.let { Format.age(it, now) } ?: "?"
        o.appendLine()
        o.appendLine("== COUNTERS (kept across restarts, started $since) ==")
        if (x.counters.isEmpty()) {
            o.appendLine("None yet.")
            return
        }
        val c = x.counters
        listOf("cycle.runs", "cycle.errors", "autobet.runs", "autobet.looked", "autobet.passed", "autobet.placed", "alerts.sent", "cno.pauses").forEach { k -> c[k]?.let { o.appendLine("$k: $it") } }
        val skips = c.filterKeys { it.startsWith("autobet.skip.") }.entries.sortedByDescending { it.value }
        if (skips.isNotEmpty()) {
            o.appendLine("Why auto-bet skipped bets (reason: count):")
            skips.take(15).forEach { o.appendLine("    ${it.key.removePrefix("autobet.skip.")}: ${it.value}") }
        }
        val sharp = c.filterKeys { it.startsWith("sharp.") }.entries.sortedBy { it.key }
        if (sharp.isNotEmpty()) o.appendLine("Sharp-book check verdicts: " + sharp.joinToString(", ") { "${it.key.removePrefix("sharp.")} ${it.value}" })
        c.filterKeys { k -> listOf("cycle.", "autobet.", "alerts.", "cno.", "sharp.").none { k.startsWith(it) } }.entries.sortedBy { it.key }.forEach { o.appendLine("${it.key}: ${it.value}") }
    }

    /** Warnings and errors from the last day, then the newest events of any kind, oldest first, as lines. */
    fun timelineEvents(events: List<Event>, now: Long): List<Event> {
        val important = events.filter { it.level != Level.INFO && now - it.lastMs < Advisor.DAY_MS }.takeLast(MAX_IMPORTANT)
        val recent = events.takeLast(MAX_RECENT)
        return (important + recent).distinct().sortedBy { it.atMs }
    }

    private fun timeline(x: Diagnostics.Extras, now: Long, zone: TimeZone, o: StringBuilder) {
        o.appendLine()
        o.appendLine("== EVENT TIMELINE (oldest first: every warning and error of the last day, then the newest events) ==")
        val clock = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).apply { timeZone = zone }
        val events = timelineEvents(x.events, now)
        if (events.isEmpty()) o.appendLine("No events yet.")
        events.forEach { e ->
            o.appendLine(
                "${clock.format(Date(e.atMs))} ${e.level.name.padEnd(5)} ${e.cat.padEnd(8)} ${clean(e.msg)}" + (if (e.n > 1) " (×${e.n}, last ${clock.format(Date(e.lastMs))})" else "") +
                    (e.ms?.let { " [$it ms]" } ?: "") + (e.where?.let { " @ ${clean(it.split(" < ").joinToString(" < ") { f -> com.tjshea.vigilant.data.diag.EventLog.short(f) })}" } ?: ""),
            )
        }
    }

    private fun appLog(x: Diagnostics.Extras, o: StringBuilder) {
        o.appendLine()
        o.appendLine("== APP LOG (this process's warnings and errors from Android's own log, newest last) ==")
        if (x.logcat.isEmpty()) o.appendLine("Nothing (none was written, or this phone doesn't let the app read its log).")
        x.logcat.forEach { o.appendLine(clean(it.toString())) }
    }

    private fun storage(x: Diagnostics.Extras, o: StringBuilder) {
        o.appendLine()
        o.appendLine("== STORAGE (the app's files) ==")
        if (x.storage.isEmpty()) o.appendLine("Not read.")
        else {
            o.appendLine("Total ${x.storage.sumOf { it.second } / 1024} KB: " + x.storage.take(MAX_FILES).joinToString(", ") { "${it.first} ${it.second / 1024} KB" })
        }
    }

    // ---- the JSON block ----------------------------------------------------------------------------------------------------

    fun json(snap: Snap, findings: List<Advisor.Finding>): JsonObject = JsonObject(
        mapOf(
            "format" to JsonPrimitive(1),
            "version" to JsonPrimitive(snap.version),
            "code" to JsonPrimitive(snap.code),
            "atMs" to JsonPrimitive(snap.atMs),
            "metrics" to JsonObject(snap.metrics.toSortedMap().mapValues { JsonPrimitive(it.value) }),
            "findings" to JsonArray(
                findings.map { f ->
                    JsonObject(
                        mapOf(
                            "key" to JsonPrimitive(mask(f.key)), "kind" to JsonPrimitive(f.kind), "title" to JsonPrimitive(mask(f.title)),
                            "evidence" to JsonPrimitive(mask(f.evidence).take(300)), "code" to JsonPrimitive(mask(f.code)), "do" to JsonPrimitive(mask(f.action).take(300)),
                        ),
                    )
                },
            ),
        ),
    )

    // ---- small things -------------------------------------------------------------------------------------------------------

    /** Every free-text field goes through the same masking as a problem (a key, token or long id becomes its last four characters), whatever stored it. */
    private fun clean(s: String) = com.tjshea.vigilant.data.diag.ProblemLog.clean(s)

    /** The same masking that keeps a stack's line breaks: for a finding's text, which the advisor built from stored words (a frame, a message, a host's answer). */
    private fun mask(s: String) = com.tjshea.vigilant.data.diag.ProblemLog.mask(s)

    private fun pctOf(v: Double) = String.format(Locale.US, "%.1f%%", v * 100)

    private fun hourLabel(hour: String, zone: TimeZone): String =
        SimpleDateFormat("MM-dd HH'h'", Locale.US).apply { timeZone = zone }.format(Date((hour.toLongOrNull() ?: 0L) * 3_600_000L))

    const val MAX_FINDINGS = 25
    const val MAX_PATHS = 5
    const val MAX_IMPORTANT = 120
    const val MAX_RECENT = 60
    const val MAX_FILES = 14
}
