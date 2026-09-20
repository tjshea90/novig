package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConsensusTest {

    @Test
    fun `prefers a sharp book when one is present, ignoring the rest`() {
        val quotes = listOf(
            BookQuote("DraftKings", listOf(1.80, 2.10)),
            BookQuote("Pinnacle", listOf(1.91, 1.95)),
            BookQuote("FanDuel", listOf(1.83, 2.05)),
        )
        val reference = Consensus.computeReferenceProbabilities(quotes, DevigMethod.MULTIPLICATIVE)

        assertEquals(listOf("Pinnacle"), reference.booksUsed)
        val expected = Devig.multiplicative(listOf(1.91, 1.95).map(Odds::impliedProbability))
        assertEquals(expected[0], reference.fairProbabilityByOutcome[0], 1e-9)
        assertEquals(expected[1], reference.fairProbabilityByOutcome[1], 1e-9)
    }

    @Test
    fun `averages every book when no sharp book is present, per Tj's own fallback`() {
        val quotes = listOf(
            BookQuote("DraftKings", listOf(1.80, 2.10)),
            BookQuote("FanDuel", listOf(1.83, 2.05)),
            BookQuote("BetMGM", listOf(1.85, 2.00)),
        )
        val reference = Consensus.computeReferenceProbabilities(quotes, DevigMethod.MULTIPLICATIVE)

        assertEquals(setOf("DraftKings", "FanDuel", "BetMGM"), reference.booksUsed.toSet())

        val perBookFair = quotes.map { Devig.multiplicative(it.decimalOddsByOutcome.map(Odds::impliedProbability)) }
        val expectedOutcome0 = perBookFair.sumOf { it[0] } / perBookFair.size
        assertEquals(expectedOutcome0, reference.fairProbabilityByOutcome[0], 1e-9)
    }

    @Test
    fun `rejects mismatched outcome counts across books`() {
        val quotes = listOf(
            BookQuote("DraftKings", listOf(1.80, 2.10)),
            BookQuote("ThreeWayBook", listOf(2.5, 3.4, 3.9)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            Consensus.computeReferenceProbabilities(quotes, DevigMethod.MULTIPLICATIVE)
        }
    }

    @Test
    fun `rejects an empty quote list`() {
        assertThrows(IllegalArgumentException::class.java) {
            Consensus.computeReferenceProbabilities(emptyList(), DevigMethod.MULTIPLICATIVE)
        }
    }
}
