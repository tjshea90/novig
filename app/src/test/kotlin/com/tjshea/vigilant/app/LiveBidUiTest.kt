package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.SettingsPage
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.livebid.LiveBidDeskStatus
import com.tjshea.vigilant.data.livebid.LiveBidLimits
import com.tjshea.vigilant.data.livebid.LiveBidPresets
import com.tjshea.vigilant.data.livebid.LiveBidQuality
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod
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
 * Tj, 2026-10-10: "a good slate of options for this setting to fine tune it, including presets I can save and manual fields to type my own numbers". Settings › Live bids: the paper switch, real money
 * behind a confirmation in his own numbers, the presets (built in, applied, saved, deleted), and every number as chips and a box.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h4000dp-xxhdpi")
class LiveBidUiTest {
    @org.junit.Before fun wake() { com.tjshea.vigilant.data.scanner.Dormant.PINNODDS = false }

    @get:Rule val compose = createComposeRule()

    private fun show(initial: UiState): () -> UiState {
        var ui by mutableStateOf(initial)
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(ui, { f -> ui = ui.copy(settings = f(ui.settings)) }, page = SettingsPage.LIVEBIDS)
                }
            }
        }
        return { ui }
    }

    private fun state(settings: ScanSettings = ScanSettings(bankroll = 500.0), keys: List<String> = listOf("abcd1234efgh5678")) =
        SampleScan.state().copy(settings = settings, pinnoddsKeys = keys, liveBidStatus = LiveBidDeskStatus())

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun type(tag: String, text: String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text) }

    // ---- the switches ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `the feed is off by default, paper when on, and real money cannot be switched on first`() {
        val ui = show(state())
        compose.onNodeWithTag("liveBidNote").performScrollTo().assertTextContains("Off", substring = true)
        tap("liveBidRealSwitch")
        assertFalse("real needs the feed on first", ui().settings.liveBidReal)
        tap("liveBidSwitch")
        assertTrue(ui().settings.liveBid)
        assertFalse("the feed alone is paper", ui().settings.liveBidReal)
        compose.onNodeWithTag("liveBidNote").performScrollTo().assertTextContains("paper", substring = true)
    }

    @Test
    fun `real money asks first, in Tj's own numbers, and Cancel leaves it off`() {
        val ui = show(state(ScanSettings(bankroll = 500.0, liveBid = true)))
        tap("liveBidRealSwitch")
        compose.onNodeWithTag("liveBidRealCancel").assertIsDisplayed()
        tap("liveBidRealCancel")
        assertFalse(ui().settings.liveBidReal)
        tap("liveBidRealSwitch")
        tap("liveBidRealConfirm")
        assertTrue(ui().settings.liveBidReal)
        compose.onNodeWithTag("liveBidNote").performScrollTo().assertTextContains("REAL", substring = true)
        // And off again with no question.
        tap("liveBidRealSwitch")
        assertFalse(ui().settings.liveBidReal)
    }

    @Test
    fun `the confirmation names the stake rule, the caps and the unknown`() {
        val text = LiveBidText.confirm(ScanSettings(bankroll = 500.0, liveBidLimits = LiveBidLimits(maxStake = 4.0, maxBids = 6, maxPerGame = 8.0, maxPerDay = 30.0, haltLoss = 12.0)))
        assertTrue(text, text.contains("⅛ Kelly"))
        assertTrue(text, text.contains("\$500"))
        assertTrue(text, text.contains("at most 6 up at once"))
        assertTrue(text, text.contains("\$8 a game"))
        assertTrue(text, text.contains("\$30 a day"))
        assertTrue(text, text.contains("lose \$12"))
        assertTrue("it says it is untested in play", text.contains("No post-only bid has been sent to Novig in play yet"))
        assertTrue(text, text.contains("5% under Pinnacle's price"))
    }

    @Test
    fun `a halt shows its reason and Resume lifts it`() {
        val ui = show(state(ScanSettings(liveBid = true, liveBidHalted = "3 of the last 5 live bid fills were picked off")))
        compose.onNodeWithTag("liveBidHalted").performScrollTo().assertTextContains("picked off", substring = true)
        tap("liveBidResume")
        assertNull(ui().settings.liveBidHalted)
    }

    @Test
    fun `no Pinnodds key is said plainly`() {
        show(state(keys = emptyList()))
        compose.onNodeWithTag("liveBidNoKey").performScrollTo().assertIsDisplayed()
    }

    // ---- presets ----------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `three built-in presets are listed, Balanced is in force on a fresh install, and Apply puts Careful's rules in force`() {
        val ui = show(state())
        for (p in LiveBidPresets.BUILT_IN) compose.onNodeWithTag("liveBidPreset:${p.name}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("liveBidPresetInForce").performScrollTo().assertTextContains("In force: Balanced")
        compose.onNodeWithTag("liveBidPresetActive:Balanced").assertExists()
        tap("liveBidPresetApply:Careful")
        assertEquals(LiveBidPresets.CAREFUL.quality, ui().settings.liveBidQuality)
        compose.onNodeWithTag("liveBidPresetInForce").performScrollTo().assertTextContains("In force: Careful")
    }

    @Test
    fun `a preset never changes his money`() {
        val mine = LiveBidLimits(stakeMode = AutoBetStake.QUARTER_KELLY, maxStake = 8.0, maxPerDay = 77.0)
        val ui = show(state(ScanSettings(bankroll = 500.0, liveBidLimits = mine)))
        tap("liveBidPresetApply:Careful")
        assertEquals(mine, ui().settings.liveBidLimits)
    }

    @Test
    fun `the paper-only preset turns real money off and keeps it off`() {
        val ui = show(state(ScanSettings(bankroll = 500.0, liveBid = true, liveBidReal = true)))
        tap("liveBidPresetApply:Paper study (wide)")
        assertFalse(ui().settings.liveBidReal)
        assertTrue(ui().settings.liveBid)
        compose.onNodeWithTag("liveBidPaperOnlyNote").performScrollTo().assertIsDisplayed()
        tap("liveBidRealSwitch")
        assertFalse("real stays off under a watching-only preset", ui().settings.liveBidReal)
    }

    @Test
    fun `changing a rule says the preset changed, and saving the rules makes his own preset he can apply and delete`() {
        val ui = show(state())
        type("liveBidMargin-field", "7")
        assertEquals(0.07, ui().settings.liveBidQuality.margin, 1e-9)
        compose.onNodeWithTag("liveBidPresetInForce").performScrollTo().assertTextContains("changed since", substring = true)
        type("liveBidPresetName", "Saturday football")
        tap("liveBidPresetSave")
        assertEquals(listOf("Saturday football"), ui().settings.liveBidPresets.map { it.name })
        assertEquals(0.07, ui().settings.liveBidPresets[0].quality.margin, 1e-9)
        compose.onNodeWithTag("liveBidPresetActive:Saturday football").performScrollTo().assertIsDisplayed()
        tap("liveBidPresetApply:Careful")
        assertEquals(0.06, ui().settings.liveBidQuality.margin, 1e-9)
        tap("liveBidPresetApply:Saturday football")
        assertEquals(0.07, ui().settings.liveBidQuality.margin, 1e-9)
        tap("liveBidPresetDelete:Saturday football")
        assertTrue(ui().settings.liveBidPresets.isEmpty())
    }

    @Test
    fun `a built-in's name cannot be saved over`() {
        show(state())
        type("liveBidPresetName", "Careful")
        compose.onNodeWithTag("liveBidPresetSave").performScrollTo().assertIsNotEnabled()
    }

    // ---- the numbers -----------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun `chips set a value and a box takes any other, and a typed value the field refuses is not saved`() {
        val ui = show(state())
        tap("liveBidMargin-4")   // 6%
        assertEquals(0.06, ui().settings.liveBidQuality.margin, 1e-9)
        type("liveBidMargin-field", "6.5")
        assertEquals(0.065, ui().settings.liveBidQuality.margin, 1e-9)
        type("liveBidMargin-field", "99")
        assertEquals("out of range: not saved", 0.065, ui().settings.liveBidQuality.margin, 1e-9)
        type("liveBidTtl-field", "25")
        assertEquals(25, ui().settings.liveBidQuality.ttlSec)
        type("liveBidTtl-field", "5")
        assertEquals("under the 10 s floor: not saved", 25, ui().settings.liveBidQuality.ttlSec)
    }

    @Test
    fun `the replacement time can never be longer than half the bid's life`() {
        val ui = show(state())
        tap("liveBidTtl-0")   // 10 s
        assertEquals(10, ui().settings.liveBidQuality.ttlSec)
        assertTrue(ui().settings.liveBidQuality.refreshBeforeSec <= 5)
        type("liveBidRefresh-field", "9")
        assertTrue("clamped to half of 10 s", ui().settings.liveBidQuality.refreshBeforeSec <= 5)
    }

    @Test
    fun `the stake rule is an eighth Kelly by default, can be changed, and a custom amount has its own box`() {
        val ui = show(state())
        assertEquals(AutoBetStake.EIGHTH_KELLY, ui().settings.liveBidLimits.stakeMode)
        compose.onNodeWithTag("liveBidTypical").performScrollTo().assertTextContains("A typical bid", substring = true)
        tap("liveBidStake-QUARTER_KELLY")
        assertEquals(AutoBetStake.QUARTER_KELLY, ui().settings.liveBidLimits.stakeMode)
        tap("liveBidStake-CUSTOM")
        type("liveBidCustomStake", "3.5")
        assertEquals(3.5, ui().settings.liveBidLimits.customStake, 1e-9)
    }

    @Test
    fun `the smallest stake never goes over the biggest, and the most per game never over the most bids`() {
        val ui = show(state())
        tap("liveBidMaxStake-1")   // $2
        assertEquals(2.0, ui().settings.liveBidLimits.maxStake, 1e-9)
        tap("liveBidMinStake-4")   // $5: raises the biggest to match
        assertEquals(5.0, ui().settings.liveBidLimits.minStake, 1e-9)
        assertEquals(5.0, ui().settings.liveBidLimits.maxStake, 1e-9)
        tap("liveBidMaxBids-0")    // 1 bid: the per-game count follows it down
        assertEquals(1, ui().settings.liveBidLimits.maxBids)
        assertEquals(1, ui().settings.liveBidLimits.maxBidsPerGame)
    }

    @Test
    fun `leagues are picked one by one, and All clears them`() {
        val ui = show(state())
        tap("liveBidLeague-NCAAF")
        tap("liveBidLeague-NFL")
        assertEquals(setOf("NCAAF", "NFL"), ui().settings.liveBidQuality.onlyLeagues)
        tap("liveBidLeague-NFL")
        assertEquals(setOf("NCAAF"), ui().settings.liveBidQuality.onlyLeagues)
        tap("liveBidLeague-ALL")
        assertTrue(ui().settings.liveBidQuality.onlyLeagues.isEmpty())
    }

    @Test
    fun `the switches and devig chips write their rule`() {
        val ui = show(state())
        tap("liveBidNeverLead"); assertTrue(ui().settings.liveBidQuality.neverLead)
        tap("liveBidBothSides"); assertTrue(ui().settings.liveBidQuality.bothSides)
        tap("liveBidTennis"); assertTrue(ui().settings.liveBidQuality.tennis)
        tap("liveBidMoneyline"); assertFalse(ui().settings.liveBidQuality.moneyline)
        tap("liveBidPullScore"); assertFalse(ui().settings.liveBidQuality.pullOnScore)
        tap("liveBidOverlap"); assertFalse(ui().settings.liveBidQuality.overlapRepost)
        tap("liveBidCredit"); assertTrue(ui().settings.liveBidQuality.countCredit)
        tap("liveBidDevig-POWER"); assertEquals(DevigMethod.POWER, ui().settings.liveBidQuality.devig)
    }

    @Test
    fun `the pick-off stop is chosen as a pair and each half can be typed`() {
        val ui = show(state())
        tap("liveBidPickOff-3")   // 4 of 6
        assertEquals(4, ui().settings.liveBidQuality.pickOffLimit)
        assertEquals(6, ui().settings.liveBidQuality.pickOffWindow)
        tap("liveBidPickOff-0")   // off
        assertEquals(0, ui().settings.liveBidQuality.pickOffLimit)
        type("liveBidPickOffLimit-field".removeSuffix("-field"), "2")
        assertEquals(2, ui().settings.liveBidQuality.pickOffLimit)
    }

    @Test
    fun `the freshness boxes take seconds, dollars and points`() {
        val ui = show(state())
        type("liveBidQuiet-field", "12")
        assertEquals(12, ui().settings.liveBidQuality.maxQuietSec)
        type("liveBidPinnLimit-field", "1500")
        assertEquals(1500.0, ui().settings.liveBidQuality.minPinnLimit, 1e-9)
        type("liveBidBookGap-field", "7")
        assertEquals(0.07, ui().settings.liveBidQuality.maxBookGap, 1e-9)
        type("liveBidScoreHold-field", "45")
        assertEquals(45, ui().settings.liveBidQuality.scoreHoldSec)
        type("liveBidPullEv-field", "1.5")
        assertEquals(0.015, ui().settings.liveBidQuality.pullBelowEv, 1e-9)
        tap("liveBidQuiet-0")   // Off
        assertEquals(0, ui().settings.liveBidQuality.maxQuietSec)
    }

    @Test
    fun `the home list says what Live bids is set to`() {
        assertEquals("Off", SettingsSummaryProbe.of(ScanSettings()))
        assertEquals("On: paper only (nothing is sent), Balanced", SettingsSummaryProbe.of(ScanSettings(liveBid = true)))
        assertEquals("On: real money, Careful", SettingsSummaryProbe.of(LiveBidPresets.apply(ScanSettings(liveBid = true, liveBidReal = true), LiveBidPresets.CAREFUL)))
        assertEquals("Stopped", SettingsSummaryProbe.of(ScanSettings(liveBid = true, liveBidHalted = "x")))
        assertEquals("On: paper only (nothing is sent), Balanced (changed)", SettingsSummaryProbe.of(ScanSettings(liveBid = true, liveBidQuality = LiveBidQuality(margin = 0.07))))
    }
}

private object SettingsSummaryProbe {
    fun of(s: ScanSettings): String = com.tjshea.vigilant.app.ui.SettingsSummary.of(SettingsPage.LIVEBIDS, SampleScan.state().copy(settings = s))
}
