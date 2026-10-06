package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.BurstTraderSettings
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.burst.BurstStatus
import com.tjshea.vigilant.data.novig.trading.burst.BurstTradeStatus
import com.tjshea.vigilant.data.novig.trading.burst.TradeRecord
import com.tjshea.vigilant.data.scanner.ScanSettings
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
 * Tj, 2026-10-06: "make it good enough so that if it is proven I can just turn it on for actual money betting". The real-money burst trader's switch: locked until the recorder's proof says so
 * (and the reason in words), then Tj's own switch behind a confirmation in his numbers; his limits as chips; a halt shown with a Resume.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class BurstTraderUiTest {

    @get:Rule val compose = createComposeRule()

    private fun show(initial: UiState, onChange: (UiState) -> Unit = {}): () -> UiState {
        var ui by androidx.compose.runtime.mutableStateOf(initial)
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BurstTraderSettings(ui) { f -> ui = ui.copy(settings = f(ui.settings)); onChange(ui) }
                    }
                }
            }
        }
        return { ui }
    }

    private fun state(settings: ScanSettings, reason: String?, read: Boolean = true) = SampleScan.state().copy(settings = settings, burstProofReason = reason, burstProofRead = read)

    @Test
    fun `locked: the recorder has not proved it - the switch does nothing and the note says why`() {
        val ui = show(state(ScanSettings(burstRecorder = true), "not enough yet: 1 games and 4 windows that paid at least 1.0 cents (it needs 3 and 10)"))
        compose.onNodeWithTag("burstTradeSwitch").performScrollTo().performClick()
        assertFalse(ui().settings.burstTrade)
        compose.onNodeWithTag("burstTradeNote").assertTextContains("Locked: not enough yet", substring = true)
        compose.onNodeWithTag("burstTradeNote").assertTextContains("no order sent yet", substring = true)
        // No limits to set while it is locked and off.
        assertTrue(compose.onAllNodesWithTagCount("burstTradeStake-1") == 0)
    }

    @Test
    fun `locked: the recorder itself is off - the note says to switch it on first, whatever the old proof says`() {
        val ui = show(state(ScanSettings(burstRecorder = false), null))
        compose.onNodeWithTag("burstTradeSwitch").performScrollTo().performClick()
        assertFalse(ui().settings.burstTrade)
        compose.onNodeWithTag("burstTradeNote").assertTextContains("switch the recorder on first", substring = true)
    }

    @Test
    fun `the proof not read yet is a lock, not a pass`() {
        val ui = show(state(ScanSettings(burstRecorder = true), null, read = false))
        compose.onNodeWithTag("burstTradeSwitch").performScrollTo().performClick()
        assertFalse(ui().settings.burstTrade)
        compose.onNodeWithTag("burstTradeNote").assertTextContains("Locked: checking", substring = true)
    }

    @Test
    fun `unlocked: the switch asks first in Tj's own numbers, Cancel leaves it off, Turn on switches it on and shows the limits`() {
        val ui = show(state(ScanSettings(burstRecorder = true, burstTradeStake = 2.0, burstTradeMaxGame = 10.0, burstTradeMaxDay = 25.0, burstTradeHaltLoss = 5.0), null))
        compose.onNodeWithTag("burstTradeNote").performScrollTo().assertTextContains("Unlocked", substring = true)
        compose.onNodeWithTag("burstTradeSwitch").performScrollTo().performClick()
        assertFalse("nothing is on until he says yes", ui().settings.burstTrade)
        compose.onNodeWithText("Trade with real money?").assertIsDisplayed()
        compose.onNodeWithText(BurstText.tradeConfirm(ui().settings)).assertIsDisplayed()
        assertTrue(BurstText.tradeConfirm(ui().settings), BurstText.tradeConfirm(ui().settings).let { it.contains("\$2 a leg") && it.contains("\$10 a game") && it.contains("\$25 a day") && it.contains("\$5") && it.contains("REAL") })
        compose.onNodeWithTag("burstTradeCancel").performClick()
        assertFalse(ui().settings.burstTrade)
        compose.onNodeWithTag("burstTradeSwitch").performClick()
        compose.onNodeWithTag("burstTradeConfirm").performClick()
        assertTrue(ui().settings.burstTrade)
        compose.onNodeWithTag("burstTradeNote").assertTextContains("ON: trading with real money", substring = true)
        // His limits are chips; each is a pick.
        compose.onNodeWithTag("burstTradeStake-5").performScrollTo().performClick()
        compose.onNodeWithTag("burstTradeGame-25").performScrollTo().performClick()
        compose.onNodeWithTag("burstTradeDay-100").performScrollTo().performClick()
        compose.onNodeWithTag("burstTradeHalt-1").performScrollTo().performClick()
        val s = ui().settings
        assertEquals(listOf(5.0, 25.0, 100.0, 1.0), listOf(s.burstTradeStake, s.burstTradeMaxGame, s.burstTradeMaxDay, s.burstTradeHaltLoss))
        // Off again takes no confirmation.
        compose.onNodeWithTag("burstTradeSwitch").performScrollTo().performClick()
        assertFalse(ui().settings.burstTrade)
    }

    @Test
    fun `a switch that is on stays switchable off even when the proof has lapsed`() {
        val ui = show(state(ScanSettings(burstRecorder = true, burstTrade = true), "the recorder's verdict on the windows it would trade is: no: the windows close before an order of yours would arrive, or the paper result is not positive"))
        compose.onNodeWithTag("burstTradeNote").performScrollTo().assertTextContains("Locked: the recorder's verdict", substring = true)
        compose.onNodeWithTag("burstTradeSwitch").performClick()
        assertFalse(ui().settings.burstTrade)
    }

    @Test
    fun `a halt shows why and one tap on Resume clears it, and a halt is not hidden when the switch is off`() {
        val ui = show(state(ScanSettings(burstRecorder = true, burstTrade = true, burstTradeHalted = "2 covers in a row ended with a leg held alone: Resume it in Settings once you have looked at the Tracker"), null))
        compose.onNodeWithTag("burstTradeHalted").performScrollTo().assertTextContains("2 covers in a row", substring = true)
        compose.onNodeWithTag("burstTradeResume").performClick()
        assertNull(ui().settings.burstTradeHalted)
        assertTrue("still on: Resume does not turn it off or on", ui().settings.burstTrade)
    }

    @Test
    fun `the words - what it did, in dollars, and why it was held back`() {
        val st = BurstTradeStatus(attempts = 5, locked = 3, partial = 1, naked = 0, none = 1, refused = 2, lockedProfit = 0.08, nakedCost = 0.45, last = "LOCKED · ML ATL YES / Spr ATL -1.5 NOT · 185 locked, 0 held alone")
        val line = BurstText.tradeLine(null, st, ScanSettings(burstRecorder = true, burstTrade = true))
        assertTrue(line, line.startsWith("ON: trading with real money") && line.contains("5 sent: 3 locked (\$0.08), 1 with a leg held alone (\$0.45), 1 not filled, 2 refused") && line.contains("last: LOCKED"))
        assertTrue(BurstText.tradeLine(null, BurstTradeStatus(), ScanSettings(burstRecorder = true, burstTrade = true, burstTradeHalted = "x")).startsWith("Unlocked"))
    }

    private fun trade(outcome: String, locked: Double, naked: Double) = TradeRecord(
        1_790_000_000_000L, "NFL", "g1", "A @ B", "ML ATL YES / Spr ATL -1.5 NOT", 185, 0.539, 0.435, 0.011, 90, outcome, 185, if (outcome == "NAKED") 0 else 185, 1.8, 0.05, 0, 185, locked, 0, naked, "both legs sent at once",
    )

    @Test
    fun `Diagnostics carries the trader - its switch and limits, why it is locked, what it held back and what it did`() {
        val on = ScanSettings(burstRecorder = true, burstTrade = true, burstTradeStake = 1.0)
        val st = BurstTradeStatus(attempts = 1, locked = 1, lockedProfit = 0.02, skipped = mapOf("cooldown" to 7, "not proved yet" to 2))
        val text = BurstText.diagnostics(BurstStatus(running = true, leagues = setOf("NFL")), emptyList(), "round trip: ASSUMED 150 ms", on, running = true, trades = listOf(trade("LOCKED", 0.02, 0.0)), trader = st, proofReason = "not enough yet")
        assertNotNull(text)
        assertTrue(text, text!!.contains("Burst trader (real money): ON · stake \$1 a leg, \$5 a game, \$10 a day, halts at \$3 held alone"))
        assertTrue(text, text.contains("Locked: not enough yet"))
        assertTrue(text, text.contains("held back this run: cooldown 7, not proved yet 2"))
        assertTrue(text, text.contains("1 attempt: 1 locked · profit locked \$0.02"))
        // A halt is in it too.
        val halted = BurstText.diagnostics(BurstStatus(), emptyList(), "", on.copy(burstTradeHalted = "an order's answer was lost"), running = false, trades = listOf(trade("UNCONFIRMED", 0.0, 0.0)))!!
        assertTrue(halted, halted.contains("HALTED: an order's answer was lost"))
        // A trade journal alone (the recorder since turned off) still shows.
        assertNotNull(BurstText.diagnostics(BurstStatus(), emptyList(), "", ScanSettings(), running = false, trades = listOf(trade("NONE", 0.0, 0.0))))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithText(text: String) = onNode(androidx.compose.ui.test.hasText(text))
}
