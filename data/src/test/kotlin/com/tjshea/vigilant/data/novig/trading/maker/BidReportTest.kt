package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every bid tracked (Tj, 2026-10-05: "make sure the auto bid feature is also thoroughly tracked in the scan/diagnosis feature and all information logged so I can see
 * how well my auto bids do"): one row per bid with its posting, its fill (how fast, the fair on the next scan, picked off or not) and the bet it became (close, CLV,
 * result); added up and split the ways that explain a fill.
 */
class BidReportTest {

    private val start = 1_800_000_000_000L + 6 * 3_600_000L
    private val now = start + 3 * 3_600_000L

    private fun bid(
        n: Int,
        price: Double = 0.45,
        kind: BetKind = BetKind.PROP,
        postedBefore: Long = 90 * 60_000L,
        fillDelayMs: Long? = 90_000L,
        fairAfter: Double? = 0.48,
        sharp: Double? = 0.47,
        led: Boolean = true,
        auto: Boolean = true,
        status: MakerStatus = MakerStatus.FILLED,
        betId: String? = "bet$n",
        why: String? = null,
    ): MakerBid {
        val posted = start - postedBefore
        return MakerBid(
            clientId = "c$n", orderId = "o$n", marketId = "m$n", eventId = "e$n", outcomeId = "x$n", league = "NFL", eventName = "A @ B", startsTs = start,
            marketLabel = "Receiving Yards", selection = "Player $n Over 50.5", kind = kind, price = price, contracts = 1_000, fair = price * 1.04, evAtFair = 0.04, margin = 0.04, books = 4,
            postedAtMs = posted, expiresAtMs = posted + 30 * 60_000L, status = status, filled = if (fillDelayMs != null) 1_000 else 0, paid = if (fillDelayMs != null) 1_000 * price * 0.01 else 0.0,
            endedAtMs = posted + (fillDelayMs ?: 30 * 60_000L), why = why, auto = auto, betId = betId.takeIf { fillDelayMs != null },
            bestBidAtPost = if (led) price - 0.01 else price + 0.005, offerAtPost = price + 0.04, bookAtMs = posted - 120_000L,
            blendFair = price * 1.05, sharpFairAtPost = sharp?.let { price * 1.04 },
            firstFillAtMs = fillDelayMs?.let { posted + it }, fairAtFill = fairAfter.takeIf { fillDelayMs != null }, sharpFairAtFill = sharp.takeIf { fillDelayMs != null && fairAfter != null },
        )
    }

    private fun bet(n: Int, bid: MakerBid, status: BetStatus = BetStatus.WON, closeFair: Double? = 0.50) = TrackedBet(
        id = "bet$n", createdAtMs = bid.firstFillAtMs ?: bid.postedAtMs, league = "NFL", eventName = "A @ B", startsTs = start, marketLabel = "Receiving Yards",
        selection = bid.selection, marketId = bid.marketId, outcomeId = bid.outcomeId, price = bid.price, cost = bid.price, fairAtBet = bid.fair, evPercentAtBet = bid.evAtFair,
        stake = 10.0, status = status, closeFair = closeFair, closeVia = "Novig's last trades (12)", closeFinal = true, maker = true,
    )

