@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.CnoScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.MiniRow
import com.tjshea.vigilant.app.ui.OpportunityCard
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.TrackerView
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.reference.Injury
import com.tjshea.vigilant.data.reference.InjuryBook
import com.tjshea.vigilant.data.reference.InjuryIndex
import com.tjshea.vigilant.data.reference.InjuryTags
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Injury tags on prop bets (Tj, 2026-09-30, PARLAY_API.md §6.1): a player who may not play is tagged on Vigilant's +EV cards, CNO's cards,
 * the widget and the Tracker's open bets (red Out/IR, amber Doubtful/Questionable), and a tap shows ESPN's report. Active players aren't
 * tagged, and a game line never is.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class InjuryTagTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun book(): InjuryBook = InjuryIndex { now }.apply {
        record(
            "americanfootball_nfl",
            listOf(
                Injury("Lamar Jackson", "Out", team = "Baltimore Ravens", teamAbbr = "BAL", bodyPart = "Ankle", side = "Right", comment = "Jackson won't play Sunday."),
                Injury("Justin Jefferson", "Questionable", team = "Minnesota Vikings", teamAbbr = "MIN", bodyPart = "Hamstring", comment = "questionable"),
                Injury("Brock Bowers", "Active", team = "Las Vegas Raiders", teamAbbr = "LV"),
            ),
        )
    }.book.value

    private val lamarBet = TrackedBet(
        id = "b1", createdAtMs = now - 3_600_000L, league = "NFL", eventName = "Baltimore Ravens @ Dallas Cowboys", startsTs = now + 50 * 3_600_000L,
        marketLabel = "Player Passing Yards", selection = "Lamar Jackson Over 224.5", marketId = "m", outcomeId = "o",
        price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 0.04, stake = 10.0,
    )

    /** The sample lists (a +EV scan, CNO's rows, one open bet) with their tags worked out as the view model does. */
    private fun tagged(): UiState {
        val base = SampleCno.state().copy(bets = listOf(lamarBet, lamarBet.copy(id = "b2", status = BetStatus.WON))).indexed(now)
        val wants = InjuryTags.wants(base.result!!.opportunities, base.cno.snapshot!!.rows, base.teams, base.bets, now)
        return base.copy(injuries = InjuryTags.tags(book(), wants, now))
    }

    private fun screen(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    @Test
    fun `every list's prop bets are looked up, and only players who may not play are tagged`() {
        val state = tagged()
        val lamar = state.result!!.opportunities.first { it.kind == LineKind.PLAYER_PROP }
        assertEquals("OUT", state.injuries[lamar.key]?.tag)
        val jefferson = state.cno.snapshot!!.rows.first { it.bet.startsWith("Justin Jefferson") }
        assertEquals("QUESTIONABLE", state.injuries[InjuryTags.cnoKey(jefferson)]?.tag)
        // Active: no tag. A game line (Ohio -33.5) is never looked up.
        val bowers = state.cno.snapshot!!.rows.first { it.bet.startsWith("Brock Bowers") }
        assertNull(state.injuries[InjuryTags.cnoKey(bowers)])
        val wants = InjuryTags.wants(state.result!!.opportunities, state.cno.snapshot!!.rows, emptyMap(), state.bets, now)
        assertTrue(wants.none { it.player.startsWith("Ohio") })
        assertTrue(wants.none { it.key.contains("TEAM_TOTAL") })
        // The open bet is tagged, the settled copy isn't looked up.
        assertEquals("OUT", state.injuries[InjuryTags.betKey(lamarBet)]?.tag)
        assertFalse(wants.any { it.key == "bet:b2" })
        // Players nobody reported on are what the /injuries list is bought for: NFL's and MLB's, by sport.
        val uncovered = InjuryTags.uncovered(book(), wants, now)
        assertEquals(setOf("Amon-Ra St. Brown"), uncovered["americanfootball_nfl"])
        assertEquals(setOf("Walker Buehler", "Geraldo Perdomo"), uncovered["baseball_mlb"])
        // The widget carries the tag on the item by its own key.
        val item = MiniWindow.items(state, now).first { it.title.startsWith("Justin Jefferson") }
        assertEquals("Q", item.injury?.shortTag)
    }

    @Test
    fun `a +EV prop card shows the tag, and a tap shows ESPN's report`() {
        val state = tagged()
        val lamar = state.result!!.opportunities.first { it.kind == LineKind.PLAYER_PROP && it.quote != null }
        screen { OpportunityCard(lamar, state.settings, now, onOpen = false, injury = state.injuries[lamar.key]) {} }
        compose.onNodeWithText("OUT").assertExists().performClick()
        compose.onNodeWithText("Lamar Jackson: Out").assertExists()
        compose.onNodeWithText("Out · Right Ankle · Jackson won't play Sunday.").assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Lamar Jackson: Out").assertDoesNotExist()
    }

    @Test
    fun `CNO's cards tag the player, an active one isn't`() {
        screen { CnoScreen(tagged(), {}, {}) }
        compose.onNodeWithText("QUESTIONABLE").assertExists()
        compose.onNodeWithText("OUT").assertDoesNotExist()
    }

    @Test
    fun `the widget's row puts the short tag first on its second line`() {
        val item = MiniWindow.items(tagged(), now).first { it.title.startsWith("Justin Jefferson") }
        screen { MiniRow(item) }
        compose.onNodeWithText("Q · ", substring = true).assertExists()
    }

    @Test
    fun `an open prop bet in the Tracker is tagged, a settled one isn't`() {
        val state = tagged()
        screen { TrackerScreen(state, onSettle = { _, _ -> }, onDelete = {}, initialView = TrackerView.BETS) }
        compose.onNodeWithText("OUT").assertExists()
        // Only the open bet is listed under Open; the won copy under Settled has no tag even with a report on file.
        assertNull(state.injuries[InjuryTags.betKey(lamarBet.copy(id = "b2"))])
    }
}
