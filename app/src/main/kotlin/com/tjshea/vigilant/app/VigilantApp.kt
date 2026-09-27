package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.app.data.KeystoreSigningKey
import com.tjshea.vigilant.app.data.NovigConnectionStore
import com.tjshea.vigilant.data.cno.CnoCache
import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoLinks
import com.tjshea.vigilant.data.cno.CnoNetwork
import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.cno.NovigLive
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.FileApiKeyStore
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.PropLinePropsSource
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.OddsApiPropsSource
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.ScanRunner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.teams.PlayerTeams
import com.tjshea.vigilant.data.teams.TeamsCache
import com.tjshea.vigilant.data.tracker.BetRecheck
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.PlacedBets
import com.tjshea.vigilant.data.tracker.PlacedBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

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
class AppContainer(app: Application) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Tj's keys as plain JSON in app storage: survives updates and restores from backup. */
    val keyStore = FileApiKeyStore(File(app.filesDir, "api_keys.json"), json)

    /** Where keys lived until v0.6.0 (Keystore-encrypted). Read once, to move them over. */
    private val legacyKeyStore = EncryptedApiKeyStore(app)

    /** Every call's usage, per provider and key, behind the meters and the key rotation. */
    val usage = UsageMeter(JsonFileStore(File(app.filesDir, "usage.json"), UsageBook.serializer(), { UsageBook() }, json))

    val settingsStore = JsonFileStore(File(app.filesDir, "settings.json"), ScanSettings.serializer(), { ScanSettings() }, json)
    val tracker = BetTracker(File(app.filesDir, "bets.json"))
    val novig = NovigPublicClient(http, json, usage = usage)
    val novigConnection = NovigConnectionStore(app)
    val scanner = Scanner(novig)

    /**
     * Lives as long as the process, not a screen: a scan Tj starts keeps going when he switches
     * apps or backs out of Vigilant. [ScanService] keeps the process alive while one runs.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val runner = ScanRunner(scanner, appScope)

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
        catalog = { row -> (betFinder.find(row) as? NovigBetFinder.Found.Bet)?.link },
    )

    /**
     * Written once the ✓ marks from before the Tracker kept them were moved into it: the move runs
     * once, so a bet deleted in the Tracker doesn't come back from its old mark.
     */
    val trackerImported = File(app.filesDir, "tracker_imported")

    /**
     * Settles tracked bets from Novig's catalog (WIN / LOSS / PUSH / fair value): on app open, on
     * the Tracker tab, and every 3 h in the background ([SettleWorker]). Reads only for open bets
     * whose game started over an hour ago.
     */
    val settler = BetSettler(
        tracker,
        market = { id -> novig.market(id) },
        resolve = { b ->
            val row = com.tjshea.vigilant.data.cno.CnoRow(
                ev = 0.0, startsAtMs = b.startsTs, league = b.league, event = b.eventName, market = b.marketLabel,
                bet = b.selection, odds = b.american ?: 100, book = b.book,
            )
            betFinder.findEnded(row)?.let { f -> f.marketId?.let { it to f.outcomeId } }
        },
    )

    /**
     * The Tracker's "Check odds now": each open CNO bet's game page re-read through [cno] (its pace;
     * nothing while CNO asked for a pause), judged against what the bet cost.
     */
    val recheck = BetRecheck(tracker, books = { row ->
        if ((cno.state.value.pausedUntilMs ?: 0L) > System.currentTimeMillis()) {
            null
        } else {
            cno.loadBooks(row, force = true)
            cno.books.value[row.key]?.takeIf { it.error == null }?.view
        }
    })

    /** Novig's price now for CNO's listed bets, from Novig's order books (only while CNO's list is on screen). */
    val live = NovigLive(novig, { row -> betFinder.find(row) })

    /** Bets Tj marked placed in the widget or the CNO tab: hidden from both until their game is over. */
    val placed = PlacedBets(JsonFileStore(File(app.filesDir, "placed.json"), PlacedBook.serializer(), { PlacedBook() }, json))

    /** Player teams for CNO's player bets ("D. Schultz (HOU)"), from ESPN; read only alongside [cno]. */
    val teams = PlayerTeams(http, json, JsonFileStore(File(app.filesDir, "teams.json"), TeamsCache.serializer(), { TeamsCache() }, json))

    /** Whether Vigilant is on screen: a finished scan only notifies when it isn't. */
    @Volatile
    var onScreen = false

    /** Book reads go through the connected key's own rate limit (or the public routes when null). */
    @Synchronized
    fun useConnection(connection: NovigConnection?) {
        novig.keyed = connection?.let(::readKeyClient)
    }

    fun readKeyClient(connection: NovigConnection) =
        NovigSignedClient(http, json, KeystoreSigningKey(connection.readAlias, connection.readKeyId))

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
    private val propLine = PropLineClient(http, KeyPool(QuotaPolicy.PROPLINE, { keyStore.current(ApiProvider.PROPLINE) }, usage), json)
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
