package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ParlayMakerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.rfq.RfqConfig
import com.tjshea.vigilant.data.rfq.RfqStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** MKV (Tj, 2026-11-18: "I want it in vigilant"): the Parlays view of the Engine tab, monitor and control of the GitHub engine's parlay maker. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class ParlayMakerUiTest {
    @get:Rule val compose = createComposeRule()

    private val shadow = RfqStatus(
        atMs = 1L, config = RfqConfig(mode = RfqConfig.Mode.SHADOW), connected = true, makerId = "mk", seen = 16_500, priceable = 83, tradesSeen = 502, pricedTrades = 164,
        medianGapPoints = 1.06, wouldWin = 60, wouldEarnPoints = 1.46, declines = listOf("a leg has no fair line here x8000"),
    )

    private val sent = ArrayList<RfqConfig>()
    private fun show(st: RfqStatus?, why: String? = null) {
        sent.clear()
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    ParlayMakerView(st, onSet = { c, done -> sent += c; done(why) })
                }
            }
        }
    }

    @Test fun shadowIsShownWithItsNumbersAndSaysItRisksNothing() {
        show(shadow)
        compose.onNodeWithTag("parlayHeadline").assertIsDisplayed()
        compose.onNodeWithText("Shadow: listening").assertIsDisplayed()
        compose.onNodeWithText("RFQs seen").assertIsDisplayed()
        compose.onNodeWithTag("parlayEvidence").assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }

    @Test fun noStatusYetOffersNoControls() {
        show(null)
        compose.onNodeWithText("Waiting for the engine").assertIsDisplayed()
        compose.onNodeWithTag("parlayMode-OFF").performScrollTo().performClick()
        assertTrue("the chips do nothing without an applied config to edit", sent.isEmpty())
    }

    @Test fun offAndShadowApplyAtOnceButLiveAsksFirst() {
        show(shadow)
        compose.onNodeWithTag("parlayMode-OFF").performScrollTo().performClick()
        assertEquals(RfqConfig.Mode.OFF, sent.single().mode)
        compose.onNodeWithTag("parlayMode-LIVE").performScrollTo().performClick()
        assertEquals("LIVE waits for the second tap", 1, sent.size)
        compose.onNodeWithText("Start live quoting?").assertIsDisplayed()
        compose.onNodeWithTag("parlayLiveConfirm").performClick()
        assertEquals(RfqConfig.Mode.LIVE, sent.last().mode); assertEquals(2, sent.size)
    }

    @Test fun theLimitsStepAndKeepTheRestOfTheConfig() {
        show(shadow)
        compose.onNodeWithTag("parlayCollateral-plus").performScrollTo().performClick()
        assertEquals(55.0, sent.last().maxCollateral, 0.0); assertEquals(RfqConfig.Mode.SHADOW, sent.last().mode); assertEquals(0.06, sent.last().minReturn, 0.0)
        compose.onNodeWithTag("parlayReturn-minus").performScrollTo().performClick()
        assertEquals(0.05, sent.last().minReturn, 1e-9)
        compose.onNodeWithTag("parlayLegs-plus").performScrollTo().performClick()
        assertEquals(6, sent.last().maxLegs)
        compose.onNodeWithTag("parlayNote").assertIsDisplayed()
    }

    @Test fun aFailedSaveIsSaidInWords() {
        show(shadow, why = "no GitHub token saved (Settings › Cloud scan)")
        compose.onNodeWithTag("parlayOpen-plus").performScrollTo().performClick()
        compose.onNodeWithText("Not saved: no GitHub token saved (Settings › Cloud scan)").assertIsDisplayed()
    }

    @Test fun aLockoutIsAWarningOnTheView() {
        show(shadow.copy(config = RfqConfig(mode = RfqConfig.Mode.LIVE), halted = "Novig locked the maker out of quoting for 5 minutes"))
        compose.onNodeWithTag("parlayWarning").assertIsDisplayed()
        compose.onNodeWithText("Live: stopped").assertIsDisplayed()
    }

    @Test fun theProfitProfileLocksTheControlsAndSaysSo() {
        show(shadow.copy(config = RfqConfig(mode = RfqConfig.Mode.LIVE), configLock = "Claude's profit profile runs the parlay maker (chosen from graded results). Settings changed on this phone do not apply to it."))
        compose.onNodeWithTag("parlayLocked").assertIsDisplayed()
        compose.onNodeWithTag("parlayModes").assertDoesNotExist()
        compose.onNodeWithTag("parlayMaxLegFair-plus").assertDoesNotExist()
        compose.onNodeWithTag("parlayHeadline").assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }

    @Test fun withoutAProfileTheControlsStayAndNothingIsSaidAboutALock() {
        show(shadow)
        compose.onNodeWithTag("parlayLocked").assertDoesNotExist()
        compose.onNodeWithTag("parlayModes").assertIsDisplayed()
    }
}
