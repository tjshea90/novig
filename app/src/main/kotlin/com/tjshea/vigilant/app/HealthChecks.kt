package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.app.ui.TrackerText
import com.tjshea.vigilant.data.keys.Runway
import com.tjshea.vigilant.data.keys.RunwayLevel
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.KeepAwake
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import java.util.Locale

/**
 * Diagnostics' first block (Tj, 2026-09-30: "make the diagnostics section in settings as smart as possible so that when I output it to Claude,
 * Claude can run deep analysis on the app and know what is working or broken and how to improve the app either in code or ui or scanning or
 * accuracy or function"): every part of the app judged OK, WARN or FAIL, each with the evidence behind it and the code that owns it, the
 * worst first. Pure: the state and extras go in, checks come out; the numbers behind each one are in the report's other blocks.
 */
object HealthChecks {

    enum class Level { FAIL, WARN, OK }

    /** One judgement: [area] and what was found, the [evidence], and where to look ([look]: the code, or what Tj can do). */
    data class Check(val level: Level, val area: String, val finding: String, val evidence: String? = null, val look: String? = null) {
        fun text(): String = "${level.name.padEnd(4)} $area: $finding" + (evidence?.let { " [$it]" } ?: "") + (look?.let { " → $it" } ?: "")
    }

    fun of(s: UiState, x: Diagnostics.Extras, now: Long): List<Check> = buildList {
        val set = s.settings
        scanning(s, now)
        sources(s)
        apis(s, x, now)
        keyRoute(s, x, now)
        if (set.cnoOn && !set.paused) cno(s, now)
        background(s, x, now)
        phone(s, x, now)
        tracker(s, x, now)
        accuracy(s, now)
        betting(s)
        autoBet(s, x, now)
        pinnacleOnly(s, x, now)
        lowUsage(s, x)
        bids(s, x, now)
        sharp(s, x)
        memory(x)
    }.sortedBy { it.level.ordinal }

    /**
     * Pinnacle only (Tj, 2026-10-05; RESEARCH.md §88.5): on, it must be pricing from Pinnacle and reading nothing else. Said when the last scan priced nothing from a Pinnacle
     * source, when it read a source Pinnacle only never asks (a bug, or a scan from before the switch), when the re-reads before betting fail more than they work, and when
     * the auto-bet's last pass refused most bets for an old Pinnacle price.
     */
    private fun MutableList<Check>.pinnacleOnly(s: UiState, x: Diagnostics.Extras, now: Long) {
        val set = s.settings
        if (!set.pinnacleOnly) return
        if (set.vigilantOn && s.status.scannedAtMs != null) {
            val reads = s.status.sources.filter { it.fetched + it.reused > 0 }
            val pinnacleSources = setOf("pinnacle", "propline", "propline_props", "parlay", "parlay_1h", "parlay_props")
            if (reads.none { it.id in pinnacleSources && it.matched > 0 }) {
                add(Check(Level.FAIL, "Pinnacle only", "the last scan priced no Novig bet from Pinnacle", reads.joinToString(", ") { "${it.name} ${it.matched} matched" }.ifEmpty { "no source answered" }.take(160), "Settings › API keys: a PinnWire or pinnapi key (or PropLine / ParlayAPI as the backup)"))
            }
            val other = reads.filter { it.id !in pinnacleSources }
            if (other.isNotEmpty()) {
                add(Check(Level.WARN, "Pinnacle only", "the last scan read ${other.joinToString(", ") { it.name }}, which Pinnacle only never asks for (a scan from before the switch, or a bug)", look = "data/scanner/Scanner.kt (readable), app/VigilantApp.kt (pinnacleOnlySources)"))
            }
        }
        val ok = x.counters["pinnacle.refresh.ok"] ?: 0L
        val failed = x.counters["pinnacle.refresh.failed"] ?: 0L
        if (failed >= 3 && failed > ok) {
            add(Check(Level.WARN, "Pinnacle only", "the re-reads of Pinnacle's price before betting fail more than they work", "$ok read, $failed failed", "PinnWire / pinnapi daily limits (API usage), data/reference/PinnapiClient.kt"))
        }
        if (set.autoBet) {
            val last = x.autoBet.last
            val oldSkips = last.skipped["Pinnacle's price is older than your limit"] ?: 0
            if (last.looked >= 3 && oldSkips * 2 >= last.looked) {
                add(Check(Level.WARN, "Pinnacle only", "the last auto-bet pass refused $oldSkips of ${last.looked} bets for a Pinnacle price older than your limit", "re-reads failing, or the limit (${set.pinnacleMaxAgeSeconds} s) is tighter than the feeds can keep", "Settings › Scanning › Pinnacle only"))
            }
        }
    }

    /**
     * Low API usage bids (Tj, 2026-10-05; RESEARCH.md §92): on, the picked books must be readable (a feed that carries each with a key and a switch on), the last scan must
     * have priced props from them and read nothing else, and at least one feed must be asked at all.
     */
    private fun MutableList<Check>.lowUsage(s: UiState, x: Diagnostics.Extras) {
        val set = s.settings
        if (!set.lowUsageNow) return
        val plan = x.lowUsagePlan
        if (plan != null && plan.feeds.isEmpty()) {
            // Nothing is asked, so nothing the last scan did can be judged: this is the one finding.
            add(Check(Level.FAIL, "Low API usage bids", "no feed can be asked for the picked books", com.tjshea.vigilant.data.scanner.LowUsageBids.names(com.tjshea.vigilant.data.scanner.LowUsageBids.books(set)), "Settings › Fair odds & sources and API keys"))
            return
        } else if (plan != null && plan.unreachable.isNotEmpty()) {
            add(Check(Level.WARN, "Low API usage bids", "${com.tjshea.vigilant.data.scanner.LowUsageBids.names(plan.unreachable.toSet())} can't be read, so a fair needs the other picked books", "no feed that carries it has a key and a switch on", "Settings › API keys, or pick other books on the Bids tab"))
        }
        if (set.vigilantOn && s.status.scannedAtMs != null && plan != null) {
            val reads = s.status.sources.filter { it.fetched + it.reused > 0 }
            val asked = plan.feeds.toSet()
            val other = reads.filter { it.id !in asked && it.id != "propline" }
            if (other.isNotEmpty()) {
                add(Check(Level.WARN, "Low API usage bids", "the last scan read ${other.joinToString(", ") { it.name }}, which the mode never asks for (a scan from before the switch, or a bug)", look = "data/scanner/Scanner.kt (readable), app/VigilantApp.kt (lowUsageSources)"))
            }
            // Every league skipped (no game with a prop market on Novig inside the window): nothing to read is not a failure to read (Tj's 8:41 PM scan, RESEARCH.md §94).
            val nothingInWindow = reads.none { it.id in asked } && s.status.sources.any { it.id in asked && it.skipped > 0 }
            if (nothingInWindow) {
                add(Check(Level.OK, "Low API usage bids", "nothing to read: no game with a prop market on Novig starts in the next ${com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(set)} h, so no feed was asked and nothing was spent"))
            } else if (reads.none { it.id in asked && it.matched > 0 }) {
                add(Check(Level.WARN, "Low API usage bids", "the last scan priced no prop from the picked books", reads.joinToString(", ") { "${it.name} ${it.matched} matched" }.ifEmpty { "no feed answered (no game in the next ${com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(set)} h, or the feeds are down)" }.take(160), "Settings › API usage"))
            }
        }
    }

