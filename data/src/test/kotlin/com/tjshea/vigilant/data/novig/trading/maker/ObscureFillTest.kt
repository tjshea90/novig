package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BidFocus
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-07: "If auto bid feature can't find enough bids that are popular, include obscure bids as well, up to the max amount of money that I selected or that is in the
 * wallet. But prioritize the bids, popular large markets most likely to get a taker first, then if there is room, obscure bids. But there must be strict safeguards on obscure
 * bids, such as sharp markets must agree and/or the positive EV must be a good margin." Quick & likely's small-market fill: a line that fails only the popularity filters is a
 * small-market bid under stricter checks, ranked after every popular bid, and taken down to make room for one.
 */
class ObscureFillTest {

    private val now = 1_800_000_000_000L
    private val start = now + 3 * 3_600_000L

    /** [marketType] decides popular (NFL RECEIVING_YARDS, $490 a day) or small (NFL LONGEST_RECEPTION, $51 a day); [id] makes the market and the side distinct. */
    private fun line(id: String, marketType: String = "RECEIVING_YARDS", books: Int = 8, fair: Double = 0.50, sharp: List<Double> = listOf(0.50), bestBid: Double? = null) = MakerLine(
        market = NovigMarket(
            marketId = "m-$id", eventId = "ev-$id", marketType = marketType, status = "OPEN", description = marketType, startsTs = start, fee = MarketFee.GAME,
            outcomes = listOf(NovigOutcome("$id-over", "Over 50.5", "TBD"), NovigOutcome("$id-under", "Under 50.5", "TBD")),
        ),
        outcomeId = "$id-over", startsTs = start, league = "NFL", eventName = "A @ B", marketLabel = marketType, selection = "Player Over 50.5", kind = BetKind.PROP,
        fair = fair, fairAsOfMs = now - 30_000, fairOld = false, books = books, sharpFairs = sharp, offer = 0.60, bestBid = bestBid, live = false, source = BetTracker.SOURCE_VIGILANT,
    )

    private val small = "LONGEST_RECEPTION"

    private fun rules(f: (ScanSettings) -> ScanSettings = { it }, g: (MakerRules) -> MakerRules = { it }) =
        g(MakerRules.of(f(ScanSettings(makerFocus = BidFocus.QUICK_LIKELY))).copy(stakeMode = AutoBetStake.CUSTOM, customStake = 5.0, maxStake = 10.0))

    private fun decide(l: MakerLine, r: MakerRules = rules()) = MakerQuote.decide(l, r, now)

    private fun why(l: MakerLine, r: MakerRules = rules()) = (decide(l, r) as MakerDecision.Skip).why

    // ---- the tiers ---------------------------------------------------------------------------------------------------------

    @Test
    fun `a popular line is a popular bid exactly as before, a small-market line is a small-market bid at a wider margin and half the stake`() {
        val popular = decide(line("p")) as MakerDecision.Post
        assertFalse(popular.obscure)
        assertEquals(0.480, popular.price, 1e-9)
        assertEquals(5.0, popular.cost, 0.01)
        val obscure = decide(line("o", small)) as MakerDecision.Post
        assertTrue(obscure.obscure)
        // 6% under the sharp fair 0.50 on Novig's half-cent grid, not the 4% of a popular bid.
        assertEquals(0.470, obscure.price, 1e-9)
        assertTrue(obscure.evAtFair >= 0.06 - 1e-9)
        // Half the stake: $2.50 of the $5.
        assertTrue("cost ${obscure.cost}", obscure.cost in 2.0..2.5 + 1e-9)
    }

    @Test
    fun `with the fill off a small-market line is skipped exactly as before, and the switch only exists inside Quick and likely`() {
        val off = rules({ it.copy(makerObscureFill = false) })
        assertFalse(off.obscureFill)
        assertTrue(why(line("o", small), off).contains("rarely trade"))
        assertTrue(why(line("o", "RECEIVING_YARDS", books = 3), off).startsWith("Only 3 books price this line (you need 5)"))
        // On in the settings but not Quick & likely: the rules never carry it (All bids has no popularity filter to fill past).
        assertFalse(MakerRules.of(ScanSettings(makerObscureFill = true)).obscureFill)
        assertTrue(rules().obscureFill)
    }

    // ---- each safeguard stops a small-market line on its own ---------------------------------------------------------------------

