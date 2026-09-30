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
}
