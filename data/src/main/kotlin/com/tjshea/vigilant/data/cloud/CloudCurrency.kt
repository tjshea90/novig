package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.Opportunity

/**
 * How current a list is, measured and not claimed (Tj, 2026-10-14: "some type of confirmation that the scan is current and the data and bets in the list are current ... it must be accurate"). Each number is worked out
 * from the rows' own timestamps at the moment the list was published, so it can only be as good as those stamps: the Novig side is when the row's order book was read from Novig, the fair side is when the OLDEST book
 * price behind the row's fair line was last seen by its feed (the same stamp the freshness rules judge by).
 */
object CloudCurrency {
    data class Ages(val novigMedianS: Long, val novigMaxS: Long, val fairMedianS: Long, val fairMaxS: Long)

    val UNKNOWN = Ages(-1, -1, -1, -1)

    fun of(rows: List<Opportunity>, atMs: Long): Ages {
        fun stat(stamps: List<Long>): Pair<Long, Long> {
            if (stamps.isEmpty()) return -1L to -1L
            val ages = stamps.map { ((atMs - it) / 1000L).coerceAtLeast(0L) }.sorted()
            return ages[ages.size / 2] to ages.last()
        }
        val (nm, nx) = stat(rows.mapNotNull { it.bookFetchedAtMs })
        val (fm, fx) = stat(rows.mapNotNull { it.fairAsOfMs })
        return Ages(nm, nx, fm, fx)
    }
}

/**
 * What the Feed says about the cloud scan, from the engine's heartbeat and the phone's own record of what it asked (pure, so every case is a test). [now] is the phone's clock; every age is shown as of now (the
 * published age plus the time since the list went out).
 */
object CloudText {
    enum class Level { GOOD, INFO, WARN, BAD }

    data class View(val level: Level, val headline: String, val detail: String?, /** The scan the phone asked for is running or waiting: the Scan button shows busy. */ val busy: Boolean)

    fun dur(s: Long): String = if (s < 90) "${s}s" else if (s < 3600) "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s" else "${s / 3600}h ${(s % 3600) / 60}m"

    fun view(st: CloudStatus?, requestedAtMs: Long, now: Long, why: String? = null): View {
        if (st == null) return View(if (why != null) Level.BAD else Level.INFO, why?.let { "Cloud scan: $it" } ?: "Cloud scan: waiting for the GitHub engine to answer", null, requestedAtMs > 0L)
        val beat = ((now - st.heartbeatMs) / 1000L).coerceAtLeast(0L)
        if (st.idle) return View(Level.WARN, "Cloud scan: the GitHub engine is switched off", "Turn Scan on GitHub on again (Settings › Cloud scan).", false)
        if (beat > 120L) return View(Level.BAD, "Cloud scan: the engine has been silent for ${dur(beat)}", "The list below is the last one it published. Tap Scan to ask GitHub to start it again.", requestedAtMs > 0L && st.lastScanForRefreshAtMs < requestedAtMs)
        val listAge = if (st.computedAtMs > 0L) ((now - st.computedAtMs) / 1000L).coerceAtLeast(0L) else -1L
        val pending = requestedAtMs > 0L && st.lastScanForRefreshAtMs < requestedAtMs
        val pct = if (st.progressTotal > 0) " ${st.progressDone}/${st.progressTotal} (${(100 * st.progressDone / st.progressTotal).coerceIn(0, 100)}%)" else ""
        val step = st.progressStep.ifBlank { "starting" }
        val headline = when {
            pending && st.ackedRefreshAtMs < requestedAtMs -> {
                val sent = ((now - requestedAtMs) / 1000L).coerceAtLeast(0L)
                "Scan requested ${dur(sent)} ago; waiting for the engine to pick it up"
            }
            pending -> "Scanning on GitHub: $step$pct, ${dur(((now - st.scanStartedAtMs) / 1000L).coerceAtLeast(0L))} so far (your request was acknowledged)"
            st.scanning -> "GitHub is scanning again: $step$pct, ${dur(((now - st.scanStartedAtMs) / 1000L).coerceAtLeast(0L))} so far"
            st.lastScanEndedAtMs > 0L -> "Last scan finished ${dur(((now - st.lastScanEndedAtMs) / 1000L).coerceAtLeast(0L))} ago and took ${dur((st.lastScanEndedAtMs - st.lastScanStartedAtMs) / 1000L)}"
            else -> "GitHub is on its first scan: $step$pct"
        }
        val sinceList = if (st.computedAtMs > 0L) (now - st.computedAtMs) / 1000L else 0L
        val detail = if (listAge < 0) "No list published yet." else buildString {
            append("List published ${dur(listAge)} ago, ${st.rows} bets")
            if (st.novigAgeMedianS >= 0) append(" · Novig prices: median ${dur(st.novigAgeMedianS + sinceList)}, oldest ${dur(st.novigAgeMaxS + sinceList)}")
            if (st.fairAgeMedianS >= 0) append(" · fair odds: median ${dur(st.fairAgeMedianS + sinceList)}, oldest ${dur(st.fairAgeMaxS + sinceList)}")
            append(" (the phone re-reads each listed bet's Novig price every 15 s, and the Bet sheet again when you tap).")
        }
        val level = when {
            listAge < 0 -> Level.INFO
            pending || st.scanning -> if (listAge <= 90L) Level.INFO else Level.WARN
            listAge <= 90L && (st.novigAgeMedianS < 0 || st.novigAgeMedianS + sinceList <= 180L) -> Level.GOOD
            else -> Level.WARN
        }
        return View(level, headline, detail, pending)
    }
}
