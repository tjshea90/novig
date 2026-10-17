package com.tjshea.vigilant.data.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * What the in-app dashboard shows (Tj, 2026-10-15: "put the dashboard inside the vigilant android app with easy to see organized information and meters/graphs optimized for mobile phone viewing which is
 * synced to the GitHub engine"). Pure: the engine's [CloudStatus] (the phone already polls it every few seconds) in, words, tones and numbers out; the screen only draws them, so every rule here is tested
 * (`DashboardModelTest`) and the same words are on the GitHub page ([CloudDashboard]).
 */
object DashboardModel {
    enum class Tone { GOOD, WARN, BAD, NEUTRAL }

    /** The one big line at the top: what the engine is doing and whether to trust the list, with the reason. */
    data class Headline(val title: String, val tone: Tone, val detail: String)

    /** One tile of the grid: a label, the figure, a small line under it. */
    data class Tile(val label: String, val value: String, val sub: String = "", val tone: Tone = Tone.NEUTRAL)

    /** The engine is called not responding when its heartbeat is older than this (it beats every few seconds, even in the middle of a scan). */
    const val STALE_MS = 3 * 60_000L

    /** The engine is running now: its heartbeat is recent and its last word was not "idle". Everything else on the page is then the LAST KNOWN reading, and says so (Tj, 2026-10-16: "make sure this page shows true readings on everything"). */
    fun running(st: CloudStatus?, now: Long): Boolean = st != null && st.heartbeatMs > 0 && now - st.heartbeatMs < STALE_MS && !st.idle

    /** Why the engine is not running, in words, for the headline and the tiles; null while it is running. [hoursOn]: the phone's "only 8 am to 1 am Eastern" switch. */
    fun notRunningWhy(st: CloudStatus?, now: Long, hoursOn: Boolean = true): String? {
        if (st == null || running(st, now)) return null
        val beat = if (st.heartbeatMs > 0) now - st.heartbeatMs else Long.MAX_VALUE
        return when {
            hoursOn && !EngineHours.isOpen(now) -> "outside its hours (8 am to 1 am Eastern); it starts again at 8 am"
            st.idle && beat < STALE_MS -> st.problem ?: "switched off on the phone"
            else -> "no heartbeat for ${ago(beat).removeSuffix(" ago")}"
        }
    }

    fun headline(st: CloudStatus?, now: Long, hoursOn: Boolean = true): Headline {
        if (st == null) return Headline("Not connected", Tone.NEUTRAL, "No word from the GitHub engine yet. Save the GitHub token in Settings › Cloud scan, and switch the cloud scan on.")
        val beat = if (st.heartbeatMs > 0) now - st.heartbeatMs else Long.MAX_VALUE
        return when {
            // Closed hours are not a failure: the engine ends itself at the window's end and the next one starts at 8 am.
            hoursOn && !running(st, now) && !EngineHours.isOpen(now) -> Headline("Idle: outside its hours", Tone.NEUTRAL, "The engine runs 8 am to 1 am Eastern and stopped ${ago(beat)}. What is shown below is its last reading, not live.")
            st.idle && beat < STALE_MS -> Headline("Idle", Tone.NEUTRAL, st.problem ?: "The engine is switched off (the phone's switch) or outside its hours (8 am to 1 am Eastern).")
            beat >= STALE_MS -> Headline("Not responding", Tone.BAD, "The engine's last heartbeat was ${ago(beat)}. A new one is started within 15 minutes while the cloud scan is on and it is 8 am to 1 am Eastern.")
            st.problem != null -> Headline("Problem", Tone.BAD, st.problem)
            st.tradeStopped.isNotBlank() -> Headline("Trading stopped", Tone.WARN, st.tradeStopped)
            st.walletSleep.isNotBlank() -> Headline("Sleeping: wallet empty", Tone.WARN, st.walletSleep)
            st.scanning -> Headline("Scanning", Tone.GOOD, st.progressStep.ifBlank { "starting" } + if (st.progressTotal > 0) " (${st.progressDone} of ${st.progressTotal})" else "")
            else -> Headline("Running", Tone.GOOD, if (st.lastScanEndedAtMs > 0) "last scan ended ${ago(now - st.lastScanEndedAtMs)}" else "waiting for the first scan")
        }
    }

    /** 0..1 progress of the scan in flight, null when none or its size is not known yet. */
    fun scanProgress(st: CloudStatus?): Float? = st?.takeIf { it.scanning && it.progressTotal > 0 }?.let { (it.progressDone.toFloat() / it.progressTotal).coerceIn(0f, 1f) }

    /** The +EV count out of the scan note ("EV spread of 16792 priced lines: +EV 230 · ..."), null when the note does not say. */
    fun posEv(scanNote: String): Int? = Regex("""\+EV (\d+)""").find(scanNote)?.groupValues?.get(1)?.toIntOrNull()

    /** Priced lines out of the same note. */
    fun priced(scanNote: String): Int? = Regex("""EV spread of (\d+) priced""").find(scanNote)?.groupValues?.get(1)?.toIntOrNull()

    fun tiles(st: CloudStatus?, now: Long, hoursOn: Boolean = true): List<Tile> {
        if (st == null) return emptyList()
        val why = notRunningWhy(st, now, hoursOn)
        if (why != null) return lastKnown(st, now, why)
        val ev = posEv(st.scanNote)
        val ageTone = when {
            st.fairAgeMedianS < 0 -> Tone.NEUTRAL
            st.fairAgeMedianS > 900 -> Tone.BAD
            st.fairAgeMedianS > 300 -> Tone.WARN
            else -> Tone.GOOD
        }
        return listOf(
            Tile("Bets listed", "${st.rows}", ev?.let { "$it +EV lines priced" } ?: "", if (st.rows > 0) Tone.GOOD else Tone.NEUTRAL),
            Tile("Last scan", if (st.lastScanEndedAtMs > 0) "${st.scanMs / 1000} s" else "none yet", if (st.lastScanEndedAtMs > 0) ago(now - st.lastScanEndedAtMs) else "", Tone.NEUTRAL),
            Tile("Odds age", if (st.fairAgeMedianS >= 0) age(st.fairAgeMedianS) else "n/a", "fair odds (median); Novig prices " + if (st.novigAgeMedianS >= 0) age(st.novigAgeMedianS) else "n/a", ageTone),
            Tile("Wallet", cash(st), walletSub(st, now), if (st.walletReadAtMs > 0 && st.walletCash >= 0.5) Tone.GOOD else if (st.walletReadAtMs > 0) Tone.WARN else Tone.NEUTRAL),
            Tile("Trading", st.tradeMode.ifBlank { "off" }.uppercase(), tradeSub(st), if (st.tradeStopped.isNotBlank()) Tone.WARN else if (st.tradeMode.equals("live", true)) Tone.GOOD else Tone.NEUTRAL),
            addressTile(st),
        )
    }

    /**
     * Whether Novig accepts THIS runner's address NOW (Tj, 2026-10-17: the tile said "accepted" while the Novig line under it said RESTRICTED_NETWORK_REGION, because it read the count of past swaps). Read from the
     * engine's own verdict ([CloudStatus.novigAddress]), or from the refusal code in the Novig line for an engine that predates it; a count of swaps only adds to the words.
     */
    fun addressTile(st: CloudStatus): Tile {
        val said = st.novigAddress
        val refused = said.startsWith("REFUSED") || (said.isBlank() && AddressWatch.ADDRESS_CODES.any { st.novigRoute.contains(it) })
        val swaps = if (st.addressHandovers > 0) " · ${st.addressHandovers} swap${if (st.addressHandovers == 1) "" else "s"} tried" else ""
        return when {
            refused -> Tile("Address", "refused", "Novig refuses this runner; a new one is drawn$swaps", Tone.BAD)
            said.startsWith("not tested") -> Tile("Address", "untested", "no Novig key on GitHub", Tone.NEUTRAL)
            else -> Tile("Address", "accepted", "Novig takes this runner$swaps", Tone.GOOD)
        }
    }

    /** The tiles of an engine that is not running: every figure is its last reading with its age, in grey, and the two that describe the LIVE state (trading, address) say they are not happening now. */
    private fun lastKnown(st: CloudStatus, now: Long, why: String): List<Tile> {
        val at = ago(now - st.heartbeatMs)
        val mode = st.tradeMode.ifBlank { "off" }
        return listOf(
            Tile("Bets listed", "${st.rows}", "last list, not live (engine's last word $at)"),
            Tile("Last scan", if (st.lastScanEndedAtMs > 0) "${st.scanMs / 1000} s" else "none yet", if (st.lastScanEndedAtMs > 0) ago(now - st.lastScanEndedAtMs) else "no scan finished in the last run"),
            Tile("Odds age", if (st.fairAgeMedianS >= 0) age(st.fairAgeMedianS) else "n/a", "when the engine last ran; not ageing live"),
            Tile("Wallet", cash(st), walletSub(st, now).let { if (st.walletReadAtMs > 0) "$it (last read)" else it }),
            Tile("Trading", "not trading", "engine $why; set to ${mode.uppercase()} for when it runs"),
            Tile("Address", "not checked", "engine not running" + if (st.addressHandovers > 0) " (last run: refused ${st.addressHandovers}x)" else ""),
        )
    }

    private fun cash(st: CloudStatus) = if (st.walletReadAtMs <= 0L || st.walletCash < 0) "not read" else String.format(java.util.Locale.US, "$%.2f", st.walletCash)
    private fun walletSub(st: CloudStatus, now: Long) = when {
        st.walletSleep.isNotBlank() -> "asleep, under \$0.50"
        st.walletReadAtMs > 0 -> "read ${ago(now - st.walletReadAtMs)}" + if (st.walletEquity > st.walletCash + 0.005) String.format(java.util.Locale.US, ", equity $%.2f", st.walletEquity) else ""
        else -> "not zero, just not read yet"
    }
    private fun tradeSub(st: CloudStatus) = when {
        st.tradeStopped.isNotBlank() -> "stopped"
        st.tradeNote.isBlank() -> ""
        else -> st.tradeNote.substringAfter(": ", st.tradeNote).substringBefore(";").take(60)
    }

    /** How a provider did on the last scan (`CloudDashboard.health` over its feeds), by the provider id of its meter. */
    fun apiHealth(st: CloudStatus?): Map<String, CloudDashboard.Health> {
        if (st == null || st.feedReportAtMs <= 0L) return emptyMap()
        return st.feedReport.groupBy { CloudDashboard.providerOf(it.id) }.mapValues { (_, feeds) -> CloudDashboard.health(feeds, true) }
    }

    /** The feeds of a provider as "sgo 8+0 · sgo-props 6+0" plus its first error or hold-back, for the API card's second line. */
    fun apiDetail(st: CloudStatus?, providerId: String): String {
        val feeds = st?.feedReport.orEmpty().filter { CloudDashboard.providerOf(it.id) == providerId }
        if (feeds.isEmpty()) return ""
        return feeds.joinToString(" · ") { "${it.id} ${it.fetched}+${it.reused}" } + (feeds.firstNotNullOfOrNull { it.error ?: it.heldBack }?.let { " — ${it.take(70)}" } ?: "")
    }

    // ---------------------------------------------------------------- history (the sparklines)

    /** One finished scan as the graphs keep it. [at] is the scan's end (the engine's clock). */
    @Serializable
    data class Point(val at: Long, val scanS: Int, val rows: Int, val posEv: Int, val novigAgeS: Int, val fairAgeS: Int, val cash: Double = -1.0)

    /** Graphs keep the last day or so: a scan ends every 5 to 6 minutes. */
    const val MAX_POINTS = 288

    /** [prev] with one point added for the scan [st] reports, when it is a scan not yet in the history; otherwise [prev] itself. */
    fun record(prev: List<Point>, st: CloudStatus): List<Point> {
        if (st.lastScanEndedAtMs <= 0L || st.lastScanEndedAtMs <= (prev.lastOrNull()?.at ?: 0L)) return prev
        val p = Point(st.lastScanEndedAtMs, (st.scanMs / 1000).toInt(), st.rows, posEv(st.scanNote) ?: -1, st.novigAgeMedianS.toInt(), st.fairAgeMedianS.toInt(), if (st.walletReadAtMs > 0) st.walletCash else -1.0)
        return (prev + p).takeLast(MAX_POINTS)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(Point.serializer())
    fun encode(points: List<Point>): String = json.encodeToString(ser, points)
    fun decode(text: String): List<Point> = runCatching { json.decodeFromString(ser, text) }.getOrDefault(emptyList())

    // ---------------------------------------------------------------- words

    fun age(s: Long): String = when {
        s < 120 -> "$s s"
        s < 7200 -> "${s / 60} min"
        else -> "${s / 3600} h"
    }

    fun ago(ms: Long): String = if (ms < 0) "just now" else "${age(ms / 1000)} ago"
}
