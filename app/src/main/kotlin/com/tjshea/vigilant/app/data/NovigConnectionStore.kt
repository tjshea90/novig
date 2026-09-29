package com.tjshea.vigilant.app.data

import android.content.Context
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
    override fun aliases(prefix: String): List<String> = KeystoreSigningKey.aliases(prefix)
}

/**
 * Remembers the Novig connection: key IDs and Keystore aliases only. There's nothing secret in
 * here (a key ID alone can't sign), so it's stored as plain DataStore preferences.
 */
class NovigConnectionStore(private val context: Context) {
    private val readKeyId = stringPreferencesKey("novig_read_key_id")
    private val readAlias = stringPreferencesKey("novig_read_alias")
    private val subaccount = stringPreferencesKey("novig_subaccount_key_id")
    private val tradingKeyId = stringPreferencesKey("novig_trading_key_id")
    private val tradingAlias = stringPreferencesKey("novig_trading_alias")

    suspend fun load(): NovigConnection? {
        val p = context.apiKeyDataStore.data.first()
        val id = p[readKeyId] ?: return null
        val alias = p[readAlias] ?: return null
        return NovigConnection(id, alias, p[subaccount] ?: "", createdSubaccount = false, tradingKeyId = p[tradingKeyId], tradingAlias = p[tradingAlias])
    }

    suspend fun save(c: NovigConnection) {
        context.apiKeyDataStore.edit {
            it[readKeyId] = c.readKeyId
            it[readAlias] = c.readAlias
            it[subaccount] = c.subaccountKeyId
            c.tradingKeyId?.let { id -> it[tradingKeyId] = id } ?: it.remove(tradingKeyId)
            c.tradingAlias?.let { a -> it[tradingAlias] = a } ?: it.remove(tradingAlias)
        }
    }

    suspend fun clear() {
        context.apiKeyDataStore.edit {
            it.remove(readKeyId)
            it.remove(readAlias)
            it.remove(subaccount)
            it.remove(tradingKeyId)
            it.remove(tradingAlias)
        }
    }
}
