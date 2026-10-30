package com.tjshea.vigilant.app

import android.app.Application
import com.tjshea.vigilant.data.backup.BackupCipher
import com.tjshea.vigilant.data.backup.SealedFiles
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A 100% copy of the app for moving it to another install, such as Samsung's Secure Folder (Tj, 2026-10-30: "a backup of all data in the vigilant app including all keys and all private keys and trading keys and
 * data ... a 100% mirror copy ... so I can easily transfer it to Samsung secure folder").
 *
 * **What is in the file:** every file in the app's storage (settings, the tracker's bets and bids, the bid desk, API keys, usage, the study journals, the chat queue...), the Novig connection record, the app's
 * small preference files, and every saved secret OPENED: the Novig management key, the engine's read key, the shared trading key, the Kalshi private key and the GitHub token. The Android Keystore that seals
 * those on this phone cannot be copied to another install, so the target seals them again with its own ([SealedFiles]). All of it is one archive under a passphrase ([BackupCipher]); without the passphrase the
 * file is noise, and it is never sent anywhere by the app (Tj saves it himself; this repository is public).
 *
 * **What cannot move:** the phone's own Novig READ key lives inside this phone's secure hardware, so it stays; the target reconnects with "Enable betting" from the management key that DOES move. The trading key the
 * engine shares is a software key and moves with the rest.
 *
 * **Restoring** is staged: the archive is opened into `restore_pending/` (secrets sealed with the target's Keystore) and applied at the next start, before any store is opened ([applyPending]); a wrong
 * passphrase or a damaged file changes nothing.
 */
object FullBackup {
    private const val MANIFEST = "manifest.json"
    private const val CONNECTION = "connection.json"
    private const val FILES = "files/"
    private const val PREFS = "shared_prefs/"
    const val PENDING = "restore_pending"
    private const val VERSION = 1
    /** Never put into the archive: the staging folder itself, DataStore (the connection is carried separately), half-written and quarantined files. */
    private fun skip(rel: String): Boolean =
        rel.startsWith("$PENDING/") || rel.startsWith("datastore/") || rel.endsWith(".tmp") || rel.contains(".corrupt-") || rel.startsWith("backup_out/")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    data class Report(val ok: Boolean, val files: Int, val bytes: Long, val secrets: List<String>, val unreadable: List<String>, val note: String)

    private val SECRET_WORDS = mapOf(
        "novig_management_key.json" to "Novig management key",
        "cloud_novig_key.json" to "Novig read key for GitHub",
        "cloud_trade_key.json" to "Novig trading key",
        "kalshi_key.json" to "Kalshi private key",
        "cloud_token.json" to "GitHub token",
    )

    /** Writes the archive to [out] (closed at the end) under [passphrase]. */
    suspend fun write(c: AppContainer, out: OutputStream, passphrase: String): Report {
        val filesDir = c.app.filesDir
        val secrets = ArrayList<String>()
        val unreadable = ArrayList<String>()
        var files = 0
        var bytes = 0L
        ZipOutputStream(BackupCipher.encryptingStream(out, passphrase)).use { zip ->
            zip.setLevel(6)
            fun put(name: String, data: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
            for (f in filesDir.walkTopDown().filter { it.isFile }.sortedBy { it.path }) {
                val rel = f.relativeTo(filesDir).invariantSeparatorsPath
                if (skip(rel)) continue
                val data = runCatching { f.readBytes() }.getOrNull() ?: continue
                val entry = if (SealedFiles.FIELDS.containsKey(rel)) {
                    val text = data.decodeToString()
                    if (!SealedFiles.holdsSecret(rel, text)) data
                    else SealedFiles.open(rel, text) { c.openSecret(it) }?.also { secrets += SECRET_WORDS[rel] ?: rel }?.toByteArray()
                        ?: run { unreadable += SECRET_WORDS[rel] ?: rel; null }
                } else data
                if (entry == null) continue
                put(FILES + rel, entry); files++; bytes += entry.size
            }
            val prefsDir = File(filesDir.parentFile, "shared_prefs")
            prefsDir.listFiles { x -> x.isFile && x.name.endsWith(".xml") }?.sortedBy { it.name }?.forEach { f ->
                runCatching { f.readBytes() }.getOrNull()?.let { put(PREFS + f.name, it); files++; bytes += it.size }
            }
            c.novigConnection.load()?.let { n ->
                put(CONNECTION, buildJsonObject {
                    put("readKeyId", JsonPrimitive(n.readKeyId)); put("readAlias", JsonPrimitive(n.readAlias))
                    put("subaccountKeyId", JsonPrimitive(n.subaccountKeyId))
                    n.tradingKeyId?.let { put("tradingKeyId", JsonPrimitive(it)) }
                    n.tradingAlias?.let { put("tradingAlias", JsonPrimitive(it)) }
                }.toString().toByteArray())
            }
            put(MANIFEST, buildJsonObject {
                put("version", JsonPrimitive(VERSION)); put("createdAtMs", JsonPrimitive(System.currentTimeMillis()))
                put("app", JsonPrimitive(BuildConfig.VERSION_NAME)); put("files", JsonPrimitive(files))
            }.toString().toByteArray())
        }
        val note = "Backed up $files files (${bytes / 1024} KB)" + (if (secrets.isEmpty()) "." else " with ${secrets.size} key${if (secrets.size == 1) "" else "s"}: ${secrets.joinToString(", ")}.") +
            (if (unreadable.isEmpty()) "" else " NOT readable on this phone, so left out: ${unreadable.joinToString(", ")}.")
        return Report(true, files, bytes, secrets, unreadable, note)
    }

    /**
     * Opens [input] with [passphrase] into `restore_pending/` and re-seals its secrets for THIS install; nothing in the running app is touched. The result says whether the app should now be restarted.
     */
    suspend fun stage(c: AppContainer, input: InputStream, passphrase: String): Report {
        val pending = File(c.app.filesDir, PENDING)
        pending.deleteRecursively()
        val stream = BackupCipher.decryptingStream(input, passphrase)
            ?: return Report(false, 0, 0, emptyList(), emptyList(), "That is not a Vigilant backup, or the passphrase is wrong. Nothing was changed.")
        var files = 0
        var bytes = 0L
        var manifest = false
        val secrets = ArrayList<String>()
        try {
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    if (name.contains("..") || name.startsWith("/") || name.contains('\\')) throw IOException("the backup has an unsafe file name")
                    val data = zip.readBytes()
                    when {
                        name == MANIFEST -> manifest = true
                        name == CONNECTION -> File(pending, CONNECTION).also { it.parentFile?.mkdirs() }.writeBytes(data)
                        name.startsWith(FILES) || name.startsWith(PREFS) -> {
                            val rel = name
                            val bare = name.removePrefix(FILES)
                            val bytesOut = if (name.startsWith(FILES) && SealedFiles.FIELDS.containsKey(bare)) {
                                SealedFiles.seal(bare, data.decodeToString()) { c.sealSecret(it) }?.also { secrets += SECRET_WORDS[bare] ?: bare }?.toByteArray() ?: data
                            } else data
                            File(pending, rel).also { it.parentFile?.mkdirs() }.writeBytes(bytesOut)
                            files++; bytes += bytesOut.size
                        }
                    }
                }
            }
            if (!manifest) throw IOException("the backup has no manifest")
        } catch (e: IOException) {
            pending.deleteRecursively()
            return Report(false, 0, 0, emptyList(), emptyList(), "The backup could not be read (${e.message?.take(80)}). Nothing was changed.")
        }
        return Report(true, files, bytes, secrets, emptyList(), "Backup opened: $files files" + (if (secrets.isEmpty()) "" else " and ${secrets.size} key${if (secrets.size == 1) "" else "s"} (${secrets.joinToString(", ")})") + ". It is applied when the app starts again.")
    }

    /** True when a staged restore is waiting for the next start. */
    fun pending(app: Application): Boolean = File(app.filesDir, PENDING).isDirectory

    /**
     * Applies a staged restore. Called from [VigilantApp.onCreate] BEFORE any store is opened, so no running store rewrites the files. Files are copied over their namesakes (the pending folder is kept until
     * everything is copied, so a crash in the middle just does it again); the Novig connection is restored only as far as this install can sign with it.
     */
    fun applyPending(app: Application) {
        val pending = File(app.filesDir, PENDING)
        if (!pending.isDirectory) return
        runCatching {
            pending.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(pending).invariantSeparatorsPath
                when {
                    rel.startsWith(FILES) -> copy(f, File(app.filesDir, rel.removePrefix(FILES)))
                    rel.startsWith(PREFS) -> copy(f, File(File(app.filesDir.parentFile, "shared_prefs"), rel.removePrefix(PREFS)))
                }
            }
            File(pending, CONNECTION).takeIf { it.isFile }?.let { restoreConnection(app, json.parseToJsonElement(it.readText()).jsonObject) }
        }
        pending.deleteRecursively()
    }

    private fun copy(from: File, to: File) {
        to.parentFile?.mkdirs()
        val tmp = File(to.path + ".tmp")
        from.copyTo(tmp, overwrite = true)
        if (!tmp.renameTo(to)) { to.delete(); tmp.renameTo(to) }
    }

    /** The shared trading key moves with the files; a key held in this phone's secure hardware moves only if it is really in this Keystore. */
    private fun restoreConnection(app: Application, o: JsonObject) {
        fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val readKeyId = s("readKeyId") ?: return
        val readAlias = s("readAlias") ?: return
        val have = com.tjshea.vigilant.app.data.KeystoreSigningKey.aliases("")
        val shared = com.tjshea.vigilant.app.data.SharedTradeVault.SHARED
        if (readAlias !in have && readAlias != shared) return
        val trading = s("tradingAlias")?.takeIf { it in have || it == shared }
        val conn = NovigConnection(
            readKeyId, readAlias, s("subaccountKeyId").orEmpty(), createdSubaccount = false,
            tradingKeyId = if (trading != null) s("tradingKeyId") else null, tradingAlias = trading,
        )
        runBlocking { com.tjshea.vigilant.app.data.NovigConnectionStore(app).save(conn) }
    }

    /** First look at a backup file for a human: nothing but its size, to confirm the right file was picked. */
    fun describe(bytes: Long): String = "${bytes / 1024} KB"
}
