package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-game exposure guard (Tj, 2026-10-04: "auto bet placed bets on a team at +5, then the same team at +6, then the same team at +10 … if that one
 * team loses badly, I lose many bets due to one event … figure out how to make it without incorrectly blocking bets on different games"). One game is
 * one Novig event: every market of it (moneyline, each alternate spread and total, its props) is one exposure, and nothing else is.
 */
class GameExposureTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun game(eventId: String = "e1", name: String = "Houston Texans @ Indianapolis Colts", start: Long = now + 5 * hour, league: String = "NFL") =
        GameRef(eventId, name, start, league)

    /** An open bet of [stake] dollars on [outcome] of [market] in [g]. */
    private fun bet(
        id: String, g: GameRef = game(), market: String = "m1", outcome: String = "o1", stake: Double = 10.0, status: BetStatus = BetStatus.PENDING,
        lockFor: String? = null, contracts: Long? = (stake / 0.5 * 100).toLong(),
    ) = TrackedBet(
        id = id, createdAtMs = now - hour, league = g.league, eventName = g.eventName, startsTs = g.startsTs, marketLabel = "Spread", selection = outcome,
        marketId = market, outcomeId = outcome, price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 0.04, stake = stake, status = status,
        orderId = "ord-$id", contracts = contracts, paid = stake, fee = 0.0, lockFor = lockFor, eventId = g.eventId,
    )

    private fun check(g: GameRef, bets: List<TrackedBet>, market: String, outcome: String, dollars: Double, cap: Double = 25.0) =
        GameExposure.check(g, GameExposure.items(bets), market, outcome, dollars, cap)

    // ---- the case Tj described: one team at +5, +6, +10 (three markets of one game) ----

    @Test
    fun `the same team at three lines of one game stacks, and the third full-size bet is the one that goes over the cap`() {
        val g = game()
        val first = bet("a", g, market = "spread+5", outcome = "HOU+5")
        val second = bet("b", g, market = "spread+6", outcome = "HOU+6")
        val c1 = check(g, listOf(first), "spread+6", "HOU+6", 10.0)
        assertEquals(10.0, c1.now, 1e-9)
        assertEquals(20.0, c1.after, 1e-9)
        assertFalse("two of $10 is under the $25 cap", c1.blocked)
        val c2 = check(g, listOf(first, second), "spread+10", "HOU+10", 10.0)
        assertEquals(20.0, c2.now, 1e-9)
        assertEquals(30.0, c2.after, 1e-9)
        assertTrue("the third, $30 on one game, is over the $25 cap", c2.blocked)
    }

    @Test
    fun `a smaller third bet that still fits under the cap is allowed - the cap is dollars, not a count of bets`() {
        val g = game()
        val open = listOf(bet("a", g, "spread+5", "HOU+5"), bet("b", g, "spread+6", "HOU+6"))
        assertFalse(check(g, open, "spread+10", "HOU+10", 5.0).blocked)
        assertTrue(check(g, open, "spread+10", "HOU+10", 5.01).blocked)
    }

    // ---- never blocking other games ----

    @Test
    fun `another game is never counted - a game at the cap does not block a bet on a different Novig event`() {
        val full = game(eventId = "e1")
        val other = game(eventId = "e2", name = "Houston Texans @ Indianapolis Colts", start = full.startsTs)
        val open = listOf(bet("a", full, "m1", "o1", 25.0))
        assertTrue(check(full, open, "m2", "o3", 1.0).blocked)
        val c = check(other, open, "m9", "o9", 25.0)
        assertEquals(0.0, c.now, 1e-9)
        assertFalse("same teams, same start, different event ids: two games", c.blocked)
    }

    @Test
    fun `two events are two games whatever their names and times say, and one event is one game whatever they say`() {
        assertFalse(GameExposure.sameGame(game("e1"), game("e2")))
        assertTrue(GameExposure.sameGame(game("e1"), game("e1", name = "x", start = 0L)))
    }

    @Test
    fun `bets saved before the event id was kept fall back to the matchup and its start - football within 12 hours is one game`() {
        val g = game(eventId = "e1")
        val legacy = bet("old", g.copy(eventId = ""), market = "m1", outcome = "o1", stake = 20.0)
        assertTrue(GameExposure.sameGame(g, legacy.let { GameExposure.gameOf(it) }))
        val c = check(g, listOf(legacy), "m2", "o2", 10.0)
        assertEquals(20.0, c.now, 1e-9)
        assertTrue(c.blocked)
    }

    @Test
    fun `a legacy bet on the same teams a day later is the next game, not this one`() {
        val g = game(eventId = "e1")
        val tomorrow = bet("old", g.copy(eventId = "", startsTs = g.startsTs + 24 * hour), stake = 25.0)
        assertFalse(check(g, listOf(tomorrow), "m2", "o2", 10.0).blocked)
    }

    @Test
    fun `a baseball doubleheader is two games - legacy bets 3 hours apart on the same teams do not add up`() {
        val g1 = game(eventId = "g1", name = "Boston Red Sox @ New York Yankees", league = "MLB")
        val g2 = game(eventId = "g2", name = "Boston Red Sox @ New York Yankees", league = "MLB", start = g1.startsTs + 3 * hour)
        val legacyFirst = bet("old", g1.copy(eventId = ""), stake = 25.0)
        val c = check(g2, listOf(legacyFirst), "m1", "o1", 10.0)
        assertEquals(0.0, c.now, 1e-9)
        assertFalse(c.blocked)
        assertTrue("and the same game's own start does match", GameExposure.sameGame(g1, GameExposure.gameOf(legacyFirst)))
    }

    @Test
    fun `a bet whose game can't be read counts for nothing - the guard never blocks on a guess`() {
        val g = game()
        val unreadable = bet("odd", g.copy(eventId = "", eventName = "not a matchup"), stake = 25.0)
        assertFalse(check(g, listOf(unreadable), "m2", "o2", 10.0).blocked)
    }

    // ---- what counts as dollars at risk ----

    @Test
    fun `only open bets count - a settled bet is no exposure`() {
        val g = game()
        val done = listOf(bet("a", g, "m1", "o1", 25.0, status = BetStatus.WON), bet("b", g, "m2", "o2", 25.0, status = BetStatus.LOST))
        val c = check(g, done, "m3", "o3", 10.0)
        assertEquals(0.0, c.now, 1e-9)
        assertFalse(c.blocked)
    }

    @Test
    fun `both sides of one market count as the larger side - the other side cannot also lose`() {
        val g = game()
        val open = listOf(bet("a", g, "m1", "o1", 10.0), bet("b", g, "m1", "o2", 4.0, lockFor = null))
        assertEquals(10.0, check(g, open, "m5", "o5", 1.0).now, 1e-9)
    }

    @Test
    fun `a market locked in counts for nothing, and a lock bet is never exposure of its own`() {
        val g = game()
        val pick = bet("p", g, "m1", "o1", 10.0, contracts = 2000)
        val lock = bet("l", g, "m1", "o2", 10.0, lockFor = "p", contracts = 2000)
        val partlyPick = bet("q", g, "m2", "o1", 12.0, contracts = 2400)
        val partlyLock = bet("r", g, "m2", "o2", 5.0, lockFor = "q", contracts = 1000)
        val c = check(g, listOf(pick, lock, partlyPick, partlyLock), "m9", "o9", 1.0)
        assertEquals("m1 is locked (0); m2 still rides on the pick's $12, its lock isn't exposure", 12.0, c.now, 1e-9)
    }

    @Test
    fun `a bet that does not raise the game's exposure is never blocked, even over the cap - a hedge or a lock can always go in`() {
        val g = game()
        val open = listOf(bet("a", g, "m1", "o1", 30.0), bet("b", g, "m2", "o3", 8.0))
        // The other side of m1 (smaller than what's held there) adds nothing: exposure stays $38 over a $25 cap.
        val hedge = check(g, open, "m1", "o2", 20.0)
        assertEquals(38.0, hedge.now, 1e-9)
        assertEquals(38.0, hedge.after, 1e-9)
        assertFalse(hedge.blocked)
        assertTrue("a new market on the same game does raise it", check(g, open, "m3", "o5", 1.0).blocked)
    }

    @Test
    fun `no limit when the cap is zero`() {
        val g = game()
        assertFalse(check(g, listOf(bet("a", g, "m1", "o1", 500.0)), "m2", "o2", 500.0, cap = 0.0).blocked)
    }

    @Test
    fun `exactly the cap is allowed, a cent over is not`() {
        val g = game()
        val open = listOf(bet("a", g, "m1", "o1", 15.0))
        assertFalse(check(g, open, "m2", "o2", 10.0).blocked)
        assertTrue(check(g, open, "m2", "o2", 10.01).blocked)
    }

    @Test
    fun `the words name the game, what is at risk and the limit`() {
        val g = game()
        val c = check(g, listOf(bet("a", g, "m1", "o1", 20.0)), "m2", "o2", 10.0)
        val words = c.words()
        assertTrue(words, words.contains("Houston Texans @ Indianapolis Colts"))
        assertTrue(words, words.contains("$20.00"))
        assertTrue(words, words.contains("$30.00"))
        assertTrue(words, words.contains("$25.00"))
    }

    // ---- resting bids ----

    private fun bid(id: String, market: String, outcome: String, status: MakerStatus, contracts: Long = 1_000, filled: Long = 0, price: Double = 0.5, eventId: String = "e1") = MakerBid(
        clientId = id, orderId = "ord-$id", marketId = market, eventId = eventId, outcomeId = outcome, league = "NFL", eventName = "Houston Texans @ Indianapolis Colts",
        startsTs = now + 5 * hour, marketLabel = "Spread", selection = outcome, price = price, contracts = contracts, fair = 0.55, evAtFair = 0.05, margin = 0.04,
        postedAtMs = now, status = status, filled = filled,
    )

    @Test
    fun `a bid that is not ended counts for what is still resting, and an ended one or a filled part does not`() {
        val g = game()
        val up = bid("a", "m1", "o1", MakerStatus.RESTING)                    // 1,000 × 50¢ × $0.01 = $5.00
        val half = bid("b", "m2", "o1", MakerStatus.RESTING, filled = 400)    // 600 left: $3.00 (the 400 filled are a bet in the Tracker)
        val down = bid("c", "m3", "o1", MakerStatus.CANCELING)                // can still fill until Novig says it's gone: $5.00
        val ended = bid("d", "m4", "o1", MakerStatus.CANCELLED)               // gone
        val items = GameExposure.bidItems(listOf(up, half, down, ended))
        assertEquals(listOf(5.0, 3.0, 5.0), items.map { it.dollars })
        assertEquals(13.0, GameExposure.atRisk(g, items), 1e-9)
        assertTrue("a bid on another event is another game", GameExposure.atRisk(game("e2"), items) == 0.0)
    }
}
