package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevigTest {

    private val allMethods = DevigMethod.values().toList()

    // -110/-110 is a standard two-way market: raw implied probabilities are equal, so every
    // method must land on exactly 50/50 once the vig is stripped out.
    private val symmetricRawProbs = listOf(
        Odds.impliedProbability(Odds.americanToDecimal(-110)),
        Odds.impliedProbability(Odds.americanToDecimal(-110)),
    )

    // A clear favorite/dog market with real vig, used for the invariant checks below.
    private val asymmetricRawProbs = listOf(
        Odds.impliedProbability(1.44), // heavy favorite
        Odds.impliedProbability(3.00), // underdog
    )

    @Test
    fun `every method returns fair probabilities that sum to one`() {
        for (method in allMethods) {
            val fair = Devig.devig(symmetricRawProbs, method)
            assertEquals("method=$method", 1.0, fair.sum(), 1e-6)

            val fairAsym = Devig.devig(asymmetricRawProbs, method)
            assertEquals("method=$method", 1.0, fairAsym.sum(), 1e-6)
        }
    }

    @Test
    fun `every method splits a symmetric market exactly 50-50`() {
        for (method in allMethods) {
            val fair = Devig.devig(symmetricRawProbs, method)
            assertEquals("method=$method", 0.5, fair[0], 1e-6)
            assertEquals("method=$method", 0.5, fair[1], 1e-6)
        }
    }

    @Test
    fun `every method preserves favorite-underdog ordering`() {
        for (method in allMethods) {
            val fair = Devig.devig(asymmetricRawProbs, method)
            assertTrue("method=$method should keep the favorite ahead of the dog", fair[0] > fair[1])
        }
    }

    @Test
    fun `every method is a no-op when there is already zero margin`() {
        // raw probs already sum to 1 — nothing to devig, every method should return them unchanged.
        val noVig = listOf(0.7, 0.3)
        for (method in allMethods) {
            val fair = Devig.devig(noVig, method)
            assertEquals("method=$method", noVig[0], fair[0], 1e-6)
            assertEquals("method=$method", noVig[1], fair[1], 1e-6)
        }
    }

    @Test
    fun `multiplicative matches its closed form directly`() {
        val fair = Devig.multiplicative(asymmetricRawProbs)
        val sum = asymmetricRawProbs.sum()
        assertEquals(asymmetricRawProbs[0] / sum, fair[0], 1e-9)
        assertEquals(asymmetricRawProbs[1] / sum, fair[1], 1e-9)
    }

    @Test
    fun `additive matches its closed form directly`() {
        val fair = Devig.additive(asymmetricRawProbs)
        val overround = asymmetricRawProbs.sum() - 1.0
        assertEquals(asymmetricRawProbs[0] - overround / 2, fair[0], 1e-9)
        assertEquals(asymmetricRawProbs[1] - overround / 2, fair[1], 1e-9)
    }

    @Test
    fun `power method solved exponent reproduces the fair probabilities`() {
        // Recover k from the solved fair probability itself (fair_i = raw_i^k) and confirm every
        // outcome agrees on the same k, rather than trusting the solver's own internals.
        val fair = Devig.power(asymmetricRawProbs)
        val kFromFirst = Math.log(fair[0]) / Math.log(asymmetricRawProbs[0])
        val kFromSecond = Math.log(fair[1]) / Math.log(asymmetricRawProbs[1])
        assertEquals(kFromFirst, kFromSecond, 1e-4)
        assertTrue("solved exponent should exceed 1 when there is real margin to remove", kFromFirst > 1.0)
    }

    @Test
    fun `three outcome market devigs correctly with multiplicative and additive`() {
        // Real vig (raw implied probabilities must sum to more than 1 — a book never offers an
        // arbitrage against itself); Devig.power in particular assumes this, see its doc comment.
        val threeWay = listOf(
            Odds.impliedProbability(2.0),
            Odds.impliedProbability(3.0),
            Odds.impliedProbability(4.0),
        )
        val multiplicativeFair = Devig.multiplicative(threeWay)
        assertEquals(1.0, multiplicativeFair.sum(), 1e-6)

        val additiveFair = Devig.additive(threeWay)
        assertEquals(1.0, additiveFair.sum(), 1e-6)

        val powerFair = Devig.power(threeWay)
        assertEquals(1.0, powerFair.sum(), 1e-6)

        val shinFair = Devig.shin(threeWay)
        assertEquals(1.0, shinFair.sum(), 1e-6)
    }
}
