package com.tjshea.vigilant.data.pinnodds

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The journals the Diagnostics file reads grew for days until it hung (Tj, 2026-10-10): it reads the newest days only, and old days can be cleared. */
class DayJournalPruneTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun journal(dir: File) = DayJournal(dir, "pinn-follow", LiveFollow.serializer()) { it.atMs }
    private val day = 86_400_000L
    private val base = 1_791_000_000_000L

    @Test
    fun `recent reads only the newest days, prune deletes the older ones, and a different prefix is left alone`() {
        val dir = tmp.newFolder()
        val j = journal(dir)
        for (d in 0 until 6) j.append(LiveFollow("f$d", base + d * day, 30))
        File(dir, "pinn-follow-extra-2026-01-01.jsonl").writeText("x\n")   // another journal's file with a longer prefix
        assertEquals(6, j.readAll().size)
        assertEquals(listOf("f4", "f5"), j.readRecent(2).map { it.id })
        assertEquals(true, j.sizeBytes() > 0)
        val (files, bytes) = j.prune(3)
        assertEquals(3, files)
        assertEquals(true, bytes > 0)
        assertEquals(listOf("f3", "f4", "f5"), j.readAll().map { it.id })
        assertEquals("the other journal's file is not touched", true, File(dir, "pinn-follow-extra-2026-01-01.jsonl").exists())
        assertEquals(0 to 0L, j.prune(3))
    }
}
