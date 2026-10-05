package com.tjshea.vigilant.data.novig.trading.maker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picked-off guard (Tj, 2026-10-05: "my bids right now are being taken fast and I'm worried they aren't true positive Ev"; RESEARCH.md §88.3): when half or
 * more of the last fills were filled at a price above the fair the next scan had for them, the bids stop.
 */
class MakerGuardTest {

    private val t0 = 1_800_000_000_000L

    /** A filled bid at [price], posted at a 4% edge, whose side the next scan priced at [fairAfter] (null: not judged). */
    private fun fill(n: Int, price: Double = 0.50, fairAfter: Double? = 0.52, sharpAfter: Double? = null, at: Long = t0 + n * 60_000L) = MakerBid(
        clientId = "c$n", orderId = "o$n", marketId = "m$n", eventId = "e$n", outcomeId = "x$n", league = "NFL", eventName = "A @ B", startsTs = t0 + 6 * 3_600_000L,
        marketLabel = "Yards", selection = "P Over", price = price, contracts = 100, fair = price * 1.04, evAtFair = 0.04, margin = 0.04, postedAtMs = at - 120_000,
        status = MakerStatus.FILLED, filled = 100, firstFillAtMs = at, fairAtFill = fairAfter, sharpFairAtFill = sharpAfter,
    )

    @Test
    fun `a fill is picked off when the fair on the next scan is under the price it was filled at, and the fill's delay is how fast it was taken`() {
        val good = fill(1, price = 0.50, fairAfter = 0.52)
        assertEquals(0.52 / 0.50 - 1.0, good.fillEv()!!, 1e-12)
        assertEquals(120_000L, good.fillDelayMs)
        // The fair fell under the price: negative.
        assertTrue(fill(2, price = 0.50, fairAfter = 0.47).fillEv()!! < 0.0)
        // A sharp book lower than the blend decides (as the bid was priced): 0.52 blend, 0.48 sharp -> -4%.
        assertEquals(0.48 / 0.50 - 1.0, fill(3, price = 0.50, fairAfter = 0.52, sharpAfter = 0.48).fillEv()!!, 1e-12)
        // With the anchor off, the blend alone.
        assertEquals(0.52 / 0.50 - 1.0, fill(3, price = 0.50, fairAfter = 0.52, sharpAfter = 0.48).fillEv(anchorSharp = false)!!, 1e-12)
        // Not judged yet: no EV at the fill.
        assertNull(fill(4, fairAfter = null).fillEv())
    }

    @Test
    fun `half or more of the last eight judged fills picked off, with the average EV under 1 percent, stops the bids - and fewer than six fills never does`() {
        // Seven judged fills, five picked off: stop.
        val bad = (1..7).map { fill(it, fairAfter = if (it <= 5) 0.47 else 0.53) }
        val v = MakerGuard.check(bad)
        assertEquals(7, v.judged)
        assertEquals(5, v.pickedOff)
        assertTrue(v.tripped)
        assertTrue(v.text, v.text.startsWith("5 of the last 7 fills were picked off"))
        assertTrue(MakerGuard.haltedText(v).contains("Resume bids"))
        // Five judged fills, all picked off: too few to say.
        assertFalse(MakerGuard.check((1..5).map { fill(it, fairAfter = 0.47) }).tripped)
        // Eight fills, three picked off: fine.
        assertFalse(MakerGuard.check((1..8).map { fill(it, fairAfter = if (it <= 3) 0.47 else 0.53) }).tripped)
        // Half picked off but the others so good that the average is over 1%: the bids are doing their job.
        assertFalse(MakerGuard.check((1..8).map { fill(it, fairAfter = if (it <= 4) 0.495 else 0.60) }).tripped)
        // Fills with no judgement don't count either way.
        assertEquals(0, MakerGuard.check((1..9).map { fill(it, fairAfter = null) }).judged)
    }

    @Test
    fun `only the newest eight, and only fills after Tj's last Resume, are looked at`() {
        // Old picked-off fills, then eight good ones: the window is the newest eight.
        val old = (1..8).map { fill(it, fairAfter = 0.47) }
        val newer = (9..16).map { fill(it, fairAfter = 0.54) }
        assertFalse(MakerGuard.check(old + newer).tripped)
        assertEquals(8, MakerGuard.check(old + newer).judged)
        // The same eight bad fills, but Tj resumed after them: nothing to judge.
        val resumedAt = old.maxOf { it.firstFillAtMs!! } + 1
        assertEquals(0, MakerGuard.check(old, fromMs = resumedAt).judged)
        assertFalse(MakerGuard.check(old, fromMs = resumedAt).tripped)
        assertTrue(MakerGuard.check(old, fromMs = 0L).tripped)
    }
}
