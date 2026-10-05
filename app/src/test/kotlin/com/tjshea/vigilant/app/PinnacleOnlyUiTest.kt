package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.AutoBetSection
import com.tjshea.vigilant.app.ui.AutoBetText
import com.tjshea.vigilant.app.ui.PinnacleOnlyRows
import com.tjshea.vigilant.app.ui.PinnacleOnlyText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
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

/** The Pinnacle only switch and its age limit, on the Auto-bet tab and as the rows Settings › Scanning shows (Tj, 2026-10-05; RESEARCH.md §88.5). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h3000dp-xxhdpi")
class PinnacleOnlyUiTest {
    @get:Rule val compose = createComposeRule()

    private var ui by mutableStateOf(SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 25.0)))
    private val settings get() = ui.settings

    private fun showAutoBet(configure: (ScanSettings) -> ScanSettings = { it }) {
        ui = SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 25.0), settings = configure(ScanSettings(autoScan = AutoScanMode.CNO)))
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AutoBetSection(ui) { t -> ui = ui.copy(settings = t(ui.settings)) }
                    }
                }
            }
        }
    }

    @Test
    fun `Pinnacle only is off by default, one tap turns it on with its age limit, and the background scan then runs Vigilant's scan`() {
        showAutoBet()
        compose.onNodeWithTag("pinnacleOnlySwitch").performScrollTo().assertIsOff()
        compose.onNodeWithTag("pinnacleOnlyAge").assertDoesNotExist()
        compose.onNodeWithTag("pinnacleOnlySwitch").performClick()
        compose.onNodeWithTag("pinnacleOnlySwitch").assertIsOn()
        assertTrue(settings.pinnacleOnly)
        // CNO's background scan (the default here) becomes Vigilant's: the list isn't read in Pinnacle only.
        assertEquals(AutoScanMode.BOTH, settings.autoScan)
        compose.onNodeWithTag("pinnacleOnlyAge").assertIsDisplayed()
        compose.onNodeWithTag("pinnacleAge90").assertIsSelected()
        compose.onNodeWithTag("pinnacleAge30").performScrollTo().performClick()
        assertEquals(30, settings.pinnacleMaxAgeSeconds)
        compose.onNodeWithTag("pinnacleAge30").assertIsSelected()
        // And off again: the limit chips go, the choice of background scan stays.
        compose.onNodeWithTag("pinnacleOnlySwitch").performScrollTo().performClick()
        assertFalse(settings.pinnacleOnly)
        compose.onNodeWithTag("pinnacleOnlyAge").assertDoesNotExist()
        assertEquals(AutoScanMode.BOTH, settings.autoScan)
    }

    @Test
    fun `with Pinnacle only on the Auto-bet tab says what it bets, says the book-count rules do not apply, and a CNO-only scanner is not what stops it`() {
        showAutoBet { it.copy(pinnacleOnly = true, scanner = ScannerMode.VIGILANT, autoBet = true, autoScan = AutoScanMode.BOTH) }
        compose.onNodeWithTag("autoBetPinnacleNote").performScrollTo().assertIsDisplayed()
        val running = AutoBetText.whyNotRunning(ui)
        assertNull("Vigilant-only scanner is how Pinnacle only runs: nothing to fix there", running)
        assertNull(AutoBetText.fixFor(ui))
        assertTrue(AutoBetText.running(settings).startsWith("Running in Pinnacle only"))
        assertTrue(AutoBetText.confirm(settings, 25.0).contains("beats Pinnacle's devigged price"))
        // Off, the same scanner choice does stop it, as before.
        val off = ui.copy(settings = settings.copy(pinnacleOnly = false))
        assertEquals("The scanner is Vigilant only, so CrazyNinjaOdds is asleep and auto-bet has nothing to read.", AutoBetText.whyNotRunning(off))
        assertEquals(AutoBetText.Fix.SCANNER, AutoBetText.fixFor(off))
    }

    @Test
    fun `the words - the age labels, the explanation that names the feeds, and what turning it on changes`() {
        assertEquals("30 s", PinnacleOnlyText.ageLabel(30))
        assertEquals("1 min 30 s", PinnacleOnlyText.ageLabel(90))
        assertEquals("2 min", PinnacleOnlyText.ageLabel(120))
        val s = ScanSettings(pinnacleOnly = true, autoBet = true, pinnacleMaxAgeSeconds = 60)
        val text = PinnacleOnlyText.explanation(s)
        assertTrue(text, text.contains("PinnWire") && text.contains("pinnapi") && text.contains("PropLine") && text.contains("1 min"))
        assertTrue(PinnacleOnlyText.explanation(s.copy(autoBet = false)).contains("Turn auto-bet on"))
        assertEquals(AutoScanMode.BOTH, PinnacleOnlyText.set(ScanSettings(autoScan = AutoScanMode.CNO), true).autoScan)
        assertEquals(AutoScanMode.OFF, PinnacleOnlyText.set(ScanSettings(autoScan = AutoScanMode.OFF), true).autoScan)
        assertEquals(ScannerMode.VIGILANT, s.scannerNow)
        assertNotNull(PinnacleOnlyText.ageNote(s))
    }
}
