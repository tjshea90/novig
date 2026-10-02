package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * The flight recorder watches the background cycle (Tj, 2026-10-02: Diagnostics should say what is slow): a whole cycle's time and count, and the time of each
 * step, so "the cycle ran long" in the file comes with which part of it did.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CycleRecorderTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        runBlocking { app.container.settingsStore.update { ScanSettings() } }
    }

    @After fun tearDown() {
        runBlocking { app.container.settingsStore.update { ScanSettings() } }
    }

    /**
     * A real cycle needs CNO and Vigilant's feeds on the network (a test's cycle ran 157 s on the sandbox's), so what the recorder takes from it is pinned in the
     * source: the whole cycle's time and count in the `finally` (a cycle that throws or is cancelled is still counted), errors counted apart.
     */
    @Test
    fun `a cycle's time and count are recorded even when it fails or is cancelled`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        val cycle = src.substringAfter("suspend fun cycle(forceVigilant: Boolean = false): Boolean {").substringBefore("private inline fun <T> timed(")
        val fin = cycle.substringAfter("} finally {\n                _status.update { it.copy(running = false")
        assertTrue(fin, fin.contains("withContext(NonCancellable)"))
        assertTrue(fin, fin.contains("c.perf.add(\"cycle.ms\", tookMs.toDouble())"))
        assertTrue(fin, fin.contains("c.eventLog.count(\"cycle.runs\")"))
        assertTrue(fin, fin.contains("if (errors.isNotEmpty()) c.eventLog.count(\"cycle.errors\")"))
        assertTrue(fin, fin.contains("c.eventLog.warn(\"CYCLE\""))
        // And a cycle that is skipped (a check holds the focus, auto-scan is off, one already runs) records nothing.
        assertTrue(cycle.indexOf("return false") < cycle.indexOf("c.perf.add(\"cycle.ms\""))
    }

    @Test
    fun `a cycle is slow past three intervals and half a minute, whichever is longer`() {
        // Every 5 s: half a minute is the floor.
        assertFalse(AutoScanner.slowCycle(30_000L, 5))
        assertTrue(AutoScanner.slowCycle(30_001L, 5))
        // Every 20 minutes: three intervals (an hour) is the limit.
        assertFalse(AutoScanner.slowCycle(3_600_000L, 1200))
        assertTrue(AutoScanner.slowCycle(3_600_001L, 1200))
        assertFalse(AutoScanner.slowCycle(100_000L, 1200))
    }

    /** Each part of the cycle is timed under its own name (a real cycle's CNO read needs the network, so the steps are pinned in the source). */
    @Test
    fun `each step of a cycle is timed under its own name`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        val cycle = src.substringAfter("suspend fun cycle(forceVigilant: Boolean = false): Boolean {").substringBefore("private inline fun <T> timed(")
        listOf(
            "timed(\"cno\") { cnoRead(s) }", "timed(\"autobet\") { c.autoBet.run(s, snapshot(s)) }", "timed(\"alerts\") { alerts += cnoAlerts(s) }",
            "timed(\"closing\") {", "timed(\"vigilant\") { alerts += vigilantScan(settings) }",
        ).forEach { assertTrue(it, cycle.contains(it)) }
        // Recorded even when the step throws or is cancelled.
        val helper = src.substringAfter("private inline fun <T> timed(").substringBefore("suspend fun afterScan")
        assertTrue(helper, helper.contains("finally") && helper.contains("c.perf.add(\"cycle.step.\$name\""))
    }

    /**
     * The places in the app that write to the flight recorder, pinned in the source (each runs inside a service, a cycle or an activity that a unit test can't drive
     * whole): the file Claude reads is only as good as these lines, so removing one must fail a test.
     */
    @Test
    fun `the cycle, the alerts, the service and the screen each tell the recorder what they did`() {
        val dir = "src/main/kotlin/com/tjshea/vigilant/app/"
        val scan = File(dir + "AutoScan.kt").readText()
        // A slow cycle is a WARN with its time, only past the rule.
        assertTrue(scan.contains("if (slowCycle(tookMs, settings.autoScanSeconds)) {\n                        c.eventLog.warn(\"CYCLE\", \"a background cycle took \${tookMs / 1_000} s"))
        // The alerts: each sharp verdict counted, each batch sent an event and a count.
        assertTrue(scan.contains(").also { c.eventLog.count(\"sharp.alert.\${it.verdict}\") }"))
        assertTrue(scan.contains("c.eventLog.info(\"ALERT\", \"sent \$posted +EV alert"))
        assertTrue(scan.contains("c.eventLog.count(\"alerts.sent\", posted.toLong())"))
        // The service: it started, was refused, was stopped, destroyed or swiped away.
        val service = File(dir + "AutoScanService.kt").readText()
        for (event in listOf("auto-scan service started", "Android refused to start auto-scan in the foreground", "auto-scan service stopping", "auto-scan service destroyed", "Vigilant swiped out of the recent apps")) {
            assertTrue(event, service.contains("container.eventLog.") && service.contains("\"SERVICE\", \"$event"))
        }
        // The screen: opened (fresh or restored), the cold start, the share sheet it starts and the button that asks for it.
        val activity = File(dir + "MainActivity.kt").readText()
        assertTrue(activity.contains("container.eventLog.info(\"APP\", \"screen opened (\${if (savedInstanceState == null) \"fresh launch\" else \"restored\"})\""))
        assertTrue(activity.contains("perf.noteColdStart(sinceStartMs, COLD_START_WINDOW_MS)"))
        assertTrue(activity.contains("vm.shareRequests.collect { intent -> runCatching { startActivity(intent) }"))
        assertTrue(activity.contains("onShare = vm::shareDiagnostics,"))
    }
}
