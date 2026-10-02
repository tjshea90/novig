package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.ReportActions
import com.tjshea.vigilant.app.ui.ReportContent
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.VigilantTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Diagnostics (Tj, 2026-09-29): the buttons that produce the copy-able reports, and the report itself. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class ReportUiTest {

    @get:Rule val compose = createComposeRule()

    private fun screen(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
    }

    @Test
    fun `Show report asks for the Diagnostics page, and Grading check is there only with betting set up`() {
        var shown = 0
        var graded = 0
        screen { SettingsScreen(SampleScan.state(), {}, reportActions = ReportActions(onDiagnostics = { shown++ }, onGradingCheck = { graded++ }), page = SettingsPage.HELP) }
        compose.onNodeWithText("Show report").performScrollTo().performClick()
        assertEquals(1, shown)
        compose.onAllNodesWithText("Grading check").assertCountEquals(0)
    }

    @Test
    fun `with betting on the Grading check button runs the ledger check`() {
        var graded = 0
        val s = SampleScan.state().let { it.copy(betting = BettingUi(enabled = true, balance = 12.5)) }
        screen { SettingsScreen(s, {}, reportActions = ReportActions(onGradingCheck = { graded++ }), page = SettingsPage.HELP) }
        compose.onNodeWithText("Grading check").performScrollTo().performClick()
        assertEquals(1, graded)
    }

    @Test
    fun `a report is shown as selectable text, and while it is being made it says so`() {
        val text = Diagnostics.report(SampleScan.state(), Diagnostics.Extras("0.21.3", 49, "test phone"), SampleScan.NOW)
        screen { ReportContent(ReportUi("Diagnostics", text)) }
        compose.onNodeWithTag("reportText").assertExists()
        compose.onNodeWithText("== Settings ==", substring = true).assertExists()
    }

    @Test
    fun `a report still being read shows progress instead of text`() {
        screen { ReportContent(ReportUi("Grading check", "Reading Novig's ledger and positions…", busy = true)) }
        compose.onNodeWithText("Reading Novig's ledger and positions…").assertExists()
    }

    @Test
    fun screenshot() {
        val text = Diagnostics.report(SampleScan.state(), Diagnostics.Extras("0.21.3", 49, "Motorola moto g 2026 · Android 16 (API 36)"), SampleScan.NOW, java.util.TimeZone.getTimeZone("UTC"))
        screen { ReportContent(ReportUi("Diagnostics", text), Modifier.fillMaxSize()) }
        compose.onRoot().captureRoboImage("screenshots/5g_diagnostics.png")
    }
}
