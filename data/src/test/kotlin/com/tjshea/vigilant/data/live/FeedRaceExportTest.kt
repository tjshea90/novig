package com.tjshea.vigilant.data.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class FeedRaceExportTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `the file has the read me, the verdict, the table, the scores that moved Novig and the raw tape it was made from, and nothing secret`() {
        val dir = java.nio.file.Files.createTempDirectory("race").toFile().also { it.deleteOnExit() }
        val j = FeedRaceJournal(dir, clock = { now })
        val t = now - 600_000L
        val scores = listOf(
            FeedRace.Sighting("fast", "1", "Spain", "Croatia", 0, 0, t - 100_000, init = true), FeedRace.Sighting("slow", "1", "Spain", "Croatia", 0, 0, t - 100_000, init = true),
            FeedRace.Sighting("fast", "1", "Spain", "Croatia", 1, 0, t), FeedRace.Sighting("slow", "1", "Spain", "Croatia", 1, 0, t + 7_000),
        )
        val trades = buildList {
            for (i in 0 until 40 step 4) add(FeedRace.NovigTick("Croatia @ Spain", "m", "o1", 0.50, 100, t - 50_000 + i * 1000L))
            add(FeedRace.NovigTick("Croatia @ Spain", "m", "o1", 0.60, 100, t + 5_000)); add(FeedRace.NovigTick("Croatia @ Spain", "m", "o1", 0.62, 100, t + 6_000))
        }
        j.append(scores, trades)
        val tape = j.read(0)
        val report = FeedRace.report(tape.scores, tape.trades, tape.odds)
        assertEquals(1, report.detail.size)
        assertTrue(report.detail.single(), report.detail.single().contains("Spain v Croatia 1-0 move +0.10") && report.detail.single().contains("fast:0.0/5.0") && report.detail.single().contains("slow:7.0/-2.0"))
        val w = StringWriter()
        FeedRaceExport.write(w, report, FeedRaceStatus(running = true, sinceMs = now - 3_600_000, liveGames = 2, readings = 4, novigTrades = 12, requests = 99), FeedRaceExport.Meta("0.72.1", "moto g", setOf("tennis"), true), j, now)
        val text = w.toString()
        for (section in listOf("== READ ME FIRST (for Claude) ==", "== STATUS ==", "== VERDICT ==", "== TABLE (the last 24 h) ==", "== EVERY SCORE THAT MOVED NOVIG'S MONEYLINE", "== RAW TAPE", "<<<JSONL", "== END OF FILE ==")) assertTrue(section, text.contains(section))
        assertTrue(text.contains("places NO order"))
        assertTrue(text, text.contains("running · live on Novig now: 2 games · sports read: tennis"))
        // The raw tape carries the readings and the trades as the journal wrote them (one line each).
        assertEquals(4 + 12, text.lines().count { it.startsWith("{\"k\":") })
        assertFalse(text.contains("apiKey", ignoreCase = true))
        assertEquals("vigilant-feed-race-v0.72.1-2027-01-15-0800.txt", FeedRaceExport.fileName("0.72.1", now))
    }
}
