package com.tjshea.vigilant.data.alerts

import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetRecheck
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.PlacedBook
import com.tjshea.vigilant.data.tracker.PlacedBets
import com.tjshea.vigilant.data.tracker.PlacedIndex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** "✓ Placed" on a +EV push notification (Tj, 2026-09-29): tracked, hidden everywhere, undoable. */
class AlertPlacementTest {

    @get:Rule val tmp = TemporaryFolder()
    private val now = 1_790_400_000_000L

    private fun tracker() = BetTracker(File(tmp.root, "bets.json"), clock = { now })
    private fun placedBets() = PlacedBets(JsonFileStore(File(tmp.root, "placed.json"), PlacedBook.serializer(), { PlacedBook() }), clock = { now })

    private fun cnoAlert(live: Boolean = false) = EvAlert(
        "CNO", "cno:https://crazyninjaodds.com/game?side_id=9|Novig", "out-1", "Justin Jefferson Under 69.5", "Player Receiving Yards",
        "Minnesota Vikings @ Tampa Bay Buccaneers", 117, 0.0584, 4, 3, now + 3_600_000L, "novigapp://events/out-1/cno", true, stake = 5.0,
        league = "NFL", gameUrl = "https://crazyninjaodds.com/game?side_id=9", betUrl = "https://crazyninjaodds.com/d?l=9", live = live,
    )

    private fun vigilantAlert() = EvAlert(
        "Vigilant", "mkt-1/out-2", "out-2", "Dallas Cowboys -3.5", "Spread", "Baltimore Ravens @ Dallas Cowboys", -105, 0.031, 5, 4,
        now + 3_600_000L, "novigapp://events/out-2", true, league = "NFL", marketId = "mkt-1", fair = 0.5301,
    )

    @Test
    fun `placed from a CNO alert: tracked at the alert's price and stake with everything a recheck and grading need`() = runTest {
        val t = tracker()
        val p = placedBets()
        val bet = AlertPlacement.place(cnoAlert(), 5.0, t, p, now)!!
        assertEquals(5.0, bet.stake, 0.0)
        assertEquals(117, bet.american)
        assertEquals(1.0 / 2.17, bet.cost, 1e-9) // pregame: no fee
        assertEquals((1.0 + 0.0584) * bet.cost, bet.fairAtBet!!, 1e-9)
        assertEquals(0.0584, bet.evPercentAtBet!!, 0.0)
        assertEquals(BetTracker.SOURCE_CNO, bet.source)
        assertEquals("NFL", bet.league)
        assertEquals("Player Receiving Yards", bet.marketLabel)
        assertEquals("out-1", bet.outcomeId)
        assertEquals("https://crazyninjaodds.com/game?side_id=9", bet.gameUrl)
        assertEquals(cnoAlert().key, bet.placedKey)
        // It can be rechecked (it has a CNO page) and graded (it has a league and a readable market).
        assertEquals(1, BetRecheck(t, books = { null }, clock = { now }).due(t.all()).size)
        // The mark is under the alert's own key and Novig outcome, so every list hides it.
        val mark = p.load().bets.single()
        assertEquals(cnoAlert().key, mark.key)
        assertEquals("out-1", mark.outcomeId)
        assertEquals("+117", mark.odds)
        assertFalse(mark.hidden)
        val index = PlacedIndex.of(p.load().bets, t.all(), now)
        assertTrue(index.has(key = "other-scanner-key", outcomeId = "out-1"))
    }

    @Test
    fun `placed from a Vigilant alert: its Novig market and outcome, so scans follow it to the close`() = runTest {
        val t = tracker()
        val bet = AlertPlacement.place(vigilantAlert(), null, t, placedBets(), now)!!
        assertEquals(1.0, bet.stake, 0.0) // no amount in the alert: $1, corrected in the Tracker
        assertEquals(BetTracker.SOURCE_VIGILANT, bet.source)
        assertEquals("mkt-1", bet.marketId)
        assertEquals("out-2", bet.outcomeId)
        assertEquals(0.5301, bet.fairAtBet!!, 0.0)
        assertEquals(-105, bet.american)
    }

    @Test
    fun `a live alert's cost has Novig's taker fee in it, and placing it twice is one bet`() = runTest {
        val t = tracker()
        val live = AlertPlacement.place(cnoAlert(live = true), 5.0, t, placedBets(), now)!!
        assertTrue(live.cost > live.price)
        AlertPlacement.place(cnoAlert(live = true), 5.0, t, placedBets(), now)
        assertEquals(1, t.all().size)
    }

    @Test
    fun `Undo takes the mark and the open bet away, a settled bet stays`() = runTest {
        val t = tracker()
        val p = placedBets()
        val a = cnoAlert()
        AlertPlacement.place(a, 5.0, t, p, now)
        AlertPlacement.undo(a, t, p)
        assertTrue(t.all().isEmpty())
        assertTrue(p.load().bets.isEmpty())
        val kept = AlertPlacement.place(a, 5.0, t, p, now)!!
        t.settle(kept.id, BetStatus.WON)
        AlertPlacement.undo(a, t, p)
        assertEquals(1, t.all().size)
    }

    @Test
    fun `an alert survives the trip through a notification's extras`() {
        val json = Json.encodeToString(EvAlert.serializer(), cnoAlert())
        val back = Json.decodeFromString(EvAlert.serializer(), json)
        assertEquals(cnoAlert(), back)
        assertNotNull(back.gameUrl)
        // An alert from before these fields existed (the alert log never stores one, but an extra could) reads with defaults.
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(
            EvAlert.serializer(),
            """{"scanner":"CNO","key":"k","outcomeId":null,"bet":"B","market":"M","event":"E","american":100,"ev":0.03,"books":3,"agreeing":3,"startsAtMs":null,"link":null,"exact":false}""",
        )
        assertEquals("Novig", old.book)
        assertEquals("", old.league)
    }
}
