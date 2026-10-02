package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.BetActions
import com.tjshea.vigilant.app.ui.BetSheetContent
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.SlipStake
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetInsight
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BookLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tracker's bet cards and sheet (Tj, 2026-09-29): a Replace button on every open bet, why a bet whose
 * game is over is still open, every book's odds for the bet tapped, the fair price now against the odds bet at.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class TrackerUiTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW
    private val hour = 3_600_000L

    private fun screen(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    /** An open bet whose game ended [hoursAgo] hours ago and that the grader couldn't settle. */
    private fun stuck(id: String, note: String, manual: Boolean, hoursAgo: Long = 5) = TrackedBet(
        id, now - 30 * hour, "NHL", "Minnesota Wild @ Detroit Red Wings", now - hoursAgo * hour, "Player Saves", "Cam Talbot Over 20.5", "m", "o",
        0.5, 0.5, 0.52, 0.04, 5.0, source = "cno", american = 100, gameUrl = "https://crazyninjaodds.com/g?side_id=$id",
        gradeNote = note, gradeManual = manual, gradeAtMs = now - 10 * 60_000L,
    )

    @Test
    fun `every open bet has a Replace button that hands back that exact bet`() {
        val replaced = mutableListOf<TrackedBet>()
        screen {
            TrackerScreen(
                SampleScan.state(), { _, _ -> }, {}, initialView = TrackerView.BETS,
                actions = BetActions(onReplace = { replaced += it }),
            )
        }
        // The sample has three open bets that haven't started (b3, b4, b5); a settled bet has none.
        compose.onAllNodesWithText("Replace").assertCountEquals(3)
        compose.onAllNodesWithText("Replace").onFirst().performClick()
        assertEquals(1, replaced.size)
        assertEquals(true, replaced.single().status == BetStatus.PENDING)
        // Settled bets don't offer it.
        compose.onNodeWithText("Settled (3)").performClick()
        compose.onAllNodesWithText("Replace").assertCountEquals(0)
    }

    @Test
    fun `a bet whose game is over but isn't graded says why, and the ones that need a tap come first`() {
        val base = SampleScan.state()
        val state = base.copy(
            bets = base.bets + stuck("waiting", "The game isn't over yet", manual = false, hoursAgo = 2) +
                stuck("tap", "Cam Talbot isn't in the box score (didn't play, or the name is spelled differently): mark it yourself", manual = true),
        )
        screen { TrackerScreen(state, { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithText("Cam Talbot isn't in the box score", substring = true).assertExists()
        compose.onNodeWithText("The game isn't over yet · checked 10m ago").assertExists()
        // Started bets have result buttons; upcoming ones don't.
        compose.onAllNodesWithText("Won").assertCountEquals(2)
        // The summary counts them and offers to read the scores again.
        compose.onNodeWithText("5 open", substring = true).assertExists()
        compose.onNodeWithText("2 started", substring = true).assertExists()
        compose.onNodeWithText("1 need a tap", substring = true).assertExists()
        compose.onNodeWithText("Grade now: read the final scores again").assertHasClickAction()
    }

    @Test
    fun `a graded bet says what it rests on`() {
        val base = SampleScan.state()
        val graded = base.bets.first { it.id == "b6" }.copy(gradeNote = "Final: Tampa Bay Rays 3, Boston Red Sox 5")
        screen { TrackerScreen(base.copy(bets = base.bets.map { if (it.id == "b6") graded else it }), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithText("Settled (3)").performClick()
        compose.onNodeWithText("Final: Tampa Bay Rays 3, Boston Red Sox 5").assertExists()
    }

    // ---- Sort and scanner filter (Tj, 2026-09-29) ----

    /** The sample's open bets with stakes that tell them apart: b3 (Vigilant, no EV yet) $25, b4 (CNO, +3.1%) $5, b5 (CNO, −2.1%) $10. */
    private fun stakes() = SampleScan.state().let { s ->
        s.copy(bets = s.bets.map { b -> when (b.id) { "b3" -> b.copy(stake = 25.0); "b4" -> b.copy(stake = 5.0); "b5" -> b.copy(stake = 10.0); else -> b } })
    }

    /** The pinned Sort chip opens a menu (v0.22.0: chips would take a third of the screen while pinned); [item] is the choice in it. */
    private fun chooseSort(item: String) {
        compose.onNodeWithText("Sort:", substring = true).performClick()
        compose.onNodeWithText(item).performClick()
    }

    private fun chooseScanner(item: String) {
        compose.onNodeWithText("Scanner:", substring = true).performClick()
        compose.onNodeWithText(item).performClick()
    }

    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    /** The open bets, top to bottom. */
    private fun listed(): List<String> = listOf("b3" to "Dallas Cowboys", "b4" to "Jaxon Smith-Njigba Over 5.5", "b5" to "Under 7.5")
        .filter { (_, t) -> compose.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }
        .sortedBy { (_, t) -> top(t) }.map { it.first }

    @Test
    fun `the open bets are ordered by what needs a look until a sort is chosen`() {
        screen { TrackerScreen(stakes(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        assertEquals(listOf("b5", "b4", "b3"), listed()) // the next games first
        compose.onNodeWithText("Sort: Needs a look").assertExists()
    }

    @Test
    fun `current EV puts the best first against the price each was placed at, and a second tap turns it round`() {
        screen { TrackerScreen(stakes(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        chooseSort("Current EV")
        assertEquals(listOf("b4", "b5", "b3"), listed()) // +3.1%, −2.1%, then the bet nothing has priced
        compose.onNodeWithText("Sort: Current EV · best").assertExists()
        chooseSort("Current EV: best first") // the menu says what a second tap does
        assertEquals(listOf("b5", "b4", "b3"), listed()) // worst first, unpriced still last
        compose.onNodeWithText("Sort: Current EV · worst").assertExists()
    }

    @Test
    fun `date placed and amount order the bets, newest and largest first`() {
        screen { TrackerScreen(stakes(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        chooseSort("Date placed")
        assertEquals(listOf("b5", "b4", "b3"), listed()) // placed 50 min, 60 min, 2 h ago
        chooseSort("Date placed: newest first")
        assertEquals(listOf("b3", "b4", "b5"), listed())
        chooseSort("Amount")
        assertEquals(listOf("b3", "b5", "b4"), listed()) // $25, $10, $5
    }

    @Test
    fun `the scanner filter lists one scanner's bets, with the counts`() {
        screen { TrackerScreen(stakes(), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithText("Scanner: All").assertExists()
        compose.onNodeWithText("Scanner:", substring = true).performClick()
        compose.onNodeWithText("All scanners (3)").assertExists()
        compose.onNodeWithText("Vigilant (1)").assertExists()
        compose.onNodeWithText("CNO (2)").assertExists()
        compose.onNodeWithText("Vigilant (1)").performClick()
        assertEquals(listOf("b3"), listed())
        compose.onNodeWithText("Scanner: Vigilant").assertExists()
        compose.onNodeWithText("Open (1)").assertExists() // the list counts follow the scanner picked
        chooseScanner("CNO (2)")
        assertEquals(listOf("b5", "b4"), listed())
        chooseScanner("All scanners (3)")
        assertEquals(3, listed().size)
    }

    @Test
    fun `a card says when it was placed, and an old EV is not called now`() {
        val base = stakes()
        val state = base.copy(bets = base.bets.map { if (it.id == "b4") it.copy(nowAtMs = now - 2 * hour, nowVia = com.tjshea.vigilant.data.tracker.BetTracker.VIA_CNO) else it })
        screen { TrackerScreen(state, { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onAllNodesWithText("placed ", substring = true).assertCountEquals(3)
        // b5's read is 5 minutes old (inside the limit): "now"; b4's is 2 hours old: when it was read.
        compose.onNodeWithText("now −2.1% EV at your -110").assertExists()
        compose.onNodeWithText("+3.1% EV at your +100").assertExists()
        compose.onNodeWithText("as of 2h ago", substring = true).assertExists()
    }

    @Test
    fun `Check odds now counts as it goes`() {
        screen { TrackerScreen(SampleScan.state().copy(checkingOdds = true, checkProgress = 12 to 61), { _, _ -> }, {}) }
        compose.onNodeWithText("Checking 12/61…").assertIsNotEnabled()
    }

    /** Tj, 2026-10-01: Check odds now pauses the CNO scanner and the rest so it can focus: the Tracker says so while it runs, and not otherwise. */
    @Test
    fun `while Check odds now runs the Tracker says what is paused for it, and not when it isn't running`() {
        screen { TrackerScreen(SampleScan.state().copy(checkingOdds = true, checkProgress = 12 to 61), { _, _ -> }, {}) }
        compose.onNodeWithTag("checkFocusBanner").assertExists()
        compose.onNodeWithText("The CNO scanner, background auto-scan (auto-bet with it), scans and the widget's refresh are paused until this finishes.", substring = true).assertExists()
    }

    @Test
    fun `with no check running there is no pause banner`() {
        screen { TrackerScreen(SampleScan.state(), { _, _ -> }, {}) }
        compose.onNodeWithTag("checkFocusBanner").assertDoesNotExist()
    }

    @Test
    fun `the stats say whether the edges are real, what's open, and where it works`() {
        screen { TrackerScreen(SampleScan.state(), { _, _ -> }, {}) }
        compose.onNodeWithText("Are the edges real?").assertExists()
        compose.onNodeWithText("At risk").assertExists()
        // What they'd make if all win (profit), said as profit: "pays" read as the payout, stake included (full test, 2026-10-02).
        compose.onNodeWithText("Profit if all win").assertExists()
        compose.onNodeWithText("Pays if all win").assertDoesNotExist()
        compose.onNodeWithText("Where it's working").assertExists()
        // The split can be switched between scanner, league, market, edge and price.
        compose.onNodeWithText("Market").performScrollTo().performClick()
        compose.onNodeWithText("Moneyline").assertExists()
        compose.onNodeWithText("Player props").assertExists()
    }

    // ---- The sheet, as the content composable (a dialog with a text field never idles under Robolectric) ----

    private val books = listOf(
        BookLine("Pinnacle", -108, -108), BookLine("DraftKings", -110, -110), BookLine("FanDuel", -112, -108), BookLine("BetMGM", -105, null),
        BookLine("Novig", 100, -112),
    )

    private fun sheetBet(nowFair: Double? = 0.5155) = SampleScan.bets.first { it.id == "b4" }.copy(books = books, booksAtMs = now - 60_000L, nowFair = nowFair, nowAmerican = 105, otherSide = "Jaxon Smith-Njigba Under 5.5")

    private fun sheet(
        bet: TrackedBet = sheetBet(), settings: ScanSettings = SampleScan.settings, actions: BetActions = BetActions(),
        rereading: Boolean = false, onSettle: (BetStatus) -> Unit = {},
    ) = screen { BetSheetContent(bet, BetInsight.of(bet), now, settings, rereading, false, false, actions, onSettle, {}, {}, {}) }

    @Test
    fun `the sheet shows the odds bet at against the fair price now, and every book`() {
        sheet()
        compose.onNodeWithText("Your bet").assertExists()
        compose.onNodeWithText("Odds bet at").assertExists()
        // +100 is the price bet at and Novig's own row in the table.
        compose.onAllNodesWithText("+100").assertCountEquals(2)
        compose.onNodeWithText("Every book").assertExists()
        listOf("Pinnacle", "DraftKings", "FanDuel", "BetMGM", "Novig").forEach { compose.onNodeWithText(it).assertExists() }
        // The fair price now and the gap to the price bet at, in words.
        compose.onNodeWithText("You bet +100 (50.0% implied). Fair now", substring = true).assertExists()
        compose.onNodeWithText("EV at your price", substring = true).assertExists()
        compose.onNodeWithText("Break-even against today's fair price", substring = true).assertExists()
        compose.onNodeWithText("Read 1m ago").assertExists()
    }

    /** PNGs for a look (`-Pscreenshots`); the assertions are in the tests above. */
    @Test
    fun screenshots() {
        val base = SampleScan.state()
        val state = base.copy(
            bets = base.bets + stuck("waiting", "The game isn't over yet", manual = false, hoursAgo = 2) +
                stuck("tap", "Cam Talbot isn't in the box score (didn't play, or the name is spelled differently): mark it yourself", manual = true),
            checkingOdds = true, checkProgress = 12 to 61,
        )
        screen { TrackerScreen(state, { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onRoot().captureRoboImage("screenshots/4d_tracker_open_bets.png")
    }

    /** The Bets list sorted by current EV, with a Vigilant bet priced by Vigilant, an old read, and a bet nothing could price. */
    @Test
    fun sortedScreenshot() {
        val base = SampleScan.state()
        val vig = com.tjshea.vigilant.data.tracker.BetTracker.VIA_VIGILANT
        val state = base.copy(
            bets = base.bets.map { b ->
                when (b.id) {
                    "b3" -> b.copy(nowFair = 0.478, nowEv = 0.062, nowAtMs = now - 90_000L, nowBooks = 5, nowVia = vig, american = 110, stake = 25.0)
                    "b4" -> b.copy(nowAtMs = now - 2 * hour, stake = 5.0)
                    "b5" -> b.copy(nowFair = null, nowEv = null, nowAtMs = null, nowBooks = null, nowNote = "No fair-odds source lists this game", nowNoteAtMs = now - 60_000L, stake = 10.0)
                    else -> b
                }
            },
        )
        screen { TrackerScreen(state, { _, _ -> }, {}, initialView = TrackerView.BETS) }
        chooseSort("Current EV")
        compose.onRoot().captureRoboImage("screenshots/4g_tracker_sorted_by_ev.png")
    }

    @Test
    fun sheetScreenshot() {
        sheet(settings = SampleScan.settings.copy(slipStake = SlipStake.ONE_DOLLAR))
        compose.onRoot().captureRoboImage("screenshots/4e_tracker_bet_sheet.png")
    }

    @Test
    fun `a bet with no fair price read says so instead of showing a blank`() {
        val bare = SampleScan.bets.first { it.id == "b4" }.copy(nowFair = null, nowEv = null, books = emptyList(), nowAtMs = null)
        sheet(bare)
        compose.onNodeWithText("No fair price read yet: tap Re-read books.", substring = true).assertExists()
        compose.onNodeWithText("No book has been read for this bet yet: tap Re-read books.").assertExists()
    }

    @Test
    fun `a Vigilant bet with no CNO page is priced from Vigilant's fair odds on Price now`() {
        val priced = mutableListOf<Pair<String, Boolean>>()
        sheet(
            SampleScan.bets.first { it.id == "b3" }.copy(nowFair = null, nowEv = null, books = emptyList()),
            actions = BetActions(onReread = { id, quiet -> priced += id to quiet }),
        )
        compose.onNodeWithText("Vigilant prices its own bets from Vigilant's fair odds", substring = true).assertExists()
        compose.onAllNodesWithText("Re-read books").assertCountEquals(0)
        compose.onNodeWithText("Price now").performScrollTo().performClick()
        assertEquals(listOf("b3" to false), priced)
    }

    @Test
    fun `an EV that is hours old says when it was read instead of now, and a bet nothing could price says why`() {
        val old = sheetBet().copy(nowEv = 0.08, nowAtMs = now - 3 * hour, nowVia = com.tjshea.vigilant.data.tracker.BetTracker.VIA_VIGILANT)
        sheet(old)
        compose.onNodeWithText("EV at your price, as of 3h ago", substring = true).assertExists()
        compose.onAllNodesWithText("Now +8.00% EV at your price").assertCountEquals(0)
        compose.onNodeWithText("Fair price worked out 3h ago by Vigilant", substring = true).assertExists()
    }

    @Test
    fun `the sheet of a bet nothing could price gives the reason`() {
        val bare = SampleScan.bets.first { it.id == "b3" }.copy(nowFair = null, nowEv = null, books = emptyList(), nowNote = "No fair-odds source lists this game", nowNoteAtMs = now - 60_000L)
        sheet(bare)
        compose.onNodeWithText("Not priced: No fair-odds source lists this game (tried 1m ago)").assertExists()
    }

    @Test
    fun `Replace in the sheet names the amount Settings fills in`() {
        val replaced = mutableListOf<TrackedBet>()
        sheet(settings = SampleScan.settings.copy(slipStake = SlipStake.CUSTOM, slipCustomStake = 5.0), actions = BetActions(onReplace = { replaced += it }))
        compose.onNodeWithTag("replaceInSheet").performScrollTo().performClick()
        assertEquals(1, replaced.size)
        compose.onNodeWithText("Replace bet · $5").assertExists()
        compose.onNodeWithText("with $5 filled in", substring = true).assertExists()
    }

    @Test
    fun `Replace with no amount set is just Replace bet`() {
        sheet(settings = SampleScan.settings.copy(slipStake = SlipStake.OFF))
        compose.onNodeWithText("Replace bet").assertExists()
    }

    @Test
    fun `a bet whose game started can be marked, graded now, or given back to auto-grading`() {
        val marked = mutableListOf<BetStatus>()
        var graded = 0
        var regraded = 0
        val started = stuck("s", "no box score", manual = true).copy(settledBy = "you")
        sheet(started, actions = BetActions(onGrade = { graded++ }, onRegrade = { regraded++ }), onSettle = { marked += it })
        compose.onNodeWithText("Won").performScrollTo().performClick()
        compose.onNodeWithText("Push").performClick()
        compose.onNodeWithText("Grade now").performScrollTo().performClick()
        compose.onAllNodesWithText("Grade automatically").onFirst().performScrollTo().performClick()
        assertEquals(listOf(BetStatus.WON, BetStatus.PUSH), marked)
        assertEquals(1, graded)
        assertEquals(1, regraded)
    }

    @Test
    fun `an upcoming bet has no result buttons`() {
        sheet()
        compose.onAllNodesWithText("Won").assertCountEquals(0)
        compose.onAllNodesWithText("Grade now").assertCountEquals(0)
    }

    @Test
    fun `a settled bet says how it was graded`() {
        val done = SampleScan.bets.first { it.id == "b6" }.copy(gradeNote = "Final: Tampa Bay Rays 3, Boston Red Sox 5")
        sheet(done)
        compose.onNodeWithText("Won · graded from the final score", substring = true).assertExists()
        compose.onNodeWithText("Final: Tampa Bay Rays 3, Boston Red Sox 5", substring = true).assertExists()
        compose.onNodeWithText("Won · undo").assertExists()
        compose.onAllNodesWithText("Replace bet", substring = true).assertCountEquals(0)
    }

    // ---- the "Check odds now" counter (Tj, 2026-09-29) --------------------------------------------------------------

    /** The sample's open bets re-read [secondsAgo] ago with these EVs (b3, b4, b5), and a settled one read then too. */
    private fun checked(evs: List<Double>, secondsAgo: Long = 30, startedSecondsAgo: Long = 60, checking: Boolean = false): UiState {
        val base = SampleScan.state()
        val open = listOf("b3", "b4", "b5")
        val bets = base.bets.map { b ->
            val i = open.indexOf(b.id)
            when {
                // The fair now that gives that EV at the bet's cost, as every read writes the two together (EV = fair / cost − 1).
                i in evs.indices -> b.copy(nowEv = evs[i], nowFair = (1 + evs[i]) * b.cost, nowAtMs = now - secondsAgo * 1000)
                b.id == "b1" -> b.copy(nowEv = 0.03, nowAtMs = now - secondsAgo * 1000) // settled: never counted
                else -> b
            }
        }
        return base.copy(bets = bets, checkStartedAtMs = now - startedSecondsAgo * 1000, checkingOdds = checking)
    }

    @Test
    fun `before any Check odds now there is no counter`() {
        screen { TrackerScreen(SampleScan.state().copy(checkStartedAtMs = null), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithTag("checkCounter").assertDoesNotExist()
        compose.onNodeWithTag("checkCaption").assertDoesNotExist()
    }

    @Test
    fun `the counter shows +EV and −EV open bets, the share +EV, and the average EV without the ones over 5 percent`() {
        screen { TrackerScreen(checked(listOf(0.02, -0.01, 0.09)), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        // 0.09 is +EV but over 5%: counted, left out of the average ((0.02 − 0.01) / 2).
        compose.onNodeWithContentDescription("2 +EV · 1 −EV · 67% +EV · Avg +0.5% EV").assertIsDisplayed()
        compose.onNodeWithTag("checkCaption").assertTextEquals("Open bets re-priced in the check 1m ago · 1 over ±5% left out of the average")
    }

    @Test
    fun `a new check starts the counter from zero and counts up as the odds come in`() {
        // The check began just now: the reads from 30 s ago are the last check's.
        screen { TrackerScreen(checked(listOf(0.02, -0.01, 0.01), startedSecondsAgo = 0, checking = true).copy(checkProgress = 0 to 3), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onNodeWithContentDescription("0 +EV · 0 −EV · – +EV · Avg – EV").assertIsDisplayed()
        compose.onNodeWithTag("checkCaption").assertTextEquals("Open bets re-priced so far in this check (0/3 read)")
    }

    @Test
    fun `the counter is on the Stats view too`() {
        screen { TrackerScreen(checked(listOf(0.02, -0.01, -0.02)), { _, _ -> }, {}, initialView = TrackerView.STATS) }
        compose.onNodeWithContentDescription("1 +EV · 2 −EV · 33% +EV · Avg −0.3% EV").assertIsDisplayed()
    }

    @Test
    fun `screenshot - the Check odds now counter`() {
        screen { TrackerScreen(checked(listOf(0.021, -0.012, 0.074)), { _, _ -> }, {}, initialView = TrackerView.BETS) }
        compose.onRoot().captureRoboImage("screenshots/4i_tracker_check_counter.png")
    }
}
