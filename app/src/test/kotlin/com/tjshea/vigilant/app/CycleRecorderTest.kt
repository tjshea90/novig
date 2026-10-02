package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    fun `a real cycle is timed and counted, and a cycle with nothing to read records no step`() = runBlocking {
        // Vigilant's own scanner with no leagues: the cycle runs, reads nothing and scans nothing.
        app.container.settingsStore.update { it.copy(scanner = ScannerMode.VIGILANT, autoScan = AutoScanMode.BOTH, leagues = emptySet()) }
        val perf = app.container.perf
        val before = perf.summary("cycle.ms").count
        val runs = app.container.eventLog.counters()["cycle.runs"] ?: 0L
        assertTrue(app.container.autoScan.cycle())
        assertEquals(before + 1, perf.summary("cycle.ms").count)
        assertEquals(runs + 1, app.container.eventLog.counters()["cycle.runs"])
        assertEquals(perf.summaries().toString(), 0, perf.summary("cycle.step.cno").count)
        assertEquals(perf.summaries().toString(), 0, perf.summary("cycle.step.vigilant").count)
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
}
