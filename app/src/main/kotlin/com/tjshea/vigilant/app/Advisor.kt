package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.data.diag.EventLog
import com.tjshea.vigilant.data.diag.Level
import com.tjshea.vigilant.data.diag.ProblemLog
import com.tjshea.vigilant.data.diag.Snap
import com.tjshea.vigilant.data.diag.Trend
import java.util.Locale

/**
 * Turns everything the flight recorder and the report know into a ranked list of work for whoever reads the file (Tj, 2026-10-02: "signal to Claude what to
 * optimize, what bugs or failures there are to fix, how to make features smarter or faster or better coded"). Pure rules over [UiState] and
 * [Diagnostics.Extras]; each finding says what it saw ([Finding.evidence]), which code owns it ([Finding.code]) and what to try ([Finding.action]). Kinds, worst first:
 * BUG (the app threw: fix it), FAILURE (something it depends on fails or a feature isn't working), OPTIMIZE (slow or wasteful: make it faster), IMPROVE (works,
 * but the data says it could be smarter), WATCH (not wrong yet).
 *
 * The rules never recommend saving mobile data or storage: Tj's standing rule (CLAUDE.md) is accuracy and speed over both. API credits and rate limits are a real
 * budget, and anything that spends money (auto-bet) is never loosened by a finding.
 */
object Advisor {

    data class Finding(
        /** Stable across reports ("net:parlay-api.com:errors"), so the next report can say whether it's still there. */
        val key: String,
        val kind: String,
        val title: String,
        val evidence: String,
        val code: String,
        val action: String,
        /** Within a kind, the bigger first. */
        val weight: Double = 0.0,
    ) {
        fun text(n: Int): String = buildString {
            append("[$kind] #$n $title\n")
            append("    key: $key\n")
            append("    evidence: $evidence\n")
            if (code.isNotBlank()) append("    code: $code\n")
            if (action.isNotBlank()) append("    do: $action\n")
        }.trimEnd()
    }

    const val DAY_MS = 24L * 3_600_000L

    /** Every finding, ranked: kind first (BUG … WATCH), then weight. */
    fun findings(s: UiState, x: Diagnostics.Extras, now: Long): List<Finding> =
        (crashes(x, now) + errorEvents(x, now) + repeats(x, now) + health(s, x, now) + network(x, now) + performance(s, x, now) + funnels(s, x) + logcat(x) + storage(x))
            .distinctBy { it.key }
            .sortedWith(compareBy<Finding> { Trend.KINDS.indexOf(it.kind) }.thenByDescending { it.weight })

    /** The numbers the next report compares with ([Trend]). */
    fun metrics(s: UiState, x: Diagnostics.Extras, now: Long): Map<String, Double> = buildMap {
        val checks = HealthChecks.of(s, x, now)
        put("health.fail", checks.count { it.level == HealthChecks.Level.FAIL }.toDouble())
        put("health.warn", checks.count { it.level == HealthChecks.Level.WARN }.toDouble())
        put("events.errors24h", x.events.filter { it.level == Level.ERROR && now - it.atMs < DAY_MS }.sumOf { it.n }.toDouble())
        put("crashes24h", x.exits.count { it.bad && now - it.atMs < DAY_MS }.toDouble())
        for ((host, h) in x.net.hosts) {
            if (h.calls >= 5) {
                put("net.$host.errorRate", Math.round(h.errorRate * 1000) / 10.0)
                put("net.$host.p95ms", h.latency.p95)
                put("net.$host.calls", h.calls.toDouble())
            }
        }
        x.perf["cycle.ms"]?.takeIf { it.count >= 5 }?.let { put("perf.cycle.p95ms", it.p95) }
        x.perf["scan.ms"]?.takeIf { it.count >= 1 }?.let { put("perf.scan.p95ms", it.p95) }
        x.coldStartMs?.let { put("perf.coldStartMs", it.toDouble()) }
        // The frame meter: the share of slow frames by what was running, so the next report shows whether a scan still makes the screen stutter.
        for ((what, b) in x.frames) if (b.frames >= MIN_FRAMES) put("frames.${FRAME_KEYS[what] ?: what}.slowPct", Math.round(b.slowShare * 1000) / 10.0)
        put("heap.pct", Math.round(x.memory.fraction * 1000) / 10.0)
        put("autobet.placed", (x.counters["autobet.placed"] ?: 0L).toDouble())
        put("autobet.looked", (x.counters["autobet.looked"] ?: 0L).toDouble())
        put("late.cycles", x.cycles.lateCount.toDouble())
    }

