package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-13: "I'm getting flooded with nhl player shots on goal props ... Make sure that they are actually matched correctly to the same odds from different sports books. They may be
 * showing positive EV because they are incorrectly matched to a different type of bet odds." A book's quote joins a Novig prop's fair line only when the stat, the line, the period and the
 * player are all the same ([LineKey.matches]) and the book prices BOTH sides ([Pricing.bookPrices]): these tests pin that for shots on goal, the stats and neighbours it could be mistaken for.
 */
class ShotsOnGoalMatchTest {
    private fun q(over: Double, under: Double?, line: Double) =
        listOf(RefQuote(Side.OVER, over, line)) + listOfNotNull(under?.let { RefQuote(Side.UNDER, it, line) })

    private fun book(key: String, stat: String, player: String, line: Double, over: Double, under: Double? = 1.9, period: Int = 0) =
        RefBookMarket(key, key, LineKind.PLAYER_PROP, q(over, under, line), 1_000L, period = period, subject = player, stat = stat)

    private val key = LineKey("e1", LineKind.PLAYER_PROP, 2.5, 0, "Brady Tkachuk", "SHOTS_ON_GOAL")

    private fun event(vararg m: RefBookMarket) = RefEvent("e1", "icehockey_nhl", 0L, "Ottawa Senators", "Florida Panthers", m.toList())

    @Test fun `only the same stat, line, period and player is the same bet`() {
        val sog = book("dk", "SHOTS_ON_GOAL", "Brady Tkachuk", 2.5, 1.95)
        assertTrue(key.matches(sog))
        // the stats a shots-on-goal line could be mistaken for, at the very same number
        for (stat in listOf("PLAYER_GOALS", "POINTS", "ASSISTS", "SAVES", "POWER_PLAY_POINTS")) {
            assertFalse("$stat must not price shots on goal", key.matches(book("dk", stat, "Brady Tkachuk", 2.5, 1.95)))
        }
        // another line of the same player's shots, and the first period's
        assertFalse(key.matches(book("dk", "SHOTS_ON_GOAL", "Brady Tkachuk", 1.5, 1.35)))
        assertFalse(key.matches(book("dk", "SHOTS_ON_GOAL", "Brady Tkachuk", 3.5, 2.9)))
        assertFalse(key.matches(book("dk", "SHOTS_ON_GOAL", "Brady Tkachuk", 2.5, 1.95, period = 1)))
        // a team's total or a game total at 2.5 is no player's shots
        assertFalse(key.matches(RefBookMarket("dk", "dk", LineKind.TOTAL, q(1.95, 1.9, 2.5), 1_000L)))
        assertFalse(key.matches(RefBookMarket("dk", "dk", LineKind.TEAM_TOTAL, q(1.95, 1.9, 2.5), 1_000L, subject = "HOME")))
    }

    @Test fun `the brothers in one game are two players, and a short first name is the same one`() {
        assertFalse(key.matches(book("dk", "SHOTS_ON_GOAL", "Matthew Tkachuk", 2.5, 1.95)))
        assertTrue(LineKey("e1", LineKind.PLAYER_PROP, 2.5, 0, "Matthew Tkachuk", "SHOTS_ON_GOAL").matches(book("dk", "SHOTS_ON_GOAL", "Matt Tkachuk", 2.5, 1.95)))
        assertFalse(key.matches(book("dk", "SHOTS_ON_GOAL", "Tkachuk", 2.5, 1.95)))
        assertFalse(key.matches(book("dk", "SHOTS_ON_GOAL", "B. Tkachuk", 2.5, 1.95)))
    }

    @Test fun `a book that prices one side only never prices the line, so an alternate ladder cannot make an edge`() {
        val ev = event(
            book("fd", "SHOTS_ON_GOAL", "Brady Tkachuk", 2.5, 2.0, under = 1.85),
            book("dk", "SHOTS_ON_GOAL", "Brady Tkachuk", 2.5, 2.4, under = null), // "to record 3+ shots" alone
        )
        val prices = Pricing.bookPrices(ev, key)
        assertEquals(listOf("fd"), prices.map { it.bookKey })
        assertEquals(listOf(2.0, 1.85), prices.single().odds) // Over first, Under second: never swapped
    }

    @Test fun `books on other stats, lines and players in the same game never reach the line`() {
        val ev = event(
            book("fd", "SHOTS_ON_GOAL", "Brady Tkachuk", 2.5, 2.0, under = 1.85),
            book("dk", "SHOTS_ON_GOAL", "Brady Tkachuk", 3.5, 3.1, under = 1.35),
            book("mgm", "POINTS", "Brady Tkachuk", 2.5, 12.0, under = 1.02),
            book("czr", "SHOTS_ON_GOAL", "Matthew Tkachuk", 2.5, 1.7, under = 2.2),
            book("bol", "SHOTS_ON_GOAL", "Brady Tkachuk", 2.5, 2.05, under = 1.8),
        )
        assertEquals(setOf("fd", "bol"), Pricing.bookPrices(ev, key).map { it.bookKey }.toSet())
    }
}
