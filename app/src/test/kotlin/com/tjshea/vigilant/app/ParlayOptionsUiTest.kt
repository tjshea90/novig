package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.ParlayMakerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.rfq.RfqConfig
import com.tjshea.vigilant.data.rfq.RfqStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** MK17 (Tj, 2026-11-19: "put these into the app as options that I can change or turn on and off"): today only, longshots only, the wallet as the collateral limit. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class ParlayOptionsUiTest {
    @get:Rule val compose = createComposeRule()

    private var saved: RfqConfig? = null

    private fun show(cfg: RfqConfig) = compose.setContent {
        var c by mutableStateOf(cfg)
        VigilantTheme(darkTheme = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ParlayMakerView(RfqStatus(config = c, connected = true, walletCash = 62.40, walletTotal = 140.0, walletParlayCap = 42.0, walletLegCap = 70.0, lossStopNow = 28.0), { n, done -> saved = n; c = n; done(null) })
            }
        }
    }

    @Test fun `the Tonight button turns on today, longshots and the wallet at once and All off turns them off`() {
        show(RfqConfig(mode = RfqConfig.Mode.SHADOW))
        compose.onNodeWithTag("pickingOptions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("optTonight").performClick()
        val s = saved!!
        assertTrue(s.onlyToday && s.longshotsOnly && s.walletCollateral)
        assertEquals(0.25, s.longshotMaxChance, 1e-9); assertEquals(0.30, s.walletShare, 1e-9)
        compose.onNodeWithTag("optWalletNumbers").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wallet now: \$62.40 free, \$140.00 with the collateral already held.", substring = true).assertExists()
        compose.onNodeWithTag("pickingOptions").performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/MK17_parlay_options.png")
        compose.onNodeWithTag("optNormal").performClick()
        assertFalse(saved!!.onlyToday || saved!!.longshotsOnly || saved!!.walletCollateral)
    }

    /** MK18 (Tj: "the controls take a long time to change"): the page shows what was tapped at once, and the next tap builds on it, even while the engine has not reported anything yet. */
    @Test fun `a tap shows at once and two quick taps build on each other while the engine has not echoed`() {
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    // the engine's status never changes in this test: it is the slow side
                    ParlayMakerView(RfqStatus(config = RfqConfig(mode = RfqConfig.Mode.SHADOW, longshotsOnly = true), connected = true), { n, done -> saved = n; done(null) })
                }
            }
        }
        compose.onNodeWithTag("optLongshotChance-minus").performScrollTo().performClick()
        compose.onNodeWithTag("optLongshotChance-minus").performClick()
        assertEquals("the second tap started from the first (0.25 -> 0.20 -> 0.15), not from the engine's old 0.25", 0.15, saved!!.longshotMaxChance, 1e-9)
        compose.onNodeWithTag("optLongshotChance-value").assertIsDisplayed()
        compose.onNodeWithText("15%").assertExists()
        compose.onNodeWithTag("parlayNote").assertIsDisplayed()
    }

    @Test fun `each switch and stepper changes only its own setting`() {
        show(RfqConfig(mode = RfqConfig.Mode.SHADOW, longshotsOnly = true))
        compose.onNodeWithTag("optToday").performScrollTo().performClick()
        assertTrue(saved!!.onlyToday); assertTrue(saved!!.longshotsOnly); assertFalse(saved!!.walletCollateral)
        compose.onNodeWithTag("optLongshotChance-plus").performScrollTo().performClick()
        assertEquals(0.30, saved!!.longshotMaxChance, 1e-9)
        compose.onNodeWithTag("optLongshotChance-minus").performScrollTo().performClick()
        compose.onNodeWithTag("optLongshotChance-minus").performClick()
        assertEquals(0.20, saved!!.longshotMaxChance, 1e-9)
        compose.onNodeWithTag("optWallet").performScrollTo().performClick()
        assertTrue(saved!!.walletCollateral)
        compose.onNodeWithTag("optWalletShare-plus").performScrollTo().performClick()
        assertEquals(0.35, saved!!.walletShare, 1e-9)
        compose.onNodeWithText("The fixed collateral limits", substring = true).performScrollTo().assertIsDisplayed()
    }
}
