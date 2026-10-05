package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.LowUsageBids
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Low-API-usage prop bids' bid half (Tj, 2026-10-05; RESEARCH.md §92): at least 2.5% under a fair built from two or more fresh two-sided picked books, no longer than +130,
 * props the takers trade, nothing the low-usage scan didn't price. The first tests drive [MakerQuote] on hand-made lines; the last run a whole scan's pricing into a bid.
 */
class LowUsageBidTest {

    private val now = Fixtures.START_MS - 2 * 3_600_000L
    private val s = ScanSettings(leagues = setOf("NFL"), makerFocus = BidFocus.LOW_USAGE, maker = true, makerStakeMode = AutoBetStake.CUSTOM, makerStake = 5.0)
    private val rules = MakerRules.of(s)

    // ---- the rules ----------------------------------------------------------------------------------------------------------

    @Test
    fun `the mode's rules - props only, 2_5 percent, nothing longer than +130, every sharp rule on, games within 6 hours`() {
        assertEquals(setOf(BetKind.PROP), rules.kinds)
        assertEquals(0.025, rules.margin, 0.0)
        assertEquals(130, rules.maxOdds)
        assertTrue(rules.requireSharp && rules.anchorSharp && rules.sharpVeto && rules.quick && rules.skipObscure)
        assertEquals(2, rules.minBooks)
        assertEquals(6, rules.earlyHours)
        assertEquals(0.60, rules.maxPrice, 0.0)
        assertEquals(setOf("Kalshi", "ProphetX", "FanDuel"), rules.lowUsageBooks)
        // Other focuses carry none of it.
        val all = MakerRules.of(ScanSettings())
        assertTrue(all.lowUsageBooks.isEmpty())
        assertFalse(all.skipObscure)
        assertEquals(0, all.maxOdds)
        assertEquals(0.04, all.margin, 0.0)
    }

    @Test
    fun `the margin is Tj's but never under 2_5 percent, whatever the usual margin says`() {
        assertEquals(0.035, MakerRules.of(s.copy(lowUsageMargin = 0.035)).margin, 0.0)
        assertEquals(0.025, MakerRules.of(s.copy(lowUsageMargin = 0.01)).margin, 0.0)
        assertEquals(0.025, MakerRules.of(s.copy(lowUsageMargin = 0.0)).margin, 0.0)
        assertEquals("the usual margin setting is not the mode's", 0.025, MakerRules.of(s.copy(makerMargin = 0.08)).margin, 0.0)
    }

    @Test
    fun `+130 is the longest, a tighter limit of Tj's stays, a looser one or none is +130`() {
        assertEquals(130, MakerRules.of(s.copy(makerMaxOdds = 0)).maxOdds)
        assertEquals(130, MakerRules.of(s.copy(makerMaxOdds = 200)).maxOdds)
        assertEquals(130, MakerRules.of(s.copy(makerMaxOdds = 130)).maxOdds)
        assertEquals(120, MakerRules.of(s.copy(makerMaxOdds = 120)).maxOdds)
        assertEquals(100, MakerRules.of(s.copy(makerMaxOdds = 100)).maxOdds)
    }

    @Test
    fun `the 6 hour window is the trap guard's when that is tighter, and 6 when it is off or longer`() {
        assertEquals(6, MakerRules.of(s.copy(trapEarlyHours = 0)).earlyHours)
        assertEquals(6, MakerRules.of(s.copy(trapEarlyHours = 12)).earlyHours)
        assertEquals(3, MakerRules.of(s.copy(trapEarlyHours = 3)).earlyHours)
    }

    // ---- one line -----------------------------------------------------------------------------------------------------------

    private fun market(type: String = "PASSING_YARDS") = NovigMarket(
        "m1", "ev-m1", type, "OPEN", "Player 224.5 $type", Fixtures.START_MS, MarketFee.GAME,
        listOf(NovigOutcome("m1-over", "Over 224.5", "TBD"), NovigOutcome("m1-under", "Under 224.5", "TBD")),
    )

