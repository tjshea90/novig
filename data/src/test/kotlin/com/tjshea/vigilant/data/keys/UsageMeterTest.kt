package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class UsageMeterTest {

    private val file = File.createTempFile("usage", ".json").also { it.delete() }
    private var now = Instant.parse("2026-09-25T14:00:00Z").toEpochMilli()
    private fun store() = JsonFileStore(file, UsageBook.serializer(), { UsageBook() })
    private val meter = UsageMeter(store()) { now }
    private val odds = QuotaPolicy.ODDS_API
    private val pinn = QuotaPolicy.PINNAPI
    private val keys = listOf("k1", "k2", "k3")

    private fun usage(policy: QuotaPolicy, key: String) = meter.flow.value.providers.getValue(policy.id).keys.getValue(key)

    @Test
    fun `periods are calendar months and UTC days`() {
        assertEquals(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli(), odds.periodStart(now))
        assertEquals(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), odds.nextReset(odds.periodStart(now)))
        assertEquals(Instant.parse("2026-09-26T00:00:00Z").toEpochMilli(), pinn.nextReset(pinn.periodStart(now)))
    }

    @Test
    fun `rotation always starts from key 1 and only moves on when a key is spent`() = runTest {
        assertEquals("k1", meter.pick(odds, keys, 3))
        meter.recordCall(odds, "k1", 3, serverRemaining = 497, serverUsed = 3)
        assertEquals("k1", meter.pick(odds, keys, 3))
        meter.recordCall(odds, "k1", 3, serverRemaining = 0, serverUsed = 500)
        assertEquals("k2", meter.pick(odds, keys, 3))
        assertEquals(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), usage(odds, "k1").depletedUntil)
    }

    @Test
    fun `when the month resets rotation goes back to key 1`() = runTest {
        meter.recordCall(odds, "k1", 3, serverRemaining = 0, serverUsed = 500)
        meter.recordCall(odds, "k2", 3, serverRemaining = 200, serverUsed = 300)
        assertEquals("k2", meter.pick(odds, keys, 3))
        now = Instant.parse("2026-10-01T00:05:00Z").toEpochMilli()
        assertEquals("k1", meter.pick(odds, keys, 3))
        assertEquals(0, usage(odds, "k2").used) // the new month starts every key fresh
        assertNull(usage(odds, "k2").remaining)
        assertEquals(500, usage(odds, "k2").left(odds)) // the allowance learned from the server carries over
    }

    @Test
    fun `a key that can't afford the next call is skipped, one that can is still used`() = runTest {
        meter.recordCall(odds, "k1", 3, serverRemaining = 2, serverUsed = 498)
        assertEquals("k2", meter.pick(odds, keys, 3))
        assertEquals("k1", meter.pick(odds, keys, 1))
    }

    @Test
    fun `a key refused on its first call after a reset is re-tried in 6 hours, not next month`() = runTest {
        now = Instant.parse("2026-10-01T01:00:00Z").toEpochMilli()
        meter.recordDepleted(odds, "k1", "monthly credits used up")
        assertEquals(now + 6 * 3_600_000L, usage(odds, "k1").depletedUntil)
        now += 6 * 3_600_000L + 1
        assertEquals("k1", meter.pick(odds, keys, 3))
    }

    @Test
    fun `a key refused mid-month rests until the 1st`() = runTest {
        meter.recordCall(odds, "k1", 3, serverRemaining = 10, serverUsed = 490)
        meter.recordDepleted(odds, "k1", "monthly credits used up")
        assertEquals(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), usage(odds, "k1").depletedUntil)
    }

    @Test
    fun `a key on its own billing cycle follows the server's count`() = runTest {
        meter.recordCall(odds, "k1", 3, serverRemaining = 100, serverUsed = 400)
        now += 5 * 86_400_000L
        // Mid-month the server's used count drops: this key reset on its own date.
        meter.recordCall(odds, "k1", 3, serverRemaining = 497, serverUsed = 3)
        assertEquals(now, usage(odds, "k1").periodStart)
        assertEquals(3, usage(odds, "k1").used)
    }

    @Test
    fun `pinnacle keys are counted locally per UTC day and per minute`() = runTest {
        repeat(20) { meter.recordCall(pinn, "k1", 1) }
        // 20 in one minute: key 1 waits, key 2 takes over.
        assertEquals("k2", meter.pick(pinn, keys, 1))
        now += 61_000
        assertEquals("k1", meter.pick(pinn, keys, 1))
        repeat(80) { now += 40_000; meter.recordCall(pinn, "k1", 1) }
        assertEquals(100, usage(pinn, "k1").used)
        assertEquals("k2", meter.pick(pinn, keys, 1)) // 100 today: spent until midnight UTC
        now = Instant.parse("2026-09-26T00:00:30Z").toEpochMilli()
        assertEquals("k1", meter.pick(pinn, keys, 1))
    }

    @Test
    fun `a refused key waits for the next period and says why`() = runTest {
        meter.recordRejected(odds, "k1", "HTTP 401 INVALID_KEY")
        assertEquals("k2", meter.pick(odds, keys, 1))
        assertEquals("HTTP 401 INVALID_KEY", usage(odds, "k1").lastNote)
    }

    @Test
    fun `the ledger survives a restart`() = runTest {
        meter.recordCall(odds, "k1", 3, serverRemaining = 488, serverUsed = 12)
        meter.countKeyless(QuotaPolicy.NOVIG, calls = 40, throttled = 1)
        meter.flush()
        val reopened = UsageMeter(store()) { now }
        reopened.load()
        assertEquals(488, reopened.flow.value.providers.getValue("oddsapi").keys.getValue("k1").remaining)
        assertEquals(40, reopened.flow.value.providers.getValue("novig").callsToday)
        assertEquals(1, reopened.flow.value.providers.getValue("novig").throttledToday)
    }

    @Test
    fun `keyless counters restart each UTC day`() = runTest {
        meter.countKeyless(QuotaPolicy.KALSHI, calls = 9)
        now = Instant.parse("2026-09-26T00:01:00Z").toEpochMilli()
        meter.countKeyless(QuotaPolicy.KALSHI, calls = 2)
        assertEquals(2, meter.flow.value.providers.getValue("kalshi").callsToday)
    }

    @Test
    fun `the pool rotates on refusal and names the reset when every key is spent`() = runTest {
        val pool = KeyPool(odds, { listOf("a", "b") }, meter)
        val tried = ArrayList<String>()
        val v = pool.execute(3) { key ->
            tried += key
            if (key == "a") KeyAttemptResult.Depleted("monthly credits used up") else KeyAttemptResult.Success("ok", cost = 3, remaining = 497, used = 3)
        }
        assertEquals("ok", v)
        assertEquals(listOf("a", "b"), tried)
        meter.recordCall(odds, "b", 3, serverRemaining = 0, serverUsed = 500)
        val e = assertThrows(AllKeysExhaustedException::class.java) {
            kotlinx.coroutines.runBlocking { pool.execute<String>(3) { KeyAttemptResult.Success("never") } }
        }
        assertTrue(e.message!!, e.message!!.contains("All 2 The Odds API keys are used up until Oct 1"))
    }

    @Test
    fun `a burst limit is waited out once on the same key`() = runTest {
        val pool = KeyPool(odds, { listOf("a") }, meter)
        var calls = 0
        val v = pool.execute(1) { if (calls++ == 0) KeyAttemptResult.RateLimited(1) else KeyAttemptResult.Success("ok") }
        assertEquals("ok", v)
        assertEquals(2, calls)
    }
}
