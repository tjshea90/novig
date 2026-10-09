package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.live.Fetched
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The lab recorder end to end on a fake Novig: a halftime football ladder in, would-be bets and their grades out, nothing ever sent. */
class LabRecorderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val event = NovigEvent("ev", "Football", "NFL", NovigEvent.STATUS_LIVE, "Tampa Bay Buccaneers @ Dallas Cowboys", 0L)
    private val ids = listOf("t1" to 36.5, "t2" to 37.5, "t3" to 38.5, "far" to 53.5, "dead" to 52.5, "dead2" to 54.5)

    private fun market(id: String, strike: Double, settled: String? = null) = NovigMarket(
        id, "ev", "TOTAL", "OPEN", "d", 0L, MarketFee.GAME,
        listOf(NovigOutcome("$id-a", "Under $strike", settled?.let { if (id == "far") "WIN" else "LOSS" } ?: "TBD"), NovigOutcome("$id-b", "Over $strike", settled?.let { if (id == "far") "LOSS" else "WIN" } ?: "TBD")), strike,
    )

    private fun bid(mid: Double) = ((mid - 0.02) * 1000).toInt()

    private fun books(): Map<String, NovigBook> = mapOf(
        "t1" to NovigBook("t1", 1, mapOf("t1-a" to listOf(BidLevel(bid(0.38), 5_000)), "t1-b" to listOf(BidLevel(bid(0.62), 5_000))), 0),
        "t2" to NovigBook("t2", 1, mapOf("t2-a" to listOf(BidLevel(bid(0.48), 5_000)), "t2-b" to listOf(BidLevel(bid(0.52), 5_000))), 0),
        "t3" to NovigBook("t3", 1, mapOf("t3-a" to listOf(BidLevel(bid(0.57), 5_000)), "t3-b" to listOf(BidLevel(bid(0.43), 5_000))), 0),
        "far" to NovigBook("far", 1, mapOf("far-b" to listOf(BidLevel(100, 500))), 0),
        "dead" to NovigBook("dead", 1, emptyMap(), 0),
        "dead2" to NovigBook("dead2", 1, emptyMap(), 0),
    )

    private class Fake(val event: NovigEvent, val markets: List<NovigMarket>, val books: Map<String, NovigBook>, val settled: Map<String, NovigMarket> = emptyMap()) : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(event)
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?) = BookBatch(books.filterKeys { it in marketIds }, 0, marketIds.size, 0)
        override suspend fun market(marketId: String): NovigMarket? = settled[marketId] ?: markets.firstOrNull { it.marketId == marketId }
    }

    private val espn = """{"events":[{"status":{"period":2,"displayClock":"0:00","type":{"state":"in"}},"competitions":[{"competitors":[
        {"homeAway":"home","score":"10","team":{"displayName":"Dallas Cowboys","abbreviation":"DAL"}},
        {"homeAway":"away","score":"7","team":{"displayName":"Tampa Bay Buccaneers","abbreviation":"TB"}}]}]}]}"""

    private fun recorder(source: NovigSource, now: () -> Long, fetchBody: String? = espn): LabRecorder {
        val dir = tmp.newFolder()
        return LabRecorder(
            scope = TestScope(), source = source,
            fetch = { fetchBody?.let { Fetched(it, 10L) } },
            altQuotes = { emptyList() },
            journal = DayJournal(dir, "lab", LabRecord.serializer()) { it.atMs },
            gradeJournal = DayJournal(dir, "lab-grade", LabGrade.serializer()) { it.atMs },
            clock = now, gradeEvery = 1,
        )
    }

    @Test
    fun `a halftime ladder gives the best-estimate tail bet once, not again within five minutes, and its result once the market settles`() = runBlocking {
        var now = 1_800_000_000_000L
        val src = Fake(event, ids.map { market(it.first, it.second) }, books(), settled = ids.associate { it.first to market(it.first, it.second, settled = "yes") })
        val rec = recorder(src, { now })
        rec.cycleOnce(setOf("NFL"))
        val first = rec.records()
        val tail = first.single { it.kind == LabKind.TAIL }
        assertEquals("UNDER", tail.side)
        assertEquals(53.5, tail.strike, 0.0)
        assertTrue(tail.note, tail.note.startsWith("explore"))
        assertTrue("no cover on a normal ladder", first.none { it.kind == LabKind.COVER })
        val st = rec.status.value
        assertEquals(1, st.games)
        assertEquals(1, st.withState)
        assertTrue(st.ladders!!, st.ladders!!.contains("1 ladder"))

        now += 20_000
        rec.cycleOnce(setOf("NFL"))
        assertEquals("the same would-be bet is not written twice in five minutes", first.size, rec.records().size)

        now += 40 * 60_000
        rec.grade(now)
        val grade = rec.grades().single()
        assertEquals(tail.id, grade.id)
        assertEquals("WIN", grade.result)
        assertTrue(LabPaper.report(rec.records(), rec.grades()).first().contains("1-0 graded"))
    }

    @Test
    fun `no game state means no tail bets, and a locked cover on the ladder is recorded`() = runBlocking {
        val now = 1_800_000_000_000L
        val coverBooks = books() + mapOf(
            "dead" to NovigBook("dead", 1, mapOf("dead-a" to listOf(BidLevel(550, 1_000))), 0),    // buy Over 52.5 at 0.45
            "far" to NovigBook("far", 1, mapOf("far-b" to listOf(BidLevel(600, 1_000))), 0),      // buy Under 53.5 at 0.40
        )
        val rec = recorder(Fake(event, ids.map { market(it.first, it.second) }, coverBooks), { now }, fetchBody = null)
        rec.cycleOnce(setOf("NFL"))
        val r = rec.records()
        assertTrue("no ESPN answer: no clock, no tail bets", r.none { it.kind == LabKind.TAIL })
        val covers = r.filter { it.kind == LabKind.COVER }
        assertTrue("the 52.5 Over at 0.45 with the 53.5 Under at 0.40 (and the lower liquid Overs with that Under, which also cost under a dollar)", covers.any { Math.abs(it.ask - 0.85) < 1e-9 })
        assertEquals(covers.size, rec.status.value.covers)
        assertEquals(0, rec.status.value.withState)
    }
}
