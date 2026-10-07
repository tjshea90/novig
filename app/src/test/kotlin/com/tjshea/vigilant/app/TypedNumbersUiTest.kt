package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-07: "anywhere there are settings for minimum/maximum EV, odds, times, or basically any number inputs that have options, also put a box where I can manually type in a
 * number to set. Also anywhere there is a longest odds setting in the app, make a shortest odds setting as well. Make sure the settings do what they say."
 * Every Settings box: typed into, the setting it names changes to exactly that; a value it doesn't take changes nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h4200dp-xxhdpi")
class TypedNumbersUiTest {

    @get:Rule val compose = createComposeRule()

    private var ui by mutableStateOf(SampleScan.state())
    private val s get() = ui.settings

    private fun show(page: SettingsPage, configure: (ScanSettings) -> ScanSettings = { it }) {
        ui = SampleScan.state().let { it.copy(settings = configure(it.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.BOTH))) }
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(ui, onUpdate = { f -> ui = ui.copy(settings = f(ui.settings)) }, page = page)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(text)
        compose.waitForIdle()
    }

    @Test
    fun `Scanning, Alerts and the CNO list take typed numbers`() {
        show(SettingsPage.SCANNING)
        type("startsWithinField", "36")
        assertEquals(36, s.startsWithinHours)
        type("startsWithinField", "0")
        assertEquals("a value the box doesn't take changes nothing", 36, s.startsWithinHours)
    }

    @Test
    fun `the alert edge is typed as a percent`() {
        show(SettingsPage.ALERTS)
        type("alertMinEvField", "2.75")
        assertEquals(0.0275, s.alertMinEv, 1e-9)
        type("alertMinEvField", "99")
        assertEquals(0.0275, s.alertMinEv, 1e-9)
    }

    @Test
    fun `every CNO list box sets its filter, shortest odds included`() {
        show(SettingsPage.CNO)
        type("cnoMaxOddsField", "180"); assertEquals(180, s.cnoFilters.maxOdds)
        type("cnoMinOddsField", "-180"); assertEquals(-180, s.cnoFilters.minOdds)
        type("cnoMinOddsField", "-50"); assertEquals("-50 isn't odds", -180, s.cnoFilters.minOdds)
        type("cnoMinOddsField", "110"); assertEquals("underdogs only", 110, s.cnoFilters.minOdds)
        type("cnoMinBooksField", "6"); assertEquals(6, s.cnoFilters.minBooks)
        type("cnoMinEvField", "1.5"); assertEquals(0.015, s.cnoFilters.minEv, 1e-9)
        type("cnoRefreshField", "20"); assertEquals(20, s.cnoRefreshSeconds)
        type("cnoRowsField", "75"); assertEquals(75, s.cnoFilters.rows)
    }

    @Test
    fun `a chip and the box agree, the box always showing the saved value`() {
        show(SettingsPage.CNO)
        compose.onNodeWithTag("cnoMinBooksField").performScrollTo()
        compose.onNodeWithTag("cnoMinBooksField").assertTextEquals("4")
        compose.onNodeWithTag("cnoRowsField").performScrollTo()
        compose.onNodeWithTag("cnoRowsField").assertTextEquals(s.cnoFilters.rows.toString())
    }

    @Test
    fun `the +EV feed boxes set what the feed shows, with a shortest odds beside the longest`() {
        show(SettingsPage.FEED)
        type("feedMinEvField", "1.75"); assertEquals(0.0175, s.minEvPercent, 1e-9)
        type("feedMaxOddsField", "250"); assertEquals(250, s.maxOdds)
        type("feedMinOddsField", "-220"); assertEquals(-220, s.minOdds)
        type("linesPerGameField", "6"); assertEquals(6, s.linesPerGame)
        type("propsPerGameField", "20"); assertEquals(20, s.propsPerGame)
        type("maxBooksField", "750"); assertEquals(750, s.maxBooksPerScan)
        type("daysAheadField", "4"); assertEquals(4, s.daysAhead)
    }

    @Test
    fun `the fair odds minimum of books is typed`() {
        show(SettingsPage.FAIR)
        type("fairMinBooksField", "3")
        assertEquals(3, s.minBooks)
    }

    @Test
    fun `the Kelly fraction is typed as a decimal`() {
        show(SettingsPage.BETTING)
        type("kellyField", "0.33")
        assertEquals(0.33, s.kellyMultiplier, 1e-9)
        type("kellyField", "1.5")
        assertEquals(0.33, s.kellyMultiplier, 1e-9)
    }

    @Test
    fun `the widget rescan is typed in minutes`() {
        show(SettingsPage.WIDGET)
        type("widgetRescanField", "7")
        assertEquals(7, s.widgetRescanMinutes)
    }

    @Test
    fun `a shortest odds longer than the longest says nothing can pass`() {
        show(SettingsPage.FEED) { it.copy(maxOdds = 150) }
        type("feedMinOddsField", "200")
        compose.onNodeWithTag("feedOddsRange").performScrollTo().assertTextContains("nothing can pass both", substring = true)
    }
}
