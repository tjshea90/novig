package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.app.data.KeystoreSecretBox
import com.tjshea.vigilant.app.data.KeystoreSigningKey
import com.tjshea.vigilant.app.data.KeystoreVault
import com.tjshea.vigilant.app.data.NovigConnectionStore
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.signing.ManagementKeyStore
import com.tjshea.vigilant.data.novig.signing.NovigBettingSetup
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.data.tracker.ApiBetSync
import com.tjshea.vigilant.data.tracker.ApiSettler
import com.tjshea.vigilant.data.cno.CnoCache
import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoLinks
import com.tjshea.vigilant.data.cno.CnoNetwork
import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.cno.NovigLive
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.TapLink
import com.tjshea.vigilant.data.alerts.AlertBook
import com.tjshea.vigilant.data.alerts.AlertLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.FileApiKeyStore
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.RoundCost
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageDelta
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.stream.NovigStream
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.LowUsageSource
import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.PropLinePropsSource
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.OddsApiPropsSource
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.book.SportsbookScanner
import com.tjshea.vigilant.data.scanner.LowUsageBids
import com.tjshea.vigilant.data.scanner.OddsScanner
import com.tjshea.vigilant.data.scanner.ScanRunner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.teams.PlayerTeams
import com.tjshea.vigilant.data.teams.TeamsCache
import com.tjshea.vigilant.data.tracker.BetRecheck
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.FreeScores
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.OpenBetPricer
import com.tjshea.vigilant.data.tracker.PlacedBets
import com.tjshea.vigilant.data.tracker.PlacedBook
import com.tjshea.vigilant.data.vigilantHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.tjshea.vigilant.data.tracker.ClosingLine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

class VigilantApp : Application() {
    private val containerLazy = lazy { AppContainer(this) }
    val container: AppContainer by containerLazy

    override fun onCreate() {
        super.onCreate()
        // A crash's stack is kept before the process goes (Diagnostics' Recent problems, Tj 2026-09-30).
        AppExits.install(this)
        // A new process has no scan results (they live in memory): a "scan done" notification left
        // from before Android closed Vigilant names bets the app can no longer show (Tj, 2026-09-27:
        // "it says the best bet is Milwaukee, but this bet isn't even shown in the widget").
        ScanService.cancelDone(this)
    }

    /**
     * Vigilant is off screen (or memory is short): the scanners let go of what can't price again (RESEARCH.md §63; Tj's 2026-10-02 Diagnostics: Android
     * ended it 3 times for memory in the background while it held 46 fair-odds boards). Not while a scan, the scan service or background auto-scan runs:
     * they're using it.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Nothing built yet (a process started for an alarm or a receiver): nothing to trim, and nothing to build for it.
        if (level < android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || !containerLazy.isInitialized()) return
        val c = container
        if (c.runner.state.value.scanning || AutoScanService.running) return
        c.appScope.launch {
            val dropped = c.scanner.trimForBackground() + (c.betScanner?.trimForBackground() ?: 0)
            c.eventLog.info("APP", "off screen: memory trimmed ($dropped old fair-odds boards let go, heap ${com.tjshea.vigilant.data.MemoryGuard.usedMb()} of ${com.tjshea.vigilant.data.MemoryGuard.maxMb()} MB)")
        }
    }
}

/**
 * One of everything, for the life of the process. A single [OkHttpClient] shares one connection
 * pool across Novig and every odds provider, so a scan reuses warm HTTP/2 connections instead of
 * paying a TLS handshake per request (a real battery cost on a phone radio). Nothing here starts
 * any network on its own: only a scan the user asks for does, and CrazyNinjaOdds' list (with its
 * books and player teams) while the CNO scanner is on screen ([cno], [teams]).
 */
class AppContainer(private val app: Application) {
    private companion object {
        /** Why scanning stopped, for the bids' cancel reason ([KillSwitch.CANCEL_WHY] for the kill switch). */
        const val NEITHER = 0
        const val PAUSED = 1
        const val KILLED = 2

        /** How often the flight recorder is written to its files. */
        const val FLUSH_EVERY_MS = 30_000L

        /** The burst recorder's connection is never closed for being idle (a scan's is after two minutes): it lives while the recorder does. */
        const val BURST_IDLE_CLOSE_MS = 24 * 3_600_000L

        /** How long the trader keeps the recorder's proof before reading the journal again. */
        const val BURST_PROOF_TTL_MS = 30_000L

        /** A leg of the burst trader is never staked under or over these, whatever the saved settings say. */
        const val BURST_TRADE_MIN_STAKE = 0.5

        /** The leagues the paper lab reads: the ones ESPN's scoreboard can give a clock for. */
        const val LAB_BOARD_MS = 25_000L
        val LAB_LEAGUES = setOf("NFL", "NCAAF", "NBA", "WNBA", "NCAAB", "NHL", "MLB")
        const val BURST_TRADE_MAX_STAKE = 10.0

        /** CNO game pages read at once in "Check odds now" (the client's bulk pace keeps them to two requests a second). */
        const val RECHECK_AT_ONCE = 3

        /** Excluded from backups (res/xml): sealed by this phone's Keystore, it couldn't be opened anywhere else. */
        const val MANAGEMENT_KEY_FILE = "novig_management_key.json"

        /** The fewest milliseconds between two make-orders passes on a running scan's partial results. */
        const val MAKER_SCAN_PASS_MS = 20_000L

        /** The wallet check ([MakerRunner.fitToWallet]) waits this long after each run before it looks again (a run reads Novig's open orders). */
        const val WALLET_CHECK_GAP_MS = 5_000L

        /** The scan study looks at the green check's book pages at most this often (a page is read every few seconds; the study logs the newest). */
        const val STUDY_BOOKS_GAP_MS = 5_000L

        /** Logged lines are written at least this often, scan or no scan. */
        const val STUDY_FLUSH_MS = 30_000L

        /** A newly started process waits this long before the study grades what ended meanwhile (the screen's own reads come first). */
        const val STUDY_SETTLE_DELAY_MS = 90_000L

        /** The study asks ParlayAPI for closes only while at least this share of its month's credits is left. */
        const val STUDY_PARLAY_RESERVE = 0.4
    }

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * The flight recorder (Tj, 2026-10-02: a diagnostics file Claude can diagnose and improve the app from): notable events with where in the code they came from,
     * every call's host, status, time and size by host, the app's own timings, and what earlier files said ([com.tjshea.vigilant.data.diag.DiagHistory]). All of it
     * kept across restarts, bounded, never a key ([com.tjshea.vigilant.data.diag.ProblemLog.clean], [com.tjshea.vigilant.data.diag.NetShape]).
     */
    val eventLog = com.tjshea.vigilant.data.diag.EventLog(
        JsonFileStore(File(app.filesDir, "events.json"), com.tjshea.vigilant.data.diag.EventBook.serializer(), { com.tjshea.vigilant.data.diag.EventBook() }, json),
    )
    val netStats = com.tjshea.vigilant.data.diag.NetStats(
        JsonFileStore(File(app.filesDir, "netstats.json"), com.tjshea.vigilant.data.diag.NetBook.serializer(), { com.tjshea.vigilant.data.diag.NetBook() }, json),
    )
    val perf = com.tjshea.vigilant.data.diag.PerfStats()

    /** Whether the phone restarted since Vigilant last looked, the only time the app switches auto-bet off ([LaunchGate]). */
    val launches = LaunchGate(app.getSharedPreferences(LaunchGate.PREFS, android.content.Context.MODE_PRIVATE))

    /** The screen's frames, by what the app was doing (Diagnostics' frame meter; [FrameMeter]). */
    val frames = com.tjshea.vigilant.data.diag.FrameStats()
    val recorder = AppRecorder(eventLog, netStats, perf)
    val diagHistory = com.tjshea.vigilant.data.diag.DiagHistory(
        JsonFileStore(File(app.filesDir, "diag_history.json"), com.tjshea.vigilant.data.diag.DiagBook.serializer(), { com.tjshea.vigilant.data.diag.DiagBook() }, json),
    )

    val http: OkHttpClient = vigilantHttpClient(listOf(com.tjshea.vigilant.data.diag.NetInterceptor(netStats, eventLog, network = { NetKind.of(app) })))

    /** Tj's keys as plain JSON in app storage: survives updates and restores from backup. */
    val keyStore = FileApiKeyStore(File(app.filesDir, "api_keys.json"), json)

    /** Where keys lived until v0.6.0 (Keystore-encrypted). Read once, to move them over. */
    private val legacyKeyStore = EncryptedApiKeyStore(app)

    /** Every call's usage, per provider and key, behind the meters and the key rotation. */
    val usage = UsageMeter(JsonFileStore(File(app.filesDir, "usage.json"), UsageBook.serializer(), { UsageBook() }, json))

    /** The kill switch's second copy ([KillSwitch]): a settings file that is damaged, reset or restored from a backup can't start things running again. */
    val killMarker = KillMarker(app.getSharedPreferences(KillMarker.PREFS, android.content.Context.MODE_PRIVATE))

    val settingsStore = JsonFileStore(
        File(app.filesDir, "settings.json"), ScanSettings.serializer(),
        { KillSwitch.reconcile(ScanSettings(), killMarker) }, json,
    )
    val tracker = BetTracker(File(app.filesDir, "bets.json"), ownBook = AppBook.name)

    /** When the last Tracker "Check odds now" began: its +EV / −EV counter counts the bets re-read since ([CheckOddsStats]). */
    val lastCheck = JsonFileStore(File(app.filesDir, "last_check.json"), com.tjshea.vigilant.data.tracker.LastCheck.serializer(), { com.tjshea.vigilant.data.tracker.LastCheck() }, json)
    val novig = NovigPublicClient(http, json, usage = usage).also { n ->
        // Every time the key route stands down (and the scan reads the public routes at a third of the pace) is on Diagnostics' timeline (Tj, 2026-10-04: "novig scanning is going extremely slow").
        n.onStanddown = { d ->
            eventLog.warn("NOVIG", "key route stood down for ${d.forMs / 1_000} s (${d.why}): reads go to the public routes meanwhile", d.forMs)
            eventLog.count("novig.keyStanddowns")
        }
    }
    val novigConnection = NovigConnectionStore(app)

    /**
     * Tj's Novig management key, saved once Novig accepts it (Tj, 2026-09-29: "I only input the API key and file one time"): sealed by the
     * Keystore, kept through every update, left out of backups. Tests swap in their own sealing ([installSecretBoxForTest]).
     */
    @Volatile var managementKeys = ManagementKeyStore(File(app.filesDir, MANAGEMENT_KEY_FILE), KeystoreSecretBox, json)
        private set

    /** Robolectric has no Android Keystore: tests seal the management key with [box] (same file). */
    internal fun installSecretBoxForTest(box: com.tjshea.vigilant.data.novig.signing.SecretBox) {
        managementKeys = ManagementKeyStore(File(filesDir, MANAGEMENT_KEY_FILE), box, json)
    }

    private val filesDir: File = app.filesDir

    /**
     * Novig's own books (Vigilant), or a sportsbook's posted odds out of the fair-odds feeds'
     * requests (Vigilant MGM: BetMGM, no request of its own; [SportsbookScanner]).
     */
    val scanner: OddsScanner = if (AppBook.isNovig) Scanner(novig) else SportsbookScanner(AppBook.current)

    /**
     * Lives as long as the process, not a screen: a scan Tj starts keeps going when he switches
     * apps or backs out of Vigilant. [ScanService] keeps the process alive while one runs.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        // A background job that dies is logged, not allowed to take the whole app down (v0.81.1: Tj's "crashing as soon as I open it").
        runCatching { eventLog.error("APP", "a background job died: ${e.javaClass.simpleName}", e) }
    })

    /**
     * Where Vigilant's scan runs: as long as the process, like [appScope], but on [ScanThreads] (background priority), so the scan's parsing and
     * pricing yield the CPU to the screen (Tj, 2026-10-03: "the entire app gets laggy when vigilant is scanning, but not when cno only is scanning").
     */
    val scanScope = CoroutineScope(SupervisorJob() + ScanThreads.dispatcher())
    val runner = ScanRunner(scanner, scanScope)

    /** Where the CPU went during the last finished Vigilant scan ([ThreadCpu]), for Diagnostics; null until one ends in this process. */
    @Volatile var scanCpu: ThreadCpu.Split? = null
        private set

    init {
        // SAFE START (Tj, 2026-10-09): after a CRASH the research recorders, bids and auto-bet are off (not at every start: Android ends a backgrounded app and the next open would switch research off, Tj 2026-10-10)
        // off too, so a pile of switches that crashes the app can never crash it again before Settings opens. Done before anything reads the settings.
        runCatching {
            val crashed = File(app.filesDir, "last_crash.txt").exists()
            kotlinx.coroutines.runBlocking {
                settingsStore.update { it.safeStart(crashed) }
            }
        }
        // The closing capture's alarm follows the open bets (Tj, 2026-09-29: true closing lines): armed for the next start, moved when a bet is
        // added, settled or deleted, cancelled when none is left to close ([ClosingAlarm]). Whatever started this process: a screen, an alert's
        // ✓, a worker.
        appScope.launch {
            runCatching { tracker.all() }
            tracker.flow.filterNotNull()
                .map { bets -> ClosingLine.nextAt(bets, System.currentTimeMillis()) }
                .distinctUntilChanged()
                .collect { at -> ClosingAlarm.set(app, at) }
        }
    }

