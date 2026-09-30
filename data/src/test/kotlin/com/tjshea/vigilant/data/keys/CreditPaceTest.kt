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
    fun `a free key is kept for the closing lines, and a key not yet heard from only keeps the reserve`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        assertEquals(CreditPace.FREE_ONLY, pace.floor(KeyUsage(periodStart = sept1, used = 10, remaining = 990, limit = 1_000), now))
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
    fun `a free key says why scans leave it alone`() = runBlocking<Unit> {
        val pool = KeyPool(QuotaPolicy.PARLAY, { listOf("f") }, meter())
        pool.execute(cost = 1) { KeyAttemptResult.Success(Unit, cost = 1, remaining = 950, used = 50) }
        val e = assertThrows(CreditsHeldBackException::class.java) {
            runBlocking { pool.execute(cost = 5, reserve = 300, pace = pace) { KeyAttemptResult.Success(Unit) } }
        }
        assertEquals("ParlayAPI is on its free plan: its credits are kept for closing lines (scans use a paid plan's).", e.message)
    }

    @Test
    fun `the meter says what scans can still spend today, or that a free key is for closes`() {
        val now = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        val book = ProviderUsage(keys = mapOf("a" to starter(5_000), "f" to KeyUsage(periodStart = sept1, used = 10, remaining = 990, limit = 1_000)))
        val both = UsageViews.build(QuotaPolicy.PARLAY, listOf("a", "f"), book, now, pace)
        assertEquals(15_000 - (20_000 - 19_700 * 10 / 30), both.scanShareToday)
        assertEquals(false, both.scansFreeOnly)
        val free = UsageViews.build(QuotaPolicy.PARLAY, listOf("f"), book, now, pace)
        assertEquals(true, free.scansFreeOnly)
        assertEquals(null, free.scanShareToday)
        // Not heard from yet: no figure to show.
        assertEquals(null, UsageViews.build(QuotaPolicy.PARLAY, listOf("new"), book, now, pace).scanShareToday)
        assertEquals(null, UsageViews.build(QuotaPolicy.ODDS_API, listOf("a"), book, now).scanShareToday)
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
    fun `a key bought late in the month is paced from its first day, not the 1st`() = runBlocking<Unit> {
        val m = meterAt { firstEvening }
        m.recordCall(QuotaPolicy.PARLAY, "k", 5, serverRemaining = 19_995, serverUsed = 5)
        val u = m.flow.value.providers.getValue("parlay").keys.getValue("k")
        // Its first day: a whole day's share (not 29 days' worth "left unspent" before it existed, not an hour's).
        val spendable = newYork.spendableToday(u, firstEvening)
        assertTrue("$spendable", spendable in (19_700 / 31 - 10)..(19_700 / 30))
    }
}
