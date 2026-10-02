package com.tjshea.vigilant.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
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

    /** The share sheet for [file]: plain text with the file attached, the prompt as the message, read permission for that file only. */
    fun intent(context: Context, file: File, versionName: String): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Vigilant diagnostics (v$versionName)")
            putExtra(Intent.EXTRA_TEXT, DiagnosticsFile.PROMPT)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share diagnostics with Claude").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** The app's files and their sizes in bytes, largest first (the file listing in the report). */
    fun storage(context: Context): List<Pair<String, Long>> =
        context.filesDir.listFiles()?.filter { it.isFile }?.map { it.name to it.length() }?.sortedByDescending { it.second }.orEmpty()
}
