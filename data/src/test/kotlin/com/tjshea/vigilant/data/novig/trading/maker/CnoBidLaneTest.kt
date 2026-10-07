package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoException
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoSource
import com.tjshea.vigilant.data.cno.NovigLive
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.BidSource
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lane that reads CrazyNinjaOdds for bids priced from it (Tj, 2026-10-07; RESEARCH.md §113-§114): which games' pages are read, how often, when everything stops, and the lines
 * it hands the bid desk. CNO is a fake source; the clock is ours.
 */
class CnoBidLaneTest {

    private var t = 1_800_000_000_000L
    private val start get() = t + 3 * 3_600_000L
    private val settings = ScanSettings(makerSource = BidSource.CNO, maker = true)

    private fun row(n: Int, startOffset: Long = 0L) = CnoRow(
        ev = 0.02, startsAtMs = start + startOffset, sport = "Football", league = "NFL", event = "A$n @ B$n", market = "Player Receiving Yards", bet = "Pat$n Over 50.5", odds = 100,
        book = "Novig", fairProbability = 0.55, books = 8, gameUrl = "https://x/game.aspx?game_id=$n&market_id=2&side_id=$n&devig_method=8",
    )

    private fun view(row: CnoRow, age: Int? = 10, fetched: Long = t) = CnoBooksView(
        bet = row.bet, otherBet = row.bet.replace("Over", "Under"), fetchedAtMs = fetched, cnoAgeSeconds = age,
        prices = listOf(CnoBookPrice("KI", -115, otherOdds = -105), CnoBookPrice("PX", -112, otherOdds = -108), CnoBookPrice("FD", -118, otherOdds = -102), CnoBookPrice("DK", -125, otherOdds = 105)),
    )

