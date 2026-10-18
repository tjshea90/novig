package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.reference.PinnWireAccount
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** TCL119 (Tj, 2026-10-18): a PinnWire key is spent only when PinnWire says so, never from the app's own request count. */
class PinnWireTruthTest {
    private val file = File.createTempFile("usage", ".json").also { it.delete() }
    private var now = Instant.parse("2026-10-18T14:00:00Z").toEpochMilli()
    private val meter = UsageMeter(JsonFileStore(file, UsageBook.serializer(), { UsageBook() })) { now }
    private val wire = QuotaPolicy.PINNWIRE
    private fun usage() = meter.flow.value.providers.getValue(wire.id).keys.getValue("k1")

    @Test
    fun `150 of our own calls do not rest a key`() = runTest {
        repeat(150) { meter.recordCall(wire, "k1", 1) }
        assertNull("no figure from PinnWire: unknown, not zero", usage().left(wire))
        assertNull(usage().depletedUntil)
        assertEquals("k1", meter.pick(wire, listOf("k1"), 1))
    }

    @Test
    fun `PinnWire's own figure is kept and only its word spends the key`() = runTest {
        meter.recordBalance(wire, "k1", remaining = 12, limit = 100, used = 88)
        meter.recordCall(wire, "k1", 1)
        assertEquals(11, usage().left(wire))
        repeat(40) { meter.recordCall(wire, "k1", 1) }
        assertTrue("our count never takes it to zero", (usage().left(wire) ?: 0) >= 1)
        meter.recordBalance(wire, "k1", remaining = 0, limit = 100, used = 100, exhausted = true)
        assertTrue(usage().depletedUntil != null)
        assertFalse(wire.usable(wire.roll(usage(), now), 1, now))
    }

    @Test
    fun `a 429 for the day rests the key`() = runTest {
        meter.recordDepleted(wire, "k1", "daily limit reached", 3_600_000L)
        assertTrue(usage().depletedUntil != null)
    }

    @Test
    fun `account read parses remaining, or limit minus used`() {
        val a = PinnWireAccount.parse(Json.parseToJsonElement("""{"email":"x@y.z","plan":"trial","limits":{"rest_per_minute":20,"rest_per_day":100},"usage":{"today":37}}""").jsonObject, 1L)
        assertEquals(63, a.remaining); assertEquals(100, a.limit); assertEquals(37, a.used)
        val b = PinnWireAccount.parse(Json.parseToJsonElement("""{"usage":{"requests_remaining_today":0,"daily_limit":100}}""").jsonObject, 1L)
        assertEquals(0, b.remaining)
        val c = PinnWireAccount.parse(Json.parseToJsonElement("""{"email":"x@y.z","plan":"trial"}""").jsonObject, 1L)
        assertNull(c.remaining)
        assertFalse("no text in the figures", c.figures.isNotEmpty())
    }
}
