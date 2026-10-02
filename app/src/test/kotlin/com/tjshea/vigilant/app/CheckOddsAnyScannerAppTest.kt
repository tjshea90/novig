package com.tjshea.vigilant.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.alerts.EvAlert
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tj, 2026-10-02 (a screenshot of a Vigilant bet still "as of 2h ago · tap Check odds now" after a check, the scanner on CNO only): "I want the
 * check odds now to refresh the current odds and EV for every single open bet regardless of scanner". Vigilant's own pricing used to be skipped
 * whole while the scanner was CNO only ("24 Vigilant bets not updated: the Vigilant scanner is off" on every check in his Diagnostics).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CheckOddsAnyScannerAppTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.settingsStore.update { ScanSettings(scanner = ScannerMode.CNO) }
        }
        app.container.focus.end()
    }

    @After fun tearDown() {
        app.container.focus.end()
        runBlocking {
            app.container.tracker.all().forEach { app.container.tracker.delete(it.id) }
            app.container.settingsStore.update { ScanSettings() }
        }
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1000) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    @Test
    fun `with the scanner on CNO only, Check odds now still prices a Vigilant bet`() {
        // A Vigilant bet (no CNO page) in a league Vigilant's sources don't carry: the pricing pass answers why without a network read, which
        // shows it ran. Skipped, the bet got nothing at all.
        val bet = runBlocking {
            app.container.tracker.logAlert(
                EvAlert(
                    "Vigilant", "m-v/o-v", "o-v", "Under 52.5", "Total", "Stanford @ Wake Forest", 115, 0.015, 7, 7,
                    System.currentTimeMillis() + 86_400_000L, link = null, exact = false, league = "Quidditch", marketId = "m-v",
                ),
            )
        }
        val vm = MainViewModel(app)
        val toasts = ArrayList<String>()
        val listen = CoroutineScope(Dispatchers.Unconfined)
        listen.launch { vm.toasts.collect { toasts += it } }
        try {
            waitFor("loaded") { vm.state.value.loaded }
            assertFalse(vm.state.value.settings.vigilantOn)
            vm.checkOdds()
            waitFor("the check finishing") { !vm.state.value.checkingOdds }
            waitFor("the report") { toasts.isNotEmpty() }
            val after = runBlocking { app.container.tracker.all() }.single { it.id == bet.id }
            assertEquals("Vigilant doesn't price Quidditch", after.nowNote)
            assertFalse(toasts.toString(), toasts.any { it.contains("scanner is off") })
            assertTrue(toasts.toString(), toasts.any { it.startsWith("Checked 0 of 1 open bet") && it.contains("1 couldn't be priced") })
        } finally {
            listen.cancel()
        }
    }
}
