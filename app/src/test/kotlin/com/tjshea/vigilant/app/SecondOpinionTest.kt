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
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.CnoDetail
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.LocalOpinions
import com.tjshea.vigilant.app.ui.OpinionActions
import com.tjshea.vigilant.app.ui.OpinionUi
import com.tjshea.vigilant.app.ui.OpportunityDetail
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.opinionButtonText
import com.tjshea.vigilant.data.cno.CnoPick
import com.tjshea.vigilant.data.reference.InjuryTags
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.ParlayVerdicts
import com.tjshea.vigilant.data.reference.Verdict
import com.tjshea.vigilant.data.reference.VerdictQuery
import com.tjshea.vigilant.data.reference.VerdictQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ParlayAPI's "Second opinion" in the bet sheets (Tj, 2026-09-30, PARLAY_API.md §6.4): offered only while ParlayAPI is on with a key, asked
 * only on a tap (5 credits), for the bet at the price shown; the answer shows its verdict, fair price and Vigilant's own EV at that price.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class SecondOpinionTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private val asked = ArrayList<Pair<String, VerdictQuery>>()

    private fun screen(enabled: Boolean = true, opinions: Map<String, OpinionUi> = emptyMap(), content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }, LocalOpinions provides OpinionActions(enabled, opinions) { k, q -> asked += k to q }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    private val state = SampleCno.state()

    private fun moneyline() = state.result!!.opportunities.first { it.kind == LineKind.MONEYLINE && it.quote != null && it.fairProbability != null }

    @Test
    fun `a +EV bet's sheet asks about that bet at its price, only when tapped`() {
        val o = moneyline()
        screen { OpportunityDetail(o, state.settings, onTrack = {}) }
        assertEquals(0, asked.size)
        compose.onNodeWithText(opinionButtonText(false)).performScrollTo().performClick()
        val (key, q) = asked.single()
        assertEquals(o.key, key)
        assertEquals("h2h", q.market)
        assertEquals(VerdictQueries.of(o), q)
    }

    @Test
    fun `the answer shows the verdict, the fair price and Vigilant's EV at the price, and offers to ask again`() {
        val o = moneyline()
        val q = VerdictQueries.of(o)!!.copy(price = 133)
        val v = Verdict("FAIR", "Fair value is +138.", 138, 0.42, "pinnacle", 133, "novig", 17, "medium", -1.2, null)
        screen(opinions = mapOf(o.key to OpinionUi(q, verdict = v, atMs = now))) { OpportunityDetail(o, state.settings, onTrack = {}) }
        compose.onNodeWithText("FAIR").assertExists()
        compose.onNodeWithText("Fair +138 (42.0%) from Pinnacle").assertExists()
        compose.onNodeWithText("EV at +133: −2.14%").assertExists()
        compose.onNodeWithText("best +133 at Novig · 17 books compared · confidence medium · -1.2 pts since open").assertExists()
        compose.onNodeWithText(opinionButtonText(true)).assertExists()
    }

    @Test
    fun `a busy board says so, and with ParlayAPI off nothing is offered`() {
        val o = moneyline()
        val q = VerdictQueries.of(o)!!
        screen(opinions = mapOf(o.key to OpinionUi.of(q, ParlayVerdicts.Result.Busy, now))) { OpportunityDetail(o, state.settings, onTrack = {}) }
        compose.onNodeWithText("ParlayAPI's props board is busy: try again in a minute.").assertExists()
    }

    @Test
    fun `off, there's no button`() {
        screen(enabled = false) { OpportunityDetail(moneyline(), state.settings, onTrack = {}) }
        compose.onNodeWithText(opinionButtonText(false)).assertDoesNotExist()
    }

    @Test
    fun `an open Tracker bet is asked about at the price it was bet`() {
        val bet = com.tjshea.vigilant.data.tracker.TrackedBet(
            "b1", now - 3_600_000L, "NFL", "Baltimore Ravens @ Dallas Cowboys", now + 50 * 3_600_000L, "Moneyline", "Dallas Cowboys", "m", "o",
            0.62, 0.62, 0.64, 0.03, 10.0, american = -163,
        )
        val content: @Composable (com.tjshea.vigilant.data.tracker.TrackedBet) -> Unit = { b ->
            com.tjshea.vigilant.app.ui.BetSheetContent(
                b, com.tjshea.vigilant.data.tracker.BetInsight.of(b), now, state.settings, false, false, false,
                com.tjshea.vigilant.app.ui.BetActions(), {}, {}, {}, {},
            )
        }
        screen { content(bet) }
        compose.onNodeWithText(opinionButtonText(false)).performScrollTo().performClick()
        val (key, q) = asked.single()
        assertEquals(InjuryTags.betKey(bet), key)
        assertEquals("Dallas Cowboys", q.side)
        assertEquals(-163, q.price)
    }

    @Test
    fun `a CNO prop bet is asked about by its player and stat at CNO's price`() {
        val row = state.cno.snapshot!!.rows.first { it.bet.startsWith("Justin Jefferson") }
        screen { CnoDetail(CnoPick(row, row.ev, false), state.cno.snapshot, state.settings, state.cnoUrl, null, now) }
        compose.onNodeWithText(opinionButtonText(false)).performScrollTo().performClick()
        val (key, q) = asked.single()
        assertEquals(InjuryTags.cnoKey(row), key)
        assertEquals("Justin Jefferson", q.player)
        assertEquals("under", q.side)
        assertEquals(69.5, q.line!!, 0.0)
        assertEquals(row.odds, q.price)
        assertNotNull(q.market)
    }
}
