package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Make orders (Tj, 2026-10-03: "figure out the optimal bets and math for make bets … build the system in the app"; RESEARCH.md §70): the bid each line
 * gets, how resting bids are kept, moved and cancelled, and the desk against a fake Novig (post-only with an expiry, fills to the Tracker, cancels, a
 * lost answer, the stops).
 */
class MakerTest {

    private var now = 1_800_000_000_000L
    private val start = now + 6 * 3_600_000L
    private val rules = MakerRules.of(ScanSettings())

    private fun market(id: String = "m1", starts: Long = start, status: String = "OPEN") = NovigMarket(
        marketId = id, eventId = "ev-$id", marketType = "RECEIVING_YARDS", status = status, description = "Player Receiving Yards", startsTs = starts,
        fee = MarketFee.GAME, outcomes = listOf(NovigOutcome("$id-over", "Over 50.5", "TBD"), NovigOutcome("$id-under", "Under 50.5", "TBD")),
    )

    private fun line(
        outcome: String = "m1-over",
        fair: Double? = 0.52,
        offer: Double? = 0.55,
        kind: BetKind = BetKind.PROP,
        books: Int = 3,
        old: Boolean = false,
        live: Boolean = false,
        m: NovigMarket = market(outcome.substringBefore('-')),
    ) = MakerLine(
        market = m, outcomeId = outcome, startsTs = m.startsTs, league = "NFL", eventName = "A @ B", marketLabel = "Player Receiving Yards",
        selection = "Player ${outcome.substringAfter('-').replaceFirstChar { it.uppercase() }} 50.5", kind = kind, fair = fair, fairAsOfMs = now - 30_000,
        fairOld = old, books = books, offer = offer, bestBid = null, live = live, source = BetTracker.SOURCE_VIGILANT,
    )

    // ---- the bid for one line -------------------------------------------------------------------------------------------

    @Test
    fun `the bid is the fair divided by one plus the margin, floored to Novig's grid, with the EV at the fair`() {
        val post = MakerQuote.decide(line(fair = 0.52), rules, now) as MakerDecision.Post
        assertEquals(0.500, post.price, 1e-9)
        assertEquals(0.04, post.evAtFair, 1e-9)
        // $5 at 0.50 = 1,000 one-cent contracts.
        assertEquals(1_000L, post.contracts)
        assertEquals(5.0, post.cost, 1e-9)
        // 0.30 / 1.04 = 0.2885 → 0.285 on the grid: the EV at the fair is a little over the margin, never under it.
        val dog = MakerQuote.decide(line(fair = 0.30, offer = 0.33), rules, now) as MakerDecision.Post
        assertEquals(0.285, dog.price, 1e-9)
        assertTrue(dog.evAtFair >= rules.margin)
    }

    @Test
    fun `no bid - and why - when it can't or shouldn't rest`() {
        fun why(l: MakerLine, held: Set<String> = emptySet(), r: MakerRules = rules) = (MakerQuote.decide(l, r, now, held) as MakerDecision.Skip).why
        assertTrue(why(line(live = true)).startsWith("The game has started"))
        assertTrue(why(line(m = market(starts = now + 10 * 60_000))).startsWith("Starts within 15 min"))
        assertTrue(why(line(m = market(status = "CLOSED"))).startsWith("Novig isn't taking orders"))
        assertTrue(why(line(kind = BetKind.MONEYLINE)).startsWith("Moneylines are off"))
        assertTrue(why(line(fair = null)).startsWith("No fair price"))
        assertTrue(why(line(old = true)).startsWith("The fair price is too old"))
        assertTrue(why(line(books = 1)).startsWith("Only 1 book"))
        assertTrue(why(line(), held = setOf("m1-over")).startsWith("Already bet"))
        // 0.80 / 1.04 = 0.769: over the 0.65 window (favorites almost never fill).
        assertTrue(why(line(fair = 0.80, offer = 0.85)).contains("outside the price window"))
        // Novig already sells it at the bid or cheaper: that's a bet to take now, not to bid on.
        assertTrue(why(line(fair = 0.52, offer = 0.50)).contains("take it instead"))
        assertTrue(why(line(), r = rules.copy(stake = 0.001)).startsWith("The stake is too small"))
    }

