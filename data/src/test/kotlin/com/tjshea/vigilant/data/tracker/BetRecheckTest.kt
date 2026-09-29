package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Tj, 2026-09-27: "scan for up to date average odds … for each of the bets I made … 'now +3% ev'". */
class BetRecheckTest {

    @get:Rule val tmp = TemporaryFolder()
    private val start = 1_790_528_400_000L
    private var now = start - 2 * 60 * 60_000L

    private fun bet(id: String, gameUrl: String? = "https://crazyninjaodds.com/game?side_id=$id", startsTs: Long = start) = TrackedBet(
        id = id, createdAtMs = now, league = "NFL", eventName = "Seattle Seahawks @ Washington Commanders", startsTs = startsTs,
        marketLabel = "Moneyline", selection = "SEA", marketId = "", outcomeId = "", price = 0.5, cost = 0.5,
        fairAtBet = 0.52, evPercentAtBet = 0.04, stake = 1.0, source = BetTracker.SOURCE_CNO, american = 100, gameUrl = gameUrl,
    )

    private fun tracker(vararg bets: TrackedBet): BetTracker {
        File(tmp.root, "bets.json").writeText(Json.encodeToString(ListSerializer(TrackedBet.serializer()), bets.toList()))
        return BetTracker(File(tmp.root, "bets.json"), clock = { now })
    }

    /** Three books pricing both sides at [odds] / [other]. */
    private fun view(odds: Int, other: Int) = CnoBooksView(
        "SEA", "WSH", null, false,
        listOf("PN", "DK", "FD").map { CnoBookPrice(it, odds, null, other, null) } + CnoBookPrice("NV", 100, null, -104, null),
        now,
    )

    @Test
    fun `each open CNO bet gets the books' fair odds now, as EV at what it cost, and the close so far`() = runTest {
        val t = tracker(bet("up"), bet("down"))
        val seen = mutableListOf<CnoRow>()
        val r = BetRecheck(t, books = { row -> seen += row; if (row.bet == "SEA" && row.gameUrl!!.endsWith("up")) view(-125, 105) else view(115, -135) }, clock = { now }).run()
        assertEquals(2, r.updated)
        val byId = t.all().associateBy { it.id }
        val up = byId.getValue("up")
        assertEquals(true, up.nowEv!! > 0.0)
        assertEquals(up.nowFair!! / 0.5 - 1.0, up.nowEv!!, 1e-12)
        assertEquals(3, up.nowBooks)
        assertEquals(up.nowFair, up.closingFair) // before the start: the close so far (CLV)
        assertEquals(true, byId.getValue("down").nowEv!! < 0.0)
        // The row asked for is the one CNO's list keyed: its game page, at its book.
        assertEquals("https://crazyninjaodds.com/game?side_id=up|Novig", seen.first { it.gameUrl!!.endsWith("up") }.key)
    }

    @Test
    fun `settled bets, bets without a CNO game page, long-over games and unreadable pages are left as they were`() = runTest {
        val t = tracker(
            bet("vig", gameUrl = null),
            bet("old", startsTs = now - BetRecheck.STALE_AFTER_START_MS),
            bet("fail"),
            bet("won").copy(status = BetStatus.WON),
        )
        var asked = 0
        val r = BetRecheck(t, books = { asked++; null }, clock = { now }).run()
        assertEquals(1, asked)
        assertEquals(0, r.updated)
        t.all().forEach { assertNull(it.id, it.nowEv) }
    }

    @Test
    fun `after the start the recheck is the EV now, not the closing line`() = runTest {
        val t = tracker(bet("live"))
        now = start + 30 * 60_000L
        BetRecheck(t, books = { view(-125, 105) }, clock = { now }).run()
        val b = t.all().single()
        assertEquals(true, b.nowEv != null)
        assertNull(b.closingFair)
    }

    // ---- Tj, 2026-09-29: "it says it check 40 out of 40 open bets, but I have 101 open bets" -----------

    @Test
    fun `every open bet is checked, not the first 40, and the count runs to the end`() = runTest {
        val bets = (1..101).map { bet("b$it", startsTs = start + it * 60_000L) }
        val t = tracker(*bets.toTypedArray())
        val progress = mutableListOf<Pair<Int, Int>>()
        val r = BetRecheck(t, books = { view(-125, 105) }, clock = { now }).run { done, total -> progress += done to total }
        assertEquals(101, r.open)
        assertEquals(101, r.checked)
        assertEquals(101, r.updated)
        assertEquals(101, t.all().count { it.nowEv != null })
        assertEquals(0 to 101, progress.first())
        assertEquals(101 to 101, progress.last())
        assertEquals("Checked 101 of 101 open bets", r.summary())
    }

