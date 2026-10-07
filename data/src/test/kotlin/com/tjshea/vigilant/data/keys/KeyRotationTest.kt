package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Tj, 2026-10-07: "I added parlay-api free keys to the vigilant app, but it says that my credits are used for the day and it won't use
 * parlay-api anymore today. Make it so it automatically uses each successive key when the last one is depleted ... Make sure all api keys
 * rotate when usage resets and the app rotates keys." The paid key's day share was spent, and the free keys were held back whole as "kept for
 * closing lines", so the pool stopped.
 */
class KeyRotationTest {

    private val utc = ZoneId.of("UTC")
    private var now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
    private val store = JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() })
    private val meter = UsageMeter(store, clock = { now })
    private val sept1 = Instant.parse("2026-09-01T00:00:00Z").toEpochMilli()
    private val pace = CreditPace(QuotaPolicy.PARLAY, reserve = 300, freeLimit = 1_000, zone = { utc })

    /** The ledger as the server's figures left it before this test's first call: a plan [used] of [limit] credits this month. */
    private fun plan(used: Int, limit: Int) = KeyUsage(periodStart = sept1, used = used, remaining = limit - used, limit = limit)

    private fun seed(vararg keys: Pair<String, KeyUsage>) {
        runBlocking { store.update { UsageBook(providers = mapOf("parlay" to ProviderUsage(keys = keys.toMap()))) } }
    }

    /** A fake ParlayAPI: every key's own count of credits used, answered as the real one does (used + remaining on every reply). */
    private class Server(val limits: Map<String, Int>) {
        val used = limits.keys.associateWith { 0 }.toMutableMap()
        val calls = ArrayList<String>()
        fun <T> answer(key: String, value: T, cost: Int): KeyAttemptResult<T> {
            calls += key
            used[key] = used.getValue(key) + cost
            return KeyAttemptResult.Success(value, cost = cost, remaining = limits.getValue(key) - used.getValue(key), used = used.getValue(key))
        }
    }

    @Test
    fun `a paid key's day share spent hands scans to each free key in turn, then the scan waits`() = runBlocking<Unit> {
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("paid", "free1", "free2") }, meter)
        // The Starter key is 7,000 used by the 10th: ahead of the pace (6,566 allowed), so nothing more today. The free keys are fresh.
        seed("paid" to plan(7_000, 20_000), "free1" to plan(0, 1_000), "free2" to plan(0, 1_000))
        val server = Server(mapOf("paid" to 20_000, "free1" to 1_000, "free2" to 1_000)).also { it.used["paid"] = 7_000 }

        // 100-credit calls (a big scan's): a free key serves while it can pay and keep its last 100, so 9 calls each (1,000 down to 100).
        repeat(18) { pool.execute(cost = 100, reserve = 300, pace = pace) { k -> server.answer(k, Unit, 100) } }
        assertEquals(List(9) { "free1" } + List(9) { "free2" }, server.calls)
        assertEquals(7_000, server.used["paid"])

        // Every key is now held back (paid: today's share; free: the closes' last 100), and the pool says so instead of failing.
        val e = assertThrows(CreditsHeldBackException::class.java) {
            runBlocking { pool.execute(cost = 100, reserve = 300, pace = pace) { k -> server.answer(k, Unit, 100) } }
        }
        assertEquals("ParlayAPI has spent today's share of its credits: back tomorrow (unused days carry over). The last 100 credits on a free key are kept for closing lines.", e.message)
        // The closes (no reserve, no pace) still reach every key, paid first.
        var closeKey = ""
        pool.execute(cost = 5) { k -> closeKey = k; server.answer(k, Unit, 5) }
        assertEquals("paid", closeKey)
    }

    @Test
    fun `scans are back on the paid key the next day, ahead of the free keys`() = runBlocking<Unit> {
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("paid", "free1") }, meter)
        seed("paid" to plan(7_000, 20_000), "free1" to plan(0, 1_000))
        val server = Server(mapOf("paid" to 20_000, "free1" to 1_000)).also { it.used["paid"] = 7_000 }
        pool.execute(cost = 100, reserve = 300, pace = pace) { k -> server.answer(k, Unit, 100) }
        assertEquals(listOf("free1"), server.calls)
        // The 11th at noon: another day's share (19,700 / 30) is open.
        now += 24 * 3_600_000L
        pool.execute(cost = 100, reserve = 300, pace = pace) { k -> server.answer(k, Unit, 100) }
        assertEquals(listOf("free1", "paid"), server.calls)
    }

    @Test
    fun `Tj's key order decides which key goes first, a free key listed first is spent first`() = runBlocking<Unit> {
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("free1", "paid") }, meter)
        seed("free1" to plan(0, 1_000), "paid" to plan(1_000, 20_000))
        val server = Server(mapOf("free1" to 1_000, "paid" to 20_000)).also { it.used["paid"] = 1_000 }
        repeat(10) { pool.execute(cost = 100, reserve = 300, pace = pace) { k -> server.answer(k, Unit, 100) } }
        assertEquals(List(9) { "free1" } + "paid", server.calls)
    }

    @Test
    fun `a key the server says is spent hands the same call to the next key, and stays out until its reset`() = runBlocking<Unit> {
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("a", "b", "c") }, meter)
        val tried = ArrayList<String>()
        val r = pool.execute(cost = 3, reserve = 300, pace = pace) { k ->
            tried += k
            if (k == "c") KeyAttemptResult.Success("done", cost = 3, remaining = 997, used = 3) else KeyAttemptResult.Depleted("monthly credits used up")
        }
        assertEquals("done", r)
        assertEquals(listOf("a", "b", "c"), tried)
        // The next call doesn't spend a refused request on the keys already known to be spent.
        tried.clear()
        pool.execute(cost = 3, reserve = 300, pace = pace) { k -> tried += k; KeyAttemptResult.Success("again", cost = 3, remaining = 994, used = 6) }
        assertEquals(listOf("c"), tried)
    }

    @Test
    fun `every key of every provider is used again from key 1 when its period resets`() = runBlocking<Unit> {
        for (policy in QuotaPolicy.ALL.filter { it.keyed }) {
            val m = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })
            val pool = KeyPool(policy, { listOf("k1", "k2", "k3") }, m)
            // Each key answers once, then the server refuses it as spent.
            for (k in listOf("k1", "k2", "k3")) {
                m.recordCall(policy, k, cost = 1)
                m.recordDepleted(policy, k, "used up")
            }
            assertThrows("${policy.id}: all spent", AllKeysExhaustedException::class.java) {
                runBlocking { pool.execute(cost = 1) { KeyAttemptResult.Success(Unit) } }
            }
            // Past the period's end every key is open again, and the first in Tj's order takes the call.
            now = policy.nextReset(policy.periodStart(now)) + 1_000
            var used = ""
            pool.execute(cost = 1) { k -> used = k; KeyAttemptResult.Success(Unit, cost = 1) }
            assertEquals("${policy.id}: key 1 first after the reset", "k1", used)
            // And the rest follow as each is spent, as before the reset.
            m.recordDepleted(policy, "k1", "used up")
            var next = ""
            pool.execute(cost = 1) { k -> next = k; KeyAttemptResult.Success(Unit, cost = 1) }
            assertEquals("${policy.id}: key 2 once key 1 is spent again", "k2", next)
            now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        }
    }

    @Test
    fun `a key on its own billing cycle comes back at the reset time the provider gave, not the calendar's`() = runBlocking<Unit> {
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("a", "b") }, meter)
        val reset = Instant.parse("2026-09-20T03:00:00Z").toEpochMilli()
        // Key a: a plan billed on the 20th, spent. Key b: a free key, spent too.
        meter.recordCall(QuotaPolicy.PARLAY, "a", cost = 5, serverRemaining = 0, serverUsed = 20_000, resetAtMs = reset)
        meter.recordCall(QuotaPolicy.PARLAY, "b", cost = 5, serverRemaining = 0, serverUsed = 1_000)
        assertThrows(AllKeysExhaustedException::class.java) { runBlocking { pool.execute(cost = 5) { KeyAttemptResult.Success(Unit) } } }
        now = reset + 60_000
        var used = ""
        pool.execute(cost = 5) { k -> used = k; KeyAttemptResult.Success(Unit, cost = 5, remaining = 19_995, used = 5) }
        assertEquals("a", used)
        // b is still spent until the calendar month ends.
        meter.recordDepleted(QuotaPolicy.PARLAY, "a", "used up")
        assertThrows(AllKeysExhaustedException::class.java) { runBlocking { pool.execute(cost = 5) { KeyAttemptResult.Success(Unit) } } }
        now = Instant.parse("2026-10-01T00:00:01Z").toEpochMilli()
        pool.execute(cost = 5) { k -> used = k; KeyAttemptResult.Success(Unit, cost = 5, remaining = 19_990, used = 10) }
        assertEquals("b", used)
    }
}
