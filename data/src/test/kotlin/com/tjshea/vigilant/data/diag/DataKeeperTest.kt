package com.tjshea.vigilant.data.diag

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tj, 2026-10-10: the diagnostics must never grow until the app cannot load it; day files older than 2 days (the study: 7) and anything over a folder's cap go by themselves. */
class DataKeeperTest {
    private val now = LocalDate.of(2026, 10, 10).atTime(12, 0).atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli()

    private fun dir(): File = java.nio.file.Files.createTempDirectory("keeper").toFile()
    private fun File.day(sub: String, name: String, bytes: Int = 10): File = File(this, sub).apply { mkdirs() }.let { File(it, name).apply { writeBytes(ByteArray(bytes)) } }

    @Test
    fun `a journal keeps today and yesterday and drops the older days`() {
        val d = dir()
        val old = d.day("lab", "lab-2026-10-07.jsonl")
        val older = d.day("lab", "bidlab-2026-10-08.jsonl")
        val yesterday = d.day("lab", "lab-2026-10-09.jsonl")
        val today = d.day("lab", "lab-2026-10-10.jsonl")
        val r = DataKeeper.sweep(d, now)
        assertFalse(old.exists()); assertFalse(older.exists())
        assertTrue(yesterday.exists()); assertTrue(today.exists())
        assertEquals(2, r.files)
    }

    @Test
    fun `the study keeps a week`() {
        val d = dir()
        val six = d.day("study", "study-2026-10-04.jsonl")
        val eight = d.day("study", "study-2026-10-02.jsonl")
        DataKeeper.sweep(d, now)
        assertTrue(six.exists()); assertFalse(eight.exists())
    }

    @Test
    fun `a folder over its cap loses its oldest days first but never the newest file`() {
        val d = dir()
        val a = d.day("race", "race-2026-10-09.jsonl", 600)
        val b = d.day("race", "race-2026-10-10.jsonl", 900)
        DataKeeper.sweep(d, now, listOf(DataKeeper.Rule("race", 2, 1000)))
        assertFalse(a.exists()); assertTrue(b.exists())
        // One file over the cap alone is kept: a cap that deletes today is a recorder that records nothing.
        val c = d.day("lab", "lab-2026-10-10.jsonl", 5000)
        DataKeeper.sweep(d, now, listOf(DataKeeper.Rule("lab", 2, 1000)))
        assertTrue(c.exists())
    }

    @Test
    fun `leftovers of a crashed save go after a day and nothing else in the files directory is touched`() {
        val d = dir()
        val tmp = File(d, "maker.json.tmp").apply { writeText("x"); setLastModified(now - 3 * 24 * 3_600_000L) }
        val fresh = File(d, "bets.json.tmp").apply { writeText("x"); setLastModified(now) }
        val bets = File(d, "bets.json").apply { writeText("[]"); setLastModified(now - 30L * 24 * 3_600_000L) }
        DataKeeper.sweep(d, now)
        assertFalse(tmp.exists()); assertTrue(fresh.exists()); assertTrue(bets.exists())
    }
}
