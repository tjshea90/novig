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
 * most, unused days carry over, the last 300 are kept for closing lines; a free key (1,000 credits) serves scans after the keys before it (Tj,
 * 2026-10-07), down to its last 100.
 */
class CreditPaceTest {

    private val utc = ZoneId.of("UTC")
    private val sept1 = Instant.parse("2026-09-01T00:00:00Z").toEpochMilli()
    private val pace = CreditPace(QuotaPolicy.PARLAY, reserve = 300, freeLimit = 1_000, zone = { utc })

    private fun starter(used: Int) = KeyUsage(periodStart = sept1, used = used, remaining = 20_000 - used, limit = 20_000)

    @Test
    fun `a Starter key keeps the reserve plus every later day's share`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        // Ten of September's 30 days' shares allowed through today (the rest, and the reserve, held): 20,000 - 19,700 x 10/30.
        assertEquals(20_000 - 19_700 * 10 / 30, pace.floor(starter(5_000), now))
        // Several days' share unspent (5,000 used by the 10th, 6,566 allowed through today): all of it can go today.
        assertEquals(15_000 - (20_000 - 19_700 * 10 / 30), pace.spendableToday(starter(5_000), now))
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
        assertEquals(20_000 - (19_700L * (endOfDay - sept1) / (reset - sept1)).toInt(), eastern.floor(starter(5_000), now))
    }

    @Test
    fun `a free key keeps only its closing-lines reserve, and a key not yet heard from keeps the pool's`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        assertEquals(CreditPace.FREE_RESERVE, pace.floor(KeyUsage(periodStart = sept1, used = 10, remaining = 990, limit = 1_000), now))
        assertEquals(300, pace.floor(KeyUsage(), now))
    }

    private fun meter() = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { Instant.parse("2026-09-10T12:00:00Z").toEpochMilli() })

    @Test
    fun `a paced call past today's share waits without failing, an unpaced one still goes`() = runBlocking<Unit> {
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
    fun `a free key alone serves scans down to its reserve, then says what it is kept for`() = runBlocking<Unit> {
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("f") }, meter())
        pool.execute(cost = 1) { KeyAttemptResult.Success(Unit, cost = 1, remaining = 950, used = 50) }
        var scans = 0
        repeat(3) { pool.execute(cost = 5, reserve = 300, pace = pace) { scans++; KeyAttemptResult.Success(Unit, cost = 5, remaining = 945 - 5 * scans, used = 55 + 5 * scans) } }
        assertEquals(3, scans)
        // 930 left: the last 100 are the closes', so a scan goes on until 105 is left, then waits.
        pool.execute(cost = 5, reserve = 300, pace = pace) { scans++; KeyAttemptResult.Success(Unit, cost = 5, remaining = 102, used = 898) }
        val e = assertThrows(CreditsHeldBackException::class.java) {
            runBlocking { pool.execute(cost = 5, reserve = 300, pace = pace) { KeyAttemptResult.Success(Unit) } }
        }
        assertEquals("Your ParlayAPI key is down to the last 100 credits, kept for closing lines: back at the reset.", e.message)
        // The closes (no reserve, no pace) still read it.
        var closes = false
        pool.execute(cost = 5) { closes = true; KeyAttemptResult.Success(Unit, cost = 5, remaining = 97, used = 903) }
        assertTrue(closes)
    }

    @Test
    fun `the meter says what scans can still spend today, paid and free keys together`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        val free = KeyUsage(periodStart = sept1, used = 10, remaining = 990, limit = 1_000)
        val book = ProviderUsage(keys = mapOf("a" to starter(5_000), "f" to free))
        val both = UsageViews.build(QuotaPolicy.PARLAY, listOf("a", "f"), book, now, pace)
        assertEquals(15_000 - (20_000 - 19_700 * 10 / 30) + (990 - CreditPace.FREE_RESERVE), both.scanShareToday)
        val onlyFree = UsageViews.build(QuotaPolicy.PARLAY, listOf("f"), book, now, pace)
        assertEquals(990 - CreditPace.FREE_RESERVE, onlyFree.scanShareToday)
        // Not heard from yet: no figure to show.
        assertEquals(null, UsageViews.build(QuotaPolicy.PARLAY, listOf("new"), book, now, pace).scanShareToday)
        assertEquals(null, UsageViews.build(QuotaPolicy.ODDS_API, listOf("a"), book, now).scanShareToday)
    }

    @Test
    fun `the meter marks the key a scan will use, not a paid key resting for the day`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        val free = KeyUsage(periodStart = sept1, used = 10, remaining = 990, limit = 1_000)
        val book = ProviderUsage(keys = mapOf("paid" to starter(7_000), "free" to free))
        val v = UsageViews.build(QuotaPolicy.PARLAY, listOf("paid", "free"), book, now, pace)
        assertEquals(listOf(KeyState.STANDBY, KeyState.ACTIVE), v.keys.map { it.state })
        // With nothing paced the first key with credits is the one in use, as before.
        assertEquals(listOf(KeyState.ACTIVE, KeyState.STANDBY), UsageViews.build(QuotaPolicy.PARLAY, listOf("paid", "free"), book, now).keys.map { it.state })
    }

    @Test
    fun `background auto-scans leave half of today's share for Tj's own scans`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        val background = CreditPace(QuotaPolicy.PARLAY, reserve = 300, freeLimit = 1_000, zone = { utc }, keepOfDay = 0.5)
        val day = 19_700 / 30
        // On pace (6,567 used by the end of the 10th's share): Tj's scans can spend today's share, a background cycle only half of it.
        val onPace = starter(20_000 - (300 + 19_700 * 21 / 30))
        assertEquals(day, pace.spendableToday(onPace, now), 1)
        assertEquals(day / 2, background.spendableToday(onPace, now), 1)
        // Days left unspent are open to both.
        assertTrue(background.spendableToday(starter(1_000), now) > 5 * day)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) = assertTrue("$expected vs $actual", kotlin.math.abs(expected - actual) <= tolerance)

    // ---- Tj's diagnostics, 2026-09-30 03:06Z: "ParlayAPI has spent today's share" with 19,974 of 20,000 left ----------------------

    /** Its first evening: 11 pm in New York on Sep 29 (03:00Z on the 30th), the key bought that day. */
    private val firstEvening = Instant.parse("2026-09-30T03:00:00Z").toEpochMilli()
    private val newYork = CreditPace(QuotaPolicy.PARLAY, reserve = 300, freeLimit = 1_000, zone = { ZoneId.of("America/New_York") })

    private fun meterAt(clock: () -> Long) = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = clock)

    @Test
    fun `answers that arrive out of order (props and lines at once) never restart the key's month`() = runBlocking<Unit> {
        var now = firstEvening
        val m = meterAt { now }
        suspend fun answer(used: Int, cost: Int) = m.recordCall(QuotaPolicy.PARLAY, "k", cost, serverRemaining = 20_000 - used, serverUsed = used)
        // A props page (3 credits) was charged at used = 8 but its big reply lands after two game-line calls already recorded 18.
        answer(5, 5)
        now += 800; answer(13, 5)
        now += 800; answer(18, 5)
        now += 900; answer(8, 3)
        val u = m.flow.value.providers.getValue("parlay").keys.getValue("k")
        assertEquals(18, u.used)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli(), u.periodStart)
        // And its scans may go on: a day's share at least.
        assertTrue("${newYork.spendableToday(u, now)}", newYork.spendableToday(u, now) >= 19_700 / 30 - 18)
    }

    @Test
    fun `a real new billing cycle is still followed`() = runBlocking<Unit> {
        var now = Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()
        val m = meterAt { now }
        m.recordCall(QuotaPolicy.PARLAY, "k", 5, serverRemaining = 4_000, serverUsed = 16_000)
        now += 6 * 3_600_000L
        m.recordCall(QuotaPolicy.PARLAY, "k", 5, serverRemaining = 19_995, serverUsed = 5)
        val u = m.flow.value.providers.getValue("parlay").keys.getValue("k")
        assertEquals(5, u.used)
        assertEquals(now, u.periodStart)
    }

    @Test
    fun `a key bought late in the month spreads the month's credits over the days left in it`() = runBlocking<Unit> {
        // Bought on the 30th (Tj's plan, 2026-09-30): ParlayAPI's credits reset at the end of the calendar month (/v1/usage's period_end),
        // so that last day may spend them all, less the reserve (not a thirtieth of them, nor 29 days' worth "left unspent" before it).
        val m = meterAt { firstEvening }
        m.recordCall(QuotaPolicy.PARLAY, "k", 5, serverRemaining = 19_995, serverUsed = 5)
        val u = m.flow.value.providers.getValue("parlay").keys.getValue("k")
        assertEquals(19_995 - 300, newYork.spendableToday(u, firstEvening))
        // Bought on the 15th: the month's credits over the 16 days left, a day's share each (today's runs to midnight New York time).
        val mid = Instant.parse("2026-09-15T16:00:00Z").toEpochMilli()
        val m2 = meterAt { mid }
        m2.recordCall(QuotaPolicy.PARLAY, "k", 5, serverRemaining = 19_995, serverUsed = 5)
        val u2 = m2.flow.value.providers.getValue("parlay").keys.getValue("k")
        val share = newYork.spendableToday(u2, mid)
        assertTrue("$share", share in (19_700 / 16 - 10)..(19_700 / 15))
        // A key seen since before the month began: paced over the whole month, as before (on the 10th, through the end of Tj's day,
        // 04:00 UTC the 11th: 10 days 4 hours of the month's 30 days)...
        val tenth = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        assertEquals(20_000 - (19_700L * (10 * 24 + 4) / (30 * 24)).toInt(), newYork.floor(u2.copy(firstSeenMs = sept1 - 1), tenth))
        // ...and on the month's last evening only the reserve is kept.
        assertEquals(300, newYork.floor(u2.copy(firstSeenMs = sept1 - 1), Instant.parse("2026-09-30T23:00:00Z").toEpochMilli()))
    }
}
