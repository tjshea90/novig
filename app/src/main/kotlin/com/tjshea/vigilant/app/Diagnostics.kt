package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.TrackerText
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.ProviderCost
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.RoundCost
import com.tjshea.vigilant.data.keys.Runway
import com.tjshea.vigilant.data.scanner.AutoScanMode
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

    /** What isn't in [UiState]: the build, the phone, and the background scan's own status. */
    data class Extras(
        val versionName: String,
        val versionCode: Int,
        val device: String,
        val autoScan: AutoScanner.Status = AutoScanner.Status(),
        val autoScanServiceRunning: Boolean = false,
        /** What the last scan and the last Check odds now cost each API (since the app opened), null before one ran. */
        val lastScan: RoundCost? = null,
        val lastCheck: RoundCost? = null,
        /** When the closing capture's alarm is armed for ([ClosingAlarm]), as this process set it. */
        val closingAlarmAtMs: Long? = null,
        /** The last look for closes after the start ([com.tjshea.vigilant.data.tracker.CloseBackfill]) and the bytes Novig's trade files cost. */
        val backfill: com.tjshea.vigilant.data.tracker.CloseBackfill.Report? = null,
        val novigTradeBytes: Long = 0,
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
    )

    /** A message shown on screen that says something failed (what goes in Recent problems), not a confirmation. */
    fun isProblem(text: String): Boolean = PROBLEM_WORDS.containsMatchIn(text)

    private val PROBLEM_WORDS = Regex("(?i)couldn't|could not|can't|failed|error|refused|not found|timed? ?out|unable|isn't|wasn't|no internet|HTTP [45]\\d\\d")

    fun report(s: UiState, x: Extras, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val time = SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).apply { timeZone = zone }
        fun at(ms: Long?) = ms?.let { time.format(Date(it)) } ?: "never"
        fun ago(ms: Long?) = ms?.let { com.tjshea.vigilant.app.ui.Format.age(it, now) } ?: "never"
        val o = StringBuilder()
        o.appendLine("VIGILANT DIAGNOSTICS · ${at(now)}")
        o.appendLine("Version ${x.versionName} (code ${x.versionCode}) · ${x.device}")
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
        o.appendLine("Scanner: ${set.scanner.displayName} · paused: ${if (set.paused) "YES" else "no"}")
        o.appendLine(
            "Background auto-scan: ${set.autoScan.displayName}" + (if (set.autoScan != AutoScanMode.OFF) " every ${set.autoScanMinutes} min" else "") +
                " → actually runs: ${runsText(set)} · service ${if (x.autoScanServiceRunning) "running" else "not running"}",
        )
        o.appendLine("Leagues: ${set.leagues.sorted().joinToString(", ").ifEmpty { "none" }} · days ahead ${set.daysAhead} · starts within ${if (set.startsWithinHours <= 0) "any time" else "${set.startsWithinHours} h"} · live games ${if (set.includeLive) "on" else "off"}")
        o.appendLine("Edge shown: ${pct(set.minEvPercent)} to ${pct(set.maxEvPercent)} · max odds +${set.maxOdds} · fair odds ${set.fairSource} / ${set.devigMethod}, at least ${set.minBooks} book${if (set.minBooks == 1) "" else "s"}")
        o.appendLine("Scan size: ${limit(set.maxBooksPerScan)} Novig prices · lines/game ${limit(set.linesPerGame)} · props/game ${limit(set.propsPerGame)} · fill the budget ${if (set.fillBudget) "on" else "off"} · window ${set.scanWindowHours} h")
        o.appendLine("Fair-odds sources on: ${set.enabledSources.sorted().joinToString(", ").ifEmpty { "none" }} · sportsbook props ${if (set.useBookProps) "on" else "off"} (credits/scan ${limit(set.bookPropCreditsPerScan)}, PropLine games ${limit(set.propLineGamesPerScan)})")
        o.appendLine("Keys saved: " + ApiProvider.entries.joinToString(" · ") { "${it.displayName} ${s.keysOf(it).size}" })
        o.appendLine("CrazyNinjaOdds: ${if (set.cnoOn) "on" else "off"} · refresh ${when { set.cnoRefreshSeconds == com.tjshea.vigilant.data.cno.CnoFeed.REALTIME -> "real time"; set.cnoRefreshSeconds <= 0 -> "taps only"; else -> "${set.cnoRefreshSeconds} s" }} · only bets the books agree on ${if (set.cnoOnlyAgreed) "on" else "off"} · alerts ≥ ${pct(set.alertMinEv)}")
        o.appendLine(
            "Betting through the API: ${if (s.betting.enabled) "on" else "off"}" + (s.betting.balance?.let { String.format(Locale.US, " · wallet $%.2f", it) } ?: "") +
                " · amount $${money(set.apiBetStake)}, most per bet $${money(set.apiMaxStake)}, most per day $${money(set.apiMaxPerDay)}, smallest edge ${pct(set.apiMinEv)}",
        )
        o.appendLine(
            "Novig key: ${if (s.novig.connection != null) "connected" else "not connected"} · management key " +
                (s.novig.managementKey?.let { if (it.unreadable) "saved but can't be unlocked (enter it again)" else "saved on this phone (••••${it.keyIdEnd})" } ?: "not saved"),
        )

        o.appendLine()
        o.appendLine("== Last Vigilant scan ==")
        val st = s.status
        if (st.scannedAtMs == null) {
            o.appendLine("No scan since the app opened.")
        } else {
            o.appendLine("Finished ${ago(st.scannedAtMs)} (${at(st.scannedAtMs)}) · window ${st.scannedWindowHours ?: "?"} h" + if (st.scanning) " · another is running now" else "")
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
                o.appendLine("  ${r.name}: ${r.fetched} fetched, ${r.reused} re-used${if (r.standingBy > 0) ", ${r.standingBy} standing by" else ""}, ${r.matched} games matched" + (r.error?.let { " · ERROR: $it" } ?: "") + (r.heldBack?.let { " · $it" } ?: ""))
            }
            if (st.errors.isEmpty()) o.appendLine("Errors: none") else st.errors.take(MAX_ERRORS).forEach { o.appendLine("Error: $it") }
            st.backoffSeconds?.let { o.appendLine("Novig asked us to wait $it s") }
        }

        o.appendLine()
        o.appendLine("== API usage (each provider's own allowance) ==")
        if (s.usage.providers.isEmpty()) o.appendLine("Nothing counted yet.")
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
            "Closes found after the start: " + (x.backfill?.let { r -> "last look ${r.looked} bet${if (r.looked == 1) "" else "s"}, found ${r.found}" + (r.bySource.takeIf { it.isNotEmpty() }?.entries?.joinToString(", ", " (", ")") { "${it.key} ${it.value}" } ?: "") } ?: "none looked for since the app opened") +
                " · Novig trade data read ${x.novigTradeBytes / 1024} KB" +
                (if (x.parlayCloseRequests > 0) " · ParlayAPI close calls ${x.parlayCloseRequests}" else ""),
        )
        val stillLooking = bets.filter { now >= it.startsTs && !it.closeFinal && com.tjshea.vigilant.data.tracker.ClosingLine.closeOf(it, now) == null && it.status != BetStatus.VOID && it.createdAtMs < it.startsTs }
        if (stillLooking.isNotEmpty()) {
            o.appendLine("  still looking for ${stillLooking.size} close${if (stillLooking.size == 1) "" else "s"}:")
            stillLooking.groupingBy { it.closeNote ?: "not looked for yet" }.eachCount().entries.sortedByDescending { it.value }.take(5).forEach { o.appendLine("    ×${it.value}: ${it.key}") }
        }
        o.appendLine("Results: ${stats.won}-${stats.lost}${if (stats.pushed > 0) "-${stats.pushed}" else ""} · profit ${String.format(Locale.US, "%+.2f", stats.profit)} on ${String.format(Locale.US, "%.2f", stats.staked)} staked" + (stats.roi?.let { String.format(Locale.US, " (%+.1f%%)", it * 100) } ?: "") + (stats.averageEv?.let { String.format(Locale.US, " · average EV when bet %+.1f%%", it * 100) } ?: "") + (stats.averageClv?.let { String.format(Locale.US, " · average CLV %+.1f%%", it * 100) } ?: ""))
        stats.luck?.let { o.appendLine(String.format(Locale.US, "Expected %+.2f vs actual %+.2f over %d settled bets with an EV: %+.1f standard deviations", stats.expectedProfit, stats.profitWithEv, stats.settledWithEv, it)) }

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
        o.appendLine()
        o.appendLine("== Bets by what made their fair odds (recorded from v0.36.0) ==")
        basisLines(kept, now).forEach { o.appendLine(it) }
        o.appendLine()
        o.appendLine("== Vigilant's own bets against the close (newest ${MAX_CLOSE_ROWS}) ==")
        closeRows(kept, now, zone).forEach { o.appendLine(it) }
        o.appendLine()
        o.appendLine("== Open bets: edge now vs when bet (pregame, current reads only) ==")
        edgeNowLines(bets, now).forEach { o.appendLine(it) }

        o.appendLine()
        o.appendLine("== Phone ==")
        val p = x.phone
        fun yn(v: Boolean?) = when (v) { true -> "yes"; false -> "NO"; null -> "?" }
        o.appendLine("Notifications ${yn(p.notifications)} · exact alarms ${yn(p.exactAlarms)} · battery unrestricted ${yn(p.batteryUnrestricted)} · draw over apps ${yn(p.overlay)} · Data Saver ${when (p.dataSaver) { true -> "ON"; false -> "off"; null -> "?" }} · online ${yn(p.online)}" + (p.network?.let { " ($it)" } ?: ""))

        o.appendLine()
        o.appendLine("== Recent problems (saved across restarts, newest first) ==")
        if (x.problems.isEmpty()) o.appendLine("None recorded.")
        x.problems.take(MAX_PROBLEMS).forEach { pr ->
            o.appendLine("${at(pr.lastAtMs)} · ${pr.area}: ${pr.message}" + if (pr.count > 1) " (×${pr.count} since ${at(pr.firstAtMs)})" else "")
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