    fun snap(s: UiState, x: Diagnostics.Extras, now: Long, findings: List<Finding>): Snap =
        Snap(now, x.versionName, x.versionCode, metrics(s, x, now), findings.associate { it.key to it.kind })

    // ---- the app threw ------------------------------------------------------------------------------------------------

    /** A crash saved by the app's own handler ([AppExits]): "at com.tjshea.vigilant.app.Foo.bar(Foo.kt:12)" is where to look. */
    private val APP_FRAME = Regex("""at (com\.tjshea\.vigilant[\w.$]*)\.([\w$<>]+)\(([\w.]+):(\d+)\)""")

    private fun crashes(x: Diagnostics.Extras, now: Long): List<Finding> {
        // A crash or exit from before this version was installed happened on older code: say so, as something to check rather than a bug to
        // chase (Tj's 2026-10-02 file ranked the v0.38.0 out-of-memory crash, fixed in v0.39.0-0.39.1, as its top BUG on v0.43.0).
        val installed = x.installedAtMs
        val (older, current) = x.problems.filter { it.area == "App crash" }.partition { installed != null && it.lastAtMs < installed }
        return crashesOf(current, x.exits.filter { it.bad && now - it.atMs < 3 * DAY_MS && (installed == null || it.atMs >= installed) }, now) +
            older.groupBy { frameOf(it) }.map { (frame, ps) ->
                Finding(
                    "watch:oldcrash:$frame", "WATCH", "A crash at $frame before this version was installed (${ps.sumOf { it.count }}×, last ${Format.age(ps.maxOf { it.lastAtMs }, now)})",
                    "${headOf(ps.first())}. It happened on an earlier version; if a later version fixed it, it won't come back.",
                    "the file ${frame.substringAfterLast('(').substringBefore(':')}; BUILDLOG.md and RESEARCH.md say what changed since", "",
                    weight = 5.0,
                )
            }
    }

