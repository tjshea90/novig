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

/** The app's own log lines, the numbers over time, and what changed since the last file (Tj, 2026-10-02: "improve the app with every upload"). */
class LogcatAndTrendTest {

    @get:Rule val tmp = TemporaryFolder()

    private val raw = """
        --------- beginning of main
        10-02 01:00:01.234  4242  4260 I OkHttp  : --> GET https://x.com
        10-02 01:00:02.345  4242  4260 W Choreographer: Skipped 47 frames!  The application may be doing too much work on its main thread.
        10-02 01:00:02.346  4242  4260 W Choreographer: Skipped 47 frames!  The application may be doing too much work on its main thread.
        10-02 01:00:03.000  4242  4261 E OkHttp  : failed with key sk-LIVEKEYLIVEKEYLIVEKEY1234 in it
        10-02 01:00:04.000  4242  4261 D Foo     : debug line
        10-02 01:00:05.000  4242  4261 F libc    : Fatal signal 11 (SIGSEGV)
        10-02 01:00:06.000  4242  4261 W chatty  : uid=10200 identical 3 lines
        not a log line
    """.trimIndent()

    @Test
    fun `only warnings and errors, this process, masked, the same line in a row once, and no noise tags`() {
        val lines = LogcatTail.parse(raw)
        assertEquals(listOf("Choreographer", "OkHttp", "libc"), lines.map { it.tag })
        assertEquals(listOf('W', 'E', 'E'), lines.map { it.level })
        assertEquals("10-02 01:00:02.345", lines[0].time)
        assertTrue(lines[0].text.startsWith("Skipped 47 frames!"))
        val key = lines.single { it.tag == "OkHttp" }
        assertFalse(key.text, key.text.contains("LIVEKEYLIVEKEY"))
        assertTrue(key.text, key.text.contains("…1234"))
        assertEquals("10-02 01:00:03.000 E/OkHttp: failed with key …1234 in it".length, key.toString().length)
        // The newest are kept when there are more than asked for.
        val many = (1..300).joinToString("\n") { "10-02 02:00:%02d.000  1  1 W Tag$it : line $it".format(it % 60) }
        assertEquals(120, LogcatTail.parse(many).size)
        assertEquals("line 300", LogcatTail.parse(many).last().text)
        assertEquals(5, LogcatTail.parse(many, max = 5).size)
    }

    @Test
    fun `reading the log never throws, and returns nothing where it can't`() {
        // A pid with no log (or a machine with no logcat): empty, not an exception.
        assertTrue(LogcatTail.read(-1, timeoutMs = 500).isEmpty())
    }

    // ---- the history and what changed since ---------------------------------------------------------------------------------

    private fun snap(at: Long, code: Int = 77, metrics: Map<String, Double> = emptyMap(), findings: Map<String, String> = emptyMap()) = Snap(at, "0.4$code", code, metrics, findings)
    private val ago: (Long) -> String = { "${(10_000_000L - it) / 3_600_000} h ago" }

    @Test
    fun `the first report has nothing to compare with, and says so`() {
        assertEquals(listOf("This is the first report saved on this phone: nothing earlier to compare with."), Trend.lines(null, snap(10_000_000L), ago))
    }

    @Test
    fun `resolved, new, worse and still-there findings, and the numbers that moved`() {
        val prev = snap(
            0L, code = 76,
            metrics = mapOf("net.a.errorRate" to 20.0, "net.a.p95ms" to 4_000.0, "health.fail" to 2.0, "stable" to 10.0, "tiny" to 0.1),
            findings = mapOf("net:a:errors" to "FAILURE", "perf:cycle" to "OPTIMIZE", "funnel:x" to "WATCH", "old" to "BUG"),
        )
        val now = snap(
            10_000_000L, code = 77,
            metrics = mapOf("net.a.errorRate" to 2.0, "net.a.p95ms" to 4_100.0, "health.fail" to 0.0, "stable" to 10.2, "tiny" to 0.3),
            findings = mapOf("perf:cycle" to "FAILURE", "funnel:x" to "WATCH", "new:one" to "BUG"),
        )
        val text = Trend.lines(prev, now, ago).joinToString("\n")
        assertTrue(text, text.contains("Previous report: 2 h ago, version 0.476 (code 76); this one is version 0.477 (code 77): THE APP WAS UPDATED SINCE"))
        assertTrue(text, text.contains("RESOLVED since then (2): net:a:errors [was FAILURE]; old [was BUG]"))
        assertTrue(text, text.contains("NEW since then (1): new:one [BUG]"))
        assertTrue(text, text.contains("WORSE since then: perf:cycle [OPTIMIZE → FAILURE]"))
        assertTrue(text, text.contains("STILL there (1): funnel:x"))
        assertTrue(text, text.contains("net.a.errorRate 20 → 2"))
        assertTrue(text, text.contains("health.fail 2 → 0"))
        // Small moves are not reported.
        assertFalse(text, text.contains("stable"))
        assertFalse(text, text.contains("net.a.p95ms"))
        assertFalse(text, text.contains("tiny"))
    }

