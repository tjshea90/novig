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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.SharpConfirmSection
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
                        SharpConfirmSection(ui) { t -> ui = ui.copy(settings = t(ui.settings)) }
                    }
                }
            }
        }
    }

    @Test
    fun `both switches are off by default and nothing else shows until one is on`() {
        show()
        assertFalse(settings.sharpConfirmAutoBet || settings.sharpConfirmAlerts)
        compose.onNodeWithTag("sharpConfirmAutoBet").assertIsOff()
        compose.onNodeWithTag("sharpConfirmAlerts").assertIsOff()
        compose.onNodeWithText("Sharp-book confirmation", ignoreCase = true).assertExists()
        compose.onNodeWithText("On top of every other criterion: a sharp book (Pinnacle) must show the bet is +EV on its own price.", substring = true).assertExists()
        compose.onAllNodesWithText("Sharp books").assertCountEquals(0)
        compose.onAllNodesWithText("Newest quote allowed").assertCountEquals(0)
    }

    @Test
    fun `each switch is its own, and turning one on shows the criteria with their defaults`() {
        show(keys = listOf("pw-FAKE-0000"))
        compose.onNodeWithTag("sharpConfirmAutoBet").performClick()
        assertTrue(settings.sharpConfirmAutoBet)
        assertFalse(settings.sharpConfirmAlerts)
        compose.onNodeWithTag("sharpConfirmAutoBet").assertIsOn()
        compose.onNodeWithTag("sharpConfirmAlerts").assertIsOff()
        // The defaults, selected: Pinnacle, 3 minutes, any +EV; CNO's page off.
        compose.onNodeWithText("Pinnacle").assertIsSelected()
        compose.onNodeWithText("3 min").assertIsSelected()
        compose.onNodeWithText("Any +EV").assertIsSelected()
        compose.onNodeWithTag("sharpConfirmViaCno").assertIsOff()
        compose.onNodeWithTag("sharpConfirmNote").assertExists()
        // The alerts' switch.
        compose.onNodeWithTag("sharpConfirmAlerts").performClick()
        assertTrue(settings.sharpConfirmAlerts)
        compose.onNodeWithText("For the auto-bet and CNO's push alerts: Pinnacle's own price", substring = true).assertExists()
        compose.onRoot().captureRoboImage("screenshots/5m_settings_sharp_confirm.png")
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
            compose.onNodeWithText(label).performClick()
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