    /**
     * The bids (Tj, 2026-10-05: "my bids right now are being taken fast and I'm worried they aren't true positive Ev"; RESEARCH.md §88.3): stopped by the picked-off
     * guard; fills being picked off short of the guard's line; fills that lose to the close once enough have one; and a rush of fills inside two minutes.
     */
    private fun MutableList<Check>.bids(s: UiState, x: Diagnostics.Extras, now: Long) {
        val set = s.settings
        if (!set.maker && x.makerBids.none { it.filled > 0 }) return
        val rows = com.tjshea.vigilant.data.novig.trading.maker.BidReport.rows(x.makerBids, s.bets, now, set.makerAnchorSharp)
        val fills = rows.filter { it.filled > 0 }
        set.makerHalted?.let { add(Check(Level.WARN, "Bids", "stopped by the picked-off guard", it, "Bids tab › Resume bids (MakerRunner.guard)")) }
        if (fills.isEmpty()) return
        val guard = com.tjshea.vigilant.data.novig.trading.maker.MakerGuard.check(x.makerBids, set.makerGuardFromMs, set.makerAnchorSharp)
        if (guard.judged >= 4 && guard.pickedOff * 3 >= guard.judged && set.makerHalted == null) {
            add(Check(Level.WARN, "Bids", "fills are being picked off", guard.text + if (set.makerGuard) "" else " (the guard is off)", "Settings › Bids › Stop bids when fills are picked off"))
        }
        val closed = fills.mapNotNull { it.clv }
        if (closed.size >= 20) {
            val avg = closed.average()
            add(
                Check(
                    if (avg < -0.01) Level.FAIL else if (avg < 0.0) Level.WARN else Level.OK, "Bids",
                    if (avg < 0.0) "bid fills lose to the close" else "bid fills beat the close",
                    "${closed.size} fills with a close: CLV ${String.format(Locale.US, "%+.1f%%", avg * 100)}, ${Math.round(100.0 * closed.count { it > 0 } / closed.size)}% beat it",
                    "Diagnostics › Bids (BidReport)",
                ),
            )
        }
        val fast = fills.mapNotNull { it.fillDelaySec }
        if (fast.size >= 6 && fast.count { it < 120 } * 2 >= fast.size) {
            // A bid rests only a few minutes, so most fills are quick whatever the market does (Tj's v0.70.1 file: 3 of 6 fired this warning while its fast fills beat the close by more than its slow
            // ones). It is a warning only when the quick fills did worse than the slow ones against the close; the picked-off check above is the one that judges a fill by the market's move.
            val quick = fills.filter { (it.fillDelaySec ?: Long.MAX_VALUE) < 120 }.mapNotNull { it.clv }
            val slow = fills.filter { (it.fillDelaySec ?: 0L) >= 120 }.mapNotNull { it.clv }
            val worse = quick.size >= 4 && quick.average() < (if (slow.size >= 2) slow.average() else 0.0) - 0.01
            add(
                Check(
                    if (worse) Level.WARN else Level.OK, "Bids", "most fills came within 2 minutes of posting",
                    "${fast.count { it < 120 }} of ${fast.size}" + if (worse) ": and they did worse against the close than the slower ones: a bid taken that fast is often one the market was already moving away from" else ": normal while a bid rests only a few minutes; the fast ones did no worse than the slow ones against the close",
                    if (worse) "Diagnostics › Bids › fills by how fast they were taken" else null,
                ),
            )
        }
    }

