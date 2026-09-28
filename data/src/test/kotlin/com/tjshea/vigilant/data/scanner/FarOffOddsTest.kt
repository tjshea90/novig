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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-28: "Research online and reconsider the 5 minute stale odds cutoff … It may be that odds do not change that
 * rapidly and are still positive EV bets even if the odds from other books are over 5 minutes old". Measured over 30
 * minutes of live prices (RESEARCH.md §30.2): lines far from the start rarely move within 10 minutes; moves bunch up near
 * the start. So other books' quotes may be 10 minutes old on a game more than 3 hours off, 5 within 3 hours or live.
 */
class FarOffOddsTest {

    private val now = Fixtures.START_MS
    private val hour = 3_600_000L
    private val min = 60_000L

    @Test
    fun `the limit is 10 minutes more than 3 hours before the start, 5 inside that, live, or when the start is unknown`() {
        assertEquals(10 * min, Freshness.maxAgeMs(now + 5 * hour, now))
        assertEquals(10 * min, Freshness.maxAgeMs(now + 3 * hour + 1, now))
        assertEquals(5 * min, Freshness.maxAgeMs(now + 3 * hour, now))
        assertEquals(5 * min, Freshness.maxAgeMs(now + hour, now))
        assertEquals(5 * min, Freshness.maxAgeMs(now - hour, now)) // live
        assertEquals(5 * min, Freshness.maxAgeMs(null, now))
        assertTrue(Freshness.fresh(now - 8 * min, now, now + 24 * hour))
        assertFalse(Freshness.fresh(now - 8 * min, now, now + 2 * hour))
    }

    private fun event(startsIn: Long) = NovigEvent("e1", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Baltimore Ravens @ Dallas Cowboys", now + startsIn)
    private fun ml(startsIn: Long) = NovigMarket("ml", "e1", "MONEY", "OPEN", "", now + startsIn, MarketFee.GAME, listOf(NovigOutcome("dal", "DAL", "TBD"), NovigOutcome("bal", "BAL", "TBD")))

    /** Dallas at 0.50 on Novig; one book says 56% fair (+12%), last seen [seenAgo] before the scan. */
    private suspend fun scan(startsIn: Long, seenAgo: Long): ScanResult {
        val novig = object : NovigSource {
            override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(event(startsIn))
            override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = listOf(ml(startsIn))
            override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
                val books = marketIds.associateWith { NovigBook(it, 1, mapOf("bal" to listOf(BidLevel(500, 1000)), "dal" to listOf(BidLevel(440, 1000))), now) }
                return BookBatch(books, 0, books.size, 0)
            }
            override suspend fun market(marketId: String): NovigMarket? = null
        }
        val fair = object : ReferenceSource {
            override val id = "kalshi"
            override val displayName = id
            override suspend fun odds(league: League, settings: ScanSettings) = RefSnapshot(
                "americanfootball_nfl",
                listOf(RefEvent("r", "americanfootball_nfl", now + startsIn, home = "Dallas Cowboys", away = "Baltimore Ravens",
                    markets = listOf(RefBookMarket(id, id, LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 1 / 0.56, null), RefQuote(Side.AWAY, 1 / 0.44, null)), now - seenAgo)))),
                now,
            )
        }
        val settings = ScanSettings(leagues = setOf("NFL"), families = setOf(MarketFamily.MONEYLINE), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1,
            minEvPercent = 0.01, sharpBooks = emptySet(), outlierGuard = false)
        return Scanner(novig, clock = { now }).scan(settings, listOf(fair), emptySet(), {}, {}).result!!
    }

    @Test
    fun `a quote 6 minutes old prices a game a day off, and stays listed until 10 minutes`() = runTest {
        val r = scan(startsIn = 24 * hour, seenAgo = 6 * min)
        val dal = r.opportunities.first { it.outcome.outcomeId == "dal" }
        assertNotNull(dal.fairProbability)
        assertFalse(dal.fairIsOld(now + 3 * min)) // 9 minutes old
        assertTrue(dal.fairIsOld(now + 4 * min + 1)) // over 10
    }

    @Test
    fun `the same quote doesn't price a game starting within 3 hours`() = runTest {
        val r = scan(startsIn = 2 * hour, seenAgo = 6 * min)
        assertNull(r.opportunities.firstOrNull { it.outcome.outcomeId == "dal" }?.fairProbability)
    }

    @Test
    fun `a far-off bet found fresh drops to the 5-minute limit once its game is within 3 hours`() = runTest {
        val r = scan(startsIn = 3 * hour + 4 * min, seenAgo = 0)
        val dal = r.opportunities.first { it.outcome.outcomeId == "dal" }
        assertFalse(dal.fairIsOld(now + 3 * min)) // still over 3 hours off: 10 minutes
        assertTrue(dal.fairIsOld(now + 6 * min)) // 2h58m off now, and 6 minutes old
    }
}
