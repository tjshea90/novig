package com.tjshea.vigilant.data.novig.signing

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tj's Novig management key: its ID and its .pem file's text. It is the only key that opens the Vigilant subaccount, mints its trading key and
 * moves money in and out of the Vigilant wallet (NOVIG_API.md §11). [toString] never prints the key itself.
 */
class ManagementKey(keyId: String, val pem: String) {
    val keyId: String = keyId.trim()

    /** Looks like a key ID and a private key: what the forms ask for before a button turns on. */
    val complete: Boolean get() = keyId.length >= 8 && pem.contains("PRIVATE KEY")

    override fun toString() = "ManagementKey(••••${keyId.takeLast(4)})"
}

/** What Settings shows about the saved key: never the key itself. [unreadable]: saved, but this phone can't unlock it any more. */
data class ManagementKeyHint(val keyIdEnd: String, val savedAtMs: Long, val unreadable: Boolean = false)

/** Seals a secret for disk and opens it again. On the phone it's an Android Keystore key that never leaves the secure hardware. */
interface SecretBox {
    fun seal(plain: String): String
    fun open(sealed: String): String
}

@Serializable
data class SavedManagementKey(val keyId: String, val sealedPem: String, val savedAtMs: Long)

@Serializable
data class ManagementKeyFile(val version: Int = 1, val key: SavedManagementKey? = null)

/**
 * The management key, kept on this phone once Novig has accepted it (Tj, 2026-09-29: "Make it so I only input the API key and file one time
 * and the vigilant app saves it permanently in the settings so I don't have to keep entering it. Make this and all keys persist even through
 * app updates").
 *
 *  - **Updates:** the file sits in the app's own storage and the sealing key in the Android Keystore; an update (same signing certificate,
 *    BRIEF.md) touches neither, so the key is there after every update. Only an uninstall or "Clear storage" removes it, and that removes
 *    the phone's Novig read and trading keys too, which have to be set up again anyway.
 *  - **Sealed:** it moves real money, so unlike the free odds keys (plain JSON, Tj's call) the key text is sealed by [box] and the file is
 *    left out of backups: it could only be opened on this phone.
 *  - Writes are atomic ([JsonFileStore]): a crash mid-save leaves the previous key intact.
 */
class ManagementKeyStore(
    file: File,
    private val box: SecretBox,
    json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = JsonFileStore(file, ManagementKeyFile.serializer(), { ManagementKeyFile() }, json)

    /** The saved key, or null when there's none or this phone can no longer open it ([hint] says which). */
    suspend fun load(): ManagementKey? {
        val saved = store.read().key ?: return null
        val pem = runCatching { box.open(saved.sealedPem) }.getOrNull()?.takeIf { it.contains("PRIVATE KEY") } ?: return null
        return ManagementKey(saved.keyId, pem)
    }

    suspend fun hint(): ManagementKeyHint? {
        val saved = store.read().key ?: return null
        val opens = runCatching { box.open(saved.sealedPem) }.getOrNull()?.contains("PRIVATE KEY") == true
        return ManagementKeyHint(saved.keyId.takeLast(4), saved.savedAtMs, unreadable = !opens)
    }

    /** Saves [key] in place of any earlier one. */
    suspend fun save(key: ManagementKey): ManagementKeyHint {
        require(key.complete) { "That isn't a complete management key" }
        val saved = SavedManagementKey(key.keyId, box.seal(key.pem), clock())
        store.update { it.copy(key = saved) }
        return ManagementKeyHint(saved.keyId.takeLast(4), saved.savedAtMs)
    }

    suspend fun clear() {
        store.update { it.copy(key = null) }
    }
}