    @Test
    fun `one side per market when both sides is off - the cheaper side, the underdog's`() {
        val lines = listOf(line("m1-over", fair = 0.60, offer = 0.65), line("m1-under", fair = 0.40, offer = 0.45))
        val both = MakerQuote.decideAll(lines, rules, now, emptySet())
        assertEquals(2, both.count { it is MakerDecision.Post })
        val one = MakerQuote.decideAll(lines, rules.copy(bothSides = false), now, emptySet())
        assertEquals(listOf("m1-under"), one.filterIsInstance<MakerDecision.Post>().map { it.line.outcomeId })
        assertTrue((one.first { it.line.outcomeId == "m1-over" } as MakerDecision.Skip).why.startsWith("One side per market"))
    }

    // ---- resting bids against the bids wanted ---------------------------------------------------------------------------

    private fun post(outcome: String, price: Double, contracts: Long = 1_000) =
        MakerDecision.Post(line(outcome), price, contracts, 0.04)

    private fun resting(outcome: String, price: Double, expires: Long? = now + 20 * 60_000, filled: Long = 0) =
        RestingBid("o-$outcome", outcome.substringBefore('-'), outcome, price, 1_000 - filled, filled, expires)

    @Test
    fun `a resting bid moves down at once when the fair falls, up only by two steps, and is re-posted before it expires`() {
        val fell = MakerPlan.plan(listOf(post("m1-over", 0.495)), listOf(resting("m1-over", 0.500)), rules, now)
        assertEquals("The fair price fell: re-posted lower", fell.cancels.single().second)
        assertEquals(0.495, fell.places.single().price, 1e-9)
        // One step up: kept (moving would lose its place in the queue).
        val oneUp = MakerPlan.plan(listOf(post("m1-over", 0.505)), listOf(resting("m1-over", 0.500)), rules, now)
        assertTrue(oneUp.cancels.isEmpty() && oneUp.places.isEmpty())
        assertEquals(1, oneUp.kept.size)
        val twoUp = MakerPlan.plan(listOf(post("m1-over", 0.510)), listOf(resting("m1-over", 0.500)), rules, now)
        assertEquals("The fair price rose: re-posted higher", twoUp.cancels.single().second)
        val expiring = MakerPlan.plan(listOf(post("m1-over", 0.500)), listOf(resting("m1-over", 0.500, expires = now + 60_000)), rules, now)
        assertEquals("About to expire: re-posted", expiring.cancels.single().second)
        assertEquals(1, expiring.places.size)
    }

    @Test
    fun `a bid no longer wanted comes down with its line's reason, and a partly filled one isn't posted again`() {
        val gone = MakerPlan.plan(emptyList(), listOf(resting("m1-over", 0.500)), rules, now, skips = mapOf("m1-over" to "Starts within 15 min: no bids this close"))
        assertEquals("Starts within 15 min: no bids this close", gone.cancels.single().second)
        val part = MakerPlan.plan(listOf(post("m1-over", 0.490)), listOf(resting("m1-over", 0.500, filled = 400)), rules, now)
        assertEquals(1, part.cancels.size)
        assertTrue(part.places.isEmpty())
    }

    @Test
    fun `new bids go in cheapest first, within the most bids, the most dollars and the budget, and a stop takes every bid down`() {
        val wanted = listOf(post("a-over", 0.50), post("b-over", 0.30, 1_666), post("c-over", 0.40, 1_250))
        val two = MakerPlan.plan(wanted, emptyList(), rules.copy(maxBids = 2), now)
        assertEquals(listOf("b-over", "c-over"), two.places.map { it.line.outcomeId })
        // $5 a bid, at most $10 resting: two bids.
        assertEquals(2, MakerPlan.plan(wanted, emptyList(), rules.copy(maxDollars = 10.0), now).places.size)
        // The wallet holds $6: one bid.
        assertEquals(1, MakerPlan.plan(wanted, emptyList(), rules, now, budget = 6.0).places.size)
        val stop = MakerPlan.plan(wanted, listOf(resting("a-over", 0.50), resting("b-over", 0.30)), rules, now, stopAll = "Scanning is paused")
        assertEquals(listOf("Scanning is paused", "Scanning is paused"), stop.cancels.map { it.second })
        assertTrue(stop.places.isEmpty())
    }

    // ---- the desk against a fake Novig ----------------------------------------------------------------------------------