    /**
     * CrazyNinjaOdds' +EV list (RESEARCH.md §18). It reads only while [MainActivity] is started
     * (on screen, or as the mini window), at most once per 30 s; the last list is kept on disk.
     */
    /** Finds a CNO bet in Novig's own public catalog: its exact bet-slip link without asking CNO. */
    val betFinder = NovigBetFinder(http, json)

    val cno = CnoFeed(
        // Its own client over the shared one: CNO's DNS fallback, short keep-alive, retry (CnoNetwork).
        CnoClient(CnoNetwork.client(http, online = { NetKind.of(app) != NetKind.NONE })),
        JsonFileStore(File(app.filesDir, "cno.json"), CnoCache.serializer(), { CnoCache() }, json),
        linkStore = JsonFileStore(File(app.filesDir, "cno_links.json"), CnoLinks.serializer(), { CnoLinks() }, json),
        // Bet links from Novig's catalog first, so taps don't depend on CNO answering (Tj, 2026-09-27).
        // Vigilant MGM has no such catalog: BetMGM bets' links come from CNO's own deeplinks.
        catalog = { row -> if (AppBook.isNovig) (betFinder.find(row) as? NovigBetFinder.Found.Bet)?.link else null },
    )

    init {
        // The outcome CNO's own link names is a second way to find a bet's Novig market (what "Open in Novig" opens): the Bet sheet, the auto-bet and every price read use it when the
        // name match finds no exact outcome (Tj, 2026-10-10).
        betFinder.linkHint = { row -> CnoFeed.outcomeIdOf(cno.links.value[CnoFeed.linkKey(row)]) }
    }

    /**
     * Written once the ✓ marks from before the Tracker kept them were moved into it: the move runs
     * once, so a bet deleted in the Tracker doesn't come back from its old mark.
     */
    val trackerImported = File(app.filesDir, "tracker_imported")

    /**
     * Settles tracked bets from each game's final score (ESPN's free scoreboard and box scores, MLB's
     * Stats API): on app open, on the Tracker tab, and every 3 h in the background ([SettleWorker]).
     * Reads only for open bets whose game started over an hour ago, one scoreboard per league and day.
     */
    /**
     * Betting through Novig's API (Tj, 2026-09-29): set when the connection holds the Vigilant subaccount's `trading` key and this phone still
     * has its private half; null = betting isn't set up. Bets placed through it are graded from Novig's own ledger ([apiSettler]) and the score
     * feeds leave them alone while it's set.
     */
    @Volatile var trading: NovigTradingClient? = null
        private set

    /** The Vigilant subaccount's key id (its wallet's address) while [trading] is set; null otherwise. */
    @Volatile var subaccountKeyId: String? = null
        private set

    /** The Vigilant wallet's balance, shown on every notification ([withWallet]; Tj, 2026-10-02 21:51Z). */
    val wallet = WalletBalance(
        read = {
            val t = trading
            val address = subaccountKeyId
            if (t == null || address == null) null else withContext(Dispatchers.IO) { t.balance(address) }
        },
        setUp = { trading != null && subaccountKeyId != null },
        prefs = app.getSharedPreferences(WalletBalance.PREFS, android.content.Context.MODE_PRIVATE),
    )

    /**
     * One order at a time across the whole app (Tj, 2026-10-01): the Bet sheet's placer and the auto-bet's share it ([ApiBetPlacer]), so the
     * same bet can never be placed by both at once.
     */
    val orderLock = Mutex()

    private var autoPlacerCache: Pair<Any, ApiBetPlacer>? = null

    /** The market's book read from Novig just now (never one shown from the last scan). */
    suspend fun freshBook(marketId: String): NovigBook? =
        withContext(Dispatchers.IO) { novig.books(listOf(marketId)).takeIf { it.failed == 0 && it.fromCache == 0 }?.books?.get(marketId) }

    /** The placer the background auto-bet uses, for the trading client in use (null = betting isn't set up); limits from the saved settings. */
    @Synchronized
    fun autoBetPlacer(): ApiBetPlacer? {
        val t = trading ?: return null
        autoPlacerCache?.takeIf { it.first === t }?.let { return it.second }
        val limits = {
            val s = settingsStore.flow.value ?: ScanSettings()
            // Every auto-bet passes its own limits; this fallback is the strict kind (a positive edge at least), never the hand-placed one.
            BetLimits(s.apiMaxStake, s.apiMaxPerDay, maxPerGame = s.apiMaxPerGame)
        }
        return ApiBetPlacer(
            t, tracker, books = ::freshBook, limits = limits, paused = { settingsStore.flow.value?.paused == true }, lock = orderLock,
            restingBids = { com.tjshea.vigilant.data.tracker.GameExposure.bidItems(makerStore.all()) },
        )
            .also { autoPlacerCache = t to it }
    }
    @Volatile var apiSettler: ApiSettler? = null
        private set
    @Volatile var apiSync: ApiBetSync? = null
        private set
    @Volatile var bettingSetup = NovigBettingSetup(http, json, KeystoreVault)
        private set

    /** Setup and transfers against a Novig the test fakes. */
    internal fun installBettingSetupForTest(setup: NovigBettingSetup) {
        bettingSetup = setup
    }

    /** Betting through a client the test built (a mock Novig), without a Keystore key. */
    internal fun installTradingForTest(client: NovigTradingClient, subaccountKeyId: String) {
        trading = client
        this.subaccountKeyId = subaccountKeyId
        apiSettler = ApiSettler(tracker, client, subaccountKeyId, scoreGrade = { bet -> settler.scoreGradeOf(bet) })
        apiSync = ApiBetSync(tracker, client, novig)
    }

    /** Players' injury reports (Tj, 2026-09-30, PARLAY_API.md §6.1): free from every ParlayAPI props answer, and its /injuries list. */
    val injuries = com.tjshea.vigilant.data.reference.InjuryIndex()

    /**
     * SportsGameOdds Pro (Tj, 2026-10-09; SPORTSGAMEODDS_API.md): its client, rotated across Tj's keys like every provider's. Used only while [sgoActive].
     */
    val sgoClient = com.tjshea.vigilant.data.reference.SportsGameOddsClient(http, KeyPool(QuotaPolicy.SGO, { keyStore.current(ApiProvider.SPORTSGAMEODDS) }, usage), json)
    val sgoGames = com.tjshea.vigilant.data.reference.SgoGamesSource(sgoClient)
    val sgoProps = com.tjshea.vigilant.data.reference.SgoPropsSource(sgoClient, injuries = injuries)

    /** SportsGameOdds Pro is on, a key is saved and it is answering: the scan, bids and open-bet pricing read it, and the feeds it replaces rest. */
    fun sgoActive(s: ScanSettings): Boolean = AppBook.isNovig && s.sgoPro && keyStore.current(ApiProvider.SPORTSGAMEODDS).isNotEmpty() && !sgoClient.down()

    /** Closing lines (CLV, including bets already in the Tracker) and final scores from SportsGameOdds: asked only while it is switched on (see [syncSgo]). */
    val sgoCloses = com.tjshea.vigilant.data.tracker.SgoCloses(sgoClient, { keyStore.current(ApiProvider.SPORTSGAMEODDS).isNotEmpty() })
    val sgoScores = com.tjshea.vigilant.data.tracker.SgoScores(sgoClient, { keyStore.current(ApiProvider.SPORTSGAMEODDS).isNotEmpty() })

    /**
     * OddsPapi v5 (Tj, 2026-10-09; ODDSPAPI_API.md): its client, rotated across Tj's keys like every provider's, and the one shared read the games source and the props source take their share of.
     * Used only while [opActive].
     */
    val opClient = com.tjshea.vigilant.data.reference.OddsPapiClient(http, KeyPool(QuotaPolicy.ODDSPAPI, { keyStore.current(ApiProvider.ODDSPAPI) }, usage))
    val opFeed = com.tjshea.vigilant.data.reference.OddsPapiFeed(opClient)
    val opGames = com.tjshea.vigilant.data.reference.OpGamesSource(opFeed)
    val opProps = com.tjshea.vigilant.data.reference.OpPropsSource(opFeed)

    /** OddsPapi is on, a key is saved and it is answering: the scan, bids and open-bet pricing read it, and the feeds it replaces rest. */
    fun opActive(s: ScanSettings): Boolean = AppBook.isNovig && s.oddsPapi && keyStore.current(ApiProvider.ODDSPAPI).isNotEmpty() && !opClient.down()

    /** Closing lines (CLV for any bet, graded or not) and final scores from OddsPapi: asked only while it is switched on (see [syncOp]). */
    val opCloses = com.tjshea.vigilant.data.tracker.OpCloses(opClient, opFeed, { keyStore.current(ApiProvider.ODDSPAPI).isNotEmpty() })
    val opScores = com.tjshea.vigilant.data.tracker.OpScores(opClient, opFeed, { keyStore.current(ApiProvider.ODDSPAPI).isNotEmpty() })

    /** Follows Settings: the closes and scores read OddsPapi only while its switch is on. Safe to call on every settings change; off, nothing is asked. */
    fun syncOp(s: ScanSettings) {
        com.tjshea.vigilant.engine.WideQuotes.enabled = s.ignoreWideQuotes
        val on = AppBook.isNovig && s.oddsPapi
        opCloses.enabled = on
        opScores.enabled = on
    }

    /** Follows Settings: the closes and scores read SportsGameOdds only while Pro is on. Safe to call on every settings change. */
    fun syncSgo(s: ScanSettings) {
        syncOp(s)
        val on = AppBook.isNovig && s.sgoPro
        sgoCloses.enabled = on
        sgoScores.enabled = on
        NotifyGate.quiet = s.quietNotifications
        com.tjshea.vigilant.data.scanner.Freshness.sgoMaxAgeMs = s.sgoMaxAgeMinutes.coerceIn(10, 30) * 60_000L
        com.tjshea.vigilant.data.scanner.Freshness.sgoMode = on
    }

    /** The free score feeds (ESPN, MLB): one instance, so its per-day and box-score caches serve the Tracker's grading and the scan study's alike. */
    val freeScores = FreeScores(http, json)

    /** OddsPapi's final scores first while it is on ([syncOp]), then SportsGameOdds' while Pro is on ([syncSgo]), the free feeds behind them (and for a prop's box score). */
    val scores: com.tjshea.vigilant.data.tracker.ScoreSource = com.tjshea.vigilant.data.tracker.ChainedScores(opScores, com.tjshea.vigilant.data.tracker.ChainedScores(sgoScores, freeScores))
    val settler = BetSettler(tracker, scores, leaveApiBets = { trading != null })

    /**
     * Closes found after the start (Tj, 2026-09-30: "My phone will not always be on"): ESPN's closing game lines, then Novig's trade history,
     * for every started bet the capture before the start didn't read. Runs with the grading: app open, Grade now, Check odds now, and the
     * 3-hourly background worker ([SettleWorker]).
     */
    val espnCloses = com.tjshea.vigilant.data.tracker.EspnCloses(http, json)
    val novigCloses = com.tjshea.vigilant.data.tracker.NovigTradeCloses(http, json)
    /** Tj's ParlayAPI keys, one pool (and one meter) for its scans and its closing lines. */
    private val parlayPool = KeyPool(QuotaPolicy.PARLAY, { keyStore.current(ApiProvider.PARLAY) }, usage)

    /** Each ParlayAPI key's own account (credits left, plan, reset), read for free so the meter is exact (Tj, 2026-09-30). */
    val parlayAccount = com.tjshea.vigilant.data.reference.ParlayAccount(http, { keyStore.current(ApiProvider.PARLAY) }, usage, json)

    /** Pinnacle's closes from ParlayAPI (RESEARCH.md §43): asked first when ParlayAPI is on and Tj has a key; nothing otherwise. */
    val parlayCloses = com.tjshea.vigilant.data.tracker.ParlayCloses(http, parlayPool, json, historyDays = { parlayAccount.historyDays() })
    val closeBackfill = com.tjshea.vigilant.data.tracker.CloseBackfill(tracker, listOf(sgoCloses, opCloses, parlayCloses, espnCloses, novigCloses))

    /**
     * The scan study (Tj, 2026-10-03: "on every cno scan, the vigilant app saves logs on all kinds of information … when those bets are final, it logs whether
     * they won or lost or pushed and their closing line odds"; [com.tjshea.vigilant.data.study.ScanStudy]): every bet a CNO or Vigilant scan lists, logged as it's
     * listed, graded and closed afterwards with the Tracker's own grader and close lookups ([settleStudy]), for Settings › Tools › Share scan study with Claude.
     */
    val study = com.tjshea.vigilant.data.study.ScanStudy(
        com.tjshea.vigilant.data.study.StudyJournal(File(app.filesDir, "study")), version = { BuildConfig.VERSION_NAME },
    )

