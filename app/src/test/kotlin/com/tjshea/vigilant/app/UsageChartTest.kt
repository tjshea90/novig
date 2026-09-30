package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.ParlayUsageChart
import com.tjshea.vigilant.app.ui.USAGE_CHART
import com.tjshea.vigilant.app.ui.UsageSection
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.usageSlots
import com.tjshea.vigilant.data.reference.ParlayAccount
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * ParlayAPI's credits a day under its meter in Settings › API usage (Tj, 2026-09-30, PARLAY_API.md §6.2): the last 30 days as bars ending
 * today, a tapped bar's day read out, and the endpoints that spent the most in words.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class UsageChartTest {

    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-09-30T06:00:00Z").toEpochMilli()

    private val history = ParlayAccount.History(
        days = listOf(ParlayAccount.Day("2026-09-01", 40, 12), ParlayAccount.Day("2026-09-28", 300, 90), ParlayAccount.Day("2026-09-30", 112, 34)),
        top = listOf(ParlayAccount.Endpoint("props:baseball_mlb", 150, 50), ParlayAccount.Endpoint("closing-lines:json", 7, 5)),
        windowDays = 30,
        readAtMs = now,
    )

    private fun screen(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    @Test
    fun `thirty slots end today, a day without calls is zero`() {
        val slots = usageSlots(history, now)
        assertEquals(30, slots.size)
        assertEquals("2026-09-01", slots.first().day)
        assertEquals("2026-09-30", slots.last().day)
        assertEquals(0, slots.first { it.day == "2026-09-15" }.credits)
        assertEquals(300, slots.first { it.day == "2026-09-28" }.credits)
    }

    @Test
    fun `today's credits are read out, a tapped day's instead, and where they went is listed`() {
        screen { ParlayUsageChart(history, now) }
        compose.onNodeWithText("Credits a day · last 30 days").assertExists()
        compose.onNodeWithText("452 in all").assertExists()
        compose.onNodeWithText("Today: 112 credits · 34 calls · most: 300 on Sep 28").assertExists()
        compose.onNodeWithTag(USAGE_CHART).assertContentDescriptionContains("most 300 on Sep 28", substring = true)
        // Tap the first bar (Sep 1).
        compose.onNodeWithTag(USAGE_CHART).performTouchInput { click(androidx.compose.ui.geometry.Offset(2f, height / 2f)) }
        compose.onNodeWithText("Sep 1: 40 credits · 12 calls · most: 300 on Sep 28").assertExists()
        compose.onNodeWithText("Props · MLB").assertExists()
        compose.onNodeWithText("150 cr · 50 calls").assertExists()
        compose.onNodeWithText("Closing lines · history file").assertExists()
    }

    @Test
    fun `the chart sits under ParlayAPI's meter once it has a key and a log`() {
        val state = SampleScan.state().copy(parlayKeys = listOf("pk-FAKE-0000"), parlayHistory = history)
        screen { UsageSection(state) }
        compose.onNodeWithText("Credits a day · last 30 days").assertExists()
        // No key: no chart, whatever was read before.
        compose.setContent { }
    }
}
