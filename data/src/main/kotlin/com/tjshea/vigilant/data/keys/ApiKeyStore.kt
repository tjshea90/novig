package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Providers whose keys Tj types into Settings. Each can hold several keys, tried in order
 * ([KeyPool]). Novig's own key is a one-time signed setup, not a list (NOVIG_API.md §11.1).
 */
enum class ApiProvider(val storageKey: String, val displayName: String) {
    THE_ODDS_API("the_odds_api", "The Odds API"),

    /** pinnapi.com's Pinnacle feed. The free trial key allows 100 requests a day. */
    PINNAPI("pinnapi", "Pinnacle (pinnapi)"),
}

/** Tj's own API keys per provider, in the order [KeyPool] tries them. */
interface ApiKeyStore {
    suspend fun getKeys(provider: ApiProvider): List<String>
    suspend fun setKeys(provider: ApiProvider, keys: List<String>)
}

/** The keys file: `{"keys": {"the_odds_api": ["k1", "k2"], "pinnapi": ["k3"]}}`. */
@Serializable
data class StoredKeys(val version: Int = 1, val keys: Map<String, List<String>> = emptyMap())

/**
 * Keys as plain JSON in the app's own storage. Tj's call (2026-09-25): these are free keys, so
 * no encryption. That's what makes them durable:
 *
 *  - **App updates** never touch the app's files, so keys survive every update.
 *  - **Android backup / new phone:** the file is included in the app's backup and restores as-is.
 *    (The old Keystore-encrypted copy couldn't be decrypted on another phone, so a restore lost it.)
 *  - **Export/import** writes or reads the same JSON as a file Tj keeps anywhere, which survives
 *    even an uninstall.
 *
 * Writes are atomic ([JsonFileStore]): a crash mid-save leaves the previous keys intact.
 */
class FileApiKeyStore(file: File, private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }) : ApiKeyStore {

    private val store = JsonFileStore(file, StoredKeys.serializer(), { StoredKeys() }, json)

    /** Null until first read; then every save. [KeyPool] reads the current list from here. */
    val flow: StateFlow<StoredKeys?> get() = store.flow

    override suspend fun getKeys(provider: ApiProvider): List<String> = store.read().keys[provider.storageKey].orEmpty()

    override suspend fun setKeys(provider: ApiProvider, keys: List<String>) {
        val clean = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        store.update { it.copy(keys = it.keys + (provider.storageKey to clean)) }
    }

    suspend fun all(): StoredKeys = store.read()

    /** The current keys for [provider] without suspending (empty before the first read). */
    fun current(provider: ApiProvider): List<String> = store.flow.value?.keys?.get(provider.storageKey).orEmpty()

    /** The whole key file as JSON, for "Export keys". */
    suspend fun exportJson(): String = json.encodeToString(StoredKeys.serializer(), store.read())

    /**
     * Merges an exported file into the current keys (existing order first, new keys appended).
     * Returns how many keys were added. Throws [IllegalArgumentException] on a file that isn't one.
     */
    suspend fun importJson(text: String): Int {
        val incoming = runCatching { json.decodeFromString(StoredKeys.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("That file isn't a Vigilant key export.") }
        var added = 0
        store.update { current ->
            val merged = current.keys.toMutableMap()
            for ((provider, keys) in incoming.keys) {
                if (ApiProvider.entries.none { it.storageKey == provider }) continue
                val have = merged[provider].orEmpty()
                val fresh = keys.map { it.trim() }.filter { it.isNotEmpty() && it !in have }.distinct()
                added += fresh.size
                merged[provider] = have + fresh
            }
            current.copy(keys = merged)
        }
        return added
    }
}