    @Test
    fun `the report accounts for every open bet, so what Tj is told adds up to what he has`() = runTest {
        val t = tracker(
            bet("read"), bet("read2"),
            bet("fresh").copy(nowAtMs = now - 10_000L, nowEv = 0.01, nowFair = 0.51),
            bet("gone"),
            bet("vigilant", gameUrl = null), bet("vigilant2", gameUrl = null),
            bet("over", startsTs = now - BetRecheck.STALE_AFTER_START_MS - 1),
            bet("won").copy(status = BetStatus.WON),
        )
        val r = BetRecheck(t, books = { row -> if (row.gameUrl!!.endsWith("gone")) null else view(-125, 105) }, clock = { now }).run()
        assertEquals(7, r.open) // the won bet isn't open
        assertEquals(2, r.updated)
        assertEquals(1, r.current)
        assertEquals(1, r.failed)
        assertEquals(2, r.vigilantOnly)
        assertEquals(1, r.over)
        assertEquals(0, r.skipped)
        assertEquals(r.open, r.updated + r.failed + r.skipped + r.current + r.over + r.vigilantOnly)
        assertEquals(
            "Checked 3 of 7 open bets · 1 couldn't be read · 1 game already over (results come from final scores) · 2 Vigilant bets update with each Vigilant scan",
            r.summary(),
        )
        assertEquals(true, r.summary(vigilantOff = true).contains("2 Vigilant bets not updated: the Vigilant scanner is off (Settings › Scanner)"))
        // With the grading pass that runs beside it, the finished game says what came of it (Tj: "it only updated 61, I have 100").
        assertEquals(
            "Checked 3 of 7 open bets · 1 couldn't be read · 1 game already over: graded 1 from final scores · 2 Vigilant bets update with each Vigilant scan",
            r.summary(graded = BetSettler.Report(asked = 1, settled = 1, stopped = false)),
        )
        assertEquals(
            true,
            r.summary(graded = BetSettler.Report(asked = 3, settled = 1, stopped = false, waiting = 1, manual = 1))
                .contains("1 game already over: graded 1 from final scores, 1 not over yet, 1 needs a tap (each says why)"),
        )
        assertEquals(true, r.summary(graded = BetSettler.Report(asked = 1, settled = 0, stopped = true)).contains("1 game already over: the score feeds didn't answer"))
        // Bets a few hours into their game (not "over" yet) that got graded are still told.
        val soon = BetRecheck.Report(open = 5, checked = 5, updated = 5)
        assertEquals("Checked 5 of 5 open bets · Graded 2 from final scores", soon.summary(graded = BetSettler.Report(asked = 2, settled = 2, stopped = false)))
    }

    /** Tj, 2026-09-29: "update the EV for every single open bet, including bets added from vigilant scanner". */
    @Test
    fun `Vigilant's own bets are planned for the pricing pass, and the bets CNO didn't cover are handed to it`() = runTest {
        val t = tracker(
            bet("cno1"), bet("cno2"), bet("gone"),
            bet("vig1", gameUrl = null).copy(marketId = "m1", outcomeId = "o1"),
            bet("vig2", gameUrl = null).copy(marketId = "m2", outcomeId = "o2", nowAtMs = now - 20_000L, nowEv = 0.02, nowFair = 0.52), // read 20 s ago
            bet("vigLive", gameUrl = null, startsTs = now - 30 * 60_000L),
        )
        val re = BetRecheck(t, books = { row -> if (row.gameUrl!!.endsWith("gone")) null else view(-125, 105) }, clock = { now })
        val plan = re.plan(t.all())
        assertEquals(listOf("vig1"), plan.vigilantBets.map { it.id })
        assertEquals(1, plan.vigilantOnly)
        assertEquals(1, plan.current) // vig2: read inside the last minute
        assertEquals(1, plan.started) // vigLive: in progress and no CNO page: nothing to price
        val r = re.run()
        assertEquals(6, r.open)
        assertEquals(listOf("gone"), r.unreadIds)
        assertEquals(r.open, r.updated + r.failed + r.skipped + r.current + r.over + r.vigilantOnly + r.started + r.priced + r.unpriced)

        // The pricing pass over the Vigilant bets: those bets are priced (or explained), so they leave "vigilantOnly".
        val afterVigilant = r.withPricing(OpenBetPricer.Report(asked = 1, priced = 1, unpriced = 0), rescue = false)
        assertEquals(0, afterVigilant.vigilantOnly)
        assertEquals(1, afterVigilant.priced)
        // ...and the bet CNO couldn't read is taken over: no longer "couldn't be read", it is priced or explained.
        val afterRescue = afterVigilant.withPricing(OpenBetPricer.Report(asked = 1, priced = 0, unpriced = 1), rescue = true)
        assertEquals(0, afterRescue.failed)
        assertEquals(1, afterRescue.unpriced)
        assertEquals(afterRescue.open, afterRescue.updated + afterRescue.failed + afterRescue.skipped + afterRescue.current + afterRescue.over + afterRescue.vigilantOnly + afterRescue.started + afterRescue.priced + afterRescue.unpriced)
        assertEquals(
            "Checked 5 of 6 open bets · 1 couldn't be priced (each bet says why) · 1 game in progress (results come from final scores)",
            afterRescue.summary(),
        )
    }

