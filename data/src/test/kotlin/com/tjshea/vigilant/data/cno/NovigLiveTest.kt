package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Novig's price now for CNO's listed bets (Tj, 2026-09-27: "consider ways to make cno respond
 * even with high traffic and rapid refreshing"): read from Novig's own order book, re-priced at
 * each CNO read without a request, EV against CNO's fair odds.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NovigLiveTest {

    private val start = 10_000_000L

    private fun market(id: String) = NovigMarket(
        marketId = id, eventId = "E1", marketType = "RECEPTIONS", status = "OPEN", description = "X 4.5 RECEPTIONS",
        startsTs = start + 3_600_000, fee = MarketFee.GAME,
        outcomes = listOf(NovigOutcome("$id-over", "Over 4.5", "TBD"), NovigOutcome("$id-under", "Under 4.5", "TBD")),
    )

    /** Every order is a bid: a bid of [overBid] on Over is what Under can be taken at (1 − bid). */
    private fun book(id: String, overBid: Int, contracts: Long = 20_000, at: Long) =
        NovigBook(id, 1, mapOf("$id-over" to listOf(BidLevel(overBid, contracts)), "$id-under" to emptyList()), at)

    private fun row(i: Int, odds: Int = 110, fair: Double = 0.50, book: String = "Novig") =
        CnoRow(fair * 100.0 / 100.0 * (1 + odds / 100.0) - 1, start + 3_600_000, "Football", "NFL", "A @ B", "Player Receptions", "P$i Under 4.5", odds, 50.0, book, null, fair, 6, "https://x/game.aspx?side_id=$i")

    @Test
    fun `the price is the other side's best bid flipped, and the EV is CNO's fair odds against it`() {
        val m = market("m1")
        // Over bid at 0.52 → Under takes at 0.48 (+108); CNO's fair 50%: EV 0.50/0.48 − 1 = +4.2%, pregame (no fee).
        val p = NovigLive.priceOf(row(1, fair = 0.50), m, book("m1", 520, at = start), "m1-under", start)!!
        assertEquals(108, p.american)
        assertEquals(0.50 / 0.48 - 1, p.ev!!, 1e-9)
        assertEquals(20_000 * 0.48 * 0.01, p.available!!, 1e-9) // $96 at that price
        // Nothing to take: no price.
        assertNull(NovigLive.priceOf(row(1), m, NovigBook("m1", 1, emptyMap(), start), "m1-under", start))
        // After the start, Novig's taker fee comes out of the EV.
        val live = NovigLive.priceOf(row(1, fair = 0.50), m, book("m1", 520, at = start), "m1-under", start + 4_000_000)!!
        assertTrue(live.ev!! < p.ev!!)
    }

    /** Books that count requests; [overBid] per market is what the next read returns. */
    private inner class Source : NovigSource {
        val overBid = HashMap<String, Int>()
        var reads = 0
        var clock: () -> Long = { 0L }
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?): List<NovigEvent> = emptyList()
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?): List<NovigMarket> = emptyList()
        override suspend fun market(marketId: String): NovigMarket = this@NovigLiveTest.market(marketId)
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            reads += marketIds.size
            return BookBatch(marketIds.associateWith { book(it, overBid[it] ?: 520, at = clock()) }, 0, marketIds.size, 0)
        }
    }

    private fun finder(i: Int): NovigBetFinder.Found? = when (i) {
        9 -> NovigBetFinder.Found.Game("E9") // the catalog couldn't pin this one down
        else -> NovigBetFinder.Found.Bet("m$i-under", "E1", "m$i")
    }

    @Test
    fun `books are read every few seconds, and a CNO refresh is re-priced at once with no request`() = runTest {
        val source = Source().also { it.clock = { currentTime } }
        val live = NovigLive(source, { r -> finder(r.bet.removePrefix("P").substringBefore(' ').toInt()) }, clock = { currentTime })
        val rows = MutableStateFlow(listOf(row(1), row(2), row(9), row(3, book = "FanDuel")))
        val job = launch { live.keepFresh(rows) }
        runCurrent()
        // Rows 1 and 2 priced; 9 (only its game found) and 3 (not a Novig bet) left as CNO had them.
        assertEquals(setOf(row(1).key, row(2).key), live.prices.value.keys)
        assertEquals(2, source.reads)
        assertEquals(108, live.prices.value[row(1).key]!!.american)
        // Novig moves: the next read (15 s on) shows it.
        source.overBid["m1"] = 560 // Under now 0.44 (+127)
        advanceTimeBy(NovigLive.LIVE_EVERY_MS - 1)
        assertEquals(2, source.reads)
        advanceTimeBy(2)
        assertEquals(4, source.reads)
        assertEquals(127, live.prices.value[row(1).key]!!.american)
        // CNO re-reads its list (new fair odds for row 1): re-priced from the books already read.
        rows.value = listOf(row(1, fair = 0.48), row(2), row(9), row(3, book = "FanDuel"))
        runCurrent()
        assertEquals(4, source.reads)
        assertEquals(0.48 / 0.44 - 1, live.prices.value[row(1).key]!!.ev!!, 1e-9)
        job.cancel()
    }

    @Test
    fun `only the top bets are read, and an empty list reads nothing`() = runTest {
        val source = Source().also { it.clock = { currentTime } }
        val live = NovigLive(source, { r -> finder(r.bet.removePrefix("P").substringBefore(' ').toInt()) }, clock = { currentTime })
        val rows = MutableStateFlow((1..8).map { row(it) } + (10..20).map { row(it) })
        val job = launch { live.keepFresh(rows, top = NovigLive.LIVE_TOP) }
        runCurrent()
        assertEquals(NovigLive.LIVE_TOP, source.reads)
        rows.value = emptyList()
        advanceTimeBy(60_000)
        assertTrue(live.prices.value.isEmpty())
        assertEquals(NovigLive.LIVE_TOP, source.reads)
        job.cancel()
    }

    @Test
    fun `a bet the catalog couldn't look up (Novig busy) is asked again a minute later`() = runTest {
        val source = Source().also { it.clock = { currentTime } }
        var busy = true
        val live = NovigLive(source, { r -> if (busy) NovigBetFinder.Found.Game("E1", searched = false) else finder(1) }, clock = { currentTime })
        val job = launch { live.keepFresh(MutableStateFlow(listOf(row(1)))) }
        runCurrent()
        assertTrue(live.prices.value.isEmpty())
        busy = false
        advanceTimeBy(NovigLive.RETRY_MS + NovigLive.LIVE_EVERY_MS)
        assertEquals(setOf(row(1).key), live.prices.value.keys)
        job.cancel()
    }

    @Test
    fun `a background check reads Novig's price now once, for the Novig bets it asks about`() = runTest {
        val source = Source().also { it.clock = { currentTime } }
        val live = NovigLive(source, { r -> finder(r.bet.removePrefix("P").substringBefore(' ').toInt()) }, clock = { currentTime })
        source.overBid["m1"] = 540 // Under takes at 0.46 (+117)
        val got = live.readNow(listOf(row(1), row(2), row(9), row(3, book = "FanDuel")))
        // One read per pinned-down Novig bet; the game-only one and the FanDuel one are left out.
        assertEquals(setOf(row(1).key, row(2).key), got.keys)
        assertEquals(2, source.reads)
        assertEquals(117, got.getValue(row(1).key).american)
        // The lists see them too, until the screen's own loop takes over.
        assertEquals(got, live.prices.value)
        // Asked again: read again (a background check wants the price now, not the last one).
        live.readNow(listOf(row(1)))
        assertEquals(3, source.reads)
        assertTrue(live.readNow(emptyList()).isEmpty())
        assertEquals(3, source.reads)
    }
}
