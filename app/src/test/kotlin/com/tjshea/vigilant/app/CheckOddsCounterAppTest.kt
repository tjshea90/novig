package com.tjshea.vigilant.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.TrackerText
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.CheckOddsStats
import com.tjshea.vigilant.data.tracker.LastCheck
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Tracker's "Check odds now" counter on a real app (Tj, 2026-09-29: "This counter should refresh back to zero every time I do a new check
 * for odds"): each check sets its starting line when it begins, saves it, and the app opened again still has it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CheckOddsCounterAppTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1000) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    @Test
    fun `each Check odds now starts the counter again, and the app opened again remembers when`() {
        runBlocking { app.container.tracker.all().forEach { app.container.tracker.delete(it.id) } }
        val vm = MainViewModel(app)
        waitFor("loaded") { vm.state.value.loaded }
        val before = System.currentTimeMillis()
        vm.checkOdds()
        val first = vm.state.value.checkStartedAtMs!!
        assertTrue(first >= before)
        waitFor("the check finishing") { !vm.state.value.checkingOdds }
        waitFor("the start saved") { runBlocking { app.container.lastCheck.read().startedAtMs } == first }

        // A second check moves the starting line: the counter is back at 0 until its reads come in.
        Thread.sleep(5)
        vm.checkOdds()
        val second = vm.state.value.checkStartedAtMs!!
        assertTrue(second > first)
        assertEquals(CheckOddsStats.EMPTY, CheckOddsStats.of(vm.state.value.bets, second, System.currentTimeMillis()))
        waitFor("the second check finishing") { !vm.state.value.checkingOdds }

        // The file the next process reads, and a state holder opened again.
        waitFor("the second start saved") {
            runBlocking { JsonFileStore(File(app.filesDir, "last_check.json"), LastCheck.serializer(), { LastCheck() }).read().startedAtMs } == second
        }
        val again = MainViewModel(app)
        waitFor("loaded again") { again.state.value.loaded }
        assertEquals(second, again.state.value.checkStartedAtMs)
    }

    @Test
    fun `the counter's words`() {
        val s = CheckOddsStats(positive = 3, negative = 1, even = 1, averageEv = -0.0042, averaged = 4, outliers = 1)
        assertEquals("3 +EV · 1 −EV · 1 even · 60% +EV", TrackerText.checkCounts(s))
        assertEquals("Avg −0.4% EV", TrackerText.checkAverage(s))
        assertEquals("Open bets re-priced so far in this check", TrackerText.checkCaption(s.copy(outliers = 0), checking = true, progress = null, startedAtMs = 0, now = 0))
        assertEquals(
            "Open bets re-priced in the check 2h ago · 1 over ±5% left out of the average",
            TrackerText.checkCaption(s, checking = false, progress = 5 to 5, startedAtMs = 0, now = 2 * 3_600_000L),
        )
        // Live games are left out of the counter, and the caption says how many (Tj, 2026-09-30).
        assertEquals(
            "Open bets re-priced in the check 2h ago · 2 live games left out",
            TrackerText.checkCaption(s.copy(outliers = 0, live = 2), checking = false, progress = null, startedAtMs = 0, now = 2 * 3_600_000L),
        )
    }
}
