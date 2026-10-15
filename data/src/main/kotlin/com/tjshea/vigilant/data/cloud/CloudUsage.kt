package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.UsageBook
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * The GitHub engine's API usage, published with its heartbeat (`usage.json` on `cloud-feed`; Tj, 2026-10-15: "Make sure I can still see accurate api usage meters in the app on my phone"). The engine makes most
 * of the calls now, so the phone's own ledger no longer sees them; the phone lays this over its own ([com.tjshea.vigilant.data.keys.UsageMerge]) so the meters, Diagnostics and the health checks show what both spent.
 *
 * **The repository is public, so no key is ever in it**: each key is replaced by [keyId] (the first 5 bytes of its SHA-256: too short to find a key from, enough to tell this account's keys apart), the call times
 * are dropped, and a provider's note has every key text scrubbed out. The phone maps the ids back to its own keys ([rekey]); an id it has no key for is dropped. The next engine job reads the file back
 * ([com.tjshea.vigilant.data.keys.UsageMeter.seed]) so a handover does not forget what a daily allowance already spent.
 */
object CloudUsage {
    @Serializable
    data class File(val v: Int = 1, val at: Long = 0L, val run: String = "", val book: UsageBook = UsageBook())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun keyId(key: String): String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).take(5).joinToString("") { "%02x".format(it) }

    /** [book] as the engine publishes it: keys by [keyId], no call times, notes scrubbed of any of [keys]. */
    fun encode(book: UsageBook, run: String, atMs: Long, keys: Collection<String>): String {
        val safe = UsageBook(book.providers.mapValues { (_, p) ->
            p.copy(keys = p.keys.entries.associate { (k, u) -> keyId(k) to u.copy(recent = emptyList(), lastNote = u.lastNote?.let { scrub(it, keys) }) })
        })
        return json.encodeToString(File.serializer(), File(1, atMs, run, safe))
    }

    private fun scrub(note: String, keys: Collection<String>): String = keys.filter { it.length >= 6 }.fold(note) { n, k -> n.replace(k, "••••") }.take(120)

    /** The published file (keys still by id), or null when it cannot be read. */
    fun decode(text: String): File? = runCatching { json.decodeFromString(File.serializer(), text) }.getOrNull()

    /** [book] with every key id turned back into the key string in [ownKeys] that has it; ids with no such key are dropped. */
    fun rekey(book: UsageBook, ownKeys: Collection<String>): UsageBook {
        val byId = ownKeys.filter { it.isNotBlank() }.associateBy(::keyId)
        return UsageBook(book.providers.mapValues { (_, p: ProviderUsage) ->
            p.copy(keys = p.keys.entries.mapNotNull { (id, u: KeyUsage) -> byId[id]?.let { it to u } }.toMap())
        })
    }
}
