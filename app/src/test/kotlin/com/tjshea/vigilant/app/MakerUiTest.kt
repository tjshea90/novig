package com.tjshea.vigilant.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.MakerActions
import com.tjshea.vigilant.app.ui.MakerRulesText
import com.tjshea.vigilant.app.ui.MakerScreen
import com.tjshea.vigilant.app.ui.MakerText
import com.tjshea.vigilant.app.ui.MakerUi
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerDecision
import com.tjshea.vigilant.data.novig.trading.maker.MakerLines
import com.tjshea.vigilant.data.novig.trading.maker.MakerQuote
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
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
@Config(sdk = [35], qualifiers = "w393dp-h2600dp-xxhdpi")
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
        compose.onNodeWithTag("makerSwitch").performClick()
        assertFalse(s.maker)
        // The rules: closed, they read as one line; open, a chip sets the margin.
        compose.onNodeWithText(MakerRulesText.summary(settings)).performClick()
        compose.onNodeWithText("6%").performClick()
        assertEquals(0.06, s.makerMargin, 1e-9)
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasTestTag("makerSkippedToggle"))
        compose.onNodeWithTag("makerSkippedToggle").performClick()
        compose.onNodeWithTag("makerScreen").performScrollToNode(hasText("Moneylines are off for bids", substring = true))
    }

    @Test
    fun `without betting set up or Vigilant's scanner it says what to do first`() {
        var opened = 0
        compose.setContent {
            VigilantTheme { MakerScreen(ui(setUp = false, vigilantOn = false, bids = emptyList()), MakerActions(onOpenBetting = { opened++ })) }
        }
        compose.onNodeWithText(MakerText.NEEDS_BETTING).assertIsDisplayed()
        compose.onNodeWithText(MakerText.NEEDS_VIGILANT).assertIsDisplayed()
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
            "4% under the fair · ¼ Kelly of \$1,000.00, up to \$10.00 a bid · Props, Team totals, 1st half / inning · up to 30 min (less if the fair goes old)",
            MakerRulesText.summary(ScanSettings()),
        )
        assertEquals("\$5.00 a bid", MakerRulesText.stake(ScanSettings(makerStakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM)))
    }

    @Test
    fun `screenshot - the Bids tab`() {
        compose.setContent {
            VigilantTheme { MakerScreen(ui(bids = listOf(bid("rest-1", MakerStatus.RESTING), bid("f-1", MakerStatus.FILLED, filled = 1_098, orderId = "o-f"))), MakerActions()) }
        }
        compose.onRoot().captureRoboImage("screenshots/4p_bids_tab.png")
    }
}
