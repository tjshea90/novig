package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.AutoBetSection
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.BetSlider
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Auto-bet tab's slider and Advanced button (Tj, 2026-10-11): a stop chip sets every bet rule, and the advanced rules are hidden until the button is pressed. */
@RunWith(AndroidJUnit4::class)
class LevelSliderUiTest {
    @get:Rule val compose = createComposeRule()
    private var ui by mutableStateOf(SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 25.0), settings = ScanSettings()))

    @Test fun `a stop chip sets the rules, and the advanced settings are hidden until asked for`() {
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) { AutoBetSection(ui) { t -> ui = ui.copy(settings = t(ui.settings)) } }
            }
        }
        compose.onNodeWithTag("autoBetMinEvField").assertDoesNotExist()
        compose.onNodeWithTag("betLevel-5").performScrollTo().performClick()
        assertEquals(5, ui.settings.vigGuards.betLevel)
        assertTrue(BetSlider.matches(ui.settings, 5))
        compose.onNodeWithTag("autoBetAdvanced").performScrollTo().performClick()
        compose.onNodeWithTag("autoBetMinEvField").performScrollTo()
    }
}
