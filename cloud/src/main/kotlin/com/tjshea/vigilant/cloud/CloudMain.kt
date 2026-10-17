package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.cloud.AddressWatch
import com.tjshea.vigilant.data.cloud.CloudBids
import com.tjshea.vigilant.data.cloud.CloudConfig
import com.tjshea.vigilant.data.cloud.CloudEngine
import com.tjshea.vigilant.data.cloud.CloudFeed
import com.tjshea.vigilant.data.cloud.CloudKeys
import com.tjshea.vigilant.data.cloud.CloudNovig
import com.tjshea.vigilant.data.cloud.CloudNovigPort
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.cloud.CloudRanking
import com.tjshea.vigilant.data.cloud.CloudSources
import com.tjshea.vigilant.data.cloud.CloudStatus
import com.tjshea.vigilant.data.cloud.CloudTrader
import com.tjshea.vigilant.data.cloud.TradeLedger
import com.tjshea.vigilant.data.cloud.TradeSettle
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.cloud.ApiGovernor
import com.tjshea.vigilant.data.cloud.CloudDashboard
import com.tjshea.vigilant.data.cloud.CloudMessages
import com.tjshea.vigilant.data.cloud.CloudOverrides
import com.tjshea.vigilant.data.cloud.CloudUsage
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.lab.gh.EdgeLog
import com.tjshea.vigilant.data.novig.lab.gh.EdgeRow
import com.tjshea.vigilant.data.novig.trading.maker.BidCandidates
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.scanner.ApiChain
import com.tjshea.vigilant.data.scanner.Scanner
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
import java.io.File
import java.time.Instant

/**
 * The GitHub engine (`.github/workflows/cloud-scan.yml`): Vigilant's scanner, run on a GitHub runner for the phone.
 *
 *   --minutes N     how long this job runs before it hands over to the next (default 345; a job is capped at 6 h)
 *   --out DIR       journals and logs (default `out`)
 *   --pub DIR       the publishing work tree (default `pub`)
 *   --cfg DIR       a git work tree of the repository, to read the `cloud-config` branch with (default `.`)
 *
 * Environment: the API keys as repository secrets ([CloudKeys.ALL], optional, each only used when its API is switched on in the phone's settings), `GITHUB_TOKEN`, `GITHUB_REPOSITORY`, `GITHUB_RUN_ID`.
 * It places no order unless the phone's trading switch is LIVE and the Vigilant subaccount's trading key was sent (`NOVIG_TRADE_KEY_*`, [CloudNovigPort]); PAPER only records what it would have ordered. Never a management key.
 */
/** The scan study also logs lines this close to break-even (EV at or above -2%), shown on the list or not. */
private const val WIDE_FLOOR = -0.02

