package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.app.ui.TrackerText
import com.tjshea.vigilant.data.keys.Runway
import com.tjshea.vigilant.data.keys.RunwayLevel
import com.tjshea.vigilant.data.scanner.AutoScanMode
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
        if (set.cnoOn && !set.paused) cno(s, now)
        background(s, x, now)
        phone(s, x)
        tracker(s, x, now)
        accuracy(s, now)
        betting(s)
    }.sortedBy { it.level.ordinal }

    private fun MutableList<Check>.scanning(s: UiState, now: Long) {
        val set = s.settings
        if (set.paused) add(Check(Level.WARN, "Scanning", "all scanning is paused", look = "Settings › Scanner › Pause (ScanSettings.paused)"))
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
            add(Check(if (novig) Level.FAIL else Level.WARN, "Vigilant scan", "${st.errors.size} error${plural(st.errors.size)} in the last scan", st.errors.first().take(160), "data/scanner/Scanner.kt; Recent problems below"))
        }
        st.backoffSeconds?.let { add(Check(Level.WARN, "Novig", "Novig asked the app to wait $it s", look = "data/novig/RateGate.kt (the key's limits: NOVIG_API.md)")) }
        val stats = s.result?.stats
        if (stats != null && stats.novigEvents >= 5) {
            val share = stats.matchedEvents.toDouble() / stats.novigEvents
            val level = if (share < 0.5) Level.WARN else Level.OK
            add(Check(level, "Game matching", "${pct0(share)} of Novig's games matched to a fair-odds source", "${stats.matchedEvents} of ${stats.novigEvents}", if (level == Level.WARN) "data/match/TeamMatcher.kt, the sources' leagues" else null))
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

    private fun MutableList<Check>.sources(s: UiState) {
        if (!s.settings.vigilantOn || s.status.scannedAtMs == null) return
        for (r in s.status.sources) {
            when {
                r.error != null -> add(Check(Level.FAIL, "Source ${r.name}", "failed in the last scan", r.error!!.take(160), "data/reference/ (its client); keys in API usage"))
                r.fetched > 0 && r.matched == 0 -> add(Check(Level.WARN, "Source ${r.name}", "answered ${r.fetched} league${plural(r.fetched)} but matched no Novig game", look = "data/match/TeamMatcher.kt, PlayerNames.kt"))
                r.heldBack != null -> add(Check(Level.OK, "Source ${r.name}", "held back to save credits", r.heldBack!!.take(120)))
                r.fetched + r.reused > 0 -> add(Check(Level.OK, "Source ${r.name}", "${r.matched} game${plural(r.matched)} matched"))
            }
        }
    }

    private fun MutableList<Check>.apis(s: UiState, x: Diagnostics.Extras, now: Long) {
        for ((id, p) in s.usage.providers) {
            val name = com.tjshea.vigilant.data.keys.QuotaPolicy.ALL.firstOrNull { it.id == id }?.displayName ?: id
            p.keys.forEach { (key, u) ->
                if (u.refused) add(Check(Level.FAIL, "API $name", "key …${key.takeLast(4)} was refused", u.lastNote?.take(120), "Settings › API keys: replace it"))
                else u.depletedUntil?.takeIf { it > now }?.let { add(Check(Level.WARN, "API $name", "key …${key.takeLast(4)} is spent until ${Format.age(now, it)} from now", look = "add a key, or wait for its reset")) }
            }
            if (p.throttledToday > 0) add(Check(Level.WARN, "API $name", "${p.throttledToday} call${plural(p.throttledToday)} throttled or refused today", "${p.callsToday} calls today", "its pacing in data/keys/ (QuotaPolicy, KeyPool)"))
        }
        x.parlayAccounts.forEach { (key, a) ->
            if (a.valid == false) add(Check(Level.FAIL, "API ParlayAPI", "key …${key.takeLast(4)} is not valid" + (a.reason?.let { ": $it" } ?: ""), look = "parlay-api.com account; Settings › API keys"))
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
            add(Check(Level.OK, "Background auto-scan", "off", look = "Settings › Auto-scan (no +EV alerts while Vigilant is closed)"))
            return
        }
        if (set.activeAutoScan == AutoScanMode.OFF) {
            add(Check(Level.WARN, "Background auto-scan", "set to ${set.autoScan.displayName} but runs nothing: ${Diagnostics.runsText(set)}", look = "the scanner choice or Pause"))
            return
        }
        val a = x.autoScan
        if (!x.autoScanServiceRunning && !a.running) {
            add(Check(Level.FAIL, "Background auto-scan", "on (${Diagnostics.runsText(set)} every ${set.autoScanMinutes} min) but its service isn't running", "last started ${a.lastStartMs?.let { Format.age(it, now) } ?: "never"}", "app/AutoScanService.kt, AutoScanAlarm.kt; battery and notification permissions below"))
        }
        val late = a.lastStartMs?.let { now - it > 3L * set.autoScanMinutes * 60_000L } ?: true
        if (late && x.autoScanServiceRunning) add(Check(Level.WARN, "Background auto-scan", "no cycle in over ${3 * set.autoScanMinutes} min", "last started ${a.lastStartMs?.let { Format.age(it, now) } ?: "never"}", "AutoScanAlarm (exact alarms), battery restrictions"))
        a.lastError?.let { add(Check(Level.WARN, "Background auto-scan", "the last cycle had an error", it.take(160), "app/AutoScan.kt")) }
        if (!late && a.lastError == null && x.autoScanServiceRunning) add(Check(Level.OK, "Background auto-scan", "running: last cycle ${a.lastStartMs?.let { Format.age(it, now) }}, found ${a.lastFound}, alerts ${a.lastAlerts}"))
    }

    private fun MutableList<Check>.phone(s: UiState, x: Diagnostics.Extras) {
        val p = x.phone
        val set = s.settings
        if (p.notifications == false) {
            add(Check(if (set.alertMinEv > 0 && set.autoScan != AutoScanMode.OFF) Level.FAIL else Level.WARN, "Phone", "notifications are off for Vigilant: no +EV alerts, no scan-done notes", look = "Android Settings › Apps › Vigilant › Notifications"))
        }
        if (p.exactAlarms == false) add(Check(Level.WARN, "Phone", "exact alarms aren't allowed: the closing-line capture and auto-scan can run late", look = "Android Settings › Apps › Special access › Alarms & reminders"))
        if (p.batteryUnrestricted == false && set.autoScan != AutoScanMode.OFF) add(Check(Level.WARN, "Phone", "battery optimization is on for Vigilant: Android may stop background scans", look = "Android Settings › Apps › Vigilant › Battery › Unrestricted"))
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
        val week = bets.filter { it.status != BetStatus.VOID && it.createdAtMs < it.startsTs && now >= it.startsTs && now - it.startsTs < 7 * 24 * HOUR }
        if (week.size >= 5) {
            val closed = week.count { ClosingLine.closeOf(it, now) != null }
            val share = closed.toDouble() / week.size
            val top = week.filter { ClosingLine.closeOf(it, now) == null }.groupingBy { it.closeNote ?: "not looked for yet" }.eachCount().maxByOrNull { it.value }
            add(
                Check(
                    if (share < 0.7) Level.WARN else Level.OK, "Closing lines", "${pct0(share)} of last week's started bets have a true close", "$closed of ${week.size}" + (top?.let { "; missing ×${it.value}: ${it.key.take(100)}" } ?: ""),
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
        if (withClv.size >= 20) {
            val clv = withClv.map { it.second }.average()
            val ev = withClv.mapNotNull { it.first.evPercentAtBet }.takeIf { it.isNotEmpty() }?.average()
            val beat = withClv.count { it.second > 0 }.toDouble() / withClv.size
            val gap = ev?.let { it - clv }
            val level = when {
                clv < 0 -> Level.FAIL
                gap != null && gap > 0.02 -> Level.WARN
                else -> Level.OK
            }
            add(
                Check(
                    level, "Edge accuracy (CLV)",
                    when (level) {
                        Level.FAIL -> "bets lose to the close on average: the edges shown aren't real"
                        Level.WARN -> "the EV shown when bet runs ${pts(gap!!)} above what the close says: edges are overstated"
                        Level.OK -> "bets beat the close: the edges hold up"
                    },
                    "average CLV ${pctSigned(clv)}, EV when bet ${ev?.let(::pctSigned) ?: "?"}, beat the close ${pct0(beat)}, ${withClv.size} bets",
                    if (level != Level.OK) "fair odds (engine/FairValue.kt, ScanSettings.fairSource/devigMethod/minBooks); Accuracy by scanner below" else null,
                ),
            )
        } else if (withClv.isNotEmpty()) {
            add(Check(Level.OK, "Edge accuracy (CLV)", "${withClv.size} bets with a true close so far: 20 needed to judge"))
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
        if (b < 1.0) add(Check(Level.WARN, "Vigilant wallet", "holds ${String.format(Locale.US, "$%.2f", b)}: bets start at what's left", look = "Settings › Betting › Add money"))
    }

    /** "Novig refused" words in a scan error: a 403/429/451 from Novig is the scan's own reads failing, not a source's. */
    private val NOVIG_REFUSED = Regex("(?i)novig.*(403|429|451|refused|blocked)")

    private const val HOUR = 3_600_000L
    private fun plural(n: Int) = if (n == 1) "" else "s"
    private fun pct0(v: Double) = String.format(Locale.US, "%.0f%%", v * 100)
    private fun pctSigned(v: Double) = String.format(Locale.US, "%+.1f%%", v * 100)
    private fun pts(v: Double) = String.format(Locale.US, "%.1f points", v * 100)
}
