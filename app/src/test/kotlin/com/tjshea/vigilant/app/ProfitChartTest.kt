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
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
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

    // ---- the card (Tj, 2026-10-18, TCL122-6: slates, one fixed start) -----------------------------------------------------------

    @Test
    fun `today is the newest slate, and the chips switch to the slates day by day`() {
        show()
        compose.onNodeWithTag("profitRange-TODAY").assertIsSelected()
        // The newest slate: one bet won at cost 0.5 on a $1 stake.
        compose.onNodeWithTag("profitSlateLine").assertExists()
        compose.onNodeWithText("This slate").assertExists()
        compose.onNodeWithTag("profitCanvas").assertExists()
        compose.onRoot().captureRoboImage("screenshots/4t_profit_chart.png")
        compose.onNodeWithTag("profitRange-YESTERDAY").performClick()
        compose.onNodeWithTag("profitRange-YESTERDAY").assertIsSelected()
        compose.onAllNodesWithText("−\$1.00").fetchSemanticsNodes().isNotEmpty().let { assertTrue(it) }
        // The last 7 slates hold the bets from 0..6 days ago: 4 won (+4) and 3 lost (-3) = +$1.00, a bar each and a row each.
        compose.onNodeWithTag("profitRange-WEEK").performClick()
        compose.onNodeWithTag("profitBars").assertExists()
        compose.onAllNodesWithText("+\$1.00").fetchSemanticsNodes().isNotEmpty().let { assertTrue(it) }
        compose.onRoot().captureRoboImage("screenshots/4u_profit_chart_days.png")
        compose.onNodeWithTag("profitRange-MONTH").assertExists()
        compose.onNodeWithTag("profitRange-ALL").assertExists()
    }

    @Test
    fun `the balance runs from the first day's stakes`() {
        show()
        // Start $1.00 (the first day's one $1 bet), 5 won and 5 lost: balance $1.00.
        compose.onNodeWithTag("profitBalance").assertTextEquals("Balance \$1.00")
        compose.onNodeWithText("Started at \$1.00", substring = true).assertExists()
    }

    @Test
    fun `with no bets it says so`() {
        show(emptyList())
        compose.onNodeWithText("No bets yet.").assertExists()
        compose.onNodeWithTag("profitCanvas").assertDoesNotExist()
    }
}
