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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tj, 2026-09-28: "Did this latest version change anything with the novig API scan because now it is reading the API
 * very slow". A scan now says where its time went (Settings › Novig API), so the next "slow" comes with numbers.
 */
class ScanTimingTest {

    @Test
    fun `the Settings line says where the time went`() {
        val t = ScanTiming(boardAtMs = 900, fairAtMs = 27_400, novigFromMs = 1_000, novigToMs = 39_000, firstBetAtMs = 12_300, totalMs = 41_200, refused = 0)
        assertEquals(
            "Last scan took 41 s: board 0.9 s · fair odds 27 s · 1,200 Novig prices in 38 s (31.6 a second: 700 by live feed, " +
                "500 through the key) · first bet at 12 s · Novig refused none · the key's limit is 16 a second",
            ScanTiming.text(t, prices = 1200, viaKey = 500, viaPush = 700, keyPerSec = 16.0),
        )
        // No key: public prices, refusals counted, no bet.
        assertEquals(
            "Last scan took 1.5 s: board 0.2 s · fair odds 1.0 s · 8 Novig prices in 1.2 s (6.7 a second: 8 public) · no bet · Novig refused 3",
            ScanTiming.text(ScanTiming(200, 1_000, 300, 1_500, null, 1_500, 3), prices = 8, viaKey = 0, viaPush = 0),
        )
        // Nothing read: no prices part.
        assertEquals(
            "Last scan took 2.0 s: board 0.5 s · fair odds 2.0 s · no bet · Novig refused none",
            ScanTiming.text(ScanTiming(500, 2_000, null, null, null, 2_000, 0), prices = 0, viaKey = 0, viaPush = 0),
        )
    }

    private var now = Fixtures.START_MS - 86_400_000L
    private var ms = 0L
    private val event = NovigEvent("e1", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
    private val ml = NovigMarket("ml", "e1", "MONEY", "OPEN", "", Fixtures.START_MS, MarketFee.GAME, listOf(NovigOutcome("dal", "DAL", "TBD"), NovigOutcome("bal", "BAL", "TBD")))

    /** Dallas at 0.50 against a 56% fair line: +12%. Each read takes 2 s on the timing clock, and Novig refused 2. */
    private val novig = object : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?): List<NovigEvent> {
            ms += 300
            return listOf(event)
        }
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?): List<NovigMarket> {
            ms += 300
            return listOf(ml)
        }
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            ms += 2_000
            val books = marketIds.associateWith { NovigBook(it, 1, mapOf("bal" to listOf(BidLevel(500, 1000)), "dal" to listOf(BidLevel(440, 1000))), now) }
            return BookBatch(books, 0, books.size, 0, refused = 2)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    private val fair = object : ReferenceSource {
        override val id = "kalshi"
        override val displayName = id
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            ms += 5_000
            val quotes = listOf(RefQuote(Side.HOME, 1 / 0.56, null), RefQuote(Side.AWAY, 1 / 0.44, null))
            return RefSnapshot(
                "americanfootball_nfl",
                listOf(RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens", markets = listOf(RefBookMarket(id, id, LineKind.MONEYLINE, quotes, now)))),
                now,
            )
        }
    }

    @Test
    fun `a scan records when the board, fair odds, prices and first bet came, and Novig's refusals`() = runTest {
        val settings = ScanSettings(leagues = setOf("NFL"), families = setOf(MarketFamily.MONEYLINE), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, minEvPercent = 0.01, sharpBooks = emptySet(), outlierGuard = false)
        val report = Scanner(novig, clock = { now }, elapsed = { ms }).scan(settings, listOf(fair), emptySet(), {}, {})
        val t = assertNotNull(report.timing).let { report.timing!! }
        assertEquals(600L, t.boardAtMs)
        assertEquals(t.totalMs, ms)
        assertNotNull(t.novigFromMs)
        assertEquals(2_000L, t.novigToMs!! - t.novigFromMs!!)
        assertNotNull("the bet showed", t.firstBetAtMs)
        assertEquals(2, t.refused)
        assertEquals(1, report.result!!.feed(settings).size)
    }

    @Test
    fun `a scan with no league picked does nothing and times nothing`() = runTest {
        val report = Scanner(novig, clock = { now }, elapsed = { ms }).scan(ScanSettings(leagues = emptySet()), listOf(fair), emptySet(), {}, {})
        assertNull(report.timing)
    }
}