    private fun MutableList<Check>.scanning(s: UiState, now: Long) {
        val set = s.settings
        if (set.killed) add(Check(Level.WARN, "Kill switch", "the STOP button is ON: nothing scans, bets or bids until RESUME on the red bar is tapped", look = "ScanSettings.killed (KillSwitch)"))
        else if (set.paused) add(Check(Level.WARN, "Scanning", "all scanning is paused", look = "Settings › Scanning › Pause (ScanSettings.paused)"))
        if (!set.vigilantOn) {
            add(Check(Level.OK, "Vigilant scanner", "asleep: the scanner is on CNO only", look = "ScanSettings.scanner"))
            return
        }
        val st = s.status
        val at = st.scannedAtMs
        if (at == null) {
            add(Check(Level.WARN, "Vigilant scan", "no scan since the app opened, so there are no scan numbers below", look = "tap Scan on the +EV tab, then copy Diagnostics again"))
            return
        }
        val age = now - at
        if (age > 3 * HOUR) add(Check(Level.WARN, "Vigilant scan", "the last scan is ${Format.age(at, now)} old", look = "tap Scan, or check the background auto-scan below"))
        if (st.errors.isNotEmpty()) {
            val novig = st.errors.any { NOVIG_REFUSED.containsMatchIn(it) }
            val passing = st.errors.all(::transient)
            add(
                Check(
                    when { novig -> Level.FAIL; passing -> Level.OK; else -> Level.WARN }, "Vigilant scan",
                    if (passing) "${st.errors.size} passing error${plural(st.errors.size)} in the last scan (a source busy; it was retried once and is asked again next scan)"
                    else "${st.errors.size} error${plural(st.errors.size)} in the last scan",
                    shortError(st.errors.first()), if (passing) null else "data/scanner/Scanner.kt; Recent problems below",
                ),
            )
        }
        st.backoffSeconds?.let { add(Check(Level.WARN, "Novig", "Novig asked the app to wait $it s", look = "data/novig/RateGate.kt (the key's limits: NOVIG_API.md)")) }
        val stats = s.result?.stats
        val byLeague = Diagnostics.matchingByLeague(s)
        val games = byLeague.sumOf { it.games }
        if (games >= 5) {
            val matched = byLeague.sumOf { it.matched }
            val share = matched.toDouble() / games
            // The leagues that hold the unmatched games, worst first: which sources cover a league is what decides it.
            val gaps = byLeague.filter { it.matched < it.games }.sortedByDescending { it.games - it.matched }
            val level = if (share < 0.5) Level.WARN else Level.OK
            add(
                Check(
                    level, "Game matching", "${pct0(share)} of Novig's games matched to a fair-odds source",
                    "$matched of $games" + (gaps.takeIf { it.isNotEmpty() }?.joinToString(", ", "; unmatched: ") { "${it.league} ${it.games - it.matched} of ${it.games}" } ?: "") +
                        (stats?.let { st2 -> (st2.novigEvents - games).takeIf { it > 0 }?.let { "; $it futures left out" } } ?: ""),
                    if (level == Level.WARN) "the sources that carry those leagues (tennis: Kalshi, Pinnacle, and ParlayAPI when it's on); data/match/TeamMatcher.kt" else null,
                ),
            )
        }
        if (stats != null && st.booksFetched >= 20) {
            val priced = stats.marketsPriced.toDouble() / st.booksFetched
            if (priced < 0.3) add(Check(Level.WARN, "Scan budget", "only ${pct0(priced)} of the Novig prices read had a fair line to judge them by", "${stats.marketsPriced} lines priced of ${st.booksFetched} prices read", "data/scanner/Planner.kt: reads lines no source prices"))
            else add(Check(Level.OK, "Scan budget", "${pct0(priced)} of the Novig prices read were judged against a fair line", "${stats.marketsPriced} of ${st.booksFetched}"))
        }
        st.timing?.let { t ->
            if (t.totalMs > 120_000) add(Check(Level.WARN, "Scan speed", "the last scan took ${t.totalMs / 1000} s", "fair odds ${(t.fairAtMs - t.boardAtMs).coerceAtLeast(0) / 1000} s; Novig refused ${t.refused}", "data/scanner/ScanTiming.kt; the slowest source in Last Vigilant scan"))
            if (t.refused > 3) add(Check(Level.WARN, "Novig", "Novig refused ${t.refused} price reads in the last scan", look = "data/novig/RateGate.kt, NovigSource.batchSize"))
            if (t.leftTooLate > 0) add(Check(Level.WARN, "Scan speed", "${t.leftTooLate} lines were left for the next scan: read later, their books' odds would have been too old", look = "Scanner.BookPump.canStillShow; a smaller scan or a faster source"))
        }
    }

    /**
     * The key route's stand-downs (RESEARCH.md §81.1): each one sends the scan to the public routes (a third of the pace, halved again by a carrier's 429s)
     * for minutes. Not a 451 (Novig's verdict on the carrier's address, which Tj can only wait out) is a bug in the app or a Novig refusal that deserves a line.
     */
    private fun MutableList<Check>.keyRoute(s: UiState, x: Diagnostics.Extras, now: Long) {
        if (s.novig.connection == null) return
        x.keyDownNow?.let { add(Check(Level.WARN, "Novig key route", "standing down now: reads are on the public routes (a third of the pace)", it.take(160), "data/novig/NovigPublicClient.kt (standDown, keyRetryAfter)")) }
        val recent = x.keyStanddowns.filter { now - it.atMs <= 6 * HOUR }
        if (recent.isNotEmpty() && x.keyDownNow == null) {
            val byWhy = recent.groupingBy { it.why }.eachCount().entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ×${it.value}" }
            add(Check(Level.WARN, "Novig key route", "stood down ${recent.size} time${plural(recent.size)} in the last 6 h", byWhy.take(160), "data/novig/NovigPublicClient.kt (standDown); the timeline's NOVIG lines say when"))
        }
    }

    private fun MutableList<Check>.sources(s: UiState) {
        if (!s.settings.vigilantOn || s.status.scannedAtMs == null) return
        for (r in s.status.sources) {
            val backup = r.id in BACKUP_SOURCES
            when {
                // Busy for one league while it answered others: a passing miss, retried once already and asked again next scan.
                r.error != null && (transient(r.error!!) || r.fetched + r.reused > 0) ->
                    add(Check(Level.WARN, "Source ${r.name}", "missed a league in the last scan (answered ${r.fetched + r.reused})", shortError(r.error!!), "retried once in the scan; if it repeats, data/reference/ (its client)"))
                r.error != null -> add(Check(Level.FAIL, "Source ${r.name}", "failed in the last scan", shortError(r.error!!), "data/reference/ (its client); keys in API usage"))
                // The Odds API backs up PropLine: it reads its free game list and buys only what PropLine missed, so nothing matched is normal.
                backup && r.matched == 0 -> add(Check(Level.OK, "Source ${r.name}", "backup: asked only for what PropLine missed, and nothing was"))
                r.fetched > 0 && r.matched == 0 -> add(Check(Level.WARN, "Source ${r.name}", "answered ${r.fetched} league${plural(r.fetched)} but matched no Novig game", look = "data/match/TeamMatcher.kt, PlayerNames.kt"))
                r.heldBack != null -> add(Check(Level.OK, "Source ${r.name}", "held back to save credits", r.heldBack!!.take(120)))
                r.fetched + r.reused > 0 -> add(Check(Level.OK, "Source ${r.name}", "${r.matched} game${plural(r.matched)} matched"))
            }
        }
    }

