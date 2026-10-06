package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ReportActions
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.burst.BurstStatus
import com.tjshea.vigilant.data.novig.burst.GameRecord
import com.tjshea.vigilant.data.novig.burst.LatencyModel
import com.tjshea.vigilant.data.novig.burst.PaperOutcome
import com.tjshea.vigilant.data.novig.burst.ProfileResult
import com.tjshea.vigilant.data.novig.burst.WindowRecord
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-06: "Build a no orders recorder of the score burst idea to see if it works with my current setup and novig key." The switch, the leagues, the one line that says what it is
 * doing, and Share live burst study, in Settings › Diagnostics & about; and its words.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class BurstUiTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `the Diagnostics section has the recorder - off by default, a switch, the leagues once on, its line, and a share button`() {
        var shared = 0
        var shown = 0
        var ui by androidx.compose.runtime.mutableStateOf(SampleScan.state().copy(burstNote = "Recording 2 live games (112 lines, 5,230 pushes, 1 window open now) · 4 windows in 1 game recorded: not enough yet"))
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        ui, { f -> ui = ui.copy(settings = f(ui.settings)) }, page = SettingsPage.HELP,
                        reportActions = ReportActions(onShareBurst = { shared++ }, onBurstShown = { shown++ }),
                    )
                }
            }
        }
        assertFalse("off by default: it opens a second websocket", ui.settings.burstRecorder)
        compose.onNodeWithTag("burstSwitch").performScrollTo().performClick()
        assertTrue(ui.settings.burstRecorder)
        // Once on, the leagues are chips (all six by default), and one can be taken off.
        assertEquals(ScanSettings.BURST_LEAGUES.toSet(), ui.settings.burstLeagues)
        compose.onNodeWithTag("burstLeague-NBA").performScrollTo().performClick()
        assertEquals(ScanSettings.BURST_LEAGUES.toSet() - "NBA", ui.settings.burstLeagues)
        compose.onNodeWithTag("burstLeague-NBA").performClick()
        assertTrue("NBA" in ui.settings.burstLeagues)
        compose.onNodeWithTag("burstNote").performScrollTo().assertTextContains("Recording 2 live games", substring = true)
        assertTrue("the page asks for the line when it opens", shown >= 1)
        compose.onNodeWithTag("shareBurstStudy").performScrollTo().assertTextContains("Share live burst study with Claude", substring = true)
        compose.onNodeWithTag("shareBurstStudy").performClick()
        assertEquals(1, shared)
        // What it is, in words: no orders, and what it cannot prove.
        compose.onNodeWithTag("burstSwitch").performScrollTo()
        assertTrue(BurstText.HINT.contains("no way to place an order") && BurstText.HINT.contains("cannot prove a profit"))
    }

    private fun window(game: String) = WindowRecord(
        "NFL", game, game, "ML ATL YES / Spr ATL -1.5 NOT", 1_790_000_000_000L, 900, 0.539, 0.435, 25_000, 0.011, 0.012, 30_000, 4,
        listOf(ProfileResult(LatencyModel.SLOW, 465, PaperOutcome.BOTH.name, 100, 0.5, 100, 0.5)),
    )

    @Test
    fun `the line says what it is doing and what it has recorded - waiting, recording, not recording and why`() {
        val off = BurstText.note(BurstStatus(), emptyList())
        assertTrue(off, off.startsWith("Off") && off.contains("nothing recorded yet"))
        val waiting = BurstText.note(BurstStatus(running = true, leagues = setOf("NFL", "NBA")), emptyList())
        assertTrue(waiting, waiting.startsWith("Waiting for a live game of NBA, NFL"))
        val on = BurstText.note(BurstStatus(running = true, leagues = setOf("NFL"), games = 2, lines = 112, updates = 5_230, open = 1), listOf(GameRecord("NFL", "g1", "A @ B", 0, 60_000, 56, 10), window("g1")))
        assertTrue(on, on.startsWith("Recording 2 live games (112 lines, 5,230 pushes, 1 window open now)") && on.contains("1 window in 1 game recorded: not enough yet"))
        val problem = BurstText.note(BurstStatus(running = false, problem = "No Novig key is connected"), emptyList())
        assertTrue(problem, problem.startsWith("Not recording: No Novig key is connected"))
    }

    @Test
    fun `Diagnostics always says whether the recorder is on - two short lines when off and empty, the full block otherwise`() {
        val on = ScanSettings(burstRecorder = true, burstLeagues = setOf("NFL"))
        val off = BurstText.diagnostics(BurstStatus(), emptyList(), "", ScanSettings(), running = false)
        assertTrue(off, off.startsWith("Live burst recorder: off · nothing recorded"))
        assertTrue(off, off.contains("Burst trader (real money): off"))
        assertFalse(off, off.contains("WHAT THIS PROVES"))
        assertTrue(BurstText.diagnostics(BurstStatus(), emptyList(), "", ScanSettings(burstTrade = true, burstTradeHalted = "x"), running = false).contains("Burst trader (real money): ON · HALTED: x"))
        val text = BurstText.diagnostics(BurstStatus(running = true, leagues = setOf("NFL")), listOf(window("g1")), "round trip: ASSUMED 150 ms", on, running = true)
        assertTrue(text, text.startsWith("Live burst recorder: ON · running · leagues NFL"))
        assertTrue(text, text.contains("WHAT THIS PROVES") && text.contains("NFL: 1 game"))
        // Off but with a journal: still shown, as off.
        assertTrue(BurstText.diagnostics(BurstStatus(), listOf(window("g1")), "", ScanSettings(), running = false).startsWith("Live burst recorder: off · not running"))
    }

    @Test
    fun `the recorder is given no way to place an order - its source files never touch the trading client`() {
        val dir = java.io.File("../data/src/main/kotlin/com/tjshea/vigilant/data/novig/burst")
        val code = dir.listFiles { f -> f.name.endsWith(".kt") }!!.joinToString("\n") { it.readText().lines().filterNot { l -> l.trimStart().startsWith("*") || l.trimStart().startsWith("//") || l.trimStart().startsWith("/*") }.joinToString("\n") }
        for (word in listOf("NovigTradingClient", "placeOrder", "cancelOrder", "orders/batch", "MakerDesk", "AutoBet", "/v3/orders")) {
            assertFalse("the burst recorder must not mention $word", code.contains(word))
        }
        // And the container hands it the READ key's stream and a free echo, no trading client.
        val app = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/VigilantApp.kt").readText()
        val block = app.substringAfter("val burst: com.tjshea.vigilant.data.novig.burst.BurstRecorder by lazy").substringBefore("/** Starts or stops the burst recorder")
        assertTrue(block, block.contains("readKeyClient") && block.contains(".echo()"))
        assertFalse(block, block.contains("trading"))
    }
}