    @Test
    fun `each bet keeps every book's price, the other side and the price now, and the file is written in batches`() = runTest {
        val t = tracker(bet("up"))
        BetRecheck(t, books = { CnoBooksView("SEA", "WSH", null, false, listOf(CnoBookPrice("PN", -125, null, 105, null), CnoBookPrice("DK", -120, null, 100, null), CnoBookPrice("FD", -130, null, 110, null), CnoBookPrice("NV", 102, null, -106, null)), now) }, clock = { now }).run()
        val b = t.all().single()
        assertEquals(listOf("Pinnacle", "DraftKings", "FanDuel", "Novig"), b.books.map { it.name })
        assertEquals(BookLine("Pinnacle", -125, 105), b.books.first())
        assertEquals("WSH", b.otherSide)
        assertEquals(102, b.nowAmerican)
        assertEquals(now, b.booksAtMs)
        // Settling drops the snapshot: only open bets show it.
        t.settle("up", BetStatus.WON)
        assertEquals(emptyList<BookLine>(), t.all().single().books)
    }

    @Test
    fun `a run stops, keeping what it read, when CNO asks for a pause or five reads in a row fail`() = runTest {
        val bets = (1..10).map { bet("p$it", startsTs = start + it) }
        var pausedNow = false
        val t = tracker(*bets.toTypedArray())
        var reads = 0
        val r = BetRecheck(t, books = { reads++; if (reads == 3) pausedNow = true; view(-125, 105) }, clock = { now }, paused = { pausedNow }).run()
        assertEquals(true, r.stopped)
        assertEquals(3, r.updated)
        assertEquals(7, r.skipped)
        assertEquals(3, t.all().count { it.nowEv != null })
        assertEquals(true, r.summary().contains("stopped early"))

        val t2 = tracker(*bets.toTypedArray())
        var asked = 0
        val down = BetRecheck(t2, books = { asked++; null }, clock = { now }).run()
        assertEquals(BetRecheck.MAX_FAILS_IN_ROW, asked)
        assertEquals(true, down.stopped)
        assertEquals(5, down.skipped)
        assertEquals(true, down.summary().startsWith("CrazyNinjaOdds didn't answer"))
    }

    @Test
    fun `a run cancelled part-way keeps the bets it already read`() = runTest {
        val bets = (1..8).map { bet("c$it", startsTs = start + it) }
        val t = tracker(*bets.toTypedArray())
        var reads = 0
        try {
            BetRecheck(t, books = { if (++reads == 4) throw kotlinx.coroutines.CancellationException("left the screen"); view(-125, 105) }, clock = { now }).run()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // expected: cancellation is never swallowed
        }
        assertEquals(3, t.all().count { it.nowEv != null })
    }

    /** Tj, 2026-09-29: "When I pressed check odds now in the tracker, it scanned very slow. Slower than before." */
    @Test
    fun `several pages are read at once, soonest game first, and every bet is still read exactly once`() = runTest {
        val bets = (1..12).map { bet("p$it", startsTs = start + it * 60_000L) }
        val t = tracker(*bets.toTypedArray())
        var inFlight = 0
        var mostAtOnce = 0
        val order = mutableListOf<String>()
        val r = BetRecheck(
            t,
            books = { row ->
                inFlight++
                mostAtOnce = maxOf(mostAtOnce, inFlight)
                order += row.gameUrl!!.substringAfter("side_id=")
                kotlinx.coroutines.delay(1_000L) // a page takes a moment
                inFlight--
                view(-125, 105)
            },
            clock = { now },
            concurrency = 3,
        )
        val report = r.run()
        assertEquals(3, mostAtOnce)
        assertEquals(12, report.updated)
        assertEquals(12, order.toSet().size)
        assertEquals(listOf("p1", "p2", "p3"), order.take(3)) // the soonest games go first
        // Twelve one-second pages, three at a time: about four seconds (a save in between can add a round), not twelve.
        assertEquals(true, testScheduler.currentTime in 4_000L..6_000L)
        assertEquals(12, t.all().count { it.nowEv != null })
    }

