package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PX11d: the outlier audit's line names each book's share of its lines left out as an outlier, worst first, and leaves out books seldom out. */
class QuoteCensusTest {
    @Test fun `worst share first, few outliers left out`() {
        val line = QuoteCensus.outlierRates(out = mapOf("sportzino" to 160L, "betmgm" to 126L, "draftkings" to 22L, "rare" to 3L), priced = mapOf("sportzino" to 40L, "betmgm" to 12_000L, "draftkings" to 190_000L))
        assertTrue(line, line.startsWith("sportzino 80.0% (160 of 200)"))
        assertTrue(line.indexOf("betmgm") < line.indexOf("draftkings"))
        assertFalse(line.contains("rare"))
    }

    @Test fun `the priced counts go into the rate line, not the stage list`() {
        QuoteCensus.describeAndReset()
        repeat(12) { QuoteCensus.add(QuoteCensus.BOOK_OUT, "sportzino") }
        repeat(36) { QuoteCensus.add(QuoteCensus.BOOK_PRICED, "sportzino") }
        val text = QuoteCensus.describeAndReset()
        assertFalse(text.contains("pricedBook"))
        assertEquals("book outliers (out/(out+priced), books out 10+ times): sportzino 25.0% (12 of 48)", text.lines().last())
    }
}
