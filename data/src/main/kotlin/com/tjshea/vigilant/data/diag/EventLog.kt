package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable

/** How much an [Event] matters to whoever reads the report. */
@Serializable
enum class Level { INFO, WARN, ERROR }

/**
 * One thing that happened, in a line: when ([atMs], and [lastMs] when the same thing repeated [n] times), what area ([cat]: "NET", "SCAN", "CYCLE",
 * "AUTOBET", "SHARP", "ALERT", "SERVICE", "CNO", "APP", "ERROR"…), what ([msg], masked and short), where in the code it came from for an error
 * ([where]: the app's own frames of its stack), and how long it took ([ms]).
 */
@Serializable
data class Event(
    val atMs: Long,
    val cat: String,
    val level: Level,
    val msg: String,
    val where: String? = null,
    val ms: Long? = null,
    val n: Int = 1,
    val lastMs: Long = atMs,
)

/** What the event log keeps on disk (files/events.json): the newest events, and counters of things too frequent to be events (reasons a bet was skipped…). */
@Serializable
data class EventBook(val events: List<Event> = emptyList(), val counters: Map<String, Long> = emptyMap(), val sinceMs: Long? = null)

/**
 * The flight recorder (Tj, 2026-10-02: "log all types of events, code, failures … so that when I output the diagnostic file Claude can diagnose and improve the
 * app"): always on, kept across restarts, bounded, never a key. Notable things only: an error with where in the code it came from, a failed or slow call, the
 * end of a scan, a cycle that ran long, what auto-bet and the sharp check decided, a service starting or stopping. What happens every few seconds is a
 * [count]er or a [NetStats] figure, not an event.
 *
 * In memory first (any thread), written to the file by [flush]: the app flushes on a timer and after an error, so a crash loses at most the last few
 * seconds. The same thing twice in a row within [MERGE_MS] is one event with a count. Messages are masked ([ProblemLog.clean]); an exception's text can
 * name a URL or a token, and neither reaches the file.
 */
class EventLog(private val store: JsonFileStore<EventBook>, private val clock: () -> Long = System::currentTimeMillis, private val keep: Int = KEEP) {
    private val lock = Any()
    private val events = ArrayList<Event>()
    private val counters = HashMap<String, Long>()
    private var sinceMs: Long? = null
    private var loaded = false
    private var dirty = false
    private var flushedAtMs = 0L

    /** Writes one event. Cheap: a lock and a list. */
    fun record(cat: String, level: Level, msg: String, where: String? = null, ms: Long? = null) {
        val now = clock()
        val text = ProblemLog.clean(msg).take(MAX_MSG)
        if (text.isBlank()) return
        synchronized(lock) {
            val last = events.lastOrNull()
            if (last != null && last.cat == cat && last.level == level && last.msg == text && last.where == where && now - last.lastMs <= MERGE_MS) {
                events[events.lastIndex] = last.copy(n = last.n + 1, lastMs = now, ms = ms ?: last.ms)
            } else {
                events += Event(now, cat, level, text, where, ms)
                if (events.size > keep) events.subList(0, events.size - keep).clear()
            }
            if (sinceMs == null) sinceMs = now
            dirty = true
        }
    }

    fun info(cat: String, msg: String, ms: Long? = null) = record(cat, Level.INFO, msg, ms = ms)

    fun warn(cat: String, msg: String, ms: Long? = null) = record(cat, Level.WARN, msg, ms = ms)

    /** An error with the exception's class and message, and where in the app's code it was thrown ([whereOf]). */
    fun error(cat: String, msg: String, t: Throwable? = null) =
        record(cat, Level.ERROR, if (t == null) msg else "$msg (${t.javaClass.simpleName}: ${t.message ?: "no message"})", where = t?.let(::whereOf))

    /** A counter for something too frequent to be events: "autobet.skip.<reason>". */
    fun count(key: String, by: Long = 1) {
        synchronized(lock) {
            counters.merge(key.take(MAX_KEY), by, Long::plus)
            if (sinceMs == null) sinceMs = clock()
            dirty = true
        }
    }

