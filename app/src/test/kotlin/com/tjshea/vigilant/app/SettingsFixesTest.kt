package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.AutoBetScreen
import com.tjshea.vigilant.app.ui.AutoBetText
import com.tjshea.vigilant.app.ui.BackgroundScan
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.SettingsSummary
import com.tjshea.vigilant.app.ui.Shadowed
import com.tjshea.vigilant.app.ui.StakeText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.SlipStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-02 ~17:55Z: "look for settings that contradict each other and fix them." Each contradiction found (TASKS.md AX1 a–g), fixed so the screen
 * can't show one thing while the app does another.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h3000dp-xxhdpi")
class SettingsFixesTest {

    @get:Rule val compose = createComposeRule()

    private val base = ScanSettings()

    // ---- (c) the background scan: one switch that means what it says, whatever the scanner -------------------------------

    @Test
    fun `the background scan switch shows what really runs, for each scanner`() {
        for (scanner in ScannerMode.entries) {
            for (mode in AutoScanMode.entries) {
                val s = base.copy(scanner = scanner, autoScan = mode)
                assertEquals("$scanner/$mode", s.autoScansCno || s.autoScansVigilant, BackgroundScan.on(s))
            }
        }
        // The two the old three-way choice let contradict: CNO + Vigilant with CNO only runs CNO alone; CNO with Vigilant only runs nothing.
        assertTrue(BackgroundScan.on(base.copy(scanner = ScannerMode.CNO, autoScan = AutoScanMode.BOTH)))
        assertFalse(BackgroundScan.on(base.copy(scanner = ScannerMode.VIGILANT, autoScan = AutoScanMode.CNO)))
    }

    @Test
    fun `switching it on runs whatever the scanner has on, and off stops it`() {
        for (scanner in ScannerMode.entries) {
            val on = BackgroundScan.set(base.copy(scanner = scanner, autoScan = AutoScanMode.OFF), true)
            assertTrue("$scanner", BackgroundScan.on(on))
            assertFalse("$scanner", BackgroundScan.on(BackgroundScan.set(on, false)))
        }
        // Both scanners: CNO alone (free) unless Vigilant's own scan is asked for, and that choice survives switching off and on? No: on keeps BOTH.
        assertEquals(AutoScanMode.CNO, BackgroundScan.set(base.copy(scanner = ScannerMode.BOTH), true).autoScan)
        assertEquals(AutoScanMode.BOTH, BackgroundScan.set(base.copy(scanner = ScannerMode.BOTH, autoScan = AutoScanMode.BOTH), true).autoScan)
        // Vigilant only: on is its scan (stored as BOTH, which is what runs it).
        assertEquals(AutoScanMode.BOTH, BackgroundScan.set(base.copy(scanner = ScannerMode.VIGILANT), true).autoScan)
        assertEquals(AutoScanMode.BOTH, BackgroundScan.setAlsoVigilant(base, true).autoScan)
        assertEquals(AutoScanMode.CNO, BackgroundScan.setAlsoVigilant(base.copy(autoScan = AutoScanMode.BOTH), false).autoScan)
    }

