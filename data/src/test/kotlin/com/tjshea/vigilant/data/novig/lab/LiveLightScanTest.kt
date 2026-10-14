package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.burst.Ladders
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live light's score-and-clock model (Tj, 2026-10-14): a decided game offered at a price that ignores it is found; a live game is never judged on a guess. */
class LiveLightScanTest {
    private fun ml(id: String = "m1") = NovigMarket(
        id, "ev", "MONEY", "OPEN", "d", 0L, MarketFee.GAME,
        listOf(NovigOutcome("$id-h", "Chicago Bulls", "TBD"), NovigOutcome("$id-a", "Denver Nuggets", "TBD")), null,
    )

    private fun total(id: String, strike: Double) = NovigMarket(
        id, "ev", "TOTAL", "OPEN", "d", 0L, MarketFee.GAME,
        listOf(NovigOutcome("$id-u", "Under $strike", "TBD"), NovigOutcome("$id-o", "Over $strike", "TBD")), strike,
    )

    private fun book(id: String, bids: Map<String, Int>) = NovigBook(id, 1L, bids.mapValues { (_, p) -> listOf(BidLevel(p, 5_000L)) }, 0L)

    /** Away Team is the line's YES (names sort), so its ask is 1 - the bid on Home. */
    private fun moneyline(homeBid: Int, awayBid: Int) = LadderPoint(Ladders.line(ml())!!, book("m1", mapOf("m1-h" to homeBid, "m1-a" to awayBid)))

    private val away: (String) -> Int? = { ref -> if (ref == "Denver Nuggets") 25 else null }
    private val nba = { frac: Double, home: Int, awayScore: Int -> GameState(TailSport.BASKETBALL, home, awayScore, frac) }

    @Test
    fun `a blowout offered at fifty-fifty is found on the team that is winning, and only on it`() {
        // Away leads by 25 with a tenth of the game left; Novig still shows 51/49 (each side's best bid 0.49).
        val found = LiveLightScan.scan(listOf(moneyline(490, 490)), "NBA", nba(0.10, 80, 105), away)
        val c = found.single()
        assertEquals("YES", c.side)
        assertEquals("m1-a", c.outcomeId)
        assertTrue("fair ${c.fair}", c.fair > 0.99)
        assertEquals(0.51, c.ask, 1e-9)
        assertTrue(c.edge > 0.5)
        assertEquals(LiveLightScan.RULE, c.rule)
    }

    @Test
    fun `a close game or a game with a lot left finds nothing, and neither does a price that already knows`() {
        assertTrue(LiveLightScan.scan(listOf(moneyline(490, 490)), "NBA", nba(0.50, 50, 53), { 3 }).isEmpty())
        assertTrue(LiveLightScan.scan(listOf(moneyline(490, 490)), "NBA", nba(0.60, 50, 70), { 20 }).isEmpty())
        // Novig already prices the leader at 0.97: no edge after the fee.
        assertTrue(LiveLightScan.scan(listOf(moneyline(20, 960)), "NBA", nba(0.10, 80, 105), away).isEmpty())
    }

    @Test
    fun `a team that cannot be matched to the score is never judged, and the last seconds and the first minutes are left alone`() {
        assertTrue(LiveLightScan.scan(listOf(moneyline(490, 490)), "NBA", nba(0.10, 80, 105), { _ -> null }).isEmpty())
        assertTrue(LiveLightScan.scan(listOf(moneyline(490, 490)), "NBA", nba(0.01, 80, 105), away).isEmpty())
        assertTrue(LiveLightScan.scan(listOf(moneyline(490, 490)), "NBA", nba(0.90, 80, 105), away).isEmpty())
        assertNull(LiveLightScan.priorTotal("CRICKET"))
        assertTrue(LiveLightScan.scan(listOf(moneyline(490, 490)), "CRICKET", nba(0.10, 80, 105), away).isEmpty())
    }

    @Test
    fun `a low over in a high scoring game, or a high under in a low scoring one, is found and the other side of it is not`() {
        val low = LadderPoint(Ladders.line(total("t1", 170.5))!!, book("t1", mapOf("t1-u" to 100, "t1-o" to 100)))   // both sides offered at 0.90
        val found = LiveLightScan.scan(listOf(low), "NBA", nba(0.15, 100, 90), { null })
        assertEquals(listOf("OVER"), found.map { it.side })
        assertEquals("t1-o", found.single().outcomeId)
        // 40 points through 60% of an NBA game: a total of 215.5 is nowhere near, the Under is decided.
        val high = LadderPoint(Ladders.line(total("t2", 215.5))!!, book("t2", mapOf("t2-u" to 100, "t2-o" to 100)))
        val under = LiveLightScan.scan(listOf(high), "NBA", nba(0.15, 70, 60), { null })
        assertEquals(listOf("UNDER"), under.map { it.side })
    }

    @Test
    fun `a whole-number total or spread is left alone (a push), and a thin offer is not taken`() {
        val whole = LadderPoint(Ladders.line(total("t3", 170.0))!!, book("t3", mapOf("t3-u" to 100, "t3-o" to 100)))
        assertTrue(LiveLightScan.scan(listOf(whole), "NBA", nba(0.15, 100, 90), { null }).isEmpty())
        val thin = LadderPoint(Ladders.line(ml())!!, NovigBook("m1", 1L, mapOf("m1-h" to listOf(BidLevel(490, 5L)), "m1-a" to listOf(BidLevel(490, 5L))), 0L))
        assertTrue(LiveLightScan.scan(listOf(thin), "NBA", nba(0.10, 80, 105), away).isEmpty())
    }

    @Test
    fun `hockey and baseball use the goals and runs still to come, not a points spread`() {
        // NHL: up 4 goals with five minutes left at fifty-fifty is found; up one goal is not.
        val nhl = { a: Int, h: Int -> GameState(TailSport.HOCKEY, h, a, 0.08) }
        val lead = LiveLightScan.scan(listOf(moneyline(490, 490)), "NHL", nhl(5, 1), { 4 })
        assertEquals("m1-a", lead.single().outcomeId)
        assertTrue(LiveLightScan.scan(listOf(moneyline(490, 490)), "NHL", nhl(2, 1), { 1 }).isEmpty())
    }
}
