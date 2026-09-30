package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Tj's ParlayAPI Starter plan (2026-09-30: 20,000 credits a month, "take full advantage of the paid API"): scans spend a day's share at
 * most, unused days carry over, the last 300 are kept for closing lines, and a free key (1,000 credits) is kept for the closes alone.
 */
class CreditPaceTest {

    private val utc = ZoneId.of("UTC")
    private val sept1 = Instant.parse("2026-09-01T00:00:00Z").toEpochMilli()
    private val pace = CreditPace(QuotaPolicy.PARLAY, reserve = 300, freeLimit = 1_000, zone = { utc })

    private fun starter(used: Int) = KeyUsage(periodStart = sept1, used = used, remaining = 20_000 - used, limit = 20_000)

    @Test
    fun `a Starter key keeps the reserve plus every later day's share`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        // 20 of September's 30 days come after today: 300 + 19,700 x 20/30.
        assertEquals(300 + 19_700 * 20 / 30, pace.floor(starter(5_000), now))
        // Nine days' share unspent (5,000 used by the 10th, 6,567 allowed through today): all of it can go today.
        assertEquals(15_000 - (300 + 19_700 * 20 / 30), pace.spendableToday(starter(5_000), now))
        // Ahead of the pace: nothing more today.
        assertEquals(0, pace.spendableToday(starter(7_000), now))
        // The last day of the cycle: only the reserve is held.
        assertEquals(300, pace.floor(starter(19_000), Instant.parse("2026-09-30T20:00:00Z").toEpochMilli()))
    }

    @Test
    fun `the day is Tj's own, not UTC's`() {
        val eastern = CreditPace(QuotaPolicy.PARLAY, reserve = 300, freeLimit = 1_000, zone = { ZoneId.of("America/New_York") })
        // 9 pm in New York on the 10th is already the 11th in UTC: New York's day still ends at 04:00Z on the 11th.
        val now = Instant.parse("2026-09-11T01:00:00Z").toEpochMilli()
        val endOfDay = Instant.parse("2026-09-11T04:00:00Z").toEpochMilli()
        val reset = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        assertEquals(300 + (19_700L * (reset - endOfDay) / (reset - sept1)).toInt(), eastern.floor(starter(5_000), now))
    }

    @Test
    fun `a free key is kept for the closing lines, and a key not yet heard from only keeps the reserve`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        assertEquals(CreditPace.FREE_ONLY, pace.floor(KeyUsage(periodStart = sept1, used = 10, remaining = 990, limit = 1_000), now))
        assertEquals(300, pace.floor(KeyUsage(), now))
    }

    private fun meter() = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { Instant.parse("2026-09-10T12:00:00Z").toEpochMilli() })

    @Test
    fun `a paced call past today's share waits without failing, an unpaced one still goes`() = runBlocking {
        val m = meter()
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("k") }, m)
        // The server says: 7,000 used of 20,000 by the 10th, ahead of the pace.
        pool.execute(cost = 1) { KeyAttemptResult.Success(Unit, cost = 1, remaining = 13_000, used = 7_000) }
        val e = assertThrows(CreditsHeldBackException::class.java) {
            runBlocking { pool.execute(cost = 5, reserve = 300, pace = pace) { KeyAttemptResult.Success(Unit) } }
        }
        assertTrue(e.message!!, e.message!!.contains("today's share"))
        var closes = false
        pool.execute(cost = 5) { closes = true; KeyAttemptResult.Success(Unit, cost = 5, remaining = 12_995, used = 7_005) }
        assertTrue(closes)
        // A spent key is still a spent key, not a held-back one.
        val spent = KeyPool(QuotaPolicy.PARLAY, { listOf("s") }, m)
        spent.execute(cost = 1) { KeyAttemptResult.Success(Unit, cost = 1, remaining = 0, used = 20_000) }
        assertThrows(AllKeysExhaustedException::class.java) {
            runBlocking { spent.execute(cost = 5, reserve = 300, pace = pace) { KeyAttemptResult.Success(Unit) } }
        }
    }

    @Test
    fun `a free key says why scans leave it alone`() = runBlocking {
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("f") }, meter())
        pool.execute(cost = 1) { KeyAttemptResult.Success(Unit, cost = 1, remaining = 950, used = 50) }
        val e = assertThrows(CreditsHeldBackException::class.java) {
            runBlocking { pool.execute(cost = 5, reserve = 300, pace = pace) { KeyAttemptResult.Success(Unit) } }
        }
        assertEquals("ParlayAPI is on its free plan: its credits are kept for closing lines (scans use a paid plan's).", e.message)
    }
}
