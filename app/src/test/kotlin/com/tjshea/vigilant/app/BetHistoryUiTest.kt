package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.tracker.AtBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-11: the Stats tab shows the bet facts with a smart filter and splits by type of bet, sport, stars, sharp book and more; the bet's sheet shows it as placed and how it closed; a button shares
 * every bet and bid with Claude.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class BetHistoryUiTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun screen(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    private fun withRecords() = SampleScan.state().let { s ->
        s.copy(
            bets = s.bets.mapIndexed { i, b ->
                val a = AtBet(
                    atMs = b.createdAtMs, how = AtBet.HOW_AUTO, scanner = "CNO", league = "NFL", sport = "FOOTBALL", kind = if (i % 2 == 0) "PROP" else "MONEYLINE", minutesToStart = 120,
                    american = 110, available = 80.0, ev = 0.04, twoSided = 5, agreeing = 5, sharpVerdict = "PASSED", sharpBook = "Pinnacle",
                )
                (if (i < 2) b.copy(maker = true, orderId = "bid-${b.id}") else b).copy(atBet = a)
            },
        )
    }

    @Test
    fun `Stats has a smart filter, a split by any dimension and a share card`() {
        var shared: com.tjshea.vigilant.app.BetShare.Range? = null
        screen { TrackerScreen(withRecords(), { _, _ -> }, {}, initialView = TrackerView.STATS, onShareBets = { shared = it }, archiveNote = "3 day files kept") }
        compose.onNodeWithTag("smartFilter").assertExists()
        // The share card is gone (Tj, 2026-10-16): the phone saves every bet to GitHub by itself.
        compose.onAllNodesWithTag("shareBetsCard").assertCountEquals(0)
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag("dimBreakdown"))
        compose.onNodeWithTag("dimBreakdown").assertExists()
        compose.onNodeWithText("Type of bet").assertExists()
        // Splitting by the star rating shows a row for the bets' rating.
        compose.onNodeWithText("Star rating").performClick()
        assertTrue(compose.onAllNodesWithTag("dimBreakdown").fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag("shareBetsCard"))
        compose.onNodeWithTag("shareBetsButton").performClick()
        assertEquals(com.tjshea.vigilant.app.BetShare.Range.ALL, shared)
    }

    @Test
    fun `the smart filter narrows every number to the chosen group and clears`() {
        screen { TrackerScreen(withRecords(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithTag("smartFilter").performClick()
        // The choices are worked out off the main thread: wait for them.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Bids only", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bids only", substring = true).performClick()
        compose.onNodeWithText("2 shown", substring = true).assertExists()
        compose.onNodeWithText("Clear").performClick()
        assertTrue(compose.onAllNodesWithTag("smartFilter").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a bet's sheet shows it as placed and how it closed`() {
        screen { TrackerScreen(withRecords(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithText("Dallas Cowboys").performClick()
        compose.onNodeWithTag("betRecord").performScrollTo().assertExists()
        compose.onNodeWithText("Odds when placed").assertExists()
        compose.onNodeWithText("Closing odds").assertExists()
        compose.onNodeWithText("A sharp book agreed").assertExists()
    }
}
