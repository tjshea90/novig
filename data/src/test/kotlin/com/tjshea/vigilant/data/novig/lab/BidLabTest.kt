package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The paper bid lab (RESEARCH.md §122): recipes, the guard, fills from the trade tape, the close, the grade and the tables. */
class BidLabTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val t0 = 1_800_000_000_000L
    private val pre = BidLab.VARIANTS.filter { !it.live }
    private val live = BidLab.VARIANTS.filter { it.live }

    private fun line(fair: Double = 0.50, offer: Double? = 0.52, live: Boolean = false, startsIn: Long = 6 * 3_600_000L, outcome: String = "o1") = LabLine(
        outcome, "m1", "e1", "A @ B", "NFL", "PROP", "Player Under 4.5", t0 + startsIn, live, fair, offer, 0.45, 4,
    )

    private fun lab(trades: List<TrapGuard.Trade> = emptyList(), status: String? = null): Triple<BidLab, DayJournal<BidLabBid>, DayJournal<BidLabEvent>> {
        val dir = tmp.newFolder()
        val bj = DayJournal(dir, "bids", BidLabBid.serializer()) { it.atMs }
        val ej = DayJournal(dir, "events", BidLabEvent.serializer()) { it.atMs }
        val mk = NovigMarket("m1", "e1", "TOTAL", "OPEN", "d", 0L, MarketFee.GAME, listOf(NovigOutcome("o1", "Under 4.5", status ?: "TBD"), NovigOutcome("o2", "Over 4.5", "TBD")), 4.5)
        return Triple(BidLab({ trades }, { mk }, bj, ej), bj, ej)
    }

    @Test
    fun `a pregame line gets one paper bid per recipe, each under Novig's offer, a live line only the live recipes, and no bid when the offer is under the price`() {
        val (l, bj, _) = lab()
        l.observe(listOf(line()), t0)
        val bids = bj.readAll()
        assertEquals(pre.size, bids.size)
        assertTrue(bids.all { it.price < 0.52 - BidLab.TICK && !it.live })
        assertEquals("margin 4% under a fair of 0.50", PriceGrid4(), bids.first { it.variant == "pre-m4-t30m" }.price, 1e-9)
        l.observe(listOf(line()), t0 + 1_000)
        assertEquals("a recipe that has a bid up does not post another", pre.size, bj.readAll().size)
        l.observe(listOf(line(fair = 0.50, offer = 0.52, live = true, outcome = "o9")), t0 + 2_000)
        assertEquals(pre.size + live.size, bj.readAll().size)
        val (l2, bj2, _) = lab()
        l2.observe(listOf(line(offer = 0.45)), t0)
        assertTrue("an offer under every price: nothing to bid", bj2.readAll().isEmpty())
        val (l3, bj3, _) = lab()
        l3.observe(listOf(line(startsIn = 4 * 60_000L)), t0)
        assertTrue("a game about to start gets no paper bid", bj3.readAll().isEmpty())
    }

    private fun PriceGrid4() = com.tjshea.vigilant.engine.PriceGrid.floor(0.50 / 1.04)!!

    @Test
    fun `the guard pulls a recipe when the fair falls under its price plus one percent, the others stay`() {
        val (l, _, ej) = lab()
        l.observe(listOf(line()), t0)
        l.observe(listOf(line(fair = 0.48)), t0 + 5_000)   // 0.48 / 1.01 = 0.4752: the 2% and 3% bids (0.490, 0.485) are over it
        val cancelled = ej.readAll().filter { it.type == "CANCEL" }
        assertEquals(setOf(true), cancelled.map { it.value!! < 0.5 }.toSet())
        assertTrue("some guard recipes were pulled", cancelled.isNotEmpty())
        assertTrue("and not all of them: the 6% bid at 0.47 is still under the fair", cancelled.size < pre.count { it.guard } )
        val (counts) = lab().let { listOf(l.counts()) }
        assertTrue(counts.first > 0)
    }

    @Test
    fun `a trade that prints at or under the price after the bid went up fills it, strictly through marks the fill certain, and one before it or above it does not`() = runBlocking {
        val tape = listOf(
            TrapGuard.Trade("o1", 0.47, 500, t0 + 60_000),    // through every recipe's price except the 6% one at exactly 0.47 (at, not strictly through)
            TrapGuard.Trade("o1", 0.45, 500, t0 - 60_000),    // before the bids went up
            TrapGuard.Trade("o2", 0.30, 500, t0 + 60_000),    // another outcome
        )
        val (l, _, ej) = lab(tape)
        l.observe(listOf(line()), t0)
        l.poll(t0 + 120_000)
        val fills = ej.readAll().filter { it.type == "FILL" }
        assertEquals("every recipe's bid is at or over 0.47", pre.size, fills.size)
        assertTrue(fills.any { it.strict } && fills.any { !it.strict })
        assertEquals(pre.size.toLong(), l.filled)
        val (l2, _, ej2) = lab(listOf(TrapGuard.Trade("o1", 0.50, 500, t0 + 60_000)))
        l2.observe(listOf(line()), t0); l2.poll(t0 + 120_000)
        assertTrue("a trade above every price fills nothing", ej2.readAll().none { it.type == "FILL" })
    }

    @Test
    fun `a filled bid is followed to the last fair before the start and graded from the settled market, and the report says it`() = runBlocking {
        val (l, bj, ej) = lab(listOf(TrapGuard.Trade("o1", 0.40, 500, t0 + 60_000)), status = "WIN")
        l.observe(listOf(line(startsIn = 2 * 3_600_000L)), t0)
        l.poll(t0 + 120_000)
        l.observe(listOf(line(fair = 0.55, startsIn = 2 * 3_600_000L)), t0 + 3_600_000L)
        l.observe(emptyList(), t0 + 2 * 3_600_000L + 1)   // the game has started
        l.poll(t0 + 2 * 3_600_000L + BidLab.GRADE_AFTER_MS + 1)
        val ev = ej.readAll()
        assertEquals(pre.size, ev.count { it.type == "CLOSE" })
        assertEquals(0.55, ev.first { it.type == "CLOSE" }.value!!, 1e-9)
        assertEquals(pre.size, ev.count { it.type == "GRADE" && it.text == "WIN" })
        val report = BidLabReport.lines(bj.readAll(), ev)
        assertTrue(report.first(), report.first().startsWith("PAPER BIDS by recipe"))
        val row = report.first { it.contains("pre-m3-t30m-guard") && it.contains("filled") }
        assertTrue(row, row.contains("1 posted, 1 filled") && row.contains("1-0 graded"))
    }

    @Test
    fun `with no bids the report says what it needs`() {
        assertTrue(BidLabReport.lines(emptyList(), emptyList()).single().startsWith("no paper bids yet"))
    }

    @Test
    fun `after a stop the journals bring back the bids still resting and the fills still waiting for their result`() {
        val (l, bj, ej) = lab(trades = listOf(TrapGuard.Trade("o1", 0.47, 10.0, t0 + 5_000)))
        l.observe(listOf(line(), line(outcome = "o2")), t0)
        runBlocking { l.poll(t0 + 10_000) }                      // o1's bids that sit at or above 0.47 fill
        val bids = bj.readAll()
        val events = ej.readAll()
        assertTrue(events.any { it.type == "FILL" })
        val (fresh, _, _) = lab()
        fresh.restore(bids, events, t0 + 20_000)
        val (resting, filledWaiting, posted) = fresh.counts()
        assertEquals(bids.size.toLong(), posted)
        assertTrue("some filled and waiting for their close", filledWaiting > 0)
        assertEquals("the rest still rest", bids.size - filledWaiting, resting)
        // a restored recipe does not post a second bid on the same side
        fresh.observe(listOf(line()), t0 + 21_000)
        assertEquals(bids.size.toLong(), fresh.counts().third - 0L)
    }

    @Test
    fun `restore leaves out what is over - expired unfilled bids, cancelled ones and graded fills`() {
        val (l, bj, ej) = lab()
        l.observe(listOf(line()), t0)
        val bids = bj.readAll()
        val (fresh, _, _) = lab()
        fresh.restore(bids, emptyList(), t0 + 3 * 3_600_000L)       // all expired by then (30 min to 2 h)
        assertEquals(0, fresh.counts().first)
        val cancelled = listOf(BidLabEvent(bids.first().id, t0 + 1, "CANCEL", 0.4))
        val (b, _, _) = lab()
        b.restore(bids, cancelled, t0 + 1_000)
        assertEquals(bids.size - 1, b.counts().first)
    }
}