    @Test
    fun `no more than twelve moved numbers are listed, the biggest change first`() {
        val prev = snap(0L, metrics = (1..20).associate { "m$it" to 10.0 })
        val now = snap(1_000L, metrics = (1..20).associate { "m$it" to 10.0 + it * 2 })
        val line = Trend.lines(prev, now, ago).first { it.startsWith("Numbers that moved") }
        val listed = line.removePrefix("Numbers that moved: ").split("; ")
        assertEquals(12, listed.size)
        assertEquals("m20 10 → 50", listed.first())
        assertTrue(line, listed.none { it.startsWith("m1 ") || it.startsWith("m2 ") })
    }

    @Test
    fun `nothing changed says so, and the same version doesn't claim an update`() {
        val a = snap(0L, metrics = mapOf("x" to 5.0), findings = mapOf("k" to "WATCH"))
        val text = Trend.lines(a, a.copy(atMs = 10_000_000L), ago).joinToString("\n")
        assertFalse(text, text.contains("UPDATED"))
        assertTrue(text, text.contains("STILL there (1): k"))
        val none = Trend.lines(snap(0L), snap(10_000_000L), ago).joinToString("\n")
        assertTrue(none, none.contains("Nothing changed enough to report."))
        assertEquals(listOf("BUG", "FAILURE", "OPTIMIZE", "IMPROVE", "WATCH"), Trend.KINDS)
        assertTrue(Trend.rank("BUG") > Trend.rank("WATCH"))
    }

    @Test
    fun `the history keeps the newest twelve across restarts`() = runBlocking {
        fun history() = DiagHistory(JsonFileStore(File(tmp.root, "h.json"), DiagBook.serializer(), { DiagBook() }))
        repeat(15) { i -> history().add(snap(i.toLong(), code = i)) }
        val all = history().all()
        assertEquals(DiagHistory.KEEP, all.size)
        assertEquals(3, all.first().code)
        assertEquals(14, all.last().code)
        assertNull(DiagHistory(JsonFileStore(File(tmp.root, "empty.json"), DiagBook.serializer(), { DiagBook() })).all().lastOrNull())
    }

    @Test
    fun `percentiles are nearest-rank samples that happened`() {
        val s = SampleSummary.of((1..100).map { it.toDouble() })
        assertEquals(50.0, s.p50, 0.0)
        assertEquals(95.0, s.p95, 0.0)
        assertEquals(100.0, s.max, 0.0)
        assertEquals(50.5, s.mean, 0.0)
        assertEquals(SampleSummary.EMPTY, SampleSummary.of(emptyList()))
        val r = RollingSamples(cap = 3)
        listOf(1.0, 2.0, 3.0, 4.0).forEach(r::add)
        assertEquals(listOf(2.0, 3.0, 4.0), r.values())
        assertEquals(3, r.summary().count)
        val perf = PerfStats()
        perf.add("cycle.ms", 100.0)
        perf.add("cycle.ms", 300.0)
        assertEquals(300.0, perf.summary("cycle.ms").max, 0.0)
        assertEquals(SampleSummary.EMPTY, perf.summary("nothing"))
        assertEquals(setOf("cycle.ms"), perf.summaries().keys)
    }
}
