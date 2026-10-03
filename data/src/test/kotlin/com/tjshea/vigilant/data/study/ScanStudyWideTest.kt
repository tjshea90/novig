package com.tjshea.vigilant.data.study

import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.scanner.ScanSettings
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
 * Tj, 2026-10-03: "make it include cno scanned bets that are filtered out of showing up in the vigilant list … log all cno finds on every scan with all the
 * information for each bet cno shows even if these bets don't meet my criteria for showing up in the list in the app. They should still be hidden in the app but
 * logged into the scan study file. The more information the better".
 */
class ScanStudyWideTest {

    @get:Rule val tmp = TemporaryFolder()

    private val start = BetGraderTest.METS_START
    private var now = start - 3 * 3_600_000L
    private val day: LocalDate = FreeScores.etDate(start)
    private val settings = ScanSettings()
    private val event = "New York Mets @ Washington Nationals"

    private fun journal() = StudyJournal(File(tmp.root, "study"))

    private fun study(j: StudyJournal = journal()) = ScanStudy(j, clock = { now }, version = { "0.58.0" }, flushEveryMs = 0L, io = Dispatchers.Unconfined)

    private fun row(
        market: String, bet: String, odds: Int, fair: Double = 0.5122, books: Int = 5, side: Int = 1, startsAt: Long? = start, ev: String? = null,
        cols: Map<String, String> = emptyMap(), eventName: String = event,
    ) = CnoRow(
        ev = fair * com.tjshea.vigilant.engine.Odds.americanToDecimal(odds) - 1.0, startsAtMs = startsAt, league = "MLB", sport = "BASEBALL", event = eventName, market = market, bet = bet,
        odds = odds, available = 40.0, book = "Novig", fairOdds = -105, fairProbability = fair, books = books, gameUrl = "https://x/game.aspx?game_id=9&side_id=$side&devig_method=8",
        cols = cols,
    )

    // What Tj's filters (4+ books, +150 at the longest, 1% at the least) do to each.
    private val shown get() = row("Moneyline", "New York Mets", 105, side = 1)
    private val lowEv get() = row("Total Runs", "Over 8.5", 110, fair = 0.4775, side = 2)
    private val fewBooks get() = row("Player Total Bases", "Carson Benge Over 1.5", 120, fair = 0.4773, books = 2, side = 3)
    private val longOdds get() = row("Player Hits", "Carson Benge Over 0.5", 300, fair = 0.27, side = 4)
    private val skipped get() = row("Total Runs", "Under 8.5", -110, fair = 0.5405, side = 5) // passes the screen; CNO's read under his filters didn't carry it

    private fun narrowSnap(vararg rows: CnoRow, url: String = "https://cno/view") =
        CnoSnapshot(url, rows.toList(), fetchedAtMs = now, cnoAgeSeconds = 4, filters = CnoFilters())

    private fun wideSnap(vararg rows: CnoRow, url: String = "https://cno/view", limit: Int? = 1000, filters: CnoFilters = CnoFilters()) =
        CnoSnapshot(url, rows.toList(), fetchedAtMs = now, cnoAgeSeconds = 4, filters = filters, wide = true, asked = "TextBoxMinimumEVPercentage=0%", limit = limit)

    private suspend fun ScanStudy.cno(s: CnoSnapshot) = observeCno(s, settings, emptyMap(), emptyMap(), emptyMap())

    private suspend fun ScanStudy.wide(w: CnoSnapshot, narrow: CnoSnapshot? = null, set: ScanSettings = settings) =
        observeCnoWide(w, narrow, set, emptyMap(), emptyMap(), emptyMap())

    private fun bets(j: StudyJournal) = j.fold(day).values.associateBy { it.bet.marketLabel + " | " + it.bet.selection }

    // ---- what the wide read logs ----------------------------------------------------------------------------------------------