    private fun MutableList<Check>.apis(s: UiState, x: Diagnostics.Extras, now: Long) {
        for ((id, p) in s.usage.providers) {
            val name = com.tjshea.vigilant.data.keys.QuotaPolicy.ALL.firstOrNull { it.id == id }?.displayName ?: id
            val policy = com.tjshea.vigilant.data.keys.QuotaPolicy.ALL.firstOrNull { it.id == id }
            fun spent(u: com.tjshea.vigilant.data.keys.KeyUsage) = u.depletedUntil?.let { it > now } == true
            p.keys.forEach { (key, u) ->
                if (u.refused) add(Check(Level.FAIL, "API $name", "key …${key.takeLast(4)} was refused", u.lastNote?.take(120), "Settings › Fair odds & sources: replace it"))
                else u.depletedUntil?.takeIf { it > now }?.let { until ->
                    // Another key with credits left carries on: a spent key is only worth a warning when it was the last one.
                    val others = p.keys.filter { (k, o) -> k != key && !o.refused && !spent(o) && (policy?.let { pol -> o.left(pol) } ?: 1) > 0 }
                    add(
                        Check(
                            if (others.isEmpty()) Level.WARN else Level.OK, "API $name",
                            "key …${key.takeLast(4)} is spent until its reset in ${inTime(until - now)}" + if (others.isEmpty()) "" else "; ${if (others.size == 1) "the other key carries" else "the other keys carry"} on",
                            look = if (others.isEmpty()) "add a key, or wait for its reset" else null,
                        ),
                    )
                }
            }
            if (p.throttledToday > 0) {
                // A few refusals in thousands of calls is the pacing working; many, or one in the last hour, is worth a look.
                val recent = p.lastThrottleMs?.let { now - it < HOUR } == true
                val many = p.throttledToday * 100 > p.callsToday.coerceAtLeast(1)
                add(
                    Check(
                        if (recent || many) Level.WARN else Level.OK, "API $name", "${p.throttledToday} call${plural(p.throttledToday)} throttled or refused today",
                        "${p.callsToday} calls today" + (p.lastThrottleMs?.let { "; the last ${Format.age(it, now)}" } ?: ""),
                        if (recent || many) "its pacing in data/keys/ (QuotaPolicy, KeyPool)" else null,
                    ),
                )
            }
        }
        x.parlayAccounts.forEach { (key, a) ->
            if (a.valid == false) add(Check(Level.FAIL, "API ParlayAPI", "key …${key.takeLast(4)} is not valid" + (a.reason?.let { ": $it" } ?: ""), look = "parlay-api.com account; Settings › Fair odds & sources"))
        }
        val views = com.tjshea.vigilant.app.ui.meterViews(s, now).filter { it.policy.keyed }
        for (line in Runway.lines(views, now)) {
            when (line.level) {
                RunwayLevel.SHORT -> add(Check(Level.WARN, "Runway ${line.name}", "won't last to its reset at this pace", line.text, "add a key, or scan less often (Runway block)"))
                RunwayLevel.WATCH -> add(Check(Level.OK, "Runway ${line.name}", "tight but lasting", line.text.take(120)))
                RunwayLevel.OK -> {}
            }
        }
    }

    private fun MutableList<Check>.cno(s: UiState, now: Long) {
        val c = s.cno
        when {
            c.errors >= 3 -> add(Check(Level.FAIL, "CrazyNinjaOdds", "${c.errors} reads in a row failed", c.error?.take(160), "data/cno/CnoClient.kt, CnoNetwork.kt"))
            c.errors > 0 -> add(Check(Level.WARN, "CrazyNinjaOdds", "the last read failed", c.error?.take(160), "data/cno/CnoClient.kt"))
            c.snapshot == null -> add(Check(Level.WARN, "CrazyNinjaOdds", "its list hasn't been read since the app opened", look = "open the CNO tab, then copy Diagnostics again"))
            else -> add(Check(Level.OK, "CrazyNinjaOdds", "read ${Format.age(c.snapshot!!.fetchedAtMs, now)}, ${c.snapshot!!.rows.size} rows"))
        }
        c.pausedUntilMs?.takeIf { it > now }?.let { add(Check(Level.WARN, "CrazyNinjaOdds", "reading is paused: CNO asked the app to wait", c.lastPause?.take(120), "CnoFeed's backoff")) }
    }

    private fun MutableList<Check>.background(s: UiState, x: Diagnostics.Extras, now: Long) {
        val set = s.settings
        if (set.autoScan == AutoScanMode.OFF) {
            add(Check(Level.OK, "Background auto-scan", "off", look = "Settings › Scanning › Background scan (no +EV alerts while Vigilant is closed)"))
            return
        }
        if (set.activeAutoScan == AutoScanMode.OFF) {
            add(Check(Level.WARN, "Background auto-scan", "set to ${set.autoScan.displayName} but runs nothing: ${Diagnostics.runsText(set)}", look = "the scanner choice or Pause"))
            return
        }
        val a = x.autoScan
        if (!x.autoScanServiceRunning && !a.running) {
            add(Check(Level.FAIL, "Background auto-scan", "on (${Diagnostics.runsText(set)} every ${ScanSettings.intervalLabel(set.autoScanSeconds)}) but its service isn't running", "last started ${a.lastStartMs?.let { Format.age(it, now) } ?: "never"}", "app/AutoScanService.kt, AutoScanAlarm.kt; battery and notification permissions below"))
        }
        // Three intervals, but never under 3 minutes: a 15 s schedule's cycle can wait on a slow page, and Vigilant's own scan runs ~2 minutes.
        val lateAfterSeconds = maxOf(3 * set.autoScanSeconds, 180)
        val late = a.lastStartMs?.let { now - it > lateAfterSeconds * 1_000L } ?: true
        if (late && x.autoScanServiceRunning) add(Check(Level.WARN, "Background auto-scan", "no cycle in over ${ScanSettings.intervalLabel(lateAfterSeconds)}", "last started ${a.lastStartMs?.let { Format.age(it, now) } ?: "never"}", "AutoScanAlarm (exact alarms), battery restrictions"))
        a.lastError?.let { add(Check(Level.WARN, "Background auto-scan", "the last cycle had an error", it.take(160), "app/AutoScan.kt")) }
        if (!late && a.lastError == null && x.autoScanServiceRunning) add(Check(Level.OK, "Background auto-scan", "running: last cycle ${a.lastStartMs?.let { Format.age(it, now) }}, found ${a.lastFound}, alerts ${a.lastAlerts}"))
        keepAwake(set, x, now)
    }

