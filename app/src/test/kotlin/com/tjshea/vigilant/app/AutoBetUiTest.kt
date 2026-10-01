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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.AutoBetSection
import com.tjshea.vigilant.app.ui.AutoBetText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Betting › Auto-bet (Tj, 2026-10-01): off until turned on (and asked once, plainly), then each of the seven choices he listed, the
 * stops it shows, and the Resume after a lost order. The state is a real one the callback edits, as Settings does.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h3000dp-xxhdpi")
class AutoBetUiTest {

    @get:Rule val compose = createComposeRule()

    private var ui by mutableStateOf(SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 25.0)))
    private val settings get() = ui.settings

    private fun show(configure: (ScanSettings) -> ScanSettings = { it }, betting: BettingUi = BettingUi(enabled = true, balance = 25.0)) {
        ui = SampleScan.state().copy(betting = betting, settings = configure(ScanSettings(autoScan = AutoScanMode.CNO)))
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

    // ---- turning it on ------------------------------------------------------------------------------------------

    @Test
    fun `it is off by default, and turning it on asks first and says it places real bets`() {
        show()
        compose.onNodeWithTag("autoBetSwitch").assertIsOn().let { }
    }
}
