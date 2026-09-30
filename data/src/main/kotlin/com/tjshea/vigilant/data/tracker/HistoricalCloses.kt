package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.engine.Devig
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/*
 * Closing lines found after the start, from sources that keep history (Tj, 2026-09-30: "My phone will not always be on. The app has to be
 * able to find clv from closing lines after the games started or even days later. Espn may have the closing lines information."). RESEARCH.md
 * §42 has what each source keeps and how it was checked.
 */

/** What looking for one bet's close in one source found. */
sealed interface CloseLookup {
    /** The bet's side's fair probability at the start, and where it came from (for the card). */
    data class Found(val fair: Double, val via: String) : CloseLookup

    /** This source will never have it ([reason] says why): the next source is asked. */
    data class None(val reason: String) : CloseLookup

    /** Not yet (the feed didn't answer, the day's file isn't out): asked again later. */
    data class Later(val reason: String) : CloseLookup
}

/** A source of closing lines after the fact. */
interface CloseSource {
    /** Each of [bets]' ids to what this source knows of its close. */
    suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup>

    /** Megabytes a look can cost ([NovigTradeCloses]): skipped only when a caller says so ([CloseBackfill.run]'s `heavyOk`). */
    val heavy: Boolean get() = false
}

/**
 * ESPN keeps each game's odds provider's (DraftKings, ESPN BET) opening and **closing** moneyline, spread and total, with their prices, for
 * finished games and past seasons: `sports.core.api.espn.com/v2/sports/{sport}/leagues/{league}/events/{id}/competitions/{id}/odds`
 * (checked 2026-09-30 on NFL, NCAAF, MLB, NBA, MLS). The close is devigged across its two sides. Full-game lines only, and only at the line
 * the book closed at: a spread or total that closed at another number isn't the same bet (Novig's trades may have it).
 */
