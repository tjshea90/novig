package com.tjshea.vigilant.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.MakerActions
import com.tjshea.vigilant.app.ui.MakerRulesText
import com.tjshea.vigilant.app.ui.MakerScreen
import com.tjshea.vigilant.app.ui.MakerUi
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.BidFocus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2600dp-xxhdpi")
class ScratchLowUsageShotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shot() {
        val settings = SampleScan.settings.copy(maker = true, makerFocus = BidFocus.LOW_USAGE)
        val st = androidx.compose.runtime.mutableStateOf(settings)
        compose.setContent {
            VigilantTheme {
                MakerScreen(
                    MakerUi(settings = st.value, setUp = true, vigilantOn = true, bids = emptyList(), decisions = emptyList(), scanAtMs = SampleScan.NOW, lastPassAtMs = SampleScan.NOW, running = false, problem = null, bets = emptyList(), now = SampleScan.NOW),
                    MakerActions(onUpdate = { f -> st.value = f(st.value) }),
                )
            }
        }
        compose.onNodeWithText(MakerRulesText.summary(settings)).performClick()
        compose.onNodeWithTag("lowUsagePanel").performScrollTo()
        compose.onRoot().captureRoboImage("/tmp/claude-0/-home-user-novig/b58de039-98c7-5df0-8389-d6cfd472501a/scratchpad/lowusage_panel.png")
    }
}
