package com.tjshea.vigilant.data.live

import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The feed race as one file for Claude (Tj, 2026-10-07: "test all available sources that can be used as a rapid source of odds or scores"): a READ ME, the verdict, the report's table, one
 * line per score that moved Novig's moneyline, and the raw tape the report was made from (the journal's lines, so any of it can be recomputed). Numbers and the names of games only: no key,
 * no account, no bet. Written like the scan study's and the burst study's files; shared the same way.
 */
object FeedRaceExport {

    class Meta(val versionName: String, val device: String, val sports: Set<String>, val running: Boolean)

    fun fileName(versionName: String, nowMs: Long): String =
        "vigilant-feed-race-v$versionName-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(nowMs)) + ".txt"

    /** The message that goes with the file. */
    const val PROMPT =
        "This is Vigilant's live feed test (which free score and odds feeds show a play before Novig's price moves). Read the READ ME in the file, then say in short bullets: which feed was " +
            "first, whether any was ahead of Novig's moneyline and by how much, how much traded at the old price, and whether it is worth building a live trigger on. Work from the numbers " +
            "(the sample is small: say so), never place anything from it."

    fun write(w: Writer, report: FeedRace.Report, status: FeedRaceStatus, meta: Meta, journal: FeedRaceJournal, nowMs: Long) {
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        w.appendLine("VIGILANT LIVE FEED TEST · version ${meta.versionName} · ${fileName(meta.versionName, nowMs)}")
        w.appendLine()
        w.appendLine("== READ ME FIRST (for Claude) ==")
        w.appendLine("Made by the Vigilant app on ${utc.format(Date(nowMs))}, ${meta.device}. The test holds free feeds of every game that is live on Novig and stamps each reading when it ARRIVES on the phone:")
        w.appendLine("  sofa = Sofascore live REST (polled every 2.5 s, only for the sports Novig has live), poly = Polymarket's sports score socket (push), espn / nhl / mlb = ESPN's scoreboard, the NHL's")
        w.appendLine("  score route and MLB's schedule (polled with a cache-busting query), Novig's own moneyline trades (engine timestamps: the instant its price moved), and Polymarket's ODDS (a push socket:")
        w.appendLine("  the mid of its best bid and ask for the match winner). There is no ground truth of when a play happened: every number is RELATIVE to the other feeds and to Novig.")
        w.appendLine("  'before' = the feed showed a score before Novig's moneyline traded at a new price (a move of 0.03 or more from its median of the 30 s before). Novig's 'move' is the first TRADE at the")
        w.appendLine("  new level, which is later than the first re-quote when nobody trades: the leads are an upper bound, the dollars (stale fills: trades that really happened at the old price, net of the")
        w.appendLine("  in-play taker fee) a floor. MIN ${FeedRace.MIN_SCORES} scores before anything is called a finding. The test places NO order and has no way to: public reads only.")
        w.appendLine("  RESEARCH.md §99 and §106 say what was measured before and what to compare with. A phone's network differs from the research container's: this file is the phone's answer.")
        w.appendLine()
        w.appendLine("== STATUS ==")
        w.appendLine("${if (meta.running) "running" else "not running"} · live on Novig now: ${status.liveGames} games · sports read: ${meta.sports.joinToString(", ").ifEmpty { "none" }} · since ${status.sinceMs?.let { utc.format(Date(it)) } ?: "not started this run"}")
        w.appendLine("readings ${status.readings} · Novig trades ${status.novigTrades} · odds ticks ${status.oddsTicks} · requests ${status.requests} this run" + (status.problem?.let { " · problem: $it" } ?: ""))
        w.appendLine()
        w.appendLine("== VERDICT ==")
        w.appendLine(report.verdict())
        w.appendLine()
        w.appendLine("== TABLE (the last 24 h) ==")
        report.lines().forEach { w.appendLine(it) }
        w.appendLine()
        w.appendLine("== EVERY SCORE THAT MOVED NOVIG'S MONEYLINE (UTC, game, score, Novig's move, then each feed: its time after the first feed / its lead over Novig's move, seconds) ==")
        if (report.detail.isEmpty()) w.appendLine("none yet") else report.detail.forEach { w.appendLine(it) }
        w.appendLine()
        w.appendLine("== RAW TAPE (JSON lines of the last 24 h: s = a score reading, n = a Novig trade, o = Polymarket odds; t = epoch ms) ==")
        w.appendLine("<<<JSONL")
        journal.copyRaw(w, nowMs - FeedRaceRunner.WINDOW_MS)
        w.appendLine(">>>")
        w.appendLine("== END OF FILE ==")
    }
}
