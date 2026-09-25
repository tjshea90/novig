package com.tjshea.vigilant.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.GamesScreen
import com.tjshea.vigilant.app.ui.OpportunityDetail
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen with [SampleScan] at a Moto G-class size (393x851dp, xxhdpi). Always
 * runs as a crash test; `./gradlew :app:testDebugUnitTest -Pscreenshots` also writes PNGs to
 * app/screenshots/ for a visual check (there's no device or emulator in this dev container).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean = true, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            VigilantTheme(darkTheme = dark) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("app/screenshots/$name.png")
    }

    @Test fun feed() = shoot("1_feed") { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }) }

    @Test fun feedLight() = shoot("1b_feed_light", dark = false) { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }) }

    @Test fun feedNoKey() = shoot("1c_feed_no_key") { FeedScreen(SampleScan.state(withKey = false), {}, {}, {}, { _, _ -> }) }

    @Test fun detail() {
        val s = SampleScan.state()
        shoot("2_detail") { OpportunityDetail(s.feed.first(), s.settings) {} }
    }

    @Test fun games() = shoot("3_games") { GamesScreen(SampleScan.state(), {}, {}) }

    @Test fun tracker() = shoot("4_tracker") { TrackerScreen(SampleScan.state(), { _, _ -> }, {}) }

    @Config(qualifiers = "w393dp-h2200dp-xxhdpi")
    @Test fun settings() = shoot("5_settings") { SettingsScreen(SampleScan.state(), {}, {}, {}) }
}
