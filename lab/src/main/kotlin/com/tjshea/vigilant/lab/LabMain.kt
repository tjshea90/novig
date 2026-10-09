package com.tjshea.vigilant.lab

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.live.Fetched
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.lab.BidLab
import com.tjshea.vigilant.data.novig.lab.BidLabBid
import com.tjshea.vigilant.data.novig.lab.BidLabEvent
import com.tjshea.vigilant.data.novig.lab.LabExport
import com.tjshea.vigilant.data.novig.lab.LabGrade
import com.tjshea.vigilant.data.novig.lab.LabRecord
import com.tjshea.vigilant.data.novig.lab.LabRecorder
import com.tjshea.vigilant.data.novig.lab.gh.EdgeLog
import com.tjshea.vigilant.data.novig.lab.gh.EdgeRow
import com.tjshea.vigilant.data.novig.lab.gh.SgoCloseRow
import com.tjshea.vigilant.data.novig.lab.gh.SgoLabBoards
import com.tjshea.vigilant.data.novig.lab.gh.SgoTape
import com.tjshea.vigilant.data.novig.lab.gh.SgoTick
import com.tjshea.vigilant.data.novig.lab.gh.feedLab
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.SgoGamesSource
import com.tjshea.vigilant.data.reference.SgoPropsSource
import com.tjshea.vigilant.data.reference.SportsGameOddsClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.vigilantHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.time.Instant

/**
 * The GitHub research lab (`.github/workflows/lab-record.yml`): the phone's paper lab, paper bids and scanner running in a GitHub Actions job against public data, so Claude gets data with no phone on.
 *
 *   --minutes N        how long to record (default 60)
 *   --out DIR          where the journals and the report go (default `out`)
 *   --leagues A,B      Novig league names (default the phone's lab leagues)
 *   --scan-seconds N   pause between scanner passes (default 60)
 *   --tape-seconds N   pause between SportsGameOdds tape reads (default 30)
 *
 * Environment: `SGO_API_KEY` (one or more SportsGameOdds keys, comma separated; without one the lab still runs on Novig, ESPN, Kalshi and Polymarket and says so). It **places no order**: it is given the
 * public Novig client only, no trading key, and nothing in the code path signs a request.
 */
