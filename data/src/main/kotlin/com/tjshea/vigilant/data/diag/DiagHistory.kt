package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * What one diagnostics file said, in numbers and finding keys, kept so the next one can be compared with it (Tj, 2026-10-02: "a smart diagnostics feature that can
 * improve the app with every upload to Claude"): [metrics] by name (error rates, latencies, counts), [findings] key → kind ("BUG", "FAILURE", "OPTIMIZE"…).
 */
@Serializable
data class Snap(val atMs: Long, val version: String, val code: Int, val metrics: Map<String, Double> = emptyMap(), val findings: Map<String, String> = emptyMap())

@Serializable
data class DiagBook(val snaps: List<Snap> = emptyList())

/** The last [KEEP] files' snapshots (files/diag_history.json). */
class DiagHistory(private val store: JsonFileStore<DiagBook>) {
    suspend fun all(): List<Snap> = runCatching { store.read().snaps }.getOrDefault(emptyList())

    /** Adds [snap] (the report just made) after the ones before it. */
    suspend fun add(snap: Snap) {
        runCatching { store.update { DiagBook((it.snaps + snap).takeLast(KEEP)) } }
    }

    companion object {
        const val KEEP = 12
    }
}

/**
 * The difference between this report and the last one: findings that are new, ones that went away (fixed, or no longer happening) and numbers that moved a
 * meaningful amount, so whoever reads the file sees whether the last change worked. Pure.
 */
object Trend {

    /** Metrics whose rise is bad, and the smallest relative change worth a line. */
    private const val MIN_RELATIVE = 0.25

    /** Lines for the report ([ago]: a time as a phrase, "3d ago"); a first report has nothing to compare with. */
    fun lines(prev: Snap?, now: Snap, ago: (Long) -> String): List<String> {
        if (prev == null) return listOf("This is the first report saved on this phone: nothing earlier to compare with.")
        val out = ArrayList<String>()
        out += "Previous report: ${ago(prev.atMs)}, version ${prev.version} (code ${prev.code}); this one is version ${now.version} (code ${now.code})" +
            if (prev.code != now.code) ": THE APP WAS UPDATED SINCE, so changes below may be the update's." else "."
        val resolved = prev.findings.keys - now.findings.keys
        val fresh = now.findings.keys - prev.findings.keys
        val worse = now.findings.filter { (k, v) -> prev.findings[k]?.let { rank(v) > rank(it) } == true }
        if (resolved.isNotEmpty()) out += "RESOLVED since then (${resolved.size}): " + resolved.sorted().joinToString("; ") { "$it [was ${prev.findings[it]}]" }
        if (fresh.isNotEmpty()) out += "NEW since then (${fresh.size}): " + fresh.sorted().joinToString("; ") { "$it [${now.findings[it]}]" }
        if (worse.isNotEmpty()) out += "WORSE since then: " + worse.keys.sorted().joinToString("; ") { "$it [${prev.findings[it]} → ${now.findings[it]}]" }
        val still = now.findings.keys.intersect(prev.findings.keys) - worse.keys
        if (still.isNotEmpty()) out += "STILL there (${still.size}): " + still.sorted().joinToString("; ")
        val moved = (prev.metrics.keys intersect now.metrics.keys).mapNotNull { k ->
            val a = prev.metrics.getValue(k); val b = now.metrics.getValue(k)
            val base = maxOf(Math.abs(a), 1e-9)
            val rel = (b - a) / base
            if (Math.abs(rel) >= MIN_RELATIVE && Math.abs(b - a) >= MIN_ABSOLUTE) Triple(k, a, b) else null
        }.sortedByDescending { (_, a, b) -> Math.abs(b - a) / maxOf(Math.abs(a), 1e-9) }.take(MAX_MOVED)
        if (moved.isNotEmpty()) out += "Numbers that moved: " + moved.joinToString("; ") { (k, a, b) -> "$k ${fmt(a)} → ${fmt(b)}" }
        if (resolved.isEmpty() && fresh.isEmpty() && worse.isEmpty() && moved.isEmpty()) out += "Nothing changed enough to report."
        return out
    }

    /** A kind's weight: worse kinds rank higher. */
    fun rank(kind: String): Int = KINDS.indexOf(kind).let { if (it < 0) 0 else KINDS.size - it }

    val KINDS = listOf("BUG", "FAILURE", "OPTIMIZE", "IMPROVE", "WATCH")

    private const val MIN_ABSOLUTE = 0.5
    private const val MAX_MOVED = 12

    private fun fmt(v: Double) = if (v == Math.rint(v) && Math.abs(v) < 1e9) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.2f", v)
}