    @Test
    fun `every row the wide read finds is a bet of its own, flagged with why the app's list would hide it`() = runBlocking {
        val j = journal()
        val s = study(j)
        val narrow = narrowSnap(shown)
        assertEquals(5, s.wide(wideSnap(shown, lowEv, fewBooks, longOdds, skipped), narrow))
        s.flush()
        val b = bets(j)
        assertEquals(5, b.size)
        assertNull(b.getValue("Moneyline | New York Mets").screen)
        assertEquals("EV", b.getValue("Total Runs | Over 8.5").screen)
        assertEquals("BOOKS", b.getValue("Player Total Bases | Carson Benge Over 1.5").screen)
        assertEquals("ODDS", b.getValue("Player Hits | Carson Benge Over 0.5").screen)
        // The screen passes it, CNO's read under his filters didn't carry it.
        assertEquals(Line.NOT_LISTED, b.getValue("Total Runs | Under 8.5").screen)
        // Each is a full record as first listed, with a wide look.
        for (sb in b.values) {
            assertEquals(Sight.WIDE, sb.sights.first().second.k)
            assertEquals(180L, sb.bet.atBet!!.minutesToStart)
            assertEquals("study", sb.bet.atBet!!.how)
            assertNull("the rules summary isn't copied onto thousands of bets", sb.bet.atBet!!.rules)
        }
        assertEquals(300, b.getValue("Player Hits | Carson Benge Over 0.5").sights.first().second.o)
        assertEquals(2, b.getValue("Player Total Bases | Carson Benge Over 1.5").sights.first().second.b)
    }

    @Test
    fun `the wide read only flags NOT_LISTED when it knows what the app's list carried`() = runBlocking {
        // No list read at all, one of another view, one from long before: it can't tell, so the screen's own verdict stands.
        for (narrow in listOf<CnoSnapshot?>(null, narrowSnap(shown, url = "https://cno/other"), narrowSnap(shown).copy(fetchedAtMs = now - 2 * 60_000L), narrowSnap(shown).copy(filters = CnoFilters(minBooks = 2)))) {
            val j = StudyJournal(File(tmp.newFolder(), "study"))
            val s = study(j)
            s.wide(wideSnap(skipped), narrow)
            s.flush()
            assertNull("narrow = ${narrow?.url} ${narrow?.fetchedAtMs}", j.fold(day).values.single().screen)
        }
    }

    @Test
    fun `a bet both reads find is one bet with a look from each, and one only the wide read finds is not an app-list bet`() = runBlocking {
        val j = journal()
        val s = study(j)
        val narrow = narrowSnap(shown)
        s.cno(narrow)
        now += 5_000
        assertEquals(1, s.wide(wideSnap(shown, lowEv), narrow))
        s.flush()
        val b = bets(j)
        assertEquals(2, b.size)
        val both = b.getValue("Moneyline | New York Mets")
        assertEquals(listOf(Sight.CNO, Sight.WIDE), both.sights.map { it.second.k })
        val rowBoth = StudyExport.rowOf(both, now, null)
        assertEquals("c+w", rowBoth.src)
        assertNotNull(rowBoth.listedMin)
        // Only the wide read found it: it was never in a list the app shows, so "how long it stayed listed" is not a thing for it.
        val hiddenRow = StudyExport.rowOf(b.getValue("Total Runs | Over 8.5"), now, null)
        assertEquals("w", hiddenRow.src)
        assertEquals("EV", hiddenRow.screen)
        assertNull(hiddenRow.listedMin)
        assertNull(hiddenRow.lastListedMinToStart)
        assertFalse(hiddenRow.gone)
        assertEquals(110, hiddenRow.american)
        assertEquals(110, hiddenRow.bestAmerican)
    }

