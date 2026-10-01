package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.TimeZone

/** What Diagnostics and the health checks say about the auto-bet (Tj, 2026-10-01), so a stop or a wrong setting shows in the report Claude reads. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutoBetDiagnosticsTest {

    private val now = SampleScan.NOW
    private val extras = Diagnostics.Extras("0.40.0", 73, "Motorola moto g 2026 · Android 16 (API 36)")

    private fun state(betting: Boolean = true, f: (ScanSettings) -> ScanSettings = { it }) =
        SampleScan.state().let { it.copy(betting = BettingUi(enabled = betting, balance = 25.0), settings = f(ScanSettings(autoScan = AutoScanMode.CNO, autoBet = true))) }

    private fun checks(s: UiState, x: Diagnostics.Extras = extras) = HealthChecks.of(s, x, now).filter { it.area == "Auto-bet" }

    private fun bet() = TrackedBet("id", 1L, "NFL", "A @ B", 2L, "Moneyline", "A", "m", "o", 0.5, 0.5, 0.52, 0.04, 1.0, orderId = "o1", auto = true)

    @Test
    fun `off says so, and the report carries the settings line`() {
        val off = checks(state { it.copy(autoBet = false) })
        assertEquals(listOf(HealthChecks.Level.OK), off.map { it.level })
        assertTrue(Diagnostics.report(state { it.copy(autoBet = false) }, extras, now, TimeZone.getTimeZone("UTC")).contains("Auto-bet (Tj, 2026-10-01): off"))
    }

    @Test
    fun `on, the report names the criteria, the limits, the halt and the last check`() {
        val s = state { it.copy(autoBetBooks = 4, autoBetMinEv = 0.0325, autoBetStake = AutoBetStake.EIGHTH_KELLY, autoBetMaxStake = 8.0, apiMaxPerDay = 40.0, bankroll = 500.0) }
        val x = extras.copy(autoBet = AutoBettor.Status(lastRunMs = now - 30_000, last = AutoBettor.Report(looked = 5, passed = 1, placed = listOf(bet()))))
        val text = Diagnostics.report(s, x, now, TimeZone.getTimeZone("UTC"))
        val line = text.lines().single { it.startsWith("Auto-bet (Tj, 2026-10-01): ON") }
        assertTrue(line, line.contains("at least 4 books agreeing it's +EV on their own"))
        assertTrue(line, line.contains("+3.25% or more"))
        assertTrue(line, line.contains("its ⅛ Kelly stake of your \$500.00 bankroll (never over \$8.00)"))
        assertTrue(line, line.contains("most a day \$40"))
        assertTrue(line, line.contains("bankroll \$500"))
        assertTrue(line, line.contains("not halted"))
        assertTrue(line, line.contains("Last check 30s ago: placed 1 (\$1.00)"))
    }

    @Test
    fun `auto-bet on with Android's notifications switched off is a WARN, naming where to turn them on`() {
        val blocked = HealthChecks.of(state(), extras.copy(phone = Diagnostics.Phone(notifications = false)), now).filter { it.area == "Auto-bet notifications" }
        assertEquals(listOf(HealthChecks.Level.WARN), blocked.map { it.level })
        assertTrue(blocked.single().text(), blocked.single().text().contains("Android Settings › Apps › Vigilant › Notifications"))
        // Allowed (or not readable), or auto-bet off: nothing to say.
        assertTrue(HealthChecks.of(state(), extras.copy(phone = Diagnostics.Phone(notifications = true)), now).none { it.area == "Auto-bet notifications" })
        assertTrue(HealthChecks.of(state(), extras, now).none { it.area == "Auto-bet notifications" })
        assertTrue(HealthChecks.of(state { it.copy(autoBet = false) }, extras.copy(phone = Diagnostics.Phone(notifications = false)), now).none { it.area == "Auto-bet notifications" })
    }

    @Test
    fun `a lost order is a FAIL, a wallet that can't fund a bet or a setting that stops it is a WARN, and a healthy one is OK`() {
        val halted = checks(state { it.copy(autoBetHalted = "an order's answer was lost") }).single()
        assertEquals(HealthChecks.Level.FAIL, halted.level)
        assertTrue(halted.text(), halted.text().contains("Resume auto-bet"))
        // On but betting isn't set up / the scanner is Vigilant only / paused / background scan off: WARN, saying what.
        assertEquals(HealthChecks.Level.WARN, checks(state(betting = false)).single().level)
        assertTrue(checks(state(betting = false)).single().text().contains("isn't set up"))
        assertEquals(HealthChecks.Level.WARN, checks(state { it.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT) }).single().level)
        assertEquals(HealthChecks.Level.WARN, checks(state { it.copy(autoScan = AutoScanMode.OFF) }).single().level)
        // The wallet.
        val empty = checks(state(), extras.copy(autoBet = AutoBettor.Status(lastRunMs = now - 10_000, last = AutoBettor.Report(walletEmpty = true)))).single()
        assertEquals(HealthChecks.Level.WARN, empty.level)
        assertTrue(empty.text(), empty.text().contains("can't fund a bet"))
        // No check yet since the app opened.
        assertEquals(HealthChecks.Level.WARN, checks(state()).single().level)
        // Healthy.
        val ok = checks(state(), extras.copy(autoBet = AutoBettor.Status(lastRunMs = now - 10_000, last = AutoBettor.Report(looked = 3, passed = 1, placed = listOf(bet())))))
        assertEquals(listOf(HealthChecks.Level.OK), ok.map { it.level })
        // The daily limit adds its own line.
        val limit = checks(state(), extras.copy(autoBet = AutoBettor.Status(lastRunMs = now - 10_000, last = AutoBettor.Report(stopped = "your daily limit of \$50.00 for API bets is reached"))))
        assertTrue(limit.any { it.text().contains("daily limit of \$50") })
    }
}
