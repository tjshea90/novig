package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.NcaabBetsCard
import com.tjshea.vigilant.app.ui.ScannerFilter
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerSort
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.ncaab.NcaabBetStats
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.SlateProfit
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** NT1 (Tj 2026-11-20): the record of the bets placed by hand from the NCAAB tab, in that tab and on the Tracker's Stats page, with nothing from any other tab in it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class NcaabBetsCardUiTest {
    @get:Rule val compose = createComposeRule()

    private fun et(d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(2026, 11, d, h, mi).atZone(SlateProfit.ET).toInstant().toEpochMilli()
    private val now = et(20, 12)

    private fun bet(id: String, starts: Long, stake: Double, status: BetStatus, label: String = "Moneyline", sel: String = "Duke", origin: String? = BetTracker.ORIGIN_NCAAB, league: String = "NCAAB") =
        TrackedBet(id, starts - 3_600_000L, league, "A @ B", starts, label, sel, "m$id", "o$id", 0.5, 0.5, 0.52, 0.04, stake, status, settledAtMs = starts + 2 * 3_600_000L, origin = origin)

    private val tabBets = listOf(
        bet("w1", et(19, 19), 10.0, BetStatus.WON), bet("w2", et(19, 20), 20.0, BetStatus.WON), bet("l1", et(19, 21), 10.0, BetStatus.LOST, label = "Spread", sel = "Duke -3.5"),
        bet("t1", et(20, 19), 5.0, BetStatus.PENDING), bet("t2", et(20, 15), 15.0, BetStatus.LOST),
    )
    private val summary = NcaabBetStats.summarize(tabBets, now)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) { content() } } }
        }
    }

    @Test fun withNoBetsTheCardSaysWhatItCountsAndShowsNoNumbers() {
        show { NcaabBetsCard(NcaabBetStats.summarize(emptyList(), now)) }
        compose.onNodeWithTag("ncaabBetsEmpty").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTagCount("ncaabBets-bets"))
    }

    @Test fun theNumbersAreTheTabBetsAloneAllTimeAndToday() {
        show { NcaabBetsCard(summary) }
        compose.onNodeWithTag("ncaabBets-bets-all").assertTextEquals("5")
        compose.onNodeWithTag("ncaabBets-bets-today").assertTextEquals("2")
        compose.onNodeWithTag("ncaabBets-staked-all").assertTextEquals("$60.00")
        compose.onNodeWithTag("ncaabBets-staked-today").assertTextEquals("$20.00")
        // won 10 + 20, lost 10 and 15 (today's 3 pm game): +5.00 all time; today's settled bet lost 15
        compose.onNodeWithTag("ncaabBets-profit-all").assertTextEquals("+$5.00")
        compose.onNodeWithTag("ncaabBets-profit-today").assertTextEquals("−$15.00")
        compose.onNodeWithTag("ncaabBets-record-all").assertTextEquals("2-2")
        compose.onNodeWithTag("ncaabBets-winPct-all").assertTextEquals("50%")
        compose.onNodeWithTag("ncaabBets-open-all").assertTextContains("1 · $5.00")
        compose.onNodeWithTag("ncaabBets-facts").assertTextContains("Best win +$20.00")
    }

    @Test fun inTheTabItIsOneLineUntilOpenedAndThenShowsEveryNumberAndTheDays() {
        show { NcaabBetsCard(summary, collapsible = true) }
        compose.onNodeWithTag("ncaabBetsLine").assertTextContains("5 bets")
        compose.onNodeWithTag("ncaabBetsLine").assertTextContains("$60.00 bet")
        compose.onNodeWithTag("ncaabBetsLine").assertTextContains("+$5.00")
        compose.onNodeWithTag("ncaabBetsLineToday").assertTextContains("Today: 2 bets")
        assertEquals("details are not drawn while collapsed", 0, compose.onAllNodesWithTagCount("ncaabBets-profit"))
        compose.onNodeWithTag("ncaabBetsToggle").performClick()
        compose.onNodeWithTag("ncaabBets-profit-all").assertTextEquals("+$5.00")
        compose.onNodeWithTag("ncaabBetsDays").performScrollTo().performClick()
        compose.onNodeWithTag("ncaabBetsDay-2026-11-19").assertIsDisplayed()
        compose.onNodeWithTag("ncaabBetsDay-2026-11-20").assertIsDisplayed()
        compose.onNodeWithTag("ncaabBetsMarkets").performScrollTo().performClick()
        compose.onNodeWithTag("ncaabBetsMarket-Moneyline").assertIsDisplayed()
        compose.onNodeWithTag("ncaabBetsMarket-Spread").assertIsDisplayed()
    }

    @Test fun theTrackerFilterAndCardLeaveEveryOtherTabsBetOut() {
        val other = bet("college", et(19, 19), 40.0, BetStatus.WON, origin = null)          // a +EV card bet on a college game: not a tab bet
        val nba = bet("nba", et(19, 19), 40.0, BetStatus.LOST, origin = null, league = "NBA")
        val all = tabBets + other + nba
        assertEquals(tabBets.map { it.id }.toSet(), TrackerSort.inScanner(all, ScannerFilter.NCAAB).map { it.id }.toSet())
        assertEquals("the Vigilant chip no longer holds the tab's bets", listOf("college", "nba"), TrackerSort.inScanner(all, ScannerFilter.VIGILANT).map { it.id })
        val s = NcaabBetStats.summarize(all, now)
        assertEquals(5, s.all.bets); assertEquals(60.0, s.all.staked, 1e-9)
        val base = SampleScan.state()
        show { TrackerScreen(base.copy(bets = all), { _, _ -> }, {}, initialView = TrackerView.STATS) }
        compose.onNodeWithTag("ncaabBets").assertExists()
        compose.onNodeWithTag("ncaabBets-staked-all").assertTextEquals("$60.00")
    }

    @Test fun theTrackerShowsNoCardWhenNoBetCameFromTheTab() {
        val base = SampleScan.state()
        show { TrackerScreen(base, { _, _ -> }, {}, initialView = TrackerView.STATS) }
        assertEquals(0, compose.onAllNodesWithTagCount("ncaabBets"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
