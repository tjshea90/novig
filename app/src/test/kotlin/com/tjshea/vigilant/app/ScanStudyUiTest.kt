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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ReportActions
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-03: "The new feature will be a button in the settings in the diagnosis section that can output a file to Claude just like the other diagnosis buttons."
 * The button, what the log holds, and its switch, in Settings › Diagnostics & about.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class ScanStudyUiTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `the Diagnostics section has Share scan study with Claude beside Share with Claude, what is logged, and a switch for the logging`() {
        var studied = 0
        var shared = 0
        var shown = 0
        var ui by androidx.compose.runtime.mutableStateOf(SampleScan.state().copy(studyNote = "1,234 bets logged over 6 days · 812 graded · 640 with a closing line · 4.1 MB"))
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        ui, { f -> ui = ui.copy(settings = f(ui.settings)) }, page = SettingsPage.HELP,
                        reportActions = ReportActions(onShare = { shared++ }, onShareStudy = { studied++ }, onStudyShown = { shown++ }),
                    )
                }
            }
        }
        // Both buttons are there, and each asks for its own file.
        compose.onNodeWithTag("shareDiagnostics").performScrollTo().assertTextContains("Share with Claude", substring = true)
        compose.onNodeWithTag("shareScanStudy").performScrollTo().assertTextContains("Share scan study with Claude", substring = true)
        compose.onNodeWithTag("shareScanStudy").performClick()
        assertEquals(1, studied)
        assertEquals(0, shared)
        // The page asks for the counts when it opens, and shows them.
        assertEquals(1, shown)
        compose.onNodeWithTag("scanStudyNote").performScrollTo().assertTextContains("1,234 bets logged over 6 days", substring = true)
        // What it does, in words.
        compose.onNodeWithText("Scan study: the app logs every bet a CNO or Vigilant scan lists as +EV", substring = true).assertExists()
        compose.onNodeWithText("analyze every bet for the patterns that beat the close and profit", substring = true).assertExists()
        // The switch: on by default, and Tj can turn the logging off.
        assertTrue(ui.settings.scanStudy)
        compose.onNodeWithTag("scanStudySwitch").performScrollTo().performClick()
        assertFalse(ui.settings.scanStudy)
        compose.onNodeWithTag("scanStudySwitch").performClick()
        assertTrue(ui.settings.scanStudy)
    }

    @Test
    fun `the study's words say what is logged, in plain numbers`() {
        val now = 1_800_000_000_000L
        assertEquals(
            "Nothing logged yet: from now on every bet a scan lists is logged here.",
            StudyText.note(com.tjshea.vigilant.data.study.ScanStudy.Overview(0, 0, 0, 0, 0, null, 0), now),
        )
        val note = StudyText.note(com.tjshea.vigilant.data.study.ScanStudy.Overview(6, 1234, 812, 640, 4_300_000L, now - 180_000L, 20), now)
        assertEquals("1,234 bets logged over 6 days · 812 graded · 640 with a closing line · last logged 3m ago · 4.1 MB", note)
        assertEquals("1 bets logged over 1 day · 0 graded · 0 with a closing line · 2 KB", StudyText.note(com.tjshea.vigilant.data.study.ScanStudy.Overview(1, 1, 0, 0, 2_100L, null, 1), now))
    }

    @Test
    fun `the hidden-bets switch is on by default, separate from the logging switch, and says the app's list stays as it is`() {
        var ui by androidx.compose.runtime.mutableStateOf(SampleScan.state())
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(ui, { f -> ui = ui.copy(settings = f(ui.settings)) }, page = SettingsPage.HELP, reportActions = ReportActions())
                }
            }
        }
        compose.onNodeWithText("Also log what your CNO filters hide").performScrollTo().assertExists()
        compose.onNodeWithText("The app's list, alerts, auto-bet and widget don't change", substring = true).assertExists()
        assertTrue(ui.settings.scanStudyHidden)
        compose.onNodeWithTag("scanStudyHiddenSwitch").performScrollTo().performClick()
        assertFalse(ui.settings.scanStudyHidden)
        assertTrue("the logging switch is its own", ui.settings.scanStudy)
        compose.onNodeWithTag("scanStudyHiddenSwitch").performClick()
        assertTrue(ui.settings.scanStudyHidden)
    }

    @Test
    fun `the wide read's line says what it read against the app's list, what CNO was asked, and what went wrong`() {
        val now = 1_800_000_000_000L
        fun row(i: Int) = com.tjshea.vigilant.data.cno.CnoRow(0.05, event = "E$i", market = "M", bet = "B", odds = 110, book = "Novig", gameUrl = "https://x/g?side_id=$i")
        val appList = com.tjshea.vigilant.data.cno.CnoSnapshot("u", (1..3).map(::row), now - 8_000L)
        val wide = com.tjshea.vigilant.data.cno.CnoSnapshot("u", (1..10).map(::row), now - 5_000L, wide = true, asked = "TextBoxMinimumEVPercentage=0%", limit = 1000)
        val ok = com.tjshea.vigilant.data.cno.CnoWideState(snapshot = wide, reads = 7)
        assertEquals(
            "10 rows read ${com.tjshea.vigilant.app.ui.Format.age(wide.fetchedAtMs, now)} (3 also in the app's list of 3; 7 only the wide read found), asked for up to 1000 · 7 good reads since the app opened · form as posted: TextBoxMinimumEVPercentage=0%",
            StudyText.wideNote(ok, appList, true, now),
        )
        // A list of another view says nothing about this read's rows.
        assertFalse(StudyText.wideNote(ok, appList.copy(url = "other"), true, now).contains("also in the app's list"))
        // As many rows as asked for: CNO may have more.
        assertTrue(StudyText.wideNote(ok.copy(snapshot = wide.copy(limit = 10)), null, true, now).contains("AS MANY AS ASKED FOR, so CNO may have more"))
        // No read yet, with what went wrong.
        assertEquals(
            "no read yet since the app opened · last problem: CrazyNinjaOdds: Maximum result count is 500 (2 in a row; asking for up to 500 rows)",
            StudyText.wideNote(com.tjshea.vigilant.data.cno.CnoWideState(error = "CrazyNinjaOdds: Maximum result count is 500", errors = 2, rowsAsked = 500), null, true, now),
        )
        assertTrue(StudyText.wideNote(ok, null, false, now).startsWith("off (Settings"))
    }
}
