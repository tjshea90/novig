package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Tracker's "Novig only" filter (Tj, 2026-10-02 ~18:50Z: "find the current novig odds for each of my open bets and show the percent EV compared only
 * from novig odds … Make sure it is smart and doesn't waste any api usage on other sports books … if I already just scanned without using this filter
 * and there is still fresh novig odds for all my bets, it doesn't need to rescan").
 */
class NovigNowTest {

    private val now = 1_800_000_000_000L
    private val start = now + 6 * 3_600_000L

    private fun market(id: String) = NovigMarket(id, "ev", "MONEYLINE", "OPEN", "B @ A", start, MarketFee.GAME, listOf(NovigOutcome("$id-A", "A", "TBD"), NovigOutcome("$id-B", "B", "TBD")))

    /** A's best bid [bidA]; B's best bid [bidB] (so A is offered at 1 − bidB). */
    private fun book(id: String, bidA: Int?, bidB: Int?) = NovigBook(
        id, 1, buildMap {
            bidA?.let { put("$id-A", listOf(BidLevel(it, 1_000))) }
            bidB?.let { put("$id-B", listOf(BidLevel(it, 1_000))) }
        }, now,
    )

    private fun bet(id: String, market: String = "m1", cost: Double = 0.40, status: BetStatus = BetStatus.PENDING, starts: Long = start, novigAt: Long? = null, novig: Double? = null) = TrackedBet(
        id = id, createdAtMs = now - 86_400_000L, league = "NBA", eventName = "B @ A", startsTs = starts, marketLabel = "Moneyline", selection = "A",
        marketId = market, outcomeId = "$market-A", price = cost, cost = cost, fairAtBet = 0.42, evPercentAtBet = 0.05, stake = 4.0, status = status,
        nowFair = 0.60, nowEv = 0.5, nowAtMs = now - 60_000, nowBooks = 7, books = listOf(BookLine("Novig", 150, -170), BookLine("Pinnacle", 140, -160)),
        novigFair = novig, novigAtMs = novigAt,
    )

    @Test
    fun `Novig's price for a bet is its odds on Novig now - the offer, what buying it costs - never the bid or a middle`() {
        // A bid 0.45; B bid 0.53, so A is offered at 0.47: Novig's odds for A are 0.47 (Tj, 2026-10-02 ~21:35Z: the middle read a wide spread as a move).
        assertEquals(0.47, NovigNow.odds(book("m1", 450, 530), market("m1"), "m1-A")!!, 1e-12)
        assertEquals(0.47, NovigNow.odds(book("m1", null, 530), market("m1"), "m1-A")!!, 1e-12)
        // Bids on A only: nobody is selling A, so Novig has no odds for it.
        assertNull(NovigNow.odds(book("m1", 450, null), market("m1"), "m1-A"))
        assertNull(NovigNow.odds(book("m1", null, null), market("m1"), "m1-A"))
    }

    @Test
    fun `Tj's two bets - Novig's odds now the same as bet at is 0% EV, and only Novig's numbers are left in the view`() {
        // Tyson Bagent Over 0.5 at +122 (logged from American odds: 0.4505), Novig offering +122 now (its grid price 0.450), the other side -223.
        // Before: the middle of 0.31 and 0.45 = 0.38 (+163), "-15.56% EV". Malik Willis Under 15.5 at +115, Novig +115 now (0.465), -120 the other side.
        fun placed(id: String, american: Int, novig: Double) = bet(id, cost = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(american)).copy(
            american = american, novigFair = novig, novigAtMs = now - 40_000, fairAtBet = 0.461, evPercentAtBet = 0.0234, cnoFair = 0.38, vigFair = 0.38,
        )
        val v = NovigNow.view(listOf(placed("bagent", 122, 0.450), placed("willis", 115, 0.465))).associateBy { it.id }
        for (b in v.values) {
            assertEquals(0.0, b.nowEv!!, 1e-12)
            assertEquals(b.price, b.nowFair!!, 1e-12)
            // When bet: Novig's own odds, the ones paid, so no edge over Novig; and nothing from any other book.
            assertEquals(b.price, b.fairAtBet!!, 1e-12)
            assertEquals(0.0, b.evPercentAtBet!!, 1e-12)
            assertNull(b.cnoFair)
            assertNull(b.vigFair)
            assertTrue(b.books.isEmpty())
            assertNull(b.nowBooks)
            assertEquals(NovigNow.VIA, b.nowVia)
        }
        assertEquals(122, v.getValue("bagent").nowAmerican)
        // Novig's odds moving: +122 bet, Novig +150 now (0.40): the bet's odds are worse than Novig's now.
        val moved = NovigNow.view(listOf(placed("bagent", 122, 0.40))).single()
        assertEquals(0.40 / (1.0 / 2.22) - 1.0, moved.nowEv!!, 1e-12)
        assertEquals(150, moved.nowAmerican)
    }

