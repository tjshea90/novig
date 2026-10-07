package com.tjshea.vigilant.data.scanner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The trap guard's third rule (Tj, 2026-10-07, proposal 2): each listed bet's first-seen time is written down once, kept across restarts, and used by [TrapGuard.listedEarly]. */
class FirstListedTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun file() = File.createTempFile("first_listed", ".json").also { it.delete(); it.deleteOnExit() }

    @Test
    fun `the first sighting is kept and a later one never moves it`() = runBlocking {
        val f = FirstListed(file())
        assertEquals(2, f.note(listOf("a" to now + 20 * hour, "b" to now + 5 * hour), now))
        assertEquals(1, f.note(listOf("a" to now + 20 * hour, "c" to now + 9 * hour), now + hour))
        val seen = f.snapshot()
        assertEquals(now, seen["a"]); assertEquals(now, seen["b"]); assertEquals(now + hour, seen["c"])
        assertNull(f.at("zzz"))
    }

    @Test
    fun `a restart remembers (an old listing must not look fresh again)`() = runBlocking {
        val file = file()
        FirstListed(file).note(listOf("a" to now + 20 * hour), now)
        val again = FirstListed(file)
        assertEquals(now, again.snapshot()["a"])
        // Seen again hours later by the new process: still the first time.
        assertEquals(0, again.note(listOf("a" to now + 20 * hour), now + 8 * hour))
        assertEquals(now, FirstListed(file).snapshot()["a"])
    }

    @Test
    fun `an entry goes 12 hours after its game's start, one with no known start after 3 days, and the file only changes when something is new or gone`() = runBlocking {
        val f = FirstListed(file())
        f.note(listOf("old" to now - 1 * hour, "keep" to now + 100 * hour, "nostart" to 0L), now)
        // 13 hours on: "old" (started 14 h ago) is gone, "keep" and "nostart" stay.
        f.note(listOf("keep" to now + 100 * hour), now + 13 * hour)
        assertEquals(setOf("keep", "nostart"), f.snapshot().keys)
        f.note(listOf("keep" to now + 100 * hour), now + 4 * 24 * hour)
        assertEquals(setOf("keep"), f.snapshot().keys)
        assertEquals(0, f.note(emptyList(), now + 4 * 24 * hour + 1))
        assertEquals(0, f.note(listOf("" to 0L), now + 4 * 24 * hour + 2))
    }

    @Test
    fun `the guard skips a bet first listed more than its hours before the start - only with a first-listed time and the hours on`() {
        val start = now + 5 * hour
        // First seen 3 h before the start: inside 6 h. First seen 8 h before: outside.
        assertNull(TrapGuard.listedEarly(start, start - 3 * hour, 6))
        assertEquals(TrapGuard.listedEarlyReason(6), TrapGuard.listedEarly(start, start - 8 * hour, 6))
        // Exactly 6 h is inside (the early rule's own edge: more than).
        assertNull(TrapGuard.listedEarly(start, start - 6 * hour, 6))
        // No time, no start, or the guard off: nothing to say.
        assertNull(TrapGuard.listedEarly(start, null, 6))
        assertNull(TrapGuard.listedEarly(null, start - 8 * hour, 6))
        assertNull(TrapGuard.listedEarly(start, start - 8 * hour, 0))
        assertNull(TrapGuard.listedEarly(start, 0L, 6))
        // The two clocks together: too far off now is the early rule's wording, otherwise the listing's, and the switch turns the second off.
        assertEquals(TrapGuard.earlyReason(6), TrapGuard.tooEarly(now + 20 * hour, now, 6, now - 30 * hour, true))
        assertEquals(TrapGuard.listedEarlyReason(6), TrapGuard.tooEarly(start, now, 6, start - 9 * hour, true))
        assertNull(TrapGuard.tooEarly(start, now, 6, start - 9 * hour, false))
        assertNull(TrapGuard.tooEarly(start, now, 6, start - 2 * hour, true))
        assertTrue(TrapGuard.listedEarlyReason(24).contains("24 h"))
    }
}