    @Test
    fun `a row joins the bid with the bet its fill became - the close, CLV, result - and says how fast it was taken and whether it was picked off`() {
        val b = bid(1, price = 0.45, fillDelayMs = 90_000L, fairAfter = 0.48, sharp = 0.47)
        val rows = BidReport.rows(listOf(b), listOf(bet(1, b, closeFair = 0.50)), now)
        val r = rows.single()
        assertEquals(90L, r.fillDelaySec)
        assertEquals(90L, (r.firstFillAtMs!! - r.postedAtMs) / 1000L)
        // Posted 90 minutes before the start; filled 88.5 minutes before it.
        assertEquals(90L, r.minToStartAtPost)
        assertEquals(88L, r.minToStartAtFill)
        assertEquals(true, r.led)
        assertEquals(2L, r.bookAgeSec!! / 60L)
        assertEquals(30L, r.lifeMin)
        // The next scan's fair (anchor 0.47: the sharp book) over the price 0.45: +4.4%, not picked off.
        assertEquals(0.47 / 0.45 - 1.0, r.evAtFill!!, 1e-12)
        assertEquals(false, r.pickedOff)
        // The close 0.50 over the cost 0.45 = +11.1% CLV; the bet won 10 on 10 at 0.45.
        assertEquals(0.50 / 0.45 - 1.0, r.clv!!, 1e-12)
        assertEquals("Novig's last trades (12)", r.closeVia)
        assertEquals(BetStatus.WON.name, r.betStatus)
        assertEquals(10.0 * (1.0 / 0.45 - 1.0), r.profit!!, 1e-9)
        // A bid that never filled has none of it.
        val u = bid(2, fillDelayMs = null, status = MakerStatus.EXPIRED, why = "expired")
        val ur = BidReport.rows(listOf(u), emptyList(), now).single()
        assertEquals(0L, ur.filled)
        assertNull(ur.fillDelaySec)
        assertNull(ur.clv)
        assertNull(ur.pickedOff)
        assertEquals("expired", ur.why)
        // A bid that was never sent (no order id) isn't a bid yet.
        assertTrue(BidReport.rows(listOf(b.copy(orderId = null)), emptyList(), now).isEmpty())
    }

    @Test
    fun `a fill whose next-scan fair is under its price is picked off, and the delay bands are the ones the report splits by`() {
        val picked = BidReport.rows(listOf(bid(1, price = 0.45, fairAfter = 0.43, sharp = null)), emptyList(), now).single()
        assertEquals(true, picked.pickedOff)
        assertTrue(picked.evAtFill!! < 0.0)
        assertEquals("under 30 s", BidReport.delayBand(picked.copy(fillDelaySec = 29)))
        assertEquals("30 s to 2 min", BidReport.delayBand(picked.copy(fillDelaySec = 30)))
        assertEquals("30 s to 2 min", BidReport.delayBand(picked.copy(fillDelaySec = 119)))
        assertEquals("2 to 10 min", BidReport.delayBand(picked.copy(fillDelaySec = 120)))
        assertEquals("10 to 60 min", BidReport.delayBand(picked.copy(fillDelaySec = 600)))
        assertEquals("an hour or more", BidReport.delayBand(picked.copy(fillDelaySec = 3600)))
        assertNull(BidReport.delayBand(picked.copy(fillDelaySec = null)))
        assertEquals("0.40-0.50", BidReport.priceBand(picked))
        assertEquals("under 0.20", BidReport.priceBand(picked.copy(price = 0.19)))
        assertEquals("0.60 and up", BidReport.priceBand(picked.copy(price = 0.6)))
    }