    @Test
    fun `a bet a read can't price says why - book unread, market gone, side not the market's, nothing offered - and counts as read for a while`() = runBlocking {
        val bets = listOf(bet("noBook", "m1"), bet("gone", "m2"), bet("wrongSide", "m3").copy(outcomeId = "x"), bet("empty", "m4"), bet("ok", "m5"))
        val books = mapOf("m2" to book("m2", 450, 530), "m3" to book("m3", 450, 530), "m4" to book("m4", null, null), "m5" to book("m5", 450, 530))
        val r = NovigNow.read(bets, now, force = false, books = { books }, market = { id -> if (id == "m2") null else market(id) })
        assertEquals(mapOf("noBook" to NovigNow.BOOK_UNREAD, "gone" to NovigNow.NOT_LISTED, "wrongSide" to NovigNow.NOT_A_SIDE, "empty" to NovigNow.NOTHING_OFFERED), r.why)
        assertEquals(setOf("ok"), r.prices.keys)
        // Shown on the bet in place of "not read yet", and not asked again until it's stale like a price.
        val looked = bets.first().copy(novigWhy = NovigNow.NOTHING_OFFERED, novigWhyAtMs = now - 30_000)
        assertEquals(NovigNow.NOTHING_OFFERED, NovigNow.view(listOf(looked)).single().nowNote)
        assertTrue(NovigNow.stale(listOf(looked), now).isEmpty())
        assertEquals(1, NovigNow.stale(listOf(looked), now + NovigNow.FRESH_MS).size)
        // A price read after the reason is the latest word; an older price under a newer reason keeps its number and shows the reason.
        val repriced = looked.copy(novigFair = 0.5, novigAtMs = now)
        assertNull(NovigNow.note(repriced))
        assertEquals(NovigNow.NOTHING_OFFERED, NovigNow.note(repriced.copy(novigAtMs = now - 60_000)))
        // Not looked at yet: a bet with no Novig ids says it hasn't been looked up, one with them that it hasn't been read.
        assertTrue(NovigNow.note(bet("x").copy(marketId = ""))!!.contains("looked up"))
        assertTrue(NovigNow.note(bet("x"))!!.contains("hasn't been read"))
    }

    @Test
    fun `each read is the price now, and the last read before the start is Novig's close`() {
        val b = NovigNow.apply(bet("a"), 0.46, now)
        assertEquals(0.46, b.novigFair!!, 0.0)
        assertEquals(0.46, b.novigClose!!, 0.0)
        val later = NovigNow.apply(b, 0.48, start - 60_000)
        assertEquals(0.48, later.novigClose!!, 0.0)
        // After the start: the price now moves, the close doesn't.
        val inGame = NovigNow.apply(later, 0.70, start + 60_000)
        assertEquals(0.70, inGame.novigFair!!, 0.0)
        assertEquals(0.48, inGame.novigClose!!, 0.0)
        assertEquals(start - 60_000, inGame.novigCloseAtMs)
        // An older read never replaces a newer close.
        assertEquals(0.48, NovigNow.apply(later, 0.40, start - 600_000).novigClose!!, 0.0)
    }

