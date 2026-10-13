package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidSlider
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-13: "More, smaller bids ... post the most profitable ones first (from the study) and then the riskier ones after, but with more stringent rules for risky bids, such as all books plus
 * a sharp book must agree." The tiers ([BidTier]) from research/profitable_types_2026-10-13.md, their order, their stricter rules, and that the mode never loosens an EV rule.
 */
class BidTierTest {
    private val now = 1_800_000_000_000L
    private val start = now + 3 * 3_600_000L

    private fun line(id: String, league: String, kind: BetKind, selection: String, marketType: String = "TEAM_TOTAL", books: Int = 6, fair: Double = 0.50,
                     bookFairs: List<Double> = List(books) { 0.50 }, sharp: List<Double> = listOf(0.50)) = MakerLine(
        market = NovigMarket(
            marketId = "m-$id", eventId = "ev-$id", marketType = marketType, status = "OPEN", description = marketType, startsTs = start, fee = MarketFee.GAME,
            outcomes = listOf(NovigOutcome("$id-a", selection, "TBD"), NovigOutcome("$id-b", "other", "TBD")),
        ),
        outcomeId = "$id-a", startsTs = start, league = league, eventName = "A @ B", marketLabel = marketType, selection = selection, kind = kind,
        fair = fair, fairAsOfMs = now - 30_000, fairOld = false, books = books, bookFairs = bookFairs, sharpFairs = sharp, offer = 0.60, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
    )

    private val many = ScanSettings(bankroll = 185.0).let { BidSlider.withMany(it, true, 60) }
    private val rules = MakerRules.of(many)

    private fun decide(l: MakerLine, r: MakerRules = rules) = MakerQuote.decide(l, r, now)

    @Test fun `the tiers follow the study`() {
        assertEquals(BidTier.PROVEN, BidTier.of(line("a", "NHL", BetKind.PROP, "Skater Over 2.5", "SHOTS")))
        assertEquals(BidTier.PROVEN, BidTier.of(line("b", "NCAAF", BetKind.TEAM_TOTAL, "Team Under 24.5")))
        assertEquals(BidTier.STEADY, BidTier.of(line("c", "NBA", BetKind.PROP, "Player Under 20.5", "POINTS")))
        assertEquals(BidTier.RISKY, BidTier.of(line("d", "NBA", BetKind.PROP, "Player Over 20.5", "POINTS")))
        assertEquals(BidTier.RISKY, BidTier.of(line("e", "NFL", BetKind.TEAM_TOTAL, "Team Under 21.5")))
        assertEquals(BidTier.RISKY, BidTier.of(line("f", "NBA", BetKind.SPREAD, "Team -3.5", "SPREAD")))
    }

    @Test fun `the mode sets 1 over 16 Kelly, hundreds of bids and every kind, and turning it off gives the old stake and count back`() {
        assertEquals(AutoBetStake.SIXTEENTH_KELLY, many.makerStakeMode)
        assertEquals(BidSlider.MANY_BIDS, many.makerMaxBids)
        assertTrue(many.makerKinds.containsAll(BetKind.entries))
        assertTrue(rules.tiered)
        val off = BidSlider.withMany(many, false, 60)
        assertEquals(AutoBetStake.QUARTER_KELLY, off.makerStakeMode)
        assertEquals(20, off.makerMaxBids)
        assertFalse(MakerRules.of(off).tiered)
        assertTrue(BidSlider.matches(many, 60))
    }

    @Test fun `a proven bid is posted under the slider's margin, a risky one under a wider margin and at half the stake`() {
        val proven = decide(line("p", "NHL", BetKind.TEAM_TOTAL, "Team Over 2.5")) as MakerDecision.Post
        val risky = decide(line("r", "NBA", BetKind.PROP, "Player Over 20.5", "POINTS")) as MakerDecision.Post
        assertTrue(proven.evAtFair >= many.makerMargin - 1e-9)
        assertTrue("risky ${risky.evAtFair} vs proven ${proven.evAtFair}", risky.evAtFair >= many.makerMargin + BidTier.RISKY_EXTRA_MARGIN - 1e-9)
        assertTrue("risky ${risky.cost} proven ${proven.cost}", risky.cost < proven.cost)
    }

