package com.tjshea.vigilant.app

import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.Locale
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Where the app's CPU time goes while a Vigilant scan runs (Tj, 2026-10-03: "the entire app gets laggy when vigilant is scanning, but not when cno
 * only is scanning"): each thread's CPU time from Linux (`/proc/self/task/<tid>/stat`, readable by the app itself, no permission), grouped by what
 * the thread does. A snapshot at the scan's start and one at its end give the split Diagnostics shows, so the next file says whether the screen
 * waited on the scan's own work, on the garbage collector, or on something else.
 */
object ThreadCpu {
    /** CPU milliseconds by thread group over [wallMs] of wall time. */
    data class Split(val wallMs: Long, val byGroup: Map<String, Long>)

    /** One thread: its group and the CPU it has used so far, in milliseconds. */
    data class Thread(val group: String, val cpuMs: Long)

    const val MAIN = "main (the screen)"
    const val RENDER = "RenderThread"
    const val SCAN = "scan workers"
    const val DEFAULT = "Default dispatcher"
    const val OKHTTP = "OkHttp"
    const val GC = "garbage collector"
    const val BINDER = "binder"
    const val OTHER = "other"

    /** The group of a thread named [comm] (Linux keeps 15 characters of it). */
    fun group(comm: String, isMain: Boolean): String = when {
        isMain -> MAIN
        comm == "RenderThread" || comm.startsWith("hwui") -> RENDER
        comm.startsWith(ScanThreads.PREFIX) -> SCAN
        comm.startsWith("DefaultDispatch") -> DEFAULT
        comm.startsWith("OkHttp") || comm.startsWith("Okio") -> OKHTTP
        comm == "HeapTaskDaemon" || comm.startsWith("ReferenceQueue") || comm.startsWith("Finalizer") -> GC
        comm.startsWith("binder") -> BINDER
        else -> OTHER
    }

    /**
     * One `stat` line: (name, CPU ticks used). The name is in parentheses and may hold spaces; after it come the state (field 3) … utime (14) and
     * stime (15). Null when it can't be read.
     */
    fun parse(stat: String): Pair<String, Long>? {
        val open = stat.indexOf('(')
        val close = stat.lastIndexOf(')')
        if (open < 0 || close <= open) return null
        val fields = stat.substring(close + 1).trim().split(' ')
        if (fields.size < 13) return null
        val utime = fields[11].toLongOrNull() ?: return null
        val stime = fields[12].toLongOrNull() ?: return null
        return stat.substring(open + 1, close) to utime + stime
    }

    /** Every thread of this process now, by thread id. Empty when /proc can't be read. */
    fun snapshot(
        dir: File = File("/proc/self/task"),
        pid: Int = android.os.Process.myPid(),
        tickMs: Double = 1_000.0 / clockTicks(),
    ): Map<Int, Thread> {
        val out = HashMap<Int, Thread>()
        dir.listFiles()?.forEach { task ->
            val tid = task.name.toIntOrNull() ?: return@forEach
            val (comm, ticks) = runCatching { parse(File(task, "stat").readText()) }.getOrNull() ?: return@forEach
            out[tid] = Thread(group(comm, tid == pid), (ticks * tickMs).toLong())
        }
        return out
    }

    /** The CPU each group used between [before] and [after] (a thread that started since counts whole; one that ended since is lost). */
    fun between(before: Map<Int, Thread>, after: Map<Int, Thread>, wallMs: Long): Split {
        val by = HashMap<String, Long>()
        for ((tid, t) in after) {
            val used = t.cpuMs - (before[tid]?.takeIf { it.group == t.group }?.cpuMs ?: 0L)
            if (used > 0) by.merge(t.group, used, Long::plus)
        }
        return Split(wallMs, by)
    }

    /** "CPU during the last Vigilant scan (484 s; 100% = one core): scan workers 85% · garbage collector 18% · main (the screen) 6% · …". */
    fun text(s: Split, what: String): String {
        if (s.wallMs <= 0 || s.byGroup.isEmpty()) return "CPU during $what: not measured"
        val total = s.byGroup.values.sum()
        return "CPU during $what (${s.wallMs / 1_000} s; 100% = one core, ${String.format(Locale.US, "%.0f%%", total * 100.0 / s.wallMs)} in all): " +
            s.byGroup.entries.sortedByDescending { it.value }.joinToString(" · ") { (g, ms) -> "$g ${String.format(Locale.US, "%.0f%%", ms * 100.0 / s.wallMs)}" }
    }

    private fun clockTicks(): Long = runCatching { android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK) }.getOrDefault(100L).takeIf { it > 0 } ?: 100L
}

/**
 * The threads Vigilant's scan runs on: as many as the phone has cores (the Default dispatcher's own count, so a scan's throughput is unchanged), at
 * Android's background priority. The screen's threads then win the CPU whenever both want it, so a scan's parsing and pricing no longer take frames
 * from the screen (Tj, 2026-10-03: "the entire app gets laggy when vigilant is scanning"); with the screen idle the scan has the cores to itself.
 */
object ScanThreads {
    const val PREFIX = "vig-scan-"

    fun factory(priority: Int = android.os.Process.THREAD_PRIORITY_BACKGROUND): ThreadFactory {
        val n = AtomicInteger()
        return ThreadFactory { r ->
            java.lang.Thread({
                runCatching { android.os.Process.setThreadPriority(priority) }
                r.run()
            }, PREFIX + n.incrementAndGet()).apply { isDaemon = true }
        }
    }

    /** The pool: [threads] at most, each ending after [idleSeconds] with nothing to do (no scan running holds no thread). */
    fun dispatcher(threads: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(2), idleSeconds: Long = 30) =
        java.util.concurrent.ThreadPoolExecutor(threads, threads, idleSeconds, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.LinkedBlockingQueue(), factory())
            .apply { allowCoreThreadTimeOut(true) }
            .asCoroutineDispatcher()
}