fun main(args: Array<String>) = runBlocking {
    fun v(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val minutes = v("--minutes")?.toIntOrNull()?.coerceIn(1, 350) ?: 345
    val out = File(v("--out") ?: "out").apply { mkdirs() }
    val pubDir = File(v("--pub") ?: "pub")
    val cfgDir = File(v("--cfg") ?: ".")
    val repo = System.getenv("GITHUB_REPOSITORY").orEmpty().ifBlank { "tjshea90/novig" }
    val token = System.getenv("GITHUB_TOKEN").orEmpty()
    val runId = System.getenv("GITHUB_RUN_ID").orEmpty()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val http = vigilantHttpClient()
    val usage = UsageMeter(JsonFileStore(File(out, "usage.json"), UsageBook.serializer(), { UsageBook() }, json))
    val novig = NovigPublicClient(http, json, usage = usage)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sources = CloudSources(http, json, usage, scope, File(out, "state").apply { mkdirs() }, CloudKeys::fromEnv)
    val scanner = Scanner(novig)
    val edge = EdgeLog(DayJournal(File(out, "cloud"), "edge", EdgeRow.serializer()) { it.atMs })
    // The volume journal (Tj, 2026-10-15: balance the best +EV bets with a decent number of fills): every trading pass's counts and why bets were held back, so a session can tune volume against edge with data.
    val passJournal = DayJournal(File(out, "cloud"), "tradepass", com.tjshea.vigilant.data.cloud.CloudTrader.PassRow.serializer()) { it.atMs }
    val git = Git(token)
    val log = Log(File(out, "cloud-engine.log"))
    // The engine's own Novig key (Tj, 2026-10-15): a read-only key sent as two secrets. Without them the engine reads the public routes as before; with them it reads through the signed route and keeps the key's websocket.
    val novigKeyId = CloudKeys.rawEnv(CloudKeys.NOVIG_KEY_ID)
    val novigKeyPem = CloudKeys.rawEnv(CloudKeys.NOVIG_KEY_PEM)
    // ParlayAPI's plan per key, read now and hourly (free): an upgraded key is found and used before the free ones.
    scope.launch {
        while (isActive) {
            runCatching { sources.refreshParlayAccounts() }
            log("ParlayAPI plans: " + sources.parlayPlans())
            runCatching { sources.refreshPropLineAccounts() }
            log("PropLine plans: " + sources.propLinePlans())
            delay(60 * 60_000L)
        }
    }
    // The settings of the scan in progress, for the PropLine +EV sweep below (it follows the phone's leagues).
    val latestSettings = java.util.concurrent.atomic.AtomicReference<com.tjshea.vigilant.data.scanner.ScanSettings?>(null)
    // Every line the last finished scan priced (not only the listed ones): the PropLine +EV rows are matched to them so the phone can open the Bet sheet on the same Novig bet.
    val latestPriced = java.util.concurrent.atomic.AtomicReference<List<com.tjshea.vigilant.data.scanner.Opportunity>>(emptyList())
    val novigKeyed = if (novigKeyId.isNotEmpty() && novigKeyPem.isNotEmpty()) CloudNovig(novig, http, json, scope, novigKeyId, novigKeyPem, log = log::invoke) else null
    val publisher = Publisher(pubDir, repo, token, git, log::tail) { policy -> CloudKeys.envFor(policy.id)?.let { CloudKeys.fromEnv(it) }.orEmpty().map(CloudUsage::keyId) }
    // PropLine's own +EV read at Novig (Tj, 2026-10-16, TCL56): with a paid key, every 15 minutes (and only while the key is within the day's share of its requests) the pregame games of the picked
    // leagues are asked for their +EV lines at Novig; the list goes to cloud-feed for the phone's PropLine screen, and every sweep's rows to a journal that the archive loop keeps for research.
    val evJournal = DayJournal(File(out, "cloud"), "propline-ev", com.tjshea.vigilant.data.reference.PlEvRow.serializer()) { it.seenAtMs }
    scope.launch {
        delay(90_000L)
        while (isActive) {
            val s = latestSettings.get()
            val now = System.currentTimeMillis()
            if (s != null && s.usePropLine && sources.propLinePaid() && sources.propLineHasRoom(now)) {
                runCatching { sources.propLineEvSweep(s, now, latestPriced.get()) }.onSuccess { f ->
                    publisher.stagePropLineEv(f)
                    runCatching { evJournal.appendAll(f.rows) }
                    log("PropLine +EV: ${f.rows.size} lines at Novig from ${f.games} games (${f.requests} requests, ${f.tier})" + (if (f.note.isNotBlank()) " · ${f.note}" else ""))
                }.onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; log("PropLine +EV sweep failed: ${e.message?.take(120) ?: e.javaClass.simpleName}") }
            }
            delay(15 * 60_000L)
        }
    }
    val config = ConfigReader(git, cfgDir)
    val started = System.currentTimeMillis()
    val until = started + minutes * 60_000L
    log("cloud engine starting: $minutes min, run $runId, keys present: " + CloudKeys.ALL.filter { CloudKeys.fromEnv(it).isNotEmpty() }.joinToString(",") { n -> CloudKeys.fromEnv(n).size.let { c -> if (c > 1) "$n($c keys)" else n } }.ifBlank { "none (public feeds only)" } + " · Novig read-only key: " + (if (novigKeyed != null) "present" else "none (public routes)") + " · " + sources.kalshiNote())

    novigKeyed?.problem?.let { log("Novig key: $it") }
    // The APIs' measured ranking survives the handover: the previous job's file is read back before the first scan ([CloudRanking]), and this job writes its own with every push.
    runCatching { PreviousFeed.read(git, cfgDir, "ranking.json") }.getOrNull()?.let { text ->
        val seeded = CloudRanking.decode(text, System.currentTimeMillis())
        if (seeded.isNotEmpty()) { ApiChain.measures = seeded; log("ranking: ${seeded.size} measurements carried over from the previous job") }
    }

    // What each key spent in the previous job (Tj, 2026-10-15): read back so a handover does not forget a daily allowance already spent, and so the phone's meters keep their totals.
    val allKeys = (CloudKeys.ALL.flatMap { CloudKeys.fromEnv(it) }).distinct()
    runCatching { PreviousFeed.read(git, cfgDir, "usage.json") }.getOrNull()?.let { text ->
        CloudUsage.decode(text)?.let { f ->
            runBlocking { usage.seed(CloudUsage.rekey(f.book, allKeys)) }
            log("usage: carried over from the previous job (${f.book.providers.size} providers)")
        }
    }
    // The engine's own trading (TCL20d): paper orders unless the phone's switch says live AND the trading key was sent; its ledger is carried from job to job in `trades.json`.
    // The trading key (Tj sent it from the phone, TCL20c) only ever makes a port; whether anything is ordered is the phone's three-way switch (off / paper / live), read with every list.
    val tradeKeyId = CloudKeys.rawEnv(CloudKeys.TRADE_KEY_ID)
    val tradeKeyPem = CloudKeys.rawEnv(CloudKeys.TRADE_KEY_PEM)
    var traderRef: CloudTrader? = null
    var tradingClient: NovigTradingClient? = null
    val tradePort = if (tradeKeyId.isNotEmpty() && tradeKeyPem.isNotEmpty()) {
        runCatching { NovigSignedClient(http, json, PemSigningKey(tradeKeyId, tradeKeyPem), "https://api.novig.com") }.getOrNull()?.let { signer ->
            val tc = NovigTradingClient(signer, json).also { tradingClient = it }
            CloudNovigPort(tc, tradeKeyId, { traderRef?.ledger?.entries?.filter { !it.note.startsWith("bid") }?.mapTo(HashSet()) { it.id } ?: emptySet() }, log = log::invoke)
        }
    } else null
    log("trading: ${if (tradePort != null) "the trading key is present (orders possible only if the phone's switch says live)" else "no trading key: paper only"}")
    val trader = CloudTrader(unavailable = { o, now -> sources.unavailable(o, now) }, port = tradePort).also { traderRef = it }
    runCatching { PreviousFeed.read(git, cfgDir, "trades.json") }.getOrNull()?.let { text ->
        TradeLedger.parse(text)?.let { trader.load(it); log("trades: ${it.entries.size} entries carried over from the previous job") }
    }
    var tradesText = ""
    fun stageLedger(now: Long) {
        val text = TradeLedger.text(trader.ledger)
        if (text != tradesText) { tradesText = text; publisher.stageTrades(text, now) }
    }
    // The engine's own scan study (TCL20e): every bet each finished scan lists, in the app's journal format, in the folder the archive loop gzips to the day's cloud-archive Release (study-<day>-run<id>.jsonl.gz).
    // Each run's file is a segment of its own; the daily grader (engine-grade.yml) puts the segments together, finds results and closes, and the daily digest reads them beside the phone's.
    val study = com.tjshea.vigilant.data.study.ScanStudy(com.tjshea.vigilant.data.study.StudyJournal(File(out, "cloud")), version = { "github-engine" })
    // The grader's settled orders (engine-results branch, TCL20e): applied at the start and every 30 minutes, so the paper wallet and the day's figures follow the real results.
    var resultsAt = 0L
    fun applyTradeResults() {
        runCatching { PreviousFeed.read(git, cfgDir, "trade-results.json", "engine-results") }.getOrNull()?.let { text ->
            TradeSettle.File.parse(text)?.let { f ->
                val open = trader.ledger.entries.filter { it.status == "OPEN" || it.status == "UNCONFIRMED" }.mapTo(HashSet()) { it.id }
                var n = 0
                for (r in f.settled) if (r.id in open) { trader.settle(r.id, r.status, r.pnl); n++ }
                if (n > 0) log("trades: $n of the engine's orders settled by the grader")
            }
        }
    }
    val flusher = scope.launch {
        var usageAt = 0L
        while (isActive) {
            val now = System.currentTimeMillis()
            // The engine's API usage goes out with the list, at most every 30 s (keys by id, never in clear: [CloudUsage]).
            if (now - resultsAt >= 30 * 60_000L) { resultsAt = now; applyTradeResults() }
            if (now - usageAt >= 30_000L) { usageAt = now; runCatching { publisher.stageUsage(CloudUsage.encode(usage.flow.value, runId, now, allKeys), now) } }
            runCatching { publisher.flush() }
            delay(4_000L)
        }
    }
    // Novig refusing THIS runner's address ends the job early so the workflow draws another runner (AddressWatch, Tj 2026-10-15); the streak is carried from job to job in the status file.
    val prevStatus = runCatching { PreviousFeed.read(git, cfgDir, "status.json") }.getOrNull()?.let { CloudStatus.parse(it) }
    val addressWatch = novigKeyed?.let { nk -> AddressWatch(started, prevStatus?.addressHandovers ?: 0, prevStatus?.addressHandoverAtMs ?: 0L) { now -> nk.addressRefused(now) } }
    // Messages for Tj and settings requests for the phone go to the cloud-config branch as one small file each (CloudMessages / CloudOverrides), by the same REST client the phone uses, with the job's own token.
    val ghClient = com.tjshea.vigilant.data.cloud.CloudClient(http, { token.ifBlank { null } }, repo)
    val postMessage: suspend (CloudMessages.Message) -> Unit = { m ->
        ghClient.putFile(CloudMessages.BRANCH, "${CloudMessages.DIR}/${CloudMessages.fileName(m)}", CloudMessages.encode(m).toByteArray(Charsets.UTF_8), "message: ${m.title.take(60)}")?.let { log("message not saved: $it") }
    }
    val postOverride: suspend (CloudOverrides.Request) -> Unit = { r ->
        ghClient.putFile(CloudOverrides.BRANCH, "${CloudOverrides.DIR}/${CloudOverrides.fileName(r)}", CloudOverrides.encode(r).toByteArray(Charsets.UTF_8), "settings request: ${r.apis}")?.let { log("settings request not saved: $it") }
    }
    val governor = ApiGovernor().also { g -> prevStatus?.governorOff?.let { g.seedOff(it, started) } }
    // The engine's bid desk (TCL31): the phone's own desk on the runner, with the trading key. Its record of bids and fills is carried from job to job in `desk.json` and rebuilt into the desk's files here.
    val deskDir = File(out, "desk").apply { mkdirs() }
    if (tradingClient != null) runCatching { PreviousFeed.read(git, cfgDir, "desk.json") }.getOrNull()?.let { text ->
        com.tjshea.vigilant.data.cloud.DeskFile.parse(text)?.let { f ->
            val keep = com.tjshea.vigilant.data.cloud.DeskFile.trim(f.bids, System.currentTimeMillis())
            File(deskDir, "maker.json").writeText(Json { encodeDefaults = false }.encodeToString(com.tjshea.vigilant.data.cloud.DeskFile.bidsSerializer, keep))
            File(deskDir, "tracker.json").writeText(Json { encodeDefaults = false }.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.tjshea.vigilant.data.tracker.TrackedBet.serializer()), f.fills))
            log("bids: ${keep.size} bids and ${f.fills.size} fills carried over from the previous job")
        }
    }
    // One instance of each store, shared by the desk and by the file the phone reads (a second instance on the same file would keep a stale copy).
    val deskStore = com.tjshea.vigilant.data.novig.trading.maker.MakerStore(File(deskDir, "maker.json"))
    val deskTracker = com.tjshea.vigilant.data.tracker.BetTracker(File(deskDir, "tracker.json"))
    val bidRunner: com.tjshea.vigilant.data.cloud.CloudBidRunner? = tradingClient?.let { tc ->
        val desk = com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(
            tc, deskTracker, deskStore,
            cancelStrays = false, newClientId = com.tjshea.vigilant.data.novig.trading.maker.MakerDesk::engineClientId,
        )
        com.tjshea.vigilant.data.cloud.CloudBidRunner(
            desk, com.tjshea.vigilant.data.cloud.CloudBidLane(novig), tradePort!!, tradeMarkets = { trader.openMarkets() },
            onFills = { bets, all ->
                val now = System.currentTimeMillis()
                val lines = ArrayList<String>()
                for (bet in bets) {
                    val bid = all.firstOrNull { it.betId == bet.id || (it.orderId != null && it.orderId == bet.orderId) } ?: continue
                    trader.record(com.tjshea.vigilant.data.cloud.CloudBidRunner.toEntry(bid, bet, now))
                    lines += "${bid.selection} (${bid.eventName}): ${bid.filled} of ${bid.contracts} at ${String.format(java.util.Locale.US, "%.0f", bid.price * 100)}c"
                }
                if (lines.isNotEmpty()) {
                    stageLedger(now)
                    runCatching { postMessage(CloudMessages.Message("bidfill-${bets.first().id.take(12)}-$now", now, "engine", "bet", "Bid filled: ${lines.size} bid${if (lines.size == 1) "" else "s"}", lines.joinToString("\n"), 1, "CLOUD")) }
                }
            },
            tell = { title, body -> runCatching { postMessage(CloudMessages.Message("bidstop-${System.currentTimeMillis()}", System.currentTimeMillis(), "engine", "alert", title, body.take(400), 1, "CLOUD")) } },
            recentTrades = { id -> novig.trades(id) }, log = log::invoke,
        )
    }
    var deskText = ""
    // Every bid's whole life (Tj, 2026-10-16: optimise bids over time from analysis of all of them): an ended bid is written once, a couple of minutes after it ended (a late fill still lands), to the day's `bids` journal,
    // which the archive loop gzips to the cloud-archive Release like the other journals. A bid carried into the next job may be written twice; the dataset tool keeps the newest.
    val bidJournal = DayJournal(File(out, "cloud"), "bids", com.tjshea.vigilant.data.novig.trading.maker.MakerBid.serializer()) { it.postedAtMs }
    val bidsJournaled = HashSet<String>()
    // The runner behind a thin wrapper that puts the desk's record on cloud-feed after each pass (only when the bids or fills changed: the file is the phone's screen of the engine's bids).
    val bidFacade: com.tjshea.vigilant.data.cloud.EngineBids? = bidRunner?.let { r ->
        suspend fun stageDesk(now: Long) {
            val bids = com.tjshea.vigilant.data.cloud.DeskFile.trim(deskStore.all(), now)
            for (b in deskStore.all()) if (b.status.ended && now - (b.endedAtMs ?: b.postedAtMs) > 120_000L && bidsJournaled.add(b.clientId)) runCatching { bidJournal.append(b) }
            val fills = deskTracker.all().filter { it.createdAtMs >= now - 7L * 24 * 3_600_000L }
            val body = com.tjshea.vigilant.data.cloud.DeskFile(0L, "", "", bids, fills)
            val text = com.tjshea.vigilant.data.cloud.DeskFile.text(body)
            if (text != deskText) { deskText = text; publisher.stageDesk(com.tjshea.vigilant.data.cloud.DeskFile.text(body.copy(atMs = now, note = r.status()?.note.orEmpty(), stopped = r.status()?.stopped.orEmpty())), now) }
        }
        object : com.tjshea.vigilant.data.cloud.EngineBids {
            override fun put(b: CloudBids) = r.put(b)
            override suspend fun pass(s: com.tjshea.vigilant.data.scanner.ScanSettings, now: Long) = r.pass(s, now).also { runCatching { stageDesk(now) } }
            override suspend fun stop(why: String) { r.stop(why); runCatching { stageDesk(System.currentTimeMillis()) } }
        }
    }
    var lastStoppedSaid = ""
    val engine = CloudEngine(
        scanner = scanner, sources = sources::sources, config = { config.read() },
        publish = { feed, status -> publisher.stage(feed, status) },
        // The bid desk's candidate lines (Tj, 2026-10-15), only while the phone has Vigilant-priced bids on: the fair side of every bid decision is made here, the phone adds Novig's books and posts.
        prepare = { s -> latestSettings.set(s); novigKeyed?.configure(s) },
        novigRoute = { novigKeyed?.route(System.currentTimeMillis()) ?: CloudNovig.PUBLIC_ONLY },
        novigAddress = { novigKeyed?.addressWords(System.currentTimeMillis(), addressWatch?.count ?: 0) ?: "not tested (no Novig key on GitHub: public routes only)" },
        addressWatch = addressWatch,
        bids = bidFacade,
        bidLines = { result, s, now -> sources.askInjuries(result, s, now); BidCandidates.from(result, s, now) { o -> sources.unavailable(o, now) } },
        publishBids = { publisher.stageBids(it) },
        onFeed = { rows, now -> runCatching { edge.observe(rows, now) } },
        // Every priced line within 2 points of break-even is logged, shown or not, with why it was not shown (Tj, 2026-10-16, TCL33).
        onResult = { result, s -> latestPriced.set((result.opportunities + result.games.flatMap { it.outcomes }).filter { it.quote != null }.distinctBy { it.key }); study.observeVigilant(result, s.copy(scanStudy = true), wideFloor = WIDE_FLOOR); study.flush() },
        trade = { rows, s, now ->
            val mode = s.gates.cloudTrading
            if (mode == CloudTradeMode.OFF) null else {
                val r = trader.pass(rows, s, now, bidRunner?.positions().orEmpty())
                runCatching { passJournal.append(r.row(now)) }
                val text = TradeLedger.text(trader.ledger)
                if (text != tradesText) { tradesText = text; publisher.stageTrades(text, now) }
                if (r.placed.isNotEmpty()) {
                    log("trading: " + r.line())
                    // Bets placed are news for Tj (Tj, 2026-10-15: "tell me ... bets placed"): one message per pass, the notification-worthy kind.
                    val lines = r.placed.joinToString("\n") { e -> "${e.selection} (${e.event}): ${e.contracts} at ${String.format(java.util.Locale.US, "%.0f", e.avgPrice * 100)}c, edge ${String.format(java.util.Locale.US, "%.1f", e.evPercent * 100)}%, $${String.format(java.util.Locale.US, "%.2f", e.cost)}" }
                    runCatching { postMessage(CloudMessages.Message("bets-${r.placed.first().id.take(12)}", now, "engine", "bet", (if (mode == CloudTradeMode.LIVE) "Placed " else "Paper-placed ") + "${r.placed.size} bet${if (r.placed.size == 1) "" else "s"}", lines, if (mode == CloudTradeMode.LIVE) 1 else 0, "CLOUD")) }
                }
                val stoppedNow = r.stopped.ifEmpty { trader.ledger.stopped }
                if (stoppedNow.isNotBlank() && stoppedNow != lastStoppedSaid) {
                    lastStoppedSaid = stoppedNow
                    runCatching { postMessage(CloudMessages.Message("stop-$now", now, "engine", "alert", "The engine stopped ordering", stoppedNow.take(300), 1, "CLOUD")) }
                } else if (stoppedNow.isBlank()) lastStoppedSaid = ""
                CloudEngine.TradeStatus(mode.name.lowercase(), r.line(), r.stopped.ifEmpty { trader.ledger.stopped }, now, cash = r.cash, equity = r.equity)
            }
        },
        // The wallet gate (Tj, 2026-10-15): only a LIVE engine with a trading key has a wallet to read; everything else is "not applicable" and never sleeps.
        walletProbe = { s -> if (s.gates.cloudTrading == CloudTradeMode.LIVE) tradePort?.cash() else null },
        governor = governor, usageBook = { usage.flow.value }, keysOf = { p -> CloudKeys.envFor(p.id)?.let { CloudKeys.fromEnv(it) }.orEmpty() },
        overrideSink = postOverride, messageSink = postMessage,
        engine = "github-actions", runId = runId, log = log::invoke, sourceNames = sources::names,
    )
    val end = engine.run(until, started)
    // The window is over (or the job is): whatever rests on Novig under the engine's key is cancelled, and the ledger goes out one last time.
    runCatching { trader.windowEnded() }
    runCatching { study.flush() }
    runCatching { publisher.stageTrades(TradeLedger.text(trader.ledger), System.currentTimeMillis()) }
    log("cloud engine ending: $end after ${(System.currentTimeMillis() - started) / 60_000L} min, ${engine.passes} passes")
    // The job must end: run 61 (2026-10-16, Tj: "the engine just stopped") logged this line at 18:20 and then sat in the Engine step for 5+ minutes, so the hand-over never ran and no new engine could start. A
    // daemon watchdog writes the end marker and halts the JVM after 90 s whatever the flush, a socket or a thread is doing; the normal path below ends first and exits at once.
    Thread {
        runCatching { Thread.sleep(END_WATCHDOG_MS) }
        runCatching { File(out, "end").writeText(end.name) }
        Runtime.getRuntime().halt(0)
    }.apply { isDaemon = true; name = "end-watchdog"; start() }
    runCatching { publisher.flush(force = true) }
    runCatching { novigKeyed?.close() }
    flusher.cancel()
    scope.cancel()
    File(out, "end").writeText(end.name)
    // Non-daemon threads (a socket's executor) kept the process alive after main returned; the job is over, so exit.
    System.out.flush()
    Runtime.getRuntime().halt(0)
}

