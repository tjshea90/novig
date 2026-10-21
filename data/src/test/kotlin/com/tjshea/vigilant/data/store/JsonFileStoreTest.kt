package com.tjshea.vigilant.data.store

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonFileStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store(f: File) = JsonFileStore(f, ListSerializer(String.serializer()), { emptyList() })

    @Test
    fun `values survive a restart`() = runTest {
        val f = File(tmp.root, "bets.json")
        store(f).update { it + "a" }
        assertEquals(listOf("a"), store(f).read())
        assertTrue(!File(tmp.root, "bets.json.tmp").exists())
    }

    @Test
    fun `concurrent updates never lose a write`() = runTest {
        val f = File(tmp.root, "bets.json")
        val s = store(f)
        (1..50).map { i -> async { s.update { it + "b$i" } } }.awaitAll()
        assertEquals(50, store(f).read().size)
    }

    @Test
    fun `a corrupt file is set aside, not deleted, and the app still starts`() = runTest {
        val f = File(tmp.root, "bets.json").apply { writeText("{not json") }
        assertEquals(emptyList<String>(), store(f).read())
        assertEquals(listOf("{not json"), tmp.root.listFiles()!!.filter { it.name.startsWith("bets.json.corrupt") }.map { it.readText() })
    }

    @Test
    fun `a second corrupt file never replaces the first one set aside`() = runTest {
        val f = File(tmp.root, "bets.json").apply { writeText("{first") }
        store(f).read()
        Thread.sleep(5)
        f.writeText("{second")
        store(f).read()
        val kept = tmp.root.listFiles()!!.filter { it.name.startsWith("bets.json.corrupt") }.map { it.readText() }.toSet()
        assertEquals(setOf("{first", "{second"), kept)
    }

    // XO2: the engine's bid desk rewrote its document whole, synced, on every post, cancel and ended bid.
    @Test
    fun `a write-behind store changes memory at once, writes the file at most once per interval and on flush, and a restart reads what was flushed`() = runTest {
        val f = File(tmp.root, "desk.json")
        var now = 1_000_000L
        val s = JsonFileStore(f, ListSerializer(String.serializer()), { emptyList() }, writeBehindMs = 3_000L, clock = { now })
        s.update { it + "a" }                                   // the first write goes out (nothing was written yet)
        assertEquals(listOf("a"), store(f).read())
        val stamp = f.lastModified(); val len = f.length()
        now += 100; s.update { it + "b" }; now += 100; s.update { it + "c" }
        assertEquals("memory is current at once", listOf("a", "b", "c"), s.read())
        assertEquals("but the file has not been rewritten inside the interval", len, f.length()); assertEquals(stamp, f.lastModified())
        now += 3_000; s.update { it + "d" }                     // the interval has passed: this update writes everything so far
        assertEquals(listOf("a", "b", "c", "d"), store(f).read())
        now += 10; s.update { it + "e" }
        assertEquals("one change inside the interval waits", listOf("a", "b", "c", "d"), store(f).read())
        s.flush()
        assertEquals("flush writes what waited", listOf("a", "b", "c", "d", "e"), store(f).read())
        s.flush()                                                // nothing changed: nothing is written again
        assertEquals(listOf("a", "b", "c", "d", "e"), store(f).read())
    }

    @Test
    fun `without write-behind every update is on disk when it returns, exactly as before`() = runTest {
        val f = File(tmp.root, "bets.json")
        val s = store(f)
        s.update { it + "x" }; s.update { it + "y" }
        assertEquals(listOf("x", "y"), store(f).read())
        s.flush()
        assertEquals(listOf("x", "y"), store(f).read())
    }
}
