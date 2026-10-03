package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.SettingsIndex
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.SettingsSummary
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-02 ~17:55Z: "the settings menu in this app is getting very large and confusing. organize the settings menu intuitively. make it so everything
 * is clear and easy to find." Settings is a home list (search, then each page with what it's set to now) opening one page at a time with a back arrow;
 * nothing that was on the old tabs went missing, and every setting search knows about is on the page it names.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class SettingsPagesTest {

    @get:Rule val compose = createComposeRule()

    private fun screen(state: UiState = SampleScan.state(), content: @androidx.compose.runtime.Composable (UiState) -> Unit = { SettingsScreen(it, {}) }) =
        compose.setContent { VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content(state) } } }

    private fun open(page: SettingsPage) {
        compose.onNodeWithTag("settingsRow-${page.name}").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.onNodeWithTag("settingsBack").performClick()
        compose.waitForIdle()
    }

    /** Each section heading, on the one page that holds it (headings are drawn in capitals). */
    private val sections = mapOf(
        SettingsPage.SCANNING to listOf("SCANNER", "BACKGROUND SCAN"),
        SettingsPage.ALERTS to listOf("WHEN TO ALERT", "SHARP-BOOK VETO FOR ALERTS"),
        SettingsPage.CNO to listOf("WHAT THE LIST SHOWS", "CHECKING EACH BET", "REFRESH"),
        SettingsPage.WIDGET to listOf("MINI WINDOW"),
        SettingsPage.FEED to listOf("WHAT THE FEED SHOWS", "SCAN SIZE (ADVANCED)"),
        SettingsPage.FAIR to listOf("FAIR ODDS METHOD", "DEVIG METHOD (ADVANCED)", "WHERE FAIR ODDS COME FROM", "SPORTSBOOKS FOR FAIR ODDS"),
        SettingsPage.BETTING to listOf("NOVIG API KEY", "BET AMOUNTS"),
        SettingsPage.USAGE to listOf("API USAGE", "KEYS BACKUP"),
        SettingsPage.HELP to listOf("DIAGNOSTICS", "ABOUT"),
    )

    /** Every text on screen, as drawn (a heading's count, like "(7/10)", is part of it). */
    private fun texts(): List<String> =
        compose.onAllNodes(SemanticsMatcher("has text") { it.config.contains(SemanticsProperties.Text) })
            .fetchSemanticsNodes().flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }

    @Test
    fun `every section is on exactly one page`() {
        screen()
        for (page in SettingsPage.entries) {
            open(page)
            for ((home, titles) in sections) {
                for (title in titles) {
                    val found = texts().count { it == title || it.startsWith("$title (") }
                    if (home == page) assertTrue("$title should be on ${page.title}", found >= 1) else assertEquals("$title should not be on ${page.title}", 0, found)
                }
            }
            back()
        }
    }

    @Test
    fun `the home list shows every page in order with what it's set to, opens one, and the back arrow returns`() {
        screen()
        compose.onNodeWithTag("settingsPage-HOME").assertExists()
        compose.onNodeWithTag("settingsSearch").assertIsDisplayed()
        val s = SampleScan.state()
        for (p in SettingsPage.shown(s.settings)) {
            compose.onNodeWithTag("settingsRow-${p.name}").performScrollTo().assertTextContains(p.title, substring = true)
            compose.onNodeWithText(SettingsSummary.of(p, s), substring = true).assertExists()
        }
        // The order: as the enum lists them.
        val shown = texts().filter { t -> SettingsPage.entries.any { it.title == t } }
        assertEquals(SettingsPage.shown(s.settings).map { it.title }, shown)
        open(SettingsPage.FAIR)
        compose.onNodeWithText("Fair odds & sources").assertIsDisplayed() // the top bar
        compose.onNodeWithText("Fair odds method", ignoreCase = true).assertIsDisplayed()
        back()
        compose.onNodeWithTag("settingsPage-HOME").assertExists()
    }

    @Test
    fun `each scanner hides the pages it doesn't use, and a page that goes away falls back to the list`() {
        val base = SampleScan.state()
        assertEquals(
            listOf(SettingsPage.SCANNING, SettingsPage.ALERTS, SettingsPage.CNO, SettingsPage.WIDGET, SettingsPage.BETTING, SettingsPage.HELP),
            SettingsPage.shown(base.settings.copy(scanner = ScannerMode.CNO)),
        )
        assertEquals(SettingsPage.entries.filter { it != SettingsPage.CNO }, SettingsPage.shown(base.settings.copy(scanner = ScannerMode.VIGILANT)))
        assertEquals(SettingsPage.entries.toList(), SettingsPage.shown(base.settings.copy(scanner = ScannerMode.BOTH)))
        screen(base.copy(settings = base.settings.copy(scanner = ScannerMode.CNO))) { SettingsScreen(it, {}, page = SettingsPage.FAIR) }
        compose.onNodeWithTag("settingsPage-HOME").assertExists()
        compose.onAllNodesWithTag("settingsRow-FAIR").assertCountEquals(0)
    }

    @Test
    fun `the page Tj picked survives a rotation or the app being recreated`() {
        val tester = StateRestorationTester(compose)
        tester.setContent { VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize()) { SettingsScreen(SampleScan.state(), {}) } } }
        open(SettingsPage.BETTING)
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settingsPage-BETTING").assertExists()
        compose.onNodeWithText("Bet amounts", ignoreCase = true).assertExists()
    }

    @Test
    fun `a page opens at its top even after the last one was scrolled`() {
        screen()
        open(SettingsPage.FAIR)
        compose.onNodeWithText("Sportsbooks for fair odds", substring = true, ignoreCase = true).performScrollTo()
        back()
        open(SettingsPage.FEED)
        compose.onNodeWithText("WHAT THE FEED SHOWS").assertIsDisplayed()
    }

    @Test
    fun `when the app keeps the page, the arrow and a row both go through it`() {
        var page by mutableStateOf<SettingsPage?>(null)
        screen { SettingsScreen(it, {}, page = page, onPage = { p -> page = p }) }
        open(SettingsPage.ALERTS)
        assertEquals(SettingsPage.ALERTS, page)
        back()
        assertNull(page)
        // Opened from elsewhere (the Auto-bet tab's "Set up betting"): the page shows.
        page = SettingsPage.BETTING
        compose.onNodeWithTag("settingsPage-BETTING").assertExists()
    }

    // ---- search -------------------------------------------------------------------------------------------------

    @Test
    fun `search finds a setting by any word and opens its page, or the Auto-bet tab`() {
        var autoBet = 0
        screen { SettingsScreen(it, {}, onOpenAutoBet = { autoBet++ }) }
        compose.onNodeWithTag("settingsSearch").performTextInput("kelly")
        compose.onNodeWithTag("settingsHit-Kelly fraction for suggested stakes").assertExists()
        compose.onNodeWithTag("settingsHit-Amount per bet").assertExists() // the auto-bet's, on its tab
        compose.onNodeWithTag("settingsHit-Kelly fraction for suggested stakes").performClick()
        compose.onNodeWithTag("settingsPage-BETTING").assertExists()
        back()
        compose.onNodeWithTag("settingsSearch").performTextClearance()
        compose.onNodeWithTag("settingsSearch").performTextInput("vig")
        compose.onNodeWithTag("settingsHit-How the true odds are worked out").assertExists()
        compose.onNodeWithTag("settingsHit-Devig method").assertExists()
        compose.onNodeWithTag("settingsSearch").performTextClearance()
        compose.onNodeWithTag("settingsSearch").performTextInput("preset")
        compose.onNodeWithTag("settingsHit-Presets").performClick()
        assertEquals(1, autoBet)
        compose.onNodeWithTag("settingsSearch").performTextClearance()
        compose.onNodeWithTag("settingsSearch").performTextInput("zzzz")
        compose.onNodeWithText("Nothing matches", substring = true).assertExists()
    }

    @Test
    fun `search leaves out what a scanner that's off hides`() {
        val cnoOnly = SampleScan.settings.copy(scanner = ScannerMode.CNO)
        assertTrue(SettingsIndex.search("devig method", cnoOnly).isEmpty()) // Vigilant's fair odds page is hidden
        assertTrue(SettingsIndex.search("true odds worked", cnoOnly).isNotEmpty()) // CNO's is there
        val vigilantOnly = SampleScan.settings.copy(scanner = ScannerMode.VIGILANT)
        assertTrue(SettingsIndex.search("preset", vigilantOnly).isEmpty()) // no Auto-bet tab without CNO
        assertTrue(SettingsIndex.search("  ", SampleScan.settings).isEmpty())
        // Every word must match.
        assertEquals(listOf("Longest odds to bet"), SettingsIndex.search("longest bet", SampleScan.settings).map { it.title })
    }

    @Test
    fun `the Bids tab has its row on the Settings list and search finds its rules and the trap guard (Tj 2026-10-03 - settings organized well)`() {
        var bids = 0
        var autoBet = 0
        screen { SettingsScreen(it, {}, onOpenAutoBet = { autoBet++ }, onOpenBids = { bids++ }) }
        compose.onNodeWithTag("settingsRow-BIDS").performScrollTo().assertExists()
        compose.onNodeWithTag("settingsSearch").performTextInput("bids")
        compose.onNodeWithTag("settingsHit-Under the fair").assertExists()
        compose.onNodeWithTag("settingsHit-Most bids up at once").performClick()
        assertEquals(1, bids)
        compose.onNodeWithTag("settingsSearch").performTextClearance()
        compose.onNodeWithTag("settingsSearch").performTextInput("trap")
        compose.onNodeWithTag("settingsHit-Trap guard").assertExists()
        compose.onNodeWithTag("settingsHit-Trap guard: only games starting within").assertExists()
        compose.onNodeWithTag("settingsHit-Skip game lines Novig just moved").performClick()
        assertEquals(1, autoBet)
        // Every hit's title is unique: each is a hit's test tag.
        assertEquals(SettingsIndex.entries.size, SettingsIndex.entries.map { it.title }.toSet().size)
    }

    @Test
    fun `every setting search knows about is on the page it names`() {
        val state = connected()
        screen(state) { SettingsScreen(it, {}) }
        for (page in SettingsPage.shown(state.settings)) {
            val entries = SettingsIndex.entries.filter { it.page == page && it.shown(state.settings) }
            if (entries.isEmpty()) continue
            open(page)
            for (e in entries) {
                assertTrue(
                    "\"${e.title}\" should be on ${page.title}",
                    compose.onAllNodesWithText(e.title, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
                )
            }
            back()
        }
    }

    // ---- the Bet sheet's "Add money" (Tj, 2026-09-29) -----------------------------------------------------------

    private fun connected(base: UiState = SampleScan.state()) = base.copy(
        novig = NovigUi(connection = com.tjshea.vigilant.data.novig.signing.NovigConnection("read-1", "a", "sub-1", false, tradingKeyId = "t", tradingAlias = "a2")),
        betting = BettingUi(enabled = true, balance = 1.0),
    )

    private fun topUp() = TopUp(
        amount = 1.0, needed = 0.85, cost = 1.85,
        bet = BetSheetUi("Team A", "Moneyline · Team B @ Team A", stake = 5.0, resolving = false),
    )

    @Test
    fun `Add money opens Settings on the wallet, scrolled into view, with the shortfall typed in`() {
        val state = connected().let { it.copy(betting = it.betting.copy(topUp = topUp())) }
        screen(state)
        compose.onNodeWithTag("settingsPage-BETTING").assertExists()
        compose.waitForIdle()
        // No scrolling by hand: the wallet is on screen.
        compose.onNodeWithTag("topUpBanner").assertIsDisplayed()
        compose.onNodeWithTag("walletAmount").assertIsDisplayed()
        compose.onNodeWithTag("walletAmount").assertTextContains("1")
    }

    @Test
    fun `without a bet waiting, Settings opens on its list`() {
        screen(connected())
        compose.onNodeWithTag("settingsPage-HOME").assertExists()
        // And the Betting page opens at its top: the limits are further down (so the scroll above is real).
        open(SettingsPage.BETTING)
        compose.onNodeWithText("Most in a day", substring = true).assertIsNotDisplayed()
    }

    @Test
    fun `in CNO only the wallet is still there, since CNO's cards bet through the API too`() {
        val base = connected()
        screen(base.copy(settings = base.settings.copy(scanner = ScannerMode.CNO)))
        open(SettingsPage.BETTING)
        compose.onNodeWithTag("walletBlock").performScrollTo().assertIsDisplayed()
    }
}
