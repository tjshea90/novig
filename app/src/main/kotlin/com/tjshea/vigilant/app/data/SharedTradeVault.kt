package com.tjshea.vigilant.app.data

import com.tjshea.vigilant.data.cloud.CloudNovigKeyStore
import com.tjshea.vigilant.data.cloud.EngineNovigKey
import com.tjshea.vigilant.data.novig.signing.KeyVault
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import kotlinx.coroutines.runBlocking

/**
 * The Keystore vault plus ONE software trading key that the GitHub engine shares (Tj, 2026-10-15, TASKS.md TCL20c). Novig allows one live `trading` key per subaccount, so once Tj has sent the trading key to
 * GitHub ([com.tjshea.vigilant.data.cloud.CloudTradeKey.swap]) the phone signs with that same key, sealed by the Keystore ([CloudNovigKeyStore]) and offered here as the alias [SHARED]. Everything else is the
 * Keystore's: [com.tjshea.vigilant.data.novig.signing.NovigBettingSetup.enable] finds [SHARED] among the aliases, proves it with an echo and so never revokes it.
 */
class SharedTradeVault(private val store: CloudNovigKeyStore, private val inner: KeyVault = KeystoreVault) : KeyVault {
    @Volatile private var cached: EngineNovigKey? = null
    @Volatile private var loaded = false

    /** The shared key, or null (none was ever made, or this phone can no longer open it). */
    fun key(): EngineNovigKey? {
        if (!loaded) { cached = runCatching { runBlocking { store.load() } }.getOrNull(); loaded = true }
        return cached
    }

    /** Called after the store changed (a key saved or removed). */
    fun reload() { loaded = false }

    override fun generate(alias: String): String = inner.generate(alias)
    override fun delete(alias: String) { if (alias != SHARED) inner.delete(alias) }

    override fun signer(alias: String, keyId: String): NovigSigningKey =
        if (alias == SHARED) PemSigningKey(keyId, key()?.pem ?: error("the shared trading key is missing")) else inner.signer(alias, keyId)

    override fun aliases(prefix: String): List<String> =
        (if (SHARED.startsWith(prefix) && key() != null) listOf(SHARED) else emptyList()) + inner.aliases(prefix)

    companion object {
        /** Starts with [com.tjshea.vigilant.data.novig.signing.NovigBettingSetup.TRADING_PREFIX], so every trading-key lookup sees it. */
        const val SHARED = "vigilant_novig_trading_shared"
    }
}