    @Test
    fun `a read asks only Novig, only for the stale bets' markets, and nothing at all when every price is fresh`() = runBlocking {
        val bets = listOf(
            bet("fresh", "m1", novigAt = now - 30_000, novig = 0.46),
            bet("old", "m2", novigAt = now - 10 * 60_000),
            bet("never", "m3"),
            bet("same-market", "m3"),
            bet("settled", "m4", status = BetStatus.WON),
        )
        var asked = listOf<String>()
        val books = mapOf("m2" to book("m2", 450, 530), "m3" to book("m3", 300, 650))
        val r = NovigNow.read(bets, now, force = false, books = { ids -> asked = ids; books }, market = { market(it) })
        assertEquals(listOf("m2", "m3"), asked)
        assertEquals(3, r.due)
        assertEquals(4, r.all)
        assertEquals(setOf("old", "never", "same-market"), r.prices.keys)
        // Novig's odds now: A offered at 1 − B's best bid.
        assertEquals(0.47, r.prices.getValue("old"), 1e-12)
        assertEquals(0.35, r.prices.getValue("never"), 1e-12)
        // All fresh (a scan just read them): nothing is asked.
        var calls = 0
        val fresh = bets.filter { it.status == BetStatus.PENDING }.map { it.copy(novigAtMs = now - 10_000, novigFair = 0.5) }
        val none = NovigNow.read(fresh, now, force = false, books = { calls++; emptyMap() }, market = { calls++; null })
        assertEquals(0, calls)
        assertEquals(0, none.due)
        // Check Novig now reads them all anyway.
        assertEquals(listOf("m1", "m2", "m3"), NovigNow.read(fresh, now, force = true, books = { ids -> asked = ids; books }, market = { market(it) }).marketsAsked)
    }

    @Test
    fun `the filter prices open bets from Novig alone, cuts the books to Novig, and judges closes by Novig's own close`() {
        val open = bet("open", novigAt = now - 30_000, novig = 0.46)
        val unread = bet("unread", "m2")
        val settled = bet("settled", "m3", status = BetStatus.WON, starts = now - 3_600_000).copy(novigClose = 0.44, novigCloseAtMs = now - 3_600_000 - 60_000, closingFair = 0.60, closingSeenAtMs = now - 3_600_000 - 60_000)
        val noClose = bet("noClose", "m4", status = BetStatus.LOST, starts = now - 3_600_000).copy(closingFair = 0.60, closingSeenAtMs = now - 3_600_000 - 60_000)
        val v = NovigNow.view(listOf(open, unread, settled, noClose)).associateBy { it.id }
        // Open: EV now = Novig's 0.46 against the 0.40 paid; every book's line gone (Novig's odds are the card's own).
        assertEquals(0.46 / 0.40 - 1.0, v.getValue("open").nowEv!!, 1e-12)
        assertEquals(NovigNow.VIA, v.getValue("open").nowVia)
        assertTrue(v.getValue("open").books.isEmpty())
        // Not read on Novig yet: no EV now (not every book's), and it says why.
        assertNull(v.getValue("unread").nowFair)
        assertTrue(v.getValue("unread").nowNote!!.contains("Check Novig now"))
        // Closing line: Novig's own (0.44), not every book's (0.60); none where Novig's close wasn't read.
        assertEquals(0.44 / 0.40 - 1.0, ClosingLine.clv(v.getValue("settled"), now)!!, 1e-12)
        assertNull(ClosingLine.clv(v.getValue("noClose"), now))
        // The stats are the Tracker's own, on these numbers.
        val stats = BetTracker.stats(v.values.toList(), now)
        assertEquals(0.44 / 0.40 - 1.0, stats.averageClv!!, 1e-12)
    }
}
