package com.tjshea.vigilant.data.study

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetGraderTest
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.CloseLookup
import com.tjshea.vigilant.data.tracker.CloseSource
import com.tjshea.vigilant.data.tracker.FreeScores
import com.tjshea.vigilant.data.tracker.GameScore
import com.tjshea.vigilant.data.tracker.PlayerLine
import com.tjshea.vigilant.data.tracker.ScoreSource
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.StringWriter
import java.time.LocalDate

/**
 * Tj, 2026-10-03: "on every cno scan, the vigilant app saves logs on all kinds of information such as … odds at the time of scan, type of bet, percent EV,
 * amount of books that agree, percentage of books that agree, time before the game begins … when those bets are final, it logs whether they won or lost or
 * pushed and their closing line odds … utilize already available features in the app, such as the function in the app that already grades results and
 * closing odds … efficient and doesn't interrupt or break any other part of the app".
 */
class ScanStudyTest {

    @get:Rule val tmp = TemporaryFolder()

    private val start = BetGraderTest.METS_START
    private var now = start - 3 * 3_600_000L
    private val day: LocalDate = FreeScores.etDate(start)
    private val settings = ScanSettings()
    private val event = "New York Mets @ Washington Nationals"

    private fun journal() = StudyJournal(File(tmp.root, "study"))

    private fun study(j: StudyJournal = journal(), flushEveryMs: Long = 0L) = ScanStudy(j, clock = { now }, version = { "0.57.0" }, flushEveryMs = flushEveryMs, io = Dispatchers.Unconfined)

    /** A CNO row whose EV follows from its fair probability and price (the app's own screen checks that), as CNO prints it. */
    private fun row(
        market: String, bet: String, odds: Int, fair: Double = 0.5122, books: Int = 5, startsAt: Long? = start, side: Int = 1,
        ev: Double = fair * com.tjshea.vigilant.engine.Odds.americanToDecimal(odds) - 1.0,
    ) = CnoRow(
        ev = ev, startsAtMs = startsAt, league = "MLB", sport = "BASEBALL", event = event, market = market, bet = bet, odds = odds, available = 40.0, book = "Novig",
        fairOdds = -105, fairProbability = fair, books = books, gameUrl = "https://x/game.aspx?game_id=9&side_id=$side&devig_method=8",
    )

    private val mlEv = 0.5122 * 2.05 - 1.0
    private val moneyline get() = row("Moneyline", "New York Mets", 105)
    private val total get() = row("Total Runs", "Over 8.5", 110, fair = 0.5, side = 2)
    private val prop get() = row("Player Total Bases", "Carson Benge Over 1.5", 120, fair = 0.4773, side = 3)

    private var read = 0L
    private fun snap(vararg rows: CnoRow, url: String = "https://cno/view", filters: CnoFilters = CnoFilters()): CnoSnapshot {
        read = now
        return CnoSnapshot(url, rows.toList(), fetchedAtMs = now, cnoAgeSeconds = 4, filters = filters)
    }

    private suspend fun ScanStudy.cno(s: CnoSnapshot, books: Map<String, CnoBooksState> = emptyMap(), live: Map<String, LivePrice> = emptyMap(), links: Map<String, String> = emptyMap(), set: ScanSettings = settings) =
        observeCno(s, set, books, live, links)

    // ---- logging -------------------------------------------------------------------------------------------------------

    @Test
    fun `every pregame bet a CNO scan lists is logged with its record as first listed, and a started or undated row isn't`() = runBlocking {
        val s = study()
        val created = s.cno(snap(moneyline, prop, row("Moneyline", "Washington Nationals", -125, startsAt = now - 600_000), row("Total Runs", "Under 8.5", -130, startsAt = null)))
        assertEquals(2, created)
        s.flush()
        val bets = journal().fold(day).values.toList()
        assertEquals(2, bets.size)
        val ml = bets.first { it.bet.marketLabel == "Moneyline" }
        val a = ml.bet.atBet!!
        // What Tj named: odds, type of bet, percent EV, books, time before the game, all as the scan showed them.
        assertEquals(AtBet.HOW_STUDY, a.how)
        assertEquals("cno", a.scanner)
        assertEquals("MONEYLINE", a.kind)
        assertEquals(180L, a.minutesToStart)
        assertEquals(105, a.american)
        assertEquals(mlEv, a.ev!!, 1e-9)
        assertEquals(5, a.cnoBooks)
        assertEquals(40.0, a.available!!, 1e-9)
        assertEquals("0.57.0", a.version)
        assertEquals(105, ml.bet.american)
        assertEquals(1.0 / 2.05, ml.bet.cost, 1e-9)
        assertEquals(1.0, ml.bet.stake, 1e-9)
        assertEquals("cno", ml.bet.source)
        assertNull(ml.screen)
        assertEquals(BetStatus.PENDING, ml.bet.status)
        assertEquals("PROP", bets.first { it.bet.marketLabel == "Player Total Bases" }.bet.atBet!!.kind)
        // Its first look: where, at what price and EV.
        val look = ml.sights.single().second
        assertEquals(Sight.CNO, look.k)
        assertEquals(105, look.o)
        assertEquals(mlEv, look.ev!!, 1e-9)
        assertEquals(5, look.b)
    }