    @Test
    fun `a bet the wide read found first and the list carries later is still one bet, its first-look flag kept`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.wide(wideSnap(lowEv), narrowSnap(shown))
        now += 90_000
        // CNO's list now carries it (its EV rose over the floor).
        val better = row("Total Runs", "Over 8.5", 125, fair = 0.4775, side = 2)
        assertEquals(0, s.cno(narrowSnap(better)))
        s.flush()
        val bet = j.fold(day).values.single()
        assertEquals("EV", bet.screen)
        assertEquals(listOf(Sight.WIDE, Sight.CNO), bet.sights.map { it.second.k })
        assertEquals(1, j.read(day).count { it.e == Line.BET })
    }

    // ---- everything CNO shows for the row ----------------------------------------------------------------------------------------

    @Test
    fun `every column CNO printed is logged once per bet, whichever read found the bet first`() = runBlocking {
        val j = journal()
        val s = study(j)
        val cols = mapOf("Bet Name" to "New York Mets", "Odds" to "+105 (\$40)", "Sportsbook" to "Novig", "Extra" to "", "@data-fairpercentage" to "0.5122")
        val narrow = narrowSnap(shown)
        s.cno(narrow) // the app's list found it first: no columns kept from its reads
        now += 5_000
        s.wide(wideSnap(row("Moneyline", "New York Mets", 105, cols = cols), lowEv.copy(cols = mapOf("Event" to "x"))), narrow)
        now += 120_000
        s.wide(wideSnap(row("Moneyline", "New York Mets", 105, cols = cols + ("Odds" to "+106 (\$30)")), lowEv.copy(cols = mapOf("Event" to "x"))), narrowSnap(shown))
        s.flush()
        assertEquals(2, j.read(day).count { it.e == Line.CNO_COLS })
        val b = bets(j)
        assertEquals(cols, b.getValue("Moneyline | New York Mets").cols)
        assertEquals(mapOf("Event" to "x"), b.getValue("Total Runs | Over 8.5").cols)
        val line = StudyExport.rowOf(b.getValue("Moneyline | New York Mets"), now, null)
        assertEquals(cols, line.cols)
        // The columns are not in the bet's own record or its looks: logged once, on a line of their own.
        assertTrue(j.read(day).filter { it.e == Line.BET }.all { it.b!!.atBet != null && it.c == null })
    }

    // ---- looks, drops, restarts -------------------------------------------------------------------------------------------------

    @Test
    fun `a bet the wide read stops finding is logged as gone, and a capped read calls nothing gone`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.wide(wideSnap(shown, lowEv), null)
        now += 61_000
        s.wide(wideSnap(shown), null)
        // As many rows as it asked for: lowEv may be past the cut, not gone.
        now += 61_000
        s.wide(wideSnap(lowEv, limit = 1), null)
        now += 61_000
        // Back, then really gone while the read has room.
        s.wide(wideSnap(shown, lowEv), null)
        now += 61_000
        s.wide(wideSnap(lowEv), null)
        s.flush()
        val b = bets(j)
        assertEquals(listOf(Sight.WIDE, Sight.GONE_WIDE, Sight.WIDE), b.getValue("Moneyline | New York Mets").sights.map { it.second.k }.take(3))
        // The capped read didn't call the Mets moneyline gone (the read before it already had), and the last read did call it gone again after it was back.
        assertEquals(listOf(Sight.WIDE, Sight.GONE_WIDE, Sight.WIDE, Sight.GONE_WIDE), b.getValue("Moneyline | New York Mets").sights.map { it.second.k })
        val row = StudyExport.rowOf(b.getValue("Moneyline | New York Mets"), now, null)
        assertFalse("gone from the wide read is not gone from the app's list", row.gone)
    }

    @Test
    fun `a changed view or filters call nothing gone across the change`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.wide(wideSnap(shown, lowEv), null)
        now += 61_000
        s.wide(wideSnap(lowEv, filters = CnoFilters(minBooks = 2)), null)
        s.flush()
        assertEquals(listOf(Sight.WIDE), bets(j).getValue("Moneyline | New York Mets").sights.map { it.second.k })
    }

    @Test
    fun `a wide read is logged only when it's on, fresh and new, and a switched-off study logs nothing`() = runBlocking {
        val j = journal()
        val s = study(j)
        val w = wideSnap(shown)
        assertEquals(0, s.wide(w, null, settings.copy(scanStudyHidden = false)))
        assertEquals(0, s.wide(w, null, settings.copy(scanStudy = false)))
        assertEquals(1, s.wide(w, null))
        assertEquals("the same read twice", 0, s.wide(w, null))
        now += 5 * 60_000L
        assertEquals("a read from five minutes ago is not a scan", 0, s.wide(wideSnap(lowEv).copy(fetchedAtMs = now - 5 * 60_000L), null))
        s.flush()
        assertEquals(1, j.fold(day).size)
        // The app's own list logs exactly as before with the wide read off.
        assertEquals(1, s.cno(narrowSnap(lowEv)))
    }

    @Test
    fun `after a restart nothing the wide read already logged is logged again, its columns included`() = runBlocking {
        val j = journal()
        val cols = mapOf("Bet Name" to "x")
        val a = study(j)
        a.wide(wideSnap(row("Moneyline", "New York Mets", 105, cols = cols), lowEv), null)
        a.flush()
        val bets = j.read(day).count { it.e == Line.BET }
        val sights = j.read(day).count { it.e == Line.SIGHT }
        now += 30_000
        val b = study(j)
        assertEquals(0, b.wide(wideSnap(row("Moneyline", "New York Mets", 105, cols = cols), lowEv), null))
        b.flush()
        assertEquals(bets, j.read(day).count { it.e == Line.BET })
        assertEquals("a look inside the minute isn't another line", sights, j.read(day).count { it.e == Line.SIGHT })
        assertEquals(2, j.read(day).count { it.e == Line.CNO_COLS } + j.read(day).count { it.e == Line.CNO_COLS && false })
        // A price move after the minute is one more look, not a second bet.
        now += 70_000
        assertEquals(0, b.wide(wideSnap(row("Moneyline", "New York Mets", 110, cols = cols), lowEv), null))
        b.flush()
        assertEquals(sights + 1, j.read(day).count { it.e == Line.SIGHT })
        assertEquals(bets, j.read(day).count { it.e == Line.BET })
    }

    // ---- grading ---------------------------------------------------------------------------------------------------------------------

    private class FakeScores : ScoreSource {
        override fun covers(league: String) = league == "MLB"
        override suspend fun games(league: String, date: LocalDate): List<GameScore>? =
            if (league == "MLB" && date == LocalDate.of(2026, 9, 26)) {
                listOf(GameScore("822678", "MLB", "Washington Nationals", "New York Mets", BetGraderTest.METS_START, true, false, 1, 7, listOf(0, 0, 1, 0, 0, 0, 0, 0, 0), listOf(0, 0, 0, 0, 0, 0, 4, 1, 2)))
            } else emptyList()

        override suspend fun players(game: GameScore): List<PlayerLine>? = BetGraderTest.padded(PlayerLine("Carson Benge", mapOf("TOTAL_BASES" to 4.0)))
    }

    private class FakeClose : CloseSource {
        val askedFor = mutableListOf<String>()
        override val id: String get() = "fake"
        override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
            askedFor += bets.map { it.selection }
            return bets.associate { it.id to CloseLookup.Found(0.55, "Fake · Pinnacle close") }
        }
    }

    @Test
    fun `a futures bet is logged but never graded or closed, and the rest of the day still is`() = runBlocking {
        val j = journal()
        val s = study(j)
        val future = row("Outright Winner", "New York Mets", 900, fair = 0.12, side = 6, eventName = "2026 World Series")
        s.wide(wideSnap(shown, future), null)
        s.flush()
        assertEquals("NOT_A_GAME", bets(j).getValue("Outright Winner | New York Mets").screen)
        now = start + 4 * 3_600_000L
        val close = FakeClose()
        val report = s.settle(FakeScores(), listOf(close), emptyList(), File(tmp.root, "scratch"))
        assertEquals(1, report.looked)
        assertEquals(1, report.graded)
        assertEquals(listOf("New York Mets"), close.askedFor)
        assertEquals(BetStatus.PENDING, bets(j).getValue("Outright Winner | New York Mets").bet.status)
        assertEquals(BetStatus.WON, bets(j).getValue("Moneyline | New York Mets").bet.status)
    }

    @Test
    fun `a day with more bets than a pass takes grades the app's own list's bets first and the rest on the next pass`() = runBlocking {
        val j = journal()
        val s = study(j)
        // The hidden one is listed first, as a busy wide read would, and its game is the same.
        s.wide(wideSnap(lowEv, shown), narrowSnap(shown))
        s.flush()
        now = start + 4 * 3_600_000L
        val close = FakeClose()
        val first = s.settle(FakeScores(), listOf(close), emptyList(), File(tmp.root, "scratch"), batch = 1)
        assertEquals(2, first.looked)
        assertEquals(1, first.graded)
        assertEquals(BetStatus.WON, bets(j).getValue("Moneyline | New York Mets").bet.status)
        assertEquals(BetStatus.PENDING, bets(j).getValue("Total Runs | Over 8.5").bet.status)
        val second = s.settle(FakeScores(), listOf(close), emptyList(), File(tmp.root, "scratch"), batch = 1)
        assertEquals(1, second.graded)
        assertEquals(BetStatus.LOST, bets(j).getValue("Total Runs | Over 8.5").bet.status)
    }

    // ---- the file -----------------------------------------------------------------------------------------------------------------

    private val meta = StudyExport.Meta("0.58.0", 100, "moto g", "rules", wide = "12 rows read 5s ago, asked for up to 1000 · form as posted: TextBoxMinimumEVPercentage=0%")

    @Test
    fun `the file sums the shown and the hidden bets apart, says what the wide read asked, and carries every hidden bet's columns`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.wide(wideSnap(shown, lowEv.copy(cols = mapOf("Bet Name" to "Over 8.5", "@data-fairpercentage" to "0.4775")), fewBooks), narrowSnap(shown))
        s.flush()
        val out = StringWriter()
        val n = StudyExport.write(out, j, emptyList(), meta, now, File(tmp.root, "export.tmp"))
        assertEquals(3, n)
        val text = out.toString()
        assertTrue(text.contains("THE WIDE READ: 12 rows read 5s ago"))
        assertTrue(text, Regex("""shown by the app's lists \(screen = none\) · 1 bets""").containsMatchIn(text))
        assertTrue(text, Regex("""hidden from the app's lists \(screen set: the wide read's extra finds\) · 2 bets""").containsMatchIn(text))
        assertTrue(text.contains("ALL BETS · 3 bets"))
        assertTrue(text.contains("-- Would the app's own CNO screen have shown it --"))
        assertTrue(text.contains("hidden: EV"))
        assertTrue(text.contains("hidden: BOOKS"))
        assertTrue(text.contains("NOT_LISTED"))
        assertTrue(text.contains("\"cols\":{\"Bet Name\":\"Over 8.5\",\"@data-fairpercentage\":\"0.4775\"}"))
        // The dictionary explains the kinds and the flags.
        assertTrue(text.contains("w = the WIDE read"))
        assertTrue(text.contains("xc / xw / xv"))
        assertTrue(text.contains("THE QUESTION TJ ASKED OF THE HIDDEN ONES"))
    }

    @Test
    fun `a file over its limit leaves out hidden bets' lines first but still counts every bet`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.wide(wideSnap(shown, lowEv, fewBooks, longOdds), narrowSnap(shown))
        s.flush()
        val one = StringWriter().also { StudyExport.write(it, j, emptyList(), meta, now, File(tmp.root, "export.tmp")) }.toString()
        val rowBytes = one.lines().first { it.startsWith("{") && it.contains("\"market\":\"Moneyline\"") }.length
        val out = StringWriter()
        val n = StudyExport.write(out, j, emptyList(), meta, now, File(tmp.root, "export.tmp"), maxBytes = rowBytes * 2L + 50)
        assertEquals("every bet is counted", 4, n)
        val text = out.toString()
        assertEquals(2, text.lines().count { it.startsWith("{") })
        assertTrue("the shown bet's line is always kept", text.lines().any { it.startsWith("{") && it.contains("\"market\":\"Moneyline\"") })
        assertTrue(text, text.contains("2 more left out of these lines but counted above"))
        assertTrue(text.contains("ALL BETS · 4 bets"))
        assertTrue(text.contains("2 bets (the hidden ones first) are counted in the SUMMARY"))
    }
}
