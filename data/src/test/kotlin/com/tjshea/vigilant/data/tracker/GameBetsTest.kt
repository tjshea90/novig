package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The button on every listed bet (Tj, 2026-10-04: "if I bet 6 player props and a total in the la rams game, make a quick button next to each bet in the
 * scanners that involve the la rams game (and the team they are playing) which pulls up which bets I already placed involving that game, money per bet,
 * and total money across all bets for that game"): what he has on one game, found from either scanner's own wording of it, and never from another game.
 */
class GameBetsTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val rams = "Los Angeles Rams @ Philadelphia Eagles"

    private fun bet(
        id: String, event: String = rams, eventId: String = "e1", market: String = "m1", outcome: String = "o1", stake: Double = 2.0, status: BetStatus = BetStatus.PENDING,
        start: Long = now + 5 * hour, league: String = "NFL", selection: String = "Matthew Stafford Over 250.5", lockFor: String? = null, placedAt: Long = now - hour,
        american: Int? = 105, contracts: Long? = null,
    ) = TrackedBet(
        id = id, createdAtMs = placedAt, league = league, eventName = event, startsTs = start, marketLabel = "Passing Yards", selection = selection,
        marketId = market, outcomeId = outcome, price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 0.04, stake = stake, status = status,
        american = american, orderId = "ord-$id", contracts = contracts ?: (stake / 0.5 * 100).toLong(), paid = stake, fee = 0.0, lockFor = lockFor, eventId = eventId,
    )

    private fun bid(id: String, market: String = "mb", outcome: String = "ob", status: MakerStatus = MakerStatus.RESTING, contracts: Long = 1_000, filled: Long = 0, eventId: String = "e1", event: String = rams) = MakerBid(
        clientId = id, orderId = "ord-$id", marketId = market, eventId = eventId, outcomeId = outcome, league = "NFL", eventName = event, startsTs = now + 5 * hour,
        marketLabel = "Receiving Yards", selection = "Puka Nacua Over 70.5", price = 0.45, contracts = contracts, fair = 0.5, evAtFair = 0.05, margin = 0.04,
        postedAtMs = now, status = status, filled = filled,
    )

    private fun ref(event: String = rams, start: Long = now + 5 * hour, id: String = "", league: String = "NFL") = GameRef(id, event, start, league)

    @Test
    fun `six props and a total in one game are one list, with each bet's money and the total`() {
        val six = (1..6).map { bet("p$it", market = "prop$it", outcome = "o$it", stake = 1.5, placedAt = now - it * 60_000L) }
        val total = bet("t", market = "total", outcome = "over", stake = 3.0, selection = "Over 44.5", placedAt = now)
        val s = GameBets.of(six + total, emptyList()).of(ref(id = "e1"))!!
        assertEquals(7, s.bets.size)
        assertEquals(12.0, s.placed, 1e-9)
        assertEquals(12.0, s.atRisk, 1e-9)
        assertEquals("newest first", "Over 44.5", s.bets.first().selection)
        assertEquals(listOf(3.0, 1.5, 1.5, 1.5, 1.5, 1.5, 1.5), s.bets.map { it.dollars })
        assertTrue(s.bids.isEmpty())
    }

    @Test
    fun `a CNO row with only the matchup finds the game by its teams and start, from either team's side of the wording`() {
        val idx = GameBets.of(listOf(bet("a", eventId = "e1"), bet("b", eventId = "", market = "m2", outcome = "o2")), emptyList())
        // CrazyNinjaOdds lists no Novig event id: its wording and start time find the game.
        assertEquals(2, idx.of(rams, now + 5 * hour, "NFL")!!.bets.size)
        // A kickoff the feeds disagree on by an hour is still that game.
        assertEquals(2, idx.of(rams, now + 6 * hour, "NFL")!!.bets.size)
    }

    @Test
    fun `another game is never mixed in - a different event, the same teams a week later, a doubleheader's other game`() {
        val idx = GameBets.of(listOf(bet("a", eventId = "e1")), emptyList())
        assertNull("another Novig event", idx.of(ref(id = "e2")))
        assertNull("same teams a week later", idx.of(rams, now + 5 * hour + 7 * 24 * hour, "NFL"))
        assertNull("another matchup", idx.of("Dallas Cowboys @ New York Giants", now + 5 * hour, "NFL"))
        val mlb = "Boston Red Sox @ New York Yankees"
        val dh = GameBets.of(listOf(bet("g1", event = mlb, eventId = "", start = now + 5 * hour, league = "MLB")), emptyList())
        assertEquals(1, dh.of(mlb, now + 5 * hour, "MLB")!!.bets.size)
        assertNull("a doubleheader's second game 3 hours later", dh.of(mlb, now + 8 * hour, "MLB"))
    }

    @Test
    fun `Novig's event id finds the game whatever its name says, the way a Vigilant bet is found`() {
        val idx = GameBets.of(listOf(bet("a", eventId = "e1", event = "LA Rams at Philly")), emptyList())
        assertEquals(1, idx.of(ref(event = "Los Angeles Rams @ Philadelphia Eagles", id = "e1"))!!.bets.size)
        assertNull("the same names without the id match nothing: the old name can't be read as a matchup", idx.of(ref(event = "Los Angeles Rams @ Philadelphia Eagles")))
    }

    @Test
    fun `two events with different ids are two games even with the same teams and start`() {
        val idx = GameBets.of(listOf(bet("a", eventId = "e1")), emptyList())
        assertNull(idx.of(ref(id = "e2")))
        assertEquals(1, idx.of(ref(id = "e1"))!!.bets.size)
    }

    @Test
    fun `only open bets count - a graded bet is not on the game any more`() {
        val idx = GameBets.of(
            listOf(bet("open"), bet("won", status = BetStatus.WON, market = "m2"), bet("lost", status = BetStatus.LOST, market = "m3"), bet("push", status = BetStatus.PUSH, market = "m4")),
            emptyList(),
        )
        assertEquals(listOf("open"), idx.of(ref(id = "e1"))!!.bets.map { it.id })
        assertNull("a game with only graded bets is not on the list", GameBets.of(listOf(bet("won", status = BetStatus.WON)), emptyList()).of(ref(id = "e1")))
    }

    @Test
    fun `resting bids are listed apart from bets and add to the game's total but never to the money placed`() {
        val s = GameBets.of(listOf(bet("a", stake = 4.0)), listOf(bid("b1"))).of(ref(id = "e1"))!!
        assertEquals(4.0, s.placed, 1e-9)
        assertEquals("1,000 contracts at 45 cents, \$0.01 each", 4.5, s.resting, 1e-9)
        assertEquals(8.5, s.total, 1e-9)
        assertEquals(1, s.bids.size)
        assertEquals(GameBets.Kind.BID, s.bids.single().kind)
        assertEquals("the limit counts bids beside bets", 8.5, s.atRisk, 1e-9)
    }

    @Test
    fun `an ended bid and the filled part of a bid count nothing`() {
        val idx = GameBets.of(emptyList(), listOf(bid("gone", status = MakerStatus.CANCELED), bid("part", market = "m2", filled = 400)))
        val s = idx.of(ref(id = "e1"))!!
        assertEquals(1, s.bids.size)
        assertEquals("only the 600 contracts still resting", 2.7, s.resting, 1e-9)
        assertNull(GameBets.of(emptyList(), listOf(bid("gone", status = MakerStatus.CANCELED))).of(ref(id = "e1")))
    }

    @Test
    fun `a lock is shown with its bet but the risk counts the market once`() {
        val pick = bet("pick", market = "m1", outcome = "o1", stake = 5.0)
        val lock = bet("lock", market = "m1", outcome = "o2", stake = 4.0, lockFor = "pick", contracts = 800, selection = "Under 44.5")
        val s = GameBets.of(listOf(pick, lock), emptyList()).of(ref(id = "e1"))!!
        assertEquals(2, s.bets.size)
        assertEquals("both are money in", 9.0, s.placed, 1e-9)
        assertTrue(s.bets.any { it.kind == GameBets.Kind.LOCK })
        // 1,000 contracts on one side and 800 on the other is not a fully locked market: it counts the larger side only.
        assertEquals(5.0, s.atRisk, 1e-9)
        assertTrue(s.hedged)
    }

    @Test
    fun `a bet with no way to read its game matches nothing`() {
        val idx = GameBets.of(listOf(bet("odd", event = "Futures: Super Bowl winner", eventId = "")), emptyList())
        assertNull(idx.of("Futures: Super Bowl winner", now + 5 * hour, "NFL"))
        assertNull(GameBets.EMPTY.of(rams, now, "NFL"))
        assertNull(idx.of("", null, ""))
    }

    @Test
    fun `equal when the lines are equal, so a flow of it only wakes the screen for a real change`() {
        val a = GameBets.of(listOf(bet("a")), emptyList())
        assertEquals(a, GameBets.of(listOf(bet("a")), emptyList()))
        assertNotEquals(a, GameBets.of(listOf(bet("a", stake = 3.0)), emptyList()))
        assertEquals(GameBets.EMPTY, GameBets.of(emptyList(), emptyList()))
    }
}
