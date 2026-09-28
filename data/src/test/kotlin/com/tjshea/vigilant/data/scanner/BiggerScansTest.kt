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
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-09-28: "See if you can make the scans better or faster or find more bets. Also increase
 * the limits on the amount of novig prices per scan so I can select 500 600 700 800 up to 1200 …
 * right now the longest odds shown option stops at +300. Let me choose +200 +150 and +120 and get rid
 * of any option over +300."
 */
class BiggerScansTest {

    // ---- choices ---------------------------------------------------------------------------------

    @Test
    fun `Novig prices per scan go up to 1,200`() {
        val choices = ScanSettings.MAX_BOOKS_CHOICES
        assertTrue(choices.containsAll(listOf(500, 600, 700, 800, 900, 1000, 1100, 1200)))
        assertEquals(1200, choices.max())
        assertEquals(choices.sorted(), choices)
        // More props per game, so a bigger budget has lines to spend on.
        assertTrue(ScanSettings.PROPS_PER_GAME_CHOICES.containsAll(listOf(16, 24)))
    }

    @Test
    fun `longest odds offered are +120, +150, +200 and +300, nothing longer and no "Any"`() {
        assertEquals(listOf(120, 150, 200, 300), ScanSettings.MAX_ODDS_CHOICES)
        assertEquals(300, ScanSettings().maxOdds)
        // +120 keeps +110 (cost 0.476) and hides +130 (cost 0.435).
        val s = ScanSettings(maxOdds = 120)
        assertTrue(s.withinMaxOdds(100.0 / 210))
        assertTrue(s.withinMaxOdds(100.0 / 220)) // exactly +120
        assertFalse(s.withinMaxOdds(100.0 / 230))
        // Favorites are never capped.
        assertTrue(s.withinMaxOdds(0.8))
    }

    @Test
    fun `a saved longer cap, or none, becomes +300 once; +300 and shorter stay`() {
        for (old in listOf(500, 1000, 2000, 0)) assertEquals("from $old", 300, ScanSettings(maxOdds = old, schema = 5).migrate().maxOdds)
        for (kept in listOf(120, 150, 200, 300)) assertEquals(kept, ScanSettings(maxOdds = kept, schema = 5).migrate().maxOdds)
        val upgraded = ScanSettings(maxOdds = 1000, schema = 5).migrate()
        assertEquals(6, upgraded.schema)
        // A current file is left alone (the cap isn't re-checked on every launch).
        assertEquals(upgraded, upgraded.migrate())
    }

    // ---- a 1,200-price scan ------------------------------------------------------------------------

    private var now = Fixtures.START_MS - 86_400_000L

    /**
     * [n] MLB games, game i starting i hours after the first (MLB's 6-hour matching window keeps
     * each game's candidates to its neighbours, so 1,300 games match in a blink).
     */
    private inner class Board(n: Int) {
        val events = (0 until n).map { i -> NovigEvent("e$i", "BASEBALL", "MLB", "OPEN_PREGAME", "Away $i @ Home $i", Fixtures.START_MS + i * 3_600_000L) }
        val markets = (0 until n).map { i ->
            NovigMarket("m$i", "e$i", "MONEY", "OPEN", "ML", Fixtures.START_MS + i * 3_600_000L, MarketFee.GAME, listOf(NovigOutcome("a$i", "Away $i", "TBD"), NovigOutcome("h$i", "Home $i", "TBD")))
        }
    }

