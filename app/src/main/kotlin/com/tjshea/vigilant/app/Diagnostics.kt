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
    )

    fun report(s: UiState, x: Extras, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val time = SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).apply { timeZone = zone }
        fun at(ms: Long?) = ms?.let { time.format(Date(it)) } ?: "never"
        fun ago(ms: Long?) = ms?.let { com.tjshea.vigilant.app.ui.Format.age(it, now) } ?: "never"
        val o = StringBuilder()
        o.appendLine("VIGILANT DIAGNOSTICS · ${at(now)}")
        o.appendLine("Version ${x.versionName} (code ${x.versionCode}) · ${x.device}")
        val set = s.settings

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
        return o.toString().trimEnd()
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
}
