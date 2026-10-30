package com.tjshea.vigilant.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Vigilant subaccount's trading key, handed from one device to another (Tj, 2026-10-30: "Make it so both devices share the same trading key").
 *
 * Novig lets a subaccount have ONE live trading key, and a device that does not hold it revokes it and mints its own (NovigBettingSetup), which locks the first device out. Two devices can bet only if both hold
 * the SAME key, so this moves that key between them: a small file under a passphrase ([BackupCipher]), never uploaded. Only a software key (the one "Send trading key to GitHub" makes) can be shared; a key kept in a
 * phone's secure hardware cannot leave it.
 */
object TradeKeyShare {
    @Serializable
    data class SharedTradeKey(val keyId: String, val pem: String, val subaccountKeyId: String) {
        override fun toString() = "SharedTradeKey(••••${keyId.takeLast(4)})"
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun pack(key: SharedTradeKey, passphrase: String): ByteArray =
        BackupCipher.encrypt(json.encodeToString(SharedTradeKey.serializer(), key).toByteArray(), passphrase)

    /** The key in [blob], or null when [passphrase] is wrong, the file is not a hand-over file, or it holds no usable key. */
    fun unpack(blob: ByteArray, passphrase: String): SharedTradeKey? {
        val plain = BackupCipher.decrypt(blob, passphrase) ?: return null
        val key = runCatching { json.decodeFromString(SharedTradeKey.serializer(), plain.decodeToString()) }.getOrNull() ?: return null
        return key.takeIf { it.keyId.isNotBlank() && it.pem.contains("PRIVATE KEY") }
    }
}
