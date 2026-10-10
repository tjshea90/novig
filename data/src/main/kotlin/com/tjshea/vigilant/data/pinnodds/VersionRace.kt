package com.tjshea.vigilant.data.pinnodds

/**
 * Times two Pinnacle feeds against each other (Tj, 2026-10-10: how fresh is the free website feed beside the Pinnodds socket?). Each feed reports every market version it takes in for the first time
 * ([PinnBook.versionListener]); when both have reported the same (matchup, market, version) the gap is a sample: positive = the website feed was later. A website poll sees only the LATEST version of a
 * market, so versions the socket saw in between are counted as skipped, not as lag. Thread safe.
 */
class VersionRace(private val clock: () -> Long = System::currentTimeMillis) {
    enum class Source(val tag: Char) { SOCKET('S'), WEBSITE('W') }

    data class Report(val paired: Int, val websiteLater: Int, val websiteFirst: Int, val medianLagMs: Long?, val p90LagMs: Long?, val socketOnly: Int, val websiteOnly: Int, val sinceMs: Long)

    private class First(val source: Source, val atMs: Long)

    private val seen = LinkedHashMap<String, First>()
    private val lags = ArrayDeque<Long>()
    private var socketOnly = 0
    private var websiteOnly = 0
    private var notes = 0L
    private var since = clock()

    @Synchronized
    fun note(source: Source, matchupId: Long, key: String, version: Long, atMs: Long) {
        val k = "$matchupId|$key|$version"
        val first = seen[k]
        if (first == null) {
            seen[k] = First(source, atMs)
        } else if (first.source != source) {
            seen.remove(k)
            val lag = if (source == Source.WEBSITE) atMs - first.atMs else first.atMs - atMs
            lags.addLast(lag)
            while (lags.size > MAX_SAMPLES) lags.removeFirst()
        }
        if (++notes % 200L == 0L) prune(atMs)
    }

    /** Entries one feed never matched, older than [EXPIRE_MS], are counted as that feed's alone. */
    private fun prune(nowMs: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (nowMs - e.value.atMs < EXPIRE_MS) break
            if (e.value.source == Source.SOCKET) socketOnly++ else websiteOnly++
            it.remove()
        }
    }

    @Synchronized
    fun reset() {
        seen.clear(); lags.clear(); socketOnly = 0; websiteOnly = 0; notes = 0; since = clock()
    }

    @Synchronized
    fun report(): Report {
        prune(clock())
        val sorted = lags.sorted()
        return Report(
            paired = sorted.size, websiteLater = sorted.count { it > 0 }, websiteFirst = sorted.count { it < 0 }, medianLagMs = sorted.getOrNull(sorted.size / 2),
            p90LagMs = sorted.getOrNull((0.9 * (sorted.size - 1)).toInt()), socketOnly = socketOnly, websiteOnly = websiteOnly, sinceMs = since,
        )
    }

    /** The race in words for Settings and Diagnostics. */
    fun lines(): List<String> {
        val r = report()
        if (r.paired == 0) return listOf("Feed race: no price version has been seen by both feeds yet (${r.socketOnly} only by the socket, ${r.websiteOnly} only by the website).")
        return listOf(
            "Feed race (website minus socket, ${r.paired} price versions both saw): median ${r.medianLagMs} ms, 90th percentile ${r.p90LagMs} ms · website later on ${r.websiteLater}, first on ${r.websiteFirst}",
            "  the socket also saw ${r.socketOnly} versions the website skipped (it polls: it sees only the latest) and the website ${r.websiteOnly} the socket never reported",
        )
    }

    companion object {
        const val MAX_SAMPLES = 2_000
        const val EXPIRE_MS = 90_000L
    }
}
