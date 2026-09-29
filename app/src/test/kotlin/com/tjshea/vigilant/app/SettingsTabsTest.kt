package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.SettingsTab
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-09-29: "the settings section is getting very long. See how you can organize it. Maybe tabs on the top." Settings is one page per tab,
 * the tab row stays put however far a page is scrolled, and nothing that was on the one long page went missing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class SettingsTabsTest {

    @get:Rule val compose = createComposeRule()

    private fun screen(state: UiState = SampleScan.state(), content: @androidx.compose.runtime.Composable (UiState) -> Unit = { SettingsScreen(it, {}) }) =
        compose.setContent { VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content(state) } } }

    private fun open(tab: SettingsTab) {
        compose.onNodeWithTag("settingsTab-${tab.name}").performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Each section title the one long page had, on the one tab that now holds it (and only there). */
    private val sections = mapOf(
        SettingsTab.SCAN to listOf("Scanner", "Background auto-scan", "Push alerts"),
        SettingsTab.CNO to listOf("CNO scanner", "Mini window"),
        SettingsTab.FAIR to listOf("Fair odds method", "Devig method", "Where fair odds come from", "Sportsbooks for fair odds"),
        SettingsTab.FEED to listOf("+EV feed"),
        SettingsTab.BETTING to listOf("Bankroll & Kelly", "Novig API key"),
        SettingsTab.USAGE to listOf("API usage", "Keys backup"),
        SettingsTab.TOOLS to listOf("Diagnostics", "About"),
    )

    @Test
    fun `every section of the old long page is on exactly one tab`() {
        screen()
        val all = SettingsTab.entries
        for (tab in all) {
            open(tab)
            for ((home, titles) in sections) {
                for (title in titles) {
                    val found = compose.onAllNodesWithText(title, substring = true, ignoreCase = true).fetchSemanticsNodes().size
                    if (home == tab) assertTrue("$title should be on ${tab.label}", found >= 1) else assertEquals("$title should not be on ${tab.label}", 0, found.coerceAtMost(0) + countExact(title, tab, home))
                }
            }
        }
    }

    /** A title another tab's own text happens to contain ("Scanner" in a hint) isn't a title: only its uppercase heading counts. */
    private fun countExact(title: String, on: SettingsTab, home: SettingsTab): Int =
        compose.onAllNodesWithText(title.uppercase(), ignoreCase = false).fetchSemanticsNodes().size

    @Test
    fun `the tab row shows the pages in order, opens on Scan, and the chosen one is selected`() {
        screen()
        assertEquals(SettingsTab.entries.size, SettingsTab.shown(SampleScan.settings).size)
        compose.onNodeWithTag("settingsTab-SCAN").assertIsSelected()
        open(SettingsTab.FAIR)
        compose.onNodeWithTag("settingsTab-FAIR").assertIsSelected()
        compose.onNodeWithText("Fair odds method", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun `the tab row stays at the top however far the page is scrolled`() {
        screen()
        open(SettingsTab.FAIR)
        // The longest page, scrolled to its far end: the tabs are still there to tap.
        compose.onNodeWithText("Sportsbooks for fair odds", substring = true, ignoreCase = true).performScrollTo()
        compose.onNodeWithTag("settingsTabs").assertIsDisplayed()
        compose.onNodeWithTag("settingsTab-FEED").assertIsDisplayed()
        // ...and dragging the page changes which tab is shown by nothing but a tap.
        compose.onNodeWithText("Where fair odds come from", ignoreCase = true).assertExists()
    }

    @Test
    fun `CNO only leaves the pages Vigilant's scanner doesn't need, and calls the CNO page the widget's when CNO is off`() {
        val base = SampleScan.state()
        assertEquals(
            listOf(SettingsTab.SCAN, SettingsTab.CNO, SettingsTab.BETTING, SettingsTab.TOOLS),
            SettingsTab.shown(base.settings.copy(scanner = ScannerMode.CNO)),
        )
        assertEquals(SettingsTab.entries.toList(), SettingsTab.shown(base.settings.copy(scanner = ScannerMode.BOTH)))
        assertEquals("CNO & widget", SettingsTab.CNO.labelFor(base.settings))
        assertEquals("Widget", SettingsTab.CNO.labelFor(base.settings.copy(scanner = ScannerMode.VIGILANT)))
        // Switching to CNO only while on a tab that goes away lands on the first tab instead of an empty page.
        screen(base.copy(settings = base.settings.copy(scanner = ScannerMode.CNO))) { SettingsScreen(it, {}, startTab = SettingsTab.FAIR) }
        compose.onNodeWithTag("settingsTab-SCAN").assertIsSelected()
        compose.onAllNodesWithTag("settingsTab-FAIR").assertCountEquals(0)
    }

    @Test
    fun `the tab Tj picked survives a rotation or the app being recreated`() {
        val tester = StateRestorationTester(compose)
        tester.setContent { VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize()) { SettingsScreen(SampleScan.state(), {}) } } }
        open(SettingsTab.BETTING)
        compose.onNodeWithTag("settingsTab-BETTING").assertIsSelected()
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settingsTab-BETTING").assertIsSelected()
        compose.onNodeWithText("Bankroll & Kelly", ignoreCase = true).assertExists()
    }

    @Test
    fun `a page opens at its top even after the last one was scrolled`() {
        screen()
        open(SettingsTab.FAIR)
        compose.onNodeWithText("Sportsbooks for fair odds", substring = true, ignoreCase = true).performScrollTo()
        open(SettingsTab.FEED)
        compose.onNodeWithText("+EV feed", ignoreCase = true).assertIsDisplayed()
    }
}
