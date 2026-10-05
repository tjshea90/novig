package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.diag.Event
import com.tjshea.vigilant.data.diag.EventBook
import com.tjshea.vigilant.data.diag.EventLog
import com.tjshea.vigilant.data.diag.HostStat
import com.tjshea.vigilant.data.diag.Level
import com.tjshea.vigilant.data.diag.NetBook
import com.tjshea.vigilant.data.diag.NetStats
import com.tjshea.vigilant.data.diag.PerfStats
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanTiming
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * What the app tells its flight recorder as it runs (Tj, 2026-10-02): the process starting after what earlier runs kept comes back, the files written on a timer,
 * a finished scan, CNO asking the app to wait, and Tj's switches. And that the container connects each of them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppRecorderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val eventStore get() = JsonFileStore(File(tmp.root, "events.json"), EventBook.serializer(), { EventBook() })
    private val netStore get() = JsonFileStore(File(tmp.root, "net.json"), NetBook.serializer(), { NetBook() })

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) {
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    private fun report(errors: List<String> = emptyList(), timing: ScanTiming? = ScanTiming(totalMs = 95_000, refused = 0)) = ScanReport(
        result = null, errors = errors, retryAfterSeconds = null, booksFetched = 120, booksNotModified = 0, booksFromCache = 0, booksViaKey = 80, novigCatalogAtMs = null,
        sources = emptyList(), creditsRemaining = null, timing = timing,
    )

    // ---- the start and the files --------------------------------------------------------------------------------------------

    @Test
    fun `a process start brings back what earlier runs kept, notes the start after it, and writes both files on its timer`() = runBlocking {
        // An earlier run left an event, a counter and a host's stats.
        eventStore.update { EventBook(listOf(Event(1_000L, "OLD", Level.WARN, "from the last run")), mapOf("x.count" to 5L), System.currentTimeMillis() - 3_600_000L) }
        netStore.update { NetBook(mapOf("old.example.com" to HostStat(calls = 7)), System.currentTimeMillis()) }
        val events = EventLog(eventStore)
        val net = NetStats(netStore)
        // This run recorded a call and a counter before the files were read: they go on top of what was kept.
        net.record("new.example.com", "/v1/odds", 200, null, 100, 150, 20_000, null)
        events.count("x.count", 2)
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            scope.launch { AppRecorder(events, net, PerfStats()).run("0.43.0", 50) }
            waitFor("the start noted") { events.events().any { it.msg.startsWith("process started: ") } }
            val e = events.events()
            assertEquals("from the last run", e.first().msg)
            assertTrue(e.last().msg, e.last().cat == "APP" && e.last().msg.endsWith("0.43.0"))
            assertEquals(7L, events.counters()["x.count"])
            assertEquals(setOf("old.example.com", "new.example.com"), net.snapshot().hosts.keys)
            // And the timer writes them (no forcing).
            waitFor("both files written") { File(tmp.root, "events.json").readText().contains("process started") && File(tmp.root, "net.json").readText().contains("new.example.com") }
            assertTrue(File(tmp.root, "events.json").readText().contains("from the last run"))
            assertTrue(File(tmp.root, "net.json").readText().contains("old.example.com"))
        } finally {
            scope.cancel()
        }
    }

    // ---- a finished scan -----------------------------------------------------------------------------------------------------

    @Test
    fun `a finished scan is one line with its time, and a warning when it had errors`() {
        val events = EventLog(eventStore)
        val perf = PerfStats()
        val recorder = AppRecorder(events, NetStats(netStore), perf)
        recorder.scanFinished(report())
        recorder.scanFinished(report(errors = listOf("PinnWire: 503"), timing = ScanTiming(totalMs = 61_000, refused = 3)))
        val (ok, bad) = events.events()
        assertEquals(Level.INFO, ok.level)
        assertEquals("Vigilant scan finished in 95 s: 120 Novig prices (80 through the key), 0 errors", ok.msg)
        assertEquals(95_000L, ok.ms)
        assertEquals(Level.WARN, bad.level)
        assertEquals("Vigilant scan finished in 61 s: 120 Novig prices (80 through the key), 1 error, Novig refused 3", bad.msg)
        assertEquals(2, perf.summary("scan.ms").count)
        assertEquals(95_000.0, perf.summary("scan.ms").max, 0.0)
        // A scan with no timing has no length and nothing for the performance block.
        recorder.scanFinished(report(timing = null))
        assertEquals("Vigilant scan finished: 120 Novig prices (80 through the key), 0 errors", events.events().last().msg)
        assertEquals(2, perf.summary("scan.ms").count)
    }

    /**
     * Tj, 2026-10-04: "a lot of times the vigilant scanner slows down significantly when it is scanning novig prices, maybe down to 2 per second": every scan on the
     * timeline says how fast its Novig reads went and what paced them, not only the last one.
     */
    @Test
    fun `every finished scan on the timeline says its pace and what slowed it`() {
        val slow = ScanTiming(
            totalMs = 299_000, novigFromMs = 4_000, novigToMs = 299_000, refused = 5, liveFeedAtMs = 4_100, liveFeedAsked = 2_000, liveFeedHeld = 315,
            pace = com.tjshea.vigilant.data.novig.ReadPace(4.0, 2.0, 3.5, 14.0, null, 14.0, 0),
        )
        assertEquals(
            "Vigilant scan finished in 299 s: 120 Novig prices (80 through the key), 0 errors, Novig refused 5 · 0.4 a second (40 public) · " +
                "public route at 4 a second, down to 2 after a refusal, 3.5 at the end · live feed held 315 of 2,000 asked at the end",
            AppRecorder.scanLine(report(timing = slow)),
        )
        // No key read anything: no live feed to hold anything, so no "held" on the line.
        assertTrue(!AppRecorder.scanLine(report(timing = slow).copy(booksViaKey = 0)).contains("live feed held"))
        // A fast scan through the key with nothing slowed: its pace and nothing more.
        val fast = ScanTiming(totalMs = 31_000, novigFromMs = 1_000, novigToMs = 7_000, pace = com.tjshea.vigilant.data.novig.ReadPace(4.0, null, 4.0, 14.0, null, 14.0, 0))
        val line = AppRecorder.scanLine(report(timing = fast).copy(booksViaKey = 120))
        assertEquals("Vigilant scan finished in 31 s: 120 Novig prices (120 through the key), 0 errors · 20.0 a second", line)
    }

    // ---- CNO's pause and Tj's switches ---------------------------------------------------------------------------------------

    @Test
    fun `CNO asking the app to wait is a warning with how long and a count`() {
        val events = EventLog(eventStore)
        val recorder = AppRecorder(events, NetStats(netStore), PerfStats())
        recorder.cnoPaused(untilMs = 130_000L, nowMs = 100_000L)
        recorder.cnoPaused(untilMs = 90_000L, nowMs = 100_000L)
        val e = events.events()
        assertEquals(listOf("CrazyNinjaOdds asked the app to wait 30 s", "CrazyNinjaOdds asked the app to wait 0 s"), e.map { it.msg })
        assertTrue(e.all { it.level == Level.WARN && it.cat == "CNO" })
        assertEquals(2L, events.counters()["cno.pauses"])
    }

    @Test
    fun `each switch that decides what runs by itself is an event when it changes, and nothing else is`() {
        val events = EventLog(eventStore)
        val recorder = AppRecorder(events, NetStats(netStore), PerfStats())
        val before = ScanSettings()
        recorder.settingsChanged(before, before.copy())
        assertTrue(events.events().isEmpty())
        recorder.settingsChanged(
            before,
            before.copy(
                autoBet = !before.autoBet, autoScan = AutoScanMode.BOTH, autoScanSeconds = 5, autoScanKeepAwake = !before.autoScanKeepAwake, sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM,
                sharpAlerts = com.tjshea.vigilant.data.scanner.SharpMode.OFF, presetName = "Mine", pausedByHand = !before.pausedByHand, scanner = ScannerMode.CNO, autoBetHalted = "a bet failed",
            ),
        )
        val msgs = events.events().map { it.msg }
        for (name in listOf("auto-bet", "background auto-scan", "auto-scan every (s)", "keep awake", "sharp books for auto-bet", "sharp books for alerts", "preset", "paused", "scanner", "auto-bet halted")) {
            assertEquals(msgs.toString(), 1, msgs.count { it.startsWith("$name: ") })
        }
        assertTrue(msgs.toString(), msgs.contains("auto-bet halted: false → true"))
        assertTrue(msgs.toString(), msgs.contains("background auto-scan: ${before.autoScan} → ${AutoScanMode.BOTH}"))
        assertTrue(events.events().all { it.cat == "SETTINGS" && it.level == Level.INFO })
        // The halt's own words are not copied into the file (only that it halted).
        assertFalse(msgs.toString(), msgs.any { it.contains("a bet failed") })
    }

    // ---- the container connects them -----------------------------------------------------------------------------------------

    @Test
    fun `the container runs the recorder in the background and hands it each finished scan, CNO pause and settings change`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/VigilantApp.kt").readText()
        val init = src.substringAfter("// The flight recorder: what earlier runs kept comes back first").substringBefore("// Written down as it happens, whatever screen is open")
        assertTrue(init, init.contains("appScope.launch(Dispatchers.IO) {\n            recorder.run(runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull(), FLUSH_EVERY_MS)"))
        assertTrue(init, init.contains("runner.state.distinctUntilChanged { a, b -> a.finished == b.finished }.collect { run -> run.report?.let { recorder.scanFinished(it) } }"))
        assertTrue(init, init.contains("cno.state.map { it.pausedUntilMs?.takeIf { p -> p > System.currentTimeMillis() } }.distinctUntilChanged().filterNotNull().collect { until ->\n                recorder.cnoPaused(until, System.currentTimeMillis())"))
        assertTrue(init, init.contains("if (b != null) recorder.settingsChanged(b, s)"))
        assertTrue(src.contains("val recorder = AppRecorder(eventLog, netStats, perf)"))
        assertTrue(src.contains("const val FLUSH_EVERY_MS = 30_000L"))
    }

    @Test
    fun `what the recorder holds goes into the report`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt").readText()
        for (line in listOf("net = c.netStats.snapshot(),", "events = c.eventLog.events(),", "counters = c.eventLog.counters(),", "eventsSinceMs = c.eventLog.sinceMs(),", "perf = c.perf.summaries(),", "coldStartMs = c.perf.coldStartMs,", "previous = runCatching { c.diagHistory.all().lastOrNull() }.getOrNull(),")) {
            assertTrue(line, src.contains(line))
        }
    }
}