    /**
     * How the study gets the scans' results ([StudySync]): the watchers below call it as things arrive, and a background cycle (and the Vigilant scan it started, when that ends)
     * calls [StudySync.catchUp] before its wake lock goes, so nothing the study still had to do waits for an alarm that is minutes off.
     */
    val studySync = StudySync(
        study, cno, livePrices = { live.prices.value },
        finishedScan = { runner.state.value.takeIf { it.finished > 0 && !it.scanning }?.result },
        settings = { currentSettings() }, step = { what, block -> studyStep(what, block) },
    )

    /** ParlayAPI's closes cost credits: the study asks for them only while the month's credits are over [STUDY_PARLAY_RESERVE] left (the Tracker's own bets always do). */
    private fun parlayCreditsPlentiful(): Boolean {
        val keys = usage.flow.value.providers["parlay"]?.keys?.values.orEmpty()
        val limit = keys.sumOf { it.limit ?: 0 }
        return limit <= 0 || keys.sumOf { it.remaining ?: 0 } >= limit * STUDY_PARLAY_RESERVE
    }

    private val studyCloses = listOf<com.tjshea.vigilant.data.tracker.CloseSource>(
        sgoCloses, opCloses, com.tjshea.vigilant.data.study.GuardedCloses(parlayCloses) { parlayCreditsPlentiful() }, espnCloses, novigCloses,
    )

    /**
     * Grades the study's bets whose games have started and finds their closes, beside the Tracker's own (the 3-hourly worker, a little after the process
     * starts): a bet Tj placed himself takes its result and close from his Tracker bet, the rest go through the same grader and close sources. Never throws.
     */
    suspend fun settleStudy() {
        try {
            parlayCloses.enabled = currentSettings().useParlay
            syncSgo(currentSettings())
            study.settle(scores, studyCloses, runCatching { tracker.all() }.getOrDefault(emptyList()), File(app.cacheDir, "study-grading"), yieldTo = { trackerClosing.get() > 0 })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Looked at again on the next pass.
        }
    }

