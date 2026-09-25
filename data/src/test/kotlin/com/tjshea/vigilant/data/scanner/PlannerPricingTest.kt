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
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerPricingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val now = Fixtures.START_MS - 2 * 86_400_000L

    private val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)

    private fun market(id: String, type: String, vararg outcomes: Pair<String, String>) =
        NovigMarket(id, Fixtures.EVENT_ID, type, "OPEN", "", Fixtures.START_MS, MarketFee.GAME, outcomes.map { NovigOutcome(it.first, it.second, "TBD") })

    private val markets = listOf(
        market(Fixtures.ML_MARKET, "MONEY", Fixtures.ML_DAL to "DAL", Fixtures.ML_BAL to "BAL"),
        market(Fixtures.SPREAD_MARKET, "SPREAD", Fixtures.SPREAD_DAL to "DAL +3.5", Fixtures.SPREAD_BAL to "BAL -3.5"),
        market(Fixtures.ALT_SPREAD_MARKET, "SPREAD", "alt-dal" to "DAL +20.5", "alt-bal" to "BAL -20.5"),
        market(Fixtures.TOTAL_MARKET, "TOTAL", Fixtures.TOTAL_OVER to "Over 47.5", Fixtures.TOTAL_UNDER to "Under 47.5"),
    )

    private val refs = mapOf(
        "americanfootball_nfl" to RefSnapshot("americanfootball_nfl", TheOddsApiClient.parseEvents(Fixtures.oddsApi, json), now),
    )

    private fun book(id: String, vararg bids: Pair<String, Pair<Int, Long>>) =
        NovigBook(id, 1, bids.groupBy({ it.first }, { BidLevel(it.second.first, it.second.second) }), now)

    private val books = mapOf(
        Fixtures.ML_MARKET to book(Fixtures.ML_MARKET, Fixtures.ML_DAL to (380 to 516_409L), Fixtures.ML_BAL to (615 to 248_541L), Fixtures.ML_BAL to (610 to 4_098L)),
        Fixtures.SPREAD_MARKET to book(Fixtures.SPREAD_MARKET, Fixtures.SPREAD_DAL to (470 to 10_000L), Fixtures.SPREAD_BAL to (500 to 20_000L)),
        Fixtures.TOTAL_MARKET to book(Fixtures.TOTAL_MARKET, Fixtures.TOTAL_OVER to (490 to 5_000L), Fixtures.TOTAL_UNDER to (490 to 5_000L)),
    )

    private val sharpOnly = ScanSettings(fairSource = FairSource.SHARP, devigMethod = DevigMethod.MULTIPLICATIVE)

    @Test
    fun `only lines a reference book quotes get planned - the alternate spread costs no book request`() {
        val plan = Planner.plan(listOf(event), markets, refs, sharpOnly, now)
        assertEquals(1, plan.matchedEvents)
        assertEquals(setOf(Fixtures.ML_MARKET, Fixtures.SPREAD_MARKET, Fixtures.TOTAL_MARKET), plan.marketIds.toSet())
    }

    @Test
    fun `moneyline sides and fair price come out right against Pinnacle`() {
        val plan = Planner.plan(listOf(event), markets, refs, sharpOnly, now)
        val r = Pricing.price(plan, books, sharpOnly, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertEquals("Dallas Cowboys", dal.selection)

        // Pinnacle: BAL 1.55 / DAL 2.55, multiplicative devig.
        val raw = listOf(1 / 2.55, 1 / 1.55) // [HOME=DAL, AWAY=BAL]
        val fairDal = raw[0] / raw.sum()
        assertEquals(fairDal, dal.fairProbability!!, 1e-12)
        assertEquals(0.385, dal.quote!!.price, 1e-12)
        assertEquals(fairDal / 0.385 - 1, dal.evPercent!!, 1e-12)
        assertEquals(listOf("Pinnacle"), dal.fair!!.sharpBooksUsed)
        // Novig's own width on this market: 0.385 + 0.62 - 1.
        assertEquals(0.005, dal.novigWidth!!, 1e-12)
    }

    @Test
    fun `spread fair price uses only books on the exact same point`() {
        val avg = ScanSettings(fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, devigMethod = DevigMethod.MULTIPLICATIVE)
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, avg, now), books, avg, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.SPREAD_DAL }
        assertEquals("Dallas Cowboys +3.5", dal.selection)
        assertEquals(listOf("Pinnacle"), dal.fair!!.averageBooksUsed) // DraftKings is on 3.0, not 3.5
        val raw = listOf(1 / 1.93, 1 / 1.95)
        assertEquals(raw[0] / raw.sum(), dal.fairProbability!!, 1e-12)
        // Take DAL +3.5 against the BAL bid at 0.50.
        assertEquals(0.50, dal.quote!!.price, 1e-12)
    }

    @Test
    fun `totals map over and under`() {
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, sharpOnly, now), books, sharpOnly, now)
        val over = r.opportunities.first { it.outcome.outcomeId == Fixtures.TOTAL_OVER }
        assertEquals("Over 47.5", over.selection)
        val raw = listOf(1 / 1.91, 1 / 1.97)
        assertEquals(raw[0] / raw.sum(), over.fairProbability!!, 1e-12)
        assertEquals(0.51, over.quote!!.price, 1e-12)
    }

    @Test
    fun `the feed keeps positive EV above the threshold, best first`() {
        val s = sharpOnly.copy(minEvPercent = 0.0)
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, s, now), books, s, now)
        val feed = r.feed(s)
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.all { it.evPercent!! >= 0.0 })
        assertEquals(feed.sortedByDescending { it.evPercent }, feed)
        // DAL at +160 vs a fair ~+155 on Pinnacle is a real (small) edge.
        assertTrue(feed.any { it.outcome.outcomeId == Fixtures.ML_DAL })
    }

    @Test
    fun `a home and away swap between providers still maps the right team`() {
        // Same game, but the reference feed lists Baltimore as home (e.g. a neutral site).
        val swapped = RefEvent("ref-x", "americanfootball_nfl", Fixtures.START_MS, home = "Baltimore Ravens", away = "Dallas Cowboys",
            markets = listOf(RefBookMarket("pinnacle", "Pinnacle", LineKind.MONEYLINE,
                listOf(RefQuote(Side.HOME, 1.55, null), RefQuote(Side.AWAY, 2.55, null)), null)))
        val r = mapOf("americanfootball_nfl" to RefSnapshot("americanfootball_nfl", listOf(swapped), now))
        val plan = Planner.plan(listOf(event), markets, r, sharpOnly, now)
        val res = Pricing.price(plan, books, sharpOnly, now)
        val dal = res.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        val raw = listOf(1 / 1.55, 1 / 2.55) // HOME=BAL, AWAY=DAL in the reference feed
        assertEquals(raw[1] / raw.sum(), dal.fairProbability!!, 1e-12)
    }

    @Test
    fun `an event with no reference match still shows Novig's moneyline, without a fair price`() {
        val plan = Planner.plan(listOf(event), markets, emptyMap(), sharpOnly, now)
        assertEquals(listOf(Fixtures.ML_MARKET), plan.marketIds)
        val r = Pricing.price(plan, books, sharpOnly, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertNull(dal.fairProbability)
        assertNull(dal.quote)
        assertTrue(r.feed(sharpOnly).isEmpty())
    }

    @Test
    fun `games beyond the day window and live games are skipped unless asked for`() {
        val far = event.copy(eventId = "far", startsTs = now + 10 * 86_400_000L)
        val live = event.copy(eventId = "live", status = "OPEN_INGAME")
        assertEquals(listOf(Fixtures.EVENT_ID), Planner.eligibleEvents(listOf(event, far, live), sharpOnly, now).map { it.eventId })
        assertEquals(2, Planner.eligibleEvents(listOf(event, far, live), sharpOnly.copy(includeLive = true), now).size)
    }

    @Test
    fun `three way soccer markets price Yes from the team and No from every other result`() {
        val soccer = NovigEvent("s1", "SOCCER", "EPL", "OPEN_PREGAME", "Newcastle United FC @ Coventry City FC", Fixtures.START_MS)
        val win = NovigMarket("w", "s1", "MONEYLINE_3_WAY_WIN", "OPEN", "Newcastle MONEYLINE_3_WAY_WIN", Fixtures.START_MS, MarketFee.GAME,
            listOf(NovigOutcome("wy", "Yes", "TBD"), NovigOutcome("wn", "No", "TBD")))
        val draw = NovigMarket("d", "s1", "MONEYLINE_3_WAY_DRAW", "OPEN", "Newcastle United FC @ Coventry City FC MONEYLINE_3_WAY_DRAW",
            Fixtures.START_MS, MarketFee.GAME, listOf(NovigOutcome("dy", "Yes", "TBD"), NovigOutcome("dn", "No", "TBD")))
        val ref = RefEvent("r", "soccer_epl", Fixtures.START_MS, home = "Coventry City", away = "Newcastle United",
            markets = listOf(RefBookMarket("pinnacle", "Pinnacle", LineKind.MONEYLINE,
                listOf(RefQuote(Side.HOME, 3.4, null), RefQuote(Side.AWAY, 2.2, null), RefQuote(Side.DRAW, 3.6, null)), null)))
        val snaps = mapOf("soccer_epl" to RefSnapshot("soccer_epl", listOf(ref), now))
        val s = sharpOnly.copy(leagues = setOf("EPL"))
        val plan = Planner.plan(listOf(soccer), listOf(win, draw), snaps, s, now)
        val bk = mapOf(
            "w" to book("w", "wy" to (430 to 100L), "wn" to (540 to 100L)),
            "d" to book("d", "dy" to (250 to 100L), "dn" to (700 to 100L)),
        )
        val r = Pricing.price(plan, bk, s, now)
        val raw = listOf(1 / 3.4, 1 / 2.2, 1 / 3.6)
        val fair = raw.map { it / raw.sum() }
        assertEquals(fair[1], r.opportunities.first { it.outcome.outcomeId == "wy" }.fairProbability!!, 1e-12)
        assertEquals(fair[0] + fair[2], r.opportunities.first { it.outcome.outcomeId == "wn" }.fairProbability!!, 1e-12)
        assertEquals(fair[2], r.opportunities.first { it.outcome.outcomeId == "dy" }.fairProbability!!, 1e-12)
        assertEquals("Newcastle United FC win: Yes", r.opportunities.first { it.outcome.outcomeId == "wy" }.selection)
    }

    @Test
    fun `stake is fractional kelly capped by what the book can fill at a positive edge`() {
        val s = sharpOnly.copy(bankroll = 1_000_000.0, kellyMultiplier = 1.0, minEvPercent = 0.0)
        val r = Pricing.price(Planner.plan(listOf(event), markets, refs, s, now), books, s, now)
        val dal = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertNotNull(dal.depth)
        // A million-dollar Kelly stake can't exceed the +EV liquidity on the book.
        assertEquals(dal.depth!!.dollarCost, dal.suggestedStake!!, 1e-9)
        assertTrue(Odds.probabilityToAmerican(dal.quote!!.price) == 160)
    }
}