    private class FakeSource(val lane: () -> CnoBidLaneTest) : CnoSource {
        var listRows: List<CnoRow> = emptyList()
        var wideRows: List<CnoRow>? = null
        var age: Int? = 10
        var failList: Exception? = null
        val pageReads = mutableListOf<String>()
        var failPage: ((CnoRow) -> Exception?)? = null
        var pageAge: Int? = 10
        override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot {
            failList?.let { throw it }
            return CnoSnapshot(url, listRows, lane().t, cnoAgeSeconds = age, filters = filters)
        }
        override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int): CnoSnapshot? =
            wideRows?.let { CnoSnapshot(url, it, lane().t, cnoAgeSeconds = age, filters = filters, wide = true) }
        override suspend fun books(row: CnoRow): CnoBooksView? {
            failPage?.invoke(row)?.let { throw it }
            pageReads += row.bet
            return lane().view(row, pageAge)
        }
    }

    private val source = FakeSource { this }
    private val feed = CnoFeed(source, clock = { t })
    private val counts = HashMap<String, Long>()

    /** Novig: a market per row (the row's number), the book as of now; rows in [missing] aren't found. */
    private val missing = HashSet<String>()
    private fun market(n: String) = NovigMarket(
        marketId = "m$n", eventId = "ev$n", marketType = "RECEIVING_YARDS", status = "OPEN", description = "Player Receiving Yards", startsTs = start, fee = MarketFee.GAME,
        outcomes = listOf(NovigOutcome("m$n-over", "Over 50.5", "TBD"), NovigOutcome("m$n-under", "Under 50.5", "TBD")),
    )
    private fun novig(rows: List<CnoRow>): Map<String, NovigLive.Resolved> = rows.filter { it.bet !in missing }.associate { r ->
        val n = r.bet.removePrefix("Pat").substringBefore(' ')
        r.key to NovigLive.Resolved(
            market(n), if (r.bet.contains("Over")) "m$n-over" else "m$n-under",
            NovigBook("m$n", 1, mapOf("m$n-over" to listOf(BidLevel(450, 100)), "m$n-under" to listOf(BidLevel(400, 100))), t - 1_000L),
        )
    }

    private val lane = CnoBidLane(feed, { novig(it) }, { "view" }, count = { k, n -> counts.merge(k, n, Long::plus) }, clock = { t }, pageGapMs = 0L, pause = {})

    private fun listRead() = runBlocking { feed.refresh("view", settings.cnoFilters) }
    private fun step(bids: List<MakerBid> = emptyList()) = runBlocking { lane.step(settings, bids) }
    private fun lines(read: Boolean = true, bids: List<MakerBid> = emptyList()) = runBlocking { lane.lines(settings, t, bids, read) }

    // ---- which pages are read ----------------------------------------------------------------------------------------------

    @Test
    fun `a step reads the soonest games' pages first, three at most, and a page already read is not read again for three minutes`() {
        source.listRows = (1..5).map { row(it, startOffset = (5 - it) * 600_000L) }
        listRead()
        step()
        // Soonest start first: game 5 (offset 0), 4, 3.
        assertEquals(listOf("Pat5 Over 50.5", "Pat4 Over 50.5", "Pat3 Over 50.5"), source.pageReads)
        assertEquals(3, lane.status.value.pagesRead)
        t += 15_000L
        listRead(); step()
        assertEquals(listOf("Pat2 Over 50.5", "Pat1 Over 50.5"), source.pageReads.drop(3))
        // Everything is held: nothing is read until the oldest page is three minutes old.
        t += 15_000L
        listRead(); step()
        assertEquals(5, source.pageReads.size)
        t += 3 * 60_000L
        listRead(); step()
        assertEquals(8, source.pageReads.size)
    }

    @Test
    fun `a game with a bid up has its page read again after a minute, ahead of the candidates`() {
        source.listRows = (1..3).map { row(it, startOffset = it * 600_000L) }
        listRead(); step()
        assertEquals(3, source.pageReads.size)
        val bid = bidOn(row(3), "m3-under", "Pat3 Under 50.5")
        t += 30_000L
        listRead(); step(listOf(bid))
        assertEquals(3, source.pageReads.size)
        t += 31_000L
        listRead(); step(listOf(bid))
        // 61 s on: the bid's game (3) is read again; the other pages are 61 s old, well inside three minutes.
        assertEquals(listOf("Pat3 Over 50.5"), source.pageReads.drop(3))
    }

    @Test
    fun `a bid whose row has left the list is still judged - its page is read from a stand-in made from the bid`() {
        source.listRows = listOf(row(1))
        listRead(); step()
        val bid = bidOn(row(7), "m7-under", "Pat7 Under 50.5")
        t += 61_000L
        source.listRows = emptyList()
        listRead(); step(listOf(bid))
        assertTrue(source.pageReads.last().startsWith("Pat7"))
        val set = lines(bids = listOf(bid))
        // The stand-in's page gives the bid's side (and its complement) lines, from the same page.
        assertTrue(set.lines.any { it.outcomeId == "m7-under" })
    }

    @Test
    fun `CNO asked for a pause - nothing is read and every bid from it must come down`() {
        source.listRows = listOf(row(1))
        listRead()
        // CNO answers the next read with a 429: the feed pauses every lane for the 120 s it asked.
        source.failList = CnoException("CrazyNinjaOdds is busy (HTTP 429)", retryAfterSeconds = 120)
        t += 4_000L
        listRead()
        step()
        assertTrue(source.pageReads.isEmpty())
        val stop = lane.stopReason(t)
        assertNotNull(stop)
        assertTrue(stop!!, stop.startsWith("CrazyNinjaOdds asked for a pause"))
        assertEquals(stop, lines().stop)
    }

    @Test
    fun `a page that fails with a pause stops the step, and a page that fails once is not tried again for a minute`() {
        source.listRows = (1..3).map { row(it, startOffset = it * 600_000L) }
        source.failPage = { r -> if (r.bet.startsWith("Pat1")) CnoException("CrazyNinjaOdds is busy (HTTP 429)", retryAfterSeconds = 120) else null }
        listRead(); step()
        assertTrue(source.pageReads.isEmpty())
        assertEquals(1, lane.status.value.failed)
        assertEquals(1L, counts["cno.bid.page.failed"])
    }

    // ---- the stops ---------------------------------------------------------------------------------------------------------

    @Test
    fun `the lane says why every CNO bid must come down - never read, no age, stuck, failing`() {
        assertEquals("CrazyNinjaOdds hasn't been read yet: no bid priced from it", lane.stopReason(t))
        source.listRows = listOf(row(1)); source.age = null
        listRead()
        assertTrue(lane.stopReason(t)!!.contains("doesn't say how old"))
        source.age = 10; t += 4_000L
        listRead()
        assertNull(lane.stopReason(t))
        // Over 10 minutes with no update from CNO.
        assertTrue(lane.stopReason(t + 11 * 60_000L)!!.contains("hasn't updated"))
        // Three reads in a row that failed.
        source.failList = CnoException("down")
        repeat(3) { t += 20_000L; listRead() }
        assertTrue(lane.stopReason(t)!!.contains("3 times in a row"))
    }

    // ---- the lines ---------------------------------------------------------------------------------------------------------

    @Test
    fun `lines come only from rows whose page was read, both sides, with the list's data time and Novig's book`() {
        source.listRows = listOf(row(1), row(2, startOffset = 600_000L))
        listRead()
        // Before any page is read: nothing to judge.
        step()
        val set = lines()
        assertNull(set.stop)
        assertEquals(setOf("m1-over", "m1-under", "m2-over", "m2-under"), set.lines.map { it.outcomeId }.toSet())
        assertEquals(t - 10_000L, set.listAtMs)
        assertTrue(set.lines.all { it.source == "cno" })
        assertEquals(0.60, set.lines.first { it.outcomeId == "m1-over" }.offer!!, 1e-9)
    }

    @Test
    fun `a row Novig cannot find gets no line, and says so`() {
        source.listRows = listOf(row(1), row(2, startOffset = 600_000L))
        missing += "Pat2 Over 50.5"
        listRead(); step()
        val set = lines()
        assertEquals(setOf("m1-over", "m1-under"), set.lines.map { it.outcomeId }.toSet())
        assertEquals(1, set.skipped["Novig doesn't list this exact bet (or its book couldn't be read)"])
    }

    @Test
    fun `the wide read's rows are candidates too - a side the taker list does not carry`() {
        source.listRows = listOf(row(1))
        source.wideRows = listOf(row(1), row(9, startOffset = 1_200_000L).copy(ev = 0.0))
        listRead(); step()
        assertTrue(source.pageReads.contains("Pat9 Over 50.5"))
        assertEquals(2, lane.status.value.wideRows)
    }

    @Test
    fun `a page that is not the list's age - an old page makes its lines old, however fresh the list`() {
        source.listRows = listOf(row(1))
        source.pageAge = 100
        listRead(); step()
        val l = lines().lines.first()
        // The page's data is 100 s old (+ the 0 s since the read): older than the list's 10 s; the fair is that old and still inside 120 s, 20 s from old.
        assertEquals(t - 100_000L, l.fairAsOfMs)
        assertFalse(l.fairOld)
        t += 21_000L
        assertTrue(runBlocking { lane.lines(settings, t, emptyList(), true) }.lines.first().fairOld)
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------------

    private fun bidOn(row: CnoRow, outcomeId: String, selection: String) = MakerBid(
        clientId = "c$outcomeId", orderId = "o$outcomeId", marketId = outcomeId.substringBefore('-'), eventId = "ev", outcomeId = outcomeId, league = "NFL", eventName = row.event,
        startsTs = row.startsAtMs!!, marketLabel = row.market, selection = selection, price = 0.48, contracts = 10, fair = 0.5, evAtFair = 0.04, margin = 0.04,
        source = CnoMakerLines.SOURCE, gameUrl = row.gameUrl, postedAtMs = t, status = MakerStatus.RESTING,
    )
}
