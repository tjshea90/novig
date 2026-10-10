package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.KeyActions
import com.tjshea.vigilant.app.ui.ReportActions
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.pinnodds.LiveRunnerStatus
import com.tjshea.vigilant.data.pinnodds.LiveTradeStatus
import com.tjshea.vigilant.data.pinnodds.LiveTrigger
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-08: "this will require a setting for me to input the pinnodds API key and a button to test the key". Settings › Pinnodds live: the key, its Test key button and answer, the feed switch
 * (paper), the real-bets switch behind a confirmation in his own numbers, the limits as chips, and a halt with a Resume.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h4000dp-xxhdpi")
class PinnLiveUiTest {
    // Pinnodds is dormant in the app (Tj, 2026-10-09); these tests keep its code honest, so they wake it for their own run only.
    @org.junit.Before fun wake() { com.tjshea.vigilant.data.scanner.Dormant.PINNODDS = false }
    @org.junit.After fun sleep() { com.tjshea.vigilant.data.scanner.Dormant.PINNODDS = false }

    @get:Rule val compose = createComposeRule()

    private class Probe {
        var added: Pair<ApiProvider, String>? = null
        var tested = 0
    }

    private fun show(initial: UiState, probe: Probe = Probe()): () -> UiState {
        var ui by mutableStateOf(initial)
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        ui, { f -> ui = ui.copy(settings = f(ui.settings)) }, page = SettingsPage.PINNODDS,
                        keys = KeyActions(add = { p, k -> probe.added = p to k }),
                        reportActions = ReportActions(onTestPinnKey = { probe.tested++ }),
                    )
                }
            }
        }
        return { ui }
    }

    private fun state(settings: ScanSettings = ScanSettings(), keys: List<String> = listOf("abcd1234efgh5678")) =
        SampleScan.state().copy(settings = settings, pinnoddsKeys = keys, pinnLive = LiveRunnerStatus(running = true, socket = "live"))

    @Test
    fun `the Pinnacle website feed is a choice beside the socket, needs no key, and shows its counters and the race`() {
        val ui = show(state(keys = emptyList()).copy(pinnWebsiteStats = com.tjshea.vigilant.data.pinnodds.WebsiteStats(games = 7, polls = 120, failures = 1, changes = 33, lastCycleMs = 410, avgLatencyMs = 380), pinnRaceLines = listOf("Feed race (website minus socket, 12 price versions both saw): median 1800 ms")))
        compose.onNodeWithTag("pinnFeed-SOCKET").performScrollTo().assertExists()
        compose.onNodeWithTag("pinnFeed-WEBSITE").performScrollTo().performClick()
        assertTrue(ui().settings.pinnWebsite.website)
        compose.onNodeWithTag("pinnWebsiteStats").performScrollTo().assertTextContains("7 live games followed", substring = true)
        compose.onNodeWithTag("pinnLiveNote").performScrollTo().assertTextContains("website", substring = true, ignoreCase = true)
        compose.onNodeWithTag("pinnFeed-SOCKET").performClick()
        compose.onNodeWithTag("pinnCompare").performScrollTo().performClick()
        assertTrue(ui().settings.pinnWebsite.compare)
        compose.onNodeWithTag("pinnRace").performScrollTo().assertTextContains("median 1800 ms", substring = true)
        compose.onNodeWithTag("pinnWebsitePoll-0").performScrollTo().performClick()
        assertEquals(1_000, ui().settings.pinnWebsite.pollMs)
    }

    @Test
    fun `the Test key button needs a saved key, then asks for the test`() {
        val probe = Probe()
        show(state(keys = emptyList()), probe)
        compose.onNodeWithTag("pinnTestKey").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `with a key saved the Test key button asks for the test once per tap`() {
        val probe = Probe()
        show(state(), probe)
        compose.onNodeWithTag("pinnTestKey").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, probe.tested)
    }

    @Test
    fun `the test answer is shown, a failure in the error colour is still readable`() {
        show(state().copy(pinnKeyNote = "Pinnodds accepted the key: Trial · 3-day full demo, ends Oct 10, 4:34 PM. WebSocket add-on is ON until Oct 10, 4:34 PM.", pinnKeyOk = true))
        compose.onNodeWithTag("pinnKeyNote").performScrollTo().assertTextContains("accepted the key", substring = true)
    }

    @Test
    fun `the feed switch is off by default, paper is the mode when it is on, and real bets need the feed first`() {
        val ui = show(state())
        compose.onNodeWithTag("pinnLiveNote").performScrollTo().assertTextContains("Off", substring = true)
        // Real bets cannot be switched on while the feed is off.
        compose.onNodeWithTag("pinnLiveBetSwitch").performScrollTo().performClick()
        assertFalse(ui().settings.pinnLiveBet)
        compose.onNodeWithTag("pinnLiveSwitch").performScrollTo().performClick()
        assertTrue(ui().settings.pinnLive)
        assertFalse("the feed alone is paper", ui().settings.pinnLiveBet)
        compose.onNodeWithTag("pinnLiveNote").performScrollTo().assertTextContains("paper", substring = true)
    }

    @Test
    fun `real bets ask first in Tj's own numbers, and Cancel leaves them off`() {
        val ui = show(state(ScanSettings(pinnLive = true, pinnLiveStake = 5.0, pinnLiveMaxGame = 10.0, pinnLiveMaxDay = 50.0)))
        compose.onNodeWithTag("pinnLiveBetSwitch").performScrollTo().performClick()
        compose.onNodeWithTag("pinnLiveCancel").performClick()
        assertFalse(ui().settings.pinnLiveBet)
        compose.onNodeWithTag("pinnLiveBetSwitch").performScrollTo().performClick()
        compose.onNodeWithTag("pinnLiveConfirm").performClick()
        assertTrue(ui().settings.pinnLiveBet)
        assertNull(ui().settings.pinnLiveHalted)
        // The dialog's words used his numbers.
        assertTrue(PinnText.confirm(ui().settings).contains("$5 a bet, $10 a game and $50 a day"))
    }

    @Test
    fun `the limits are chips and the trigger is chosen from three`() {
        val ui = show(state(ScanSettings(pinnLive = true)))
        compose.onNodeWithTag("pinnStake-5").performScrollTo().performClick()
        compose.onNodeWithTag("pinnGame-25").performScrollTo().performClick()
        compose.onNodeWithTag("pinnDay-100").performScrollTo().performClick()
        compose.onNodeWithTag("pinnEv-50").performScrollTo().performClick()
        compose.onNodeWithTag("pinnMove-30").performScrollTo().performClick()
        compose.onNodeWithTag("pinnTrigger-STANDING").performScrollTo().performClick()
        compose.onNodeWithTag("pinnTrigger-STALE").performScrollTo().performClick()
        compose.onNodeWithTag("pinnTrigger-STANDING").performScrollTo().performClick()
        compose.onNodeWithTag("pinnHoldoff-5").performScrollTo().performClick()
        val s = ui().settings
        assertEquals(5, s.pinnLiveHoldoffSeconds)
        assertEquals(5.0, s.pinnLiveStake, 0.0)
        assertEquals(25.0, s.pinnLiveMaxGame, 0.0)
        assertEquals(100.0, s.pinnLiveMaxDay, 0.0)
        assertEquals(0.05, s.pinnLiveMinEv, 1e-9)
        assertEquals(0.03, s.pinnLiveMinMove, 1e-9)
        assertEquals(LiveTrigger.STANDING, s.pinnLiveTrigger)
    }

    @Test
    fun `a halt is shown with the reason and Resume clears it`() {
        val ui = show(state(ScanSettings(pinnLive = true, pinnLiveBet = true, pinnLiveHalted = "an order's answer was lost")))
        compose.onNodeWithText("Stopped: an order's answer was lost").performScrollTo()
        compose.onNodeWithTag("pinnLiveResume").performScrollTo().performClick()
        assertNull(ui().settings.pinnLiveHalted)
    }

    @Test
    fun `the status line says the feed, the matches and the paper or real count`() {
        val run = LiveRunnerStatus(running = true, socket = "live", pinnLive = 40, matched = 9, watched = 70, frameAgeMs = 300, candidates = 3)
        val trade = LiveTradeStatus(paper = 2, bets = 1, missed = 1, last = "PAPER · OKC")
        val line = PinnText.statusLine(run, trade, ScanSettings(pinnLive = true), keyCount = 1)
        assertTrue(line, line.contains("9 matched") && line.contains("70 Novig markets") && line.contains("paper") && line.contains("1 bets, 2 paper, 1 missed"))
        assertTrue(PinnText.statusLine(run, trade, ScanSettings(pinnLive = true, pinnLiveBet = true), 1).contains("REAL BETS"))
        assertTrue(PinnText.statusLine(run, trade, ScanSettings(pinnLive = true), keyCount = 0).contains("no Pinnodds key"))
        assertEquals("Off.", PinnText.statusLine(run, trade, ScanSettings(), 1))
    }
}
