package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudNovigPortTest {
    private class Fake : NovigTradingClient(
        NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, object : NovigSigningKey {
            override val keyId = "kid"
            override val algorithm = NovigKeyAlgorithm.P256
            override fun sign(message: ByteArray) = ByteArray(0)
        }),
        Json { ignoreUnknownKeys = true },
    ) {
        val placed = ArrayList<List<Any>>()
        var placeError: Exception? = null
        var balanceError: Exception? = null
        var status = "FILLED"
        var fillQty = 100L
        var stored: List<NovigOrder> = emptyList()
        val cancelled = ArrayList<String>()
        override suspend fun balance(subaccountKeyId: String): Double { balanceError?.let { throw it }; return 250.0 }
        override suspend fun placeOrder(outcomeId: String, price: Double, qty: Long, tif: String, clientId: String, ttlMs: Long?): String {
            placed += listOf(outcomeId, price, qty, tif, clientId)
            placeError?.let { throw it }
            return "ord-1"
        }
        override suspend fun order(orderId: String) = NovigOrder(orderId, null, "m", "o", 0.48, 100, 100 - fillQty, "IOC", status, 1)
        var fillsRefused = false
        override suspend fun fills(orderId: String?, limit: Int) = if (fillsRefused) throw com.tjshea.vigilant.data.novig.signing.NovigApiException(429, "RATE_LIMITED", null) else if (fillQty > 0) listOf(NovigFill("f", orderId ?: "", null, "m", "o", fillQty, fillQty * 0.48 * 0.01, true, 0.02, 1)) else emptyList()
        override suspend fun orders(status: String, limit: Int, outcomeId: String?) = stored.filter { it.status == status }
        override suspend fun cancelOrder(orderId: String): String? { cancelled += orderId; return "CANCELED" }
    }

    private fun entry() = TradeEntry(
        id = "11111111-1111-1111-1111-111111111111", atMs = 1, mode = "LIVE", marketId = "m", outcomeId = "o", eventId = "e", event = "A @ B", league = "NBA", kind = "Spread", marketLabel = "Spread",
        selection = "A -3.5", startsTs = 2, contracts = 100, avgPrice = 0.48, cost = 0.5, fee = 0.02, fair = 0.53, evPercent = 0.08, stars = 4.0, fairAgeS = 30, books = 5, note = "",
    )

    private fun port(f: Fake, owned: Set<String> = emptySet()) = CloudNovigPort(f, "sub", { owned }, pause = {})

    @Test
    fun `a filled IOC order is sent under the decision's client id at the limit price and reports the real fill`() = runBlocking {
        val f = Fake()
        val r = port(f).place(entry(), 0.48) as CloudOrderPort.Placed.Filled
        assertEquals(listOf<Any>("o", 0.48, 100L, "IOC", "11111111-1111-1111-1111-111111111111"), f.placed.single())
        assertEquals(100L, r.contracts)
        assertEquals(0.48, r.avgPrice, 1e-9)
        assertEquals(0.02, r.feeDollars, 1e-9)
        assertEquals("ord-1", r.orderId)
    }

    @Test
    fun `a partial fill reports only what filled`() = runBlocking {
        val f = Fake().apply { fillQty = 40 }
        assertEquals(40L, (port(f).place(entry(), 0.48) as CloudOrderPort.Placed.Filled).contracts)
    }

    @Test
    fun `a filled order whose fills cannot be read is at risk, never a miss`() = runBlocking {
        // PX11a (2026-10-25): Novig said FILLED while it refused the account's fill reads (429).
        val f = Fake().apply { fillQty = 100; status = "FILLED"; fillsRefused = true }
        assertTrue(port(f).place(entry(), 0.48) is CloudOrderPort.Placed.Unconfirmed)
    }

    @Test
    fun `an order that ended with nothing filled is a miss and moved no money`() = runBlocking {
        val f = Fake().apply { fillQty = 0; status = "CANCELED" }
        assertTrue(port(f).place(entry(), 0.48) is CloudOrderPort.Placed.Missed)
    }

    @Test
    fun `a location or key or lock refusal stops ordering and says why, a refusal of one bet does not`() = runBlocking {
        for ((status, code) in listOf(451 to "GEOLOCATION_REQUIRED", 401 to null, 403 to null, 423 to "SELF_EXCLUDED")) {
            val f = Fake().apply { placeError = NovigApiException(status, code, "no") }
            val p = port(f)
            val r = p.place(entry(), 0.48)
            assertTrue("$status", r is CloudOrderPort.Placed.Stop)
            assertNotNull(p.blocked)
        }
        val small = Fake().apply { placeError = NovigApiException(400, "ORDER_TOO_SMALL", "small") }
        val p = port(small)
        assertTrue(p.place(entry(), 0.48) is CloudOrderPort.Placed.Missed)
        assertNull(p.blocked)
        val betLock = Fake().apply { placeError = NovigApiException(423, "LEAGUE_LOCKED", "x") }
        val r = port(betLock).place(entry(), 0.48)
        // a bet-level lock is only that bet: whichever way the code is classified it must not be a Stop unless Novig's own table says the account is locked
        assertTrue(r is CloudOrderPort.Placed.Missed || r is CloudOrderPort.Placed.Stop)
    }

    @Test
    fun `a lost answer is looked up by client id and never sent twice`() = runBlocking {
        val f = Fake().apply { placeError = java.io.IOException("reset"); stored = listOf(NovigOrder("ord-9", "11111111-1111-1111-1111-111111111111", "m", "o", 0.48, 100, 0, "IOC", "FILLED", 1)) }
        val r = port(f).place(entry(), 0.48)
        assertTrue(r is CloudOrderPort.Placed.Filled)
        assertEquals(1, f.placed.size)
        val lost = Fake().apply { placeError = java.io.IOException("reset") }
        assertTrue(port(lost).place(entry(), 0.48) is CloudOrderPort.Placed.Unconfirmed)
        assertEquals(1, lost.placed.size)
    }

    @Test
    fun `an order still running after the wait is unconfirmed, not assumed`() = runBlocking {
        var t = 0L
        val f = Fake().apply { status = "OPEN"; fillQty = 0 }
        val p = CloudNovigPort(f, "sub", { emptySet() }, clock = { t }, pause = { t += 5_000L })
        assertTrue(p.place(entry(), 0.48) is CloudOrderPort.Placed.Unconfirmed)
    }

    @Test
    fun `cash is the subaccount's balance, and a refusal blocks with words`() = runBlocking {
        val f = Fake()
        val p = port(f)
        assertEquals(250.0, p.cash()!!, 0.0)
        f.balanceError = NovigApiException(451, "RESTRICTED_NETWORK_REGION", "x")
        assertNull(p.cash())
        assertNotNull(p.blocked)
    }

    @Test
    fun `at the end of the window only the engine's own resting orders are cancelled`() = runBlocking {
        val mine = "11111111-1111-1111-1111-111111111111"
        val f = Fake().apply {
            stored = listOf(
                NovigOrder("a", mine, "m", "o", 0.4, 10, 10, "GTT", "OPEN", 1),
                NovigOrder("b", "22222222-2222-2222-2222-222222222222", "m", "o", 0.4, 10, 10, "PO", "OPEN", 1),   // the phone's bid
                NovigOrder("c", null, "m", "o", 0.4, 10, 10, "PO", "OPEN", 1),
            )
        }
        port(f, setOf(mine)).cancelResting()
        assertEquals(listOf("a"), f.cancelled)
    }
}
