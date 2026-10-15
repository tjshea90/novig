@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.cloud.CloudStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-14/15: pull to refresh scans on GitHub, "show me its scanning progress and some type of confirmation that the scan is current ... a stat which shows how current the odds are, but it must be accurate".
 * The Feed's title carries the measured freshness of the bets shown, its cloud bar the engine's progress for the scan asked for, and the Settings page says why a quiet engine is quiet.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class CloudUiTest {
    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun cloudState(bids: Boolean = false, status: CloudStatus? = alive(), requested: Long = 0L): UiState {
        val base = SampleScan.state()
        val s = base.settings.copy(maker = bids, makerRecommend = false, gates = base.settings.gates.copy(cloudScan = true))
        return base.copy(settings = s, cloudUi = CloudLink.Ui(hasToken = true, tokenTail = "wxyz", status = status, requestedAtMs = requested, pushedRev = 10, pushedAtMs = now - 3_000L))
    }

    private fun alive() = CloudStatus(
        heartbeatMs = now - 3_000, computedAtMs = now - 15_000, rows = 12, configRev = 10, pricedConfigRev = 10, scanning = true, scanStartedAtMs = now - 90_000,
        progressStep = "Novig prices", progressDone = 300, progressTotal = 1200, ackedRefreshAtMs = 0,
    )

    private fun feed(state: UiState) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) { VigilantTheme(darkTheme = true) { FeedScreen(state, {}, {}, {}, { _, _ -> }) } }
    }

    @Test
    fun `the title carries the measured freshness of the bets shown and a tap says how it was worked out`() {
        feed(cloudState())
        compose.onNodeWithTag("cloudFreshness").assertIsDisplayed()
        assertTrue("the sample bets' Novig prices were read 4 s before the clock", compose.onAllNodesWithTag("cloudFreshness").fetchSemanticsNodes().isNotEmpty())
        assertEquals("the detail is closed until tapped", 0, compose.onAllNodesWithTag("cloudFreshnessDetail").fetchSemanticsNodes().size)
        compose.onNodeWithTag("cloudFreshness").performClick()
        compose.onNodeWithTag("cloudFreshnessDetail").assertIsDisplayed()
    }

    @Test
    fun `a scan asked for shows the engine's progress and the acknowledgement, and the old list is not called the answer`() {
        val requested = now - 20_000
        feed(cloudState(status = alive().copy(ackedRefreshAtMs = requested, lastScanForRefreshAtMs = 0L), requested = requested))
        compose.onNodeWithTag("cloudBar").assertIsDisplayed()
        val text = compose.onNodeWithTag("cloudBar").fetchSemanticsNode().config.toString()
        assertTrue(text, text.contains("Scanning on GitHub: Novig prices 300/1200 (25%)"))
        assertTrue(text, text.contains("acknowledged"))
    }

    @Test
    fun `with no cloud engine driving the scan there is no cloud bar and no freshness chip`() {
        val base = SampleScan.state()
        feed(base)
        assertEquals(0, compose.onAllNodesWithTag("cloudBar").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("cloudFreshness").fetchSemanticsNodes().size)
    }

    @Test
    fun `bids that need every priced line pause the cloud scan and the Feed says why`() {
        feed(cloudState(bids = true))
        compose.onNodeWithTag("cloudPaused").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("cloudFreshness").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("cloudBar").fetchSemanticsNodes().size)
    }

    @Test
    fun `a settings change GitHub has not applied yet is said on the Feed`() {
        feed(cloudState(status = alive().copy(configRev = 10, settingsPending = true)))
        compose.onNodeWithTag("cloudSync").assertIsDisplayed()
    }

    private fun settings(state: UiState) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) { VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { SettingsScreen(state, {}, page = SettingsPage.CLOUD) } } }
    }

    @Test
    fun `the Cloud page says the engine's state and whether the settings were applied, and why it is paused`() {
        settings(cloudState())
        compose.onNodeWithTag("cloudLine").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloudSettingsSync").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloudSendKeys").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("cloudSaveSwitch").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the Cloud page with bids on says the phone scans for itself`() {
        settings(cloudState(bids = true))
        compose.onNodeWithTag("cloudPausedNote").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("cloudLine").fetchSemanticsNodes().size)
    }
}
