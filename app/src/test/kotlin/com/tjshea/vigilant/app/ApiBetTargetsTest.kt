package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a Bet from a CNO card is made of (Tj, 2026-09-29): its Novig outcome, CNO's fair price and when it was read, the earlier start. */
class ApiBetTargetsTest {

    private val start = 1_800_000_000_000L
    private val market = NovigMarket("mkt", "ev", "PLAYER_RECEPTIONS", "OPEN", "x", start, MarketFee.GAME, listOf(NovigOutcome("o-over", "Over 5.5", "TBD"), NovigOutcome("o-under", "Under 5.5", "TBD")))
    private val found = NovigBetFinder.Found.Bet("o-over", "ev", "mkt", market)

    private fun row(book: String = "Novig", fair: Double? = 0.55, starts: Long? = start) = CnoRow(
        ev = 0.04, startsAtMs = starts, league = "NFL", event = "A @ B", market = "Player Receptions", bet = "Brock Bowers Over 5.5", odds = -110, book = book,
        fairProbability = fair, gameUrl = "https://crazyninjaodds.com/g?side_id=7",
    )

    @Test
    fun `a CNO bet at Novig becomes a target with CNO's fair price and the read time`() {
        val t = ApiBetTargets.of(row(), found, market, fairAsOfMs = start - 60_000)!!
        assertEquals("o-over", t.outcomeId)
        assertEquals(0.55, t.fair, 1e-9)
        assertEquals(start - 60_000, t.fairAsOfMs)
        assertEquals(BetTracker.SOURCE_CNO, t.source)
        assertEquals(MiniWindow.cnoKey(row()), t.placedKey)
        assertEquals("Brock Bowers Over 5.5", t.selection)
    }

    @Test
    fun `the earlier start counts, so a game Novig has started is never bet as pregame`() {
        assertEquals(start - 1_000, ApiBetTargets.of(row(starts = start - 1_000), found, market, 0)!!.startsTs)
        assertEquals(start, ApiBetTargets.of(row(starts = start + 60_000), found, market, 0)!!.startsTs)
        assertEquals(start, ApiBetTargets.of(row(starts = null), found, market, 0)!!.startsTs)
    }

    @Test
    fun `no fair price, no bet; only Novig's own bets can be placed through Novig`() {
        assertNull(ApiBetTargets.of(row(fair = null), found, market, 0))
        assertTrue(ApiBetTargets.atNovig(row("Novig")))
        assertTrue(ApiBetTargets.atNovig(row("")))
        assertFalse(ApiBetTargets.atNovig(row("DraftKings")))
    }
}
