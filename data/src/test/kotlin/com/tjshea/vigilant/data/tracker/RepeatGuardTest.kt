package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.tracker.RepeatGuard.Pos
import com.tjshea.vigilant.data.tracker.RepeatGuard.Rules
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepeatGuardTest {
    private val rules = Rules(maxBets = 2, maxDollars = 100.0)

    private fun pos(outcome: String, dollars: Double, contracts: Double, market: String = "m1") = Pos(market, outcome, dollars, contracts)

    @Test fun `a first bet on a side is allowed`() {
        assertNull(RepeatGuard.check(rules, emptyList(), "m1", "over", 20.0, 40.0))
    }

    @Test fun `the same side at a new price is held back after the limit`() {
        val held = listOf(pos("over", 20.0, 40.0), pos("over", 20.0, 38.0))
        val why = assertNotNull(RepeatGuard.check(rules, held, "m1", "over", 20.0, 36.0))
        assertTrue("same side" in why)
    }

    @Test fun `the same side is held back by dollars before the count`() {
        val held = listOf(pos("over", 90.0, 180.0))
        assertNotNull(RepeatGuard.check(rules, held, "m1", "over", 20.0, 38.0))
        assertNull(RepeatGuard.check(rules, held, "m1", "over", 10.0, 19.0))
    }

    @Test fun `a limit of zero is no limit`() {
        val held = List(10) { pos("over", 50.0, 100.0) }
        assertNull(RepeatGuard.check(Rules(0, 0.0), held, "m1", "over", 50.0, 100.0))
    }

    @Test fun `the other side is allowed only when both sides are a sure profit`() {
        // 40 contracts bought for $20 on Over; Under at 0.45 for 30 contracts = $13.50: spent 33.50; Over pays 40 (+6.50), Under pays 30 (-3.50) -> a loss
        val held = listOf(pos("over", 20.0, 40.0))
        assertNotNull(RepeatGuard.check(rules, held, "m1", "under", 13.5, 30.0, listOf("over", "under")))
        // Under 40 contracts for $13.60: spent 33.60; both pay 40 -> +6.40 either way
        assertNull(RepeatGuard.check(rules, held, "m1", "under", 13.6, 40.0, listOf("over", "under")))
    }

    @Test fun `a resting bid counts as if it filled`() {
        val held = listOf(pos("over", 20.0, 40.0), pos("under", 19.0, 40.0))
        // already 39 spent for 40 either way (+1); a second Under bid would make a loss on Over
        assertNotNull(RepeatGuard.check(rules, held, "m1", "under", 5.0, 8.0, listOf("over", "under")))
    }

    @Test fun `a three way market with an outcome nobody holds is never a sure profit`() {
        val held = listOf(pos("home", 20.0, 40.0))
        assertNotNull(RepeatGuard.check(rules, held, "m1", "away", 10.0, 30.0, listOf("home", "away", "draw")))
    }

    @Test fun `hedging off refuses any other side`() {
        val held = listOf(pos("over", 20.0, 40.0))
        assertNotNull(RepeatGuard.check(rules.copy(hedge = false), held, "m1", "under", 13.6, 40.0, listOf("over", "under")))
    }

    @Test fun `other markets are not in the way`() {
        val held = listOf(pos("a", 20.0, 40.0, market = "m2"), pos("a", 20.0, 40.0, market = "m2"))
        assertNull(RepeatGuard.check(rules, held, "m1", "over", 20.0, 40.0))
    }

    @Test fun `only open API bets with contracts are held`() {
        val bet = TrackedBetFixtures.open("o1", "m1", stake = 20.0, contracts = 40L)
        val mark = TrackedBetFixtures.open("o2", "m1", stake = 20.0, contracts = null)
        assertTrue(RepeatGuard.fromBets(listOf(bet, mark)).size == 1)
    }
}
