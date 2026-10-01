package com.tjshea.vigilant.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-09-28: "Make an option in the app to pause all scanning". The app's own state holder, on a real app: while
 * paused nothing starts (Scan, Recheck, Refresh say it's paused) and CNO isn't read even with its tab open.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PauseScanningAppTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    /** Runs the main thread's work until [cond] holds (settings load off it, on the IO pool). */
    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    @Test
    fun `while paused, Scan, Recheck and Refresh start nothing and say so, and CNO isn't read with its tab open`() {
        val vm = MainViewModel(app)
        val toasts = ArrayList<String>()
        val listen = CoroutineScope(Dispatchers.Unconfined)
        listen.launch { vm.toasts.collect { toasts += it } }
        try {
            waitFor("settings loaded") { vm.state.value.loaded }
            vm.setPaused(true)
            waitFor("paused") { vm.state.value.settings.paused }
            waitFor("the pause's own toast") { "Scanning paused: nothing is read until you resume" in toasts }
            assertTrue(kotlinx.coroutines.runBlocking { app.container.currentSettings() }.paused) // saved: it outlives a restart

            toasts.clear()
            vm.scan()
            vm.recheck(listOf("m1"))
            vm.refreshCno()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(app.container.runner.running)
            assertFalse(vm.state.value.status.scanning)
            assertFalse(vm.state.value.status.rechecking)
            assertFalse(app.container.cno.state.value.refreshing)
            assertEquals(List(3) { PAUSED_TOAST }, toasts)

            // The CNO tab open while paused: its reads stay asleep.
            vm.watchCno("tab", true)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(vm.state.value.cnoLive)
            vm.watchCno("tab", false)
        } finally {
            listen.cancel()
        }
    }

    /** Source pins for what needs a live scan or Android's service lifecycle (no unit-test harness here). */
    @Test
    fun `pausing stops a running scan and background auto-scan, and resuming starts auto-scan again`() {
        val vm = File("src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt").readText()
        assertTrue(vm.contains("if (!before.paused && next.paused) c.runner.stop()"))
        // CNO's reads are held by the pause (and, since v0.40.1, also by a Check odds now: [cnoReadsHeld]).
        assertTrue(vm.contains("state.map(::cnoReadsHeld).distinctUntilChanged().collect { cnoWatch.hold(it) }"))
        assertTrue(vm.contains("internal fun cnoReadsHeld(s: UiState): Boolean = !s.loaded || s.settings.paused || s.checkingOdds"))
        val service = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScanService.kt").readText()
        assertTrue(service.contains("container.settingsStore.flow.filterNotNull().map { it.activeAutoScan to it.autoScanSeconds }"))
        assertTrue(service.contains("app.container.currentSettings().activeAutoScan != AutoScanMode.OFF"))
        val cycle = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        assertTrue(cycle.contains("if (settings.activeAutoScan == AutoScanMode.OFF) return false"))
        val activity = File("src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt").readText()
        // Paused is part of activeAutoScan (OFF while paused), so the same line also stops the service when the scanner choice leaves nothing to read.
        assertTrue(activity.contains("mode != com.tjshea.vigilant.data.scanner.AutoScanMode.OFF && active == com.tjshea.vigilant.data.scanner.AutoScanMode.OFF"))
        // A scan stopped by a pause isn't reported as done.
        val scan = File("src/main/kotlin/com/tjshea/vigilant/app/ScanService.kt").readText()
        assertTrue(scan.contains("it.vigilantOn && !it.paused"))
    }
}
