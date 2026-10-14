package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A listed bet is priced again from Novig's newer book with the fair it carries (Tj, 2026-10-14: Oregon State -19.5 was listed at +125 and +1.68% while Novig's price was +113 and -3.7% EV,
 * because a long scan held the list and nothing re-priced it). [Pricing.requote] is the pure step; the list drops a row whose quote is no longer +EV.
 */
class RequoteTest {
    private val now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0, ignoreWideQuotes = false)

    private fun fresh(json: String) = json.replace(Regex("\"last_update\":\"[^\"]+\""), "\"last_update\":\"${java.time.Instant.ofEpochMilli(now)}\"")

    private fun listed(): Opportunity {
        val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
        val market = NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", Fixtures.START_MS, MarketFee.GAME,
            listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD")))
        val refs = mapOf("americanfootball_nfl" to RefSnapshot("americanfootball_nfl", TheOddsApiClient.parseEvents(fresh(Fixtures.oddsApi), Json { ignoreUnknownKeys = true }), now))
        val plan = Planner.plan(listOf(event), listOf(market), refs, settings, now)
        val book = NovigBook(Fixtures.ML_MARKET, 1, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to listOf(BidLevel(615, 1000))), now - 120_000L)
        return Pricing.price(plan, mapOf(Fixtures.ML_MARKET to book), settings, now).opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
    }

    @Test
    fun `a worse Novig price takes the edge away and a better one gives it back`() {
        val o = listed()
        assertTrue("the fixture bet starts +EV", (o.evPercent ?: -1.0) > 0)
        // The other side's best bid falls from 0.615 to 0.50: buying this side now costs 0.50 instead of 0.385.
        val worse = NovigBook(Fixtures.ML_MARKET, 2, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to listOf(BidLevel(500, 1000))), now)
        val after = Pricing.requote(o, worse, settings, now)
        assertTrue("no longer +EV: ${after.evPercent}", (after.evPercent ?: -1.0) <= 0)
        assertEquals(now, after.bookFetchedAtMs)
        assertEquals("the fair it carried is untouched", o.fairProbability, after.fairProbability)
        assertEquals("no stake on a bet that is not +EV", null, after.suggestedStake)
        // Back to the first price: the same EV as before.
        val back = NovigBook(Fixtures.ML_MARKET, 3, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to listOf(BidLevel(615, 1000))), now)
        assertNotNull(Pricing.requote(after, back, settings, now).quote)
        assertEquals(o.evPercent!!, Pricing.requote(after, back, settings, now).evPercent!!, 1e-9)
    }

    @Test
    fun `a side nobody offers any more has no quote`() {
        val o = listed()
        val empty = NovigBook(Fixtures.ML_MARKET, 2, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to emptyList()), now)
        val after = Pricing.requote(o, empty, settings, now)
        assertEquals(null, after.quote)
        assertTrue(after.ladder.isEmpty())
    }
}
