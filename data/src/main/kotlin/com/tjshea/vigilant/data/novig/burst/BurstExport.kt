package com.tjshea.vigilant.data.novig.burst

import com.tjshea.vigilant.data.novig.trading.burst.BurstTradeReport
import com.tjshea.vigilant.data.novig.trading.burst.TradeRecord
import kotlinx.serialization.json.Json
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * "Share live burst study" (Tj, 2026-10-06): one text file for Claude with what the no-orders recorder saw on this phone, this key and this network: the report (per league, the
 * windows, the paper trades at Tj's measured delays, the verdict and what it cannot prove), a READ ME for whoever analyzes it, and one JSON line per window and per game watched.
 * Nothing secret is in it: no key, no account id.
 */
object BurstExport {
    private val json = Json { encodeDefaults = true; classDiscriminator = "k" }

    const val PROMPT = "This is Vigilant's live burst study (a no-orders recorder of cross-line mispricings after plays, RESEARCH.md §95). Read the READ ME at the top, then the report, " +
        "then analyze the window lines: which leagues show windows that last long enough for an order from this phone to arrive, what a paper trade at the measured delays made, and whether " +
        "the verdict (needs 3 games and 10 windows) says it is worth a real $1 test. If REAL-MONEY TRADES is there, compare it with the paper trade. Do not turn the real-money trader on or change its limits without asking Tj."

    fun fileName(versionName: String, nowMs: Long): String =
        "vigilant-burst-study-v$versionName-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/New_York") }.format(Date(nowMs))}.txt"

    class Meta(val versionName: String, val device: String, val capDollars: Double, val leagues: Set<String>)

    fun write(w: Writer, records: List<BurstLine>, latencyNote: String, meta: Meta, nowMs: Long, trades: List<TradeRecord> = emptyList()) {
        w.appendLine("VIGILANT LIVE BURST STUDY · version ${meta.versionName} · ${meta.device}")
        w.appendLine()
        w.appendLine("== READ ME FIRST (for Claude) ==")
        w.appendLine("The cross-line burst (RESEARCH.md §83-§84): a game's moneyline, spreads and totals are separate order books for one number (the margin, or the total). Buying YES at a lower line and NOT at")
        w.appendLine("a higher one pays at least $1 whatever happens, so when the pair costs under $1 after Novig's in-play taker fee (0.03 x P x (1 - P) a leg) it is a profit. After a play the makers re-quote")
        w.appendLine("the lines one after another, and for a moment a pair does cost under $1: a WINDOW. This recorder only watches (the read key's websocket; it cannot place or cancel an order).")
        w.appendLine("Each window line says when it opened (phone clock at the moment the push arrived), how long it lasted, the prices and depth at the best price level, and what a taker would have")
        w.appendLine("got on paper at three delays: none; typical (half the measured signed round trip + 15 ms + the measured push delay); slow (95th percentiles). 'both' = both legs still on offer at the")
        w.appendLine("seen price or better when the order would arrive; 'one leg' = a naked leg (charged its fee and a flat 2 cents a contract); 'missed' = neither. 'capped' = at Tj's own per-bet limit.")
        w.appendLine("Conservative on purpose: only the best price level, the chance the margin lands between the lines ignored, the push delay includes any clock difference between the phone and Novig.")
        w.appendLine("WHAT THIS PROVES: ${BurstStudy.PROVES}")
        w.appendLine("The recorder itself never places an order. A separate real-money trader exists (OFF by default, locked until this verdict says WORTH A TEST, then Tj's own switch); if it ever ran, its attempts are in")
        w.appendLine("REAL-MONEY TRADES below: that is the REAL answer to 'does it profit on this setup', which the paper trade can only estimate. Compare the two (did both legs fill as often as the paper said?).")
        w.appendLine("Rules: never turn the trader on or loosen a limit without Tj's say-so; the repo is public: no keys. Tj's questions: does it work on this setup, and does it work in sports other than the NFL?")
        w.appendLine()
        w.appendLine("== SETTINGS ==")
        w.appendLine("leagues: ${meta.leagues.sorted().joinToString(", ")} · per-bet limit used for 'capped': ${if (meta.capDollars > 0) "$" + "%.2f".format(Locale.US, meta.capDollars) else "none"} · made ${Date(nowMs)}")
        w.appendLine()
        w.appendLine("== REPORT ==")
        w.append(BurstStudy.report(records, latencyNote, meta.capDollars))
        w.appendLine()
        w.appendLine("== GAMES WATCHED (JSON lines) ==")
        for (g in records.filterIsInstance<GameRecord>()) w.appendLine(json.encodeToString(BurstLine.serializer(), g))
        w.appendLine()
        w.appendLine("== WINDOWS (JSON lines, oldest first) ==")
        for (x in records.filterIsInstance<WindowRecord>().sortedBy { it.openedMs }) w.appendLine(json.encodeToString(BurstLine.serializer(), x))
        if (trades.isNotEmpty()) {
            w.appendLine()
            w.appendLine("== REAL-MONEY TRADES (the trader's own record: one JSON line an attempt) ==")
            w.append(BurstTradeReport.section(trades))
        }
        w.appendLine()
        w.appendLine("== END OF FILE ==")
    }
}
