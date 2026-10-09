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
 * Writing the diagnostics file and offering it to Android's "Share with" sheet (Tj, 2026-10-02: "when I output the diagnostic file, it opens an android 'share with' prompt,
 * and I can share it directly with Claude app"). The file lives in the app's cache (`cache/diagnostics/`, only the newest [KEEP] kept), is handed over through a
 * FileProvider (read permission for the one file, for the app he picks), and goes with a ready message telling Claude what to do with it ([DiagnosticsFile.PROMPT]).
 */
object DiagnosticsShare {

    const val KEEP = 3

    /** The FileProvider's authority: this app's own package, so Vigilant and Vigilant MGM never share one. */
    fun authority(context: Context) = "${context.packageName}.files"

    fun dir(context: Context) = File(context.cacheDir, "diagnostics")

    /** Writes [text] to [name] in [dir], and removes all but the newest [KEEP] files there. */
    fun write(context: Context, text: String, name: String): File {
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(text)
        dir.listFiles { f -> f.isFile && f.name.startsWith("vigilant-diagnostics-") }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { runCatching { it.delete() } }
        return file
    }

    /** Where [saveToDownloads] puts the file, as Tj's Files app shows it. */
    val DOWNLOADS_DIR = Environment.DIRECTORY_DOWNLOADS + "/Vigilant"

    /**
     * A copy of [file] in the phone's Downloads/Vigilant folder (Tj, 2026-10-02 17:01Z: "in addition to the share with feature, make sure the diagnosis prompt file
     * for Claude is saved to my android downloads folder"), through Android's MediaStore (Android 10+: no storage permission for the app's own files). Every
     * file is kept (storage isn't a constraint). Returns its content Uri; throws when Android refuses (nothing half-written is left behind).
     */
    fun saveToDownloads(resolver: ContentResolver, file: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOADS_DIR)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Android didn't make the file in Downloads")
        try {
            (resolver.openOutputStream(uri) ?: error("Android didn't open the file in Downloads")).use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /** The share sheet for [file]: plain text with the file attached, the prompt as the message, read permission for that file only. */
    fun intent(context: Context, file: File, versionName: String): Intent =
        share(context, file, "Vigilant diagnostics (v$versionName)", DiagnosticsFile.PROMPT, "Share diagnostics with Claude")

    /** The same for the scan study's file ([com.tjshea.vigilant.data.study.StudyExport]): its own subject and message, telling Claude to analyze every bet. */
    fun studyIntent(context: Context, file: File, versionName: String): Intent =
        share(context, file, "Vigilant scan study (v$versionName)", com.tjshea.vigilant.data.study.StudyExport.PROMPT, "Share scan study with Claude")

    private fun share(context: Context, file: File, subject: String, prompt: String, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, prompt)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Makes the scan study's file [name] in the same cache folder the FileProvider already shares ([fill] writes it, streaming: the file can be megabytes), and
     * removes all but the newest [KEEP] study files (their own count: the diagnostics files are left alone).
     */
    fun writeStudy(context: Context, name: String, fill: (java.io.Writer) -> Unit): File {
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, name)
        file.bufferedWriter().use(fill)
        dir.listFiles { f -> f.isFile && f.name.startsWith(STUDY_PREFIX) }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { runCatching { it.delete() } }
        return file
    }

    const val STUDY_PREFIX = "vigilant-scan-study-"

    /** The live burst study's file (RESEARCH.md §95), kept like the others: the newest [KEEP]. */
    fun writeBurst(context: Context, name: String, fill: (java.io.Writer) -> Unit): File {
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, name)
        file.bufferedWriter().use(fill)
        dir.listFiles { f -> f.isFile && f.name.startsWith(BURST_PREFIX) }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { runCatching { it.delete() } }
        return file
    }

    fun burstIntent(context: Context, file: File, versionName: String): Intent =
        share(context, file, "Vigilant live burst study (v$versionName)", com.tjshea.vigilant.data.novig.burst.BurstExport.PROMPT, "Share live burst study")

    const val BURST_PREFIX = "vigilant-burst-study-"

    /** The live feed test's file (RESEARCH.md §106), kept like the others: the newest [KEEP]. */
    fun writeFeedRace(context: Context, name: String, fill: (java.io.Writer) -> Unit): File {
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, name)
        file.bufferedWriter().use(fill)
        dir.listFiles { f -> f.isFile && f.name.startsWith(FEED_RACE_PREFIX) }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { runCatching { it.delete() } }
        return file
    }

    fun feedRaceIntent(context: Context, file: File, versionName: String): Intent =
        share(context, file, "Vigilant live feed test (v$versionName)", com.tjshea.vigilant.data.live.FeedRaceExport.PROMPT, "Share live feed test")

    const val FEED_RACE_PREFIX = "vigilant-feed-race-"

    /** The one research file (RESEARCH.md §122), kept like the others: the newest [KEEP]. */
    fun writeResearch(context: Context, name: String, fill: (java.io.Writer) -> Unit): File {
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, name)
        file.bufferedWriter().use(fill)
        dir.listFiles { f -> f.isFile && f.name.startsWith(RESEARCH_PREFIX) }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { runCatching { it.delete() } }
        return file
    }

    fun researchIntent(context: Context, file: File, versionName: String): Intent =
        share(context, file, "Vigilant research file (v$versionName)", com.tjshea.vigilant.data.novig.lab.LabExport.PROMPT, "Share research file")

    const val RESEARCH_PREFIX = "vigilant-research-"

    /**
     * The app's files and their sizes in bytes, largest first (the file listing in the report). A folder is listed once, as `name/`, with everything under it: the total used to
     * count only the files directly in the app's folder and left out the scan study (11.2 MB in Tj's v0.70.1 file, "Total 5651 KB").
     */
    fun storage(context: Context): List<Pair<String, Long>> = storageOf(context.filesDir)

    fun storageOf(dir: java.io.File): List<Pair<String, Long>> =
        dir.listFiles()?.map { f ->
            if (f.isDirectory) "${f.name}/" to f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.name to f.length()
        }?.sortedByDescending { it.second }.orEmpty()
}