    @Test
    fun `a line one or two books price never gets a bid, even here`() {
        val r = rules()
        assertTrue(why(line("o", small, books = 2), r).startsWith("Only 2 books price this small-market line (a small-market bid needs 3)"))
        assertTrue(why(line("o", small, books = 1), r).startsWith("Only 1 book price this small-market line"))
        assertTrue(decide(line("o", small, books = 3), r) is MakerDecision.Post)
        // The floor is Tj's: 5 books needed, 4 are too few.
        assertTrue(why(line("o", small, books = 4), rules({ it.copy(makerObscureMinBooks = 5) })).contains("needs 5"))
    }

    @Test
    fun `a small market gets a bid only where a sharp book prices the line - even with the sharp requirement turned off`() {
        assertTrue(why(line("o", small, sharp = emptyList())).contains("a sharp book must agree"))
        val noRequire = rules(g = { it.copy(requireSharp = false) })
        assertTrue(why(line("o", small, sharp = emptyList()), noRequire).contains("small market gets a bid only where a sharp book"))
        // A popular line is not held to it (its own rules decide).
        assertTrue(decide(line("p", sharp = emptyList()), noRequire) is MakerDecision.Post)
    }

    @Test
    fun `every sharp book's own price must give a small-market bid a real edge - more than the normal veto asks`() {
        // Not anchored to the sharp book: the bid is priced from the blend 0.50 (6% under: 0.470) and the sharp book says 0.48: only 2.1% for it, under the 3% bar.
        val loose = rules(g = { it.copy(anchorSharp = false) })
        assertTrue(why(line("o", small, sharp = listOf(0.48)), loose).contains("under 3.0% edge (small markets need more)"))
        // The same sharp price on a popular line passes the normal veto (its bar is 0 by default).
        assertTrue(decide(line("p", sharp = listOf(0.49)), loose) is MakerDecision.Post)
        // A typed bar of 2% lets the 2.1% through; an 8% bar stops what 3% let through.
        assertTrue(decide(line("o", small, sharp = listOf(0.48)), rules({ it.copy(makerObscureSharpMinEv = 0.02) }, { it.copy(anchorSharp = false) })) is MakerDecision.Post)
        assertTrue(why(line("o", small, sharp = listOf(0.50)), rules({ it.copy(makerObscureSharpMinEv = 0.08, makerObscureMargin = 0.06) }, { it.copy(anchorSharp = false) })).contains("under 8.0% edge"))
    }

    @Test
    fun `the sharp books and the blend must agree within the points Tj sets`() {
        // Blend 0.50, a sharp book at 0.53: 3 points apart, over the 2-point default.
        assertTrue(why(line("o", small, fair = 0.50, sharp = listOf(0.53))).contains("3.0 points apart on this small-market line (they must be within 2.0 points)"))
        // Two sharp books that disagree with each other count too.
        assertTrue(why(line("o", small, fair = 0.50, sharp = listOf(0.50, 0.535))).contains("apart"))
        // Within 2 points: a bid. A typed 5 points lets the wide one through.
        assertTrue(decide(line("o", small, fair = 0.50, sharp = listOf(0.515))) is MakerDecision.Post)
        assertTrue(decide(line("o", small, fair = 0.50, sharp = listOf(0.53)), rules({ it.copy(makerObscureAgreePoints = 0.05) })) is MakerDecision.Post)
        // A popular line is not held to it.
        assertTrue(decide(line("p", fair = 0.50, sharp = listOf(0.53))) is MakerDecision.Post)
    }

    @Test
    fun `the margin is never under the normal one - a wider normal margin wins over the small-market default`() {
        // Normal margin typed at 8%: a small-market bid is 8% under too, not 6%.
        val wide = rules({ it.copy(makerMargin = 0.08) })
        val bid = decide(line("o", small), wide) as MakerDecision.Post
        assertTrue(bid.price <= 0.50 / 1.08 + 1e-9)
        // A small-market margin typed under the normal one is the normal one.
        val low = decide(line("o", small), rules({ it.copy(makerMargin = 0.04, makerObscureMargin = 0.01) })) as MakerDecision.Post
        assertTrue(low.price <= 0.50 / 1.04 + 1e-9)
    }

