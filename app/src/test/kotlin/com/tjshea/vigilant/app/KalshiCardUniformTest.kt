package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ApiBetSheetContent
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.OpportunityCard
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.trading.BetPlan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tj, 2026-10-23: a Kalshi card and its slip are the Novig ones, only the company differs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class KalshiCardUniformTest {
    @get:Rule val compose = createComposeRule()
    private val now = SampleScan.NOW

    private fun screen(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    @Test
    fun `a Kalshi card has the X, the Bet button and Open in Kalshi like a Novig card`() {
        val s = SampleScan.state()
        val o = s.result!!.opportunities.first { it.quote != null && it.fairProbability != null }
        var hidden = 0
        var bet = 0
        screen {
            OpportunityCard(o, s.settings, now, venue = "Kalshi", onHide = { hidden += 1 }, onBet = { bet += 1 }, onClick = {})
        }
        compose.onNodeWithContentDescription("Remove ${o.selection} from the list").assertIsDisplayed().performClick()
        assertEquals(1, hidden)
        compose.onNodeWithTag("kalshiBetButton").assertIsDisplayed().performClick()
        assertEquals("the Bet button opens the mini slip, not the details sheet", 1, bet)
        compose.onNodeWithTag("kalshiOpenButton").assertIsDisplayed()
        compose.onAllNodesWithText("Bet on Kalshi").assertCountEquals(0)
    }

    @Test
    fun `the slip for Kalshi is Novig's slip in Kalshi's words, without the Vigilant wallet block`() {
        val plan = BetPlan(0.42, 100, 0.43, 0.42, 1.0, 0.03, 0.42, null, feeDollars = 0.01)
        val sheet = BetSheetUi("Ashton Jeanty Under 13.5", "Rushing Attempts · Rams @ Raiders", null, 0.5, resolving = false, plan = plan, balance = 6.29, venue = "Kalshi")
        screen { ApiBetSheetContent(sheet, onStake = {}, onConfirm = {}, onRefresh = {}, onRepeat = {}, onDismiss = {}) }
        compose.onNodeWithTag("confirmBet").assertIsDisplayed()
        compose.onNodeWithText("Kalshi cash").assertIsDisplayed()
        compose.onNodeWithTag("betFeeNote").assertIsDisplayed()
        compose.onAllNodesWithText("Add money to the wallet", substring = true).assertCountEquals(0)
    }
}
