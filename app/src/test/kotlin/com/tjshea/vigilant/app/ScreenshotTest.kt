@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.foundation.layout.size
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

    /**
     * The mini window as MainActivity draws it: straight into the theme, no Surface behind it
     * (a Surface here once hid unreadable pick names, 2026-09-26).
     */
    private fun shootAsWindow(name: String, dark: Boolean = true, content: @androidx.compose.runtime.Composable () -> Unit) {
        screen(dark = dark) { content() }
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


    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
    @Test fun settings() = shoot("5_settings") { SettingsScreen(SampleScan.state(), {}) }

    @Test fun settingsOfferSportsbookPropsWithTheirCreditBudget() {
        screen { SettingsScreen(SampleScan.state(), {}) }
        compose.onNodeWithText("Sportsbook player props").assertExists()
        compose.onNodeWithText("Most credits per scan on props").assertExists()
        compose.onNodeWithText("up to 6 games a scan", substring = true).assertExists()
    }

    @Test fun settingsTakePinnWireAndPropLineKeys() {
        screen { SettingsScreen(SampleScan.state(), {}) }
        compose.onNodeWithText("PinnWire keys (game lines and player props)").assertExists()
        compose.onNodeWithText("Add a PinnWire key").assertExists()
        compose.onNodeWithText("PropLine").assertExists()
        compose.onNodeWithText("Add a PropLine key").assertExists()
        compose.onNodeWithText("Sportsbooks for fair odds", substring = true).assertExists()
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
        // Key 1 of each keyed provider (PinnWire, pinnapi, PropLine, The Odds API) is the one the next call uses.
        compose.onAllNodesWithText("in use").assertCountEquals(4)
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

    @Config(qualifiers = "w393dp-h6400dp-xxhdpi")
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
    @Test fun miniWindow() = shootAsWindow("7_mini_window") { MiniFeed(SampleScan.state(), next = 0) }

    /** About the size Android opens a 3:2 picture-in-picture window at on a phone. */
    @Config(qualifiers = "w180dp-h120dp-xxhdpi")
    @Test fun miniWindowSmall() {
        val s = SampleScan.state()
        shootAsWindow("7a_mini_window_small") { MiniFeed(s, next = 0) }
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
    @Test fun miniWindowEnlarged() = shootAsWindow("7b_mini_window_large") { MiniFeed(SampleScan.streaming(), next = 0) }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowBeforeAnyScan() {
        shootAsWindow("7c_mini_window_empty") { MiniFeed(SampleScan.fresh(), next = 0) }
        compose.onNodeWithText("Tap the window, then Scan").assertIsDisplayed()
    }

    @Test fun theFeedHasAMiniWindowButtonWhenThePhoneSupportsIt() {
        var opened = 0
        screen { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }, onMiniWindow = { opened++ }) }
        compose.onNodeWithContentDescription("Mini window over Novig").performClick()
        assert(opened == 1)
    }

    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
    @Test fun settingsOfferTheMiniWindowSwitch() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleScan.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Float over Novig").performClick()
        assert(picked?.miniWindow == false) { "picked $picked" }
    }

    // ---- The CNO scanner (RESEARCH.md §18–19): its tab, bet detail, and in the mini window ----

    @Test fun cnoTab() {
        shoot("8_cno") { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), {}, {}) }
        compose.onNodeWithText("Justin Jefferson Under 69.5").assertIsDisplayed()
        compose.onNodeWithText("Conservative worst case · to +150 · 5+ books · ≥1% EV").assertIsDisplayed()
        compose.onNodeWithText("View: Novig · 3+ books").assertIsDisplayed()
        compose.onNodeWithText("Read 20s ago · odds 49s old · every 15 s", substring = true).assertIsDisplayed()
        compose.onNodeWithText("4 bets pass · 2 hidden: 1 too few books, 1 longer odds than your cap").assertIsDisplayed()
        compose.onAllNodesWithText("Walker Buehler Over 15.5").assertCountEquals(0) // 4 books: too thin
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
        compose.onNodeWithText("Justin Jefferson Under 69.5").assertIsDisplayed()
    }

    @Test fun cnoTabWarnsWhenCnoHasStoppedUpdating() {
        val cno = com.tjshea.vigilant.data.cno.CnoState(snapshot = SampleCno.snapshot().copy(cnoAgeSeconds = 12 * 60))
        screen { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(cno = cno), {}, {}) }
        compose.onNodeWithText("hasn't updated its odds", substring = true).assertIsDisplayed()
    }

    @Test fun cnoTabOffInVigilantOnlyMode() {
        val s = SampleCno.state()
        screen { com.tjshea.vigilant.app.ui.CnoScreen(s.copy(settings = s.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT)), {}, {}) }
        compose.onNodeWithText("CrazyNinjaOdds is off").assertIsDisplayed()
        compose.onAllNodesWithText("Justin Jefferson Under 69.5").assertCountEquals(0)
    }

    @Test fun cnoTabRefreshButtonAndCnoOnlyChip() {
        var refreshed = 0
        var mode: com.tjshea.vigilant.data.scanner.ScannerMode? = null
        screen { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), { refreshed++ }, {}, onScanner = { mode = it }) }
        compose.onNodeWithContentDescription("Refresh CrazyNinjaOdds").performClick()
        assert(refreshed == 1)
        compose.onNodeWithText("CNO only").performClick()
        assert(mode == com.tjshea.vigilant.data.scanner.ScannerMode.CNO) { "mode $mode" }
    }

    @Test fun cnoCardShowsTheBooksVerdictOnceLoaded() {
        screen { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.withBooks(), {}, {}) }
        compose.onNodeWithText("✓ 3 of 3 books agree").assertIsDisplayed()
    }

    @Test fun cnoSheetAsksForTheBetsBooks() {
        var asked: com.tjshea.vigilant.data.cno.CnoRow? = null
        screen { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), {}, {}, onLoadBooks = { r, _ -> asked = r }) }
        compose.onNodeWithText("Justin Jefferson Under 69.5").performClick()
        compose.waitForIdle()
        assert(asked?.bet == "Justin Jefferson Under 69.5") { "asked $asked" }
        compose.onNodeWithText("Reading every book's odds from CNO…").assertExists()
        compose.onNodeWithText("Open in Novig").assertExists()
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun cnoDetailWithEveryBook() {
        val s = SampleCno.withBooks()
        val pick = s.cnoPicks(SampleScan.NOW)!!.picks.first { it.row.bet == "Justin Jefferson Under 69.5" }
        shoot("8e_cno_detail") {
            com.tjshea.vigilant.app.ui.CnoDetail(pick, s.cno.snapshot, s.settings, s.cnoUrl, s.books[pick.row.key], SampleScan.NOW)
        }
        compose.onNodeWithText("✓ 3 of 3 books agree").assertIsDisplayed()
        compose.onNodeWithText("Pinnacle").assertIsDisplayed()
        compose.onNodeWithText("DraftKings").assertIsDisplayed() // one side only: listed, not counted
        compose.onNodeWithText("judged").assertIsDisplayed() // Novig's own row
        compose.onNodeWithText("so +117 on Novig is", substring = true).assertIsDisplayed()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowWithBothLists() {
        val s = SampleCno.state()
        shootAsWindow("7d_mini_window_both") { MiniFeed(s, next = 0) }
        compose.onNodeWithText("${s.feed.size + SampleCno.kept.size} +EV").assertIsDisplayed()
        compose.onAllNodesWithText("CNO", substring = true).onFirst().assertExists()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowWithCnoOnly() {
        val base = SampleCno.state()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        shootAsWindow("7e_mini_window_cno") { MiniFeed(s, next = 0) }
        compose.onNodeWithText("Justin Jefferson Under 69.5").assertIsDisplayed()
        compose.onNodeWithText("CNO 49s", substring = true).assertIsDisplayed()
        compose.onNodeWithText("${SampleCno.kept.size} +EV").assertIsDisplayed()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowBooksAsksForTheBetsBooks() {
        val base = SampleCno.withBooks()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        var asked: com.tjshea.vigilant.data.cno.CnoRow? = null
        shootAsWindow("7f_mini_window_books") { MiniFeed(s, next = 0, booksKey = "cno:" + SampleCno.rows[1].key, onLoadBooks = { asked = it }) }
        assert(asked?.bet == "Justin Jefferson Under 69.5") { "asked $asked" }
        compose.onNodeWithText("✓ 3 of 3 books agree", substring = true).assertIsDisplayed()
        compose.onNodeWithText("PN +100/-122").assertIsDisplayed()
        compose.onNodeWithText("1/4").assertIsDisplayed()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowBooksStaysOnItsBetAndShowsLoading() {
        val base = SampleCno.state()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        screen { MiniFeed(s, next = 0, booksKey = "cno:" + SampleCno.rows[2].key) }
        compose.onNodeWithText("Reading books…").assertIsDisplayed()
        compose.onNodeWithText("Ohio -33.5").assertIsDisplayed()
        compose.onNodeWithText("2/4").assertIsDisplayed()
    }

    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
    @Test fun settingsOfferTheCnoScannerAndItsFilters() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleScan.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Shared View link").assertExists()
        compose.onNodeWithText("Longest odds").assertExists()
        compose.onNodeWithText("+150").assertExists()
        compose.onNodeWithText("Fewest books behind the fair price").assertExists()
        // The chips read as numbers (a first draft printed "${'$'}it+" on every one).
        compose.onNodeWithText("5+").assertExists()
        compose.onNodeWithText("10+").assertExists()
        compose.onNodeWithText("50").assertExists()
        compose.onAllNodesWithText("${'$'}it", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Real time").assertExists()
        compose.onNodeWithText("Tap only").assertExists()
        compose.onNodeWithText("CNO only").performClick()
        assert(picked?.scanner == com.tjshea.vigilant.data.scanner.ScannerMode.CNO) { "picked $picked" }
        compose.onNodeWithText("+100").performClick()
        assert(picked?.cnoFilters?.maxOdds == 100) { "picked $picked" }
    }

    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
    @Test fun cnoOnlySettingsHideWhatsAsleep() {
        val base = SampleScan.state()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        shoot("5c_settings_cno_only") { SettingsScreen(s, {}) }
        compose.onAllNodesWithText("Fair odds method", ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("API usage", ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("Novig API key", ignoreCase = true).assertCountEquals(0)
        compose.onNodeWithText("CNO scanner", ignoreCase = true).assertExists()
        compose.onNodeWithText("Bankroll & Kelly", ignoreCase = true).assertExists()
    }

    /** The other half of the check above: with both scanners on, those sections are there. */
    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
    @Test fun bothScannersSettingsShowVigilantsSections() {
        screen { SettingsScreen(SampleScan.state(), {}) }
        compose.onNodeWithText("Fair odds method", ignoreCase = true).assertExists()
        compose.onNodeWithText("API usage", ignoreCase = true).assertExists()
        compose.onNodeWithText("CNO scanner", ignoreCase = true).assertExists()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowBooksFallsBackToTheTopBetWhenItsBetIsGone() {
        val base = SampleCno.withBooks()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        screen { MiniFeed(s, next = 0, booksKey = "cno:a bet CNO no longer lists") }
        compose.onNodeWithText("Justin Jefferson Under 69.5").assertIsDisplayed()
        compose.onNodeWithText("1/4").assertIsDisplayed()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowTagsCnoRowsOnlyWhenBothListsAreMixed() {
        val base = SampleCno.state()
        screen { MiniFeed(base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO)), next = 0) }
        compose.onAllNodesWithText("CNO Player Receiving Yards", substring = true).assertCountEquals(0)
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowTagsCnoRowsWhenMixed() {
        screen { MiniFeed(SampleCno.state(), next = 0) }
        compose.onAllNodesWithText("CNO Player Receiving Yards", substring = true).onFirst().assertExists()
    }

    @Test fun cnoTabSaysWhenCnoUsedAnotherDevig() {
        val snap = SampleCno.snapshot().copy(evLabel = "LW-WC") // asked for Conservative
        screen { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(cno = com.tjshea.vigilant.data.cno.CnoState(snapshot = snap)), {}, {}) }
        compose.onNodeWithText("CrazyNinjaOdds used liquidity-weighted, worst case instead of conservative worst case.").assertIsDisplayed()
    }

    /**
     * Tj's screenshot, 2026-09-26: over his home screen the mini window showed EV, market and price,
     * but each pick's name ("Jahmyr Gibbs Over 4.5") was nearly black on the dark window. MainActivity
     * puts MiniFeed straight into the theme with no Surface behind it, so text without its own color
     * fell back to black. Rendered here the same way (no Surface), the name must stand out.
     */
    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowPickNamesAreReadableAsTheWindowDrawsThem() {
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { SampleScan.NOW }) {
                VigilantTheme(darkTheme = true) { MiniFeed(SampleCno.state(), next = 0) }
            }
        }
        assertReadable("Justin Jefferson Under 69.5")
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowPickNamesAreReadableInLightThemeToo() {
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { SampleScan.NOW }) {
                VigilantTheme(darkTheme = false) { MiniFeed(SampleCno.state(), next = 0) }
            }
        }
        assertReadable("Justin Jefferson Under 69.5")
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowBooksViewPickNameIsReadableAsTheWindowDrawsIt() {
        val base = SampleCno.withBooks()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { SampleScan.NOW }) {
                VigilantTheme(darkTheme = true) { MiniFeed(s, next = 0, booksKey = "cno:" + SampleCno.rows[1].key) }
            }
        }
        assertReadable("Justin Jefferson Under 69.5")
    }

    /**
     * The text's own pixels differ clearly from the window behind them (luminance spread over 0.5).
     * Draws the Compose view into a bitmap (captureToImage times out under Robolectric) and reads
     * the node's bounds.
     */
    private fun assertReadable(text: String) {
        compose.waitForIdle()
        val bounds = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        val activity = (compose as androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>).activity as android.app.Activity
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = android.graphics.Bitmap.createBitmap(content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888)
        content.draw(android.graphics.Canvas(bitmap))
        var lo = 1.0
        var hi = 0.0
        for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height)) {
            for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width)) {
                val c = bitmap.getPixel(x, y)
                val l = (0.2126 * android.graphics.Color.red(c) + 0.7152 * android.graphics.Color.green(c) + 0.0722 * android.graphics.Color.blue(c)) / 255.0
                lo = minOf(lo, l)
                hi = maxOf(hi, l)
            }
        }
        assert(hi - lo > 0.5) { "\"$text\" is barely visible: luminance ${"%.2f".format(lo)}..${"%.2f".format(hi)}" }
    }

    /** At the size Android first opens the window, a long pick still shows its full line. */
    @Config(qualifiers = "w180dp-h120dp-xxhdpi")
    @Test fun miniWindowSmallKeepsThePicksLine() {
        val base = SampleCno.state()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        shootAsWindow("7g_mini_window_small_cno") { MiniFeed(s, next = 0) }
        compose.onNodeWithText("Justin Jefferson Under 69.5").assertIsDisplayed() // the whole pick, for TalkBack
        // Too narrow for name and line side by side: the name gets the first line (shortened to fit,
        // never "Ju…") and the line leads the second.
        assertNotCutOff("Jefferson")
        compose.onNodeWithText("Under 69.5 · Player Receiving Yards", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Config(qualifiers = "w240dp-h160dp-xxhdpi")
    @Test fun miniWindowShortensNamesThatDontFitAtTheUsualSize() {
        val base = SampleCno.state()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        screen { MiniFeed(s, next = 0) }
        assertNotCutOff("Jefferson")
        assertNotCutOff("Bowers")
    }

    /** The drawn text containing [part] fits its space: no "…". */
    private fun assertNotCutOff(part: String) {
        // The drawn Text (it has a layout), not the row's combined label for TalkBack.
        val node = compose.onNode(
            androidx.compose.ui.test.hasText(part, substring = true) and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assert((0 until layout.lineCount).none { layout.isLineEllipsized(it) }) {
            "\"${layout.layoutInput.text}\" is cut off"
        }
    }

    // ---- The floating widget (Tj, 2026-09-26) ------------------------------------------------

    /** CNO only, with [extra] more bets than [SampleCno] so the list scrolls. */
    private fun floatingState(extra: Int = 6): UiState {
        val more = (1..extra).map { i ->
            // +100, so a fair probability of (1 + EV) / 2 keeps each row's EV consistent (the app checks it).
            val ev = 0.03 - i * 0.001
            SampleCno.rows[3].copy(ev = ev, fairProbability = (1 + ev) / 2, bet = "Player$i Over ${i}.5", gameUrl = "https://crazyninjaodds.com/site/browse/game.aspx?side_id=${100 + i}")
        }
        val base = SampleCno.withBooks(SampleCno.state(cno = com.tjshea.vigilant.data.cno.CnoState(snapshot = SampleCno.snapshot(rows = SampleCno.rows + more))))
        return base.copy(
            settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO),
            teams = mapOf(SampleCno.rows[1].key to "MIN", SampleCno.rows[3].key to "LV"),
        )
    }

    private fun floating(name: String, s: UiState, dark: Boolean = true, minimized: Boolean = false, actions: com.tjshea.vigilant.app.ui.FloatingActions = com.tjshea.vigilant.app.ui.FloatingActions(), w: Int = FloatingWidget.DEFAULT_W_DP) {
        screen(dark = dark) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(8.dp)) {
                com.tjshea.vigilant.app.ui.FloatingFeed(
                    s, actions,
                    if (minimized) androidx.compose.ui.Modifier else androidx.compose.ui.Modifier.androidxSize(w, FloatingWidget.DEFAULT_H_DP),
                    minimized = minimized,
                )
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private fun androidx.compose.ui.Modifier.androidxSize(w: Int, h: Int) =
        this.then(androidx.compose.ui.Modifier.size(w.dp, h.dp))

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetKeepsItsButtonsAndScrollsAPageAtATime() {
        floating("9_floating_widget", floatingState())
        // Always there, no tap needed (picture-in-picture can't do this).
        listOf("Refresh", "Up", "Down", "Books").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onNodeWithText("Justin Jefferson Under 69.5", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Player6 Over 6.5").assertCountEquals(0) // below the fold (lazy)
        compose.onNodeWithContentDescription("Down").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Justin Jefferson Under 69.5", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Down").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Player6 Over 6.5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Up").performClick()
        compose.onNodeWithContentDescription("Up").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Justin Jefferson Under 69.5", substring = true).assertIsDisplayed()
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetTapOpensTheBetAndItsCheckMarksItPlacedWithUndo() {
        var opened: MiniWindow.Item? = null
        var placed: MiniWindow.Item? = null
        var undone: String? = null
        floating(
            "9f_floating_placed", floatingState(),
            actions = com.tjshea.vigilant.app.ui.FloatingActions(onOpenBet = { opened = it }, onPlaced = { placed = it }, onUndoPlaced = { undone = it }),
        )
        compose.onNodeWithText("Ohio -33.5").performClick()
        assert(opened?.title == "Ohio -33.5") { "opened $opened" }
        compose.onNodeWithContentDescription("I placed Ohio -33.5: hide it").performClick()
        assert(placed?.title == "Ohio -33.5") { "placed $placed" }
        compose.onNodeWithText("UNDO").assertIsDisplayed()
        compose.onNodeWithText("Placed: Ohio -33.5").assertIsDisplayed()
        compose.onNodeWithText("UNDO").performClick()
        assert(undone == placed!!.key) { "undone $undone" }
        compose.onAllNodesWithText("UNDO").assertCountEquals(0)
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetShowsTeamsAndGreenChecks() {
        floating("9b_floating_widget_light", floatingState(), dark = false)
        // Jefferson's books agree (Pinnacle, ProphetX, Kalshi): ✓; his team is known: (MIN).
        compose.onNodeWithText("✓ ", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(" (MIN)", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(" (LV)", useUnmergedTree = true).assertIsDisplayed()
        // Ohio's spread isn't a player bet: no team.
        compose.onAllNodesWithText("(", substring = true, useUnmergedTree = true).assertCountEquals(2)
        assertReadable("Justin Jefferson Under 69.5")
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetHoldingABetShowsEveryBook() {
        floating("9c_floating_books", floatingState())
        compose.onNodeWithText("Justin Jefferson Under 69.5").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("PN +100/-122").assertIsDisplayed()
        compose.onNodeWithText("✓ 3 of 3 books agree", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("List").performClick()
        compose.onAllNodesWithText("PN +100/-122").assertCountEquals(0)
        // Books from the bottom bar: the first bet showing.
        compose.onNodeWithContentDescription("Books").performClick()
        compose.onNodeWithText("PN +100/-122").assertIsDisplayed()
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetShrinksToABubble() {
        var expanded = false
        floating("9d_floating_bubble", floatingState(), minimized = true, actions = com.tjshea.vigilant.app.ui.FloatingActions(onExpand = { expanded = true }))
        compose.onNodeWithText("10 +EV").assertIsDisplayed()
        compose.onNodeWithText("10 +EV").performClick()
        assert(expanded)
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetWithBothScannersHasScanAndRecheck() {
        val s = floatingState().let { it.copy(settings = it.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.BOTH)) }
        floating("9e_floating_both", s)
        listOf("Scan", "Recheck", "Up", "Down", "Books").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onAllNodesWithContentDescription("Refresh").assertCountEquals(0)
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetHeaderClosesShrinksAndOpensTheApp() {
        var closed = false
        var shrunk = false
        var app = false
        floating("9g_floating_header", floatingState(), actions = com.tjshea.vigilant.app.ui.FloatingActions(onClose = { closed = true }, onMinimize = { shrunk = true }, onOpenApp = { app = true }))
        compose.onNodeWithContentDescription("Close the widget").performClick()
        compose.onNodeWithContentDescription("Shrink to a bubble").performClick()
        compose.onNodeWithContentDescription("Open Vigilant").performClick()
        assert(closed && shrunk && app)
    }

    // ---- Tj, 2026-09-26 ~23:45Z: resize/move, "CNO error", the stuck refresh arrow ----------

    /** The window as FloatingWidget draws it: frame, corner handles, the widget inside. */
    @Config(qualifiers = "w380dp-h340dp-xxhdpi")
    @Test fun floatingWindowHasAFrameWithFourCornerHandlesAndATallerTopBar() {
        screen {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size((FloatingWidget.DEFAULT_W_DP + 2 * FloatingWidget.FRAME_DP).dp, (FloatingWidget.DEFAULT_H_DP + 2 * FloatingWidget.FRAME_DP).dp)) {
                com.tjshea.vigilant.app.ui.FloatingWindow(floatingState(), com.tjshea.vigilant.app.ui.FloatingActions())
            }
        }
        compose.onRoot().captureRoboImage("screenshots/9h_floating_window_frame.png")
        compose.onNodeWithContentDescription("Drag a corner to resize", substring = true).assertExists()
        // v0.15.0's in-corner grip is gone (the rounded corner cut it off).
        compose.onAllNodesWithContentDescription("Drag to resize").assertCountEquals(0)
        listOf("Refresh", "Up", "Down", "Books").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
    }

    @Test fun theWidgetSaysWhatWentWrongWithCnoNotJustCnoError() {
        assertEquals("CNO offline, retrying", com.tjshea.vigilant.app.ui.cnoErrorShort("Couldn't reach CrazyNinjaOdds (timeout)"))
        assertEquals("CNO busy, waiting", com.tjshea.vigilant.app.ui.cnoErrorShort("CrazyNinjaOdds is busy (HTTP 429); trying again later"))
        assertEquals("CNO refused, waiting", com.tjshea.vigilant.app.ui.cnoErrorShort("CrazyNinjaOdds refused the request (HTTP 403)"))
        assertEquals("CNO page problem", com.tjshea.vigilant.app.ui.cnoErrorShort("CrazyNinjaOdds' reply had no table"))
        assertEquals("CNO HTTP 500", com.tjshea.vigilant.app.ui.cnoErrorShort("CrazyNinjaOdds answered HTTP 500"))
        val s = SampleCno.state(cno = com.tjshea.vigilant.data.cno.CnoState(snapshot = SampleCno.snapshot(), error = "Couldn't reach CrazyNinjaOdds (timeout)"))
        assertTrue(com.tjshea.vigilant.app.ui.miniStatus(s, SampleScan.NOW).endsWith("CNO offline, retrying"))
    }

    // ---- Tj, 2026-09-27: ✕ to remove a bet, the bar at any width, only agreed bets, DNS/timeouts ----

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetXRemovesABetWithoutPlacingItAndCanBeUndone() {
        var removed: MiniWindow.Item? = null
        var placed: MiniWindow.Item? = null
        var undone: String? = null
        floating(
            "9i_floating_remove", floatingState(),
            actions = com.tjshea.vigilant.app.ui.FloatingActions(onPlaced = { placed = it }, onHidden = { removed = it }, onUndoPlaced = { undone = it }),
        )
        // Every row has both, on the right: ✓ placed and ✕ remove.
        compose.onNodeWithContentDescription("I placed Ohio -33.5: hide it").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Ohio -33.5 from the list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Ohio -33.5 from the list").performClick()
        assert(removed?.title == "Ohio -33.5" && placed == null) { "removed $removed, placed $placed" }
        compose.onNodeWithText("Removed: Ohio -33.5").assertIsDisplayed()
        compose.onNodeWithText("UNDO").performClick()
        assert(undone == removed!!.key) { "undone $undone" }
        // The price stays whole next to the two buttons.
        assertReadable("+106")
    }

    /** "Books" was cut to "Bo" (Tj's screenshot, v0.15.0): narrow, the bar shows icons only. */
    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetBarFitsAtAnyWidth() {
        val both = floatingState().let { it.copy(settings = it.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.BOTH)) }
        floating("9j_floating_narrow", both, w = 250)
        listOf("Scan", "Recheck", "Up", "Down", "Books").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onAllNodesWithText("Books", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Recheck", useUnmergedTree = true).assertCountEquals(0)
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun floatingWidgetBarKeepsItsLabelsWhenTheyFit() {
        floating("9k_floating_labels", floatingState())
        compose.onNodeWithText("Books", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Refresh", useUnmergedTree = true).assertIsDisplayed()
        assertReadable("Books")
    }

    @Test fun cnoCardXRemovesItAndTheRemovedListPutsItBack() {
        var removed: MiniWindow.Item? = null
        var back: String? = null
        val removedBet = com.tjshea.vigilant.data.tracker.PlacedBet("cno:x", "Somebody Over 1.5", "Player Hits · A @ B", placedAtMs = SampleScan.NOW, hidden = true)
        val placedBet = com.tjshea.vigilant.data.tracker.PlacedBet("cno:y", "Other Under 2.5", "Player Hits · A @ B", placedAtMs = SampleScan.NOW)
        screen {
            com.tjshea.vigilant.app.ui.CnoScreen(
                SampleCno.state().copy(placed = listOf(removedBet, placedBet)), {}, {},
                onHide = { removed = it }, onUnplace = { back = it },
            )
        }
        compose.onNodeWithContentDescription("Remove Ohio -33.5 from the list").performClick()
        assert(removed?.title == "Ohio -33.5") { "removed $removed" }
        compose.onNodeWithText("Removed: Ohio -33.5", substring = true).assertIsDisplayed()
        // Placed and removed bets are listed apart.
        compose.onNodeWithText("Show the 1 bet you placed").assertIsDisplayed()
        compose.onNodeWithText("Show the 1 bet you removed").performClick()
        compose.onNodeWithText("✕ Somebody Over 1.5").assertIsDisplayed()
        compose.onNodeWithText("Put back").performClick()
        assertEquals("cno:x", back)
    }

    @Test fun cnoTabWithOnlyAgreedBetsSaysWhatsHeldBack() {
        val base = SampleCno.withBooks()
        shoot("8e_cno_only_agreed") { com.tjshea.vigilant.app.ui.CnoScreen(base.copy(settings = base.settings.copy(cnoOnlyAgreed = true)), {}, {}) }
        compose.onNodeWithText("Justin Jefferson Under 69.5", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Ohio -33.5").assertCountEquals(0)
        compose.onNodeWithText("1 bet passes · 2 hidden: 1 too few books, 1 longer odds than your cap · only ✓ bets: 3 held back, 3 being checked").assertIsDisplayed()
    }

    @Config(qualifiers = "w393dp-h6400dp-xxhdpi")
    @Test fun settingsHasTheOnlyAgreedSwitch() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleCno.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Only bets the books agree on").performClick()
        assert(picked?.cnoOnlyAgreed == true) { "picked $picked" }
    }

    @Test fun theWidgetTellsDnsFailuresFromTimeouts() {
        assertEquals("CNO lookup failed, retrying", com.tjshea.vigilant.app.ui.cnoErrorShort("Couldn't reach CrazyNinjaOdds (the phone couldn't look up its address: no signal, or a VPN reconnecting)"))
        assertEquals("CNO slow, retrying", com.tjshea.vigilant.app.ui.cnoErrorShort("Couldn't reach CrazyNinjaOdds (it didn't answer in time)"))
        assertEquals("CNO offline, retrying", com.tjshea.vigilant.app.ui.cnoErrorShort("Couldn't reach CrazyNinjaOdds (no connection to it)"))
    }

    // ---- K10 (full test, 2026-09-27) ----------------------------------------------------------

    /** Jefferson re-read at -125 after his books were read (at +117): the list's price is the newer one. */
    private fun jeffersonMoved(): Pair<com.tjshea.vigilant.data.cno.CnoPick, com.tjshea.vigilant.data.cno.CnoSnapshot> {
        val jj = SampleCno.rows[1]
        val moved = jj.copy(odds = -125, ev = 0.012, fairProbability = 0.5613)
        val snap = SampleCno.snapshot(readAgoMs = 1_000, rows = SampleCno.rows.map { if (it.key == jj.key) moved else it })
        return com.tjshea.vigilant.data.cno.CnoPick(moved, 0.012, live = false) to snap
    }

    /** The sheet judged the game page's older price while the card and the ✓ judged the list's newer one. */
    @Test fun theSheetJudgesTheSamePriceAsTheGreenCheck() {
        val (pick, snap) = jeffersonMoved()
        screen {
            com.tjshea.vigilant.app.ui.CnoDetail(
                pick, snap, SampleScan.settings, com.tjshea.vigilant.data.cno.CnoView.DEFAULT,
                com.tjshea.vigilant.data.cno.CnoBooksState(view = SampleCno.jeffersonBooks()), SampleScan.NOW,
            )
        }
        compose.onAllNodesWithText("✓ 3 of 3 books agree", substring = true).assertCountEquals(0)
        compose.onNodeWithText("✗ books say", substring = true).assertExists()
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetsBooksViewJudgesTheSamePriceAsItsGreenCheck() {
        val (_, snap) = jeffersonMoved()
        val s = floatingState().let { it.copy(cno = com.tjshea.vigilant.data.cno.CnoState(snapshot = snap)) }
        floating("9l_floating_books_moved", s)
        compose.onNodeWithText("Justin Jefferson Under 69.5").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("PN +100/-122").assertIsDisplayed()
        compose.onAllNodesWithText("✓ 3 of 3 books agree", substring = true).assertCountEquals(0)
    }

    /** "Only bets the books agree on" with none agreed yet: the widget says so, not "no +EV". */
    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetSaysWhenBetsAreWaitingForTheBooks() {
        val s = floatingState().let { it.copy(books = emptyMap(), settings = it.settings.copy(cnoOnlyAgreed = true)) }
        floating("9m_floating_only_agreed_waiting", s)
        compose.onAllNodesWithText("No +EV on CrazyNinjaOdds right now").assertCountEquals(0)
        compose.onNodeWithText("No bets the books agree on yet", substring = true).assertIsDisplayed()
        compose.onNodeWithText("being checked", substring = true).assertIsDisplayed()
    }

    /** Tj, 2026-09-27: "an option to also use the regular scan in addition to cno" — right in the widget. */
    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetsTopBarSwitchesVigilantsScanOnAndOff() {
        var both: Boolean? = null
        floating("9n_floating_switch_cno", floatingState(), actions = com.tjshea.vigilant.app.ui.FloatingActions(onBoth = { both = it }))
        compose.onNodeWithText("CNO only").assertIsDisplayed()
        compose.onNodeWithContentDescription("Showing CNO only. Tap to add Vigilant's own scan").performClick()
        assertEquals(true, both)
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetWithBothListsABetBothScannersFoundOnce() {
        var both: Boolean? = null
        val feedItem = MiniWindow.items(SampleScan.state().copy(settings = SampleScan.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT)), SampleScan.NOW).first()
        val ohio = SampleCno.rows[2]
        val s = SampleCno.state().copy(cnoLinks = mapOf(com.tjshea.vigilant.data.cno.CnoFeed.linkKey(ohio) to "novigapp://events/${feedItem.outcomeId}/cno"))
        floating("9o_floating_both_merged", s, actions = com.tjshea.vigilant.app.ui.FloatingActions(onBoth = { both = it }))
        compose.onNodeWithText("Both").assertIsDisplayed()
        compose.onAllNodesWithText("Ohio -33.5").assertCountEquals(0) // folded into Vigilant's row
        compose.onNodeWithText("CNO +5.3%", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Showing CNO and Vigilant's scan. Tap for CNO only").performClick()
        assertEquals(false, both)
    }

    @Config(qualifiers = "w393dp-h6400dp-xxhdpi")
    @Test fun settingsOfferVigilantsScanAgainWhileTheWidgetIsOpen() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleCno.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Vigilant's scan again while the widget is open").assertExists()
        compose.onNodeWithText("10 min").performClick()
        assertEquals(10, picked?.widgetRescanMinutes)
    }

    /** Novig's price now for CNO's bets (RESEARCH.md §20.3): the widget and the CNO tab say what CNO had. */
    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetShowsNovigsPriceNowAndWhatCnoHad() {
        val jj = SampleCno.rows[1]
        // Novig moved Jefferson in Tj's favor since CNO's read: +125 now, a better EV, so still on top.
        val s = floatingState().copy(novigLive = mapOf(jj.key to com.tjshea.vigilant.data.cno.LivePrice(125, 40.0, 0.0973, SampleScan.NOW - 5_000)))
        floating("9p_floating_novig_now", s)
        compose.onNodeWithText("was +117", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+125", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+9.7%", useUnmergedTree = true).assertExists()
    }

    @Test fun cnoCardShowsNovigsPriceNowAndWhatCnoHad() {
        val jj = SampleCno.rows[1]
        val s = SampleCno.state().copy(novigLive = mapOf(jj.key to com.tjshea.vigilant.data.cno.LivePrice(105, 40.0, 0.004, SampleScan.NOW - 5_000)))
        shoot("8f_cno_novig_now") { com.tjshea.vigilant.app.ui.CnoScreen(s, {}, {}) }
        compose.onNodeWithText("CNO had +117").assertIsDisplayed()
        compose.onNodeWithText("NOVIG NOW").assertIsDisplayed()
    }

    @Config(qualifiers = "w393dp-h6400dp-xxhdpi")
    @Test fun settingsHaveTheNovigPriceNowSwitch() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleCno.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Novig's price now").performClick()
        assertEquals(false, picked?.cnoLivePrices)
    }

    // ---- Full test, 2026-09-27 ~02:40Z ------------------------------------------------------

    /** 8f's screenshot: the ✕ on cards with a Kelly stake was squeezed smaller than a touch target. */
    @Test fun cnoCardButtonsKeepTheirSizeWhateverTheValuesRowHolds() {
        screen { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), {}, {}) }
        listOf("Ohio -33.5", "Brock Bowers Under 4.5", "Justin Jefferson Under 69.5").forEach { bet ->
            compose.onNodeWithContentDescription("Remove $bet from the list").assertWidthIsAtLeast(36.dp).assertHeightIsAtLeast(36.dp)
            compose.onNodeWithContentDescription("I placed $bet: hide it").assertWidthIsAtLeast(36.dp)
        }
    }

    /** At Novig's price now a bet can fall under the minimum: the CNO tab warns, as the widget does. */
    @Test fun cnoCardWarnsWhenNovigsPriceNowIsUnderTheMinimum() {
        val jj = SampleCno.rows[1]
        val s = SampleCno.state().copy(novigLive = mapOf(jj.key to com.tjshea.vigilant.data.cno.LivePrice(105, 40.0, 0.004, SampleScan.NOW - 5_000)))
        screen { com.tjshea.vigilant.app.ui.CnoScreen(s, {}, {}) }
        compose.onNodeWithContentDescription("Expected value +0.40%, under your minimum").assertExists()
        compose.onNodeWithContentDescription("Expected value +5.28%").assertExists() // Ohio: CNO's price, fine
        // Best EV first at the prices shown, as in the widget: Jefferson (+0.40% now) is last, not first.
        val order = compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Expected value", substring = true)).fetchSemanticsNodes()
            .sortedBy { it.positionInRoot.y }.map { it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first() }
        assertEquals("Expected value +5.28%", order.first())
        assertEquals("Expected value +0.40%, under your minimum", order.last())
    }

    @Config(qualifiers = "w393dp-h6400dp-xxhdpi")
    @Test fun settingsDescribeTheWidgetAsItIsNow() {
        screen { SettingsScreen(SampleCno.state(), { }) }
        compose.onNodeWithText("✕ to remove it without betting", substring = true).assertExists()
        compose.onNodeWithText("CNO only / Both switch", substring = true).assertExists()
    }

    // ---- N (2026-09-27): every ✓ tracked, auto-settled, "now ±x% EV", a stats section -----------

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun trackerStats() {
        shoot("4_tracker") { TrackerScreen(SampleScan.state(), { _, _ -> }, {}) }
        // b1 won, b2 lost, b6 won (settled by Novig): 2-1, and profit is +$34.52 − $18 + $1.
        compose.onNodeWithText("66.7%").assertExists()
        compose.onNodeWithText("2-1").assertExists()
        compose.onNodeWithText("+$17.52").assertExists()
        compose.onNodeWithText("By scanner").assertExists()
    }

    @Config(qualifiers = "w393dp-h2000dp-xxhdpi")
    @Test fun trackerBetsShowTheirEvNowGreenOrRed() {
        shoot("4b_tracker_bets") { TrackerScreen(SampleScan.state(), { _, _ -> }, {}, initialView = com.tjshea.vigilant.app.ui.TrackerView.BETS) }
        compose.onNodeWithText("now +3.1% EV").assertExists()
        compose.onNodeWithText("now −2.1% EV").assertExists()
        compose.onNodeWithText("Open (3)").assertExists()
        // Open bets only by default: the settled ones are one tap away.
        compose.onAllNodesWithText("Won · undo").assertCountEquals(0)
        compose.onNodeWithText("Settled (3)").performClick()
        compose.onNodeWithText("settled by Novig").assertExists()
    }

    @Test fun trackerStakeIsEditable() {
        screen { TrackerScreen(SampleScan.state(), { _, _ -> }, {}, initialView = com.tjshea.vigilant.app.ui.TrackerView.BETS) }
        // Tapping the stake opens its dialog (not opened here: a text field in a Robolectric dialog never idles).
        compose.onAllNodesWithText("Stake ✎")[0].assertHasClickAction()
    }

    @Test fun trackerSaysCloseForEverySportNotJustFootball() {
        screen { TrackerScreen(SampleScan.state(), { _, _ -> }, { }) }
        compose.onNodeWithText("last fair line seen before the game started", substring = true).assertExists()
    }

    /** "Open in Novig" on the CNO tab: it says it's working while the bet's link is found. */
    @Test fun openInNovigSaysItsOpening() {
        val (pick, snap) = jeffersonMoved()
        screen {
            com.tjshea.vigilant.app.ui.CnoDetail(pick, snap, SampleScan.settings, com.tjshea.vigilant.data.cno.CnoView.DEFAULT, null, SampleScan.NOW, opening = true)
        }
        compose.onNodeWithText("Opening…").assertIsDisplayed()
        compose.onAllNodesWithText("Open in Novig").assertCountEquals(0)
    }

    /** Tj's screenshot: the pull-to-refresh arrow stuck half way down the CNO tab. */
    @Test fun thePullToRefreshArrowLetsGoAfterARead() {
        var refreshing by androidx.compose.runtime.mutableStateOf(false)
        var pulls = 0
        lateinit var pull: androidx.compose.material3.pulltorefresh.PullToRefreshState
        screen {
            pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
            val base = SampleCno.state()
            com.tjshea.vigilant.app.ui.CnoScreen(
                base.copy(cno = base.cno.copy(refreshing = refreshing)),
                onRefresh = { pulls++; refreshing = true },
                onOpenSettings = {},
                pullState = pull,
            )
        }
        compose.onNodeWithText("Justin Jefferson Under 69.5").performTouchInput { swipeDown(startY = top, endY = top + 1_500f, durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(1, pulls)
        refreshing = false // the read ended
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        assertEquals(0f, pull.distanceFraction, 0.001f)
    }

    /** A pull whose read CNO's pacing skipped (nothing starts): the arrow still lets go. */
    @Test fun thePullToRefreshArrowLetsGoWhenNothingWasRead() {
        var pulls = 0
        lateinit var pull: androidx.compose.material3.pulltorefresh.PullToRefreshState
        screen {
            pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
            com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), onRefresh = { pulls++ }, onOpenSettings = {}, pullState = pull)
        }
        compose.onNodeWithText("Justin Jefferson Under 69.5").performTouchInput { swipeDown(startY = top, endY = top + 1_500f, durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(1, pulls)
        compose.mainClock.advanceTimeBy(3_000)
        org.robolectric.shadows.ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(0f, pull.distanceFraction, 0.001f)
    }
}
