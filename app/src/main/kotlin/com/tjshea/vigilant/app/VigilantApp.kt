package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.app.data.KeystoreSigningKey
import com.tjshea.vigilant.app.data.NovigConnectionStore
import com.tjshea.vigilant.data.keys.ApiKeyStore
import com.tjshea.vigilant.data.keys.KeyRotator
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetTracker
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
 * any network on its own: only a scan the user asks for does.
 */
class AppContainer(app: Application) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val keyStore: ApiKeyStore = EncryptedApiKeyStore(app)
    val settingsStore = JsonFileStore(File(app.filesDir, "settings.json"), ScanSettings.serializer(), { ScanSettings() }, json)
    val tracker = BetTracker(File(app.filesDir, "bets.json"))
    val novig = NovigPublicClient(http, json)
    val novigConnection = NovigConnectionStore(app)
    val scanner = Scanner(novig)

    /** Book reads go through the connected key's own rate limit (or the public routes when null). */
    @Synchronized
    fun useConnection(connection: NovigConnection?) {
        novig.keyed = connection?.let(::readKeyClient)
    }

    fun readKeyClient(connection: NovigConnection) =
        NovigSignedClient(http, json, KeystoreSigningKey(connection.readAlias, connection.readKeyId))

    private val polymarket = PolymarketClient(http, json)
    private val kalshi = KalshiClient(http, json)

    private var oddsKeys: List<String>? = null
    private var oddsApi: TheOddsApiClient? = null
    private var pinnKey: String? = null
    private var pinnacle: PinnapiClient? = null

    /**
     * The fair-odds providers a scan should call, for the switches in Settings and the keys on
     * this phone. Keyed clients live between scans (so [KeyRotator] remembers used-up keys and
     * pinnapi remembers a 429) and are rebuilt only when the keys change.
     */
    @Synchronized
    fun referenceSources(settings: ScanSettings, oddsApiKeys: List<String>, pinnapiKeys: List<String>): List<ReferenceSource> {
        if (oddsApiKeys != oddsKeys) {
            oddsKeys = oddsApiKeys
            oddsApi = oddsApiKeys.takeIf { it.isNotEmpty() }?.let { TheOddsApiClient(http, KeyRotator(it), json) }
        }
        val pk = pinnapiKeys.firstOrNull()
        if (pk != pinnKey) {
            pinnKey = pk
            pinnacle = pk?.let { PinnapiClient(http, json, it) }
        }
        return buildList {
            if (settings.usePinnacle) pinnacle?.let(::add)
            if (settings.usePolymarket) add(polymarket)
            if (settings.useKalshi) add(kalshi)
            if (settings.useOddsApi) oddsApi?.let(::add)
        }
    }
}
