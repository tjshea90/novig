package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What RESEARCH.md §16 (OddsAssist Pro, CrazyNinjaOdds) added to the app. */
class ResearchFeaturesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val now = Fixtures.START_MS - 2 * 86_400_000L
    private val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
    private val ml = NovigMarket(
        Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "", Fixtures.START_MS, MarketFee.GAME,
        listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD")),
    )
    private val refs = listOf(RefSnapshot("americanfootball_nfl", TheOddsApiClient.parseEvents(Fixtures.oddsApi, json), now))
    private val settings = ScanSettings(fairSource = FairSource.SHARP, devigMethod = DevigMethod.MULTIPLICATIVE, minEvPercent = 0.0)

    /** DAL costs 1 − BAL's best bid; DAL's own best bid is [dalBid]. */
    private fun price(balBid: Int, dalBid: Int = 300, s: ScanSettings = settings, fetchedAt: Long = now): ScanResult {
        val book = NovigBook(
            Fixtures.ML_MARKET, 1,
            mapOf(Fixtures.ML_BAL to listOf(BidLevel(balBid, 10_000)), Fixtures.ML_DAL to listOf(BidLevel(dalBid, 5_000))),
            fetchedAt,
        )
        val plan = Planner.plan(listOf(event), listOf(ml), refs, s, now)
        return Pricing.price(plan, mapOf(Fixtures.ML_MARKET to book), s, now)
    }

    private fun ScanResult.dal() = opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }

    @Test
    fun `the feed hides longshots past the max odds, and 0 means no limit`() {
        // DAL at 1 − 0.915 = 0.085 costs about +1076: past +1000, inside +2000.
        val r = price(balBid = 915)
        val dal = r.dal()
        assertTrue(dal.quote!!.cost < 100.0 / 1100)
        // Force it +EV so only the odds cap decides.
        val loose = settings.copy(minEvPercent = -1.0, maxEvPercent = 100.0)
        assertTrue(dal !in r.feed(loose.copy(maxOdds = 1000)))
        assertTrue(dal in r.feed(loose.copy(maxOdds = 2000)))
        assertTrue(dal in r.feed(loose.copy(maxOdds = 0)))
        assertEquals(1000, ScanSettings().maxOdds)
    }

    @Test
    fun `settings carry the outlier guard into the fair math, on by default`() {
        assertTrue(ScanSettings().fairSettings().outlierGuard)
        assertFalse(ScanSettings(outlierGuard = false).fairSettings().outlierGuard)
        // A settings file saved before the field existed reads it as on.
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(ScanSettings.serializer(), """{"leagues":["NFL"]}""")
        assertTrue(old.outlierGuard)
        assertEquals(1000, old.maxOdds)
    }

    @Test
    fun `each priced outcome knows its own best bid and where to post a maker order`() {
        val dal = price(balBid = 615, dalBid = 355).dal()
        assertEquals(0.355, dal.bestBid!!, 1e-12)
        val bid = dal.makerBid(0.02)!!
        val fair = dal.fairProbability!!
        assertTrue(bid.price <= fair / 1.02 + 1e-12)
        assertTrue(bid.evPercent >= 0.02)
        // One grid step higher would miss the 2% target.
        assertTrue(fair / (bid.price + 0.005) - 1 < 0.02)
        // Taking at 0.385 is already at least as cheap as any 2% bid: no maker suggestion.
        val cheap = price(balBid = 615 + 200).dal()
        assertTrue(cheap.quote!!.cost <= cheap.fairProbability!! / 1.02)
        assertNull(cheap.makerBid(0.02))
    }

    @Test
    fun `a Novig price older than ten minutes is flagged, a fresh one isn't`() {
        val dal = price(balBid = 615).dal()
        assertFalse(dal.priceIsOld(now + 60_000))
        assertTrue(dal.priceIsOld(now + Pricing.OLD_PRICE_MS + 1))
    }

    @Test
    fun `the CrazyNinjaOdds link carries the sharp book's two sides, this side first, and Novig's price`() {
        val dal = price(balBid = 615).dal()
        val link = CrossCheck.devigger(dal)!!
        assertEquals("Pinnacle", link.bookTitle)
        // Pinnacle in the fixture: DAL 2.45 (+145) / BAL 1.62 (-161). Novig: 1 − 0.615 = 0.385 (+160).
        assertEquals(
            "https://crazyninjaodds.com/Public/sportsbooks/sportsbook_devigger.aspx?autofill=1&LegOdds=%2B145%2F-161&FinalOdds=%2B160",
            link.url,
        )
        val bal = price(balBid = 615).opportunities.first { it.outcome.outcomeId == Fixtures.ML_BAL }
        assertTrue(CrossCheck.devigger(bal)!!.url.contains("LegOdds=-161%2F%2B145"))
    }

    @Test
    fun `no fair line or no offer means no cross-check link`() {
        val plan = Planner.plan(listOf(event), listOf(ml), emptyList<RefSnapshot>(), settings, now)
        val r = Pricing.price(plan, emptyMap(), settings, now)
        assertNull(CrossCheck.devigger(r.dal()))
    }
}
