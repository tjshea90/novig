package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Betting through Novig's API (Tj, 2026-09-29): the planner's arithmetic and refusals, and the placer against a mock Novig speaking the documented
 * routes (NOVIG_API.md §14-15): what goes on the wire, what the Tracker records from the fills, and what happens on a refusal, a partial fill,
 * a moved price and a lost answer.
 */
class ApiBettingTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var now = 1_800_000_000_000L
    private val startsTs = now + 6 * 3_600_000L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun signer() = NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid-1", pem), server.url("").toString().trimEnd('/'))
    private fun tradingClient() = NovigTradingClient(signer(), json)
    private fun tracker() = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })

    // ---- a market: outcomes A (the bet) and B; the ladder for A is 1 - B's bids ---------------------------------

    private val market = NovigMarket(
        marketId = "mkt", eventId = "ev", marketType = "MONEY", status = "OPEN", description = "A vs B", startsTs = startsTs,
        fee = MarketFee.GAME, outcomes = listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")),
    )

    /** Bids on B: 0.540 (100 contracts) and 0.535 (300), i.e. A costs 0.460 for 100 and 0.465 for 300. */
    private fun book(fetchedAt: Long = now, bidsB: List<BidLevel> = listOf(BidLevel(540, 100), BidLevel(535, 300))) =
        NovigBook("mkt", 1, mapOf("B" to bidsB, "A" to listOf(BidLevel(450, 50))), fetchedAt)

    private fun target(fair: Double = 0.50, fairAsOf: Long? = now - 30_000, starts: Long = startsTs, placedKey: String? = "cno:row1") = BetTarget(
        market = market.copy(startsTs = starts), outcomeId = "A", league = "NFL", eventName = "Team B @ Team A", startsTs = starts,
        marketLabel = "Moneyline", selection = "Team A", fair = fair, fairAsOfMs = fairAsOf, source = BetTracker.SOURCE_CNO, placedKey = placedKey,
    )

    private val limits = BetLimits(maxStake = 20.0, maxPerDay = 50.0, minEv = 0.0)

    // ---- the planner --------------------------------------------------------------------------------------------

    private fun ready(r: PlanResult) = (r as PlanResult.Ready).plan
    private fun refused(r: PlanResult) = (r as PlanResult.Refused).reason

    @Test
    fun `the plan buys the cheapest offers first and reaches only as deep as the stake needs`() {
        // $10 at 0.46 = 2173 contracts wanted, 100 offered there; then 0.465 for the rest of the dollars.
        val p = ready(ApiBetPlanner.plan(target(), book(), 10.0, now, limits, 0.0))
        assertEquals(0.465, p.limitPrice, 1e-9)
        assertEquals(0.46, p.bestPrice, 1e-9)
        // 100 at 0.46 = $0.46; the $9.54 left buys floor(9.54 / (0.465 × 0.01)) = 2051 at 0.465, but only 300 are offered.
        assertEquals(400L, p.contracts)
        assertEquals(0.46 * 1.0 + 0.465 * 3.0, p.expectedCost, 1e-9)
        assertEquals(4.0, p.payout, 1e-9)
        assertEquals((0.46 * 100 + 0.465 * 300) / 400, p.averagePrice, 1e-9)
        assertEquals(0.50 / p.averagePrice - 1.0, p.evPercent, 1e-9)
        assertTrue("only about $1.85 is offered, so it says so", p.note!!.contains("Only "))
    }

    @Test
    fun `a small stake stays on the first level`() {
        val p = ready(ApiBetPlanner.plan(target(), book(), 0.46, now, limits, 0.0))
        assertEquals(0.46, p.limitPrice, 1e-9)
        assertEquals(100L, p.contracts)
        assertNull(p.note)
    }

    @Test
    fun `every refusal says why and sends nothing`() {
        fun plan(t: BetTarget = target(), b: NovigBook? = book(), stake: Double = 5.0, l: BetLimits = limits, spent: Double = 0.0) =
            ApiBetPlanner.plan(t, b, stake, now, l, spent)
        assertTrue(refused(plan(stake = 0.0)).contains("Pick an amount"))
        assertTrue(refused(plan(stake = 25.0)).contains("over your $20.00 limit per bet"))
        assertTrue(refused(plan(spent = 48.0)).contains("daily limit"))
        assertTrue(refused(plan(t = target(starts = now - 1))).contains("pregame only"))
        assertTrue(refused(plan(t = target().let { it.copy(market = it.market.copy(status = "CLOSED")) })).contains("closed"))
        assertTrue(refused(plan(t = target().let { it.copy(market = it.market.copy(fee = null)) })).contains("fee"))
        assertTrue(refused(plan(t = target(fairAsOf = now - 20 * 60_000L))).contains("scan again"))
        // An unknown age isn't taken as fresh when money is at stake.
        assertTrue(refused(plan(t = target(fairAsOf = null))).contains("isn't known"))
        assertTrue(refused(plan(b = null)).contains("couldn't be read"))
        assertTrue(refused(plan(b = book(fetchedAt = now - 60_000L))).contains("seconds old"))
        assertTrue(refused(plan(b = book(bidsB = emptyList()))).contains("Nobody is offering"))
        // Fair 0.45 against a 0.46 price: the edge is gone.
        assertTrue(refused(plan(t = target(fair = 0.45))).contains("The edge is gone"))
        assertTrue(refused(plan(stake = 0.004)).contains("less than one contract"))
    }

    /**
     * Tj, 2026-10-02: "Why is this saying the edge is gone? It's the same odds, and they are positive ev". His sheet: Novig +108 against a fair
     * +103 is +2.4% EV, under his +3% minimum, and it read "The edge is gone". A positive edge under the minimum says so and where it's set.
     */
    @Test
    fun `a positive edge under the minimum says it's under the minimum, not that the edge is gone`() {
        // Fair 0.4685 against the 0.46 price: +1.8% EV. A 3% minimum refuses it.
        val under = refused(ApiBetPlanner.plan(target(fair = 0.4685), book(), 5.0, now, limits.copy(minEv = 0.03), 0.0))
        assertFalse(under, under.contains("edge is gone"))
        assertTrue(under, under.startsWith("+1.8% EV at Novig's best price now"))
        assertTrue(under, under.contains("is under your +3.0% minimum (Settings › Betting › Smallest edge a bet is still placed at)"))
        // Auto-bet's limits name auto-bet's setting.
        val auto = refused(ApiBetPlanner.plan(target(fair = 0.4685), book(), 5.0, now, limits.copy(minEv = 0.03, minEvWhere = "Auto-bet's minimum"), 0.0))
        assertTrue(auto, auto.contains("(Auto-bet's minimum)"))
        // Zero or less at Novig's price now: the edge really is gone, whatever the minimum.
        assertTrue(refused(ApiBetPlanner.plan(target(fair = 0.46), book(), 5.0, now, limits.copy(minEv = 0.03), 0.0)).startsWith("The edge is gone"))
        assertTrue(refused(ApiBetPlanner.plan(target(fair = 0.45), book(), 5.0, now, limits.copy(minEv = 0.03), 0.0)).startsWith("The edge is gone"))
        // At the minimum or over it, the bet is planned.
        ready(ApiBetPlanner.plan(target(fair = 0.4685), book(), 0.46, now, limits.copy(minEv = 0.018), 0.0))
    }

    @Test
    fun `a stake cut short by the minimum names the minimum`() {
        // Fair 0.4685: the 0.46 level is +1.8%, the 0.465 level +0.7%: with a 1% minimum only the first is taken, and the note says why.
        val p = ready(ApiBetPlanner.plan(target(fair = 0.4685), book(), 10.0, now, limits.copy(minEv = 0.01), 0.0))
        assertTrue(p.note, p.note!!.contains("offered at your +1.0% minimum edge or better right now"))
        // No minimum: a positive edge.
        val any = ready(ApiBetPlanner.plan(target(fair = 0.4685), book(), 10.0, now, limits, 0.0))
        assertTrue(any.note, any.note == null || any.note!!.contains("at a positive edge right now"))
    }

    @Test
    fun `the minimum edge is respected level by level`() {
        // Fair 0.4685: the 0.46 level is +1.8%, the 0.465 level +0.7%: with a 1% minimum only the first is taken.
        val p = ready(ApiBetPlanner.plan(target(fair = 0.4685), book(), 10.0, now, limits.copy(minEv = 0.01), 0.0))
        assertEquals(100L, p.contracts)
        assertEquals(0.46, p.limitPrice, 1e-9)
    }

    // ---- the placer against a mock Novig ------------------------------------------------------------------------

    private class Scenario(
        var orderStatus: String = "FILLED",
        var fills: String = """{"items":[{"fillId":"f1","orderId":"o1","clientId":"c","marketId":"mkt","outcomeId":"A","qty":400,"cost":"1.85000","taker":true,"ts":1800000000123}]}""",
        var postResponse: MockResponse? = null,
        var listedOrders: Map<String, String> = emptyMap(),
        var qty: Long = 400,
    )

    private fun novig(s: Scenario) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!
                return when {
                    request.method == "POST" && path == "/v3/orders" -> s.postResponse ?: run {
                        // Novig parses clientId as a UUID and answers 4xx for anything else (Tj's first real order, 2026-09-29).
                        val sent = json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject["clientId"]?.jsonPrimitive?.content
                        if (sent != null && !NovigTradingClient.isUuid(sent)) {
                            MockResponse().setResponseCode(400).setBody("""{"code":"BAD_REQUEST","message":"clientId: UUID parsing failed: invalid character: expected an optional prefix of `urn:uuid:` followed by [0-9a-fA-F-], found `v` at 1"}""")
                        } else {
                            MockResponse().setResponseCode(201).setBody("""{"orderId":"o1","clientId":"c"}""")
                        }
                    }
                    path == "/v3/orders/o1" -> MockResponse().setBody(
                        """{"orderId":"o1","clientId":"c","marketId":"mkt","outcomeId":"A","price":"0.465","qty":${s.qty},"remaining":0,"tif":"IOC","status":"${s.orderStatus}","createdTs":1800000000000}""",
                    )
                    path.startsWith("/v3/portfolio/fills") -> MockResponse().setBody(s.fills)
                    path.startsWith("/v3/orders?") -> {
                        val status = Regex("status=([A-Z]+)").find(path)!!.groupValues[1]
                        MockResponse().setBody(s.listedOrders[status] ?: """{"items":[]}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun placer(t: BetTracker, paused: Boolean = false, book: NovigBook? = book(), l: BetLimits = limits): ApiBetPlacer {
        return ApiBetPlacer(tradingClient(), t, books = { book }, limits = { l }, paused = { paused }, clock = { now }, dayStart = { now - 3_600_000L }, pause = { now += it })
    }

    private fun orderBody(): kotlinx.serialization.json.JsonObject =
        json.parseToJsonElement(requests.first { it.method == "POST" }.body.readUtf8()).jsonObject

    @Test
    fun `a filled bet goes out as an IOC at the confirmed ceiling and is tracked from the real fills`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        val r = placer(t).place(target(), 10.0, confirmedLimit = 0.465) as PlaceResult.Placed
        val body = orderBody()
        assertEquals("A", body["outcomeId"]!!.jsonPrimitive.content)
        assertEquals("0.465", body["price"]!!.jsonPrimitive.content)
        assertEquals("400", body["qty"]!!.jsonPrimitive.content)
        assertEquals("IOC", body["tif"]!!.jsonPrimitive.content)
        // Novig parses the clientId as a UUID (Tj's first real order was refused for a "vigilant-" prefix): a plain, valid one.
        val clientId = body["clientId"]!!.jsonPrimitive.content
        assertEquals(clientId, java.util.UUID.fromString(clientId).toString())
        assertTrue(NovigTradingClient.isUuid(clientId))
        // The Tracker gets what the fills say: 400 contracts for $1.85 (0.4625 each, better than the 0.465 ceiling).
        val bet = r.bet
        assertEquals("o1", bet.orderId)
        assertEquals(400L, bet.contracts)
        assertEquals(1.85, bet.paid!!, 1e-9)
        assertEquals(1.85, bet.stake, 1e-9)
        assertEquals(1.85 / 4.0, bet.price, 1e-9)
        assertEquals(0.50 / (1.85 / 4.0) - 1.0, bet.evPercentAtBet!!, 1e-9)
        assertEquals(2.15, bet.profitIfWon, 1e-9)
        assertEquals(0L, r.unfilledContracts)
        assertEquals(listOf(bet), t.all())
        assertTrue(bet.viaApi)
    }

    @Test
    fun `an order id that isn't a UUID is refused before anything is sent, and the ones the app makes are UUIDs`() = runBlocking {
        novig(Scenario())
        val client = tradingClient()
        val refused = runCatching { client.placeOrder("A", 0.465, 400, "IOC", "vigilant-" + java.util.UUID.randomUUID()) }.exceptionOrNull()
        assertTrue(refused is IllegalArgumentException)
        assertTrue(requests.none { it.method == "POST" })
        repeat(50) { assertTrue(NovigTradingClient.isUuid(NovigTradingClient.newClientId())) }
        assertTrue(!NovigTradingClient.isUuid("vigilant-" + java.util.UUID.randomUUID()))
        assertTrue(!NovigTradingClient.isUuid("c"))
    }

    @Test
    fun `a partly filled order is tracked for what filled`() = runBlocking {
        novig(Scenario(orderStatus = "CANCELED", qty = 400, fills = """{"items":[{"fillId":"f1","orderId":"o1","marketId":"mkt","outcomeId":"A","qty":100,"cost":"0.46000","taker":true,"ts":1}]}"""))
        val t = tracker()
        val r = placer(t).place(target(), 10.0, confirmedLimit = 0.465) as PlaceResult.Placed
        assertEquals(100L, r.bet.contracts)
        assertEquals(300L, r.unfilledContracts)
    }

    @Test
    fun `an order that filled nothing places no bet`() = runBlocking {
        novig(Scenario(orderStatus = "REJECTED", fills = """{"items":[]}"""))
        val t = tracker()
        val r = placer(t).place(target(), 10.0, confirmedLimit = 0.465)
        assertTrue(r.toString(), r is PlaceResult.NotFilled)
        assertTrue(t.all().isEmpty())
    }

    @Test
    fun `a price that moved against the confirm is refused and nothing is sent`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        val r = placer(t).place(target(), 10.0, confirmedLimit = 0.46) as PlaceResult.Refused
        assertTrue(r.reason, r.reason.startsWith("The price moved"))
        assertTrue("no order went out", requests.none { it.method == "POST" })
        assertTrue(t.all().isEmpty())
    }

    @Test
    fun `Novig's refusal comes back as words, with no bet`() = runBlocking {
        novig(Scenario(postResponse = MockResponse().setResponseCode(422).setBody("""{"code":"INSUFFICIENT_FUNDS","message":"balance too low"}""")))
        val t = tracker()
        val r = placer(t).place(target(), 10.0, confirmedLimit = 0.465) as PlaceResult.Failed
        assertTrue(r.message, r.message.contains("doesn't have enough money"))
        assertTrue(t.all().isEmpty())
        // A location refusal says what to do.
        novig(Scenario(postResponse = MockResponse().setResponseCode(451).setBody("""{"code":"GEOLOCATION_EXPIRED","message":"x"}""")))
        val g = placer(t).place(target(), 10.0, confirmedLimit = 0.465) as PlaceResult.Failed
        assertTrue(g.message, g.message.contains("open the Novig app"))
    }

    /** Novig lists ORDER_TOO_SMALL (docs.novig.com/api/errors) without publishing its size: an order it refuses so is about that order, not the account. */
    @Test
    fun `an order Novig refuses as too small is a refusal of that bet, tagged so, with no bet and no money moved`() = runBlocking {
        novig(Scenario(postResponse = MockResponse().setResponseCode(400).setBody("""{"code":"ORDER_TOO_SMALL","message":"order below the minimum"}""")))
        val t = tracker()
        val r = placer(t).placeAuto(target(), 0.05, autoLimits, expectedPrice = 0.46) as PlaceResult.Refused
        assertTrue(r.tooSmall)
        assertTrue(r.reason, r.reason.contains("too small"))
        assertTrue(t.all().isEmpty())
        // The same answer to a Bet-sheet bet reads the same (and a different 400 is still a failure, not "too small").
        val manual = placer(t).place(target(), 0.05, confirmedLimit = 0.465) as PlaceResult.Refused
        assertTrue(manual.tooSmall)
        novig(Scenario(postResponse = MockResponse().setResponseCode(400).setBody("""{"code":"INVALID_PRICE","message":"off the grid"}""")))
        assertTrue(placer(t).place(target(), 5.0, confirmedLimit = 0.465) is PlaceResult.Failed)
    }

    @Test
    fun `a stake of a few cents is planned as the contracts it buys, down to one`() {
        // A contract pays 1 cent and costs its price in cents: at 0.46 a cent buys 2, 37 cents buys 80, and a stake under one contract is refused with words.
        assertEquals(2L, ready(ApiBetPlanner.plan(target(), book(), 0.01, now, autoLimits.copy(minEv = 0.0), 0.0)).contracts)
        assertEquals(80L, ready(ApiBetPlanner.plan(target(), book(), 0.37, now, autoLimits.copy(minEv = 0.0), 0.0)).contracts)
        assertTrue(refused(ApiBetPlanner.plan(target(), book(), 0.004, now, autoLimits.copy(minEv = 0.0), 0.0)).contains("less than one contract"))
    }

    @Test
    fun `a lost answer is looked up by its clientId before anything is called failed`() = runBlocking {
        // The POST goes through and the connection drops: the order shows up as FILLED in the list, carrying the clientId sent.
        val s = Scenario(postResponse = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        novig(s)
        val t = tracker()
        // The list answers with whatever clientId was sent: read it from the recorded POST.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!
                return when {
                    request.method == "POST" && path == "/v3/orders" -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    path.startsWith("/v3/orders?") && path.contains("status=FILLED") -> {
                        val sent = json.parseToJsonElement(requests.first { it.method == "POST" }.body.clone().readUtf8()).jsonObject["clientId"]!!.jsonPrimitive.content
                        MockResponse().setBody("""{"items":[{"orderId":"o1","clientId":"$sent","marketId":"mkt","outcomeId":"A","price":"0.465","qty":400,"remaining":0,"tif":"IOC","status":"FILLED","createdTs":1}]}""")
                    }
                    path.startsWith("/v3/orders?") -> MockResponse().setBody("""{"items":[]}""")
                    path == "/v3/orders/o1" -> MockResponse().setBody("""{"orderId":"o1","marketId":"mkt","outcomeId":"A","price":"0.465","qty":400,"remaining":0,"tif":"IOC","status":"FILLED","createdTs":1}""")
                    path.startsWith("/v3/portfolio/fills") -> MockResponse().setBody(s.fills)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val r = placer(t).place(target(), 10.0, confirmedLimit = 0.465)
        assertTrue(r.toString(), r is PlaceResult.Placed)
        assertEquals("o1", t.all().single().orderId)
    }

    @Test
    fun `a lost answer's order still queued (PENDING) is found, asking only about this outcome's orders`() = runBlocking {
        val s = Scenario()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!
                return when {
                    request.method == "POST" && path == "/v3/orders" -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    path.startsWith("/v3/orders?") && path.contains("status=PENDING") -> {
                        val sent = json.parseToJsonElement(requests.first { it.method == "POST" }.body.clone().readUtf8()).jsonObject["clientId"]!!.jsonPrimitive.content
                        MockResponse().setBody("""{"items":[{"orderId":"o1","clientId":"$sent","marketId":"mkt","outcomeId":"A","price":"0.465","qty":400,"remaining":400,"tif":"IOC","status":"PENDING","createdTs":1}]}""")
                    }
                    path.startsWith("/v3/orders?") -> MockResponse().setBody("""{"items":[]}""")
                    path == "/v3/orders/o1" -> MockResponse().setBody("""{"orderId":"o1","marketId":"mkt","outcomeId":"A","price":"0.465","qty":400,"remaining":0,"tif":"IOC","status":"FILLED","createdTs":1}""")
                    path.startsWith("/v3/portfolio/fills") -> MockResponse().setBody(s.fills)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val r = placer(tracker()).place(target(), 10.0, confirmedLimit = 0.465)
        assertTrue(r.toString(), r is PlaceResult.Placed)
        assertTrue(requests.filter { it.method == "GET" && it.path!!.startsWith("/v3/orders?") }.all { it.path!!.contains("outcome=A") })
    }

    @Test
    fun `an open order with nothing left resting has filled all it will`() {
        val o = NovigOrder("o", null, "m", "A", 0.5, 400, 0, "IOC", "OPEN", 1)
        assertTrue(o.terminal)
        assertTrue(!o.copy(remaining = 10).terminal)
        assertTrue(!o.copy(status = "PENDING", remaining = 400).terminal)
    }

    @Test
    fun `a lost answer with no trace is unconfirmed, never called placed or failed`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return if (request.method == "POST") MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else MockResponse().setBody("""{"items":[]}""")
            }
        }
        val t = tracker()
        val r = placer(t).place(target(), 10.0, confirmedLimit = 0.465)
        assertTrue(r.toString(), r is PlaceResult.Unconfirmed)
        assertTrue(t.all().isEmpty())
        assertEquals("the order was sent once, never again", 1, requests.count { it.method == "POST" })
    }

    @Test
    fun `the same outcome isn't bet twice, the daily total counts, and pausing blocks it`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        val p = placer(t)
        assertTrue(p.place(target(), 10.0, 0.465) is PlaceResult.Placed)
        val again = p.plan(target(), 5.0)
        assertTrue((again as PlanResult.Refused).reason.contains("already bet this through the API"))
        // Allowed on purpose: the day's total is the first bet's $1.85 plus this one; a $49 cap would refuse a $48 bet.
        val capped = placer(t, l = limits.copy(maxStake = 100.0, maxPerDay = 50.0)).plan(target(), 49.0, allowRepeat = true)
        assertTrue((capped as PlanResult.Refused).reason.contains("daily limit"))
        val paused = placer(t, paused = true).plan(target(), 5.0, allowRepeat = true)
        assertTrue((paused as PlanResult.Refused).reason.contains("paused"))
    }

    @Test
    fun `the trading client reads fills, positions, orders and the ledger from Novig's shapes`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!
                return when {
                    path.startsWith("/v3/portfolio/positions") -> MockResponse().setBody("""[{"marketId":"mkt","outcomeId":"A","qty":400,"cost":"1.85000"}]""")
                    path.startsWith("/v3/account/subaccounts/sub/transactions") ->
                        MockResponse().setBody("""{"items":[{"transactionId":"t1","kind":"SETTLEMENT","amount":"4.00000","ref":"mkt","ts":5}],"next":null}""")
                    path.startsWith("/v3/account/subaccounts/sub/balance") -> MockResponse().setBody("""{"keyId":"sub","balance":"12.50000"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val c = tradingClient()
        assertEquals(listOf(NovigPosition("mkt", "A", 400, 1.85)), c.positions("mkt"))
        assertEquals(LedgerRow("t1", "SETTLEMENT", 4.0, "mkt", 5), c.ledger("sub", "SETTLEMENT", 1, 2).single())
        assertEquals(12.5, c.balance("sub"), 1e-9)
        assertTrue(requests.any { it.path!!.contains("kind=SETTLEMENT") && it.path!!.contains("startsAfter=1") && it.path!!.contains("startsBefore=2") })
        assertEquals("0.455", NovigTradingClient.priceText(0.455))
        assertEquals("0.050", NovigTradingClient.priceText(0.05))
    }

    @Test
    fun `fills are read page by page until the last one`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!
                fun fill(id: String) = """{"fillId":"$id","orderId":"o","marketId":"m","outcomeId":"A","qty":1,"cost":"0.01000","taker":true,"ts":1}"""
                return when {
                    path.contains("after=CURSOR%2F2") -> MockResponse().setBody("""{"items":[${fill("f3")}]}""")
                    path.startsWith("/v3/portfolio/fills") -> MockResponse().setBody("""{"items":[${fill("f1")},${fill("f2")}],"next":"CURSOR/2"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        assertEquals(listOf("f1", "f2", "f3"), tradingClient().fills().map { it.fillId })
    }

    @Test
    fun `an Undo of a check mark never removes a bet placed through the API`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        placer(t).place(target(placedKey = "cno:row1"), 10.0, 0.465)
        t.untrack("cno:row1")
        assertNotNull(t.all().singleOrNull { it.orderId == "o1" })
        assertEquals(BetStatus.PENDING, t.all().single().status)
    }

    // ---- the auto-bet's path (Tj, 2026-10-01) ------------------------------------------------------------------

    private val autoLimits = BetLimits(maxStake = 10.0, maxPerDay = 50.0, minEv = 0.02)

    @Test
    fun `an auto-bet goes out with no confirm at the plan's own ceiling, under its own limits, and is tracked from the fills`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        // The placer's own limits are tighter than the auto-bet's (a $1 per-bet limit): the auto-bet's override is what applies.
        val r = placer(t, l = BetLimits(1.0, 50.0, 0.5)).placeAuto(target(), 10.0, autoLimits, expectedPrice = 0.46) as PlaceResult.Placed
        val body = orderBody()
        assertEquals("0.465", body["price"]!!.jsonPrimitive.content)
        assertEquals("400", body["qty"]!!.jsonPrimitive.content)
        assertEquals("IOC", body["tif"]!!.jsonPrimitive.content)
        assertEquals(BetTracker.SOURCE_CNO, r.bet.source)
        assertEquals("cno:row1", r.bet.placedKey)
        assertEquals(1.85, r.bet.stake, 1e-9)
        assertEquals(listOf(r.bet), t.all())
    }

    @Test
    fun `an auto-bet's own minimum edge decides how deep it buys`() = runBlocking {
        novig(Scenario(fills = """{"items":[{"fillId":"f1","orderId":"o1","marketId":"mkt","outcomeId":"A","qty":100,"cost":"0.46000","taker":true,"ts":1}]}""", qty = 100))
        // Fair 0.4685: the 0.46 level is +1.8%, the 0.465 level +0.7%: a 1.5% minimum buys only the first level.
        placer(tracker()).placeAuto(target(fair = 0.4685), 10.0, autoLimits.copy(minEv = 0.015), expectedPrice = 0.46)
        val body = orderBody()
        assertEquals("0.460", body["price"]!!.jsonPrimitive.content)
        assertEquals("100", body["qty"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an auto-bet whose book price isn't the price it was judged at sends nothing`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        // The bet was judged at 0.60 but the outcome found is offered at 0.46: not the same bet (or the market moved). Never sent.
        val r = placer(t).placeAuto(target(), 5.0, autoLimits, expectedPrice = 0.60) as PlaceResult.Refused
        assertTrue(r.reason, r.reason.contains("isn't the price it was judged at"))
        assertTrue(requests.none { it.method == "POST" })
        assertTrue(t.all().isEmpty())
    }

    /** Tj, 2026-10-01: "I don't want it to bet anything that is more of a longshot than +130 odds". The book's price is +117 (0.46). */
    @Test
    fun `a longest-odds limit is checked on the book read just before the order, not only on the price it was judged at`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        // Judged at +105 (0.4878), within the 3-point match of the book's 0.46: the price match alone lets it through. The book is now at +117, over a +110 limit.
        val drifted = placer(t).placeAuto(target(), 5.0, autoLimits.copy(maxOdds = 110), expectedPrice = 0.4878) as PlaceResult.Refused
        assertEquals("Novig's best price is now +117, longer than your +110 limit.", drifted.reason)
        assertTrue("nothing was sent", requests.none { it.method == "POST" })
        assertTrue(t.all().isEmpty())
        // At the limit it is bet; with no limit (the default) it is too.
        assertTrue(placer(tracker()).placeAuto(target(), 5.0, autoLimits.copy(maxOdds = 117), expectedPrice = 0.46) is PlaceResult.Placed)
    }

    @Test
    fun `the longest-odds limit is the planner's, so a bet by hand is never held to it`() {
        val long = BetLimits(maxStake = 20.0, maxPerDay = 50.0, minEv = 0.0, maxOdds = 110)
        assertTrue(refused(ApiBetPlanner.plan(target(), book(), 5.0, now, long, 0.0)).contains("longer than your +110 limit"))
        // The same bet under the limits a manual bet has (no odds limit) is planned as before.
        assertEquals(0, limits.maxOdds)
        assertEquals(0.46, ready(ApiBetPlanner.plan(target(), book(), 5.0, now, limits, 0.0)).bestPrice, 1e-9)
        // A favourite (A offered at 0.60, -150) passes even a limit of +100.
        val fav = NovigBook("mkt", 1, mapOf("B" to listOf(BidLevel(400, 500)), "A" to listOf(BidLevel(450, 50))), now)
        assertEquals(0.6, ready(ApiBetPlanner.plan(target(fair = 0.65), fav, 5.0, now, long.copy(maxOdds = 100), 0.0)).bestPrice, 1e-9)
    }

    @Test
    fun `an auto-bet obeys its per-bet maximum, the daily limit, the pregame rule and the fresh fair odds like any API bet`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        fun refused(target: BetTarget = target(), stake: Double = 5.0, l: BetLimits = autoLimits, price: Double = 0.46) =
            (kotlinx.coroutines.runBlocking { placer(t).placeAuto(target, stake, l, price) } as PlaceResult.Refused).reason
        assertTrue(refused(stake = 12.0).contains("over your $10.00 limit per bet"))
        assertTrue(refused(l = autoLimits.copy(maxPerDay = 4.0)).contains("daily limit"))
        assertTrue(refused(target = target(starts = now - 1)).contains("pregame only"))
        assertTrue(refused(target = target(fairAsOf = now - 20 * 60_000L)).contains("scan again"))
        assertTrue(refused(target = target(fair = 0.45)).contains("The edge is gone"))
        assertTrue("nothing was sent", requests.none { it.method == "POST" })
    }

    @Test
    fun `an auto-bet never repeats a bet that is still open`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        val p = placer(t)
        assertTrue(p.placeAuto(target(), 10.0, autoLimits, 0.46) is PlaceResult.Placed)
        val again = p.placeAuto(target(), 10.0, autoLimits, 0.46) as PlaceResult.Refused
        assertTrue(again.reason, again.reason.contains("already bet this through the API"))
        assertEquals(1, requests.count { it.method == "POST" })
    }

    @Test
    fun `the Bet sheet's placer and the auto-bet's share one order lock, so the same bet is never placed twice at once`() = runBlocking {
        novig(Scenario())
        val t = tracker()
        val shared = kotlinx.coroutines.sync.Mutex()
        fun make() = ApiBetPlacer(tradingClient(), t, books = { book() }, limits = { limits }, clock = { now }, dayStart = { now - 3_600_000L }, pause = { now += it }, lock = shared)
        val results = listOf(
            kotlinx.coroutines.GlobalScope.async(kotlinx.coroutines.Dispatchers.Default) { make().place(target(), 10.0, confirmedLimit = 0.465) },
            kotlinx.coroutines.GlobalScope.async(kotlinx.coroutines.Dispatchers.Default) { make().placeAuto(target(), 10.0, autoLimits, 0.46) },
        ).map { it.await() }
        assertEquals(1, results.count { it is PlaceResult.Placed })
        assertEquals(1, results.count { it is PlaceResult.Refused })
        assertEquals("exactly one order reached Novig", 1, requests.count { it.method == "POST" })
        assertEquals(1, t.all().size)
    }
}
