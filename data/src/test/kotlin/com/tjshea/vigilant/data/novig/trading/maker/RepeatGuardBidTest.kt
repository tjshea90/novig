package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.RepeatGuard
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repeat and hedge guard in the bid desk (Tj, 2026-10-12): a side already bet to the limit gets no new bid at a new price; the other side only when both are a sure profit. */
class RepeatGuardBidTest {
    private val now = 1_800_000_000_000L
    private val start = now + 6 * 3_600_000L
    private val guard = RepeatGuard.Rules(maxBets = 2, maxDollars = 100.0, hedge = true)
    private val rules = MakerRules.of(ScanSettings()).copy(stakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM, customStake = 5.0, maxStake = 10.0, repeat = guard)

    private val market = NovigMarket(
        marketId = "m1", eventId = "ev1", marketType = "RECEIVING_YARDS", status = "OPEN", description = "Player Receiving Yards", startsTs = start,
        fee = MarketFee.GAME, outcomes = listOf(NovigOutcome("m1-over", "Over 50.5", "TBD"), NovigOutcome("m1-under", "Under 50.5", "TBD")),
    )

    private fun post(outcome: String, price: Double, contracts: Long = 1_000) = MakerDecision.Post(
        MakerLine(
            market = market, outcomeId = outcome, startsTs = start, league = "NFL", eventName = "A @ B", marketLabel = "Player Receiving Yards", selection = outcome, kind = BetKind.PROP,
            fair = maxOf(0.52, price + 0.04), fairAsOfMs = now - 30_000, fairOld = false, books = 3, offer = 0.55, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
        ),
        price, contracts, 0.04,
    )

    private fun plan(wanted: List<MakerDecision.Post>, held: List<RepeatGuard.Pos>, r: MakerRules = rules) = MakerPlan.plan(wanted, emptyList(), r, now, held = held)

    private fun pos(outcome: String, dollars: Double, payout: Double) = RepeatGuard.Pos("m1", outcome, dollars, payout)

    @Test
    fun `a side bet twice already gets no new bid at a new price`() {
        val held = listOf(pos("m1-over", 5.0, 10.0), pos("m1-over", 5.0, 10.0))
        val p = plan(listOf(post("m1-over", 0.45)), held)
        assertTrue(p.places.isEmpty())
        assertEquals(mapOf(MakerPlan.REPEAT_GUARD to 1), p.waiting)
        // One bid filled before: another may go up. A BET held there (by hand or by the engine) gets no bid on top (Tj, 2026-10-25).
        assertEquals(1, plan(listOf(post("m1-over", 0.45)), held.take(1).map { it.copy(who = RepeatGuard.Who.BID) }).places.size)
        assertTrue(plan(listOf(post("m1-over", 0.45)), held.take(1)).places.isEmpty())
    }

    @Test
    fun `the other side goes up only when both sides are a sure profit`() {
        // Over held: $5 for a $10 payout. Under at 0.45 x 1000 contracts = $4.50 for $10: spent 9.50, both pay 10 -> +0.50 either way: allowed.
        assertEquals(1, plan(listOf(post("m1-under", 0.45)), listOf(pos("m1-over", 5.0, 10.0))).places.size)
        // Under 0.55 x 1000 = $5.50: spent 10.50 against a $10 payout: a locked loss, refused.
        val loss = plan(listOf(post("m1-under", 0.55)), listOf(pos("m1-over", 5.0, 10.0)))
        assertTrue(loss.places.isEmpty())
        assertEquals(mapOf(MakerPlan.REPEAT_GUARD to 1), loss.waiting)
    }

    @Test
    fun `with the guard off nothing changes`() {
        val held = listOf(pos("m1-over", 5.0, 10.0), pos("m1-over", 5.0, 10.0))
        assertEquals(1, plan(listOf(post("m1-over", 0.45)), held, rules.copy(repeat = null)).places.size)
    }
}
