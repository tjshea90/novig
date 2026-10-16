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
}
