package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tj, 2026-10-02: "log all types of events, code, failures". The flight recorder: bounded, kept across restarts, one line for a repeat, errors with where in the code
 * they came from, and never a key.
 */
class EventLogTest {

    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_800_000_000_000L
    private fun store() = JsonFileStore(File(tmp.root, "events.json"), EventBook.serializer(), { EventBook() })
    private fun log(keep: Int = EventLog.KEEP) = EventLog(store(), { now }, keep)

    @Test
    fun `an event keeps its time, area, level, text and timing, oldest first`() {
        val l = log()
        l.info("SCAN", "scan finished", ms = 41_000)
        now += 1_000
        l.warn("CNO", "asked to wait")
        now += 1_000
        l.record("AUTOBET", Level.ERROR, "stopped", where = "AutoBettor.run(AutoBettor.kt:150)")
        val e = l.events()
        assertEquals(listOf("SCAN", "CNO", "AUTOBET"), e.map { it.cat })
        assertEquals(listOf(Level.INFO, Level.WARN, Level.ERROR), e.map { it.level })
        assertEquals(41_000L, e[0].ms)
        assertEquals("AutoBettor.run(AutoBettor.kt:150)", e[2].where)
        assertTrue(e[0].atMs < e[1].atMs && e[1].atMs < e[2].atMs)
    }

    @Test
    fun `the same thing twice in a minute is one event with a count, but another thing or a later time is a new one`() {
        val l = log()
        l.warn("NET", "host failed: timeout")
        now += 10_000
        l.warn("NET", "host failed: timeout")
        now += 10_000
        l.warn("NET", "host failed: timeout")
        assertEquals(1, l.events().size)
        assertEquals(3, l.events().single().n)
        assertEquals(now, l.events().single().lastMs)
        l.warn("NET", "other failed: timeout")
        assertEquals(2, l.events().size)
        // A minute after the last one: a new event.
        now += EventLog.MERGE_MS + 1
        l.warn("NET", "other failed: timeout")
        assertEquals(3, l.events().size)
        // A different level isn't a repeat.
        l.record("NET", Level.ERROR, "other failed: timeout")
        assertEquals(4, l.events().size)
    }

    @Test
    fun `only the newest events are kept`() {
        val l = log(keep = 5)
        repeat(12) { i -> now += 1; l.info("X", "event $i") }
        assertEquals((7..11).map { "event $it" }, l.events().map { it.msg })
    }

    @Test
    fun `a message is masked and short, so a key in an exception's text never reaches the file`() {
        val l = log()
        l.error("NET", "call failed", IllegalStateException("bad key abcdefghijklmnopqrstuvwxyz0123456789SECRET9"))
        l.info("APP", "x".repeat(900))
        val e = l.events()
        assertFalse(e[0].msg, e[0].msg.contains("abcdefghijklmnopqrstuvwxyz0123456789SECRET9"))
        assertTrue(e[0].msg, e[0].msg.contains("…RET9"))
        assertTrue(e[1].msg.length <= EventLog.MAX_MSG)
        // A blank message isn't an event.
        l.info("APP", "   ")
        assertEquals(2, l.events().size)
    }

    @Test
    fun `an error says where in the app's code it was thrown, the app's own frames first`() {
        fun inner(): Nothing = throw IllegalArgumentException("boom")
        val t = try { inner() } catch (e: IllegalArgumentException) { RuntimeException("outer", e) }
        val where = EventLog.whereOf(t)
        assertTrue(where, where.contains("EventLogTest."))
        assertTrue(where, where.contains("EventLogTest.kt:"))
        assertTrue("at most three frames: $where", where.split(" < ").size <= 3)
        // A throwable with no frames of the app's: the first frames at all.
        val foreign = Throwable("x").apply { stackTrace = arrayOf(StackTraceElement("okhttp3.Foo", "bar", "Foo.kt", 7)) }
        assertEquals("okhttp3.Foo.bar(Foo.kt:7)", EventLog.whereOf(foreign))
        val l = log()
        l.error("CYCLE", "CNO read failed", t)
        assertTrue(l.events().single().msg.contains("(RuntimeException: outer)"))
        assertTrue(l.events().single().where!!.contains("EventLogTest"))
    }

    @Test
    fun `a frame is shortened for reading and mapped to the repo file it names`() {
        val frames = "com.tjshea.vigilant.app.AutoScanner.cycle(AutoScan.kt:231) < com.tjshea.vigilant.data.diag.EventLog.error(EventLog.kt:74)"
        assertEquals("AutoScanner.cycle(AutoScan.kt:231)", EventLog.short(frames))
        assertEquals("app/src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt", EventLog.pathOf(frames))
        // The first frame decides, and a lambda's or companion's generated class still names its file.
        val inner = "com.tjshea.vigilant.data.diag.EventLog\$Companion.whereOf(EventLog.kt:135)"
        assertEquals("EventLog.whereOf(EventLog.kt:135)", EventLog.short(inner))
        assertEquals("data/src/main/kotlin/com/tjshea/vigilant/data/diag/EventLog.kt", EventLog.pathOf(inner))
        // Not the app's own code, or no file: no path to point Claude at.
        assertEquals(null, EventLog.pathOf("okhttp3.Foo.bar(Foo.kt:7)"))
        assertEquals(null, EventLog.pathOf("com.tjshea.vigilant.app.X.y(Unknown Source)"))
        assertEquals("Foo.bar(Foo.kt:7)", EventLog.short("okhttp3.Foo.bar(Foo.kt:7)"))
    }

    @Test
    fun `counters add up and are kept apart from events`() {
        val l = log()
        l.count("autobet.looked", 3)
        l.count("autobet.looked", 2)
        l.count("autobet.skip.no price")
        assertEquals(5L, l.counters()["autobet.looked"])
        assertEquals(1L, l.counters()["autobet.skip.no price"])
        assertTrue(l.events().isEmpty())
    }

    @Test
    fun `events and counters survive a restart, in front of what the new run has written already`() = runBlocking {
        val first = log()
        first.info("APP", "old event")
        first.count("cycle.runs", 7)
        first.flush(force = true)
        val second = log()
        second.info("APP", "new event before the load")
        second.count("cycle.runs", 2)
        second.load()
        assertEquals(listOf("old event", "new event before the load"), second.events().map { it.msg })
        assertEquals(9L, second.counters()["cycle.runs"])
        assertEquals(first.sinceMs(), second.sinceMs())
        // Loading twice doesn't double anything.
        second.load()
        assertEquals(9L, second.counters()["cycle.runs"])
    }

    @Test
    fun `it is written at most every few seconds unless forced, and not at all when nothing changed`() = runBlocking {
        val l = log()
        l.flush()
        assertFalse(File(tmp.root, "events.json").exists())
        l.info("A", "one")
        l.flush()
        assertTrue(File(tmp.root, "events.json").readText().contains("one"))
        l.info("A", "two")
        now += 1_000
        l.flush()
        assertFalse("too soon", File(tmp.root, "events.json").readText().contains("two"))
        l.flush(force = true)
        assertTrue(File(tmp.root, "events.json").readText().contains("two"))
        now += EventLog.MIN_FLUSH_GAP_MS + 1
        l.info("A", "three")
        l.flush()
        assertTrue(File(tmp.root, "events.json").readText().contains("three"))
    }

    @Test
    fun `counters older than two weeks start again`() = runBlocking {
        val old = log()
        old.count("x", 5)
        old.flush(force = true)
        now += EventLog.WINDOW_MS + 1
        val fresh = log()
        fresh.load()
        assertNull(fresh.counters()["x"])
        assertEquals(now, fresh.sinceMs())
    }
}
