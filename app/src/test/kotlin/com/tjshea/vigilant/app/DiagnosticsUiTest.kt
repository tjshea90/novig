package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ReportActions
import com.tjshea.vigilant.app.ui.ReportDialog
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.SettingsTab
import com.tjshea.vigilant.app.ui.VigilantTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tj, 2026-10-02: the diagnostics file goes out through Android's share sheet: the button in Settings › Tools, and the one on the Diagnostics page. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1400dp-xxhdpi")
class DiagnosticsUiTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `Settings Tools has Share with Claude, which asks for the file, beside Show report`() {
        var shared = 0
        var shown = 0
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(SampleScan.state(), {}, startTab = SettingsTab.TOOLS, reportActions = ReportActions(onDiagnostics = { shown++ }, onShare = { shared++ }))
                }
            }
        }
        compose.onNodeWithText("Tap Share with Claude: it makes one file with everything the app has recorded", substring = true).assertExists()
        compose.onNodeWithText("It never has a key in it.", substring = true).assertExists()
        compose.onNodeWithTag("shareDiagnostics").performScrollTo().performClick()
        assertEquals(1, shared)
        compose.onNodeWithText("Show report").performClick()
        assertEquals(1, shown)
        assertEquals(1, shared)
    }

    @Test
    fun `the Diagnostics page offers Share with Claude and Copy, other reports only Copy`() {
        var shared = 0
        var copied = 0
        fun show(report: ReportUi) = compose.setContent {
            VigilantTheme(darkTheme = true) { ReportDialog(report, ReportActions(onShare = { shared++ }, onCopied = { copied++ })) }
        }
        show(ReportUi("Diagnostics", "VIGILANT DIAGNOSTICS FILE …"))
        compose.onNodeWithTag("reportShare").performClick()
        assertEquals(1, shared)
        compose.onNodeWithText("Copy").performClick()
        assertEquals(1, copied)
    }

    @Test
    fun `a report that isn't the Diagnostics has no share button`() {
        compose.setContent { VigilantTheme(darkTheme = true) { ReportDialog(ReportUi("Grading check", "text"), ReportActions()) } }
        compose.onAllNodesWithTag("reportShare").assertCountEquals(0)
        compose.onNodeWithText("Copy").assertExists()
    }
}
