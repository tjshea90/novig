package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.data.keys.ApiKeyStore
import com.tjshea.vigilant.data.keys.KeyRotator
import com.tjshea.vigilant.data.novig.NovigPublicClient
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
 * pool across Novig and The Odds API, so each refresh reuses warm HTTP/2 connections instead of
 * paying a TLS handshake per request (a real battery cost on a phone radio).
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
    val scanner = Scanner(novig)

    private var referenceKeys: List<String>? = null
    private var reference: ReferenceSource? = null

    /**
     * The Odds API client for the current key list. Kept alive between refreshes so
     * [KeyRotator] remembers which keys are used up; rebuilt only when the keys change.
     */
    @Synchronized
    fun referenceFor(keys: List<String>): ReferenceSource? {
        if (keys.isEmpty()) return null
        if (keys != referenceKeys) {
            referenceKeys = keys
            reference = TheOddsApiClient(http, KeyRotator(keys), json)
        }
        return reference
    }
}
