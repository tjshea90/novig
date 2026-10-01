package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.batteryHint
import com.tjshea.vigilant.app.ui.keepAwakeHint
import com.tjshea.vigilant.data.diag.CycleBook
import com.tjshea.vigilant.data.diag.CycleLog
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * What Diagnostics and the health checks say about keeping auto-scan alive with the screen off (Tj, 2026-10-02), so a night of idling shows in the report
 * Claude reads: the switch, whether the CPU lock is held, the cycle record, and the phone's battery state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class KeepAwakeDiagnosticsTest {

    private val now = SampleScan.NOW
    private val zone = TimeZone.getTimeZone("UTC")
    private val extras = Diagnostics.Extras("0.41.0", 76, "Motorola moto g 2026 · Android 16 (API 36)", autoScanServiceRunning = true, keepAwakeHeld = true)

    private fun state(f: (ScanSettings) -> ScanSettings = { it }) =
        SampleScan.state().copy(settings = f(ScanSettings(autoScan = AutoScanMode.CNO, autoScanSeconds = 5)))

    private fun checks(s: UiState = state(), x: Diagnostics.Extras = extras) = HealthChecks.of(s, x, now).filter { it.area == "Background auto-scan" }

    private fun book(vararg lateAgoMs: Long, screenOff: Int = 0, doze: Int = 0) = CycleBook(
        sinceMs = now - 12 * 3_600_000L, cycles = 8_000, screenOffCycles = screenOff, dozeCycles = doze, lastStartMs = now - 5_000, lastEndMs = now - 4_000, lastSeconds = 5, open = true,
        lateCount = lateAgoMs.size, worst = lateAgoMs.firstOrNull()?.let { com.tjshea.vigilant.data.diag.LateCycle(now - it, 9 * 60_000L, screenOff = true, dozing = true) },
        late = lateAgoMs.map { com.tjshea.vigilant.data.diag.LateCycle(now - it, 9 * 60_000L, screenOff = true, dozing = true) },
    )

    @Test
    fun `a fast schedule with keep awake off is warned about, with the reason and where to switch it on`() {
        val warn = checks(state { it.copy(autoScanKeepAwake = false) }).single { it.text().contains("Keep awake is off") }
        assertEquals(HealthChecks.Level.WARN, warn.level)
        assertTrue(warn.text(), warn.text().contains("about every 9 minutes, not every 5 sec"))
        assertTrue(warn.text(), warn.text().contains("Settings › Background auto-scan › Keep awake"))
        // At 10 minutes an alarm is on time: nothing to warn about; and with the switch on there's nothing either.
        assertTrue(checks(state { it.copy(autoScanKeepAwake = false, autoScanSeconds = 600) }).none { it.text().contains("Keep awake is off") })
        assertTrue(checks(state()).none { it.text().contains("Keep awake is off") })
    }

    @Test
    fun `keep awake on but the service not holding the CPU is a warning, holding it is not`() {
        val bad = checks(x = extras.copy(keepAwakeHeld = false)).single { it.text().contains("isn't holding the CPU awake") }
        assertEquals(HealthChecks.Level.WARN, bad.level)
        assertTrue(checks().none { it.text().contains("isn't holding") })
        // A service that isn't running has its own, louder check, not this one.
        assertTrue(checks(x = extras.copy(keepAwakeHeld = false, autoScanServiceRunning = false)).none { it.text().contains("isn't holding") })
    }

    @Test
    fun `late cycles in the last day are a warning naming the worst, none late with the screen off is an OK, and old ones are forgotten`() {
        val late = checks(x = extras.copy(cycles = book(3_600_000L, 7_200_000L, screenOff = 5_000, doze = 4_000))).single { it.text().contains("started late") }
        assertEquals(HealthChecks.Level.WARN, late.level)
        assertTrue(late.text(), late.text().contains("2 cycles started late in the last day (worst 9 min, "))
        assertTrue(late.text(), late.text().contains("Doze was on then"))
        val ok = checks(x = extras.copy(cycles = book(screenOff = 5_000, doze = 4_000))).single { it.text().contains("kept its schedule with the screen off") }
        assertEquals(HealthChecks.Level.OK, ok.level)
        assertTrue(ok.text(), ok.text().contains("5000 cycles (4000 in Doze), none late"))
        // A late cycle from two days ago doesn't warn any more.
        assertTrue(checks(x = extras.copy(cycles = book(2 * 24 * 3_600_000L, screenOff = 5_000))).none { it.text().contains("started late") })
        // Nothing recorded with the screen off yet: neither line.
        assertTrue(checks(x = extras.copy(cycles = book())).none { it.text().contains("kept its schedule") || it.text().contains("started late") })
    }

    @Test
    fun `Battery Saver without the exemption, and a restricted or rare standby bucket, are warnings with where to fix them`() {
        fun phone(p: Diagnostics.Phone) = HealthChecks.of(state(), extras.copy(phone = p), now).filter { it.area == "Phone" }.map { it.text() }
        val saver = phone(Diagnostics.Phone(batteryUnrestricted = false, batterySaver = true))
        assertTrue(saver.toString(), saver.any { it.contains("Battery Saver is on and Vigilant isn't unrestricted") })
        assertTrue(phone(Diagnostics.Phone(batteryUnrestricted = true, batterySaver = true)).none { it.contains("Battery Saver") })
        for (bucket in listOf("restricted", "rare")) {
            assertTrue(bucket, phone(Diagnostics.Phone(standbyBucket = bucket)).any { it.contains("$bucket standby bucket") })
        }
        for (bucket in listOf("active", "working set", "frequent", "exempted")) {
            assertTrue(bucket, phone(Diagnostics.Phone(standbyBucket = bucket)).none { it.contains("standby bucket") })
        }
        // Auto-scan off: no reason to worry about the buckets.
        assertTrue(HealthChecks.of(state { it.copy(autoScan = AutoScanMode.OFF) }, extras.copy(phone = Diagnostics.Phone(standbyBucket = "restricted")), now).none { it.text().contains("standby bucket") })
        assertEquals(listOf("exempted", "active", "working set", "frequent", "rare", "restricted", "never"), listOf(5, 10, 20, 30, 40, 45, 50).map(Diagnostics::bucketName))
    }

    @Test
    fun `the report carries the keep-awake line, the cycle record with its late cycles, and the phone's battery state`() {
        val x = extras.copy(
            cycles = book(3_600_000L, screenOff = 5_000, doze = 4_000),
            phone = Diagnostics.Phone(notifications = true, exactAlarms = true, batteryUnrestricted = true, overlay = false, dataSaver = false, online = true, network = "Wi-Fi", batterySaver = false, dozing = true, standbyBucket = "active"),
        )
        val text = Diagnostics.report(state(), x, now, zone)
        assertTrue(text, text.contains("Keep awake (Tj, 2026-10-02): switch on · holding the CPU awake (screen off): yes, the wake lock is held now"))
        assertTrue(text, text.contains("Cycle record since "))
        assertTrue(text, text.contains("8,000 cycles, 5,000 started with the screen off (4,000 of those in Doze) · 1 late"))
        assertTrue(text, text.contains("  late: "))
        assertTrue(text, text.contains("9 min after schedule · Doze on"))
        assertTrue(text, text.contains("Battery Saver off · Doze now yes · standby bucket active"))
        // The other states of the line.
        assertTrue(Diagnostics.report(state { it.copy(autoScanKeepAwake = false) }, x, now, zone).contains("switch OFF · NOT holding it: scans between 5 sec apart run on alarms, which Doze spaces about 9 minutes apart"))
        assertTrue(Diagnostics.report(state { it.copy(autoScanSeconds = 600) }, x, now, zone).contains("not needed at 10 min: an alarm is on time at 9 minutes or more"))
        assertTrue(Diagnostics.report(state(), x.copy(keepAwakeHeld = false), now, zone).contains("NO, the service isn't holding it"))
        assertTrue(Diagnostics.report(state { it.copy(autoScan = AutoScanMode.OFF) }, x, now, zone).contains("auto-scan runs nothing, nothing to keep awake"))
        // Nothing recorded yet.
        assertTrue(Diagnostics.report(state(), extras, now, zone).contains("Cycle record: none yet"))
    }

    @Test
    fun `the settings words say what keep awake does at each interval and what the battery setting is`() {
        val s = ScanSettings(autoScan = AutoScanMode.CNO, autoScanSeconds = 5)
        assertTrue(keepAwakeHint(s), keepAwakeHint(s).startsWith("On: the screen can stay off and locked, but the CPU stays awake"))
        assertTrue(keepAwakeHint(s), keepAwakeHint(s).contains("5 sec schedule") && keepAwakeHint(s).contains("best plugged in"))
        assertTrue(keepAwakeHint(s.copy(autoScanSeconds = 600)).startsWith("On, but not needed at 10 min"))
        assertTrue(keepAwakeHint(s.copy(autoScanKeepAwake = false)).startsWith("Off: with the screen off and the phone still, Android runs each alarm-driven scan only about every 9 minutes"))
        assertTrue(keepAwakeHint(s.copy(autoScanKeepAwake = false, autoScanSeconds = 600)).startsWith("Off: the CPU sleeps between scans"))
        assertTrue(batteryHint(false).contains("Settings › Apps › Vigilant › App battery usage › Unrestricted"))
        assertTrue(batteryHint(true).startsWith("Battery: Unrestricted"))
        assertFalse(batteryHint(true).contains("Settings ›"))
        assertEquals("Cycle record: none yet (it starts with the next background cycle)", CycleLog.line(CycleBook(), now, zone))
    }
}
