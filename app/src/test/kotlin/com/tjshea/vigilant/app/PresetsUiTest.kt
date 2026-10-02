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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.PresetsTab
import com.tjshea.vigilant.app.ui.PresetsText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.Presets
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-10-02 17:01Z: "make a preset section in the settings that sets all the settings to ideal settings for volume but safe clv scanning … make it so I
 * can make my own settings presets." Settings › Presets.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PresetsUiTest {

    @get:Rule val compose = createComposeRule()

    private var settings by mutableStateOf(ScanSettings(bankroll = 300.0))

    private fun show() {
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { PresetsTab(settings) { t -> settings = t(settings) } }
                }
            }
        }
    }

    @Test
    fun `the built-ins are listed with why, and one tap applies every rule`() {
        show()
        compose.onNodeWithTag("presetInForce").assertExists()
        compose.onNodeWithText("No preset applied: your own settings.").assertExists()
        compose.onNodeWithText(PresetsText.why(Presets.VOLUME)!!).assertExists()
        compose.onNodeWithText(Presets.VOLUME.rules.summary()).assertExists()
        compose.onNodeWithText(Presets.STRICT.rules.summary()).assertExists()
        compose.onRoot().captureRoboImage("screenshots/5n_settings_presets.png")
        compose.onNodeWithTag("presetApply:Volume + safe CLV").performClick()
        assertEquals(Presets.VOLUME, Presets.active(settings))
        assertEquals(300.0, settings.bankroll, 0.0)
        compose.onNodeWithText("In force: Volume + safe CLV.").assertExists()
        compose.onNodeWithTag("presetActive:Volume + safe CLV").assertExists()
        // Changing a rule elsewhere: the card says it changed and offers Apply again.
        settings = settings.copy(autoBetMinEv = 0.04)
        compose.onNodeWithText("Last applied: Volume + safe CLV, changed since (your own settings now).").assertExists()
        compose.onNodeWithTag("presetApply:Volume + safe CLV").assertExists()
    }

    @Test
    fun `Tj saves his own, applies and deletes it, and can't take a built-in's name`() {
        show()
        compose.onNodeWithTag("presetSave").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("presetName").performTextInput("Strict CLV")
        compose.onNodeWithTag("presetSave").assertIsNotEnabled()
        compose.onNodeWithText("That's a built-in preset's name").assertExists()
        compose.onNodeWithTag("presetName").performTextInput(" mine")
        compose.onNodeWithTag("presetSave").assertIsEnabled().performClick()
        assertEquals(listOf("Strict CLV mine"), settings.presets.map { it.name })
        assertEquals("Strict CLV mine", Presets.active(settings)?.name)
        compose.onNodeWithText("Strict CLV mine (yours)").assertExists()
        // Applying a built-in and then his own brings his rules back.
        val mine = settings.presets.single().rules
        compose.onNodeWithTag("presetApply:Volume + safe CLV").performScrollTo().performClick()
        compose.onNodeWithTag("presetApply:Strict CLV mine").performScrollTo().performClick()
        assertEquals(mine, com.tjshea.vigilant.data.scanner.PresetRules.of(settings))
        compose.onNodeWithTag("presetDelete:Strict CLV mine").performScrollTo().performClick()
        assertTrue(settings.presets.isEmpty())
        assertNull(settings.presetName)
    }
}
