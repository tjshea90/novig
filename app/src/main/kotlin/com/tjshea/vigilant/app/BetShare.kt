package com.tjshea.vigilant.app

import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sharing every bet and bid with Claude, and the daily copies in Downloads (Tj, 2026-10-11). The files are made by [com.tjshea.vigilant.data.tracker.BetHistoryExport] into the cache folder
 * the FileProvider shares (`cache/bet-share/`, emptied before each export so old parts never pile up); this hands them to Android's "Share with" sheet with a ready message, and copies
 * them to Downloads/Vigilant as well. [DownloadsMirror] keeps `Downloads/Vigilant/bets/` current with the app's day-by-day archive ([com.tjshea.vigilant.data.tracker.BetArchive]).
 */
object BetShare {

    /** How far back a share goes. */
    enum class Range(val label: String, val days: Int?) {
        LAST_7("Last 7 days", 7), LAST_30("Last 30 days", 30), LAST_90("Last 90 days", 90), ALL("Everything", null);

        fun fromMs(now: Long): Long? = days?.let { now - it * 86_400_000L }
    }

    fun dir(context: Context) = File(context.cacheDir, "bet-share")

    /** The message that goes with the files. */
    const val PROMPT = "These are my Vigilant bet and bid logs: a README (definitions, summary tables, automatic findings) and the bets and bids as JSON lines. " +
        "Read the README first, then analyze every bet and bid: which kinds beat the closing line (CLV) and are profitable, whether the star rating, sharp-book check and book agreement predict it, " +
        "and give specific recommendations (filters, thresholds, auto-bet and auto-bid rules, app changes). Give sample sizes and say when a sample is too small to trust."

    /** The share sheet for [files] (a README and parts): plain text with every file attached, read permission for those files only. */
    fun intent(context: Context, files: List<File>, versionName: String): Intent {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, DiagnosticsShare.authority(context), it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Vigilant bet history (v$versionName)")
            putExtra(Intent.EXTRA_TEXT, PROMPT)
            clipData = ClipData.newRawUri(files.first().name, uris.first()).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share bets with Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Where the day files go, as Tj's Files app shows it. */
    val BETS_DIR: String = Environment.DIRECTORY_DOWNLOADS + "/Vigilant/bets"

    /** Copies of files in Downloads/Vigilant/bets, replacing the one of the same name (a day file is rewritten as its results arrive). */
    object DownloadsMirror {

        /**
         * Copies [file] to [relativeDir] in Downloads: over the file of that name the app made before (opened for rewriting), else as a new file. Throws when Android refuses; nothing half-written
         * is left (a new file is deleted, a rewrite is whole or untouched because the bytes are written after the stream opens).
         */
        fun put(resolver: ContentResolver, file: File, relativeDir: String = BETS_DIR, mime: String = "application/x-ndjson"): Uri {
            existing(resolver, file.name, relativeDir)?.let { uri ->
                val ok = runCatching {
                    (resolver.openOutputStream(uri, "wt") ?: error("Android didn't open ${file.name}")).use { out -> file.inputStream().use { it.copyTo(out) } }
                }.isSuccess
                if (ok) return uri
                // Made by an earlier install (not ours to rewrite): fall through to a fresh file; Android numbers the name.
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Android didn't make ${file.name} in Downloads")
            try {
                (resolver.openOutputStream(uri) ?: error("Android didn't open ${file.name} in Downloads")).use { out -> file.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            return uri
        }

        private fun existing(resolver: ContentResolver, name: String, relativeDir: String): Uri? {
            val rel = relativeDir.trimEnd('/') + "/"
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf(name, rel), null,
            )?.use { c -> if (c.moveToFirst()) return android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)) }
            return null
        }
    }
}
