package com.tjshea.vigilant.data.ncaab

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.engine.FeeCharge
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NT2 (Tj 2026-11-20): "remove and no longer show the very small college games on the schedule that have no betting odds and usually aren't bet on because the school is so small". */
class NcaabNoOddsTest {
    private val fee = MarketFee(0.0, 0.0, FeeCharge.WHEN_LIVE)
    private val start = 1_800_000_000_000L
    private val now = start - 3 * 3_600_000L
    private val event = NovigEvent("ev1", "BASKETBALL", "NCAAB", "OPEN_PREGAME", "Rider @ Davidson", start)

    private fun spread(strike: Double, id: String) = NovigMarket(
        "m-$id", "ev1", "SPREAD", "OPEN", "x", start, fee,
        listOf(NovigOutcome("$id-h", "DAV ${if (strike >= 0) "+" else ""}$strike", "TBD"), NovigOutcome("$id-a", "RID ${if (-strike >= 0) "+" else ""}${-strike}", "TBD")), strike,
    )

    private fun book(m: NovigMarket) = NovigBook(m.marketId, 1, mapOf(m.outcomes[0].outcomeId to listOf(BidLevel(490, 500)), m.outcomes[1].outcomeId to listOf(BidLevel(490, 500))), now, partial = true)

    private val priced = run {
        val m = spread(-4.5, "s1")
        NcaabBoard.build(listOf(event), listOf(m), mapOf(m.marketId to book(m)), emptyMap(), now)
    }

    private fun game(id: String, home: String, away: String, sp: Double? = null, tt: Double? = null, t: Long = start) =
        SlateGame(id, t, h = SlateTeam("h$id", home, home), a = SlateTeam("a$id", away, away), sp = sp, tt = tt)

    // Davidson (Novig lists it), Kansas (ESPN's line only), Paine (a small school: no line anywhere)
    private val slate = NcaabSlate(1, listOf(game("g1", "Davidson", "Rider"), game("g2", "Kansas", "Houston", sp = -3.5, tt = 140.5), game("g3", "Paine", "West Georgia")))

    @Test fun aGameWithNeitherANovigMarketNorESPNsLineIsLeftOutAndCounted() {
        val s = NcaabView.split(slate, null, NcaabBoardFeed(now, priced, 1), now)
        assertEquals(listOf("g1", "g2"), s.games.map { it.game.id })
        assertEquals(1, s.hiddenNoOdds)
        assertTrue(s.games.all { it.hasOdds })
    }

    @Test fun aGameWithOnlyESPNsLineStaysAndOneWithNovigsLinesStays() {
        val v = NcaabView.build(slate, null, NcaabBoardFeed(now, priced, 1), now).associateBy { it.game.id }
        assertTrue(v.getValue("g1").hasOdds); assertEquals("Novig", v.getValue("g1").lineSource)
        assertTrue("ESPN's spread and total are odds", v.getValue("g2").hasOdds); assertEquals("ESPN", v.getValue("g2").lineSource)
    }

    @Test fun beforeNovigsBoardIsReadNoGameIsHiddenForHavingNoOdds() {
        val s = NcaabView.split(slate, null, null, now)
        assertEquals(listOf("g1", "g2", "g3"), s.games.map { it.game.id })
        assertEquals(0, s.hiddenNoOdds)
        assertFalse(s.games.first { it.game.id == "g3" }.hasOdds)
    }

    @Test fun anEmptyBoardStillHidesOnlyTheGamesWithNoLineAnywhere() {
        val s = NcaabView.split(slate, null, NcaabBoardFeed(now, emptyList(), 0), now)
        assertEquals("only Kansas has ESPN's line", listOf("g2"), s.games.map { it.game.id })
        assertEquals(2, s.hiddenNoOdds)
    }

    @Test fun aGameNovigListsThatTheScheduleLacksIsShownWithItsOdds() {
        val s = NcaabView.split(NcaabSlate(1, emptyList()), null, NcaabBoardFeed(now, priced, 1), now)
        assertEquals(1, s.games.size); assertTrue(s.games.single().hasOdds); assertEquals(0, s.hiddenNoOdds)
    }
}
