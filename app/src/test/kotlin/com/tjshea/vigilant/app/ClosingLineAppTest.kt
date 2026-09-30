package com.tjshea.vigilant.app

import android.app.AlarmManager
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.ClosingLineCard
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.ClvPeriod
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * True closing line value (Tj, 2026-09-29) on a real app: the alarm is armed for the next start as bets come and go, the capture reads nothing
 * while paused (but notes the try), and the Stats card shows the share that beat the close and by how much, with its own periods and outliers.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class ClosingLineAppTest {

    @get:Rule val compose = createComposeRule()

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val min = 60_000L
    private val hour = 60 * min

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("never happened: $what")
    }

    private fun bet(id: String, starts: Long, closingFair: Double? = null, seenBefore: Long? = null, status: BetStatus = BetStatus.PENDING, placed: Long = starts - 24 * hour) = TrackedBet(
        id, placed, "NBA", "A @ B", starts, "Moneyline", "A", "m-$id", "o-$id", 0.5, 0.5, 0.515, 0.03, 10.0,
        status = status, closingFair = closingFair, closingSeenAtMs = seenBefore?.let { starts - it },
    )

    private fun writeBets(vararg bets: TrackedBet) {
        File(app.filesDir, "bets.json").writeText(kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(TrackedBet.serializer()), bets.toList()))
    }

    private fun alarmAt(): Long? {
        val am = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        return am.scheduledAlarms.firstOrNull { it.operation?.let { p -> shadowOf(p).savedIntent.action } == ClosingAlarm.ACTION }?.triggerAtTime
    }

    // ---- the capture's alarm and run ---------------------------------------------------------------------------------

    @Test
    fun `the alarm is armed 6 minutes before the next start, and follows the bets`() {
        val now = System.currentTimeMillis()
        val later = now + 5 * hour
        writeBets(bet("later", later), bet("sooner", now + 2 * hour))
        val c = AppContainer(app) // a new process over these bets: its watcher arms the alarm
        waitFor("the alarm for the sooner game") { alarmAt() == now + 2 * hour - ClosingLine.LEAD_MS }
        // The sooner bet is settled early (a cash-out, a void): the alarm moves to the next game.
        runBlocking { c.tracker.settle("sooner", BetStatus.VOID) }
        waitFor("the alarm for the later game") { alarmAt() == later - ClosingLine.LEAD_MS }
        // Nothing left to close: no alarm.
        runBlocking { c.tracker.delete("later") }
        waitFor("no alarm") { alarmAt() == null }
    }

    @Test
    fun `while scanning is paused the capture reads nothing, and waits before trying again`() {
        val now = System.currentTimeMillis()
        writeBets(bet("due", now + 8 * min), bet("far", now + 3 * hour))
        val c = AppContainer(app)
        runBlocking {
            c.settingsStore.update { it.copy(paused = true) }
            val r = ClosingCapture.run(c, now)
            assertEquals(ClosingCapture.Result(due = 1, read = 0), r)
            val bets = c.tracker.all()
            assertEquals(now, bets.first { it.id == "due" }.closeTriedAtMs)
            assertNull(bets.first { it.id == "far" }.closeTriedAtMs)
            // Tried just now: the next try is 2 minutes later, not at once.
            assertEquals(now + ClosingLine.RETRY_MS, ClosingLine.nextAt(bets, now))
            assertEquals(ClosingCapture.Result(0, 0), ClosingCapture.run(c, now + 30_000))
            c.settingsStore.update { it.copy(paused = false) }
        }
    }

    // ---- the Stats card --------------------------------------------------------------------------------------------

    private val start = SampleScan.NOW - 2 * hour
    private val closed = listOf(
        bet("a", start, closingFair = 0.52, seenBefore = 5 * min),               // +4%
        bet("b", start, closingFair = 0.51, seenBefore = 5 * min, status = BetStatus.WON), // +2%
        bet("c", start, closingFair = 0.49, seenBefore = 5 * min, status = BetStatus.LOST), // −2%
        bet("big", start, closingFair = 0.56, seenBefore = 5 * min),             // +12%: an outlier
        bet("early", start, closingFair = 0.52, seenBefore = 5 * hour),          // read hours early: no close
        bet("open", SampleScan.NOW + 5 * hour),                                  // waiting
    )

    private fun card() = compose.setContent {
        VigilantTheme(darkTheme = true) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                var period by remember { mutableStateOf(ClvPeriod.ALL) }
                var hide by remember { mutableStateOf(false) }
                Column(Modifier.verticalScroll(rememberScrollState())) { ClosingLineCard(closed, SampleScan.NOW, period, { period = it }, hide, { hide = it }) }
            }
        }
    }

    @Test
    fun `the card shows the share that beat the close and the average by how much, true closes only`() {
        card()
        // 4 true closes (+4, +2, −2, +12): 3 beat it; average +4%.
        compose.onNodeWithContentDescription("Beat the close 75% (3 of 4) · avg vs close +4.0% · avg EV at bet +3.0%").assertExists()
        compose.onNodeWithText("4 bets with a true close · 1 waiting for their close (game not started) · 1 started with no close read · 1 over ±5% included").assertExists()
    }

    @Test
    fun `Hide outliers takes out the bets over 5 percent from the close`() {
        card()
        compose.onNodeWithTag("clvOutliers").performClick()
        compose.onNodeWithContentDescription("Beat the close 67% (2 of 3) · avg vs close +1.3% · avg EV at bet +3.0%").assertExists()
        compose.onNodeWithText("1 over ±5% left out", substring = true).assertExists()
    }

    @Test
    fun `the periods count by when each bet was placed`() {
        card()
        // Every sample bet was placed a day before its game: none today.
        compose.onNodeWithTag("clvPeriod-TODAY").performClick()
        compose.onNodeWithContentDescription("Beat the close – · avg vs close – · avg EV at bet –").assertExists()
        compose.onNodeWithTag("clvPeriod-WEEK").performClick()
        compose.onNodeWithContentDescription("Beat the close 75% (3 of 4) · avg vs close +4.0% · avg EV at bet +3.0%").assertExists()
    }

    @Test
    fun `the card is in the Tracker's Stats, over every bet whatever period is picked at the top`() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.tjshea.vigilant.app.ui.LocalClock provides { SampleScan.NOW }) {
                VigilantTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        TrackerScreen(SampleScan.state().copy(bets = closed), { _, _ -> }, {}, initialView = TrackerView.STATS)
                    }
                }
            }
        }
        compose.onNodeWithTag("clvCard").assertExists()
        compose.onNodeWithText("Today").performClick() // the pinned period: no bets placed today
        compose.onNodeWithTag("clvCard").assertDoesNotExist()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithContentDescription("Beat the close 75% (3 of 4) · avg vs close +4.0% · avg EV at bet +3.0%").assertExists()
    }

    @Test
    fun `screenshot - the closing line value card`() {
        card()
        compose.onRoot().captureRoboImage("screenshots/4j_tracker_clv_card.png")
    }
}
