package com.tjshea.vigilant.app

import android.app.AlarmManager
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.KeepAwake
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-10-02: "Research if this app stays awake and auto bets if the option is turned on even through screen lock and an idle android 16 moto g 2026.
 * If not, research if there are ways to keep it alive robustly … Maybe wake lock … (but I still want the screen turned off if possible)".
 * The real service on a Robolectric phone (screen off, Doze on): it holds the CPU awake while auto-scan runs faster than every 9 minutes, drives the cycles
 * itself, keeps only a safety alarm behind them, lets go when the switch goes off or Stop is tapped, and writes each cycle into the record Diagnostics reads.
 * What no test here can show is Android's own behaviour on a Moto G with the screen off for hours: Diagnostics' cycle record is for that (RESEARCH.md §59).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class KeepAwakeServiceTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val pm: PowerManager get() = app.getSystemService(PowerManager::class.java)

    /** Scanner on Vigilant only with no leagues: a cycle runs for real and reads nothing (no CNO, no scan), so no network is touched. */
    private val on = ScanSettings(autoScan = AutoScanMode.BOTH, scanner = ScannerMode.VIGILANT, leagues = emptySet(), autoScanSeconds = 5)

    @Before fun setUp() {
        runBlocking { app.container.settingsStore.update { on } }
        shadowOf(pm).turnScreenOn(false)
        shadowOf(pm).setIsDeviceIdleMode(true)
    }

    @After fun tearDown() {
        runBlocking { app.container.settingsStore.update { ScanSettings() } }
        AutoScanAlarm.cancel(app)
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1000) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what; status=$status held=${AutoScanService.keepAwakeHeld} alarms=${alarms().map { it.triggerAtTime }} running=${app.container.autoScan.running}")
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms

    private fun start(intent: Intent = Intent(app, AutoScanService::class.java)) =
        Robolectric.buildService(AutoScanService::class.java, intent).create().startCommand(0, 1).also {
            System.err.println("DEBUG stoppedBySelf=${shadowOf(it.get()).isStoppedBySelf} running=${AutoScanService.running} fg=${shadowOf(it.get()).isLastForegroundNotificationAttached} settings=${app.container.settingsStore.flow.value?.activeAutoScan}")
        }

    private val status get() = app.container.autoScan.status.value

    // ---- keeping awake ----------------------------------------------------------------------------------------------

    @Test
    fun `with keep awake on, the service holds the CPU awake, runs the cycle itself and keeps only a safety alarm`() {
        val controller = start()
        val service = controller.get()
        try {
            waitFor("the first cycle") { status.lastStartMs != null && !status.running }
            // The CPU lock is held between scans (the cycle's own lock is released).
            assertTrue(service.keepAwakeIsHeld)
            assertTrue(AutoScanService.keepAwakeHeld)
            // The only alarm is the safety net, three minutes off, and the notification doesn't announce it as the next scan.
            val now = System.currentTimeMillis()
            val armed = alarms().map { it.triggerAtTime }
            assertTrue("armed: $armed", armed.isNotEmpty() && armed.all { it - now in 170_000L..190_000L })
            assertNull(AutoScanAlarm.nextAtMs)
            // The cycle was recorded with the phone's state as it started: screen off, Doze on.
            waitFor("the cycle record") { runBlocking { app.container.cycleLog.summary().cycles } >= 1 }
            val book = runBlocking { app.container.cycleLog.summary() }
            assertEquals(1, book.cycles)
            assertEquals(1, book.screenOffCycles)
            assertEquals(1, book.dozeCycles)
            assertEquals(0, book.lateCount)
        } finally {
            controller.destroy()
        }
        assertFalse(AutoScanService.keepAwakeHeld)
    }

    @Test
    fun `a stray safety alarm while the loop runs fine starts no extra cycle, but one after a stall does`() {
        val controller = start()
        try {
            waitFor("the first cycle") { status.lastStartMs != null && !status.running }
            val first = status.lastStartMs
            Thread.sleep(20)
            controller.withIntent(Intent(app, AutoScanService::class.java).setAction(AutoScanService.ACTION_CYCLE)).startCommand(0, 2)
            waitFor("the stray alarm handled") { true }
            Thread.sleep(200)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("no cycle for a stray alarm", first, status.lastStartMs)
            // A tap on Scan now is not an alarm: it always runs.
            controller.withIntent(Intent(app, AutoScanService::class.java).setAction(AutoScanService.ACTION_SCAN_NOW)).startCommand(0, 3)
            waitFor("Scan now") { status.lastStartMs != first && !status.running }
            assertNotEquals(first, status.lastStartMs)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `switching keep awake off lets the CPU sleep and goes back to an alarm at the next scan, switching it on holds it again`() {
        val controller = start()
        val service = controller.get()
        try {
            waitFor("the first cycle") { status.lastStartMs != null && !status.running }
            assertTrue(service.keepAwakeIsHeld)
            runBlocking { app.container.settingsStore.update { it.copy(autoScanKeepAwake = false) } }
            waitFor("the CPU lock let go") { !service.keepAwakeIsHeld }
            assertFalse(AutoScanService.keepAwakeHeld)
            // Alarm-driven now: the alarm is the next scan's, announced as such, a few seconds after the last start (5 s interval).
            waitFor("the exact alarm") { AutoScanAlarm.nextAtMs != null }
            val next = AutoScanAlarm.nextAtMs!!
            assertTrue(next >= status.lastStartMs!! + 1_000L && next <= System.currentTimeMillis() + 10_000L)
            runBlocking { app.container.settingsStore.update { it.copy(autoScanKeepAwake = true) } }
            waitFor("the CPU lock held again") { service.keepAwakeIsHeld }
            assertTrue(AutoScanService.keepAwakeHeld)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `at 10 minutes keep awake isn't needed, so no CPU lock and an exact alarm for the next scan`() {
        runBlocking { app.container.settingsStore.update { it.copy(autoScanSeconds = 600) } }
        assertFalse(KeepAwake.active(on.copy(autoScanSeconds = 600)))
        val controller = start()
        val service = controller.get()
        try {
            waitFor("the first cycle") { status.lastStartMs != null && !status.running }
            assertFalse(service.keepAwakeIsHeld)
            assertFalse(AutoScanService.keepAwakeHeld)
            waitFor("the alarm") { AutoScanAlarm.nextAtMs != null }
            assertTrue(AutoScanAlarm.nextAtMs!! - status.lastStartMs!! in 590_000L..610_000L)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `Stop lets go of everything and cancels the alarm, and the record notes a deliberate stop`() {
        val controller = start()
        val service = controller.get()
        waitFor("the first cycle") { status.lastStartMs != null && !status.running }
        waitFor("the cycle record") { runBlocking { app.container.cycleLog.summary().cycles } >= 1 }
        assertTrue(runBlocking { app.container.cycleLog.summary().open })
        controller.withIntent(Intent(app, AutoScanService::class.java).setAction(AutoScanService.ACTION_STOP)).startCommand(0, 4)
        waitFor("Stop") { !service.keepAwakeIsHeld && alarms().isEmpty() }
        assertEquals(AutoScanMode.OFF, runBlocking { app.container.currentSettings() }.autoScan)
        controller.destroy()
        waitFor("the record closed") { !runBlocking { app.container.cycleLog.summary().open } }
        assertFalse(AutoScanService.keepAwakeHeld)
    }

    // ---- the phone's state, read the way the cycle reads it ---------------------------------------------------------

    @Test
    fun `a cycle records the screen and Doze state of the phone`() {
        shadowOf(pm).turnScreenOn(true)
        shadowOf(pm).setIsDeviceIdleMode(false)
        assertEquals(false to false, AutoScanner.screenOffAndDozing(app))
        shadowOf(pm).turnScreenOn(false)
        assertEquals(true to false, AutoScanner.screenOffAndDozing(app))
        shadowOf(pm).setIsDeviceIdleMode(true)
        assertEquals(true to true, AutoScanner.screenOffAndDozing(app))
    }

    // ---- what the code is held to, where a test can't run Android ---------------------------------------------------

    @Test
    fun `the service source keeps the CPU lock timed and renewed, drives cycles from a guarded loop, and restarts after a swipe or a stall`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScanService.kt").readText()
        // A partial lock (CPU, not screen), tagged, never held without a timeout, and let go when the service is.
        assertTrue(src.contains("""newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vigilant:keepawake")"""))
        assertTrue(src.contains("lock.acquire(KeepAwake.LOCK_TIMEOUT_MS)"))
        assertFalse("never an untimed acquire", Regex("""keepAwakeLock!?\??\.acquire\(\)""").containsMatchIn(src))
        assertFalse(src.contains("SCREEN_ON") || src.contains("FULL_WAKE_LOCK") || src.contains("FLAG_KEEP_SCREEN_ON") || src.contains("ACQUIRE_CAUSES_WAKEUP"))
        val destroy = src.substringAfter("override fun onDestroy() {").substringBefore("super.onDestroy()")
        assertTrue(destroy, destroy.contains("releaseKeepAwake()") && destroy.contains("container.cycleLog.stopped()"))
        val stop = src.substringAfter("private fun stopNow() {").substringBefore("stopSelf()")
        assertTrue(stop, stop.contains("stopLoop()") && stop.contains("releaseKeepAwake()"))
        // The loop: a failure is a problem line and a retry, never an uncaught exception; cancellation passes through.
        val loop = src.substringAfter("private suspend fun loop() {").substringBefore("/** One wait and one cycle")
        assertTrue(loop, loop.contains("catch (e: CancellationException) {\n                throw e") && loop.contains("container.problems.add(\"Keep-awake loop\""))
        // The lock is renewed between every wait slice, and the safety alarm moved on.
        val step = src.substringAfter("private suspend fun step(): Boolean {").substringBefore("private fun restartLoop()")
        assertTrue(step, step.contains("maintainKeepAwake(s.autoScanSeconds)") && step.indexOf("delay(minOf(left, LOOP_SLICE_MS))") < step.lastIndexOf("maintainKeepAwake(s.autoScanSeconds)"))
        assertTrue(step, step.contains("runCycle()") && step.contains("cycleJob?.join()"))
        // The safety alarm is not announced as the next scan, and a swipe-away arms a restart that is also not announced.
        assertTrue(src.contains("AutoScanAlarm.set(this, at, announce = false)"))
        assertTrue(src.contains("System.currentTimeMillis() + TASK_REMOVED_RESTART_MS, announce = false)"))
        assertTrue(src.contains("override fun onTaskRemoved(rootIntent: Intent?)"))
        // A stray safety alarm (the loop ran a cycle inside its delay) only moves itself on.
        assertTrue(src.contains("KeepAwake.active(s) && loopJob?.isActive == true && last != null && now - last < KeepAwake.watchdogDelayMs(s.autoScanSeconds)"))
        // Keeping awake, the per-cycle alarm is the safety net, not an exact alarm that Doze would hold for 9 minutes.
        assertTrue(src.contains("if (hold) armWatchdog(seconds, force = true) else AutoScanAlarm.set(this, nextCycleAtMs)"))
        assertTrue(src.contains("holds no wake lock between scans").not())
    }

    @Test
    fun `the manifest has the wake lock permission the keep-awake lock needs`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.WAKE_LOCK"))
        assertTrue(manifest.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertNotNull(manifest)
    }
}
