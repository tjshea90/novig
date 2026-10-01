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
    fun `Novig prices per scan go up to 2,000 (the live feed's watch), then No limit`() {
        val choices = ScanSettings.MAX_BOOKS_CHOICES
        assertTrue(choices.containsAll(listOf(500, 600, 700, 800, 900, 1000, 1100, 1200, 1500, 2000)))
        // Tj, 2026-09-28 (v0.19.4): "If I can have no limit on the prices safely" (not then, RESEARCH.md §31); then (v0.19.6)
        // "Add unlimited options … unlimited novig prices per scan": No limit, bounded by the time window (RESEARCH.md §32).
        assertEquals(ScanSettings.NO_LIMIT, choices.last())
        assertEquals(2000, choices.dropLast(1).max())
        assertTrue(choices.dropLast(1).max() <= com.tjshea.vigilant.data.novig.stream.NovigStream.MAX_MARKETS)
        assertEquals(choices.sorted(), choices)
        // More lines and props per game ("consider if I can safely raise the max alternate lines and player props per game").
        assertTrue(ScanSettings.PROPS_PER_GAME_CHOICES.containsAll(listOf(16, 24, 32, 48, ScanSettings.NO_LIMIT)))
        assertTrue(ScanSettings.LINES_PER_GAME_CHOICES.containsAll(listOf(5, 8, 10, ScanSettings.NO_LIMIT)))
        assertEquals(listOf(0, 12, 24, 48, 96, 192, ScanSettings.NO_LIMIT), ScanSettings.BOOK_PROP_CREDIT_CHOICES)
        assertEquals(ScanSettings.NO_LIMIT, ScanSettings.BOOK_PROP_HOURS_CHOICES.last())
        assertEquals(listOf(12, 24, 48, ScanSettings.NO_LIMIT), ScanSettings.PROPLINE_GAMES_CHOICES)
    }

    // ---- No limit (Tj, 2026-09-28: "unlimited novig prices per scan … make sure the app doesn't just scan continuously,
    // it should stop the scan when all the markets are finished scanning for the selected time period") -----------------

    @Test
    fun `with no limit a scan reads every priced line in the time window once, then stops`() = runTest {
        val board = Board(400) // a game an hour, the first a day off
        val novig = Novig(board)
        val week = settings.copy(maxBooksPerScan = ScanSettings.NO_LIMIT, daysAhead = 7)
        val inWindow = board.events.filter { it.startsTs <= now + 7 * 86_400_000L }.map { it.eventId.replace("e", "m") }.toSet()
        assertEquals(145, inWindow.size)
        val report = Scanner(novig, clock = { now }).scan(week, listOf(Fair(board)), onProgress = {}, onPartial = {})
        val read = novig.calls.flatten()
        assertEquals(inWindow, read.toSet()) // every line in the window, nothing past it
        assertEquals(read.size, read.toSet().size) // each once
        assertEquals(145, report.booksFetched)
        assertEquals(400 - 145, report.result!!.stats.laterGames)
        // The same scan with a number reads that many.
        val capped = Novig(board)
        Scanner(capped, clock = { now }).scan(week.copy(maxBooksPerScan = 100), listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(100, capped.calls.sumOf { it.size })
    }

    @Test
    fun `a scan reads only the games in Starts within when it's shorter than Days ahead`() = runTest {
        now = Fixtures.START_MS - 3_600_000L // the first game an hour off
        val board = Board(100)
        val novig = Novig(board)
        val s = settings.copy(maxBooksPerScan = ScanSettings.NO_LIMIT, daysAhead = 7, startsWithinHours = 12)
        assertEquals(12, s.scanWindowHours)
        val report = Scanner(novig, clock = { now }).scan(s, listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals((0..11).map { "m$it" }.toSet(), novig.calls.flatten().toSet())
        assertEquals(100 - 12, report.result!!.stats.laterGames)
        // Longer than Days ahead (or Any time): Days ahead decides.
        assertEquals(24, s.copy(daysAhead = 1, startsWithinHours = 48).scanWindowHours)
        assertEquals(7 * 24, s.copy(startsWithinHours = 0).scanWindowHours)
    }

    /** A no-limit scan that ran out of time (its odds would be too old) reads what it left first next time. */
    @Test
    fun `lines a long scan left too late are read first by the next scan`() = runTest {
        val board = Board(40, every = 0L)
        now = Fixtures.START_MS - 30 * 60_000L // 5 minutes' odds: reads stop 3 minutes in
        val novig = Novig(board, stepMs = 60_000L)
        val scanner = Scanner(novig, clock = { now })
        val first = scanner.scan(settings.copy(maxBooksPerScan = ScanSettings.NO_LIMIT), listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(8, first.booksTooLate)
        val left = (board.markets.map { it.marketId }.toSet() - novig.calls.flatten().toSet())
        assertEquals(8, left.size)
        novig.calls.clear()
        scanner.scan(settings.copy(maxBooksPerScan = ScanSettings.NO_LIMIT), listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(left, novig.calls.first().toSet())
    }

    /**
     * A long scan (a big budget on public routes) must not show bets already on their way out: once it has run past the time
     * a line's other-book odds (read as it began) could stay listed 2 minutes, the line is left for the next scan.
     */
    @Test
    fun `a scan running long leaves lines whose odds would be too old, and says how many`() = runTest {
        val board = Board(40, every = 0L) // 40 games, all starting at START
        now = Fixtures.START_MS - 30 * 60_000L // half an hour off: 5 minutes' odds, so reads stop 3 minutes in
        val novig = Novig(board, stepMs = 60_000L) // each batch of 8 takes a minute
        val report = Scanner(novig, clock = { now }).scan(settings.copy(maxBooksPerScan = 1200), listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(listOf(8, 8, 8, 8), novig.calls.map { it.size }) // batches at 0, 1, 2 and 3 minutes; none at 4
        assertEquals(8, report.booksTooLate)
        assertEquals(8, report.timing!!.leftTooLate)
    }

    @Test
    fun `the same scan on games a day off reads them all (their odds last 10 minutes)`() = runTest {
        val board = Board(40, every = 0L)
        now = Fixtures.START_MS - 86_400_000L
        val novig = Novig(board, stepMs = 60_000L)
        val report = Scanner(novig, clock = { now }).scan(settings.copy(maxBooksPerScan = 1200), listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(40, novig.calls.sumOf { it.size })
        assertEquals(0, report.booksTooLate)
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
    fun `a saved longer cap, or none, becomes +300 once, and +300 or shorter stays`() {
        for (old in listOf(500, 1000, 2000, 0)) assertEquals("from $old", 300, ScanSettings(maxOdds = old, schema = 5).migrate().maxOdds)
        for (kept in listOf(120, 150, 200, 300)) assertEquals(kept, ScanSettings(maxOdds = kept, schema = 5).migrate().maxOdds)
        val upgraded = ScanSettings(maxOdds = 1000, schema = 5).migrate()
        assertTrue(upgraded.schema >= 6)
        // A current file is left alone (the cap isn't re-checked on every launch).
        assertEquals(upgraded, upgraded.migrate())
    }

    // ---- a 1,200-price scan ------------------------------------------------------------------------

    private var now = Fixtures.START_MS - 86_400_000L

    /**
     * [n] MLB games, game i starting i hours after the first (MLB's 6-hour matching window keeps
     * each game's candidates to its neighbours, so 1,300 games match in a blink).
     */
    private inner class Board(n: Int, every: Long = 3_600_000L) {
        val events = (0 until n).map { i -> NovigEvent("e$i", "BASEBALL", "MLB", "OPEN_PREGAME", "Away $i @ Home $i", Fixtures.START_MS + i * every) }
        val markets = (0 until n).map { i ->
            NovigMarket("m$i", "e$i", "MONEY", "OPEN", "ML", Fixtures.START_MS + i * every, MarketFee.GAME, listOf(NovigOutcome("a$i", "Away $i", "TBD"), NovigOutcome("h$i", "Home $i", "TBD")))
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

    fun probeScan(games: Int, cap: Int) = kotlinx.coroutines.runBlocking {
        val board = Board(games)
        val novig = Novig(board)
        var partials = 0
        val t0 = System.nanoTime()
        Scanner(novig, clock = { now }).scan(settings.copy(maxBooksPerScan = cap), listOf(Fair(board)), onProgress = {}, onPartial = { partials++ })
        println("PROBE games=$games cap=$cap: ${(System.nanoTime() - t0) / 1_000_000} ms, ${novig.calls.size} batches, $partials partials")
    }

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

    // ---- the Novig key's websocket (Tj, 2026-09-28: "taking full advantage of the novig API key") -----

    /**
     * Novig with a key: the scan's plan goes to the websocket, whose snapshot lands after [restChunks] REST
     * batches (a fresh socket waits ~8 s for a full throttle bucket). Books pushed are [novig]'s own prices.
     */
    private inner class Pushing(private val novig: Novig, private val restChunks: Int) : NovigSource by novig {
        val watched = ArrayList<List<String>>()
        override fun watch(marketIds: Collection<String>) { watched += marketIds.toList() }
        override fun pushed(marketIds: Collection<String>): Map<String, NovigBook> {
            if (novig.calls.size < restChunks) return emptyMap()
            val held = watched.lastOrNull().orEmpty().toSet()
            val ids = marketIds.filter { it in held }
            return ids.associateWith { id ->
                val i = id.drop(1).toInt()
                val (awayBid, homeBid) = if (i in novig.edgeOn) 440 to 550 else 480 to 480
                NovigBook(id, 1, mapOf("a$i" to listOf(BidLevel(awayBid, 1000)), "h$i" to listOf(BidLevel(homeBid, 1000))), now)
            }
        }
    }

    @Test
    fun `with a key, the scan's plan goes to the websocket and its books are read at once, no request each`() = runTest {
        val board = Board(1200)
        val novig = Novig(board, edgeOn = mutableSetOf(5, 700))
        val pushing = Pushing(novig, restChunks = 2)
        val partials = ArrayList<ScanResult>()
        val r = Scanner(pushing, clock = { now }).scan(settings.copy(maxBooksPerScan = 1200), listOf(Fair(board)), onProgress = {}, onPartial = { partials += it })
        // Two REST batches while the socket warms up, then everything else in one pass.
        assertEquals(2, novig.calls.size)
        assertEquals(2 * Scanner.CHUNK, novig.calls.sumOf { it.size })
        assertEquals(1200, r.booksFetched)
        assertEquals(1200 - 2 * Scanner.CHUNK, r.booksViaPush)
        // The whole plan (up to the budget) was handed to the websocket, likeliest first.
        assertEquals(1200, pushing.watched.last().size)
        assertEquals(setOf("m5", "m700"), r.result!!.feed(settings).map { it.market.marketId }.toSet())
        // One publish per REST batch plus one for the pushed books: not 150 small ones.
        assertTrue("${partials.size} partial results", partials.size <= 4)
    }

    @Test
    fun `a scan asks Novig for as many books at a time as the source reads at once`() = runTest {
        val board = Board(100)
        val novig = Novig(board)
        val wide = object : NovigSource by novig {
            override fun batchSize() = 30
        }
        Scanner(wide, clock = { now }).scan(settings.copy(maxBooksPerScan = 100), listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(listOf(30, 30, 30, 10), novig.calls.map { it.size })
    }

    @Test
    fun `without the websocket, the same scan reads every book by request as before`() = runTest {
        val board = Board(40)
        val novig = Novig(board)
        val r = Scanner(novig, clock = { now }).scan(settings, listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(40, novig.calls.sumOf { it.size })
        assertEquals(0, r.booksViaPush)
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

    // ---- lighter: a partial result prices only the books that changed (Tj's Diagnostics 2026-10-01: OutOfMemoryError) ----

    private suspend fun reusePlan(n: Int = 4): Triple<Plan, Map<String, NovigBook>, Board> {
        val board = Board(n)
        val refs = listOf(Fair(board).odds(Leagues.byNovigName("MLB")!!, settings))
        val plan = Planner.plan(board.events, board.markets, refs, settings, now)
        val books = (0 until n).associate { i ->
            "m$i" to NovigBook("m$i", 1, mapOf("a$i" to listOf(BidLevel(480, 10)), "h$i" to listOf(BidLevel(480, 10))), now)
        }
        return Triple(plan, books, board)
    }

    /** The whole result as numbers, to tell a re-used market from a freshly priced one by its content as well as its identity. */
    private fun ScanResult.digest() = opportunities.map { listOf(it.key, it.evPercent, it.suggestedStake, it.fairProbability, it.ladder, it.bookFetchedAtMs, it.novigWidth, it.bestBid) }

    @Test
    fun `a market whose book hasn't changed is re-used as the very same outcomes, and one that has is priced again`() = runTest {
        val (plan, books, _) = reusePlan()
        val memo = FairMemo()
        val first = Pricing.price(plan, books, settings, now, memo)
        assertEquals(8, first.opportunities.size)
        assertEquals(4, memo.pricedCount())
        // Only m1's book is read again (a new object, a better price): the other three markets' outcomes are the same objects.
        val moved = books + ("m1" to NovigBook("m1", 2, mapOf("a1" to listOf(BidLevel(440, 10)), "h1" to listOf(BidLevel(550, 10))), now + 1_000))
        val second = Pricing.price(plan, moved, settings, now + 1_000, memo)
        for (o in second.opportunities) {
            val before = first.opportunities.first { it.key == o.key }
            if (o.market.marketId == "m1") assertNotSame("m1 was read again: ${o.key}", before, o) else assertSame("${o.key} didn't change", before, o)
        }
        assertTrue("the moved market's edge is the new price's", second.opportunities.first { it.key == "m1/a1" }.evPercent!! > first.opportunities.first { it.key == "m1/a1" }.evPercent!!)
    }

    @Test
    fun `the result with re-use is exactly the result without it, however the books change between passes`() = runTest {
        val (plan, books, _) = reusePlan(6)
        val memo = FairMemo()
        Pricing.price(plan, books, settings, now, memo) // warm
        val steps = listOf(
            books,
            books + ("m2" to NovigBook("m2", 2, mapOf("a2" to listOf(BidLevel(430, 50)), "h2" to listOf(BidLevel(560, 50))), now + 5_000)),
            books - "m0" - "m3", // books dropped
            books + ("m4" to NovigBook("m4", 3, mapOf("a4" to listOf(BidLevel(450, 7))), now + 9_000)), // a book with only one side
            emptyMap(),
        )
        for ((i, step) in steps.withIndex()) {
            val with = Pricing.price(plan, step, settings, now + i, memo)
            val without = Pricing.price(plan, step, settings, now + i)
            assertEquals("step $i", without.digest(), with.digest())
            assertEquals("step $i stats", without.stats, with.stats)
            assertEquals("step $i games", without.games.map { g -> g.outcomes.map { it.key } }, with.games.map { g -> g.outcomes.map { it.key } })
        }
    }

    @Test
    fun `a new bankroll or Kelly multiplier re-works every stake, and a different fair method every line`() = runTest {
        val (plan, _, _) = reusePlan(2)
        // Deep enough that a stake is Kelly's, not capped by what's for sale.
        val edge = mapOf("m0" to NovigBook("m0", 1, mapOf("a0" to listOf(BidLevel(440, 1_000_000)), "h0" to listOf(BidLevel(550, 1_000_000))), now))
        val memo = FairMemo()
        val base = Pricing.price(plan, edge, settings.copy(bankroll = 1000.0, kellyMultiplier = 0.25), now, memo).opportunities.first { it.key == "m0/a0" }
        assertTrue("there is a stake to change", (base.suggestedStake ?: 0.0) > 0.0)
        val richer = Pricing.price(plan, edge, settings.copy(bankroll = 2000.0, kellyMultiplier = 0.25), now, memo).opportunities.first { it.key == "m0/a0" }
        assertTrue("a bigger bankroll stakes more, not the old stake", richer.suggestedStake!! > base.suggestedStake!!)
        val bolder = Pricing.price(plan, edge, settings.copy(bankroll = 2000.0, kellyMultiplier = 0.5), now, memo).opportunities.first { it.key == "m0/a0" }
        assertTrue("a bigger Kelly fraction stakes more", bolder.suggestedStake!! > richer.suggestedStake!!)
        val shin = Pricing.price(plan, edge, settings.copy(devigMethod = DevigMethod.SHIN), now, memo).opportunities.first { it.key == "m0/a0" }
        assertNotSame("another fair method is not served from the first's outcomes", base.fair, shin.fair)
    }

    @Test
    fun `only the last plan's outcomes are kept, so a second plan never doubles the memory`() = runTest {
        val (plan, books, board) = reusePlan(3)
        val memo = FairMemo()
        Pricing.price(plan, books, settings, now, memo)
        assertEquals(3, memo.pricedCount())
        val other = Planner.plan(board.events, board.markets, listOf(Fair(board).odds(Leagues.byNovigName("MLB")!!, settings)), settings, now)
        val priced = Pricing.price(other, books, settings, now, memo)
        assertEquals("the new plan's, not both", 3, memo.pricedCount())
        // Back to the first plan: priced afresh (its outcomes were let go), and right.
        assertEquals(priced.digest(), Pricing.price(plan, books, settings, now, memo).digest())
    }

    @Test
    fun `without a memo nothing is kept or shared`() = runTest {
        val (plan, books, _) = reusePlan(2)
        val a = Pricing.price(plan, books, settings, now).opportunities
        val b = Pricing.price(plan, books, settings, now).opportunities
        assertEquals(a.size, b.size)
        for (i in a.indices) assertNotSame(a[i], b[i])
    }

    @Test
    fun `a scan's partial results share the outcomes of the markets a batch didn't touch`() = runTest {
        val board = Board(90)
        val novig = Novig(board)
        val partials = ArrayList<ScanResult>()
        val big = settings.copy(maxBooksPerScan = ScanSettings.NO_LIMIT, daysAhead = 60)
        // Every partial: the scanner publishes at most every 2 s on a big plan, so ask for them all with a small plan threshold.
        val scanner = Scanner(novig, clock = { now }, bigPlanMarkets = Int.MAX_VALUE)
        scanner.scan(big, listOf(Fair(board)), onProgress = {}, onPartial = { partials += it })
        assertTrue("a scan this size publishes several partials", partials.size >= 3)
        val shared = partials.zipWithNext().sumOf { (a, b) ->
            val earlier = a.opportunities.associateBy { it.key }
            b.opportunities.count { earlier[it.key] === it }
        }
        assertTrue("later partials re-use the earlier ones' outcomes ($shared shared)", shared > 0)
    }

    @Test
    fun `a scan lets go of the boards of leagues no longer picked`() = runTest {
        val board = Board(6)
        val scanner = Scanner(Novig(board), clock = { now })
        val two = settings.copy(leagues = setOf("MLB", "NHL"))
        scanner.scan(two, listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals("a board per league", 2, scanner.holdings().snapshots)
        scanner.scan(settings, listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals("NHL was turned off: its board is let go", 1, scanner.holdings().snapshots)
        // Turned back on, it's read again, not served from a board of unknown age.
        scanner.scan(two, listOf(Fair(board)), onProgress = {}, onPartial = {})
        assertEquals(2, scanner.holdings().snapshots)
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