class EspnCloses(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val siteBase: String = "https://site.api.espn.com/apis/site/v2/sports",
    private val coreBase: String = "https://sports.core.api.espn.com/v2/sports",
    private val gapMs: Long = 250,
) : CloseSource {

    /** Requests made (tests count them). */
    @Volatile
    var requests = 0
        private set

    override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
        val out = HashMap<String, CloseLookup>()
        val days = HashMap<Pair<String, LocalDate>, List<GameScore>?>()
        val odds = HashMap<String, JsonElement?>()
        for (b in bets) {
            val pick = BetGrader.pickOf(b)
            if (!gameLine(pick)) {
                out[b.id] = CloseLookup.None("ESPN keeps full-game moneylines, spreads and totals only")
                continue
            }
            val path = PATHS[b.league.trim().uppercase()]
            if (path == null) {
                out[b.id] = CloseLookup.None("ESPN has no closing odds for ${b.league.ifBlank { "this league" }}")
                continue
            }
            val day = FreeScores.etDate(b.startsTs)
            var reached = false
            var game: GameScore? = null
            for (date in listOf(day, day.minusDays(1), day.plusDays(1))) {
                val games = days.getOrPut(path to date) { get("$siteBase/$path/scoreboard?dates=${date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)}")?.let { FreeScores.parseEspnDay(it, b.league.uppercase()) } }
                if (games == null) continue
                reached = true
                game = BetGrader.gameOf(b, games)
                if (game != null) break
            }
            if (game == null) {
                out[b.id] = if (reached) CloseLookup.None("Not on ESPN's scoreboard") else CloseLookup.Later("ESPN didn't answer")
                continue
            }
            val sport = path.substringBefore('/')
            val league = path.substringAfter('/')
            val root = odds.getOrPut("$path/${game.id}") { get("$coreBase/$sport/leagues/$league/events/${game.id}/competitions/${game.id}/odds") }
            out[b.id] = if (root == null) CloseLookup.Later("ESPN's odds didn't answer") else parseClose(root, pick!!, game)
        }
        return out
    }

    private val pace = Mutex()
    private var lastMs = 0L

    private suspend fun get(url: String): JsonElement? {
        pace.withLock {
            val wait = lastMs + gapMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastMs = System.currentTimeMillis()
        }
        requests++
        return try {
            val reply = http.newCall(Request.Builder().url(url).get().build()).awaitText()
            if (!reply.isSuccessful) null else runCatching { json.parseToJsonElement(reply.body) }.getOrNull()
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        /** Leagues whose odds ESPN keeps, to its site path. */
        val PATHS = mapOf(
            "NFL" to "football/nfl",
            "NCAAF" to "football/college-football",
            "NBA" to "basketball/nba",
            "WNBA" to "basketball/wnba",
            "NCAAB" to "basketball/mens-college-basketball",
            "NHL" to "hockey/nhl",
            "MLB" to "baseball/mlb",
        )

        /** A full-game moneyline, spread or total: what ESPN's odds carry. */
        fun gameLine(pick: BetGrader.Pick?): Boolean = when (pick) {
            is BetGrader.Pick.Moneyline -> true
            is BetGrader.Pick.Spread -> pick.period == BetGrader.Period.GAME
            is BetGrader.Pick.Total -> pick.period == BetGrader.Period.GAME
            else -> false
        }

        private fun JsonElement?.obj() = this as? JsonObject
        private fun JsonElement?.arr() = (this as? JsonArray).orEmpty()
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

        /** "+275", "-110", "EVEN" to its implied probability (vig included). */
        fun implied(american: String?): Double? {
            val t = american?.trim()?.replace('−', '-') ?: return null
            val n = if (t.equals("EVEN", true) || t.equals("EV", true)) 100 else t.removePrefix("+").toDoubleOrNull()?.toInt() ?: return null
            if (n == 0 || abs(n) < 100) return null
            return 1.0 / Odds.americanToDecimal(n)
        }

        private fun line(s: String?): Double? = s?.trim()?.removePrefix("+")?.removePrefix("o")?.removePrefix("u")?.replace('−', '-')?.toDoubleOrNull()

        /** Two sides' vigged probabilities to the fair one of the first. */
        private fun fairOf(mine: Double, other: Double): Double = Devig.multiplicative(listOf(mine, other))[0]

        /** [pick]'s close in ESPN's odds [root] for [game]: the first provider (priority order) that closed that exact line. */
        fun parseClose(root: JsonElement, pick: BetGrader.Pick, game: GameScore): CloseLookup {
            val items = root.obj()?.get("items").arr().mapNotNull { it.obj() }
                .filterNot { it["provider"].obj()?.str("name")?.contains("live", ignoreCase = true) == true }
                .sortedBy { it["provider"].obj()?.str("priority")?.toIntOrNull() ?: 99 }
            if (items.isEmpty()) return CloseLookup.None("ESPN has no odds for this game")
            var reason = "ESPN has no closing line for this bet"
            for (item in items) {
                val provider = item["provider"].obj()?.str("name") ?: "ESPN"
                val via = "ESPN · $provider close"
                val home = item["homeTeamOdds"].obj()?.get("close").obj()
                val away = item["awayTeamOdds"].obj()?.get("close").obj()
                when (pick) {
                    is BetGrader.Pick.Moneyline -> {
                        val isAway = BetGrader.sideOf(pick.team, game) ?: return CloseLookup.None("Couldn't tell which team this is")
                        val h = implied(home?.get("moneyLine").obj()?.str("american"))
                        val a = implied(away?.get("moneyLine").obj()?.str("american"))
                        if (h != null && a != null) return CloseLookup.Found(if (isAway) fairOf(a, h) else fairOf(h, a), via)
                    }
                    is BetGrader.Pick.Spread -> {
                        val isAway = BetGrader.sideOf(pick.team, game) ?: return CloseLookup.None("Couldn't tell which team this is")
                        val mine = if (isAway) away else home
                        val theirs = if (isAway) home else away
                        val closed = line(mine?.get("pointSpread").obj()?.str("american"))
                        val p = implied(mine?.get("spread").obj()?.str("american"))
                        val q = implied(theirs?.get("spread").obj()?.str("american"))
                        if (closed != null && p != null && q != null) {
                            if (abs(closed - pick.line) < 1e-6) return CloseLookup.Found(fairOf(p, q), via)
                            reason = "$provider closed at ${fmt(closed)}, not your ${fmt(pick.line)}"
                        }
                    }
                    is BetGrader.Pick.Total -> {
                        val close = item["close"].obj()
                        val closed = line(close?.get("total").obj()?.str("american"))
                        val over = implied(close?.get("over").obj()?.str("american"))
                        val under = implied(close?.get("under").obj()?.str("american"))
                        if (closed != null && over != null && under != null) {
                            if (abs(closed - pick.line) < 1e-6) return CloseLookup.Found(if (pick.over) fairOf(over, under) else fairOf(under, over), via)
                            reason = "$provider's total closed at ${fmt(closed)}, not your ${fmt(pick.line)}"
                        }
                    }
                    else -> return CloseLookup.None("ESPN keeps full-game moneylines, spreads and totals only")
                }
            }
            return CloseLookup.None(reason)
        }

        private fun fmt(v: Double): String = (if (v > 0) "+" else "") + (if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString())
    }
}