    /** One step of the study's logging: whatever goes wrong is a line in Recent problems, never a failure of the scan it watched. */
    private suspend fun studyStep(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            eventLog.count("study.errors")
            runCatching { problems.add("Scan study", "$what: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** The last back-fill's report (this process), for Diagnostics. */
    @Volatile var lastBackfill: com.tjshea.vigilant.data.tracker.CloseBackfill.Report? = null

    /** Held while Check odds now runs: the loops that read on their own wait ([FocusGate]). */
    val focus = FocusGate()

    /**
     * Runs the back-fill, never throwing (a feed down is looked at again next time). Novig's trade files (a few MB a kickoff) are read on any
     * network: Tj, 2026-09-30, "My mobile data is fast and unlimited and my phone storage is large. Choose accuracy and speed over mobile data
     * or phone storage always."
     */
    suspend fun backfillCloses(force: Boolean = false) {
        trackerClosing.incrementAndGet()
        try {
            parlayCloses.enabled = currentSettings().useParlay
            syncSgo(currentSettings())
            lastBackfill = closeBackfill.run(heavyOk = true, force = force)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Looked at again on the next pass.
        } finally {
            trackerClosing.decrementAndGet()
        }
    }

    /** How many of the Tracker's own close lookups are running now: the scan study's grading waits its turn behind them (it shares their feeds and never delays Tj's own bets). */
    private val trackerClosing = java.util.concurrent.atomic.AtomicInteger()

    /**
     * The Tracker's "Check odds now": every open bet's CNO game page re-read through [cno], [RECHECK_AT_ONCE] at a time at a brisk
     * pace (a page is two requests; one after another a hundred bets took five minutes), nothing while CNO asked for a pause,
     * judged against what the bet cost.
     */
    val recheck = BetRecheck(
        tracker,
        books = { row -> cno.readBooks(row) },
        paused = { (cno.state.value.pausedUntilMs ?: 0L) > System.currentTimeMillis() },
        concurrency = RECHECK_AT_ONCE,
        backup = { bet -> parlayBooks.view(bet) },
    )

    /**
     * The Tracker's "Check odds now" for Vigilant's own bets, and any bet CNO couldn't read (Tj, 2026-09-29: "update the EV for every single
     * open bet, including bets added from vigilant scanner"): a bets-only [Scanner] of its own (never the feed's catalog, books or fair-odds
     * snapshots) prices exactly those bets' games from the same fair-odds sources and rules as the feed. Null for Vigilant MGM.
     */
    /** Check odds now's own scanner (bets only), kept here so it's trimmed with the feed scanner when Vigilant leaves the screen. */
    val betScanner: Scanner? = if (AppBook.isNovig) Scanner(novig, betsOnly = true) else null
    val betPricer: OpenBetPricer? = betScanner?.let { OpenBetPricer(tracker, it, ::referenceSources) }

    /** Novig's price now for CNO's listed bets, from Novig's order books (only while CNO's list is on screen). */
    val live = NovigLive(novig, { row -> betFinder.find(row) })

    /** Bets Tj marked placed in the widget or the CNO tab: hidden from both until their game is over. */
    val placed = PlacedBets(JsonFileStore(File(app.filesDir, "placed.json"), PlacedBook.serializer(), { PlacedBook() }, json))

    /** Player teams for CNO's player bets ("D. Schultz (HOU)"), from ESPN; read only alongside [cno]. */
    val teams = PlayerTeams(http, json, JsonFileStore(File(app.filesDir, "teams.json"), TeamsCache.serializer(), { TeamsCache() }, json))

    /** Whether Vigilant is on screen: a finished scan only notifies when it isn't. */
    var onScreen: Boolean
        get() = screen.value
        set(value) { screen.value = value }

    /** [onScreen] as a flow: what reads only while Tj is looking (ParlayAPI's movers) waits on it instead of waking on a timer. */
    val screen = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Bets a push alert went out for (alerts.json), so each bet alerts once (Tj, 2026-09-28). */
    val alertLog = AlertLog(JsonFileStore(File(app.filesDir, "alerts.json"), AlertBook.serializer(), { AlertBook() }, json))

    /** The background auto-scan and its +EV alerts (Tj, 2026-09-28), run by [AutoScanService]. */
    val autoScan: AutoScanner by lazy { AutoScanner(app, this) }

    /** When each background cycle started against its schedule, and whether the screen was off (files/cycles.json, Tj 2026-10-02): the evidence in Diagnostics that scanning goes on while the phone idles. */
    val cycleLog = com.tjshea.vigilant.data.diag.CycleLog(
        JsonFileStore(File(app.filesDir, "cycles.json"), com.tjshea.vigilant.data.diag.CycleBook.serializer(), { com.tjshea.vigilant.data.diag.CycleBook() }, json),
    )

    /**
     * The sharp books' prices for an exact bet (Tj, 2026-10-02: sharp-book confirmation, RESEARCH.md §60): the Pinnacle feeds Vigilant already has, asked
     * only for a bet that passed every other criterion, a league's board kept a minute. Background, so the paced feeds keep Tj's own scans their share.
     */
    val sharp: com.tjshea.vigilant.data.reference.SharpBooks by lazy {
        com.tjshea.vigilant.data.reference.SharpBooks(sources = { s -> referenceSources(s, background = true) }, settings = { currentSettings() })
    }

    /** The auto-bet (Tj, 2026-10-01): called by [autoScan]'s cycle. */
    val autoBet: AutoBettor by lazy { AutoBettor(app, this) }

    /** Locks on Tj's open API bets (RESEARCH.md §67): what's on offer ([LockScanner]) and the background's auto-lock ([AutoLocker]). */
    val locks: LockScanner by lazy { LockScanner(this) }
    val autoLock: AutoLocker by lazy { AutoLocker(app, this) }

    /** Every bid make orders posted (files/maker.json; RESEARCH.md §70), the sides Tj denied, and the sides already recommended. */
    val makerStore = com.tjshea.vigilant.data.novig.trading.maker.MakerStore(File(app.filesDir, "maker.json"))
    val makerDenials = com.tjshea.vigilant.data.novig.trading.maker.MakerDenials(File(app.filesDir, "maker_denied.json"))
    val makerRecommended = MakerRecommended(File(app.filesDir, "maker_recommended.json"))

    /** When each listed bet was first seen (files/first_listed.json): the trap guard skips a bet that was already listed too far off the start ([com.tjshea.vigilant.data.scanner.FirstListed]). */
    val firstListed = com.tjshea.vigilant.data.scanner.FirstListed(File(app.filesDir, "first_listed.json"))
    @Volatile private var makerDeskCache: Pair<NovigTradingClient, com.tjshea.vigilant.data.novig.trading.maker.MakerDesk>? = null

    /** The make-orders desk on the Vigilant wallet, sharing the one order lock; null when betting through the API isn't set up. */
    fun makerDesk(): com.tjshea.vigilant.data.novig.trading.maker.MakerDesk? {
        val t = trading ?: return null
        makerDeskCache?.takeIf { it.first === t }?.let { return it.second }
        return com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(
            t, tracker, makerStore, lock = orderLock,
            // The live bids are post-only orders this desk has no record of; they are the live bid desk's, never strays.
            otherDesks = { liveBidDesk.bidsNow().let { l -> com.tjshea.vigilant.data.novig.trading.maker.MakerDesk.OtherOrders(l.mapNotNull { it.orderId }.toSet(), l.map { it.clientId }.toSet()) } },
        ).also { makerDeskCache = t to it }
    }

    /** Make orders: the passes and the Make tab's state ([MakerRunner]). */
    val maker: MakerRunner by lazy { MakerRunner(app, this) }

    /** Diagnostics' "Recent problems" (files/problems.json, Tj 2026-09-30): what went wrong, kept across restarts, never a key. */
    val problems = com.tjshea.vigilant.data.diag.ProblemLog(
        JsonFileStore(File(app.filesDir, "problems.json"), com.tjshea.vigilant.data.diag.ProblemBook.serializer(), { com.tjshea.vigilant.data.diag.ProblemBook() }, json),
        onAdd = { area, text -> eventLog.record("PROBLEM", com.tjshea.vigilant.data.diag.Level.ERROR, "$area: $text") },
    )

    init {
        // The kill switch outlives a settings file that lost it (a restored backup, a reset): put back from its second copy before anything reads the settings
        // for long, and every part that stops for it (the bids, the services) sees it through the same flow. Never turns it off: only Resume does.
        appScope.launch(Dispatchers.IO) {
            if (killMarker.on) runCatching { settingsStore.update { KillSwitch.reconcile(it, killMarker) } }
        }
        // Housekeeping (Tj, 2026-10-10: the app lagged and the diagnostics grew too big to load): the recorders' day files older than 2 days (the scan study: 7), anything over a folder's cap
        // and the leftovers of a crashed save are deleted at start and every few hours. Pure file work, off the main thread, a minute after start so the first screen is not competing.
        appScope.launch(Dispatchers.IO) {
            delay(60_000)
            while (true) {
                runCatching { com.tjshea.vigilant.data.diag.DataKeeper.sweep(app.filesDir, System.currentTimeMillis()) }.onSuccess { r ->
                    if (r.files > 0) eventLog.info("DIAG", "housekeeping: " + com.tjshea.vigilant.data.diag.DataKeeper.line(r))
                }
                delay(com.tjshea.vigilant.data.diag.DataKeeper.EVERY_MS)
            }
        }
        // The flight recorder: what earlier runs kept comes back first, then events and connection stats are written out every half minute.
        appScope.launch(Dispatchers.IO) {
            recorder.run(runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull(), FLUSH_EVERY_MS)
        }
        // Each scan's CPU by thread group ([ThreadCpu]): a snapshot as it starts, the split as it ends.
        appScope.launch(Dispatchers.IO) {
            var before: Map<Int, ThreadCpu.Thread>? = null
            var startedAt = 0L
            runner.state.map { it.scanning }.distinctUntilChanged().collect { scanning ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (scanning) {
                    before = runCatching { ThreadCpu.snapshot() }.getOrNull()
                    startedAt = now
                } else {
                    val b = before ?: return@collect
                    runCatching { ThreadCpu.snapshot() }.getOrNull()?.let { after -> scanCpu = ThreadCpu.between(b, after, now - startedAt) }
                    before = null
                }
            }
        }
        // What happened to the Vigilant scan, in one line each: its length, what it read, what failed.
        appScope.launch {
            runner.state.distinctUntilChanged { a, b -> a.finished == b.finished }.collect { run -> run.report?.let { recorder.scanFinished(it, lowUsage = run.settings?.lowUsageNow == true, lowUsageHours = run.settings?.let { st -> com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(st) } ?: com.tjshea.vigilant.data.scanner.LowUsageBids.WINDOW_HOURS) } }
        }
        // CNO asked the app to wait (its 403/429 backoff): when, and a count.
        appScope.launch {
            cno.state.map { it.pausedUntilMs?.takeIf { p -> p > System.currentTimeMillis() } }.distinctUntilChanged().filterNotNull().collect { until ->
                recorder.cnoPaused(until, System.currentTimeMillis())
            }
        }
        // The switches that decide what runs by itself, as Tj turns them.
        appScope.launch {
            var before: ScanSettings? = null
            settingsStore.flow.filterNotNull().collect { s ->
                val b = before
                before = s
                if (b != null) recorder.settingsChanged(b, s)
            }
        }
        // The live feed test follows its switch and STOP ALL (public reads only, no orders; RESEARCH.md §106).
        appScope.launch {
            settingsStore.flow.filterNotNull().collect { s -> runCatching { feedRaceTick(s.migrate()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it } }
        }
        // SportsGameOdds Pro's closes and scores follow its switch (Tj, 2026-10-09).
        appScope.launch {
            settingsStore.flow.filterNotNull().collect { s -> runCatching { syncSgo(s.migrate()) } }
        }
        // The paper lab follows its switch and STOP ALL (no orders; RESEARCH.md §120.6).
        appScope.launch {
            settingsStore.flow.filterNotNull().collect { s -> runCatching { labTick(s.migrate()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it } }
        }
        // The burst recorder follows its switch, the leagues and STOP ALL (no orders; RESEARCH.md §95).
        appScope.launch {
            settingsStore.flow.filterNotNull().collect { s -> runCatching { burstTick(s.migrate()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it } }
        }
        // Pinnodds live follows its switch, the Pinnodds key, the Novig key and STOP ALL (paper unless "Place real bets" is on; RESEARCH.md §116).
        appScope.launch {
            settingsStore.flow.filterNotNull().collect { s ->
                // The live bid desk is started here too, not only at init: this block runs while the class is still being built, when `liveBidDesk`'s own inputs (declared further down) may not exist yet,
                // so its first start can fail without a trace (v0.85.0-0.85.3: "loop steps 0"). `start()` does nothing when it has already started.
                runCatching { liveBidDesk.start() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                runCatching { pinnTick(s.migrate()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
        }
        // The live bid desk runs for the life of the app (cheap when nothing is up): bids left from a run that ended come down at once, and a fill that lands after the switch is turned off is still recorded.
        appScope.launch { runCatching { liveBidDesk.start() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it } }
        // While real live bids are on, the wallet is read once a minute and after every fill: bids are sized against the money that is really there.
        appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                val s = settingsStore.flow.value
                if (s != null && s.liveBid && s.liveBidReal && !s.paused) runCatching { wallet.fresh() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
        }
        // Written down as it happens, whatever screen is open: a finished scan's errors and failed fair-odds sources, and CNO's errors
        // (the background scan's own are added where it ends: [AutoScanner]).
        appScope.launch {
            runner.state.distinctUntilChanged { a, b -> a.finished == b.finished }.collect { run ->
                val r = run.report ?: return@collect
                r.errors.forEach { runCatching { problems.add("Vigilant scan", it) } }
                r.sources.forEach { src -> src.error?.let { runCatching { problems.add("Fair odds: ${src.name}", it) } } }
            }
        }
        appScope.launch {
            cno.state.map { it.error }.distinctUntilChanged().filterNotNull().collect { runCatching { problems.add("CrazyNinjaOdds", it) } }
        }
        // Each bet's first-listed time (the trap guard's third rule; Tj, 2026-10-07): written down from what each CNO read and each Vigilant scan already hold, no request of its own.
        scanScope.launch {
            cno.state.map { it.snapshot }.distinctUntilChanged { a, b -> a?.fetchedAtMs == b?.fetchedAtMs }.filterNotNull().collect { snap ->
                runCatching { firstListed.note(snap.rows.map { it.key to (it.startsAtMs ?: 0L) }, snap.fetchedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
        }
        scanScope.launch {
            runner.state.map { it.result }.distinctUntilChanged { a, b -> a === b }.filterNotNull().collect { r ->
                runCatching { firstListed.note(r.feed(currentSettings()).map { it.key to it.event.startsTs }, System.currentTimeMillis()) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
        }
        // The scan study (Tj, 2026-10-03; [ScanStudy]): each CNO read, each finished Vigilant scan and each book page the green check read is logged from what it
        // already holds (no request of its own), on the scan's background-priority threads so it never competes with the screen.
        scanScope.launch {
            cno.state.map { it.snapshot }.distinctUntilChanged { a, b -> a?.fetchedAtMs == b?.fetchedAtMs }.filterNotNull().collect { snap ->
                studySync.list(snap)
            }
        }
        // The wide read (Tj, 2026-10-03: "log all cno finds on every scan … even if these bets don't meet my criteria"): the study's one request of its own, after a live list
        // read, in a session of its own, kept in cno.wide and nowhere else (the list, alerts, auto-bet and widget never see it); CnoFeed paces it (30 s, only when CNO's odds moved).
        scanScope.launch {
            cno.state.map { it.snapshot }.distinctUntilChanged { a, b -> a?.fetchedAtMs == b?.fetchedAtMs }.filterNotNull().collect { snap ->
                studySync.wideRead(snap)
            }
        }
        scanScope.launch {
            cno.wide.map { it.snapshot }.distinctUntilChanged { a, b -> a?.fetchedAtMs == b?.fetchedAtMs }.filterNotNull().collect { wide ->
                studySync.wide(wide)
            }
        }
        scanScope.launch {
            runner.state.distinctUntilChanged { a, b -> a.finished == b.finished }.filter { it.finished > 0 && !it.scanning }.collect { run ->
                run.result?.let { r -> studySync.vigilant(r) }
            }
        }
        scanScope.launch {
            cno.books.collect { books ->
                studySync.books(books)
                delay(STUDY_BOOKS_GAP_MS)
            }
        }
        // What a scan logged is written within half a minute even when no scan follows (the process may be ended any time).
        scanScope.launch {
            while (true) {
                delay(STUDY_FLUSH_MS)
                studyStep("flush") { study.flush() }
            }
        }
        // A while after the process starts (a restart, an alarm, a service): the games that ended since are graded and closed.
        scanScope.launch {
            delay(STUDY_SETTLE_DELAY_MS)
            settleStudy()
        }
        // Make orders (RESEARCH.md §70): a pass after every finished Vigilant scan (new fair prices to bid under), and every bid down the moment bids are
        // switched off or scanning pauses (the background cycle doesn't run while paused, and an empty wallet pauses it).
        appScope.launch {
            runner.state.distinctUntilChanged { a, b -> a.finished == b.finished }.filter { it.finished > 0 && !it.scanning }.collect {
                // Bids priced from CrazyNinjaOdds have their own pass in the background cycle: a Vigilant scan's end is no reason for one.
                if (currentSettings().makerSource == com.tjshea.vigilant.data.scanner.BidSource.VIGILANT) {
                    runCatching { maker.run("after a scan") }.onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e }
                }
            }
        }
        // And while a scan runs (Tj, 2026-10-03: "it didn't actually make any bids by itself"): each league's lines are bid on once its fair odds are
        // in, not minutes later at the scan's end. The newest partial result at most every [MAKER_SCAN_PASS_MS] (a pass reads Novig's open orders).
        appScope.launch {
            runner.state.filter { it.scanning && it.result?.partial == true }.map { it.result }.distinctUntilChanged { a, b -> a === b }.conflate().collect {
                val s = currentSettings()
                if (!s.paused && AppBook.isNovig && (s.maker || s.makerRecommend) && s.makerSource == com.tjshea.vigilant.data.scanner.BidSource.VIGILANT) {
                    runCatching { maker.run("during a scan") }.onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e }
                    kotlinx.coroutines.delay(MAKER_SCAN_PASS_MS)
                }
            }
        }
        // The wallet kept ahead of the bids (Tj, 2026-10-04: "the app doesn't constantly monitor how much money is in the wallet to make sure the open bids
        // aren't more than available money"): Novig holds nothing for a resting bid, so a bet by hand, an auto-bet or a fill leaves more bids up than money.
        // Every balance reading (the Bet sheet's, auto-bet's, the strip's half-minute read, a background cycle's) and every change to the bids is checked
        // against it, and the extra bids come down least valuable first, whatever auto-make is set to. No request unless the bids are over.
        appScope.launch {
            kotlinx.coroutines.flow.combine(wallet.flow, makerStore.flow) { reading, bids -> MakerRunner.overWallet(bids.orEmpty(), reading) }
                .filter { it }.conflate().collect {
                    runCatching { maker.fitToWallet("wallet check") }
                        .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; runCatching { problems.add("Make orders", e.message ?: e.javaClass.simpleName) } }
                    kotlinx.coroutines.delay(WALLET_CHECK_GAP_MS)
                }
        }
        // A new source for bids ([ScanSettings.makerSource]): the bids the old one priced were judged on data the new one doesn't have, so the ones auto-make posted come down (the ones
        // Tj approved by hand stay); the next pass bids from the new source.
        appScope.launch {
            settingsStore.flow.filterNotNull().map { it.makerSource }.distinctUntilChanged().drop(1).collect { source ->
                runCatching { maker.cancelAuto("Bids are now priced from ${source.displayName}") }
                    .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; runCatching { problems.add("Make orders", e.message ?: e.javaClass.simpleName) } }
            }
        }
        appScope.launch {
            // Paused (the Pause button, or the wallet ran out): every bid down. Auto-make switched off: the bids it posted down; the ones Tj approved stay.
            settingsStore.flow.filterNotNull().map { Triple(if (it.killed) KILLED else if (it.paused) PAUSED else NEITHER, it.maker, it.makerHalted != null) }.distinctUntilChanged().collect { (stop, on, guarded) ->
                runCatching {
                    when {
                        // The kill switch's own words on the bids, whichever of this watcher and the button takes them down first.
                        stop == KILLED -> maker.cancelAll(KillSwitch.CANCEL_WHY)
                        stop == PAUSED -> maker.cancelAll("Scanning is paused")
                        !on -> maker.cancelAuto("Auto-make switched off")
                        // Stopped by the picked-off guard (Tj, 2026-10-05): the bids it posted come down, the ones Tj approved by hand stay.
                        guarded -> maker.cancelAuto("Stopped by the picked-off guard")
                        else -> null
                    }
                }.onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; runCatching { problems.add("Make orders", e.message ?: e.javaClass.simpleName) } }
            }
        }
        // A crash saved as the last process went down ([AppExits.install]): into Recent problems at the time it happened.
        appScope.launch(Dispatchers.IO) {
            // A copy in Downloads/Vigilant first, so a crash on open can be sent to Claude from the Files app (v0.81.1).
            runCatching {
                val saved = File(app.filesDir, "last_crash.txt")
                if (saved.exists()) { val copy = File(app.cacheDir, "vigilant-last-crash.txt"); saved.copyTo(copy, overwrite = true); DiagnosticsShare.saveToDownloads(app.contentResolver, copy) }
            }
            AppExits.takeSavedCrash(app)?.let { (at, text) -> runCatching { problems.add("App crash", text, atMs = at, maxLength = problems.crashLength) } }
        }
        // Bets and bids told apart (Tj, 2026-10-07; [com.tjshea.vigilant.data.tracker.TrackedBet.isBid]): a record of an order the bid store knows, or that carries one of its
        // two tags, gets both, so an older fill (an import, one logged before the second tag) reads as a bid in the Tracker, Diagnostics and the scan study.
        appScope.launch(Dispatchers.IO) {
            runCatching { tracker.tagBids(makerStore.all().mapNotNullTo(HashSet()) { it.orderId }) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; runCatching { problems.add("Tracker", "Couldn't tell bids from bets: ${it.message ?: it.javaClass.simpleName}") } }
        }
    }

    private val loadMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile
    private var loadedConnection: Result<NovigConnection?>? = null

    /**
     * What any scan needs before its first request, with or without a screen (the background
     * auto-scan can run in a process no screen has opened): keys moved from v0.6.0's store, the
     * usage meters, and the connected Novig key (its reads go through the key's own rate limit).
     * Once per process; returns the Novig connection.
     */
    suspend fun ensureLoaded(): NovigConnection? = loadMutex.withLock {
        loadedConnection?.let { return@withLock it.getOrNull() }
        runCatching { migrateKeys() }
        runCatching { usage.load() }
        val connection = runCatching { novigConnection.load() }
        useConnection(connection.getOrNull())
        loadedConnection = Result.success(connection.getOrNull())
        connection.getOrNull()
    }

    /** The saved settings, brought up to date ([ScanSettings.migrate]). */
    suspend fun currentSettings(): ScanSettings = runCatching { settingsStore.read().migrate() }.getOrDefault(ScanSettings())

    /**
     * Starts Vigilant's own scan in [runner] (false when one is already running): the Scan button's
     * and the background auto-scan's. Lines Tj has open [bets] on are priced past the per-game cap,
     * so their closing value updates. Afterwards, even with no screen: the Tracker follows the new
     * prices, and the usage counters are saved.
     */
    /** [background]: a background auto-scan's cycle, not a scan Tj started (paced feeds leave part of the day for his: [referenceSources]). */
    fun startVigilantScan(requested: ScanSettings, bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>, background: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        // The long-run saver (RESEARCH.md §119): the background scan reads only the families Quick & likely bids go on; Tj's own scan never does.
        val settings = if (background && com.tjshea.vigilant.data.scanner.LongRunBids.leanApplies(requested)) requested.copy(leanScan = true) else requested
        val pinned = bets.filter { it.status == com.tjshea.vigilant.data.tracker.BetStatus.PENDING && it.startsTs > now }.mapTo(HashSet()) { it.marketId }
        // Low API usage bids: the markets a bid of ours rests on are read first, so each is re-posted from this scan's fair before its old one ends (RESEARCH.md §93).
        if (settings.lowUsageNow) pinned += com.tjshea.vigilant.data.novig.trading.maker.LowUsage.restingMarkets(makerStore.flow.value.orEmpty())
        val before = usage.flow.value
        // ParlayAPI's own figure for what's left, read for free (at most every few minutes): the pace decides from the key's word.
        if (settings.useParlay && keyStore.current(ApiProvider.PARLAY).isNotEmpty()) appScope.launch { runCatching { parlayAccount.refresh() } }
        return runner.start(settings, referenceSources(settings, background, scan = true), pinned) { report ->
            // A scan that ended with Vigilant off screen (background auto-scan, or Tj left) closes Novig's
            // live feed at once: nothing will recheck in the next two minutes, and pushes cost battery.
            if (!onScreen) novig.stream?.close()
            // Disk trouble (full storage) must never break a scan.
            report?.result?.let { runCatching { tracker.observe(it) } }
            // Keyed calls saved as they happened; this saves the keyless request counters.
            runCatching { usage.flush() }
            // What this scan cost each API, for Settings › Diagnostics & about (a scan that failed outright has no report and no cost to show).
            if (report != null) lastScanCost = RoundCost(now, System.currentTimeMillis() - now, UsageDelta.between(before, usage.flow.value))
        }
    }

    /** What the last Vigilant scan and the last Check odds now cost each API since the app opened ([RoundCost]), for Diagnostics. */
    @Volatile var lastScanCost: RoundCost? = null
    @Volatile var lastCheckCost: RoundCost? = null

    /**
     * Where a CNO bet opens in Novig: its bet slip, else its game ([TapLink]); null when nothing
     * answered. A tap in the widget or the CNO tab, and a background alert, all ask this.
     */
    suspend fun betLink(row: CnoRow): TapLink.Link? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val state = cno.state.value
        val paused = (state.pausedUntilMs ?: 0L) > System.currentTimeMillis()
        val found = TapLink.resolve(
            cached = cno.cachedLink(row),
            cnoFailing = state.error != null,
            // CNO asked for a pause: not even a tap asks it; Novig's catalog answers alone.
            fromCno = if (paused) null else ({ cno.novigLink(row) }),
            // Vigilant MGM has no public BetMGM catalog: CNO's own link is the way in.
            fromNovig = { if (AppBook.isNovig) betFinder.find(row) else null },
        )
        // An exact link from Novig's catalog is kept like CNO's: the next tap needs no network.
        if (found?.exact == true) cno.rememberLink(row, found.link)
        found
    }

    /**
     * Book reads go through the connected key's own rate limit (or the public routes when null), and a
     * scan's whole plan through the key's websocket ([NovigStream]: opened by a scan, closed two minutes
     * after the last one used it).
     */
    @Synchronized
    fun useConnection(connection: NovigConnection?) {
        readConnection = connection
        val signer = connection?.let(::readKeyClient)
        novig.stream?.close()
        novig.keyed = signer
        novig.stream = signer?.let { NovigStream(http, it, appScope) }
        val alias = connection?.tradingAlias
        val key = connection?.tradingKeyId
        val client = if (alias != null && key != null && alias in KeystoreVault.aliases(NovigBettingSetup.TRADING_PREFIX)) {
            NovigTradingClient(NovigSignedClient(http, json, KeystoreSigningKey(alias, key)), json, batchOrders = true)
        } else {
            null
        }
        trading = client
        subaccountKeyId = connection?.subaccountKeyId?.takeIf { client != null }
        apiSettler = client?.let { ApiSettler(tracker, it, connection?.subaccountKeyId.orEmpty(), scoreGrade = { bet -> settler.scoreGradeOf(bet) }) }
        apiSync = client?.let { ApiBetSync(tracker, it, novig) }
    }

    fun readKeyClient(connection: NovigConnection, client: OkHttpClient = http) =
        NovigSignedClient(client, json, KeystoreSigningKey(connection.readAlias, connection.readKeyId))

    /** The connected Novig key as [useConnection] last saw it (null: none): the score-burst recorder opens its own connection on its READ key. */
    @Volatile
    private var readConnection: NovigConnection? = null

    /** The burst recorder's files (RESEARCH.md §95): one journal a day, appended to, never rewritten. */
    val burstJournal = com.tjshea.vigilant.data.novig.burst.BurstJournal(File(app.filesDir, "burst"))

    /** The real-money burst trader's attempts (RESEARCH.md §95): one file a day, appended to, never rewritten. A cover's two legs are not Tracker bets (one of them always loses); this is their record. */
    val burstTradeJournal = com.tjshea.vigilant.data.novig.trading.burst.BurstTradeJournal(File(app.filesDir, "burst-trades"))

    /** What the trader sends orders through: whatever betting client is connected at that moment (the gate has checked there is one; a key removed mid-way fails the order, which halts it). */
    private val burstOrders = object : com.tjshea.vigilant.data.novig.trading.burst.BurstOrders {
        private fun client() = trading ?: error("no betting key connected")
        override suspend fun placeBatch(orders: List<NovigTradingClient.NewOrder>) = client().placeOrders(orders)
        override suspend fun order(orderId: String) = client().order(orderId)
        override suspend fun fills(orderId: String) = client().fills(orderId)
    }

    /** The recorder's proof for the trader, read from the journal at most this often (a window is judged in milliseconds; the journal is a file). */
    @Volatile private var burstProofCache: Pair<Long, com.tjshea.vigilant.data.novig.burst.BurstStudy.Proof>? = null

    suspend fun burstProof(force: Boolean = false): com.tjshea.vigilant.data.novig.burst.BurstStudy.Proof {
        val now = System.currentTimeMillis()
        burstProofCache?.takeIf { !force && now - it.first < BURST_PROOF_TTL_MS }?.let { return it.second }
        val proof = withContext(Dispatchers.IO) {
            com.tjshea.vigilant.data.novig.burst.BurstStudy.proof(burstJournal.readAll(), com.tjshea.vigilant.data.novig.trading.burst.BurstTradeLimits.MIN_NET, burst.latency)
        }
        burstProofCache = now to proof
        return proof
    }

    /** Tj's limits for the trader, from the saved settings, read at each window; off unless the recorder is on too (it is the recorder's windows it trades). */
    private fun burstRules(): com.tjshea.vigilant.data.novig.trading.burst.BurstTradeRules {
        val s = settingsStore.flow.value ?: return com.tjshea.vigilant.data.novig.trading.burst.BurstTradeRules(false, 0.0, 0.0, 0.0, 0.0)
        return com.tjshea.vigilant.data.novig.trading.burst.BurstTradeRules(
            enabled = AppBook.isNovig && s.burstTrade && s.burstRecorder && !s.killed,
            stakePerLeg = s.burstTradeStake.coerceIn(BURST_TRADE_MIN_STAKE, BURST_TRADE_MAX_STAKE),
            maxPerGame = s.burstTradeMaxGame, maxPerDay = s.burstTradeMaxDay, haltLoss = s.burstTradeHaltLoss, halted = s.burstTradeHalted,
        )
    }

    /**
     * Why the trader must not trade right now, or null: STOP ALL or a pause, no betting key, a wallet that cannot cover both legs, or a recorder that has not proved the idea
     * on this phone with these delays ([burstProof]). The words are fixed (they are counted in the status); the detail is [burstTradeNote].
     */
    private suspend fun burstGate(): String? {
        val s = settingsStore.flow.value ?: return "settings not loaded"
        val proved = burstProof().proved
        return com.tjshea.vigilant.data.novig.trading.burst.BurstTradeGate.reason(
            killed = s.killed, pausedByHand = s.pausedByHand, hasBettingKey = trading != null, walletDollars = wallet.flow.value?.dollars,
            stakePerLeg = s.burstTradeStake.coerceIn(BURST_TRADE_MIN_STAKE, BURST_TRADE_MAX_STAKE), proved = { proved },
        )
    }

    /** The trader: a [WindowSink] of the recorder, so it hears of each window at the moment it opens. */
    val burstTrader: com.tjshea.vigilant.data.novig.trading.burst.BurstTrader by lazy {
        com.tjshea.vigilant.data.novig.trading.burst.BurstTrader(
            orders = burstOrders, scope = appScope, rules = ::burstRules, gate = ::burstGate,
            ownBids = { makerStore.flow.value.orEmpty().filter { it.resting }.map { com.tjshea.vigilant.data.novig.trading.burst.OwnBid(it.marketId, it.outcomeId, it.price) } },
            journal = burstTradeJournal,
            onHalt = { why ->
                eventLog.warn("BURST", "trader halted: $why")
                appScope.launch {
                    withContext(kotlinx.coroutines.NonCancellable) {
                        runCatching { settingsStore.update { if (it.burstTradeHalted == null) it.copy(burstTradeHalted = why) else it } }
                        runCatching { AutoBetNotes.stopped(app, "Burst trading stopped", why) }
                    }
                }
            },
            lock = orderLock,
            leagueOk = { league -> burstProof().leagues.contains(league) },
        )
    }

    /** The feed race's files (RESEARCH.md §106): one journal a day under files/race, appended to, never rewritten. */
    val feedRaceJournal = com.tjshea.vigilant.data.live.FeedRaceJournal(File(app.filesDir, "race"))

    /**
     * The live feed test (Tj, 2026-10-07): it reads the free feeds of the games live on Novig and compares them with Novig's own price. **No order**: it is given no trading client, no key and no
     * order route, only public reads ([feedRaceFetch], the public trades and catalog).
     */
    val feedRace: com.tjshea.vigilant.data.live.FeedRaceRunner by lazy {
        com.tjshea.vigilant.data.live.FeedRaceRunner(
            scope = appScope, fetch = ::feedRaceFetch, sockets = com.tjshea.vigilant.data.live.OkHttpFeedSockets(http), liveGames = ::feedRaceGames,
            novigTrades = { id -> novig.trades(id) }, journal = feedRaceJournal,
        )
    }

    private suspend fun feedRaceFetch(url: String): com.tjshea.vigilant.data.live.Fetched? {
        val t0 = System.currentTimeMillis()
        return http.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").get().build()).await().use { r ->
            if (!r.isSuccessful) null else com.tjshea.vigilant.data.live.Fetched(r.body?.string().orEmpty(), System.currentTimeMillis() - t0)
        }
    }

    /** The games live on Novig now with their moneyline markets: what the feed test follows (one catalog read, then one for the markets, once a minute while it runs). */
    private suspend fun feedRaceGames(): List<com.tjshea.vigilant.data.live.LiveGame> {
        val leagues = Leagues.ALL.map { it.novigName }.toSet()
        val statuses = listOf(com.tjshea.vigilant.data.novig.NovigEvent.STATUS_LIVE)
        val live = novig.events(leagues, statuses).filter { it.status in statuses }
        if (live.isEmpty()) return emptyList()
        val ids = live.mapTo(HashSet()) { it.eventId }
        val money = novig.markets(leagues, listOf("MONEY", "MONEYLINE_3_WAY_WIN", "1X2"), statuses).filter { it.eventId in ids && it.isOpen }.groupBy { it.eventId }
        return live.map { e -> com.tjshea.vigilant.data.live.LiveGame(e.eventId, e.description, e.league, e.sport, money[e.eventId]?.firstOrNull()?.marketId) }
    }

    /** The paper lab's files (RESEARCH.md §120.6): one journal a day each for the would-be bets and their grades, appended to, never rewritten. */
    val labJournal by lazy { com.tjshea.vigilant.data.pinnodds.DayJournal(File(app.filesDir, "lab"), "lab", com.tjshea.vigilant.data.novig.lab.LabRecord.serializer()) { it.atMs } }
    val labGradeJournal by lazy { com.tjshea.vigilant.data.pinnodds.DayJournal(File(app.filesDir, "lab"), "lab-grade", com.tjshea.vigilant.data.novig.lab.LabGrade.serializer()) { it.atMs } }

    /**
     * The paper lab (Tj, 2026-10-09): ladder covers, late-game tail strikes and alternate lines, all on paper. **No order**: it is given the public Novig source, ESPN's public scoreboard and Pinnacle's alternate
     * lines from the Pinnodds feed when that is on, and no trading client or key.
     */
    val lab: com.tjshea.vigilant.data.novig.lab.LabRecorder by lazy {
        com.tjshea.vigilant.data.novig.lab.LabRecorder(
            scope = appScope, source = novig, fetch = ::feedRaceFetch, altQuotes = ::labAltQuotes,
            journal = labJournal, gradeJournal = labGradeJournal, bidLab = bidLab, grader = labGrader,
        )
    }

    /** The lab's SportsGameOdds boards by league: one read serves every game of the league for [LAB_BOARD_MS] (SGO refreshes about every 30 s). */
    private val labBoards = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, com.tjshea.vigilant.data.reference.RefSnapshot>>()

    /**
     * The paper lab's outside alternate lines for a Novig game (Tj, 2026-10-09: "incorporate sportsgamesodds pro into the research labs"): SportsGameOdds' main and alternate spreads and totals of the game,
     * every book, live games included, matched to the Novig game by teams and start. Nothing while SGO Pro is off or has no key (Pinnodds is dormant): the lab then has no outside quotes and records only
     * the ladder covers and tail strikes.
     */
    private suspend fun labAltQuotes(ev: com.tjshea.vigilant.data.novig.NovigEvent): List<com.tjshea.vigilant.data.novig.lab.AltQuote> {
        val s = currentSettings()
        // Pinnodds' live Pinnacle lines first when its socket is running (Tj, 2026-10-09: the app still uses Pinnodds; "stop" meant Claude's own connections), then SGO's.
        val pinn = if (!com.tjshea.vigilant.data.scanner.Dormant.PINNODDS && pinnRunner.running) runCatching { pinnRunner.altQuotes(ev.eventId) }.getOrDefault(emptyList()) else emptyList()
        val useSgo = sgoActive(s)
        val useOp = !useSgo && opActive(s)
        if (!useSgo && !useOp) return pinn
        val league = com.tjshea.vigilant.data.scanner.Leagues.byNovigName(ev.league) ?: return pinn
        if (!(if (useSgo) com.tjshea.vigilant.data.reference.SgoBooks.supports(league) else com.tjshea.vigilant.data.reference.OpBooks.supports(league))) return pinn
        val now = System.currentTimeMillis()
        val snap = labBoards[league.novigName]?.takeIf { now - it.first < LAB_BOARD_MS }?.second
            ?: (if (useSgo) sgoGames.odds(league, s.copy(includeLive = true)) else opGames.odds(league, s.copy(includeLive = true, opAltLines = true))).also { labBoards[league.novigName] = now to it }
        val match = com.tjshea.vigilant.data.scanner.Planner.matchEvents(listOf(ev), listOf(snap)).firstOrNull()?.refEvent ?: return pinn
        val novigHome = com.tjshea.vigilant.data.novig.NovigText.parseMatchup(ev.description)?.home.orEmpty()
        val swapped = com.tjshea.vigilant.data.match.TeamMatcher.whichOf(novigHome, match.home, match.away) == 2
        return pinn + com.tjshea.vigilant.data.novig.lab.SgoAltQuotes.quotes(match, swapped)
    }

    /** The paper bid lab's files (RESEARCH.md §122): the paper bids when they went up and what happened to them. */
    /** Grades the paper lab from final scores (SGO, OddsPapi, ESPN, MLB): Novig drops a settled market, so its own status never arrives. */
    val labGrader by lazy { com.tjshea.vigilant.data.novig.lab.LabGrader(scores) }
    val bidLabBidJournal by lazy { com.tjshea.vigilant.data.pinnodds.DayJournal(File(app.filesDir, "lab"), "bidlab", com.tjshea.vigilant.data.novig.lab.BidLabBid.serializer()) { it.atMs } }
    val bidLabEventJournal by lazy { com.tjshea.vigilant.data.pinnodds.DayJournal(File(app.filesDir, "lab"), "bidlab-event", com.tjshea.vigilant.data.novig.lab.BidLabEvent.serializer()) { it.atMs } }

    /** Paper bids on every line the bid desk looks at and on Pinnacle-priced live lines: **no order**, public trades and markets only. */
    val bidLab: com.tjshea.vigilant.data.novig.lab.BidLab by lazy {
        com.tjshea.vigilant.data.novig.lab.BidLab(trades = { id -> novig.trades(id, 60) }, market = { id -> novig.market(id) }, bidJournal = bidLabBidJournal, eventJournal = bidLabEventJournal, grader = labGrader)
            // Bids still resting and fills still waiting for their result come back from the journals after a stop or a restart (the journals themselves are never touched).
            .also { runCatching { it.restore(bidLabBidJournal.readAll(), bidLabEventJournal.readAll(), System.currentTimeMillis()) } }
    }
    @Volatile private var bidLabJob: kotlinx.coroutines.Job? = null

    /** Whether research (the paper lab and paper bids) is on now: the master switch or the lab's own, not STOP ALL, the Novig app. */
    fun researchOn(): Boolean {
        val s = settingsStore.flow.value ?: return false
        return AppBook.isNovig && (s.researchMode || s.altLab) && !s.killed
    }

    /** Starts or stops the paper lab to match [s]: on, not STOP ALL, the Novig app. Safe to call on every settings change. */
    fun labTick(s: ScanSettings) {
        if (!AppBook.isNovig) return
        val on = (s.altLab || s.researchMode) && !s.killed
        if (on) lab.start(LAB_LEAGUES) else if (lab.running) lab.stop()
        // A foreground service holds the process while research is on (Tj, 2026-10-10), so Android does not end it a few minutes after he leaves.
        if (on) ResearchService.start(app) else ResearchService.stop(app)
        // The paper bid lab's tape reader: every 45 s while research is on (its lines come from the Bids passes and the paper lab).
        if (on && bidLabJob?.isActive != true) {
            bidLabJob = appScope.launch {
                while (true) {
                    runCatching { bidLab.poll(System.currentTimeMillis()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    kotlinx.coroutines.delay(45_000L)
                }
            }
        } else if (!on) { bidLabJob?.cancel(); bidLabJob = null }
    }

    /** Starts or stops the feed test to match [s]: on, not STOP ALL, the Novig app. Safe to call on every settings change. */
    fun feedRaceTick(s: ScanSettings) {
        if (!AppBook.isNovig) return
        if ((s.feedRace || s.researchMode) && !s.killed) feedRace.start() else if (feedRace.running) feedRace.stop()
    }

    /**
     * The score-burst recorder (Tj, 2026-10-06): its connection never places an order. Its connection is the read key's own websocket ([NovigStream] with a listener), its echo probe is a signed `POST /v3/echo`
     * (free), and it is given no trading client at all, so nothing in it can place or cancel an order whatever the code did.
     */
    val burst: com.tjshea.vigilant.data.novig.burst.BurstRecorder by lazy {
        com.tjshea.vigilant.data.novig.burst.BurstRecorder(
            scope = appScope,
            source = novig,
            newFeed = { listener -> readConnection?.let { NovigStream(http, readKeyClient(it), appScope, idleCloseMs = BURST_IDLE_CLOSE_MS, bookListener = listener) } },
            echo = { (novig.keyed ?: error("no Novig key")).echo() },
            trades = { id -> novig.trades(id) },
            journal = burstJournal,
            windowSink = burstTrader,
        )
    }

    /** True while the settings carry a halt of the burst trader, so the Resume that clears it is told to the trader once ([burstTick]). */
    @Volatile private var burstSawHalt = false

    /** Starts or stops the burst recorder to match [s]: on, a key connected, STOP ALL not pressed, the Novig app. Safe to call on every settings change. */
    suspend fun burstTick(s: ScanSettings) {
        if (!AppBook.isNovig) return
        if (!(s.burstRecorder || s.researchMode) || s.killed) {
            if (burst.running) burst.stop(if (s.killed) "stopped by STOP ALL" else null)
            return
        }
        ensureLoaded()
        if (s.burstTradeHalted != null) burstSawHalt = true else if (burstSawHalt) { burstSawHalt = false; burstTrader.resumed() }
        if (s.burstTrade) runCatching { wallet.fresh() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        // New leagues take effect at once: the run in progress stops (its windows are written) and a new one starts when it has finished.
        if (burst.running && burst.status.value.leagues != s.burstLeagues) burst.stop()
        burst.start(s.burstLeagues, s.apiMaxStake)
    }

    // ---- Pinnodds live (Tj, 2026-10-08; RESEARCH.md §116): Pinnacle's live price on the Pinnodds WebSocket against Novig's live books -----------------------------------

    /** Decisions (real and paper) and their 30 s / 120 s follow-ups: one file a day each, appended to, never rewritten. */
    val pinnJournal = com.tjshea.vigilant.data.pinnodds.DayJournal(File(app.filesDir, "pinn-live"), "pinn-live", com.tjshea.vigilant.data.pinnodds.LiveRecord.serializer()) { it.atMs }
    val pinnReopenJournal = com.tjshea.vigilant.data.pinnodds.DayJournal(File(app.filesDir, "pinn-live"), "pinn-reopen", com.tjshea.vigilant.data.pinnodds.ReopenProbe.serializer()) { it.atMs }
    val pinnFollowJournal = com.tjshea.vigilant.data.pinnodds.DayJournal(File(app.filesDir, "pinn-live"), "pinn-follow", com.tjshea.vigilant.data.pinnodds.LiveFollow.serializer()) { it.atMs }

    /** What the live trader sends orders through: whatever betting client is connected at that moment (the gate has checked there is one; a key removed mid-way fails the order, which halts it). */
    private val pinnOrders = object : com.tjshea.vigilant.data.pinnodds.LiveOrders {
        private fun client() = trading ?: error("no betting key connected")
        override suspend fun place(outcomeId: String, price: Double, qty: Long, clientId: String) = client().placeOrder(outcomeId, price, qty, "IOC", clientId)
        override suspend fun order(orderId: String) = client().order(orderId)
        override suspend fun fills(orderId: String) = client().fills(orderId)
    }

    /** Tj's limits for the live trader, read at each decision. A stake is never over his per-bet maximum for the API. */
    private fun pinnTradeRules(): com.tjshea.vigilant.data.pinnodds.LiveTradeRules {
        val s = settingsStore.flow.value ?: return com.tjshea.vigilant.data.pinnodds.LiveTradeRules(false, false, 0.0, 0.0, 0.0, 0.0)
        return com.tjshea.vigilant.data.pinnodds.LiveTradeRules(
            enabled = AppBook.isNovig && !com.tjshea.vigilant.data.scanner.Dormant.PINNODDS && s.pinnLive && !s.killed, bet = s.pinnLiveBet,
            stake = if (s.apiMaxStake > 0.0) minOf(s.pinnLiveStake, s.apiMaxStake) else s.pinnLiveStake,
            maxPerGame = s.pinnLiveMaxGame, maxPerDay = s.pinnLiveMaxDay, haltLoss = s.pinnLiveHaltLoss, halted = s.pinnLiveHalted,
        )
    }

    /** Why a REAL bet must not be made now (words are counted in the status), or null. Paper decisions need none of it. */
    private suspend fun pinnGate(): String? {
        val s = settingsStore.flow.value ?: return "settings not loaded"
        val stake = pinnTradeRules().stake
        val wallet = wallet.flow.value?.dollars
        return when {
            s.killed -> "STOP ALL is on"
            s.pausedByHand -> "scanning is paused"
            trading == null -> "no betting key connected"
            wallet == null -> "wallet not read yet"
            wallet < stake * 1.2 -> "wallet too low"
            else -> null
        }
    }

    /** Dollars lost today on settled live bets (positive = loss): the day's halt. */
    private suspend fun pinnLossToday(): Double {
        val from = ApiBetPlacer.localMidnight(System.currentTimeMillis())
        val net = tracker.all().filter { it.source == BetTracker.SOURCE_PINNODDS && (it.settledAtMs ?: 0L) >= from }.sumOf { it.profit ?: 0.0 }
        return (-net).coerceAtLeast(0.0)
    }

    val pinnTrader: com.tjshea.vigilant.data.pinnodds.PinnLiveTrader by lazy {
        com.tjshea.vigilant.data.pinnodds.PinnLiveTrader(
            orders = pinnOrders, scope = appScope, rules = ::pinnTradeRules, gate = ::pinnGate,
            ownBids = {
                makerStore.flow.value.orEmpty().filter { it.resting }.map { com.tjshea.vigilant.data.pinnodds.LiveOwnBid(it.marketId, it.outcomeId, it.price) } +
                    liveBidDesk.bidsNow().filter { it.active }.map { com.tjshea.vigilant.data.pinnodds.LiveOwnBid(it.marketId, it.outcomeId, it.price) }
            },
            journal = pinnJournal,
            onHalt = { why ->
                eventLog.warn("PINNLIVE", "trader halted: $why")
                appScope.launch {
                    withContext(kotlinx.coroutines.NonCancellable) {
                        runCatching { settingsStore.update { if (it.pinnLiveHalted == null) it.copy(pinnLiveHalted = why) else it } }
                        runCatching { AutoBetNotes.stopped(app, "Live betting stopped", why) }
                    }
                }
            },
            logFills = { target, orderId, fills -> tracker.logApi(target, orderId, fills) != null },
            lossToday = ::pinnLossToday, lock = orderLock,
        )
    }

    private fun pinnConfig(): com.tjshea.vigilant.data.pinnodds.LiveConfig {
        val s = settingsStore.flow.value ?: ScanSettings()
        return com.tjshea.vigilant.data.pinnodds.LiveConfig(
            rules = com.tjshea.vigilant.data.pinnodds.LiveRules(minEv = s.pinnLiveMinEv, minMove = s.pinnLiveMinMove, pregame = s.pinnLivePregame, trigger = s.pinnLiveTrigger, holdoffMs = s.pinnLiveHoldoffSeconds * 1000L),
            method = s.pinnLiveDevig, leagues = Leagues.ALL.map { it.novigName }.toSet() + com.tjshea.vigilant.data.pinnodds.PinnLiveRunner.EXTRA_LEAGUES,
            // The lag taker judges only while its own switch is on; with only the live bids on the feed runs for them alone.
            takerOn = s.pinnLive,
        )
    }

    /**
     * The live engine: one Pinnodds socket (the account allows one) and one Novig book feed on the connected key, judging Pinnacle's price against Novig's lagging quote. Nothing is sent unless
     * Settings › Pinnodds live › "Place real bets" is on; the trader is the only thing that can place an order, and it takes the app's one-order lock.
     */
    val pinnRunner: com.tjshea.vigilant.data.pinnodds.PinnLiveRunner by lazy {
        com.tjshea.vigilant.data.pinnodds.PinnLiveRunner(
            scope = appScope, source = novig,
            newFeed = { listener -> readConnection?.let { NovigStream(http, readKeyClient(it), appScope, idleCloseMs = BURST_IDLE_CLOSE_MS, bookListener = listener) } },
            openFeed = { onFrame -> com.tjshea.vigilant.data.pinnodds.PinnSocket(http, { keyStore.current(ApiProvider.PINNODDS).firstOrNull() }, appScope, onFrame) },
            trader = pinnTrader, config = ::pinnConfig, followJournal = pinnFollowJournal, reopenJournal = pinnReopenJournal,
            bids = liveBidDesk, bidConfig = ::liveBidConfig,
        )
    }

    // ---- Live bids (Tj, 2026-10-10; RESEARCH.md §123-§124): post-only bids on Novig's live lines, priced under Pinnacle's live fair --------------------------------------------

    /** Every thing the live bid desk did (posted, pulled, filled, followed up, halted), one file a day, appended to, never rewritten: the research record. */
    val liveBidJournal = com.tjshea.vigilant.data.pinnodds.DayJournal(File(app.filesDir, "live-bids"), "livebid", com.tjshea.vigilant.data.livebid.LiveBidEvent.serializer()) { it.atMs }

    /** What the desk sends orders through: whatever betting client is connected at that moment (a real bid does not go up without one: [liveBidConfig]'s `blockedWhy`). */
    private val liveBidOrders = object : com.tjshea.vigilant.data.livebid.LiveBidOrders {
        private fun client() = trading ?: error("no betting key connected")
        override suspend fun place(outcomeId: String, price: Double, qty: Long, ttlMs: Long, clientId: String) = client().placeOrder(outcomeId, price, qty, "PO", clientId, ttlMs)

        override suspend fun cancel(orderIds: List<String>): Map<String, String?> {
            val c = client()
            if (orderIds.size > 1 && c.batchOrders) {
                try {
                    val r = c.cancelOrdersBatch(orderIds)
                    return orderIds.associateWith { id -> if (id in r.canceled) "OPEN" else r.notCanceled[id]?.takeIf { it == "FILLED" } }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One by one below.
                }
            }
            val out = HashMap<String, String?>()
            var failure: Exception? = null
            for (id in orderIds) {
                try {
                    out[id] = c.cancelOrder(id)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (failure == null) failure = e
                }
            }
            failure?.let { throw it }
            return out
        }

        override suspend fun open() = client().orders("OPEN")

        override suspend fun find(clientId: String, outcomeId: String): com.tjshea.vigilant.data.novig.trading.NovigOrder? {
            val c = client()
            for (status in listOf("OPEN", "PENDING", "FILLED", "CANCELED", "REJECTED")) {
                val hit = try {
                    c.orders(status, outcomeId = outcomeId).firstOrNull { it.clientId == clientId }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (hit != null) return hit
            }
            return null
        }

        override suspend fun order(orderId: String) = client().order(orderId)
        override suspend fun fills(orderId: String) = client().fills(orderId)

        // One read for many orders: every fill on games that started after the earliest of theirs, less a day (a read costs the `history` bucket 8 + 1 per 50 rows).
        override suspend fun fillsOf(orderIds: Collection<String>, startsAfterMs: Long): Map<String, List<com.tjshea.vigilant.data.novig.trading.NovigFill>> {
            val c = client()
            if (orderIds.size <= 1) return orderIds.associateWith { c.fills(it) }
            val wanted = orderIds.toSet()
            return c.fillsStartingAfter(startsAfterMs).filter { it.orderId in wanted }.groupBy { it.orderId }
        }
    }

    /** Tj's live bid switches, rules and limits as the desk reads them at each look. STOP ALL and the Pause button switch it off (everything comes down); a real bid also needs the betting key. */
    private fun liveBidConfig(): com.tjshea.vigilant.data.livebid.LiveBidConfig {
        val s = settingsStore.flow.value ?: return com.tjshea.vigilant.data.livebid.LiveBidConfig.OFF
        val on = AppBook.isNovig && !com.tjshea.vigilant.data.scanner.Dormant.PINNODDS && s.liveBid && !s.paused
        val blocked = if (s.liveBidReal && trading == null) "no betting key connected" else null
        val preset = com.tjshea.vigilant.data.livebid.LiveBidPresets.active(s)?.name ?: s.liveBidPresetName?.let { "$it (changed)" }
        return com.tjshea.vigilant.data.livebid.LiveBidConfig(
            on = on, real = s.liveBidReal, quality = s.liveBidQuality, limits = s.liveBidLimits, bankroll = s.bankroll, apiMaxStake = s.apiMaxStake, preset = preset,
            halted = s.liveBidHalted, blockedWhy = blocked,
        )
    }

    /** Dollars of API bets filled today across every desk, locks left out (as the Bet sheet's and the bid desk's daily limit count them). */
    private suspend fun liveBidSpentToday(): Double {
        val from = ApiBetPlacer.localMidnight(System.currentTimeMillis())
        return tracker.all().filter { it.orderId != null && !it.isLock && it.createdAtMs >= from }.sumOf { it.stake }
    }

    /** Dollars lost today on settled live bids (positive = a loss): the day's halt. */
    private suspend fun liveBidLossToday(): Double {
        val from = ApiBetPlacer.localMidnight(System.currentTimeMillis())
        val net = tracker.all().filter { it.source == BetTracker.SOURCE_LIVEBID && (it.settledAtMs ?: 0L) >= from }.sumOf { it.profit ?: 0.0 }
        return (-net).coerceAtLeast(0.0)
    }

    /**
     * The live bid desk: it owns the orders (post-only, a ttl, pulled the moment the runner stops vouching for them) and answers for the money at risk. Real orders take the app's one-order lock
     * only while being placed; a pull never waits for it. Fills go to the Tracker as "Live bids".
     */
    val liveBidDesk: com.tjshea.vigilant.data.livebid.LiveBidDesk by lazy {
        com.tjshea.vigilant.data.livebid.LiveBidDesk(
            scope = appScope, orders = liveBidOrders, store = com.tjshea.vigilant.data.livebid.LiveBidStore(File(app.filesDir, "live-bids.json")), journal = liveBidJournal, config = ::liveBidConfig,
            wallet = { wallet.flow.value?.dollars },
            otherRestingDollars = { makerStore.flow.value.orEmpty().filter { it.resting }.sumOf { it.restingDollars } },
            spentToday = ::liveBidSpentToday, dayLimit = { settingsStore.flow.value?.apiMaxPerDay ?: 0.0 }, lossToday = ::liveBidLossToday,
            logFills = { target, orderId, fills ->
                val bet = tracker.logMakerFills(target, orderId, fills)
                // The wallet changed: read it again, so the next bid is sized against the money that is really there.
                if (bet != null) appScope.launch { runCatching { wallet.fresh() } }
                bet?.id
            },
            onHalt = { why ->
                eventLog.warn("LIVEBID", "live bids halted: $why")
                appScope.launch {
                    withContext(kotlinx.coroutines.NonCancellable) {
                        runCatching { settingsStore.update { if (it.liveBidHalted == null) it.copy(liveBidHalted = why) else it } }
                        runCatching { AutoBetNotes.stopped(app, "Live bids stopped", why) }
                    }
                }
            },
            lock = orderLock, version = BuildConfig.VERSION_NAME,
        )
    }

    @Volatile private var pinnSawHalt = false
    @Volatile private var liveBidSawHalt = false

    /**
     * Starts or stops the live engine to match [s]: on (the lag taker, the live bids, or both), a Pinnodds key saved, a Novig key connected, STOP ALL not pressed, the Novig app. The one Pinnodds socket and
     * the one Novig book feed serve both. Safe to call on every settings change.
     */
    suspend fun pinnTick(s: ScanSettings) {
        if (!AppBook.isNovig) return
        val wanted = s.pinnLive || s.liveBid
        if (com.tjshea.vigilant.data.scanner.Dormant.PINNODDS || !wanted || s.killed) {   // dormant (Tj, 2026-10-09): never opened
            if (pinnRunner.running) pinnRunner.stop(if (s.killed) "stopped by STOP ALL" else null)
            LiveFeedService.stop(app)
            return
        }
        ensureLoaded()
        if (s.pinnLiveHalted != null) pinnSawHalt = true else if (pinnSawHalt) { pinnSawHalt = false; pinnTrader.resumed() }
        if (s.liveBidHalted != null) liveBidSawHalt = true else if (liveBidSawHalt) { liveBidSawHalt = false; liveBidDesk.resumed() }
        if (s.pinnLiveBet || (s.liveBid && s.liveBidReal)) runCatching { wallet.fresh() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        if (keyStore.current(ApiProvider.PINNODDS).isEmpty()) {
            if (pinnRunner.running) pinnRunner.stop("No Pinnodds key saved (Settings › Pinnodds live).")
            LiveFeedService.stop(app)
            return
        }
        pinnRunner.start()
        // The foreground service holds the process and the CPU awake with the screen off (allowed from the screen, a boot or an update; refused quietly otherwise).
        LiveFeedService.start(app)
    }

    private val polymarket = PolymarketClient(http, json, usage = usage)
    private val kalshi = KalshiClient(http, json, altBaseUrl = KalshiClient.ALT_URL, usage = usage, fastPace = { settingsStore.flow.value?.kalshiFastPace ?: true })

    /** How the Kalshi 3-requests-a-second test has gone this session (Diagnostics). */
    fun kalshiPaceNote(): String = kalshi.paceNote()
    private val oddsApi = TheOddsApiClient(http, KeyPool(QuotaPolicy.ODDS_API, { keyStore.current(ApiProvider.THE_ODDS_API) }, usage), json)

    /** ParlayAPI's degraded-mode check (free): books it says aren't keeping up don't price from it. */
    private val parlayQuality = com.tjshea.vigilant.data.reference.ParlaySourceQuality(http, json)

    /** ParlayAPI: The Odds API's format at parlay-api.com (Pinnacle, ProphetX, bet365 and the US books, props too; RESEARCH.md §43). */
    val parlayOdds = TheOddsApiClient(
        http, parlayPool, json,
        baseUrl = com.tjshea.vigilant.data.reference.OddsFeed.PARLAY.base, feed = com.tjshea.vigilant.data.reference.OddsFeed.PARLAY,
        quality = parlayQuality,
    )
    /** Whether ParlayAPI is on and has a key: its extra calls (injuries, the Check odds now backup, …) are made only then. */
    private suspend fun parlayActive(): Boolean = keyStore.current(ApiProvider.PARLAY).isNotEmpty() && currentSettings().useParlay

    /** A whole league's player props in one 3-credit call (RESEARCH.md §43). */
    private val parlayProps = com.tjshea.vigilant.data.reference.ParlayPropsSource(parlayOdds, injuries)

    /** Pinnacle's biggest moneyline moves per league (ParlayAPI's public /v1/meta/movers: free, no key; PARLAY_API.md §6.3). */
    val parlayMovers = com.tjshea.vigilant.data.reference.ParlayMovers(http, json)

    /** ESPN's injury list through ParlayAPI (1 credit a league, 10 min apart) for listed or open prop bets no props answer covered. */
    val parlayInjuries = com.tjshea.vigilant.data.reference.ParlayInjuries(parlayOdds, injuries, json, active = { parlayActive() && !sgoActive(currentSettings()) })

    /**
     * What bids priced from CrazyNinjaOdds read and judge (Tj, 2026-10-07; [com.tjshea.vigilant.data.novig.trading.maker.CnoBidLane], RESEARCH.md §114): the background cycle calls its `step`,
     * the Make pass its `lines`. Nothing in it runs unless Settings' "Bids priced from" is CrazyNinjaOdds and bids are on.
     */
    val cnoBids = com.tjshea.vigilant.data.novig.trading.maker.CnoBidLane(
        cno = cno,
        novig = { rows -> live.targetsNow(rows) },
        urlFor = { s -> UiState(settings = s, loaded = true).cnoUrl },
        askInjuries = { rows -> askCnoInjuries(rows) },
        count = { key, n -> eventLog.count(key, n) },
    )

    /** The prop players of [rows] no injury report covers yet are asked about (one read a sport at most every [com.tjshea.vigilant.data.reference.ParlayInjuries.REUSE_MS], and only with ParlayAPI on). */
    private fun askCnoInjuries(rows: List<CnoRow>) {
        val now = System.currentTimeMillis()
        val book = injuries.book.value
        val uncovered = HashMap<String, MutableSet<String>>()
        for (r in rows) {
            val want = com.tjshea.vigilant.data.reference.InjuryTags.wantOf(r) ?: continue
            if (want.sportKey !in com.tjshea.vigilant.data.reference.ParlayInjuries.SPORTS || book.covers(want.sportKey, want.player, now)) continue
            uncovered.getOrPut(want.sportKey) { HashSet() } += want.player
        }
        uncovered.forEach { (sport, players) -> appScope.launch { parlayInjuries.fill(sport, players) } }
    }

    /** ParlayAPI's own +EV list at Novig (its /best-bets, 10 credits a league, only on a tap; PARLAY_API.md §6.5). */
    val parlayBestBets = com.tjshea.vigilant.data.reference.ParlayBestBets(parlayOdds, json, active = { parlayActive() })

    /** "Second opinion" on one bet (ParlayAPI's /v1/verdict, 5 credits, only on a tap; PARLAY_API.md §6.4). */
    val parlayVerdicts = com.tjshea.vigilant.data.reference.ParlayVerdicts(parlayOdds, json, active = { parlayActive() })

    /** Check odds now's backup for CNO (Tj, 2026-09-30): every book's price for a bet from ParlayAPI, while it's on with a key. */
    val parlayBooks = com.tjshea.vigilant.data.tracker.ParlayBooks(parlayOdds, active = { parlayActive() }, injuries = injuries)

    /** The same for background auto-scans: they leave half of each day's ParlayAPI share for the scans Tj starts himself. */
    private val parlayOddsBackground = TheOddsApiClient(
        http, parlayPool, json,
        baseUrl = com.tjshea.vigilant.data.reference.OddsFeed.PARLAY.base, feed = com.tjshea.vigilant.data.reference.OddsFeed.PARLAY, background = true,
        quality = parlayQuality,
    )
    private val parlayPropsBackground = com.tjshea.vigilant.data.reference.ParlayPropsSource(parlayOddsBackground, injuries)
    /** 1st-half spreads and totals from more books (ParlayAPI's period markets), for Tj's scans and for auto-scan's. */
    private val parlayHalves = com.tjshea.vigilant.data.reference.ParlayPeriodSource(parlayOdds, json)
    private val parlayHalvesBackground = com.tjshea.vigilant.data.reference.ParlayPeriodSource(parlayOddsBackground, json)
    /** Sportsbook player props: the same client, key pool and meter as the main lines. */
    private val bookProps = OddsApiPropsSource(oddsApi)
    /** Pinnacle: PinnWire's keys first (their free keys include player props), then pinnapi's. */
    private val pinnacle = PinnapiClient(
        http, json,
        listOf(
            PinnapiClient.pinnwire(KeyPool(QuotaPolicy.PINNWIRE, { keyStore.current(ApiProvider.PINNWIRE) }, usage)),
            PinnapiClient.pinnapi(KeyPool(QuotaPolicy.PINNAPI, { keyStore.current(ApiProvider.PINNAPI) }, usage)),
        ),
        // SGO Pro mode with a Pinnodds key (Tj, 2026-10-09): Pinnodds' board is asked first, so Pinnacle's price in every scan, bid and EV is the fresh one; the others carry on if it fails.
        first = { settingsStore.flow.value?.let { s -> if (pinnoddsActive(s)) pinnoddsHost else null } },
    )
    private val pinnoddsHost = PinnapiClient.pinnodds(KeyPool(QuotaPolicy.PINNODDS, { keyStore.current(ApiProvider.PINNODDS) }, usage))

    /** SGO Pro or OddsPapi is on and a Pinnodds key is saved: Pinnodds is no longer dormant and its Pinnacle board prices the scan before any other Pinnacle feed. */
    fun pinnoddsActive(s: ScanSettings): Boolean = AppBook.isNovig && (s.sgoPro || s.oddsPapi) && keyStore.current(ApiProvider.PINNODDS).isNotEmpty()

    /** For Diagnostics: which feed's Pinnacle board the last scan used. */
    fun pinnacleFeedNote(now: Long = System.currentTimeMillis()): String =
        pinnacle.lastHost?.let { "$it, ${(now - pinnacle.lastBoardAtMs) / 1000L}s old when read" } ?: "no board read yet"

    /** Thirty sportsbooks' lines (per league) and props (per game) through one free PropLine key. */
    private val propLine = PropLineClient(
        http, KeyPool(QuotaPolicy.PROPLINE, { keyStore.current(ApiProvider.PROPLINE) }, usage), json,
        // Vigilant MGM: no Novig reads to order, and BetMGM's own ids for its bet-slip links.
        relayNovig = AppBook.isNovig, bookIds = !AppBook.isNovig,
    )
    private val propLineProps = PropLinePropsSource(propLine)

    /**
     * Every other sportsbook's odds for a tapped ParlayAPI pick (Tj, 2026-09-30: "always see other sports books odds for any bet when I click on
     * it"): ParlayAPI and PropLine side by side, The Odds API last, each only while it's on with a key, and none while Vigilant's scanner is
     * asleep (CNO only). Display only: never a fair line.
     */
    val otherBooks = com.tjshea.vigilant.data.reference.OtherBooks(parlayOdds, propLine, oddsApi, json, on = {
        val s = currentSettings()
        // SportsGameOdds Pro carries every book these three do, in one read: while it answers it is the only one asked (and falls back to them if it is down).
        val sgo = s.vigilantOn && sgoActive(s)
        // OddsPapi likewise (its props are the scan's own read): the paid three rest while it answers.
        val op = s.vigilantOn && opActive(s) && !sgo
        val rest = !sgo && !op
        com.tjshea.vigilant.data.reference.OtherBooks.Sources(
            parlay = rest && s.vigilantOn && parlayActive(),
            propLine = rest && s.vigilantOn && s.usePropLine && keyStore.current(ApiProvider.PROPLINE).isNotEmpty(),
            oddsApi = rest && s.vigilantOn && s.useOddsApi && keyStore.current(ApiProvider.THE_ODDS_API).isNotEmpty(),
            sgo = sgo,
            op = op,
        )
    }, sgo = sgoClient, op = { league -> opFeed.read(league, currentSettings().let { it.copy(families = it.families + com.tjshea.vigilant.data.scanner.MarketFamily.PLAYER_PROPS) }).props })

    /**
     * Moves keys saved by v0.6.0 and earlier (encrypted with a Keystore key, which a backup
     * restored onto another phone can't decrypt) into the plain key file, once. Safe to call on
     * every launch: a provider that already has keys in the file is left alone.
     */
    suspend fun migrateKeys() {
        for (provider in ApiProvider.entries) {
            if (keyStore.getKeys(provider).isNotEmpty()) continue
            val old = runCatching { legacyKeyStore.getKeys(provider) }.getOrDefault(emptyList())
            if (old.isNotEmpty()) {
                keyStore.setKeys(provider, old)
                runCatching { legacyKeyStore.setKeys(provider, emptyList()) }
            }
        }
    }

    /**
     * The fair-odds providers a scan should call, for the switches in Settings and the keys on
     * this phone. Clients live for the whole process; the key pools read the current keys on
     * every call, so adding or removing a key takes effect on the next scan.
     */
    fun referenceSources(settings: ScanSettings, background: Boolean = false, scan: Boolean = false): List<ReferenceSource> = when {
        // Low-usage bids narrow Vigilant's own scan only ([scan]): what prices Tj's open bets, the sharp-book confirmations and the rest read the usual feeds.
        // SportsGameOdds Pro has no per-call budget to save (unlimited events), so low-usage bids narrow nothing while it answers.
        scan && settings.lowUsageNow && !sgoActive(settings) && !opActive(settings) -> lowUsageSources(settings, background)
        else -> allReferenceSources(settings, background, scan)
    }

    /**
     * Low API usage bids (Tj, 2026-10-05; RESEARCH.md §92): the fewest feeds that carry the picked sharp prop books ([LowUsageBids.feedsFor]) among the ones with a key and
     * a switch on in Settings (Kalshi needs no key), each asked only for a league that has a game inside the window with a prop market on Novig ([LowUsageSource]). Never
     * The Odds API, Polymarket, a game-line board or ParlayAPI's odds, alternates and 1st-half calls.
     */
    internal fun lowUsageSources(settings: ScanSettings, background: Boolean): List<ReferenceSource> = lowUsagePlan(settings).feeds.map { feed ->
        LowUsageSource(
            when (feed) {
                LowUsageBids.FEED_KALSHI -> kalshi
                LowUsageBids.FEED_PINNACLE -> pinnacle
                LowUsageBids.FEED_PROPLINE -> propLineProps
                else -> if (background) parlayPropsBackground else parlayProps
            },
        )
    }

    /** The feeds low-usage bids would ask, and the picked books no feed with a key and a switch on can read (Diagnostics and the health checks say so). */
    fun lowUsagePlan(settings: ScanSettings): LowUsageBids.Plan {
        val available = buildSet {
            if (settings.useKalshi) add(LowUsageBids.FEED_KALSHI)
            if (settings.usePinnacle && (keyStore.current(ApiProvider.PINNWIRE).isNotEmpty() || keyStore.current(ApiProvider.PINNAPI).isNotEmpty())) add(LowUsageBids.FEED_PINNACLE)
            if (settings.usePropLine && keyStore.current(ApiProvider.PROPLINE).isNotEmpty()) add(LowUsageBids.FEED_PROPLINE)
            if (settings.useParlay && keyStore.current(ApiProvider.PARLAY).isNotEmpty()) add(LowUsageBids.FEED_PARLAY)
        }
        return LowUsageBids.feedsFor(LowUsageBids.books(settings), available)
    }

    private fun allReferenceSources(settings: ScanSettings, background: Boolean, scan: Boolean = false): List<ReferenceSource> {
        val all = baseReferenceSources(settings, background, scan)
        // SportsGameOdds Pro (Tj, 2026-10-09): one feed prices what the paid ones sell; they rest for every league it carries (tennis keeps them), and come back if SGO stops answering.
        val sgoOn = sgoActive(settings)
        val opOn = opActive(settings)
        if (!sgoOn && !opOn) return all
        val replaced = setOfNotNull(
            pinnacle.id.takeUnless { pinnoddsActive(settings) },   // Pinnodds' Pinnacle board prices every league, SGO's included
            propLine.id, propLineProps.id, oddsApi.id, bookProps.id, parlayOdds.id, parlayOddsBackground.id, parlayProps.id, parlayPropsBackground.id, parlayHalves.id, parlayHalvesBackground.id,
        )
        // Each feed that answers rests the paid ones for the leagues it carries (tennis keeps them); both on: both rest them, and the earlier in SOURCE_ORDER (SGO) wins a book both send.
        val props = com.tjshea.vigilant.data.scanner.MarketFamily.PLAYER_PROPS in settings.families
        val rest = all.map { src ->
            var r = src
            if (src.id in replaced) {
                if (sgoOn) r = com.tjshea.vigilant.data.reference.OutsideSgo(r)
                if (opOn) r = com.tjshea.vigilant.data.reference.OutsideOp(r)
            }
            r
        }
        return (if (sgoOn) listOf<ReferenceSource>(sgoGames) + (if (props) listOf(sgoProps) else emptyList()) else emptyList()) +
            (if (opOn) listOf<ReferenceSource>(opGames) + (if (props) listOf(opProps) else emptyList()) else emptyList()) + rest
    }

    internal fun baseReferenceSources(settings: ScanSettings, background: Boolean, scan: Boolean = false): List<ReferenceSource> = buildList {
        val pinnacleOn = settings.usePinnacle && (keyStore.current(ApiProvider.PINNWIRE).isNotEmpty() || keyStore.current(ApiProvider.PINNAPI).isNotEmpty() || pinnoddsActive(settings))
        if (pinnacleOn) add(pinnacle)
        // ParlayAPI's alternate lines are Pinnacle's: bought only when PinnWire/pinnapi aren't sending them (2 credits a league saved).
        parlayOdds.alternates = !pinnacleOn
        parlayOddsBackground.alternates = !pinnacleOn
        // Baseball's first 5 innings are Pinnacle's alone at ParlayAPI: not bought while a Pinnacle feed sends them.
        parlayHalves.pinnacleFeedOn = pinnacleOn
        parlayHalvesBackground.pinnacleFeedOn = pinnacleOn
        if (settings.usePolymarket) add(polymarket)
        if (settings.useKalshi) add(kalshi)
        if (settings.usePropLine && keyStore.current(ApiProvider.PROPLINE).isNotEmpty()) {
            add(propLine)
            if (settings.useBookProps) add(propLineProps)
        }
        if (settings.useOddsApi && keyStore.current(ApiProvider.THE_ODDS_API).isNotEmpty()) {
            add(oddsApi)
            if (settings.useBookProps) add(bookProps)
        }
        if (settings.useParlay && keyStore.current(ApiProvider.PARLAY).isNotEmpty()) {
            add(if (background) parlayOddsBackground else parlayOdds)
            // The scan's own props call is credits (3 a league): not bought for a league with no pregame game inside the window (v0.70.1 diagnostics: 6 of 40 credits went to such boards).
            // What prices Tj's open bets and the sharp-book confirmations (not [scan]) reads every picked league, as it always did: a bet's game may be past the window.
            if (settings.useBookProps) add((if (background) parlayPropsBackground else parlayProps).let { if (scan) LowUsageSource(it, windowGuard = true) else it })
            // More books' 1st-half lines (2 credits a league, only where Novig lists a 1st-half spread or total; PARLAY_API.md §6.7).
            add(if (background) parlayHalvesBackground else parlayHalves)
        }
    }
}
