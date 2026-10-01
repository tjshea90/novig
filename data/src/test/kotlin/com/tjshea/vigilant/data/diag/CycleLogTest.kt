package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.TimeZone

/**
 * Tj, 2026-10-02: does background auto-scan keep its schedule with the phone idle and the screen off? Every cycle records when it started against when
 * the schedule said, so Diagnostics can answer after a night.
 */
class CycleLogTest {

    @get:Rule val tmp = TemporaryFolder()

    private val t0 = 1_800_000_000_000L
    private val zone = TimeZone.getTimeZone("UTC")

    private fun apply(book: CycleBook, start: Long, seconds: Int = 5, end: Long = start + 400, screenOff: Boolean = false, dozing: Boolean = false, afterPause: Boolean = false) =
        CycleLog.apply(book, start, end, seconds, screenOff, dozing, afterPause)

    private fun store(name: String = "cycles.json") = JsonFileStore(File(tmp.root, name), CycleBook.serializer(), { CycleBook() })

    @Test
    fun `cycles on time are counted with the screen and Doze they started in, and none is late`() {
        var b = CycleBook()
        b = apply(b, t0)
        b = apply(b, t0 + 5_000, screenOff = true)
        b = apply(b, t0 + 10_050, screenOff = true, dozing = true)
        assertEquals(3, b.cycles)
        assertEquals(2, b.screenOffCycles)
        assertEquals(1, b.dozeCycles)
        assertEquals(0, b.lateCount)
        assertNull(b.worst)
        assertEquals(t0, b.sinceMs)
        assertEquals(t0 + 10_050, b.lastStartMs)
        assertTrue(b.open)
    }

    @Test
    fun `a cycle that starts long after its time is late, with how late and what the phone was doing`() {
        var b = apply(CycleBook(), t0)
        // 5 s schedule; the next one came 9 minutes later (Doze letting one alarm through): 8 min 55 s late, in Doze.
        b = apply(b, t0 + 9 * 60_000L, screenOff = true, dozing = true)
        assertEquals(1, b.lateCount)
        assertEquals(9 * 60_000L - 5_000L - 400L + 400L, b.worst!!.lateMs)
        assertTrue(b.worst!!.dozing && b.worst!!.screenOff)
        assertEquals(listOf(b.worst), b.late)
        // A later, lesser one doesn't replace the worst but is counted and kept.
        b = apply(b, t0 + 9 * 60_000L + 5_000L + 45_000L)
        assertEquals(2, b.lateCount)
        assertEquals(9 * 60_000L - 5_000L, b.worst!!.lateMs)
        assertEquals(2, b.late.size)
    }

    @Test
    fun `wander under 30 seconds, or under one interval, is not late`() {
        val seconds = 600
        var b = apply(CycleBook(), t0, seconds = seconds)
        // An alarm 9 min 59 s late on a 10-minute schedule is within an interval: not late. 10 min + 31 s over the schedule is.
        b = apply(b, t0 + 600_000L + 29_000L, seconds = seconds)
        assertEquals(0, b.lateCount)
        b = apply(b, b.lastStartMs!! + 600_000L + 601_000L, seconds = seconds)
        assertEquals(1, b.lateCount)
        // At 5 s the limit is 30 s: 30 s late is not, 31 s is.
        assertEquals(30_000L, CycleLog.lateAfterMs(5))
        assertEquals(600_000L, CycleLog.lateAfterMs(600))
        val fast = apply(CycleBook(), t0)
        assertEquals(0, apply(fast, t0 + 5_000 + 30_000).lateCount)
        assertEquals(1, apply(fast, t0 + 5_000 + 30_001).lateCount)
    }

    @Test
    fun `a cycle that ran longer than its interval is on time when the next starts right after it`() {
        // Vigilant's own scan: 8 minutes long at a 5 s interval. The next cycle starts a second after it ended: not late.
        val long = apply(CycleBook(), t0, end = t0 + 8 * 60_000L)
        val next = apply(long, t0 + 8 * 60_000L + 1_000L)
        assertEquals(0, next.lateCount)
        // But a start 2 minutes after that end is.
        assertEquals(1, apply(long, t0 + 10 * 60_000L).lateCount)
    }

    @Test
    fun `switching the interval is not lateness, from slow to fast or fast to slow`() {
        val slow = apply(CycleBook(), t0, seconds = 600)
        // 10 minutes -> 5 s, the next cycle two minutes later (Tj changed it): scheduled by the longer of the two, so early, never late.
        assertEquals(0, apply(slow, t0 + 120_000L, seconds = 5).lateCount)
        // 5 s -> 10 minutes, the next one 10 minutes after: on time.
        val fast = apply(CycleBook(), t0, seconds = 5)
        assertEquals(0, apply(fast, t0 + 5_000L + 600_000L - 5_000L, seconds = 600).lateCount)
    }