    private fun frameOf(p: com.tjshea.vigilant.data.diag.Problem): String =
        APP_FRAME.find(p.message)?.let { "${it.groupValues[1].substringAfterLast('.')}.${it.groupValues[2]}(${it.groupValues[3]}:${it.groupValues[4]})" }
            ?: p.message.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("at=") }?.take(80) ?: "unknown"

    private fun headOf(p: com.tjshea.vigilant.data.diag.Problem): String =
        p.message.lineSequence().firstOrNull { it.contains("Exception") || it.contains("Error") }?.take(140) ?: p.message.take(140)

    private fun crashesOf(crashes: List<com.tjshea.vigilant.data.diag.Problem>, bad: List<AppExits.Exit>, now: Long): List<Finding> {
        if (crashes.isEmpty() && bad.isEmpty()) return emptyList()
        val byFrame = crashes.groupBy { p ->
            APP_FRAME.find(p.message)?.let { "${it.groupValues[1].substringAfterLast('.')}.${it.groupValues[2]}(${it.groupValues[3]}:${it.groupValues[4]})" }
                ?: p.message.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("at=") }?.take(80) ?: "unknown"
        }
        return byFrame.map { (frame, ps) ->
            val head = ps.first().message.lineSequence().firstOrNull { it.contains("Exception") || it.contains("Error") }?.take(140) ?: ps.first().message.take(140)
            Finding(
                "bug:crash:$frame", "BUG", "The app crashed ${ps.sumOf { it.count }} time${if (ps.sumOf { it.count } == 1) "" else "s"} at $frame",
                "$head; last ${Format.age(ps.maxOf { it.lastAtMs }, now)}. The stack is in 'Recent problems' below.", frame.substringAfterLast('(').substringBefore(':').let { "the file $it (line in the title)" },
                "Reproduce it with a test that makes the same input, fix the cause (not a blanket catch), and keep the test.", weight = 100.0 + ps.sumOf { it.count },
            )
        } + bad.filter { e -> crashes.none { kotlin.math.abs(it.lastAtMs - e.atMs) < 120_000L } }.groupBy { it.reason }.map { (reason, es) ->
            Finding(
                "bug:exit:$reason", "BUG", "Android ended the app ${es.size} time${if (es.size == 1) "" else "s"}: $reason",
                "last ${Format.age(es.maxOf { it.atMs }, now)}, ${es.first().where}" + (es.first().pssMb?.let { ", using $it MB" } ?: "") + (es.first().description?.let { ": ${it.take(100)}" } ?: "") +
                    (es.first().trace.firstOrNull { it.contains("vigilant") }?.let { "; main thread at $it" } ?: ""),
                "'How the app last ended' below (a freeze's main-thread stack)", "A 'not responding' is work on the main thread: find the frame in the stack and move it off (Dispatchers.Default/IO). A memory kill: see the Memory block.", weight = 90.0 + es.size,
            )
        }
    }

    /** Errors the code reported with where they came from ([com.tjshea.vigilant.data.diag.EventLog.error]): the first app frame is the place to open. */
    private fun errorEvents(x: Diagnostics.Extras, now: Long): List<Finding> {
        val errors = x.events.filter { it.level == Level.ERROR && it.where != null && now - it.atMs < 3 * DAY_MS }
        // The frames are class and file names, never text a person typed; masked anyway, since the title, key and trend lines are built from them.
        return errors.groupBy { ProblemLog.clean(EventLog.short(it.where!!)) }.map { (where, es) ->
            val n = es.sumOf { it.n }
            Finding(
                "bug:error:$where", "BUG", "$n caught error${if (n == 1) "" else "s"} at $where",
                "${es.last().cat}: ${ProblemLog.clean(es.last().msg)}; last ${Format.age(es.maxOf { it.lastMs }, now)} (${ProblemLog.clean(es.last().where!!.split(" < ").joinToString(" < ") { EventLog.short(it) })})",
                (EventLog.pathOf(es.last().where!!) ?: where.substringAfter('(').substringBefore(':')).let { "${ProblemLog.clean(it)}, around the line in the title" },
                "Find why it throws here (the message says what), handle that case on purpose, and add a test for it; an error that is caught and logged still means a feature didn't finish.", weight = 70.0 + n,
            )
        }
    }

    /** The same thing, over and over: something failing on every cycle is worth more than one crash. */
    private fun repeats(x: Diagnostics.Extras, now: Long): List<Finding> =
        x.events.filter { it.n >= REPEATS && it.level != Level.INFO && now - it.lastMs < DAY_MS && it.cat != "NET" && it.where == null }.map { e ->
            Finding(
                "repeat:${e.cat}:${e.msg.take(48)}", "FAILURE", "Repeating ${e.level.name.lowercase()}: ${e.cat} '${e.msg.take(90)}' ×${e.n}",
                "first ${Format.age(e.atMs, now)}, last ${Format.age(e.lastMs, now)}", "the area's code (see the code map for ${e.cat})",
                "Something is retrying a failing step without changing anything: find what each retry could change (a backoff, a different source, a stop) and make the cause visible instead of repeating it.",
                weight = e.n.toDouble(),
            )
        }

    // ---- the health checks, as findings ---------------------------------------------------------------------------------

    private fun health(s: UiState, x: Diagnostics.Extras, now: Long): List<Finding> =
        HealthChecks.of(s, x, now).filter { it.level != HealthChecks.Level.OK }.map { c ->
            val kind = if (c.level == HealthChecks.Level.FAIL) "FAILURE" else "WATCH"
            Finding(
                "health:${c.area}:${c.finding.take(60)}", kind, "${c.area}: ${c.finding}", c.evidence ?: "(no further evidence)", c.look ?: "",
                // A failing check is work; a warning is information (and the 'code' line may be something Tj can do, not a file).
                if (c.level == HealthChecks.Level.FAIL) "A failing health check: the evidence says what, 'code' says where to look (or what to change on the phone)." else "",
                weight = if (c.level == HealthChecks.Level.FAIL) 10.0 else 1.0,
            )
        }

    // ---- connections ------------------------------------------------------------------------------------------------------

    /** Where each host's pacing lives, for a rate-limit finding. */
    private val PACE_CODE = mapOf(
        "crazyninjaodds.com" to "data/.../cno/CnoClient.kt (CnoPace) and CnoFeed.kt (the 3 s floor, backoff)",
        "api.novig.com" to "data/.../novig/NovigPublicClient.kt, RateGate.kt and signing/NovigSignedClient.kt (the key's 16 a second)",
        "data.novig.com" to "data/.../tracker/HistoricalCloses.kt (Novig's trade files)",
        "parlay-api.com" to "data/.../reference/TheOddsApiClient.kt (CreditPace) and keys/Usage.kt",
        "pinnwire.com" to "data/.../reference/PinnapiClient.kt (a key rests on 429)",
        "pinnapi.com" to "data/.../reference/PinnapiClient.kt (a key rests on 429)",
        "api.prop-line.com" to "data/.../reference/PropLineClient.kt",
        "api.the-odds-api.com" to "data/.../reference/TheOddsApiClient.kt",
    )

    /** The two hosts [com.tjshea.vigilant.data.cno.DnsOverHttps] asks when the phone's own DNS has failed. */
    private val DOH_HOSTS = setOf("cloudflare-dns.com", "dns.google")

    private fun network(x: Diagnostics.Extras, now: Long): List<Finding> = buildList {
        for ((host, h) in x.net.hosts) {
            val worstPath = h.paths.entries.filter { it.value.calls >= 3 }.maxByOrNull { it.value.errors.toDouble() / it.value.calls }
            val code = PACE_CODE[host] ?: "the client for $host (grep for the host in data/.../)"
            // Calls made with no network at all fail whatever the app does (Tj's v0.52.0 file: the DNS-over-HTTPS fallback's 25 calls, all offline,
            // read as "100% of calls failed"): judged on the calls that had a connection.
            val offline = (h.byNet[NetKind.NONE] ?: 0L).coerceAtMost(h.errors)
            val onlineCalls = h.calls - offline
            val onlineRate = if (onlineCalls <= 0) 0.0 else (h.errors - offline).toDouble() / onlineCalls
            if (onlineCalls >= MIN_CALLS && onlineRate >= 0.10 && host in DOH_HOSTS) {
                // The DNS-over-HTTPS fallback's own resolvers (only asked after the phone's own DNS failed): a VPN or carrier that blocks them is not an app fault.
                add(
                    Finding(
                        "net:$host:errors", "WATCH", "$host: the DNS fallback couldn't reach it (${h.errors - offline} of $onlineCalls tries)",
                        "only asked when the phone's own name lookup failed" + (h.lastError?.let { "; last: ${ProblemLog.clean(it)} ${h.lastErrorAtMs?.let { t -> Format.age(t, now) } ?: ""}" } ?: ""),
                        "data/.../cno/CnoNetwork.kt (DnsOverHttps)",
                        "Not a fault by itself: a VPN or the carrier blocks the resolver. The app stops asking for 2 minutes after both fail.",
                    ),
                )
            } else if (onlineCalls >= MIN_CALLS && onlineRate >= 0.10) {
                val kinds = (h.kinds.entries.map { "${it.value} ${it.key}" } + h.status.entries.filter { (it.key.toIntOrNull() ?: 0) >= 400 }.map { "${it.value}× HTTP ${it.key}" }).joinToString(", ")
                add(
                    Finding(
                        "net:$host:errors", "FAILURE", "$host: ${pct(onlineRate)} of calls failed (${h.errors - offline} of $onlineCalls with a connection)",
                        "$kinds" + (if (offline > 0) "; $offline more failed with no connection (not counted)" else "") + (worstPath?.let { "; worst endpoint ${it.key} (${it.value.errors}/${it.value.calls})" } ?: "") + (h.lastError?.let { "; last: ${ProblemLog.clean(it)} ${h.lastErrorAtMs?.let { t -> Format.age(t, now) } ?: ""}" } ?: ""),
                        code,
                        when {
                            h.kinds["timeout"].let { it != null && it * 3 >= h.errors } -> "Mostly timeouts: check the call's timeout and how many run at once (HttpSupport.MAX_PER_HOST), and whether the caller retries or backs off; speed matters more than data here."
                            h.status.keys.any { it == "429" || it == "403" } -> "Refused with 429/403: the app is calling faster than the host allows; slow the caller down or share one answer between callers."
                            h.status.keys.any { (it.toIntOrNull() ?: 0) in 500..599 } -> "The host's own errors: the app should back off and say so, not hammer; check the caller's retry policy."
                            else -> "Find which step fails (the endpoint above) and whether the app can recover (another source, a retry later) or must say so."
                        },
                        weight = h.errors.toDouble(),
                    ),
                )
            }
            val p95 = h.latency.p95
            if (h.latency.count >= MIN_SAMPLES && p95 >= SLOW_P95_MS) {
                add(
                    Finding(
                        "net:$host:slow", "OPTIMIZE", "$host is slow: half of calls take ${h.latency.p50.toLong()} ms to the first byte, 1 in 20 over ${p95.toLong()} ms",
                        "over the last ${h.latency.count} calls; slowest endpoint " + (h.paths.entries.filter { it.value.calls >= 3 }.maxByOrNull { it.value.totalMs / it.value.calls }?.let { "${it.key} (${it.value.totalMs / it.value.calls} ms average)" } ?: "n/a") +
                            (h.byNet.entries.joinToString(prefix = "; on ", separator = ", ") { "${it.key} ${it.value}" }.takeIf { h.byNet.isNotEmpty() } ?: ""),
                        code, "Speed is the goal here: call this host in parallel where it allows, start its reads earlier (before the other sources finish), or re-use an answer within the freshness limit instead of asking again.", weight = p95 / 1000.0,
                    ),
                )
            }
            if (h.limits >= 3) {
                add(
                    Finding(
                        "net:$host:limits", "OPTIMIZE", "$host asked the app to slow down ${h.limits} times", "${h.lastLimit ?: "429"}; last ${h.lastLimitAtMs?.let { Format.age(it, now) } ?: "?"}",
                        code, "Pace the caller to what the host allows (its Retry-After says how long to wait) and combine requests that ask for the same thing.", weight = h.limits.toDouble(),
                    ),
                )
            }
            val bps = h.speed
            if (bps.count >= 5 && bps.p50 < SLOW_BPS) {
                add(
                    Finding(
                        "net:$host:throughput", "OPTIMIZE", "$host's big answers arrive slowly: ${(bps.p50 / 1024).toLong()} KB/s typical",
                        "over the last ${bps.count} answers of 8 KB or more; 1 in 20 under ${(SampleSummaryLow(h) / 1024).toLong()} KB/s", code,
                        "A scan waits on these bodies: ask for fewer rows or fewer fields only if that does not lose a bet's data; otherwise read it while the next request starts.", weight = 1.0,
                    ),
                )
            }
        }
        // The phone's connection itself: one kind of network failing more than the other.
        val wifi = x.net.hosts.values.sumOf { it.byNet["Wi-Fi"] ?: 0L }
        val mobile = x.net.hosts.values.sumOf { it.byNet["mobile"] ?: 0L }
        if (x.net.hosts.values.sumOf { it.kinds["dns"] ?: 0L } >= 3) {
            add(Finding("net:dns", "WATCH", "Name lookups failed ${x.net.hosts.values.sumOf { it.kinds["dns"] ?: 0L }} times", "calls by network: Wi-Fi $wifi, mobile $mobile", "the phone's connection", "Not the app's code unless every host fails together; check the times of the failures in the timeline."))
        }
    }

    /** "slowest step: CNO read 1 in 20 over 3400 ms (of 4000 ms for a whole cycle)": which part of a cycle to look at. */
    private fun slowestStep(x: Diagnostics.Extras): String {
        val steps = x.perf.filterKeys { it.startsWith("cycle.step.") }.filterValues { it.count >= 5 }
        val worst = steps.maxByOrNull { it.value.p95 } ?: return "per-step timings: none yet"
        return "slowest step: ${worst.key.removePrefix("cycle.step.")} (1 in 20 over ${worst.value.p95.toLong()} ms; typical ${worst.value.p50.toLong()} ms)"
    }

    private fun SampleSummaryLow(h: com.tjshea.vigilant.data.diag.HostStat): Double = h.recentBps.sorted().let { it[(it.size * 0.05).toInt().coerceIn(0, it.size - 1)].toDouble() }

    // ---- timings --------------------------------------------------------------------------------------------------------

    private fun performance(s: UiState, x: Diagnostics.Extras, now: Long): List<Finding> = buildList {
        val cycle = x.perf["cycle.ms"]
        val every = s.settings.autoScanSeconds * 1_000.0
        if (cycle != null && cycle.count >= 8 && cycle.p95 > maxOf(every * 1.5, 20_000.0) && s.settings.autoScan != com.tjshea.vigilant.data.scanner.AutoScanMode.OFF) {
            add(
                Finding(
                    "perf:cycle", "OPTIMIZE", "Background cycles run longer than their interval: typical ${(cycle.p50 / 1000).toLong()} s, 1 in 20 over ${(cycle.p95 / 1000).toLong()} s",
                    "interval ${com.tjshea.vigilant.data.scanner.ScanSettings.intervalLabel(s.settings.autoScanSeconds)}, ${cycle.count} cycles this run; " + slowestStep(x),
                    "app/AutoScan.kt (AutoScanner.cycle: cnoRead, auto-bet, alerts, closing capture, the Vigilant scan)",
                    "Find the step that takes the time (CNO read, each game page's books, the sharp lookup, the Vigilant scan) and overlap it with the others or skip what hasn't changed since the last cycle; an overrun is what makes a 5 s schedule late.",
                    weight = cycle.p95 / 1000.0,
                ),
            )
        }
        val scan = x.perf["scan.ms"]
        if (scan != null && scan.count >= 1 && scan.max >= SLOW_SCAN_MS) {
            add(
                Finding(
                    "perf:scan", "OPTIMIZE", "A Vigilant scan took ${(scan.max / 1000).toLong()} s at its slowest (typical ${(scan.p50 / 1000).toLong()} s)",
                    s.status.timing?.let { com.tjshea.vigilant.data.scanner.ScanTiming.text(it, s.status.booksFetched, s.status.booksViaKey, s.status.booksViaPush, s.status.keyReadPerSec) } ?: "see the Last Vigilant scan block",
                    "data/.../scanner/Scanner.kt, Planner.kt and novig/NovigPublicClient.kt", "Read the timing line: whichever of board / fair odds / Novig prices dominates is the one to speed up.", weight = scan.max / 1000.0,
                ),
            )
        }
        frameFinding(x.frames)?.let(::add)
        x.coldStartMs?.takeIf { it > COLD_START_SLOW_MS }?.let {
            add(Finding("perf:coldstart", "OPTIMIZE", "The screen took $it ms to appear after the process started", "this run only", "app/MainActivity.kt, VigilantApp.kt (AppContainer's eager work)", "Move whatever runs before the first frame (store loads, receivers) off the main thread or after the first frame.", weight = it / 1000.0))
        }
        if (x.memory.fraction >= 0.7) {
            add(Finding("perf:heap", "OPTIMIZE", "The heap is ${Math.round(x.memory.fraction * 100)}% full (${x.memory.usedMb} of ${x.memory.maxMb} MB)", x.memory.lines.take(3).joinToString("; "), "data/.../MemoryGuard.kt and what the Memory block lists", "Release what is held longest (see the Memory block's biggest items); an OutOfMemoryError ends the app.", weight = x.memory.fraction * 10))
        }
    }

    // ---- what the app decided ---------------------------------------------------------------------------------------------

    /** What each skip reason points at in the code (the reasons are the sentences the app writes). */
    private val SKIP_CODE = listOf(
        "no Novig price read" to "app/AutoScan.kt (cnoRead: c.live.readNow) and data/.../cno/NovigLive.kt: the live price wasn't read in the last minute",
        "its exact bet couldn't be found on Novig" to "data/.../cno/NovigBetFinder.kt (matchEvent/matchOutcome): CNO's wording doesn't match Novig's market",
        "not priced at Novig" to "app/ApiBetting.kt (ApiBetTargets.atNovig)",
        "Pinnacle" to "data/.../scanner/SharpConfirm.kt and reference/SharpBooks.kt: the sharp check is what stops it",
        "books say +EV" to "data/.../novig/trading/AutoBet.kt (judge): the criteria are what stop it",
        "its edge" to "data/.../novig/trading/AutoBet.kt (judge): the minimum edge",
    )

    private fun funnels(s: UiState, x: Diagnostics.Extras): List<Finding> = buildList {
        val c = x.counters
        val looked = c["autobet.looked"] ?: 0L
        val passed = c["autobet.passed"] ?: 0L
        val placed = c["autobet.placed"] ?: 0L
        val skips = c.filterKeys { it.startsWith("autobet.skip.") }.mapKeys { it.key.removePrefix("autobet.skip.") }
        val skipTotal = skips.values.sum()
        if (skipTotal >= 20) {
            val (reason, n) = skips.maxByOrNull { it.value }!!
            if (n * 2 >= skipTotal) {
                val where = SKIP_CODE.firstOrNull { reason.contains(it.first, ignoreCase = true) }?.second ?: "data/.../novig/trading/AutoBet.kt and app/AutoBettor.kt"
                add(
                    Finding(
                        "funnel:autobet:top-skip", "IMPROVE", "Auto-bet: ${Math.round(n * 100.0 / skipTotal)}% of the bets it skipped were skipped for one reason: $reason",
                        "$skipTotal skips, $looked bets looked at, $passed passed every criterion, $placed placed (counted since ${x.eventsSinceMs?.let { Format.age(it, System.currentTimeMillis()) } ?: "the file started"})",
                        where, "Decide whether this reason is the criteria working (leave it) or the app failing to see a bet it should (fix the read or the match). Never loosen a safety limit to raise the count.", weight = n.toDouble(),
                    ),
                )
            }
        }
        val sharp = c.filterKeys { it.startsWith("sharp.") }
        val sharpTotal = sharp.values.sum()
        if (sharpTotal >= 10) {
            fun share(v: String) = sharp.filterKeys { it.endsWith(".$v") }.values.sum().toDouble() / sharpTotal
            if (share("UNAVAILABLE") >= 0.3) add(Finding("funnel:sharp:unavailable", "FAILURE", "The sharp-book check couldn't ask in ${pct(share("UNAVAILABLE"))} of its ${sharpTotal} checks", sharp.entries.joinToString(", ") { "${it.key.removePrefix("sharp.")} ${it.value}" }, "data/.../reference/SharpBooks.kt (feeds, caches) and the feeds' keys", "A key's allowance, held-back credits or no feed on: see Diagnostics' feeds line and the net findings."))
            if (share("STALE") >= 0.3) add(Finding("funnel:sharp:stale", "OPTIMIZE", "The sharp check found Pinnacle's quote too old in ${pct(share("STALE"))} of ${sharpTotal} checks", "limit ${s.settings.sharpConfirmMaxAgeSeconds} s; ${x.sharpAnswers.entries.joinToString { "${it.key} ${it.value}" }.ifEmpty { "no feed answered" }}", "data/.../reference/SharpBooks.kt (KEEP_MS, source order) and SharpConfirm.kt", "A board kept a minute can be older than a tight limit by the time it's judged: re-ask sooner for a bet about to be placed, or prefer the feed whose quote carries its own time."))
            if (share("NO_QUOTE") >= 0.5) add(Finding("funnel:sharp:noquote", "IMPROVE", "Pinnacle had no price for the exact bet in ${pct(share("NO_QUOTE"))} of ${sharpTotal} checks", sharp.entries.joinToString(", ") { "${it.key.removePrefix("sharp.")} ${it.value}" }, "data/.../tracker/ParlayBooks.kt (viewOf: pairFor) and BetGrader.pickOf", "Check whether the matcher fails on markets Pinnacle does carry (names, lines, periods) before accepting that Pinnacle simply doesn't list them."))
        }
        // Unmatched games by league: the matcher is where fair odds are lost.
        Diagnostics.matchingByLeague(s).filter { it.games >= 5 && it.matched * 10 < it.games * 7 }.forEach { l ->
            add(Finding("match:${l.league}", "IMPROVE", "${l.league}: only ${l.matched} of ${l.games} games matched to fair odds", "unmatched, e.g.: ${l.unmatched.take(4).joinToString(" | ")}", "data/.../match/TeamMatcher.kt and the league's sources", "Compare these names with the sources' names for the same games and teach the matcher (aliases, start-time slack); each game left out is a bet never priced.", weight = (l.games - l.matched).toDouble()))
        }
    }

    // ---- the system's own words, the app's files --------------------------------------------------------------------------

    private fun logcat(x: Diagnostics.Extras): List<Finding> {
        val lines = x.logcat
        if (lines.isEmpty()) return emptyList()
        val jank = lines.filter { it.text.contains("Skipped") && it.text.contains("frames") || it.text.contains("Davey") }
        return buildList {
            if (jank.size >= 2) {
                add(Finding("logcat:jank", "OPTIMIZE", "Android reported dropped frames ${jank.size} times", jank.last().toString().take(160), "the screen in use at those times (see the timeline)", "Load the compose-performance skill: look for work in composition or on the main thread while that screen is open."))
            }
            val byTag = lines.filter { it.level == 'E' }.groupBy { it.tag }.filterKeys { it !in QUIET_TAGS }
            byTag.entries.sortedByDescending { it.value.size }.take(3).forEach { (tag, ls) ->
                add(Finding("logcat:error:$tag", "WATCH", "The app's log has ${ls.size} error line${if (ls.size == 1) "" else "s"} from '$tag'", ls.last().toString().take(180), "whatever logs as $tag", "Read the lines in the App log block; an error from a library often names the call that misused it."))
            }
        }
    }

    private val QUIET_TAGS = setOf("AndroidRuntime")

    private fun storage(x: Diagnostics.Extras): List<Finding> {
        val total = x.storage.sumOf { it.second }
        return buildList {
            // A document store (a .json file) is rewritten WHOLE on every change, so its size is app-wide lag, not just disk (Tj, 2026-10-10: maker.json reached 15 MB, flagged only at 20, and the
            // app lagged): over 4 MB is an OPTIMIZE. A recorder folder over its [DataKeeper] cap means the housekeeping is not running: a BUG.
            x.storage.filter { it.first.endsWith(".json") && it.second >= BIG_DOCUMENT }.forEach { (name, bytes) ->
                add(Finding("storage:$name", "OPTIMIZE", "$name is ${bytes / 1_048_576} MB and is rewritten whole on every change", "the app's files total ${total / 1_048_576} MB; a big document rewritten often is garbage-collector load felt as lag everywhere", "the store that writes $name (JsonFileStore)", "Prune what it keeps (the oldest ended records) or move the bulk to an append-only day journal."))
            }
            com.tjshea.vigilant.data.diag.DataKeeper.RULES.forEach { rule ->
                val bytes = x.storage.firstOrNull { it.first == rule.dir + "/" }?.second ?: return@forEach
                if (bytes > rule.maxBytes * 3 / 2) add(Finding("storage:${rule.dir}", "BUG", "${rule.dir}/ holds ${bytes / 1_048_576} MB, over its ${rule.maxBytes / 1_048_576} MB cap", "DataKeeper sweeps every ${com.tjshea.vigilant.data.diag.DataKeeper.EVERY_MS / 3_600_000L} h and should have trimmed it", "data/diag/DataKeeper.kt, the sweep loop in VigilantApp's init", "Find why the sweep did not run or could not delete (a read-only file, an exception swallowed by runCatching)."))
            }
            x.storage.filter { it.second >= BIG_FILE && it.first.endsWith("/").not() && !it.first.endsWith(".json") }.forEach { (name, bytes) ->
                add(Finding("storage:$name", "WATCH", "$name is ${bytes / 1_048_576} MB", "the app's files total ${total / 1_048_576} MB", "the store that writes $name", "Storage is not a constraint, but a file that keeps growing is a leak: check that the store prunes it."))
            }
        }
    }

    // ---- small things ---------------------------------------------------------------------------------------------------

    private fun pct(v: Double) = String.format(Locale.US, "%.0f%%", v * 100)

    private const val MIN_CALLS = 15
    private const val MIN_SAMPLES = 20
    private const val SLOW_P95_MS = 3_000.0
    private const val SLOW_BPS = 60.0 * 1024
    /**
     * The screen stutters while something runs (Tj, 2026-10-02: "When I scan with vigilant scanner, the entire app becomes laggy still"): enough
     * of its frames slow ([SLOW_FRAMES_SHARE]) and clearly more than with nothing running, from enough frames to say ([MIN_FRAMES]).
     */
    internal fun frameFinding(frames: Map<String, com.tjshea.vigilant.data.diag.FrameStats.Bucket>): Finding? {
        val quiet = frames[com.tjshea.vigilant.data.diag.FrameStats.QUIET]?.takeIf { it.frames >= MIN_FRAMES }
        val worst = frames.filterKeys { it != com.tjshea.vigilant.data.diag.FrameStats.QUIET }.entries
            .filter { (_, b) -> b.frames >= MIN_FRAMES && b.slowShare >= SLOW_FRAMES_SHARE && (quiet == null || b.slowShare >= quiet.slowShare * 2) }
            .maxByOrNull { it.value.slowShare } ?: return null
        val (what, b) = worst
        val pct = { s: Double -> "${Math.round(s * 1000) / 10.0}%" }
        return Finding(
            "perf:frames:${FRAME_KEYS[what] ?: what}", "OPTIMIZE",
            "The screen stutters with $what: ${pct(b.slowShare)} of its frames are slow" + (quiet?.let { ", ${pct(it.slowShare)} with nothing running" } ?: ""),
            "${b.frames} frames, ${b.slow} slow, ${b.frozen} frozen, p95 ${b.durations.p95.toLong()} ms, worst ${b.durations.max.toLong()} ms (Performance block)",
            "what that work does on the main thread or per state it publishes: app/MainViewModel.kt (follow, the mirrors), ScanService.kt, the screen it was on",
            "Find what runs on the main thread, or recomposes, each time that work publishes; move it off or publish less often. Never drop data to do it.",
            weight = b.slowShare * 20,
        )
    }

    private val FRAME_KEYS = mapOf(
        com.tjshea.vigilant.data.diag.FrameStats.SCAN to "scan", com.tjshea.vigilant.data.diag.FrameStats.CHECK to "check",
        com.tjshea.vigilant.data.diag.FrameStats.CNO to "cno", com.tjshea.vigilant.data.diag.FrameStats.QUIET to "quiet",
    )
    private const val MIN_FRAMES = 300L
    private const val SLOW_FRAMES_SHARE = 0.05

    private const val SLOW_SCAN_MS = 180_000.0
    private const val COLD_START_SLOW_MS = 2_500L
    private const val REPEATS = 20
    private const val BIG_FILE = 20L * 1_048_576
    private const val BIG_DOCUMENT = 4L * 1_048_576
}
