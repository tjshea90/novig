package com.tjshea.vigilant.data.novig.signing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.UUID

/** How a fund or withdraw ended. [applied]: the money moved. [balance]: the subaccount's balance after, when Novig said. */
data class TransferOutcome(val applied: Boolean, val message: String, val balance: Double? = null)

/**
 * The management-key half of betting through Novig's API (Tj, 2026-09-29: "Build the betting through the API function"): making sure this phone
 * holds the Vigilant subaccount's `trading` key, and moving money between Tj's cash wallet and that subaccount. This class holds the
 * management key in memory for the calls here only; the app keeps it sealed on the phone ([ManagementKeyStore], Tj 2026-09-29: "I only
 * input the API key and file one time").
 *
 * Novig lets a subaccount have ONE live `trading` key, and mints a second only after the first is revoked (docs.novig.com/api/api-keys):
 *  1. If this phone's Keystore holds a key that signs as the subaccount's live trading key, use it (Tj's setup created one).
 *  2. Otherwise the live one can't be used from here: revoke it and mint a replacement from a new phone-generated keypair. Revocation
 *     takes up to a minute to take effect, so the mint is retried (a 409 means the old key is still live). The subaccount holds no money
 *     of Vigilant's until Tj funds it, and its address doesn't change.
 */
class NovigBettingSetup(
    private val http: OkHttpClient,
    private val json: Json,
    private val vault: KeyVault,
    private val baseUrl: String = "https://api.novig.com",
    private val clock: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    /** Checks Novig accepts [key] (one signed echo) before it's saved, so a mistyped ID or the wrong file is never kept. */
    suspend fun check(key: ManagementKey) {
        NovigSignedClient(http, json, PemSigningKey(key.keyId, key.pem), baseUrl, clock).echo()
    }

    /** Makes sure the phone can sign as the subaccount's trading key; returns [conn] with it set. */
    suspend fun enable(conn: NovigConnection, managementKeyId: String, managementPem: String, onStep: (String) -> Unit = {}): NovigConnection {
        val admin = NovigSignedClient(http, json, PemSigningKey(managementKeyId.trim(), managementPem), baseUrl, clock)
        onStep("Checking your management key with Novig…")
        admin.echo()

        onStep("Finding the Vigilant subaccount…")
        val rows = admin.listSubaccounts()
        val row = rows.firstOrNull { it.keyId == conn.subaccountKeyId } ?: rows.firstOrNull { it.label == NovigSetup.LABEL }
            ?: throw IllegalStateException("The Vigilant subaccount isn't on this Novig account any more: connect the key again in Settings.")
        val address = conn.subaccountKeyId.ifBlank { row.keyId }
        val live = row.keyId

        // 1. A trading key this phone already holds and Novig accepts.
        val candidates = (listOfNotNull(conn.tradingAlias) + vault.aliases(TRADING_PREFIX)).distinct()
        for (alias in candidates) {
            onStep("Trying the trading key already on this phone…")
            val works = try {
                NovigSignedClient(http, json, vault.signer(alias, live), baseUrl, clock).echo()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (works) return conn.copy(subaccountKeyId = address, tradingKeyId = live, tradingAlias = alias)
        }

        // 2. Revoke the live one and mint a replacement this phone holds.
        onStep("This phone doesn't hold the subaccount's trading key: replacing it (the old one is revoked)…")
        admin.call("DELETE", "/v3/keys/$live")
        val alias = "$TRADING_PREFIX${clock()}"
        val pub = vault.generate(alias)
        var minted: String? = null
        try {
            val deadline = clock() + MINT_WAIT_MS
            while (minted == null) {
                try {
                    minted = admin.createSubaccountKey(address, "Vigilant trading", pub, NovigKeyAlgorithm.P256, "trading").keyId
                } catch (e: NovigApiException) {
                    // 409: the revoked key still counts as live for up to a minute.
                    if (e.status != 409 || clock() >= deadline) throw e
                    onStep("Waiting for Novig to finish revoking the old key…")
                    pause(MINT_RETRY_MS)
                }
            }
            onStep("Testing the new trading key…")
            NovigSignedClient(http, json, vault.signer(alias, minted), baseUrl, clock).echo()
        } catch (e: Exception) {
            vault.delete(alias)
            throw e
        }
        return conn.copy(subaccountKeyId = address, tradingKeyId = minted, tradingAlias = alias)
    }

    /**
     * Moves [amount] dollars between Tj's cash wallet and the subaccount ("fund" or "defund"), waiting for Novig to apply it. Needs the
     * management key, the only key that moves money.
     */
    suspend fun transfer(
        conn: NovigConnection,
        managementKeyId: String,
        managementPem: String,
        direction: String,
        amount: Double,
        onStep: (String) -> Unit = {},
    ): TransferOutcome {
        require(direction == "fund" || direction == "defund") { "direction must be fund or defund" }
        require(amount > 0.0) { "amount must be positive" }
        val admin = NovigSignedClient(http, json, PemSigningKey(managementKeyId.trim(), managementPem), baseUrl, clock)
        val text = BigDecimal.valueOf(amount).setScale(5, RoundingMode.HALF_UP).toPlainString()
        // A plain UUID, like an order's clientId: Novig parses that id as a UUID (Tj's first real order, 2026-09-29).
        val clientId = com.tjshea.vigilant.data.novig.trading.NovigTradingClient.newClientId()
        onStep(if (direction == "fund") "Asking Novig to move $$text into the subaccount…" else "Asking Novig to move $$text back to your cash wallet…")
        val body = """{"direction":"$direction","amount":"$text","clientTransferId":"$clientId"}"""
        val sent = json.decodeFromString(TransferDto.serializer(), admin.call("POST", "/v3/account/subaccounts/${conn.subaccountKeyId}/transfer", body = body))
        var t = sent
        val deadline = clock() + TRANSFER_WAIT_MS
        while (t.status == "Requested" && clock() < deadline) {
            pause(1_000)
            onStep("Waiting for Novig to apply the transfer…")
            t = json.decodeFromString(TransferDto.serializer(), admin.call("GET", "/v3/account/subaccounts/${conn.subaccountKeyId}/transfers/${sent.transferId}"))
        }
        return when (t.status) {
            "Applied" -> {
                val bal = runCatching { balance(admin, conn.subaccountKeyId) }.getOrNull()
                TransferOutcome(true, (if (direction == "fund") "Added " else "Took back ") + "$" + String.format(Locale.US, "%.2f", amount) + (bal?.let { ". The subaccount now holds ${money(it)}." } ?: "."), bal)
            }
            "Rejected" -> TransferOutcome(false, "Novig rejected the transfer" + (t.actualBalance?.let { " (the subaccount holds \$${it.toDoubleOrNull()?.let { v -> String.format(Locale.US, "%.2f", v) } ?: it})" } ?: "") + ": no money moved.")
            else -> TransferOutcome(false, "Novig hasn't applied the transfer yet (still requested after ${TRANSFER_WAIT_MS / 1000} s). Don't send it again: check the balance in a minute.")
        }
    }

    private suspend fun balance(client: NovigSignedClient, address: String): Double =
        json.decodeFromString(BalanceDto.serializer(), client.call("GET", "/v3/account/subaccounts/$address/balance")).balance.toDouble()

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

    @Serializable
    private data class TransferDto(val transferId: String = "", val status: String = "", val actualBalance: String? = null)

    @Serializable
    private data class BalanceDto(val balance: String)

    companion object {
        /** Keystore aliases of trading keys start with this, then the time they were made. */
        const val TRADING_PREFIX = "vigilant_novig_trading_"

        const val MINT_WAIT_MS = 90_000L
        const val MINT_RETRY_MS = 5_000L
        const val TRANSFER_WAIT_MS = 30_000L
    }
}
