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
    /** The defaults with a fixed $5 a bid (the sizing tests below cover Kelly). */
    private val rules = MakerRules.of(ScanSettings()).copy(stakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM, customStake = 5.0, maxStake = 10.0)

    /** A far-off game's fair is fresh for 10 minutes (Freshness), seen 30 s ago: a bid rests 9.5 min at most, not the 30-minute expiry. */
    private val life = 10 * 60_000L - 30_000L

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
        assertTrue(why(line(), r = rules.copy(customStake = 0.001)).startsWith("The stake is too small"))
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
        /** Orders Novig's reads don't show yet (its replica lags a just-placed order: 404). */
        val lagging = HashSet<String>()
        var loseNextAnswer = false
        var refuse: NovigApiException? = null
        /** A cancel Novig queues but hasn't applied: the order stays on the book until [applyCancels]. */
        var slowCancel = false
        private val queuedCancels = ArrayList<String>()
        /** Post-only orders refused after acceptance (their price would have taken). */
        var rejectPosts = false
        /** Orders whose record answers 404 (their fills can still be read). */
        val unreadable = HashSet<String>()
        private var n = 0

        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            refuse?.let { throw it }
            placed += listOf(outcomeId, price, qty, tif, ttlMs)
            val id = "o${++n}"
            orders[id] = NovigOrder(id, clientId, outcomeId.substringBefore('-'), outcomeId, price, qty, qty, tif, if (rejectPosts) "REJECTED" else "OPEN", now, ttlMs?.let { now + it })
            if (loseNextAnswer) {
                loseNextAnswer = false
                throw java.io.IOException("timeout")
            }
            return id
        }

        override suspend fun orders(status: String, limit: Int, outcomeId: String?) =
            orders.values.filter { it.status == status && it.orderId !in lagging && (outcomeId == null || it.outcomeId == outcomeId) }

        override suspend fun order(orderId: String) = orders[orderId]?.takeIf { orderId !in lagging && orderId !in unreadable }

        override suspend fun fills(orderId: String?, limit: Int) = fillsBy[orderId].orEmpty().toList()

        override suspend fun cancelOrder(orderId: String): String? {
            val o = orders[orderId] ?: return null
            cancelled += orderId
            if (o.status != "OPEN") return o.status
            if (slowCancel) queuedCancels += orderId else orders[orderId] = o.copy(status = "CANCELED")
            return "OPEN"
        }

        fun applyCancels() {
            queuedCancels.forEach { id -> orders[id]?.takeIf { it.status == "OPEN" }?.let { orders[id] = it.copy(status = "CANCELED") } }
            queuedCancels.clear()
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
        MakerDesk(novig, tracker, MakerStore(File.createTempFile("maker", ".json").also { it.delete() }), clock = { now }, dayStart = { now - 3_600_000L }, pause = {})

    private fun tracker() = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })

    @Test
    fun `a cycle posts post-only bids with an expiry, and their fills become maker bets in the Tracker at the fair they were posted at`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val d = desk(novig, t)
        val r = d.cycle(listOf(line("m1-over", fair = 0.52), line("m1-under", fair = 0.48, offer = 0.50)), rules, stop = null, maxPerDay = 50.0, wallet = 100.0)
        assertEquals(2, r.placed)
        // Post-only, living only as long as the fair it was priced from, at fair / 1.04 on the grid.
        assertEquals(listOf("m1-under", 0.460, 1_086L, "PO", life), novig.placed[0])
        assertEquals(listOf("m1-over", 0.500, 1_000L, "PO", life), novig.placed[1])
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
    fun `a bid Novig's reads don't show yet isn't called ended - it's asked about again`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        novig.lagging += "o1"
        now += 30_000
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        assertEquals(MakerStatus.RESTING, d.bids().single().status)
        assertEquals(1, novig.placed.size)
        novig.lagging.clear()
        now += 30_000
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        assertEquals(MakerStatus.RESTING, d.bids().single().status)
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

    // ---- only +EV, never outliving the fair (Tj, 2026-10-03: "It should not keep make orders long enough that they lose their positive EV") ----

    @Test
    fun `a bid never outlives its fair, the stop window or the expiry, and isn't posted on a fair of unknown age or about to go old`() {
        // A game 2 hours off: fairs are good for 5 minutes. Seen a minute ago: the bid may rest 4 minutes.
        val near = market(starts = now + 2 * 3_600_000L)
        val post = MakerQuote.decide(line(m = near).copy(fairAsOfMs = now - 60_000), rules, now) as MakerDecision.Post
        assertEquals(now + 4 * 60_000, post.restUntilMs)
        // 20 minutes off with no bids in the last 15: the stop window ends it at 5 minutes, before its fair would.
        val soon = market(starts = now + 20 * 60_000L)
        assertEquals(now + 5 * 60_000, (MakerQuote.decide(line(m = soon).copy(fairAsOfMs = now), rules, now) as MakerDecision.Post).restUntilMs)
        assertEquals("The fair price's age isn't known: no bid on it", (MakerQuote.decide(line().copy(fairAsOfMs = null), rules, now) as MakerDecision.Skip).why)
        // Seen 9.5 minutes ago on a far-off game: 30 seconds of freshness left, under the one-minute floor.
        assertTrue((MakerQuote.decide(line().copy(fairAsOfMs = now - 570_000), rules, now) as MakerDecision.Skip).why.startsWith("The fair price goes old"))
    }

    @Test
    fun `every bid posted is at least the margin under the fair, and a resting bid stays only while it still is at the new fair`() {
        val rnd = java.util.Random(70)
        repeat(4_000) {
            val fair = 0.11 + rnd.nextDouble() * 0.6
            val margin = listOf(0.03, 0.04, 0.06, 0.08)[rnd.nextInt(4)]
            val r = rules.copy(margin = margin)
            val d = MakerQuote.decide(line(fair = fair, offer = null), r, now)
            if (d is MakerDecision.Post) {
                assertTrue("fair $fair bid ${d.price}", fair / d.price - 1.0 >= margin - 1e-12)
                // The fair then moves anywhere: the plan keeps the bid only if it's still at least the margin under the new fair.
                val moved = (fair + (rnd.nextDouble() - 0.5) * 0.06).coerceIn(0.05, 0.9)
                val again = MakerQuote.decide(line(fair = moved, offer = null), r, now)
                val resting = RestingBid("o", "m1", "m1-over", d.price, d.contracts, 0, now + 300_000)
                val plan = MakerPlan.plan(listOfNotNull(again as? MakerDecision.Post), listOf(resting), r, now)
                if (plan.kept.isNotEmpty()) assertTrue("kept ${d.price} at fair $moved", moved / d.price - 1.0 >= margin - 1e-12)
            }
        }
    }

    @Test
    fun `game lines need a sharp book in the fair, books must agree the bid is +EV, and a sharp book saying no vetoes it`() {
        val lines = rules.copy(kinds = rules.kinds + BetKind.MONEYLINE)
        assertEquals("Game lines need a sharp book (Pinnacle, Circa …) in the fair", (MakerQuote.decide(line(kind = BetKind.MONEYLINE), lines, now) as MakerDecision.Skip).why)
        assertTrue(MakerQuote.decide(line(kind = BetKind.MONEYLINE).copy(sharpFairs = listOf(0.53)), lines, now) is MakerDecision.Post)
        // Bid 0.500: two books' own fairs over it, one under.
        val agree = line().copy(bookFairs = listOf(0.53, 0.505, 0.49))
        assertTrue(MakerQuote.decide(agree, rules, now) is MakerDecision.Post)
        assertEquals("Only 2 books price this bid +EV on their own (fewest: 3)", (MakerQuote.decide(agree, rules.copy(minBooks = 3), now) as MakerDecision.Skip).why)
        assertEquals("A sharp book's own price says this bid isn't +EV", (MakerQuote.decide(line().copy(sharpFairs = listOf(0.495)), rules, now) as MakerDecision.Skip).why)
        assertTrue(MakerQuote.decide(line().copy(sharpFairs = listOf(0.495)), rules.copy(sharpVeto = false), now) is MakerDecision.Post)
    }

    @Test
    fun `sizing - a quarter Kelly on the bankroll for the bid's own edge, held to the most a bid may cost`() {
        val kelly = rules.copy(stakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.QUARTER_KELLY, bankroll = 1_000.0, maxStake = 25.0)
        // fair 0.52 at 0.50: full Kelly (0.52 − 0.50) / (1 − 0.50) = 4% → ¼ = 1% of $1,000 = $10 = 2,000 contracts.
        assertEquals(10.0, MakerQuote.stake(0.52, 0.50, kelly)!!, 1e-9)
        assertEquals(2_000L, (MakerQuote.decide(line(fair = 0.52), kelly, now) as MakerDecision.Post).contracts)
        assertEquals(5.0, MakerQuote.stake(0.52, 0.50, kelly.copy(maxStake = 5.0))!!, 1e-9)
        assertNull(MakerQuote.stake(0.52, 0.50, kelly.copy(bankroll = 0.0)))
        assertEquals(1.0, MakerQuote.stake(0.52, 0.50, kelly.copy(stakeMode = com.tjshea.vigilant.data.scanner.AutoBetStake.ONE_DOLLAR))!!, 1e-9)
    }

    // ---- the desk: a cancel is only queued; fills never go unrecorded ----------------------------------------------------

    @Test
    fun `a cancel Novig hasn't applied keeps the side busy - a fill that lands meanwhile is recorded - and nothing new goes up there until it ends`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val d = desk(novig, t)
        d.cycle(listOf(line("m1-over", fair = 0.52)), rules, null, 50.0, 100.0)
        novig.slowCancel = true
        now += 60_000
        // The fair falls: the bid is cancelled, but Novig hasn't confirmed it.
        val r = d.cycle(listOf(line("m1-over", fair = 0.50)), rules, null, 50.0, 100.0)
        assertEquals(1, r.cancelled)
        assertEquals(0, r.placed)
        assertEquals(MakerStatus.CANCELING, d.bids().single().status)
        // A taker fills 300 of it before the cancel is applied.
        novig.fill("o1", 300)
        novig.applyCancels()
        now += 60_000
        val r2 = d.cycle(listOf(line("m1-over", fair = 0.50)), rules, null, 50.0, 100.0)
        assertEquals(300L, r2.fills.single().contracts)
        assertEquals(MakerStatus.CANCELED, d.bids().first { it.orderId == "o1" }.status)
        // That side is a bet now: no new bid on it.
        assertEquals(1, novig.placed.size)
        assertTrue(t.all().single().maker)
    }

    @Test
    fun `an order whose record can't be read still has its fills read, and a lost answer that filled is found in Novig's other lists`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val d = desk(novig, t)
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        novig.fill("o1", 1_000)
        novig.unreadable += "o1"
        now += 2 * 60_000
        val r = d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        assertEquals(1_000L, r.fills.single().contracts)
        assertEquals(MakerStatus.FILLED, d.bids().single().status)
        // A second desk: its first bid's answer is lost and the bid fills before the next look.
        val novig2 = FakeNovig()
        val t2 = tracker()
        val d2 = desk(novig2, t2)
        novig2.loseNextAnswer = true
        d2.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        novig2.fill("o1", 1_000)
        now += 2 * 60_000
        val r2 = d2.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        assertEquals(1_000L, r2.fills.single().contracts)
        assertEquals(MakerStatus.FILLED, d2.bids().single().status)
        assertEquals(1, novig2.placed.size)
    }

    @Test
    fun `a post-only bid Novig refused isn't sent again on that side for five minutes`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        novig.rejectPosts = true
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        now += 2 * 60_000
        val r = d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        assertEquals(MakerStatus.REFUSED, d.bids().single().status)
        assertTrue((r.decisions.single() as MakerDecision.Skip).why.startsWith("Novig refused a bid here"))
        assertEquals(1, novig.placed.size)
        novig.rejectPosts = false
        now += MakerDesk.REFUSED_COOLOFF_MS
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        assertEquals(2, novig.placed.size)
    }

    @Test
    fun `with auto-make off a bid Tj approved is only taken down when it stops being worth it - never moved, re-posted or joined by new ones`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        // Approved by hand: posted through post().
        val post = MakerQuote.decide(line("m1-over", fair = 0.52), rules, now) as MakerDecision.Post
        assertNull(d.post(post, rules))
        // The fair rises two steps and another line is worth a bid: nothing moves, nothing new goes up.
        now += 60_000
        val up = d.cycle(listOf(line("m1-over", fair = 0.535), line("m2-over", m = market("m2"))), rules, null, 50.0, 100.0, autoPost = false)
        assertEquals(0, up.placed)
        assertEquals(0, up.cancelled)
        // It nears its expiry: left to expire, not re-posted.
        now += 8 * 60_000
        val late = d.cycle(listOf(line("m1-over", fair = 0.52)), rules, null, 50.0, 100.0, autoPost = false)
        assertEquals(0, late.cancelled + late.placed)
        // The fair falls under it: it comes down, with nothing posted in its place.
        val down = d.cycle(listOf(line("m1-over", fair = 0.50).copy(fairAsOfMs = now - 10_000)), rules, null, 50.0, 100.0, autoPost = false)
        assertEquals(1, down.cancelled)
        assertEquals(0, down.placed)
        assertEquals("The fair price fell under the bid: taken down", d.bids().single().why)
        assertEquals(1, novig.placed.size)
    }

    @Test
    fun `a denied side gets no bid and a resting one there comes down, and the denial lasts until its game`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0)
        now += 60_000
        val r = d.cycle(listOf(line("m1-over")), rules, null, 50.0, 100.0, denied = setOf("m1-over"))
        assertEquals(1, r.cancelled)
        assertEquals(MakerDesk.DENIED, (r.decisions.single() as MakerDecision.Skip).why)
        val denials = MakerDenials(File.createTempFile("denied", ".json").also { it.delete() }, clock = { now })
        denials.deny("m1-over", start, "Player Over 50.5")
        assertEquals(setOf("m1-over"), denials.outcomes())
        now = start + 1
        assertTrue(denials.outcomes().isEmpty())
    }

    // ---- a scan still running (Tj, 2026-10-03: "I had auto make bids turned on, but it didn't actually make any bids by itself") ------------------

    @Test
    fun `on a running scan a bid whose line it hasn't judged yet stays up, one it judged comes down for its reason, and a finished scan judges them all`() {
        val two = listOf(resting("m1-over", 0.500), resting("m2-over", 0.500))
        // The scan has priced m1 only so far (m2's league is still waiting for its fair odds): m2's bid stays, its ttl still bounds it.
        val running = MakerPlan.plan(listOf(post("m1-over", 0.500)), two, rules, now, partial = true)
        assertTrue(running.cancels.isEmpty())
        assertEquals(setOf("m1-over", "m2-over"), running.kept.map { it.outcomeId }.toSet())
        // It judged m2 and wants no bid there: down, with that line's reason.
        val judged = MakerPlan.plan(listOf(post("m1-over", 0.500)), two, rules, now, skips = mapOf("m2-over" to "The fair price is too old to bid on"), partial = true)
        assertEquals(listOf("m2-over" to "The fair price is too old to bid on"), judged.cancels.map { it.first.outcomeId to it.second })
        // The finished scan has no line for m2 at all: down.
        val done = MakerPlan.plan(listOf(post("m1-over", 0.500)), two, rules, now)
        assertEquals(listOf("m2-over" to "No longer a bid to post"), done.cancels.map { it.first.outcomeId to it.second })
    }

    @Test
    fun `bids wanted that can't go up say why - the most bids, the most dollars, the wallet`() {
        val wanted = listOf(post("a-over", 0.50), post("b-over", 0.30, 1_666), post("c-over", 0.40, 1_250))
        assertEquals(mapOf(MakerPlan.MAX_BIDS_REACHED.format(1) to 2), MakerPlan.plan(wanted, emptyList(), rules.copy(maxBids = 1), now).waiting)
        assertEquals(mapOf(MakerPlan.MAX_DOLLARS_REACHED.format("$10.00") to 1), MakerPlan.plan(wanted, emptyList(), rules.copy(maxDollars = 10.0), now).waiting)
        assertEquals(mapOf(MakerPlan.BUDGET_REACHED to 2), MakerPlan.plan(wanted, emptyList(), rules, now, budget = 6.0).waiting)
        assertTrue(MakerPlan.plan(wanted, emptyList(), rules, now).waiting.isEmpty())
    }

    @Test
    fun `the desk on a running scan posts the lines it has and leaves the other bids up`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        d.cycle(listOf(line("m1-over"), line("m2-over", m = market("m2"))), rules, null, 50.0, 100.0)
        assertEquals(2, novig.orders.size)
        now += 60_000
        // A new scan has priced m3 so far: it's posted, and the two bids it hasn't judged stay up.
        val r = d.cycle(listOf(line("m3-over", m = market("m3"))), rules, null, 50.0, 100.0, partial = true)
        assertEquals(1, r.placed)
        assertEquals(0, r.cancelled)
        assertTrue(r.partial)
        assertEquals(3, d.bids().count { it.resting })
        // The same pass on a finished scan takes the two down.
        val f = d.cycle(listOf(line("m3-over", m = market("m3"))), rules, null, 50.0, 100.0)
        assertEquals(2, f.cancelled)
        assertEquals(1, d.bids().count { it.resting })
    }

    @Test
    fun `a running scan's lines are bid on with the last scan's Novig book (the bid comes from the fair), never one older than 20 minutes or a league still waiting`() {
        val start = now + 6 * 3_600_000L
        val event = com.tjshea.vigilant.data.novig.NovigEvent("e1", "FOOTBALL", "NFL", com.tjshea.vigilant.data.novig.NovigEvent.STATUS_PREGAME, "A @ B", start)
        fun opp(id: String, bookAt: Long?, league: String = "NFL") = com.tjshea.vigilant.data.scanner.Opportunity(
            league = com.tjshea.vigilant.data.scanner.Leagues.byNovigName(league)!!, event = event, market = market(id),
            outcome = market(id).outcomes.first(), marketLabel = "Player Receiving Yards", kind = com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP,
            selection = "Player Over 50.5", fair = null, fairProbability = 0.52, quote = null, ladder = emptyList(), depth = null, suggestedStake = null,
            novigWidth = null, bookFetchedAtMs = bookAt, fairUpdatedMs = now - 30_000, refEvent = null, lineKey = null, target = null, fairAsOfMs = now - 30_000,
        )
        val scanStart = now - 60_000
        val result = com.tjshea.vigilant.data.scanner.ScanResult(
            games = emptyList(),
            opportunities = listOf(
                opp("fresh", now - 5_000),
                // Read by the last scan, 9 minutes ago: still judged (only the offer and the best bid come from it).
                opp("lastscan", now - 9 * 60_000),
                opp("ancient", now - 21 * 60_000),
                opp("never", null),
                opp("waiting", now - 5_000, league = "MLB"),
            ),
            stats = com.tjshea.vigilant.data.scanner.ScanStats(0, 0, 0, 0, 0),
            computedAtMs = now, freshSinceMs = scanStart,
            waitingFor = setOf(com.tjshea.vigilant.data.scanner.ScanResult.waitKey("MLB", props = true)),
        )
        val settings = ScanSettings(leagues = setOf("NFL", "MLB"))
        val lines = MakerLines.from(result, settings, now)
        assertEquals(listOf("fresh-over", "lastscan-over"), lines.map { it.outcomeId })
        assertEquals(now - 9 * 60_000, lines[1].bookAtMs)
    }
}
