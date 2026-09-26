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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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

    @Test fun tracker() = shoot("4_tracker") { TrackerScreen(SampleScan.state(), { _, _ -> }, {}) }

    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
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
        compose.onNodeWithText("Odds 49s old · every 15 s", substring = true).assertIsDisplayed()
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

    private fun floating(name: String, s: UiState, dark: Boolean = true, minimized: Boolean = false, actions: com.tjshea.vigilant.app.ui.FloatingActions = com.tjshea.vigilant.app.ui.FloatingActions()) {
        screen(dark = dark) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(8.dp)) {
                com.tjshea.vigilant.app.ui.FloatingFeed(
                    s, actions,
                    if (minimized) androidx.compose.ui.Modifier else androidx.compose.ui.Modifier.androidxSize(FloatingWidget.DEFAULT_W_DP, FloatingWidget.DEFAULT_H_DP),
                    minimized = minimized,
                )
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private fun androidx.compose.ui.Modifier.androidxSize(w: Int, h: Int) =
        this.then(androidx.compose.ui.Modifier.size(w.dp, h.dp))

    @Config(qualifiers = "w360dp-h320dp-xxhdpi")
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

    @Config(qualifiers = "w360dp-h320dp-xxhdpi")
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

    @Config(qualifiers = "w360dp-h320dp-xxhdpi")
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

    @Config(qualifiers = "w360dp-h320dp-xxhdpi")
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

    @Config(qualifiers = "w360dp-h320dp-xxhdpi")
    @Test fun floatingWidgetShrinksToABubble() {
        var expanded = false
        floating("9d_floating_bubble", floatingState(), minimized = true, actions = com.tjshea.vigilant.app.ui.FloatingActions(onExpand = { expanded = true }))
        compose.onNodeWithText("10 +EV").assertIsDisplayed()
        compose.onNodeWithText("10 +EV").performClick()
        assert(expanded)
    }

    @Config(qualifiers = "w360dp-h320dp-xxhdpi")
    @Test fun floatingWidgetWithBothScannersHasScanAndRecheck() {
        val s = floatingState().let { it.copy(settings = it.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.BOTH)) }
        floating("9e_floating_both", s)
        listOf("Scan", "Recheck", "Up", "Down", "Books").forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
        compose.onAllNodesWithContentDescription("Refresh").assertCountEquals(0)
    }

    @Config(qualifiers = "w360dp-h320dp-xxhdpi")
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
}