    @Test
    fun `on the Scanning page the switch reads the real state and asks Vigilant's scan only with both scanners`() {
        var s by mutableStateOf(base.copy(scanner = ScannerMode.VIGILANT, autoScan = AutoScanMode.CNO))
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(SampleScan.state().copy(settings = s), { t -> s = t(s) }, page = SettingsPage.SCANNING)
                }
            }
        }
        // Stored CNO with Vigilant only: nothing runs, and the switch says so.
        compose.onNodeWithTag("backgroundScan").assertIsOff()
        compose.onNodeWithTag("backgroundScan").performClick()
        assertTrue(s.autoScansVigilant)
        compose.onNodeWithTag("backgroundScan").assertIsOn()
        assertNull(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.TestTag).let { null })
        compose.onNodeWithTag("backgroundAlsoVigilant").assertDoesNotExist()
        s = s.copy(scanner = ScannerMode.BOTH, autoScan = AutoScanMode.CNO)
        compose.onNodeWithTag("backgroundAlsoVigilant").assertIsOff().performClick()
        assertEquals(AutoScanMode.BOTH, s.autoScan)
        // (d) Off with auto-bet on: it says auto-bet stops too.
        s = s.copy(autoBet = true, autoScan = AutoScanMode.OFF)
        compose.onNodeWithTag("backgroundAutoBetOff").assertExists()
    }

    // ---- (a) the starting amount: one choice for the bet slip and the Bet sheet --------------------------------------------

    @Test
    fun `the Bet sheet's own amount shows only when something uses it, and says when`() {
        assertTrue(StakeText.sheetAmountUsed(base.copy(slipStake = SlipStake.OFF)))
        assertTrue(StakeText.sheetAmountUsed(base.copy(slipStake = SlipStake.KELLY))) // a bet with no Kelly stake
        assertFalse(StakeText.sheetAmountUsed(base.copy(slipStake = SlipStake.ONE_DOLLAR)))
        assertFalse(StakeText.sheetAmountUsed(base.copy(slipStake = SlipStake.CUSTOM)))
        assertEquals("When a bet has no Kelly stake (no edge at its price), start at", StakeText.sheetAmountTitle(base.copy(slipStake = SlipStake.KELLY)))
        assertEquals("Bet sheet starts at", StakeText.sheetAmountTitle(base.copy(slipStake = SlipStake.OFF)))
        assertTrue(StakeText.startHint(base.copy(slipStake = SlipStake.ONE_DOLLAR)).contains("in Novig's bet slip and in Vigilant's Bet sheet"))
        // And the amount it starts at agrees: $1 is $1 in the sheet too.
        assertEquals(1.0, BetAmount.base(base.copy(slipStake = SlipStake.ONE_DOLLAR, apiBetStake = 5.0), 3.0).first, 0.0)
    }

    // ---- (e)/(f) a setting another one makes moot says so ----------------------------------------------------------------

    @Test
    fun `a limit CNO's own list already decides is said where it's set`() {
        val cno = base.copy(scanner = ScannerMode.CNO, cnoFilters = base.cnoFilters.copy(maxOdds = 150, minEv = 0.03))
        assertNotNull(Shadowed.autoBetOdds(cno.copy(autoBetMaxOdds = 0))) // no limit: CNO's +150 decides
        assertNotNull(Shadowed.autoBetOdds(cno.copy(autoBetMaxOdds = 200)))
        assertNull(Shadowed.autoBetOdds(cno.copy(autoBetMaxOdds = 130)))
        assertNull(Shadowed.autoBetOdds(cno.copy(autoBetMaxOdds = 200, cnoFilters = cno.cnoFilters.copy(maxOdds = 0))))
        assertTrue(Shadowed.autoBetOdds(cno.copy(autoBetMaxOdds = 200))!!.contains("+150"))
        assertNotNull(Shadowed.autoBetEdge(cno.copy(autoBetMinEv = 0.025)))
        assertNull(Shadowed.autoBetEdge(cno.copy(autoBetMinEv = 0.03)))
        assertNotNull(Shadowed.alertEdge(cno.copy(alertMinEv = 0.02)))
        assertNull(Shadowed.alertEdge(cno.copy(alertMinEv = 0.0))) // alerts off: nothing to say
        assertNull(Shadowed.alertEdge(cno.copy(alertMinEv = 0.04)))
        // Vigilant only: CNO's list isn't read, so it decides nothing.
        assertNull(Shadowed.autoBetOdds(cno.copy(scanner = ScannerMode.VIGILANT, autoBetMaxOdds = 300)))
        // Days ahead past the start window.
        assertNotNull(Shadowed.daysAhead(base.copy(startsWithinHours = 6, daysAhead = 7)))
        assertNull(Shadowed.daysAhead(base.copy(startsWithinHours = 0, daysAhead = 7)))
        assertNull(Shadowed.daysAhead(base.copy(startsWithinHours = 240, daysAhead = 7)))
    }

    // ---- (d) auto-bet: why it isn't running, with the tap that fixes it -----------------------------------------------------

    private fun ready(s: ScanSettings = base.copy(autoBet = true, autoScan = AutoScanMode.CNO)) = SampleScan.state().copy(
        settings = s,
        novig = NovigUi(connection = com.tjshea.vigilant.data.novig.signing.NovigConnection("read-1", "a", "sub-1", false, tradingKeyId = "t", tradingAlias = "a2")),
        betting = BettingUi(enabled = true, balance = 25.0),
    )

    @Test
    fun `each reason auto-bet can't run has its own one-tap fix`() {
        assertNull(AutoBetText.fixFor(ready()))
        assertEquals(AutoBetText.Fix.SET_UP_BETTING, AutoBetText.fixFor(ready().copy(betting = BettingUi())))
        assertEquals(AutoBetText.Fix.RESUME_SCANNING, AutoBetText.fixFor(ready(base.copy(autoBet = true, autoScan = AutoScanMode.CNO, paused = true))))
        assertEquals(AutoBetText.Fix.SCANNER, AutoBetText.fixFor(ready(base.copy(autoBet = true, autoScan = AutoScanMode.CNO, scanner = ScannerMode.VIGILANT))))
        assertEquals(AutoBetText.Fix.BACKGROUND_SCAN, AutoBetText.fixFor(ready(base.copy(autoBet = true, autoScan = AutoScanMode.OFF))))
        // A halt has its own Resume: no second button.
        assertNull(AutoBetText.fixFor(ready(base.copy(autoBet = true, autoBetHalted = "lost order"))))
    }

    @Test
    fun `on the Auto-bet tab the fix is one tap`() {
        var state by mutableStateOf(ready(base.copy(autoBet = true, autoScan = AutoScanMode.OFF)))
        val opened = ArrayList<SettingsPage>()
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AutoBetScreen(state, { t -> state = state.copy(settings = t(state.settings)) }, onOpenSettings = { opened += it })
                }
            }
        }
        compose.onNodeWithTag("autoBetFixBackground").performClick()
        assertTrue(state.settings.autoBetsNow)
        compose.onNodeWithTag("autoBetFixBackground").assertDoesNotExist()
        state = state.copy(settings = state.settings.copy(paused = true))
        compose.onNodeWithTag("autoBetFixPause").performClick()
        assertFalse(state.settings.paused)
        // Not set up: the button opens Settings › Betting & Novig account.
        state = state.copy(betting = BettingUi())
        compose.onNodeWithTag("autoBetFixBetting").performScrollTo().performClick()
        assertEquals(listOf(SettingsPage.BETTING), opened)
    }

    // ---- the home list's lines ---------------------------------------------------------------------------------------------

    @Test
    fun `each page's line on the home list says what it's set to now`() {
        val st = SampleScan.state()
        val s = st.settings
        assertTrue(SettingsSummary.of(SettingsPage.SCANNING, st.copy(settings = s.copy(paused = true))).startsWith("Paused"))
        assertTrue(SettingsSummary.of(SettingsPage.SCANNING, st.copy(settings = BackgroundScan.set(s.copy(autoScanSeconds = 30), true))).contains("background every 30 sec"))
        assertTrue(SettingsSummary.of(SettingsPage.SCANNING, st.copy(settings = s.copy(autoScan = AutoScanMode.OFF))).contains("background off"))
        assertEquals("Off", SettingsSummary.of(SettingsPage.ALERTS, st.copy(settings = s.copy(alertMinEv = 0.0))))
        assertEquals("3%+ · sharp books: veto", SettingsSummary.of(SettingsPage.ALERTS, st.copy(settings = s.copy(alertMinEv = 0.03))))
        assertTrue(SettingsSummary.of(SettingsPage.BETTING, st).contains("Novig key not connected"))
        assertTrue(SettingsSummary.of(SettingsPage.BETTING, ready()).contains("Wallet $25.00"))
        assertEquals("Off", SettingsSummary.autoBet(base))
        assertEquals("On · Volume + safe CLV", SettingsSummary.autoBet(com.tjshea.vigilant.data.scanner.Presets.apply(base.copy(autoBet = true), com.tjshea.vigilant.data.scanner.Presets.VOLUME)))
        assertEquals("Stopped: needs you", SettingsSummary.autoBet(base.copy(autoBet = true, autoBetHalted = "x")))
    }
}