    /** Whether the schedule survives the phone idling with the screen off (Tj, 2026-10-02): the switch, the lock, and the record of late cycles. */
    private fun MutableList<Check>.keepAwake(set: ScanSettings, x: Diagnostics.Extras, now: Long) {
        if (!set.autoScanKeepAwake && set.autoScanSeconds < KeepAwake.ALARM_ONLY_BELOW_SECONDS) {
            add(
                Check(
                    Level.WARN, "Background auto-scan", "Keep awake is off: with the screen off and the phone still, Android may run alarm-driven scans only about every 9 minutes, not every ${ScanSettings.intervalLabel(set.autoScanSeconds)}",
                    look = "Settings › Scanning › Keep awake",
                ),
            )
        }
        if (KeepAwake.active(set) && x.autoScanServiceRunning && !x.keepAwakeHeld) {
            add(Check(Level.WARN, "Background auto-scan", "Keep awake is on but the service isn't holding the CPU awake", look = "app/AutoScanService.kt (holdKeepAwake)"))
        }
        val late = com.tjshea.vigilant.data.diag.CycleLog.lateWithin(x.cycles, now, 24 * HOUR)
        if (late.isNotEmpty()) {
            val worst = late.maxByOrNull { it.lateMs }!!
            add(
                Check(
                    Level.WARN, "Background auto-scan", "${late.size} cycle${plural(late.size)} started late in the last day (worst ${com.tjshea.vigilant.data.diag.CycleLog.span(worst.lateMs)}, ${Format.age(worst.atMs, now)})",
                    "${if (worst.dozing) "Doze was on" else if (worst.screenOff) "the screen was off" else "the screen was on"} then; Cycle record in the Background auto-scan block lists them",
                    "Keep awake switch, Android battery 'Unrestricted', How the app last ended (a killed process shows as a late cycle)",
                ),
            )
        } else if (x.cycles.screenOffCycles > 0) {
            add(
                Check(
                    Level.OK, "Background auto-scan",
                    "kept its schedule with the screen off: ${x.cycles.screenOffCycles} cycle${plural(x.cycles.screenOffCycles)} (${x.cycles.dozeCycles} in Doze), none late",
                ),
            )
        }
    }

    private fun MutableList<Check>.phone(s: UiState, x: Diagnostics.Extras, now: Long) {
        // Crashes, freezes and kills for memory in the last day (Android's own record: AppExits).
        val day = x.exits.filter { now - it.atMs < 24 * HOUR }
        val bad = day.filter { it.bad }
        // Android freeing a cached Vigilant (nothing running) is what it does to any app in the background: said, never a failure.
        val reclaimed = day.count { it.reclaimed }
        val reclaimedText = if (reclaimed > 0) "; Android also freed Vigilant's memory $reclaimed time${plural(reclaimed)} while it sat cached in the background (normal, costs a cold start)" else ""
        if (bad.isNotEmpty()) {
            val last = bad.maxByOrNull { it.atMs }!!
            // Only before this version was installed: the version running now hasn't done it (yet).
            val since = x.installedAtMs?.let { at -> bad.count { it.atMs >= at } } ?: bad.size
            add(
                Check(
                    if (since > 0) Level.FAIL else Level.WARN, "App stability",
                    "the app ended badly ${bad.size} time${plural(bad.size)} in the last day (${bad.groupingBy { it.reason }.eachCount().entries.joinToString { "${it.value} ${it.key}" }})" +
                        (if (since < bad.size) ", ${bad.size - since} before this version was installed" else ""),
                    "the last ${Format.age(last.atMs, now)}, ${last.where}" + (last.description?.takeIf { it.isNotBlank() }?.let { ": ${com.tjshea.vigilant.data.diag.ProblemLog.clean(it).take(140)}" } ?: "") + reclaimedText,
                    "How the app last ended (a freeze's main-thread stack) and Recent problems (a crash's stack) below",
                ),
            )
        } else if (x.exits.isNotEmpty()) {
            add(Check(Level.OK, "App stability", "no crash, freeze or memory kill while it was working in the last day" + reclaimedText))
        }
        val p = x.phone
        val set = s.settings
        if (p.notifications == false) {
            add(Check(if (set.alertMinEv > 0 && set.autoScan != AutoScanMode.OFF) Level.FAIL else Level.WARN, "Phone", "notifications are off for Vigilant: no +EV alerts, no scan-done notes", look = "Android Settings › Apps › Vigilant › Notifications"))
        }
        if (p.exactAlarms == false) add(Check(Level.WARN, "Phone", "exact alarms aren't allowed: the closing-line capture and auto-scan can run late", look = "Android Settings › Apps › Special access › Alarms & reminders"))
        if (p.batteryUnrestricted == false && set.autoScan != AutoScanMode.OFF) add(Check(Level.WARN, "Phone", "battery optimization is on for Vigilant: Android may stop background scans", look = "Android Settings › Apps › Vigilant › Battery › Unrestricted"))
        if (p.batterySaver == true && p.batteryUnrestricted == false && set.autoScan != AutoScanMode.OFF) add(Check(Level.WARN, "Phone", "Battery Saver is on and Vigilant isn't unrestricted: Android holds back background work", look = "Android Settings › Battery › Battery Saver; Apps › Vigilant › Battery › Unrestricted"))
        if ((p.standbyBucket == "restricted" || p.standbyBucket == "rare") && set.autoScan != AutoScanMode.OFF) add(Check(Level.WARN, "Phone", "Android has put Vigilant in its ${p.standbyBucket} standby bucket: background work and alarms are cut back", look = "Android Settings › Apps › Vigilant › Battery › Unrestricted; open Vigilant more often"))
        if (p.dataSaver == true) add(Check(Level.WARN, "Phone", "Data Saver is on: background reads can be blocked", look = "Android Settings › Network › Data Saver"))
        if (p.online == false) add(Check(Level.FAIL, "Phone", "no internet connection when this report was made"))
    }

