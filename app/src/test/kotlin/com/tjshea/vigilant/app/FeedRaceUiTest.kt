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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.ReportActions
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.live.FeedRace
import com.tjshea.vigilant.data.live.FeedRaceRunner
import com.tjshea.vigilant.data.live.FeedRaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-07: "I want to be able to test all available sources that can be used as a rapid source of odds or scores. This will be implemented in the app for live betting."
 * The live feed test's switch (off by default), its line and Share button in Settings › Diagnostics & about, its words, and that it cannot place an order.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class FeedRaceUiTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `the Diagnostics section has the live feed test - off by default, a switch, its line and a share button`() {
        var shared = 0
        var shown = 0
        var ui by androidx.compose.runtime.mutableStateOf(SampleScan.state().copy(feedRaceNote = "Running for 12 min: 3 live games on Novig (tennis), 40 score readings, 210 Novig trades, 0 odds ticks, 88 requests."))
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        ui, { f -> ui = ui.copy(settings = f(ui.settings)) }, page = SettingsPage.RESEARCH,
                        reportActions = ReportActions(onShareFeedRace = { shared++ }, onFeedRaceShown = { shown++ }),
                    )
                }
            }
        }
        assertFalse("off by default: it opens sockets and polls other companies' sites", ui.settings.feedRace)
        compose.onNodeWithTag("feedRaceSwitch").performScrollTo().performClick()
        assertTrue(ui.settings.feedRace)
        compose.onNodeWithTag("feedRaceNote").performScrollTo().assertTextContains("Running for 12 min", substring = true)
        assertTrue("the page asks for the line when it opens", shown >= 1)
        compose.onNodeWithTag("shareFeedRace").performScrollTo().assertTextContains("Share live feed test with Claude", substring = true)
        compose.onNodeWithTag("shareFeedRace").performClick()
        assertEquals(1, shared)
    }

    @Test
    fun `screenshot - Settings, Diagnostics and about, with the live feed test running`() {
        val ui = SampleScan.state().copy(
            settings = SampleScan.state().settings.copy(feedRace = true),
            feedRaceNote = "Running for 12 min: 3 live games on Novig (tennis), 40 score readings, 210 Novig trades, 7 odds ticks, 88 requests.",
        )
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(ui, {}, page = SettingsPage.RESEARCH, reportActions = ReportActions())
                }
            }
        }
        compose.onNodeWithTag("feedRaceNote").performScrollTo().assertTextContains("Running for 12 min", substring = true)
        compose.onRoot().captureRoboImage("screenshots/5q_settings_live_feed_test.png")
    }

    @Test
    fun `the line says what it is doing, its problem and its verdict, and Not running before it ever ran`() {
        assertEquals("Not running.", FeedRaceText.note(FeedRaceStatus(), 0))
        val running = FeedRaceStatus(running = true, sinceMs = 0, liveGames = 1, sports = setOf("tennis"), readings = 40, novigTrades = 210, oddsTicks = 7, requests = 88, verdict = "Live feed test: 12 scores seen live (5 by two or more feeds), 2 moved Novig's moneyline. Too few moves to say whether any feed leads Novig (needs 8).")
        val line = FeedRaceText.note(running, 75 * 60_000L)
        assertTrue(line, line.startsWith("Running for 1 h 15 min: 1 live game on Novig (tennis), 40 score readings, 210 Novig trades, 7 odds ticks, 88 requests."))
        assertTrue(line, line.endsWith("(needs 8)."))
        assertTrue(FeedRaceText.note(running.copy(problem = "Sofascore: HTTP 403"), 0).contains("Problem: Sofascore: HTTP 403."))
        assertTrue(FeedRaceText.note(running.copy(running = false), 0).startsWith("Stopped: 40 score readings, 210 Novig trades this run."))
        assertTrue(FeedRaceText.SWITCH_SUB.contains("no order, ever"))
    }

    @Test
    fun `the feed test is given nothing that could place an order - no trading client, no signed client, no key`() {
        val ctor = FeedRaceRunner::class.java.declaredConstructors.first { it.parameterTypes.size >= 7 }
        val types = ctor.parameterTypes.map { it.name }
        assertFalse(types.toString(), types.any { it.contains("Trading") || it.contains("Signed") || it.contains("Placer") || it.contains("KeyStore") })
        // Its source never names an order route either.
        val dir = java.io.File("../data/src/main/kotlin/com/tjshea/vigilant/data/live")
        val text = dir.listFiles()!!.filter { it.name.startsWith("FeedRace") || it.name.startsWith("FeedParsers") }.joinToString("\n") { it.readText() }
        assertFalse(text.contains("placeOrder") || text.contains("/v3/orders") || text.contains("NovigTradingClient") || text.contains("NovigSignedClient"))
    }

    @Test
    fun `a settings search finds the live feed test`() {
        assertTrue(com.tjshea.vigilant.app.ui.SettingsIndex.entries.any { it.title == "Test live score and odds feeds" })
        assertTrue(com.tjshea.vigilant.app.ui.SettingsIndex.entries.any { it.title == "Share live feed test with Claude" })
        assertEquals(setOf("sofa"), setOf(FeedRace.Sighting("sofa", "1", "A", "B", 0, 0, 0).src))
    }
}