private const val END_WATCHDOG_MS = 90_000L

/**
 * Reads `settings.json` from the `cloud-config` branch with git (the checkout already has the token). The engine looks every 3 s, so it asks first with the light `ls-remote` which commit the branch is at and
 * fetches only when that changed: an unchanged answer is a few hundred bytes, not a fetch.
 */
private class ConfigReader(private val git: Git, private val dir: File) {
    private var sha: String? = null
    private var cached: CloudConfig? = null

    @Synchronized
    fun read(): CloudConfig? {
        val head = git.out(dir, "ls-remote", "origin", "refs/heads/cloud-config")?.trim()?.substringBefore('\t')?.takeIf { it.length >= 40 } ?: return cached
        if (head == sha && cached != null) return cached
        if (git.run(dir, "fetch", "-q", "--depth=1", "origin", "cloud-config") != 0) return cached
        val text = git.out(dir, "show", "FETCH_HEAD:settings.json") ?: return cached
        return CloudConfig.parse(text)?.also { cached = it; sha = head } ?: cached
    }
}

/** The previous job's `ranking.json` or `usage.json` from `cloud-feed`, read with git like [ConfigReader] reads the settings (the checkout already has the token). Null when there is none. */
private object PreviousFeed {
    fun read(git: Git, dir: File, file: String, branch: String = "cloud-feed"): String? {
        if (git.run(dir, "fetch", "-q", "--depth=1", "origin", branch) != 0) return null
        return git.out(dir, "show", "FETCH_HEAD:$file")
    }
}

