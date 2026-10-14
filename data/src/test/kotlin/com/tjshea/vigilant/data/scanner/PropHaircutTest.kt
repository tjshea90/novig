package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player-prop haircut (Tj, 2026-10-14): the history's prop Overs won 39.7% against a 49.4% fair, so the fair is cut before anything reads it. */
class PropHaircutTest {
    @Test
    fun `a prop over loses seven points and an under three, and nothing else is touched`() {
        assertEquals(0.07, PropHaircut.points(BetKind.PROP, "Bo Nix Over 12.5"), 1e-12)
        assertEquals(0.03, PropHaircut.points(BetKind.PROP, "Bo Nix Under 12.5"), 1e-12)
        assertEquals(0.07, PropHaircut.points(BetKind.PROP, "Over 72.5"), 1e-12)
        assertEquals(0.0, PropHaircut.points(BetKind.SPREAD, "Over 72.5"), 0.0)
        assertEquals(0.0, PropHaircut.points(BetKind.MONEYLINE, "Boston Celtics"), 0.0)
        assertEquals(0.0, PropHaircut.points(BetKind.PROP, "Player Anytime"), 0.0)
        assertEquals(0.0, PropHaircut.points(null, "Over 1.5"), 0.0)
    }

    private fun row(market: String, bet: String, fairProbability: Double = 0.50, odds: Int = 110, ev: Double = 0.05) = CnoRow(
        ev = ev, startsAtMs = null, sport = "football", league = "NFL", event = "A @ B", market = market, bet = bet, odds = odds, book = "Novig", fairProbability = fairProbability, books = 6, gameUrl = "https://crazyninjaodds.com/g?side_id=1",
    )

    @Test
    fun `a CNO prop over has its fair cut and its EV worked out again at the same price, other rows are returned unchanged`() {
        val over = CnoChecks.haircut(row("Player Receiving Yards", "Brock Bowers Over 4.5"))
        assertEquals(0.43, over.fairProbability!!, 1e-9)
        assertEquals(0.43 * 2.10 - 1.0, over.ev, 1e-9)   // +110 pays 2.10: a +5% edge becomes -9.7%
        assertTrue(over.ev < 0.0)
        val under = CnoChecks.haircut(row("Player Receiving Yards", "Brock Bowers Under 4.5"))
        assertEquals(0.47, under.fairProbability!!, 1e-9)
        val total = row("Total Points", "Over 72.5")
        assertEquals(total, CnoChecks.haircut(total))
    }

    @Test
    fun `the schema migration turns Profit-proven only and the prop haircut on once, and a later choice stays`() {
        val migrated = ScanSettings(schema = 17).migrate()
        assertTrue(migrated.gates.profitProven)
        val chosen = ScanSettings(schema = 18, gates = ScanSettings().gates.copy(profitProven = false)).migrate()
        assertTrue(!chosen.gates.profitProven)
        assertTrue(migrated.propHaircut)
        assertTrue(!ScanSettings(schema = 18).migrate().propHaircut)   // a choice made after the migration stays
    }
}