    private fun MutableList<Check>.tracker(s: UiState, x: Diagnostics.Extras, now: Long) {
        val bets = s.bets
        val open = bets.filter { it.status == BetStatus.PENDING }
        val upcoming = open.filter { now < it.startsTs }
        if (upcoming.isNotEmpty()) {
            val current = upcoming.count { TrackerText.currentEv(it, now) }
            val none = upcoming.count { it.nowEv == null }
            val lastCheck = s.checkStartedAtMs
            val checkedLately = lastCheck != null && now - lastCheck < 30 * 60_000L
            val share = current.toDouble() / upcoming.size
            val level = when {
                !checkedLately -> Level.OK
                share < 0.75 -> Level.WARN
                else -> Level.OK
            }
            val top = upcoming.filter { it.nowNote != null && !TrackerText.currentEv(it, now) }.groupingBy { it.nowNote!! }.eachCount().maxByOrNull { it.value }
            add(
                Check(
                    level, "Open bets' EV now", "$current of ${upcoming.size} upcoming bets have a current EV" + if (!checkedLately) " (no Check odds now in the last 30 min: tap it before copying)" else "",
                    listOfNotNull("$none never priced".takeIf { none > 0 }, top?.let { "most common reason ×${it.value}: ${it.key.take(100)}" }).joinToString("; ").ifEmpty { null },
                    if (level == Level.WARN) "data/tracker/OpenBetPricer.kt, BetRecheck.kt, ParlayBooks.kt" else null,
                ),
            )
        }
        val started = open.filter { now >= it.startsTs }
        val overdue = started.filter { now - it.startsTs > 6 * HOUR }
        if (overdue.isNotEmpty()) {
            val top = overdue.groupingBy { it.gradeNote ?: "no note yet" }.eachCount().maxByOrNull { it.value }!!
            add(Check(Level.WARN, "Grading", "${overdue.size} bet${plural(overdue.size)} started over 6 h ago still open (${started.count { it.gradeManual || it.autoGradeOff }} need a tap)", "most common ×${top.value}: ${top.key.take(100)}", "data/tracker/BetSettler.kt, BetGrader.kt, Scores.kt"))
        } else if (bets.any { it.status != BetStatus.PENDING }) {
            add(Check(Level.OK, "Grading", "no bet waiting over 6 h for its result"))
        }
        // Closes: every bet placed before its start in the last week should have a true close once it's started.
        // A ✓ mark imported before the Tracker kept bets has no league and no Novig ids: no source can ever find its close, so it isn't counted.
        val (week, unclosable) = bets
            .filter { it.status != BetStatus.VOID && it.createdAtMs < it.startsTs && now >= it.startsTs && now - it.startsTs < 7 * 24 * HOUR }
            .partition { !neverCloses(it) }
        if (week.size >= 5) {
            val closed = week.count { ClosingLine.closeOf(it, now) != null }
            val share = closed.toDouble() / week.size
            val top = week.filter { ClosingLine.closeOf(it, now) == null }.groupingBy { it.closeNote ?: "not looked for yet" }.eachCount().maxByOrNull { it.value }
            add(
                Check(
                    if (share < 0.7) Level.WARN else Level.OK, "Closing lines", "${pct0(share)} of last week's started bets have a true close",
                    "$closed of ${week.size}" + (top?.let { "; missing ×${it.value}: ${it.key.take(100)}" } ?: "") +
                        (if (unclosable.isNotEmpty()) "; ${unclosable.size} imported ✓ mark${plural(unclosable.size)} left out (no league or Novig ids to find a close by)" else ""),
                    if (share < 0.7) "data/tracker/ClosingLine.kt (capture), HistoricalCloses.kt, ParlayCloses.kt" else null,
                ),
            )
        }
        val needsAlarm = ClosingLine.nextAt(bets, now)
        if (needsAlarm != null && x.closingAlarmAtMs == null) add(Check(Level.WARN, "Closing lines", "a close is due but no capture alarm is set in this process", look = "app/ClosingAlarm.kt"))
        // Data the stats can't use.
        val dupes = open.groupBy { Triple(it.marketId, it.outcomeId, it.startsTs) }.filter { (k, v) -> k.first.isNotBlank() && k.second.isNotBlank() && v.size > 1 }
        if (dupes.isNotEmpty()) add(Check(Level.WARN, "Tracker data", "${dupes.size} open bet${plural(dupes.size)} tracked more than once", dupes.values.first().first().let { "${it.marketLabel} ${it.selection}" }, "BetTracker.track/logCno/logApi keys; delete the copy in the Tracker"))
        val noEv = bets.count { it.evPercentAtBet == null && it.status != BetStatus.VOID }
        if (noEv > 0) add(Check(Level.OK, "Tracker data", "$noEv bet${plural(noEv)} ${if (noEv == 1) "has" else "have"} no EV on record (imported or synced): left out of expected vs actual"))
        val outliers = bets.count { it.isOutlier }
        if (outliers > 0) add(Check(Level.OK, "Tracker data", "$outliers outlier bet${plural(outliers)} (over ±${pct0(BetTracker.OUTLIER_EV)} EV when bet) left out of the stats"))
    }

