package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpBookChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.TimeZone

/** What Diagnostics and the health checks say about the sharp-book confirmation (Tj, 2026-10-02), so a switch that can't do anything shows in the report. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SharpDiagnosticsTest {

    private val now = SampleScan.NOW
    private val zone = TimeZone.getTimeZone("UTC")
    private val extras = Diagnostics.Extras("0.42.0", 77, "Motorola moto g 2026 · Android 16 (API 36)", sharpFeeds = listOf("PinnWire / pinnapi", "ParlayAPI"), sharpCalls = 6, sharpFailures = 1, sharpAnswers = mapOf("PinnWire" to 4))

    private fun state(f: (ScanSettings) -> ScanSettings = { it }) =
        SampleScan.state().copy(settings = f(ScanSettings(autoScan = AutoScanMode.CNO, sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM)))

    private fun checks(s: UiState = state(), x: Diagnostics.Extras = extras) = HealthChecks.of(s, x, now).filter { it.area == "Sharp-book confirmation" }

    @Test
    fun `with no confirmation there is no health line, and the report says each mode`() {
        val veto = SampleScan.state()
        assertTrue(HealthChecks.of(veto, extras, now).none { it.area == "Sharp-book confirmation" })
        assertTrue(Diagnostics.report(veto, extras, now, zone).contains("Sharp books (Tj, 2026-10-02: veto by default): auto-bet VETO · alerts VETO · veto bar 1.0% on the sharpest book (RESEARCH.md §72)\n"))
        val off = veto.copy(settings = veto.settings.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.OFF))
        assertTrue(Diagnostics.report(off, extras, now, zone).contains("Sharp books (Tj, 2026-10-02: veto by default): auto-bet OFF · alerts VETO · veto bar 1.0% on the sharpest book (RESEARCH.md §72)\n"))
        // No veto anywhere (the bids' included): no bar to report.
        val none = off.copy(settings = off.settings.copy(sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.OFF, makerSharpVeto = false))
        assertTrue(Diagnostics.report(none, extras, now, zone).contains("auto-bet OFF · alerts OFF\n"))
    }

    @Test
    fun `switched on with a feed it says which, and how many calls it has made`() {
        val ok = checks().single()
        assertEquals(HealthChecks.Level.OK, ok.level)
        assertTrue(ok.text(), ok.text().contains("on for auto-bet via PinnWire / pinnapi, ParlayAPI (6 feed calls since the app opened)"))
        val both = checks(state { it.copy(sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM) }).single()
        assertTrue(both.text(), both.text().contains("on for auto-bet and alerts"))
    }

    @Test
    fun `switched on with nothing that can ask is a warning that says what it stops`() {
        val none = checks(x = extras.copy(sharpFeeds = emptyList())).single()
        assertEquals(HealthChecks.Level.WARN, none.level)
        assertTrue(none.text(), none.text().contains("no Pinnacle feed is on with a key: nothing can be confirmed, so the auto-bet skips every bet"))
        val alerts = checks(state { it.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.OFF, sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM) }, extras.copy(sharpFeeds = emptyList())).single()
        assertTrue(alerts.text(), alerts.text().contains("no CNO alert is sent"))
        // CNO's page may confirm: then no feed is not a dead end.
        val viaCno = checks(state { it.copy(sharpConfirmViaCno = true) }, extras.copy(sharpFeeds = emptyList())).single()
        assertEquals(HealthChecks.Level.OK, viaCno.level)
        assertTrue(viaCno.text(), viaCno.text().contains("via CNO's page"))
    }

    @Test
    fun `feeds that mostly fail are a warning`() {
        val bad = checks(x = extras.copy(sharpCalls = 6, sharpFailures = 4)).single()
        assertEquals(HealthChecks.Level.WARN, bad.level)
        assertTrue(bad.text(), bad.text().contains("4 of 6 feed calls failed"))
        assertEquals(HealthChecks.Level.OK, checks(x = extras.copy(sharpCalls = 6, sharpFailures = 3)).single().level)
    }

    @Test
    fun `the report carries the criteria, the feeds and what they have answered`() {
        val s = state { it.copy(sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM, sharpConfirmBooks = SharpBookChoice.PINNACLE_CIRCA, sharpConfirmMaxAgeSeconds = 120, sharpConfirmMinEv = 0.01, sharpConfirmViaCno = true) }
        val text = Diagnostics.report(s, extras, now, zone)
        val line = text.lines().single { it.startsWith("Sharp books (Tj, 2026-10-02") }
        assertTrue(line, line.contains("auto-bet CONFIRM · alerts CONFIRM · Pinnacle or Circa, quote at most 2 min old, edge at least 1.0%, CNO's page may confirm"))
        assertTrue(line, line.contains("feeds: PinnWire / pinnapi, ParlayAPI · feed calls since the app opened 6 (1 failed), answers: PinnWire 4"))
        val none = Diagnostics.report(state(), extras.copy(sharpFeeds = emptyList(), sharpAnswers = emptyMap(), sharpCalls = 0, sharpFailures = 0), now, zone).lines().single { it.startsWith("Sharp books (Tj, 2026-10-02") }
        assertTrue(none, none.contains("edge any +EV, CNO's page only vetoes · feeds: none on with a key · feed calls since the app opened 0 (0 failed)"))
    }
}
