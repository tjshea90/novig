package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable

/** One endpoint's figures at a host ("/v1/sports/{sport}/odds": [NetShape]). */
@Serializable
data class PathStat(val calls: Long = 0, val errors: Long = 0, val totalMs: Long = 0, val lastStatus: Int? = null)

/** One hour's calls to one host, for the trend over the last two days. */
@Serializable
data class HourStat(val calls: Int = 0, val errors: Int = 0, val ms: Long = 0, val bytes: Long = 0)

/**
 * Everything the app has learned about one host from its own calls ([NetInterceptor]): how many, how many failed and how, the status codes, how long headers
 * and whole bodies took (the last [NetStats.RECENT] of each), how fast bodies arrived, the busiest endpoints, and the last few hours.
 */
@Serializable
data class HostStat(
    val calls: Long = 0,
    val errors: Long = 0,
    val bytes: Long = 0,
    /** Time to the response headers, summed (mean = this / calls). */
    val totalMs: Long = 0,
    val status: Map<String, Long> = emptyMap(),
    /** Failures that never got a status: "timeout", "dns", "connect", "reset", "tls", "cancelled", "other". */
    val kinds: Map<String, Long> = emptyMap(),
    val recentMs: List<Int> = emptyList(),
    /** Bytes per second of bodies of 8 KB and more (a small body's time is mostly latency). */
    val recentBps: List<Int> = emptyList(),
    val paths: Map<String, PathStat> = emptyMap(),
    val hours: Map<String, HourStat> = emptyMap(),
    /** Calls by network type ("Wi-Fi", "mobile", "other"). */
    val byNet: Map<String, Long> = emptyMap(),
    val lastErrorAtMs: Long? = null,
    val lastError: String? = null,
    /** A 429 or 403 and what the host asked ("Retry-After: 30"). */
    val lastLimitAtMs: Long? = null,
    val lastLimit: String? = null,
    val limits: Long = 0,
) {
    val errorRate: Double get() = if (calls == 0L) 0.0 else errors.toDouble() / calls
    val latency: SampleSummary get() = SampleSummary.of(recentMs.map { it.toDouble() })
    val speed: SampleSummary get() = SampleSummary.of(recentBps.map { it.toDouble() })
}

@Serializable
data class NetBook(val hosts: Map<String, HostStat> = emptyMap(), val sinceMs: Long? = null)

/**
 * Connection speed and API issues, by host (Tj, 2026-10-02: "connection speed and issues, API usage and issues"): one record per call, kept as rolling figures so a
 * call every few seconds costs a few bytes, not a line. Kept across restarts (files/netstats.json, written by [flush]) for [WINDOW_MS], then started again.
 * Nothing in it can hold a key: a host, an endpoint's shape with every id and token masked ([NetShape]), a status, a number.
 */