    private fun MutableList<Check>.accuracy(s: UiState, now: Long) {
        val bets = s.bets
        val withClv = bets.filter { it.status != BetStatus.VOID && !it.isOutlier }.mapNotNull { b -> ClosingLine.clv(b, now)?.let { b to it } }
        // The trap guard's window (Tj's v0.60.0 file, RESEARCH.md §82): his bets placed within 6 h of the start kept most of their shown EV at the close (+3.0% on
        // +3.3%, 96 closes, 81% beat it), those placed earlier none of it (−0.5% on 322; all 37 that lost over 10% to the close were among them). The auto-bet, the
        // alerts and the bids only make the first kind, so that is what "do the edges hold up" is asked of; pooled, the check called the edges "overstated".
        val windowH = s.settings.trapEarlyHours.takeIf { it > 0 } ?: TrapGuard.DEFAULT_EARLY_HOURS
        val (inside, earlier) = withClv.partition { leadMs(it.first) <= windowH * HOUR }
        val judged = if (inside.size >= MIN_WINDOW_CLV) inside else withClv
        val inWindow = judged === inside
        if (judged.size >= 20) {
            val clv = judged.map { it.second }.average()
            val ev = judged.mapNotNull { it.first.evPercentAtBet }.takeIf { it.isNotEmpty() }?.average()
            val beat = judged.count { it.second > 0 }.toDouble() / judged.size
            val gap = ev?.let { it - clv }
            val level = when {
                clv < 0 -> Level.FAIL
                gap != null && gap > 0.02 -> Level.WARN
                else -> Level.OK
            }
            val who = if (inWindow) "bets placed within $windowH h of the start" else "bets"
            add(
                Check(
                    level, "Edge accuracy (CLV)",
                    when (level) {
                        Level.FAIL -> "$who lose to the close on average: the edges shown aren't real"
                        Level.WARN -> "the EV shown when bet runs ${pts(gap!!)} above what the close says: edges are overstated" + if (inWindow) " ($who)" else ""
                        Level.OK -> "$who beat the close: the edges hold up"
                    },
                    "average CLV ${pctSigned(clv)}, EV when bet ${ev?.let(::pctSigned) ?: "?"}, beat the close ${pct0(beat)}, ${judged.size} bets" +
                        (if (inWindow) " placed within $windowH h of the start" else "") +
                        (if (inWindow && earlier.isNotEmpty()) "; placed earlier: CLV ${pctSigned(earlier.map { it.second }.average())} on ${earlier.size} bets (see Early bets)" else ""),
                    if (level != Level.OK) "fair odds (engine/FairValue.kt, ScanSettings.fairSource/devigMethod/minBooks); Accuracy by scanner below" else null,
                ),
            )
        } else if (withClv.isNotEmpty()) {
            add(Check(Level.OK, "Edge accuracy (CLV)", "${withClv.size} bets with a true close so far: 20 needed to judge"))
        }
        // The bets the guard keeps the app out of, which the lists and a hand bet still allow: judged on their own so they cannot hide inside the pooled number.
        if (inWindow && earlier.size >= MIN_SCANNER_CLV) {
            val clv = earlier.map { it.second }.average()
            val beat = earlier.count { it.second > 0 }.toDouble() / earlier.size
            val deep = earlier.count { it.second < -0.10 }
            val recent = bets.count { it.status != BetStatus.VOID && !it.isLock && it.startsTs > 0 && leadMs(it) > windowH * HOUR && it.createdAtMs >= now - 3 * 24 * HOUR }
            val level = if (clv < 0 && recent > 0) Level.WARN else Level.OK
            add(
                Check(
                    level, "Early bets (CLV)",
                    when {
                        clv >= 0 -> "bets placed more than $windowH h before the start beat the close"
                        recent > 0 -> "bets placed more than $windowH h before the start lose to the close, and $recent ${if (recent == 1) "was" else "were"} placed that early in the last 3 days"
                        else -> "bets placed more than $windowH h before the start lose to the close; none were placed that early in the last 3 days"
                    },
                    "CLV ${pctSigned(clv)} on ${earlier.size} bets, beat the close ${pct0(beat)}, $deep closed over 10% worse than their price; within $windowH h: CLV " +
                        "${pctSigned(inside.map { it.second }.average())} on ${inside.size}",
                    if (level == Level.WARN) "Settings › Scanning › Starts within: ${windowH}h keeps them out of the lists (the trap guard already keeps the auto-bet, alerts and bids out of them)" else null,
                ),
            )
        }
        // Each scanner judged on its own (Tj's diagnostics 2026-09-30: CNO's bets beat the close, Vigilant's own lost to it, and the
        // overall number hid that), on the bets placed inside the guard's window when it has enough of them.
        withClv.groupBy { com.tjshea.vigilant.data.tracker.TrackerBreakdown.keyOf(it.first, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.SCANNER) }
            .filter { it.value.size >= MIN_SCANNER_CLV }
            .toSortedMap()
            .forEach { (scanner, all) ->
                val near = all.filter { leadMs(it.first) <= windowH * HOUR }
                val group = if (near.size >= MIN_SCANNER_CLV) near else all
                val clv = group.map { it.second }.average()
                val beat = group.count { it.second > 0 }.toDouble() / group.size
                val ev = group.mapNotNull { it.first.evPercentAtBet }.takeIf { it.isNotEmpty() }?.average()
                // A scanner that is switched off isn't failing now: its old bets (and the closes they are held to) are history, not a thing to fix today.
                val asleep = scanner == "Vigilant" && !s.settings.vigilantOn
                val raw = when {
                    clv < 0 -> Level.FAIL
                    ev != null && ev - clv > 0.02 -> Level.WARN
                    else -> Level.OK
                }
                val level = if (asleep && raw == Level.FAIL) Level.WARN else raw
                add(
                    Check(
                        level, "$scanner's edges (CLV)",
                        when (level) {
                            Level.FAIL -> "its bets lose to the close on average: the edges it shows aren't real"
                            Level.WARN -> if (raw == Level.FAIL) "its old bets lose to the close, and its scanner is asleep now (CNO only), so there is nothing running to fix"
                            else "the EV it shows runs ${pts(ev!! - clv)} above what the close says"
                            Level.OK -> "its bets beat the close"
                        },
                        "CLV ${pctSigned(clv)} on ${group.size} bets, beat the close ${pct0(beat)}, EV when bet ${ev?.let(::pctSigned) ?: "?"}" +
                            (if (group.size < all.size) " (placed within $windowH h; ${all.size - group.size} earlier left out)" else ""),
                        if (level == Level.OK) null else when (scanner) {
                            "Vigilant" -> "its fair odds (engine/FairValue.kt; ScanSettings.fairSource, sharpBooks, minBooks); by market and by what made the fair below"
                            else -> "that list's filters (minimum EV, fewest books, devig)"
                        },
                    ),
                )
            }
        val stats = BetTracker.stats(bets, now)
        stats.luck?.let { z ->
            if (kotlin.math.abs(z) >= 2.0 && stats.settledWithEv >= 30) {
                add(Check(Level.WARN, "Results vs edges", "results are ${String.format(Locale.US, "%.1f", kotlin.math.abs(z))} standard deviations ${if (z < 0) "below" else "above"} what the edges promised", "${stats.settledWithEv} settled bets: expected ${String.format(Locale.US, "%+.2f", stats.expectedProfit)}, actual ${String.format(Locale.US, "%+.2f", stats.profitWithEv)}", if (z < 0) "fair odds too optimistic, or grading (check both)" else null))
            }
        }
    }

    private fun MutableList<Check>.betting(s: UiState) {
        if (!s.betting.enabled) return
        val b = s.betting.balance ?: return
        if (b < 1.0) add(Check(Level.WARN, "Vigilant wallet", "holds ${String.format(Locale.US, "$%.2f", b)}: bets start at what's left", look = "Settings › Betting & Novig account › Add money"))
    }

    /**
     * The heap (Tj's v0.38.0 report, 2026-10-01: an OutOfMemoryError mid-scan with no limit on anything): over the guard's trim line is a WARN
     * with where to cut a scan down; a scan the guard ended early says so in its own errors.
     */
    private fun MutableList<Check>.memory(x: Diagnostics.Extras) {
        val m = x.memory
        if (m.maxMb <= 0) return
        val text = "heap ${m.usedMb} of ${m.maxMb} MB (${Math.round(m.fraction * 100)}%)"
        if (m.fraction >= com.tjshea.vigilant.data.MemoryGuard.TRIM_AT) {
            add(Check(Level.WARN, "Memory", "the app's heap is nearly full: $text", m.lines.firstOrNull(), "Settings › +EV feed & scan size (Novig prices per scan, lines and props per game), fewer leagues; data/MemoryGuard.kt"))
        } else {
            add(Check(Level.OK, "Memory", text))
        }
    }

