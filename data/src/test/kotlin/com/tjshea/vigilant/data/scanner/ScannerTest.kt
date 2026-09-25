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
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = listOf(
            NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", Fixtures.START_MS, MarketFee.GAME,
                listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD"))),
        )
        override suspend fun books(marketIds: Collection<String>): BookBatch {
            bookCalls++
            lastBookIds = marketIds
            val b = NovigBook(Fixtures.ML_MARKET, 1, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to listOf(BidLevel(615, 1000))), now)
            return BookBatch(mapOf(Fixtures.ML_MARKET to b), 0, 1, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    private class FakeReference(val fail: Boolean = false) : ReferenceSource {
        var calls = 0
        override suspend fun odds(sportKey: String, bookmakers: List<String>): RefSnapshot {
            calls++
            if (fail) throw AllKeysExhaustedException("All 1 The Odds API key(s) are rate-limited or invalid")
            return RefSnapshot(sportKey, TheOddsApiClient.parseEvents(Fixtures.oddsApi, Json { ignoreUnknownKeys = true }), 0, 488, 12)
        }
    }

    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0)

    @Test
    fun `auto ticks refresh books every time but spend Odds API credits only once`() = runTest {
        val novig = FakeNovig()
        val ref = FakeReference()
        val scanner = Scanner(novig, clock = { now })

        val first = scanner.refresh(settings, RefreshKind.AUTO, ref)
        assertNotNull(first.result)
        assertEquals(488, first.creditsRemaining)
        repeat(5) { now += 15_000; scanner.refresh(settings, RefreshKind.AUTO, ref) }

        assertEquals(1, ref.calls)          // reference: once
        assertEquals(1, novig.catalogCalls) // catalog: within its 3-minute TTL
        assertEquals(6, novig.bookCalls)    // books: every tick
        assertEquals(listOf(Fixtures.ML_MARKET), novig.lastBookIds.toList())
    }

    @Test
    fun `the catalog refreshes after its TTL and immediately when leagues change`() = runTest {
        val novig = FakeNovig()
        val scanner = Scanner(novig, clock = { now })
        scanner.refresh(settings, RefreshKind.AUTO, null)
        now += 4 * 60_000
        scanner.refresh(settings, RefreshKind.AUTO, null)
        assertEquals(2, novig.catalogCalls)
        scanner.refresh(settings.copy(leagues = setOf("NFL", "MLB")), RefreshKind.AUTO, null)
        assertEquals(3, novig.catalogCalls)
    }

    @Test
    fun `pull to refresh re-pulls reference odds, and auto refresh does when the interval is due`() = runTest {
        val ref = FakeReference()
        val scanner = Scanner(FakeNovig(), clock = { now })
        scanner.refresh(settings, RefreshKind.AUTO, ref)
        scanner.refresh(settings, RefreshKind.FULL, ref)
        assertEquals(2, ref.calls)
        val auto = settings.copy(referenceRefreshMinutes = 10)
        now += 9 * 60_000
        scanner.refresh(auto, RefreshKind.AUTO, ref)
        assertEquals(2, ref.calls)
        now += 2 * 60_000
        scanner.refresh(auto, RefreshKind.AUTO, ref)
        assertEquals(3, ref.calls)
    }

    @Test
    fun `a dead key is not retried every tick`() = runTest {
        val ref = FakeReference(fail = true)
        val scanner = Scanner(FakeNovig(), clock = { now })
        val r = scanner.refresh(settings, RefreshKind.AUTO, ref)
        assertTrue(r.errors.single().contains("rate-limited or invalid"))
        repeat(10) { now += 15_000; scanner.refresh(settings, RefreshKind.AUTO, ref) }
        assertEquals(1, ref.calls)
        now += 10 * 60_000
        scanner.refresh(settings, RefreshKind.AUTO, ref)
        assertEquals(2, ref.calls)
    }

    @Test
    fun `changing a pricing setting re-prices from cache with no network`() = runTest {
        val novig = FakeNovig()
        val ref = FakeReference()
        val scanner = Scanner(novig, clock = { now })
        scanner.refresh(settings, RefreshKind.AUTO, ref)
        val before = novig.bookCalls
        val repriced = scanner.reprice(settings.copy(fairSource = FairSource.MARKET_AVERAGE, minBooks = 1))!!
        assertEquals(before, novig.bookCalls)
        assertEquals(1, ref.calls)
        val dal = repriced.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }
        assertEquals(FairSource.MARKET_AVERAGE, dal.fair!!.sourceUsed)
    }

    @Test
    fun `no key still gives a live Novig board`() = runTest {
        val r = Scanner(FakeNovig(), clock = { now }).refresh(settings, RefreshKind.FULL, null)
        assertEquals(false, r.hasReferenceSource)
        assertEquals(2, r.result!!.opportunities.size)
        assertEquals(0.385, r.result!!.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }.ladder.first().price, 1e-12)
    }
}