    /** The events, oldest first. */
    fun events(): List<Event> = synchronized(lock) { events.toList() }

    fun counters(): Map<String, Long> = synchronized(lock) { HashMap(counters) }

    fun sinceMs(): Long? = synchronized(lock) { sinceMs }

    /** The saved events and counters, put in front of what this run has written already. Once. */
    suspend fun load() {
        val book = runCatching { store.read() }.getOrDefault(EventBook())
        synchronized(lock) {
            if (loaded) return
            loaded = true
            val mine = events.toList()
            events.clear()
            events += (book.events + mine).takeLast(keep)
            book.counters.forEach { (k, v) -> counters.merge(k, v, Long::plus) }
            sinceMs = listOfNotNull(book.sinceMs, sinceMs).minOrNull()
            // A record older than [WINDOW_MS] starts again: the counters are about recent behaviour.
            val start = sinceMs
            if (start != null && clock() - start > WINDOW_MS) {
                counters.clear()
                sinceMs = clock()
            }
        }
    }

    /** Writes what changed to the file. [force]: even if it was written a moment ago (an error, the report). */
    suspend fun flush(force: Boolean = false) {
        val book = synchronized(lock) {
            if (!dirty || (!force && clock() - flushedAtMs < MIN_FLUSH_GAP_MS)) return
            dirty = false
            flushedAtMs = clock()
            EventBook(events.toList(), HashMap(counters), sinceMs)
        }
        runCatching { store.update { book } }
    }

    companion object {
        const val KEEP = 1_000
        const val MAX_MSG = 220
        const val MAX_KEY = 120
        const val MERGE_MS = 60_000L
        const val MIN_FLUSH_GAP_MS = 5_000L
        const val WINDOW_MS = 14L * 24 * 3_600_000L

        /**
         * Where an exception came from: the first three frames of the app's own code ("com.tjshea.vigilant.app.AutoScanner.cycle(AutoScan.kt:231)"), else the first
         * frame at all. Class names and line numbers only: nothing a user typed. [short] and [pathOf] turn a frame into what a reader wants.
         */
        fun whereOf(t: Throwable): String {
            val chain = generateSequence(t) { it.cause?.takeIf { c -> c !== it } }.take(6).toList()
            val frames = chain.flatMap { it.stackTrace.toList() }
            val mine = frames.filter { it.className.startsWith("com.tjshea.vigilant") }
            val pick = (if (mine.isNotEmpty()) mine else frames).take(3)
            return pick.joinToString(" < ") { "${it.className}.${it.methodName}(${it.fileName ?: "?"}:${it.lineNumber})" }
        }

        /** The first frame of [where], with its class without the package: "AutoScanner.cycle(AutoScan.kt:231)". */
        fun short(where: String): String = where.substringBefore(" < ").let { f ->
            val call = f.substringBefore('(')
            val cls = call.substringBeforeLast('.').substringAfterLast('.').substringBefore('$')
            "$cls.${call.substringAfterLast('.')}(${f.substringAfter('(')}"
        }

        /** The repo path of the file a frame names: "app/src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt"; null for a frame that isn't the app's. */
        fun pathOf(where: String): String? {
            val f = where.substringBefore(" < ")
            val cls = f.substringBefore('(').substringBeforeLast('.')
            val file = f.substringAfter('(').substringBefore(':').takeIf { it.endsWith(".kt") } ?: return null
            if (!cls.startsWith("com.tjshea.vigilant.")) return null
            val pkg = cls.substringBeforeLast('.').let { p -> if (cls.substringAfterLast('.').first().isUpperCase()) p else cls }
            val module = pkg.removePrefix("com.tjshea.vigilant.").substringBefore('.').takeIf { it in setOf("app", "data", "engine", "mgm") } ?: return null
            return "$module/src/main/kotlin/${pkg.replace('.', '/')}/$file"
        }
    }
}
