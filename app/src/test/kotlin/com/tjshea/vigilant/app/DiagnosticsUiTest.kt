package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.tjshea.vigilant.app.ui.SettingsPage
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
                    SettingsScreen(SampleScan.state(), {}, page = SettingsPage.HELP, reportActions = ReportActions(onDiagnostics = { shown++ }, onShare = { shared++ }))
                }
            }
        }
        // Claude reads the data from GitHub by itself (Tj, 2026-10-16): no share button, just the report.
        compose.onAllNodesWithTag("shareDiagnostics").assertCountEquals(0)
        compose.onNodeWithText("It never has a key in it.", substring = true).assertExists()
        compose.onNodeWithTag("showDiagnostics").performScrollTo().performClick()
        assertEquals(1, shown)
        assertEquals(0, shared)
    }

    @Test
    fun `the Diagnostics report has Copy and no share button`() {
        var copied = 0
        compose.setContent { VigilantTheme(darkTheme = true) { ReportDialog(ReportUi("Diagnostics", "VIGILANT DIAGNOSTICS FILE …"), ReportActions(onCopied = { copied++ })) } }
        compose.onAllNodesWithTag("reportShare").assertCountEquals(0)
        compose.onNodeWithText("Copy").performClick()
        assertEquals(1, copied)
    }

    @Test
    fun `while the report is still being made, Copy waits`() {
        compose.setContent { VigilantTheme(darkTheme = true) { ReportDialog(ReportUi("Diagnostics", "", busy = true), ReportActions()) } }
        compose.onNodeWithText("Copy").assertIsNotEnabled()
    }

    @Test
    fun `a report that isn't the Diagnostics has no share button`() {
        compose.setContent { VigilantTheme(darkTheme = true) { ReportDialog(ReportUi("Grading check", "text"), ReportActions()) } }
        compose.onAllNodesWithTag("reportShare").assertCountEquals(0)
        compose.onNodeWithText("Copy").assertExists()
    }
}
