package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    /** Each section heading the one long page had, on the one tab that now holds it (headings are drawn in capitals). */
    private val sections = mapOf(
        SettingsTab.SCAN to listOf("SCANNER", "BACKGROUND AUTO-SCAN"),
        SettingsTab.CNO to listOf("CNO SCANNER", "MINI WINDOW"),
        SettingsTab.FAIR to listOf("FAIR ODDS METHOD", "DEVIG METHOD", "WHERE FAIR ODDS COME FROM", "SPORTSBOOKS FOR FAIR ODDS"),
        SettingsTab.FEED to listOf("+EV FEED"),
        SettingsTab.BETTING to listOf("BANKROLL & KELLY", "NOVIG API KEY"),
        SettingsTab.USAGE to listOf("API USAGE", "KEYS BACKUP"),
        SettingsTab.TOOLS to listOf("DIAGNOSTICS", "ABOUT"),
    )

    /** Every text on screen, as drawn (a heading's count, like "(7/10)", is part of it). */
    private fun texts(): List<String> =
        compose.onAllNodes(SemanticsMatcher("has text") { it.config.contains(SemanticsProperties.Text) })
            .fetchSemanticsNodes().flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }

    @Test
    fun `every section of the old long page is on exactly one tab`() {
        screen()
        for (tab in SettingsTab.entries) {
            open(tab)
            for ((home, titles) in sections) {
                for (title in titles) {
                    val found = texts().count { it == title || it.startsWith("$title (") }
                    if (home == tab) assertTrue("$title should be on the ${tab.label} tab", found >= 1) else assertEquals("$title should not be on the ${tab.label} tab", 0, found)
                }
            }
        }
    }

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
        compose.onNodeWithText("+EV FEED", ignoreCase = false).assertIsDisplayed()
    }
}
