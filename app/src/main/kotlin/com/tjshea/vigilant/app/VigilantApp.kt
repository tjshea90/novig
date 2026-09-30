package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.app.data.KeystoreSecretBox
import com.tjshea.vigilant.app.data.KeystoreSigningKey
import com.tjshea.vigilant.app.data.KeystoreVault
import com.tjshea.vigilant.app.data.NovigConnectionStore
import com.tjshea.vigilant.data.novig.signing.ManagementKeyStore
import com.tjshea.vigilant.data.novig.signing.NovigBettingSetup
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.tjshea.vigilant.data.tracker.ClosingLine
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

class VigilantApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // A new process has no scan results (they live in memory): a "scan done" notification left
        // from before Android closed Vigilant names bets the app can no longer show (Tj, 2026-09-27:
        // "it says the best bet is Milwaukee, but this bet isn't even shown in the widget").
        ScanService.cancelDone(this)
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
        /** CNO game pages read at once in "Check odds now" (the client's bulk pace keeps them to two requests a second). */
        const val RECHECK_AT_ONCE = 3

        /** Excluded from backups (res/xml): sealed by this phone's Keystore, it couldn't be opened anywhere else. */
        const val MANAGEMENT_KEY_FILE = "novig_management_key.json"
    }

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val http: OkHttpClient = vigilantHttpClient()

    /** Tj's keys as plain JSON in app storage: survives updates and restores from backup. */
    val keyStore = FileApiKeyStore(File(app.filesDir, "api_keys.json"), json)

    /** Where keys lived until v0.6.0 (Keystore-encrypted). Read once, to move them over. */
    private val legacyKeyStore = EncryptedApiKeyStore(app)

    /** Every call's usage, per provider and key, behind the meters and the key rotation. */
    val usage = UsageMeter(JsonFileStore(File(app.filesDir, "usage.json"), UsageBook.serializer(), { UsageBook() }, json))

    val settingsStore = JsonFileStore(File(app.filesDir, "settings.json"), ScanSettings.serializer(), { ScanSettings() }, json)
    val tracker = BetTracker(File(app.filesDir, "bets.json"), ownBook = AppBook.name)

    /** When the last Tracker "Check odds now" began: its +EV / −EV counter counts the bets re-read since ([CheckOddsStats]). */
    val lastCheck = JsonFileStore(File(app.filesDir, "last_check.json"), com.tjshea.vigilant.data.tracker.LastCheck.serializer(), { com.tjshea.vigilant.data.tracker.LastCheck() }, json)
    val novig = NovigPublicClient(http, json, usage = usage)
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
    val runner = ScanRunner(scanner, appScope)

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
        CnoClient(CnoNetwork.client(http)),
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
        apiSettler = ApiSettler(tracker, client, subaccountKeyId, scoreGrade = { bet -> settler.scoreGradeOf(bet) })
        apiSync = ApiBetSync(tracker, client, novig)
    }

    val settler = BetSettler(tracker, FreeScores(http, json), leaveApiBets = { trading != null })

    /**
     * Closes found after the start (Tj, 2026-09-30: "My phone will not always be on"): ESPN's closing game lines, then Novig's trade history,
     * for every started bet the capture before the start didn't read. Runs with the grading: app open, Grade now, Check odds now, and the
     * 3-hourly background worker ([SettleWorker]).
     */
    val espnCloses = com.tjshea.vigilant.data.tracker.EspnCloses(http, json)
    val novigCloses = com.tjshea.vigilant.data.tracker.NovigTradeCloses(http, json)
    val closeBackfill = com.tjshea.vigilant.data.tracker.CloseBackfill(tracker, listOf(espnCloses, novigCloses))

    /** The last back-fill's report (this process), for Diagnostics. */
    @Volatile var lastBackfill: com.tjshea.vigilant.data.tracker.CloseBackfill.Report? = null

    /**
     * Runs the back-fill, never throwing (a feed down is looked at again next time). Novig's trade files (a few MB a kickoff) are read on any
     * network: Tj, 2026-09-30, "My mobile data is fast and unlimited and my phone storage is large. Choose accuracy and speed over mobile data
     * or phone storage always."
     */
    suspend fun backfillCloses() {
        try {
            lastBackfill = closeBackfill.run(heavyOk = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Looked at again on the next pass.
        }
    }

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
    )

    /**
     * The Tracker's "Check odds now" for Vigilant's own bets, and any bet CNO couldn't read (Tj, 2026-09-29: "update the EV for every single
     * open bet, including bets added from vigilant scanner"): a bets-only [Scanner] of its own (never the feed's catalog, books or fair-odds
     * snapshots) prices exactly those bets' games from the same fair-odds sources and rules as the feed. Null for Vigilant MGM.
     */
    val betPricer: OpenBetPricer? = if (AppBook.isNovig) OpenBetPricer(tracker, Scanner(novig, betsOnly = true), ::referenceSources) else null

    /** Novig's price now for CNO's listed bets, from Novig's order books (only while CNO's list is on screen). */
    val live = NovigLive(novig, { row -> betFinder.find(row) })

    /** Bets Tj marked placed in the widget or the CNO tab: hidden from both until their game is over. */
    val placed = PlacedBets(JsonFileStore(File(app.filesDir, "placed.json"), PlacedBook.serializer(), { PlacedBook() }, json))

    /** Player teams for CNO's player bets ("D. Schultz (HOU)"), from ESPN; read only alongside [cno]. */
    val teams = PlayerTeams(http, json, JsonFileStore(File(app.filesDir, "teams.json"), TeamsCache.serializer(), { TeamsCache() }, json))

    /** Whether Vigilant is on screen: a finished scan only notifies when it isn't. */
    @Volatile
    var onScreen = false

    /** Bets a push alert went out for (alerts.json), so each bet alerts once (Tj, 2026-09-28). */
    val alertLog = AlertLog(JsonFileStore(File(app.filesDir, "alerts.json"), AlertBook.serializer(), { AlertBook() }, json))

    /** The background auto-scan and its +EV alerts (Tj, 2026-09-28), run by [AutoScanService]. */
    val autoScan: AutoScanner by lazy { AutoScanner(app, this) }

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
    fun startVigilantScan(settings: ScanSettings, bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>): Boolean {
        val now = System.currentTimeMillis()
        val pinned = bets.filter { it.status == com.tjshea.vigilant.data.tracker.BetStatus.PENDING && it.startsTs > now }.mapTo(HashSet()) { it.marketId }
        val before = usage.flow.value
        return runner.start(settings, referenceSources(settings), pinned) { report ->
            // A scan that ended with Vigilant off screen (background auto-scan, or Tj left) closes Novig's
            // live feed at once: nothing will recheck in the next two minutes, and pushes cost battery.
            if (!onScreen) novig.stream?.close()
            // Disk trouble (full storage) must never break a scan.
            report?.result?.let { runCatching { tracker.observe(it) } }
            // Keyed calls saved as they happened; this saves the keyless request counters.
            runCatching { usage.flush() }
            // What this scan cost each API, for Settings › Diagnostics (a scan that failed outright has no report and no cost to show).
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
        apiSettler = client?.let { ApiSettler(tracker, it, connection?.subaccountKeyId.orEmpty(), scoreGrade = { bet -> settler.scoreGradeOf(bet) }) }
        apiSync = client?.let { ApiBetSync(tracker, it, novig) }
    }

    fun readKeyClient(connection: NovigConnection, client: OkHttpClient = http) =
        NovigSignedClient(client, json, KeystoreSigningKey(connection.readAlias, connection.readKeyId))

    private val polymarket = PolymarketClient(http, json, usage = usage)
    private val kalshi = KalshiClient(http, json, usage = usage)
    private val oddsApi = TheOddsApiClient(http, KeyPool(QuotaPolicy.ODDS_API, { keyStore.current(ApiProvider.THE_ODDS_API) }, usage), json)
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
    fun referenceSources(settings: ScanSettings): List<ReferenceSource> = buildList {
        if (settings.usePinnacle && (keyStore.current(ApiProvider.PINNWIRE).isNotEmpty() || keyStore.current(ApiProvider.PINNAPI).isNotEmpty())) add(pinnacle)
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
    }
}
