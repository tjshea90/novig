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
import com.tjshea.vigilant.data.tracker.GameExposure
import com.tjshea.vigilant.data.tracker.GameRef
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
    fun `no bid on a game further off than the trap guard's hours (RESEARCH 71) - posted once it's inside them, and none of it when the guard is off`() {
        val far = market(starts = now + 7 * 3_600_000L)
        val rules6 = rules.copy(earlyHours = 6)
        val skip = MakerQuote.decide(line(m = far), rules6, now) as MakerDecision.Skip
        assertEquals("Starts in more than 6 h: no bids this early (trap guard)", skip.why)
        // An hour later the same game is inside the window.
        assertTrue(MakerQuote.decide(line(m = far).copy(fairAsOfMs = now + 3_600_000L - 30_000), rules6, now + 3_600_000L) is MakerDecision.Post)
        assertTrue(MakerQuote.decide(line(m = far), rules6.copy(earlyHours = 0), now) is MakerDecision.Post)
        // The settings carry it: 6 h by default.
        assertEquals(6, MakerRules.of(ScanSettings()).earlyHours)
        assertEquals(0, MakerRules.of(ScanSettings(trapEarlyHours = 0)).earlyHours)
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

    /**
     * Full test 2026-10-03: a bid that moves (the fair fell, or it's about to expire) is cancelled and re-posted in the same pass, but the wallet's budget
     * still counted the old bid as up, so on a tight wallet the move waited a pass with that side bare. The replacement may use its own predecessor's
     * dollars, and no other side may.
     */
    @Test
    fun `a moved bid's replacement can use the dollars its own cancelled bid frees, and no other side can`() {
        // One $5.00 bid up on a $5.00 wallet: the budget beside what's up is $0. The fair falls: the bid moves down to 0.495 ($4.95).
        val moved = MakerPlan.plan(listOf(post("m1-over", 0.495), post("m2-over", 0.300, 1_000)), listOf(resting("m1-over", 0.500)), rules, now, budget = 0.0)
        assertEquals("The fair price fell: re-posted lower", moved.cancels.single().second)
        assertEquals(listOf("m1-over"), moved.places.map { it.line.outcomeId })
        // The other side's $3.00 bid can't borrow m1's freed $5.00.
        assertEquals(mapOf(MakerPlan.BUDGET_REACHED to 1), moved.waiting)
        // A replacement costing more than its predecessor frees still needs the rest from the budget.
        val bigger = MakerPlan.plan(listOf(post("m1-over", 0.495, 1_200)), listOf(resting("m1-over", 0.500)), rules, now, budget = 0.0)
        assertTrue(bigger.places.isEmpty())
        assertEquals(1, MakerPlan.plan(listOf(post("m1-over", 0.495, 1_200)), listOf(resting("m1-over", 0.500)), rules, now, budget = 1.0).places.size)
        // About to expire with a fresher fair: re-posted on its own dollars too.
        val expiring = MakerPlan.plan(
            listOf(post("m1-over", 0.500).copy(restUntilMs = now + 30 * 60_000)), listOf(resting("m1-over", 0.500, expires = now + 60_000)), rules, now, budget = 0.0,
        )
        assertEquals("About to expire: re-posted", expiring.cancels.single().second)
        assertEquals(1, expiring.places.size)
        // A partly filled bid isn't re-posted at all (that side is a bet now), credit or not.
        assertTrue(MakerPlan.plan(listOf(post("m1-over", 0.495)), listOf(resting("m1-over", 0.500, filled = 200)), rules, now, budget = 0.0).places.isEmpty())
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

        /** Reads of one order's record, and of fills (every one is a whole read of the `history` bucket). */
        var orderReads = 0
        var fillReads = 0
        /** Fills reads answer 429 while set. */
        var throttleFills = false

        override suspend fun order(orderId: String): NovigOrder? {
            orderReads++
            return orders[orderId]?.takeIf { orderId !in lagging && orderId !in unreadable }
        }

        override suspend fun fills(orderId: String?, limit: Int): List<NovigFill> {
            fillReads++
            return fillsBy[orderId].orEmpty().toList()
        }

        override suspend fun fillsStartingAfter(startsAfterMs: Long, limit: Int): List<NovigFill> {
            fillReads++
            if (throttleFills) throw NovigApiException(429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded")
            return fillsBy.values.flatten()
        }

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

    /** [outcomes]' lines, every one a market of the one game "ev-game". */
    private fun oneGame(vararg outcomes: String) = outcomes.map { line(it, fair = 0.52, m = market(it.substringBefore('-')).copy(eventId = "ev-game")) }

    @Test
    fun `a cycle holds one game to the per-game limit, and the bids still up from the last pass keep holding it`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        val capped = rules.copy(maxPerGame = 12.0)
        val first = d.cycle(oneGame("a-over", "b-over", "c-over"), capped, stop = null, maxPerDay = 50.0, wallet = 100.0)
        assertEquals("two $5 bids fit $12; the third would make $15", 2, first.placed)
        assertEquals(mapOf(MakerPlan.GAME_REACHED.format("$12.00") to 1), first.waiting)
        // The same lines again: the two bids up are kept, and their $10 still hold the game: the third is still not posted.
        val second = d.cycle(oneGame("a-over", "b-over", "c-over"), capped, stop = null, maxPerDay = 50.0, wallet = 100.0)
        assertEquals(0, second.placed)
        assertEquals(0, second.cancelled)
        assertEquals(mapOf(MakerPlan.GAME_REACHED.format("$12.00") to 1), second.waiting)
    }

    @Test
    fun `open bets on the game count against a cycle's bids`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val alt = BetTarget(
            market = market("alt").copy(eventId = "ev-game"), outcomeId = "alt-over", league = "NFL", eventName = "A @ B", startsTs = start, marketLabel = "Spread",
            selection = "A -3.5", fair = 0.52, fairAsOfMs = now, source = BetTracker.SOURCE_VIGILANT,
        )
        t.logApi(alt, "o-alt", listOf(NovigFill("f-alt", "o-alt", null, "alt", "alt-over", 2_000, 10.0, true, 0.0, now - 60_000)))!!
        val d = desk(novig, t)
        val r = d.cycle(oneGame("a-over"), rules.copy(maxPerGame = 12.0), stop = null, maxPerDay = 50.0, wallet = 100.0)
        assertEquals("$10 open + a $5 bid is over $12", 0, r.placed)
        assertEquals(mapOf(MakerPlan.GAME_REACHED.format("$12.00") to 1), r.waiting)
        assertEquals(1, d.cycle(oneGame("a-over"), rules.copy(maxPerGame = 15.0), stop = null, maxPerDay = 50.0, wallet = 100.0).placed)
    }

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

    /**
     * Tj, 2026-10-03 (v0.56.1 Diagnostics: "I pressed pause and even that took a while to register"): a pass posted every bid it wanted, one request
     * after another, under the lock the Pause's cancel-all waits for, and then that cancel-all took them down again. A pass told to stop stops at once.
     */
    @Test
    fun `a pass told to stop posts no further bids, and those already up stay for the cancel-all`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        val lines = listOf(line("a-over", fair = 0.40), line("b-over", fair = 0.45), line("c-over", fair = 0.50), line("d-over", fair = 0.52))
        // Paused after the second bid went up.
        val r = d.cycle(lines, rules, null, 50.0, 100.0, keepPosting = { novig.placed.size < 2 })
        assertEquals(2, r.placed)
        assertEquals(2, novig.placed.size)
        assertEquals(2, d.bids().count { it.active })
        // Nothing stops it by default: the same four lines, all posted.
        val all = FakeNovig()
        assertEquals(4, desk(all, tracker()).cycle(lines, rules, null, 50.0, 100.0).placed)
        // And the cancel-all that follows the pause finds the lock free and takes both down.
        assertEquals(2, d.cancelAll("Scanning is paused"))
        assertTrue(d.bids().none { it.active })
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

    /**
     * RESEARCH.md §72 (`novig_toxic_flow_study.py`): re-quoted game-line bids on a side whose Novig price fell 2¢+ over the hour kept far less at the close
     * than on steady lines; props and 1st-half lines didn't. So the trap guard's move rule also stands over game-line bids, read at Novig's price now.
     */
    @Test
    fun `a game-line bid on a side Novig just moved against gets none - props aren't checked, and the rule off or no trades read changes nothing`() {
        val ml = rules.copy(kinds = rules.kinds + BetKind.MONEYLINE, novigMove = true)
        val game = line(kind = BetKind.MONEYLINE).copy(sharpFairs = listOf(0.53))
        // Over the last 15 min our side traded at 0.58 three times: takers bought the other side for $126 (3 × 10,000 × 0.42¢), and the price to take
        // our side is now 0.55, 3¢ under that level.
        val moved = (1..3).map { com.tjshea.vigilant.data.scanner.TrapGuard.Trade("m1-over", 0.58, 10_000, now - it * 60_000L) }
        val trades = mapOf("m1" to moved)
        val wanted = MakerLines.moveWanted(listOf(game), ml, now)
        assertEquals(listOf(game.outcomeId), wanted.map { it.outcomeId })
        val judged = MakerLines.withMoves(listOf(game), wanted, trades, now).single()
        assertTrue(judged.novigMove!!.trap)
        val why = (MakerQuote.decide(judged, ml, now) as MakerDecision.Skip).why
        assertTrue(why, why.startsWith("Novig just moved: 3.0¢ under where it traded this hour, with \$126 bought on the other side in 15 min"))
        // The rule off: posted (and nothing is wanted for reading).
        assertTrue(MakerQuote.decide(judged, ml.copy(novigMove = false), now) is MakerDecision.Post)
        assertTrue(MakerLines.moveWanted(listOf(game), ml.copy(novigMove = false), now).isEmpty())
        // No trades read for that market: judged without the rule, posted.
        assertTrue(MakerQuote.decide(MakerLines.withMoves(listOf(game), wanted, emptyMap(), now).single(), ml, now) is MakerDecision.Post)
        // A quiet market (no money on the other side): posted.
        val quiet = (1..3).map { com.tjshea.vigilant.data.scanner.TrapGuard.Trade("m1-under", 0.42, 10, now - it * 60_000L) }
        assertTrue(MakerQuote.decide(MakerLines.withMoves(listOf(game), wanted, mapOf("m1" to quiet), now).single(), ml, now) is MakerDecision.Post)
        // What isn't read: a prop (the same flow didn't hurt prop bids), a game line with no sharp book, game lines switched off for bids, nothing offered.
        val prop = line().copy(sharpFairs = listOf(0.53))
        assertTrue(MakerLines.moveWanted(listOf(prop, line(kind = BetKind.MONEYLINE), game.copy(offer = null)), ml, now).isEmpty())
        assertTrue(MakerLines.moveWanted(listOf(game), ml.copy(kinds = rules.kinds), now).isEmpty())
        // A prop with a "trap" read anyway is still bid on.
        assertTrue(MakerQuote.decide(MakerLines.withMoves(listOf(prop), listOf(prop), trades, now).single(), ml, now) is MakerDecision.Post)
        // The setting is the auto-bet's trap switch.
        assertTrue(MakerRules.of(ScanSettings()).novigMove)
        assertFalse(MakerRules.of(ScanSettings(trapNovigMove = false)).novigMove)
    }

    /** RESEARCH.md §72: a filled bid keeps about the sharp book's own edge, so the auto-bet's veto bar applies to a bid's own price too. */
    @Test
    fun `the sharp veto's bar applies at the bid's price - every sharp book must give the bid the set edge, 1% by default`() {
        assertEquals(0.01, rules.sharpMinEv, 0.0)
        // Bid 0.500 (fair 0.52). A sharp fair of 0.504 gives it +0.8%: under 1%, skipped; 0.505 gives exactly 1%: posted (the bar is inclusive).
        assertEquals(
            "A sharp book's own price gives this bid under the sharp veto's 1.0% edge",
            (MakerQuote.decide(line().copy(sharpFairs = listOf(0.504)), rules, now) as MakerDecision.Skip).why,
        )
        assertTrue(MakerQuote.decide(line().copy(sharpFairs = listOf(0.505)), rules, now) is MakerDecision.Post)
        // Every sharp book in the fair must clear it, not just one.
        assertTrue(MakerQuote.decide(line().copy(sharpFairs = listOf(0.53, 0.504)), rules, now) is MakerDecision.Skip)
        // The old bar (any +EV), or the veto off, lets it through.
        assertTrue(MakerQuote.decide(line().copy(sharpFairs = listOf(0.504)), rules.copy(sharpMinEv = 0.0), now) is MakerDecision.Post)
        assertTrue(MakerQuote.decide(line().copy(sharpFairs = listOf(0.504)), rules.copy(sharpVeto = false), now) is MakerDecision.Post)
        // No sharp book in the fair (most props): nothing to veto.
        assertTrue(MakerQuote.decide(line(), rules.copy(sharpMinEv = 0.02), now) is MakerDecision.Post)
        // The setting is the auto-bet's, held to 0..10%.
        assertEquals(0.02, MakerRules.of(ScanSettings(sharpVetoMinEv = 0.02)).sharpMinEv, 0.0)
        assertEquals(MakerRules.MAX_SHARP_MIN_EV, MakerRules.of(ScanSettings(sharpVetoMinEv = 0.5)).sharpMinEv, 0.0)
        assertEquals(0.0, MakerRules.of(ScanSettings(sharpVetoMinEv = -0.01)).sharpMinEv, 0.0)
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

    // ---- one game is one event (Tj, 2026-10-04): the per-game limit on bids ----------------------------------------------------------------

    /** A $5 bid (1,000 contracts at 50¢) on [outcome] of [market] in the one game "ev-game"; [event] puts it in another. */
    private fun inGame(outcome: String, market: String = outcome.substringBefore('-'), event: String = "ev-game") =
        MakerDecision.Post(line(outcome, m = market(market).copy(eventId = event)), 0.5, 1_000, 0.04)

    private val theGame = GameRef("ev-game", "A @ B", start, "NFL")

    @Test
    fun `new bids on one game stop at the per-game limit, and bids on other games still go up`() {
        val wanted = listOf(inGame("a-over"), inGame("b-over"), inGame("c-over"), inGame("d-over", event = "ev-other"))
        val plan = MakerPlan.plan(wanted, emptyList(), rules.copy(maxPerGame = 12.0), now)
        assertEquals("two $5 bids fit $12 on the game; the third would make $15; the other game is its own", 3, plan.places.size)
        assertEquals(listOf("a-over", "b-over", "d-over"), plan.places.map { it.line.outcomeId }.sorted())
        assertEquals(mapOf(MakerPlan.GAME_REACHED.format("$12.00") to 1), plan.waiting)
    }

    @Test
    fun `open bets on the game and bids that are kept both count against its limit`() {
        val held = listOf(GameExposure.Item(theGame, "alt-line", "x", 8.0))
        assertTrue("$8 held + a $5 bid is over $12", MakerPlan.plan(listOf(inGame("a-over")), emptyList(), rules.copy(maxPerGame = 12.0), now, heldItems = held).places.isEmpty())
        assertEquals(1, MakerPlan.plan(listOf(inGame("a-over")), emptyList(), rules.copy(maxPerGame = 13.0), now, heldItems = held).places.size)
        val kept = resting("b-over", 0.50).copy(game = theGame)
        val plan = MakerPlan.plan(listOf(inGame("a-over"), inGame("b-over")), listOf(kept), rules.copy(maxPerGame = 9.0), now)
        assertTrue("the kept $5 bid + a new $5 one is $10 over $9", plan.places.isEmpty())
        assertEquals(listOf(kept), plan.kept)
    }

    @Test
    fun `both sides of one market count as the larger side, not twice`() {
        val both = listOf(inGame("m1-over", "m1"), inGame("m1-under", "m1"))
        assertEquals(2, MakerPlan.plan(both, emptyList(), rules.copy(maxPerGame = 5.0), now).places.size)
        val twoMarkets = listOf(inGame("m1-over", "m1"), inGame("m2-over", "m2"))
        assertEquals(1, MakerPlan.plan(twoMarkets, emptyList(), rules.copy(maxPerGame = 5.0), now).places.size)
    }

    @Test
    fun `the rules take the per-game limit from the settings, on by default`() {
        assertEquals(25.0, MakerRules.of(ScanSettings()).maxPerGame, 1e-9)
        assertEquals(0.0, MakerRules.of(ScanSettings(apiMaxPerGame = 0.0)).maxPerGame, 1e-9)
        assertEquals(0.0, MakerRules.of(ScanSettings(apiMaxPerGame = -5.0)).maxPerGame, 1e-9)
    }

    @Test
    fun `no limit when it is 0, and a bid replacing its own side is not counted twice`() {
        val wanted = listOf(inGame("a-over"), inGame("b-over"), inGame("c-over"))
        assertEquals(3, MakerPlan.plan(wanted, emptyList(), rules.copy(maxPerGame = 0.0), now).places.size)
        // The kept bid on a-over is cancelled to be re-posted lower: its dollars are not on the game twice.
        val old = resting("a-over", 0.50).copy(game = theGame)
        val moved = MakerPlan.plan(listOf(inGame("a-over").copy(price = 0.495)), listOf(old), rules.copy(maxPerGame = 5.0), now)
        assertEquals(1, moved.cancels.size)
        assertEquals(1, moved.places.size)
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

    // ---- why no bid filled (Tj, 2026-10-03, v0.53.0: "None of my auto bids were accepted") -----------------------------------------------------

    @Test
    fun `an expiring bid is re-posted only when a fresher fair lets the new one rest at least a minute longer - never from the same fair`() {
        val r = resting("m1-over", 0.500, expires = now + 90_000)
        // The same fair: the new bid would end when this one does, and only lose its place in the queue.
        val same = MakerPlan.plan(listOf(post("m1-over", 0.500).copy(restUntilMs = now + 90_000)), listOf(r), rules, now)
        assertTrue(same.cancels.isEmpty() && same.places.isEmpty())
        assertEquals(1, same.kept.size)
        // 30 s more: not worth it.
        assertTrue(MakerPlan.plan(listOf(post("m1-over", 0.500).copy(restUntilMs = now + 120_000)), listOf(r), rules, now).cancels.isEmpty())
        // A fresher fair, a minute more: re-posted.
        val fresher = MakerPlan.plan(listOf(post("m1-over", 0.500).copy(restUntilMs = now + 150_000)), listOf(r), rules, now)
        assertEquals("About to expire: re-posted", fresher.cancels.single().second)
        assertEquals(1, fresher.places.size)
    }

    @Test
    fun `a bid priced from an aging fair rests to its expiry instead of being re-posted every pass, and its re-post doesn't count our own bid as one to beat`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        // A game 2 hours off (fairs good for 5 minutes), its fair seen 2 minutes ago: the bid rests 3 minutes.
        val near = market(starts = now + 2 * 3_600_000L)
        val seen = now - 2 * 60_000
        d.cycle(listOf(line(m = near).copy(fairAsOfMs = seen)), rules, null, 50.0, 100.0)
        assertEquals(180_000L, novig.placed.single()[4])
        val posted = now
        // Passes 20 s apart on the same scan: within 2 minutes of its expiry from the third on, and it stays.
        repeat(4) {
            now += 20_000
            val r = d.cycle(listOf(line(m = near).copy(fairAsOfMs = seen)), rules, null, 50.0, 100.0)
            assertEquals(0, r.cancelled + r.placed)
        }
        // A new scan's fair: re-posted with its longer life. Its book (read after our bid went up) shows only our own 1,000 at 0.500.
        now += 20_000
        val fresh = line(m = near).copy(fairAsOfMs = now - 10_000, bestBid = 0.500, bookAtMs = now - 5_000, bidLevels = listOf(com.tjshea.vigilant.data.novig.BidLevel(500, 1_000)))
        val r = d.cycle(listOf(fresh), rules, null, 50.0, 100.0)
        assertEquals(1, r.cancelled)
        assertEquals(1, r.placed)
        assertEquals(290_000L, novig.placed.last()[4])
        // The re-post leads its side: nobody else's bid was there.
        assertNull(d.bids().last().bestBidAtPost)
        assertTrue(d.bids().first().postedAtMs == posted)
    }

    @Test
    fun `Vigilant's own bids aren't the bid to beat - only the book's other bids are`() {
        val bookAt = now - 60_000
        val levels = listOf(com.tjshea.vigilant.data.novig.BidLevel(500, 1_000), com.tjshea.vigilant.data.novig.BidLevel(480, 500))
        val l = line("m1-over").copy(bestBid = 0.500, bookAtMs = bookAt, bidLevels = levels)
        fun mine(price: Double, contracts: Long, posted: Long = bookAt - 30_000, ended: Long? = null, outcome: String = "m1-over") = MakerBid(
            clientId = "c-$price-$contracts-$posted-$ended", orderId = "o-$posted", marketId = "m1", eventId = "ev-m1", outcomeId = outcome, league = "NFL",
            eventName = "A @ B", startsTs = start, marketLabel = "x", selection = "x", price = price, contracts = contracts, fair = 0.52, evAtFair = 0.04,
            margin = 0.04, postedAtMs = posted, endedAtMs = ended, status = if (ended == null) MakerStatus.RESTING else MakerStatus.EXPIRED,
        )
        fun best(vararg bids: MakerBid) = MakerLines.withoutOwn(listOf(l), bids.toList()).single().bestBid
        // All 1,000 at 0.500 were ours: the best bid is someone else's 0.480.
        assertEquals(0.480, best(mine(0.500, 1_000))!!, 1e-9)
        // 600 of them ours: someone else is at 0.500 too.
        assertEquals(0.500, best(mine(0.500, 600))!!, 1e-9)
        // Ours were the only bids: nobody to beat.
        assertNull(MakerLines.withoutOwn(listOf(l.copy(bidLevels = levels.take(1))), listOf(mine(0.500, 1_000))).single().bestBid)
        // Posted within 2 s of the read (maybe not in it yet), ended before it, or on the other side: nothing taken off.
        assertEquals(0.500, best(mine(0.500, 1_000, posted = bookAt - 1_000))!!, 1e-9)
        assertEquals(0.500, best(mine(0.500, 1_000, ended = bookAt - 1))!!, 1e-9)
        assertEquals(0.500, best(mine(0.500, 1_000, outcome = "m1-under"))!!, 1e-9)
    }

    @Test
    fun `when not every bid can go up, the ones that would lead their side go first, then the cheapest`() {
        // Someone already bids 0.30 on b: ours at 0.30 would sit behind it. a leads at 0.40 (best 0.35); nobody bids on c.
        val behind = MakerDecision.Post(line("b-over").copy(bestBid = 0.30), 0.30, 1_666, 0.04)
        val leads = MakerDecision.Post(line("a-over").copy(bestBid = 0.35), 0.40, 1_250, 0.04)
        val alone = MakerDecision.Post(line("c-over"), 0.45, 1_111, 0.04)
        assertFalse(behind.leads)
        assertTrue(leads.leads && alone.leads)
        val two = MakerPlan.plan(listOf(behind, leads, alone), emptyList(), rules.copy(maxBids = 2), now)
        assertEquals(listOf("a-over", "c-over"), two.places.map { it.line.outcomeId })
        assertEquals(mapOf(MakerPlan.MAX_BIDS_REACHED.format(2) to 1), two.waiting)
    }

    @Test
    fun `bids already up count against the wallet - Novig doesn't hold them from the balance`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        val two = listOf(line("m1-over"), line("m2-over", m = market("m2")))
        // $5 a bid, $8 in the wallet: one bid.
        val r = d.cycle(two, rules, null, 50.0, wallet = 8.0)
        assertEquals(1, r.placed)
        assertEquals(mapOf(MakerPlan.BUDGET_REACHED to 1), r.waiting)
        // The balance still reads $8 with that bid up (as Novig's did, Tj's v0.53.0 file): no second one.
        now += 30_000
        val r2 = d.cycle(two, rules, null, 50.0, wallet = 8.0)
        assertEquals(0, r2.placed)
        assertEquals(1, novig.orders.values.count { it.status == "OPEN" })
    }

    @Test
    fun `bids that end are finished with one fills read for all of them, and a bid seen on the book isn't looked up one by one`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        val three = listOf(line("m1-over"), line("m2-over", m = market("m2")), line("m3-over", m = market("m3")))
        d.cycle(three, rules, null, 50.0, 100.0)
        now += 30_000
        d.cycle(three, rules, null, 50.0, 100.0)
        // All three run out their time; 200 of one filled first.
        novig.fill("o2", 200)
        listOf("o1", "o2", "o3").forEach { novig.orders[it] = novig.orders.getValue(it).copy(status = "CANCELED") }
        now += 10 * 60_000
        novig.orderReads = 0
        novig.fillReads = 0
        val r = d.cycle(emptyList(), rules, null, 50.0, 100.0)
        assertEquals(1, novig.fillReads)
        assertEquals(0, novig.orderReads)
        assertEquals(200L, r.fills.single().contracts)
        assertEquals(listOf(MakerStatus.EXPIRED, MakerStatus.EXPIRED, MakerStatus.EXPIRED), d.bids().map { it.status })
        // Cancels too: a stop takes three down with one look at the open orders and one fills read.
        d.cycle(three.map { it.copy(fairAsOfMs = now - 30_000) }, rules, null, 50.0, 100.0)
        now += 30_000
        novig.orderReads = 0
        novig.fillReads = 0
        val stop = d.cycle(three, rules, "Scanning is paused", 50.0, 100.0)
        assertEquals(2, stop.cancelled)
        assertEquals(1, novig.fillReads)
        assertEquals(0, novig.orderReads)
        assertTrue(d.bids().none { it.active })
    }

    @Test
    fun `a fills read Novig throttles finishes nothing - the bid stays on its way down, no bid replaces it, and its fill is recorded next pass`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val d = desk(novig, t)
        d.cycle(listOf(line("m1-over", fair = 0.52)), rules, null, 50.0, 100.0)
        novig.throttleFills = true
        now += 30_000
        novig.fill("o1", 300)
        // The fair falls: the bid comes down, but its fills can't be read.
        val r = d.cycle(listOf(line("m1-over", fair = 0.50)), rules, null, 50.0, 100.0)
        assertEquals(0, r.placed)
        assertTrue(r.problems.joinToString(), r.problems.any { it.startsWith("Novig's fills") })
        assertEquals(MakerStatus.CANCELING, d.bids().single().status)
        assertTrue(t.all().isEmpty())
        novig.throttleFills = false
        now += 30_000
        val r2 = d.cycle(listOf(line("m1-over", fair = 0.50)), rules, null, 50.0, 100.0)
        assertEquals(300L, r2.fills.single().contracts)
        assertEquals(MakerStatus.CANCELED, d.bids().single().status)
        // That side is a bet now: no new bid.
        assertEquals(0, r2.placed)
    }

    // ---- switching bids on (Tj, 2026-10-03: "As soon as I turn on make bidding or auto make bidding, the app will automatically toggle on everything it needs")

    @Test
    fun `a mode that bids turns on Vigilant's scanner, the background scan with Vigilant at least once a minute and scanning, says so, and Off touches nothing else`() {
        val cnoOnly = ScanSettings(
            scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO, autoScan = com.tjshea.vigilant.data.scanner.AutoScanMode.OFF, autoScanSeconds = 600,
            paused = true, maker = false, makerRecommend = false,
        )
        val rec = MakerSetup.set(cnoOnly, BidMode.RECOMMEND)
        assertEquals(BidMode.RECOMMEND, BidMode.of(rec.settings))
        assertEquals(com.tjshea.vigilant.data.scanner.ScannerMode.BOTH, rec.settings.scanner)
        assertEquals(com.tjshea.vigilant.data.scanner.AutoScanMode.BOTH, rec.settings.autoScan)
        assertEquals(60, rec.settings.autoScanSeconds)
        assertFalse(rec.settings.paused)
        assertTrue(rec.settings.autoScansVigilant)
        assertEquals(4, rec.turnedOn.size)
        assertFalse(rec.startsAutoBet)
        val auto = MakerSetup.set(cnoOnly, BidMode.AUTOMATIC)
        assertTrue(auto.settings.maker)
        assertTrue(auto.settings.makerNow)
        // Already set up (Vigilant only, background every 30 s): nothing else changes, nothing to say.
        val ready = ScanSettings(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT, autoScan = com.tjshea.vigilant.data.scanner.AutoScanMode.BOTH, autoScanSeconds = 30)
        assertEquals(ready.copy(maker = true), MakerSetup.set(ready, BidMode.AUTOMATIC).settings)
        assertTrue(MakerSetup.set(ready, BidMode.AUTOMATIC).turnedOn.isEmpty())
        // Off: bids off, the scanner and the background scan left as they are.
        val off = MakerSetup.set(auto.settings, BidMode.OFF)
        assertEquals(auto.settings.copy(maker = false, makerRecommend = false), off.settings)
        // Auto-bet on but idle for want of the background scan: switching bids on starts it too, and says so first.
        assertTrue(MakerSetup.set(cnoOnly.copy(autoBet = true), BidMode.RECOMMEND).startsAutoBet)
    }

    // ---- the wallet kept ahead of the bids (Tj, 2026-10-04) ------------------------------------------------------------------

    /**
     * Tj, 2026-10-04 (screenshot: "Vigilant wallet $8.98 · 7 bids up ($16.14)"): bids were checked against the wallet only when posted, and Novig holds
     * nothing for a resting bid (NOVIG_API.md §17), so a bet by hand, an auto-bet or a fill afterwards left more bids up than the wallet covers, and
     * nothing took them down. The plan now trims: the budget (wallet and day's limit, less every bid up) going under zero takes the least valuable bids down.
     */
    private fun budgetFor(wallet: Double, vararg bids: RestingBid) = wallet - bids.sumOf { it.restingDollars }

    @Test
    fun `bids up that the wallet no longer covers come down, and a trimmed side isn't posted again that pass`() {
        // $5.00, $4.00 and $3.00 up ($12.00) and a wallet that fell to $8.98: the $5.00 bid is the one that doesn't fit.
        val up = listOf(resting("a-over", 0.50), resting("b-over", 0.40), resting("c-over", 0.30))
        val wanted = listOf(post("a-over", 0.50), post("b-over", 0.40), post("c-over", 0.30))
        val plan = MakerPlan.plan(wanted, up, rules, now, budget = budgetFor(8.98, *up.toTypedArray()))
        assertEquals(listOf("a-over"), plan.cancels.map { it.first.outcomeId })
        assertEquals(MakerPlan.TRIMMED, plan.cancels.single().second)
        assertEquals(1, plan.trimmed)
        assertEquals(setOf("b-over", "c-over"), plan.kept.map { it.outcomeId }.toSet())
        // Its line is still wanted, but the wallet just took it down: not posted again, not even a smaller bid that would fit what the cancel frees.
        assertTrue(plan.places.isEmpty())
        val smaller = MakerPlan.plan(listOf(post("a-over", 0.50, 300), post("b-over", 0.40), post("c-over", 0.30)), up, rules, now, budget = budgetFor(8.98, *up.toTypedArray()))
        assertEquals(listOf("a-over"), smaller.cancels.map { it.first.outcomeId })
        assertTrue(smaller.places.isEmpty())
        // Covered to the cent: nothing comes down. One cent short: the bid goes.
        assertEquals(0, MakerPlan.plan(wanted, up, rules, now, budget = budgetFor(12.0, *up.toTypedArray())).trimmed)
        assertEquals(1, MakerPlan.plan(wanted, up, rules, now, budget = budgetFor(11.99, *up.toTypedArray())).trimmed)
        // No wallet reading (the default budget): nothing is judged.
        assertEquals(0, MakerPlan.plan(wanted, up, rules, now).trimmed)
    }

    @Test
    fun `the bids trimmed are the least valuable - those Tj approved by hand last, then ones behind another bid, the dearest and the least EV`() {
        // Room for two of three $4.00 bids: X leads its side, Y sits behind another bid, Z is Tj's own approval and sits behind one too.
        val behind = 0.40
        val x = MakerDecision.Post(line("x-over").copy(bestBid = 0.35), 0.40, 1_000, 0.04)
        val y = MakerDecision.Post(line("y-over").copy(bestBid = behind), 0.40, 1_000, 0.04)
        val z = MakerDecision.Post(line("z-over").copy(bestBid = behind), 0.40, 1_000, 0.04)
        val up = listOf(resting("x-over", 0.40), resting("y-over", 0.40), resting("z-over", 0.40).copy(auto = false))
        val plan = MakerPlan.plan(listOf(x, y, z), up, rules, now, budget = budgetFor(8.0, *up.toTypedArray()))
        assertEquals(listOf("y-over"), plan.cancels.map { it.first.outcomeId })
        // A leader outranks a cheaper bid behind another: $4.00 leading and $3.00 behind won't both fit $4.50, and the leader stays.
        val dearLeader = MakerDecision.Post(line("l-over").copy(bestBid = 0.35), 0.40, 1_000, 0.04)
        val cheapFollower = MakerDecision.Post(line("f-over").copy(bestBid = 0.30), 0.30, 1_000, 0.04)
        val lf = listOf(resting("f-over", 0.30), resting("l-over", 0.40))
        val ranked = MakerPlan.plan(listOf(dearLeader, cheapFollower), lf, rules, now, budget = budgetFor(4.5, *lf.toTypedArray()))
        assertEquals(listOf("f-over"), ranked.cancels.map { it.first.outcomeId })
        // Leaders over followers, the cheaper over the dearer, then the more EV: two leaders, $4.00 + $5.00 won't both fit a $5.50 wallet.
        val cheap = listOf(resting("a-over", 0.50), resting("b-over", 0.30))
        assertEquals(listOf("a-over"), MakerPlan.plan(listOf(post("a-over", 0.50), post("b-over", 0.30)), cheap, rules, now, budget = budgetFor(5.5, *cheap.toTypedArray())).cancels.map { it.first.outcomeId })
        val same = listOf(resting("c-over", 0.40).copy(evAtFair = 0.04), resting("d-over", 0.40).copy(evAtFair = 0.06))
        val tie = MakerPlan.plan(emptyList(), same, rules, now, budget = budgetFor(4.0, *same.toTypedArray()), partial = true)
        assertEquals(listOf("c-over"), tie.cancels.map { it.first.outcomeId })
        // What fits is kept even when it ranks lower: the $5.00 leader doesn't fit a $4.00 wallet and comes down; the $3.00 bid behind another one does, and stays.
        val fit = listOf(resting("e-over", 0.50), resting("f-over", 0.30))
        val behindF = MakerDecision.Post(line("f-over").copy(bestBid = 0.30), 0.30, 1_000, 0.04)
        val kept = MakerPlan.plan(listOf(post("e-over", 0.50), behindF), fit, rules, now, budget = budgetFor(4.0, *fit.toTypedArray()))
        assertEquals(listOf("e-over"), kept.cancels.map { it.first.outcomeId })
        assertEquals(listOf("f-over"), kept.kept.map { it.outcomeId })
    }

    @Test
    fun `with auto-make off, or a scan still running, a bid the wallet can't cover still comes down`() {
        val up = listOf(resting("a-over", 0.50), resting("b-over", 0.40))
        val wanted = listOf(post("a-over", 0.50), post("b-over", 0.40))
        val hand = MakerPlan.plan(wanted, up, rules, now, budget = budgetFor(6.0, *up.toTypedArray()), repost = false)
        assertEquals(listOf("a-over"), hand.cancels.map { it.first.outcomeId })
        assertTrue(hand.places.isEmpty())
        // A running scan hasn't judged these lines: they stay up, unless the wallet can't hold them.
        val running = MakerPlan.plan(emptyList(), up, rules, now, budget = budgetFor(6.0, *up.toTypedArray()), partial = true)
        assertEquals(listOf("a-over"), running.cancels.map { it.first.outcomeId })
        assertEquals(0, MakerPlan.plan(emptyList(), up, rules, now, budget = budgetFor(9.0, *up.toTypedArray()), partial = true).cancels.size)
    }

    @Test
    fun `a cycle takes down the bids a fallen wallet can't cover, and says why on the bid`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        // Two bids up on a $100 wallet: m1's $5.00 and m2's cheaper one (fair 0.45 → bid 0.43).
        val lines = listOf(line("m1-over", fair = 0.52), line("m2-over", fair = 0.45, m = market("m2")))
        assertEquals(2, d.cycle(lines, rules, null, 50.0, wallet = 100.0).placed)
        // A bet by hand takes the wallet to $7.00 (Novig held nothing for the bids): $9.99 is up.
        now += 30_000
        val r = d.cycle(lines, rules, null, 50.0, wallet = 7.0)
        assertEquals(1, r.trimmed)
        assertEquals(1, r.cancelled)
        assertEquals(0, r.placed)
        val m1 = d.bids().first { it.outcomeId == "m1-over" }
        assertEquals(MakerStatus.CANCELED, m1.status)
        assertEquals(MakerPlan.TRIMMED, m1.why)
        assertEquals(MakerStatus.RESTING, d.bids().first { it.outcomeId == "m2-over" }.status)
        assertEquals(1, novig.orders.values.count { it.status == "OPEN" })
        // Nothing more comes down while the wallet covers what's left, and nothing is posted into the room that isn't there.
        now += 30_000
        val again = d.cycle(lines, rules, null, 50.0, wallet = 7.0)
        assertEquals(0, again.trimmed + again.cancelled + again.placed)
        assertEquals(2, novig.placed.size)
    }

    @Test
    fun `a bid that moves on a wallet that can't cover its replacement waits - the cancel's dollars don't make up for an empty wallet`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        d.cycle(listOf(line("m1-over", fair = 0.52)), rules, null, 50.0, wallet = 100.0)
        // The wallet fell to $3.00 and the fair fell: the old $5.00 bid is cancelled, and a $4.95 replacement would be $1.95 over the wallet.
        now += 30_000
        val r = d.cycle(listOf(line("m1-over", fair = 0.50)), rules, null, 50.0, wallet = 3.0)
        assertEquals(1, r.cancelled)
        assertEquals(0, r.placed)
        assertEquals(1, novig.placed.size)
        assertEquals(mapOf(MakerPlan.BUDGET_REACHED to 1), r.waiting)
    }

    @Test
    fun `the day's limit trims bids too - resting bids may not push the day's API bets over it`() = runBlocking {
        val novig = FakeNovig()
        val t = tracker()
        val d = desk(novig, t)
        val lines = listOf(line("m1-over", fair = 0.52), line("m2-over", fair = 0.45, m = market("m2")))
        d.cycle(lines, rules, null, 50.0, wallet = 100.0)
        // $5.00 of API bets today against a $12.00 limit leaves $7.00: the $9.99 up is over it.
        t.logApi(BetTarget(market("m9"), "m9-over", "NFL", "A @ B", start, "Player Receiving Yards", "X Over", 0.5, now, BetTracker.SOURCE_VIGILANT), "taker-1",
            listOf(NovigFill("tf", "taker-1", null, "m9", "m9-over", 1_000, 5.0, true, 0.0, now)))
        now += 30_000
        val r = d.cycle(lines, rules, null, maxPerDay = 12.0, wallet = 100.0)
        assertEquals(1, r.trimmed)
        assertEquals(MakerStatus.CANCELED, d.bids().first { it.outcomeId == "m1-over" }.status)
    }

    @Test
    fun `fit between passes takes bids down with no lines to judge by, settles first so a bid that filled isn't counted twice, and counts a cancel Novig hasn't applied`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        val lines = listOf(line("m1-over", fair = 0.52), line("m2-over", fair = 0.45, m = market("m2")))
        d.cycle(lines, rules, null, 50.0, wallet = 100.0)
        // The wallet's balance already shows the m1 bid's $5.00 as spent (it filled), and $5.00 is what's left: m2's $4.99 fits.
        novig.fill(novig.orders.values.first { it.outcomeId == "m1-over" }.orderId, 1_000)
        now += 30_000
        val settled = d.fit(rules, 50.0, wallet = 5.0)
        assertEquals(0, settled.trimmed)
        assertEquals(1, settled.fills.size)
        assertTrue(novig.cancelled.isEmpty())
        // The wallet falls under m2's bid: it comes down, with nothing posted and no line needed.
        now += 30_000
        val r = d.fit(rules, 50.0, wallet = 3.0)
        assertEquals(1, r.trimmed)
        assertEquals(0, r.placed)
        assertEquals(MakerPlan.TRIMMED, d.bids().first { it.outcomeId == "m2-over" }.why)
        assertEquals(2, novig.placed.size)
        // Nothing up, nothing to do: no cancel.
        val none = d.fit(rules, 50.0, wallet = 0.0)
        assertEquals(0, none.trimmed)
        assertEquals(1, novig.cancelled.size)
    }

    @Test
    fun `a cancel Novig hasn't applied still counts as up - the next fit takes down what the wallet can't hold beside it`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        val lines = listOf(line("m1-over", fair = 0.52), line("m2-over", fair = 0.45, m = market("m2")))
        d.cycle(lines, rules, null, 50.0, wallet = 100.0)
        novig.slowCancel = true
        now += 30_000
        // m1 goes first (the dearer one); Novig hasn't applied its cancel.
        assertEquals(1, d.fit(rules, 50.0, wallet = 7.0).trimmed)
        assertEquals(MakerStatus.CANCELING, d.bids().first { it.outcomeId == "m1-over" }.status)
        // Still on the book: it could still fill, so m2 can't rest beside it on $7.00.
        now += 30_000
        assertEquals(1, d.fit(rules, 50.0, wallet = 7.0).trimmed)
        // Applied: both are off the book now, so nothing is up and nothing more comes down.
        novig.applyCancels()
        now += 30_000
        assertEquals(0, d.fit(rules, 50.0, wallet = 7.0).trimmed)
        assertTrue(d.bids().none { it.active })
    }

    @Test
    fun `a bid Tj approves by hand is held to the wallet beside the bids already up, and the day's limit`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        val first = MakerQuote.decide(line("m1-over", fair = 0.52), rules, now) as MakerDecision.Post
        val second = MakerQuote.decide(line("m2-over", fair = 0.52, m = market("m2")), rules, now) as MakerDecision.Post
        // $9.00 in the wallet: each $5.00 bid is under it alone, both are not.
        assertNull(d.post(first, rules, wallet = 9.0, maxPerDay = 50.0))
        val refused = d.post(second, rules, wallet = 9.0, maxPerDay = 50.0)!!
        assertTrue(refused, refused.contains("wallet"))
        assertEquals(1, novig.placed.size)
        // A wallet reading that wasn't made, or room for both, posts.
        assertNull(d.post(second, rules, wallet = 10.0, maxPerDay = 50.0))
        // The day's limit counts what's up too: $5.00 + $5.00 up against $12.00 leaves nothing for a third.
        val third = MakerQuote.decide(line("m3-over", fair = 0.52, m = market("m3")), rules, now) as MakerDecision.Post
        assertTrue(d.post(third, rules, wallet = 100.0, maxPerDay = 12.0)!!.contains("limit"))
        assertNull(d.post(third, rules))
    }

    @Test
    fun `the bids Tj approved by hand are the last to come down when the wallet can't hold them all`() = runBlocking {
        val novig = FakeNovig()
        val d = desk(novig, tracker())
        // m1 is auto-make's, the cheaper bid (fair 0.40 → bid 0.38); m2 is Tj's own, the dearer one (bid 0.50).
        val auto = listOf(line("m1-over", fair = 0.40))
        d.cycle(auto, rules, null, 50.0, wallet = 100.0)
        assertNull(d.post(MakerQuote.decide(line("m2-over", fair = 0.52, m = market("m2")), rules, now) as MakerDecision.Post, rules))
        now += 30_000
        val both = auto + line("m2-over", fair = 0.52, m = market("m2"))
        val r = d.cycle(both, rules, null, 50.0, wallet = 7.0)
        assertEquals(1, r.trimmed)
        assertEquals(MakerStatus.CANCELED, d.bids().first { it.outcomeId == "m1-over" }.status)
        assertEquals(MakerStatus.RESTING, d.bids().first { it.outcomeId == "m2-over" }.status)
    }

    // ---- Tj, 2026-10-04: "bids … more popular than obscure players props", "Maybe a sharp book should be required", "lowering the EV to 3.5 or 3.25%" (RESEARCH.md §81.4) ----

    @Test
    fun `popular lines go up before obscure ones when not every bid fits, but never ahead of the ones that lead their side`() {
        val popularDear = MakerDecision.Post(line("a-over", books = 8), 0.45, 1_111, 0.04)
        val obscureCheap = MakerDecision.Post(line("b-over", books = 3), 0.30, 1_666, 0.04)
        val popularMid = MakerDecision.Post(line("c-over", books = 6), 0.40, 1_250, 0.04)
        val wanted = listOf(obscureCheap, popularDear, popularMid)
        // Two bids fit: the two popular ones, the cheaper first among them; the obscure bid, though the cheapest of all, waits.
        val two = MakerPlan.plan(wanted, emptyList(), rules.copy(maxBids = 2), now)
        assertEquals(listOf("c-over", "a-over"), two.places.map { it.line.outcomeId })
        // Off: the old order, the cheapest first.
        val old = MakerPlan.plan(wanted, emptyList(), rules.copy(maxBids = 2, popularFirst = false), now)
        assertEquals(listOf("b-over", "c-over"), old.places.map { it.line.outcomeId })
        // A bid that leads its side is still first: this popular bid would sit behind someone already bidding at its price.
        val popularBehind = MakerDecision.Post(line("d-over", books = 9).copy(bestBid = 0.35), 0.35, 1_428, 0.04)
        assertFalse(popularBehind.leads)
        val lead = MakerPlan.plan(listOf(popularBehind, obscureCheap), emptyList(), rules.copy(maxBids = 1), now)
        assertEquals(listOf("b-over"), lead.places.map { it.line.outcomeId })
        // Five books is not popular, six is.
        val five = MakerDecision.Post(line("e-over", books = 5), 0.20, 2_500, 0.04)
        val six = MakerDecision.Post(line("f-over", books = 6), 0.44, 1_136, 0.04)
        assertEquals(listOf("f-over"), MakerPlan.plan(listOf(five, six), emptyList(), rules.copy(maxBids = 1), now).places.map { it.line.outcomeId })
    }

    @Test
    fun `a sharp book can be required to agree - none pricing the line is a no, one that says no is still a no, game lines already needed one`() {
        fun why(l: MakerLine, r: MakerRules) = (MakerQuote.decide(l, r, now) as MakerDecision.Skip).why
        val noSharp = line(fair = 0.52)
        // Off (the default): a prop no sharp book prices gets its bid.
        assertTrue(MakerQuote.decide(noSharp, rules, now) is MakerDecision.Post)
        val required = rules.copy(requireSharp = true)
        assertTrue(why(noSharp, required).contains("a sharp book must agree"))
        // With one that agrees (its own fair 0.55 > the 0.50 bid), the bid goes up.
        assertTrue(MakerQuote.decide(noSharp.copy(sharpFairs = listOf(0.55)), required, now) is MakerDecision.Post)
        // The veto is the veto it was: a sharp book at 0.49 says the 0.50 bid isn't +EV.
        assertTrue(why(noSharp.copy(sharpFairs = listOf(0.49)), required).contains("A sharp book's own price says this bid isn't +EV"))
        // Game lines asked for one before this setting existed, and say so in their own words.
        assertTrue(why(line(kind = BetKind.MONEYLINE), rules.copy(kinds = rules.kinds + BetKind.MONEYLINE)).startsWith("Game lines need a sharp book"))
        // The settings carry both new switches: popular first on, a sharp book required off.
        val d = MakerRules.of(ScanSettings())
        assertTrue(d.popularFirst)
        assertFalse(d.requireSharp)
        assertEquals(6, d.popularBooks)
        val set = MakerRules.of(ScanSettings(makerPopularFirst = false, makerRequireSharp = true))
        assertFalse(set.popularFirst)
        assertTrue(set.requireSharp)
    }

    @Test
    fun `3_25 and 3_5 percent are choices, the default stays 4, and on Novig's half-cent grid they often land on the same price`() {
        assertTrue(ScanSettings.MAKER_MARGIN_CHOICES.containsAll(listOf(0.03, 0.0325, 0.035, 0.04)))
        assertEquals(0.04, ScanSettings().makerMargin, 0.0)
        fun price(fair: Double, margin: Double) = (MakerQuote.decide(line(fair = fair, offer = fair + 0.05), rules.copy(margin = margin), now) as MakerDecision.Post).price
        // Fair 0.52: 4%, 3.5% and 3.25% all post at 0.500 (the grid's step is half a cent, about 1% of EV at this price).
        assertEquals(listOf(0.500, 0.500, 0.500), listOf(0.04, 0.035, 0.0325).map { price(0.52, it) })
        // Fair 0.45: 4% and 3.5% both at 0.430 (4.65% EV at the fair), only 3.25% moves up a step, to 0.435.
        assertEquals(listOf(0.430, 0.430, 0.435), listOf(0.04, 0.035, 0.0325).map { price(0.45, it) })
    }
}
