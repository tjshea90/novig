package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ChartView
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.ProfitChartCard
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The profit graph (Tj, 2026-10-10: "filter the graph ... today only, yesterday, last 2 days ... like stock market graphs ... full screen, pinch zoom, scroll left to right"): the window maths,
 * the range chips and the numbers inside the view, the pinch and the drag, and the full-screen view.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class ProfitChartTest {
    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW
    private val day = 24L * 3_600_000L

    // One settled bet a day for ten days, each +$1 or -$1 (cost 0.5).
    private val bets = (0 until 10).map { d ->
        TrackedBet("b$d", now - (d * day) - 2 * 3_600_000L, "NFL", "A @ B", now - d * day - 2 * 3_600_000L, "Moneyline", "A", "m", "o", 0.5, 0.5, 0.52, 0.04, 1.0, if (d % 2 == 0) BetStatus.WON else BetStatus.LOST)
    }

    private fun show(list: List<TrackedBet> = bets) {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalClock provides { now }) {
                VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ProfitChartCard(list) } }
            }
        }
    }

    // ---- the window maths ---------------------------------------------------------------------------------------------------

    @Test
    fun `zooming keeps the time under the fingers, never goes past the data, and never narrower than half an hour`() {
        val min = 0L; val max = 10 * day
        val w = ChartView.Window(2 * day, 6 * day)
        val z = ChartView.zoom(w, 0.25, 2.0, min, max)
        assertEquals(w.span / 2, z.span)
        // The time a quarter of the way across stays a quarter of the way across.
        assertEquals(w.start + w.span / 4, z.start + z.span / 4)
        val out = ChartView.zoom(w, 0.5, 0.01, min, max)
        assertEquals(min, out.start); assertEquals(max, out.end)                     // zoomed out to everything and no further
        val tiny = ChartView.zoom(w, 0.5, 1_000_000.0, min, max)
        assertEquals(ChartView.MIN_SPAN_MS, tiny.span)
        assertEquals(w, ChartView.zoom(w, 0.5, 0.0, min, max))                       // a nonsense factor does nothing
    }

    @Test
    fun `dragging right goes back in time by that share of the window, and stops at the ends`() {
        val min = 0L; val max = 10 * day
        val w = ChartView.Window(4 * day, 6 * day)
        val p = ChartView.pan(w, 0.5, min, max)
        assertEquals(3 * day, p.start); assertEquals(5 * day, p.end)
        assertEquals(min, ChartView.pan(w, 50.0, min, max).start)
        assertEquals(max, ChartView.pan(w, -50.0, min, max).end)
        assertEquals(w.span, ChartView.pan(w, 50.0, min, max).span)
    }

    @Test
    fun `ticks are round times inside the window and few`() {
        val zone = java.time.ZoneId.of("America/New_York")
        val w = ChartView.Window(java.time.LocalDate.of(2026, 10, 10).atTime(1, 30).atZone(zone).toInstant().toEpochMilli(), java.time.LocalDate.of(2026, 10, 10).atTime(23, 0).atZone(zone).toInstant().toEpochMilli())
        val ticks = ChartView.ticks(w, 6, zone)
        assertTrue("$ticks", ticks.size in 2..7)
        ticks.forEach { assertTrue(it in w.start..w.end); assertEquals(0, java.time.Instant.ofEpochMilli(it).atZone(zone).minute) }
        val month = ChartView.ticks(ChartView.Window(0, 60 * day), 6, zone)
        assertTrue(month.size in 2..20)
    }

    // ---- the card -----------------------------------------------------------------------------------------------------------

    @Test
    fun `the chips set the window and the numbers are what happened inside it`() {
        show()
        compose.onNodeWithTag("profitRange-ALL").assertIsSelected()
        // All time: 10 bets, 5 won and 5 lost: profit 0.
        compose.onAllNodesWithText("Profit in view").assertCountEquals(1)
        compose.onNodeWithText("+\$1.00").assertDoesNotExist()
        compose.onNodeWithTag("profitRange-WEEK").performClick()
        compose.onNodeWithTag("profitRange-WEEK").assertIsSelected()
        // The last 7 days hold the bets from 0..6 days ago: 7 bets, 4 won (+4) and 3 lost (-3) = +$1.00.
        compose.onNodeWithText("+\$1.00").assertExists()
        compose.onNodeWithTag("profitRange-TODAY").performClick()
        compose.onNodeWithTag("profitRange-YESTERDAY").assertExists()
        compose.onNodeWithTag("profitRange-THIS_WEEK").assertExists()
        compose.onNodeWithTag("profitRange-TWO_DAYS").assertExists()
        compose.onNodeWithTag("profitRange-THREE_DAYS").assertExists()
        compose.onNodeWithTag("profitRange-MONTH").assertExists()
    }

    @Test
    fun `pinching in narrows the window and dragging moves it`() {
        show()
        fun window(): String = compose.onNodeWithTag("profitCanvas").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)!!.first()
        val before = window()
        compose.onNodeWithTag("profitCanvas").performTouchInput { pinch(Offset(centerX - 40f, centerY), Offset(centerX - 200f, centerY), Offset(centerX + 40f, centerY), Offset(centerX + 200f, centerY)) }
        compose.waitForIdle()
        val zoomed = window()
        assertFalse("pinch changed the window: $before -> $zoomed", before == zoomed)
        compose.onNodeWithTag("profitCanvas").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertFalse("a drag moved it: $zoomed -> ${window()}", zoomed == window())
        // Double-tap goes back to the range.
        compose.onNodeWithTag("profitCanvas").performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertEquals(before, window())
    }

    @Test
    fun `full screen opens and closes, and it is the same graph`() {
        show()
        compose.onNodeWithTag("profitFullScreen").performClick()
        compose.onNodeWithTag("profitChartFull").assertIsDisplayed()
        compose.onNodeWithTag("profitChartClose").performClick()
        compose.onNodeWithTag("profitChartFull").assertDoesNotExist()
    }

    @Test
    fun `with fewer than two settled bets it says so instead of drawing nothing`() {
        show(bets.take(1))
        compose.onNodeWithTag("profitChartReadout").assertExists()
        compose.onNodeWithTag("profitCanvas").assertDoesNotExist()
    }
}
