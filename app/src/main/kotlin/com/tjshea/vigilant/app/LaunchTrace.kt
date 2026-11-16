package com.tjshea.vigilant.app

import java.io.File

/**
 * PF8 (Tj chat 2026-11-16, tablet on Android 10 Go: "it closes after the update, I open it again and it closes again", with no crash recorded):
 * Android 10 has no ApplicationExitInfo, so a process the system ended for memory leaves no record. Each launch keeps one line per stage reached in
 * files/launch_trace.txt (process, screen being made, on screen, stopped). The NEXT start reads it: a run that never got to "stopped" ended without
 * the screen leaving cleanly, and says how far it got and how long it lived. Tiny writes, no keys, one file replaced per start.
 */
object LaunchTrace {
    private const val FILE = "launch_trace.txt"
    @Volatile private var file: File? = null
    @Volatile private var startMs = 0L

    /** What the run before this one did, set once by [begin]; null when there was none (a first install). */
    @Volatile var previous: String? = null
        private set

    /** Call first in Application.onCreate: reads the last run's stages, then starts this run's file. */
    fun begin(dir: File, nowMs: Long = System.currentTimeMillis()) {
        runCatching {
            val f = File(dir, FILE)
            file = f
            startMs = nowMs
            previous = summarize(runCatching { f.readLines() }.getOrDefault(emptyList()))
            f.writeText("process $nowMs\n")
        }
    }

    /** One stage reached (appended; a failure is ignored). */
    fun stage(name: String, nowMs: Long = System.currentTimeMillis()) {
        runCatching { file?.appendText("$name $nowMs\n") }
    }

    /** Words for a run's lines; null when it stopped cleanly (the screen left and the process was ended later) or there is nothing to say. */
    fun summarize(lines: List<String>): String? {
        val stages = lines.mapNotNull { l -> l.trim().split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1].toLongOrNull() } }.filter { it.second != null }
        if (stages.isEmpty()) return null
        val last = stages.last()
        val first = stages.first().second!!
        val livedS = (last.second!! - first) / 1_000
        return when (last.first) {
            "stopped" -> null
            "process" -> "the process started and was ended before any screen was made (lived $livedS s)"
            "screen-create" -> "the process was ended while the first screen was being made (lived $livedS s): Android ended it during startup"
            "on-screen" -> "the process was ended while Vigilant was on screen (lived $livedS s after it started): closed by Android or crashed without a record"
            else -> "the process was ended after stage '${last.first}' (lived $livedS s)"
        }
    }
}