    @Test
    fun `every other rule still binds a small-market bid - the price window, the longest odds, the start window and an already-held side`() {
        val r = rules()
        // A longshot is outside the 30-60% window, small market or not.
        assertTrue(why(line("o", small, fair = 0.25, sharp = listOf(0.25)), r).contains("outside the price window"))
        // The longest odds Tj allows (+100: nothing over even money).
        assertTrue(why(line("o", small, fair = 0.40, sharp = listOf(0.40)), rules({ it.copy(makerMaxOdds = 100) })).contains("longer odds than your +100 limit"))
        // Already held.
        assertTrue((MakerQuote.decide(line("o", small), r, now, held = setOf("o-over")) as MakerDecision.Skip).why.contains("Already bet or bid"))
    }

    // ---- order and room ---------------------------------------------------------------------------------------------------------

    private fun post(id: String, obscure: Boolean, price: Double = 0.48, leads: Boolean = true, cost: Long = 1_000) =
        MakerDecision.Post(line(id, if (obscure) small else "RECEIVING_YARDS", bestBid = if (leads) null else price + 0.01), price, cost, 0.04, obscure = obscure)

    private fun resting(id: String, obscure: Boolean, price: Double = 0.48, leads: Boolean = true, contracts: Long = 1_000) =
        RestingBid("o-$id", "m-$id", "$id-over", price, contracts, 0, now + 20 * 60_000L, evAtFair = 0.05, leads = leads, obscure = obscure)

    @Test
    fun `popular bids go up before any small-market bid whatever else ranks them`() {
        val r = rules()
        // The small-market bid leads its side and is cheaper; the popular one is behind another bid and dearer: popular still first.
        val order = listOf(post("o", obscure = true, price = 0.30, leads = true), post("p", obscure = false, price = 0.55, leads = false))
            .sortedWith(MakerPlan.priority(r)).map { it.line.outcomeId }
        assertEquals(listOf("p-over", "o-over"), order)
        // The same under a rules set that is not quick (the ordering is the plan's, not the focus').
        assertEquals(listOf("p-over", "o-over"), listOf(post("o", true), post("p", false)).sortedWith(MakerPlan.priority(rules(g = { it.copy(quick = false) }))).map { it.line.outcomeId })
    }

    @Test
    fun `with room for both, both go up - popular first - and nothing is cancelled`() {
        val r = rules(g = { it.copy(maxBids = 5, maxDollars = 100.0) })
        val plan = MakerPlan.plan(listOf(post("o", true), post("p", false)), emptyList(), r, now)
        assertEquals(listOf("p-over", "o-over"), plan.places.map { it.line.outcomeId })
        assertTrue(plan.cancels.isEmpty())
    }

    @Test
    fun `when only one bid fits the popular one takes it and the small-market one waits, with the reason`() {
        val r = rules(g = { it.copy(maxBids = 1, maxDollars = 100.0) })
        val plan = MakerPlan.plan(listOf(post("o", true), post("p", false)), emptyList(), r, now)
        assertEquals(listOf("p-over"), plan.places.map { it.line.outcomeId })
        assertEquals(1, plan.waiting.values.sum())
    }

    @Test
    fun `a small-market bid never takes money a waiting popular bid needs - it waits while any popular bid waits for the wallet`() {
        // $3 of room: the popular bid ($4.80) does not fit and waits; the small-market bid ($2.40) would fit but waits behind it, so the money stays for the popular bid.
        val r = rules(g = { it.copy(maxBids = 10, maxDollars = 100.0) })
        val plan = MakerPlan.plan(listOf(post("o", true, cost = 500), post("p", false)), emptyList(), r, now, budget = 3.0)
        assertTrue(plan.places.isEmpty())
        assertEquals(1, plan.waiting[MakerPlan.POPULAR_WAITING])
        assertEquals(1, plan.waiting[MakerPlan.BUDGET_REACHED])
        // With room for the popular bid, the small-market one goes up beside it.
        assertEquals(listOf("p-over", "o-over"), MakerPlan.plan(listOf(post("o", true, cost = 500), post("p", false)), emptyList(), r, now, budget = 10.0).places.map { it.line.outcomeId })
    }

