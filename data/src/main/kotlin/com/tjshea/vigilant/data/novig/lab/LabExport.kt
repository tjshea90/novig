package com.tjshea.vigilant.data.novig.lab

import kotlinx.serialization.json.Json
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The one research file (Tj, 2026-10-09: "just make it simple for me to run and tell me what to run and what to send you"): what every recorder found, the paper bids' tables, and the raw journals
 * (one JSON object a line) so Claude can redo any number. The README says what to look for and what must never change without Tj's word.
 */
object LabExport {
    class Meta(val versionName: String, val device: String, val settingsNote: String)

    fun fileName(versionName: String, now: Long): String =
        "vigilant-research-v$versionName-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(now)) + ".txt"

    const val PROMPT = "Vigilant research file: read the README, then do the analysis it asks for."

    private val json = Json { encodeDefaults = true }

    private const val README = """== READ ME FIRST (for Claude) ==
This is Tj's research file from the Vigilant app (RESEARCH.md §120-§122). The app is in research and development: your job is to find ANY way to profit on Novig (taking, making/bidding, live, pregame, creative), look for
patterns, stale odds and algorithms, and tell Tj in plain words what is and is not worth building. He wants simple answers, with sample sizes, and wants you to take control of the research.
What is here: STATUS (what each recorder did), the PAPER LAB (would-be bets: ladder covers, late-game tail strikes, alternate lines against Pinnacle's, graded from settled markets) and the PAPER BIDS (many make-bid
recipes run side by side on every line the bid desk looked at, against Novig's real trade tape: margin under the fair, how long a bid rests, a cancel-when-the-fair-moves guard; live recipes use Pinnacle's price from the Pinnodds feed).
How to read the paper bids: a FILL means a trade printed at or under the bid's price after it went up (queue position unknown; 'strictly through' certainly filled). EV at post = fair/price - 1 (the fair is Vigilant's own at the
time, not the truth). CLV = the last fair seen before the start / price - 1. ROI is from the settled market. Compare recipes, not single bids: the question is which recipe has the best EV x fill rate AND a CLV that keeps the edge.
Do: (1) rank the recipes by fills per bid-hour x CLV and say which (margin, rest time, guard) to make the app's default; (2) check the slices (kind, side, hours to start, league, books behind the fair) for a pattern worth a rule;
(3) check the live recipes: did the guard keep the edge? (4) check the paper lab's graded would-be bets: does any kind have a real edge after fees?; (5) say what to log next.
Rules that stand: never loosen a limit or turn on real money without Tj's word; the repo is public (no keys); say 'the data suggests' on small samples; MIN 30 fills before calling a recipe better.
The raw sections below are JSON lines: LAB RECORDS, LAB GRADES, BID LAB BIDS, BID LAB EVENTS (FILL/CANCEL/CLOSE/GRADE by bid id).
"""

    fun write(w: Writer, meta: Meta, status: List<String>, records: List<LabRecord>, grades: List<LabGrade>, bids: List<BidLabBid>, events: List<BidLabEvent>, now: Long) {
        w.appendLine("VIGILANT RESEARCH FILE · version ${meta.versionName} · ${meta.device} · made ${Instant.ofEpochMilli(now)}")
        w.appendLine()
        w.appendLine(README)
        w.appendLine("== STATUS ==")
        w.appendLine(meta.settingsNote)
        status.forEach { w.appendLine(it) }
        w.appendLine()
        w.appendLine("== PAPER LAB (would-be bets) ==")
        LabPaper.report(records, grades).forEach { w.appendLine(it) }
        w.appendLine()
        w.appendLine("== PAPER BIDS ==")
        BidLabReport.lines(bids, events).forEach { w.appendLine(it) }
        w.appendLine()
        w.appendLine("== LAB RECORDS (JSON lines) ==")
        records.forEach { w.appendLine(json.encodeToString(LabRecord.serializer(), it)) }
        w.appendLine("== LAB GRADES (JSON lines) ==")
        grades.forEach { w.appendLine(json.encodeToString(LabGrade.serializer(), it)) }
        w.appendLine("== BID LAB BIDS (JSON lines) ==")
        bids.forEach { w.appendLine(json.encodeToString(BidLabBid.serializer(), it)) }
        w.appendLine("== BID LAB EVENTS (JSON lines) ==")
        events.forEach { w.appendLine(json.encodeToString(BidLabEvent.serializer(), it)) }
        w.appendLine("== END OF FILE ==")
    }
}
