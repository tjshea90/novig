package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tj, 2026-10-15: "Is this speed too slow for pre game betting and bidding?" A long scan read its fair odds once and then spent minutes on Novig's books, so the odds behind a game about to start were 13-19 minutes old. With
 * [ScanGuards.hotRefresh] the games starting within [ScanGuards.hotHours] get their fair odds (from the feeds that cost nothing per read) and their likeliest Novig books read again WHILE the scan runs; the far games keep the
 * scan's own pace, and a credit-metered feed is never asked again.
 */
class HotRefreshTest {
    private val base = Fixtures.START_MS - 86_400_000L
    private val hotStart = base + 2 * 3_600_000L
    private val farStart = base + 72 * 3_600_000L

    private val hotEvent = NovigEvent("e-hot", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Baltimore Ravens @ Dallas Cowboys", hotStart)
    private val farEvent = NovigEvent("e-far", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Denver Broncos @ Miami Dolphins", farStart)
    private val hotMl = NovigMarket("ml-hot", "e-hot", "MONEY", "OPEN", "", hotStart, MarketFee.GAME, listOf(NovigOutcome("dal", "DAL", "TBD"), NovigOutcome("bal", "BAL", "TBD")))
    private val farMl = NovigMarket("ml-far", "e-far", "MONEY", "OPEN", "", farStart, MarketFee.GAME, listOf(NovigOutcome("mia", "MIA", "TBD"), NovigOutcome("den", "DEN", "TBD")))

    private class Novig(val hot: NovigMarket, val far: NovigMarket, val events: List<NovigEvent>, val clock: () -> Long) : NovigSource {
        /** Each books() call's market ids, with the time it was made. */
        val reads = CopyOnWriteArrayList<Pair<Long, List<String>>>()
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = listOf(hot, far)
        override suspend fun market(marketId: String): NovigMarket? = null
        override fun batchSize(): Int = 1
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            reads += clock() to marketIds.toList()
            delay(40_000)
            val at = clock()
            // Dallas / Miami at 0.50 against a 56% fair: +12%
            val books = marketIds.associateWith { id ->
                val (a, b) = if (id == "ml-hot") "dal" to "bal" else "mia" to "den"
                NovigBook(id, 1, mapOf(b to listOf(BidLevel(500, 1000)), a to listOf(BidLevel(440, 1000))), at)
            }
            return BookBatch(books, 0, books.size, 0)
        }
    }

    private inner class Fair(override val id: String, val clock: () -> Long, private val isMetered: Boolean = false, private val slowMs: Long = 0L, private val holds: Boolean = true) : ReferenceSource {
        val asked = CopyOnWriteArrayList<Long>()
        override val displayName = id
        override val metered: Boolean get() = isMetered
        override val holdsBackList: Boolean get() = holds
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            asked += clock()
            if (slowMs > 0) delay(slowMs)
            val q = listOf(RefQuote(Side.HOME, 1 / 0.56, null), RefQuote(Side.AWAY, 1 / 0.44, null))
            val t = clock()
            fun ev(home: String, away: String, start: Long) = RefEvent("r-$home", "americanfootball_nfl", start, home = home, away = away, markets = listOf(RefBookMarket(id, id, LineKind.MONEYLINE, q, t)))
            return RefSnapshot("americanfootball_nfl", listOf(ev("Dallas Cowboys", "Baltimore Ravens", hotStart), ev("Miami Dolphins", "Denver Broncos", farStart)), t)
        }
    }

    private fun settings(hot: Boolean) = ScanSettings(
        leagues = setOf("NFL"), families = setOf(MarketFamily.MONEYLINE), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, minEvPercent = 0.01, sharpBooks = emptySet(), outlierGuard = false,
        vigGuards = ScanGuards(hotRefresh = hot, hotHours = 12, hotEverySeconds = 30),
    )

