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

    /** Full test, 2026-09-27: a doubleheader's game 2 is a different bet; a football kickoff the feeds disagree on isn't. */
    @Test
    fun `baseball needs the same start within two hours, football within twelve`() {
        val mets = "New York Mets @ Washington Nationals"
        val game1 = PlacedBet(key = "cno:g1", title = "New York Mets", event = mets, market = "Moneyline", league = "MLB", placedAtMs = now, startsAtMs = start)
        val index = PlacedIndex.of(listOf(game1), emptyList(), now)
        assertTrue(index.has(event = mets, market = "Moneyline", selection = "New York Mets", startsTs = start + 10 * 60_000L, league = "MLB"))
        // Game 2, five hours later: not placed.
        assertFalse(index.has(event = mets, market = "Moneyline", selection = "New York Mets", startsTs = start + 5 * 3_600_000L, league = "MLB"))
        // A mark saved without a league still reads the listing's.
        assertFalse(PlacedIndex.of(listOf(game1.copy(league = "")), emptyList(), now)
            .has(event = mets, market = "Moneyline", selection = "New York Mets", startsTs = start + 5 * 3_600_000L, league = "MLB"))
        // Football: the same kickoff listed 5 hours apart is still the one game.
        val bengals = PlacedBet(key = "cno:f", title = "Erick All Jr. Under 0.5", event = event, market = "Player Receptions", league = "NFL", placedAtMs = now, startsAtMs = start)
        assertTrue(PlacedIndex.of(listOf(bengals), emptyList(), now)
            .has(event = event, market = "Receptions", selection = "Erick All Jr. Under 0.5", startsTs = start + 5 * 3_600_000L, league = "NFL"))
    }

    @Test
    fun `with no start time to compare, only a mark from the last day counts`() {
        val noStart = cnoMark.copy(startsAtMs = null)
        fun has(index: PlacedIndex) = index.has(event = event, market = "Receptions", selection = "Erick All Jr. Under 0.5", startsTs = start)
        assertTrue(has(PlacedIndex.of(listOf(noStart), emptyList(), now + 3_600_000L)))
        // Marked three days ago, game unknown: the same pairing now is a later game.
        assertFalse(has(PlacedIndex.of(listOf(noStart), emptyList(), now + 72 * 3_600_000L)))
        // And a listing with no start of its own is judged the same way.
        val index = PlacedIndex.of(listOf(cnoMark), emptyList(), now + 72 * 3_600_000L)
        assertFalse(index.has(event = event, market = "Receptions", selection = "Erick All Jr. Under 0.5", startsTs = null))
    }
}
