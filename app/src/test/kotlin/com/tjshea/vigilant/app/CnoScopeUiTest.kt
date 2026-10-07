@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.CnoScopeText
import com.tjshea.vigilant.app.ui.CnoScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.WORDS_IDLE_MS
import com.tjshea.vigilant.app.ui.cnoFiltersLabel
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoLeagues
import com.tjshea.vigilant.data.cno.CnoScope
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-07: "for the cno only scanner, right now I can't filter sports leagues at all. Make sure the cno scanner has plenty of filters just like vigilant scanner" (RESEARCH.md §85):
 * league chips on the CNO tab, a "Which games" section on Settings › CrazyNinjaOdds list (leagues grouped by sport, kinds of bet, pregame only, dollars available, words, props per game),
 * and what each does to the list, the auto-bet's view and the files.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class CnoScopeUiTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun cnoTab(initial: ScanSettings = ScanSettings(scanner = ScannerMode.CNO), onScope: (CnoScope) -> Unit = {}): () -> ScanSettings {
        var s by mutableStateOf(initial)
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { now }) {
              VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CnoScreen(
                        SampleCno.state(SampleScan.state().copy(settings = s)), {}, {},
                        onScope = { t -> s = s.copy(cnoFilters = s.cnoFilters.copy(scope = t(s.cnoFilters.scope))); onScope(s.cnoFilters.scope) },
                    )
                }
              }
            }
        }
        return { s }
    }

    // ---- the CNO tab's league chips (the +EV tab's, hidden in CNO only, for CNO's own list) --------------------------------------------------

    @Test
    fun `the CNO tab has an All chip and one chip for each of CNO's 17 leagues`() {
        cnoTab()
        compose.onNodeWithTag("cnoLeagueAll").assertIsSelected()
        for (l in CnoLeagues.ALL) compose.onNodeWithTag("cnoLeague-${l.label}").performScrollTo().assertExists()
        compose.onAllNodesWithTag("cnoLeague-ATP").assertCountEquals(0)
    }

    @Test
    fun `tapping a league picks it, several can be picked, and taking the last one off is all leagues again`() {
        val settings = cnoTab()
        compose.onNodeWithTag("cnoLeague-NHL").performScrollTo().performClick()
        assertEquals(setOf("NHL"), settings().cnoFilters.scope.leagues)
        compose.onNodeWithTag("cnoLeague-NHL").assertIsSelected()
        compose.onNodeWithTag("cnoLeagueAll").assertIsNotSelected()
        compose.onNodeWithTag("cnoLeague-MLS (USA)").performScrollTo().performClick()
        assertEquals(setOf("NHL", "MLS (USA)"), settings().cnoFilters.scope.leagues)
        compose.onNodeWithTag("cnoLeague-NHL").performScrollTo().performClick()
        compose.onNodeWithTag("cnoLeague-MLS (USA)").performScrollTo().performClick()
        assertTrue(settings().cnoFilters.scope.isDefault)
        compose.onNodeWithTag("cnoLeagueAll").assertIsSelected()
        // All clears a pick in one tap.
        compose.onNodeWithTag("cnoLeague-NFL").performScrollTo().performClick()
        compose.onNodeWithTag("cnoLeagueAll").performScrollTo().performClick()
        assertTrue(settings().cnoFilters.scope.isDefault)
    }

    @Test
    fun `the filter line says the pick, and the default line is exactly what it was`() {
        assertEquals("Conservative worst case · to +150 · 4+ books · ≥1% EV", cnoFiltersLabel(CnoFilters()))
        assertEquals(
            "Conservative worst case · to +150 · 4+ books · ≥1% EV · NHL · pregame only",
            cnoFiltersLabel(CnoFilters(scope = CnoScope(leagues = setOf("NHL"), hideLive = true))),
        )
    }

    @Test
    fun `a list read under other filters says it is reading with the new ones, and the list screens the rows it has under the pick meanwhile`() {
        // The sample snapshot was read with the default filters: a pick makes them differ until the re-read lands.
        cnoTab(ScanSettings(scanner = ScannerMode.CNO, cnoFilters = CnoFilters(scope = CnoScope(leagues = setOf("NCAAF")))))
        compose.onNodeWithTag("cnoReadPending").assertExists()
        // Only the NCAAF row of the sample list is kept; the NFL ones are counted as left out.
        val screened = CnoChecks.screen(SampleCno.snapshot(), CnoFilters(scope = CnoScope(leagues = setOf("NCAAF"))), now)
        assertEquals(listOf("Ohio -33.5"), screened.picks.map { it.row.bet })
        assertTrue(screened.hidden.containsKey(CnoChecks.Reason.LEAGUE))
    }

    @Test
    fun `with the default filters there is no pending note`() {
        cnoTab()
        compose.onAllNodesWithTag("cnoReadPending").assertCountEquals(0)
    }

    // ---- Settings › CrazyNinjaOdds list › Which games -----------------------------------------------------------------------------------------

    private fun settingsPage(initial: ScanSettings = ScanSettings(scanner = ScannerMode.CNO)): () -> ScanSettings {
        var s by mutableStateOf(initial)
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(SampleScan.state().copy(settings = s), { t -> s = t(s) }, page = SettingsPage.CNO)
                }
            }
        }
        return { s }
    }

    @Test
    fun `the CNO settings page has a Which games section with leagues grouped by sport, and a sport's heading ticks all of its leagues`() {
        val settings = settingsPage()
        compose.onNodeWithText(CnoScopeText.TITLE, ignoreCase = true).performScrollTo().assertExists()
        compose.onNodeWithTag("cnoScopeAll").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("cnoScopeLeague-NHL").performScrollTo().performClick()
        assertEquals(setOf("NHL"), settings().cnoFilters.scope.leagues)
        compose.onNodeWithTag("cnoScopeAll").assertIsNotSelected()
        compose.onNodeWithTag("cnoScopeSport-FOOTBALL").performScrollTo().performClick()
        assertEquals(setOf("NHL", "NFL", "NCAAF"), settings().cnoFilters.scope.leagues)
        compose.onNodeWithTag("cnoScopeSport-FOOTBALL").performClick()
        assertEquals(setOf("NHL"), settings().cnoFilters.scope.leagues)
        compose.onNodeWithTag("cnoScopeAll").performScrollTo().performClick()
        assertTrue(settings().cnoFilters.scope.isDefault)
    }

    @Test
    fun `the section says how the list is read for the pick - asked of CNO directly for one league, the app's own for several sports`() {
        val settings = settingsPage()
        compose.onNodeWithText("Read as: every league", substring = true).performScrollTo().assertExists()
        compose.onNodeWithTag("cnoScopeLeague-NHL").performScrollTo().performClick()
        compose.onNodeWithText("asked of CrazyNinjaOdds directly (NHL)", substring = true).assertExists()
        compose.onNodeWithTag("cnoScopeLeague-WNBA").performScrollTo().performClick()
        compose.onNodeWithText("can only be asked for one league or one sport at a time", substring = true).assertExists()
        assertEquals(setOf("NHL", "WNBA"), settings().cnoFilters.scope.leagues)
    }

    @Test
    fun `kinds, hide live, the dollars box, props per game and the words boxes set the scope`() {
        val settings = settingsPage()
        compose.onNodeWithTag("cnoScopeKind-PROP").performScrollTo().performClick()
        assertEquals(setOf(BetKind.PROP), settings().cnoFilters.scope.kinds)
        compose.onNodeWithTag("cnoScopeKindAll").performScrollTo().performClick()
        assertTrue(settings().cnoFilters.scope.kinds.isEmpty())
        compose.onNodeWithTag("cnoHideLive").performScrollTo().performClick()
        assertTrue(settings().cnoFilters.scope.hideLive)
        compose.onNodeWithTag("cnoMinLiquidityField").performScrollTo().performTextClearance()
        compose.onNodeWithTag("cnoMinLiquidityField").performTextInput("40")
        assertEquals(40, settings().cnoFilters.scope.minLiquidity)
        compose.onNodeWithText("\$50+").performScrollTo().performClick()
        assertEquals(50, settings().cnoFilters.scope.minLiquidity)
        compose.onNodeWithTag("cnoPropsPerGameField").performScrollTo().performTextClearance()
        compose.onNodeWithTag("cnoPropsPerGameField").performTextInput("4")
        assertEquals(4, settings().cnoFilters.scope.propsPerGame)
        compose.onNodeWithTag("cnoIncludeField").performScrollTo().performTextInput("ramirez, judge")
        compose.mainClock.advanceTimeBy(WORDS_IDLE_MS + 100)
        assertEquals("ramirez, judge", settings().cnoFilters.scope.include)
        compose.onNodeWithTag("cnoExcludeField").performScrollTo().performTextInput("hits")
        compose.mainClock.advanceTimeBy(WORDS_IDLE_MS + 100)
        assertEquals("hits", settings().cnoFilters.scope.exclude)
    }

    @Test
    fun `a words box saves after typing stops, not on every letter`() {
        val settings = settingsPage()
        compose.onNodeWithTag("cnoIncludeField").performScrollTo().performTextInput("ram")
        assertEquals("nothing saved yet: a read per letter would be a request per letter", "", settings().cnoFilters.scope.include)
        compose.mainClock.advanceTimeBy(WORDS_IDLE_MS / 2)
        assertEquals("", settings().cnoFilters.scope.include)
        compose.mainClock.advanceTimeBy(WORDS_IDLE_MS)
        assertEquals("ram", settings().cnoFilters.scope.include)
    }

    @Test
    fun `the page's summary line and the settings search know the new section`() {
        val state = SampleScan.state().copy(settings = ScanSettings(cnoFilters = CnoFilters(scope = CnoScope(leagues = setOf("NHL")))))
        val line = com.tjshea.vigilant.app.ui.SettingsSummary.of(SettingsPage.CNO, state)
        assertTrue(line, line.endsWith(" · NHL"))
        assertFalse(com.tjshea.vigilant.app.ui.SettingsSummary.of(SettingsPage.CNO, SampleScan.state()).contains(" · NHL"))
        val titles = com.tjshea.vigilant.app.ui.SettingsIndex.entries.map { it.title }
        for (t in listOf("Leagues listed", "Kinds of bet listed", "Hide live games", "Fewest dollars available", "Show only bets with these words", "Leave out bets with these words", "Props per game")) {
            assertTrue(t, t in titles)
        }
    }

    @Test
    fun `a screenshot of the new section`() {
        settingsPage(ScanSettings(scanner = ScannerMode.CNO, cnoFilters = CnoFilters(scope = CnoScope(leagues = setOf("NHL", "WNBA"), kinds = setOf(BetKind.PROP), minLiquidity = 50, propsPerGame = 4))))
        compose.onNodeWithText(CnoScopeText.TITLE, ignoreCase = true).performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/5r_settings_cno_which_games.png")
        compose.onNodeWithTag("cnoPropsPerGameField").performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/5r_settings_cno_which_games_more.png")
    }

    @Test
    fun `a screenshot of the CNO tab with a league picked`() {
        cnoTab(ScanSettings(scanner = ScannerMode.CNO, cnoFilters = CnoFilters(scope = CnoScope(leagues = setOf("NCAAF")))))
        compose.onRoot().captureRoboImage("screenshots/5r_cno_tab_league_chips.png")
    }
}