    @Test
    fun `while the scan reads Novig, a game about to start gets its fair odds and its Novig book read again, and a far game does not`() = runTest {
        val clock = { base + currentTime }
        val novig = Novig(hotMl, farMl, listOf(hotEvent, farEvent), clock)
        val kalshi = Fair("kalshi", clock)
        val report = Scanner(novig, clock = clock, elapsed = { currentTime }).scan(settings(hot = true), listOf(kalshi), emptySet(), {}, {})
        val hotReads = novig.reads.count { "ml-hot" in it.second }
        val farReads = novig.reads.count { "ml-far" in it.second }
        assertEquals("the far game's book is read once", 1, farReads)
        assertTrue("the hot game's book is read again: ${novig.reads}", hotReads >= 2)
        assertTrue("the fair odds were asked again while the scan ran: ${kalshi.asked}", kalshi.asked.size >= 2)
        val t = report.timing!!
        assertTrue(t.hotReads >= 1 && t.hotFairReads >= 1)
        assertTrue(ScanTiming.text(t, prices = 2, viaKey = 0, viaPush = 0).contains("hot refresh:"))
        // the scan's own result prices the hot line from the book read again
        val hotOpp = report.result!!.opportunities.first { it.market.marketId == "ml-hot" }
        assertTrue("priced from a book read after the first one", (hotOpp.bookFetchedAtMs ?: 0L) > base + 40_000L)
    }

    @Test
    fun `with the hot refresh off the scan is what it was - every feed asked once, every book read once`() = runTest {
        val clock = { base + currentTime }
        val novig = Novig(hotMl, farMl, listOf(hotEvent, farEvent), clock)
        val kalshi = Fair("kalshi", clock)
        val report = Scanner(novig, clock = clock, elapsed = { currentTime }).scan(settings(hot = false), listOf(kalshi), emptySet(), {}, {})
        assertEquals(1, kalshi.asked.size)
        assertEquals(listOf(1, 1), listOf("ml-hot", "ml-far").map { id -> novig.reads.count { id in it.second } })
        assertEquals(0, report.timing!!.hotReads); assertEquals(0, report.timing!!.hotFairReads)
        assertTrue(!ScanTiming.text(report.timing!!, prices = 2, viaKey = 0, viaPush = 0).contains("hot refresh"))
    }

    @Test
    fun `a feed that is credit-metered or daily-capped is never asked again by the hot refresh`() = runTest {
        val clock = { base + currentTime }
        val novig = Novig(hotMl, farMl, listOf(hotEvent, farEvent), clock)
        val kalshi = Fair("kalshi", clock)
        val parlay = Fair("parlay", clock, isMetered = true)
        Scanner(novig, clock = clock, elapsed = { currentTime }).scan(settings(hot = true), listOf(kalshi, parlay), emptySet(), {}, {})
        assertTrue("kalshi is on the hot list", kalshi.asked.size >= 2)
        assertEquals("ParlayAPI is asked once, as in any scan", 1, parlay.asked.size)
        assertTrue("Scanner.HOT_FEEDS holds no credit-metered feed", Scanner.HOT_FEEDS.none { it in setOf("parlay", "parlay_props", "parlay_1h", "oddsapi", "oddsapi_props", "propline", "propline_props", "propline_1h", "pinnacle", "oddspapi_free") })
    }

    @Test
    fun `a slow last-resort feed that does not hold the list back no longer delays a league's bets (Unabated, 95 s of a 548 s scan)`() = runTest {
        suspend fun firstPartialWaiting(holds: Boolean): Set<String> {
            val clock = { base + currentTime }
            val novig = Novig(hotMl, farMl, listOf(hotEvent, farEvent), clock)
            val kalshi = Fair("kalshi", clock)
            val slow = Fair("unabated", clock, slowMs = 95_000L, holds = holds)
            val partials = CopyOnWriteArrayList<ScanResult>()
            Scanner(novig, clock = clock, elapsed = { currentTime }).scan(settings(hot = false), listOf(kalshi, slow), emptySet(), {}, { partials += it })
            return partials.first().waitingFor
        }
        assertTrue("held back while the slow feed is out", firstPartialWaiting(holds = true).isNotEmpty())
        assertTrue("not held back by a feed that does not hold the list", firstPartialWaiting(holds = false).isEmpty())
    }
}
