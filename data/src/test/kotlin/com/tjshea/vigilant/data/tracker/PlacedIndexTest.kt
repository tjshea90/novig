package com.tjshea.vigilant.data.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-27: "some bets are showing up in the vigilant positive EV scanner which I already placed
 * in the cno scanner widget … hides bets I already placed throughout the whole app regardless of scanner".
 */
class PlacedIndexTest {

    private val now = 1_790_600_000_000L
    private val start = now + 3 * 3_600_000L
    private val event = "Cincinnati Bengals @ Pittsburgh Steelers"

    /** A CNO bet ✓'d in the widget before v0.16.2: key and detail only. */
    private val cnoMark = PlacedBet(
        key = "cno:https://crazyninjaodds.com/site/browse/game.aspx?game_id=9&side_id=1|Novig", title = "Erick All Jr. Under 0.5",
        detail = "Player Receptions · $event", placedAtMs = now, startsAtMs = start,
    )

    @Test
    fun `a CNO bet placed in the widget hides Vigilant's listing of the same bet`() {
        val index = PlacedIndex.of(listOf(cnoMark), emptyList(), now)
        // Vigilant's own wording for it: "Receptions" (Novig's stat), its own key.
        assertTrue(index.has(key = "m1/o1", event = event, market = "Receptions", selection = "Erick All Jr. Under 0.5", startsTs = start + 60_000))
        // Another line, the other side, another player, another stat: not placed.
        assertFalse(index.has(key = "m1/o2", event = event, market = "Receptions", selection = "Erick All Jr. Over 0.5", startsTs = start))
        assertFalse(index.has(key = "m2/o1", event = event, market = "Receptions", selection = "Erick All Jr. Under 1.5", startsTs = start))
        assertFalse(index.has(key = "m3/o1", event = event, market = "Receptions", selection = "Ja'Marr Chase Under 0.5", startsTs = start))
        assertFalse(index.has(key = "m4/o1", event = event, market = "Receiving Yards", selection = "Erick All Jr. Under 0.5", startsTs = start))
    }

    @Test
    fun `the same pairing a day later is a different game`() {
        val index = PlacedIndex.of(listOf(cnoMark), emptyList(), now)
        assertFalse(index.has(event = event, market = "Receptions", selection = "Erick All Jr. Under 0.5", startsTs = start + 24 * 3_600_000L))
    }

    @Test
    fun `a Novig outcome placed from either scanner hides it everywhere`() {
        val index = PlacedIndex.of(listOf(cnoMark.copy(outcomeId = "novig-outcome-7")), emptyList(), now)
        assertTrue(index.has(key = "cno:another-row|Novig", outcomeId = "novig-outcome-7"))
    }

    @Test
    fun `bets in the Tracker count too, by key, outcome and wording`() {
        val tracked = TrackedBet(
            id = "t", createdAtMs = now, league = "NFL", eventName = "Seattle Seahawks @ Washington Commanders", startsTs = start,
            marketLabel = "Team Total", selection = "Washington Commanders Over 15.5", marketId = "mk", outcomeId = "oc",
            price = 0.49, cost = 0.49, fairAtBet = 0.505, evPercentAtBet = 0.03, stake = 1.0,
        )
        val index = PlacedIndex.of(emptyList(), listOf(tracked), now)
        assertTrue(index.has(key = "mk/oc"))
        assertTrue(index.has(outcomeId = "oc"))
        // CNO's wording of the same bet.
        assertTrue(index.has(key = "cno:x", event = "Seattle Seahawks @ Washington Commanders", market = "Team Total Points", selection = "Washington Commanders Over 15.5", startsTs = start))
        // A bet on a game long over can't be on a list, and isn't kept in the index.
        assertTrue(PlacedIndex.of(emptyList(), listOf(tracked.copy(startsTs = now - 48 * 3_600_000L)), now).isEmpty)
    }

    @Test
    fun `game lines match across both scanners' wording`() {
        val mark = PlacedBet(key = "cno:r", title = "Houston Texans -3.5", detail = "Point Spread · Houston Texans @ Indianapolis Colts", placedAtMs = now, startsAtMs = start)
        val index = PlacedIndex.of(listOf(mark), emptyList(), now)
        assertTrue(index.has(event = "Houston Texans @ Indianapolis Colts", market = "Spread", selection = "Houston Texans -3.5", startsTs = start))
        assertFalse(index.has(event = "Houston Texans @ Indianapolis Colts", market = "Spread", selection = "Indianapolis Colts +3.5", startsTs = start))
    }
}
