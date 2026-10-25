package com.tjshea.vigilant.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.EngineScreen
import com.tjshea.vigilant.app.ui.RefreshApisButton
import com.tjshea.vigilant.app.ui.VigilantTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PR1 (Tj 2026-10-25): "When I pull to refresh in the engine window, I don't think it needs to read the apis every time, make it refresh only the engine data ... and a separate button on the apis tab to
 * refresh api status and meters". The APIs view has its own button, wired to its own callback; the other views do not show it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class EngineRefreshUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `only the APIs view has the API refresh, and it calls the API refresh alone`() {
        var engine = 0
        var apis = 0
        compose.setContent { VigilantTheme { EngineScreen(UiState(), onOpenCloudSettings = {}, onRefresh = { engine++ }, onRefreshApis = { apis++ }) } }
        compose.onNodeWithTag("refreshApis").assertDoesNotExist()
        compose.onNodeWithTag("engineSection-APIS").performClick()
        compose.onNodeWithText("Refresh API status and meters").assertIsEnabled().performClick()
        assertEquals(1, apis)
        assertEquals(0, engine)
    }

    @Test fun `the button waits while the APIs are being checked`() {
        compose.setContent { VigilantTheme { RefreshApisButton(busy = true, onRefreshApis = {}) } }
        compose.onNodeWithText("Checking every API…").assertIsNotEnabled()
    }
}
