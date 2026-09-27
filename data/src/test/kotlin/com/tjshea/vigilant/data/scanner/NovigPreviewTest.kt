package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceException
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * Tj, 2026-09-27: "use PropLine's Novig prices to order the reads, but if there is any failure or
 * delay, make the app automatically fallback to the original novig read" (RESEARCH.md §23.6).
 */
class NovigPreviewTest {

    private var now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0, sharpBooks = setOf("pinnacle"))
    private val log: MutableList<String> = Collections.synchronizedList(ArrayList())

    /** Ravens @ Cowboys: moneyline, spread -3.5 and total 47.5. Books are read, never priced here. */
    private inner class Novig : NovigSource {
        val reads = Collections.synchronizedList(ArrayList<List<String>>())
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?): List<NovigEvent> {
            delay(100) // the board lands after the fair odds (and a quick relay)
            return listOf(NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS))
        }
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = listOf(
            NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", Fixtures.START_MS, MarketFee.GAME,
                listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD"))),
            NovigMarket(Fixtures.SPREAD_MARKET, Fixtures.EVENT_ID, "SPREAD", "OPEN", "DAL +3.5", Fixtures.START_MS, MarketFee.GAME,
                listOf(NovigOutcome(Fixtures.SPREAD_DAL, "DAL +3.5", "TBD"), NovigOutcome(Fixtures.SPREAD_BAL, "BAL -3.5", "TBD"))),
            NovigMarket(Fixtures.TOTAL_MARKET, Fixtures.EVENT_ID, "TOTAL", "OPEN", "BAL @ DAL t47.5", Fixtures.START_MS, MarketFee.GAME,
                listOf(NovigOutcome(Fixtures.TOTAL_OVER, "Over 47.5", "TBD"), NovigOutcome(Fixtures.TOTAL_UNDER, "Under 47.5", "TBD"))),
        )
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            log += "books"
            reads += marketIds.toList()
            return BookBatch(emptyMap(), 0, 0, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    /** Pinnacle's lines for the game (the fixture board), as the fair line. */
    private class Pinnacle : ReferenceSource {
        override val id = "pinnacle"
        override val displayName = "Pinnacle"
        override suspend fun odds(league: League, settings: ScanSettings) =
            RefSnapshot(league.oddsApiSportKey, Fixtures.oddsApiSeenNow(), 0)
    }

    /**
     * PropLine, relaying Novig's prices seconds ago: the Over 47.5 at +130 (fair is about -104: a big
     * edge), the moneyline a little worse than fair, and no spread.
     */
    private inner class Relay(val waitMs: Long = 0, val fail: Boolean = false, val reuse: Long = 0) : ReferenceSource {
        override val id = "propline"
        override val displayName = "PropLine"
        override val metered = true
        override fun reuseMs(settings: ScanSettings) = reuse
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            delay(waitMs)
            log += "relay"
            if (fail) throw ReferenceException("PropLine: daily limit reached")
            val novig = RefEvent(
                "pl:1", league.oddsApiSportKey, Fixtures.START_MS, home = "Dallas Cowboys", away = "Baltimore Ravens",
                markets = listOf(
                    RefBookMarket("novig", "Novig", LineKind.TOTAL, listOf(RefQuote(Side.OVER, 2.30, 47.5), RefQuote(Side.UNDER, 1.60, 47.5)), now),
                    RefBookMarket("novig", "Novig", LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 2.40, null), RefQuote(Side.AWAY, 1.60, null)), now),
                ),
            )
            return RefSnapshot(league.oddsApiSportKey, emptyList(), 0, novig = listOf(novig))
        }
    }

    private val original = listOf(Fixtures.ML_MARKET, Fixtures.SPREAD_MARKET, Fixtures.TOTAL_MARKET)

    @Test
    fun `with no relay the reads keep the original order`() = runTest {
        val novig = Novig()
        Scanner(novig, clock = { now }).scan(settings, listOf(Pinnacle()))
        assertEquals(original, novig.reads.first())
    }

    @Test
    fun `PropLine's Novig prices put the likeliest +EV line first and the worst last`() = runTest {
        val novig = Novig()
        val r = Scanner(novig, clock = { now }).scan(settings, listOf(Pinnacle(), Relay()))
        // Over 47.5 (+EV at the relayed price), then the spread (no relayed price: its usual place),
        // then the moneyline (clearly -EV at the relayed price).
        assertEquals(listOf(Fixtures.TOTAL_MARKET, Fixtures.SPREAD_MARKET, Fixtures.ML_MARKET), novig.reads.first())
        // The relayed prices only ordered the reads: nothing is priced from them.
        assertTrue(r.result!!.feed(settings).isEmpty())
        assertTrue(r.result!!.opportunities.none { it.quote != null })
    }

    @Test
    fun `a relay that answers late never holds the reads back`() = runTest {
        val novig = Novig()
        Scanner(novig, clock = { now }).scan(settings, listOf(Pinnacle(), Relay(waitMs = 10_000)))
        assertEquals("books", log.first())
        assertEquals(original, novig.reads.first())
    }

    @Test
    fun `a relay that fails leaves the original order`() = runTest {
        val novig = Novig()
        Scanner(novig, clock = { now }).scan(settings, listOf(Pinnacle(), Relay(fail = true)))
        assertEquals(original, novig.reads.first())
    }

    @Test
    fun `a relay older than three minutes orders nothing`() = runTest {
        val novig = Novig()
        val scanner = Scanner(novig, clock = { now })
        scanner.scan(settings, listOf(Pinnacle(), Relay()))
        assertEquals(Fixtures.TOTAL_MARKET, novig.reads.first().first())
        // Five minutes on PropLine fails: its last answer is kept a while, but it's too old to order by.
        novig.reads.clear()
        now += 5 * 60_000L
        scanner.scan(settings, listOf(Pinnacle(), Relay(fail = true)))
        assertEquals(original, novig.reads.first())
    }
}
