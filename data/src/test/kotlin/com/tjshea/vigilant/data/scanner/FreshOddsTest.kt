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
import com.tjshea.vigilant.data.reference.ReferenceException
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-27: "make sure it never gives me stale odds when comparing odds from other sports books
 * … The other sports books odds MUST be current or at most a few minutes old" (RESEARCH.md §24).
 */
class FreshOddsTest {

    private var now = Fixtures.START_MS - 86_400_000L
    private val minute = 60_000L
    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0, sharpBooks = setOf("pinnacle"))

    private inner class Novig : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) =
            listOf(NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS))
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = listOf(
            NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", Fixtures.START_MS, MarketFee.GAME,
                listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD"))),
        )
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            val b = NovigBook(Fixtures.ML_MARKET, 1, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to listOf(BidLevel(615, 1000))), now)
            return BookBatch(mapOf(Fixtures.ML_MARKET to b), 0, 1, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    /** Pinnacle's moneyline, which the feed last saw [seenAgoMs] before each call. */
    private inner class Book(var seenAgoMs: Long? = 0, val reuse: Long = 0, var fail: Boolean = false) : ReferenceSource {
        var calls = 0
        override val id = "pinnacle"
        override val displayName = "Pinnacle"
        override val metered = true
        override fun reuseMs(settings: ScanSettings) = reuse
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            calls++
            if (fail) throw ReferenceException("Pinnacle: HTTP 500")
            val ml = RefBookMarket("pinnacle", "Pinnacle", LineKind.MONEYLINE,
                listOf(RefQuote(Side.HOME, 2.45, null), RefQuote(Side.AWAY, 1.62, null)), seenAgoMs?.let { now - it })
            return RefSnapshot(league.oddsApiSportKey, listOf(RefEvent("p1", league.oddsApiSportKey, Fixtures.START_MS, "Dallas Cowboys", "Baltimore Ravens", listOf(ml))), 0)
        }
    }

    private fun dal(r: ScanReport) = r.result!!.opportunities.single { it.outcome.outcomeId == Fixtures.ML_DAL }

    @Test
    fun `a book price the feed last saw minutes ago prices the line`() = runTest {
        val r = Scanner(Novig(), clock = { now }).scan(settings, listOf(Book(seenAgoMs = 2 * minute)))
        assertNotNull(dal(r).quote)
        // The EV says how old its oldest book price is, so the app can stop showing it.
        assertEquals(now - 2 * minute, dal(r).fairAsOfMs)
    }

    @Test
    fun `a book price the feed last saw over five minutes ago never prices anything`() = runTest {
        // The Odds API keeps a pulled market for ~15 minutes with its last_update frozen.
        val r = Scanner(Novig(), clock = { now }).scan(settings, listOf(Book(seenAgoMs = 6 * minute)))
        assertTrue(r.result!!.opportunities.none { it.fairProbability != null || it.quote != null })
    }

    @Test
    fun `a feed's answer is re-used for two minutes at most, whatever its own window`() = runTest {
        val book = Book(reuse = 15 * minute)
        val scanner = Scanner(Novig(), clock = { now })
        scanner.scan(settings, listOf(book))
        now += minute
        scanner.scan(settings, listOf(book))
        assertEquals(1, book.calls) // re-used a minute on
        now += 2 * minute
        assertNotNull(dal(scanner.scan(settings, listOf(book))).quote)
        assertEquals(2, book.calls) // asked again, three minutes on
    }

    @Test
    fun `a failed call's last answer stops pricing once it's five minutes old`() = runTest {
        val book = Book()
        val scanner = Scanner(Novig(), clock = { now })
        scanner.scan(settings, listOf(book))
        book.fail = true
        now += 3 * minute
        assertNotNull(dal(scanner.scan(settings, listOf(book))).quote) // three minutes: still fresh
        now += 3 * minute
        assertNull(dal(scanner.scan(settings, listOf(book))).quote) // six: never
    }

    @Test
    fun `a recheck judges the fair odds' age now, not as of the scan`() = runTest {
        val scanner = Scanner(Novig(), clock = { now })
        assertNotNull(dal(scanner.scan(settings, listOf(Book()))).quote)
        now += 10 * minute
        val recheck = scanner.recheck(settings, listOf(Fixtures.ML_MARKET))
        assertNull(recheck.result!!.opportunities.single { it.outcome.outcomeId == Fixtures.ML_DAL }.quote)
        // Re-pricing (a settings change) the same.
        assertNull(scanner.reprice(settings)!!.opportunities.single { it.outcome.outcomeId == Fixtures.ML_DAL }.quote)
    }
}
