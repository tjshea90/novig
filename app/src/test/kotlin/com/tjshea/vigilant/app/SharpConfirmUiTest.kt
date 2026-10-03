package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotSelected
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.SharpVetoSection
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpBookChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Betting › Sharp-book confirmation (Tj, 2026-10-02: "a setting for the cno scanner and auto bet feature to require bets to be proven positive EV by a
 * current, devigged sharp book such as Pinnacle"): two switches, both off until turned on, and the choices that only show once one is on. The state is a real one
 * the callback edits, as Settings does.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1800dp-xxhdpi")
class SharpConfirmUiTest {

    @get:Rule val compose = createComposeRule()

    private var ui by mutableStateOf(SampleScan.state())
    private val settings get() = ui.settings

    private fun show(configure: (ScanSettings) -> ScanSettings = { it }, keys: List<String> = emptyList()) {
        // No other key: SampleScan's own keys would make feeds of their own.
        ui = SampleScan.state().copy(
            settings = configure(ScanSettings()), oddsApiKeys = emptyList(), pinnapiKeys = emptyList(), pinnwireKeys = keys, proplineKeys = emptyList(), parlayKeys = emptyList(),
        )
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SharpVetoSection(ui, forAlerts = false) { t -> ui = ui.copy(settings = t(ui.settings)) }
                        SharpVetoSection(ui, forAlerts = true) { t -> ui = ui.copy(settings = t(ui.settings)) }
                    }
                }
            }
        }
    }

    @Test
    fun `the veto is the default for both, its books are named, and the confirmation's criteria show only when one requires it`() {
        // Tj, 2026-10-02 17:01Z: "sharp veto instead of requirement".
        show()
        assertEquals(com.tjshea.vigilant.data.scanner.SharpMode.VETO, settings.sharpAutoBet)
        assertEquals(com.tjshea.vigilant.data.scanner.SharpMode.VETO, settings.sharpAlerts)
        compose.onAllNodesWithText("Veto")[0].assertIsSelected()
        compose.onAllNodesWithText("Veto")[1].assertIsSelected()
        // Each section says which books are sharpest while it's on Veto, and what its mode means in plain words.
        compose.onAllNodesWithTag("sharpVetoNote").assertCountEquals(2)
        compose.onNodeWithTag("sharpAutoBetNote").assertTextContains("Veto (recommended)", substring = true)
        compose.onNodeWithTag("sharpAlertsNote").assertTextContains("Veto (recommended)", substring = true)
        compose.onAllNodesWithText("player props Kalshi, ProphetX, FanDuel, Caesars (MLB props Kalshi, ProphetX, DraftKings, FanDuel)", substring = true)[0].assertExists()
        compose.onAllNodesWithText("moneylines, spreads, totals and period lines Pinnacle, Circa (college Circa, Pinnacle; soccer and tennis Pinnacle)", substring = true)[0].assertExists()
        compose.onAllNodesWithText("Oldest quote allowed").assertCountEquals(0)
    }

    /** RESEARCH.md §72: the sharpest book's own edge is what a bet keeps by the close, so the veto has a bar (1% by default), one for every screen. */
    @Test
    fun `the veto's bar shows under Veto, 1% by default, one setting for the auto-bet, the alerts and the bids`() {
        show()
        assertEquals(0.01, settings.sharpVetoMinEv, 0.0)
        fun chip(label: String, tag: String) = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)))
        for (tag in listOf("sharpVetoMinEv", "sharpVetoMinEvAlerts")) {
            for (label in listOf("Any +EV", "+0.5%", "+1%", "+1.5%", "+2%")) chip(label, tag).assertExists()
            chip("+1%", tag).assertIsSelected()
        }
        compose.onNodeWithTag("sharpVetoBarNote").assertTextContains("must show at least +1% at Novig's price", substring = true)
        compose.onNodeWithTag("sharpVetoBarNote").assertTextContains("the auto-bet, CNO's alerts and the bids", substring = true)
        // A pick in one section is the setting both read.
        chip("+2%", "sharpVetoMinEv").performClick()
        assertEquals(0.02, settings.sharpVetoMinEv, 0.0)
        chip("+2%", "sharpVetoMinEvAlerts").assertIsSelected()
        chip("+1%", "sharpVetoMinEvAlerts").assertIsNotSelected()
        compose.onNodeWithTag("sharpVetoBarNoteAlerts").assertTextContains("must show at least +2%", substring = true)
        chip("Any +EV", "sharpVetoMinEvAlerts").performClick()
        assertEquals(0.0, settings.sharpVetoMinEv, 0.0)
        compose.onNodeWithTag("sharpVetoBarNote").assertTextContains("Any +EV on the sharpest book's own price passes", substring = true)
        // Off (or a confirmation required) has no veto, so no bar for that section.
        compose.onAllNodesWithText("Off")[0].performClick()
        compose.onAllNodesWithTag("sharpVetoMinEv").assertCountEquals(0)
        compose.onAllNodesWithTag("sharpVetoMinEvAlerts").assertCountEquals(1)
    }

    @Test
    fun `each is its own, and requiring a confirmation shows the criteria with their defaults`() {
        show(keys = listOf("pw-FAKE-0000"))
        compose.onAllNodesWithText("Require a confirmation")[0].performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM, settings.sharpAutoBet)
        assertEquals(com.tjshea.vigilant.data.scanner.SharpMode.VETO, settings.sharpAlerts)
        // The defaults, selected: Pinnacle, 3 minutes, any +EV; CNO's page off.
        compose.onNodeWithText("Pinnacle").assertIsSelected()
        compose.onNodeWithText("3 min").assertIsSelected()
        // (The alerts' section, still on Veto, has its bar's "Any +EV" chip too, below this one.)
        compose.onAllNodesWithText("Any +EV")[0].assertIsSelected()
        compose.onNodeWithTag("sharpConfirmViaCno").assertIsOff()
        compose.onNodeWithTag("sharpConfirmNote").assertExists()
        // The alerts' own.
        compose.onAllNodesWithText("Require a confirmation")[1].performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM, settings.sharpAlerts)
        // Both require it now: each shows the shared criteria, and they say they're for both.
        compose.onAllNodesWithText("Oldest quote allowed").assertCountEquals(2)
        compose.onAllNodesWithText("For the auto-bet and CNO's push alerts: Pinnacle's own price", substring = true)[0].assertExists()
        compose.onRoot().captureRoboImage("screenshots/5m_settings_sharp_confirm.png")
        // Off for the auto-bet: nothing the sharp books say stops a bet.
        compose.onAllNodesWithText("Off")[0].performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.SharpMode.OFF, settings.sharpAutoBet)
    }

    @Test
    fun `the choices change what is saved`() {
        show({ it.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM) }, keys = listOf("pw-FAKE-0000"))
        compose.onNodeWithText("Pinnacle or Circa").performClick()
        assertEquals(SharpBookChoice.PINNACLE_CIRCA, settings.sharpConfirmBooks)
        for ((label, seconds) in listOf("1 min" to 60, "2 min" to 120, "5 min" to 300)) {
            compose.onNodeWithText(label).performClick()
            assertEquals(label, seconds, settings.sharpConfirmMaxAgeSeconds)
        }
        for ((label, ev) in listOf("+1%" to 0.01, "+2%" to 0.02, "+3%" to 0.03, "Any +EV" to 0.0)) {
            // The first of each (the alerts' section below, on Veto, has a bar with some of the same labels).
            compose.onAllNodesWithText(label)[0].performClick()
            assertEquals(label, ev, settings.sharpConfirmMinEv, 0.0)
        }
        compose.onNodeWithTag("sharpConfirmViaCno").performClick()
        assertTrue(settings.sharpConfirmViaCno)
        compose.onNodeWithText("On: Pinnacle's column on CNO's game page can confirm a bet too", substring = true).assertExists()
    }

    @Test
    fun `with no Pinnacle feed it says nothing can be confirmed, and with one it names it`() {
        show({ it.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM) })
        compose.onNodeWithTag("sharpConfirmFeeds").assertExists()
        compose.onNodeWithText("No Pinnacle feed is on with a key", substring = true).assertExists()
        compose.onNodeWithText("the auto-bet skips every bet and no CNO alert is sent", substring = true).assertExists()
    }

    @Test
    fun `a saved key makes PinnWire a feed`() {
        show({ it.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.CONFIRM) }, keys = listOf("pw-FAKE-0000"))
        compose.onNodeWithText("Asked in this order, and the first that has the bet answers: PinnWire / pinnapi.", substring = true).assertExists()
    }
}
