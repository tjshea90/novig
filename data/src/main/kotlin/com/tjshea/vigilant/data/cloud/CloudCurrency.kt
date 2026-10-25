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
    /** The engine's real-time list ran within the last 20 s (PB15/PB19): it re-prices the listed bets itself, so the phone's own 15 s re-read is not needed. */
    fun realtime(st: CloudStatus?, now: Long): Boolean = st != null && st.realtimeAtMs > 0L && now - st.realtimeAtMs < 20_000L

    enum class Level { GOOD, INFO, WARN, BAD }

    data class View(val level: Level, val headline: String, val detail: String?, /** The scan the phone asked for is running or waiting: the Scan button shows busy. */ val busy: Boolean)

    fun dur(s: Long): String = if (s < 90) "${s}s" else if (s < 3600) "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s" else "${s / 3600}h ${(s % 3600) / 60}m"

    fun view(st: CloudStatus?, requestedAtMs: Long, now: Long, why: String? = null): View {
        if (st == null) return View(if (why != null) Level.BAD else Level.INFO, why?.let { "Cloud scan: $it" } ?: "Cloud scan: waiting for the GitHub engine to answer", null, requestedAtMs > 0L)
        val beat = ((now - st.heartbeatMs) / 1000L).coerceAtLeast(0L)
        if (st.idle) return View(Level.WARN, "Cloud scan: the GitHub engine is stopped or restarting", "Its last word was \"idle\": the switch is off, it is outside its hours, or a new run is starting (a restart takes up to a few minutes). If your switch is on, it starts by itself; Settings › Cloud scan › Start the engine now asks for it.", false)
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
            // PB15: with the engine's real-time list running, the engine itself re-prices every listed bet every few seconds from the live feeds and Novig's socket; the phone does not.
            if (realtime(st, now)) append(" · the engine re-prices the ${st.realtimeWatched} bets in reach of +EV every ${RealtimeList.EVERY_MS / 1000} s from the live feeds and Novig's socket")
            else append(" · this phone re-reads the listed bets' Novig prices every 15 s")
        }
        val level = when {
            listAge < 0 -> Level.INFO
            realtime(st, now) && listAge <= 90L -> Level.GOOD
            pending || st.scanning -> if (listAge <= 90L) Level.INFO else Level.WARN
            listAge <= 90L && (st.novigAgeMedianS < 0 || st.novigAgeMedianS + sinceList <= 180L) -> Level.GOOD
            else -> Level.WARN
        }
        return View(level, headline, detail, pending)
    }

    /**
     * Whether the GitHub engine has read the phone's latest settings, said plainly (Tj, 2026-10-15: "make sure the settings are properly mirrored"). [pushedRev] and [pushedAtMs] are the newest settings the phone
     * wrote; the engine reports the newest it has read ([CloudStatus.configRev]) and whether the scan in progress still runs under older ones ([CloudStatus.settingsPending], applied at its next scan).
     */
    fun settings(st: CloudStatus?, pushedRev: Long, pushedAtMs: Long, now: Long): Pair<Level, String>? {
        if (pushedRev <= 0L) return null
        if (st == null) return Level.INFO to "Settings sent; waiting for the engine to read them"
        val sent = ((now - pushedAtMs) / 1000L).coerceAtLeast(0L)
        return when {
            st.configRev < pushedRev && sent < 45L -> Level.INFO to "Settings on their way to GitHub (sent ${dur(sent)} ago)"
            st.configRev < pushedRev -> Level.WARN to "GitHub has not read your latest settings after ${dur(sent)} (a running engine reads them every 3 s)"
            st.settingsPending -> Level.WARN to "GitHub applies your latest settings from its next scan (the one running began before the change)"
            else -> Level.GOOD to "GitHub is using your current settings"
        }
    }
}

/**
 * The one-glance freshness of the list on screen (Tj, 2026-10-15: "a mechanism by which I can quickly see the freshness of the odds"), worked out from the bets actually SHOWN, never from a claim: how long ago each
 * one's Novig order book was read ([Opportunity.bookFetchedAtMs]: the phone re-reads the listed bets' Novig books every 15 s, so this is the phone's own read when it has made one) and how old the books behind each fair
 * line are ([Opportunity.fairAsOfMs]: the oldest book price in it, as its feed last saw it). Medians say what is typical, the oldest says what to distrust.
 */
