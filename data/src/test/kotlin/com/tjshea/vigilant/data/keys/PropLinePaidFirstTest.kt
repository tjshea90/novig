package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant

/** Tj, 2026-10-16 (TCL56): the PropLine key whose own plan is above the free 1,000 a day is found and used first, wherever he typed it. */
class PropLinePaidFirstTest {
    private val now = Instant.parse("2026-10-16T18:00:00Z").toEpochMilli()
    private val store = JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() })
    private val meter = UsageMeter(store, clock = { now })

    @Test
    fun theProKeyGoesFirstOnceItsPlanIsKnown() = runBlocking<Unit> {
        meter.recordBalance(QuotaPolicy.PROPLINE, "free", remaining = 900, limit = 1_000, used = 100)
        meter.recordBalance(QuotaPolicy.PROPLINE, "pro", remaining = 24_990, limit = 25_000, used = 10)
        val pool = KeyPool(QuotaPolicy.PROPLINE, { listOf("free", "pro") }, meter)
        val tried = ArrayList<String>()
        repeat(3) { pool.execute(cost = 1) { k -> tried += k; KeyAttemptResult.Success(Unit) } }
        assertEquals(listOf("pro", "pro", "pro"), tried)
    }

    @Test
    fun withNoPlanKnownTheTypedOrderStands() = runBlocking<Unit> {
        val pool = KeyPool(QuotaPolicy.PROPLINE, { listOf("a", "b") }, meter)
        var first = ""
        pool.execute(cost = 1) { k -> first = k; KeyAttemptResult.Success(Unit) }
        assertEquals("a", first)
    }

    @Test
    fun aSpentProKeyHandsOverToTheFreeOne() = runBlocking<Unit> {
        meter.recordBalance(QuotaPolicy.PROPLINE, "free", remaining = 900, limit = 1_000, used = 100)
        meter.recordBalance(QuotaPolicy.PROPLINE, "pro", remaining = 0, limit = 25_000, used = 25_000, exhausted = true)
        val pool = KeyPool(QuotaPolicy.PROPLINE, { listOf("pro", "free") }, meter)
        var k0 = ""
        pool.execute(cost = 1) { k -> k0 = k; KeyAttemptResult.Success(Unit) }
        assertEquals("free", k0)
    }

    @Test
    fun tiersFollowTheDailyLimit() {
        val t = com.tjshea.vigilant.data.reference.PropLineAccount
        assertEquals("free", t.tierOf(1_000)); assertEquals("hobby", t.tierOf(5_000)); assertEquals("pro", t.tierOf(25_000))
        assertEquals("streaming-lite", t.tierOf(250_000)); assertEquals("?", t.tierOf(null))
    }

    @Test
    fun aProKeyReadsEveryGameWhileTodaysShareHoldsAndTheFreeKeyNever() = runBlocking<Unit> {
        val noon = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli()
        val m = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { noon })
        val client = com.tjshea.vigilant.data.reference.PropLineClient(
            okhttp3.OkHttpClient(), KeyPool(QuotaPolicy.PROPLINE, { listOf("pro", "free") }, m), kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, clock = { noon }, meter = m,
        )
        assertEquals(null, client.plan())
        m.recordBalance(QuotaPolicy.PROPLINE, "free", remaining = 900, limit = 1_000, used = 100)
        assertEquals(false, client.roomForEveryGame(noon))                    // a free key keeps the setting's cap
        m.recordBalance(QuotaPolicy.PROPLINE, "pro", remaining = 20_000, limit = 25_000, used = 5_000)
        assertEquals("pro", client.plan()?.tier)
        assertEquals(true, client.roomForEveryGame(noon))                     // noon: 12,500 + 10% = 15,000 allowed, 5,000 used
        m.recordBalance(QuotaPolicy.PROPLINE, "pro", remaining = 5_000, limit = 25_000, used = 20_000)
        assertEquals(false, client.roomForEveryGame(noon))                    // over the day's share: back to the setting's cap
    }

    @Test
    fun theDayIsSpentOnlyAcrossTheHoursTheEngineWorks() {
        val day = Instant.parse("2026-10-16T00:00:00Z").toEpochMilli()
        fun f(h: Int) = com.tjshea.vigilant.data.reference.PropLineClient.activeDayFraction(day + h * 3_600_000L)
        // October 16 is daylight time: the engine works 12:00Z to 05:00Z next day, so the UTC day holds 00:00-05:00 and 12:00-24:00 = 17 hours of work.
        assertEquals(0.0, f(0), 1e-9)
        assertEquals(5.0 / 17, f(5), 1e-9)
        assertEquals("nothing is read from 05:00 to 12:00", f(5), f(12), 1e-9)
        assertEquals(11.0 / 17, f(18), 1e-9)
        assertEquals("the last millisecond of the day", 1.0, com.tjshea.vigilant.data.reference.PropLineClient.activeDayFraction(day + 24 * 3_600_000L - 1), 1e-6)
        // Winter (EST): 13:00Z to 06:00Z
        val w = Instant.parse("2026-12-10T00:00:00Z").toEpochMilli()
        assertEquals(6.0 / 17, com.tjshea.vigilant.data.reference.PropLineClient.activeDayFraction(w + 6 * 3_600_000L), 1e-9)
    }

    @Test
    fun farGamesKeepTheirPropsLongerThanNearOnes() {
        val now = Instant.parse("2026-10-16T18:00:00Z").toEpochMilli()
        assertEquals(2 * 60_000L, com.tjshea.vigilant.data.reference.PropLineClient.propsReuseMs(now + 2 * 3_600_000L, now))
        assertEquals(5 * 60_000L, com.tjshea.vigilant.data.reference.PropLineClient.propsReuseMs(now + 5 * 3_600_000L, now))
    }

    @Test
    fun propsThrottleBeforeTheEvSweepDoes() = runBlocking<Unit> {
        val t = Instant.parse("2026-10-16T18:00:00Z").toEpochMilli()      // 11 of 17 working hours gone: pace 16,176 of 25,000
        val m = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { t })
        val client = com.tjshea.vigilant.data.reference.PropLineClient(okhttp3.OkHttpClient(), KeyPool(QuotaPolicy.PROPLINE, { listOf("pro") }, m), kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, clock = { t }, meter = m)
        m.recordBalance(QuotaPolicy.PROPLINE, "pro", remaining = 25_000 - 12_000, limit = 25_000, used = 12_000)      // props line: 16,176 * .95 + 1,250 = 16,617
        assertEquals(true, client.roomForEveryGame(t)); assertEquals(true, client.roomForEv(t))
        m.recordBalance(QuotaPolicy.PROPLINE, "pro", remaining = 25_000 - 17_000, limit = 25_000, used = 17_000)
        assertEquals("props over their share: back to the setting's cap", false, client.roomForEveryGame(t)); assertEquals("the sweep still has room (16,176 + 1,250 = 17,426)", true, client.roomForEv(t))
        m.recordBalance(QuotaPolicy.PROPLINE, "pro", remaining = 25_000 - 18_000, limit = 25_000, used = 18_000)
        assertEquals(false, client.roomForEv(t))
    }
}