    @Test
    fun `the summary adds the fills up and splits them by speed, kind, price, sharp book, lead, time, picked off and who posted`() {
        val bids = listOf(
            bid(1, price = 0.45, fillDelayMs = 20_000L, fairAfter = 0.42, sharp = null, led = true),       // fast, picked off
            bid(2, price = 0.45, fillDelayMs = 40_000L, fairAfter = 0.41, sharp = null, led = true),       // fast, picked off
            bid(3, price = 0.35, fillDelayMs = 20 * 60_000L, fairAfter = 0.38, sharp = 0.37, led = false), // slow, fine, sharp
            bid(4, price = 0.55, kind = BetKind.TEAM_TOTAL, fillDelayMs = 3 * 3_600_000L, fairAfter = null, sharp = null, auto = false),
            bid(5, fillDelayMs = null, status = MakerStatus.EXPIRED, why = "expired", led = false),
            bid(6, fillDelayMs = null, status = MakerStatus.CANCELED, why = "The fair price fell", led = true),
            bid(7, fillDelayMs = null, status = MakerStatus.RESTING, led = true),
        )
        val bets = bids.filter { it.filled > 0 }.mapIndexed { i, b -> bet(b.clientId.drop(1).toInt(), b, status = if (i % 2 == 0) BetStatus.WON else BetStatus.LOST, closeFair = 0.5) }
        val lines = BidReport.summary(BidReport.rows(bids, bets, now), now)
        val text = lines.joinToString("\n")
        assertTrue(text, lines.first().startsWith("bids: 7 posted · 4 filled (57%) · 1 resting now · 2 ended without a fill · auto-make 6, by hand 1"))
        assertTrue(text, text.contains("ALL FILLS: 4 fills"))
        assertTrue(text, text.contains("EV at fill") && text.contains("3 judged") && text.contains("67% picked off"))
        assertTrue(text, text.contains("-- fills by how fast they were taken --"))
        assertTrue(text, text.contains("under 30 s: 1 fill") && text.contains("30 s to 2 min: 1 fill") && text.contains("10 to 60 min: 1 fill") && text.contains("an hour or more: 1 fill"))
        // The delay splits come in time order, not by count.
        assertTrue(text.indexOf("under 30 s:") < text.indexOf("30 s to 2 min:") && text.indexOf("30 s to 2 min:") < text.indexOf("10 to 60 min:"))
        assertTrue(text, text.contains("-- fills by kind of market --") && text.contains("TEAM_TOTAL: 1 fill") && text.contains("PROP: 3 fills"))
        assertTrue(text, text.contains("-- fills by bid price (about the chance the side wins) --") && text.contains("0.30-0.40: 1 fill") && text.contains("0.40-0.50: 2 fills") && text.contains("0.50-0.60: 1 fill"))
        assertTrue(text, text.contains("sharp book in the fair: 1 fill") && text.contains("no sharp book in the fair: 3 fills"))
        assertTrue(text, text.contains("led (no bid as high): 3 fills") && text.contains("behind another bid: 1 fill"))
        assertTrue(text, text.contains("picked off (fair under the price): 2 fills") && text.contains("still above the price: 1 fill"))
        assertTrue(text, text.contains("auto-make: 3 fills") && text.contains("by hand: 1 fill"))
        // Led: filled 3 of 4 known... the line compares the bids that filled with the ones that didn't.
        assertTrue(text, text.contains("led their side when posted (no bid as high): filled 75% of 4, unfilled 67% of 3"))
        // How fast: 2 of the 4 fills inside 2 minutes.
        assertTrue(text, text.contains("50% within 2 minutes of posting"))
        // Results are added up for the settled ones.
        assertTrue(text, text.contains("results +10.79 on 40.00 staked (4 settled)") && text.contains("(1 close, 100% beat)"))
        // Nothing posted: nothing said.
        assertTrue(BidReport.summary(emptyList(), now).isEmpty())
        // Posted but none filled: one line.
        assertEquals(1, BidReport.summary(BidReport.rows(listOf(bid(9, fillDelayMs = null, status = MakerStatus.EXPIRED)), emptyList(), now), now).size)
    }