    @Test
    fun `with several at once a run still stops when CNO keeps failing, and no bet is counted twice`() = runTest {
        val bets = (1..20).map { bet("f$it", startsTs = start + it) }
        val t = tracker(*bets.toTypedArray())
        var asked = 0
        val down = BetRecheck(t, books = { asked++; kotlinx.coroutines.delay(10L); null }, clock = { now }, concurrency = 3).run()
        assertEquals(true, down.stopped)
        // Five in a row, plus the reads already under way when the fifth failed.
        assertEquals(true, asked in BetRecheck.MAX_FAILS_IN_ROW..(BetRecheck.MAX_FAILS_IN_ROW + 2))
        assertEquals(asked, down.checked)
        assertEquals(down.checked, down.failed)
        assertEquals(20 - asked, down.skipped)
        assertEquals(down.open, down.updated + down.failed + down.skipped + down.current + down.over + down.vigilantOnly)
    }

    @Test
    fun `one bet can be re-read on its own, whatever it was last read`() = runTest {
        val t = tracker(bet("one").copy(nowAtMs = now - 1_000L), bet("two"))
        var asked = 0
        val r = BetRecheck(t, books = { asked++; view(-125, 105) }, clock = { now })
        assertEquals(true, r.checkOne("one"))
        assertEquals(1, asked)
        assertEquals(now, t.all().first { it.id == "one" }.nowAtMs)
        assertNull(t.all().first { it.id == "two" }.nowEv)
        // A bet that's settled, unknown, or off CNO can't be re-read.
        t.settle("two", BetStatus.WON)
        assertEquals(false, r.checkOne("two"))
        assertEquals(false, r.checkOne("nobody"))
        // A page that won't read says so.
        assertEquals(false, BetRecheck(t, books = { null }, clock = { now }).checkOne("one"))
        // And a pause from CNO means no read at all.
        assertEquals(false, BetRecheck(t, books = { asked++; view(-125, 105) }, clock = { now }, paused = { true }).checkOne("one"))
        assertEquals(1, asked)
    }

    @Test
    fun `a bet settled while its page was being read isn't given a book snapshot or a new EV`() = runTest {
        val t = tracker(bet("race"))
        val r = BetRecheck(t, books = { t.settle("race", BetStatus.WON); view(-125, 105) }, clock = { now })
        r.run()
        val b = t.all().single()
        assertEquals(BetStatus.WON, b.status)
        assertEquals(emptyList<BookLine>(), b.books)
        assertNull(b.nowEv)
    }

    @Test
    fun `re-reading one bet never waits for a whole pass over the rest`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val t = tracker(bet("slow", startsTs = start + 1), bet("fast", startsTs = start + 2))
        val r = BetRecheck(t, books = { row -> if (row.gameUrl!!.endsWith("slow")) gate.await(); view(-125, 105) }, clock = { now })
        val pass = launch { r.run() }
        kotlinx.coroutines.yield()
        // The pass is stuck on the first bet; the second can still be read on its own (the sheet opening mid-pass).
        assertEquals(true, r.checkOne("fast"))
        assertEquals(true, t.all().first { it.id == "fast" }.nowEv != null)
        gate.complete(Unit)
        pass.join()
        assertEquals(2, t.all().count { it.nowEv != null })
    }

    @Test
    fun `the closing line is read for open bets about to start, and only those`() = runTest {
        now = start - 30 * 60_000L
        val t = tracker(
            bet("soon", startsTs = start),
            bet("later", startsTs = start + 3 * 60 * 60_000L),
            bet("started", startsTs = now - 10 * 60_000L),
            bet("fresh", startsTs = start).copy(nowAtMs = now - 60_000L, nowFair = 0.5, nowEv = 0.0),
            bet("vigilant", startsTs = start, gameUrl = null),
            bet("won", startsTs = start).copy(status = BetStatus.WON),
        )
        val seen = mutableListOf<String>()
        val r = BetRecheck(t, books = { row -> seen += row.gameUrl!!.substringAfter("side_id="); view(-125, 105) }, clock = { now })
        assertEquals(1, r.captureClosing())
        assertEquals(listOf("soon"), seen)
        // What it read is the closing line so far: the fair price before the start.
        val soon = t.all().first { it.id == "soon" }
        assertEquals(soon.nowFair, soon.closingFair)
        assertNull(t.all().first { it.id == "later" }.closingFair)
        // Read again once its books are five minutes old, and the closing line follows.
        now += 6 * 60_000L
        assertEquals(2, r.captureClosing()) // soon (old now) and fresh (over five minutes since its read)
        // Nothing to do: nothing is read.
        val quiet = tracker()
        assertEquals(0, BetRecheck(quiet, books = { seen += "x"; null }, clock = { now }).captureClosing())
    }
}
