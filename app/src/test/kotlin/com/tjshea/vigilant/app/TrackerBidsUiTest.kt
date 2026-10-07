package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    private fun shown(vararg texts: String) = texts.filter { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `the Bets tab lists bets, bids or both, each choice with its count, and a BID tag marks the bids`() {
        screen { TrackerScreen(withBids(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithText("Bets & bids").assertExists()
        // Open: b3, b4 and b5; b4 is a bid.
        assertEquals(listOf("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5"), shown("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5"))
        compose.onAllNodesWithTag("bidTag").assertCountEquals(1)
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
        assertTrue(compose.onNodeWithTag("madeCaption").fetchSemanticsNode().toString().contains("Bids only: 2 bids a taker filled"))

        choose("Bets only (4)")
        assertEquals(listOf("Dallas Cowboys", "Under 7.5"), shown("Dallas Cowboys", "Jaxon Smith-Njigba Over 5.5", "Under 7.5"))
        compose.onAllNodesWithTag("bidTag").assertCountEquals(0)
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
        compose.onNodeWithText("+$17.52").assertExists()
        compose.onNodeWithText("2-1").assertExists()
        compose.onAllNodesWithTag("madeCaption").assertCountEquals(0)

        choose("Bids only (2)")
        compose.onNodeWithText("+$34.52").assertExists()
        compose.onNodeWithText("1-0").assertExists()
        compose.onNodeWithTag("madeCaption").assertIsDisplayed()

        choose("Bets only (4)")
        compose.onNodeWithText("-$17.00").assertExists()
        compose.onNodeWithText("1-1").assertExists()
        compose.onAllNodesWithText("+$34.52").assertCountEquals(0)

        choose("Bets & bids (6)")
        compose.onNodeWithText("+$17.52").assertExists()
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
        compose.onNodeWithText("Bids nobody filled", substring = true).assertExists()
    }
}
