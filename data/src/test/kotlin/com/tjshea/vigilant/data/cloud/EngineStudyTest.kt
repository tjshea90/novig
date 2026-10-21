package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats
import com.tjshea.vigilant.data.study.ScanStudy
import com.tjshea.vigilant.data.study.StudyJournal
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The engine's own scan study (TCL20e): a finished scan is logged in the app's journal format with the ids that tie an order to its result. */
class EngineStudyTest {
    private val now = System.currentTimeMillis()
    private val start = now + 6 * 3_600_000L

    private fun row(id: String): Opportunity {
        val ev = NovigEvent("e-$id", "BASKETBALL", "NBA", NovigEvent.STATUS_PREGAME, "Boston Celtics @ New York Knicks", start)
        val market = NovigMarket("m-$id", "e-$id", "SPREAD", "OPEN", "Spread", start, MarketFee.GAME, listOf(NovigOutcome("o-$id", "NYK -3.5", "TBD"), NovigOutcome("x-$id", "BOS +3.5", "TBD")), strike = -3.5)
        fun book(k: String, sharp: Boolean) = BookFair(BookPrices(k, k, listOf(1.78, 2.15), now - 30_000), listOf(0.54, 0.46), 0.03, sharp)
        val fair = FairLine(
            probabilities = listOf(0.53, 0.47), requestedSource = FairSource.BLEND, sourceUsed = FairSource.BLEND, method = DevigMethod.WORST_CASE,
            perBook = listOf(book("pinnacle", true), book("dk", false), book("fd", false)), sharpBooksUsed = listOf("pinnacle"), averageBooksUsed = listOf("dk", "fd"),
        )
        return Opportunity(
            league = Leagues.byNovigName("NBA")!!, event = ev, market = market, outcome = market.outcomes.first(), marketLabel = "Spread", kind = LineKind.SPREAD,
            selection = "NYK -3.5", fair = fair, fairProbability = 0.53, quote = EvQuote(0.53, 0.48, 0.0075), ladder = listOf(TakeLevel(0.48, 5_000)), depth = null,
            suggestedStake = null, novigWidth = null, bookFetchedAtMs = now - 5_000, fairUpdatedMs = now - 30_000, refEvent = null,
            lineKey = LineKey("ref-$id", LineKind.SPREAD, -3.5, 0, null, null), target = OutcomeTarget.Is(Side.HOME), fairAsOfMs = now - 30_000,
        )
    }

