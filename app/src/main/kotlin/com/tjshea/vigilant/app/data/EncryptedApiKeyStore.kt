package com.tjshea.vigilant.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tjshea.vigilant.data.keys.ApiKeyStore
import com.tjshea.vigilant.data.keys.ApiProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal val Context.apiKeyDataStore by preferencesDataStore(name = "vigilant_api_keys")

/**
 * [ApiKeyStore] on Android: DataStore for storage, [KeyCipher] (Android Keystore AES/GCM) so no
 * key is ever written to disk in the clear. Order is preserved: it's the order keys are tried in.
 */
class EncryptedApiKeyStore(
    private val context: Context,
    private val json: Json = Json,
) : ApiKeyStore {

    override suspend fun getKeys(provider: ApiProvider): List<String> {
        val raw = context.apiKeyDataStore.data.first()[stringPreferencesKey(provider.storageKey)] ?: return emptyList()
        val encrypted = runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrDefault(emptyList())
        return encrypted.mapNotNull { runCatching { KeyCipher.decrypt(it) }.getOrNull() }
    }

    override suspend fun setKeys(provider: ApiProvider, keys: List<String>) {
        val encrypted = keys.map { KeyCipher.encrypt(it) }
        context.apiKeyDataStore.edit { prefs ->
            prefs[stringPreferencesKey(provider.storageKey)] = json.encodeToString(ListSerializer(String.serializer()), encrypted)
        }
    }
}
