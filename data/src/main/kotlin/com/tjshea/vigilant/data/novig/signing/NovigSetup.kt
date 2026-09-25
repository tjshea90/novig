package com.tjshea.vigilant.data.novig.signing

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** Where new keypairs come from. On the phone: the Android Keystore. In tests: memory. */
interface KeyVault {
    /** Makes a fresh P-256 keypair under [alias]; returns the public half as SPKI PEM. */
    fun generate(alias: String): String
    fun signer(alias: String, keyId: String): NovigSigningKey
    fun delete(alias: String)
}

data class NovigConnection(
    /** The `trading::read` key the app streams with. Can't trade, can't move money. */
    val readKeyId: String,
    val readAlias: String,
    /** The Vigilant subaccount's trading key ID (its address). */
    val subaccountKeyId: String,
    val createdSubaccount: Boolean,
)

/**
 * One-time setup from Tj's management key (NOVIG_API.md §2):
 *
 *  1. `POST /v3/echo` with the management key: proves ID, file, clock and network (no VPN).
 *  2. Find a subaccount labeled [LABEL], or open one. Opening registers a trading key the phone
 *     generates and keeps; the subaccount is never funded by this app.
 *  3. Mint a `trading::read` key on it from a second phone-generated keypair.
 *  4. `POST /v3/echo` with the new read key: proves it works before anything is saved.
 *
 * The management key is only ever held in memory for these few calls. The caller must not
 * persist it; only [NovigConnection] (key IDs and Keystore aliases, no secrets) is stored.
 */
class NovigSetup(
    private val http: OkHttpClient,
    private val json: Json,
    private val vault: KeyVault,
    private val baseUrl: String = "https://api.novig.com",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun connect(managementKeyId: String, managementPem: String, onStep: (String) -> Unit = {}): NovigConnection {
        val mgmt = PemSigningKey(managementKeyId.trim(), managementPem)
        val admin = NovigSignedClient(http, json, mgmt, baseUrl, clock)

        onStep("Checking your key with Novig…")
        admin.echo()

        onStep("Finding the Vigilant subaccount…")
        val existing = admin.listSubaccounts().firstOrNull { it.label == LABEL }
        val stamp = clock()
        val subaccountKeyId: String
        if (existing != null) {
            subaccountKeyId = existing.keyId
        } else {
            onStep("Opening a Vigilant subaccount (no money is moved)…")
            val tradingAlias = "vigilant_novig_trading_$stamp"
            val pub = vault.generate(tradingAlias)
            subaccountKeyId = runCatching { admin.openSubaccount(LABEL, pub, NovigKeyAlgorithm.P256).keyId }
                .onFailure { vault.delete(tradingAlias) }
                .getOrThrow()
        }

        onStep("Creating a read-only scanning key…")
        val readAlias = "vigilant_novig_read_$stamp"
        val readPub = vault.generate(readAlias)
        val readKeyId = try {
            admin.createSubaccountKey(subaccountKeyId, "Vigilant scanner", readPub, NovigKeyAlgorithm.P256, "trading::read").keyId
        } catch (e: Exception) {
            vault.delete(readAlias)
            throw e
        }

        onStep("Testing the new key…")
        NovigSignedClient(http, json, vault.signer(readAlias, readKeyId), baseUrl, clock).echo()

        return NovigConnection(readKeyId, readAlias, subaccountKeyId, createdSubaccount = existing == null)
    }

    companion object {
        const val LABEL = "Vigilant"
    }
}
