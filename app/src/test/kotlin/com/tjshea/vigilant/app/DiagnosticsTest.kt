package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderCost
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.RoundCost
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.tracker.BetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * The Diagnostics page (Tj, 2026-09-29: "tell me what you need me to do or show you to optimize the app and make sure everything is working as
 * designed"): one copy-able page of the settings, the last scan, API usage, the background scan and the Tracker. Never a key.
 */
class DiagnosticsTest {

    private val now = SampleScan.NOW
    private val extras = Diagnostics.Extras("0.21.3", 49, "Motorola moto g 2026 · Android 16 (API 36)")
    private fun report(s: UiState = SampleScan.state(), x: Diagnostics.Extras = extras) = Diagnostics.report(s, x, now, TimeZone.getTimeZone("UTC"))

    @Test
    fun `the page has every section, the build and what the phone is`() {
        val text = report()
        listOf(
            "VIGILANT DIAGNOSTICS", "Version 0.21.3 (code 49) · Motorola moto g 2026 · Android 16 (API 36)", "== Settings ==", "== Last Vigilant scan ==",
            "== API usage (each provider's own allowance) ==", "== Runway (will each API's allowance last?) ==", "== Last rounds (what they cost each API) ==",
            "== CrazyNinjaOdds ==", "== Background auto-scan ==", "== Tracker ==",
        ).forEach { assertTrue("$it in:\n$text", text.contains(it)) }
        assertTrue(text, text.contains("Bets: 6 (open 3: 3 upcoming, 0 started; settled 3)"))
        assertTrue(text, text.contains("Current EV: 2 of 3 upcoming bets have one read inside the fair odds' age limit"))
    }

    @Test
    fun `it says what the background scan really runs at each scanner choice`() {
        fun runs(scanner: ScannerMode, auto: AutoScanMode) = Diagnostics.runsText(SampleScan.settings.copy(scanner = scanner, autoScan = auto))
        assertEquals("CNO + Vigilant", runs(ScannerMode.BOTH, AutoScanMode.BOTH))
        assertEquals("CNO only (Vigilant skipped: the scanner is on CNO only)", runs(ScannerMode.CNO, AutoScanMode.BOTH))
        assertEquals("CNO only", runs(ScannerMode.CNO, AutoScanMode.CNO))
        assertEquals("Vigilant only (CNO skipped: the scanner is on Vigilant only)", runs(ScannerMode.VIGILANT, AutoScanMode.BOTH))
        assertEquals("nothing (the scanner choice leaves nothing to read)", runs(ScannerMode.VIGILANT, AutoScanMode.CNO))
        assertEquals("nothing", runs(ScannerMode.BOTH, AutoScanMode.OFF))
        val cnoOnly = report(SampleScan.state().let { it.copy(settings = it.settings.copy(scanner = ScannerMode.CNO, autoScan = AutoScanMode.BOTH)) })
        assertTrue(cnoOnly, cnoOnly.contains("actually runs: CNO only (Vigilant skipped: the scanner is on CNO only)"))
    }

    @Test
    fun `no key ever appears, only how many are saved and the last four characters in the usage meters`() {
        val secret = "sk_live_SUPERSECRETKEYVALUE1234"
        val base = SampleScan.state()
        val s = base.copy(
            oddsApiKeys = listOf(secret),
            usage = UsageBook(mapOf("oddsapi" to ProviderUsage(keys = mapOf(secret to KeyUsage(used = 40, remaining = 460, limit = 500)), callsToday = 40))),
        )
        val text = report(s)
        assertFalse(text, text.contains(secret))
        assertFalse(text, text.contains("SUPERSECRET"))
        assertTrue(text, text.contains("The Odds API 1"))
        assertTrue(text, text.contains("key …1234: used 40, 460 left"))
        assertTrue(text, text.contains("oddsapi: 40 calls today"))
    }

    @Test
    fun `it carries the Tracker's Check odds now counter once a check has run`() {
        val base = SampleScan.state()
        assertFalse(report(base.copy(checkStartedAtMs = null)).contains("Check odds now counter"))
        val bets = base.bets.map { if (it.id == "b3") it.copy(nowEv = 0.02, nowAtMs = now) else if (it.id == "b4") it.copy(nowEv = -0.07, nowAtMs = now) else it }
        val text = report(base.copy(bets = bets, checkStartedAtMs = now - 60_000))
        assertTrue(text, text.contains("1 +EV · 1 −EV · 50% +EV · Avg +2.0% EV (1 over ±5% left out)"))
    }

    @Test
    fun `it says what the look for closes after the start found, and what it's still waiting for`() {
        val base = SampleScan.state()
        val bets = base.bets.map { if (it.id == "b1") it.copy(closeNote = "Novig publishes this day's trades the next morning", closeLookedAtMs = now - 3_600_000L) else it }
        val x = extras.copy(backfill = com.tjshea.vigilant.data.tracker.CloseBackfill.Report(5, 3, mapOf("ESPN" to 2, "Novig's last trades" to 1)), novigTradeBytes = 2_048_000)
        val text = Diagnostics.report(base.copy(bets = bets), x, now, TimeZone.getTimeZone("UTC"))
        assertTrue(text, text.contains("Closes found after the start: last look 5 bets, found 3 (ESPN 2, Novig's last trades 1) · Novig trade data read 2000 KB"))
        assertTrue(text, text.contains("×1: Novig publishes this day's trades the next morning"))
        assertFalse(text, text.contains("ParlayAPI close calls"))
        val parlay = Diagnostics.report(base.copy(bets = bets), x.copy(parlayCloseRequests = 4), now, TimeZone.getTimeZone("UTC"))
        assertTrue(parlay, parlay.contains("Novig trade data read 2000 KB · ParlayAPI close calls 4"))
        assertTrue(report(base).contains("Closes found after the start: none looked for since the app opened"))
    }

    @Test
    fun `it says whether the management key is saved, by its last four only`() {
        val base = SampleScan.state()
        assertTrue(report(base).contains("management key not saved"))
        val saved = base.copy(novig = base.novig.copy(managementKey = com.tjshea.vigilant.data.novig.signing.ManagementKeyHint("5678", 0L)))
        assertTrue(report(saved).contains("management key saved on this phone (••••5678)"))
        val locked = base.copy(novig = base.novig.copy(managementKey = com.tjshea.vigilant.data.novig.signing.ManagementKeyHint("5678", 0L, unreadable = true)))
        assertTrue(report(locked).contains("management key saved but can't be unlocked"))
    }

    @Test
    fun `the Tracker part names what is priced, what is not and why, and what has been waiting since its game`() {
        val base = SampleScan.state()
        val noted = base.bets.map { if (it.id == "b3") it.copy(nowNote = "No fair-odds source has current prices for this game", nowNoteAtMs = now) else it }
        val stuck = base.bets.first { it.id == "b3" }.copy(
            id = "stuck", startsTs = now - 9 * 3_600_000L, status = BetStatus.PENDING, gradeNote = "Cam Talbot isn't in the box score", gradeManual = true,
            nowNote = null, nowEv = null,
        )
        val text = report(base.copy(bets = noted + stuck))
        assertTrue(text, text.contains("not priced ×1: No fair-odds source has current prices for this game"))
        assertTrue(text, text.contains("Started and still open: 1 (1 for over 6 hours, 1 need a tap)"))
        assertTrue(text, text.contains("waiting ×1: Cam Talbot isn't in the box score"))
        assertTrue(text, text.contains("Results: "))
    }

    /** Tj, 2026-09-29: "tell me which apis deplete too quickly for daily use so I can add more keys". */
    @Test
    fun `the runway names the API running short and what a scan and a Check odds now each cost it`() {
        val noon = java.time.Instant.parse("2026-09-29T12:00:00Z").toEpochMilli()
        val day = QuotaPolicy.PINNWIRE.periodStart(noon)
        val s = SampleScan.state().copy(
            pinnwireKeys = listOf("wire-key-0001"),
            // No pinnapi key behind PinnWire here (with one, pinnapi's allowance counts too: RunwayTest).
            pinnapiKeys = emptyList(),
            proplineKeys = listOf("prop-key-0002"),
            usage = UsageBook(
                mapOf(
                    "pinnwire" to ProviderUsage(keys = mapOf("wire-key-0001" to KeyUsage(periodStart = day, used = 90)), dayStart = day, callsToday = 90),
                    "propline" to ProviderUsage(keys = mapOf("prop-key-0002" to KeyUsage(periodStart = day, used = 100, remaining = 900, limit = 1000)), dayStart = day, callsToday = 60),
                ),
            ),
        )
        val x = extras.copy(
            lastScan = RoundCost(noon - 120_000, 30_000, listOf(ProviderCost("kalshi", 57, 57), ProviderCost("pinnwire", 6, 6))),
            lastCheck = RoundCost(
                noon - 30_000, 74_000, listOf(ProviderCost("kalshi", 103, 103), ProviderCost("propline", 14, 24)),
                note = "covered 106 of 109 open bets: CNO read 50, 56 priced from Vigilant's own fair odds",
            ),
        )
        val text = Diagnostics.report(s, x, noon, TimeZone.getTimeZone("UTC"))
        // 90 by noon runs out at 1:20 PM, well before midnight; PropLine's 100 is on course for 200 of 1,000.
        assertTrue(text, text.contains("Pinnacle (PinnWire): 90 of 100 requests used today (1 key), 10 left"))
        assertTrue(text, text.contains("the last of it goes in 1h 20m, before the reset: SHORT (add keys, or scan less)"))
        assertTrue(text, text.contains("PropLine: 100 of 1,000 requests used today (1 key), 900 left") && text.contains("at this pace about 200 by the reset: OK"))
        assertTrue(text, text.contains("    a scan costs 6 → 16 a day"))
        assertTrue(text, text.contains("    a Check odds now costs 24 → 41 a day"))
        assertTrue(text, text.contains("Scan: 2m ago · took 30 s"))
        assertTrue(text, text.contains("    cost: Kalshi 57, Pinnacle (PinnWire) 6"))
        assertTrue(text, text.contains("Check odds now: 30s ago · took 74 s · covered 106 of 109 open bets: CNO read 50, 56 priced from Vigilant's own fair odds"))
        assertTrue(text, text.contains("    cost: Kalshi 103, PropLine 14 (24 of its allowance)"))
        // No key is ever shown.
        assertFalse(text, text.contains("wire-key-0001"))
        assertFalse(text, text.contains("prop-key-0002"))
    }

    @Test
    fun `before any round ran it says none since the app opened`() {
        val text = report()
        assertTrue(text, text.contains("Scan: none since the app opened."))
        assertTrue(text, text.contains("Check odds now: none since the app opened."))
    }

    @Test
    fun `before any scan it says so instead of showing blanks`() {
        val text = report(UiState())
        assertTrue(text, text.contains("No scan since the app opened."))
        assertTrue(text, text.contains("Nothing counted yet."))
        assertTrue(text, text.contains("Bets: 0"))
    }

    // ---- V3 (Tj, 2026-09-30: "make the diagnostics section in settings as smart as possible so that when I output it to Claude, Claude can
    // run deep analysis on the app and know what is working or broken and how to improve the app") --------------------------------------

    @Test
    fun `health checks come first, worst first, each with its evidence and the code that owns it`() {
        val base = SampleScan.state()
        val s = base.copy(
            settings = base.settings.copy(autoScan = AutoScanMode.BOTH),
            status = base.status.copy(sources = base.status.sources + com.tjshea.vigilant.data.scanner.SourceReport("kalshi", "Kalshi", 0, 0, 0, "HTTP 503")),
        )
        val x = extras.copy(autoScanServiceRunning = false, phone = Diagnostics.Phone(notifications = false, exactAlarms = false, batteryUnrestricted = false, online = true))
        val text = report(s, x)
        assertTrue(text, text.indexOf("== Health checks (worst first) ==") in 0 until text.indexOf("== Settings =="))
        assertTrue(text, text.contains("For Claude: code at github.com/tjshea90/novig"))
        val checks = HealthChecks.of(s, x, now)
        assertEquals(checks.sortedBy { it.level.ordinal }, checks)
        fun find(area: String, level: HealthChecks.Level) = checks.firstOrNull { it.area == area && it.level == level }
        // The background scan is on but its service isn't running; alerts can't reach a phone with notifications off.
        assertTrue(text, find("Background auto-scan", HealthChecks.Level.FAIL)!!.look!!.contains("AutoScanService"))
        assertTrue(text, find("Phone", HealthChecks.Level.FAIL)!!.finding.contains("notifications are off"))
        assertTrue(text, checks.any { it.area == "Phone" && it.finding.contains("exact alarms") })
        assertTrue(text, checks.any { it.area == "Phone" && it.finding.contains("battery optimization") })
        // A source that failed names the error and where to look.
        val kalshi = find("Source Kalshi", HealthChecks.Level.FAIL)!!
        assertEquals("HTTP 503", kalshi.evidence)
        assertTrue(text, text.contains("FAIL Source Kalshi: failed in the last scan [HTTP 503] → data/reference/"))
        // The counts line says how many of each.
        assertTrue(text, Regex("\\d+ FAIL · \\d+ WARN · \\d+ OK").containsMatchIn(text))
    }

    @Test
    fun `before a scan or a check it says what to tap so the report has numbers`() {
        val base = SampleScan.fresh()
        val checks = HealthChecks.of(base, extras, now)
        assertTrue(checks.joinToString("\n") { it.text() }, checks.any { it.area == "Vigilant scan" && it.look!!.contains("tap Scan") })
    }

    @Test
    fun `edges that lose to the close are called out as not real, with the numbers`() {
        val base = SampleScan.state()
        val start = now - 2 * 3_600_000L
        // 20 bets at +3% EV when bet, each closing 2% worse than its price: CLV −2%.
        val bets = (1..20).map { i ->
            val cost = 0.50
            com.tjshea.vigilant.data.tracker.TrackedBet(
                "c$i", start - 3_600_000L, "NFL", "A @ B", start, "Moneyline", "A", "m$i", "o$i", cost, cost, 0.515, 0.03, 1.0,
                status = BetStatus.PENDING, closingFair = cost * 0.98, closingSeenAtMs = start - 5 * 60_000L,
            )
        }
        val checks = HealthChecks.of(base.copy(bets = bets), extras, now)
        val clv = checks.first { it.area == "Edge accuracy (CLV)" }
        assertEquals(HealthChecks.Level.FAIL, clv.level)
        assertTrue(clv.evidence!!, clv.evidence!!.contains("average CLV -2.0%") && clv.evidence!!.contains("EV when bet +3.0%") && clv.evidence!!.contains("20 bets"))
        // Beating the close by less than the EV claimed: overstated, a warning.
        val over = bets.map { it.copy(closingFair = it.cost * 1.005) }
        assertEquals(HealthChecks.Level.WARN, HealthChecks.of(base.copy(bets = over), extras, now).first { it.area == "Edge accuracy (CLV)" }.level)
        val real = bets.map { it.copy(closingFair = it.cost * 1.03) }
        assertEquals(HealthChecks.Level.OK, HealthChecks.of(base.copy(bets = real), extras, now).first { it.area == "Edge accuracy (CLV)" }.level)
    }

    @Test
    fun `the report splits accuracy by scanner and market, compares open bets' edge now with when bet, and lists the phone`() {
        val base = SampleScan.state()
        val bets = base.bets.map { if (it.status == BetStatus.PENDING && it.nowEv != null) it.copy(nowFair = (it.fairAtBet ?: 0.5) + 0.01, nowAtMs = now) else it }
        val text = report(base.copy(bets = bets), extras.copy(phone = Diagnostics.Phone(true, true, true, false, false, true, "Wi-Fi")))
        listOf("== Accuracy by scanner and by market (outliers aside) ==", "== Open bets: edge now vs when bet (pregame, current reads only) ==", "== Phone ==", "== Recent problems (saved across restarts, newest first) ==")
            .forEach { assertTrue("$it in:\n$text", text.contains(it)) }
        assertTrue(text, text.contains("Scanner Vigilant:") || text.contains("Scanner CNO:"))
        assertTrue(text, text.contains("Notifications yes · exact alarms yes · battery unrestricted yes · draw over apps NO · Data Saver off · online yes (Wi-Fi)"))
        assertTrue(text, text.contains("None recorded."))
    }

    @Test
    fun `recent problems are listed newest first with their counts, and only failures shown on screen go in`() {
        val p = listOf(
            com.tjshea.vigilant.data.diag.Problem("CrazyNinjaOdds", "Couldn't reach CrazyNinjaOdds (timeout)", now - 3_600_000L, now - 60_000L, 4),
            com.tjshea.vigilant.data.diag.Problem("Vigilant scan", "Kalshi: HTTP 503", now - 7_200_000L),
        )
        val text = report(x = extras.copy(problems = p))
        assertTrue(text, text.contains("CrazyNinjaOdds: Couldn't reach CrazyNinjaOdds (timeout) (×4 since"))
        assertTrue(text, text.indexOf("CrazyNinjaOdds: Couldn't reach") < text.indexOf("Vigilant scan: Kalshi: HTTP 503"))
        assertTrue(Diagnostics.isProblem("Couldn't save"))
        assertTrue(Diagnostics.isProblem("Check odds failed: timeout"))
        assertTrue(Diagnostics.isProblem("ParlayAPI answered HTTP 503"))
        assertFalse(Diagnostics.isProblem("Tracked: $1 on Team A"))
        assertFalse(Diagnostics.isProblem("Bet placed: $5.00 on Team A"))
    }

    @Test
    fun `open bets' edge now compares with when bet, overall and by scanner`() {
        val start = now + 3_600_000L
        fun bet(id: String, source: String, fairAtBet: Double, fairNow: Double) = com.tjshea.vigilant.data.tracker.TrackedBet(
            id, now - 3_600_000L, "NFL", "A @ B", start, "Moneyline", "A", "m$id", "o$id", 0.5, 0.5, fairAtBet, fairAtBet / 0.5 - 1, 1.0,
            status = BetStatus.PENDING, source = source, nowFair = fairNow, nowEv = fairNow / 0.5 - 1, nowAtMs = now,
        )
        val lines = Diagnostics.edgeNowLines(
            listOf(bet("a", com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_CNO, 0.52, 0.53), bet("b", com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_VIGILANT, 0.52, 0.49)),
            now,
        )
        assertEquals("All: 2 bets · EV when bet +4.0% → now +2.0% · fair moved toward the bet on 1, away on 1 · still +EV 1", lines[0])
        assertTrue(lines.toString(), lines.any { it.startsWith("CNO: 1 bets · EV when bet +4.0% → now +6.0%") })
        assertTrue(lines.toString(), lines.any { it.startsWith("Vigilant: 1 bets") })
        assertEquals(listOf("No open pregame bet has a current EV (tap Check odds now, then copy Diagnostics again)."), Diagnostics.edgeNowLines(emptyList(), now))
    }
}
