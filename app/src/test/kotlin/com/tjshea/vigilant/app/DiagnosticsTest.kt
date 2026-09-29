package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
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
            "== API usage (each provider's own allowance) ==", "== CrazyNinjaOdds ==", "== Background auto-scan ==", "== Tracker ==",
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

    @Test
    fun `before any scan it says so instead of showing blanks`() {
        val text = report(UiState())
        assertTrue(text, text.contains("No scan since the app opened."))
        assertTrue(text, text.contains("Nothing counted yet."))
        assertTrue(text, text.contains("Bets: 0"))
    }
}
