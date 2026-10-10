package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Locked bets in the Tracker (Tj, 2026-10-02 20:06Z: "Add options to remove arbitraged locked bets out of stats and bet trackers. It makes no sense for me
 * to track a bet that is already cashed out. Maybe a stat tracker for amount and percentage of bets locked in and the total profit and percentage of
 * profit for those bets"): the switch, the lists without them, and the Locked in card.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class TrackerLocksTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun api(id: String, market: String, selection: String, contracts: Long, paid: Double, lockFor: String? = null) = TrackedBet(
        id = id, createdAtMs = now - 3_600_000, league = "NBA", eventName = "B @ A", startsTs = now + 3_600_000, marketLabel = "Moneyline", selection = selection,
        marketId = market, outcomeId = "$market-$selection", price = paid / (contracts * 0.01), cost = paid / (contracts * 0.01), fairAtBet = 0.42, evPercentAtBet = 0.03,
        stake = paid, status = BetStatus.PENDING, orderId = "o-$id", contracts = contracts, paid = paid, fee = 0.0, lockFor = lockFor,
    )

    // Team A bought for $4.00, locked with Team B for $5.50: $10 back either way, $0.50 profit. Team C rides alone.
    private val bets = listOf(api("p", "m1", "Team A", 1000, 4.00), api("l", "m1", "Team B", 1000, 5.50, lockFor = "p"), api("c", "m2", "Team C", 1000, 4.00))

    private fun show(hide: () -> Boolean, onHide: (Boolean) -> Unit, view: TrackerView) {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.tjshea.vigilant.app.ui.LocalClock provides { now }) {
                VigilantTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val state = SampleScan.state().let { it.copy(bets = bets, settings = it.settings.copy(trackerHideLocked = hide())) }
                        TrackerScreen(state, onSettle = { _, _ -> }, onDelete = {}, initialView = view, onHideLocked = onHide)
                    }
                }
            }
        }
    }

    @Test
    fun `locked bets leave the Bets list while the switch is on, and come back when it's off`() {
        var hide by mutableStateOf(true)
        val switched = ArrayList<Boolean>()
        show({ hide }, { switched += it; hide = it }, TrackerView.BETS)
        compose.onNodeWithTag("hideLockedChip").assertIsSelected()
        compose.onNodeWithText("2 locked bets hidden", substring = true).assertExists()
        compose.onNodeWithText("Team C", substring = true).assertExists()
        compose.onNodeWithText("Team A", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Team B", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Open (1)").assertExists()
        compose.onNodeWithTag("hideLockedChip").performClick()
        assertEquals(listOf(false), switched)
        compose.onNodeWithTag("hideLockedChip").assertIsNotSelected()
        compose.onNodeWithText("Team A", substring = true).assertExists()
        compose.onNodeWithText("Open (3)").assertExists()
    }

    @Test
    fun `there is no Locked in card (Tj, 2026-10-10), and Hide locked bets still decides what the open money counts`() {
        var hide by mutableStateOf(true)
        show({ hide }, { hide = it }, TrackerView.STATS)
        compose.onNodeWithTag("lockStats").assertDoesNotExist()
        // Hidden: only Team C's $4.00 is open money; shown, both picks (the lock is in the money, not the picks).
        compose.onNodeWithText("The 1 open bet", substring = true).assertExists()
        hide = false
        compose.onNodeWithText("The 2 open bets", substring = true).assertExists()
    }
}
