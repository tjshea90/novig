package com.tjshea.vigilant.app

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * How Vigilant's process last ended, for Diagnostics (Tj, 2026-09-30: "The app just crashed a couple times. Both times it was scanning
 * vigilant and I tried to switch tabs, which got very laggy then crashed"): a crash's stack is kept the moment it happens ([install]), and
 * Android's own record of each exit ([ApplicationExitInfo]: a crash, "not responding", killed for memory…) is read when the report is made.
 * Nothing here holds a key: stacks name the app's code, and a crash message is masked like any other problem ([com.tjshea.vigilant.data.diag.ProblemLog.clean]).
 */
object AppExits {

    /** One exit as Android recorded it. [trace]: for "not responding", the main thread's stack (where it was stuck); for a crash, none (the saved stack has it). */
    data class Exit(
        val atMs: Long,
        val reason: String,
        val description: String?,
        /** Whether Vigilant was on screen then. */
        val foreground: Boolean,
        /** Memory the process used (proportional set size, MB), when Android kept it. */
        val pssMb: Long?,
        val trace: List<String> = emptyList(),
        /** How important Android held the process when it ended (ActivityManager.RunningAppProcessInfo.IMPORTANCE_*); null = not known. */
        val importance: Int? = null,
    ) {
        /**
         * Ended for memory while it was a CACHED app: nothing on screen, no widget over Novig, no scan or service running. That is Android making room
         * for the apps in use, as it does for any app sitting in the background; it costs a cold start, not a feature (Tj's 2026-10-02 Diagnostics
         * counted three as failures).
         */
        val reclaimed: Boolean get() = reason == "low memory" && importance != null && importance >= CACHED

        /** A crash, a freeze or a kill for memory while it was doing something: something that went wrong, not Tj closing the app or an update. */
        val bad: Boolean get() = reason in BAD && !reclaimed

        /** Where it was, in words: "on screen", "a visible window", "background service", "cached in the background"… */
        val where: String get() = when {
            foreground && (importance == null || importance <= FOREGROUND) -> "on screen"
            importance == null -> "in the background"
            importance <= FOREGROUND_SERVICE -> "running a foreground service (a scan or auto-scan)"
            importance <= VISIBLE -> "in a visible window (the widget or the mini window)"
            importance <= PERCEPTIBLE -> "perceptible (the widget over another app)"
            importance < CACHED -> "in the background with work running"
            else -> "cached in the background (nothing running)"
        }
    }

    // ActivityManager.RunningAppProcessInfo's importance levels, named here so the tests can use them without Android.
    const val FOREGROUND = 100
    const val FOREGROUND_SERVICE = 125
    const val VISIBLE = 200
    const val PERCEPTIBLE = 230
    const val CACHED = 400

    private const val FILE = "last_crash.txt"
    private val BAD = setOf("crash", "native crash", "not responding", "low memory", "excessive resource use", "initialization failure")

    /**
     * Keeps the stack of any crash in files/last_crash.txt before Android ends the process (written at once: nothing after a crash
     * can wait for a coroutine), then lets the crash go on as it would have.
     */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val file = File(context.filesDir, FILE)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText(crashText(thread.name, error, System.currentTimeMillis())) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** What's kept of a crash: when, the thread, and the stack (the causes too), at most [MAX_CRASH_CHARS]. */
    fun crashText(thread: String, error: Throwable, atMs: Long): String {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        // The heap at the crash says whether it was memory (an OutOfMemoryError names its limit, but not how full the heap was).
        val heap = runCatching { " heap=${com.tjshea.vigilant.data.MemoryGuard.usedMb()}/${com.tjshea.vigilant.data.MemoryGuard.maxMb()}MB" }.getOrDefault("")
        return "at=$atMs thread=$thread$heap\n" + stack.take(MAX_CRASH_CHARS)
    }

    /** The crash saved by [install] since the last look, as (when, first lines), and the file removed; null when there's none. */
    fun takeSavedCrash(context: Context): Pair<Long, String>? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        return text?.let(::parseSaved)
    }

    /** [crashText]'s text back: when, and the thread with the stack's first lines. */
    fun parseSaved(text: String): Pair<Long, String>? {
        val head = text.lineSequence().firstOrNull() ?: return null
        val at = Regex("at=(\\d+)").find(head)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        // "thread=main heap=250/256MB": the heap is part of what's said, the thread's name is the rest.
        val thread = Regex("thread=(.*)").find(head)?.groupValues?.get(1).orEmpty().replace(Regex(" heap=(\\d+)/(\\d+)MB"), " (heap $1 of $2 MB)")
        val stack = text.lineSequence().drop(1).filter { it.isNotBlank() }.take(STACK_LINES).joinToString(" | ") { it.trim() }
        return at to "on $thread: $stack"
    }

    /** Android's record of how the process ended, newest first ([max] at most). */
    fun recent(context: Context, max: Int = 6): List<Exit> {
        val am = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        val infos = runCatching { am.getHistoricalProcessExitReasons(null, 0, max) }.getOrNull() ?: return emptyList()
        return infos.map { info ->
            val reason = reasonName(info.reason)
            Exit(
                atMs = info.timestamp,
                reason = reason,
                description = info.description,
                foreground = info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                pssMb = info.pss.takeIf { it > 0 }?.let { it / 1024 },
                importance = info.importance,
                trace = if (info.reason == ApplicationExitInfo.REASON_ANR) runCatching { mainThread(info.traceInputStream?.bufferedReader()?.use { it.readText() }) }.getOrDefault(emptyList()) else emptyList(),
            )
        }
    }

    /** From a "not responding" dump, the main thread's stack: where the app was stuck. */
    fun mainThread(dump: String?): List<String> {
        if (dump.isNullOrBlank()) return emptyList()
        val lines = dump.lines()
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        if (start < 0) return emptyList()
        return lines.drop(start).takeWhile { it.isNotBlank() }.map { it.trim() }.filter { it.startsWith("at ") || it.startsWith("\"main\"") }.take(STACK_LINES + 4)
    }

    fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "not responding"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by you"
        ApplicationExitInfo.REASON_USER_STOPPED -> "force-stopped by you"
        ApplicationExitInfo.REASON_EXIT_SELF -> "ended itself"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "a permission changed"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a dependency died"
        ApplicationExitInfo.REASON_OTHER -> "other (often Android freeing memory or an update)"
        else -> "reason $reason"
    }

    private const val MAX_CRASH_CHARS = 6_000
    private const val STACK_LINES = 12
}
