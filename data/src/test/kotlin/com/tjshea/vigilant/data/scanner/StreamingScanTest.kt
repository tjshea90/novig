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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-25 ~18:05Z: "This app is very slow… make the results show up in the app as they come
 * in" and "I will run the scan then switch apps". Novig's prices stream into the feed, likeliest
 * +EV first, and are read while the fair odds are still loading; the scan itself lives in
 * [ScanRunner], outside any screen.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StreamingScanTest {

    private var now = Fixtures.START_MS - 86_400_000L

    private val teams = listOf(
        "Baltimore Ravens" to "Dallas Cowboys", "Buffalo Bills" to "Miami Dolphins", "Kansas City Chiefs" to "Denver Broncos",
        "Green Bay Packers" to "Chicago Bears", "Seattle Seahawks" to "Los Angeles Rams", "Detroit Lions" to "Minnesota Vikings",
        "Houston Texans" to "Tennessee Titans", "Atlanta Falcons" to "New Orleans Saints", "Pittsburgh Steelers" to "Cleveland Browns",
        "Arizona Cardinals" to "San Francisco 49ers",
    )

    /** Game i starts i hours after the first: soonest-first order is m0, m1, … m9. */
    private fun startOf(i: Int) = Fixtures.START_MS + i * 3_600_000L

    private val events = teams.mapIndexed { i, (a, h) -> NovigEvent("e$i", "FOOTBALL", "NFL", "OPEN_PREGAME", "$a @ $h", startOf(i)) }
    private val markets = teams.mapIndexed { i, (a, h) ->
        NovigMarket("m$i", "e$i", "MONEY", "OPEN", "ML", startOf(i), MarketFee.GAME, listOf(NovigOutcome("a$i", a, "TBD"), NovigOutcome("h$i", h, "TBD")))
    }

    /**
     * Ten moneylines. Fair is 50/50 on every game. Normally Novig's take is 0.52 either way (−3.8%);
     * on a game in [edgeOn] the away side can be taken at 0.45 (+11.1%).
     */
    private inner class Novig(var edgeOn: Set<Int>) : NovigSource {
        val calls = ArrayList<List<String>>()
        val order: List<String> get() = calls.flatten()

        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            calls += marketIds.toList()
            val books = marketIds.associateWith { id ->
                val i = id.drop(1).toInt()
                val (awayBid, homeBid) = if (i in edgeOn) 440 to 550 else 480 to 480
                NovigBook(id, 1, mapOf("a$i" to listOf(BidLevel(awayBid, 1000)), "h$i" to listOf(BidLevel(homeBid, 1000))), now)
            }
            onProgress?.invoke(marketIds.size, marketIds.size)
            return BookBatch(books, 0, books.size, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    /** A free exchange quoting every game at 50/50. [gate] holds its answer back until completed. */
    private inner class Fair(override val id: String = "polymarket", val gate: CompletableDeferred<Unit>? = null) : ReferenceSource {
        var calls = 0
        override val displayName = id
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            calls++
            gate?.await()
            val refs = teams.mapIndexed { i, (a, h) ->
                RefEvent(
                    "$id-$i", league.oddsApiSportKey, startOf(i), home = h, away = a,
                    markets = listOf(RefBookMarket(id, id, LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, 2.0, null), RefQuote(Side.HOME, 2.0, null)), now)),
                )
            }
            return RefSnapshot(league.oddsApiSportKey, refs, now)
        }
    }

    private val settings = ScanSettings(fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, minEvPercent = 0.01)

    private fun ids(r: ScanResult) = r.feed(settings).map { it.market.marketId }.toSet()

    @Test
    fun `results stream in as Novig's prices land, and every market is read once`() = runTest {
        val novig = Novig(edgeOn = setOf(7))
        val partials = ArrayList<ScanResult>()
        val progress = ArrayList<ScanProgress>()
        val r = Scanner(novig, clock = { now }).scan(settings, listOf(Fair()), onProgress = { progress += it }, onPartial = { partials += it })

        // A few books at a time, soonest game first, each market once.
        assertEquals(listOf(Scanner.CHUNK, 10 - Scanner.CHUNK), novig.calls.map { it.size })
        assertEquals((0..9).map { "m$it" }, novig.order)
        // One partial result per batch; the edge on game 7 shows after the first batch, not the last.
        assertEquals(2, partials.size)
        assertTrue(partials.all { it.partial })
        assertEquals(setOf("m7"), ids(partials.first()))
        assertEquals(setOf("m7"), ids(r.result!!))
        assertFalse(r.result!!.partial)
        assertEquals(10, r.booksFetched)
        assertEquals(ScanProgress("Novig prices", 10, 10), progress.last())
    }

    @Test
    fun `a rescan reads last scan's +EV lines first, so they show within the first batch`() = runTest {
        val novig = Novig(edgeOn = setOf(9))
        val scanner = Scanner(novig, clock = { now })
        scanner.scan(settings, listOf(Fair()))
        assertEquals("m9", novig.order.last()) // latest game: read last the first time

        novig.calls.clear()
        now += 60_000
        val partials = ArrayList<ScanResult>()
        scanner.scan(settings, listOf(Fair()), onPartial = { partials += it })
        assertEquals("m9", novig.order.first())
        assertEquals(setOf("m9"), ids(partials.first()))
    }

    @Test
    fun `open bets are read before anything else`() = runTest {
        val novig = Novig(edgeOn = emptySet())
        Scanner(novig, clock = { now }).scan(settings, listOf(Fair()), pinned = setOf("m8"))
        assertEquals("m8", novig.order.first())
    }

    @Test
    fun `mid-scan, last scan's prices stay off the feed until they're read again`() = runTest {
        val novig = Novig(edgeOn = (0..9).toSet())
        val scanner = Scanner(novig, clock = { now })
        scanner.scan(settings, listOf(Fair()))
        now += 60_000
        val partials = ArrayList<ScanResult>()
        val r = scanner.scan(settings, listOf(Fair()), onPartial = { partials += it })

        val first = partials.first()
        // Games 8 and 9 aren't re-read yet: their old prices still price the Games tab…
        assertNotNull(first.opportunities.first { it.market.marketId == "m9" && it.outcome.outcomeId == "a9" }.quote)
        // …but only prices read this scan are offered as bets.
        assertEquals((0..7).map { "m$it" }.toSet(), ids(first))
        assertEquals((0..9).map { "m$it" }.toSet(), ids(r.result!!))
    }

    @Test
    fun `Novig's prices are read while a slow fair-odds source is still answering`() = runTest {
        val novig = Novig(edgeOn = setOf(3))
        val gate = CompletableDeferred<Unit>()
        val slow = Fair(id = "kalshi", gate = gate)
        val scan = async { Scanner(novig, clock = { now }).scan(settings, listOf(slow)) }
        runCurrent()
        // The board's games are being read already, before any fair price exists.
        assertEquals((0..9).map { "m$it" }, novig.order)
        assertFalse(scan.isCompleted)

        gate.complete(Unit)
        val r = scan.await()
        // Nothing read twice once the fair odds landed, and the edge is found.
        assertEquals(10, novig.order.size)
        assertEquals(setOf("m3"), ids(r.result!!))
    }

    @Test
    fun `a scan never reads more than the per-scan limit`() = runTest {
        val novig = Novig(edgeOn = emptySet())
        val r = Scanner(novig, clock = { now }).scan(settings.copy(maxBooksPerScan = 5), listOf(Fair()))
        assertEquals(5, novig.order.size)
        assertEquals(5, r.result!!.stats.marketsPriced)
    }

    // ---- ScanRunner: the scan outlives the screen --------------------------------------------------

    @Test
    fun `the runner scans in the app's scope, streams progress and partials, and refuses a second start`() = runTest {
        val novig = Novig(edgeOn = setOf(2))
        val runner = ScanRunner(Scanner(novig, clock = { now }), this)
        val seen = ArrayList<ScanReport?>()
        assertTrue(runner.start(settings, listOf(Fair())) { seen += it })
        assertTrue(runner.state.value.scanning)
        assertFalse(runner.start(settings, listOf(Fair())))

        advanceUntilIdle()
        val done = runner.state.value
        assertFalse(done.scanning)
        assertNull(done.progress)
        assertEquals(1, done.finished)
        assertFalse(done.result!!.partial)
        assertEquals(setOf("m2"), ids(done.result!!))
        assertEquals(listOf(done.report), seen)
        assertEquals(settings, done.settings)
        assertEquals(10, novig.order.size) // the refused second start fetched nothing
    }

    @Test
    fun `a scan that blows up keeps the last good result on screen and says why`() = runTest {
        val novig = Novig(edgeOn = setOf(2))
        val runner = ScanRunner(Scanner(novig, clock = { now }), this)
        runner.start(settings, listOf(Fair()))
        advanceUntilIdle()
        val good = runner.state.value.result!!

        val broken = object : ReferenceSource {
            override val id = "polymarket"
            override val displayName = "broken"
            override fun supports(league: League): Boolean = error("bad league table")
            override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = error("unused")
        }
        assertTrue(runner.start(settings, listOf(broken)))
        advanceUntilIdle()
        val after = runner.state.value
        assertEquals(2, after.finished)
        assertTrue(after.result === good)
        assertTrue(after.report!!.errors.single(), after.report!!.errors.single().startsWith("Scan failed"))
    }
}
