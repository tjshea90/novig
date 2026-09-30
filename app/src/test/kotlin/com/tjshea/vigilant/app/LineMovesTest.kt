package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.GamesScreen
import com.tjshea.vigilant.app.ui.LINE_MOVES_CARD
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.OpportunityCard
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.moveText
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.LineMoves
import com.tjshea.vigilant.data.reference.Mover
import com.tjshea.vigilant.data.reference.MoversBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pinnacle's line moves (Tj, 2026-09-30, PARLAY_API.md §6.3): the Games tab lists the picked leagues' biggest moves, and a +EV team bet whose
 * game moved says whether Pinnacle moved toward it or against it. Props and totals never get a note.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class LineMovesTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    /** Dallas steamed from -160 to -175 against Baltimore at Pinnacle (the sample's game g1). */
    private val board = MoversBoard(
        "americanfootball_nfl", 360, now,
        listOf(Mover("americanfootball_nfl", "Dallas Cowboys", "Baltimore Ravens", now + 50 * 3_600_000L, -160, -175, 140, 150, 2.1, -2.0, 80)),
    )

    private fun state(): UiState {
        val base = SampleScan.state(SampleScan.settings.copy(useParlay = true))
        val movers = mapOf("americanfootball_nfl" to board)
        return base.copy(movers = movers, lineMoves = LineMoves.notes(movers, base.result!!.opportunities, emptyList(), emptyList(), now))
    }

    private fun screen(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    @Test
    fun `a team bet's note says which way Pinnacle went for its side, a prop or total gets none`() {
        val s = state()
        val ops = s.result!!.opportunities
        val dal = ops.first { it.kind == LineKind.MONEYLINE && it.event.description == "Baltimore Ravens @ Dallas Cowboys" && it.selection.contains("Dallas") || it.kind == LineKind.MONEYLINE && it.selection == "DAL" }
        assertEquals(true, s.lineMoves[dal.key]?.toward)
        val bal = ops.first { it.kind == LineKind.MONEYLINE && it.event.eventId == dal.event.eventId && it.key != dal.key }
        assertEquals(false, s.lineMoves[bal.key]?.toward)
        assertTrue(ops.filter { it.kind == LineKind.PLAYER_PROP || it.kind == LineKind.TOTAL }.none { it.key in s.lineMoves })
        assertEquals("Pinnacle moved toward it: -160 → -175 (+2.1 pts in 6 h)", moveText(s.lineMoves.getValue(dal.key)))
        // Another game's bets: nothing.
        assertNull(ops.firstOrNull { it.event.eventId != dal.event.eventId && it.key in s.lineMoves })
    }

    @Test
    fun `the +EV card shows the note under its game`() {
        val s = state()
        val o = s.result!!.opportunities.first { it.key in s.lineMoves && it.quote != null && it.fairProbability != null }
        screen { OpportunityCard(o, s.settings, now, onOpen = false, move = s.lineMoves[o.key]) {} }
        compose.onNodeWithText(moveText(s.lineMoves.getValue(o.key))).assertExists()
    }

    @Test
    fun `the Games tab lists the biggest moves with the side the money went to`() {
        screen { GamesScreen(state(), onOpen = {}, onToggleLeague = {}) }
        compose.onNodeWithText("Line moves at Pinnacle · last 6 h").assertExists()
        compose.onNodeWithText("Baltimore Ravens @ Dallas Cowboys").assertExists()
        compose.onNodeWithText("Money on Dallas Cowboys: -160 → -175 (+2.1 pts)").assertExists()
    }

    @Test
    fun `with ParlayAPI off the Games tab has no moves card`() {
        screen { GamesScreen(state().let { it.copy(settings = it.settings.copy(useParlay = false)) }, onOpen = {}, onToggleLeague = {}) }
        compose.onNodeWithTag(LINE_MOVES_CARD).assertDoesNotExist()
    }
}
