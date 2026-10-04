package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.ReadPace
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
 * very slow". A scan now says where its time went (Settings › Betting & Novig account › Novig API key), so the next "slow" comes with numbers.
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
        // With more than one fair-odds source, the slowest ones are named: the scan's first bets wait for them.
        assertEquals(
            "Last scan took 41 s: board 0.9 s · fair odds 27 s (Kalshi 27 s, Polymarket 13 s, Pinnacle 2.1 s) · first bet at 12 s · Novig refused none",
            ScanTiming.text(
                t.copy(sourceMs = listOf("Pinnacle" to 2_100L, "Kalshi" to 27_400L, "Polymarket" to 13_000L)),
                prices = 0, viaKey = 0, viaPush = 0,
            ),
        )
        // A lone source says nothing extra.
        assertEquals(
            "Last scan took 41 s: board 0.9 s · fair odds 27 s · first bet at 12 s · Novig refused none",
            ScanTiming.text(t.copy(sourceMs = listOf("Kalshi" to 27_400L)), prices = 0, viaKey = 0, viaPush = 0),
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

    /**
     * Tj, 2026-10-04 ("a lot of times the vigilant scanner slows down significantly when it is scanning novig prices, maybe down to 2 per second. Other times it
     * is very fast"): the file said how fast the last scan went but not what paced it. Now it says the public route's pace after refusals, the key route's
     * refusals, and how much of what the live feed was asked for it held at the end: and says nothing of a route that wasn't slowed or used.
     */
    @Test
    fun `the line says what paced the reads - the public route after refusals, the key's refusals, the live feed's hold - only where it matters`() {
        val slow = ScanTiming(
            boardAtMs = 900, fairAtMs = 27_400, novigFromMs = 1_000, novigToMs = 201_000, firstBetAtMs = 12_300, totalMs = 205_000, refused = 7,
            liveFeedAtMs = 4_100, liveFeedAsked = 2_000, liveFeedHeld = 315, pace = ReadPace(4.0, 2.0, 3.5, 14.0, null, 14.0, 0),
        )
        assertEquals(
            "Last scan took 205 s: board 0.9 s · fair odds 27 s · 4,359 Novig prices in 200 s (21.8 a second: 315 by live feed, 2501 through the key, 1543 public) · " +
                "live feed asked for 2,000 at 4.1 s · public route at 4 a second, down to 2 after a refusal, 3.5 at the end · live feed held 315 of 2,000 asked at the end · " +
                "first bet at 12 s · Novig refused 7 · the key's limit is 16 a second",
            ScanTiming.text(slow, prices = 4_359, viaKey = 2_501, viaPush = 315, keyPerSec = 16.0),
        )
        // The key route refused: its pace and the count say so; the public route, unused and never slowed, says nothing.
        val keyed = ScanTiming(
            novigFromMs = 0, novigToMs = 10_000, totalMs = 12_000, pace = ReadPace(4.0, null, 4.0, 14.0, 7.0, 14.0, 3),
        )
        assertEquals(
            "Last scan took 12 s: board 0.0 s · fair odds 0.0 s · 100 Novig prices in 10 s (10.0 a second: 100 through the key) · " +
                "key route at 14 a second, down to 7 after a refusal, refused 3× · no bet · Novig refused none",
            ScanTiming.text(keyed, prices = 100, viaKey = 100, viaPush = 0),
        )
        // A clean, fast scan on the key: nothing to add.
        assertEquals(
            emptyList<String>(), ScanTiming.routeNotes(ScanTiming(pace = ReadPace(4.0, null, 4.0, 14.0, null, 14.0, 0)), publicReads = 0),
        )
        // A public read at an unslowed pace still says its pace (it is the slow route), and an end pace that differs says where it ended.
        assertEquals(
            listOf("public route at 4 a second, 5 at the end"), ScanTiming.routeNotes(ScanTiming(pace = ReadPace(4.0, null, 5.0, 14.0, null, 14.0, 0)), publicReads = 12),
        )
        // A live feed never asked for says nothing; asked and holding none says so.
        assertEquals(emptyList<String>(), ScanTiming.routeNotes(ScanTiming(liveFeedAsked = 0, liveFeedHeld = null), publicReads = 0))
        assertEquals(listOf("live feed held 0 of 50 asked at the end"), ScanTiming.routeNotes(ScanTiming(liveFeedAsked = 50, liveFeedHeld = 0), publicReads = 0))
    }

    @Test
    fun `batches' paces merge into the scan's - where it began, the lowest reached, where it ended, the refusals added`() {
        val first = ReadPace(4.0, 2.0, 2.0, 14.0, null, 14.0, 1)
        val second = ReadPace(2.0, null, 3.5, 14.0, 7.0, 7.0, 2)
        assertEquals(ReadPace(4.0, 2.0, 3.5, 14.0, 7.0, 7.0, 3), first.merge(second))
        assertEquals(ReadPace(4.0, 1.5, 3.5, 14.0, null, 7.0, 3), first.merge(second.copy(publicLow = 1.5, keyedLow = null)))
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
            return BookBatch(books, 0, books.size, 0, refused = 2, pace = ReadPace(4.0, 2.0, 3.5, 14.0, null, 14.0, 0))
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
        val t = report.timing!!
        assertEquals(600L, t.boardAtMs)
        assertEquals(t.totalMs, ms)
        assertNotNull(t.novigFromMs)
        assertEquals(2_000L, t.novigToMs!! - t.novigFromMs!!)
        assertNotNull("the bet showed", t.firstBetAtMs)
        assertEquals(2, t.refused)
        // What paced the batch reaches the scan's timing, and a scan that never asked the live feed has no hold to report.
        assertEquals(ReadPace(4.0, 2.0, 3.5, 14.0, null, 14.0, 0), t.pace)
        assertNull(t.liveFeedHeld)
        assertEquals(1, report.result!!.feed(settings).size)
    }

    @Test
    fun `a scan with no league picked does nothing and times nothing`() = runTest {
        val report = Scanner(novig, clock = { now }, elapsed = { ms }).scan(ScanSettings(leagues = emptySet()), listOf(fair), emptySet(), {}, {})
        assertNull(report.timing)
    }
}
