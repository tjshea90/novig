@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import com.tjshea.vigilant.app.ui.SettingsTab
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

    /** Settings is one page per tab (v0.22.0, Tj: "tabs on the top"): open [tab] before looking for what's on it. */
    private fun openSettingsTab(tab: com.tjshea.vigilant.app.ui.SettingsTab) {
        // The row scrolls sideways: the later tabs start off screen, as they do on a phone.
        compose.onNodeWithTag("settingsTab-${tab.name}").performScrollTo().performClick()
        compose.waitForIdle()
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


    @Config(qualifiers = "w393dp-h6800dp-xxhdpi")
    @Test fun settings() = shoot("5_settings") { SettingsScreen(SampleScan.state(), {}) }

    /** One picture per Settings tab (v0.22.0). */
    private fun settingsTab(tab: SettingsTab) = shoot("5_settings_tab_${tab.name.lowercase()}") { SettingsScreen(SampleScan.state(), {}, startTab = tab) }

    @Test fun settingsTabScan() = settingsTab(SettingsTab.SCAN)
    @Test fun settingsTabCno() = settingsTab(SettingsTab.CNO)
    @Test fun settingsTabFair() = settingsTab(SettingsTab.FAIR)
    @Test fun settingsTabFeed() = settingsTab(SettingsTab.FEED)
    @Test fun settingsTabBetting() = settingsTab(SettingsTab.BETTING)
    @Test fun settingsTabUsage() = settingsTab(SettingsTab.USAGE)
    @Test fun settingsTabTools() = settingsTab(SettingsTab.TOOLS)

    @Test fun settingsOfferSportsbookPropsWithTheirCreditBudget() {
        // No PropLine key: The Odds API buys props on its own.
        screen { SettingsScreen(SampleScan.state().copy(proplineKeys = emptyList()), {}) }
        openSettingsTab(SettingsTab.FAIR)
        compose.onNodeWithText("Sportsbook player props").assertExists()
        compose.onNodeWithText("Most credits per scan on props").assertExists()
        compose.onNodeWithText("up to 6 games a scan", substring = true).assertExists()
    }

    /** With a PropLine key, The Odds API's prop credits go only to what PropLine didn't price (RESEARCH.md §23). */
    @Test fun settingsSayPropCreditsOnlyBackUpPropLine() {
        screen { SettingsScreen(SampleScan.state(), {}) }
        openSettingsTab(SettingsTab.FAIR)
        compose.onNodeWithText("Only games and prop types PropLine didn't price", substring = true).assertExists()
        compose.onAllNodesWithText("up to 6 games a scan", substring = true).assertCountEquals(0)
    }

    @Test fun settingsTakePinnWireAndPropLineKeys() {
        screen { SettingsScreen(SampleScan.state().copy(pinnwireKeys = emptyList(), proplineKeys = emptyList()), {}) }
        openSettingsTab(SettingsTab.FAIR)
        compose.onNodeWithText("PinnWire keys (game lines and player props)").assertExists()
        compose.onNodeWithText("Add a PinnWire key").assertExists()
        compose.onNodeWithText("Free key at prop-line.com", substring = true).assertExists()
        compose.onNodeWithText("Add a PropLine key").assertExists()
        compose.onNodeWithText("SPORTSBOOKS FOR FAIR ODDS", substring = true).assertExists()
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
        // Cards are taller since v0.19.4 (the Open in Novig button): scroll to the Dallas moneyline's card first.
        val dallas = s.feed.first { it.selection == "Dallas Cowboys" }
        compose.onAllNodes(androidx.compose.ui.test.hasScrollToKeyAction()).onFirst().performScrollToKey(dallas.key)
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

    /**
     * Tj, 2026-09-28: "On the vigilant +ev scan tab when the app is in full screen, make easy one press buttons next to each
     * bet to Open the bet in novig, just as the widget does". And "automatically enter 1 dollar … the kelly value … or an
     * amount I can type into the settings".
     */
    @Test fun everyBetHasAOneTapOpenInNovigButtonWithTheChosenStake() {
        val opened = ArrayList<String?>()
        var settings by androidx.compose.runtime.mutableStateOf(SampleScan.settings)
        screen {
            CompositionLocalProvider(com.tjshea.vigilant.app.ui.LocalOpenNovig provides { link -> opened += link }) {
                FeedScreen(SampleScan.state().copy(settings = settings), {}, {}, {}, { _, _ -> })
            }
        }
        val first = SampleScan.state().feed.first { it.quote != null && it.fairProbability != null }
        compose.onAllNodesWithTag("openBet").onFirst().assertIsDisplayed()
        compose.onAllNodesWithTag("openBet").onFirst().performClick()
        assertEquals("novigapp://events/${first.outcome.outcomeId}", opened.last())
        // $1 in the slip.
        settings = SampleScan.settings.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR)
        compose.onAllNodesWithText("Open in Novig · $1").onFirst().assertIsDisplayed()
        compose.onAllNodesWithTag("openBet").onFirst().performClick()
        assertEquals("novigapp://events/${first.outcome.outcomeId}/novig/1", opened.last())
        // Its Kelly stake.
        settings = SampleScan.settings.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.KELLY)
        compose.onAllNodesWithTag("openBet").onFirst().performClick()
        val kelly = com.tjshea.vigilant.data.novig.NovigLinks.amountText(maxOf(1.0, Math.round(first.suggestedStake!! * 100) / 100.0))
        assertEquals("novigapp://events/${first.outcome.outcomeId}/novig/$kelly", opened.last())
    }

    @Test fun feedCardsWithTheOpenInNovigButton() = shoot("1j_feed_open_buttons") {
        FeedScreen(SampleScan.state().copy(settings = SampleScan.settings.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR)), {}, {}, {}, { _, _ -> })
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
            com.tjshea.vigilant.app.ui.NovigKeySection(NovigUi(), {}, {}, {})
        }
    }

    /** A keyed scan: where its 41 seconds went (Tj, 2026-09-28: "now it is reading the API very slow"). */
    private val timedScan = ScanStatus(
        scannedAtMs = SampleScan.NOW, booksFetched = 1200, booksViaKey = 500, booksViaPush = 700, keyReadPerSec = 16.0,
        timing = com.tjshea.vigilant.data.scanner.ScanTiming(boardAtMs = 900, fairAtMs = 14_600, novigFromMs = 1_000, novigToMs = 39_000, firstBetAtMs = 6_200, totalMs = 41_200),
    )

    @Test fun novigKeyConnected() = shoot("6b_novig_key_connected") {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
            com.tjshea.vigilant.app.ui.NovigKeySection(
                NovigUi(
                    connection = com.tjshea.vigilant.data.novig.signing.NovigConnection("3f2504e0-4f89-11d3-9a0c-0305e82c9a1b", "a", "t", false),
                    message = "Novig accepted the key over Wi-Fi (signature, clock and network all OK).",
                ),
                {}, {}, {},
                lastScan = timedScan,
            )
        }
    }

    @Test fun novigKeySaysWhereTheLastScansTimeWent() {
        screen {
            com.tjshea.vigilant.app.ui.NovigKeySection(
                NovigUi(connection = com.tjshea.vigilant.data.novig.signing.NovigConnection("3f2504e0-4f89-11d3-9a0c-0305e82c9a1b", "a", "t", false)),
                {}, {}, {},
                lastScan = timedScan,
            )
        }
        compose.onNodeWithTag("scanTiming").assertIsDisplayed()
        compose.onNodeWithText("1,200 Novig prices in 38 s (31.6 a second: 700 by live feed, 500 through the key)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("first bet at 6.2 s", substring = true).assertIsDisplayed()
        compose.onNodeWithText("the key's limit is 16 a second", substring = true).assertIsDisplayed()
    }

    /**
     * Tj, 2026-09-27: "make sure it never gives me stale odds when comparing odds from other sports
     * books" (RESEARCH.md §24). 25 minutes after a scan (the sample's book prices were seen 3 minutes
     * before it), no bet is offered: the feed says why and offers a scan. Until v0.16.4 the same bets
     * still showed, only flagged "old price".
     */
    @Test fun oldOddsLeaveTheFeedAndAskForAScan() {
        var scanned = false
        screen(now = SampleScan.NOW + 25 * 60_000L) { FeedScreen(SampleScan.state(), { scanned = true }, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("Odds too old to compare").assertIsDisplayed()
        compose.onAllNodesWithText("old price", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(SampleScan.state().feed.first().selection).assertCountEquals(0)
        compose.onNodeWithText("Scan now").performClick()
        assertTrue(scanned)
    }

    /** Past three minutes a bet says how old its odds are (they leave the feed at 5 minutes, 10 for games over 3 hours off). */
    @Test fun agingOddsAreFlaggedBeforeTheyLeave() {
        // The sample's book prices were seen 3 minutes before the scan: a minute later, 4 minutes old.
        screen(now = SampleScan.NOW + 60_000L) { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }) }
        compose.onAllNodesWithText("odds 4 min old").onFirst().assertIsDisplayed()
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

    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
    @Test fun settingsOfferTheOutlierGuardAndAnOddsCap() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        shoot("5e_settings_feed") { SettingsScreen(SampleScan.state(), { t -> picked = t(SampleScan.settings) }, startTab = SettingsTab.FEED) }
        // Up to 1,200 Novig prices a scan, and props per game up to 24 (v0.18.0).
        for (n in listOf("500", "800", "1200")) compose.onNodeWithText(n).assertExists()
        compose.onAllNodesWithText("24").assertCountEquals(1) // props per game's (props credits' and PropLine games per scan's are on the Fair odds tab)
        compose.onNodeWithText("300 is about a minute", substring = true).assertExists()
        // +300 at most since v0.18.0 (Tj, 2026-09-28: "Let me choose +200 +150 and +120 and get rid of any option over +300").
        compose.onNodeWithText("Longest odds shown: +300").assertExists()
        for (gone in listOf("Any", "+500", "+1000", "+2000")) compose.onAllNodesWithText(gone).assertCountEquals(0)
        compose.onNodeWithText("+120").performClick()
        assert(picked?.maxOdds == 120) { "picked $picked" }
        // v0.19.0: the per-scan budget's leftovers go to every other quoted line; the key's live feed is named.
        compose.onNodeWithText("arrives by live feed about 8 seconds in", substring = true).assertExists()
        compose.onNodeWithText("Fill the scan with every quoted line").performClick()
        assert(picked?.fillBudget == false) { "picked $picked" }
        // The Fair odds tab: the outlier guard, props credits' 24 and PropLine games per scan's 24 (v0.19.6).
        openSettingsTab(SettingsTab.FAIR)
        compose.onNodeWithText("Outlier guard").assertExists()
        compose.onAllNodesWithText("24").assertCountEquals(2)
    }

    /** Tj, 2026-09-28: "There are way more than 7 total games for it to scan". The feed says what it covered. */
    @Test fun anEmptyFeedSaysItsWindowAndHowManyGamesStartLater() {
        var opened = false
        val base = SampleScan.state()
        val r = base.result!!.let { it.copy(stats = it.stats.copy(laterGames = 55)) }
        shoot("1h_feed_empty_later_games") {
            FeedScreen(base.copy(result = r, feed = emptyList(), settings = base.settings.copy(daysAhead = 3)), {}, {}, { opened = true }, { _, _ -> })
        }
        compose.onNodeWithText("starting in the next 3 days", substring = true).assertExists()
        compose.onNodeWithText("55 more games on Novig start later than that", substring = true).assertExists()
        compose.onNodeWithText("Days ahead").performClick()
        assert(opened)
    }

    /** Tj, 2026-09-28: "found several positive EV bets while scanning but they quickly disappeared". */
    @Test fun betsHiddenForOldOddsAreCountedNotJustDropped() {
        val base = SampleScan.state()
        // Half the feed's other-book prices were seen 11 minutes before now: past the limit (the sample's games are two
        // days off, so 10 minutes).
        val old = base.feed.mapIndexed { i, o -> if (i % 2 == 0) o.copy(fairAsOfMs = SampleScan.NOW - 11 * 60_000L) else o.copy(fairAsOfMs = SampleScan.NOW) }
        val aged = old.count { it.fairIsOld(SampleScan.NOW) }
        assertTrue(aged > 0 && aged < old.size)
        shoot("1i_feed_aged_out") { FeedScreen(base.copy(feed = old), {}, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("$aged bets hidden: the other books", substring = true).assertExists()
        compose.onNodeWithText("are too old (over 5 minutes, or 10 for games more than 3 hours away)", substring = true).assertExists()
        assertEquals(null, com.tjshea.vigilant.app.ui.agedOutText(base, SampleScan.NOW))
    }

    @Test fun theLeagueChipsOfferTennis() {
        var toggled: String? = null
        screen { com.tjshea.vigilant.app.ui.LeagueChips(com.tjshea.vigilant.data.scanner.Leagues.ALL, setOf("NFL"), { toggled = it }) }
        // After Tj's first five, off screen at phone width: scrolled to, as a thumb would.
        compose.onNode(androidx.compose.ui.test.hasScrollToKeyAction()).performScrollToKey("WTA")
        compose.onNodeWithText("🎾 ATP").performClick()
        assert(toggled == "ATP") { "toggled $toggled" }
        compose.onNodeWithText("🎾 WTA").assertExists()
    }

    @Test fun theFeedCanBeSortedBySoonest() {
        var picked: com.tjshea.vigilant.data.scanner.FeedSort? = null
        screen { FeedScreen(SampleScan.state(), {}, {}, {}, { _, _ -> }, onSort = { picked = it }) }
        compose.onNodeWithText("Soonest").performClick()
        assert(picked == com.tjshea.vigilant.data.scanner.FeedSort.START)
    }

    @Test fun theFeedCanShowOnlyGamesStartingSoon() {
        var picked: Int? = null
        screen { FeedScreen(SampleScan.state().let { it.copy(settings = it.settings.copy(startsWithinHours = 24)) }, {}, {}, {}, { _, _ -> }, onStartsWithin = { picked = it }) }
        compose.onNodeWithText("Starts within").assertExists()
        // The picked window and sort are told to TalkBack, not only drawn in bold (full test, 2026-09-27).
        compose.onNodeWithText("24h").assertIsSelected()
        compose.onNodeWithText("48h").assertIsNotSelected()
        compose.onNodeWithText("Best EV").assertIsSelected()
        compose.onNodeWithText("Soonest").assertIsNotSelected()
        compose.onNodeWithText("12h").performClick()
        assert(picked == 12) { "picked $picked" }
        compose.onNodeWithText("Any time").performClick()
        assert(picked == 0) { "picked $picked" }
    }

    @Test fun startsWithinFilterShot() = shoot("1c_feed_starts_within_24h") {
        FeedScreen(SampleScan.state().let { it.copy(settings = it.settings.copy(startsWithinHours = 24)) }, {}, {}, {}, { _, _ -> })
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
        openSettingsTab(SettingsTab.CNO)
        // Off by default (Tj, 2026-09-29: the widget opens only from its button); the switch turns the auto-open on.
        compose.onNodeWithText("The widget opens only when you press its button at the top of the list.").assertExists()
        compose.onNodeWithText("Also open it when I leave Vigilant").assertExists()
        compose.onNodeWithText("Also open it when I leave Vigilant").performClick()
        assert(picked?.miniWindow == true) { "picked $picked" }
    }

    // ---- The CNO scanner (RESEARCH.md §18–19): its tab, bet detail, and in the mini window ----

    @Test fun cnoTab() {
        shoot("8_cno") { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), {}, {}) }
        compose.onNodeWithText("Justin Jefferson Under 69.5").assertIsDisplayed()
        compose.onNodeWithText("Conservative worst case · to +150 · 4+ books · ≥1% EV").assertIsDisplayed()
        compose.onNodeWithText("View: Novig · 3+ books").assertIsDisplayed()
        compose.onNodeWithText("Read 20s ago · odds 49s old · every 15 s", substring = true).assertIsDisplayed()
        compose.onNodeWithText("4 bets pass · 2 hidden: 1 too few books, 1 longer odds than your cap").assertIsDisplayed()
        compose.onAllNodesWithText("Walker Buehler Over 15.5").assertCountEquals(0) // 3 books: too thin
        compose.onAllNodesWithText("\$88.00").onFirst().assertIsDisplayed() // dollars available
    }

    /** RESEARCH.md §24: CNO's EVs rest on other books' prices; once its odds are over 5 minutes old, none are offered. */
    @Test fun cnoTabHidesItsBetsWhileCnosOddsAreOld() {
        screen(now = SampleScan.NOW + 6 * 60_000L) { com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.state(), {}, {}) }
        compose.onNodeWithText("CrazyNinjaOdds' odds are too old").assertIsDisplayed()
        compose.onAllNodesWithText(SampleCno.rows[1].bet, substring = true).assertCountEquals(0)
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

    /** Tj, 2026-09-27: "select the time periods 12h 24h 48h and anytime for the cno scanner … as well". */
    @Test fun cnoTabPicksTheStartTimeWindow() {
        var picked: Int? = null
        val s = SampleCno.state().let { it.copy(settings = it.settings.copy(startsWithinHours = 12)) }
        shoot("8e_cno_starts_within_12h") { com.tjshea.vigilant.app.ui.CnoScreen(s, {}, {}, onStartsWithin = { picked = it }) }
        compose.onNodeWithText("Starts within").assertIsDisplayed()
        // 12h: Ohio (8 h out) is listed, the three bets 24 h out aren't, and the count says why.
        compose.onNodeWithText("Ohio -33.5").assertIsDisplayed()
        compose.onAllNodesWithText("Justin Jefferson Under 69.5").assertCountEquals(0)
        compose.onNodeWithText("3 start after 12h", substring = true).assertIsDisplayed()
        compose.onNodeWithText("24h").performClick()
        assertEquals(24, picked)
        compose.onNodeWithText("Any time").performClick()
        assertEquals(0, picked)
    }

    @Test fun cnoTabHidesTheWindowWhenCnoIsOff() {
        val s = SampleCno.state().let { it.copy(settings = it.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT)) }
        screen { com.tjshea.vigilant.app.ui.CnoScreen(s, {}, {}) }
        compose.onAllNodesWithText("Starts within").assertCountEquals(0)
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
        compose.onNodeWithTag("openInSheet").assertExists()
    }

    /**
     * Tj, 2026-09-28: "just like the vigilant positive EV tab has buttons to open the bet in novig, put these same buttons in
     * the cno scanner in full screen (it already works in the widget)". One tap on a CNO card opens its bet (not its books),
     * with the stake Settings asks for.
     */
    @Test fun everyCnoBetHasAOneTapOpenInNovigButtonWithTheChosenStake() {
        val base = SampleCno.state()
        val first = base.cnoShown(SampleScan.NOW).maxBy { base.livePick(it, SampleScan.NOW).ev }
        val opened = ArrayList<com.tjshea.vigilant.data.cno.CnoRow>()
        var settings by androidx.compose.runtime.mutableStateOf(base.settings)
        screen { com.tjshea.vigilant.app.ui.CnoScreen(base.copy(settings = settings), {}, {}, onOpenInNovig = { opened += it }) }
        compose.onAllNodesWithTag("openBet").onFirst().assertIsDisplayed()
        compose.onAllNodesWithTag("openBet").onFirst().performClick()
        assertEquals(listOf(first.row.key), opened.map { it.key })
        // The button opens the bet, not the card's sheet.
        compose.onAllNodesWithTag("openInSheet").assertCountEquals(0)
        // With $1 in the slip, the button says so.
        settings = base.settings.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR)
        compose.onAllNodesWithText("Open in Novig · $1").onFirst().assertIsDisplayed()
    }

    /** The CNO card's button spins while that bet's link is found (the widget's spinner, on the tab). */
    @Test fun cnoCardsOpenButtonSaysItsOpening() {
        val base = SampleCno.state()
        val first = base.cnoShown(SampleScan.NOW).maxBy { base.livePick(it, SampleScan.NOW).ev }
        screen { com.tjshea.vigilant.app.ui.CnoScreen(base, {}, {}, opening = first.row.key) }
        compose.onAllNodesWithText("Opening…").assertCountEquals(1)
    }

    /** The CNO card's stake is the same one "Open in Novig" puts in the slip: its Kelly stake, $1 at least. */
    @Test fun aCnoBetsSlipStakeIsItsKellyStake() {
        val base = SampleCno.state()
        val pick = base.cnoShown(SampleScan.NOW).first { com.tjshea.vigilant.app.ui.cnoStake(it, base.settings) != null }
        val kelly = base.settings.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.KELLY)
        val stake = com.tjshea.vigilant.app.ui.cnoStake(pick, kelly)!!
        assertEquals(maxOf(1.0, Math.round(stake * 100) / 100.0), com.tjshea.vigilant.app.ui.cnoSlipStake(pick, kelly)!!, 1e-9)
        assertEquals(null, com.tjshea.vigilant.app.ui.cnoSlipStake(pick, base.settings))
        assertEquals(" · $1", com.tjshea.vigilant.app.ui.cnoSlipStakeSuffix(pick, base.settings.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR)))
    }

    @Test fun cnoCardsWithTheOpenInNovigButton() = shoot("8g_cno_open_buttons") {
        com.tjshea.vigilant.app.ui.CnoScreen(SampleCno.withBooks().let { it.copy(settings = it.settings.copy(slipStake = com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR)) }, {}, {})
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
        openSettingsTab(SettingsTab.CNO)
        compose.onNodeWithText("Shared View link").assertExists()
        compose.onNodeWithText("Longest odds").assertExists()
        // CNO's +150 here; Vigilant's own odds cap's (since v0.18.0) is on the +EV feed tab.
        compose.onAllNodesWithText("+150").assertCountEquals(1)
        compose.onAllNodesWithText("+150")[0].assertIsSelected()
        compose.onNodeWithText("Fewest books behind the fair price").assertExists()
        // The chips read as numbers (a first draft printed "${'$'}it+" on every one): 1+ to 4+ since v0.19.3 (Tj: "add
        // options for 1 and 2 books. Remove any option over 4 books"), 4+ picked by default.
        compose.onNodeWithText("1+").assertExists()
        compose.onNodeWithText("2+").assertExists()
        compose.onNodeWithText("4+").assertIsSelected()
        compose.onAllNodesWithText("5+").assertCountEquals(0)
        compose.onAllNodesWithText("10+").assertCountEquals(0)
        compose.onNodeWithText("50").assertExists()
        compose.onAllNodesWithText("${'$'}it", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Real time").assertExists()
        compose.onNodeWithText("Tap only").assertExists()
        compose.onNodeWithText("+100").performClick()
        assert(picked?.cnoFilters?.maxOdds == 100) { "picked $picked" }
        // The scanner choice is on the Scan tab; Vigilant's own odds cap on the +EV feed tab.
        openSettingsTab(SettingsTab.SCAN)
        compose.onNodeWithText("CNO only").performClick()
        assert(picked?.scanner == com.tjshea.vigilant.data.scanner.ScannerMode.CNO) { "picked $picked" }
        openSettingsTab(SettingsTab.FEED)
        compose.onAllNodesWithText("+150").assertCountEquals(1)
    }

    /** Tj, 2026-09-28: background auto-scan (CNO, or CNO + Vigilant, every 5-40 min) and alerts at 2/3/4%+. */
    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun settingsOfferBackgroundAutoScanAndAlerts() {
        val base = SampleScan.state()
        val s = base.copy(settings = base.settings.copy(autoScan = com.tjshea.vigilant.data.scanner.AutoScanMode.BOTH, autoScanMinutes = 10))
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        shoot("5d_settings_auto_scan") { SettingsScreen(s, { t -> picked = t(s.settings) }) }
        compose.onNodeWithText("Background auto-scan", ignoreCase = true).assertExists()
        compose.onNodeWithText("CNO + Vigilant").assertIsSelected()
        compose.onNodeWithText("Every 10 min, with Vigilant open or closed", substring = true).assertExists()
        compose.onNodeWithText("Push alerts").assertExists()
        compose.onNodeWithText("3%+").assertIsSelected()
        compose.onNodeWithText("40 min").performClick()
        assert(picked?.autoScanMinutes == 40) { "picked $picked" }
        compose.onNodeWithText("4%+").performClick()
        assert(picked?.alertMinEv == 0.04) { "picked $picked" }
        compose.onNodeWithText("2%+").performClick()
        assert(picked?.alertMinEv == 0.02) { "picked $picked" }
    }

    /** Off: no interval to pick, and the hint says nothing runs by itself. */
    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun autoScanOffHidesTheInterval() {
        screen { SettingsScreen(SampleScan.state(), {}) }
        compose.onAllNodesWithText("40 min").assertCountEquals(0)
        compose.onNodeWithText("Off: Vigilant scans only when you tap Scan, and CrazyNinjaOdds", substring = true).assertExists()
    }

    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
    @Test fun cnoOnlySettingsHideWhatsAsleep() {
        val base = SampleScan.state()
        val s = base.copy(settings = base.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        shoot("5c_settings_cno_only") { SettingsScreen(s, {}) }
        // Vigilant's pages go with its scanner: no fair odds, feed or API usage tab. The Novig key stays on the Betting one: CNO's cards bet
        // through Novig's API too, and a Bet sheet's "Add money" lands on its wallet (Tj, 2026-09-29).
        for (gone in listOf(SettingsTab.FAIR, SettingsTab.FEED, SettingsTab.USAGE)) compose.onAllNodesWithTag("settingsTab-${gone.name}").assertCountEquals(0)
        compose.onAllNodesWithText("Fair odds method", ignoreCase = true).assertCountEquals(0)
        openSettingsTab(SettingsTab.CNO)
        compose.onNodeWithText("CNO scanner", ignoreCase = true).assertExists()
        openSettingsTab(SettingsTab.BETTING)
        compose.onNodeWithText("Bankroll & Kelly", ignoreCase = true).assertExists()
        compose.onAllNodesWithText("Novig API key", ignoreCase = true).assertCountEquals(1)
    }

    /** The other half of the check above: with both scanners on, those sections are there. */
    @Config(qualifiers = "w393dp-h5200dp-xxhdpi")
    @Test fun bothScannersSettingsShowVigilantsSections() {
        screen { SettingsScreen(SampleScan.state(), {}) }
        openSettingsTab(SettingsTab.FAIR)
        compose.onNodeWithText("Fair odds method", ignoreCase = true).assertExists()
        openSettingsTab(SettingsTab.USAGE)
        compose.onNodeWithText("API usage", ignoreCase = true).assertExists()
        openSettingsTab(SettingsTab.CNO)
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
        screen { SettingsScreen(SampleCno.state(), { t -> picked = t(SampleScan.settings) }, startTab = SettingsTab.CNO) }
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
        var picked: com.tjshea.vigilant.data.scanner.ScannerMode? = null
        floating("9n_floating_switch_cno", floatingState(), actions = com.tjshea.vigilant.app.ui.FloatingActions(onScanner = { picked = it }))
        compose.onNodeWithText("CNO only").assertIsDisplayed()
        compose.onNodeWithContentDescription("Showing CNO only. Tap for CNO and Vigilant's scan").performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.ScannerMode.BOTH, picked)
    }

    /** Tj, 2026-09-27: "select the time periods 12h 24h 48h and anytime for the … cno widget as well". */
    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetsTopBarPicksTheStartTimeWindow() {
        var picked: Int? = null
        val s = floatingState().let { it.copy(settings = it.settings.copy(startsWithinHours = 24)) }
        floating("9q_floating_starts_within_24h", s, actions = com.tjshea.vigilant.app.ui.FloatingActions(onStartsWithin = { picked = it }))
        compose.onNodeWithText("24h").assertIsDisplayed()
        compose.onNodeWithContentDescription("Showing games starting within 24 hours. Tap for within 48 hours").performClick()
        assertEquals(48, picked)
    }

    /** Everything CNO listed starts after the window: the widget says so instead of "no +EV". */
    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetSaysWhenTheWindowHidesEverything() {
        val base = floatingState()
        val s = base.copy(settings = base.settings.copy(startsWithinHours = 12), cno = base.cno.copy(snapshot = base.cno.snapshot!!.copy(rows = base.cno.snapshot!!.rows.map { it.copy(startsAtMs = SampleScan.NOW + 30 * 3_600_000L) })))
        floating("9r_floating_all_later", s)
        compose.onNodeWithText("Nothing starting within 12h", substring = true).assertIsDisplayed()
        compose.onNodeWithText("12h").assertIsDisplayed()
    }

    /**
     * Tj, 2026-09-27: "on the regular vigilant scanner, make it also have a widget". The widget on
     * Vigilant's scan alone: its bets, Scan and Recheck, no CNO buttons, and the switch goes on to CNO.
     */
    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetWorksOnVigilantsScanAlone() {
        var picked: com.tjshea.vigilant.data.scanner.ScannerMode? = null
        var opened: MiniWindow.Item? = null
        val s = SampleScan.state().copy(settings = SampleScan.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT, floatingWidget = true))
        val first = MiniWindow.items(s, SampleScan.NOW).first()
        floating("9p_floating_vigilant_only", s, actions = com.tjshea.vigilant.app.ui.FloatingActions(onScanner = { picked = it }, onOpenBet = { opened = it }))
        compose.onNodeWithText("Vigilant").assertIsDisplayed()
        compose.onNodeWithContentDescription("Scan").assertExists()
        compose.onAllNodesWithContentDescription("Books").assertCountEquals(0)
        compose.onNodeWithText(first.title).performClick()
        // The exact bet slip, not just Novig's app.
        assertEquals("novigapp://events/${first.outcomeId}", opened?.let { MiniWindow.novigLink(it) })
        compose.onNodeWithContentDescription("Showing Vigilant's scan only. Tap for CNO only").performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.ScannerMode.CNO, picked)
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetWithBothListsABetBothScannersFoundOnce() {
        var picked: com.tjshea.vigilant.data.scanner.ScannerMode? = null
        val feedItem = MiniWindow.items(SampleScan.state().copy(settings = SampleScan.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT)), SampleScan.NOW).first()
        val ohio = SampleCno.rows[2]
        val s = SampleCno.state().copy(cnoLinks = mapOf(com.tjshea.vigilant.data.cno.CnoFeed.linkKey(ohio) to "novigapp://events/${feedItem.outcomeId}/cno"))
        floating("9o_floating_both_merged", s, actions = com.tjshea.vigilant.app.ui.FloatingActions(onScanner = { picked = it }))
        compose.onNodeWithText("Both").assertIsDisplayed()
        compose.onAllNodesWithText("Ohio -33.5").assertCountEquals(0) // folded into Vigilant's row
        compose.onNodeWithText("CNO +5.3%", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Showing CNO and Vigilant's scan. Tap for Vigilant's scan only").performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT, picked)
    }

    @Config(qualifiers = "w393dp-h6400dp-xxhdpi")
    @Test fun settingsOfferVigilantsScanAgainWhileTheWidgetIsOpen() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleCno.state(), { t -> picked = t(SampleScan.settings) }, startTab = SettingsTab.CNO) }
        compose.onNodeWithText("Vigilant's scan again while the widget is open").assertExists()
        compose.onNodeWithText("10 min").performClick()
        assertEquals(10, picked?.widgetRescanMinutes)
    }

    /** RESEARCH.md §23: with a PropLine key, The Odds API says it only backs PropLine up, and what that costs. */
    @Config(qualifiers = "w393dp-h7400dp-xxhdpi")
    @Test fun settingsSayTheOddsApiBacksUpPropLine() {
        screen { SettingsScreen(SampleScan.state(), { }, startTab = SettingsTab.FAIR) }
        compose.onNodeWithText("Backup to PropLine", substring = true).assertExists()
        compose.onNodeWithText("Nothing while PropLine answers", substring = true).assertExists()
        compose.onNodeWithText("Read from PropLine first", substring = true).assertExists()
    }

    /** Tj, 2026-09-27: "on the regular vigilant scanner, make it also have a widget": offered with Vigilant's scan alone too. */
    @Config(qualifiers = "w393dp-h7400dp-xxhdpi")
    @Test fun settingsOfferTheFloatingWidgetOnVigilantsScanAlone() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        val s = SampleScan.state().copy(settings = SampleScan.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT, floatingWidget = false))
        screen { SettingsScreen(s, { t -> picked = t(s.settings) }, startTab = SettingsTab.CNO) }
        compose.onNodeWithText("Floating widget you can touch").assertExists().performClick()
        assertEquals(true, picked?.floatingWidget)
        compose.onNodeWithText("Vigilant's scan again while the widget is open").assertExists()
        compose.onAllNodesWithText("CNO is read only", substring = true).assertCountEquals(0)
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

    // Tall enough for every card under the CNO tab's header (its "Starts within" row since v0.17.1).
    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
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
        screen { SettingsScreen(SampleCno.state(), { t -> picked = t(SampleScan.settings) }, startTab = SettingsTab.CNO) }
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
    // Tall enough for every card under the CNO tab's header (its "Starts within" row since v0.17.1).
    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
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
        screen { SettingsScreen(SampleCno.state(), { }, startTab = SettingsTab.CNO) }
        compose.onNodeWithText("✕ to remove it without betting", substring = true).assertExists()
        compose.onNodeWithText("switch picks CNO only, Both or Vigilant only", substring = true).assertExists()
    }

    // ---- N (2026-09-27): every ✓ tracked, auto-settled, "now ±x% EV", a stats section -----------

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun trackerStats() {
        shoot("4_tracker") { TrackerScreen(SampleScan.state(), { _, _ -> }, {}) }
        // b1 won, b2 lost, b6 won (settled from the final score): 2-1, and profit is +$34.52 − $18 + $1.
        compose.onNodeWithText("66.7%").assertExists()
        compose.onNodeWithText("2-1").assertExists()
        compose.onNodeWithText("+$17.52").assertExists()
        // The split by scanner, league, market, edge and price is one card now (TASKS.md T6).
        compose.onNodeWithText("Where it's working").assertExists()
        compose.onNodeWithText("Are the edges real?").assertExists()
    }

    @Config(qualifiers = "w393dp-h2000dp-xxhdpi")
    @Test fun trackerBetsShowTheirEvNowGreenOrRed() {
        shoot("4b_tracker_bets") { TrackerScreen(SampleScan.state(), { _, _ -> }, {}, initialView = com.tjshea.vigilant.app.ui.TrackerView.BETS) }
        // The EV the fair odds now give the price each bet was placed at (+100 and -110), "now" while the read is young.
        compose.onNodeWithText("now +3.1% EV at your +100").assertExists()
        compose.onNodeWithText("now −2.1% EV at your -110").assertExists()
        compose.onNodeWithText("Open (3)").assertExists()
        // Open bets only by default: the settled ones are one tap away.
        compose.onAllNodesWithText("Won · undo").assertCountEquals(0)
        compose.onNodeWithText("Settled (3)").performClick()
        compose.onNodeWithText("from the final score").assertExists()
    }

    /** Tj, 2026-09-27: bets over ±6% EV "ignore them completely" in the stats; still listed under Bets. */
    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun trackerLeavesOutlierBetsOutOfTheStats() {
        val base = SampleScan.state()
        val outlier = base.bets.first { it.id == "b1" }.copy(id = "big", selection = "Big Outlier", evPercentAtBet = 0.18, stake = 50.0)
        val s = base.copy(bets = base.bets + outlier)
        shoot("4c_tracker_outlier") { TrackerScreen(s, { _, _ -> }, {}) }
        // The same record and profit as without it (trackerStats): a $50 win at +18% EV counts for nothing.
        compose.onNodeWithText("2-1").assertExists()
        compose.onNodeWithText("+$17.52").assertExists()
        compose.onNodeWithText("1 outlier bet (over ±6% EV when bet)", substring = true).assertExists()
    }

    @Config(qualifiers = "w393dp-h2000dp-xxhdpi")
    @Test fun anOutlierBetSaysItIsNotInTheStats() {
        val base = SampleScan.state()
        val outlier = base.bets.first { it.id == "b4" }.copy(id = "big", selection = "Big Outlier", evPercentAtBet = -0.08)
        screen { TrackerScreen(base.copy(bets = base.bets + outlier), { _, _ -> }, {}, initialView = com.tjshea.vigilant.app.ui.TrackerView.BETS) }
        compose.onNodeWithText("Outlier (over ±6% EV): not in stats").assertExists()
        compose.onAllNodesWithText("not in stats", substring = true).assertCountEquals(1)
    }

    @Test fun trackerStakeIsEditable() {
        screen { TrackerScreen(SampleScan.state(), { _, _ -> }, {}, initialView = com.tjshea.vigilant.app.ui.TrackerView.BETS) }
        // Tapping the stake opens its dialog (not opened here: a text field in a Robolectric dialog never idles).
        compose.onAllNodesWithText("Stake ✎")[0].assertHasClickAction()
    }

    @Test fun trackerSaysCloseForEverySportNotJustFootball() {
        screen { TrackerScreen(SampleScan.state(), { _, _ -> }, { }) }
        // The closing line is read before any game's start, whatever the sport (ClosingLine).
        compose.onNodeWithText("devigged fair line read in the last 15 minutes before the start", substring = true).performScrollTo().assertExists()
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

    // ---- Tj, 2026-09-28: "Make an option in the app to pause all scanning" ------------------------------------

    @Test fun thePauseButtonOnTheFeedPausesAndTheBannerResumes() {
        val picked = ArrayList<Boolean>()
        var paused by androidx.compose.runtime.mutableStateOf(false)
        screen { FeedScreen(SampleScan.state().let { it.copy(settings = it.settings.copy(paused = paused)) }, {}, {}, {}, { _, _ -> }, onPause = { picked += it; paused = it }) }
        compose.onAllNodesWithText(com.tjshea.vigilant.app.ui.PAUSED_TEXT).assertCountEquals(0)
        compose.onNodeWithContentDescription("Pause all scanning").performClick()
        assertEquals(listOf(true), picked)
        compose.onNodeWithText(com.tjshea.vigilant.app.ui.PAUSED_TEXT).assertIsDisplayed()
        compose.onNodeWithText("  Scan").assertIsNotEnabled()
        compose.onNodeWithText("Resume").performClick()
        assertEquals(listOf(true, false), picked)
        compose.onNodeWithContentDescription("Pause all scanning").assertIsDisplayed()
    }

    @Test fun feedWhilePaused() = shoot("1k_feed_paused") {
        FeedScreen(SampleScan.state().let { it.copy(settings = it.settings.copy(paused = true)) }, {}, {}, {}, { _, _ -> })
    }

    @Test fun theCnoTabSaysItsPausedAndResumes() {
        val picked = ArrayList<Boolean>()
        val base = SampleCno.state()
        shoot("8h_cno_paused") { com.tjshea.vigilant.app.ui.CnoScreen(base.copy(settings = base.settings.copy(paused = true)), {}, {}, onPause = { picked += it }) }
        compose.onNodeWithText("Paused · read 20s ago").assertIsDisplayed()
        compose.onNodeWithText(com.tjshea.vigilant.app.ui.PAUSED_TEXT).assertIsDisplayed()
        // Refresh waits too (a pull says it's paused).
        compose.onNodeWithContentDescription("Refresh CrazyNinjaOdds").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Resume scanning").performClick()
        assertEquals(listOf(false), picked)
    }

    /** Paused before CNO's list was ever read: not "Reading CrazyNinjaOdds…" forever. */
    @Test fun theCnoTabPausedBeforeItsFirstReadSaysPaused() {
        val base = SampleCno.state(cno = com.tjshea.vigilant.data.cno.CnoState())
        screen { com.tjshea.vigilant.app.ui.CnoScreen(base.copy(settings = base.settings.copy(paused = true)), {}, {}) }
        compose.onNodeWithText("Scanning is paused").assertIsDisplayed()
        compose.onAllNodesWithText("Reading CrazyNinjaOdds…").assertCountEquals(0)
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetPausesAndResumesFromItsTopBar() {
        val picked = ArrayList<Boolean>()
        val s = floatingState()
        floating("9p_floating_paused", s.copy(settings = s.settings.copy(paused = true)), actions = com.tjshea.vigilant.app.ui.FloatingActions(onPause = { picked += it }))
        compose.onNodeWithText("Paused", substring = true).assertIsDisplayed()
        // Refresh waits with it (greyed, not a silent tap).
        compose.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Resume scanning").performClick()
        assertEquals(listOf(false), picked)
        assertTrue(com.tjshea.vigilant.app.ui.miniStatus(s.copy(settings = s.settings.copy(paused = true)), SampleScan.NOW).startsWith("Paused · "))
        assertFalse(com.tjshea.vigilant.app.ui.miniStatus(s, SampleScan.NOW).startsWith("Paused"))
        assertEquals("Scanning is paused · tap ▶ to resume", com.tjshea.vigilant.app.ui.emptyText(s.copy(settings = s.settings.copy(paused = true)), floating = true))
    }

    @Config(qualifiers = "w380dp-h320dp-xxhdpi")
    @Test fun theWidgetsPauseButton() {
        val picked = ArrayList<Boolean>()
        floating("9q_floating_pause_button", floatingState(), actions = com.tjshea.vigilant.app.ui.FloatingActions(onPause = { picked += it }))
        compose.onNodeWithContentDescription("Pause all scanning").performClick()
        assertEquals(listOf(true), picked)
    }

    @Test fun settingsOfferPauseAllScanning() {
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleScan.state(), { t -> picked = t(SampleScan.settings) }) }
        compose.onNodeWithText("Pause all scanning").performClick()
        assertEquals(true, picked?.paused)
    }

    // ---- Tj, 2026-09-28: "Add unlimited options in the vigilant app for all types of scans that can benefit from unlimited …
    // make sure the app doesn't just scan continuously, it should stop the scan when all the markets are finished scanning for
    // the selected time period" ---------------------------------------------------------------------------------------------

    @Test fun settingsOfferNoLimitOnEveryScanCapThatCanUseIt() {
        val nl = com.tjshea.vigilant.data.scanner.ScanSettings.NO_LIMIT
        var picked: com.tjshea.vigilant.data.scanner.ScanSettings? = null
        screen { SettingsScreen(SampleScan.state(), { t -> picked = t(SampleScan.settings) }) }
        // Each "No limit" chip sets its own cap: Novig prices (+EV feed tab), The Odds API credits and PropLine's games (Fair odds tab).
        val set = HashSet<String>()
        for (tab in listOf(SettingsTab.FEED, SettingsTab.FAIR)) {
            openSettingsTab(tab)
            val noLimit = compose.onAllNodesWithText("No limit")
            repeat(noLimit.fetchSemanticsNodes().size) { i ->
                noLimit[i].performScrollTo().performClick()
                val p = picked!!
                if (p.maxBooksPerScan == nl) set += "prices"
                if (p.bookPropCreditsPerScan == nl) set += "credits"
                if (p.propLineGamesPerScan == nl) set += "propline"
            }
        }
        assertEquals(setOf("prices", "credits", "propline"), set)
        // "All": lines and props per game (+EV feed tab), and the sportsbook-props window (Fair odds tab).
        val all = HashSet<String>()
        for (tab in listOf(SettingsTab.FEED, SettingsTab.FAIR)) {
            openSettingsTab(tab)
            val allChips = compose.onAllNodesWithText("All")
            repeat(allChips.fetchSemanticsNodes().size) { i ->
                allChips[i].performScrollTo().performClick()
                val p = picked!!
                if (p.linesPerGame == nl) all += "lines"
                if (p.propsPerGame == nl) all += "props"
                if (p.bookPropHours == nl) all += "hours"
            }
        }
        assertTrue(all.toString(), all.containsAll(setOf("lines", "props", "hours")))
    }

    @Test fun theNoLimitHintsSayWhatBoundsTheScan() {
        val nl = com.tjshea.vigilant.data.scanner.ScanSettings.NO_LIMIT
        val prices = com.tjshea.vigilant.app.ui.scanSizeHint(nl)
        assertTrue(prices, prices.contains("each read once; then the scan stops"))
        assertTrue(prices, prices.contains("never runs past about 8 minutes"))
        assertEquals("as long as the games take, 10 minutes at most", com.tjshea.vigilant.app.ui.scanTime(nl))
        val credits = com.tjshea.vigilant.app.ui.creditWorstCase(nl)
        assertTrue(credits, credits.contains("free 500 a month can go in one or two scans"))
        val s = SampleScan.settings.copy(bookPropCreditsPerScan = nl, bookPropHours = nl, daysAhead = 7)
        assertTrue(com.tjshea.vigilant.app.ui.bookPropEstimate(s).startsWith("No limit: every game with props in the next 7 days"))
        assertTrue(com.tjshea.vigilant.app.ui.propLineGamesHint(s.copy(propLineGamesPerScan = nl)).contains("1,000 a day can run out"))
        assertEquals("the next 12 hours", com.tjshea.vigilant.app.ui.windowLabel(12))
        assertEquals("the next day", com.tjshea.vigilant.app.ui.windowLabel(24))
        // Games past "Starts within" point there, not at Days ahead.
        val narrow = SampleScan.settings.copy(daysAhead = 7, startsWithinHours = 12)
        assertTrue(com.tjshea.vigilant.app.ui.laterGamesText(5, narrow).contains("widen Starts within and scan again"))
        assertTrue(com.tjshea.vigilant.app.ui.laterGamesText(5, narrow.copy(startsWithinHours = 0)).contains("raise Days ahead"))
    }

    /** "Starts within" widened after a scan that read only 12 hours: the feed says the rest needs a scan. */
    @Test fun theFeedSaysWhenTheWindowIsWiderThanTheLastScan() {
        var scans = 0
        val base = SampleScan.state()
        val s = base.copy(
            settings = base.settings.copy(startsWithinHours = 24, daysAhead = 7),
            status = base.status.copy(scannedWindowHours = 12),
        )
        var scanned by androidx.compose.runtime.mutableStateOf(12)
        shoot("1l_feed_window_widened") { FeedScreen(s.copy(status = s.status.copy(scannedWindowHours = scanned)), { scans++ }, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("The last scan read games starting in the next 12 hours. Scan to add the rest of the next day.").assertIsDisplayed()
        compose.onAllNodesWithText("Scan").onFirst().performClick()
        assertEquals(1, scans)
        // The same window as the scan: nothing to say.
        scanned = 24
        compose.onAllNodesWithText("Scan to add the rest", substring = true).assertCountEquals(0)
    }
}