    @Test
    fun `a small-market bid already up comes down to make room for a popular one, and not when there is room`() {
        val r = rules(g = { it.copy(maxBids = 1, maxDollars = 100.0) })
        val plan = MakerPlan.plan(listOf(post("o", true), post("p", false)), listOf(resting("o", obscure = true)), r, now)
        assertEquals(listOf("o-over"), plan.cancels.map { it.first.outcomeId })
        assertEquals(MakerPlan.MADE_ROOM, plan.cancels.single().second)
        // The popular bid goes up on the next pass, once the cancel has landed: not this one.
        assertTrue(plan.places.isEmpty())
        // Room for both: the small-market bid stays and the popular one goes up.
        val roomy = MakerPlan.plan(listOf(post("o", true), post("p", false)), listOf(resting("o", obscure = true)), rules(g = { it.copy(maxBids = 2, maxDollars = 100.0) }), now)
        assertTrue(roomy.cancels.isEmpty())
        assertEquals(listOf("p-over"), roomy.places.map { it.line.outcomeId })
        // A popular bid already up is never the one that comes down for another popular one.
        val twoPopular = MakerPlan.plan(listOf(post("p2", false), post("p", false)), listOf(resting("p", obscure = false)), r, now)
        assertTrue(twoPopular.cancels.isEmpty())
    }

    @Test
    fun `the dollars are made room for too - the least valuable small-market bids come down first, and no more than the popular bid needs`() {
        // Cap of $6 up: two small-market bids resting ($2.40 each: 500 contracts at 48 cents) and a $4.80 popular bid wanted: both small ones must go.
        val r = rules(g = { it.copy(maxBids = 10, maxDollars = 6.0) })
        val weak = resting("o1", obscure = true, leads = false, contracts = 500).copy(evAtFair = 0.04)
        val strong = resting("o2", obscure = true, leads = true, contracts = 500).copy(evAtFair = 0.09)
        // The small-market bids are still wanted (they stay up on their own merits): only the room comes out of them.
        val wanted = listOf(post("o1", true), post("o2", true))
        val plan = MakerPlan.plan(listOf(post("p", false)) + wanted, listOf(weak, strong), r, now)
        assertEquals(listOf("o1-over", "o2-over"), plan.cancels.map { it.first.outcomeId })
        assertTrue(plan.cancels.all { it.second == MakerPlan.MADE_ROOM })
        // A cheaper popular bid ($2.40) needs only one of them gone (the weaker, first).
        val oneNeeded = MakerPlan.plan(listOf(post("p", false, cost = 500)) + wanted, listOf(weak, strong), r, now)
        assertEquals(listOf("o1-over"), oneNeeded.cancels.map { it.first.outcomeId })
        assertEquals(listOf("o2-over"), oneNeeded.kept.map { it.outcomeId })
    }

    @Test
    fun `when the wallet cannot hold every bid a small-market bid is trimmed before a popular one`() {
        val r = rules()
        // Both rest, the wallet covers one ($4.80 each): the popular bid stays, the small-market one is trimmed first, though it leads and the popular one does not.
        val plan = MakerPlan.plan(
            wanted = listOf(post("o", true), post("p", false)), resting = listOf(resting("o", obscure = true, leads = true), resting("p", obscure = false, leads = false)),
            rules = r, now = now, budget = -0.5,
        )
        assertEquals(listOf("o-over"), plan.cancels.map { it.first.outcomeId })
        assertEquals(MakerPlan.TRIMMED, plan.cancels.single().second)
        assertNull(plan.cancels.firstOrNull { it.first.outcomeId == "p-over" })
    }

    // ---- the settings carry it -------------------------------------------------------------------------------------------------

    @Test
    fun `the settings reach the rules, clamped, and the defaults are the safeguards`() {
        val d = ScanSettings()
        assertTrue(d.makerObscureFill)
        assertEquals(listOf(0.06, 0.03, 0.02, 3, 0.5), listOf(d.makerObscureMargin, d.makerObscureSharpMinEv, d.makerObscureAgreePoints, d.makerObscureMinBooks, d.makerObscureStake))
        val r = MakerRules.of(ScanSettings(makerFocus = BidFocus.QUICK_LIKELY, makerObscureMargin = 9.0, makerObscureStake = 0.0, makerObscureMinBooks = 0, makerObscureAgreePoints = -1.0))
        assertEquals(0.5, r.obscureMargin, 1e-12)
        assertEquals(0.05, r.obscureStake, 1e-12)
        assertEquals(1, r.obscureMinBooks)
        assertEquals(0.0, r.obscureAgreePoints, 1e-12)
        // Saved settings with none of the new fields load with the defaults (no migration needed).
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(ScanSettings.serializer(), "{\"makerFocus\":\"QUICK_LIKELY\",\"maker\":true}")
        assertTrue(old.makerObscureFill)
        assertEquals(0.5, old.makerObscureStake, 1e-12)
    }
}