    @Test fun `every posted bid is still positive EV at the floor, in every tier`() {
        val lines = listOf(
            line("1", "NHL", BetKind.PROP, "S Over 2.5", "SHOTS"), line("2", "NBA", BetKind.PROP, "P Under 20.5", "POINTS"), line("3", "NBA", BetKind.PROP, "P Over 20.5", "POINTS"),
        )
        for (l in lines) (decide(l) as? MakerDecision.Post)?.let { assertTrue(it.evAtFair >= BidSlider.MIN_MARGIN - 1e-9) }
    }

    @Test fun `a risky bid needs a sharp book and every book that prices it to agree`() {
        val noSharp = decide(line("n", "NBA", BetKind.PROP, "P Over 20.5", "POINTS", sharp = emptyList()))
        assertTrue((noSharp as MakerDecision.Skip).why.contains("sharp book"))
        // one book of six disagrees (its own fair is under the bid price)
        val dissent = decide(line("d", "NBA", BetKind.PROP, "P Over 20.5", "POINTS", bookFairs = listOf(0.50, 0.50, 0.50, 0.50, 0.50, 0.40)))
        assertTrue((dissent as MakerDecision.Skip).why.contains("every book"))
        val thin = decide(line("t", "NBA", BetKind.PROP, "P Over 20.5", "POINTS", books = 3, bookFairs = listOf(0.50, 0.50, 0.50)))
        assertTrue((thin as MakerDecision.Skip).why.contains("at least"))
        assertTrue(decide(line("ok", "NBA", BetKind.PROP, "P Over 20.5", "POINTS")) is MakerDecision.Post)
    }

    @Test fun `a steady bid needs a sharp book and one more book than the slider's minimum`() {
        val noSharp = decide(line("s", "NBA", BetKind.PROP, "P Under 20.5", "POINTS", sharp = emptyList()))
        assertTrue((noSharp as MakerDecision.Skip).why.contains("sharp book"))
        val few = decide(line("f", "NBA", BetKind.PROP, "P Under 20.5", "POINTS", books = rules.minBooks, bookFairs = List(rules.minBooks) { 0.50 }))
        assertTrue((few as MakerDecision.Skip).why.contains("books on its side"))
    }

    @Test fun `a proven bid keeps the slider's own rules (no extra books or sharp book asked)`() {
        val l = line("pp", "NHL", BetKind.TEAM_TOTAL, "T Over 2.5", sharp = emptyList(), books = rules.minBooks, bookFairs = List(rules.minBooks) { 0.50 })
        assertTrue(decide(l) is MakerDecision.Post || (decide(l) as MakerDecision.Skip).why.let { !it.contains("sharp book (Pinnacle") })
    }

    @Test fun `proven bids go up before steady ones before risky ones`() {
        val posts = listOf(
            line("r", "NBA", BetKind.PROP, "P Over 20.5", "POINTS"), line("s", "NBA", BetKind.PROP, "P Under 20.5", "POINTS"), line("p", "NHL", BetKind.TEAM_TOTAL, "T Over 2.5"),
        ).map { decide(it) as MakerDecision.Post }
        val ordered = posts.sortedWith(MakerQuote.priority(rules, now)).map { BidTier.of(it.line) }
        assertEquals(listOf(BidTier.PROVEN, BidTier.STEADY, BidTier.RISKY), ordered)
    }

    @Test fun `with the mode off nothing changes`() {
        val off = MakerRules.of(ScanSettings(bankroll = 185.0))
        assertFalse(off.tiered)
        assertEquals(MakerRules.POSTS_PER_PASS, off.postsPerPass)
        assertEquals(MakerRules.MANY_POSTS_PER_PASS, rules.postsPerPass)
    }
}