    @Test
    fun `a finished scan is logged with its market and outcome ids, and the grader's result for those ids settles the engine's order`() = runBlocking {
        val dir = Files.createTempDirectory("engine-study").toFile()
        try {
            val study = ScanStudy(StudyJournal(dir), version = { "github-engine" }, flushEveryMs = 0)
            val s = ScanSettings(scanStudy = true, leagues = setOf("NBA"), minEvPercent = 0.0)
            val result = ScanResult(emptyList(), listOf(row("a")), ScanStats(1, 1, 1, 1, 1), now)
            val created = study.observeVigilant(result, s)
            study.flush()
            assertEquals(1, created)
            val journal = StudyJournal(dir)
            val bets = journal.days().flatMap { journal.fold(it).values }
            val bet = bets.single().bet
            assertEquals("m-a", bet.marketId)
            assertEquals("o-a", bet.outcomeId)
            // the same scan again logs no new bet (it is watched, not re-listed)
            assertEquals(0, study.observeVigilant(result.copy(computedAtMs = now + 1), s))
            // once the grader finds the bet won, the order on the same outcome is settled from it
            val entry = TradeEntry(
                id = "x", atMs = now, mode = "PAPER", marketId = "m-a", outcomeId = "o-a", eventId = "e-a", event = "Boston Celtics @ New York Knicks", league = "NBA", kind = "Spread", marketLabel = "Spread",
                selection = "NYK -3.5", startsTs = start, contracts = 200, avgPrice = 0.48, cost = 1.0, fee = 0.0, fair = 0.53, evPercent = 0.1, stars = 4.0, fairAgeS = 30, books = 3, note = "",
            )
            val settled = TradeSettle.settle(listOf(entry)) { m, o -> if (m == bet.marketId && o == bet.outcomeId) TradeSettle.Outcome(BetStatus.WON) else null }
            assertEquals(1.0, settled.single().pnl, 1e-9)
            assertTrue(File(dir, "study-${journal.days().single()}.jsonl").length() > 0)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the wide log also keeps lines the list did not show, with why, and only the engine asks for it (Tj, 2026-10-16)`() = runBlocking {
        val dir = Files.createTempDirectory("engine-study-wide").toFile()
        try {
            val study = ScanStudy(StudyJournal(dir), version = { "github-engine" }, flushEveryMs = 0)
            val s = ScanSettings(scanStudy = true, leagues = setOf("NBA"), minEvPercent = 0.03)
            val shown = row("a")
            val nearMiss = row("b").copy(selection = "NYK -4.5", fairProbability = 0.485, quote = EvQuote(0.485, 0.48, 0.0075))
            val far = row("c").copy(selection = "NYK -5.5", fairProbability = 0.40, quote = EvQuote(0.40, 0.48, 0.0075))
            val result = ScanResult(emptyList(), listOf(shown, nearMiss, far), ScanStats(3, 3, 3, 3, 3), now)
            assertEquals("the phone's study logs what its list shows", 1, study.observeVigilant(result, s))
            study.flush()
            val wide = ScanStudy(StudyJournal(Files.createTempDirectory("engine-study-wide2").toFile()), version = { "github-engine" }, flushEveryMs = 0)
            assertEquals("the engine also logs the near miss, not the far one", 2, wide.observeVigilant(result, s, wideFloor = -0.02))
            wide.flush()
            val j = StudyJournal(dir)
            assertEquals(1, j.days().flatMap { j.fold(it).values }.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    // XO2 (2026-10-21): 82 of 459 started engine orders were never graded: a bid fills where the Novig ask sits over 2% above fair, so no scan logged that line and the grader only grades the journal.
    @Test
    fun `an order whose line no scan logged is added to the journal as a bet of its own once its game has started, once, and a known line or a future game is left alone`() = runBlocking {
        val dir = Files.createTempDirectory("engine-study-orders").toFile()
        try {
            val started = now - 5 * 3_600_000L
            val study = ScanStudy(StudyJournal(dir), clock = { now }, version = { "github-grader" }, flushEveryMs = 0, io = kotlinx.coroutines.Dispatchers.Unconfined)
            fun order(m: String, o: String, starts: Long) = ScanStudy.OrderLine(
                id = "ord-$o", atMs = starts - 2 * 3_600_000L, league = "NHL", event = "Calgary Flames @ Vegas Golden Knights", startsTs = starts, marketLabel = "Team Total", selection = "Vegas Golden Knights Under 3.5",
                marketId = m, outcomeId = o, eventId = "ev-$m", price = 0.505, fair = 0.5154, evPercent = 0.0205, note = "bid",
            )
            val orders = listOf(order("m1", "o1", started), order("m2", "o2", started), order("m3", "o3", now + 3_600_000L), order("", "o4", started))
            val added = study.addOrders(orders, known = setOf("m2" to "o2"))
            assertEquals("m1/o1 only: m2/o2 is already in the journal, m3 has not started, m4 has no market id", 1, added)
            val j = StudyJournal(dir)
            val bets = j.days().flatMap { j.fold(it).values }
            val b = bets.single().bet
            assertEquals("m1", b.marketId); assertEquals("o1", b.outcomeId)
            assertEquals(0.505, b.price, 1e-9); assertEquals(BetStatus.PENDING, b.status)
            assertEquals("the kind the dataset reads", "TEAM_TOTAL", b.atBet?.kind)
            assertTrue(b.atBet?.rules.orEmpty().startsWith("NOT SHOWN (an order of the engine's"))
            assertEquals("minutes to the start at the fill", 120L, b.atBet?.minutesToStart)
            // a second run knows the journal now: nothing is added twice
            val known = j.days().flatMap { j.fold(it).values }.map { it.bet.marketId to it.bet.outcomeId }.toSet()
            assertEquals(0, ScanStudy(StudyJournal(dir), clock = { now }, flushEveryMs = 0, io = kotlinx.coroutines.Dispatchers.Unconfined).addOrders(orders, known))
        } finally {
            dir.deleteRecursively()
        }
    }
}
