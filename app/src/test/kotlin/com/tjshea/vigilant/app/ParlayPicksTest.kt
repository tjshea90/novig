package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ApiBetActions
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.LocalApiBet
import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.PARLAY_PICKS
import com.tjshea.vigilant.app.ui.ParlayPickActions
import com.tjshea.vigilant.app.ui.ParlayPicksUi
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.parlayItem
import com.tjshea.vigilant.app.ui.parlayShown
import com.tjshea.vigilant.app.ui.vigilantAsks
import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoState
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.tracker.OpenBetPricer
import com.tjshea.vigilant.data.reference.ParlayBestBets
import com.tjshea.vigilant.data.reference.ParlayPick
import com.tjshea.vigilant.data.scanner.Leagues
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ParlayAPI's own +EV list at Novig on the +EV tab (Tj, 2026-09-30, PARLAY_API.md §6.5): read only on a tap (10 credits a league), each play
 * shown at Novig's price now with Vigilant's EV there, never at the price ParlayAPI listed; plays that aren't +EV at Novig or aren't in its
 * catalog stay out; its edge alerts say "verify first"; ✓ and ✕ as on CNO's cards.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class ParlayPicksTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun picks(): List<ParlayPick> {
        // Four entries of ParlayAPI's real MLB answer (data/src/test/resources/parlay-best-bets-mlb.json), trimmed.
        val body = """{"best_bets":[
            {"bet":"Carson Kelly Over 0.5 Home Runs (Chicago Cubs @ San Diego Padres)","market_key":"player_home_runs","fair_price":900,"best_price":2122,"best_book":"novig","edge_pct":5.5,"verdict":"BET","books_compared":3},
            {"bet":"Seiya Suzuki Over 0.5 Home Runs (Chicago Cubs @ San Diego Padres)","market_key":"player_home_runs","fair_price":525,"best_price":809,"best_book":"novig","edge_pct":5.0,"verdict":"BET","books_compared":3},
            {"bet":"Austin Wells Over 0.5 Batter Home Runs (Boston Red Sox @ New York Yankees)","market_key":"player_home_runs","fair_price":530,"best_price":733,"best_book":"novig","edge_pct":3.87,"verdict":"BET","books_compared":4}],
            "edge_alerts":[{"bet":"Ian Happ Over 0.5 Home Runs (Chicago Cubs @ San Diego Padres)","book":"novig","price":5163,"apparent_edge_pct":10.6,
            "caveat":"far better than the rest of the market, so it is either a rare soft mispricing worth grabbing fast or a stale/limited line; verify it is still live before betting"}]}"""
        val board = ParlayBestBets.parse(body, Json { ignoreUnknownKeys = true }, Leagues.byNovigName("MLB")!!, now)!!
        // Kelly (+2122 listed) is +950 at Novig now: still +EV against +900. Suzuki went against it; Wells isn't on Novig; the Happ alert holds up.
        val plays = board.plays.filter { it.player in setOf("Carson Kelly", "Seiya Suzuki", "Austin Wells", "Ian Happ") }
        val rows = plays.map { it.row(startsAtMs = now + 5 * 3_600_000L) }
        val live = mapOf(
            rows[0].key to LivePrice(950, 25.0, 0.045, now),
            rows[1].key to LivePrice(700, 30.0, -0.02, now),
            rows[3].key to LivePrice(900, 10.0, 0.06, now),
        )
        return ParlayPick.priced(plays, rows, live)
    }

    /** [maxOdds] 0: no odds cap (the default +300 would hide these home-run props). */
    private fun state(on: Boolean = true, maxOdds: Int = 0): UiState = SampleScan.state(SampleScan.settings.copy(useParlay = on, maxOdds = maxOdds)).copy(
        parlayKeys = listOf("pk-FAKE-0000"),
        parlayPicks = ParlayPicksUi(picks = picks(), readAtMs = now - 60_000, leagues = listOf("MLB")),
    ).indexed(now)

    private fun screen(state: UiState, actions: ParlayPickActions, bet: ApiBetActions? = null) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }, LocalApiBet provides bet) {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FeedScreen(state, onScan = {}, onToggleLeague = {}, onOpenSettings = {}, onTrack = { _, _ -> }, parlay = actions)
                }
            }
        }
    }

    @Test
    fun `only plays +EV at Novig's price now are shown, best first, and one already marked isn't`() {
        val s = state()
        val shown = s.parlayShown(now)
        assertEquals(listOf("Ian Happ Over 0.5", "Carson Kelly Over 0.5"), shown.map { it.row.bet })
        val marked = s.copy(placed = listOf(MiniWindow.placed(parlayItem(shown.first()), now))).indexed(now)
        assertEquals(listOf("Carson Kelly Over 0.5"), marked.parlayShown(now).map { it.row.bet })
    }

    @Test
    fun `the section says what ParlayAPI listed and what held up, with Novig's price now and the price it had listed`() {
        var scans = 0
        var placed: MiniWindow.Item? = null
        screen(state(), ParlayPickActions(onScan = { scans++ }, onPlaced = { placed = it }))
        compose.onNodeWithTag(PARLAY_PICKS).assertExists()
        compose.onNodeWithText("4 listed · 2 +EV at Novig now · 1 not found there · read 1m ago").assertExists()
        compose.onNodeWithText("Carson Kelly Over 0.5").assertExists()
        compose.onNodeWithText("ParlayAPI had +2122").assertExists()
        compose.onNodeWithText("+950").assertExists()
        compose.onNodeWithText("Seiya Suzuki Over 0.5").assertDoesNotExist()
        compose.onNodeWithText("Austin Wells Over 0.5").assertDoesNotExist()
        compose.onNodeWithText("Verify first", substring = true).assertExists()
        // Two leagues picked, both with props: 20 credits, only when tapped.
        compose.onNodeWithText("Scan ParlayAPI again · 20 credits (NFL, MLB)").performClick()
        assertEquals(1, scans)
        compose.onNodeWithContentDescription("I placed Carson Kelly Over 0.5: hide it").performClick()
        assertTrue(placed!!.key.startsWith("parlay:"))
        assertEquals("Carson Kelly Over 0.5", placed!!.title)
        compose.onNodeWithText("Placed: Carson Kelly Over 0.5. Logged in the Tracker.").assertExists()
    }

    @Test
    fun `plays over Tj's odds cap are left out and counted`() {
        val s = state(maxOdds = 300)
        assertTrue(s.parlayShown(now).isEmpty())
        screen(s, ParlayPickActions())
        compose.onNodeWithText("4 listed · 0 +EV at Novig now · 2 over your +300 odds cap · 1 not found there · read 1m ago").assertExists()
    }

    @Test
    fun `with ParlayAPI off there's no section`() {
        screen(state(on = false), ParlayPickActions())
        compose.onNodeWithTag(PARLAY_PICKS).assertDoesNotExist()
    }

    // ---- TASKS.md P1 / P2 / P4 (Tj, 2026-09-30) ---------------------------------------------------------------------------

    private fun happ(s: UiState) = s.parlayShown(now).first { it.row.bet == "Ian Happ Over 0.5" }
    private fun kelly(s: UiState) = s.parlayShown(now).first { it.row.bet == "Carson Kelly Over 0.5" }

    /** CNO's list with Carson Kelly's bet (fair +800) at Novig, read 30 s ago, and its game page. */
    private fun cnoKelly(start: Long) = CnoRow(
        ev = 0.03, startsAtMs = start, sport = "Baseball", league = "MLB", event = "Chicago Cubs @ San Diego Padres", market = "Player Home Runs",
        bet = "Carson Kelly Over 0.5", odds = 950, available = 20.0, book = "Novig", fairOdds = 800, books = 5, gameUrl = "https://crazyninjaodds.com/game?side_id=9",
    )

    private fun withCno(s: UiState): UiState {
        val row = cnoKelly(kelly(s).row.startsAtMs!!)
        return s.copy(cno = CnoState(snapshot = CnoSnapshot(url = "https://crazyninjaodds.com/view", rows = listOf(row), fetchedAtMs = now - 30_000L).let { it }))
    }

    private fun books(bet: String, at: Long) = CnoBooksView(
        bet = bet, otherBet = bet.replace("Over", "Under"),
        prices = listOf(
            CnoBookPrice("PN", 800, null, -1400, null), CnoBookPrice("DK", 750, null, -1300, null),
            CnoBookPrice("FD", 820, null, -1500, null), CnoBookPrice("NV", 900, 10.0, null, null),
        ),
        fetchedAtMs = at,
    )

    @Test
    fun `P1 each pick has the Bet button when betting through Novig's API is set up, and it bets that pick`() {
        val s = state()
        var got: ParlayPick? = null
        screen(s, ParlayPickActions(), ApiBetActions(true, {}, {}, betParlay = { got = it }))
        compose.onAllNodesWithTag("apiBet")[0].performClick()
        assertEquals("Ian Happ Over 0.5", got!!.row.bet)
        assertTrue(got!!.key.startsWith("parlay:"))
    }

    @Test
    fun `P1 without betting set up there's no Bet button on a pick`() {
        screen(state(), ParlayPickActions(), ApiBetActions(false, {}, {}))
        compose.onAllNodesWithTag("apiBet").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun `P2 each card shows CNO's and Vigilant's EV at the same Novig price beside ParlayAPI's, or why one has none`() {
        val base = withCno(state())
        val k = kelly(base)
        val s = base.copy(parlayPicks = base.parlayPicks.copy(vigilant = mapOf(k.key to OpenBetPricer.FairRead(0.10, now - 20_000L, null))))
        screen(s, ParlayPickActions())
        val cnoFair = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(800)
        // Kelly: CNO's fair +800 and Vigilant's 10% at Novig's +950.
        compose.onNode(hasTestTag("pickEv-CNO") and androidx.compose.ui.test.hasAnyDescendant(hasText(Format.evPercent(CnoBooks.evAt(cnoFair, 950, false)))), useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag("pickEv-Vigilant") and androidx.compose.ui.test.hasAnyDescendant(hasText(Format.evPercent(CnoBooks.evAt(0.10, 950, false)))), useUnmergedTree = true).assertExists()
        // Happ: CNO doesn't list it, and Vigilant hasn't read it: "—", and the card says why.
        compose.onNodeWithText("CNO: not on CNO's +EV list · Vigilant: not read yet: tap Recheck").assertExists()
    }

    @Test
    fun `P4 tapping a pick opens its sheet with every book's odds, ParlayAPI's books when CNO doesn't list it`() {
        val base = state()
        val h = happ(base)
        val s = base.copy(parlayPicks = base.parlayPicks.copy(books = mapOf(h.key to CnoBooksState(view = books(h.row.bet, now - 10_000L)))))
        val loads = ArrayList<Pair<String, Boolean>>()
        screen(s, ParlayPickActions(onLoadBooks = { p, force -> loads += p.row.bet to force }))
        compose.onNodeWithText("Ian Happ Over 0.5").performClick()
        compose.onNodeWithTag("parlayPickSheet").assertExists()
        compose.waitForIdle()
        assertEquals(listOf("Ian Happ Over 0.5" to false), loads)
        // The books, as CNO's sheet lists them: this bet, the other side, and Vigilant's worst-case verdict on them.
        compose.onNodeWithText("Pinnacle").assertExists()
        compose.onNodeWithText("DraftKings").assertExists()
        compose.onNodeWithText("This bet").assertExists()
        compose.onNodeWithText("Books read 10s ago from ParlayAPI's books").assertExists()
        compose.onNode(hasTestTag("pickEv-Books")).assertExists()
        compose.onNodeWithTag("openInSheet").assertExists()
        compose.onNodeWithText("Re-read books").performClick()
        assertEquals("Ian Happ Over 0.5" to true, loads.last())
    }

    @Test
    fun `P4 when CNO lists the same bet, its game page's books are the ones shown`() {
        val base = withCno(state())
        val k = kelly(base)
        val cnoRow = base.cno.snapshot!!.rows.single()
        val s = base.copy(books = mapOf(cnoRow.key to CnoBooksState(view = books(k.row.bet, now - 5_000L))))
        assertEquals(cnoRow, com.tjshea.vigilant.app.ui.pickCnoRow(s, k))
        screen(s, ParlayPickActions())
        compose.onNodeWithText("Carson Kelly Over 0.5").performClick()
        compose.onNodeWithText("Books read 5s ago from CNO's game page").assertExists()
    }

    @Test
    fun `P4 the sheet says what it's reading, and why when there's no book list`() {
        val base = state()
        val h = happ(base)
        screen(base.copy(parlayPicks = base.parlayPicks.copy(books = mapOf(h.key to CnoBooksState(error = "ParlayAPI has no other book pricing this exact bet right now")))), ParlayPickActions())
        compose.onNodeWithText("Ian Happ Over 0.5").performClick()
        compose.onNodeWithText("ParlayAPI has no other book pricing this exact bet right now").assertExists()
        compose.onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `P2 Vigilant's read asks only for picks not priced freshly by the last scan or read in the last 2 minutes`() {
        val base = state()
        val k = kelly(base)
        val h = happ(base)
        assertEquals(setOf(h.key, k.key), base.vigilantAsks(now).map { it.key }.toSet())
        // Kelly read 30 s ago: a recheck now asks for Happ only (no credits spent twice on the same answer).
        val recent = base.copy(parlayPicks = base.parlayPicks.copy(vigilant = mapOf(k.key to OpenBetPricer.FairRead(0.1, now - 30_000L, null))))
        assertEquals(listOf(h.key), recent.vigilantAsks(now).map { it.key })
        // Read 5 minutes ago, or a read that found no line: asked again.
        val old = base.copy(parlayPicks = base.parlayPicks.copy(vigilant = mapOf(k.key to OpenBetPricer.FairRead(0.1, now - 300_000L, null), h.key to OpenBetPricer.FairRead(null, null, "No fair-odds source has a line for this bet"))))
        assertEquals(setOf(h.key, k.key), old.vigilantAsks(now).map { it.key }.toSet())
    }

    @Test
    fun `P4 a sheet whose pick a new read dropped closes for good, and doesn't pop open when a later read lists it again`() {
        val first = state()
        var s by androidx.compose.runtime.mutableStateOf(first)
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { now }) {
                VigilantTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        FeedScreen(s, onScan = {}, onToggleLeague = {}, onOpenSettings = {}, onTrack = { _, _ -> }, parlay = ParlayPickActions())
                    }
                }
            }
        }
        compose.onNodeWithText("Ian Happ Over 0.5").performClick()
        compose.onNodeWithTag("parlayPickSheet").assertExists()
        s = first.copy(parlayPicks = first.parlayPicks.copy(picks = emptyList()))
        compose.waitForIdle()
        compose.onNodeWithTag("parlayPickSheet").assertDoesNotExist()
        s = first
        compose.waitForIdle()
        compose.onNodeWithTag("parlayPickSheet").assertDoesNotExist()
    }
}
