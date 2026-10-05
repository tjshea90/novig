package com.tjshea.vigilant.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.KillBar
import com.tjshea.vigilant.app.ui.KillBarText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * The kill switch's bar on every tab (Tj, 2026-10-05): red STOP ALL while running, one tap; solid red RESUME once pressed, one tap; and what it says.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class KillBarUiTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `the words say what is on, what is held, and that it stays so until resume`() {
        val none = ScanSettings()
        assertEquals("Scanners on", KillBarText.runningLine(none))
        val on = ScanSettings(autoBet = true, maker = true, autoLock = true, autoScan = AutoScanMode.BOTH)
        assertEquals("Running: auto-bet · bids · auto-lock · background scan", KillBarText.runningLine(on))
        // An auto-bet that stopped itself isn't "running".
        assertEquals("Running: bids", KillBarText.runningLine(ScanSettings(autoBet = true, autoBetHalted = "a lost answer", maker = true)))
        assertEquals("Scanning paused · bids waiting", KillBarText.runningLine(ScanSettings(maker = true, pausedByHand = true)))
        val killed = on.copy(killed = true, killedAtMs = 1_800_000_000_000L)
        val line = KillBarText.stoppedLine(killed, TimeZone.getTimeZone("UTC"))
        assertTrue(line, line.startsWith("STOPPED 8:00 PM") || line.startsWith("STOPPED "))
        assertTrue(line, line.endsWith("nothing scans, bets or bids until you resume"))
        assertEquals("scanning, auto-bet, bids, auto-lock, background scan", KillBarText.resumedList(on))
    }

    @Test
    fun `running shows a red STOP ALL that stops with one tap, and no RESUME`() {
        var killed = 0
        var resumed = 0
        compose.setContent { VigilantTheme { KillBar(ScanSettings(autoBet = true), onKill = { killed++ }, onResume = { resumed++ }) } }
        compose.onNodeWithTag("killBar").assertIsDisplayed()
        compose.onNodeWithText("STOP ALL").assertIsDisplayed()
        compose.onNodeWithTag("killResume").assertDoesNotExist()
        compose.onNodeWithTag("killStatus").assertIsDisplayed()
        compose.onNodeWithTag("killStop").performClick()
        assertEquals(1, killed)
        assertEquals(0, resumed)
    }

    @Test
    fun `stopped shows a solid red bar with RESUME that lifts it with one tap, and no STOP ALL`() {
        var killed = 0
        var resumed = 0
        compose.setContent {
            VigilantTheme { KillBar(ScanSettings(killed = true, killedAtMs = 1_800_000_000_000L), onKill = { killed++ }, onResume = { resumed++ }) }
        }
        compose.onNodeWithText("RESUME").assertIsDisplayed()
        compose.onNodeWithTag("killStop").assertDoesNotExist()
        compose.onNodeWithTag("killResume").performClick()
        assertEquals(1, resumed)
        assertEquals(0, killed)
    }
}
