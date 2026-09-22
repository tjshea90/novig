package com.tjshea.vigilant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvCalculatorTest {

    // A reference market where the true/fair probability of the favorite is 65%, built from a
    // single sharp-book quote so the exact expected fair probability is known ahead of time.
    private fun favoriteReference(): ReferenceProbabilities {
        val quotes = listOf(BookQuote("Pinnacle", listOf(1.44, 3.00)))
        return Consensus.computeReferenceProbabilities(quotes, DevigMethod.MULTIPLICATIVE)
    }

    @Test
    fun `flags a genuinely underpriced pregame Novig line as positive EV`() {
        val reference = favoriteReference()
        val fairFavoriteProb = reference.fairProbabilityByOutcome[0]

        // Novig is offering the favorite cheaper than fair value.
        val novig = NovigQuote(price = fairFavoriteProb - 0.05, isMaker = false, context = TradeContext.PREGAME_STRAIGHT)

        val result = EvCalculator.evaluate(
            eventName = "Home Team vs Away Team",
            marketDescription = "Moneyline",
            outcomeLabel = "Home Team",
            outcomeIndex = 0,
            novig = novig,
            reference = reference,
        )

        assertEquals(0.05, result.rawEdge, 1e-9)
        assertEquals(result.rawEdge, result.evPer1, 1e-9) // identical for a $1-payout Novig contract
        assertEquals(0.0, (result.fee as FeeResult.Known).amountPerDollarStaked, 1e-9)
        assertEquals(result.evPer1, result.netEv)
        assertTrue(result.isPositiveEv)
    }

    @Test
    fun `flags an overpriced Novig line as negative EV, not positive`() {
        val reference = favoriteReference()
        val fairFavoriteProb = reference.fairProbabilityByOutcome[0]

        // Novig wants MORE than fair value for the favorite — a bad price, not an edge.
        val novig = NovigQuote(price = fairFavoriteProb + 0.05, isMaker = false, context = TradeContext.PREGAME_STRAIGHT)

        val result = EvCalculator.evaluate(
            eventName = "Home Team vs Away Team",
            marketDescription = "Moneyline",
            outcomeLabel = "Home Team",
            outcomeIndex = 0,
            novig = novig,
            reference = reference,
        )

        assertTrue(result.rawEdge < 0.0)
        assertFalse(result.isPositiveEv)
    }

    @Test
    fun `a live-straight taker fee can turn a thin edge into a net loss`() {
        val reference = favoriteReference()
        val fairFavoriteProb = reference.fairProbabilityByOutcome[0]

        // A razor-thin raw edge that the live taker fee should wipe out.
        val novig = NovigQuote(price = fairFavoriteProb - 0.001, isMaker = false, context = TradeContext.LIVE_STRAIGHT)

        val result = EvCalculator.evaluate(
            eventName = "Home Team vs Away Team",
            marketDescription = "Moneyline - Live",
            outcomeLabel = "Home Team",
            outcomeIndex = 0,
            novig = novig,
            reference = reference,
        )

        assertTrue("raw edge should still be positive before fees", result.rawEdge > 0.0)
        assertTrue("fee should exceed the raw edge here", (result.fee as FeeResult.Known).amountPerDollarStaked > result.rawEdge)
        assertFalse("net EV must go negative once the fee eats the whole edge", result.isPositiveEv)
    }

    @Test
    fun `a priced parlay fee is netted out like any other known fee`() {
        val reference = favoriteReference()
        val novig = NovigQuote(price = 0.3, isMaker = false, context = TradeContext.PARLAY)

        val result = EvCalculator.evaluate(
            eventName = "Home Team vs Away Team",
            marketDescription = "3-leg parlay",
            outcomeLabel = "Home Team",
            outcomeIndex = 0,
            novig = novig,
            reference = reference,
        )

        val fee = result.fee
        check(fee is FeeResult.Known)
        assertEquals(result.evPer1 - fee.amountPerDollarStaked, result.netEv!!, 1e-9)
    }

    @Test
    fun `roi is ev per dollar divided by novig's own price`() {
        val reference = favoriteReference()
        val fairFavoriteProb = reference.fairProbabilityByOutcome[0]
        val novigPrice = fairFavoriteProb - 0.05
        val novig = NovigQuote(price = novigPrice, isMaker = false, context = TradeContext.PREGAME_STRAIGHT)

        val result = EvCalculator.evaluate(
            eventName = "Home Team vs Away Team",
            marketDescription = "Moneyline",
            outcomeLabel = "Home Team",
            outcomeIndex = 0,
            novig = novig,
            reference = reference,
        )

        assertEquals(result.evPer1 / novigPrice, result.roi, 1e-9)
    }
}