    private fun line(
        fair: Double = 0.50,
        sharp: List<Double> = listOf(0.50, 0.505),
        books: List<String> = listOf("Kalshi", "ProphetX"),
        kind: BetKind = BetKind.PROP,
        type: String = "PASSING_YARDS",
        league: String = "NFL",
        offer: Double? = 0.55,
    ) = MakerLine(
        market = market(type), outcomeId = "m1-over", startsTs = Fixtures.START_MS, league = league, eventName = "A @ B", marketLabel = "Passing Yards",
        selection = "Player Over 224.5", kind = kind, fair = fair, fairAsOfMs = now - 60_000, fairOld = false, books = books.size, fairBooks = books,
        bookFairs = sharp, sharpFairs = sharp, offer = offer, bestBid = null, live = false, source = BetTracker.SOURCE_VIGILANT,
    )

    private fun why(l: MakerLine) = (MakerQuote.decide(l, rules, now) as MakerDecision.Skip).why
    private fun post(l: MakerLine) = MakerQuote.decide(l, rules, now) as MakerDecision.Post

    @Test
    fun `the bid is at least 2_5 percent under the fair - and under the lowest picked book's own fair`() {
        val p = post(line(fair = 0.50, sharp = listOf(0.50, 0.505)))
        // 0.50 / 1.025 = 0.4878 → 0.485 on the grid.
        assertEquals(0.485, p.price, 1e-9)
        assertTrue("EV at the fair ${p.evAtFair}", p.evAtFair >= 0.025)
        // The blend is higher than the lowest book: the margin is taken under the lowest.
        val lower = post(line(fair = 0.52, sharp = listOf(0.49, 0.53)))
        assertEquals(0.475, lower.price, 1e-9)
        assertTrue("each picked book gives the bid its margin", listOf(0.49, 0.53).all { it / lower.price - 1.0 >= 0.025 })
        assertEquals(0.49, lower.anchorFair!!, 1e-12)
    }

    @Test
    fun `+130 is the boundary - 43_5 cents posts, 43 cents does not`() {
        // fair 0.4465 / 1.025 = 0.4356 → 0.435 (+129.9): posted.
        val inside = post(line(fair = 0.4465, sharp = listOf(0.4465, 0.447)))
        assertEquals(0.435, inside.price, 1e-9)
        assertTrue(100.0 / inside.price - 100.0 <= 130.0)
        // fair 0.44 / 1.025 = 0.4293 → 0.425 (+135.3): skipped, not raised to the limit's price.
        assertTrue(why(line(fair = 0.44, sharp = listOf(0.44, 0.441))).contains("longer odds than your +130 limit"))
        // A favorite always passes.
        assertEquals(0.585, post(line(fair = 0.60, sharp = listOf(0.60, 0.61), offer = 0.65)).price, 1e-9)
    }

    @Test
    fun `nothing priced over 60 cents - a favorite that long almost never fills`() {
        // 0.64 / 1.025 = 0.6244 → 0.620 is past the 0.60 window.
        assertTrue(why(line(fair = 0.64, sharp = listOf(0.64, 0.65), offer = 0.70)).contains("outside the price window"))
    }