/**
 * Novig's own record of every trade, published each morning for the day before (Eastern time) at `data.novig.com/reporting/trade-data/<date>/
 * trades.csv`, from 2026-08-03 (NOVIG_API.md §10). Every market is in it, player props and alternate lines included, keyed by the outcome ids the
 * Tracker keeps. A bet's close is what its outcome traded at in the last [windowMs] before the start (volume-weighted: pregame Novig trades
 * carry no fee, and the two sides' prices add to 1, so it's already fair). The file is ~36 MB, sorted by time: only the window is fetched,
 * found by a binary search of byte ranges.
 */
class NovigTradeCloses(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val base: String = "https://data.novig.com/reporting/trade-data",
    private val windowMs: Long = WINDOW_MS,
) : CloseSource {

    /** A kickoff's half hour of trades is 0.5 to 4 MB (the app reads it on any network: Tj's data is unlimited). */
    override val heavy: Boolean get() = true

    /** Bytes read (tests and Diagnostics). */
    @Volatile
    var bytesRead = 0L
        private set

    private data class Trade(val ts: Long, val outcomeId: String, val cost: Double, val qty: Double)

    override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
        val out = HashMap<String, CloseLookup>()
        val withIds = bets.mapNotNull { b ->
            val id = outcomeOf(b)
            if (id == null) out[b.id] = CloseLookup.None("No Novig outcome on record for this bet")
            id?.let { b to it }
        }
        if (withIds.isEmpty()) return out
        val dates = index()
        if (dates == null) {
            withIds.forEach { (b, _) -> out[b.id] = CloseLookup.Later("Novig's trade history didn't answer") }
            return out
        }
        val first = dates.minOrNull()
        for ((date, group) in withIds.groupBy { FreeScores.etDate(it.first.startsTs).toString() }) {
            if (date !in dates) {
                val reason = if (first != null && date < first) "Before Novig's trade history starts ($first)" else "Novig publishes this day's trades the next morning"
                group.forEach { (b, _) -> out[b.id] = if (first != null && date < first) CloseLookup.None(reason) else CloseLookup.Later(reason) }
                continue
            }
            val url = "$base/$date/trades.csv"
            val file = try { open(url) } catch (e: IOException) { null }
            if (file == null) {
                group.forEach { (b, _) -> out[b.id] = CloseLookup.Later("Novig's trade file didn't answer") }
                continue
            }
            for ((start, atStart) in group.groupBy { it.first.startsTs }) {
                val trades = try { window(file, start - windowMs, start) } catch (e: IOException) { null }
                for ((b, outcome) in atStart) {
                    out[b.id] = when {
                        trades == null -> CloseLookup.Later("Novig's trade file didn't answer")
                        else -> {
                            val mine = trades.filter { it.outcomeId == outcome }
                            val qty = mine.sumOf { it.qty }
                            if (mine.isEmpty() || qty <= 0.0) CloseLookup.None("No trades on Novig in the ${windowMs / 60_000} minutes before the start")
                            else CloseLookup.Found((mine.sumOf { it.cost } / qty).coerceIn(0.001, 0.999), "Novig's last trades (${mine.size})")
                        }
                    }
                }
            }
        }
        return out
    }

    // ---- the file ---------------------------------------------------------------------------------------------------

    private suspend fun index(): Set<String>? = try {
        val reply = http.newCall(Request.Builder().url("$base/index.json").get().build()).awaitText()
        if (!reply.isSuccessful) null
        else json.parseToJsonElement(reply.body).let { (it as? JsonObject)?.get("dates") as? JsonArray }?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet()
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** A day's trades file: its size and column positions. */
    private class TradeFile(val url: String, val size: Long, val cols: Map<String, Int>, val headerEnd: Long)

    private suspend fun range(url: String, from: Long, to: Long): Pair<String, Long?> {
        val reply = http.newCall(Request.Builder().url(url).header("Range", "bytes=$from-$to").get().build()).awaitText()
        if (reply.code != 206 && reply.code != 200) throw IOException("HTTP ${reply.code}")
        bytesRead += reply.body.length
        val total = reply.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
        return reply.body to total
    }

    private suspend fun open(url: String): TradeFile? {
        val (head, total) = range(url, 0, 4095)
        val size = total ?: return null
        val nl = head.indexOf('\n').takeIf { it > 0 } ?: return null
        val cols = head.substring(0, nl).trim().split(',').withIndex().associate { (i, c) -> c.trim() to i }
        if (listOf("timestamp", "outcomeId", "cost", "qty").any { it !in cols }) return null
        return TradeFile(url, size, cols, (head.substring(0, nl + 1).toByteArray().size).toLong())
    }

    /** The first line's time at or after byte [offset] (the line cut by [offset] is skipped); null past the end. */
    private suspend fun timeAt(file: TradeFile, offset: Long): Long? {
        if (offset >= file.size) return null
        val (text, _) = range(file.url, offset, minOf(file.size - 1, offset + PROBE_BYTES))
        val start = if (offset <= file.headerEnd) 0 else text.indexOf('\n') + 1
        if (start <= 0 && offset > file.headerEnd) return null
        val end = text.indexOf('\n', start)
        if (end < 0) return null
        return tsOf(text.substring(start, end))
    }

    private fun tsOf(line: String): Long? = runCatching { Instant.parse(line.substringBefore(',')).toEpochMilli() }.getOrNull()

    /** The trades from [fromMs] to [toMs]: a byte offset before [fromMs] found by halving, then read forward until [toMs]. */
    private suspend fun window(file: TradeFile, fromMs: Long, toMs: Long): List<Trade> {
        var lo = file.headerEnd
        var hi = file.size
        while (hi - lo > PROBE_BYTES) {
            val mid = (lo + hi) / 2
            val t = timeAt(file, mid)
            if (t == null || t >= fromMs) hi = mid else lo = mid
        }
        val trades = ArrayList<Trade>()
        var at = lo
        var carry = ""
        var skipFirst = lo > file.headerEnd
        var read = 0L
        while (at < file.size && read < MAX_WINDOW_BYTES) {
            val (text, _) = range(file.url, at, minOf(file.size - 1, at + CHUNK_BYTES - 1))
            read += text.length
            at += text.toByteArray().size
            val lines = (carry + text).split('\n')
            carry = lines.last()
            var done = false
            for ((i, raw) in lines.dropLast(1).withIndex()) {
                if (i == 0 && skipFirst) continue
                val line = raw.trimEnd('\r')
                val ts = tsOf(line) ?: continue
                if (ts >= toMs) { done = true; break }
                if (ts < fromMs) continue
                parse(file, line, ts)?.let(trades::add)
            }
            skipFirst = false
            if (done) break
        }
        return trades
    }

    private fun parse(file: TradeFile, line: String, ts: Long): Trade? {
        val f = line.split(',')
        fun col(name: String) = file.cols[name]?.let { f.getOrNull(it) }
        // Singles only: a parlay's row prices the whole ticket, not one outcome.
        if (col("tradeType")?.let { it != "STRAIGHT" } == true) return null
        if (col("legs")?.let { it != "1" } == true) return null
        val cost = col("cost")?.toDoubleOrNull() ?: return null
        val qty = col("qty")?.toDoubleOrNull() ?: return null
        if (qty <= 0.0 || cost <= 0.0 || cost >= qty) return null
        return Trade(ts, col("outcomeId") ?: return null, cost, qty)
    }

    companion object {
        /** The close is what traded in the last half hour before the start. */
        const val WINDOW_MS = 30 * 60_000L

        private const val PROBE_BYTES = 2_048L
        private const val CHUNK_BYTES = 262_144L

        /** A busy slate's half hour is ~3.5 MB; past this, the rest is left (the close comes from what was read). */
        private const val MAX_WINDOW_BYTES = 12_000_000L

        private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** The bet's Novig outcome: kept on the bet, or in its Novig link (`novigapp://events/<outcomeId>…`, CNO's and Vigilant's). */
        fun outcomeOf(b: TrackedBet): String? =
            b.outcomeId.takeIf { UUID.matches(it) } ?: b.betUrl?.substringAfter("events/", "")?.let { UUID.find(it)?.value }
    }
}

