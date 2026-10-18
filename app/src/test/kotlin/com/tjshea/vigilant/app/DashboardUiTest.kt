package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.DashboardContent
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.cloud.CloudStatus
import com.tjshea.vigilant.data.cloud.DashboardModel
import com.tjshea.vigilant.data.cloud.FeedLine
import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageViews
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tj, 2026-10-15: "put the dashboard inside the vigilant android app with easy to see organized information and meters/graphs optimized for mobile phone viewing". Rendered from sample engine data, no app objects. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class DashboardUiTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_792_092_000_000L
    private val day = QuotaPolicy.PROPLINE.periodStart(now)

    private val status = CloudStatus(
        runId = "38795443180", heartbeatMs = now - 4_000, passes = 5, scanMs = 300_000, rows = 38, lastScanEndedAtMs = now - 90_000, fairAgeMedianS = 200, novigAgeMedianS = 40,
        scanNote = "EV spread of 16792 priced lines: +EV 230 · 0 to -2% 1482", tradeMode = "live", tradeNote = "live: looked 33, passed 0, ordered 0 (\$0.00)",
        walletCash = 124.27, walletEquity = 124.27, walletReadAtMs = now - 30_000, novigRoute = "Novig read-only key active: 16 reads a second, websocket on",
        feedReportAtMs = now - 90_000, feedReport = listOf(FeedLine("propline", fetched = 6, matched = 100), FeedLine("bolt", error = "BoltOdds: the lines socket is not connected")),
    )

    private val propline = UsageViews.build(
        QuotaPolicy.PROPLINE, listOf("k1", "k2"),
        ProviderUsage(keys = mapOf("k1" to KeyUsage(periodStart = day, used = 1000, remaining = 0, limit = 1000, depletedUntil = day + 86_400_000L), "k2" to KeyUsage(periodStart = day, used = 206, remaining = 794, limit = 1000)), dayStart = day, callsToday = 741),
        now,
    )
    private val bolt = UsageViews.build(QuotaPolicy.BOLTODDS, listOf("b1"), ProviderUsage(keys = mapOf("b1" to KeyUsage(periodStart = day, used = 104)), dayStart = day, callsToday = 110), now)

    private fun show(st: CloudStatus?, history: List<DashboardModel.Point> = emptyList()) = compose.setContent {
        VigilantTheme(darkTheme = true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                DashboardContent(st, history, listOf(propline, bolt), now, cloudOn = true, onOpenCloudSettings = {})
            }
        }
    }

    @Test
    fun `the page shows the engine state, the tiles, each api's health and its meter`() {
        show(status)
        compose.onNodeWithTag("dashboardHeadline").assertIsDisplayed()
        compose.onNodeWithText("Running").assertExists()
        compose.onNodeWithText("$124.27").assertExists()
        compose.onNodeWithText("Bets listed").assertExists()
        compose.onNodeWithTag("dashboardTab-APIs").performClick()
        compose.onNodeWithTag("dashboardApi-propline").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("794 of 2000 requests left", substring = true).assertExists()
        compose.onNodeWithText("working").assertExists()
        compose.onNodeWithTag("dashboardApi-boltodds").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("failing").assertExists()
        compose.onNodeWithText("lines socket is not connected", substring = true).assertExists()
        compose.onNodeWithTag("dashboardOpenGitHub").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `before any engine word the page says so`() {
        show(null)
        compose.onNodeWithText("Not connected").assertExists()
    }

    @Test
    fun `with history the page draws a graph for each number`() {
        val points = (1..10).map { DashboardModel.Point(now - (10 - it) * 300_000L, 280 + it, 38, 200 + it, 40, 200 + it, 120.0 + it) }
        show(status, points)
        compose.onNodeWithTag("dashboardTab-Trends").performClick()
        compose.onNodeWithTag("dashboardTrend-Scan time").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dashboardTrend-Wallet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$130.00").assertExists()
    }
}
