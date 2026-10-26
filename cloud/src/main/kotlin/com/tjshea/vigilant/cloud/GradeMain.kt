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
 *   --settings FILE the phone's `settings.json` from cloud-config: SportsGameOdds and PropLine are asked only while their switches are on there (PB5); without it, neither is
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
    // An API switched off on the phone is never called, here too (Tj, 2026-10-25, PB5): its switch is read from the settings the engine runs on; no settings file = off.
    val switches = v("--settings")?.let { f -> runCatching { json.parseToJsonElement(File(f).readText()) }.getOrNull() } as? kotlinx.serialization.json.JsonObject
    fun on(name: String): Boolean {
        val st = (switches?.get("settings") as? kotlinx.serialization.json.JsonObject) ?: switches ?: return false
        return (st[name] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
    }
    val sgoKeys = if (on("sgoPro")) CloudKeys.fromEnv(CloudKeys.SGO) else emptyList()
    if (sgoKeys.isNotEmpty()) {
        val usage = UsageMeter(JsonFileStore(File(journalDir.parentFile ?: File("."), "grade-usage.json"), UsageBook.serializer(), { UsageBook() }, json))
        val client = SportsGameOddsClient(http, KeyPool(QuotaPolicy.SGO, { sgoKeys }, usage), json)
        val sgoScores = SgoScores(client, { true }).also { it.enabled = true }
        scores = ChainedScores(sgoScores, scores)
        closes += SgoCloses(client, { true }).also { it.enabled = true }
    }
    val plKeys = if (on("usePropLine")) CloudKeys.fromEnv(CloudKeys.PROPLINE) else emptyList()
    if (plKeys.isNotEmpty()) {
        val usage = UsageMeter(JsonFileStore(File(journalDir.parentFile ?: File("."), "grade-usage-pl.json"), UsageBook.serializer(), { UsageBook() }, json))
        val pl = com.tjshea.vigilant.data.reference.PropLineClient(http, KeyPool(QuotaPolicy.PROPLINE, { plKeys }, usage), json, relayNovig = false, meter = usage)
        scores = ChainedScores(scores, com.tjshea.vigilant.data.tracker.PropLineScores(pl, { true }, json).also { it.enabled = true })
    }
    // ParlayAPI's Pinnacle closes (PB17, Tj 2026-10-25: "make sure all functions and features that were provided by apis which have since been paused or discontinued can be recovered ... clv studies"):
    // the sharpest close there is, asked FIRST, as the phone's Tracker does; the Business plan's credits cover it many times over. Only while ParlayAPI is switched on.
    val parlayKeys = if (on("useParlay")) CloudKeys.fromEnv(CloudKeys.PARLAY) else emptyList()
    if (parlayKeys.isNotEmpty()) {
        val usage = UsageMeter(JsonFileStore(File(journalDir.parentFile ?: File("."), "grade-usage-parlay.json"), UsageBook.serializer(), { UsageBook() }, json))
        val account = com.tjshea.vigilant.data.reference.ParlayAccount(http, { parlayKeys }, usage, json)
        runCatching { account.refresh(force = true) }
        closes.add(0, com.tjshea.vigilant.data.tracker.ParlayCloses(http, KeyPool(QuotaPolicy.PARLAY, { parlayKeys }, usage), json, historyDays = { account.historyDays() }).also { it.enabled = true })
        println("grade: ParlayAPI's Pinnacle closes asked first (${account.historyDays()} days back)")
    }
    closes += EspnCloses(http, json)
    closes += NovigTradeCloses(http, json)

    val study = ScanStudy(StudyJournal(journalDir), version = { "github-grader" })
    val scratch = File(journalDir.parentFile ?: File("."), "grading-scratch").apply { mkdirs() }
    // The engine's own orders whose line no scan ever logged (a bid filled where the ask sat over 2% above fair: 82 of 459 started orders were never graded, 2026-10-21) are added to the journal as bets of their
    // own, so the grader below gives them a result and a closing line like every other line. The lines it adds are kept with the rest of what this run added.
    v("--trades")?.let { path ->
        val ledger = File(path).takeIf { it.isFile }?.readText()?.let(TradeLedger::parse)
        if (ledger != null) {
            val journal = StudyJournal(journalDir)
            val known = HashSet<Pair<String, String>>()
            for (day in journal.days()) for (sb in journal.fold(day, withSights = false).values) known += sb.bet.marketId to sb.bet.outcomeId
            val orders = ledger.entries.filter { it.kind != "live" && it.mode == "LIVE" }.map {
                ScanStudy.OrderLine(it.id, it.atMs, it.league, it.event, it.startsTs, it.marketLabel, it.selection, it.marketId, it.outcomeId, it.eventId, it.avgPrice, it.fair, it.evPercent, if (it.note.startsWith("bid")) "bid" else "bet")
            }
            println("grade: ${study.addOrders(orders, known)} of the engine's orders had no line in the journal and were added")
        }
    }
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
            val studied = HashSet(byOutcome.keys)
            // PX14: the orders the study never logged (most bid fills) are settled from Novig's own record of the outcome (its public market read, no key).
            val ask = TradeSettle.askNovig(ledger.entries, { m, o -> (m to o) in studied }, System.currentTimeMillis())
            var fromNovig = 0
            var novigFailed = 0
            if (ask.isNotEmpty()) {
                val novig = com.tjshea.vigilant.data.novig.NovigPublicClient(okhttp3.OkHttpClient(), kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                for (mid in ask.map { it.marketId }.distinct()) {
                    val market = try { novig.market(mid) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { novigFailed++; null } ?: continue
                    for (oc in market.outcomes) TradeSettle.fromNovig(oc.status)?.let { if (byOutcome.putIfAbsent(mid to oc.outcomeId, it) == null) fromNovig++ }
                }
            }
            val settled = TradeSettle.settle(ledger.entries) { m, o -> byOutcome[m to o] } + TradeSettle.carried(ledger.entries) { m, o -> (m to o) in byOutcome }
            out.parentFile?.mkdirs()
            out.writeText(TradeSettle.File.text(TradeSettle.File(System.currentTimeMillis(), settled)))
            val done = settled.mapTo(HashSet()) { it.id }
            val open = ledger.entries.count { e -> System.currentTimeMillis() - e.startsTs >= TradeSettle.ASK_AFTER_START_MS && e.id !in done }
            println("grade: ${settled.size} of the engine's orders settled (${ask.size} asked of Novig's record, $fromNovig outcomes settled by it, $novigFailed reads failed); $open orders 3 h+ past their start still without a result")
        } else println("grade: no usable trades.json")
    }
}