    @Test
    fun `the same read twice, a list saved before the launch's first read and a switched-off study log nothing`() = runBlocking {
        val s = study()
        val first = snap(moneyline)
        assertEquals(1, s.cno(first))
        assertEquals(0, s.cno(first))
        // A list saved on disk: read long ago.
        now += 10 * 60_000L
        assertEquals(0, s.cno(first.copy(fetchedAtMs = now - 5 * 60_000L, rows = listOf(prop))))
        assertEquals(0, s.cno(snap(prop), set = settings.copy(scanStudy = false)))
        s.flush()
        assertEquals(1, journal().fold(day).size)
        assertEquals(1L, s.betsLogged)
    }

    @Test
    fun `a watched bet is logged again when its price moves, at most once a minute, and at least every five minutes`() = runBlocking {
        val s = study()
        s.cno(snap(moneyline))
        now += 20_000
        // Within a minute: a price move isn't another line.
        s.cno(snap(row("Moneyline", "New York Mets", 110)))
        now += 45_000
        s.cno(snap(row("Moneyline", "New York Mets", 110)))
        now += 61_000
        // Unchanged but over a minute: nothing; changed: a line.
        s.cno(snap(row("Moneyline", "New York Mets", 110)))
        now += 61_000
        s.cno(snap(row("Moneyline", "New York Mets", 115)))
        // Quiet for five minutes: one anyway.
        now += 5 * 60_000L + 1_000
        s.cno(snap(row("Moneyline", "New York Mets", 115)))
        s.flush()
        val looks = journal().fold(day).values.single().sights.map { it.second.o }
        assertEquals(listOf(105, 110, 115, 115), looks)
    }

    @Test
    fun `a bet the same list stops showing is logged as gone with when, a changed view calls nothing gone, and it's logged again when it returns`() = runBlocking {
        val s = study()
        s.cno(snap(moneyline, prop))
        now += 30_000
        s.cno(snap(moneyline))
        now += 30_000
        // Another view: its rows leave without having gone.
        s.cno(snap(prop, url = "https://cno/other"))
        now += 30_000
        s.cno(snap(moneyline, prop, url = "https://cno/other"))
        s.flush()
        val bets = journal().fold(day).values
        val p = bets.first { it.bet.marketLabel == "Player Total Bases" }
        assertEquals(listOf(Sight.CNO, Sight.GONE_CNO), p.sights.map { it.second.k }.take(2))
        assertEquals(now - 60_000, p.sights[1].first)
        // The prop went at the second read, came back at the fourth (a different view made the third's changes not gone).
        assertEquals(listOf(Sight.CNO, Sight.GONE_CNO, Sight.CNO), p.sights.map { it.second.k })
        val m = bets.first { it.bet.marketLabel == "Moneyline" }
        // Listed, then dropped by the third read's other view: no "gone" there, and listed again in the fourth.
        assertFalse(m.sights.any { Sight.isGone(it.second.k) })
    }

    @Test
    fun `Novig's own price is the look's price when it was read in the last minute, and a stale one isn't`() = runBlocking {
        val s = study()
        val live = mapOf(moneyline.key to LivePrice(american = 118, available = 75.0, ev = 0.061, atMs = now - 20_000, marketId = "m-1", outcomeId = "o-1"))
        s.cno(snap(moneyline), live = live)
        now += 120_000
        val stale = mapOf(moneyline.key to LivePrice(american = 140, available = 5.0, ev = 0.2, atMs = now - 90_000))
        s.cno(snap(moneyline), live = stale)
        s.flush()
        val b = journal().fold(day).values.single()
        assertEquals(118, b.bet.american)
        assertEquals("m-1", b.bet.marketId)
        assertEquals("o-1", b.bet.outcomeId)
        assertEquals(listOf(118, 105), b.sights.map { it.second.o })
        assertEquals(75.0, b.sights[0].second.a!!, 1e-9)
    }

    @Test
    fun `a bet CNO's screen would hide is logged with why`() = runBlocking {
        val s = study()
        // One book behind the fair, under the four the filters ask for.
        s.cno(snap(row("Moneyline", "New York Mets", 105, books = 1)))
        s.flush()
        assertEquals("BOOKS", journal().fold(day).values.single().screen)
    }

    // ---- the book check ---------------------------------------------------------------------------------------------------

    private fun view(at: Long) = CnoBooksView(
        "New York Mets", "Washington Nationals", null, false,
        listOf(
            CnoBookPrice("PN", -105, null, -110, null), CnoBookPrice("DK", -105, null, -110, null), CnoBookPrice("FD", -105, null, -110, null),
            CnoBookPrice("CZR", -105, null, -110, null), CnoBookPrice("NV", 105, null, -125, null),
        ),
        fetchedAtMs = at,
    )

