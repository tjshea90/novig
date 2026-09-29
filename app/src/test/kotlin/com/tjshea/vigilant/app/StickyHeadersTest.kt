@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.CnoScreen
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.GamesScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.STICKY_BAR
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.tracker.BetStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-09-29: "for any section with tabs on the top, such as the bet tracker section, keep the top navigation tabs sticky to the top. When I scroll
 * down through the long list of my active bets, I still want to have the filters at the top without having to scroll all the way back up."
 * A short screen, so even the sample's few cards overflow it and the list has to scroll.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h420dp-xxhdpi")
class StickyHeadersTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun screen(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    /** The page's own list (the first scrollable-to-a-key node: the league chips' row is inside it). */
    private fun scrollToKey(key: Any) = compose.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey(key)

    /** [text] as drawn inside the pinned bar (the same words are on cards too). */
    private fun inBar(text: String) = compose.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(STICKY_BAR)))

    /** Forty open bets, each a different player, the first with the biggest stake. */
    private fun manyBets(): UiState {
        val base = SampleScan.state()
        val template = base.bets.first { it.status == BetStatus.PENDING }
        return base.copy(bets = (0 until 40).map { i -> template.copy(id = "x$i", selection = "Player $i Over 1.5", stake = if (i == 0) 99.0 else 1.0 + i / 100.0) })
    }

    @Test
    fun `the Tracker's tabs and filters stay on screen while the bets scroll under them`() {
        screen { TrackerScreen(manyBets(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        scrollToKey("x39")
        compose.onNodeWithText("Player 39 Over 1.5", substring = true).assertIsDisplayed()
        // Stats | Bets, Open / Settled / All, Sort and Scanner: all still there, without scrolling back up.
        compose.onNodeWithTag(STICKY_BAR).assertIsDisplayed()
        for (pinned in listOf("Stats", "Bets", "Open (40)", "Sort", "Date placed", "Current EV", "Amount", "Game start", "Scanner", "Vigilant (40)", "CNO (0)")) {
            inBar(pinned).assertIsDisplayed()
        }
    }

    @Test
    fun `a pinned filter works from deep in the list, and the new list starts at its top`() {
        screen { TrackerScreen(manyBets(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        scrollToKey("x39")
        inBar("Amount").performClick()
        compose.waitForIdle()
        println("DEBUG-TREE " + compose.onRoot().printToString(maxDepth = 12).replace("\n", " ¶ "))
        // Largest amount first: bet 0 ($99) is the first card, on screen with no scrolling back.
        compose.onNodeWithText("Player 0 Over 1.5", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(STICKY_BAR).assertIsDisplayed()
    }

    @Test
    fun `the Stats view keeps its period chips pinned too`() {
        screen { TrackerScreen(manyBets(), { _, _ -> }, {}, initialView = TrackerView.STATS) }
        for (pinned in listOf("Stats", "Bets", "Today", "7 days", "30 days")) compose.onNodeWithText(pinned).assertIsDisplayed()
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performTouchScrollBy(1_500f)
        compose.onNodeWithTag(STICKY_BAR).assertIsDisplayed()
        compose.onNodeWithText("7 days").assertIsDisplayed()
    }

    @Test
    fun `the +EV tab keeps its league chips and start window pinned`() {
        val state = SampleScan.state()
        screen { FeedScreen(state, {}, {}, {}, { _, _ -> }) }
        val shown = state.feedAt(now)
        assertTrue("the sample needs a few bets to scroll", shown.size >= 2)
        scrollToKey(shown.last().key)
        compose.onNodeWithTag(STICKY_BAR).assertIsDisplayed()
        compose.onNodeWithText("Starts within").assertIsDisplayed()
        inBar("NFL").assertIsDisplayed()
    }

    @Test
    fun `the Games tab keeps its league chips pinned`() {
        val state = SampleScan.state()
        screen { GamesScreen(state, {}, {}) }
        val games = state.gamesAt(now)
        assertTrue(games.isNotEmpty())
        scrollToKey(games.last().event.eventId)
        compose.onNodeWithTag(STICKY_BAR).assertIsDisplayed()
        inBar("NFL").assertIsDisplayed()
    }

    @Test
    fun `the CNO tab keeps its scanner chip, filters and start window pinned`() {
        val state = SampleCno.state()
        screen { CnoScreen(state, {}, {}) }
        val picks = state.cnoShown(now)
        assertTrue("the sample needs a few bets to scroll", picks.size >= 2)
        scrollToKey(picks.last().row.key)
        compose.onNodeWithTag(STICKY_BAR).assertIsDisplayed()
        compose.onNodeWithText("CNO only").assertIsDisplayed()
        compose.onNodeWithText("Starts within").assertIsDisplayed()
    }

    @Test
    fun `nothing else on these screens is pinned`() {
        screen { TrackerScreen(manyBets(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onAllNodesWithTag(STICKY_BAR).assertCountEquals(1)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTouchScrollBy(dy: Float) = performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.1f, durationMillis = 100) }