object CloudFreshness {
    data class Chip(val level: CloudText.Level, val label: String, val detail: String)

    private fun median(l: List<Long>) = l.sorted()[l.size / 2]

    fun of(rows: List<Opportunity>, st: CloudStatus?, now: Long, why: String? = null): Chip {
        val beat = st?.let { ((now - it.heartbeatMs) / 1000L).coerceAtLeast(0L) }
        val listAge = st?.takeIf { it.computedAtMs > 0L }?.let { ((now - it.computedAtMs) / 1000L).coerceAtLeast(0L) }
        val engine = when {
            st == null -> why ?: "the engine has not answered yet"
            st.idle -> "the engine is stopped or restarting"
            beat!! > 120L -> "the engine has been silent for ${CloudText.dur(beat)}"
            else -> "the engine reported ${CloudText.dur(beat)} ago"
        } + (listAge?.let { "; its last list went out ${CloudText.dur(it)} ago" } ?: "")
        val novig = rows.mapNotNull { it.bookFetchedAtMs }.map { ((now - it) / 1000L).coerceAtLeast(0L) }
        val fair = rows.mapNotNull { it.fairAsOfMs }.map { ((now - it) / 1000L).coerceAtLeast(0L) }
        val dead = st == null || st.idle || beat!! > 120L
        if (novig.isEmpty()) return Chip(if (dead) CloudText.Level.BAD else CloudText.Level.INFO, if (dead) "GitHub: ${engine.substringBefore(';')}" else "GitHub: no bets listed", "No bet is shown, so there is no price to date. $engine.")
        val nm = median(novig); val nx = novig.max()
        val fm = if (fair.isEmpty()) null else median(fair)
        val level = when {
            dead || nx > 600L -> CloudText.Level.BAD
            nm > 90L || nx > 300L || (fm != null && fm > 600L) -> CloudText.Level.WARN
            else -> CloudText.Level.GOOD
        }
        // The games that matter for betting now (Tj, 2026-10-15: "is this speed too slow for pre game betting"): the engine keeps the ones starting within [SOON_HOURS] hours young while it scans, so they are told apart.
        val soon = rows.filter { it.event.startsTs - now in 1L..SOON_HOURS * 3_600_000L }
        val soonFair = soon.mapNotNull { it.fairAsOfMs }.map { ((now - it) / 1000L).coerceAtLeast(0L) }
        val soonNovig = soon.mapNotNull { it.bookFetchedAtMs }.map { ((now - it) / 1000L).coerceAtLeast(0L) }
        val label = "Novig ${CloudText.dur(nm)} (oldest ${CloudText.dur(nx)})" + (fm?.let { " · fair ${CloudText.dur(it)}" } ?: "") +
            (if (soonFair.isNotEmpty() && soon.size < rows.size) " · games within ${SOON_HOURS}h: fair ${CloudText.dur(median(soonFair))}" else "")
        val detail = "Across the ${rows.size} bets shown: Novig prices were read a median ${CloudText.dur(nm)} ago (the oldest ${CloudText.dur(nx)}); " +
            (fm?.let { "the oldest book price behind each fair line is a median ${CloudText.dur(it)} old (the oldest ${CloudText.dur(fair.max())}). " } ?: "") +
            (if (soon.isNotEmpty() && soon.size < rows.size && soonNovig.isNotEmpty()) {
                "The ${soon.size} starting within ${SOON_HOURS} h: Novig a median ${CloudText.dur(median(soonNovig))} ago" +
                    (if (soonFair.isNotEmpty()) ", fair odds ${CloudText.dur(median(soonFair))} (the oldest ${CloudText.dur(soonFair.max())}). " else ". ")
            } else "") +
            "$engine."
        return Chip(level, label, detail)
    }

    /** Games starting within this many hours are the ones the engine refreshes during its long scan ([com.tjshea.vigilant.data.scanner.ScanGuards.hotHours]). */
    const val SOON_HOURS = 12L
}