class NetStats(private val store: JsonFileStore<NetBook>, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private var book = NetBook()
    private var loaded = false
    private var dirty = false
    private var flushedAtMs = 0L

    /**
     * One finished call. [status] null: it failed before a status ([kind] says how). [ttfbMs]: to the headers; [totalMs]: to the end of the body (equal when
     * the call failed); [bytes]: the body as it arrived, or 0.
     */
    fun record(
        host: String, shape: String, status: Int?, kind: String?, ttfbMs: Long, totalMs: Long, bytes: Long, net: String?,
        error: String? = null, limit: String? = null,
    ) {
        val now = clock()
        val failed = status == null || status >= 400
        synchronized(lock) {
            val h = book.hosts[host] ?: HostStat()
            val hour = (now / HOUR_MS).toString()
            val oldHour = h.hours[hour] ?: HourStat()
            val path = h.paths[shape] ?: PathStat()
            val bps = if (bytes >= BPS_MIN_BYTES && totalMs > 0) (bytes * 1000 / totalMs).toInt().coerceAtLeast(1) else null
            val hours = (h.hours + (hour to HourStat(oldHour.calls + 1, oldHour.errors + if (failed) 1 else 0, oldHour.ms + ttfbMs, oldHour.bytes + bytes)))
                .filterKeys { (it.toLongOrNull() ?: 0L) > now / HOUR_MS - HOURS_KEPT }
            val paths = (h.paths + (shape to PathStat(path.calls + 1, path.errors + if (failed) 1 else 0, path.totalMs + ttfbMs, status ?: path.lastStatus)))
                .let { m -> if (m.size > MAX_PATHS) m.entries.sortedByDescending { it.value.calls }.take(MAX_PATHS).associate { it.toPair() } else m }
            val limited = status == 429 || (status == 403 && limit != null)
            book = book.copy(
                sinceMs = book.sinceMs ?: now,
                hosts = book.hosts + (host to h.copy(
                    calls = h.calls + 1,
                    errors = h.errors + if (failed) 1 else 0,
                    bytes = h.bytes + bytes,
                    totalMs = h.totalMs + ttfbMs,
                    status = if (status != null) h.status.merge(status.toString()) else h.status,
                    kinds = if (kind != null) h.kinds.merge(kind) else h.kinds,
                    recentMs = (h.recentMs + ttfbMs.toInt()).takeLast(RECENT),
                    recentBps = if (bps != null) (h.recentBps + bps).takeLast(RECENT_BPS) else h.recentBps,
                    paths = paths,
                    hours = hours,
                    byNet = if (net != null) h.byNet.merge(net) else h.byNet,
                    lastErrorAtMs = if (failed) now else h.lastErrorAtMs,
                    lastError = if (failed) (error ?: kind ?: "HTTP $status").take(120) else h.lastError,
                    lastLimitAtMs = if (limited) now else h.lastLimitAtMs,
                    lastLimit = if (limited) (limit ?: "HTTP $status").take(80) else h.lastLimit,
                    limits = h.limits + if (limited) 1 else 0,
                )),
            )
            dirty = true
        }
    }

    fun snapshot(): NetBook = synchronized(lock) { book }

    suspend fun load() {
        val saved = runCatching { store.read() }.getOrDefault(NetBook())
        synchronized(lock) {
            if (loaded) return
            loaded = true
            // What this run has recorded before the file was read goes on top of it.
            val mine = book
            book = if (saved.sinceMs != null && clock() - saved.sinceMs > WINDOW_MS) mine else merge(saved, mine)
        }
    }

    suspend fun flush(force: Boolean = false) {
        val out = synchronized(lock) {
            if (!dirty || (!force && clock() - flushedAtMs < MIN_FLUSH_GAP_MS)) return
            dirty = false
            flushedAtMs = clock()
            book
        }
        runCatching { store.update { out } }
    }

    private fun Map<String, Long>.merge(key: String): Map<String, Long> = this + (key to ((this[key] ?: 0L) + 1))

    private fun merge(a: NetBook, b: NetBook): NetBook {
        if (b.hosts.isEmpty()) return a
        val hosts = a.hosts.toMutableMap()
        for ((host, x) in b.hosts) {
            val y = hosts[host]
            hosts[host] = if (y == null) x else y.copy(
                calls = y.calls + x.calls, errors = y.errors + x.errors, bytes = y.bytes + x.bytes, totalMs = y.totalMs + x.totalMs,
                status = y.status.sumWith(x.status), kinds = y.kinds.sumWith(x.kinds), byNet = y.byNet.sumWith(x.byNet),
                recentMs = (y.recentMs + x.recentMs).takeLast(RECENT), recentBps = (y.recentBps + x.recentBps).takeLast(RECENT_BPS),
                paths = (y.paths.keys + x.paths.keys).associateWith { k ->
                    val p = y.paths[k] ?: PathStat(); val q = x.paths[k] ?: PathStat()
                    PathStat(p.calls + q.calls, p.errors + q.errors, p.totalMs + q.totalMs, q.lastStatus ?: p.lastStatus)
                },
                hours = (y.hours.keys + x.hours.keys).associateWith { k ->
                    val p = y.hours[k] ?: HourStat(); val q = x.hours[k] ?: HourStat()
                    HourStat(p.calls + q.calls, p.errors + q.errors, p.ms + q.ms, p.bytes + q.bytes)
                },
                lastErrorAtMs = listOfNotNull(y.lastErrorAtMs, x.lastErrorAtMs).maxOrNull(),
                lastError = if ((x.lastErrorAtMs ?: 0L) >= (y.lastErrorAtMs ?: 0L)) x.lastError ?: y.lastError else y.lastError,
                lastLimitAtMs = listOfNotNull(y.lastLimitAtMs, x.lastLimitAtMs).maxOrNull(),
                lastLimit = if ((x.lastLimitAtMs ?: 0L) >= (y.lastLimitAtMs ?: 0L)) x.lastLimit ?: y.lastLimit else y.lastLimit,
                limits = y.limits + x.limits,
            )
        }
        return NetBook(hosts, listOfNotNull(a.sinceMs, b.sinceMs).minOrNull())
    }

    private fun Map<String, Long>.sumWith(o: Map<String, Long>): Map<String, Long> = (keys + o.keys).associateWith { (this[it] ?: 0L) + (o[it] ?: 0L) }

    companion object {
        const val HOUR_MS = 3_600_000L
        const val HOURS_KEPT = 48
        const val RECENT = 100
        const val RECENT_BPS = 50
        const val MAX_PATHS = 14
        const val BPS_MIN_BYTES = 8_192L
        const val MIN_FLUSH_GAP_MS = 30_000L
        const val WINDOW_MS = 14L * 24 * HOUR_MS
    }
}
