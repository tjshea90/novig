package com.tjshea.vigilant.app

import com.tjshea.vigilant.app.data.SharedTradeVault
import com.tjshea.vigilant.data.backup.TradeKeyShare
import com.tjshea.vigilant.data.cloud.EngineNovigKey
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import java.io.InputStream
import java.io.OutputStream

/**
 * Two devices, one trading key (Tj, 2026-10-30). [export] writes this device's shareable trading key to a file under a passphrase; [import] on the other device checks that Novig still accepts it (one signed echo),
 * keeps it sealed by that device's Keystore and points the Novig connection at it, so "Enable betting" there finds a key it can sign with and revokes nothing. Words only: no key text is ever in a result.
 */
object TradeKeySharing {
    data class Result(val ok: Boolean, val note: String)

    suspend fun export(c: AppContainer, out: OutputStream, passphrase: String): Result {
        val key = c.cloudTradeKeys.load()
            ?: return Result(false, "This device has no shareable trading key yet. Go to Settings › Cloud scan and tap \"Send trading key to GitHub\" (it makes one both devices can hold), then try again.")
        val sub = c.cloudTradeKeys.saved()?.subaccountKeyId.orEmpty()
        out.use { it.write(TradeKeyShare.pack(TradeKeyShare.SharedTradeKey(key.keyId, key.pem, sub), passphrase)) }
        return Result(true, "Saved the trading key (••••${key.keyId.takeLast(4)}) for your other device. On that device: Settings › Usage & keys › \"Use the trading key from my other device\". Keep the file private.")
    }

    suspend fun import(
        c: AppContainer, input: InputStream, passphrase: String,
        verify: suspend (TradeKeyShare.SharedTradeKey) -> Unit = { k -> NovigSignedClient(c.http, c.json, PemSigningKey(k.keyId, k.pem)).echo() },
    ): Result {
        val key = TradeKeyShare.unpack(input.use { it.readBytes() }, passphrase)
            ?: return Result(false, "That is not a trading key file from Vigilant, or the passphrase is wrong. Nothing was changed.")
        try {
            verify(key)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result(false, "Novig does not accept that key (${e.message?.take(80) ?: e.javaClass.simpleName}). It may have been replaced since the file was made: save a new file on the other device. Nothing was changed.")
        }
        c.cloudTradeKeys.save(EngineNovigKey(key.keyId, key.pem), key.subaccountKeyId)
        c.tradeVault.reload()
        val conn = runCatching { c.novigConnection.load() }.getOrNull()
        if (conn == null) return Result(true, "Trading key saved (••••${key.keyId.takeLast(4)}). Now connect Novig and tap \"Enable betting\": it will use this key and replace nothing.")
        val updated = conn.copy(
            subaccountKeyId = conn.subaccountKeyId.ifBlank { key.subaccountKeyId },
            tradingKeyId = key.keyId, tradingAlias = SharedTradeVault.SHARED,
        )
        runCatching { c.novigConnection.save(updated) }
        c.useConnection(updated)
        c.eventLog.info("CLOUD", "trading key shared from another device (••••${key.keyId.takeLast(4)}), accepted by Novig")
        return Result(true, "This device now bets with the same trading key as your other one (••••${key.keyId.takeLast(4)}), accepted by Novig. Nothing was revoked.")
    }
}
