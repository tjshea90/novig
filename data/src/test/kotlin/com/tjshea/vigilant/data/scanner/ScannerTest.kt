package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.PartialReferenceException
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.ScanContext
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerTest {

    private var now = Fixtures.START_MS - 86_400_000L

    private inner class FakeNovig : NovigSource {
        var catalogCalls = 0
        var bookCalls = 0
        var lastBookIds: Collection<String> = emptyList()
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(
            NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS),
        ).also { catalogCalls++ }
        var lastTypes: Collection<String> = emptyList()
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = listOf(
            NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", Fixtures.START_MS, MarketFee.GAME,
                listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD"))),
        ).also { lastTypes = marketTypes }
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            bookCalls++
            lastBookIds = marketIds
            onProgress?.invoke(marketIds.size, marketIds.size)
            val b = NovigBook(Fixtures.ML_MARKET, 1, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to listOf(BidLevel(615, 1000))), now)
            return BookBatch(mapOf(Fixtures.ML_MARKET to b), 0, 1, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    /** The Odds API, faked: metered, re-used for the settings' window. */
    private class FakeOddsApi(val fail: Boolean = false) : ReferenceSource {
        var calls = 0
        override val id = "oddsapi"
        override val displayName = "The Odds API"
        override val metered = true
        override fun reuseMs(settings: ScanSettings) = settings.oddsApiReuseMinutes * 60_000L
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            calls++
            if (fail) throw AllKeysExhaustedException("All 1 The Odds API key(s) are rate-limited or invalid")
            return RefSnapshot(league.oddsApiSportKey, TheOddsApiClient.parseEvents(Fixtures.oddsApi, Json { ignoreUnknownKeys = true }), 0, 488, 12)
        }
    }

    /** A free exchange: fetched on every scan. Quotes DAL/BAL like Polymarket would. */
    private class FakeExchange(val key: String, val dal: Double, val bal: Double) : ReferenceSource {
        var calls = 0
        override val id = key
        override val displayName = key
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            calls++
            val ev = RefEvent("$key-1", league.oddsApiSportKey, Fixtures.START_MS, home = "Cowboys", away = "Ravens",
                markets = listOf(RefBookMarket(key, key, LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, bal, null), RefQuote(Side.HOME, dal, null)), 0)))
            return RefSnapshot(league.oddsApiSportKey, listOf(ev), 0)
        }
    }

    /** Sportsbook props, faked: needs Novig's board, and can fail after answering part of it. */
    private class FakeProps(val partial: Boolean) : ReferenceSource {
        var seen: ScanContext? = null
        override val id = "oddsapi_props"
        override val displayName = "Sportsbook props"
        override val metered = true
        override val needsCatalog = true
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = error("needs Novig's board")
        override suspend fun odds(league: League, settings: ScanSettings, context: ScanContext): RefSnapshot {
            seen = context
            val snap = RefSnapshot(league.oddsApiSportKey, TheOddsApiClient.parseEvents(Fixtures.oddsApi, Json { ignoreUnknownKeys = true }), 0, 470, 30)
            if (partial) throw PartialReferenceException(snap, "Sportsbook props NFL: credits ran out")
            return snap
        }
    }

    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0, sharpBooks = setOf("pinnacle"))

    @Test
    fun `prop stats only the sportsbooks price are fetched from Novig only when book props are on`() = runTest {
        val novig = FakeNovig()
        Scanner(novig, clock = { now }).scan(settings, emptyList())
        assertTrue("PITCHER_STRIKEOUTS" in novig.lastTypes && "PITCHER_OUTS" in novig.lastTypes) // Kalshi prices them
        assertTrue("BATTING_STRIKEOUTS" !in novig.lastTypes && "KICKING_POINTS" !in novig.lastTypes)

        val withProps = FakeNovig()
        Scanner(withProps, clock = { now }).scan(settings, listOf(FakeProps(partial = false)))
        assertTrue("BATTING_STRIKEOUTS" in withProps.lastTypes && "KICKING_POINTS" in withProps.lastTypes)
    }

    @Test
    fun `a source that needs the board gets it, and a partial answer is priced and reported`() = runTest {
        val props = FakeProps(partial = true)
        val r = Scanner(FakeNovig(), clock = { now }).scan(settings, listOf(props))
        assertEquals(listOf(Fixtures.EVENT_ID), props.seen!!.novigEvents.map { it.eventId })
        assertEquals(now, props.seen!!.now)
        assertEquals(listOf("Sportsbook props NFL: credits ran out"), r.errors)
        // What came back before the failure still prices the game.
        assertNotNull(r.result!!.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }.fairProbability)
        assertEquals(1, r.sources.single().fetched)
    }

    @Test
    fun `a scan fetches the board, fair odds and books once each, with progress`() = runTest {
        val novig = FakeNovig()
        val ref = FakeOddsApi()
        val steps = ArrayList<ScanProgress>()
        val r = Scanner(novig, clock = { now }).scan(settings, listOf(ref), onProgress = { steps += it })
        assertNotNull(r.result)
        assertEquals(488, r.creditsRemaining)
        assertEquals(1, ref.calls)
        assertEquals(1, novig.catalogCalls)
        assertEquals(1, novig.bookCalls)
        assertEquals(listOf(Fixtures.ML_MARKET), novig.lastBookIds.toList())
        assertEquals("Novig prices", steps.last().step)
        assertEquals(1, r.sources.single().fetched)
        assertEquals(1, r.sources.single().matched)
    }

    @Test
    fun `The Odds API is re-used inside its window so repeated scans don't burn credits`() = runTest {
        val ref = FakeOddsApi()
        val scanner = Scanner(FakeNovig(), clock = { now })
        val s = settings.copy(oddsApiReuseMinutes = 15)
        scanner.scan(s, listOf(ref))
        now += 5 * 60_000
        val second = scanner.scan(s, listOf(ref))
        assertEquals(1, ref.calls)
        assertEquals(1, second.sources.single().reused)
        now += 11 * 60_000
        scanner.scan(s, listOf(ref))
        assertEquals(2, ref.calls)
        // 0 = fetch on every scan.
        scanner.scan(s.copy(oddsApiReuseMinutes = 0), listOf(ref))
        assertEquals(3, ref.calls)
    }

    @Test
    fun `changing the reference books makes the next scan pay for fresh odds`() = runTest {
        val ref = FakeOddsApi()
        val scanner = Scanner(FakeNovig(), clock = { now })
        scanner.scan(settings, listOf(ref))
        scanner.scan(settings.copy(referenceBooks = listOf("pinnacle")), listOf(ref))
        assertEquals(2, ref.calls)
    }

    @Test
    fun `free exchanges are fetched on every scan and merged with the other feeds`() = runTest {
        val poly = FakeExchange("polymarket", dal = 1 / 0.40, bal = 1 / 0.61)
        val kalshi = FakeExchange("kalshi", dal = 1 / 0.41, bal = 1 / 0.60)
        val scanner = Scanner(FakeNovig(), clock = { now })
        val s = settings.copy(fairSource = FairSource.MARKET_AVERAGE, minBooks = 1)
        scanner.scan(s, listOf(kalshi, poly, FakeOddsApi()))
        val r = scanner.scan(s, listOf(kalshi, poly, FakeOddsApi()))
        assertEquals(2, poly.calls)
        assertEquals(2, kalshi.calls)
        val dal = r.result!!.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        // Pinnacle, DraftKings, FanDuel from The Odds API, plus both exchanges.
        assertEquals(setOf("Pinnacle", "DraftKings", "FanDuel", "polymarket", "kalshi"), dal.fair!!.averageBooksUsed.toSet())
        assertEquals(setOf("polymarket", "kalshi", "oddsapi"), r.sources.filter { it.matched == 1 }.map { it.id }.toSet())
    }

    @Test
    fun `the catalog is re-used for three minutes and refetched when leagues change`() = runTest {
        val novig = FakeNovig()
        val scanner = Scanner(novig, clock = { now })
        scanner.scan(settings, emptyList())
        now += 60_000
        scanner.scan(settings, emptyList())
        assertEquals(1, novig.catalogCalls)
        now += 3 * 60_000
        scanner.scan(settings, emptyList())
        assertEquals(2, novig.catalogCalls)
        scanner.scan(settings.copy(leagues = setOf("NFL", "MLB")), emptyList())
        assertEquals(3, novig.catalogCalls)
    }

    @Test
    fun `a used-up key is reported once and not retried for every league`() = runTest {
        val ref = FakeOddsApi(fail = true)
        val r = Scanner(FakeNovig(), clock = { now }).scan(settings.copy(leagues = setOf("NFL", "MLB", "NBA")), listOf(ref))
        assertTrue(r.errors.single().contains("rate-limited or invalid"))
        assertEquals(1, ref.calls)
    }

    @Test
    fun `changing a pricing setting re-prices from cache with no network`() = runTest {
        val novig = FakeNovig()
        val ref = FakeOddsApi()
        val scanner = Scanner(novig, clock = { now })
        scanner.scan(settings, listOf(ref))
        val repriced = scanner.reprice(settings.copy(fairSource = FairSource.MARKET_AVERAGE, minBooks = 1))!!
        assertEquals(1, novig.bookCalls)
        assertEquals(1, novig.catalogCalls)
        assertEquals(1, ref.calls)
        val dal = repriced.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertEquals(FairSource.MARKET_AVERAGE, dal.fair!!.sourceUsed)
    }

    @Test
    fun `turning a source off in settings drops its quotes on re-price, without a scan`() = runTest {
        val scanner = Scanner(FakeNovig(), clock = { now })
        val s = settings.copy(fairSource = FairSource.MARKET_AVERAGE, minBooks = 1)
        scanner.scan(s, listOf(FakeExchange("polymarket", 1 / 0.40, 1 / 0.61), FakeOddsApi()))
        val off = scanner.reprice(s.copy(usePolymarket = false))!!
        val dal = off.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertTrue("polymarket" !in dal.fair!!.averageBooksUsed)
    }

    @Test
    fun `nothing is fetched before the first scan and re-price has nothing to show`() = runTest {
        val novig = FakeNovig()
        val scanner = Scanner(novig, clock = { now })
        assertNull(scanner.reprice(settings))
        assertEquals(setOf("NFL"), scanner.unscannedLeagues(settings))
        assertEquals(0, novig.catalogCalls + novig.bookCalls)
    }

    @Test
    fun `no fair source still gives a live Novig board`() = runTest {
        val r = Scanner(FakeNovig(), clock = { now }).scan(settings, emptyList())
        assertEquals(2, r.result!!.opportunities.size)
        assertEquals(0.385, r.result!!.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }.ladder.first().price, 1e-12)
    }
}
