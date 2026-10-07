package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.STICKY_BAR
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-07: "make the app bet logging differentiate from bets and bids … so I can see stats and ev filtered my bids as well as bets." The Tracker's chip lists
 * bets, bids or both, and every number on the Stats tab follows it and says which it covers.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class TrackerBidsUiTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun screen(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    /**
     * The sample's six bets with two of them Vigilant's bids that a taker filled: b1 (settled, a win: $25 at 0.42) and b4 (open). The other four are taker bets:
     * b2 a $18 loss, b6 a $1 win, b3 and b5 open.
     */
    private fun withBids() = SampleScan.state().let { s ->
        s.copy(bets = s.bets.map { b -> if (b.id == "b1" || b.id == "b4") b.copy(maker = true, orderId = "bid-${b.id}") else b })
    }

    private fun choose(item: String) {
        compose.onNodeWithTag("madeChip").performClick()
        compose.onNodeWithText(item).performClick()
    }

    private fun inBar(text: String) = compose.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(STICKY_BAR)))

    private fun has(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun shown(vararg texts: String) = texts.filter { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `the Bets tab lists bets, bids or both, each choice with its count, and a BID tag marks the bids`() {
        screen { TrackerScreen(withBids(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithText("Bets & bids").assertExists()
        // Open: b3, b4 and b5; b4 is a bid.
        assertEquals(listOf("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5"), shown("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5"))
        compose.onAllNodesWithTag("bidTag", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("your bid, filled (make order)", substring = true).assertExists()

        compose.onNodeWithTag("madeChip").performClick()
        compose.onNodeWithText("Bets & bids (6)").assertExists()
        compose.onNodeWithText("Bets only (4)").assertExists()
        compose.onNodeWithText("Bids only (2)").assertExists()
        compose.onNodeWithText("Bids only (2)").performClick()
        assertEquals(listOf("Jaxon Smith-Njigba Over 5.5"), shown("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5"))
        compose.onNodeWithText("Bids only").assertExists()
        compose.onNodeWithText("Open (1)").assertExists() // the list counts follow the choice
        compose.onNodeWithTag("madeCaption").assertIsDisplayed()
        compose.onNodeWithTag("madeCaption").assertTextContains("Bids only: 2 bids a taker filled", substring = true)

        choose("Bets only (4)")
        assertEquals(listOf("Dallas Cowboys", "Under 7.5"), shown("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5"))
        compose.onAllNodesWithTag("bidTag", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Open (2)").assertExists()

        choose("Bets & bids (6)")
        assertEquals(3, shown("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5").size)
        compose.onAllNodesWithTag("madeCaption").assertCountEquals(0)
    }

    @Test
    fun `a bid's card calls its EV the one posted, a bet's the one at the bet`() {
        screen { TrackerScreen(withBids(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        choose("Bids only (2)")
        compose.onNodeWithText("EV posted").assertExists()
        compose.onAllNodesWithText("EV at bet").assertCountEquals(0)
        choose("Bets only (4)")
        compose.onAllNodesWithText("EV posted").assertCountEquals(0)
        compose.onAllNodesWithText("EV at bet").assertCountEquals(2)
    }

    @Test
    fun `the Stats tab's profit, record and caption follow the chip`() {
        screen { TrackerScreen(withBids(), { _, _ -> }, {}, initialView = TrackerView.STATS) }
        // Everything: +34.52 (b1) − 18.00 (b2) + 1.00 (b6).
        assertTrue("+$17.52", has("+$17.52"))
        assertTrue("2-1", has("2-1"))
        compose.onAllNodesWithTag("madeCaption").assertCountEquals(0)

        choose("Bids only (2)")
        assertTrue("+$34.52", has("+$34.52"))
        assertTrue("1-0", has("1-0"))
        compose.onNodeWithTag("madeCaption").assertIsDisplayed()

        choose("Bets only (4)")
        assertTrue("−$17.00", has("−$17.00")) // Format.money writes a minus sign, not a hyphen
        assertTrue("1-1", has("1-1"))
        compose.onAllNodesWithText("+$34.52").assertCountEquals(0)

        choose("Bets & bids (6)")
        assertTrue("+$17.52", has("+$17.52"))
    }

    @Test
    fun `the chip is pinned on both tabs and the choice stays when the tab changes`() {
        screen { TrackerScreen(withBids(), { _, _ -> }, {}, initialView = TrackerView.STATS) }
        inBar("Bets & bids").assertIsDisplayed()
        choose("Bids only (2)")
        compose.onNodeWithText("Bets", useUnmergedTree = false).performClick() // the Stats | Bets switch
        inBar("Bids only").assertIsDisplayed()
        assertEquals(listOf("Jaxon Smith-Njigba Over 5.5"), shown("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5"))
    }

    @Test
    fun `with no bid filled, the Bids choice says so instead of an empty page`() {
        screen { TrackerScreen(SampleScan.state(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        choose("Bids only (0)")
        compose.onNodeWithText("No open bids").assertExists()
        compose.onNodeWithText("A bid is a make order", substring = true).assertExists()
    }

    @Test
    fun `the closing-line card follows the chip too`() {
        // b1 (a bid) was bet at 0.42 and closed at 0.50: it beat the close. b2 (a bet) was bet at 0.51 and closed at 0.50: it didn't.
        val state = withBids().let { s ->
            s.copy(bets = s.bets.map { b ->
                when (b.id) {
                    "b1" -> b.copy(closingFair = 0.50, closingSeenAtMs = b.startsTs - 60_000L)
                    "b2" -> b.copy(closingFair = 0.50, closingSeenAtMs = b.startsTs - 60_000L)
                    else -> b
                }
            })
        }
        screen { TrackerScreen(state, { _, _ -> }, {}, initialView = TrackerView.STATS) }
        fun clv() = compose.onNodeWithTag("clvValues").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
        assertTrue(clv(), clv().startsWith("Beat the close 50% (1 of 2)"))
        choose("Bids only (2)")
        assertTrue(clv(), clv().startsWith("Beat the close 100% (1 of 1)"))
        choose("Bets only (4)")
        assertTrue(clv(), clv().startsWith("Beat the close 0% (0 of 1)"))
    }

    @Test
    fun `screenshot - the Tracker's Bets tab and Stats tab with the Bids chip`() {
        screen { TrackerScreen(withBids(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onRoot().captureRoboImage("screenshots/4q_tracker_bets_and_bids.png")
        choose("Bids only (2)")
        compose.onRoot().captureRoboImage("screenshots/4q_tracker_bids_only.png")
        compose.onNodeWithText("Stats").performClick()
        compose.onRoot().captureRoboImage("screenshots/4q_tracker_bids_only_stats.png")
    }
}
