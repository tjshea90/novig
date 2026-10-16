package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.cloud.CloudKeys
import com.tjshea.vigilant.data.cloud.TradeLedger
import com.tjshea.vigilant.data.cloud.TradeSettle
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.SportsGameOddsClient
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.study.ScanStudy
import com.tjshea.vigilant.data.study.StudyJournal
import com.tjshea.vigilant.data.tracker.ChainedScores
import com.tjshea.vigilant.data.tracker.CloseSource
import com.tjshea.vigilant.data.tracker.EspnCloses
import com.tjshea.vigilant.data.tracker.FreeScores
import com.tjshea.vigilant.data.tracker.NovigTradeCloses
import com.tjshea.vigilant.data.tracker.ScoreSource
import com.tjshea.vigilant.data.tracker.SgoCloses
import com.tjshea.vigilant.data.tracker.SgoScores
import com.tjshea.vigilant.data.vigilantHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The daily grader of the engine's scan study (Tj, 2026-10-15; TCL20e: "log every scanned +EV line ... closing odds and result won/lost/pushed filled in by a scheduled GitHub grader"). It runs the app's OWN grading
 * ([ScanStudy.settle]: final scores and box scores, then the closing line from every source that keeps history) over the journal the engine's runs wrote, and keeps only what it ADDED, so a result is a line of its own and no
 * earlier file is ever rewritten. The engine's own orders ([TradeLedger]) are settled from the same results ([TradeSettle]).
 *
 *   --journal DIR   the assembled journal (`study-<day>.jsonl`, every run's segment in order, earlier grades last); results are appended to it
 *   --new DIR       receives `grades-<day>.jsonl`: the lines this run added
 *   --trades FILE   the engine's `trades.json` (optional)
 *   --results FILE  writes the settled orders here (`trade-results.json`) when --trades is given
 *
 * Free sources only unless `SGO_API_KEY` is present (SportsGameOdds' scores and closes are flat-rate on the Pro plan). It places no order and holds no trading key.
 */
fun main(args: Array<String>) { runBlocking { gradeMain(args) } }

private suspend fun gradeMain(args: Array<String>) {
    fun v(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val journalDir = File(v("--journal") ?: error("--journal DIR"))
    val newDir = File(v("--new") ?: error("--new DIR")).apply { mkdirs() }
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val http = vigilantHttpClient()
    val before = journalDir.listFiles { f -> f.isFile && f.name.startsWith(StudyJournal.PREFIX) && f.name.endsWith(StudyJournal.SUFFIX) }.orEmpty().associate { it.name to it.length() }

    var scores: ScoreSource = FreeScores(http, json)
    val closes = ArrayList<CloseSource>()
    val sgoKeys = CloudKeys.fromEnv(CloudKeys.SGO)
    if (sgoKeys.isNotEmpty()) {
        val usage = UsageMeter(JsonFileStore(File(journalDir.parentFile ?: File("."), "grade-usage.json"), UsageBook.serializer(), { UsageBook() }, json))
        val client = SportsGameOddsClient(http, KeyPool(QuotaPolicy.SGO, { sgoKeys }, usage), json)
        val sgoScores = SgoScores(client, { true }).also { it.enabled = true }
        scores = ChainedScores(sgoScores, scores)
        closes += SgoCloses(client, { true }).also { it.enabled = true }
    }
    val plKeys = CloudKeys.fromEnv(CloudKeys.PROPLINE)
    if (plKeys.isNotEmpty()) {
        val usage = UsageMeter(JsonFileStore(File(journalDir.parentFile ?: File("."), "grade-usage-pl.json"), UsageBook.serializer(), { UsageBook() }, json))
        val pl = com.tjshea.vigilant.data.reference.PropLineClient(http, KeyPool(QuotaPolicy.PROPLINE, { plKeys }, usage), json, relayNovig = false, meter = usage)
        scores = ChainedScores(scores, com.tjshea.vigilant.data.tracker.PropLineScores(pl, { true }, json).also { it.enabled = true })
    }
    closes += EspnCloses(http, json)
    closes += NovigTradeCloses(http, json)

    val study = ScanStudy(StudyJournal(journalDir), version = { "github-grader" })
    val scratch = File(journalDir.parentFile ?: File("."), "grading-scratch").apply { mkdirs() }
    val report = study.settle(scores, closes, emptyList(), scratch)
    println("grade: looked ${report.looked}, graded ${report.graded}, closed ${report.closed} over ${report.days} day(s)" + (study.lastProblem?.let { ", problem: $it" } ?: ""))

    // What this run added, per day, as its own file.
    var added = 0
    for (f in journalDir.listFiles { f -> f.isFile && f.name.startsWith(StudyJournal.PREFIX) && f.name.endsWith(StudyJournal.SUFFIX) }.orEmpty()) {
        val from = before[f.name] ?: 0L
        if (f.length() <= from) continue
        val bytes = f.inputStream().use { it.skip(from); it.readBytes() }
        File(newDir, f.name.replace(StudyJournal.PREFIX, "grades-")).writeBytes(bytes)
        added++
    }
    println("grade: $added day file(s) grew")

    // The engine's own orders, settled from the same results.
    v("--trades")?.let { path ->
        val ledger = File(path).takeIf { it.isFile }?.readText()?.let(TradeLedger::parse)
        val out = File(v("--results") ?: error("--results FILE"))
        if (ledger != null) {
            val journal = StudyJournal(journalDir)
            val byOutcome = HashMap<Pair<String, String>, TradeSettle.Outcome>()
            for (day in journal.days()) for (sb in journal.fold(day, withSights = false).values) {
                val b = sb.bet
                if (b.marketId.isNotBlank() && b.outcomeId.isNotBlank() && b.status != com.tjshea.vigilant.data.tracker.BetStatus.PENDING) byOutcome[b.marketId to b.outcomeId] = TradeSettle.Outcome(b.status, b.settleValue)
            }
            val settled = TradeSettle.settle(ledger.entries) { m, o -> byOutcome[m to o] }
            out.parentFile?.mkdirs()
            out.writeText(TradeSettle.File.text(TradeSettle.File(System.currentTimeMillis(), settled)))
            println("grade: ${settled.size} of the engine's orders settled")
        } else println("grade: no usable trades.json")
    }
}
