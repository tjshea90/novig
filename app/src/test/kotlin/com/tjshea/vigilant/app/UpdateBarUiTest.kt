package com.tjshea.vigilant.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.UpdateBarContent
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.update.AppUpdate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "Install update" bar (Tj, 2026-10-16): shown only while a newer release exists; its button installs, or asks for the one-time install permission first. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h800dp-xxhdpi")
class UpdateBarUiTest {
    @get:Rule val compose = createComposeRule()

    private val latest = AppUpdate("v9.9.9", "Vigilant v9.9.9", "notes", "u", "a", 1000, "2026-10-16T00:00:00Z")

    @Test
    fun `no newer release shows nothing, a newer one shows the button and it installs`() {
        var installs = 0
        var state = AppUpdater.State()
        compose.setContent { VigilantTheme(darkTheme = true) { UpdateBarContent(state, { installs++ }, {}) } }
        compose.onNodeWithTag("updateBar").assertDoesNotExist()
        state = AppUpdater.State(latest = latest, available = true)
        compose.setContent { VigilantTheme(darkTheme = true) { UpdateBarContent(state, { installs++ }, {}) } }
        compose.onNodeWithTag("updateInstall").assertIsDisplayed().performClick()
        assertEquals(1, installs)
    }

    @Test
    fun `without the one-time permission the button asks for it instead`() {
        var allowed = 0
        compose.setContent { VigilantTheme(darkTheme = true) { UpdateBarContent(AppUpdater.State(latest = latest, available = true, needsPermission = true), {}, { allowed++ }) } }
        compose.onNodeWithTag("updateInstall").assertDoesNotExist()
        compose.onNodeWithTag("updateAllow").assertIsDisplayed().performClick()
        assertEquals(1, allowed)
    }
}
