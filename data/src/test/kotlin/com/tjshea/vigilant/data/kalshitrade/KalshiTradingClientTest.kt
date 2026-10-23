package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.kalshi.KalshiSigner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/** KV1: Kalshi's order/portfolio calls: the shapes sent (V2 bid/ask on the YES leg), the signing, the replies parsed, the legacy fallback. Nothing here has met a live key. */
class KalshiTradingClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private val routes = HashMap<String, (RecordedRequest) -> MockResponse>()

    private fun pem(): String {
        val p = Ed25519PrivateKeyParameters(SecureRandom())
        val der = PrivateKeyInfoFactory.createPrivateKeyInfo(p).encoded
        return "-----BEGIN " + "PRIVATE " + "KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END " + "PRIVATE " + "KEY-----\n"
    }

    private val signer = KalshiSigner("key-id-1", pem())

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                val key = request.method + " " + request.requestUrl!!.encodedPath
                return routes[key]?.invoke(request) ?: MockResponse().setResponseCode(404).setBody("""{"error":{"code":"not_found","message":"no route $key"}}""")
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun client() = KalshiTradingClient(OkHttpClient(), json, { signer }, server.url("/trade-api/v2").toString().trimEnd('/'))

    private val cid = "11111111-2222-3333-4444-555555555555"

    @Test
    fun `buying YES is a V2 bid at the price, signed, with the id and an IOC`() = runTest {
        routes["POST /trade-api/v2/portfolio/events/orders"] = { MockResponse().setBody("""{"order_id":"o-1","client_order_id":"$cid","fill_count":"3.00","remaining_count":"0.00","average_fill_price":"0.4000","average_fee_paid":"0.0170","ts_ms":1}""") }
        val placed = client().buy("KXNFLGAME-26OCT25BALDAL-BAL", "yes", 0.40, 3, cid)
        assertEquals("o-1", placed.orderId)
        assertEquals(3.0, placed.filled, 0.0)
        assertEquals(0.40, placed.averagePrice!!, 1e-9)
        assertEquals(0.017, placed.averageFee!!, 1e-9)
        val r = seen.single()
        val body = json.parseToJsonElement(r.body.readUtf8()).jsonObject
        assertEquals("bid", body["side"]!!.jsonPrimitive.content)
        assertEquals("0.4000", body["price"]!!.jsonPrimitive.content)
        assertEquals("3.00", body["count"]!!.jsonPrimitive.content)
        assertEquals("immediate_or_cancel", body["time_in_force"]!!.jsonPrimitive.content)
        assertEquals(cid, body["client_order_id"]!!.jsonPrimitive.content)
        assertEquals("taker_at_cross", body["self_trade_prevention_type"]!!.jsonPrimitive.content)
        assertEquals("key-id-1", r.getHeader("KALSHI-ACCESS-KEY"))
        assertNotNull(r.getHeader("KALSHI-ACCESS-SIGNATURE"))
        assertNotNull(r.getHeader("KALSHI-ACCESS-TIMESTAMP"))
    }

    @Test
    fun `buying NO at 37 cents is an ask on the YES leg at 63 cents when the old route is gone`() = runTest {
        routes["POST /trade-api/v2/portfolio/orders"] = { MockResponse().setResponseCode(404).setBody("{}") }
        routes["POST /trade-api/v2/portfolio/events/orders"] = { MockResponse().setBody("""{"order_id":"o-2","fill_count":"0.00","remaining_count":"5.00","ts_ms":1}""") }
        val placed = client().buy("T", "no", 0.37, 5, cid, KalshiTradingClient.GTC, postOnly = true, expirationMs = 1_800_000_000_000L)
        assertEquals(5.0, placed.remaining, 0.0)
        assertNull(placed.averagePrice)
        val body = json.parseToJsonElement(seen.last().body.readUtf8()).jsonObject
        assertEquals("ask", body["side"]!!.jsonPrimitive.content)
        assertEquals("0.6300", body["price"]!!.jsonPrimitive.content)
        assertEquals("good_till_canceled", body["time_in_force"]!!.jsonPrimitive.content)
        assertEquals("true", body["post_only"]!!.jsonPrimitive.content)
        assertEquals("1800000000", body["expiration_time"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the balance read carries the cash of each exchange shard`() = runTest {
        routes["GET /trade-api/v2/portfolio/balance"] = { MockResponse().setBody("""{"balance":629,"balance_dollars":"6.2900","portfolio_value":0,"balance_breakdown":[{"exchange_index":0,"balance":"0.0000"},{"exchange_index":1,"balance":"6.2900"}]}""") }
        val b = client().balance()
        assertEquals(6.29, b.cash, 1e-9)
        assertEquals(listOf(0 to 0.0, 1 to 6.29), b.shards)
    }

    @Test
    fun `insufficient_balance moves the cash of the other shards to the market's shard and sends the order again`() = runTest {
        var orders = 0
        var bal = 0
        routes["POST /trade-api/v2/portfolio/events/orders"] = {
            orders += 1
            if (orders == 1) MockResponse().setResponseCode(400).setBody("""{"error":{"code":"insufficient_balance","message":"insufficient balance"}}""")
            else MockResponse().setBody("""{"order_id":"o-9","fill_count":"1.00","remaining_count":"0.00","ts_ms":1}""")
        }
        routes["GET /trade-api/v2/markets/T"] = { MockResponse().setBody("""{"market":{"ticker":"T","exchange_index":2}}""") }
        routes["GET /trade-api/v2/portfolio/balance"] = {
            bal += 1
            MockResponse().setBody(if (bal <= 1) """{"balance":629,"balance_dollars":"6.2900","portfolio_value":0,"balance_breakdown":[{"exchange_index":0,"balance":"6.2900"},{"exchange_index":2,"balance":"0.0000"}]}"""
                else """{"balance":629,"balance_dollars":"6.2900","portfolio_value":0,"balance_breakdown":[{"exchange_index":0,"balance":"0.0000"},{"exchange_index":2,"balance":"6.2900"}]}""")
        }
        routes["POST /trade-api/v2/portfolio/intra_exchange_instance_transfer"] = { MockResponse().setBody("""{"transfer_id":"t-1"}""") }
        val placed = client().buy("T", "no", 0.42, 1, cid)
        assertEquals("o-9", placed.orderId)
        assertEquals(2, orders)
        val transfer = seen.first { it.path!!.endsWith("intra_exchange_instance_transfer") }
        val body = json.parseToJsonElement(transfer.body.readUtf8()).jsonObject
        assertEquals("0", body["source_exchange_shard"]!!.jsonPrimitive.content)
        assertEquals("2", body["destination_exchange_shard"]!!.jsonPrimitive.content)
        assertEquals("62900", body["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a refusal that persists after a move is not answered with another move`() = runTest {
        routes["POST /trade-api/v2/portfolio/events/orders"] = { MockResponse().setResponseCode(400).setBody("""{"error":{"code":"insufficient_balance","message":"insufficient balance"}}""") }
        routes["GET /trade-api/v2/markets/T"] = { MockResponse().setBody("""{"market":{"ticker":"T","exchange_index":0}}""") }
        routes["GET /trade-api/v2/portfolio/balance"] = { MockResponse().setBody("""{"balance":0,"balance_dollars":"0.0000","portfolio_value":0,"balance_breakdown":[{"exchange_index":0,"balance":"0.0000"}]}""") }
        val e = runCatching { client().buy("T", "yes", 0.42, 1, cid) }.exceptionOrNull() as KalshiApiException
        assertEquals(400, e.status)
        assertTrue("no transfer without cash on another shard", seen.none { it.path!!.endsWith("intra_exchange_instance_transfer") })
    }

    @Test
    fun `a retired V2 route falls back to the legacy one and stays there`() = runTest {
        routes["POST /trade-api/v2/portfolio/events/orders"] = { MockResponse().setResponseCode(404).setBody("{}") }
        routes["POST /trade-api/v2/portfolio/orders"] = {
            MockResponse().setBody("""{"order":{"order_id":"o-3","client_order_id":"$cid","ticker":"T","outcome_side":"yes","status":"executed","yes_price_dollars":"0.4000","no_price_dollars":"0.6000",
              "fill_count_fp":"2.00","remaining_count_fp":"0.00","initial_count_fp":"2.00","taker_fill_cost_dollars":"0.8000","maker_fill_cost_dollars":"0.0000","taker_fees_dollars":"0.0300","maker_fees_dollars":"0.0000"}}""")
        }
        val c = client()
        val p = c.buy("T", "yes", 0.40, 2, cid)
        assertEquals(2.0, p.filled, 0.0)
        assertEquals(0.40, p.averagePrice!!, 1e-9)
        assertEquals(0.015, p.averageFee!!, 1e-9)
        val legacy = json.parseToJsonElement(seen.last().body.readUtf8()).jsonObject
        assertEquals("buy", legacy["action"]!!.jsonPrimitive.content)
        assertEquals("0.4000", legacy["yes_price_dollars"]!!.jsonPrimitive.content)
        seen.clear()
        c.buy("T", "yes", 0.40, 2, "11111111-2222-3333-4444-555555555556")
        assertEquals(listOf("/trade-api/v2/portfolio/orders"), seen.map { it.requestUrl!!.encodedPath })
    }

    @Test
    fun `a refusal carries Kalshi's code, and a key or a rate limit is told apart`() = runTest {
        routes["POST /trade-api/v2/portfolio/events/orders"] = { MockResponse().setResponseCode(403).setBody("""{"error":{"code":"forbidden","message":"key cannot trade"}}""") }
        try {
            client().buy("T", "yes", 0.4, 1, cid)
            fail("expected a refusal")
        } catch (e: KalshiApiException) {
            assertTrue(e.isAuth)
            assertEquals("forbidden", e.code)
            assertEquals("key cannot trade", e.detail)
        }
    }

    @Test
    fun `an order that is not a UUID or has a bad price is refused before any request`() = runTest {
        val c = client()
        try { c.buy("T", "yes", 0.4, 1, "vigilant-1"); fail() } catch (_: IllegalArgumentException) {}
        try { c.buy("T", "yes", 1.0, 1, cid); fail() } catch (_: IllegalArgumentException) {}
        try { c.buy("T", "maybe", 0.5, 1, cid); fail() } catch (_: IllegalArgumentException) {}
        try { c.buy("T", "yes", 0.5, 0, cid); fail() } catch (_: IllegalArgumentException) {}
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `no key is a refusal with no request`() = runTest {
        val c = KalshiTradingClient(OkHttpClient(), json, { null }, server.url("/trade-api/v2").toString())
        assertFalse(c.ready)
        try { c.balance(); fail() } catch (e: KalshiApiException) { assertEquals("no_key", e.code) }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `balance, order, fills, positions and settlements are read in dollars`() = runTest {
        routes["GET /trade-api/v2/portfolio/balance"] = { MockResponse().setBody("""{"balance":12345,"balance_dollars":"123.4500","portfolio_value":500}""") }
        routes["GET /trade-api/v2/portfolio/orders/o-9"] = {
            MockResponse().setBody("""{"order":{"order_id":"o-9","client_order_id":"$cid","ticker":"T","outcome_side":"no","book_side":"ask","status":"resting","yes_price_dollars":"0.6300","no_price_dollars":"0.3700",
              "fill_count_fp":"1.00","remaining_count_fp":"4.00","initial_count_fp":"5.00","taker_fill_cost_dollars":"0.0000","maker_fill_cost_dollars":"0.3700","taker_fees_dollars":"0.0000","maker_fees_dollars":"0.0100","created_time":"2026-10-20T10:00:00Z"}}""")
        }
        routes["GET /trade-api/v2/portfolio/fills"] = {
            MockResponse().setBody("""{"fills":[{"fill_id":"f1","order_id":"o-9","ticker":"T","outcome_side":"no","book_side":"ask","count_fp":"1.00","yes_price_dollars":"0.6300","no_price_dollars":"0.3700","is_taker":false,"fee_cost":"0.0100","created_time":"2026-10-20T10:01:00Z"}],"cursor":""}""")
        }
        routes["GET /trade-api/v2/portfolio/positions"] = {
            MockResponse().setBody("""{"market_positions":[{"ticker":"T","position_fp":"-1.00","market_exposure_dollars":"0.3700","total_traded_dollars":"0.3700","realized_pnl_dollars":"0.0000","fees_paid_dollars":"0.0100"}],"event_positions":[],"cursor":""}""")
        }
        routes["GET /trade-api/v2/portfolio/settlements"] = {
            MockResponse().setBody("""{"settlements":[{"ticker":"T","event_ticker":"E","market_result":"no","yes_count_fp":"0.00","yes_total_cost_dollars":"0.0","no_count_fp":"1.00","no_total_cost_dollars":"0.37","revenue":100,"settled_time":"2026-10-20T23:00:00Z","fee_cost":"0.0100"}]}""")
        }
        val c = client()
        assertEquals(123.45, c.balance().cash, 1e-9)
        val o = c.order("o-9")!!
        assertEquals("no", o.outcomeSide)
        assertEquals(0.37, o.price, 1e-9)
        assertEquals(4.0, o.remainingCount, 0.0)
        assertFalse(o.terminal)
        assertEquals(0.01, o.fees, 1e-9)
        val f = c.fills().single()
        assertEquals(0.37, f.price, 1e-9)
        assertFalse(f.taker)
        assertEquals(-1.0, c.positions().single().position, 0.0)
        val s = c.settlements().single()
        assertEquals("no", s.result)
        assertEquals(1.0, s.revenue, 1e-9)
        assertNull(c.order("missing"))
    }

    @Test
    fun `cancel says whether the order was still there`() = runTest {
        routes["DELETE /trade-api/v2/portfolio/events/orders/o-1"] = { MockResponse().setBody("""{"order_id":"o-1","reduced_by":"4.00","ts_ms":1}""") }
        routes["DELETE /trade-api/v2/portfolio/events/orders/o-2"] = { MockResponse().setResponseCode(404).setBody("""{"error":{"code":"order_not_found","message":"gone"}}""") }
        val c = client()
        assertTrue(c.cancel("o-1", "T"))
        assertFalse(c.cancel("o-2", "T"))
    }
}
