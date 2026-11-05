package com.tjshea.vigilant.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ApiBetActions
import com.tjshea.vigilant.app.ui.LocalApiBet
import com.tjshea.vigilant.app.ui.NcaabContent
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.ncaab.NcaabBoard
import com.tjshea.vigilant.data.ncaab.NcaabBoardFeed
import com.tjshea.vigilant.data.ncaab.NcaabBucket
import com.tjshea.vigilant.data.ncaab.NcaabModel
import com.tjshea.vigilant.data.ncaab.NcaabRatings
import com.tjshea.vigilant.data.ncaab.NcaabSignalTable
import com.tjshea.vigilant.data.ncaab.NcaabSignals
import com.tjshea.vigilant.data.ncaab.NcaabSlate
import com.tjshea.vigilant.data.ncaab.NcaabTeam
import com.tjshea.vigilant.data.ncaab.NcaabView
import com.tjshea.vigilant.data.ncaab.SlateGame
import com.tjshea.vigilant.data.ncaab.SlateTeam
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The college basketball tab (NC1) drawn from sample data: the schedule in Tj's `Team (74 ++)` form, Novig's lines, the Bet buttons, the pages and the game sheet with its other-number drop-downs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class NcaabUiTest {
    @get:Rule val compose = createComposeRule()

    private val fee = MarketFee(0.0, 0.0, FeeCharge.WHEN_LIVE)
    private val start = 1_800_000_000_000L
    private val now = start - 3 * 3_600_000L
    private val event = NovigEvent("ev1", "BASKETBALL", "NCAAB", "OPEN_PREGAME", "Rider @ Davidson", start)

    private fun spread(strike: Double, id: String) = NovigMarket(
        "m-$id", "ev1", "SPREAD", "OPEN", "x", start, fee,
        listOf(NovigOutcome("$id-h", "DAV ${if (strike >= 0) "+" else ""}$strike", "TBD"), NovigOutcome("$id-a", "RID ${if (-strike >= 0) "+" else ""}${-strike}", "TBD")), strike,
    )

    private fun total(line: Double, id: String) = NovigMarket("m-$id", "ev1", "TOTAL", "OPEN", "x", start, fee, listOf(NovigOutcome("$id-o", "Over $line", "TBD"), NovigOutcome("$id-u", "Under $line", "TBD")), line)

    private fun book(m: NovigMarket, a: Double, b: Double) = NovigBook(
        m.marketId, 1, mapOf(m.outcomes[0].outcomeId to listOf(BidLevel((a * 1000).toInt(), 500)), m.outcomes[1].outcomeId to listOf(BidLevel((b * 1000).toInt(), 500))), now, partial = true,
    )

    private fun bucket(lo: Double, hi: Double?, n: Int, r: Double) = NcaabBucket(lo, hi, n, r, r, n / 2, r, n / 2)

    /** [streak]: how often the team with the better against-the-spread average covered (the method fades it, so 0.48 means the other side won 52%). */
    private fun modelWith(streak: Double) = NcaabModel(
        source = "backtest", sigma = 11.0, seasons = listOf(2024, 2025, 2026), games = 14_669, currentSeason = 2027, note = "test",
        signals = mapOf(
            NcaabSignals.ATS_GAP to NcaabSignalTable("Against-the-spread streak", "spread", "tiers", "", -1, listOf(bucket(0.0, 0.8, 8000, streak), bucket(0.8, null, 2000, streak))),
            NcaabSignals.AVGS_LINE to NcaabSignalTable("Averages vs the spread", "spread", "points", "", 1, listOf(bucket(0.0, 6.0, 8000, 0.50), bucket(6.0, null, 2000, 0.48))),
            NcaabSignals.TOTAL_GAP to NcaabSignalTable("Total vs scoring", "total", "points", "", 1, listOf(bucket(0.0, null, 9000, 0.488))),
        ),
    )

    private val model = modelWith(0.49)   // fading it wins 51%: not enough over Novig's 50.5 cents once the uncertainty is counted

    private fun team(n: String, ppg: Double, papg: Double, ats: Double, sym: String) = NcaabTeam(n = n, g = 8, all = 8, ppg = ppg, papg = papg, ats = ats, sym = sym, last = listOf(1, 2, 0, -1, 1))

    private val markets = listOf(spread(-4.5, "s1"), spread(-2.5, "s2"), spread(-6.5, "s3"), total(141.5, "t1"), total(143.5, "t2"))
    private val books = mapOf(
        "m-s1" to book(markets[0], 0.49, 0.49), "m-s2" to book(markets[1], 0.44, 0.54), "m-s3" to book(markets[2], 0.54, 0.44),
        "m-t1" to book(markets[3], 0.49, 0.49), "m-t2" to book(markets[4], 0.44, 0.54),
    )

    private fun views(withRatings: Boolean = true, model: NcaabModel = this.model) = NcaabView.build(
        NcaabSlate(1, listOf(SlateGame("g1", start, h = SlateTeam("1", "Davidson", "Davidson Wildcats"), a = SlateTeam("2", "Rider", "Rider Broncs")))),
        if (withRatings) NcaabRatings(1, 2027, mapOf("1" to team("Davidson", 74.2, 66.0, 1.4, "++"), "2" to team("Rider", 62.3, 70.1, -0.9, "-")), model, 40) else null,
        NcaabBoardFeed(now, NcaabBoard.build(listOf(event), markets, books, emptyMap(), now), 1), now,
    )

    private val bets = ArrayList<Opportunity>()
    private val betsWithFair = ArrayList<Pair<Opportunity, Double>>()
    private val actions = ApiBetActions(enabled = true, betOpportunity = { bets += it }, betCno = {}, betWithFair = { o, f -> betsWithFair += o to f })

    private fun show(v: List<com.tjshea.vigilant.data.ncaab.NcaabGameView>, bet: Boolean = true, model: NcaabModel = this.model) {
        compose.setContent {
            VigilantTheme {
                CompositionLocalProvider(LocalApiBet provides (if (bet) actions else null)) {
                    NcaabContent(v, ratingsAsOf = now - 3_600_000L, boardAt = now - 20_000L, boardReadAt = now, note = "", model = model, ratedTeams = 2, hasSlate = true, hasToken = true,
                        settings = ScanSettings(), now = now, onTrack = { _, _ -> }, onRecheck = {})
                }
            }
        }
    }

    @Test
    fun `a game reads like Tj's example with each team's points and tier average, Novig's spread and total, and a Bet button on each side`() {
        show(views())
        compose.onNodeWithText("Rider (62 −)").assertIsDisplayed()
        compose.onNodeWithText("Davidson (74 ++)").assertIsDisplayed()
        compose.onNodeWithText("Total 141.5").assertIsDisplayed()
        // Rider is the underdog on Novig's main number (-4.5 for Davidson), each side a button with its price
        compose.onNodeWithText("+4.5  51¢").assertIsDisplayed()
        compose.onNodeWithText("−4.5  51¢").assertIsDisplayed()
        compose.onNodeWithText("O 51¢").assertIsDisplayed()
    }

    @Test
    fun `a Bet on a number no book prices gives the Bet sheet Novig's middle price and claims no edge`() {
        show(views())
        compose.onNodeWithText("−4.5  51¢").performClick()
        assertEquals(0, bets.size)
        val (o, fair) = betsWithFair.single()
        assertEquals("DAV -4.5", o.outcome.name)
        // the take costs 0.51 and the best bid is 0.49: the middle is 0.50, an EV of about -2%
        assertEquals(0.50, fair, 1e-9)
    }

    @Test
    fun `without a betting setup there are no Bet buttons, only the lines`() {
        show(views(), bet = false)
        compose.onNodeWithText("Total 141.5").assertIsDisplayed()
        compose.onNodeWithText("+4.5").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTagCount("ncaabBet") == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun `the leans page says plainly that a lean the history does not back is an opinion, and the evidence card gives the verdict`() {
        show(views())
        compose.onNodeWithTag("ncaabPage-LEANS").performClick()
        compose.onNodeWithTag("ncaabLeanNote").assertIsDisplayed()
        compose.onNodeWithTag("ncaabLeanNote").assertTextContains("None of today's leans is backed by history", substring = true)
        compose.onNodeWithTag("ncaabEvidence").assertIsDisplayed()
        compose.onNodeWithText("None of these did, so they are opinions, not edges.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a lean whose history clears Novig's price with confidence is marked backed and the evidence card says which signal did`() {
        val strong = modelWith(0.48)   // fading wins 52% of 10,000 games
        show(views(model = strong), model = strong)
        compose.onNodeWithTag("ncaabPage-LEANS").performClick()
        compose.onNodeWithTag("ncaabLeanNote").assertTextContains("1 lean is backed by history", substring = true)
        compose.onNodeWithTag("ncaabEvidence").assertTextContains("cleared it, thinly", substring = true)
    }

    @Test
    fun `the unlike-the-averages page names the spread and total furthest from what the teams' numbers say`() {
        show(views())
        compose.onNodeWithTag("ncaabPage-UNLIKE").performClick()
        compose.onNodeWithText("Spreads furthest from the averages").assertIsDisplayed()
        compose.onNodeWithText("Totals furthest from the teams' scoring").assertIsDisplayed()
    }

    @Test
    fun `a game opens its sheet, and the other-spreads drop-down switches to another Novig number with its own Bet buttons`() {
        show(views())
        compose.onNodeWithTag("ncaabGame-g1").performClick()
        compose.onNodeWithTag("ncaabSheet").assertIsDisplayed()
        compose.onNodeWithTag("ncaabSpreadPicker").performScrollTo().performClick()
        // options are sorted by the home handicap: -6.5, -4.5 (main), -2.5
        compose.onNodeWithTag("ncaabSpreadPicker-2").performClick()
        compose.onNodeWithTag("ncaabSide-s2-h").performScrollTo().assertIsDisplayed()
        compose.onNode(hasTestTag("ncaabSide-s2-h") and hasAnyDescendant(hasText("Davidson", substring = true))).assertIsDisplayed()
    }

    @Test
    fun `before the ratings exist the card still lists the game with a dash for the averages`() {
        show(views(withRatings = false))
        compose.onNodeWithText("Davidson (—)").assertIsDisplayed()
        compose.onNodeWithText("Rider (—)").assertIsDisplayed()
    }
}
