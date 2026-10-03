package com.tjshea.vigilant.data.study

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetGraderTest
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.StringWriter
import java.time.LocalDate

/**
 * Tj, 2026-10-03: "consider if it is needed or smart to require that prop bets have at least one sharp prop book that agrees that the prop bet is positive EV …"
 * then "Do option 1 and add the props split to the study": the study file splits the props by what the sharp-ranked book said, and simulates what each candidate
 * rule would have kept and dropped, so the data answers the question.
 */
class ScanStudyPropsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val start = BetGraderTest.METS_START
    private var now = start - 3 * 3_600_000L
    private val day: LocalDate = FreeScores.etDate(start)
    private val settings = ScanSettings()
    private val event = "New York Mets @ Washington Nationals"
    private val meta = StudyExport.Meta("0.58.1", 101, "moto g", "rules")

    private fun journal() = StudyJournal(File(tmp.root, "study"))

    private fun study(j: StudyJournal = journal()) = ScanStudy(j, clock = { now }, version = { "0.58.1" }, flushEveryMs = 0L, io = Dispatchers.Unconfined)

    /** Novig's price is +130 (2.3): a book whose fair is 0.5 gives it +15%, one whose fair is under 0.435 says it isn't +EV. */
    private fun row(market: String, bet: String, side: Int) = CnoRow(
        ev = 0.45 * 2.3 - 1.0, startsAtMs = start, league = "MLB", sport = "BASEBALL", event = event, market = market, bet = bet, odds = 130, available = 40.0, book = "Novig",
        fairOdds = 120, fairProbability = 0.45, books = 5, gameUrl = "https://x/game.aspx?game_id=9&side_id=$side&devig_method=8",
    )

    private fun snap(vararg rows: CnoRow) = CnoSnapshot("https://cno/view", rows.toList(), fetchedAtMs = now, cnoAgeSeconds = 4, filters = CnoFilters())

    private fun view(vararg prices: CnoBookPrice) = CnoBooksView("bet", "other", null, false, prices.toList(), fetchedAtMs = now - 5_000)

    // The bet side's price and the other side's: even money both ways (fair 0.5: +15% at Novig's +130), or a short price on the bet (fair about 0.37: far under).
    private fun agrees(code: String) = CnoBookPrice(code, -105, null, -105, null)
    private fun says(code: String) = CnoBookPrice(code, 150, null, -200, null)

    private val exchangeAgrees = row("Player Total Bases", "Carson Benge Over 1.5", 1)
    private val exchangeNo = row("Player Hits", "Player B Over 0.5", 2)
    private val bookAgrees = row("Player Strikeouts", "Player C Over 4.5", 3)
    private val bookNo = row("Player Home Runs", "Player D Over 0.5", 4)
    private val noSharp = row("Player RBIs", "Player E Over 0.5", 5)
    private val noPage = row("Player Runs", "Player F Over 0.5", 6)
    private val moneyline = row("Moneyline", "New York Mets", 7)

    private suspend fun logAll(s: ScanStudy) {
        s.observeCno(snap(exchangeAgrees, exchangeNo, bookAgrees, bookNo, noSharp, noPage, moneyline), settings, emptyMap(), emptyMap(), emptyMap())
        now += 30_000
        val pages = mapOf(
            exchangeAgrees.key to view(agrees("KI"), agrees("DK")),
            exchangeNo.key to view(says("PX"), agrees("DK")),
            bookAgrees.key to view(agrees("DK"), CnoBookPrice("KI", -110, null, null, null)),
            bookNo.key to view(says("DK"), agrees("FD")),
            // One MGM price (not a sharp-ranked book) and a ProphetX one side only: nobody ranked prices both sides.
            noSharp.key to view(agrees("MGM"), CnoBookPrice("PX", -110, null, null, null)),
            moneyline.key to view(agrees("DK")),
        ).mapValues { CnoBooksState(view = it.value) }
        s.observeBooks(pages, settings, emptyMap())
        s.flush()
    }

    private fun export(j: StudyJournal): String =
        StringWriter().also { StudyExport.write(it, j, emptyList(), meta, now, File(tmp.root, "export.tmp")) }.toString()

    /** The lines under a section's heading, up to the next heading. */
    private fun section(text: String, heading: String): List<String> {
        val lines = text.lines()
        val i = lines.indexOfFirst { it.startsWith(heading) }
        assertTrue("no '$heading' in the file", i >= 0)
        return lines.drop(i + 1).takeWhile { !it.startsWith("-- ") && !it.startsWith("== ") && it.isNotBlank() }
    }

    private fun count(lines: List<String>, group: String): Int =
        Regex("""^\s*${Regex.escape(group)} · (\d+) bets""").find(lines.first { it.trim().startsWith(group) })!!.groupValues[1].toInt()

    // ---- the splits -----------------------------------------------------------------------------------------------------------

    @Test
    fun `the first book page decides a prop's group, and only props are in the props splits`() = runBlocking {
        val j = journal()
        logAll(study(j))
        val text = export(j)
        val verdicts = section(text, "-- PROPS: what the sharp-ranked book said")
        assertEquals(1, count(verdicts, "an exchange (Kalshi or ProphetX) agrees"))
        assertEquals(1, count(verdicts, "an exchange (Kalshi or ProphetX) says no (vetoed)"))
        assertEquals(1, count(verdicts, "an originating book (FanDuel, Caesars or DraftKings) agrees"))
        assertEquals(1, count(verdicts, "an originating book (FanDuel, Caesars or DraftKings) says no (vetoed)"))
        assertEquals(1, count(verdicts, "no sharp-ranked book prices both sides (not vetoed)"))
        assertEquals(1, count(verdicts, "no book page was read (no verdict)"))
        assertEquals("six props, not seven bets: the moneyline isn't a prop", 6, verdicts.size)
        // Whether the exchanges are on the page at all: both sides on A and B, one side only on C (Kalshi) and E (ProphetX), neither on D, no page on F.
        val ex = section(text, "-- PROPS: are the exchanges")
        assertEquals(2, count(ex, "an exchange prices both sides"))
        assertEquals(2, count(ex, "an exchange prices one side only"))
        assertEquals(1, count(ex, "neither exchange is on the page"))
        assertEquals(1, count(ex, "no book page was read"))
        // The sharp book's own edge: +15% for those that agree, far under zero for those that say no; none for the two without a sharp book.
        val edge = section(text, "-- PROPS: the sharp book's own edge")
        assertEquals(2, count(edge, "sharp edge 4% or more"))
        assertEquals(2, count(edge, "sharp edge under 0%"))
        assertEquals("none for E (nobody ranked prices both sides) or F (no page)", 2, edge.size)
    }

    @Test
    fun `what-if lines say what each rule keeps, drops and can't judge`() = runBlocking {
        val j = journal()
        logAll(study(j))
        val text = export(j)
        assertTrue(text.contains("== WHAT IF PROPS NEEDED A SHARP BOOK"))
        fun rule(prefix: String): Triple<Int, Int, Int> {
            val lines = section(text, "-- $prefix")
            return Triple(count(lines, "KEPT"), count(lines, "DROPPED"), count(lines, "NOT JUDGED"))
        }
        // Today: only a "no" from a sharp-ranked book drops a prop (B and D); the one with no verdict can't be judged (F).
        assertEquals(Triple(3, 2, 1), rule("TODAY'S RULE"))
        // A sharp-ranked book must agree: only A and C stay; E (nobody ranked prices both sides) goes too.
        assertEquals(Triple(2, 3, 1), rule("REQUIRE a sharp-ranked book"))
        // An exchange must agree: only A.
        assertEquals(Triple(1, 4, 1), rule("REQUIRE an exchange"))
    }

    @Test
    fun `results and closes are added up by the new groups like every other split`() = runBlocking {
        val j = journal()
        val s = study(j)
        logAll(s)
        now = start + 4 * 3_600_000L
        val scores = object : ScoreSource {
            override fun covers(league: String) = league == "MLB"
            override suspend fun games(league: String, date: LocalDate): List<GameScore>? =
                if (league == "MLB" && date == LocalDate.of(2026, 9, 26)) {
                    listOf(GameScore("822678", "MLB", "Washington Nationals", "New York Mets", start, true, false, 1, 7, listOf(0, 0, 1, 0, 0, 0, 0, 0, 0), listOf(0, 0, 0, 0, 0, 0, 4, 1, 2)))
                } else emptyList()

            override suspend fun players(game: GameScore): List<PlayerLine>? = BetGraderTest.padded(PlayerLine("Carson Benge", mapOf("TOTAL_BASES" to 4.0)))
        }
        val close = object : CloseSource {
            override val id: String get() = "fake"
            override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> = bets.associate { it.id to CloseLookup.Found(0.55, "Fake · Pinnacle close") }
        }
        s.settle(scores, listOf(close), emptyList(), File(tmp.root, "scratch"))
        val verdicts = section(export(j), "-- PROPS: what the sharp-ranked book said")
        val line = verdicts.first { it.trim().startsWith("an exchange (Kalshi or ProphetX) agrees") }
        // Benge's 4 total bases beat 1.5: a win at +130, closed at 0.55 against a cost of 1/2.3: CLV = 0.55 × 2.3 − 1 = +26.5%.
        assertTrue(line, line.contains("1-0-0 (W-L-P)"))
        assertTrue(line, line.contains("ROI +130.00% (+1.3u on 1u)"))
        assertTrue(line, line.contains("CLV +26.50% on 1 closes"))
    }

    @Test
    fun `no props means no what-if section, and a bet without a check is no verdict`() = runBlocking {
        val j = journal()
        val s = study(j)
        s.observeCno(snap(moneyline), settings, emptyMap(), emptyMap(), emptyMap())
        s.flush()
        val text = export(j)
        assertFalse(text.contains("== WHAT IF PROPS NEEDED A SHARP BOOK"))
        assertFalse(text.contains("-- PROPS:"))
        // The group names and bands are a pure function of the row: a prop without a book check is 'no verdict', one with books and no sharp one is 'no sharp-ranked book'.
        val bare = StudyExport.rowOf(
            StudyBet("id", com.tjshea.vigilant.data.tracker.BetTracker.cnoBet(exchangeAgrees, 0.03, false, null, 1.0, "", "", "cno", null, "id", now), null), now, null,
        )
        assertEquals("no book page was read (no verdict)", StudyExport.propSharp(bare))
        assertEquals(null, StudyExport.propSharpEdge(bare))
        assertEquals("no book page was read", StudyExport.propExchanges(bare))
    }

    @Test
    fun `the READ ME puts Tj's question to Claude with the splits to answer it from, and says how the sample is chosen`() = runBlocking {
        val j = journal()
        logAll(study(j))
        val text = export(j)
        assertTrue(text.contains("TJ'S OPEN QUESTION (2026-10-03): should a prop bet need a sharp prop book"))
        assertTrue(text.contains("Answer it from the PROPS splits"))
        assertTrue(text.contains("PROPS AND THE SHARP BOOK: a verdict exists only for bets whose CNO game page was read"))
        assertTrue(text.contains("PROPS splits and the WHAT IF section use atBet.sharpVerdict"))
        assertNotNull(text.lines().firstOrNull { it.trim().startsWith("8. Deliver") })
    }

    @Test
    fun `the sharp edge bands break at 0, 1, 2 and 4 percent`() {
        val base = StudyExport.rowOf(
            StudyBet("id", com.tjshea.vigilant.data.tracker.BetTracker.cnoBet(exchangeAgrees, 0.03, false, null, 1.0, "", "", "cno", null, "id", now), null), now, null,
        )
        fun band(ev: Double) = StudyExport.propSharpEdge(base.copy(atBet = com.tjshea.vigilant.data.tracker.AtBet(atMs = 1, how = "study", scanner = "cno", checkAtMs = 1, sharpEv = ev)))
        assertEquals("sharp edge under 0%", band(-0.001))
        assertEquals("sharp edge 0 to 1%", band(0.0))
        assertEquals("sharp edge 0 to 1%", band(0.0099))
        assertEquals("sharp edge 1 to 2%", band(0.01))
        assertEquals("sharp edge 2 to 4%", band(0.02))
        assertEquals("sharp edge 4% or more", band(0.04))
        // A bet whose page was never read has no sharp edge, whatever its record says.
        assertEquals(null, StudyExport.propSharpEdge(base.copy(atBet = com.tjshea.vigilant.data.tracker.AtBet(atMs = 1, how = "study", scanner = "cno", sharpEv = 0.05))))
    }
}
