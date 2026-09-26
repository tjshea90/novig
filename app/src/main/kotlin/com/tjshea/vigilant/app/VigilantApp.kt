package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.app.data.KeystoreSigningKey
import com.tjshea.vigilant.app.data.NovigConnectionStore
import com.tjshea.vigilant.data.cno.CnoCache
import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFeed
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
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.OddsApiPropsSource
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.ScanRunner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class VigilantApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * One of everything, for the life of the process. A single [OkHttpClient] shares one connection
 * pool across Novig and every odds provider, so a scan reuses warm HTTP/2 connections instead of
 * paying a TLS handshake per request (a real battery cost on a phone radio). Nothing here starts
 * any network on its own: only a scan the user asks for does, and CrazyNinjaOdds' list while
 * Vigilant is on screen ([cno]).
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
    val cno = CnoFeed(CnoClient(http), JsonFileStore(File(app.filesDir, "cno.json"), CnoCache.serializer(), { CnoCache() }, json))

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
    private val pinnacle = PinnapiClient(http, json, KeyPool(QuotaPolicy.PINNAPI, { keyStore.current(ApiProvider.PINNAPI) }, usage))

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
        if (settings.usePinnacle && keyStore.current(ApiProvider.PINNAPI).isNotEmpty()) add(pinnacle)
        if (settings.usePolymarket) add(polymarket)
        if (settings.useKalshi) add(kalshi)
        if (settings.useOddsApi && keyStore.current(ApiProvider.THE_ODDS_API).isNotEmpty()) {
            add(oddsApi)
            if (settings.useBookProps) add(bookProps)
        }
    }
}
