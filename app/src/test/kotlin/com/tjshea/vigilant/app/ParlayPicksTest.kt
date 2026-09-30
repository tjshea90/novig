package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.PARLAY_PICKS
import com.tjshea.vigilant.app.ui.ParlayPickActions
import com.tjshea.vigilant.app.ui.ParlayPicksUi
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.parlayItem
import com.tjshea.vigilant.app.ui.parlayShown
import com.tjshea.vigilant.data.cno.LivePrice
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

    private fun state(on: Boolean = true): UiState = SampleScan.state(SampleScan.settings.copy(useParlay = on)).copy(
        parlayKeys = listOf("pk-FAKE-0000"),
        parlayPicks = ParlayPicksUi(picks = picks(), readAtMs = now - 60_000, leagues = listOf("MLB")),
    ).indexed(now)

    private fun screen(state: UiState, actions: ParlayPickActions) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
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
    fun `with ParlayAPI off there's no section`() {
        screen(state(on = false), ParlayPickActions())
        compose.onNodeWithTag(PARLAY_PICKS).assertDoesNotExist()
    }
}
