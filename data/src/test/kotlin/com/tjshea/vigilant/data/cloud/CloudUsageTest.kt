package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloudUsageTest {
    @get:Rule val tmp = TemporaryFolder()
    private val now = 1_800_000_000_000L
    private val keyA = "sgo-live-KEY-aaaaaaaaaaaaaaaa"
    private val keyB = "sgo-live-KEY-bbbbbbbbbbbbbbbb"

    private fun book() = UsageBook(
        mapOf(
            "sgo" to ProviderUsage(
                keys = mapOf(
                    keyA to KeyUsage(periodStart = QuotaPolicy.SGO.periodStart(now), used = 40, calls = 40, lastCallMs = now - 1_000, recent = listOf(now - 1_000, now - 2_000), lastNote = "429 for $keyA: slow down"),
                    keyB to KeyUsage(periodStart = QuotaPolicy.SGO.periodStart(now), used = 5, calls = 5),
                ),
                dayStart = QuotaPolicy.NOVIG.periodStart(now), callsToday = 45,
            ),
            "novig" to ProviderUsage(dayStart = QuotaPolicy.NOVIG.periodStart(now), callsToday = 900, throttledToday = 3),
        ),
    )

    @Test
    fun `the published usage never carries a key - not in a key's place, not in a note - and drops the call times`() {
        val text = CloudUsage.encode(book(), "run-9", now, listOf(keyA, keyB))
        assertFalse("key A is not in the file", text.contains(keyA))
        assertFalse("key B is not in the file", text.contains(keyB))
        assertFalse("not even a part of a key", text.contains("aaaaaaaaaaaaaaaa") || text.contains("bbbbbbbbbbbbbbbb"))
        assertTrue(text.contains(CloudUsage.keyId(keyA)))
        val f = CloudUsage.decode(text)!!
        assertEquals("run-9", f.run); assertEquals(now, f.at)
        val a = f.book.providers.getValue("sgo").keys.getValue(CloudUsage.keyId(keyA))
        assertTrue("call times are dropped", a.recent.isEmpty())
        assertEquals("the note is kept, with the key scrubbed out", "429 for ••••: slow down", a.lastNote)
        assertEquals(900, f.book.providers.getValue("novig").callsToday)
    }

    @Test
    fun `the phone gets its own keys back by their ids, and an id it has no key for is dropped`() {
        val f = CloudUsage.decode(CloudUsage.encode(book(), "r", now, listOf(keyA, keyB)))!!
        val back = CloudUsage.rekey(f.book, listOf(keyA, "another-key-1234567890"))
        assertEquals(setOf(keyA), back.providers.getValue("sgo").keys.keys)
        assertEquals(40, back.providers.getValue("sgo").keys.getValue(keyA).used)
        assertNotEquals(CloudUsage.keyId(keyA), CloudUsage.keyId(keyB))
        assertNull(CloudUsage.decode("not json"))
    }

    @Test
    fun `a new engine job starts from the last one's ledger - what a key spent today is not forgotten at a handover`() = runBlocking {
        val published = CloudUsage.decode(CloudUsage.encode(book(), "r", now, listOf(keyA, keyB)))!!
        val meter = UsageMeter(JsonFileStore(tmp.newFile("u.json"), UsageBook.serializer(), { UsageBook() }), clock = { now + 60_000 })
        meter.seed(CloudUsage.rekey(published.book, listOf(keyA, keyB)))
        val sgo = meter.flow.value.providers.getValue("sgo")
        assertEquals(40, sgo.keys.getValue(keyA).used)
        assertEquals(45, sgo.callsToday)
        assertEquals(900, meter.flow.value.providers.getValue("novig").callsToday)
        // seeding again never doubles what the ledger already holds
        meter.seed(CloudUsage.rekey(published.book, listOf(keyA, keyB)))
        assertEquals(40, meter.flow.value.providers.getValue("sgo").keys.getValue(keyA).used)
        assertEquals(45, meter.flow.value.providers.getValue("sgo").callsToday)
    }

    @Test
    fun `a ledger from yesterday is rolled to today when it seeds - a new day starts at zero`() = runBlocking {
        val meter = UsageMeter(JsonFileStore(tmp.newFile("u2.json"), UsageBook.serializer(), { UsageBook() }), clock = { now + 36 * 3_600_000L })
        meter.seed(CloudUsage.rekey(CloudUsage.decode(CloudUsage.encode(book(), "r", now, listOf(keyA)))!!.book, listOf(keyA)))
        assertEquals(0, meter.flow.value.providers.getValue("sgo").keys.getValue(keyA).used)
        assertEquals(0, meter.flow.value.providers.getValue("novig").callsToday)
    }
}