private class Log(private val file: File) {
    private val recent = ArrayDeque<String>()

    operator fun invoke(s: String) {
        val line = "${Instant.now()} $s"
        println(line)
        runCatching { file.appendText(line + "\n") }
        synchronized(recent) { recent.addLast(line); while (recent.size > TAIL_LINES) recent.removeFirst() }
    }

    /** The newest lines, published with the list (`engine-log.txt` on `cloud-feed`) so the engine can be read from the repository without opening a job's logs. */
    fun tail(): String = synchronized(recent) { recent.joinToString("\n") }

    companion object { const val TAIL_LINES = 200 }
}

/** git through the command line; the token is never printed (it is part of the remote URL only, and anything git says is scrubbed). */
internal class Git(private val token: String) {
    fun run(dir: File, vararg a: String): Int = exec(dir, a.toList()).first
    fun out(dir: File, vararg a: String): String? = exec(dir, a.toList()).let { if (it.first == 0) it.second else null }
    fun exec(dir: File, a: List<String>): Pair<Int, String> = runCatching {
        val p = ProcessBuilder(listOf("git") + a).directory(dir).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        code to (if (token.isNotEmpty()) text.replace(token, "***") else text)
    }.getOrElse { -1 to it.javaClass.simpleName }
}

/**
 * Publishes to the `cloud-feed` branch as ONE commit that is amended and force-pushed each time, so the branch never grows (the same trick as `lab-data`). [stage] only stores the newest list and
 * heartbeat; [flush] (every few seconds, from one coroutine) pushes the newest, never faster than [MIN_GAP_MS] (GitHub's secondary limits dislike a push a second), a heartbeat at least
 * every [HEARTBEAT_MS] when nothing else changed.
 */
