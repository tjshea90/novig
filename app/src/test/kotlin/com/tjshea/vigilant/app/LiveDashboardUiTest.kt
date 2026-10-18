package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.cloud.CloudStatus
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.cloud.LiveStats
import com.tjshea.vigilant.data.cloud.LiveTrend
import com.tjshea.vigilant.data.cloud.SocketState
import com.tjshea.vigilant.data.scanner.TailLiveSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tj, 2026-10-18 (TCL122-4): the Live tab is a monitor and control board for GitHub's live engine, with a GitHub / Phone switch, real-time numbers, graphs and every socket. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class LiveDashboardUiTest {
    @get:Rule val compose = createComposeRule()
    private val now = SampleScan.NOW

    private fun base(): UiState {
        val st = SampleScan.state()
        val s = st.settings.copy(tailLive = TailLiveSettings(on = true, light = true, bids = true, engine = true), gates = st.settings.gates.copy(cloudScan = true, cloudTrading = CloudTradeMode.LIVE))
        val status = CloudStatus(
            runId = "9", heartbeatMs = now - 1_000, liveActive = true, liveNote = "REAL · 3 live games, 3 with a score",
            liveStats = LiveStats(real = true, games = 3, withScore = 3, markets = 18, pushes = 640, judgments = 90, offers = 1, bidsUp = 2, lastPushMs = now - 1_000, judgeTookMs = 28, gameLines = listOf("NYK @ BOS 88-80 Q4 5:10")),
            socketStates = listOf(SocketState("BoltOdds lines", true, true, now - 2_000), SocketState("Kalshi", true, false), SocketState("SX Bet", false, false, 0L, "API off")),
        )
        return st.copy(settings = s, cloudUi = st.cloudUi.copy(status = status))
    }

    private fun show(initial: UiState, light: MainViewModel.LightUi = MainViewModel.LightUi()): () -> UiState {
        var ui by mutableStateOf(initial)
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalClock provides { now }) {
                VigilantTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        com.tjshea.vigilant.app.ui.LiveScreen(ui, light, now, { f -> ui = ui.copy(settings = f(ui.settings)) }, {}, {}, {})
                    }
                }
            }
        }
        return { ui }
    }

    @Test
    fun `GitHub is the default and the board shows the engine's lane, numbers, graphs and sockets`() {
        val trend = (0..10).map { LiveTrend.Sample(now - (10 - it) * 1_000L, 600L + it * 4, 80L + it, 3, 2) }
        show(base(), MainViewModel.LightUi(githubTrend = trend))
        compose.onNodeWithTag("liveWhere-GITHUB").assertIsSelected()
        compose.onNodeWithTag("liveBoardTitle").assertTextContains("Live on GitHub: running", substring = true)
        compose.onNodeWithTag("liveTile-Live games").assertTextContains("3", substring = true)
        compose.onNodeWithTag("liveSpark-Novig pushes a minute").assertExists()
        compose.onNodeWithTag("liveSocket-BoltOdds lines").assertTextContains("live", substring = true)
        compose.onNodeWithTag("liveSocket-Kalshi").assertTextContains("not connected", substring = true)
        compose.onRoot().captureRoboImage("screenshots/9a_live_dashboard_github.png")
    }

    @Test
    fun `Phone moves live to this phone, and back to GitHub`() {
        val ui = show(base())
        compose.onNodeWithTag("liveWhere-PHONE").performClick()
        assertFalse(ui().settings.liveOnGitHub)
        compose.onNodeWithTag("liveBoardTitle").assertTextContains("this phone", substring = true)
        compose.onRoot().captureRoboImage("screenshots/9b_live_dashboard_phone.png")
        compose.onNodeWithTag("liveWhere-GITHUB").performScrollTo().performClick()
        assertTrue(ui().settings.liveOnGitHub)
    }
}
