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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tj's 2026-10-02 Diagnostics: Android ended Vigilant 3 times "for low memory, in the background" while it kept 46 fair-odds boards (63,903 book
 * lines) and Novig's last 3,000 books between scans. Android ends the biggest cached apps first. A board older than every game's freshness limit
 * can never price again (scans and re-pricing take only younger ones), so leaving the screen lets go of it and of what is cheap to make again.
 */
class BackgroundTrimTest {

    private var now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(leagues = setOf("MLB"), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, minEvPercent = 0.01, daysAhead = 60)
    private val events = (0 until 5).map { i -> NovigEvent("e$i", "BASEBALL", "MLB", "OPEN_PREGAME", "Away $i @ Home $i", Fixtures.START_MS + i * 3_600_000L) }
    private val markets = (0 until 5).map { i ->
        NovigMarket("m$i", "e$i", "MONEY", "OPEN", "ML", Fixtures.START_MS + i * 3_600_000L, MarketFee.GAME, listOf(NovigOutcome("a$i", "Away $i", "TBD"), NovigOutcome("h$i", "Home $i", "TBD")))
    }

    private inner class Novig(val gate: CompletableDeferred<Unit>? = null) : NovigSource {
        var trimmed = 0
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            gate?.await()
            val b = marketIds.associateWith { id ->
                val i = id.drop(1).toInt()
                NovigBook(id, 1, mapOf("a$i" to listOf(BidLevel(440, 1000)), "h$i" to listOf(BidLevel(550, 1000))), now)
            }
            return BookBatch(b, 0, b.size, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
        override fun trimCaches() { trimmed++ }
    }

    private inner class Fair(override val id: String) : ReferenceSource {
        override val displayName = id
        override suspend fun odds(league: League, settings: ScanSettings) = RefSnapshot(
            league.oddsApiSportKey,
            events.mapIndexed { i, e ->
                RefEvent("$id$i", league.oddsApiSportKey, e.startsTs, home = "Home $i", away = "Away $i",
                    markets = listOf(RefBookMarket(id, id, LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, 2.0, null), RefQuote(Side.HOME, 2.0, null)), now)))
            },
            now,
        )
    }

    /**
     * A heap with room: the scanner also lets its caches go mid-scan when the heap is pressing ([com.tjshea.vigilant.data.MemoryGuard.pressing]),
     * and on CI's busy test JVM that counted as trims here (failed twice on CI, 2026-10-02 04:46Z and 15:17Z). This test is about leaving the
     * screen, not about memory pressure (StreamingScanTest covers that).
     */
    @Before fun roomyHeap() {
        com.tjshea.vigilant.data.MemoryGuard.probe = object : com.tjshea.vigilant.data.MemoryGuard.Probe {
            override fun used() = 0L
            override fun max() = 1L shl 30
            override fun collect() {}
        }
    }

    @After fun realHeap() = com.tjshea.vigilant.data.MemoryGuard.useRealProbe()

    @Test
    fun `boards past every freshness limit go when Vigilant leaves the screen, young ones stay and still price`() = runTest {
        val novig = Novig()
        val scanner = Scanner(novig, clock = { now })
        scanner.scan(settings, listOf(Fair("polymarket"), Fair("kalshi")), onProgress = {}, onPartial = {})
        assertEquals(2, scanner.holdings().snapshots)

        // Five minutes on: both boards can still price a game hours off. Nothing dropped; re-pricing still finds the edges.
        now += 5 * 60_000L
        assertEquals(0, scanner.trimForBackground())
        assertEquals(2, scanner.holdings().snapshots)
        assertTrue(scanner.reprice(settings)!!.feed(settings).isNotEmpty())
        assertEquals("the books cache is let go either way", 1, novig.trimmed)

        // Eleven minutes on: past the 10-minute limit for any game, they can't price again; they go.
        now += 6 * 60_000L
        assertEquals(2, scanner.trimForBackground())
        assertEquals(0, scanner.holdings().snapshots)
        // Re-pricing then shows nothing (as it would have: the boards were too old), and a new scan works as before.
        assertTrue(scanner.reprice(settings)!!.feed(settings).isEmpty())
        val again = scanner.scan(settings, listOf(Fair("polymarket"), Fair("kalshi")), onProgress = {}, onPartial = {})
        assertNotNull(again.result)
        assertTrue(again.result!!.feed(settings).isNotEmpty())
    }

    @Test
    fun `a scan in progress is never trimmed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val novig = Novig(gate)
        val scanner = Scanner(novig, clock = { now })
        val running = async { scanner.scan(settings, listOf(Fair("polymarket")), onProgress = {}, onPartial = {}) }
        testScheduler.advanceUntilIdle()
        now += 20 * 60_000L
        assertEquals(0, scanner.trimForBackground())
        assertEquals(0, novig.trimmed)
        gate.complete(Unit)
        running.await()
    }
}
