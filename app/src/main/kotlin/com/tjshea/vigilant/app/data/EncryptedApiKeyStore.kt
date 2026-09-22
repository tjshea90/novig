package com.tjshea.vigilant.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tjshea.vigilant.data.keys.ApiKeyStore
import com.tjshea.vigilant.data.keys.ApiProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.apiKeyDataStore by preferencesDataStore(name = "vigilant_api_keys")

private val NOVIG_DIRECT_MODE_KEY = booleanPreferencesKey("novig_direct_mode_enabled")

/**
 * The real, Android-side implementation of [ApiKeyStore] — DataStore Preferences for storage,
 * [KeyCipher] (Android Keystore-backed AES/GCM) for encryption before anything touches disk.
 * Order is preserved (a JSON array, not a Set) since [com.tjshea.vigilant.data.keys.KeyRotator]
 * tries keys in the order Tj added them.
 */
class EncryptedApiKeyStore(
    private val context: Context,
    private val json: Json = Json,
) : ApiKeyStore {

    override suspend fun getKeys(provider: ApiProvider): List<String> {
        val prefKey = stringPreferencesKey(provider.storageKey)
        val raw = context.apiKeyDataStore.data.first()[prefKey] ?: return emptyList()
        val encrypted = runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(emptyList())
        return encrypted.mapNotNull { runCatching { KeyCipher.decrypt(it) }.getOrNull() }
    }

    override suspend fun setKeys(provider: ApiProvider, keys: List<String>) {
        val prefKey = stringPreferencesKey(provider.storageKey)
        val encrypted = keys.map { KeyCipher.encrypt(it) }
        context.apiKeyDataStore.edit { prefs ->
            prefs[prefKey] = json.encodeToString(ListSerializer(String.serializer()), encrypted)
        }
    }
}
