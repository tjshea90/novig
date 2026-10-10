package com.tjshea.vigilant.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tj, 2026-10-02: "If I press check odds now, or pull to refresh, and the scanner is paused, automatically resume the scanner." His own pull or
 * Check odds now resumes the scanner (saved, as ▶ Resume saves it) and then does what he asked; a plain Scan or Refresh still says it's paused.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutoResumeAppTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

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

    /** A paused app with the scanner on [mode], its toasts collected into [toasts]. */
    private fun pausedApp(mode: ScannerMode, toasts: MutableList<String>, listen: CoroutineScope): MainViewModel {
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.settingsStore.update { ScanSettings(scanner = mode, pausedByHand = true) }
        }
        val vm = MainViewModel(app)
        listen.launch { vm.toasts.collect { toasts += it } }
        waitFor("loaded paused") { vm.state.value.loaded && vm.state.value.settings.paused }
        return vm
    }

    private fun savedPaused() = runBlocking { app.container.currentSettings() }.paused

    @Test
    fun `Check odds now while paused resumes the scanner and checks`() {
        val toasts = ArrayList<String>()
        val listen = CoroutineScope(Dispatchers.Unconfined)
        try {
            val vm = pausedApp(ScannerMode.BOTH, toasts, listen)
            vm.checkOdds()
            waitFor("resumed") { !vm.state.value.settings.paused && toasts.isNotEmpty() }
            assertFalse("saved, as the Resume button saves it", savedPaused())
            waitFor("the check's report") { toasts.any { it.startsWith("No open bets to check") } }
            assertEquals(RESUMED_TOAST, toasts.first())
            assertFalse(toasts.contains(PAUSED_TOAST))
        } finally {
            listen.cancel()
        }
    }

    @Test
    fun `a pull to refresh while paused resumes the scanner, on the +EV tab and on CNO's`() {
        val toasts = ArrayList<String>()
        val listen = CoroutineScope(Dispatchers.Unconfined)
        try {
            // CNO only: the scan after resuming starts nothing (Vigilant asleep), so no network is read here.
            val vm = pausedApp(ScannerMode.CNO, toasts, listen)
            vm.scan(resume = true)
            waitFor("resumed by the +EV pull, and said") { !vm.state.value.settings.paused && toasts.isNotEmpty() }
            assertFalse(savedPaused())
            assertEquals(listOf(RESUMED_TOAST), toasts)
        } finally {
            listen.cancel()
        }
        val toasts2 = ArrayList<String>()
        val listen2 = CoroutineScope(Dispatchers.Unconfined)
        try {
            // Vigilant only: the CNO read after resuming starts nothing either.
            val vm = pausedApp(ScannerMode.VIGILANT, toasts2, listen2)
            vm.refreshCno(resume = true)
            waitFor("resumed by the CNO pull, and said") { !vm.state.value.settings.paused && toasts2.isNotEmpty() }
            assertFalse(savedPaused())
            assertEquals(listOf(RESUMED_TOAST), toasts2)
        } finally {
            listen2.cancel()
        }
    }

    @Test
    fun `the plain Scan and Refresh buttons still keep the pause`() {
        val toasts = ArrayList<String>()
        val listen = CoroutineScope(Dispatchers.Unconfined)
        try {
            val vm = pausedApp(ScannerMode.BOTH, toasts, listen)
            vm.scan()
            vm.refreshCno()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(vm.state.value.settings.paused)
            assertTrue(savedPaused())
            assertEquals(List(2) { PAUSED_TOAST }, toasts)
        } finally {
            listen.cancel()
        }
    }

    @Test
    fun `the screens' pulls are the resuming ones`() {
        val main = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt").readText()
        assertTrue(main.contains("onPull = { scan(resume = true) }"))
        assertTrue(main.contains("onPull = { vm.refreshCno(resume = true) }"))
        assertTrue(main.contains("onCheckOdds = vm::checkOdds"))
        val feed = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/ui/FeedScreen.kt").readText()
        assertTrue(feed.contains("onRefresh = onPull,"))
        assertTrue(java.io.File("src/main/kotlin/com/tjshea/vigilant/app/ui/CnoScreen.kt").readText().contains("onRefresh = onPull,"))
    }
}
