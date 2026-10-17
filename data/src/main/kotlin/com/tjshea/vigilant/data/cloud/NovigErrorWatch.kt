package com.tjshea.vigilant.data.cloud

/**
 * Novig answering 5xx (Tj, 2026-10-17, TCL84: "it says novig http 502. any time there is any error like this for any connection or API, see if Claude can figure it out or reset it without delay"). On 2026-10-17
 * 14:17-14:26Z Novig answered 502/504 on every read and the engine simply scanned again on its 30 s cycle with nothing said. Pure: each finished scan says whether Novig's server answered with errors; the watch
 * gives a short retry wait while it does ([backoffMs]: 10 s, doubling to 2 min, so the engine neither waits a whole cycle nor hammers a server that is down), says so to Tj once the outage is [ALERT_AFTER_MS] old
 * ([Event.ALERT]) and once when Novig is back ([Event.RECOVERED]). A server outage is Novig's to fix: this reports it, it does not "fix" it; an address or key refusal (451/401/403) is other code's job ([AddressWatch]).
 */
class NovigErrorWatch(private val alertAfterMs: Long = ALERT_AFTER_MS) {
    enum class Event { ALERT, RECOVERED }

    /** When the current run of failing scans began; 0 = Novig is answering. */
    @Volatile var since = 0L
        private set
    @Volatile var streak = 0
        private set
    @Volatile var last = ""
        private set
    private var alerted = false

    /** [errors] are the scan's own error lines; a scan with none (or none of the 5xx kind) counts as Novig answering. Returns what to tell Tj, if anything. */
    fun observe(errors: List<String>, now: Long): Event? {
        val bad = errors.firstOrNull { SERVER_ERROR.containsMatchIn(it) }
        if (bad == null) {
            val was = alerted
            since = 0L; streak = 0; last = ""; alerted = false
            return if (was) Event.RECOVERED else null
        }
        if (since == 0L) since = now
        streak++
        last = bad.take(160)
        if (!alerted && now - since >= alertAfterMs) { alerted = true; return Event.ALERT }
        return null
    }

    /** True while the last scan failed with a server error. */
    val active: Boolean get() = since != 0L

    /** The wait before the next scan while Novig is failing: 10 s, 20 s, 40 s ... at most 2 min. */
    fun backoffMs(): Long = (BASE_MS shl (streak - 1).coerceIn(0, 4)).coerceAtMost(MAX_MS)

    fun outageMs(now: Long): Long = if (since == 0L) 0L else now - since

    companion object {
        const val ALERT_AFTER_MS = 10 * 60_000L
        const val BASE_MS = 10_000L
        const val MAX_MS = 120_000L
        private val SERVER_ERROR = Regex("""(?i)\bHTTP 5\d\d\b|\b50[0-4]\b.*(gateway|unavailable|timeout|timed out)|bad gateway|gateway time-?out|service unavailable""")
    }
}
