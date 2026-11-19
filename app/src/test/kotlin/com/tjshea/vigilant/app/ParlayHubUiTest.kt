package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.ParlayHubSection
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.rfq.BookLeg
import com.tjshea.vigilant.data.rfq.BookParlay
import com.tjshea.vigilant.data.rfq.ParlayBook
import com.tjshea.vigilant.data.rfq.RfqConfig
import com.tjshea.vigilant.data.rfq.RfqStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MK15 (Tj, 2026-11-19: "a parlay hub ... resting parlays and their legs ... if any legs are already won ... open and closed ... expected return if the remaining open parlays are lost by the buyer ...
 * Make the gui intuitive and easy to read"). Rendered from a sample book, no app objects; `-Pscreenshots` writes app/screenshots/MK15_parlay_hub*.png: look at them after changing the screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class ParlayHubUiTest {
    @get:Rule val compose = createComposeRule()

    private val h = 3_600_000L
    private val now = 1_795_118_400_000L

    private fun leg(id: String, label: String, startsInH: Double, p: Double, result: String? = null, league: String = "NBA", desc: String = "Houston Rockets @ Indiana Pacers") =
        BookLeg(outcomeId = id, label = label, desc = desc, league = league, type = "MONEY", startsMs = now + (startsInH * h).toLong(), p = p, result = result)

    private fun parlay(id: String, legs: List<BookLeg>, wager: Double, price: Double, fair: Double, settled: Boolean = false, result: String? = null) =
        BookParlay(id, filledMs = now - 5 * h, price = price, wager = wager, collateral = wager * (1 - price) / price, fair = fair, settled = settled, result = result, settledMs = if (settled) now - 2 * h else null, nLegs = legs.size, legs = legs)

    private val book = ParlayBook(
        atMs = now - 40_000L, mode = "SHADOW",
        parlays = listOf(
            parlay("A", listOf(leg("a1", "Moneyline Houston Texans", -1.0, 0.84, "WIN", "NFL", "Indianapolis Colts @ Houston Texans"), leg("a2", "Total Over 234.5", 2.0, 0.48, desc = "Portland Trail Blazers @ Milwaukee Bucks"), leg("a3", "Moneyline San Antonio Spurs", 5.0, 0.83, desc = "Los Angeles Clippers @ San Antonio Spurs")), 9.42, 0.393, 0.363),
            parlay("B", listOf(leg("b1", "Spread Phoenix Suns +5.5", -3.0, 0.5, "LOSS", desc = "Phoenix Suns @ Sacramento Kings"), leg("b2", "Moneyline Miami Heat", 1.0, 0.7, desc = "Miami Heat @ Orlando Magic")), 4.76, 0.509, 0.502),
            parlay("C", listOf(leg("c1", "Moneyline Florida", 22.0, 0.65, league = "NCAAB", desc = "West Virginia @ Florida"), leg("c2", "Moneyline High Point", 24.0, 0.8, league = "NCAAB", desc = "Montana State @ High Point")), 1.92, 0.637, 0.616),
            parlay("D", listOf(leg("d1", "Spread Eastern Michigan -3.5", -9.0, 0.5, "LOSS", "NCAAB", "Detroit Mercy @ Eastern Michigan"), leg("d2", "Total Under 245.5", -9.0, 0.5, "WIN")), 6.70, 0.554, 0.524, settled = true, result = "win"),
            parlay("E", listOf(leg("e1", "Moneyline Hurricanes", -30.0, 0.55, "WIN", "NHL"), leg("e2", "Total Over 5.5", -30.0, 0.5, "LOSS", "NHL")), 2.22, 0.59, 0.57, settled = true, result = "loss"),
        ),
    )

    private val rfq = RfqStatus(config = RfqConfig(mode = RfqConfig.Mode.SHADOW, maxOpenCollateral = 80.0), connected = true)

    private fun show(b: ParlayBook?) = compose.setContent {
        VigilantTheme(darkTheme = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { ParlayHubSection(rfq, b, now, { _, _ -> }) }
        }
    }

    @Test fun `the hub opens on the money and the open parlays with their legs one tap away`() {
        show(book)
        compose.onNodeWithTag("parlayHub").assertIsDisplayed()
        compose.onNodeWithTag("hubPnl").assertIsDisplayed()
        // realized: the Eastern Michigan parlay was won by its buyer (-$5.39 of collateral... ) and the other kept its $2.22 stake
        compose.onNodeWithTag("hubHeadline").assertExists()
        compose.onNodeWithText("Money tied up now").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("If every open parlay loses").assertExists()
        compose.onNodeWithText("Worst case").assertExists()
        compose.onNodeWithText("Expected on the open ones").assertExists()
        compose.onNodeWithText("All open parlays final by").assertExists()
        compose.onNodeWithTag("hubOpen-A").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("The buyer is ahead: 1 of 3 legs won").assertExists()
        compose.onNodeWithText("The buyer already lost a leg", substring = true).assertExists()
        compose.onNodeWithText("Moneyline Houston Texans").assertDoesNotExist()      // the legs are folded until the card is tapped
        compose.onNodeWithTag("hubOpen-A").performClick()
        compose.onNodeWithText("Moneyline Houston Texans").assertExists()
        compose.onNodeWithText("Won for the buyer").assertExists()
        compose.onNodeWithText("Total Over 234.5").assertExists()
        compose.onNodeWithText("Portland Trail Blazers @ Milwaukee Bucks · NBA").assertExists()
        compose.onRoot().captureRoboImage("screenshots/MK15_parlay_hub.png")
    }

    @Test fun `the closed list says who won each parlay and the legs behind it`() {
        show(book)
        compose.onNodeWithTag("hubTab-closed").performScrollTo().performClick()
        compose.onNodeWithTag("hubClosed-D").assertIsDisplayed()
        compose.onNodeWithText("The buyer won: we paid", substring = true).assertExists()
        compose.onNodeWithText("The buyer lost: we kept", substring = true).assertExists()
        compose.onNodeWithTag("hubClosed-D").performClick()
        compose.onNodeWithText("Spread Eastern Michigan -3.5").assertExists()
        compose.onRoot().captureRoboImage("screenshots/MK15_parlay_hub_closed.png")
    }

    @Test fun `before the engine has published anything the hub says so and the maker page is a tap away`() {
        show(null)
        compose.onNodeWithTag("hubNote").assertIsDisplayed()
        compose.onNodeWithText("Waiting for the engine", substring = true).assertExists()
        compose.onNodeWithTag("parlayPage-maker").performClick()
        compose.onNodeWithTag("parlayMaker").assertIsDisplayed()
    }
}