/**
 * Finds each started bet's close when Vigilant wasn't running at the start (Tj, 2026-09-30): ESPN first (right after the start, game lines),
 * then Novig's trade history (the next morning, every market). A close read just before the start ([ClosingLine.captured]) always wins, so
 * only bets without one are looked for; each is looked at again every [RETRY_MS] until a source has it or none ever will, for up to
 * [GIVE_UP_MS].
 */
class CloseBackfill(
    private val tracker: BetTracker,
    private val sources: List<CloseSource>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Report(val looked: Int, val found: Int, val bySource: Map<String, Int>)

    private val mutex = Mutex()

    /** [heavyOk]: the sources that download megabytes ([CloseSource.heavy]) may run (Wi-Fi); otherwise their bets wait for the next look. */
    suspend fun run(heavyOk: Boolean = true): Report = mutex.withLock {
        val now = clock()
        val todo = tracker.all().filter { due(it, now) }.sortedByDescending { it.startsTs }.take(MAX_PER_RUN)
        if (todo.isEmpty()) return@withLock Report(0, 0, emptyMap())
        val found = HashMap<String, CloseLookup.Found>()
        val notes = HashMap<String, MutableList<CloseLookup>>()
        var left = todo
        for (source in sources) {
            if (left.isEmpty()) break
            if (source.heavy && !heavyOk) {
                left.forEach { notes.getOrPut(it.id) { ArrayList() } += CloseLookup.Later("Novig's trade history wasn't read this time") }
                continue
            }
            val answers = runCatching { source.closes(left) }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                left.associate { it.id to CloseLookup.Later(e.message ?: "failed") }
            }
            for ((id, a) in answers) if (a is CloseLookup.Found) found[id] = a else notes.getOrPut(id) { ArrayList() } += a
            left = left.filter { it.id !in found }
        }
        tracker.editMany(todo.associate { b ->
            b.id to { cur: TrackedBet ->
                val f = found[b.id]
                val tried = notes[b.id].orEmpty()
                when {
                    f != null -> cur.copy(closeFair = f.fair, closeVia = f.via, closeNote = null, closeLookedAtMs = now, closeFinal = true)
                    else -> cur.copy(
                        closeNote = tried.joinToString("; ") { (it as? CloseLookup.None)?.reason ?: (it as CloseLookup.Later).reason }.ifBlank { null },
                        closeLookedAtMs = now,
                        // Every source said it never will: stop looking.
                        closeFinal = tried.isNotEmpty() && tried.size >= sources.size && tried.all { it is CloseLookup.None },
                    )
                }
            }
        })
        Report(todo.size, found.size, found.values.groupingBy { it.via.substringBefore(" ·").substringBefore(" (") }.eachCount())
    }

    companion object {
        const val VIA_ESPN = "ESPN"
        const val VIA_NOVIG = "Novig"

        /** ESPN posts the close at the start: looked for from a little after. */
        const val AFTER_START_MS = 10 * 60_000L

        /** A bet whose close wasn't found is looked for again after this. */
        const val RETRY_MS = 3 * 60 * 60_000L

        /** Bets whose game started longer ago than this aren't looked for. */
        const val GIVE_UP_MS = 60L * 24 * 60 * 60_000L

        const val MAX_PER_RUN = 120

        /** A started bet with no close yet (no capture, not bet in the last minutes) that a source may still have. */
        fun due(b: TrackedBet, now: Long): Boolean =
            b.status != BetStatus.VOID && !b.closeFinal && b.createdAtMs < b.startsTs && now >= b.startsTs + AFTER_START_MS &&
                now - b.startsTs <= GIVE_UP_MS && ClosingLine.closeOf(b, now) == null &&
                (b.closeLookedAtMs == null || now - b.closeLookedAtMs >= RETRY_MS)
    }
}
