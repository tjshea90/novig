package com.tjshea.vigilant.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.CloseBackfill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-10-01: "Also make it so if I 'check odds now', make sure it gets all available closing line data, and make it pause other parts of the app
 * such as the cno scanner so that it focuses on refreshing the current odds and EV and stats." The focus on a real app: while a check runs the loops that
 * read on their own wait (and say so), and it all goes on when the check ends; the same tap looks for every closing line.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CheckFocusAppTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.settingsStore.update { ScanSettings() }
        }
        app.container.focus.end()
    }

    @After fun tearDown() {
        app.container.focus.end()
        runBlocking { app.container.settingsStore.update { ScanSettings() } }
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1000) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    // ---- the gate ---------------------------------------------------------------------------------------------------

    @Test
    fun `the focus is held from begin to end, and never longer than 15 minutes`() {
        var now = 1_000_000L
        val gate = FocusGate { now }
        assertFalse(gate.active())
        gate.begin()
        assertTrue(gate.active())
        assertEquals(1_000_000L, gate.since.value)
        now += FocusGate.CEILING_MS - 1
        assertTrue(gate.active())
        // A check that hangs can't hold the app: the focus lapses by itself.
        now += 1
        assertFalse(gate.active())
        gate.begin()
        assertTrue(gate.active())
        gate.end()
        assertFalse(gate.active())
        assertNull(gate.since.value)
    }

    // ---- while a check runs -----------------------------------------------------------------------------------------

    @Test
    fun `while Check odds now runs, Scan, Recheck and Refresh start nothing and say why, and a background cycle does nothing`() {
        runBlocking { app.container.settingsStore.update { it.copy(autoScan = AutoScanMode.CNO) } }
        val vm = MainViewModel(app)
        val toasts = ArrayList<String>()
        val listen = CoroutineScope(Dispatchers.Unconfined)
        listen.launch { vm.toasts.collect { toasts += it } }
        try {
            waitFor("settings loaded") { vm.state.value.loaded }
            vm.checkOdds()
            // The focus is held the moment the check starts, before anything has been read.
            assertTrue(vm.state.value.checkingOdds)
            assertTrue(app.container.focus.active())
            toasts.clear()
            vm.scan()
            vm.recheck(listOf("m1"))
            vm.refreshCno()
            assertEquals(List(3) { CHECKING_TOAST }, toasts)
            assertFalse(app.container.runner.running)
            assertFalse(app.container.cno.state.value.refreshing)
            // CNO's own loops are held by the same state.
            assertTrue(cnoReadsHeld(vm.state.value))
            // A background cycle that falls in the check reads nothing, bets nothing, and the notification says why.
            val cycled = runBlocking { app.container.autoScan.cycle() }
            assertFalse(cycled)
            val status = app.container.autoScan.status.value
            assertTrue(status.pausedForCheck)
            assertNull("no CNO read was made", app.container.cno.state.value.lastAttemptMs)
            assertEquals("Paused while Check odds now runs (auto-bet too)", AutoScanText.status(status, vm.state.value.settings, null, System.currentTimeMillis()))
            waitFor("the check finishing") { !vm.state.value.checkingOdds }
        } finally {
            listen.cancel()
        }
    }

    @Test
    fun `when the check ends everything goes on, and the same tap asked every close source about every started bet`() {
        val vm = MainViewModel(app)
        val toasts = ArrayList<String>()
        val listen = CoroutineScope(Dispatchers.Unconfined)
        listen.launch { vm.toasts.collect { toasts += it } }
        try {
            waitFor("settings loaded") { vm.state.value.loaded }
            vm.checkOdds()
            waitFor("the check finishing") { !vm.state.value.checkingOdds }
            // Released: the loops go on, the status stops saying it's paused.
            assertFalse(app.container.focus.active())
            assertFalse(cnoReadsHeld(vm.state.value))
            assertFalse(app.container.autoScan.status.value.pausedForCheck)
            // The look for closes was the forced one, and the report says what it found.
            assertTrue("a Check odds now looks for every close, not the 3-hourly retry's few", app.container.lastBackfill!!.forced)
            waitFor("the report") { toasts.any { it.contains("Closing lines:") } }
            assertTrue(toasts.toString(), toasts.any { it.startsWith("No open bets to check") && it.contains("Closing lines: every started bet has one") })
            // Nothing is held back by the check afterwards.
            toasts.clear()
            vm.refreshCno()
            assertFalse(toasts.contains(CHECKING_TOAST))
        } finally {
            listen.cancel()
        }
    }

    // ---- what the report says about closes -----------------------------------------------------------------------------

    private fun bet(id: String, start: Long, close: Double? = null, status: BetStatus = BetStatus.PENDING) =
        com.tjshea.vigilant.data.tracker.TrackedBet(id, start - 3_600_000L, "NFL", "A @ B", start, "Moneyline", "A", "m", "o", 0.5, 0.5, 0.52, 0.04, 1.0, status = status, closeFair = close, closeVia = close?.let { "ESPN" })

    @Test
    fun `the close sentence says what was found, by whom, and what is still missing and why`() {
        val now = 10_000_000L
        val bets = listOf(
            bet("ok", now - 3_600_000L, close = 0.6), bet("a", now - 3_600_000L), bet("b", now - 7_200_000L),
            bet("void", now - 3_600_000L, status = BetStatus.VOID), bet("future", now + 3_600_000L),
        )
        assertEquals("only started, non-void bets without a close", 2, CloseText.missing(bets, now))
        val r = CloseBackfill.Report(looked = 5, found = 3, bySource = mapOf("ESPN" to 1, "Novig's last trades" to 2), missing = mapOf("Novig publishes this day's trades the next morning" to 2), forced = true)
        assertEquals(
            "Closing lines: found 3 of 5 (ESPN 1, Novig's last trades 2) · 2 started bets still have none: Novig publishes this day's trades the next morning (2)",
            CloseText.summary(r, missing = 2),
        )
        assertEquals("Closing lines: found 3 of 3 (ESPN 3) · every started bet has one now", CloseText.summary(CloseBackfill.Report(3, 3, mapOf("ESPN" to 3), forced = true), missing = 0))
        assertEquals("Closing lines: every started bet has one", CloseText.summary(null, missing = 0))
        assertEquals("Closing lines: every started bet has one", CloseText.summary(CloseBackfill.Report(0, 0, emptyMap(), forced = true), missing = 0))
        val recent = CloseText.summary(CloseBackfill.Report(0, 0, emptyMap(), forced = true), missing = 4)
        assertTrue(recent, recent.startsWith("Closing lines: 4 started bets have none yet (looked for in the last 10 minutes"))
        assertEquals("Closing lines: 1 started bet has none yet", CloseText.summary(null, missing = 1).substringBefore(" (looked"))
    }

    // ---- what else waits: the loops with no UI of their own ------------------------------------------------------------

    @Test
    fun `the widget's rescans, ParlayAPI's movers and injury look-ups also wait for the check, and the closing capture doesn't`() {
        val vm = File("src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt").readText()
        val rescan = vm.substringAfter("private suspend fun rescanWhileWatched()").substringBefore("private suspend fun applySettings")
        assertTrue(rescan.contains("s.status.scanning || s.checkingOdds"))
        val movers = vm.substringAfter("private suspend fun keepMovers()").substringBefore("The \"Pinnacle moved toward/against\"")
        assertTrue(movers.contains("on && !checking"))
        assertTrue(vm.contains("if (!_state.value.checkingOdds) uncovered.forEach"))
        // The pre-start closing capture is time-critical (the last read before the start is the close): it is not held.
        val capture = File("src/main/kotlin/com/tjshea/vigilant/app/ClosingCapture.kt").readText()
        assertFalse(capture.contains("focus"))
        // The check holds the focus before it reads anything, stops a running scan, and lets go in the finally.
        val check = vm.substringAfter("fun checkOdds()").substringBefore("fun setStake")
        assertTrue(check.indexOf("c.focus.begin()") in 0 until check.indexOf("c.recheck.preview()"))
        assertTrue(check.contains("c.runner.stop()"))
        assertTrue(check.substringAfter("} finally {").contains("c.focus.end()"))
        assertTrue(check.contains("gradeAll(force = true, forceCloses = true)"))
    }

    @Test
    fun `the cycle checks the focus before it reads anything`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        val cycle = src.substringAfter("suspend fun cycle(forceVigilant: Boolean = false): Boolean").substringBefore("private suspend fun cnoRead")
        assertTrue(cycle.indexOf("c.focus.active(clock())") in 0 until cycle.indexOf("cnoRead(s)"))
        assertTrue(cycle.indexOf("c.focus.active(clock())") in 0 until cycle.indexOf("c.autoBet.run"))
        assertTrue(cycle.indexOf("c.focus.active(clock())") in 0 until cycle.indexOf("vigilantScan(settings)"))
    }
}
