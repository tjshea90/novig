package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BookLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tracker's "Novig only" filter (Tj, 2026-10-02 ~18:50Z: "make a filter option for the stats and bet tracker where I can select novig only … show the
 * percent EV compared only from novig odds, filtering out other sports books"): the chip, its own check button, and the EV it shows.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class TrackerNovigOnlyTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    /** An open bet at 0.40: every book says 0.60 now (+50% EV), Novig's own price is 0.46 (+15%). */
    private val bet = TrackedBet(
        id = "b1", createdAtMs = now - 3_600_000, league = "NBA", eventName = "B @ A", startsTs = now + 3_600_000, marketLabel = "Moneyline", selection = "Team A",
        marketId = "m1", outcomeId = "m1-A", price = 0.40, cost = 0.40, fairAtBet = 0.42, evPercentAtBet = 0.05, stake = 4.0, status = BetStatus.PENDING,
        nowFair = 0.60, nowEv = 0.50, nowAtMs = now - 60_000, nowBooks = 7, books = listOf(BookLine("Novig", 150, -170), BookLine("Pinnacle", 140, -160)),
        novigFair = 0.46, novigAtMs = now - 30_000,
    )

    @Test
    fun `the chip switches to Novig's own prices, with its own check button, and every number follows`() {
        var on by mutableStateOf(false)
        val switched = ArrayList<Boolean>()
        var checks = 0
        var odds = 0
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.tjshea.vigilant.app.ui.LocalClock provides { now }) {
                VigilantTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val state = SampleScan.state().let { it.copy(bets = listOf(bet), settings = it.settings.copy(trackerNovigOnly = on)) }
                        TrackerScreen(
                            state, onSettle = { _, _ -> }, onDelete = {}, onCheckOdds = { odds++ }, initialView = TrackerView.BETS,
                            onNovigOnly = { switched += it; on = it }, onCheckNovig = { checks++ },
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("novigOnlyChip").assertIsNotSelected()
        compose.onNodeWithText("Check odds now").assertExists()
        // Every book: +50.0% EV now.
        assertTrue(compose.onAllNodesWithText("+50.0%", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("novigOnlyChip").performClick()
        assertEquals(listOf(true), switched)
        compose.onNodeWithTag("novigOnlyChip").assertIsSelected()
        compose.onRoot().captureRoboImage("screenshots/4n_tracker_novig_only.png")
        // Novig only: +15.0% (0.46 against 0.40), and nothing of every book's +50%.
        assertTrue(compose.onAllNodesWithText("+15.0%", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, compose.onAllNodesWithText("+50.0%", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag("novigOnlyNote").assertExists()
        compose.onNodeWithText("1 of 1 open bets priced", substring = true).assertExists()
        // Its own button reads Novig only.
        compose.onNodeWithTag("checkNovig").performClick()
        assertEquals(1, checks)
        assertEquals(0, odds)
        compose.onNodeWithTag("novigOnlyChip").performClick()
        assertEquals(listOf(true, false), switched)
    }

    @Test
    fun `Tj's bet sheet with Novig only - Novig's odds now the same as bet at is 0% EV, and nothing from another book is shown`() {
        // Tyson Bagent Over 0.5 at +122, Novig still +122 (its other side -223): before, the bid/offer middle (+163) read as -15.56% EV.
        val placed = bet.copy(
            selection = "Tyson Bagent Over 0.5", marketLabel = "Player Passing Interceptions", eventName = "New York Jets @ Chicago Bears",
            american = 122, price = 1.0 / 2.22, cost = 1.0 / 2.22, stake = 1.15, fairAtBet = 0.461, evPercentAtBet = 0.0234,
            novigFair = 0.450, novigAtMs = now - 40_000, nowFair = 0.38, nowEv = -0.1556, nowBooks = 1, gameUrl = "https://crazyninjaodds.com/x",
        )
        val shown = com.tjshea.vigilant.data.tracker.NovigNow.view(listOf(placed)).single()
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.tjshea.vigilant.app.ui.LocalClock provides { now }) {
                VigilantTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val settings = SampleScan.state().settings.copy(trackerNovigOnly = true)
                        com.tjshea.vigilant.app.ui.BetSheetContent(
                            shown, com.tjshea.vigilant.data.tracker.BetInsight.of(shown), now, settings, rereading = false, grading = false, replacing = false,
                            actions = com.tjshea.vigilant.app.ui.BetActions(), onSettle = {}, onStake = {}, onPrice = {}, onDelete = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/4o_novig_only_bet_sheet.png")
        compose.onNodeWithText("Now +0.00% EV at your price").assertExists()
        compose.onNodeWithText("Novig now").assertExists()
        compose.onNodeWithText("the same odds you bet at", substring = true).assertExists()
        compose.onNodeWithText("Novig's odds haven't moved since you placed it.").assertExists()
        compose.onNodeWithText("no other book is used", substring = true).assertExists()
        // Nothing from any other book: no fair from the books, no books behind it, no book table, no other-book EV at bet, no ParlayAPI.
        for (gone in listOf("Fair now", "Books behind it", "Every book", "EV when bet", "Fair when bet", "devigged", "Re-read books", "Price now")) {
            assertEquals(gone, 0, compose.onAllNodesWithText(gone, substring = true).fetchSemanticsNodes().size)
        }
        assertEquals(0, compose.onAllNodesWithText("Second opinion", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("-15.56%", substring = true).fetchSemanticsNodes().size)
    }
}
