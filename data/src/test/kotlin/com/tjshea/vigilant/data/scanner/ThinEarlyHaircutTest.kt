package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-13: "Do option 1": NHL shots-on-goal fair prices built from fewer than 5 books, on a game more than 12 hours away, are cut 2.5 points
 * (research/nhl_sog_matching_2026-10-13.md: the fair ran +3.0 to +3.7 points above Pinnacle's close there, +0.1 inside 2 hours, about 0 with 8+ books).
 */
class ThinEarlyHaircutTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    @Test fun `the rule is NHL shots on goal, under 5 books, over 12 hours, and nothing else`() {
        assertEquals(0.025, ThinEarlyHaircut.points("NHL", "SHOTS_ON_GOAL", 4, 15.0), 0.0)
        assertEquals(0.025, ThinEarlyHaircut.points("NHL", "SHOTS_ON_GOAL", 3, 12.5), 0.0)
        assertEquals("5 books are enough", 0.0, ThinEarlyHaircut.points("NHL", "SHOTS_ON_GOAL", 5, 15.0), 0.0)
        assertEquals("12 hours or less", 0.0, ThinEarlyHaircut.points("NHL", "SHOTS_ON_GOAL", 4, 12.0), 0.0)
        assertEquals("near the start", 0.0, ThinEarlyHaircut.points("NHL", "SHOTS_ON_GOAL", 4, 1.0), 0.0)
        assertEquals("another stat", 0.0, ThinEarlyHaircut.points("NHL", "SAVES", 4, 15.0), 0.0)
        assertEquals("another league", 0.0, ThinEarlyHaircut.points("NFL", "SHOTS_ON_GOAL", 4, 15.0), 0.0)
        assertEquals("not a prop", 0.0, ThinEarlyHaircut.points("NHL", null, 4, 15.0), 0.0)
    }

    private fun scan(books: Int, hoursAway: Double, on: Boolean = true): Double {
        val starts = now + (hoursAway * hour).toLong()
        val league = Leagues.ALL.first { it.novigName == "NHL" }
        val event = NovigEvent("e1", "HOCKEY", "NHL", NovigEvent.STATUS_PREGAME, "Ottawa Senators @ New Jersey Devils", starts)
        val market = NovigMarket(
            "m1", "e1", "SHOTS_ON_GOAL", "OPEN", "Timo Meier", starts, MarketFee.GAME,
            listOf(NovigOutcome("m1-o", "Over 2.5", "TBD"), NovigOutcome("m1-u", "Under 2.5", "TBD")),
        )
        val refMarkets = (1..books).map { i ->
            RefBookMarket(
                "b$i", "Book $i", LineKind.PLAYER_PROP,
                listOf(RefQuote(Side.OVER, 1.91, 2.5), RefQuote(Side.UNDER, 1.91, 2.5)), now - 1_000L, subject = "Timo Meier", stat = "SHOTS_ON_GOAL",
            )
        }
        val ref = RefEvent("r1", "icehockey_nhl", starts, "New Jersey Devils", "Ottawa Senators", refMarkets)
        val key = LineKey("r1", LineKind.PLAYER_PROP, 2.5, 0, "Timo Meier", "SHOTS_ON_GOAL")
        val plan = Plan(
            listOf(
                PlannedMarket(
                    league, event, ref, market, LineKind.PLAYER_PROP, key, "Shots On Goal",
                    listOf(PlannedOutcome(market.outcomes[0], OutcomeTarget.Is(Side.OVER), "Timo Meier Over 2.5"), PlannedOutcome(market.outcomes[1], OutcomeTarget.Is(Side.UNDER), "Timo Meier Under 2.5")),
                ),
            ),
            emptyList(),
        )
        val book = NovigBook("m1", 1, mapOf("m1-o" to listOf(BidLevel(450, 1000)), "m1-u" to listOf(BidLevel(450, 1000))), now)
        val s = ScanSettings(fairSource = FairSource.MARKET_AVERAGE, devigMethod = DevigMethod.MULTIPLICATIVE, minEvPercent = -50.0, maxEvPercent = 50.0)
            .let { it.copy(vigGuards = it.vigGuards.copy(thinEarlyHaircut = on)) }
        val r = Pricing.price(plan, mapOf("m1" to book), s, now)
        return r.opportunities.single { it.outcome.outcomeId == "m1-o" }.fairProbability!!
    }

    @Test fun `four books 15 hours out price 2_5 points lower, and the edge shrinks with it`() {
        val plain = scan(books = 4, hoursAway = 15.0, on = false)
        val cut = scan(books = 4, hoursAway = 15.0)
        assertEquals(0.5, plain, 1e-9)
        assertEquals(plain - 0.025, cut, 1e-9)
        assertTrue(cut < plain)
    }

    @Test fun `five books, or a game inside 12 hours, or the switch off, price as before`() {
        assertEquals(0.5, scan(books = 5, hoursAway = 15.0), 1e-9)
        assertEquals(0.5, scan(books = 4, hoursAway = 6.0), 1e-9)
        assertEquals(0.5, scan(books = 4, hoursAway = 15.0, on = false), 1e-9)
    }
}