internal class Publisher(private val dir: File, private val repo: String, private val token: String, private val git: Git, private val logTail: () -> String = { "" }, private val keyIds: (com.tjshea.vigilant.data.keys.QuotaPolicy) -> List<String> = { emptyList() }) {
    private var usageBook: UsageBook? = null
    private var feedBytes: ByteArray? = null
    private var bidsBytes: ByteArray? = null
    private var evBytes: ByteArray? = null
    private var evAt = 0L
    private var evRows = 0
    private var usageText: String? = null
    private var usageAt = 0L
    private var tradesText: String? = null
    private var tradesAt = 0L
    private var deskText: String? = null
    private var status: CloudStatus? = null
    private var dirty = false
    private var lastPushAt = 0L
    private var committed = false
    @Volatile private var lastProblem: String? = null

    @Synchronized
    fun stage(feed: CloudFeed?, st: CloudStatus): String? {
        if (feed != null) feedBytes = CloudFeed.encode(feed)
        status = st.copy(feedBytes = feedBytes?.size ?: 0, bidsBytes = bidsBytes?.size ?: 0, propLineEvAtMs = evAt, propLineEvRows = evRows, usageAtMs = usageAt, tradesAtMs = maxOf(st.tradesAtMs, tradesAt), deskBytes = deskText?.length ?: 0)
        dirty = true
        return lastProblem
    }

