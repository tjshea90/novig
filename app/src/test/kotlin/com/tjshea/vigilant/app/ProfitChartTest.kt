package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.ChartView
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.ProfitChartCard
import com.tjshea.vigilant.app.ui.ProfitText
import com.tjshea.vigilant.app.ui.TrackerPeriod
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.WalletMoneyUi
import com.tjshea.vigilant.app.ui.inPeriod
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.tracker.WalletProfit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tracker's profit card (Tj, 2026-10-19, PL: "Today's slate has 0 settled bets and should show 0 profit or loss ... make a chart which shows daily profit/loss and total all time profit/loss that is
 * logical and shows an accurate number"): all-time money on top (the wallet's, or the settled bets' when there is none), the Tracker's own period below it, Today as today's games, open stakes never a loss.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class ProfitChartTest {
    @get:Rule val compose = createComposeRule()

    // Sat 2026-09-26 01:20 Eastern: before 5 am, so "today" is Friday the 25th's games.
    private val now = SampleScan.NOW
    private val day = 24L * 3_600_000L

    private fun bet(id: String, starts: Long, status: BetStatus, stake: Double = 1.0) =
        TrackedBet(id, starts - 2 * 3_600_000L, "NFL", "A @ B", starts, "Moneyline", "Team $id", "m$id", "o$id", 0.5, 0.5, 0.52, 0.04, stake, status)

    // One settled bet a day for ten days (Friday the 25th back to the 16th), each +$1 or -$1 (cost 0.5), the 25th's won.
    private val tenDays = (0 until 10).map { d -> bet("b$d", now - d * day - 2 * 3_600_000L, if (d % 2 == 0) BetStatus.WON else BetStatus.LOST) }

    private fun show(list: List<TrackedBet>, period: TrackerPeriod, wallet: WalletMoneyUi = WalletMoneyUi()) {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalClock provides { now }) {
                VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ProfitChartCard(list, period, wallet) } }
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
        assertEquals(w.start + w.span / 4, z.start + z.span / 4)
        val out = ChartView.zoom(w, 0.5, 0.01, min, max)
        assertEquals(min, out.start); assertEquals(max, out.end)
        assertEquals(ChartView.MIN_SPAN_MS, ChartView.zoom(w, 0.5, 1_000_000.0, min, max).span)
        assertEquals(w, ChartView.zoom(w, 0.5, 0.0, min, max))
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
        assertTrue(ChartView.ticks(ChartView.Window(0, 60 * day), 6, zone).size in 2..20)
    }

    // ---- the words ------------------------------------------------------------------------------------------------------------

    @Test
    fun `a zero is $0_00 with no sign, and a chart time always names its day`() {
        assertEquals("\$0.00", ProfitText.pl(0.0))
        assertEquals("\$0.00", ProfitText.pl(-0.001))
        assertEquals("+\$1.50", ProfitText.pl(1.5))
        assertEquals("Sat 1:20 AM", ProfitText.dayTime(now))
    }

    // ---- the card -------------------------------------------------------------------------------------------------------------

    @Test
    fun `today is today's games, $0_00 until one is graded, and its open stake is at stake, not lost`() {
        // Settled bets on the three days before, one open bet on tonight's late game and one on next week's.
        val bets = tenDays.drop(1).take(3) + bet("t", now + 3_600_000L, BetStatus.PENDING, stake = 15.0) + bet("w", now + 5 * day, BetStatus.PENDING, stake = 25.0)
        show(bets, TrackerPeriod.TODAY)
        compose.onNodeWithTag("profitDayLine").assertTextContains("Fri Sep 25 games", substring = true)
        compose.onNodeWithTag("profitInView").assertTextContains("\$0.00", substring = true)
        compose.onNodeWithTag("profitChartReadout").assertTextContains("No results yet: \$0.00", substring = true)
        compose.onNodeWithTag("profitChartReadout").assertTextContains("\$15.00 is at stake on 1 open bet", substring = true)
        compose.onNodeWithTag("profitLater").assertTextContains("1 open bet on later games (\$25.00", substring = true)
        compose.onNodeWithTag("profitCanvas").assertDoesNotExist()
        // No wallet reading: all time is the three settled bets (-1, +1, -1), said so.
        compose.onNodeWithTag("profitAllTime").assertTextEquals("−\$1.00")
        compose.onNodeWithTag("profitAllTimeHow").assertTextContains("From the 3 settled bets", substring = true)
    }

    @Test
    fun `today with results runs a line from the first game, with the day on its times`() {
        show(tenDays, TrackerPeriod.TODAY)
        compose.onNodeWithTag("profitInView").assertTextContains("+\$1.00", substring = true)
        compose.onNodeWithTag("profitCanvas").assertExists()
        compose.onNodeWithTag("profitDayLine").assertTextContains("last result Fri", substring = true)
        compose.onRoot().captureRoboImage("screenshots/4t_profit_chart.png")
    }

    @Test
    fun `the wallet's real money heads the card, open bets at cost, deposits from Novig's record`() {
        val m = WalletProfit.Money(198.63, 42.55, 12, 230.0, 0.0, 2, now - 120_000L)
        show(tenDays, TrackerPeriod.DAYS_7, WalletMoneyUi(m))
        compose.onNodeWithTag("profitAllTime").assertTextEquals("+\$11.18")
        compose.onNodeWithTag("profitAllTimeHow").assertTextContains("Vigilant wallet \$241.18: \$198.63 cash + \$42.55 in 12 open bets, minus the \$230.00 put in", substring = true)
        compose.onNodeWithText("on \$230.00 put in").assertExists()
        compose.onNodeWithText("The settled bets shown here: \$0.00 on \$10.00 staked.").assertExists()
    }

    @Test
    fun `seven days are a running total, a bar a day and the days one by one`() {
        show(tenDays, TrackerPeriod.DAYS_7)
        // Fri 25th back to Sat 19th: won, lost, won, lost, won, lost, won = +$1.00.
        compose.onNodeWithTag("profitInView").assertTextContains("Last 7 days", substring = true)
        compose.onNodeWithTag("profitInView").assertTextContains("+\$1.00", substring = true)
        compose.onNodeWithTag("profitRunning").assertExists()
        compose.onNodeWithTag("profitBars").assertExists()
        compose.onNodeWithTag("profitDay-2026-09-25").assertExists()
        compose.onNodeWithTag("profitDay-2026-09-18").assertDoesNotExist()
        compose.onRoot().captureRoboImage("screenshots/4u_profit_chart_days.png")
    }

    @Test
    fun `All runs from the first day with a bet`() {
        show(tenDays, TrackerPeriod.ALL)
        compose.onNodeWithTag("profitInView").assertTextContains("Since Sep 16", substring = true)
        compose.onNodeWithTag("profitInView").assertTextContains("\$0.00", substring = true)
        compose.onNodeWithTag("profitDay-2026-09-16").assertExists()
    }

    @Test
    fun `with no bets it says so`() {
        show(emptyList(), TrackerPeriod.TODAY)
        compose.onNodeWithTag("profitAllTime").assertTextEquals("\$0.00")
        compose.onNodeWithText("No bets on today's games yet: \$0.00.").assertExists()
        compose.onNodeWithTag("profitCanvas").assertDoesNotExist()
    }

    @Test
    fun `the Tracker's Today is today's games, and the profit card stays when they are none`() {
        // Bets placed today on games of the days before are not today's; a bet placed days ago on today's game is.
        val placedToday = tenDays.drop(1).take(2).map { it.copy(createdAtMs = now - 60_000L) }
        val placedEarlier = bet("e", now + 3_600_000L, BetStatus.PENDING).copy(createdAtMs = now - 4 * day)
        assertEquals(listOf("e"), inPeriod(placedToday + placedEarlier, TrackerPeriod.TODAY, now).map { it.id })
        assertEquals(3, inPeriod(placedToday + placedEarlier, TrackerPeriod.DAYS_7, now).size)
        val state = SampleScan.state().copy(bets = placedToday)
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalClock provides { now }) {
                VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TrackerScreen(state, { _, _ -> }, {}, initialView = TrackerView.STATS) } }
            }
        }
        compose.onNode(hasText("Today") and hasTestTag("periodChip-TODAY")).performClick()
        compose.onNodeWithTag("profitChart").assertExists()
        compose.onNodeWithText("No bets in this period").assertExists()
    }
}
