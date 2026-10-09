package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.lab.gh.EdgeLog
import com.tjshea.vigilant.data.novig.lab.gh.EdgeRow
import com.tjshea.vigilant.data.pinnodds.DayJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The journals the GitHub lab shares with the phone's lab: a prefix reads only its own files, and many records are one write. */
class GhLabTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun aJournalReadsOnlyItsOwnFilesEvenWhenAnotherPrefixStartsWithIt() {
        val dir = tmp.newFolder()
        val lab = DayJournal(dir, "lab", LabGrade.serializer()) { it.atMs }
        val grade = DayJournal(dir, "lab-grade", LabGrade.serializer()) { it.atMs }
        lab.append(LabGrade("a", 1_800_000_000_000L, "WIN"))
        grade.append(LabGrade("b", 1_800_000_000_000L, "LOSS"))
        assertEquals(listOf("a"), lab.readAll().map { it.id })
        assertEquals(listOf("b"), grade.readAll().map { it.id })
    }

    @Test fun appendAllIsOneOpenPerDayAndReadsBackInOrder() {
        val dir = tmp.newFolder()
        val j = DayJournal(dir, "edge", EdgeRow.serializer()) { it.atMs }
        val day = 1_800_000_000_000L
        val rows = (0 until 50).map { EdgeRow(day + it, "NFL", "e", "A @ B", day, false, "MONEYLINE", "A", "m", "o$it", 0.5, 0.52, 0.5, -0.04, 3, 10) } +
            EdgeRow(day + 3 * 86_400_000L, "NFL", "e", "A @ B", day, false, "MONEYLINE", "A", "m", "late", 0.5, 0.52, 0.5, -0.04, 3, 10)
        j.appendAll(rows)
        j.appendAll(emptyList())
        assertEquals(51, j.readAll().size)
        assertEquals("o0", j.readAll().first().outcomeId)
        assertEquals("one file per day: two", 2, dir.listFiles()!!.size)
        assertTrue(dir.listFiles()!!.all { it.name.matches(Regex("edge-\\d{4}-\\d{2}-\\d{2}\\.jsonl")) })
    }

    @Test fun theNewestRawLinesAreKeptInAReportWhenAskedAndTheTablesUseEverything() {
        val bids = (0 until 30).map { BidLabBid("b$it", 1_800_000_000_000L + it, "pre-m4-t30m", false, "o$it", "m", "e", "A @ B", "NFL", "TOTAL", "Over 3.5", 1_800_000_900_000L, 0.45, 0.5, 0.52, 0.44, 3, 1_800_000_500_000L) }
        val all = java.io.StringWriter().also { LabExport.write(it, LabExport.Meta("t", "d", "n"), emptyList(), emptyList(), emptyList(), bids, emptyList(), 1_800_000_100_000L) }.toString()
        val capped = java.io.StringWriter().also { LabExport.write(it, LabExport.Meta("t", "d", "n"), emptyList(), emptyList(), emptyList(), bids, emptyList(), 1_800_000_100_000L, rawLimit = 5) }.toString()
        assertEquals(30, all.lines().count { it.startsWith("{\"id\":\"b") })
        assertEquals(5, capped.lines().count { it.startsWith("{\"id\":\"b") })
        assertTrue("b29" in capped && "\"id\":\"b0\"" !in capped)
        assertTrue("the table still counts all 30", capped.contains("30 posted"))
    }
}