    /** The engine's API usage snapshot ([CloudUsage]; keys by id), written beside the list and announced in the status. */
    @Synchronized
    fun stageUsage(text: String, at: Long) {
        usageText = text; usageAt = at
        usageBook = CloudUsage.decode(text)?.book
        status = status?.copy(usageAtMs = at)
        dirty = true
    }

    /** The engine's trading ledger (`trades.json`, [TradeLedger]), written beside the list and announced in the status. */
    @Synchronized
    fun stageTrades(text: String, at: Long) {
        tradesText = text; tradesAt = at
        status = status?.copy(tradesAtMs = at)
        dirty = true
    }

    /** The engine's bid desk record (`desk.json`, [com.tjshea.vigilant.data.cloud.DeskFile]): the bids and their fills, for the phone's screens and for the next job. */
    @Synchronized
    fun stageDesk(text: String, at: Long) {
        deskText = text
        status = status?.copy(deskBytes = text.length)
        dirty = true
    }

    /** The bid desk's candidate lines (`bids.json.gz`); null (bids switched off on the phone) takes the file away so nothing stale lingers. Goes out with the next push. */
    @Synchronized
    fun stageBids(b: CloudBids?) {
        bidsBytes = b?.let { CloudBids.encode(it) }
        status = status?.copy(bidsBytes = bidsBytes?.size ?: 0)
        dirty = true
    }

