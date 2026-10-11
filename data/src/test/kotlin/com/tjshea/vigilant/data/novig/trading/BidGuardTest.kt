package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import java.security.SecureRandom
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-11: "I accidentally left a bad bid up indefinitely, and someone filled the bid and I lost almost immediately." */
class BidGuardTest {
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"

    private val now = 1_800_000_000_000L
    private fun order(id: String, ageMin: Long, expires: Long? = null, tif: String = "GTC", status: String = "OPEN", remaining: Long = 10L, client: String? = null) =
        NovigOrder(id, client, "m", "o", 0.5, 10, remaining, tif, status, now - ageMin * 60_000L, expires)

    @Test fun anOrderWithNoExpiryUpLongerThanTheLimitIsStaleAndAnythingElseIsLeftAlone() {
        val all = listOf(
            order("old-no-expiry", 45), order("young-no-expiry", 5), order("old-with-expiry", 45, expires = now + 5 * 60_000L, client = "c"),
            order("ioc", 45, tif = "IOC"), order("filled", 45, status = "FILLED"), order("drained", 45, remaining = 0L),
        )
        val stale = BidGuard.stale(all, now, 30 * 60_000L)
        assertEquals(setOf("old-no-expiry"), stale.keys)
        assertTrue(stale.getValue("old-no-expiry").contains("no expiry"))
    }

    @Test fun theLimitIsTjsToSet() {
        assertEquals(setOf("a"), BidGuard.stale(listOf(order("a", 12)), now, 10 * 60_000L).keys)
        assertTrue(BidGuard.stale(listOf(order("a", 12)), now, 15 * 60_000L).isEmpty())
    }

    @Test fun aPassCancelsOnlyTheStaleOnesInOneBatchAndCountsThem() = runTest {
        val cancelled = ArrayList<List<String>>()
        val client = object : NovigTradingClient(NovigSignedClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, PemSigningKey("kid", pem), "http://localhost"), Json { ignoreUnknownKeys = true }) {
            override suspend fun orders(status: String, limit: Int, outcomeId: String?) = listOf(order("old", 99), order("new", 1))
            override suspend fun cancelOrdersBatch(orderIds: List<String>): BatchCancel { cancelled += orderIds; return BatchCancel(orderIds.toSet(), emptyMap()) }
        }
        val told = ArrayList<String>()
        val runner = BidGuard.Runner(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), { client }, { true }, { 30 * 60_000L }, { done -> told += done.map { it.first.orderId } }, { now })
        assertEquals(1, runner.pass())
        assertEquals(listOf(listOf("old")), cancelled)
        assertEquals(listOf("old"), told)
        assertEquals(1, runner.status.value.cancelledTotal)
        val off = BidGuard.Runner(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), { client }, { false }, { 30 * 60_000L }, clock = { now })
        assertEquals("switched off: not even a read", 0, off.pass())
        assertEquals(1, cancelled.size)
    }
}