    @Test
    fun `the wait after Check odds now is on purpose, and a deliberate stop doesn't make the next start late, a killed process does`() {
        val b = apply(CycleBook(), t0)
        // Check odds now held the focus for 4 minutes: the first cycle after it isn't late.
        assertEquals(0, apply(b, t0 + 4 * 60_000L, afterPause = true).lateCount)
        assertEquals(1, apply(b, t0 + 4 * 60_000L).lateCount)
        // Stop: the schedule isn't running, so the hours until the next start aren't lateness.
        runBlocking {
            val log = CycleLog(store(), clock = { t0 })
            log.record(t0, t0 + 300, 5, false, false)
            log.stopped()
            assertFalse(log.summary().open)
            log.record(t0 + 3_600_000L, t0 + 3_600_300L, 5, false, false)
            assertEquals(0, log.summary().lateCount)
            // No stop (the process was killed in the night): the next cycle after the restart is late, and says so.
            log.record(t0 + 3_600_000L + 6 * 3_600_000L, t0 + 3_600_000L + 6 * 3_600_000L + 300L, 5, true, false)
            assertEquals(1, log.summary().lateCount)
            assertEquals(6 * 3_600_000L - 5_000L - 300L + 300L, log.summary().worst!!.lateMs)
        }
    }

    @Test
    fun `the record starts again after a week, so the numbers are about recent behaviour`() {
        var b = apply(CycleBook(), t0, screenOff = true)
        b = apply(b, t0 + 5_000, screenOff = true)
        val later = t0 + CycleLog.WINDOW_MS + 10_000L
        b = apply(b, later, screenOff = false)
        assertEquals(1, b.cycles)
        assertEquals(0, b.screenOffCycles)
        assertEquals(later, b.sinceMs)
    }

    @Test
    fun `only the newest late cycles are kept, and the count goes on`() {
        var b = apply(CycleBook(), t0)
        var at = t0
        repeat(CycleLog.KEEP_LATE + 5) {
            at += 20 * 60_000L
            b = apply(b, at)
        }
        assertEquals(CycleLog.KEEP_LATE + 5, b.lateCount)
        assertEquals(CycleLog.KEEP_LATE, b.late.size)
        assertEquals(at, b.late.last().atMs)
    }

    @Test
    fun `the record is saved across restarts, at most every 30 seconds unless a cycle was late`() = runBlocking {
        var now = t0
        val log = CycleLog(store(), clock = { now })
        log.record(t0, t0 + 300, 5, true, true)
        // Saved on the first cycle...
        assertEquals(1, CycleLog(store(), clock = { now }).summary().cycles)
        // ...then not for every 5 s cycle.
        now = t0 + 5_000
        log.record(t0 + 5_000, t0 + 5_300, 5, true, true)
        log.record(t0 + 10_000, t0 + 10_300, 5, true, true)
        assertEquals(1, CycleLog(store(), clock = { now }).summary().cycles)
        assertEquals(3, log.summary().cycles)
        // A late cycle is written at once.
        log.record(t0 + 30 * 60_000L, t0 + 30 * 60_000L + 300, 5, true, true)
        val reread = CycleLog(store(), clock = { now }).summary()
        assertEquals(4, reread.cycles)
        assertEquals(1, reread.lateCount)
        // And a later cycle past the 30 s since the last save saves again.
        log.record(t0 + 30 * 60_000L + 40_000L, t0 + 30 * 60_000L + 40_300L, 5, true, true)
        assertEquals(5, CycleLog(store(), clock = { now }).summary().cycles)
    }

    @Test
    fun `a file from an older version, or one with unknown fields, reads as an empty record`() = runBlocking {
        File(tmp.root, "cycles.json").writeText("""{"cycles": 4, "futureField": 1}""")
        val b = CycleLog(store()).summary()
        assertEquals(4, b.cycles)
        assertNull(b.sinceMs)
        assertEquals("Cycle record: none yet (it starts with the next background cycle)", CycleLog.line(CycleBook(), t0, zone))
    }

    @Test
    fun `the Diagnostics line says how many cycles ran with the screen off and in Doze, and the worst late one`() {
        var b = CycleBook()
        repeat(3) { i -> b = apply(b, t0 + i * 5_000L, screenOff = true, dozing = i > 0) }
        val calm = CycleLog.line(b, t0 + 20_000L, zone)
        assertTrue(calm, calm.contains("3 cycles, 3 started with the screen off (2 of those in Doze) · none late"))
        b = apply(b, t0 + 10_000L + 7 * 60_000L, screenOff = true, dozing = true)
        val late = CycleLog.line(b, t0 + 10_000L + 7 * 60_000L, zone)
        assertTrue(late, late.contains("4 cycles"))
        assertTrue(late, late.contains("1 late (more than 30 sec after schedule), worst 7 min at "))
        assertTrue(late, late.endsWith(" in Doze"))
        assertNotNull(b.worst)
        assertEquals("45 sec", CycleLog.span(45_000L))
        assertEquals("4 min", CycleLog.span(240_000L))
        assertEquals("2 h 10 min", CycleLog.span(7_800_000L))
        // Within the last day only.
        assertEquals(1, CycleLog.lateWithin(b, b.lastStartMs!! + 3_600_000L, 24 * 3_600_000L).size)
        assertEquals(0, CycleLog.lateWithin(b, b.lastStartMs!! + 25 * 3_600_000L, 24 * 3_600_000L).size)
    }
}