    @Test
    fun `the fill lines are one a fill, newest first, and say PICKED OFF where the fair had moved under the price`() {
        val bids = listOf(
            bid(1, fillDelayMs = 20_000L, fairAfter = 0.40, sharp = null, postedBefore = 3 * 3_600_000L),
            bid(2, fillDelayMs = 300_000L, fairAfter = 0.50, sharp = 0.48, postedBefore = 60 * 60_000L),
        )
        val rows = BidReport.rows(bids, emptyList(), now)
        val lines = BidReport.fillLines(rows, now, zone = java.util.TimeZone.getTimeZone("UTC"))
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].contains("Player 2 Over 50.5") && lines[0].contains("taken 5 min after posting") && !lines[0].contains("PICKED OFF"))
        assertTrue(lines[1], lines[1].contains("Player 1 Over 50.5") && lines[1].contains("PICKED OFF") && lines[1].contains("no sharp"))
        assertTrue(lines[0], lines[0].contains("sharp 0.468") || lines[0].contains("sharp "))
        assertTrue(lines[1], lines[1].contains("no close yet"))
        assertFalse(BidReport.fillLines(rows, now, limit = 1).size != 1)
        assertNotNull(lines.firstOrNull())
    }

    // ---- low API usage bids (RESEARCH.md §92) ------------------------------------------------------------------------------

    private fun tagged(n: Int, focus: String?, books: List<String> = emptyList(), age: Int? = null) =
        bid(n).copy(focus = focus, fairBooks = books, fairAgeSec = age, fairNewestAgeSec = age)

    @Test
    fun `a row carries which choice posted the bid, the books behind its fair and how old their prices were`() {
        val b = tagged(1, "LOW_USAGE", listOf("Kalshi", "ProphetX"), 75)
        val r = BidReport.rows(listOf(b), emptyList(), now).single()
        assertEquals("LOW_USAGE", r.focus)
        assertEquals(listOf("Kalshi", "ProphetX"), r.fairBooks)
        assertEquals(75, r.fairAgeSec)
        assertEquals("Low API usage", BidReport.focusLabel("LOW_USAGE"))
        assertEquals("Quick & likely to win", BidReport.focusLabel("QUICK_LIKELY"))
        assertEquals("All bids", BidReport.focusLabel("ALL"))
        assertNull(BidReport.focusLabel(null))
        assertEquals("under 1 min", BidReport.ageBand(59))
        assertEquals("1 to 3 min", BidReport.ageBand(60))
        assertEquals("3 to 5 min", BidReport.ageBand(180))
        assertEquals("5 min or more", BidReport.ageBand(300))
    }

    @Test
    fun `small-market bids are counted apart and their fills split from the popular ones, only once one was posted (Tj, 2026-10-07)`() {
        val rows = BidReport.rows(listOf(bid(1), bid(2), bid(3).copy(obscure = true), bid(4, fillDelayMs = null, status = MakerStatus.EXPIRED, betId = null).copy(obscure = true)), emptyList(), now)
        assertEquals(listOf(false, false, true, true), rows.map { it.obscure })
        val text = BidReport.summary(rows, now).joinToString("\n")
        assertTrue(text, text.contains("posted: 2 popular, 2 small-market"))
        assertTrue(text, text.contains("popular market: 2 fills"))
        assertTrue(text, text.contains("small market (strict safeguards): 1 fill"))
        // With none posted the split is not printed.
        val plain = BidReport.summary(BidReport.rows(listOf(bid(1), bid(2)), emptyList(), now), now).joinToString("\n")
        assertFalse(plain.contains("small-market"))
    }

    @Test
    fun `once a low-usage bid filled the fills are split by the choice, the books behind the fair and the age of their prices`() {
        val rows = BidReport.rows(
            listOf(tagged(1, "LOW_USAGE", listOf("Kalshi", "ProphetX"), 30), tagged(2, "LOW_USAGE", listOf("FanDuel", "Kalshi"), 200), tagged(3, "ALL"), tagged(4, null)),
            emptyList(), now,
        )
        val text = BidReport.summary(rows, now).joinToString("\n")
        assertTrue(text, text.contains("-- fills by which bids go up (Settings › Bids) --"))
        assertTrue(text.contains("Low API usage: 2 fills"))
        assertTrue(text.contains("All bids: 1 fill"))
        assertTrue(text, text.contains("Kalshi + ProphetX: 1 fill"))
        assertTrue(text, text.contains("FanDuel + Kalshi: 1 fill"))
        assertTrue(text, text.contains("under 1 min: 1 fill") && text.contains("3 to 5 min: 1 fill"))
        // Without a low-usage bid none of those splits is printed.
        val plain = BidReport.summary(BidReport.rows(listOf(tagged(1, "ALL"), tagged(2, null)), emptyList(), now), now).joinToString("\n")
        assertFalse(plain.contains("which bids go up"))
        assertFalse(plain.contains("the books behind the fair"))
    }
}