    /**
     * The auto-bet (Tj, 2026-10-01), judged only when it's on: stopped after a lost order is a FAIL (it stays stopped until Tj resumes it), anything
     * else that keeps it from running is a WARN with what to do; a wallet that can't fund a bet is a WARN.
     */
    private fun MutableList<Check>.autoBet(s: UiState, x: Diagnostics.Extras, now: Long) {
        val set = s.settings
        if (!set.autoBet) {
            add(Check(Level.OK, "Auto-bet", "off", look = "the Auto-bet tab"))
            return
        }
        val st = x.autoBet
        val line = AutoBettor.line(st, now)
        val why = com.tjshea.vigilant.app.ui.AutoBetText.whyNotRunning(s)
        val halted = set.autoBetHalted
        when {
            halted != null -> add(Check(Level.FAIL, "Auto-bet", "stopped after a lost order, placing nothing until resumed", halted.take(160), "Novig and the Tracker's Sync with Novig's fills, then the Auto-bet tab › Resume auto-bet"))
            why != null -> add(Check(Level.WARN, "Auto-bet", "is on but can't run: $why", look = "the Auto-bet tab (its one-tap fix), Settings › Betting & Novig account, Settings › Scanning"))
            st.last.walletEmpty -> add(Check(Level.WARN, "Auto-bet", "the Vigilant wallet can't fund a bet: nothing is placed", line, "Settings › Betting & Novig account › Add money"))
            st.blocker != null -> add(Check(Level.WARN, "Auto-bet", "can't place bets right now: ${st.blocker}", line, "app/AutoBettor.kt"))
            st.lastRunMs == null -> add(Check(Level.WARN, "Auto-bet", "on, but no check has run since the app opened", look = "the background auto-scan below, Android's battery limits"))
            else -> add(Check(Level.OK, "Auto-bet", "running: $line"))
        }
        // Tj, 2026-10-01: every bet gets a push notification; one Android won't show means bets placed that he never sees.
        if (x.phone.notifications == false) add(Check(Level.WARN, "Auto-bet notifications", "Android has notifications switched off for Vigilant: bets are placed with no pop-up", look = "Android Settings › Apps › Vigilant › Notifications, then Settings › Betting & Novig account › Send a test notification"))
        if (st.last.stopped?.contains("daily limit") == true) add(Check(Level.WARN, "Auto-bet", "today's API bets reached the daily limit of ${Locale.US.let { String.format(it, "$%.0f", set.apiMaxPerDay) }}", look = "Settings › Betting & Novig account › Most in a day"))
    }

    /**
     * Sharp-book confirmation (Tj, 2026-10-02): switched on with nothing that can ask is a bet-stopper (the auto-bet skips every bet it can't prove), and
     * is said so; with a feed, it says which and how it has been going.
     */
    private fun MutableList<Check>.sharp(s: UiState, x: Diagnostics.Extras) {
        val set = s.settings
        val confirmBet = set.sharpAutoBet == com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM
        val confirmAlerts = set.sharpAlerts == com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM
        if (!confirmBet && !confirmAlerts) return
        val where = listOfNotNull("auto-bet".takeIf { confirmBet }, "alerts".takeIf { confirmAlerts }).joinToString(" and ")
        if (x.sharpFeeds.isEmpty() && !set.sharpConfirmViaCno) {
            add(
                Check(
                    Level.WARN, "Sharp-book confirmation", "is on for $where but no Pinnacle feed is on with a key: nothing can be confirmed, so ${if (confirmBet) "the auto-bet skips every bet" else "no CNO alert is sent"}",
                    look = "Settings › Fair odds & sources (PinnWire or pinnapi, ParlayAPI, PropLine), or switch on \"Also take Pinnacle's price from CNO's page\"",
                ),
            )
            return
        }
        if (x.sharpCalls > 0 && x.sharpFailures * 2 > x.sharpCalls) {
            add(Check(Level.WARN, "Sharp-book confirmation", "${x.sharpFailures} of ${x.sharpCalls} feed calls failed (a key's allowance, credits held back or no answer)", look = "the API usage meters in Settings"))
            return
        }
        add(Check(Level.OK, "Sharp-book confirmation", "on for $where via ${x.sharpFeeds.joinToString(", ").ifEmpty { "CNO's page" }} (${x.sharpCalls} feed calls since the app opened)"))
    }

    /** A bet no source can find a close for: an early ✓ import with no league and no Novig outcome id. */
    fun neverCloses(b: TrackedBet): Boolean = b.league.isBlank() && b.outcomeId.isBlank()

    /** A source's answer "busy, try again shortly" (ParlayAPI's 503 `…temporarily_busy`, a gateway 502-504): not a fault of the app's. */
    fun transient(error: String): Boolean = Regex("(?i)temporarily_busy|busy|HTTP 50[234]|timeout").containsMatchIn(error)

    /** An error without its JSON body and request id: what it was, in a line. */
    fun shortError(error: String): String {
        val code = Regex("\"error\"\\s*:\\s*\"([a-z_]+)\"").find(error)?.groupValues?.get(1)
        val head = error.substringBefore(" {").replace(Regex("\\s*\\(request [0-9a-f]+\\)"), "").trim()
        return (head + (code?.let { " ($it)" } ?: "")).take(160)
    }

    /** A duration ahead: "2h 25m", "40m". */
    private fun inTime(ms: Long): String {
        val m = (ms / 60_000L).coerceAtLeast(0)
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    /** Sources that only back another up: nothing matched is normal for them. */
    private val BACKUP_SOURCES = setOf(com.tjshea.vigilant.data.reference.OddsFeed.ODDS_API.sourceId, com.tjshea.vigilant.data.reference.OddsFeed.ODDS_API.propsId)

    /** A scanner is judged on its own closes once it has this many. */
    const val MIN_SCANNER_CLV = 15

    /** Edge accuracy is judged on the bets placed inside the trap guard's window once there are this many with a close; fewer, all bets are judged together. */
    const val MIN_WINDOW_CLV = 20

    /** How long before its start a bet was placed. */
    private fun leadMs(b: TrackedBet) = b.startsTs - b.createdAtMs

    /** "Novig refused" words in a scan error: a 403/429/451 from Novig is the scan's own reads failing, not a source's. */
    private val NOVIG_REFUSED = Regex("(?i)novig.*(403|429|451|refused|blocked)")

    private const val HOUR = 3_600_000L
    private fun plural(n: Int) = if (n == 1) "" else "s"
    private fun pct0(v: Double) = String.format(Locale.US, "%.0f%%", v * 100)
    private fun pctSigned(v: Double) = String.format(Locale.US, "%+.1f%%", v * 100)
    private fun pts(v: Double) = String.format(Locale.US, "%.1f points", v * 100)
}