fun main(args: Array<String>) = runBlocking {
    val a = Args.parse(args)
    val out = File(a.out).apply { mkdirs() }
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val http = vigilantHttpClient()
    val usage = UsageMeter(JsonFileStore(File(out, "usage.json"), UsageBook.serializer(), { UsageBook() }, json))
    val novig = NovigPublicClient(http, json, usage = usage)
    val keys = System.getenv("SGO_API_KEY").orEmpty().split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    val sgo = SportsGameOddsClient(http, KeyPool(QuotaPolicy.SGO, { keys }, usage), json)
    val dir = File(out, "lab")
    val labJ = DayJournal(dir, "lab", LabRecord.serializer()) { it.atMs }
    val gradeJ = DayJournal(dir, "lab-grade", LabGrade.serializer()) { it.atMs }
    val bidJ = DayJournal(dir, "bidlab", BidLabBid.serializer()) { it.atMs }
    val eventJ = DayJournal(dir, "bidlab-event", BidLabEvent.serializer()) { it.atMs }
    val edgeJ = DayJournal(dir, "edge", EdgeRow.serializer()) { it.atMs }
    val tickJ = DayJournal(dir, "sgo-tick", SgoTick.serializer()) { it.atMs }
    val closeJ = DayJournal(dir, "sgo-close", SgoCloseRow.serializer()) { it.atMs }

    val bidLab = BidLab(trades = { id -> novig.trades(id, 60) }, market = { id -> novig.market(id) }, bidJournal = bidJ, eventJournal = eventJ)
    // Paper bids still resting or waiting for a result in the journals of earlier runs (restored by the workflow) carry on.
    bidLab.restore(bidJ.readAll(), eventJ.readAll(), System.currentTimeMillis())
    val edge = EdgeLog(edgeJ)
    val tape = SgoTape(sgo, tickJ, closeJ).also { it.resume(closeJ.readAll()) }

    val settings = ScanSettings(
        leagues = a.leagues.toSet(), includeLive = true, sgoPro = keys.isNotEmpty(), sgoExtraBooks = true, sgoAltLines = true,
        useKalshi = true, usePolymarket = true,
    )
    val sgoGames = SgoGamesSource(sgo)
    val boards = SgoLabBoards(sgoGames, { settings })
    val sources: List<ReferenceSource> =
        (if (keys.isNotEmpty()) listOf(sgoGames, SgoPropsSource(sgo)) else emptyList()) + listOf(KalshiClient(http, json, altBaseUrl = KalshiClient.ALT_URL, usage = usage), PolymarketClient(http, json, usage = usage))

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val lab = LabRecorder(
        scope = scope, source = novig,
        fetch = { url -> espn(http, url) },
        altQuotes = { ev -> if (keys.isEmpty()) emptyList() else boards.quotes(ev) },
        journal = labJ, gradeJournal = gradeJ, bidLab = bidLab,
    )
    val started = System.currentTimeMillis()
    val until = started + a.minutes * 60_000L
    val notes = java.util.concurrent.CopyOnWriteArrayList<String>()
    fun note(s: String) { println("${Instant.now()} $s"); notes += "${Instant.now()} $s" }
    note("GitHub lab starting: ${a.minutes} min, leagues ${a.leagues.joinToString(",")}, SportsGameOdds keys: ${keys.size}")
    if (keys.isEmpty()) note("No SGO_API_KEY: running on Novig, ESPN, Kalshi and Polymarket only")

    lab.start(a.leagues.toSet())
    val scanner = Scanner(novig)
    var scans = 0
    var lastLines = 0
    var lastEdge = 0
    scope.launch {
        while (isActive) {
            val t0 = System.currentTimeMillis()
            runCatching {
                val report = scanner.scan(settings, sources)
                val ops = report.result.opportunities.let { _ -> report.result.games.flatMap { it.outcomes } }
                val (lines, rows) = feedLab(ops, bidLab, edge, System.currentTimeMillis())
                lastLines = lines; lastEdge = rows; scans++
                note("scan $scans: ${ops.size} priced outcomes, $lines paper-bid lines, $rows edge rows (${(System.currentTimeMillis() - t0) / 1000}s)")
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; note("scan failed: ${it.javaClass.simpleName}: ${it.message}") }
            delay((a.scanSeconds * 1000L - (System.currentTimeMillis() - t0)).coerceAtLeast(5_000L))
        }
    }
    scope.launch {
        while (isActive) {
            runCatching { bidLab.poll(System.currentTimeMillis()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            delay(45_000L)
        }
    }
    if (keys.isNotEmpty()) {
        scope.launch {
            var cycle = 0
            while (isActive) {
                val now = System.currentTimeMillis()
                runCatching {
                    val n = tape.cycle(a.leagues, now)
                    if (cycle % 60 == 0) note("SGO tape: $n price changes (total ${tape.ticks})")
                    if (cycle % 60 == 0) tape.closes(a.leagues, now)   // about every 30 minutes at the default pace
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; note("SGO tape failed: ${it.javaClass.simpleName}: ${it.message}") }
                cycle++
                delay(a.tapeSeconds * 1000L)
            }
        }
    }
    while (System.currentTimeMillis() < until) delay(5_000L)
    lab.stop()
    scope.cancel()
    if (keys.isNotEmpty()) runCatching { tape.closes(a.leagues, System.currentTimeMillis()) }

    val now = System.currentTimeMillis()
    val status = buildList {
        add("GitHub Actions run: ${a.minutes} min from ${Instant.ofEpochMilli(started)}; scanner passes: $scans")
        lab.status.value.let { add("paper lab: ${it.cycles} cycles, ${it.games} live games followed, ${it.tail} tail / ${it.alt} alt / ${it.covers} cover would-be bets (last pass), problem: ${it.problem ?: "none"}") }
        val (up, filledWaiting, posted) = bidLab.counts()
        add("paper bids: $posted posted so far, $up resting, $filledWaiting filled and waiting, ${bidLab.filled} fills")
        add("edge log: ${edge.written} rows this run")
        if (keys.isNotEmpty()) addAll(tape.report()) else add("SportsGameOdds: no key this run")
        addAll(notes.takeLast(40))
    }
    val file = File(out, LabExport.fileName("github", now).replace("vigilant-research-", "github-lab-"))
    file.bufferedWriter().use { w ->
        LabExport.write(
            w, LabExport.Meta("github-actions", "GitHub Actions (ubuntu), no phone", "GitHub lab · SportsGameOdds ${if (keys.isNotEmpty()) "ON (${keys.size} key${if (keys.size == 1) "" else "s"})" else "off"} · Novig public API only, no orders"),
            status, labJ.readAll(), gradeJ.readAll(), bidJ.readAll(), eventJ.readAll(), now,
        )
    }
    note("report written: ${file.path}")
}

private suspend fun espn(http: okhttp3.OkHttpClient, url: String): Fetched? {
    val t0 = System.currentTimeMillis()
    return http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").get().build()).execute().use { r ->
        if (!r.isSuccessful) null else Fetched(r.body?.string().orEmpty(), System.currentTimeMillis() - t0)
    }
}

internal data class Args(val minutes: Int, val out: String, val leagues: List<String>, val scanSeconds: Int, val tapeSeconds: Int) {
    companion object {
        val DEFAULT_LEAGUES = listOf("NFL", "NCAAF", "NBA", "WNBA", "NCAAB", "NHL", "MLB")

        fun parse(args: Array<String>): Args {
            fun v(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
            val known = Leagues.ALL.map { it.novigName }.toSet()
            return Args(
                minutes = v("--minutes")?.toIntOrNull()?.coerceIn(1, 330) ?: 60,
                out = v("--out") ?: "out",
                leagues = (v("--leagues")?.split(',')?.map { it.trim().uppercase() } ?: DEFAULT_LEAGUES).filter { it in known }.ifEmpty { DEFAULT_LEAGUES },
                scanSeconds = v("--scan-seconds")?.toIntOrNull()?.coerceAtLeast(20) ?: 60,
                tapeSeconds = v("--tape-seconds")?.toIntOrNull()?.coerceAtLeast(15) ?: 30,
            )
        }
    }
}
