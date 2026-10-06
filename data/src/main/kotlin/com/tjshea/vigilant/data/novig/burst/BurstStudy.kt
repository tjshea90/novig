package com.tjshea.vigilant.data.novig.burst

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** The paper trade of one window at one delay, with and without Tj's own per-bet limit. */
@Serializable
data class ProfileResult(
    val profile: String,
    val delayMs: Long,
    val outcome: String,
    val contracts: Long,
    val pnl: Double,
    val cappedContracts: Long,
    val cappedPnl: Double,
)

/** What the journal holds: a window that paid, and what each game was watched for (so "per game" and "per hour" mean something). */
@Serializable
sealed interface BurstLine

/** One window of a cover paying, seen on the phone (times are its clock when the push arrived), with its paper trades. */
@Serializable
@SerialName("w")
data class WindowRecord(
    val league: String,
    val eventId: String,
    val event: String,
    val pair: String,
    val openedMs: Long,
    val durationMs: Long,
    val yesAsk: Double,
    val noAsk: Double,
    val contracts: Long,
    val net: Double,
    val peakNet: Double,
    val peakContracts: Long,
    val updates: Int,
    val results: List<ProfileResult>,
) : BurstLine

/** A game the recorder watched: from [fromMs] to [toMs] with [lines] ladder lines subscribed and [updates] book pushes seen. */
@Serializable
@SerialName("g")
data class GameRecord(
    val league: String,
    val eventId: String,
    val event: String,
    val fromMs: Long,
    val toMs: Long,
    val lines: Int,
    val updates: Long,
) : BurstLine

/** The journal: one file a day (Eastern), one JSON line per record, only ever appended to (the scan study's way: nothing is rewritten, nothing deleted). */
class BurstJournal(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "k" }

    fun file(day: LocalDate) = File(dir, "$PREFIX$day$SUFFIX")

    fun days(): List<LocalDate> = dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
        ?.mapNotNull { f -> runCatching { LocalDate.parse(f.name.removePrefix(PREFIX).removeSuffix(SUFFIX)) }.getOrNull() }?.sorted().orEmpty()

    @Synchronized
    fun append(atMs: Long, lines: List<BurstLine>) {
        if (lines.isEmpty()) return
        dir.mkdirs()
        val f = file(dayOf(atMs))
        val text = StringBuilder()
        if (f.exists() && f.length() > 0 && !endsWithNewline(f)) text.append('\n')
        for (l in lines) text.append(json.encodeToString(BurstLine.serializer(), l)).append('\n')
        FileOutputStream(f, true).use { out -> out.write(text.toString().toByteArray()); out.fd.sync() }
    }

    /** Every record, oldest day first; a line that can't be read is skipped. */
    fun readAll(): List<BurstLine> = days().flatMap { d ->
        file(d).takeIf { it.exists() }?.useLines { ls -> ls.filter { it.isNotBlank() }.mapNotNull { runCatching { json.decodeFromString(BurstLine.serializer(), it) }.getOrNull() }.toList() }.orEmpty()
    }

    private fun endsWithNewline(f: File): Boolean = java.io.RandomAccessFile(f, "r").use { r -> r.seek(r.length() - 1); r.read() == '\n'.code }

    companion object {
        private const val PREFIX = "burst-"
        private const val SUFFIX = ".jsonl"
        private val ET: ZoneId = ZoneId.of("America/New_York")
        fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ET).toLocalDate()
    }
}

/** One delay's totals. */
data class ProfileTotals(
    val profile: String,
    val delayMs: Long,
    val windows: Int,
    val both: Int,
    val oneLeg: Int,
    val missed: Int,
    val pnl: Double,
    val cappedPnl: Double,
    /** Games where the capped paper P&L at this delay was above zero. */
    val gamesUp: Int,
)

data class LeagueTotals(val league: String, val games: Int, val minutes: Double, val windows: Int, val medianMs: Long, val p90Ms: Long, val profiles: List<ProfileTotals>)

enum class BurstVerdict(val label: String) {
    NEEDS_DATA("not enough yet"),
    NOT_CATCHABLE("no: the windows close before an order of yours would arrive, or the paper result is not positive"),
    TOO_SMALL("positive on paper, but too small to matter at your limits"),
    WORTH_A_TEST("positive on paper at your delays: worth a $1 test, which needs your say-so"),
}

/**
 * What the journal says about score bursts on THIS phone with THIS key (RESEARCH.md §95): per league and overall, how many windows, how long, and what a taker with
 * Tj's measured delays would have made on paper. [verdict] is the plain answer, and [PROVES] says what it cannot prove.
 */
object BurstStudy {
    /** Fewest games and windows before a verdict other than "not enough yet". */
    const val MIN_GAMES = 3
    const val MIN_WINDOWS = 10

    /** At the slow delay: the share of windows both legs still filled, and of games the paper result was above zero, that count as catchable. */
    const val CATCH_SHARE = 0.25
    const val GAMES_UP_SHARE = 0.70

    /** A paper profit under this a game, at Tj's limits, is not worth a live test (the work, the 451s, a phone). */
    const val MIN_DOLLARS_PER_GAME = 0.50

