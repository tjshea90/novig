package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Diagnostics' "Recent problems" (Tj, 2026-09-30): kept across restarts, repeats counted, the newest kept, never a key. */
class ProblemLogTest {

    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_790_000_000_000L
    private fun log() = ProblemLog(JsonFileStore(File(tmp.root, "problems.json"), ProblemBook.serializer(), { ProblemBook() }), clock = { now })

    @Test
    fun `the same problem again adds to its count, a new one gets its own line, newest first`() = runTest {
        val l = log()
        l.add("CNO", "Couldn't reach CrazyNinjaOdds (timeout)")
        now += 60_000
        l.add("CNO", "Couldn't reach CrazyNinjaOdds (timeout)")
        now += 60_000
        l.add("Vigilant scan", "Kalshi: HTTP 503")
        val r = log().recent() // read back from the file, as after a restart
        assertEquals(2, r.size)
        assertEquals("Vigilant scan", r[0].area)
        assertEquals(2, r[1].count)
        assertEquals(now - 120_000, r[1].firstAtMs)
        assertEquals(now - 60_000, r[1].lastAtMs)
        // Hours later it's a new line.
        now += ProblemLog.MERGE_MS + 1
        l.add("CNO", "Couldn't reach CrazyNinjaOdds (timeout)")
        assertEquals(3, l.recent().size)
    }

    @Test
    fun `only the newest are kept, and nothing shaped like a key is ever written`() = runTest {
        val l = log()
        repeat(ProblemLog.KEEP + 5) { now += 1_000; l.add("x", "problem $it") }
        assertEquals(ProblemLog.KEEP, l.recent().size)
        assertEquals("problem ${ProblemLog.KEEP + 4}", l.recent().first().message)
        l.add("ParlayAPI", "refused key FAKEabcdefghijklmnopqrstuvwxyz0123456789 (401)")
        assertFalse(File(tmp.root, "problems.json").readText().contains("abcdefghijklmnop"))
        assertEquals("refused key …6789 (401)", l.recent().first().message)
    }
}