    /**
     * Novig for [board]: every game 50/50 fair; the away side can be taken at 0.45 (+11%) on games in
     * [edgeOn], else 0.52. [stepMs] passes on the clock with every batch read.
     */
    private inner class Novig(private val board: Board, val edgeOn: MutableSet<Int> = mutableSetOf(), private val stepMs: Long = 0) : NovigSource {
        val calls = ArrayList<List<String>>()
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = board.events
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = board.markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            calls += marketIds.toList()
            val books = marketIds.associateWith { id ->
                val i = id.drop(1).toInt()
                val (awayBid, homeBid) = if (i in edgeOn) 440 to 550 else 480 to 480
                NovigBook(id, 1, mapOf("a$i" to listOf(BidLevel(awayBid, 1000)), "h$i" to listOf(BidLevel(homeBid, 1000))), now)
            }
            now += stepMs
            return BookBatch(books, 0, books.size, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
    }

    private inner class Fair(private val board: Board) : ReferenceSource {
        override val id = "polymarket"
        override val displayName = id
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = RefSnapshot(
            league.oddsApiSportKey,
            board.events.mapIndexed { i, e ->
                RefEvent(
                    "r$i", league.oddsApiSportKey, e.startsTs, home = "Home $i", away = "Away $i",
                    markets = listOf(RefBookMarket(id, id, LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, 2.0, null), RefQuote(Side.HOME, 2.0, null)), now)),
                )
            },
            now,
        )
    }

    private val settings = ScanSettings(leagues = setOf("MLB"), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, minEvPercent = 0.01, daysAhead = 60)

    @Test
    fun `a scan set to 1,200 reads 1,200 prices, never more`() = runTest {
        val board = Board(1300)
        val novig = Novig(board)
        val r = Scanner(novig, clock = { now }).scan(settings.copy(maxBooksPerScan = 1200), listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(1200, novig.calls.sumOf { it.size })
        assertEquals(1200, novig.calls.flatten().toSet().size)
        assertEquals(1200, r.booksFetched)
        // Past the budget, the soonest games are the ones read.
        assertEquals((0 until 1200).map { "m$it" }.toSet(), novig.calls.flatten().toSet())
    }

    @Test
    fun `the budget cuts a 1,500-line plan to 1,200, main lines and soonest games first`() {
        val board = Board(1500)
        val plan = Planner.plan(board.events, board.markets, listOf(kotlinx.coroutines.runBlocking { Fair(board).odds(Leagues.byNovigName("MLB")!!, settings) }), settings.copy(maxBooksPerScan = 1200), now)
        assertEquals(1200, plan.markets.size)
    }

    // ---- faster: each line devigged once per plan --------------------------------------------------

    @Test
    fun `re-pricing the same plan re-uses its fair lines, and a new plan or method prices afresh`() = runTest {
        val board = Board(3)
        val refs = listOf(Fair(board).odds(Leagues.byNovigName("MLB")!!, settings))
        val plan = Planner.plan(board.events, board.markets, refs, settings, now)
        val books = mapOf("m0" to NovigBook("m0", 1, mapOf("a0" to listOf(BidLevel(480, 10))), now))
        val memo = FairMemo()
        val first = Pricing.price(plan, books, settings, now, memo).opportunities.first().fair
        val again = Pricing.price(plan, emptyMap(), settings, now, memo).opportunities.first().fair
        assertSame("the same plan's fair line is worked out once", first, again)
        val otherMethod = Pricing.price(plan, books, settings.copy(devigMethod = DevigMethod.SHIN), now, memo).opportunities.first().fair
        assertNotSame(first, otherMethod)
        val newPlan = Planner.plan(board.events, board.markets, refs, settings, now)
        assertNotSame(first, Pricing.price(newPlan, books, settings, now, memo).opportunities.first().fair)
        // Without a memo, as before: nothing shared.
        assertNotSame(Pricing.price(plan, books, settings, now).opportunities.first().fair, Pricing.price(plan, books, settings, now).opportunities.first().fair)
    }

    // ---- better: a long scan's first edges are read again at the end ---------------------------------

    @Test
    fun `an edge read over a minute before the scan ends is read again, and one that vanished isn't offered`() = runTest {
        val board = Board(10)
        // Game 1's edge is in the first batch (read at t), game 9's in the second (t + 45 s); the scan ends at t + 90 s.
        val novig = Novig(board, edgeOn = mutableSetOf(1, 9), stepMs = 45_000)
        val scanner = Scanner(novig, clock = { now })
        var report = scanner.scan(settings, listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(listOf(Scanner.CHUNK, 2, 1), novig.calls.map { it.size })
        assertEquals(listOf("m1"), novig.calls.last())
        assertEquals(1, report.booksReread)
        assertEquals(setOf("m1", "m9"), report.result!!.feed(settings).map { it.market.marketId }.toSet())
        // The re-read price is the one priced.
        assertEquals(now - 45_000, report.result!!.feed(settings).first { it.market.marketId == "m1" }.bookFetchedAtMs)

        // Next scan, game 1's edge is gone by the time it's read again: not offered.
        now += 10 * 60_000L
        novig.calls.clear()
        val gone = object : NovigSource by novig {
            override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
                if (novig.calls.isNotEmpty() && "m1" in marketIds) novig.edgeOn -= 1
                return novig.books(marketIds, onProgress)
            }
        }
        report = Scanner(gone, clock = { now }).scan(settings, listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(1, report.booksReread)
        assertEquals(setOf("m9"), report.result!!.feed(settings).map { it.market.marketId }.toSet())
    }

    @Test
    fun `a quick scan reads nothing twice`() = runTest {
        val board = Board(10)
        val novig = Novig(board, edgeOn = mutableSetOf(1, 9), stepMs = 1_000)
        val report = Scanner(novig, clock = { now }).scan(settings, listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(10, novig.calls.sumOf { it.size })
        assertEquals(0, report.booksReread)
    }
}
