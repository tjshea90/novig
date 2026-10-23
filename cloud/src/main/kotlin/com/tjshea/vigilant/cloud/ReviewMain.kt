package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.study.StudyExport
import com.tjshea.vigilant.data.study.StudyJournal
import java.io.File
import java.time.Instant
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * The daily review of every scanned +EV bet, done by GitHub and not by Claude (Tj, 2026-10-15: "I want it to log and review all scanned positive EV bets and look for patterns and what really is profitable"; the
 * free-GitHub-first rule, CLAUDE.md: a mechanical crunch goes to Actions, and Claude's scheduled session only READS the result).
 *
 * The phone's scan study (`ScanStudy`) logs, grades and closes every bet a scan lists and saves its journals to the never-rewritten `phone-data` branch (`phone/study/study-<game day>.jsonl.gz`). This program lays those
 * files out as the journal the app reads and runs the app's OWN report over them ([StudyExport]: the summary, ROI, CLV against the close, and every split by league, kind, book count, star rating, lead time and so on),
 * so the numbers are the ones the phone's "Share scan study" file would give, with the same caveats, and no second implementation to drift. The bets' own lines (tens of thousands) stay in `phone-data`; the digest
 * is everything above them. Pure but for files. Usage: `review <phone-data checkout> <output file>`.
 */
object Review {
    /** The digest as text: a header, then the study's READ ME, data dictionary, caveats, summary and splits. Never throws for missing data; says so. */
    fun digest(phoneData: File, work: File, now: Long): String {
        val src = File(phoneData, "phone/study")
        val files = src.listFiles { f -> f.isFile && f.name.startsWith("study-") && (f.name.endsWith(".jsonl.gz") || f.name.endsWith(".jsonl")) }?.sortedBy { it.name }.orEmpty()
        val head = "VIGILANT DAILY REVIEW (made by GitHub from the phone-data branch) · ${Instant.ofEpochMilli(now)} · ${files.size} study day files"
        if (files.isEmpty()) return "$head\nNo scan-study files on the phone-data branch yet (the phone saves them every 15 minutes while a GitHub token is saved in Settings › Cloud scan).\n"
        val dir = File(work, "study").apply { deleteRecursively(); mkdirs() }
        for (f in files) {
            val out = File(dir, f.name.removeSuffix(".gz"))
            if (f.name.endsWith(".gz")) GZIPInputStream(f.inputStream().buffered()).use { gz -> out.outputStream().buffered().use { gz.copyTo(it) } } else f.copyTo(out, overwrite = true)
        }
        val full = File(work, "export.txt")
        val tmp = File(work, "export.tmp")
        val meta = StudyExport.Meta(
            versionName = "github-review", versionCode = 0, device = "GitHub Actions",
            rules = "the rules in force when each bet was listed are in that bet's own atBet.preset and atBet.version; the digest adds every day together",
            zone = TimeZone.getTimeZone("America/New_York"),
        )
        // The digest keeps the summary and splits, never the bets' lines, so it adds up every day it can (a file's 72 MB day budget held only the engine's games still to come: FX 2026-10-23).
        val bets = full.bufferedWriter().use { w -> StudyExport.write(w, StudyJournal(dir), emptyList(), meta, now, tmp, maxBytes = LINE_BYTES, dayBudget = DAY_BYTES) }
        // Everything above the bets' own lines: the lines are in phone-data already and would make the digest as big as the journals.
        val keep = ArrayList<String>()
        full.bufferedReader().useLines { lines ->
            for (l in lines) {
                if (l.startsWith("== EVERY BET") || l.startsWith("== EVERY FILLED BID")) break
                keep += l
            }
        }
        val text = keep.joinToString("\n")
        val capped = if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n[digest cut at $MAX_CHARS characters; the splits above are the biggest groups first]" else text
        return "$head · $bets bets logged\n\n$capped\n"
    }

    /** About 60k tokens at most: a scheduled Claude session reads this, and the summary and splits are what it needs. */
    const val MAX_CHARS = 240_000

    /** The bets' lines the export writes and the digest then drops: a little, so no gigabyte scratch file. */
    const val LINE_BYTES = 2L * 1024 * 1024

    /** Journal bytes the digest adds up: every day the engine has (a played day is ~100 MB), bounded so a runner's memory and the job's time hold. */
    const val DAY_BYTES = 4L * 1024 * 1024 * 1024
}

fun main(args: Array<String>) {
    if (args.size < 2) { System.err.println("usage: review <phone-data checkout> <output file>"); return }
    val work = File(System.getProperty("java.io.tmpdir"), "vigilant-review").apply { mkdirs() }
    val text = Review.digest(File(args[0]), work, System.currentTimeMillis())
    File(args[1]).apply { parentFile?.mkdirs() }.writeText(text)
    println("review digest: ${text.length} characters -> ${args[1]}")
}
