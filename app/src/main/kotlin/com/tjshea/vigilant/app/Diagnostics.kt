package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.TrackerText
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.ProviderCost
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.RoundCost
import com.tjshea.vigilant.data.keys.Runway
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.KeepAwake
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanTiming
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The "Diagnostics" text (Tj, 2026-09-29: "tell me what you need me to do or show you to optimize the app and make sure everything is working as
 * designed"): one page of what the app is set to, what its last scan did and where the time went, how much of each API's allowance is used, what
 * the background scan is doing, and how the Tracker's bets stand, so that a copy of it says more than a round of questions. Pure: it reads the
 * state it is handed. Never a key (only how many, and the last four characters of a provider's key in the usage meters), never a Novig key ID.
 */
object Diagnostics {

    /** The heap now ([usedMb] of [maxMb]) and what's held in it, one line each ([lines]). */
    data class Memory(val usedMb: Long = 0, val maxMb: Long = 0, val lines: List<String> = emptyList()) {
        val fraction: Double get() = if (maxMb > 0) usedMb.toDouble() / maxMb else 0.0
    }

    /** What isn't in [UiState]: the build, the phone, and the background scan's own status. */
    data class Extras(
        val versionName: String,
        val versionCode: Int,
        val device: String,
        /** When this version was installed (PackageInfo.lastUpdateTime): a crash or exit before it happened on an older version. Null = not known. */
        val installedAtMs: Long? = null,
        val autoScan: AutoScanner.Status = AutoScanner.Status(),
        /** What the auto-bet did last (Tj, 2026-10-01). */
        val autoBet: AutoBettor.Status = AutoBettor.Status(),
        /** Make orders (RESEARCH.md §70): every bid on record and the last pass. */
        val makerBids: List<com.tjshea.vigilant.data.novig.trading.maker.MakerBid> = emptyList(),
        val maker: MakerRunner.Status = MakerRunner.Status(),
        /** The app's heap and what is held in it (Tj's 2026-10-01 report: an OutOfMemoryError mid-scan). */
        val memory: Memory = Memory(),
        val autoScanServiceRunning: Boolean = false,
        /** The service holds the CPU awake between scans right now ([com.tjshea.vigilant.data.scanner.KeepAwake]). */
        val keepAwakeHeld: Boolean = false,
        /** Sharp-book confirmation (Tj, 2026-10-02): the feeds that could confirm a bet now (in the order asked), calls made and failed since the app opened, and answers by feed. */
        val sharpFeeds: List<String> = emptyList(),
        /** Low API usage bids (RESEARCH.md §92): the feeds the picked books need and the picked books nothing can read; null when that isn't the bids' choice. */
        val lowUsagePlan: com.tjshea.vigilant.data.scanner.LowUsageBids.Plan? = null,
        /** How the Kalshi 3-requests-a-second test has gone this session ([com.tjshea.vigilant.data.reference.KalshiClient.paceNote]); null when not asked. */
        val kalshiPace: String? = null,
        /** The live feed test (RESEARCH.md §106): its status line and report's table, null when it was never on and has recorded nothing. */
        val feedRace: List<String>? = null,
        /** The live burst recorder (no orders; RESEARCH.md §95): its status line and its report from the journal; null when it was never on and has recorded nothing. */
        val burstReport: String? = null,
        val sharpCalls: Int = 0,
        val sharpFailures: Int = 0,
        val sharpAnswers: Map<String, Int> = emptyMap(),
        /** When each background cycle started against its schedule, and whether the screen was off or Doze on ([com.tjshea.vigilant.data.diag.CycleLog]); saved across restarts. */
        val cycles: com.tjshea.vigilant.data.diag.CycleBook = com.tjshea.vigilant.data.diag.CycleBook(),
        /** What the last scan and the last Check odds now cost each API (since the app opened), null before one ran. */
        val lastScan: RoundCost? = null,
        val lastCheck: RoundCost? = null,
        /** When the closing capture's alarm is armed for ([ClosingAlarm]), as this process set it. */
        val closingAlarmAtMs: Long? = null,
        /** The last look for closes after the start ([com.tjshea.vigilant.data.tracker.CloseBackfill]) and the bytes Novig's trade files cost. */
        val backfill: com.tjshea.vigilant.data.tracker.CloseBackfill.Report? = null,
        /** The scan study at a glance, and the last thing that went wrong in it ([com.tjshea.vigilant.data.study.ScanStudy]); null: not read. */
        val study: com.tjshea.vigilant.data.study.ScanStudy.Overview? = null,
        val studyProblem: String? = null,
        /** The study's wide read of CNO in a line ([StudyText.wideNote]); null: not read. */
        val studyWide: String? = null,
        val novigTradeBytes: Long = 0,
        /** The key route's stand-downs since the app opened, oldest first, and why it is down now (null: usable) ([com.tjshea.vigilant.data.novig.NovigPublicClient.keyStanddowns]). */
        val keyStanddowns: List<com.tjshea.vigilant.data.novig.KeyStanddown> = emptyList(),
        val keyDownNow: String? = null,
        /** Calls to ParlayAPI's closing lines since the app opened (Pinnacle's closes, when Tj has a key). */
        val parlayCloseRequests: Int = 0,
        /** What each ParlayAPI key said of itself (its free account check): plan, credits left, reset. */
        val parlayAccounts: Map<String, com.tjshea.vigilant.data.reference.ParlayAccount.Check> = emptyMap(),
        /** Calls to ParlayAPI's other endpoints since the app opened, by name (injuries, movers, second opinions, picks), and players' reports kept. */
        val parlayExtras: Map<String, Int> = emptyMap(),
        val injuryReports: Int = 0,
        /** What Android allows Vigilant on this phone; null where it couldn't be read. */
        val phone: Phone = Phone(),
        /** Diagnostics' "Recent problems": saved across restarts, newest first ([com.tjshea.vigilant.data.diag.ProblemLog]). */
        val problems: List<com.tjshea.vigilant.data.diag.Problem> = emptyList(),
        /** Android's record of how the app's process last ended, newest first ([AppExits.recent]). */
        val exits: List<AppExits.Exit> = emptyList(),
        /** The flight recorder (Tj, 2026-10-02): every host's calls, the notable events and counters, the app's own timings, its log lines, its files. */
        val net: com.tjshea.vigilant.data.diag.NetBook = com.tjshea.vigilant.data.diag.NetBook(),
        val events: List<com.tjshea.vigilant.data.diag.Event> = emptyList(),
        val counters: Map<String, Long> = emptyMap(),
        val eventsSinceMs: Long? = null,
        val perf: Map<String, com.tjshea.vigilant.data.diag.SampleSummary> = emptyMap(),
        val coldStartMs: Long? = null,
        /** The screen's frames this run, by what the app was doing ([com.tjshea.vigilant.data.diag.FrameStats]). */
        val frames: Map<String, com.tjshea.vigilant.data.diag.FrameStats.Bucket> = emptyMap(),
        /** Where the CPU went during the last finished Vigilant scan ([ThreadCpu]). */
        val scanCpu: ThreadCpu.Split? = null,
        val logcat: List<com.tjshea.vigilant.data.diag.LogcatTail.Line> = emptyList(),
        /** The app's files and their sizes in bytes, largest first. */
        val storage: List<Pair<String, Long>> = emptyList(),
        /** The snapshot of the report before this one ([com.tjshea.vigilant.data.diag.DiagHistory]): what [com.tjshea.vigilant.data.diag.Trend] compares with. */
        val previous: com.tjshea.vigilant.data.diag.Snap? = null,
    )

    /** The phone's side of it: permissions and settings that decide whether background scans, alerts and the closing capture run. */
    data class Phone(
        val notifications: Boolean? = null,
        val exactAlarms: Boolean? = null,
        val batteryUnrestricted: Boolean? = null,
        val overlay: Boolean? = null,
        val dataSaver: Boolean? = null,
        val online: Boolean? = null,
        /** "Wi-Fi", "mobile", "VPN"… */
        val network: String? = null,
        /** Android's Battery Saver, the Doze state now, and the App Standby bucket Android keeps Vigilant in ("active", "working set", "frequent", "rare", "restricted"). */
        val batterySaver: Boolean? = null,
        val dozing: Boolean? = null,
        val standbyBucket: String? = null,
        /** The battery's charge (percent), whether it's charging, and Android's thermal state ("none", "light", "moderate", "severe"…). */
        val batteryPct: Int? = null,
        val charging: Boolean? = null,
        val thermal: String? = null,
    )

    /** Android's App Standby bucket number as its name (UsageStatsManager.STANDBY_BUCKET_*). */
    fun bucketName(bucket: Int): String = when {
        bucket <= 5 -> "exempted"
        bucket <= 10 -> "active"
        bucket <= 20 -> "working set"
        bucket <= 30 -> "frequent"
        bucket <= 40 -> "rare"
        bucket <= 45 -> "restricted"
        else -> "never"
    }

    /** A message shown on screen that says something failed (what goes in Recent problems), not a confirmation. */
    fun isProblem(text: String): Boolean = PROBLEM_WORDS.containsMatchIn(text)

    private val PROBLEM_WORDS = Regex("(?i)couldn't|could not|can't|failed|error|refused|not found|timed? ?out|unable|isn't|wasn't|no internet|HTTP [45]\\d\\d")

    fun report(s: UiState, x: Extras, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val time = SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).apply { timeZone = zone }
        fun at(ms: Long?) = ms?.let { time.format(Date(it)) } ?: "never"
        fun ago(ms: Long?) = ms?.let { com.tjshea.vigilant.app.ui.Format.age(it, now) } ?: "never"
        val o = StringBuilder()
        o.appendLine("VIGILANT DIAGNOSTICS · ${at(now)}")
        o.appendLine("Version ${x.versionName} (code ${x.versionCode})" + (x.installedAtMs?.let { " installed ${at(it)}" } ?: "") + " · ${x.device}")
        // For whoever reads it next (Tj pastes it to Claude): where the code is, and how the report is laid out.
        o.appendLine("For Claude: code at github.com/tjshea90/novig (modules engine/data/app; paths below are under data/src/main/kotlin/com/tjshea/vigilant/ or app's). Health checks come first, worst first, each with its evidence [in brackets] and the code that owns it (→); the blocks after are the numbers behind them. No keys are ever included.")
        val set = s.settings

        o.appendLine()
        o.appendLine("== Health checks (worst first) ==")
        val checks = HealthChecks.of(s, x, now)
        o.appendLine("${checks.count { it.level == HealthChecks.Level.FAIL }} FAIL · ${checks.count { it.level == HealthChecks.Level.WARN }} WARN · ${checks.count { it.level == HealthChecks.Level.OK }} OK")
        checks.forEach { o.appendLine(it.text()) }

        o.appendLine()
        o.appendLine("== Settings ==")
        o.appendLine("Scanner: ${if (set.pinnacleOnly) "Pinnacle only (${set.scanner.displayName} underneath)" else set.scanner.displayName} · paused: ${if (set.paused) "YES" else "no"}" + (if (set.killed) " · STOPPED by the STOP button" else ""))
        // The kill switch (Tj, 2026-10-05): saved with the settings and in a second copy; what it stops and since when.
        o.appendLine(
            "Kill switch (STOP ALL): ${if (set.killed) "ON" + (set.killedAtMs?.let { " since ${at(it)}" } ?: "") + " · scanning, CNO, auto-bet, auto-lock, bids and the background scan are all held; only RESUME on the red bar lifts it" else "off"}",
        )
        o.appendLine(
            "Background auto-scan: ${set.autoScan.displayName}" + (if (set.autoScan != AutoScanMode.OFF) " every ${ScanSettings.intervalLabel(set.autoScanSeconds)}" else "") +
                " → actually runs: ${runsText(set)} · service ${if (x.autoScanServiceRunning) "running" else "not running"}",
        )
        o.appendLine(
            "Keep awake (Tj, 2026-10-02): switch ${if (set.autoScanKeepAwake) "on" else "OFF"} · " + when {
                set.activeAutoScan == AutoScanMode.OFF -> "auto-scan runs nothing, nothing to keep awake"
                KeepAwake.active(set) -> "holding the CPU awake (screen off): ${if (x.keepAwakeHeld) "yes, the wake lock is held now" else "NO, the service isn't holding it"}"
                !set.autoScanKeepAwake && set.autoScanSeconds < KeepAwake.ALARM_ONLY_BELOW_SECONDS -> "NOT holding it: scans between ${ScanSettings.intervalLabel(set.autoScanSeconds)} apart run on alarms, which Doze may space about 9 minutes apart"
                else -> "not needed at ${ScanSettings.intervalLabel(set.autoScanSeconds)}: an alarm is on time at 9 minutes or more"
            },
        )
        o.appendLine(
            "Auto-bet (Tj, 2026-10-01): " + if (!set.autoBet) "off" else {
                "ON · ${if (set.pinnacleOnly) "Pinnacle only: beats Pinnacle's devigged price by ${com.tjshea.vigilant.app.ui.AutoBetText.evLabel(com.tjshea.vigilant.data.novig.trading.AutoBet.rules(set).minEv)} or more, Pinnacle's price within ${com.tjshea.vigilant.app.ui.PinnacleOnlyText.ageLabel(set.pinnacleMaxAgeSeconds)}" else com.tjshea.vigilant.app.ui.AutoBetText.criteria(set)} · most a day ${"$%.0f".format(java.util.Locale.US, set.apiMaxPerDay)} · most on one game ${if (set.apiMaxPerGame > 0.0) "$%.0f".format(java.util.Locale.US, set.apiMaxPerGame) else "no limit"} · bankroll ${"$%.0f".format(java.util.Locale.US, set.bankroll)} · " +
                    (set.autoBetHalted?.let { "HALTED: $it" } ?: "not halted") + " · ${AutoBettor.line(x.autoBet, now, set.pinnacleOnly)}"
            },
        )
        o.appendLine(
            "Auto-lock (RESEARCH.md §67): ${if (set.autoLock) "ON" else "off"} · min ${pct(set.autoLockMinPercent)} of the stake · in-game ${if (set.autoLockLive) "yes" else "no"}" +
                " · locks on offer now ${s.locks.values.count { it.result is com.tjshea.vigilant.data.novig.trading.LockResult.Ready }} of ${s.locks.size} API markets" +
                " · Tracker Novig-only filter ${if (set.trackerNovigOnly) "on" else "off"}" +
                " · hide locked bets ${if (set.trackerHideLocked) "on" else "off"}",
        )
        o.appendLine(
            "Make orders / Bids (RESEARCH.md §70): ${if (set.maker) "ON" else "off"} · ${com.tjshea.vigilant.app.ui.MakerRulesText.summary(set)} · " +
                "most ${if (set.makerMaxBids >= ScanSettings.NO_LIMIT) "unlimited" else set.makerMaxBids} bids / ${if (set.makerMaxDollars >= ScanSettings.MAKER_NO_DOLLAR_LIMIT) "no dollar limit" else "$%.0f".format(java.util.Locale.US, set.makerMaxDollars)} · stop ${set.makerStopMinutes} min before the start · " +
                "both sides ${if (set.makerBothSides) "yes" else "no"} · sharp veto ${if (set.makerSharpVeto) "on" else "off"} · sharp book required ${if (set.makerRequireSharp) "yes" else "no"} · popular first ${if (set.makerPopularFirst) "yes (Novig's volume by kind of market)" else "no"} · fewest books agreeing ${set.makerMinBooks} · " +
                "recommend ${if (set.makerRecommend) "on" else "off"} · ${x.makerBids.count { it.resting }} resting, ${x.makerBids.count { it.status == com.tjshea.vigilant.data.novig.trading.maker.MakerStatus.CANCELING }} cancelling, " +
                "${x.makerBids.count { it.filled > 0 }} filled of ${x.makerBids.size} on record (14 days)" +
                (x.maker.lastAtMs?.let { " · last pass ${com.tjshea.vigilant.app.ui.Format.age(it, now)}" } ?: "") +
                (x.maker.lastReport?.let { r -> " (${r.placed} posted, ${r.cancelled} cancelled${r.stopped?.let { s -> ", $s" } ?: ""})" } ?: "") +
                (x.maker.lastReport?.waiting?.takeIf { it.isNotEmpty() }?.let { w -> " · held back: " + w.entries.sortedByDescending { it.value }.joinToString("; ") { (why, k) -> "$k because $why" } } ?: "") +
                (x.maker.problem?.let { " · problem: $it" } ?: ""),
        )
        lowUsageLines(set, x.lowUsagePlan, now).forEach { o.appendLine(it) }
        x.burstReport?.let { r -> o.appendLine(); o.append(r) }
        x.feedRace?.let { lines -> o.appendLine(); o.appendLine("== LIVE FEED TEST (which free feed shows a score or odds move before Novig's price; no orders; RESEARCH.md §106) =="); lines.forEach { o.appendLine(it) } }
        MakerStats.line(x.makerBids, now)?.let { o.appendLine("  $it") }
        MakerStats.recent(x.makerBids, now).forEach { o.appendLine("  $it") }
        x.makerBids.filter { it.status.ended }.groupingBy { it.status.label + (it.why?.let { w -> ": $w" } ?: "") }.eachCount().entries.sortedByDescending { it.value }.take(5)
            .forEach { o.appendLine("  bids ended ×${it.value}: ${it.key}") }
        // How well the bids do (Tj, 2026-10-05: "make sure the auto bid feature is also thoroughly tracked … so I can see how well my auto bids do"): every fill,
        // how fast it was taken, whether the fair had already moved under it, its close and its result; then the same split the ways that explain a fill.
        val bidRows = com.tjshea.vigilant.data.novig.trading.maker.BidReport.rows(x.makerBids, s.bets, now, set.makerAnchorSharp)
        if (bidRows.isNotEmpty()) {
            val guard = com.tjshea.vigilant.data.novig.trading.maker.MakerGuard.check(x.makerBids, set.makerGuardFromMs, set.makerAnchorSharp)
            o.appendLine(
                "  picked-off guard: ${if (set.makerGuard) "on" else "off"} · price under the sharp book's fair: ${if (set.makerAnchorSharp) "on" else "off"} · focus: ${set.makerFocus.displayName}${com.tjshea.vigilant.data.novig.trading.maker.QuickLikely.diagnosticsNote(set)} · most bids ${if (set.makerMaxBids >= ScanSettings.NO_LIMIT) "unlimited" else set.makerMaxBids} · ${guard.text}" +
                    (if (guard.tripped && set.makerHalted == null) " · (would stop the bids)" else "") + (set.makerHalted?.let { " · BIDS STOPPED BY THE GUARD: $it" } ?: ""),
            )
            com.tjshea.vigilant.data.novig.trading.maker.BidReport.summary(bidRows, now).forEach { o.appendLine("  $it") }
            val fillLines = com.tjshea.vigilant.data.novig.trading.maker.BidReport.fillLines(bidRows, now, zone = time.timeZone)
            if (fillLines.isNotEmpty()) {
                o.appendLine("  the newest fills (when · bid · price · how fast it was taken · the fair and EV claimed · the fair and EV on the next scan after the fill · the book · CLV · result):")
                fillLines.forEach { o.appendLine(it) }
            }
        }
        o.appendLine(
            "Sharp books (Tj, 2026-10-02: veto by default): auto-bet ${set.sharpAutoBet} · alerts ${set.sharpAlerts}" +
                if (set.sharpAutoBet != com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM && set.sharpAlerts != com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM) "" else {
                    " · ${set.sharpConfirmBooks.displayName}, quote at most ${ScanSettings.intervalLabel(set.sharpConfirmMaxAgeSeconds)} old, edge ${if (set.sharpConfirmMinEv <= 0.0) "any +EV" else "at least ${pct(set.sharpConfirmMinEv)}"}, " +
                        "CNO's page ${if (set.sharpConfirmViaCno) "may confirm" else "only vetoes"} · feeds: ${x.sharpFeeds.joinToString(", ").ifEmpty { "none on with a key" }} · " +
                        "feed calls since the app opened ${x.sharpCalls} (${x.sharpFailures} failed)" + (x.sharpAnswers.takeIf { it.isNotEmpty() }?.let { m -> ", answers: " + m.entries.joinToString(", ") { "${it.key} ${it.value}" } } ?: "")
                } +
                // The veto's bar (RESEARCH.md §72), where a veto uses it: the auto-bet's, the alerts' and the bids'.
                (if (set.sharpAutoBet != com.tjshea.vigilant.data.scanner.SharpMode.VETO && set.sharpAlerts != com.tjshea.vigilant.data.scanner.SharpMode.VETO && !set.makerSharpVeto) "" else
                    " · veto bar ${if (set.sharpVetoMinEv <= 0.0) "any +EV" else "${pct(set.sharpVetoMinEv)} on the sharpest book (RESEARCH.md §72)"}"),
        )
        o.appendLine(
            "Trap guard (RESEARCH.md §71): auto-bet, alerts and bids only on games starting within " +
                (if (set.trapEarlyHours <= 0) "any time (off)" else "${set.trapEarlyHours} h") +
                " · bets first listed more than that many hours before the start ${if (set.trapFirstListed && set.trapEarlyHours > 0) "skipped (auto-bet and alerts)" else "not skipped"}" +
                " · game lines Novig just moved ${if (set.trapNovigMove) "skipped (the auto-bet and game-line bids read Novig's trades first)" else "not checked"}" +
                " · favorites need ${if (set.autoBetFavouriteExtraEv <= 1e-9) "no extra edge" else "${pct(set.autoBetFavouriteExtraEv)} more edge"} (auto-bet)",
        )
        // The small-prop guard (RESEARCH.md §109): its limits, and what the last day and week of auto-bets look like by kind of prop, so the next file shows whether one market is carrying the day.
        o.appendLine(
            "Small-prop guard (RESEARCH.md §109): ${com.tjshea.vigilant.data.novig.trading.PropGuard.summary(com.tjshea.vigilant.data.novig.trading.PropGuard.rules(set))}" +
                " · " + com.tjshea.vigilant.app.ui.PropGuardText.shares(s.bets, System.currentTimeMillis()),
        )
        o.appendLine("Leagues: ${set.leagues.sorted().joinToString(", ").ifEmpty { "none" }} · days ahead ${set.daysAhead} · starts within ${if (set.startsWithinHours <= 0) "any time" else "${set.startsWithinHours} h"} · live games ${if (set.includeLive) "on" else "off"}")
        o.appendLine("Edge shown: ${pct(set.minEvPercent)} to ${pct(set.maxEvPercent)} · max odds +${set.maxOdds} · fair odds ${set.fairSource} / ${set.devigMethod}, at least ${set.minBooks} book${if (set.minBooks == 1) "" else "s"}")
        o.appendLine("Scan size: ${limit(set.maxBooksPerScan)} Novig prices · lines/game ${limit(set.linesPerGame)} · props/game ${limit(set.propsPerGame)} · fill the budget ${if (set.fillBudget) "on" else "off"} · window ${set.effective().scanWindowHours} h")
        o.appendLine("Fair-odds sources on: ${set.enabledSources.sorted().joinToString(", ").ifEmpty { "none" }} · sportsbook props ${if (set.useBookProps) "on" else "off"} (credits/scan ${limit(set.bookPropCreditsPerScan)}, PropLine games ${limit(set.propLineGamesPerScan)})")
        o.appendLine("Keys saved: " + ApiProvider.entries.joinToString(" · ") { "${it.displayName} ${s.keysOf(it).size}" })
        o.appendLine("CrazyNinjaOdds: ${if (set.cnoOn) "on" else "off"} · refresh ${when { set.cnoRefreshSeconds == com.tjshea.vigilant.data.cno.CnoFeed.REALTIME -> "real time"; set.cnoRefreshSeconds <= 0 -> "taps only"; else -> "${set.cnoRefreshSeconds} s" }} · only bets the books agree on ${if (set.cnoOnlyAgreed) "on" else "off"} · alerts ≥ ${pct(set.alertMinEv)}")
        o.appendLine(
            "Stakes: bankroll $${money(set.bankroll)} · ${com.tjshea.vigilant.app.ui.Format.kellyLabel(set.kellyMultiplier)} · bet slip amount ${set.slipStake.label}" +
                (if (set.slipStake == com.tjshea.vigilant.data.novig.SlipStake.CUSTOM) " ($${money(set.slipCustomStake)})" else ""),
        )
        o.appendLine(
            "Betting through the API: ${if (s.betting.enabled) "on" else "off"}" + (s.betting.balance?.let { String.format(Locale.US, " · wallet $%.2f", it) } ?: "") +
                " · amount $${money(set.slipCustomStake)}, most per bet $${money(set.apiMaxStake)}, most per day $${money(set.apiMaxPerDay)}, most on one game ${if (set.apiMaxPerGame > 0.0) "$" + money(set.apiMaxPerGame) else "no limit"}, no minimum edge by hand (auto-bet has its own)",
        )
        o.appendLine(
            "Novig key: ${if (s.novig.connection != null) "connected" else "not connected"} · management key " +
                (s.novig.managementKey?.let { if (it.unreadable) "saved but can't be unlocked (enter it again)" else "saved on this phone (••••${it.keyIdEnd})" } ?: "not saved"),
        )
        // Why a scan read the public routes (2-4 a second) instead of the key's (14 a second): each stand-down of the key route, when, how long, and why (RESEARCH.md §81.1).
        if (s.novig.connection != null) {
            o.appendLine("Novig key route: " + (x.keyDownNow?.let { "STANDING DOWN now (reads go to the public routes): $it" } ?: "usable now") + " · stand-downs since the app opened: ${x.keyStanddowns.size}")
            x.keyStanddowns.takeLast(10).forEach { d -> o.appendLine("  ${at(d.atMs)} · public routes for ${d.forMs / 1_000} s · ${d.why}") }
        }

        o.appendLine()
        o.appendLine("== Last Vigilant scan ==")
        val st = s.status
        if (st.scannedAtMs == null) {
            o.appendLine("No scan since the app opened.")
        } else {
            // scannedAtMs is when the scan's result was computed (its start); the file used to print it as the finish, a scan's length too early (Tj's v0.70.1 file: "Finished 3m ago (12:04:46 AM)").
            val finishedAt = st.timing?.takeIf { it.totalMs > 0 }?.let { st.scannedAtMs + it.totalMs }
            o.appendLine(
                (if (finishedAt != null) "Started ${ago(st.scannedAtMs)} (${at(st.scannedAtMs)}), finished ${ago(finishedAt)} (${at(finishedAt)})" else "Started ${ago(st.scannedAtMs)} (${at(st.scannedAtMs)})") +
                    " · window ${st.scannedWindowHours ?: "?"} h" + if (st.scanning) " · another is running now" else "",
            )
            st.timing?.let { o.appendLine(ScanTiming.text(it, st.booksFetched, st.booksViaKey, st.booksViaPush, st.keyReadPerSec)) }
            o.appendLine("Novig prices: ${st.booksFetched} read (${st.booksViaKey} through the key, ${st.booksViaPush} pushed), ${st.booksFromCache} shown from the last scan")
            s.result?.stats?.let {
                o.appendLine("Games ${it.novigEvents} on Novig, ${it.matchedEvents} matched to fair odds · ${it.marketsPriced} lines priced · ${it.outcomesWithFair} sides with a fair price · ${it.positiveEv} +EV · ${it.laterGames} games past days ahead")
            }
            // Which leagues the unmatched games are in, and a few of them by name: a league's sources decide its matching.
            val byLeague = matchingByLeague(s)
            if (byLeague.isNotEmpty()) {
                o.appendLine("Matched by league (games, futures aside): " + byLeague.joinToString(" · ") { "${it.league} ${it.matched}/${it.games}" })
                byLeague.flatMap { l -> l.unmatched.take(3).map { "${l.league}: $it" } }.take(8).takeIf { it.isNotEmpty() }?.let { o.appendLine("  unmatched, e.g.: " + it.joinToString(" | ")) }
            }
            o.appendLine("Feed now: ${s.feed.size} bets")
            for (r in st.sources) {
                o.appendLine("  ${r.name}: ${r.fetched} fetched, ${r.reused} re-used${if (r.standingBy > 0) ", ${r.standingBy} standing by" else ""}${if (r.skipped > 0) ", ${r.skipped} leagues not asked (nothing in the window)" else ""}, ${r.matched} games matched" + (r.error?.let { " · ERROR: $it" } ?: "") + (r.heldBack?.let { " · $it" } ?: ""))
            }
            if (st.errors.isEmpty()) o.appendLine("Errors: none") else st.errors.take(MAX_ERRORS).forEach { o.appendLine("Error: $it") }
            st.backoffSeconds?.let { o.appendLine("Novig asked us to wait $it s") }
        }

        o.appendLine()
        o.appendLine("== API usage (each provider's own allowance) ==")
        if (s.usage.providers.isEmpty()) o.appendLine("Nothing counted yet.")
        x.kalshiPace?.let { o.appendLine(it) }
        for ((id, p) in s.usage.providers.toSortedMap()) {
            o.appendLine("$id: ${p.callsToday} calls today, ${p.throttledToday} refused/throttled" + (p.lastThrottleMs?.let { ", last throttle ${ago(it)}" } ?: ""))
            for ((key, u) in p.keys) {
                val left = u.remaining ?: (u.limit?.let { (it - u.used).coerceAtLeast(0) })
                o.appendLine("    key …${key.takeLast(4)}: used ${u.used}${left?.let { ", $it left" } ?: ""}${if (u.refused) ", REFUSED" else ""}${u.depletedUntil?.let { ", spent until ${at(it)}" } ?: ""}${u.resetAtMs?.let { ", resets ${at(it)} (the provider's time)" } ?: ""}${u.lastNote?.let { " · $it" } ?: ""}")
                // What a ParlayAPI key said of itself (Tj, 2026-09-30: "the API key to tell the app how many credits I have left").
                if (id == com.tjshea.vigilant.data.keys.QuotaPolicy.PARLAY.id) x.parlayAccounts[key]?.let { a ->
                    o.appendLine(
                        "      its own account: " + listOfNotNull(
                            a.tier?.let { "plan $it" },
                            a.remaining?.let { r -> "$r left" + (a.limit?.let { " of $it" } ?: "") },
                            a.resetAtMs?.let { "resets ${at(it)}" },
                            if (a.valid == false) "NOT VALID" + (a.reason?.let { ": $it" } ?: "") else null,
                            a.source?.let { "read from /v1/$it" },
                        ).joinToString(" · ").ifEmpty { "answered, no figures" },
                    )
                }
            }
        }
        // ParlayAPI's extras (v0.30.0, PARLAY_API.md §6): what they've been asked since the app opened.
        if (s.settings.useParlay) {
            o.appendLine(
                "ParlayAPI extras since the app opened: " + x.parlayExtras.entries.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "none" } +
                    " · injury reports kept ${x.injuryReports} · tagged now ${s.injuries.size} · line moves ${s.lineMoves.size}" +
                    " · picks listed ${s.parlayPicks.picks.size} (${s.parlayPicks.picks.count { it.found }} found at Novig)",
            )
        }

        o.appendLine()
        o.appendLine("== Runway (will each API's allowance last?) ==")
        val views = com.tjshea.vigilant.app.ui.meterViews(s, now).filter { it.policy.keyed }
        val runway = Runway.lines(views, now)
        if (runway.isEmpty()) o.appendLine("No keyed API has a key saved.")
        for (line in runway) {
            o.appendLine(line.text)
            val view = views.first { it.policy.id == line.id }
            listOf("a scan" to x.lastScan, "a Check odds now" to x.lastCheck).forEach { (what, round) ->
                round?.costs?.firstOrNull { it.id == line.id }?.let { c -> Runway.roundsNote(view, c, what)?.let { o.appendLine("    $it") } }
            }
        }

        o.appendLine()
        o.appendLine("== Last rounds (what they cost each API) ==")
        roundText("Scan", x.lastScan, now).forEach { o.appendLine(it) }
        roundText("Check odds now", x.lastCheck, now).forEach { o.appendLine(it) }

        o.appendLine()
        o.appendLine("== CrazyNinjaOdds ==")
        val cno = s.cno
        o.appendLine("Last read: ${ago(cno.snapshot?.fetchedAtMs)} · ${cno.snapshot?.rows?.size ?: 0} rows · errors in a row ${cno.errors}" + (cno.error?.let { " · last error: $it" } ?: "") + (cno.pausedUntilMs?.takeIf { it > now }?.let { " · paused until ${at(it)}" } ?: "") +
            (cno.lastPause?.let { " · last pause CNO asked for ${ago(cno.lastPauseAtMs)}: $it" } ?: ""))
        o.appendLine("Kept current now: ${if (s.cnoLive) "yes (its tab or a widget is on screen)" else "no"}")

        o.appendLine()
        o.appendLine("== Background auto-scan ==")
        val a = x.autoScan
        o.appendLine("Now: ${if (a.running) "running (${a.step ?: "…"})" else "idle"} · last started ${ago(a.lastStartMs)} · ended ${ago(a.lastEndMs)} · found ${a.lastFound}, alerts sent ${a.lastAlerts}" + (a.lastError?.let { " · last error: $it" } ?: ""))
        o.appendLine(com.tjshea.vigilant.data.diag.CycleLog.line(x.cycles, now, zone))
        com.tjshea.vigilant.data.diag.CycleLog.lateWithin(x.cycles, now, 24 * 60 * 60_000L).takeLast(5).forEach { l ->
            o.appendLine("  late: ${at(l.atMs)} · ${com.tjshea.vigilant.data.diag.CycleLog.span(l.lateMs)} after schedule" + (if (l.dozing) " · Doze on" else if (l.screenOff) " · screen off" else " · screen on"))
        }

        o.appendLine()
        o.appendLine("== Tracker ==")
        val bets = s.bets
        val open = bets.filter { it.status == BetStatus.PENDING }
        val upcoming = open.filter { now < it.startsTs }
        val started = open.filter { now >= it.startsTs }
        o.appendLine("Bets: ${bets.size} (open ${open.size}: ${upcoming.size} upcoming, ${started.size} started; settled ${bets.size - open.size})")
        o.appendLine("By scanner: Vigilant ${bets.count { it.source == BetTracker.SOURCE_VIGILANT }}, CNO ${bets.count { it.source == BetTracker.SOURCE_CNO }}, ParlayAPI ${bets.count { it.source == BetTracker.SOURCE_PARLAY }} · placed through the API ${bets.count { it.viaApi }}")
        o.appendLine("Current EV: ${upcoming.count { TrackerText.currentEv(it, now) }} of ${upcoming.size} upcoming bets have one read inside the fair odds' age limit; ${upcoming.count { it.nowEv != null && !TrackerText.currentEv(it, now) }} have an old one; ${upcoming.count { it.nowEv == null }} none")
        val reasons = upcoming.filter { it.nowNote != null && it.nowEv == null }.groupingBy { it.nowNote!! }.eachCount().entries.sortedByDescending { it.value }.take(6)
        reasons.forEach { o.appendLine("  not priced ×${it.value}: ${it.key}") }
        // The Tracker's counter (Tj, 2026-09-29): open bets re-priced since the last Check odds now began.
        s.checkStartedAtMs?.let { since ->
            val c = com.tjshea.vigilant.data.tracker.CheckOddsStats.of(bets, since, now)
            o.appendLine("Check odds now counter (since ${at(since)}): ${TrackerText.checkCounts(c)} · ${TrackerText.checkAverage(c)}" + (if (c.outliers > 0) " (${c.outliers} over ±5% left out)" else "") + (if (c.live > 0) " (${c.live} live games left out)" else ""))
        }
        // The scan study (Tj, 2026-10-03): every bet a scan listed, for Settings › Tools › Share scan study with Claude.
        o.appendLine(
            "Scan study: " + (x.study?.let { StudyText.note(it, now) + " · ${it.loggedThisRun} bets logged since the app opened" } ?: "not read") +
                " · ${if (set.scanStudy) "logging on" else "logging OFF (Settings › Diagnostics & about)"}" + (x.studyProblem?.let { " · last problem: $it" } ?: ""),
        )
        x.studyWide?.let { o.appendLine("Scan study's wide CNO read: $it") }
        o.appendLine("Settled by: score feeds ${bets.count { it.settledBy == BetSettler.BY_SCORES }}, Novig's ledger ${bets.count { it.settledBy == BetSettler.BY_NOVIG }}, you ${bets.count { it.settledBy == BetSettler.BY_YOU }}")
        val overdue = started.filter { now - it.startsTs > 6 * 3_600_000L }
        o.appendLine("Started and still open: ${started.size} (${overdue.size} for over 6 hours, ${started.count { it.gradeManual || it.autoGradeOff }} need a tap)")
        overdue.groupingBy { it.gradeNote ?: "no note yet" }.eachCount().entries.sortedByDescending { it.value }.take(6).forEach { o.appendLine("  waiting ×${it.value}: ${it.key}") }
        val stats = BetTracker.stats(bets, now)
        // True closing line value (Tj, 2026-09-29): all time, outliers in, and when the next close is read.
        val clv = com.tjshea.vigilant.data.tracker.ClvStats.of(bets, now, zone = zone.toZoneId())
        o.appendLine("Closing line value (all time): ${TrackerText.clvLine(clv)} · ${TrackerText.clvCounts(clv)}")
        o.appendLine("Next closing-line read: " + (com.tjshea.vigilant.data.tracker.ClosingLine.nextAt(bets, now)?.let { at(it) } ?: "none needed") +
            " · alarm " + (x.closingAlarmAtMs?.let { at(it) } ?: "not set in this process"))
        // Closes found after the start (Tj, 2026-09-30: ESPN's closing odds, Novig's trade history, Pinnacle's via ParlayAPI).
        o.appendLine(
            "Closes found after the start: " + (x.backfill?.let { r -> (if (r.forced) "last look (Check odds now: every one) " else "last look ") + "${r.looked} bet${if (r.looked == 1) "" else "s"}, found ${r.found}" + (r.bySource.takeIf { it.isNotEmpty() }?.entries?.joinToString(", ", " (", ")") { "${it.key} ${it.value}" } ?: "") + (r.missing.takeIf { it.isNotEmpty() }?.entries?.sortedByDescending { it.value }?.take(3)?.joinToString("; ", ", still without: ", "") { "${it.key.take(60)} (${it.value})" } ?: "") } ?: "none looked for since the app opened") +
                " · Novig trade data read ${x.novigTradeBytes / 1024} KB" +
                (if (x.parlayCloseRequests > 0) " · ParlayAPI close calls ${x.parlayCloseRequests}" else ""),
        )
        val stillLooking = bets.filter { now >= it.startsTs && !it.closeFinal && com.tjshea.vigilant.data.tracker.ClosingLine.closeOf(it, now) == null && it.status != BetStatus.VOID && it.createdAtMs < it.startsTs }
        if (stillLooking.isNotEmpty()) {
            o.appendLine("  still looking for ${stillLooking.size} close${if (stillLooking.size == 1) "" else "s"}:")
            stillLooking.groupingBy { it.closeNote ?: "not looked for yet" }.eachCount().entries.sortedByDescending { it.value }.take(5).forEach { o.appendLine("    ×${it.value}: ${it.key}") }
        }
        o.appendLine("Results: ${stats.won}-${stats.lost}${if (stats.pushed > 0) "-${stats.pushed}" else ""} · profit ${String.format(Locale.US, "%+.2f", stats.profit)} on ${String.format(Locale.US, "%.2f", stats.staked)} staked" + (stats.roi?.let { String.format(Locale.US, " (%+.1f%%)", it * 100) } ?: "") + (stats.averageEv?.let { String.format(Locale.US, " · average EV when bet %+.1f%%", it * 100) } ?: "") + (stats.averageClv?.let { String.format(Locale.US, " · average CLV %+.1f%%", it * 100) } ?: ""))
        // What the Tracker's Profit shows (every settled bet, the same with its "Novig only" filter on or off): the line above leaves the outliers out.
        if (stats.outliers > 0) o.appendLine("Every settled bet, outliers too (the Tracker's Profit): profit ${String.format(Locale.US, "%+.2f", stats.profitAll)} on ${String.format(Locale.US, "%.2f", stats.stakedAll)} staked" + (stats.roiAll?.let { String.format(Locale.US, " (%+.1f%%)", it * 100) } ?: "") + " · ${stats.outliers} outlier bet${if (stats.outliers == 1) "" else "s"} (over ±${(BetTracker.OUTLIER_EV * 100).toInt()}% EV when bet)")
        stats.luck?.let { o.appendLine(String.format(Locale.US, "Expected %+.2f vs actual %+.2f over %d settled bets with an EV: %+.1f standard deviations", stats.expectedProfit, stats.profitWithEv, stats.settledWithEv, it)) }
        // Locks (Tj, 2026-10-02 20:06Z): the numbers above count every bet; the Tracker hides locked ones when its switch is on.
        val locks = com.tjshea.vigilant.data.tracker.LockedBets.stats(bets)
        o.appendLine("Locked in: ${TrackerText.lockCaption(locks, s.settings.trackerHideLocked)}" + String.format(Locale.US, " (profit %% %s)", locks.roi?.let { String.format(Locale.US, "%+.1f%%", it * 100) } ?: "–"))
        // Make orders' fills against the close (RESEARCH.md §70.5: judge them by CLV over 200+ fills).
        val makerFills = bets.filter { it.maker }
        if (makerFills.isNotEmpty()) {
            val mc = com.tjshea.vigilant.data.tracker.ClvStats.of(makerFills, now)
            o.appendLine(
                "Maker fills: ${makerFills.size} bets · average EV at the fair when posted " +
                    (makerFills.mapNotNull { it.evPercentAtBet }.takeIf { it.isNotEmpty() }?.average()?.let { String.format(Locale.US, "%+.1f%%", it * 100) } ?: "–") +
                    " · CLV ${mc.averageClv?.let { String.format(Locale.US, "%+.1f%%", it * 100) } ?: "–"} on ${mc.closed} with a close, ${mc.beat} beat it",
            )
        }
        // Why open Novig bets have no Novig price now (the Novig-only filter; Tj, 2026-10-02 20:06Z: "many open bets are not finding the current novig odds").
        val novigOpen = com.tjshea.vigilant.data.tracker.NovigNow.open(bets, now)
        o.appendLine("Novig's own price: ${novigOpen.count { com.tjshea.vigilant.data.tracker.NovigNow.note(it) == null }} of ${novigOpen.size} open Novig bets · ${novigOpen.count { it.marketId.isBlank() || it.outcomeId.isBlank() }} without Novig's ids on record")
        novigOpen.mapNotNull { com.tjshea.vigilant.data.tracker.NovigNow.note(it) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
            .forEach { o.appendLine("  no Novig price ×${it.value}: ${it.key}") }

        // Where the edge is real (Tj, 2026-09-30: "know … how to improve the app … accuracy"): the same numbers split, then open bets' edge now.
        o.appendLine()
        o.appendLine("== Accuracy by scanner and by market (outliers aside; CLV on n = bets with a true close) ==")
        val kept = bets.filterNot { it.isOutlier }
        for (by in listOf(com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.SCANNER, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.MARKET, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.EV)) {
            com.tjshea.vigilant.data.tracker.TrackerBreakdown.of(bets, by).forEach { row ->
                val group = kept.filter { com.tjshea.vigilant.data.tracker.TrackerBreakdown.keyOf(it, by) == row.label }
                o.appendLine("${by.label} ${row.label}: ${breakdownText(row.stats, closedCount(group, now))}")
            }
        }

        // Each scanner by market, and Vigilant's own by what made its fair odds (Tj's diagnostics 2026-09-30: where Vigilant loses to the close).
        o.appendLine()
        o.appendLine("== Each scanner by market ==")
        for ((scanner, group) in kept.groupBy { com.tjshea.vigilant.data.tracker.TrackerBreakdown.keyOf(it, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.SCANNER) }.toSortedMap()) {
            com.tjshea.vigilant.data.tracker.TrackerBreakdown.of(group, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.MARKET).forEach { row ->
                val rows = group.filter { com.tjshea.vigilant.data.tracker.TrackerBreakdown.keyOf(it, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.MARKET) == row.label }
                o.appendLine("$scanner · ${row.label}: ${breakdownText(row.stats, closedCount(rows, now))}")
            }
        }

        // The bet as placed (Tj, 2026-10-02 17:01Z: "record all types of information on the bet as placed … include this information for all bets in the
        // diagnosis feature"): the close and the results split by what each bet looked like when it was made (recorded from v0.45.0).
        o.appendLine()
        val recorded = kept.count { it.atBet != null }
        o.appendLine("== The bet as placed: close and results split by it ($recorded of ${kept.size} bets recorded, from v0.45.0; outliers aside) ==")
        for (split in com.tjshea.vigilant.data.tracker.BetLedger.Split.entries) {
            com.tjshea.vigilant.data.tracker.BetLedger.of(kept, split, now).forEach { row ->
                val group = kept.filter { com.tjshea.vigilant.data.tracker.BetLedger.keyOf(it, split) == row.label }
                o.appendLine("${split.label} ${row.label}: ${breakdownText(row.stats, closedCount(group, now))}")
            }
        }
        o.appendLine()
        o.appendLine("== Bets by what made their fair odds (recorded from v0.36.0) ==")
        basisLines(kept, now).forEach { o.appendLine(it) }
        // Pinnacle only (Tj, 2026-10-05): EV, CLV and profit of the bets found and judged against Pinnacle's devigged price alone.
        o.appendLine()
        o.appendLine("== Pinnacle only: bets made against Pinnacle's devigged price alone (RESEARCH.md §88.5) ==")
        pinnacleOnlyLines(bets, s.settings, x.autoBet, now, x.counters).forEach { o.appendLine(it) }
        o.appendLine()
        o.appendLine("== Vigilant's own bets against the close (newest ${MAX_CLOSE_ROWS}) ==")
        closeRows(kept, now, zone).forEach { o.appendLine(it) }
        o.appendLine()
        o.appendLine("== Open bets: edge now vs when bet (pregame, current reads only) ==")
        edgeNowLines(bets, now).forEach { o.appendLine(it) }

        o.appendLine()
        o.appendLine("== Memory (the app's heap is fixed: Android ends the app when a request can't fit) ==")
        o.appendLine("Heap ${x.memory.usedMb} of ${x.memory.maxMb} MB (${Math.round(x.memory.fraction * 100)}%)")
        x.memory.lines.forEach { o.appendLine(it) }

        o.appendLine()
        o.appendLine("== Phone ==")
        val p = x.phone
        fun yn(v: Boolean?) = when (v) { true -> "yes"; false -> "NO"; null -> "?" }
        o.appendLine("Notifications ${yn(p.notifications)} · exact alarms ${yn(p.exactAlarms)} · battery unrestricted ${yn(p.batteryUnrestricted)} · draw over apps ${yn(p.overlay)} · Data Saver ${when (p.dataSaver) { true -> "ON"; false -> "off"; null -> "?" }} · online ${yn(p.online)}" + (p.network?.let { " ($it)" } ?: ""))
        o.appendLine("Battery Saver ${when (p.batterySaver) { true -> "ON"; false -> "off"; null -> "?" }} · Doze now ${when (p.dozing) { true -> "yes"; false -> "no"; null -> "?" }} · standby bucket ${p.standbyBucket ?: "?"}")

        o.appendLine()
        o.appendLine("== How the app last ended (Android's own record, newest first) ==")
        if (x.exits.isEmpty()) o.appendLine("Nothing recorded.")
        x.exits.forEach { e ->
            o.appendLine(
                "${at(e.atMs)} · ${e.reason} · ${e.where}" + (e.pssMb?.let { " · ${it} MB" } ?: "") +
                    (if (x.installedAtMs != null && e.atMs < x.installedAtMs) " · before this version was installed" else "") +
                    (e.description?.takeIf { it.isNotBlank() }?.let { " · ${com.tjshea.vigilant.data.diag.ProblemLog.clean(it).take(200)}" } ?: ""),
            )
            // Where the main thread was stuck when Android said "not responding".
            e.trace.forEach { o.appendLine("    $it") }
        }

        o.appendLine()
        o.appendLine("== Recent problems (saved across restarts, newest first) ==")
        if (x.problems.isEmpty()) o.appendLine("None recorded.")
        x.problems.take(MAX_PROBLEMS).forEach { pr ->
            o.appendLine("${at(pr.lastAtMs)} · ${pr.area}: ${com.tjshea.vigilant.data.diag.ProblemLog.mask(pr.message)}" + if (pr.count > 1) " (×${pr.count} since ${at(pr.firstAtMs)})" else "")
        }
        return o.toString().trimEnd()
    }

    /** One breakdown row: bets, record, ROI, EV when bet, CLV on how many true closes and how often it beat the close. */
    private fun breakdownText(st: com.tjshea.vigilant.data.tracker.TrackerStats, closed: Int? = null): String = listOfNotNull(
        "${st.bets} bet${if (st.bets == 1) "" else "s"} (${st.pending} open)",
        "${st.won}-${st.lost}${if (st.pushed > 0) "-${st.pushed}" else ""}".takeIf { st.settled > 0 },
        st.roi?.let { String.format(Locale.US, "ROI %+.1f%%", it * 100) },
        st.averageEv?.let { String.format(Locale.US, "EV when bet %+.1f%%", it * 100) },
        st.averageClv?.let { String.format(Locale.US, "CLV %+.1f%%", it * 100) + (closed?.let { n -> " on $n" } ?: "") },
        st.beatClosePercent?.let { String.format(Locale.US, "beat close %.0f%%", it * 100) },
        String.format(Locale.US, "expected %+.2f vs actual %+.2f", st.expectedProfit, st.profitWithEv).takeIf { st.settledWithEv > 0 },
    ).joinToString(" · ")

    /** How many of [bets] have a true close (the n behind a CLV average). */
    private fun closedCount(bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>, now: Long): Int =
        bets.count { it.status != BetStatus.VOID && com.tjshea.vigilant.data.tracker.ClosingLine.clv(it, now) != null }

    /** One Novig league's games in the last scan (futures aside), how many matched a fair-odds source, and a few that didn't. */
    data class LeagueMatch(val league: String, val games: Int, val matched: Int, val unmatched: List<String>)

    /** Android's thermal status (`PowerManager.currentThermalStatus`) in words: a hot phone throttles the CPU, which is what makes a cycle slow. */
    fun thermalName(status: Int): String = when (status) {
        0 -> "none"; 1 -> "light"; 2 -> "moderate"; 3 -> "severe"; 4 -> "critical"; 5 -> "emergency"; 6 -> "shutdown"; else -> "?"
    }

    /**
     * The last scan's games by league: matched = a source's game was found for it. Futures ("Super Bowl Winner": no "@" or "vs") can't
     * match a game and are left out. Leagues with the most games first.
     */
    fun matchingByLeague(s: UiState): List<LeagueMatch> {
        val games = s.result?.games ?: return emptyList()
        return games.filter { g -> FUTURES_NOT.containsMatchIn(g.event.description) }
            .groupBy { it.league.displayName }
            .map { (league, gs) ->
                val (hit, miss) = gs.partition { g -> g.refEvent != null || g.outcomes.any { it.fairProbability != null } }
                LeagueMatch(league, gs.size, hit.size, miss.map { it.event.description })
            }
            .sortedByDescending { it.games }
    }

    private val FUTURES_NOT = Regex(" @ | vs\\.? ", RegexOption.IGNORE_CASE)

    /**
     * Low API usage bids (Tj, 2026-10-05; RESEARCH.md §92) in a few lines: whether the scan is narrowed now, the books, the feeds really asked (and the books nothing can read),
     * the pace, and the rules. Nothing when that isn't the bids' choice.
     */
    internal fun lowUsageLines(set: ScanSettings, plan: com.tjshea.vigilant.data.scanner.LowUsageBids.Plan?, now: Long): List<String> {
        if (set.makerFocus != com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE) return emptyList()
        val books = com.tjshea.vigilant.data.scanner.LowUsageBids.books(set)
        val titles = { keys: Collection<String> -> com.tjshea.vigilant.data.scanner.LowUsageBids.names(keys.toSet()) }
        val feeds = plan?.feeds?.joinToString(", ") { com.tjshea.vigilant.app.ui.LowUsageText.feedName(it) }?.ifEmpty { "none" } ?: "not worked out"
        val out = ArrayList<String>()
        out += "  Low API usage bids (RESEARCH.md §92): ${if (set.lowUsageNow) "ON: Vigilant's scan reads player props only, the next ${com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(set)} h (the trap guard's hours; Off = as far as Starts within and Days ahead say), from the picked books alone" else "chosen but bids are off: the scan is the usual one"} · " +
            "books ${titles(books)} · scan pace ${if (set.lowUsagePace == com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO) "Auto (every ${com.tjshea.vigilant.data.scanner.LowUsageBids.NEAR_GAP_SECONDS / 60} min with a game inside 3 h, ${com.tjshea.vigilant.data.scanner.LowUsageBids.FAR_GAP_SECONDS / 60} min otherwise)" else "at most every ${set.lowUsagePace} min"} (the usual gap is ${ScanSettings.AUTO_SCAN_VIGILANT_MIN_GAP_SECONDS / 60} min) · " +
            "at least ${pct(set.lowUsageMargin.coerceAtLeast(com.tjshea.vigilant.data.scanner.LowUsageBids.MIN_MARGIN))} under the fair · no bid longer than ${com.tjshea.vigilant.engine.Odds.formatAmerican(com.tjshea.vigilant.app.ui.LowUsageText.lowUsageMaxOdds(set))}"
        out += "    feeds asked: $feeds · a league with no game in the window and a prop market on Novig is not asked · what the last scan cost each API is under \"Last Vigilant scan\" below"
        plan?.unreachable?.takeIf { it.isNotEmpty() }?.let { out += "    CANNOT BE READ: ${titles(it)} (no feed that carries it has a key and a switch on in Settings › Fair odds & sources): lines needing two of the picked books get no bid" }
        return out
    }

    /**
     * Pinnacle only's report (Tj, 2026-10-05: "make the diagnostics scan logging keep track of all betting information used with this Pinnacle only setting on so I can
     * track how well bets do clv and EV and profit when only compared to Pinnacle"): whether it is on and how its auto-bet pass went, then every bet made with it on
     * ([com.tjshea.vigilant.data.tracker.AtBet.pinnacleOnly]) as a group: results and profit (outliers included, like the Tracker's Profit), EV when bet against Pinnacle, CLV
     * and the share that beat the close, then the same split by the age of Pinnacle's price at the bet, by kind of market, and where the closes came from.
     */
    internal fun pinnacleOnlyLines(
        bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>,
        set: com.tjshea.vigilant.data.scanner.ScanSettings,
        autoBet: AutoBettor.Status,
        now: Long,
        counters: Map<String, Long> = emptyMap(),
    ): List<String> {
        val out = ArrayList<String>()
        out += if (set.pinnacleOnly) "On: age limit ${com.tjshea.vigilant.app.ui.PinnacleOnlyText.ageLabel(set.pinnacleMaxAgeSeconds)} · reads Novig and Pinnacle only (PinnWire, then pinnapi; PropLine or ParlayAPI for a league those can't answer) · devig: the lowest of four"
        else "Off (switch it on in Settings › Scanning or the Auto-bet tab)."
        if (set.pinnacleOnly) {
            out += "Auto-bet's last pass: ${AutoBettor.line(autoBet, now, true)}"
            out += "Pinnacle re-reads before betting: ${counters["pinnacle.refresh.ok"] ?: 0} read, ${counters["pinnacle.refresh.failed"] ?: 0} failed · bets placed this run: ${counters["pinnacle.autobet.placed"] ?: 0}"
        }
        val mine = bets.filter { it.atBet?.pinnacleOnly == true && it.status != BetStatus.VOID && it.atBet?.how != com.tjshea.vigilant.data.tracker.AtBet.HOW_STUDY }
        if (mine.isEmpty()) return out + "No bet made with it on yet: the first ones appear here with their EV against Pinnacle, CLV and profit."
        val st = BetTracker.stats(mine)
        out += "Bets: ${mine.size} (${st.pending} open) · " + breakdownText(st, closedCount(mine.filterNot { it.isOutlier }, now))
        out += String.format(Locale.US, "Profit (every settled bet): %+.2f on %.2f staked", st.profitAll, st.stakedAll) + (st.roiAll?.let { String.format(Locale.US, " (%+.1f%%)", it * 100) } ?: "")
        val kept = mine.filterNot { it.isOutlier }
        fun band(b: com.tjshea.vigilant.data.tracker.TrackedBet): String = when (val age = b.atBet?.pinnacleAgeSec) {
            null -> "age not recorded"
            in 0..30 -> "Pinnacle's price ≤30 s old"
            in 31..60 -> "Pinnacle's price 31–60 s old"
            in 61..90 -> "Pinnacle's price 61–90 s old"
            else -> "Pinnacle's price over 90 s old"
        }
        kept.groupBy(::band).toSortedMap().forEach { (label, g) -> out += "$label: ${breakdownText(BetTracker.stats(g), closedCount(g, now))}" }
        com.tjshea.vigilant.data.tracker.TrackerBreakdown.of(kept, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.MARKET).forEach { row ->
            val g = kept.filter { com.tjshea.vigilant.data.tracker.TrackerBreakdown.keyOf(it, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.MARKET) == row.label }
            out += "Market ${row.label}: ${breakdownText(row.stats, closedCount(g, now))}"
        }
        com.tjshea.vigilant.data.tracker.TrackerBreakdown.of(kept, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.LEAD).forEach { row ->
            val g = kept.filter { com.tjshea.vigilant.data.tracker.TrackerBreakdown.keyOf(it, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.LEAD) == row.label }
            out += "Time to start ${row.label}: ${breakdownText(row.stats, closedCount(g, now))}"
        }
        val closes = kept.mapNotNull { b -> com.tjshea.vigilant.data.tracker.ClosingLine.closeOf(b, now)?.second }
        if (closes.isNotEmpty()) out += "Closes came from: " + closes.groupingBy { com.tjshea.vigilant.data.tracker.ClosingLine.sourceLabel(it) }.eachCount().entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }
        return out
    }

    /** Closing-line value by what made each bet's fair odds ([com.tjshea.vigilant.data.tracker.FairBasis]); bets from before it was kept are counted apart. */
    internal fun basisLines(bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>, now: Long): List<String> {
        val live = bets.filter { it.status != BetStatus.VOID }
        val (known, unknown) = live.partition { it.fairBasis != null }
        if (known.isEmpty()) return listOf("None recorded yet (${unknown.size} bets were placed before it was kept): the next report after a few days of bets will split CLV by it.")
        val lines = known.groupBy { it.fairBasis!!.group }.toSortedMap().map { (group, gs) ->
            val clvs = gs.mapNotNull { com.tjshea.vigilant.data.tracker.ClosingLine.clv(it, now) }
            String.format(Locale.US, "%s: %d bets", group, gs.size) +
                (gs.mapNotNull { it.evPercentAtBet }.takeIf { it.isNotEmpty() }?.let { String.format(Locale.US, " · EV when bet %+.1f%%", it.average() * 100) } ?: "") +
                (if (clvs.isNotEmpty()) String.format(Locale.US, " · CLV %+.1f%% on %d, beat close %.0f%%", clvs.average() * 100, clvs.size, clvs.count { it > 0 } * 100.0 / clvs.size) else " · no true close yet")
        }
        return lines + "(${unknown.size} older bets: not recorded)"
    }

    /** Vigilant's own bets with a true close, newest first: what was bet, at what, the EV then, the close and where it came from. */
    internal fun closeRows(bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>, now: Long, zone: TimeZone): List<String> {
        val day = SimpleDateFormat("MMM d", Locale.US).apply { timeZone = zone }
        val rows = bets.filter { it.source == BetTracker.SOURCE_VIGILANT && it.status != BetStatus.VOID }
            .mapNotNull { b -> com.tjshea.vigilant.data.tracker.ClosingLine.closeOf(b, now)?.let { b to it } }
            .sortedByDescending { it.first.startsTs }
            .take(MAX_CLOSE_ROWS)
        if (rows.isEmpty()) return listOf("None with a true close yet.")
        return rows.map { (b, close) ->
            val clv = close.first / b.cost - 1.0
            listOfNotNull(
                day.format(Date(b.startsTs)), b.league.ifBlank { "?" }, "${b.marketLabel}: ${b.selection}",
                b.american?.let { com.tjshea.vigilant.engine.Odds.formatAmerican(it) } ?: com.tjshea.vigilant.app.ui.Format.american(b.price),
                b.evPercentAtBet?.let { String.format(Locale.US, "EV %+.1f%%", it * 100) },
                String.format(Locale.US, "fair %s → close %s", b.fairAtBet?.let { com.tjshea.vigilant.app.ui.Format.american(it) } ?: "?", com.tjshea.vigilant.app.ui.Format.american(close.first)),
                String.format(Locale.US, "CLV %+.1f%%", clv * 100) + " (" + com.tjshea.vigilant.data.tracker.ClosingLine.sourceLabel(close.second) + ")",
                b.fairBasis?.group,
                b.status.name.lowercase(),
            ).joinToString(" · ")
        }
    }

    /**
     * Open pregame bets with a current EV: how their EV now compares with the EV when bet, overall and by scanner. A fair line that moved
     * toward the bet since it was placed is the same sign as beating the close; one that moved away says the edge wasn't there.
     */
    internal fun edgeNowLines(bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>, now: Long): List<String> {
        val live = bets.filter { it.status == BetStatus.PENDING && now < it.startsTs && it.evPercentAtBet != null && it.fairAtBet != null && it.nowFair != null && TrackerText.currentEv(it, now) }
        if (live.isEmpty()) return listOf("No open pregame bet has a current EV (tap Check odds now, then copy Diagnostics again).")
        fun line(label: String, group: List<com.tjshea.vigilant.data.tracker.TrackedBet>): String {
            val atBet = group.map { it.evPercentAtBet!! }.average()
            val nowEv = group.map { it.nowEv!! }.average()
            val toward = group.count { it.nowFair!! > it.fairAtBet!! + 1e-9 }
            val stillPositive = group.count { it.nowEv!! > 0 }
            return String.format(
                Locale.US, "%s: %d bet%s · EV when bet %+.1f%% → now %+.1f%% · fair moved toward the bet on %d, away on %d · still +EV %d",
                label, group.size, if (group.size == 1) "" else "s", atBet * 100, nowEv * 100, toward, group.count { it.nowFair!! < it.fairAtBet!! - 1e-9 }, stillPositive,
            )
        }
        return listOf(line("All", live)) + live.groupBy { com.tjshea.vigilant.data.tracker.TrackerBreakdown.keyOf(it, com.tjshea.vigilant.data.tracker.TrackerBreakdown.By.SCANNER) }
            .toSortedMap().map { (k, v) -> line(k, v) }
    }

    /** One round's when, how long, what it did and what it cost each API, in lines; "none since the app opened" without one. */
    private fun roundText(name: String, r: RoundCost?, now: Long): List<String> {
        if (r == null) return listOf("$name: none since the app opened.")
        val took = if (r.tookMs < 1_000) "${r.tookMs} ms" else "${(r.tookMs + 500) / 1_000} s"
        val lines = ArrayList<String>()
        lines += "$name: ${com.tjshea.vigilant.app.ui.Format.age(r.startedAtMs, now)} · took $took" + (r.note?.let { " · $it" } ?: "")
        lines += "    cost: " + (r.costs.takeIf { it.isNotEmpty() }?.joinToString(", ") { costText(it) } ?: "no API was asked (everything was re-used)")
        return lines
    }

    private fun costText(c: ProviderCost): String {
        val name = QuotaPolicy.ALL.firstOrNull { it.id == c.id }?.displayName ?: c.id
        return "$name ${c.calls}" + if (c.units != c.calls) " (${c.units} of its allowance)" else ""
    }

    /** What a background cycle reads at these settings, in words. */
    fun runsText(s: ScanSettings): String = when {
        s.autoScansCno && s.autoScansVigilant -> "CNO + Vigilant"
        s.autoScansCno -> "CNO only" + if (s.autoScan.vigilant) " (Vigilant skipped: the scanner is on CNO only)" else ""
        s.autoScansVigilant -> "Vigilant only" + " (CNO skipped: the scanner is on Vigilant only)"
        s.autoScan == AutoScanMode.OFF -> "nothing"
        s.paused -> "nothing (paused)"
        else -> "nothing (the scanner choice leaves nothing to read)"
    }

    private fun limit(n: Int) = if (n >= ScanSettings.NO_LIMIT) "no limit" else n.toString()
    private fun pct(v: Double) = String.format(Locale.US, "%.1f%%", v * 100)
    private fun money(v: Double) = String.format(Locale.US, "%.2f", v)

    private const val MAX_ERRORS = 8
    private const val MAX_PROBLEMS = 25
    private const val MAX_CLOSE_ROWS = 30
}

/**
 * What the bids did, in one line for Diagnostics (Tj, 2026-10-03: "I posted plenty of bids and not one of them was taken"): how many were posted by
 * auto-make and by hand, how long they rested, whether they led their side's book when posted, and how far under Novig's price to take them they sat.
 */
object MakerStats {
    /**
     * The chance that one prop bid 4% under the fair fills while it rests [hours] (RESEARCH.md §70.3, static bids: 4% at 15 min, 11% at 1 h, 25% at 3 h,
     * 37% at 6 h, 40% until the close), straight between those rows. A rough yardstick: team totals and periods fill differently, the margin is Tj's.
     */
    fun fillChance(hours: Double): Double {
        val rows = listOf(0.0 to 0.0, 0.25 to 0.04, 1.0 to 0.11, 3.0 to 0.25, 6.0 to 0.37, 24.0 to 0.40)
        if (hours <= 0.0) return 0.0
        val i = rows.indexOfFirst { it.first >= hours }
        if (i < 0) return rows.last().second
        val (h0, p0) = rows[i - 1]
        val (h1, p1) = rows[i]
        return p0 + (p1 - p0) * (hours - h0) / (h1 - h0)
    }

    /**
     * What the last 24 hours of bids add up to (Tj, 2026-10-03: "how long do they usually take to get filled?"): the bids' total time up (bid-hours: what
     * fills depend on, not how many were posted), the fills the research would expect from lives like those, the fills there were, and why the ended ones
     * ended. The 14-day line above mixes versions, including the one-minute re-posting; this is the recent one. Empty when none was posted in the day.
     */
    fun recent(bids: List<com.tjshea.vigilant.data.novig.trading.maker.MakerBid>, now: Long): List<String> {
        val posted = bids.filter { it.orderId != null && it.postedAtMs >= now - DAY_MS }
        if (posted.isEmpty()) return emptyList()
        val hours = posted.map { ((it.endedAtMs ?: now) - it.postedAtMs).coerceAtLeast(0L) / 3_600_000.0 }
        val up = hours.sum()
        val expected = hours.sumOf(::fillChance)
        val filled = posted.count { it.filled > 0 }
        val longest = hours.max() * 60
        val verdict = when {
            expected < 3.0 -> "too few bid-hours to judge: ${if (filled == 0) "no fill" else "this many"} is the likely outcome"
            filled < expected / 3 -> "well under the research's rate: look at the book position (led their side) and the margin"
            // Tj, 2026-10-05: "my bids right now are being taken fast". Many more fills than lives like these should get is a symptom of stale bids, not of luck.
            filled >= 5 && filled > expected * 2 -> "MORE than twice the research's rate: bids taken this fast are often ones the market was already moving away from (picked off): compare the fills' EV at post with their EV at the fill below, and watch the CLV"
            else -> "in line with the research"
        }
        val reasons = posted.filter { it.status.ended }.groupingBy { it.status.label + (it.why?.let { w -> ": $w" } ?: "") }.eachCount().entries
            .sortedByDescending { it.value }.take(4).joinToString(" · ") { "×${it.value} ${it.key}" }
        return listOfNotNull(
            "last 24 h: ${posted.size} bids posted, ${"%.1f".format(java.util.Locale.US, up)} bid-hours up (longest ${"%.0f".format(java.util.Locale.US, longest)} min) · filled $filled · " +
                "the research (§70.3: a prop bid 4% under the fair fills ~4% in 15 min, 11% in 1 h, 25% in 3 h, 37% in 6 h) expects ≈${"%.1f".format(java.util.Locale.US, expected)} from lives like these: $verdict",
            reasons.takeIf { it.isNotEmpty() }?.let { "last 24 h ended: $it" },
        )
    }

    private const val DAY_MS = 24 * 3_600_000L

    fun line(bids: List<com.tjshea.vigilant.data.novig.trading.maker.MakerBid>, now: Long): String? {
        val posted = bids.filter { it.orderId != null }
        if (posted.isEmpty()) return null
        fun pct(n: Int, of: Int) = if (of == 0) "?" else "${Math.round(n * 100.0 / of)}%"
        val lives = posted.map { ((it.endedAtMs ?: now) - it.postedAtMs).coerceAtLeast(0L) / 60_000.0 }.sorted()
        fun at(q: Double) = lives[((lives.size - 1) * q).toInt()]
        val known = posted.filter { it.offerAtPost != null || it.bestBidAtPost != null || it.bookAtMs != null }
        val led = known.count { b -> b.bestBidAtPost.let { it == null || it < b.price - 1e-9 } }
        val gaps = known.mapNotNull { b -> b.offerAtPost?.let { it - b.price } }.sorted()
        val bookAges = known.mapNotNull { b -> b.bookAtMs?.let { (b.postedAtMs - it).coerceAtLeast(0L) / 60_000.0 } }.sorted()
        return "bids posted ${posted.size} (auto-make ${posted.count { it.auto }}, by hand ${posted.count { !it.auto }}) · " +
            "rested ${"%.0f".format(java.util.Locale.US, at(0.5))} min at the median, ${"%.0f".format(java.util.Locale.US, at(0.9))} at the 90th · filled ${posted.count { it.filled > 0 }}" +
            (if (known.isEmpty()) " · their book when posted: not recorded (from v0.53.0)" else
                " · led their side (no bid as high) ${pct(led, known.size)} of ${known.size}" +
                    (gaps.takeIf { it.isNotEmpty() }?.let { g -> " · under Novig's price to take ${"%.1f".format(java.util.Locale.US, g[g.size / 2] * 100)}¢ at the median" } ?: "") +
                    (bookAges.takeIf { it.isNotEmpty() }?.let { a -> " · priced with a book ${"%.0f".format(java.util.Locale.US, a[a.size / 2])} min old at the median" } ?: ""))
    }
}