    /** PropLine's +EV-at-Novig list (`propline-ev.json.gz`, TCL56), announced in the status; goes out with the next push. */
    @Synchronized
    fun stagePropLineEv(f: com.tjshea.vigilant.data.reference.PlEvFeed) {
        evBytes = com.tjshea.vigilant.data.reference.PlEvFeed.encode(f)
        evAt = f.atMs; evRows = f.rows.size
        status = status?.copy(propLineEvAtMs = evAt, propLineEvRows = evRows)
        dirty = true
    }

    @Synchronized
    fun flush(force: Boolean = false) {
        val st = status ?: return
        val now = System.currentTimeMillis()
        if (!dirty || (!force && now - lastPushAt < MIN_GAP_MS)) return
        dir.mkdirs()
        if (!File(dir, ".git").exists()) {
            git.run(dir, "init", "-q")
            git.run(dir, "config", "user.name", "vigilant-cloud")
            git.run(dir, "config", "user.email", "actions@users.noreply.github.com")
            git.run(dir, "remote", "add", "origin", "https://x-access-token:$token@github.com/$repo.git")
            git.run(dir, "checkout", "-q", "--orphan", "cloud-feed")
        }
        feedBytes?.let { File(dir, "feed.json.gz").writeBytes(it) }
        usageText?.let { File(dir, "usage.json").writeText(it) }
        // The page Tj reads on GitHub (TCL22d): which APIs work, what each has left. A failure to draw it never blocks the list.
        runCatching { File(dir, "DASHBOARD.md").writeText(CloudDashboard.markdown(st.copy(heartbeatMs = now), usageBook ?: UsageBook(), keyIds, now, repo)) }
        tradesText?.let { File(dir, "trades.json").writeText(it) }
        deskText?.let { File(dir, "desk.json").writeText(it) }
        ApiChain.measures.takeIf { it.isNotEmpty() }?.let { File(dir, "ranking.json").writeText(CloudRanking.encode(it)) }
        File(dir, "bids.json.gz").let { f -> bidsBytes?.let { f.writeBytes(it) } ?: f.delete() }
        evBytes?.let { File(dir, "propline-ev.json.gz").writeBytes(it) }
        File(dir, "status.json").writeText(CloudStatus.text(st.copy(heartbeatMs = now)))
        logTail().takeIf { it.isNotBlank() }?.let { File(dir, "engine-log.txt").writeText(it + "\n") }
        git.run(dir, "add", "-A")
        val msg = "cloud feed ${java.time.Instant.ofEpochMilli(now)}"
        val c = if (committed) git.exec(dir, listOf("commit", "-q", "--amend", "--allow-empty", "-m", msg)) else git.exec(dir, listOf("commit", "-q", "--allow-empty", "-m", msg))
        if (c.first != 0) { lastProblem = "commit failed: ${c.second.take(120)}"; return }
        committed = true
        val p = git.exec(dir, listOf("push", "-q", "-f", "origin", "cloud-feed"))
        lastPushAt = now
        if (p.first == 0) { lastProblem = null; dirty = false } else lastProblem = "push failed: ${p.second.take(160)}"
    }

    companion object {
        const val MIN_GAP_MS = 8_000L
    }
}
