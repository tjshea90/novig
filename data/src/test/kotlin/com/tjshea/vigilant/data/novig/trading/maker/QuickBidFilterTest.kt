package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-07: "make sure that the type of bids in the auto bids section are truly the type of bids most likely to be taken quickly, in other words, no strange props or
 * small markets." Quick & likely used to ORDER popular markets first and leave every one eligible; it now leaves the obscure kinds and thinly priced lines out altogether.
 */
class QuickBidFilterTest {

    private val now = 1_800_000_000_000L
    private val start = now + 3 * 3_600_000L

    private fun line(marketType: String, books: Int, league: String = "NFL", fair: Double = 0.52) = MakerLine(
        market = NovigMarket(
            marketId = "m-$marketType", eventId = "ev", marketType = marketType, status = "OPEN", description = marketType, startsTs = start, fee = MarketFee.GAME,
            outcomes = listOf(NovigOutcome("o-over", "Over 50.5", "TBD"), NovigOutcome("o-under", "Under 50.5", "TBD")),
        ),
        outcomeId = "o-over", startsTs = start, league = league, eventName = "A @ B", marketLabel = marketType, selection = "Player Over 50.5", kind = BetKind.PROP,
        fair = fair, fairAsOfMs = now - 30_000, fairOld = false, books = books, offer = 0.55, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
    )

    private fun quick(s: ScanSettings = ScanSettings()) = MakerRules.of(s.copy(makerFocus = BidFocus.QUICK_LIKELY))

    private fun skipped(l: MakerLine, r: MakerRules): String? = (MakerQuote.precheck(l, r, now) as? MakerQuote.Pre.No)?.skip?.why

    @Test
    fun `a popular kind priced by enough books gets a bid`() {
        val r = quick()
        // NFL receiving yards: about $490 a listed market a day, popular; 8 books price it.
        assertEquals(null, skipped(line("RECEIVING_YARDS", books = 8), r))
        assertEquals(null, skipped(line("RECEPTIONS", books = 5), r))
    }

    @Test
    fun `a kind takers were measured to trade rarely gets no bid however many books price it`() {
        val r = quick()
        for (obscure in listOf("LONGEST_RECEPTION", "LONGEST_RUSH", "KICKING_POINTS", "RUSHING_AND_RECEIVING_YARDS")) {
            assertTrue("$obscure", skipped(line(obscure, books = 12), r)!!.contains("rarely trade"))
        }
        // The same kind is still a bid under "All bids".
        assertEquals(null, skipped(line("LONGEST_RECEPTION", books = 12), MakerRules.of(ScanSettings())))
    }

    @Test
    fun `a line few books price is a small market, and the floor is Tj's to set`() {
        val r = quick()
        assertEquals(5, r.minLineBooks)
        assertTrue(skipped(line("RECEIVING_YARDS", books = 4), r)!!.startsWith("Only 4 books price this line (you need 5)"))
        assertTrue(skipped(line("RECEIVING_YARDS", books = 1), r)!!.startsWith("Only 1 book price"))
        // Typed 8: 7 books are too few; 3: 3 books are enough.
        assertTrue(skipped(line("RECEIVING_YARDS", books = 7), quick(ScanSettings(makerQuickMinBooks = 8)))!!.startsWith("Only 7 books"))
        assertEquals(null, skipped(line("RECEIVING_YARDS", books = 3), quick(ScanSettings(makerQuickMinBooks = 3))))
    }

    @Test
    fun `a kind never measured counts as popular only when six books or more price it`() {
        val r = quick(ScanSettings(makerQuickMinBooks = 3))
        assertEquals(null, skipped(line("SOME_NEW_PROP", books = 6), r))
        assertTrue(skipped(line("SOME_NEW_PROP", books = 5), r)!!.startsWith("A small or unusual market"))
    }

    @Test
    fun `low API usage bids are not held to the book count, they price from two or three books by design`() {
        val r = MakerRules.of(ScanSettings(makerFocus = BidFocus.LOW_USAGE))
        assertFalse(r.popularOnly)
        assertEquals(0, r.minLineBooks)
        assertTrue(r.skipObscure)
    }

    @Test
    fun `All bids is unchanged`() {
        val r = MakerRules.of(ScanSettings())
        assertFalse(r.popularOnly || r.skipObscure)
        assertEquals(0, r.minLineBooks)
    }

    @Test
    fun `the margin choices are 2 to 4 percent, 6 and 8 are gone, and a 2 percent margin is a bid at least 2 percent under the fair`() {
        assertEquals(listOf(0.02, 0.025, 0.03, 0.0325, 0.035, 0.04), ScanSettings.MAKER_MARGIN_CHOICES)
        assertFalse(0.06 in ScanSettings.MAKER_MARGIN_CHOICES || 0.08 in ScanSettings.MAKER_MARGIN_CHOICES)
        val r = MakerRules.of(ScanSettings(makerMargin = 0.02)).copy(stakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM, customStake = 5.0, maxStake = 10.0)
        val post = MakerQuote.decide(line("RECEIVING_YARDS", books = 3, fair = 0.52), r, now) as MakerDecision.Post
        assertTrue(post.price <= 0.52 / 1.02 + 1e-9)
        assertTrue(post.evAtFair >= 0.02 - 1e-9)
    }
}