    @Test
    fun `a line is bid on only when 2 or more of the PICKED books made its fair`() {
        assertEquals(LowUsage.NOT_PRICED, why(line(books = listOf("Kalshi"))))
        assertEquals(LowUsage.NOT_PRICED, why(line(books = emptyList())))
        // A soft book in the fair: a full scan's line, not this mode's.
        assertEquals(LowUsage.NOT_PRICED, why(line(books = listOf("Kalshi", "ProphetX", "DraftKings"))))
        // Pinnacle wasn't picked.
        assertEquals(LowUsage.NOT_PRICED, why(line(books = listOf("Kalshi", "Pinnacle"))))
        // Any two of the three picked are enough, and all three.
        assertTrue(MakerQuote.decide(line(books = listOf("ProphetX", "FanDuel")), rules, now) is MakerDecision.Post)
        assertTrue(MakerQuote.decide(line(books = listOf("Kalshi", "ProphetX", "FanDuel"), sharp = listOf(0.5, 0.505, 0.51)), rules, now) is MakerDecision.Post)
        // Picking others changes who counts.
        val pinn = MakerRules.of(s.copy(lowUsageBooks = setOf("kalshi", "pinnacle")))
        assertTrue(MakerQuote.decide(line(books = listOf("Kalshi", "Pinnacle")), pinn, now) is MakerDecision.Post)
        assertEquals(LowUsage.NOT_PRICED, (MakerQuote.decide(line(books = listOf("Kalshi", "ProphetX")), pinn, now) as MakerDecision.Skip).why)
    }

    @Test
    fun `props only - a team total, a moneyline and a game total get no bid`() {
        assertTrue(why(line(kind = BetKind.TEAM_TOTAL)).contains("off for bids"))
        assertTrue(why(line(kind = BetKind.MONEYLINE)).contains("off for bids"))
        assertTrue(why(line(kind = BetKind.TOTAL)).contains("off for bids"))
    }

    @Test
    fun `a kind of prop takers were measured to trade rarely is skipped, a busy one or an unmeasured one is not`() {
        // NHL assists: $75 a listed market a day. NHL shots on goal: $780. NBA points: never measured.
        assertTrue(why(line(type = "ASSISTS", league = "NHL")).startsWith("Takers rarely trade"))
        assertTrue(MakerQuote.decide(line(type = "SHOTS_ON_GOAL", league = "NHL"), rules, now) is MakerDecision.Post)
        assertTrue(MakerQuote.decide(line(type = "POINTS", league = "NBA"), rules, now) is MakerDecision.Post)
        assertTrue(MarketPopularity.measuredObscure("NHL", "ASSISTS"))
        assertFalse(MarketPopularity.measuredObscure("NHL", "SHOTS_ON_GOAL"))
        assertFalse(MarketPopularity.measuredObscure("NBA", "POINTS"))
        // The usual bids don't skip them.
        assertTrue(MakerQuote.decide(line(type = "ASSISTS", league = "NHL"), MakerRules.of(ScanSettings()).copy(kinds = setOf(BetKind.PROP)), now) is MakerDecision.Post)
    }

    @Test
    fun `the likeliest fills go up first - leading bids, hot markets, then price`() {
        val hot = MakerDecision.Post(line(type = "RUSHING_ATTEMPTS"), 0.50, 100, 0.03)
        val popular = MakerDecision.Post(line(type = "PASSING_YARDS"), 0.50, 100, 0.03)
        val order = listOf(popular, hot).sortedWith(MakerPlan.priority(rules))
        assertEquals("the hot market first", listOf(hot, popular), order)
        assertTrue(rules.quick)
    }

    // ---- a whole scan into a bid -------------------------------------------------------------------------------------------

    private val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
    private val prop = NovigMarket(
        "p1", Fixtures.EVENT_ID, "PASSING_YARDS", "OPEN", "Lamar Jackson 224.5 PASSING_YARDS", Fixtures.START_MS, MarketFee.GAME,
        listOf(NovigOutcome("o1", "Over 224.5", "TBD"), NovigOutcome("u1", "Under 224.5", "TBD")),
    )

    private fun quote(key: String, title: String, over: Double, under: Double, ageMs: Long, oneSided: Boolean = false) = RefBookMarket(
        key, title, LineKind.PLAYER_PROP,
        listOfNotNull(RefQuote(Side.OVER, over, 224.5), if (oneSided) null else RefQuote(Side.UNDER, under, 224.5)), now - ageMs, 0, "Lamar Jackson", "PASSING_YARDS",
    )

