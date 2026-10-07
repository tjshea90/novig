package com.tjshea.vigilant.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.MakerActions
import com.tjshea.vigilant.app.ui.MakerRulesText
import com.tjshea.vigilant.app.ui.MakerScreen
import com.tjshea.vigilant.app.ui.MakerText
import com.tjshea.vigilant.app.ui.LowUsageText
import com.tjshea.vigilant.app.ui.MakerUi
import com.tjshea.vigilant.app.ui.TrapGuardText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerDecision
import com.tjshea.vigilant.data.novig.trading.maker.MakerLines
import com.tjshea.vigilant.data.novig.trading.maker.MakerQuote
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Bids tab (Tj, 2026-10-03: "build the system in the app … It may need a separate section in the app"; RESEARCH.md §70): what it says and what its
 * buttons send, from fixed state (the sample scan's lines, one bid resting, one filled).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h4200dp-xxhdpi")
class MakerUiTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW
    private val settings = SampleScan.settings.copy(maker = true)

    private fun decisions(s: ScanSettings = settings): List<MakerDecision> =
        MakerQuote.decideAll(MakerLines.from(SampleScan.result(), s, now), MakerRules.of(s), now, emptySet())

    private fun bid(outcome: String, status: MakerStatus, filled: Long = 0, orderId: String = "o-$outcome") = MakerBid(
        clientId = "c-$outcome", orderId = orderId, marketId = "m", eventId = "g1", outcomeId = outcome, league = "NFL",
        eventName = "Baltimore Ravens @ Dallas Cowboys", startsTs = now + 50 * 3_600_000L, marketLabel = "Passing Yards", selection = "Lamar Jackson Over 224.5",
        kind = BetKind.PROP, price = 0.455, contracts = 1_098, fair = 0.474, evAtFair = 0.474 / 0.455 - 1, margin = 0.04, postedAtMs = now - 10 * 60_000,
        expiresAtMs = now + 20 * 60_000, status = status, filled = filled, paid = filled * 0.455 * 0.01,
    )

    private fun ui(s: ScanSettings = settings, setUp: Boolean = true, vigilantOn: Boolean = true, bids: List<MakerBid> = listOf(bid("rest-1", MakerStatus.RESTING))) = MakerUi(
        settings = s, setUp = setUp, vigilantOn = vigilantOn, bids = bids, decisions = decisions(s), scanAtMs = now - 2 * 60_000,
        lastPassAtMs = now - 60_000, running = false, problem = null, bets = emptyList(), now = now,
    )

    /** A list that counts how often its elements are walked: the work a screen does with it. */
    private class WalkCounted<T>(private val inner: List<T>) : java.util.AbstractList<T>() {
        var walks = 0
        override val size: Int get() = inner.size
        override fun get(index: Int): T = inner[index]
        override fun iterator(): MutableIterator<T> {
            walks++
            return inner.toMutableList().iterator()
        }
    }

    /**
     * Tj, 2026-10-03 (v0.56.1 Diagnostics, "so laggy I almost couldn't use it" while auto-bid ran): the tab filtered, sorted and grouped the pass's
     * thousands of decisions again for every state the app published (three a second in a scan), and several times within each. The lists are worked
     * out once for the same bids and decisions, however many times the screen asks and however many [MakerUi]s it builds from them.
     */
    @Test
    fun `the tab's lists are worked out once for the same bids and decisions, not on every state`() {
        val decided = WalkCounted(decisions())
        val bids = listOf(bid("rest-1", MakerStatus.RESTING), bid("fill-1", MakerStatus.FILLED, filled = 500))
        // The root builds a new MakerUi for every state it takes; the lists inside it come from the same flows.
        fun built() = MakerUi(
            settings = settings, setUp = true, vigilantOn = true, bids = bids, decisions = decided, scanAtMs = now - 2 * 60_000,
            lastPassAtMs = now - 60_000, running = false, problem = null, bets = emptyList(), now = now,
        )
        val first = built()
        val ready = first.ready
        val skipped = first.skipped
        assertEquals(listOf("rest-1"), first.resting.map { it.outcomeId })
        assertEquals(listOf("fill-1"), first.filled.map { it.outcomeId })
        assertTrue(ready.isNotEmpty() && skipped.isNotEmpty())
        val walked = decided.walks
        repeat(10) {
            val u = built()
            assertEquals(ready, u.ready)
            assertEquals(skipped, u.skipped)
            u.resting; u.filled
            MakerText.status(u)
            MakerText.fillSummary(u)
        }
        assertEquals("no more walks of the decisions", walked, decided.walks)
        // A finished pass hands over new decisions: worked out again, for them.
        val next = WalkCounted(decisions())
        built().copy(decisions = next).ready
        assertTrue(next.walks > 0)
    }

    @Test
    fun `the sample scan's prop and team total get bids, its game lines don't (they're off by default)`() {
        val d = decisions()
        val posts = d.filterIsInstance<MakerDecision.Post>()
        assertTrue(posts.isNotEmpty())
        assertTrue(posts.all { it.line.kind in ScanSettings.MAKER_DEFAULT_KINDS })
        assertTrue(posts.all { it.evAtFair >= 0.04 - 1e-9 && it.price < (it.line.offer ?: 1.0) })
        assertTrue(d.filterIsInstance<MakerDecision.Skip>().any { it.why == "Moneylines are off for bids" })
    }

    @Test
    fun `it shows the bids up, the ones ready to post and why the rest get none, and every button sends what it should`() {
        var cancelled: String? = null
        var posted: String? = null
        var s = settings
        var cancelAll = 0
        compose.setContent {
            VigilantTheme {
                MakerScreen(ui(), MakerActions(onUpdate = { f -> s = f(s) }, onCancel = { cancelled = it }, onPost = { posted = it }, onCancelAll = { cancelAll++ }))
            }
        }
        compose.onNodeWithTag("makerStatus").assertIsDisplayed()
        compose.onNodeWithText("1 resting · $5.00 held · 0 filled in the last 24 h · last pass 1m ago").assertIsDisplayed()
        compose.onNodeWithTag("cancelBid-o-rest-1").performClick()
        assertEquals("o-rest-1", cancelled)
        compose.onNodeWithTag("makerCancelAll").performClick()
        assertEquals(1, cancelAll)
        val first = ui().ready.first().line.outcomeId
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("postBid-$first"))
        compose.onNodeWithTag("postBid-$first").performClick()
        assertEquals(first, posted)
        // Off: nothing posted, nothing recommended.
        compose.onNodeWithTag("makerMode-OFF").performClick()
        assertFalse(s.maker)
        assertFalse(s.makerRecommend)
        // The rules: closed, they read as one line; open, a chip sets the margin.
        compose.onNodeWithText(MakerRulesText.summary(settings)).performClick()
        compose.onNodeWithText("2.5%").performClick()
        assertEquals(0.025, s.makerMargin, 1e-9)
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerSkippedToggle"))
        compose.onNodeWithTag("makerSkippedToggle").performClick()
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasText("Moneylines are off for bids", substring = true))
    }

    @Test
    fun `without betting set up or Vigilant's scanner it says what to do first`() {
        var opened = 0
        compose.setContent {
            val off = settings.copy(maker = false, makerRecommend = false, scanner = ScannerMode.CNO)
            VigilantTheme { MakerScreen(ui(s = off, setUp = false, vigilantOn = false, bids = emptyList()), MakerActions(onOpenBetting = { opened++ })) }
        }
        compose.onNodeWithText(MakerText.NEEDS_BETTING).assertIsDisplayed()
        compose.onNodeWithText(MakerText.NEEDS_VIGILANT, substring = true).assertIsDisplayed()
        // Without betting set up only Off can be picked.
        compose.onNodeWithTag("makerMode-AUTOMATIC").assertIsNotEnabled()
        compose.onNodeWithText("Set up").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the words - times, a bid, a fill, the reasons grouped`() {
        assertEquals("24 min", MakerText.span(24 * 60_000 - 5))
        assertEquals("3 h 10 min", MakerText.span(190 * 60_000L))
        assertEquals("2 d", MakerText.span(50 * 3_600_000L))
        assertEquals("Bid -122 · fair -106 · +4.0% EV at the fair · \$5.49 (1,000 contracts)", MakerText.bidLine(0.549, 0.515, 0.04, 1_000).replace('−', '-'))
        assertEquals("Outside the price window", MakerText.reasonGroup("A bid at 76.5% is outside the price window (10.0%-65.0%)"))
        assertEquals("Too few books behind the fair price", MakerText.reasonGroup("Only 1 book behind the fair price (fewest: 2)"))
        val filled = bid("f-1", MakerStatus.FILLED, filled = 1_098)
        assertTrue(MakerText.fillLine(filled, null, now).startsWith("Filled 1,098 of 1,098 at"))
        assertEquals("1 fill · +4.2% EV at the fair", MakerText.fillSummary(ui(bids = listOf(filled))))
        assertEquals(
            "4% under the fair (sharp book's if lower) · ¼ Kelly of \$1,000.00, up to \$10.00 a bid · Props, Team totals, 1st half / inning · up to 30 min (less if the fair goes old) · games within 6 h",
            MakerRulesText.summary(ScanSettings()),
        )
        assertTrue(MakerRulesText.summary(ScanSettings(trapEarlyHours = 0)).endsWith("(less if the fair goes old)"))
        assertEquals("\$5.00 a bid", MakerRulesText.stake(ScanSettings(makerStakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM)))
    }

    @Test
    fun `screenshot - the Bids tab`() {
        compose.setContent {
            VigilantTheme { MakerScreen(ui(bids = listOf(bid("rest-1", MakerStatus.RESTING), bid("f-1", MakerStatus.FILLED, filled = 1_098, orderId = "o-f"))), MakerActions()) }
        }
        compose.onRoot().captureRoboImage("screenshots/4p_bids_tab.png")
    }

    @Test
    fun `Approve and Deny on a recommendation, Undo on a denied side, and switching auto-make on asks first`() {
        var posted: String? = null
        var denied: String? = null
        var undone: String? = null
        var s = settings.copy(maker = false)
        val deniedSide = com.tjshea.vigilant.data.novig.trading.maker.DeniedBid("gone-1", now + 3_600_000L, "Someone Over 1.5", now - 60_000)
        compose.setContent {
            VigilantTheme {
                MakerScreen(
                    ui(s = settings.copy(maker = false)).copy(denied = listOf(deniedSide)),
                    MakerActions(onUpdate = { f -> s = f(s) }, onPost = { posted = it }, onDeny = { denied = it }, onUndoDeny = { undone = it }),
                )
            }
        }
        val first = ui(s = settings.copy(maker = false)).ready.first().line.outcomeId
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("denyBid-$first"))
        compose.onNodeWithTag("denyBid-$first").performClick()
        assertEquals(first, denied)
        compose.onNodeWithTag("postBid-$first").assertTextContains("Approve")
        compose.onNodeWithTag("postBid-$first").performClick()
        assertEquals(first, posted)
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("undoDeny-gone-1"))
        compose.onNodeWithTag("undoDeny-gone-1").performClick()
        assertEquals("gone-1", undone)
        // Fully automatic asks first: nothing changes until Switch on.
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerMode"))
        compose.onNodeWithTag("makerMode-AUTOMATIC").performClick()
        assertFalse(s.maker)
        compose.onNodeWithTag("makerConfirmOn").performClick()
        assertTrue(s.maker)
    }

    @Test
    fun `the Bids tab is there whatever the scanner, and picking a mode that bids turns on what bids need and says so`() {
        ScannerMode.entries.forEach { assertTrue(it.name, Tab.BIDS.shownIn(it)) }
        // CNO only, no background scan, paused: Recommend turns on Vigilant's scanner, the background scan with Vigilant every minute, and scanning.
        var s = settings.copy(maker = false, makerRecommend = false, scanner = ScannerMode.CNO, autoScan = AutoScanMode.OFF, autoScanSeconds = 600, pausedByHand = true, autoBet = false)
        compose.setContent {
            VigilantTheme { MakerScreen(ui(s = s, vigilantOn = false, bids = emptyList()), MakerActions(onUpdate = { f -> s = f(s) })) }
        }
        compose.onNodeWithTag("makerMode-RECOMMEND").performClick()
        assertTrue(s.makerRecommend)
        assertFalse(s.maker)
        assertEquals(ScannerMode.BOTH, s.scanner)
        assertEquals(AutoScanMode.BOTH, s.autoScan)
        assertEquals(60, s.autoScanSeconds)
        assertFalse(s.paused)
        compose.onNodeWithTag("makerTurnedOn").assertTextContains("Vigilant's scanner", substring = true)
    }

    @Test
    fun `a bidding mode with Vigilant's scan switched off since says what's missing and turns it back on in one tap, and fully automatic says why ready bids wait`() {
        var s = settings.copy(maker = true, scanner = ScannerMode.CNO, autoScan = AutoScanMode.BOTH, autoScanSeconds = 30)
        compose.setContent {
            VigilantTheme {
                MakerScreen(
                    ui(s = s, vigilantOn = false).copy(waiting = mapOf(com.tjshea.vigilant.data.novig.trading.maker.MakerPlan.MAX_BIDS_REACHED.format(20) to 12)),
                    MakerActions(onUpdate = { f -> s = f(s) }),
                )
            }
        }
        compose.onNodeWithTag("makerNeeds").assertIsDisplayed()
        compose.onNodeWithText("Turn on").performClick()
        assertEquals(ScannerMode.BOTH, s.scanner)
        assertTrue(s.maker)
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerWaiting"))
        compose.onNodeWithTag("makerWaiting").assertTextContains("12 ready bids wait: the most bids up at once (20) is reached")
    }

    @Test
    fun `the Auto-bet tab says what auto-make is set to and opens the Bids tab`() {
        var opened = 0
        compose.setContent {
            VigilantTheme {
                com.tjshea.vigilant.app.ui.AutoBetScreen(SampleScan.fresh(settings.copy(maker = true)), onUpdate = {}, onOpenBids = { opened++ })
            }
        }
        compose.onNodeWithTag("autoMakeLink").assertIsDisplayed()
        compose.onNodeWithText("Bids").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the +EV detail sheet's suggested bid uses the Bids tab's margin, not the old 2 percent`() {
        val s = settings.copy(makerMargin = 0.06)
        val o = SampleScan.result(s).opportunities.first { it.quote != null && it.makerBid(0.06) != null }
        compose.setContent { VigilantTheme { com.tjshea.vigilant.app.ui.OpportunityDetail(o, s, onRecheck = {}) {} } }
        compose.onNodeWithText("asks ${com.tjshea.vigilant.app.ui.Format.percent(0.06)} EV at the fair", substring = true).assertExists()
        compose.onNodeWithText("Bid up to").assertExists()
        compose.onNodeWithText("${com.tjshea.vigilant.app.ui.Format.american(o.makerBid(0.06)!!.price)} · ${com.tjshea.vigilant.app.ui.Format.percent(o.makerBid(0.06)!!.price)}").assertExists()
    }

    @Test
    fun `every Bids-tab setting search knows about is on the tab once its rules are open, the trap guard's hours among them`() {
        var s = settings
        compose.setContent {
            VigilantTheme { MakerScreen(ui(), MakerActions(onUpdate = { f -> s = f(s) })) }
        }
        compose.onNodeWithTag("makerRulesToggle").performClick()
        compose.waitForIdle()
        for (e in com.tjshea.vigilant.app.ui.SettingsIndex.entries.filter { it.bids && it.shown(settings) }) {
            assertTrue(
                "\"${e.title}\" should be on the Bids tab",
                compose.onAllNodesWithText(e.title, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        compose.onNodeWithText("12 h", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(12, s.trapEarlyHours)
    }

    /** v0.56.0 put the trap guard's move rule over game-line bids (RESEARCH.md §72.3): the Bids tab shows that switch once game lines get bids. */
    @Test
    fun `with game lines on for bids, the rules show the trap guard's move switch (the auto-bet's same switch), and not without them`() {
        var s = settings.copy(makerKinds = settings.makerKinds + BetKind.MONEYLINE)
        compose.setContent {
            VigilantTheme { MakerScreen(ui(s), MakerActions(onUpdate = { f -> s = f(s) })) }
        }
        compose.onNodeWithTag("makerRulesToggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("makerTrapMove", useUnmergedTree = true).performScrollTo().assertExists()
        compose.onNodeWithTag("makerTrapMoveNote", useUnmergedTree = true).assertTextContains("game-line bid", substring = true)
        compose.onNodeWithTag("makerTrapMove", useUnmergedTree = true).performClick()
        assertFalse(s.trapNovigMove)
        assertTrue(com.tjshea.vigilant.app.ui.TrapGuardText.moveNote(true).contains("bid"))
    }

    @Test
    fun `with game lines off for bids (the default) there is no move switch on the Bids tab`() {
        compose.setContent { VigilantTheme { MakerScreen(ui(), MakerActions()) } }
        compose.onNodeWithTag("makerRulesToggle").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("makerTrapMove", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `the rules offer 3_25 and 3_5 percent with their own words, and switches for popular first and a required sharp book`() {
        assertEquals("4%", MakerRulesText.pct(0.04))
        assertEquals("3.5%", MakerRulesText.pct(0.035))
        assertEquals("3.25%", MakerRulesText.pct(0.0325))
        assertEquals("8%", MakerRulesText.pct(0.08))
        // Tj, 2026-10-07: "remove the 6% and 8% under the fair options and add 2% and 2.5%".
        assertEquals(listOf(0.02, 0.025, 0.03, 0.0325, 0.035, 0.04), ScanSettings.MAKER_MARGIN_CHOICES)
        var s = settings
        compose.setContent {
            VigilantTheme { MakerScreen(ui(), MakerActions(onUpdate = { f -> s = f(s) })) }
        }
        compose.onNodeWithText(MakerRulesText.summary(settings)).performClick()
        compose.onNodeWithText("3.25%").performClick()
        assertEquals(0.0325, s.makerMargin, 1e-9)
        compose.onNodeWithText("3.5%").performClick()
        assertEquals(0.035, s.makerMargin, 1e-9)
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerPopularFirst"))
        compose.onNodeWithTag("makerPopularFirst").assertIsOn()
        compose.onNodeWithTag("makerPopularFirst").performClick()
        assertFalse(s.makerPopularFirst)
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerRequireSharp"))
        compose.onNodeWithTag("makerRequireSharp").assertIsOff()
        compose.onNodeWithTag("makerRequireSharp").performClick()
        assertTrue(s.makerRequireSharp)
    }

    /** Tj, 2026-10-07: a box wherever a rule has number options, and a shortest odds beside the longest. */
    @Test
    fun `every number rule on the Bids tab has a box that sets exactly what was typed, shortest odds and the quick bids' market size included`() {
        val st = androidx.compose.runtime.mutableStateOf(settings.copy(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY, makerStakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM))
        compose.setContent { VigilantTheme { MakerScreen(ui(st.value), MakerActions(onUpdate = { f -> st.value = f(st.value) })) } }
        compose.onNodeWithText(MakerRulesText.summary(st.value)).performClick()
        fun type(tag: String, text: String) {
            compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).performTextClearance()
            compose.onNodeWithTag(tag).performTextInput(text)
            compose.waitForIdle()
        }
        type("makerMarginField", "3.1"); assertEquals(0.031, st.value.makerMargin, 1e-9)
        type("makerMarginField", "0.1"); assertEquals("under 0.5% isn't taken", 0.031, st.value.makerMargin, 1e-9)
        type("makerStakeField", "7.5"); assertEquals(7.5, st.value.makerStake, 1e-9)
        type("makerMaxStakeField", "12"); assertEquals(12.0, st.value.makerMaxStake, 1e-9)
        type("makerMaxBidsField", "33"); assertEquals(33, st.value.makerMaxBids)
        type("makerMaxDollarsField", "375"); assertEquals(375.0, st.value.makerMaxDollars, 1e-9)
        type("makerQuickMinBooksField", "7"); assertEquals(7, st.value.makerQuickMinBooks)
        assertEquals("quick bids use what was typed", 7, MakerRules.of(st.value).minLineBooks)
        type("makerMaxOddsField", "160"); assertEquals(160, st.value.makerMaxOdds)
        type("makerMinOddsField", "-210"); assertEquals(-210, st.value.makerMinOdds)
        type("makerMinOddsField", "120"); assertEquals(120, st.value.makerMinOdds)
        type("makerMinBooksField", "4"); assertEquals(4, st.value.makerMinBooks)
        type("makerTtlField", "45"); assertEquals(45, st.value.makerTtlMinutes)
        type("makerStopField", "20"); assertEquals(20, st.value.makerStopMinutes)
        compose.onNodeWithTag("makerOddsRange").assertDoesNotExist() // +120 shortest against +160 longest is fine; then flip them
        type("makerMaxOddsField", "110")
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerOddsRange"))
        compose.onNodeWithTag("makerOddsRange").assertTextContains("nothing can pass both", substring = true)
    }

    /** Tj, 2026-10-07: "include obscure bids as well ... prioritize ... popular ... first ... strict safeguards" - the switch and every safeguard are settings, each with a box. */
    @Test
    fun `quick and likely's small-market fill has a switch, a box for every safeguard, and says in words what it does with Tj's numbers`() {
        val st = androidx.compose.runtime.mutableStateOf(settings.copy(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY))
        compose.setContent { VigilantTheme { MakerScreen(ui(st.value), MakerActions(onUpdate = { f -> st.value = f(st.value) })) } }
        compose.onNodeWithText(MakerRulesText.summary(st.value)).performClick()
        fun show(tag: String) { compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag(tag)) }
        fun type(tag: String, text: String) { show(tag); compose.onNodeWithTag(tag).performTextClearance(); compose.onNodeWithTag(tag).performTextInput(text); compose.waitForIdle() }
        // On by default, with the rule in words and Tj's own numbers.
        show("makerObscureFill")
        compose.onNodeWithTag("makerObscureFill").assertIsOn()
        show("makerObscureNote")
        compose.onNodeWithTag("makerObscureNote").assertTextContains("only after every popular bid", substring = true)
        compose.onNodeWithTag("makerObscureNote").assertTextContains("at least 3 books", substring = true)
        // Every number has a box that sets exactly what was typed.
        type("makerObscureMarginField", "7.5"); assertEquals(0.075, st.value.makerObscureMargin, 1e-9)
        type("makerObscureSharpMinEvField", "4"); assertEquals(0.04, st.value.makerObscureSharpMinEv, 1e-9)
        type("makerObscureAgreeField", "1.5"); assertEquals(0.015, st.value.makerObscureAgreePoints, 1e-9)
        type("makerObscureMinBooksField", "4"); assertEquals(4, st.value.makerObscureMinBooks)
        type("makerObscureStakeField", "40"); assertEquals(0.40, st.value.makerObscureStake, 1e-9)
        // What was typed reaches the bid rules.
        val r = MakerRules.of(st.value)
        assertTrue(r.obscureFill)
        assertEquals(listOf(0.075, 0.04, 0.015, 4, 0.40), listOf(r.obscureMargin, r.obscureSharpMinEv, r.obscureAgreePoints, r.obscureMinBooks, r.obscureStake))
        // The note follows the numbers.
        show("makerObscureNote")
        compose.onNodeWithTag("makerObscureNote").assertTextContains("at least 4 books", substring = true)
        // Off: the safeguards disappear and the rules carry no fill.
        show("makerObscureFill")
        compose.onNodeWithTag("makerObscureFill").performClick()
        assertFalse(st.value.makerObscureFill)
        compose.onNodeWithTag("makerObscureMarginField").assertDoesNotExist()
        assertFalse(MakerRules.of(st.value).obscureFill)
        // Not Quick & likely: neither the switch nor the safeguards are shown (they only mean something there).
        val all = androidx.compose.runtime.mutableStateOf(settings)
        compose.runOnUiThread { st.value = all.value }
        compose.waitForIdle()
        compose.onNodeWithTag("makerObscureFill").assertDoesNotExist()
    }

    /** Tj, 2026-10-07: "make sure that the type of bids in the auto bids section are truly the type of bids most likely to be taken quickly, in other words, no strange props or small markets." */
    @Test
    fun `quick and likely hides the switches it overrides, and says what it keeps out`() {
        val st = androidx.compose.runtime.mutableStateOf(settings.copy(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY))
        compose.setContent { VigilantTheme { MakerScreen(ui(st.value), MakerActions(onUpdate = { f -> st.value = f(st.value) })) } }
        compose.onNodeWithText(MakerRulesText.summary(st.value)).performClick()
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerFocusNote"))
        compose.onNodeWithTag("makerFocusNote").assertTextContains("never a market only a few books price", substring = true)
        compose.onNodeWithTag("makerPopularFirst").assertDoesNotExist()
        compose.onNodeWithTag("makerRequireSharp").assertDoesNotExist()
    }

    /** Tj, 2026-10-05: "an option for unlimited bids up at once" and "only the bets which have the maximum chance of being filled quickly and also are decent chance for me to win". */
    @Test
    fun `the rules offer unlimited bids, a quick and likely focus, the sharp-anchored price and the picked-off guard`() {
        assertEquals("Unlimited", MakerRulesText.bidsLabel(ScanSettings.NO_LIMIT))
        assertEquals("40", MakerRulesText.bidsLabel(40))
        assertEquals("No limit", MakerRulesText.dollarsLabel(ScanSettings.MAKER_NO_DOLLAR_LIMIT))
        assertEquals("\$250.00", MakerRulesText.dollarsLabel(250.0))
        var s = settings
        compose.setContent { VigilantTheme { MakerScreen(ui(), MakerActions(onUpdate = { f -> s = f(s) })) } }
        compose.onNodeWithText(MakerRulesText.summary(settings)).performClick()
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasText("Unlimited"))
        compose.onNodeWithText("Unlimited").performClick()
        assertEquals(ScanSettings.NO_LIMIT, s.makerMaxBids)
        compose.onNodeWithText("Quick & likely to win").performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY, s.makerFocus)
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerAnchorSharp"))
        compose.onNodeWithTag("makerAnchorSharp").assertIsOn()
        compose.onNodeWithTag("makerAnchorSharp").performClick()
        assertFalse(s.makerAnchorSharp)
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerGuard"))
        compose.onNodeWithTag("makerGuard").assertIsOn()
        compose.onNodeWithTag("makerGuard").performClick()
        assertFalse(s.makerGuard)
    }

    /** Tj, 2026-10-05: "make a settings options for the auto bid feature for me to select the longest odds for bids (for example, do not post bids longer than +140 odds)". */
    @Test
    fun `the longest odds for a bid is a preset or typed, has no limit until picked, and the tab says what it does`() {
        assertEquals("no limit by default: what ran before doesn't change", 0, settings.makerMaxOdds)
        assertFalse(MakerRulesText.summary(settings).contains("no bid longer than"))
        val st = androidx.compose.runtime.mutableStateOf(settings)
        compose.setContent { VigilantTheme { MakerScreen(ui(st.value), MakerActions(onUpdate = { f -> st.value = f(st.value) })) } }
        compose.onNodeWithText(MakerRulesText.summary(settings)).performClick()
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerMaxOddsField"))
        compose.onNodeWithText("Longest odds a bid may be posted at").assertExists()
        compose.onNodeWithTag("makerMaxOddsNote").assertTextContains("No limit", substring = true)
        for ((label, odds) in listOf("+100" to 100, "+110" to 110, "+120" to 120, "+130" to 130, "+140" to 140, "+150" to 150, "+175" to 175, "+200" to 200, "+250" to 250, "+300" to 300)) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(label, odds, st.value.makerMaxOdds)
        }
        compose.onNodeWithText("+140").performScrollTo().performClick()
        compose.onNodeWithTag("makerMaxOddsNote").assertTextContains("longer than +140", substring = true)
        compose.onNodeWithTag("makerMaxOddsNote").assertTextContains("41.7¢", substring = true)
        assertTrue(MakerRulesText.summary(st.value).contains("no bid longer than +140"))
        // Typed.
        compose.onNodeWithTag("makerMaxOddsField").performScrollTo().performTextClearance()
        compose.onNodeWithTag("makerMaxOddsField").performTextInput("165")
        assertEquals(165, st.value.makerMaxOdds)
        // Under +100 isn't saved (the last good one stands), and the field says why.
        compose.onNodeWithTag("makerMaxOddsField").performTextClearance()
        compose.onNodeWithTag("makerMaxOddsField").performTextInput("50")
        assertEquals(165, st.value.makerMaxOdds)
        compose.onNodeWithText("(even money) or more", substring = true).assertExists()
        // No limit puts it back (the dollars limit's own "No limit" chip comes first on the tab, the longest odds' second).
        compose.onAllNodesWithText("No limit")[1].performScrollTo().performClick()
        assertEquals(0, st.value.makerMaxOdds)
        compose.onNodeWithTag("makerMaxOddsNote").assertTextContains("No limit", substring = true)
    }

    /** Tj, 2026-10-06: "for the vigilant auto bid low api usage setting, add options for minimum 1.5% positive EV or an amount I type in". */
    @Test
    fun `low API usage's margin has a 1_5 percent chip and a field for any other percent, and a number the mode can't take saves nothing and says so`() {
        val st = androidx.compose.runtime.mutableStateOf(settings.copy(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE))
        compose.setContent { VigilantTheme { MakerScreen(ui(st.value), MakerActions(onUpdate = { f -> st.value = f(st.value) })) } }
        compose.onNodeWithText(MakerRulesText.summary(st.value)).performClick()
        compose.onNodeWithTag("lowUsageMarginField").performScrollTo().assertTextContains("2.5")
        compose.onNodeWithTag("lowUsageMargin-15").performClick()
        assertEquals(0.015, st.value.lowUsageMargin, 1e-12)
        compose.onNodeWithTag("lowUsageMarginField").assertTextContains("1.5")
        compose.onNodeWithTag("lowUsageMarginNote").assertTextContains("1.5% under the fair", substring = true)
        // A typed amount saves as it becomes a percent the mode takes...
        compose.onNodeWithTag("lowUsageMarginField").performTextReplacement("1.7")
        assertEquals(0.017, st.value.lowUsageMargin, 1e-12)
        assertTrue(MakerRulesText.summary(st.value), MakerRulesText.summary(st.value).contains("1.7% or more under the fair"))
        // ...and one it can't (under half a percent) saves nothing, flags itself and keeps the last good margin.
        compose.onNodeWithTag("lowUsageMarginField").performTextReplacement("0.2")
        assertEquals(0.017, st.value.lowUsageMargin, 1e-12)
        compose.onNodeWithText(LowUsageText.MARGIN_ERROR).assertExists()
        compose.onNodeWithTag("lowUsageMarginField").performTextReplacement("3.25")
        assertEquals(0.0325, st.value.lowUsageMargin, 1e-12)
        compose.onNodeWithText(LowUsageText.MARGIN_ERROR).assertDoesNotExist()
        compose.onNodeWithTag("lowUsageMargin-25").performClick()
        assertEquals(0.025, st.value.lowUsageMargin, 1e-12)
        compose.onNodeWithTag("lowUsageMarginField").assertTextContains("2.5")
    }

    /** Tj, 2026-10-06: "add trap guard option for maximum 12 hours until game time or an amount in hours I type in" (the Bids tab's copy of the shared setting). */
    @Test
    fun `the Bids tab's trap guard window has a 12 h chip and a field for any whole number of hours`() {
        val st = androidx.compose.runtime.mutableStateOf(settings)
        compose.setContent { VigilantTheme { MakerScreen(ui(st.value), MakerActions(onUpdate = { f -> st.value = f(st.value) })) } }
        compose.onNodeWithText(MakerRulesText.summary(st.value)).performClick()
        compose.onNodeWithText("12 h").performScrollTo().performClick()
        assertEquals(12, st.value.trapEarlyHours)
        compose.onNodeWithTag("maker-trapEarlyField").assertTextContains("12")
        compose.onNodeWithTag("maker-trapEarlyField").performTextReplacement("9")
        assertEquals(9, st.value.trapEarlyHours)
        compose.onNodeWithTag("makerTrapEarlyNote").assertTextContains("more than 9 h off", substring = true)
        compose.onNodeWithTag("maker-trapEarlyField").performTextReplacement("0")
        assertEquals("0 saves nothing: Off is its own chip", 9, st.value.trapEarlyHours)
        compose.onNodeWithText(TrapGuardText.HOURS_ERROR).assertExists()
    }

    /** Tj, 2026-10-05: "make an option for a low API usage auto bid feature". */
    @Test
    fun `low API usage is a choice under which bids go up - it picks the books, the pace and the margin, hides what it overrides, and says what it does`() {
        val low = com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE
        assertEquals("off until picked: nothing changes", com.tjshea.vigilant.data.scanner.BidFocus.ALL, settings.makerFocus)
        val st = androidx.compose.runtime.mutableStateOf(settings)
        compose.setContent { VigilantTheme { MakerScreen(ui(st.value), MakerActions(onUpdate = { f -> st.value = f(st.value) })) } }
        compose.onNodeWithText(MakerRulesText.summary(settings)).performClick()
        compose.onAllNodesWithTag("lowUsagePanel").assertCountEquals(0)
        compose.onAllNodesWithTag("makerKind-PROP").assertCountEquals(1)
        compose.onNodeWithText("Low API usage").performScrollTo().performClick()
        assertEquals(low, st.value.makerFocus)
        // Its panel is there; the controls it overrides (the usual margin, the kinds, the sharp switches) are not.
        compose.onNodeWithTag("lowUsagePanel").assertExists()
        compose.onAllNodesWithTag("makerKind-PROP").assertCountEquals(0)
        compose.onAllNodesWithTag("makerAnchorSharp").assertCountEquals(0)
        compose.onAllNodesWithTag("makerRequireSharp").assertCountEquals(0)
        compose.onNodeWithTag("makerFocusNote").assertTextContains("player props", substring = true)
        compose.onNodeWithTag("lowUsagePriceNote").assertTextContains("+130", substring = true)
        // The usual longest-odds control stays (a tighter limit is kept), and its note says the mode's cap.
        compose.onNodeWithTag("makerMaxOddsNote").performScrollTo().assertTextContains("never go longer than +130", substring = true)
        // The books: two or three, never fewer or more.
        compose.onNodeWithTag("lowUsageBook-fanduel").performScrollTo().performClick()
        assertEquals(setOf("kalshi", "prophetx"), st.value.lowUsageBooks)
        compose.onNodeWithTag("lowUsageBook-kalshi").performClick()
        assertEquals("a third pick can't go under two", setOf("kalshi", "prophetx"), st.value.lowUsageBooks)
        compose.onNodeWithTag("lowUsageBook-pinnacle").performClick()
        assertEquals(setOf("kalshi", "prophetx", "pinnacle"), st.value.lowUsageBooks)
        compose.onNodeWithTag("lowUsageBook-draftkings").performClick()
        assertEquals("a fourth isn't added", setOf("kalshi", "prophetx", "pinnacle"), st.value.lowUsageBooks)
        compose.onNodeWithTag("lowUsageBooksNote").assertTextContains("Pinnacle", substring = true)
        // The pace and the margin.
        compose.onNodeWithTag("lowUsagePace-5").performScrollTo().performClick()
        assertEquals(5, st.value.lowUsagePace)
        compose.onNodeWithTag("lowUsagePaceNote").assertTextContains("every 5 min", substring = true)
        compose.onNodeWithTag("lowUsagePace-15").performClick()
        assertEquals(15, st.value.lowUsagePace)
        // Auto (the default) is a chip too, and its note says what it costs.
        compose.onNodeWithTag("lowUsagePace-0").performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO, st.value.lowUsagePace)
        compose.onNodeWithTag("lowUsagePaceNote").assertTextContains("Bids stay up", substring = true)
        compose.onNodeWithTag("lowUsagePace-15").performClick()
        assertEquals(15, st.value.lowUsagePace)
        compose.onNodeWithTag("lowUsageMargin-35").performScrollTo().performClick()
        assertEquals(0.035, st.value.lowUsageMargin, 1e-12)
        compose.onNodeWithTag("lowUsageMargin-25").performClick()
        assertEquals(0.025, st.value.lowUsageMargin, 1e-12)
        // The summary says it.
        val summary = MakerRulesText.summary(st.value)
        assertTrue(summary, summary.startsWith("low API usage: Kalshi, ProphetX, Pinnacle · scan every 15 min · props in the next 6 h · 2.5% or more under the fair · no bid longer than +130"))
        // Going back to All bids brings the usual controls back.
        compose.onNodeWithText("All bids").performScrollTo().performClick()
        assertEquals(com.tjshea.vigilant.data.scanner.BidFocus.ALL, st.value.makerFocus)
        compose.onAllNodesWithTag("lowUsagePanel").assertCountEquals(0)
        compose.onAllNodesWithTag("makerKind-PROP").assertCountEquals(1)
    }

    @Test
    fun `with unlimited chosen the tab says what still limits the bids, and with quick and likely chosen it says what the focus keeps`() {
        compose.setContent { VigilantTheme { MakerScreen(ui(settings.copy(makerMaxBids = ScanSettings.NO_LIMIT, makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY)), MakerActions()) } }
        compose.onNodeWithText(MakerRulesText.summary(settings.copy(makerMaxBids = ScanSettings.NO_LIMIT, makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY))).performClick()
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerUnlimitedNote"))
        compose.onNodeWithTag("makerUnlimitedNote").assertTextContains("the wallet", substring = true)
        compose.onNodeWithTag("makerFocusNote").assertTextContains("player props and team totals", substring = true)
        // The summary says so when small markets fill the rest (on by default), and is the old words with that off.
        assertTrue(MakerRulesText.summary(settings.copy(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY)).contains("quick & likely to win (small markets fill the rest): "))
        assertTrue(MakerRulesText.summary(settings.copy(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY, makerObscureFill = false)).contains("quick & likely to win: "))
    }

    @Test
    fun `when the picked-off guard stopped the bids the tab says why, first, and Resume bids lifts it from the fills after that moment`() {
        val why = com.tjshea.vigilant.data.novig.trading.maker.MakerGuard.haltedText(
            com.tjshea.vigilant.data.novig.trading.maker.MakerGuard.Verdict(8, 5, -0.021, 0.042, true),
        )
        var s = settings.copy(makerHalted = why)
        compose.setContent { VigilantTheme { MakerScreen(ui(s), MakerActions(onUpdate = { f -> s = f(s) })) } }
        compose.onNodeWithTag("makerHalted").assertIsDisplayed()
        compose.onNodeWithText("5 of the last 8 fills were picked off", substring = true).assertIsDisplayed()
        val before = System.currentTimeMillis()
        compose.onNodeWithText("Resume bids").performClick()
        assertEquals(null, s.makerHalted)
        assertTrue(s.makerGuardFromMs >= before)
    }
}
