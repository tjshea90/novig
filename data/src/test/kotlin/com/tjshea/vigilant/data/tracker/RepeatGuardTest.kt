package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.tracker.RepeatGuard.Pos
import com.tjshea.vigilant.data.tracker.RepeatGuard.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatGuardTest {
    private val rules = Rules(maxBets = 2, maxDollars = 100.0)

    private fun tracked(id: String, contracts: Long?) = TrackedBet(
        id = id, createdAtMs = 0L, league = "NFL", eventName = "A @ B", startsTs = 1L, marketLabel = "Total", selection = "Over", marketId = "m1", outcomeId = "over",
        price = 0.5, cost = 0.5, fairAtBet = 0.52, evPercentAtBet = 0.04, stake = 20.0, orderId = "ord-$id", contracts = contracts, paid = 20.0, fee = 0.0, eventId = "e1",
    )

    private fun pos(outcome: String, dollars: Double, contracts: Double, market: String = "m1") = Pos(market, outcome, dollars, contracts)

    private val start = 1_792_000_000_000L
    private fun fam(selection: String, market: String = "Spread", event: String = "Portland Trail Blazers @ Sacramento Kings") = RepeatGuard.family(event, start, market, selection)

    @Test fun `a team's spreads are one family whatever the line or sign`() {
        assertEquals(fam("Sacramento Kings +6.5"), fam("Sacramento Kings +3.5"))
        assertEquals(fam("Sacramento Kings +1.5"), fam("Sacramento Kings -1.5"))
        assertEquals(fam("Sacramento Kings +6.5"), fam("Sacramento Kings +3.5", market = "Point Spread".let { "Spread" }))
        assertTrue(fam("Sacramento Kings +6.5") != fam("Portland Trail Blazers -6.5"))
        // Another game of the same teams a day later is another family.
        assertTrue(fam("Sacramento Kings +6.5") != RepeatGuard.family("Portland Trail Blazers @ Sacramento Kings", start + 24 * 3_600_000L, "Spread", "Sacramento Kings +6.5"))
        // A player's over and under are different families; two overs at any line are one.
        assertEquals(fam("Bo Nix Under 12.5", "Rushing Yards"), fam("Bo Nix Under 11.5", "Rushing Yards"))
        assertTrue(fam("Bo Nix Under 12.5", "Rushing Yards") != fam("Bo Nix Over 12.5", "Rushing Yards"))
        assertEquals("", fam(""))
    }

    @Test fun `another line of the same team in the same game is held back, once the limit is reached`() {
        val f = fam("Sacramento Kings +6.5")
        val held = listOf(Pos("m65", "sac65", 3.0, 6.0, family = f))
        assertNull(RepeatGuard.check(Rules(maxBets = 2, maxLines = 0), held, "m35", "sac35", 2.0, 4.0, family = f))
        val why = RepeatGuard.check(Rules(maxBets = 2, maxLines = 1), held, "m35", "sac35", 2.0, 4.0, family = fam("Sacramento Kings +3.5"))!!
        assertTrue(why, "other line" in why)
        assertNull(RepeatGuard.check(Rules(maxBets = 2, maxLines = 2), held, "m35", "sac35", 2.0, 4.0, family = f))
        // The same line again is the old same-side rule, not this one; no family known = never counted; another team is fine.
        assertNull(RepeatGuard.check(Rules(maxBets = 2, maxLines = 1), held, "m65", "sac65", 2.0, 4.0, family = f))
        assertNull(RepeatGuard.check(Rules(maxBets = 2, maxLines = 1), held, "m35", "sac35", 2.0, 4.0))
        assertNull(RepeatGuard.check(Rules(maxBets = 2, maxLines = 1), held, "mp", "por", 2.0, 4.0, family = fam("Portland Trail Blazers -6.5")))
    }

    @Test fun `open bets and resting bids both count as lines`() {
        val bet = tracked("1", 40L).copy(eventName = "Portland Trail Blazers @ Sacramento Kings", marketLabel = "Spread", selection = "Sacramento Kings +6.5", startsTs = start, outcomeId = "sac65", marketId = "m65")
        val held = RepeatGuard.fromBets(listOf(bet))
        assertEquals(fam("Sacramento Kings +6.5"), held.single().family)
        assertNotNull(RepeatGuard.check(Rules(maxLines = 1), held, "m35", "sac35", 1.0, 2.0, family = fam("Sacramento Kings +3.5")))
    }

    @Test fun `a first bet on a side is allowed`() {
        assertNull(RepeatGuard.check(rules, emptyList(), "m1", "over", 20.0, 40.0))
    }

    @Test fun `the same side at a new price is held back after the limit`() {
        val held = listOf(pos("over", 20.0, 40.0), pos("over", 20.0, 38.0))
        val why = RepeatGuard.check(rules, held, "m1", "over", 20.0, 36.0)!!
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
        val bet = tracked("o1", 4_000L)
        val mark = tracked("o2", null)
        val held = RepeatGuard.fromBets(listOf(bet, mark, tracked("o3", 4_000L).copy(status = BetStatus.WON)))
        assertTrue(held.size == 1)
        assertEquals(40.0, held.single().payout, 1e-9)
    }

    @Test fun `the red X says why a side is held: the same side, or another line of its team, player or prop`() {
        val fam = RepeatGuard.family("Phoenix Suns @ Portland Trail Blazers", 1_000L, "Three Pointers Made", "Khaman Maluach Under 0.5")
        val held = listOf(RepeatGuard.Pos("m1", "under-0.5", 2.69, 4.76, count = 1, family = fam))
        assertTrue(RepeatGuard.heldWhy(held, "under-0.5", fam)!!.startsWith("You already hold this same side"))
        val otherLine = RepeatGuard.family("Phoenix Suns @ Portland Trail Blazers", 1_000L, "Three Pointers Made", "Khaman Maluach Under 1.5")
        assertEquals(fam, otherLine)
        assertTrue(RepeatGuard.heldWhy(held, "under-1.5", otherLine)!!.contains("another line"))
        assertNull("another player is not held", RepeatGuard.heldWhy(held, "x", RepeatGuard.family("Phoenix Suns @ Portland Trail Blazers", 1_000L, "Three Pointers Made", "Devin Booker Under 2.5")))
        assertNull(RepeatGuard.heldWhy(emptyList(), "under-0.5", fam))
        // the other side of the same market is a hedge, not a repeat
        assertNull(RepeatGuard.heldWhy(held, "over-0.5", ""))
    }
}
