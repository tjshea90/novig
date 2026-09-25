package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BetTrackerTest {

    @get:Rule val tmp = TemporaryFolder()
    private var now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0)

    private fun scan(dalBid: Int = 380, balBid: Int = 615, pinDal: Double = 2.45): ScanResult {
        val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
        val market = NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", Fixtures.START_MS, MarketFee.GAME,
            listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD")))
        val refs = mapOf("americanfootball_nfl" to RefSnapshot("americanfootball_nfl",
            TheOddsApiClient.parseEvents(Fixtures.oddsApi.replace("\"price\":2.45", "\"price\":$pinDal"), Json { ignoreUnknownKeys = true }), now))
        val plan = Planner.plan(listOf(event), listOf(market), refs, settings, now)
        val book = NovigBook(Fixtures.ML_MARKET, 1, mapOf(Fixtures.ML_DAL to listOf(BidLevel(dalBid, 1000)), Fixtures.ML_BAL to listOf(BidLevel(balBid, 1000))), now)
        return Pricing.price(plan, mapOf(Fixtures.ML_MARKET to book), settings, now)
    }

    private fun dal(r: ScanResult) = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }

    @Test
    fun `tracks, settles and computes profit at Novig's price`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        val bet = t.track(dal(scan()), stake = 38.5)!!
        assertEquals(0.385, bet.cost, 1e-12)
        t.settle(bet.id, BetStatus.WON)
        val won = t.all().single()
        assertEquals(38.5 * (1 / 0.385 - 1), won.profit!!, 1e-9) // $61.50
        val s = BetTracker.stats(t.all())
        assertEquals(1, s.settled)
        assertEquals(won.profit!! / 38.5, s.roi!!, 1e-12)
    }

    @Test
    fun `closing fair keeps updating until kickoff, then freezes`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        t.track(dal(scan()), stake = 10.0)
        t.observe(scan(pinDal = 2.30)) // line moved toward DAL: we beat the close
        val clv1 = t.all().single().clvPercent!!
        now = Fixtures.START_MS + 1
        t.observe(scan(pinDal = 3.00)) // after kickoff: ignored
        assertEquals(clv1, t.all().single().clvPercent!!, 0.0)
        assertEquals(1.0, BetTracker.stats(t.all()).beatClosePercent!!, 0.0)
    }

    @Test
    fun `an opportunity with no price can't be tracked`() = runTest {
        val t = BetTracker(File(tmp.root, "bets.json"), clock = { now })
        val noPrice = dal(scan()).copy(quote = null)
        assertNull(t.track(noPrice, 5.0))
    }
}
