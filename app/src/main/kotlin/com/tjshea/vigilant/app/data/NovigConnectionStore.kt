package com.tjshea.vigilant.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tjshea.vigilant.data.novig.signing.KeyVault
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import kotlinx.coroutines.flow.first

/** [KeyVault] backed by the Android Keystore: private keys are generated there and never leave. */
object KeystoreVault : KeyVault {
    override fun generate(alias: String): String = KeystoreSigningKey.generate(alias)
    override fun signer(alias: String, keyId: String): NovigSigningKey = KeystoreSigningKey(alias, keyId)
    override fun delete(alias: String) = KeystoreSigningKey.delete(alias)
}

/**
 * Remembers the Novig connection: key IDs and Keystore aliases only. There's nothing secret in
 * here (a key ID alone can't sign), so it's stored as plain DataStore preferences.
 */
class NovigConnectionStore(private val context: Context) {
    private val readKeyId = stringPreferencesKey("novig_read_key_id")
    private val readAlias = stringPreferencesKey("novig_read_alias")
    private val subaccount = stringPreferencesKey("novig_subaccount_key_id")
    private val streamOn = booleanPreferencesKey("novig_stream_enabled")

    suspend fun load(): NovigConnection? {
        val p = context.apiKeyDataStore.data.first()
        val id = p[readKeyId] ?: return null
        val alias = p[readAlias] ?: return null
        return NovigConnection(id, alias, p[subaccount] ?: "", createdSubaccount = false)
    }

    suspend fun streamEnabled(): Boolean = context.apiKeyDataStore.data.first()[streamOn] ?: true

    suspend fun setStreamEnabled(on: Boolean) {
        context.apiKeyDataStore.edit { it[streamOn] = on }
    }

    suspend fun save(c: NovigConnection) {
        context.apiKeyDataStore.edit {
            it[readKeyId] = c.readKeyId
            it[readAlias] = c.readAlias
            it[subaccount] = c.subaccountKeyId
        }
    }

    suspend fun clear() {
        context.apiKeyDataStore.edit {
            it.remove(readKeyId)
            it.remove(readAlias)
            it.remove(subaccount)
        }
    }
}