    /** What the bid maker makes of the low-usage scan of one prop priced by [quotes]. */
    private fun bid(vararg quotes: RefBookMarket, settings: ScanSettings = s): MakerDecision {
        val e = settings.effective()
        val refs = listOf(RefSnapshot("americanfootball_nfl", listOf(RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, "Dallas Cowboys", "Baltimore Ravens", quotes.toList())), now))
        val plan = Planner.plan(listOf(event), listOf(prop), refs, e, now)
        val book = NovigBook("p1", 1, mapOf("o1" to listOf(BidLevel(470, 10_000)), "u1" to listOf(BidLevel(500, 10_000))), now)
        val result = Pricing.price(plan, mapOf("p1" to book), e, now)
        val lines = MakerLines.from(result, settings, now)
        val over = lines.firstOrNull { it.outcomeId == "o1" } ?: error("the over wasn't a line: ${result.opportunities.map { it.fairProbability }}")
        return MakerQuote.decide(over, MakerRules.of(settings), now)
    }

    private val kalshi = quote("kalshi", "Kalshi", 1.95, 1.95, 60_000)
    private val prophetx = quote("prophetx", "ProphetX", 1.90, 2.00, 60_000)

    @Test
    fun `two fresh two-sided picked books price a bid at least 2_5 percent under the fair, at +130 or shorter`() {
        val d = bid(kalshi, prophetx) as MakerDecision.Post
        assertTrue("EV at the fair ${d.evAtFair}", d.evAtFair >= 0.025)
        assertTrue(100.0 / d.price - 100.0 <= 130.0)
        assertEquals(setOf("Kalshi", "ProphetX"), d.line.fairBooks.toSet())
        assertTrue("under every picked book's own fair", d.line.sharpFairs.all { it > d.price })
    }

    @Test
    fun `one book, a stale book or a one-sided book leaves no bid - and each is said`() {
        // The scan makes no fair from one fresh two-sided pick, so the line isn't there to bid on at all.
        assertTrue(runCatching { bid(kalshi) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { bid(kalshi, quote("prophetx", "ProphetX", 1.90, 2.00, 6 * 60_000L)) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { bid(kalshi, quote("prophetx", "ProphetX", 1.90, 2.00, 60_000, oneSided = true)) }.exceptionOrNull() is IllegalStateException)
        // A third book, stale, doesn't take the bid away.
        val d = bid(kalshi, prophetx, quote("fanduel", "FanDuel", 1.70, 2.30, 6 * 60_000L)) as MakerDecision.Post
        assertEquals(setOf("Kalshi", "ProphetX"), d.line.fairBooks.toSet())
    }

    @Test
    fun `a line priced by an older full scan, with a soft book in it, gets no bid`() {
        // The same quotes through a plain (not low-usage) scan: the blend includes DraftKings, which isn't a pick.
        val full = ScanSettings(leagues = setOf("NFL"), maker = true)
        val refs = listOf(
            RefSnapshot(
                "americanfootball_nfl",
                listOf(RefEvent("r", "americanfootball_nfl", Fixtures.START_MS, "Dallas Cowboys", "Baltimore Ravens", listOf(kalshi, prophetx, quote("draftkings", "DraftKings", 1.92, 1.98, 60_000)))),
                now,
            ),
        )
        val plan = Planner.plan(listOf(event), listOf(prop), refs, full, now)
        val book = NovigBook("p1", 1, mapOf("o1" to listOf(BidLevel(470, 10_000)), "u1" to listOf(BidLevel(500, 10_000))), now)
        val result = Pricing.price(plan, mapOf("p1" to book), full, now)
        val l = MakerLines.from(result, s, now).first { it.outcomeId == "o1" }
        assertTrue(l.fairBooks.contains("DraftKings"))
        assertEquals(LowUsage.NOT_PRICED, (MakerQuote.decide(l, MakerRules.of(s), now) as MakerDecision.Skip).why)
        assertNull((MakerQuote.decide(l, MakerRules.of(s), now) as? MakerDecision.Post))
    }
}
