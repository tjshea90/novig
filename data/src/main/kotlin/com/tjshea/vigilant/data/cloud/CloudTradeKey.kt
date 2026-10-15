package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * The Vigilant subaccount's `trading` key in a form the GitHub engine can hold (Tj, 2026-10-15: "make it so I can push all needed keys, including secret keys, from the app on my phone to GitHub" and "fully
 * autonomous bet and bid app", TASKS.md TCL20c).
 *
 * The phone's trading key lives in the Android Keystore and cannot be taken out, and Novig allows ONE live `trading` key per subaccount (NOVIG_API.md §15). So handing the engine a trading key means REPLACING the
 * live one: revoke it with the management key (takes up to a minute), then make a new `trading` key from a software keypair whose private half the phone seals ([CloudNovigKeyStore]) and sends to a write-only
 * GitHub secret. The phone signs with the same sealed key afterwards, so nothing about betting by hand changes. It can place orders on the Vigilant subaccount; it cannot move money (only the management
 * key does, and that one NEVER leaves the phone). The caller asks Tj to confirm first; nothing here runs by itself.
 */
object CloudTradeKey {
    const val NAME = "Vigilant trading (phone and cloud engine)"
    const val MINT_WAIT_MS = 90_000L
    const val MINT_RETRY_MS = 5_000L

    /** [verified]: the new key answered a signed echo (false: made but not proved yet, the caller says so and keeps it). */
    class Swapped(val key: EngineNovigKey, val verified: Boolean)

    suspend fun swap(
        http: OkHttpClient, json: Json, management: ManagementKey, subaccountKeyId: String,
        baseUrl: String = "https://api.novig.com", clock: () -> Long = System::currentTimeMillis,
        pause: suspend (Long) -> Unit = { delay(it) }, onStep: (String) -> Unit = {},
    ): Swapped {
        require(subaccountKeyId.isNotBlank()) { "no Vigilant subaccount is connected" }
        val admin = NovigSignedClient(http, json, PemSigningKey(management.keyId, management.pem), baseUrl, clock)
        onStep("Checking your management key with Novig…")
        admin.echo()
        val live = admin.listSubaccounts().firstOrNull { it.keyId == subaccountKeyId || it.label == com.tjshea.vigilant.data.novig.signing.NovigSetup.LABEL }?.keyId
            ?: throw IllegalStateException("The Vigilant subaccount isn't on this Novig account any more.")
        onStep("Revoking the subaccount's current trading key…")
        admin.call("DELETE", "/v3/keys/$live")
        val (privatePem, publicPem) = CloudNovigKey.generate()
        val deadline = clock() + MINT_WAIT_MS
        var minted: String? = null
        while (minted == null) {
            try {
                minted = admin.createSubaccountKey(subaccountKeyId, NAME, publicPem, NovigKeyAlgorithm.P256, "trading").keyId
            } catch (e: NovigApiException) {
                // 409: the revoked key still counts as live for up to a minute.
                if (e.status != 409 || clock() >= deadline) throw e
                onStep("Waiting for Novig to finish revoking the old key…")
                pause(MINT_RETRY_MS)
            }
        }
        val verified = try {
            NovigSignedClient(http, json, PemSigningKey(minted, privatePem), baseUrl, clock).echo(); true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        return Swapped(EngineNovigKey(minted, privatePem), verified)
    }
}
