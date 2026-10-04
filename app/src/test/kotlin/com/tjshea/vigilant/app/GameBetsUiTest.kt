package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.GameBetsChip
import com.tjshea.vigilant.app.ui.GameBetsDetail
import com.tjshea.vigilant.app.ui.GameBetsText
import com.tjshea.vigilant.app.ui.GameBetsView
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.LocalGameBets
import com.tjshea.vigilant.app.ui.OpportunityCard
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.GameBets
import com.tjshea.vigilant.data.tracker.GameRef
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The button next to every listed bet (Tj, 2026-10-04: "a quick button next to each bet in the scanners that involve the la rams game (and the team they
 * are playing) which pulls up which bets I already placed involving that game, money per bet, and total money across all bets for that game … the
 * button itself show the total I already bet involving that game … a small button or drop down box"): "$21.30 in game ▾" on a bet in a game he has money
 * on, nothing on a bet in a game he hasn't, and a tap lists each bet's money and the total.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1200dp-xxhdpi")
class GameBetsUiTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    private fun screen(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    private fun bet(id: String, o: Opportunity, stake: Double, selection: String, market: String = "m-$id", eventId: String = o.market.eventId, status: BetStatus = BetStatus.PENDING) = TrackedBet(
        id = id, createdAtMs = now - 60_000, league = o.event.league, eventName = o.event.description, startsTs = o.event.startsTs, marketLabel = "Player prop", selection = selection,
        marketId = market, outcomeId = "out-$id", price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 0.04, stake = stake, status = status, american = 105,
        orderId = "ord-$id", contracts = (stake / 0.5 * 100).toLong(), paid = stake, fee = 0.0, eventId = eventId,
    )

    private val all get() = SampleScan.state().result!!.opportunities.filter { it.quote != null && it.fairProbability != null }

    private fun fixture(): Triple<Opportunity, Opportunity, GameBets> {
        val first = all.first()
        val other = all.first { it.event.eventId != first.event.eventId }
        val bets = listOf(bet("a", first, 2.5, "Matthew Stafford Over 250.5"), bet("b", first, 3.0, "Total Under 44.5"), bet("c", other, 9.0, "Another game's bet"))
        return Triple(first, other, GameBets.of(bets, emptyList()))
    }

    @Test
    fun `a bet in a game Tj has money on shows the total on a small button, a bet in another game shows none`() {
        val (first, other, index) = fixture()
        screen {
            CompositionLocalProvider(LocalGameBets provides GameBetsView(index)) {
                OpportunityCard(first, SampleScan.settings, now, onOpen = false) {}
            }
        }
        compose.onNodeWithTag("gameBetsChip").assertTextContains("\$5.50 in game", substring = true)
        compose.onRoot().captureRoboImage("screenshots/0_game_bets_chip.png")
        // The other game's card (its own $9.00) says its own total, never this game's.
        compose.setContentOther(other, index)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setContentOther(o: Opportunity, index: GameBets) {
        // A second composition in the same rule would replace the first: this checks the lookup the card makes instead.
        assertEquals(9.0, index.of(o.event.description, o.event.startsTs, o.event.league, o.market.eventId)!!.placed, 1e-9)
    }

    @Test
    fun `a bet in a game he has nothing on shows no button at all`() {
        val (first, _, _) = fixture()
        val none = GameBets.of(listOf(bet("z", all.first { it.event.eventId != first.event.eventId }, 4.0, "Elsewhere")), emptyList())
        screen {
            CompositionLocalProvider(LocalGameBets provides GameBetsView(none)) {
                OpportunityCard(first, SampleScan.settings, now, onOpen = false) {}
            }
        }
        compose.onNodeWithTag("gameBetsChip").assertDoesNotExistCompat()
    }

    @Test
    fun `pressing the button lists each bet with its money and the total`() {
        val (first, _, index) = fixture()
        screen {
            CompositionLocalProvider(LocalGameBets provides GameBetsView(index)) {
                OpportunityCard(first, SampleScan.settings, now, onOpen = false) {}
            }
        }
        compose.onNodeWithTag("gameBetsChip").performClick()
        compose.onNodeWithText("Your bets in this game").assertExistsCompat()
        compose.onNodeWithText("Matthew Stafford Over 250.5").assertExistsCompat()
        compose.onNodeWithText("Total Under 44.5").assertExistsCompat()
        compose.onNodeWithText("Total bet \$5.50 across 2 bets").assertExistsCompat()
        compose.onNodeWithTag("gameBetsTotal").assertTextEquals("\$5.50")
    }

    @Test
    fun `the sheet's rows read as money per bet, bids apart from bets, and the limit's room`() {
        val first = all.first()
        val bid = MakerBid(
            clientId = "bid1", orderId = "ord-bid1", marketId = "mb", eventId = first.market.eventId, outcomeId = "ob", league = first.event.league, eventName = first.event.description,
            startsTs = first.event.startsTs, marketLabel = "Receiving Yards", selection = "Puka Nacua Over 70.5", price = 0.45, contracts = 1_000, fair = 0.5, evAtFair = 0.05,
            margin = 0.04, postedAtMs = now, status = MakerStatus.RESTING,
        )
        val s = GameBets.of(listOf(bet("a", first, 2.5, "Matthew Stafford Over 250.5"), bet("b", first, 3.0, "Total Under 44.5")), listOf(bid))
            .of(GameRef(first.market.eventId, first.event.description, first.event.startsTs, first.event.league))!!
        screen { GameBetsDetail(s, limit = 10.0) }
        compose.onNodeWithText("Resting bids (not placed yet)").assertExistsCompat()
        compose.onNodeWithText("Puka Nacua Over 70.5").assertExistsCompat()
        compose.onNodeWithTag("gameBetsTotal").assertTextEquals("\$5.50")
        // $5.50 bet + $4.50 bid = $10.00: exactly at the $10 limit, so the sheet says so and nothing is left.
        compose.onNodeWithTag("gameBetsLimit").assertTextContains("At your \$10.00 limit per game", substring = true)
        compose.onRoot().captureRoboImage("screenshots/0_game_bets_sheet.png")
    }

    @Test
    fun `the words`() {
        val g = GameRef("e1", "Los Angeles Rams @ Philadelphia Eagles", now, "NFL")
        fun line(id: String, kind: GameBets.Kind, d: Double, auto: Boolean = false, american: Int? = 110) = GameBets.Line(id, kind, "sel-$id", "Total", american, d, now, auto)
        val s = GameBets.Summary(g, listOf(line("a", GameBets.Kind.BET, 6.0, auto = true), line("b", GameBets.Kind.BET, 15.3)), emptyList(), atRisk = 21.3)
        assertEquals("\$21.30 in game", GameBetsText.chip(s))
        assertEquals("Total bet \$21.30 across 2 bets", GameBetsText.total(s))
        assertEquals("Total · +110 · auto-bet", GameBetsText.detail(line("a", GameBets.Kind.BET, 6.0, auto = true)))
        assertEquals("Total · +110", GameBetsText.detail(line("b", GameBets.Kind.BET, 1.0)))
        assertEquals("Total · +110 · lock", GameBetsText.detail(line("c", GameBets.Kind.LOCK, 1.0)))
        assertEquals("Total · +110 · bid resting, not a bet yet", GameBetsText.detail(line("d", GameBets.Kind.BID, 1.0)))
        assertEquals("\$21.30 bet in this game on 2 bets, press for details", GameBetsText.chipDescription(s))
        assertEquals("\$3.70 room under your \$25.00 limit per game", GameBetsText.limit(s, 25.0))
        assertEquals("At your \$20.00 limit per game: auto-bet and bids add nothing more here", GameBetsText.limit(s, 20.0))
        assertNull(GameBetsText.limit(s, 0.0))
        assertEquals(false, GameBetsText.atLimit(s, 25.0))
        assertEquals(true, GameBetsText.atLimit(s, 21.3))
        assertEquals(false, GameBetsText.atLimit(s, 0.0))
        assertNull(GameBetsText.hedgeNote(s))
        val hedged = s.copy(atRisk = 15.3)
        assertEquals("\$15.30 at risk: a lock, or both sides of one market, counts once", GameBetsText.hedgeNote(hedged))
        val bidsOnly = GameBets.Summary(g, emptyList(), listOf(line("d", GameBets.Kind.BID, 4.5)), atRisk = 4.5)
        assertEquals("\$4.50 in bids", GameBetsText.chip(bidsOnly))
    }

    @Test
    fun `the chip finds a game by a bare matchup and start too, the way a CrazyNinjaOdds row has it`() {
        val (first, _, index) = fixture()
        screen {
            CompositionLocalProvider(LocalGameBets provides GameBetsView(index)) {
                // No Novig event id: its teams and start find the game.
                GameBetsChip(first.event.description, first.event.startsTs, first.event.league)
            }
        }
        compose.onNodeWithTag("gameBetsChip").assertTextContains("\$5.50 in game", substring = true)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
