package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.GamesScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.MiniFeed
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
        screen(dark = dark) {
            // A Surface, like the app's own Scaffold/sheet, so text gets the theme's content color.
            Surface(color = MaterialTheme.colorScheme.background) { content() }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    /** Sets the content on [SampleScan]'s clock, so price ages read the same on any day. */
    private fun screen(dark: Boolean = true, now: Long = SampleScan.NOW, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { now }) { VigilantTheme(darkTheme = dark) { content() } }
        }
    }

    @Test fun feed() = shoot("1_feed") { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }) }

    @Test fun feedLight() = shoot("1b_feed_light", dark = false) { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }) }

    @Test fun feedBeforeFirstScan() = shoot("1c_feed_before_scan") { FeedScreen(SampleScan.fresh(), {}, {}, {}, { _, _ -> }) }

    @Test fun feedScanning() = shoot("1d_feed_scanning") { FeedScreen(SampleScan.scanning(), {}, {}, {}, { _, _ -> }) }

    @Test fun feedStreaming() {
        val s = SampleScan.streaming()
        shoot("1f_feed_streaming") { FeedScreen(s, {}, {}, {}, { _, _ -> }) }
        // Results show while the scan runs, marked as a running count, with no "prices are old" banner.
        compose.onNodeWithText("checked so far", substring = true).assertExists()
        compose.onNodeWithText("Fair odds 3/5 · Novig prices 40/120", substring = true).assertExists()
        compose.onAllNodesWithText("Recheck them", substring = true).assertCountEquals(0)
    }

    @Test fun feedScanningBeforeFirstPrices() {
        shoot("1g_feed_scanning_empty") { FeedScreen(SampleScan.scanning(), {}, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("Bets appear here as Novig's prices come in", substring = true).assertExists()
    }

    @Test fun feedNoFairMatch() = shoot("1e_feed_no_fair") { FeedScreen(SampleScan.state(withFair = false), {}, {}, {}, { _, _ -> }) }

    @Test fun detail() {
        val s = SampleScan.state()
        shoot("2_detail") { OpportunityDetail(s.feed.first(), s.settings) {} }
    }

    @Test fun games() = shoot("3_games") { GamesScreen(SampleScan.state(), {}, {}) }

    @Test fun gamesBeforeFirstScan() = shoot("3b_games_before_scan") { GamesScreen(SampleScan.fresh(), {}, {}) }

    @Test fun tracker() = shoot("4_tracker") { TrackerScreen(SampleScan.state(), { _, _ -> }, {}) }

    @Config(qualifiers = "w393dp-h4400dp-xxhdpi")
    @Test fun settings() = shoot("5_settings") { SettingsScreen(SampleScan.state(), {}) }

    @Test fun settingsOfferSportsbookPropsWithTheirCreditBudget() {
        screen { SettingsScreen(SampleScan.state(), {}) }
        compose.onNodeWithText("Sportsbook player props").assertExists()
        compose.onNodeWithText("Most credits per scan on props").assertExists()
        compose.onNodeWithText("up to 6 games a scan", substring = true).assertExists()
    }

    @Config(qualifiers = "w393dp-h1300dp-xxhdpi")
    @Test fun usageMeters() = shoot("5b_usage_meters") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
            com.tjshea.vigilant.app.ui.UsageSection(SampleScan.state())
        }
    }

    @Test fun theMetersShowWhatsLeftPerKeyAndWhichKeyIsInUse() {
        screen { com.tjshea.vigilant.app.ui.UsageSection(SampleScan.state()) }
        compose.onNodeWithText("688 credits left", substring = true).assertIsDisplayed()
        // Key 1 of each keyed provider is the one the next call uses.
        compose.onAllNodesWithText("in use").assertCountEquals(2)
        compose.onNodeWithText("next").assertIsDisplayed()
        compose.onNodeWithText("142 requests today").assertIsDisplayed()
    }

    @Test fun tappingACardOpensItsDetailWithTheBookBreakdown() {
        val s = SampleScan.state()
        screen { FeedScreen(s, {}, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("Dallas Cowboys").performClick()
        compose.onNodeWithText("FAIR ODDS: BLEND · POWER").assertIsDisplayed()
        compose.onNodeWithText("Track").assertIsDisplayed()
    }

    @Test fun beforeTheFirstScanTheFeedAsksForOneAndTheButtonScans() {
        var scans = 0
        screen { FeedScreen(SampleScan.fresh(), { scans++ }, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("Tap Scan to find +EV bets").assertIsDisplayed()
        compose.onNodeWithText("Not scanned yet").assertIsDisplayed()
        compose.onNodeWithText("Scan now").performClick()
        assert(scans == 1) { "Scan now should start exactly one scan, got $scans" }
    }

    @Test fun whileScanningTheButtonIsBusyAndProgressShows() {
        var scans = 0
        screen { FeedScreen(SampleScan.scanning(), { scans++ }, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("Novig prices 9/24").assertIsDisplayed()
        compose.onNodeWithText("Scanning…").assertIsDisplayed()
        assert(scans == 0)
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun novigKeySetup() = shoot("6_novig_key_setup") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
            com.tjshea.vigilant.app.ui.NovigKeySection(NovigUi(), { _, _ -> }, {}, {})
        }
    }

    @Test fun novigKeyConnected() = shoot("6b_novig_key_connected") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
            com.tjshea.vigilant.app.ui.NovigKeySection(
                NovigUi(
                    connection = com.tjshea.vigilant.data.novig.signing.NovigConnection("3f2504e0-4f89-11d3-9a0c-0305e82c9a1b", "a", "t", false),
                    message = "Novig accepted the key (signature, clock and network all OK).",
                ),
                { _, _ -> }, {}, {},
            )
        }
    }

    @Test fun oldPricesWarnBeforeBettingAndOfferAQuickRecheck() {
        var rechecked: Collection<String>? = null
        screen(now = SampleScan.NOW + 25 * 60_000L) { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }, onRecheck = { rechecked = it }) }
        compose.onNodeWithText("These prices are up to 25m old. Recheck them", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("old price", substring = true).onFirst().assertIsDisplayed()
        compose.onNodeWithText("Recheck", useUnmergedTree = true).performClick()
        assert(rechecked == SampleScan.state().feed.map { it.market.marketId }.distinct()) { "rechecked $rechecked" }
    }

    @Test fun freshPricesHaveNoWarningAndTheFeedCanStillBeRechecked() {
        var rechecked: Collection<String>? = null
        screen { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }, onRecheck = { rechecked = it }) }
        compose.onAllNodesWithText("Recheck them", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("old price", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Recheck prices").performClick()
        assert(rechecked!!.isNotEmpty())
    }

    @Config(qualifiers = "w393dp-h2000dp-xxhdpi")
    @Test fun detailMaker() {
        val s = SampleScan.state()
        // A line that isn't +EV to take: the sheet suggests a maker bid instead.
        val o = s.result!!.opportunities.first { it.quote != null && it.makerBid(0.02) != null && it.fair?.perBook?.any { b -> b.book.bookKey == "pinnacle" } == true }
        shoot("2b_detail_maker") { OpportunityDetail(o, s.settings, onRecheck = {}) {} }
        compose.onNodeWithText("OR POST A BID (MAKER)").assertExists()
        compose.onNodeWithText("Bid up to").assertExists()
        compose.onNodeWithText("Double-check on CrazyNinjaOdds (Pinnacle)").assertExists()
        compose.onNodeWithText("Recheck price").assertExists()
        compose.onNodeWithText("Novig price read just now", substring = true).assertExists()
    }

    @Test fun aBetAlreadyCheapToTakeGetsNoMakerSuggestion() {
        val s = SampleScan.state()
        screen { OpportunityDetail(s.feed.first(), s.settings) {} }
        compose.onAllNodesWithText("OR POST A BID (MAKER)").assertCountEquals(0)
    }

    @Config(qualifiers = "w393dp-h4400dp-xxhdpi")
    @Test fun settingsOfferTheOutlierGuardAndAnOddsCap() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleScan.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Outlier guard").assertExists()
        compose.onNodeWithText("Longest odds shown: +1000").assertExists()
        compose.onNodeWithText("Any").performClick()
        assert(picked?.maxOdds == 0) { "picked $picked" }
    }

    @Test fun theFeedCanBeSortedBySoonest() {
        var picked: com.tjshea.vigilant.data.scanner.FeedSort? = null
        screen { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }, onSort = { picked = it }) }
        compose.onNodeWithText("Soonest").performClick()
        assert(picked == com.tjshea.vigilant.data.scanner.FeedSort.START)
    }

    // ---- The mini window (picture-in-picture over Novig), at the sizes Android gives it ----

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindow() = shoot("7_mini_window") { MiniFeed(SampleScan.state(), next = 0) }

    /** About the size Android opens a 3:2 picture-in-picture window at on a phone. */
    @Config(qualifiers = "w180dp-h120dp-xxhdpi")
    @Test fun miniWindowSmall() {
        val s = SampleScan.state()
        shoot("7a_mini_window_small") { MiniFeed(s, next = 0) }
        compose.onNodeWithText("${s.feed.size} +EV").assertIsDisplayed()
        compose.onNodeWithText(s.feed.first().selection).assertIsDisplayed()
        compose.onNodeWithText("/${s.feed.size}", substring = true).assertIsDisplayed() // more than fit: a page label
    }

    @Config(qualifiers = "w180dp-h120dp-xxhdpi")
    @Test fun miniWindowNextShowsTheNextPage() {
        val s = SampleScan.state()
        screen { MiniFeed(s, next = 1) }
        compose.onAllNodesWithText(s.feed.first().selection).assertCountEquals(0)
    }

    @Config(qualifiers = "w360dp-h240dp-xxhdpi")
    @Test fun miniWindowEnlarged() = shoot("7b_mini_window_large") { MiniFeed(SampleScan.streaming(), next = 0) }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowBeforeAnyScan() {
        shoot("7c_mini_window_empty") { MiniFeed(SampleScan.fresh(), next = 0) }
        compose.onNodeWithText("Tap the window, then Scan").assertIsDisplayed()
    }

    @Test fun theFeedHasAMiniWindowButtonWhenThePhoneSupportsIt() {
        var opened = 0
        screen { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }, onMiniWindow = { opened++ }) }
        compose.onNodeWithContentDescription("Mini window over Novig").performClick()
        assert(opened == 1)
    }

    @Config(qualifiers = "w393dp-h4400dp-xxhdpi")
    @Test fun settingsOfferTheMiniWindowSwitch() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleScan.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Float over Novig").performClick()
        assert(picked?.miniWindow == false) { "picked $picked" }
    }

    // ---- CrazyNinjaOdds' list (RESEARCH.md §18): its tab, and in the mini window ----

    @Test fun cnoTab() {
        shoot("8_cno") { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), {}, {}) }
        compose.onNodeWithText("Walker Buehler Over 15.5").assertIsDisplayed()
        compose.onNodeWithText("Novig · 3+ books", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Odds 49s old · every 1 min", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("\$88.00").onFirst().assertIsDisplayed() // dollars available
    }

    @Test fun cnoTabLight() = shoot("8b_cno_light", dark = false) { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), {}, {}) }

    @Test fun cnoTabFirstRead() {
        shoot("8c_cno_reading") { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(cno = com.tjshea.vigilant.data.cno.CnoState(refreshing = true)), {}, {}) }
        compose.onNodeWithText("Reading CrazyNinjaOdds…").assertIsDisplayed()
    }

    @Test fun cnoTabKeepsTheLastListThroughAnError() {
        val cno = com.tjshea.vigilant.data.cno.CnoState(snapshot = SampleCno.snapshot(readAgoMs = 180_000), error = "CrazyNinjaOdds answered HTTP 503")
        shoot("8d_cno_error") { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(cno = cno), {}, {}) }
        compose.onNodeWithText("Showing the list from 3m ago", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Walker Buehler Over 15.5").assertIsDisplayed()
    }

    @Test fun cnoTabOff() {
        val s = SampleCno.state()
        screen { com.tjshea.vigilant.app.ui.CnoScreen(s.copy(settings = s.settings.copy(cnoEnabled = false)), {}, {}) }
        compose.onNodeWithText("CrazyNinjaOdds is off").assertIsDisplayed()
        compose.onAllNodesWithText("Walker Buehler Over 15.5").assertCountEquals(0)
    }

    @Test fun cnoTabRefreshButton() {
        var refreshed = 0
        screen { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), { refreshed++ }, {}) }
        compose.onNodeWithContentDescription("Refresh CrazyNinjaOdds").performClick()
        assert(refreshed == 1)
    }

    @Test fun cnoSheet() {
        screen { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), {}, {}) }
        compose.onNodeWithText("Justin Jefferson Under 69.5").performClick()
        compose.onNodeWithText("Every book on CNO").assertExists()
        compose.onNodeWithText("check them in Novig before betting", substring = true).assertExists()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowWithBothLists() {
        val s = SampleCno.state()
        shoot("7d_mini_window_both") { MiniFeed(s, next = 0) }
        compose.onNodeWithText("${s.feed.size + SampleCno.rows.size} +EV").assertIsDisplayed()
        compose.onAllNodesWithText("CNO", substring = true).onFirst().assertExists()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowWithCnoOnly() {
        val base = SampleCno.state()
        val s = base.copy(settings = base.settings.copy(miniSource = com.tjshea.vigilant.data.scanner.MiniSource.CNO))
        shoot("7e_mini_window_cno") { MiniFeed(s, next = 0) }
        compose.onNodeWithText("Walker Buehler Over 15.5").assertIsDisplayed()
        compose.onNodeWithText("CNO 49s", substring = true).assertIsDisplayed()
    }

    @Config(qualifiers = "w393dp-h4400dp-xxhdpi")
    @Test fun settingsOfferTheCnoListAndLink() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleScan.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Shared View link").assertExists()
        compose.onNodeWithText("The mini window lists").assertExists()
        compose.onNodeWithText("Tap only").assertExists()
        compose.onNodeWithText("CrazyNinjaOdds' +EV list").performClick()
        assert(picked?.cnoEnabled == false) { "picked $picked" }
    }
}