    private inner class FakeNovig : NovigTradingClient(
        NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }),
        Json { ignoreUnknownKeys = true },
    ) {
        val orders = LinkedHashMap<String, NovigOrder>()
        val fillsBy = HashMap<String, MutableList<NovigFill>>()
        val placed = ArrayList<List<Any?>>()
        val cancelled = ArrayList<String>()
        var loseNextAnswer = false
        var refuse: NovigApiException? = null
        private var n = 0

        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            refuse?.let { throw it }
            placed += listOf(outcomeId, price, qty, tif, ttlMs)
            val id = "o${++n}"
            orders[id] = NovigOrder(id, clientId, outcomeId.substringBefore('-'), outcomeId, price, qty, qty, tif, "OPEN", now, ttlMs?.let { now + it })
            if (loseNextAnswer) {
                loseNextAnswer = false
                throw java.io.IOException("timeout")
            }
            return id
        }

        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = orders.values.filter { it.status == status }

        override suspend fun order(orderId: String) = orders[orderId]

        override suspend fun fills(orderId: String?, limit: Int) = fillsBy[orderId].orEmpty().toList()

        override suspend fun cancelOrder(orderId: String): String? {
            val o = orders[orderId] ?: return null
            cancelled += orderId
            if (o.status != "OPEN") return o.status
            orders[orderId] = o.copy(status = "CANCELED")
            return "OPEN"
        }

        override suspend fun cancelOrders(marketId: String?, eventId: String?): Int {
            val open = orders.values.filter { it.status == "OPEN" }
            open.forEach { orders[it.orderId] = it.copy(status = "CANCELED") }
            return open.size
        }

        /** A taker fills [qty] of [orderId] at its price. */
        fun fill(orderId: String, qty: Long) {
            val o = orders.getValue(orderId)
            val left = o.remaining - qty
            orders[orderId] = o.copy(remaining = left, status = if (left <= 0) "FILLED" else "OPEN")
            fillsBy.getOrPut(orderId) { ArrayList() } += NovigFill("f-$orderId-${fillsBy[orderId]?.size ?: 0}", orderId, o.clientId, o.marketId, o.outcomeId, qty, qty * o.price * 0.01, false, 0.0, now)
        }
    }

    private fun desk(novig: FakeNovig, tracker: BetTracker) =
        MakerDesk(novig, tracker, MakerStore(File.createTempFile("maker", ".json").also { it.delete() }), clock = { now }, dayStart = { now - 3_600_000L })

    private fun tracker() = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })

    @Test
    fun `a cycle posts post-only bids with an expiry, and their fills become maker bets in the Tracker at the fair they were posted at`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val d = desk(novig, t)
        val r = d.cycle(listOf(line("m1-over", fair = 0.52), line("m1-under", fair = 0.48, offer = 0.50)), rules, stop = null, maxPerDay = 50.0, wallet = 100.0)
        assertEquals(2, r.placed)
        // Post-only, 30 minutes to live, at fair / 1.04 on the grid.
        assertEquals(listOf("m1-under", 0.460, 1_086L, "PO", 1_800_000L), novig.placed[0])
        assertEquals(listOf("m1-over", 0.500, 1_000L, "PO", 1_800_000L), novig.placed[1])
        val over = novig.orders.values.first { it.outcomeId == "m1-over" }.orderId
        // A taker fills 400 of the Over bid.
        novig.fill(over, 400)
        now += 60_000
        val r2 = d.cycle(listOf(line("m1-over", fair = 0.52), line("m1-under", fair = 0.48, offer = 0.50)), rules, null, 50.0, 100.0)
        val bet = r2.fills.single()
        assertTrue(bet.maker)
        assertEquals(400L, bet.contracts)
        assertEquals(0.52, bet.fairAtBet!!, 1e-9)
        assertEquals(0.04, bet.evPercentAtBet!!, 1e-9)
        assertEquals(0, r2.placed)
        // The rest fills: the same bet grows, the bid is done.
        novig.fill(over, 600)
        now += 60_000
        val r3 = d.cycle(listOf(line("m1-over", fair = 0.52), line("m1-under", fair = 0.48, offer = 0.50)), rules, null, 50.0, 100.0)
        assertEquals(bet.id, r3.fills.single().id)
        assertEquals(1_000L, t.all().single().contracts)
        assertEquals(MakerStatus.FILLED, d.bids().first { it.orderId == over }.status)
        // That side is bet now: not bid on again.
        assertEquals(2, novig.placed.size)
    }

    @Test
    fun `when the fair falls the bid is cancelled and re-posted lower, and a stop takes every bid down`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        d.cycle(listOf(line("m1-over", fair = 0.52)), rules, null, 50.0, 100.0)
        now += 60_000
        val r = d.cycle(listOf(line("m1-over", fair = 0.50)), rules, null, 50.0, 100.0)
        assertEquals(1, r.cancelled)
        assertEquals(listOf("o1"), novig.cancelled)
        assertEquals(0.480, novig.placed.last()[1] as Double, 1e-9)
        assertEquals("The fair price fell: re-posted lower", d.bids().first { it.orderId == "o1" }.why)
        now += 60_000
        val stop = d.cycle(listOf(line("m1-over", fair = 0.50)), rules, stop = "Scanning is paused", maxPerDay = 50.0, wallet = 100.0)
        assertEquals("Scanning is paused", stop.stopped)
        assertEquals(0, stop.placed)
        assertTrue(d.bids().none { it.active })
        assertTrue(novig.orders.values.none { it.status == "OPEN" })
    }

    @Test
    fun `a bid that ran out its time is marked expired, one Vigilant has no record of comes down, and the day's limit stops new bids`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val d = desk(novig, t)
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        // Novig cancels it at its expiry.
        now += 31 * 60_000
        novig.orders["o1"] = novig.orders.getValue("o1").copy(status = "CANCELED")
        // A post-only order nobody here placed (a lost list).
        novig.orders["stray"] = NovigOrder("stray", "c-x", "m9", "m9-over", 0.4, 100, 100, "PO", "OPEN", now)
        d.cycle(emptyList(), rules, null, 50.0, 100.0)
        assertEquals(MakerStatus.EXPIRED, d.bids().first { it.orderId == "o1" }.status)
        assertTrue("stray" in novig.cancelled)
        // $50 of API bets today: nothing new is posted.
        t.logApi(BetTarget(market(), "m2-over", "NFL", "A @ B", start, "Player Receiving Yards", "X Over", 0.5, now, BetTracker.SOURCE_VIGILANT), "taker-1",
            listOf(NovigFill("tf", "taker-1", null, "m2", "m2-over", 10_000, 50.0, true, 0.0, now)))
        val r = d.cycle(listOf(line("m1-over")), rules, null, maxPerDay = 50.0, wallet = 100.0)
        assertEquals(0, r.placed)
        assertTrue(r.stopped!!.startsWith("Today's limit"))
    }

    @Test
    fun `a lost answer is never sent again - the next cycle finds the order by its clientId - and an open bet's side gets no bid`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val d = desk(novig, t)
        novig.loseNextAnswer = true
        val r = d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        assertEquals(0, r.placed)
        assertNull(d.bids().single().orderId)
        now += 60_000
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        assertEquals(1, novig.placed.size)
        assertEquals("o1", d.bids().single().orderId)
        assertEquals(MakerStatus.RESTING, d.bids().single().status)
        // An open Tracker bet on the Under: no bid there.
        t.logApi(BetTarget(market(), "m1-under", "NFL", "A @ B", start, "Player Receiving Yards", "X Under", 0.5, now, BetTracker.SOURCE_VIGILANT), "t2",
            listOf(NovigFill("tf2", "t2", null, "m1", "m1-under", 100, 0.5, true, 0.0, now)))
        val r2 = d.cycle(listOf(line("m1-over"), line("m1-under", fair = 0.48, offer = 0.50)), rules, null, 50.0, 100.0)
        assertEquals("Already bet or bid on this side", (r2.decisions.first { it.line.outcomeId == "m1-under" } as MakerDecision.Skip).why)
        assertEquals(1, novig.placed.size)
    }

    @Test
    fun `Novig refusing for the wallet stops the cycle's bids and says why`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        novig.refuse = NovigApiException(422, "INSUFFICIENT_BALANCE", "Insufficient balance")
        val r = d.cycle(listOf(line("m1-over"), line("m2-over", m = market("m2"))), rules, null, 50.0, 100.0)
        assertEquals(0, r.placed)
        assertEquals(1, r.problems.size)
        assertEquals(MakerStatus.REFUSED, d.bids().single().status)
        assertFalse(d.bids().single().active)
    }
}
