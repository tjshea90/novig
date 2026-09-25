package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FairValueTest {

    private fun book(key: String, vararg american: Int) =
        BookPrices(key, key.replaceFirstChar { it.uppercase() }, american.map { Odds.americanToDecimal(it) })

    private val pinnacle = book("pinnacle", -150, 130)
    private val draftkings = book("draftkings", -165, 140)
    private val fanduel = book("fanduel", -160, 135)

    private fun devigged(b: BookPrices, m: DevigMethod) = Devig.devig(b.decimalOdds.map(Odds::impliedProbability), m)

    @Test
    fun `sharp source uses only the sharp book`() {
        val s = FairSettings(source = FairSource.SHARP, method = DevigMethod.MULTIPLICATIVE)
        val line = FairValue.compute(listOf(draftkings, pinnacle, fanduel), s)!!
        assertEquals(FairSource.SHARP, line.sourceUsed)
        assertEquals(listOf("Pinnacle"), line.sharpBooksUsed)
        assertEquals(devigged(pinnacle, DevigMethod.MULTIPLICATIVE)[0], line.probabilities[0], 1e-12)
    }

    @Test
    fun `sharp source falls back to the average when no sharp book quoted the line`() {
        val s = FairSettings(source = FairSource.SHARP, method = DevigMethod.MULTIPLICATIVE)
        val line = FairValue.compute(listOf(draftkings, fanduel), s)!!
        assertEquals(FairSource.MARKET_AVERAGE, line.sourceUsed)
        val expected = (devigged(draftkings, DevigMethod.MULTIPLICATIVE)[0] + devigged(fanduel, DevigMethod.MULTIPLICATIVE)[0]) / 2
        assertEquals(expected, line.probabilities[0], 1e-12)
    }

    @Test
    fun `sharp source with fallback off skips a line with no sharp book`() {
        val s = FairSettings(source = FairSource.SHARP, fallbackToAverage = false)
        assertNull(FairValue.compute(listOf(draftkings, fanduel), s))
    }

    @Test
    fun `market average devigs every book on its own then averages`() {
        val s = FairSettings(source = FairSource.MARKET_AVERAGE, method = DevigMethod.POWER)
        val line = FairValue.compute(listOf(draftkings, pinnacle, fanduel), s)!!
        val expected = listOf(draftkings, pinnacle, fanduel).map { devigged(it, DevigMethod.POWER)[1] }.average()
        assertEquals(expected, line.probabilities[1], 1e-12)
        assertEquals(1.0, line.probabilities.sum(), 1e-9)
    }

    @Test
    fun `blend weights sharp against the market average`() {
        val s = FairSettings(source = FairSource.BLEND, method = DevigMethod.MULTIPLICATIVE, sharpWeight = 0.7)
        val line = FairValue.compute(listOf(draftkings, pinnacle, fanduel), s)!!
        val sharp = devigged(pinnacle, DevigMethod.MULTIPLICATIVE)[0]
        val avg = listOf(draftkings, pinnacle, fanduel).map { devigged(it, DevigMethod.MULTIPLICATIVE)[0] }.average()
        assertEquals(0.7 * sharp + 0.3 * avg, line.probabilities[0], 1e-12)
        assertEquals(FairSource.BLEND, line.sourceUsed)
    }

    @Test
    fun `minimum book count gates the market average`() {
        val s = FairSettings(source = FairSource.MARKET_AVERAGE, minBooks = 3)
        assertNull(FairValue.compute(listOf(draftkings, fanduel), s))
        assertNotNull(FairValue.compute(listOf(draftkings, fanduel, pinnacle), s))
    }

    @Test
    fun `a book quoting an arbitrage against itself is thrown out, not averaged in`() {
        val broken = book("mybookieag", 150, 150) // both sides plus money: hold < 0
        val s = FairSettings(source = FairSource.MARKET_AVERAGE, minBooks = 1, method = DevigMethod.MULTIPLICATIVE)
        val line = FairValue.compute(listOf(broken, draftkings), s)!!
        assertEquals(listOf("Draftkings"), line.averageBooksUsed)
    }

    @Test
    fun `hold is reported from the books actually used`() {
        val s = FairSettings(source = FairSource.SHARP)
        val line = FairValue.compute(listOf(pinnacle, draftkings), s)!!
        val raw = pinnacle.decimalOdds.sumOf { 1 / it } - 1
        assertEquals(raw, line.hold, 1e-12)
    }
}