    @Test
    fun `the book page the green check read adds the check to the bet's record and to its looks, once`() = runBlocking {
        val s = study()
        s.cno(snap(moneyline))
        now += 30_000
        val books = mapOf(moneyline.key to CnoBooksState(view = view(now - 5_000)))
        assertEquals(1, s.observeBooks(books, settings, emptyMap()))
        // The same page again: nothing new.
        assertEquals(0, s.observeBooks(books, settings, emptyMap()))
        s.flush()
        val b = journal().fold(day).values.single()
        val a = b.bet.atBet!!
        assertNotNull(a.twoSided)
        assertEquals(4, a.agreeing)
        assertEquals(a.twoSided, a.books.count { it.other != null && it.book != "Novig" })
        assertEquals(now, a.checkAtMs)
        // The record as first listed is kept: the price and minutes to start are the first look's.
        assertEquals(105, a.american)
        assertEquals(180L, a.minutesToStart)
        val check = b.sights.map { it.second }.last { it.k == Sight.CHECK }
        assertEquals(4, check.g)
        assertEquals(a.twoSided, check.n)
        assertNotNull(check.ce)
        assertNotNull(check.sv)
    }

    // ---- the journal --------------------------------------------------------------------------------------------------------

    @Test
    fun `after a restart a bet the list still shows isn't logged a second time, and a half-written line is skipped`() = runBlocking {
        val j = journal()
        val a = study(j)
        a.cno(snap(moneyline, prop))
        a.flush()
        // The process died mid-line.
        j.file(day).appendText("""{"e":"s","id":"zz","t":1,"s":{"k":"c","o":""")
        now += 30_000
        val b = study(j)
        assertEquals(0, b.cno(snap(moneyline, prop)))
        // A price move a minute on: its line goes after the torn one, on a line of its own.
        now += 70_000
        assertEquals(0, b.cno(snap(row("Moneyline", "New York Mets", 110), prop)))
        b.flush()
        val folded = j.fold(day)
        assertEquals(2, folded.size)
        assertEquals(2, j.read(day).count { it.e == Line.BET })
        assertEquals(3, j.read(day).count { it.e == Line.SIGHT })
        assertEquals(listOf(105, 110), folded.values.first { it.bet.marketLabel == "Moneyline" }.sights.map { it.second.o })
    }

    @Test
    fun `lines are written together once the flush gap has passed, and kept when the disk refuses them`() = runBlocking {
        // A disk that refuses: nothing is thrown at the scan, the problem is noted, the lines wait.
        val blocked = StudyJournal(File(tmp.root, "blocked").also { it.writeText("a file where the folder should be") })
        val s = ScanStudy(blocked, clock = { now }, flushEveryMs = 0, io = Dispatchers.Unconfined)
        assertEquals(1, s.cno(snap(moneyline)))
        assertNotNull(s.lastProblem)
        // Within the gap nothing is written yet; past it, one write for everything that waited.
        val ok = journal()
        val t = ScanStudy(ok, clock = { now }, flushEveryMs = 10_000, io = Dispatchers.Unconfined)
        t.cno(snap(moneyline))
        assertTrue(ok.fold(day).isEmpty())
        now += 11_000
        t.cno(snap(prop))
        assertEquals(2, ok.fold(day).size)
        assertEquals(1, ok.read(day).count { it.e == Line.BET && it.id == ok.fold(day).keys.first() })
    }

    @Test
    fun `the study makes no request of its own and reads only what the scan handed it`() {
        val source = File("src/main/kotlin/com/tjshea/vigilant/data/study/ScanStudy.kt").readText()
        assertFalse(source.contains("okhttp3"))
        assertFalse(source.contains("OkHttpClient"))
        assertFalse(source.contains("loadBooks("))
        assertFalse(source.contains("readNow("))
    }

    // ---- grading and closes -------------------------------------------------------------------------------------------------

    private class FakeScores : ScoreSource {
        override fun covers(league: String) = league == "MLB"
        override suspend fun games(league: String, date: LocalDate): List<GameScore>? =
            if (league == "MLB" && date == LocalDate.of(2026, 9, 26)) {
                listOf(GameScore("822678", "MLB", "Washington Nationals", "New York Mets", BetGraderTest.METS_START, true, false, 1, 7, listOf(0, 0, 1, 0, 0, 0, 0, 0, 0), listOf(0, 0, 0, 0, 0, 0, 4, 1, 2)))
            } else emptyList()

        override suspend fun players(game: GameScore): List<PlayerLine>? = BetGraderTest.padded(PlayerLine("Carson Benge", mapOf("TOTAL_BASES" to 4.0)))
    }

