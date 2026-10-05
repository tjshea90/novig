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
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.stream.NovigStream
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.PropLinePropsSource
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.OddsApiPropsSource
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.book.SportsbookScanner
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
        /** How often the flight recorder is written to its files. */
        const val FLUSH_EVERY_MS = 30_000L

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
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    /** The free score feeds (ESPN, MLB): one instance, so its per-day and box-score caches serve the Tracker's grading and the scan study's alike. */
    val scores = FreeScores(http, json)
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
    val closeBackfill = com.tjshea.vigilant.data.tracker.CloseBackfill(tracker, listOf(parlayCloses, espnCloses, novigCloses))

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
        com.tjshea.vigilant.data.study.GuardedCloses(parlayCloses) { parlayCreditsPlentiful() }, espnCloses, novigCloses,
    )

    /**
     * Grades the study's bets whose games have started and finds their closes, beside the Tracker's own (the 3-hourly worker, a little after the process
     * starts): a bet Tj placed himself takes its result and close from his Tracker bet, the rest go through the same grader and close sources. Never throws.
     */
    suspend fun settleStudy() {
        try {
            parlayCloses.enabled = currentSettings().useParlay
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
    @Volatile private var makerDeskCache: Pair<NovigTradingClient, com.tjshea.vigilant.data.novig.trading.maker.MakerDesk>? = null

    /** The make-orders desk on the Vigilant wallet, sharing the one order lock; null when betting through the API isn't set up. */
    fun makerDesk(): com.tjshea.vigilant.data.novig.trading.maker.MakerDesk? {
        val t = trading ?: return null
        makerDeskCache?.takeIf { it.first === t }?.let { return it.second }
        return com.tjshea.vigilant.data.novig.trading.maker.MakerDesk(t, tracker, makerStore, lock = orderLock).also { makerDeskCache = t to it }
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
            runner.state.distinctUntilChanged { a, b -> a.finished == b.finished }.collect { run -> run.report?.let { recorder.scanFinished(it) } }
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
                runCatching { maker.run("after a scan") }.onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e }
            }
        }
        // And while a scan runs (Tj, 2026-10-03: "it didn't actually make any bids by itself"): each league's lines are bid on once its fair odds are
        // in, not minutes later at the scan's end. The newest partial result at most every [MAKER_SCAN_PASS_MS] (a pass reads Novig's open orders).
        appScope.launch {
            runner.state.filter { it.scanning && it.result?.partial == true }.map { it.result }.distinctUntilChanged { a, b -> a === b }.conflate().collect {
                val s = currentSettings()
                if (!s.paused && AppBook.isNovig && (s.maker || s.makerRecommend)) {
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
        appScope.launch {
            // Paused (the Pause button, or the wallet ran out): every bid down. Auto-make switched off: the bids it posted down; the ones Tj approved stay.
            settingsStore.flow.filterNotNull().map { it.paused to it.maker }.distinctUntilChanged().collect { (paused, on) ->
                runCatching {
                    when {
                        paused -> maker.cancelAll("Scanning is paused")
                        !on -> maker.cancelAuto("Auto-make switched off")
                        else -> null
                    }
                }.onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; runCatching { problems.add("Make orders", e.message ?: e.javaClass.simpleName) } }
            }
        }
        // A crash saved as the last process went down ([AppExits.install]): into Recent problems at the time it happened.
        appScope.launch(Dispatchers.IO) {
            AppExits.takeSavedCrash(app)?.let { (at, text) -> runCatching { problems.add("App crash", text, atMs = at, maxLength = problems.crashLength) } }
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
    fun startVigilantScan(settings: ScanSettings, bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>, background: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val pinned = bets.filter { it.status == com.tjshea.vigilant.data.tracker.BetStatus.PENDING && it.startsTs > now }.mapTo(HashSet()) { it.marketId }
        val before = usage.flow.value
        // ParlayAPI's own figure for what's left, read for free (at most every few minutes): the pace decides from the key's word.
        if (settings.useParlay && keyStore.current(ApiProvider.PARLAY).isNotEmpty()) appScope.launch { runCatching { parlayAccount.refresh() } }
        return runner.start(settings, referenceSources(settings, background), pinned) { report ->
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
        val signer = connection?.let(::readKeyClient)
        novig.stream?.close()
        novig.keyed = signer
        novig.stream = signer?.let { NovigStream(http, it, appScope) }
        val alias = connection?.tradingAlias
        val key = connection?.tradingKeyId
        val client = if (alias != null && key != null && alias in KeystoreVault.aliases(NovigBettingSetup.TRADING_PREFIX)) {
            NovigTradingClient(NovigSignedClient(http, json, KeystoreSigningKey(alias, key)), json)
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

    private val polymarket = PolymarketClient(http, json, usage = usage)
    private val kalshi = KalshiClient(http, json, usage = usage)
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

    /** Players' injury reports (Tj, 2026-09-30, PARLAY_API.md §6.1): free from every ParlayAPI props answer, and its /injuries list. */
    val injuries = com.tjshea.vigilant.data.reference.InjuryIndex()

    /** A whole league's player props in one 3-credit call (RESEARCH.md §43). */
    private val parlayProps = com.tjshea.vigilant.data.reference.ParlayPropsSource(parlayOdds, injuries)

    /** Pinnacle's biggest moneyline moves per league (ParlayAPI's public /v1/meta/movers: free, no key; PARLAY_API.md §6.3). */
    val parlayMovers = com.tjshea.vigilant.data.reference.ParlayMovers(http, json)

    /** ESPN's injury list through ParlayAPI (1 credit a league, 10 min apart) for listed or open prop bets no props answer covered. */
    val parlayInjuries = com.tjshea.vigilant.data.reference.ParlayInjuries(parlayOdds, injuries, json, active = { parlayActive() })

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
    )

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
        com.tjshea.vigilant.data.reference.OtherBooks.Sources(
            parlay = s.vigilantOn && parlayActive(),
            propLine = s.vigilantOn && s.usePropLine && keyStore.current(ApiProvider.PROPLINE).isNotEmpty(),
            oddsApi = s.vigilantOn && s.useOddsApi && keyStore.current(ApiProvider.THE_ODDS_API).isNotEmpty(),
        )
    })

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
    fun referenceSources(settings: ScanSettings, background: Boolean = false): List<ReferenceSource> = buildList {
        val pinnacleOn = settings.usePinnacle && (keyStore.current(ApiProvider.PINNWIRE).isNotEmpty() || keyStore.current(ApiProvider.PINNAPI).isNotEmpty())
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
            if (settings.useBookProps) add(if (background) parlayPropsBackground else parlayProps)
            // More books' 1st-half lines (2 credits a league, only where Novig lists a 1st-half spread or total; PARLAY_API.md §6.7).
            add(if (background) parlayHalvesBackground else parlayHalves)
        }
    }
}