    const val PROVES = "A paper result: it proves what a cover looks like on this phone, this key and this network, and how long it lasts against YOUR measured delays. " +
        "It cannot prove a profit: no order was sent, so it cannot know a rival's speed (a faster taker may already have the cover), what Novig does with an order that arrives " +
        "late (price band, in-play delay, NOT_LIVE_TRADABLE), or that both legs fill. Only a real $1 test can; the verdict says whether one is worth running."

    fun summarize(records: List<BurstLine>): List<LeagueTotals> {
        val windows = records.filterIsInstance<WindowRecord>()
        val games = records.filterIsInstance<GameRecord>()
        val leagues = (windows.map { it.league } + games.map { it.league }).distinct().sorted()
        return leagues.map { lg -> league(lg, windows.filter { it.league == lg }, games.filter { it.league == lg }) } + all(windows, games)
    }

    private fun all(windows: List<WindowRecord>, games: List<GameRecord>) = league("ALL", windows, games)

    private fun league(name: String, w: List<WindowRecord>, g: List<GameRecord>): LeagueTotals {
        val gameIds = (g.map { it.eventId } + w.map { it.eventId }).distinct()
        val durations = w.map { it.durationMs }.sorted()
        val profiles = w.flatMap { it.results }.map { it.profile to it.delayMs }.distinct().sortedBy { it.second }.map { (p, d) ->
            val rs = w.mapNotNull { rec -> rec.results.firstOrNull { it.profile == p }?.let { rec to it } }
            val byGame = rs.groupBy { it.first.eventId }.mapValues { (_, v) -> v.sumOf { it.second.cappedPnl } }
            ProfileTotals(
                p, d, rs.size, rs.count { it.second.outcome == PaperOutcome.BOTH.name }, rs.count { it.second.outcome == PaperOutcome.ONE_LEG.name },
                rs.count { it.second.outcome == PaperOutcome.MISSED.name }, rs.sumOf { it.second.pnl }, rs.sumOf { it.second.cappedPnl }, byGame.values.count { it > 0.0 },
            )
        }
        return LeagueTotals(name, gameIds.size, g.sumOf { (it.toMs - it.fromMs) / 60_000.0 }, w.size, durations.getOrElse(durations.size / 2) { 0L }, durations.getOrElse(minOf(durations.size - 1, (durations.size * 0.9).toInt())) { 0L }, profiles)
    }

    /** The answer for [t] (the "ALL" row): see [BurstVerdict]. */
    fun verdict(t: LeagueTotals): BurstVerdict {
        if (t.games < MIN_GAMES || t.windows < MIN_WINDOWS) return BurstVerdict.NEEDS_DATA
        val slow = t.profiles.lastOrNull { it.profile == LatencyModel.SLOW } ?: return BurstVerdict.NEEDS_DATA
        val bothShare = if (slow.windows == 0) 0.0 else slow.both.toDouble() / slow.windows
        if (bothShare < CATCH_SHARE || slow.cappedPnl <= 0.0) return BurstVerdict.NOT_CATCHABLE
        val perGame = slow.cappedPnl / t.games.coerceAtLeast(1)
        val upShare = slow.gamesUp.toDouble() / t.games.coerceAtLeast(1)
        return if (perGame < MIN_DOLLARS_PER_GAME || upShare < GAMES_UP_SHARE) BurstVerdict.TOO_SMALL else BurstVerdict.WORTH_A_TEST
    }

    /** The report Diagnostics and "Share live burst study" print. [latencyNote]: where the delays came from ([LatencyModel.note]). */
    fun report(records: List<BurstLine>, latencyNote: String, capDollars: Double): String {
        val totals = summarize(records)
        val o = StringBuilder()
        val all = totals.lastOrNull()
        o.appendLine("Live burst recorder (no orders; RESEARCH.md §95)")
        if (all == null || (all.games == 0 && all.windows == 0)) {
            o.appendLine("  nothing recorded yet: it records only while a live game of a picked league is on Novig, with the recorder switched on")
            return o.toString()
        }
        o.appendLine("  delays used: $latencyNote")
        o.appendLine("  your limit for the paper trade: ${if (capDollars > 0) money(capDollars) + " a leg" else "none"}")
        for (t in totals) {
            o.appendLine("  ${t.league}: ${t.games} game${if (t.games == 1) "" else "s"}, ${"%.0f".format(Locale.US, t.minutes)} min watched, ${t.windows} window${if (t.windows == 1) "" else "s"} " +
                "(open ${t.medianMs} ms at the median, ${t.p90Ms} ms at the 90th)")
            for (p in t.profiles) {
                o.appendLine("      ${p.profile.padEnd(14)} (${p.delayMs} ms): both legs ${p.both}, one leg ${p.oneLeg}, missed ${p.missed} · paper ${money(p.cappedPnl)} at your limit, ${money(p.pnl)} at the best price level's depth · up in ${p.gamesUp} of ${t.games} games")
            }
        }
        if (all.games > 0 && all.windows > 0) {
            val v = verdict(all)
            o.appendLine("  VERDICT: ${v.label}" + if (v == BurstVerdict.NEEDS_DATA) " (needs $MIN_GAMES games and $MIN_WINDOWS windows; has ${all.games} and ${all.windows})" else "")
        }
        o.appendLine("  WHAT THIS PROVES: $PROVES")
        return o.toString()
    }

    private fun money(d: Double) = (if (d < 0) "-$" else "$") + "%.2f".format(Locale.US, Math.abs(d))
}