    private class FakeClose(val fair: Double = 0.55, val note: String? = null) : CloseSource {
        var asked = 0
        override val id: String get() = "fake"
        override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
            asked += bets.size
            return bets.associate { it.id to (if (note == null) CloseLookup.Found(fair, "Fake · Pinnacle close") else CloseLookup.None(note)) }
        }
    }

    @Test
    fun `after the game the logged bets are graded and closed by the app's own grader and close lookups, and the results go to the journal`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline, total, prop))
        s.flush()
        now = start + 4 * 3_600_000L
        val close = FakeClose(0.55)
        val report = s.settle(FakeScores(), listOf(close), emptyList(), File(tmp.root, "scratch"))
        assertEquals(3, report.looked)
        assertEquals(3, report.graded)
        assertEquals(3, report.closed)
        val bets = j.fold(day).values.associateBy { it.bet.marketLabel }
        assertEquals(BetStatus.WON, bets.getValue("Moneyline").bet.status)
        assertEquals(BetStatus.LOST, bets.getValue("Total Runs").bet.status)
        assertEquals(BetStatus.WON, bets.getValue("Player Total Bases").bet.status)
        assertEquals(1.05, bets.getValue("Moneyline").bet.profit!!, 1e-9)
        assertEquals(0.55, bets.getValue("Moneyline").bet.closeFair!!, 1e-9)
        assertEquals("Fake · Pinnacle close", bets.getValue("Moneyline").bet.closeVia)
        // CLV the app's own way: the close's fair over what the price cost, minus one.
        val b = bets.getValue("Moneyline").bet
        assertEquals(0.55 * 2.05 - 1.0, com.tjshea.vigilant.data.tracker.ClosingLine.clv(b, now)!!, 1e-9)
        // Nothing is left to do: the next pass looks at nothing, asks no feed, and writes nothing.
        val lines = j.read(day).count()
        val again = s.settle(FakeScores(), listOf(close), emptyList(), File(tmp.root, "scratch"))
        assertEquals(0, again.looked)
        assertEquals(lines, j.read(day).count())
        assertEquals(3, close.asked)
        // The scratch Tracker file is gone.
        assertTrue(File(tmp.root, "scratch").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `grading yields to the Tracker's own close lookups and picks the bets up on the next pass`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline))
        s.flush()
        now = start + 4 * 3_600_000L
        val close = FakeClose(0.55)
        val busy = s.settle(FakeScores(), listOf(close), emptyList(), File(tmp.root, "scratch"), yieldTo = { true })
        assertEquals(0, busy.looked)
        assertEquals(0, close.asked)
        assertEquals(BetStatus.PENDING, j.fold(day).values.single().bet.status)
        val free = s.settle(FakeScores(), listOf(close), emptyList(), File(tmp.root, "scratch"), yieldTo = { false })
        assertEquals(1, free.graded)
        assertEquals(1, free.closed)
    }

    @Test
    fun `a bet whose game isn't over or has no close yet is tried again next pass, with the close lookup's own gap kept`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline))
        s.flush()
        now = start + 90 * 60_000L
        val none = FakeClose(note = "ESPN keeps no line for this")
        val live = object : ScoreSource by FakeScores() {
            override suspend fun games(league: String, date: LocalDate): List<GameScore>? = FakeScores().games(league, date)?.map { it.copy(final = false) }
        }
        s.settle(live, listOf(none), emptyList(), File(tmp.root, "scratch"))
        var b = j.fold(day).values.single().bet
        assertEquals(BetStatus.PENDING, b.status)
        assertEquals("ESPN keeps no line for this", b.closeNote)
        assertTrue(b.closeFinal)
        // Every source said never: a close isn't looked for again; the result is, once the game is over.
        now += 40 * 60_000L
        s.settle(FakeScores(), listOf(none), emptyList(), File(tmp.root, "scratch"))
        b = j.fold(day).values.single().bet
        assertEquals(BetStatus.WON, b.status)
        assertNull(b.closeFair)
        assertEquals(1, none.asked)
    }

    @Test
    fun `a bet Tj placed himself takes its result and its close from his Tracker bet, and nothing is looked up for it`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline, total))
        s.flush()
        now = start + 4 * 3_600_000L
        val placed = TrackedBet(
            id = "tj", createdAtMs = start - 2 * 3_600_000L, league = "MLB", eventName = event, startsTs = start, marketLabel = "Moneyline", selection = "New York Mets",
            marketId = "", outcomeId = "", price = 0.48, cost = 0.48, fairAtBet = 0.5, evPercentAtBet = 0.04, stake = 2.0, american = 108, status = BetStatus.LOST,
            settledAtMs = now - 1_000, settledBy = "scores", closingFair = 0.57, closingSeenAtMs = start - 120_000, novigClose = 0.56, novigCloseAtMs = start - 100_000,
            nowVia = com.tjshea.vigilant.data.tracker.BetTracker.VIA_CNO,
        )
        val close = FakeClose(0.5)
        val report = s.settle(FakeScores(), listOf(close), listOf(placed), File(tmp.root, "scratch"))
        assertEquals(1, report.copied)
        val bets = j.fold(day).values.associateBy { it.bet.marketLabel }
        val ml = bets.getValue("Moneyline")
        // His bet's graded result and the close read just before the start, copied; the unrelated total graded the normal way.
        assertEquals(BetStatus.LOST, ml.bet.status)
        assertEquals(0.57, com.tjshea.vigilant.data.tracker.ClosingLine.closeFair(ml.bet, now)!!, 1e-9)
        assertEquals(0.56, ml.bet.novigClose!!, 1e-9)
        assertEquals("tracker", ml.from)
        // Whose fair line that close is, named (the file said only "read before the start" and could not be told from a sharp book's close).
        assertEquals("Tracker · read before the start (CNO's books)", ml.bet.closeVia)
        assertEquals(BetStatus.LOST, bets.getValue("Total Runs").bet.status)
        // The close source was asked only for the total.
        assertEquals(1, close.asked)
    }

    /**
     * Tj's v0.70.1 file: "why no close" cut each note at 90 characters, so the last sources' reasons (the biggest: 824 of 1,084 started bets had no Novig outcome id) never
     * printed; a voided bet was in no count; and "closes found" divided by every bet, including games not on yet. Now each source's own reason is counted, in full, once per bet.
     */
    @Test
    fun `no-close reasons are counted one source at a time and in full, voids are counted, and started bets are the denominator`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline, total))
        s.flush()
        now = start + 4 * 3_600_000L
        val note = "Novig's trade file for this day isn't out yet; ESPN keeps full-game moneylines, spreads and totals only; no Novig outcome on record for this bet (it was found by the wide read, so its link was never kept)"
        for (b in j.fold(day).values) j.append(day, listOf(Line(Line.RES, b.id, now, r = StudyResult(closeNote = note))))
        val voided = j.fold(day).values.first()
        j.append(day, listOf(Line(Line.RES, voided.id, now, r = StudyResult(status = com.tjshea.vigilant.data.tracker.BetStatus.VOID))))
        val out = StringWriter()
        val meta = StudyExport.Meta("0.70.4", 122, "moto g", "edge ≥ 2.5%", java.util.TimeZone.getTimeZone("America/New_York"))
        StudyExport.write(out, j, emptyList(), meta, now, File(tmp.root, "export.tmp"))
        val text = out.toString()
        assertTrue(text, text.contains("Closes found: 0 of 2 started bets (0%)"))
        // Each reason on its own line with its count, none cut at 90 characters.
        assertTrue(text, text.contains("    ×2 Novig's trade file for this day isn't out yet"))
        assertTrue(text, text.contains("    ×2 ESPN keeps full-game moneylines, spreads and totals only"))
        assertTrue(text, text.contains("    ×2 no Novig outcome on record for this bet (it was found by the wide read, so its link was never kept)"))
        // The void is said.
        assertTrue(text, text.lines().first { it.startsWith("ALL BETS") }.contains("1 void"))
    }

    /**
     * Tj's v0.58.3 file: a Washington State moneyline of -117 "closed" at +272 (CLV -50%) and dragged the hidden group's CLV from +2.0% to -0.4%. A close the
     * lookup finds that can't be the bet's is left out ([com.tjshea.vigilant.data.tracker.ClosePlausibility]); and the journal, which is append-only, may still
     * hold one from before the matcher was fixed, so the file leaves it out too, with the reason.
     */
    @Test
    fun `a close that can't be the bet's is never kept by the lookup, and one the journal holds is left out of the file with its reason`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline))
        s.flush()
        now = start + 4 * 3_600_000L
        // Today's lookup: the close source says 5% for a bet listed near 50%: no close, with why.
        s.settle(FakeScores(), listOf(FakeClose(0.05)), emptyList(), File(tmp.root, "scratch"))
        val held = j.fold(day).values.single().bet
        assertNull(held.closeFair)
        assertTrue(held.closeNote, held.closeNote!!.contains("probably another game or side, not used"))
        // A line written by an older version, before the check: kept in the journal, left out of the file.
        j.append(day, listOf(Line(Line.RES, j.fold(day).values.single().id, now, r = StudyResult(closeFair = 0.05, closeVia = "ParlayAPI · Pinnacle close", closeFinal = true))))
        val out = StringWriter()
        val meta = StudyExport.Meta("0.58.3", 103, "moto g", "edge ≥ 2.5%", java.util.TimeZone.getTimeZone("America/New_York"))
        StudyExport.write(out, j, emptyList(), meta, now, File(tmp.root, "export.tmp"))
        val text = out.toString()
        assertTrue(text, text.contains("Closes found: 0 of 1 started bets (0%)"))
        val row = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(
            StudyExport.StudyRow.serializer(), text.substringAfter("<<<JSONL\n").substringBefore("\n>>>").lines().first { it.isNotBlank() },
        )
        assertNull(row.closeFair)
        assertNull(row.clv)
        assertNull(row.closeVia)
        assertTrue(row.closeNote!!.contains("probably another game or side"))
        // The rules line says which part decides the app's list.
        assertTrue(text, text.contains("follows the 'CNO:' part"))
    }

    // ---- efficiency ---------------------------------------------------------------------------------------------------------

    /**
     * Tj, 2026-10-03: "app storage is no concern … make sure it is efficient". A busy evening: CNO's 100 rows read every 30 seconds for two hours, every price
     * flickering. The journal takes a line per change at most once a minute, one write every ten seconds, and the whole evening costs a few seconds of CPU.
     */
    @Test
    fun `a two-hour evening of 100-row scans every 30 seconds stays small and quick, and logs a price flicker once a minute at most`() = runBlocking {
        val j = journal()
        val s = ScanStudy(j, clock = { now }, version = { "0.57.0" }, flushEveryMs = 10_000, io = Dispatchers.Unconfined)
        val picks = (1..100).map { "Player $it Over 1.5" }
        var flickers = 0
        val began = System.nanoTime()
        repeat(240) { scan ->
            now += 30_000
            val rows = picks.mapIndexed { i, p ->
                // Every price moves by a point or two at every read.
                row("Player Total Bases", p, 100 + (scan * 7 + i * 13) % 40, fair = 0.52, side = i + 1)
            }
            flickers += rows.size
            s.cno(snap(*rows.toTypedArray()))
        }
        s.flush()
        val tookMs = (System.nanoTime() - began) / 1_000_000
        val bets = j.fold(day).values
        assertEquals(100, bets.size)
        // At most a line a minute for 120 minutes, plus the first.
        assertTrue("${bets.maxOf { it.sights.size }} looks for one bet", bets.all { it.sights.size <= 125 })
        assertTrue("${bets.sumOf { it.sights.size }} looks for $flickers reads", bets.sumOf { it.sights.size } <= 100 * 125)
        // A few MB at most for the whole evening, and a few seconds.
        assertTrue("${j.bytes()} bytes", j.bytes() < 6_000_000)
        assertTrue("$tookMs ms for 240 scans", tookMs < 8_000)
        // Written in about a line of writes per ten seconds, not one per scan.
        assertTrue(j.read(day).count() > 100)
    }

    // ---- the pieces -------------------------------------------------------------------------------------------------------------

    @Test
    fun `a look is worth a line when it's the first, a change, or the heartbeat - never more than once a minute`() {
        val a = Sight(Sight.CNO, o = 105, ev = 0.045, f = 0.512, b = 5)
        val t = 1_000_000L
        assertTrue(ScanStudy.worthLogging(null, null, a, t))
        assertFalse(ScanStudy.worthLogging(a, t, a.copy(o = 110), t + 59_000))
        assertTrue(ScanStudy.worthLogging(a, t, a.copy(o = 110), t + 60_000))
        assertFalse(ScanStudy.worthLogging(a, t, a.copy(ev = 0.0449), t + 120_000))
        assertTrue(ScanStudy.worthLogging(a, t, a.copy(ev = 0.0476), t + 120_000))
        assertTrue(ScanStudy.worthLogging(a, t, a.copy(b = 6), t + 120_000))
        assertTrue(ScanStudy.worthLogging(a, t, a.copy(f = 0.515), t + 120_000))
        assertFalse(ScanStudy.worthLogging(a, t, a, t + 299_000))
        assertTrue(ScanStudy.worthLogging(a, t, a, t + 300_000))
    }

    // ---- the file for Claude ----------------------------------------------------------------------------------------------------

    @Test
    fun `the file tells Claude the goal and to be thorough, sums the bets up by every split, and has one JSON line per bet with its looks, close and result`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline, total, prop))
        now += 90_000
        s.cno(snap(row("Moneyline", "New York Mets", 120), total, prop))
        // The green check read the moneyline's page: four companies price both sides, all four agree.
        s.observeBooks(mapOf(moneyline.key to CnoBooksState(view = view(now - 5_000))), settings, emptyMap())
        s.flush()
        now = start + 4 * 3_600_000L
        s.settle(FakeScores(), listOf(FakeClose(0.55)), emptyList(), File(tmp.root, "scratch"))
        val out = StringWriter()
        val meta = StudyExport.Meta("0.57.0", 99, "moto g", "edge ≥ 2.5%", java.util.TimeZone.getTimeZone("America/New_York"))
        val placed = TrackedBet(
            id = "tj", createdAtMs = start - 3_600_000L, league = "MLB", eventName = event, startsTs = start, marketLabel = "Player Total Bases", selection = "Carson Benge Over 1.5",
            marketId = "", outcomeId = "", price = 0.45, cost = 0.45, fairAtBet = 0.5, evPercentAtBet = 0.04, stake = 1.0, american = 122,
        )
        val n = StudyExport.write(out, j, listOf(placed), meta, now, File(tmp.root, "export.tmp"))
        assertEquals(3, n)
        val text = out.toString()
        assertTrue(text, text.startsWith("VIGILANT SCAN STUDY · version 0.57.0 · vigilant-scan-study-v0.57.0-"))
        // The goal and the instruction Tj asked for.
        assertTrue(text.contains("THE GOAL IS PROFIT"))
        assertTrue(text.contains("be thorough, and analyze ALL of the data for patterns and for profitable bet strategies"))
        assertTrue(text.contains("== DATA DICTIONARY =="))
        assertTrue(text.contains("== HOW THIS DATA WAS COLLECTED, AND WHAT IT CAN'T SAY =="))
        // The sums: 3 bets, 2 won and 1 lost at the first-listed price; the close beaten on all three.
        assertTrue(text, text.contains("ALL BETS · 3 bets · 2-1-0 (W-L-P)"))
        // Tj's v0.70.1 file printed 683 of 2,080 (32.8%) though 313 of those bets had not started: the share is of the bets a close can be asked of.
        assertTrue(text, text.contains("Closes found: 3 of 3 started bets (100%) (Fake 3)"))
        assertTrue(text.contains("-- Books agreeing --") && text.contains("-- Kind of bet --") && text.contains("-- Time to the start --") && text.contains("-- League --"))
        assertTrue(text, text.contains("Moneylines · 1 bets") || text.contains("Moneyline"))
        // The lines.
        val lines = text.substringAfter("<<<JSONL\n").substringBefore("\n>>>").lines().filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val rows = lines.map { json.decodeFromString(StudyExport.StudyRow.serializer(), it) }
        val ml = rows.first { it.market == "Moneyline" }
        assertEquals("c", ml.src)
        assertEquals(105, ml.american)
        assertEquals(120, ml.bestAmerican)
        assertEquals(120, ml.lastAmerican)
        assertEquals("WON", ml.status)
        assertEquals(0.55, ml.closeFair!!, 1e-9)
        assertEquals(0.55 * 2.05 - 1.0, ml.clv!!, 1e-9)
        // At the better price it later had, the close was beaten by more.
        assertEquals(0.55 / (1.0 / 2.2) - 1.0, ml.clvBest!!, 1e-9)
        // Looks: the two CNO reads and the book page read.
        assertEquals(3, ml.looks)
        assertEquals(3, ml.s.size)
        assertEquals(180, ml.s[0].toString().removePrefix("[").substringBefore(",").toInt())
        // The fields Tj named, at the top level: kind, minutes to the start, books, how many agree and what share.
        assertEquals("MONEYLINE", ml.kind)
        assertEquals("BASEBALL", ml.sport)
        assertEquals(180L, ml.minToStartFirst)
        assertEquals(5, ml.cnoBooks)
        assertEquals(4, ml.booksTwoSided)
        assertEquals(4, ml.booksAgreeing)
        assertEquals(1.0, ml.agreeShare!!, 1e-9)
        assertEquals(40.0, ml.available!!, 1e-9)
        assertNotNull(ml.sharpVerdict)
        // A bet whose page wasn't read has no share (not a zero).
        assertNull(rows.first { it.market == "Total Runs" }.agreeShare)
        assertEquals(5, rows.first { it.market == "Total Runs" }.cnoBooks)
        assertFalse(ml.placedByTj)
        assertTrue(rows.first { it.market == "Player Total Bases" }.placedByTj)
        assertEquals(122, rows.first { it.market == "Player Total Bases" }.placedAmerican)
        // The close's number as odds, and the rules line isn't repeated on every bet.
        assertNotNull(ml.closeAmerican)
        assertNull(ml.atBet!!.rules)
        // Newest first, and the scratch file is gone.
        assertTrue(rows.zipWithNext().all { (a, b) -> a.firstSeenMs >= b.firstSeenMs })
        assertFalse(File(tmp.root, "export.tmp").exists())
    }

    @Test
    fun `a day too big for the file's limit stays on the phone, newest days first`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline))
        s.flush()
        val older = LocalDate.of(2026, 9, 20)
        j.append(older, listOf(Line(Line.BET, "old1", start - 6 * 86_400_000L, b = j.fold(day).values.single().bet.copy(id = "old1", startsTs = start - 6 * 86_400_000L, createdAtMs = start - 6 * 86_400_000L - 3_600_000L))))
        val out = StringWriter()
        val meta = StudyExport.Meta("0.57.0", 99, "moto g", "rules")
        // A limit whose days budget (three times it) holds only the newest day.
        val n = StudyExport.write(out, j, emptyList(), meta, now, File(tmp.root, "export.tmp"), maxBytes = j.file(day).length() / 3)
        assertEquals("the older day is in neither the lines nor the sums", 1, n)
        assertTrue(out.toString().contains("1 older day(s) are on the phone but left out"))
        assertEquals(2, StudyExport.write(StringWriter(), j, emptyList(), meta, now, File(tmp.root, "export.tmp")))
    }

    /** Tj, 2026-10-05: "make sure the auto bid feature is also thoroughly tracked in the scan/diagnosis feature and all information logged so I can see how well my auto bids do". */
    @Test
    fun `the export carries the bids - the summary, every filled bid and the newest unfilled ones as JSON lines - and the read me says how to judge them`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline))
        s.flush()
        fun bid(n: Int, filledDelayMs: Long?, status: com.tjshea.vigilant.data.novig.trading.maker.MakerStatus) = com.tjshea.vigilant.data.novig.trading.maker.MakerBid(
            clientId = "c$n", orderId = "o$n", marketId = "m$n", eventId = "e$n", outcomeId = "x$n", league = "NFL", eventName = "A @ B", startsTs = start,
            marketLabel = "Receiving Yards", selection = "Player $n Over 50.5", kind = BetKind.PROP, price = 0.45, contracts = 1_000, fair = 0.468, evAtFair = 0.04, margin = 0.04, books = 4,
            postedAtMs = start - 3_600_000L, expiresAtMs = start - 3_000_000L, status = status, filled = if (filledDelayMs != null) 1_000 else 0, paid = if (filledDelayMs != null) 4.5 else 0.0,
            endedAtMs = start - 3_000_000L, bestBidAtPost = 0.44, offerAtPost = 0.50, bookAtMs = start - 3_700_000L, blendFair = 0.48, sharpFairAtPost = 0.468,
            firstFillAtMs = filledDelayMs?.let { start - 3_600_000L + it }, fairAtFill = 0.44.takeIf { filledDelayMs != null }, sharpFairAtFill = 0.43.takeIf { filledDelayMs != null },
        )
        val out = StringWriter()
        val meta = StudyExport.Meta("0.64.0", 111, "moto g", "rules")
        val bids = listOf(
            bid(1, 45_000L, com.tjshea.vigilant.data.novig.trading.maker.MakerStatus.FILLED),
            bid(2, null, com.tjshea.vigilant.data.novig.trading.maker.MakerStatus.EXPIRED).copy(why = "expired"),
        )
        StudyExport.write(out, j, emptyList(), meta, now, File(tmp.root, "export.tmp"), bids = bids)
        val text = out.toString()
        assertTrue(text, text.contains("== BIDS (Vigilant's make orders"))
        assertTrue(text, text.contains("bids: 2 posted · 1 filled (50%)"))
        assertTrue(text, text.contains("evAtFill") && text.contains("picked off"))
        assertTrue(text, text.contains("-- bids that ended without a fill, by why --") && text.contains("×1 expired"))
        val filled = text.substringAfter("<<<BIDJSONL\n").substringBefore("\n>>>").lines().filter { it.isNotBlank() }
        assertEquals(1, filled.size)
        val row = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(com.tjshea.vigilant.data.novig.trading.maker.BidReport.Row.serializer(), filled.single())
        assertEquals(45L, row.fillDelaySec)
        assertEquals(true, row.pickedOff)
        assertEquals(0.43 / 0.45 - 1.0, row.evAtFill!!, 1e-9)
        assertEquals(true, row.led)
        val unfilled = text.substringAfter("<<<UNFILLEDJSONL\n").substringBefore("\n>>>").lines().filter { it.isNotBlank() }
        assertEquals(1, unfilled.size)
        assertTrue(text.contains("BIDS: when Vigilant has posted bids"))
        // No bids: no section.
        val none = StringWriter()
        StudyExport.write(none, j, emptyList(), meta, now, File(tmp.root, "export.tmp"))
        assertFalse(none.toString().contains("== BIDS"))
    }

    /**
     * Tj, 2026-10-07: "make the app bet logging differentiate from bets and bids … and also for the diagnostics and studies sections." A scan-listed bet Tj holds as a bid
     * of Vigilant's that a taker filled is not "placed by Tj"; the file says how the Tracker holds it, and has a block with his bets and his bids apart.
     */
    @Test
    fun `a bid that filled is not marked as Tj's own bet, the split says so, and a block gives bets and bids each their own numbers`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.cno(snap(moneyline, total, prop))
        s.flush()
        now = start + 4 * 3_600_000L
        s.settle(FakeScores(), listOf(FakeClose(0.55)), emptyList(), File(tmp.root, "scratch"))
        val meta = StudyExport.Meta("0.74.0", 132, "moto g", "rules", java.util.TimeZone.getTimeZone("America/New_York"))
        fun held(id: String, market: String, pick: String, maker: Boolean) = TrackedBet(
            id = id, createdAtMs = start - 3_600_000L, league = "MLB", eventName = event, startsTs = start, marketLabel = market, selection = pick,
            marketId = "", outcomeId = "", price = 0.45, cost = 0.45, fairAtBet = 0.5, evPercentAtBet = 0.04, stake = 1.0, american = 122, status = BetStatus.WON, settledAtMs = now,
            maker = maker, orderId = if (maker) "bid-$id" else null,
        )
        val tracked = listOf(held("tap", "Player Total Bases", "Carson Benge Over 1.5", maker = false), held("mm", "Moneyline", "New York Mets", maker = true))
        val out = StringWriter()
        StudyExport.write(out, j, tracked, meta, now, File(tmp.root, "export.tmp"))
        val text = out.toString()
        val rows = text.substringAfter("<<<JSONL\n").substringBefore("\n>>>").lines().filter { it.isNotBlank() }
            .map { kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(StudyExport.StudyRow.serializer(), it) }
        val taker = rows.first { it.market == "Player Total Bases" }
        val bid = rows.first { it.market == "Moneyline" }
        assertTrue(taker.placedByTj)
        assertEquals("bet", taker.placedAs)
        assertFalse("a bid of Vigilant's that filled is not Tj's own bet", bid.placedByTj)
        assertEquals("bid", bid.placedAs)
        assertEquals(122, bid.placedAmerican)
        assertNull(rows.first { it.market == "Total Runs" }.placedAs)
        assertTrue(text, text.contains("-- Tj placed it --"))
        assertTrue(text, text.contains("yes, as a bet: 1 bets") || text.contains("yes, as a bet"))
        assertTrue(text, text.contains("no, but Vigilant's bid on it filled"))
        // The scan-listed splits have no "Bet or bid" (every one is "listed"); Tj's own records do, in a block of their own.
        assertFalse(text, text.contains("-- Bet or bid --"))
        val block = text.substringAfter("== BETS AND BIDS APART").substringBefore("\n== ")
        assertTrue(block, block.contains("Bets (taker orders): 1 bet (0 open)"))
        assertTrue(block, block.contains("Bids (make orders that filled): 1 bid (0 open)"))
        assertTrue(text, text.contains("BETS vs BIDS: a BET is a taker order"))
        assertTrue(text, text.contains("placedAs"))
        // Nothing held: no block.
        val none = StringWriter()
        StudyExport.write(none, j, emptyList(), meta, now, File(tmp.root, "export.tmp"))
        assertFalse(none.toString().contains("== BETS AND BIDS APART"))
    }
}
