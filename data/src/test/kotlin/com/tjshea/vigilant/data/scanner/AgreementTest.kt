package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-28: alerts only when "multiple books agree on the price". A Vigilant bet agrees when
 * 3+ books price both sides and 3+ of them, each devigged worst case on its own, make Novig's price +EV.
 */
class AgreementTest {

    private val now = Fixtures.START_MS - 86_400_000L
    private val event = NovigEvent("e1", "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
    private val market = NovigMarket("m1", "e1", "MONEY", "OPEN", "ML", Fixtures.START_MS, MarketFee.GAME, listOf(NovigOutcome("bal", "BAL", "TBD"), NovigOutcome("dal", "DAL", "TBD")))

    /** Each book's (Baltimore, Dallas) decimal odds. */
    private fun priced(vararg books: Pair<Double, Double>, dalBid: Int = 550, source: FairSource = FairSource.MARKET_AVERAGE): Opportunity {
        val ref = RefEvent(
            "r1", "americanfootball_nfl", Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens",
            markets = books.mapIndexed { i, (bal, dal) -> RefBookMarket("b$i", "Book $i", LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, bal, null), RefQuote(Side.HOME, dal, null)), now) },
        )
        val settings = ScanSettings(fairSource = source, sharpBooks = setOf("b0"), minBooks = 1, minEvPercent = 0.0)
        val plan = Planner.plan(listOf(event), listOf(market), listOf(RefSnapshot("americanfootball_nfl", listOf(ref), now)), settings, now)
        // Baltimore takes at 1 − Dallas's best bid.
        val book = NovigBook("m1", 1, mapOf("dal" to listOf(BidLevel(dalBid, 1_000)), "bal" to listOf(BidLevel(300, 1_000))), now)
        return Pricing.price(plan, mapOf("m1" to book), settings, now).opportunities.first { it.outcome.outcomeId == "bal" }
    }

    @Test
    fun `four books that each make Baltimore +EV agree`() {
        // Baltimore at 0.45 (+122); every book has it near 50/50.
        val o = priced(1.95 to 1.95, 1.93 to 1.97, 1.97 to 1.93, 1.95 to 1.95)
        assertTrue(o.evPercent!! > 0.03)
        assertEquals(Agreement.Count(4, 4), Agreement.of(o))
        assertTrue(Agreement.of(o).agrees)
    }

    @Test
    fun `two books aren't several, and a split doesn't agree`() {
        assertFalse(Agreement.of(priced(1.95 to 1.95, 1.93 to 1.97)).agrees)
        // Two of four books have Baltimore well under 45%: only two agree.
        val split = priced(1.95 to 1.95, 1.93 to 1.97, 2.60 to 1.55, 2.70 to 1.50)
        assertEquals(4, Agreement.of(split).twoSided)
        assertEquals(2, Agreement.of(split).agreeing)
        assertFalse(Agreement.of(split).agrees)
    }

    @Test
    fun `a single sharp book pricing the fair line doesn't make agreement on its own`() {
        // SHARP: only b0 prices it. The other books still count for the agreement check, and here they don't agree.
        val o = priced(1.95 to 1.95, 2.60 to 1.55, 2.70 to 1.50, 2.80 to 1.45, source = FairSource.SHARP)
        assertTrue(o.evPercent!! > 0.0)
        assertFalse(Agreement.of(o).agrees)
        // No fair line or no price: nothing agrees.
        assertEquals(Agreement.Count(0, 0), Agreement.of(o.copy(fair = null)))
        assertEquals(Agreement.Count(0, 0), Agreement.of(o.copy(quote = null)))
    }
}
