package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
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
import java.text.Normalizer
import kotlin.math.abs

/**
 * Finds a CNO bet in Novig's own public catalog, for a tap when CNO can't hand over its link
 * (Tj, 2026-09-27: "sometimes they pull up the novig bet slip, but sometimes they don't … it says
 * cno could not be reached"). Two public Novig reads at most (the league's events, then the
 * game's markets), each kept a few minutes, and only on a tap: nothing runs in the background.
 *
 * Matching is strict, because the bet slip it opens is where Tj bets: the game by its two teams
 * (and start time), then one outcome whose player or team, line and side all agree, in a market of
 * the kind CNO named. Anything short of exactly one such outcome opens the game in Novig instead
 * ([Found.Game]), never a guess.
 */
class NovigBetFinder(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = "https://api.novig.com",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed class Found {
        /**
         * The exact outcome: `novigapp://events/<id>` opens Novig's bet slip on it. [market] is its
         * market as read (fee, outcomes), for pricing it from Novig's book ([NovigLive]).
         */
        data class Bet(
            val outcomeId: String,
            val eventId: String,
            val marketId: String? = null,
            val market: com.tjshea.vigilant.data.novig.NovigMarket? = null,
        ) : Found() {
            override val link: String get() = "novigapp://events/$outcomeId"
        }

        /**
         * Only the game: `novigapp://event-markets/<id>` opens its markets in Novig. [searched] is
         * false when the game's markets couldn't be read (Novig busy): worth asking again later.
         */
        data class Game(val eventId: String, val searched: Boolean = true) : Found() {
            override val link: String get() = "novigapp://event-markets/$eventId"
        }

        abstract val link: String
    }

    /** One event of Novig's catalog. */
    data class Event(val id: String, val description: String, val startsTs: Long)

    /** One outcome of one market. */
    data class Outcome(val id: String, val name: String)

    /** One market of an event ([novig]: the same market as the app models it, fee included). */
    data class Market(
        val id: String,
        val type: String,
        val description: String,
        val strike: Double?,
        val outcomes: List<Outcome>,
        val novig: com.tjshea.vigilant.data.novig.NovigMarket? = null,
    )

    private class Kept<T>(val value: T, val atMs: Long)

    private val mutex = Mutex()
    private val events = HashMap<String, Kept<List<Event>>>()
    private val markets = HashMap<String, Kept<List<Market>>>()

    /** Requests made (tests count them). */
    @Volatile
    var requests = 0
        private set

    /** [row] on Novig: the bet, the game, or null (not found, or Novig out of reach). */
    suspend fun find(row: CnoRow): Found? {
        val league = novigLeague(row.league) ?: return null
        val event = eventsOf(league)?.let { matchEvent(row, it) } ?: return null
        val list = marketsOf(event.id) ?: return Found.Game(event.id, searched = false)
        val outcome = matchOutcome(row, event, list) ?: return Found.Game(event.id)
        val market = list.firstOrNull { m -> m.outcomes.any { it.id == outcome.id } }
        return Found.Bet(outcome.id, event.id, market?.id, market?.novig)
    }

    private suspend fun eventsOf(league: String): List<Event>? = cached(events, league) {
        get("$baseUrl/v3/public/catalog/events?league=$league&status=OPEN_PREGAME,OPEN_INGAME&limit=1000")?.let(::parseEvents)
    }

    private suspend fun marketsOf(eventId: String): List<Market>? = cached(markets, eventId) {
        get("$baseUrl/v3/public/catalog/markets?event=$eventId&limit=2000")?.let(::parseMarkets)
    }

    private suspend fun <T> cached(map: HashMap<String, Kept<T>>, key: String, load: suspend () -> T?): T? {
        mutex.withLock { map[key]?.takeIf { clock() - it.atMs < KEEP_MS }?.let { return it.value } }
        val value = load() ?: return null
        mutex.withLock {
            // Games long over drop out, so a day of taps doesn't pile them up.
            val now = clock()
            map.entries.removeAll { now - it.value.atMs >= KEEP_MS }
            map[key] = Kept(value, now)
        }
        return value
    }

    private val paceMutex = Mutex()
    private var lastGetMs = Long.MIN_VALUE / 2

    @Volatile
    private var pausedUntilMs = 0L

    /**
     * One public Novig read, never closer than [MIN_GAP_MS] to the last, and none while Novig asked
     * for a pause (HTTP 429's Retry-After): its public routes allow short bursts only, and Tj's
     * phone has hit that limit before (NOVIG_API.md §5.1).
     */
    private suspend fun get(url: String): JsonElement? {
        paceMutex.withLock {
            val wait = maxOf(lastGetMs + MIN_GAP_MS, pausedUntilMs) - clock()
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastGetMs = clock()
        }
        requests++
        return try {
            val reply = http.newCall(Request.Builder().url(url).get().build()).awaitText()
            if (reply.code == 429) {
                pausedUntilMs = clock() + (reply.header("Retry-After")?.trim()?.toLongOrNull() ?: 2L).coerceIn(1L, 60L) * 1000L
                return null
            }
            if (!reply.isSuccessful) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { json.parseToJsonElement(reply.body) }.getOrNull() }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        /** Catalog answers are kept this long (a tap on the next bet of the same game costs nothing). */
        const val KEEP_MS = 5 * 60_000L

        /** The least time between two of its reads. */
        const val MIN_GAP_MS = 350L

        private val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        /** A game found by its teams must start within this of CNO's start time. */
        private const val START_SLACK_MS = 6 * 60 * 60_000L

        /** Novig's league name for CNO's ("MLS (USA)" → "MLS"), or null when it can't be one. */
        fun novigLeague(cno: String): String? = cno.substringBefore('(').trim().uppercase().takeIf { it.isNotEmpty() }

        fun parseEvents(root: JsonElement): List<Event> = items(root).mapNotNull { o ->
            val id = o.str("eventId") ?: return@mapNotNull null
            Event(id, o.str("description") ?: return@mapNotNull null, o.str("startsTs")?.toLongOrNull() ?: 0L)
        }

        fun parseMarkets(root: JsonElement): List<Market> = items(root).mapNotNull { o ->
            val id = o.str("marketId") ?: return@mapNotNull null
            val outcomes = (o["outcomes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .mapNotNull { oc -> Outcome(oc.str("outcomeId") ?: return@mapNotNull null, oc.str("name").orEmpty()) }
            Market(id, o.str("marketType").orEmpty(), o.str("description").orEmpty(), o.str("strike")?.toDoubleOrNull(), outcomes, com.tjshea.vigilant.data.novig.novigMarketOf(LENIENT, o))
        }

        private fun items(root: JsonElement): List<JsonObject> =
            ((root as? JsonObject)?.get("items") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

        /** The game: the same two teams (full names, "away @ home" on both sites), starting near CNO's time. */
        fun matchEvent(row: CnoRow, events: List<Event>): Event? {
            val cno = NovigText.parseMatchup(row.event) ?: return null
            fun near(e: Event) = row.startsAtMs == null || e.startsTs == 0L || abs(e.startsTs - row.startsAtMs) <= START_SLACK_MS
            events.firstOrNull { norm(it.description) == norm(row.event) && near(it) }?.let { return it }
            val scored = events.filter(::near).mapNotNull { e ->
                val m = NovigText.parseMatchup(e.description) ?: return@mapNotNull null
                val s = TeamMatcher.similarity(m.away, cno.away) + TeamMatcher.similarity(m.home, cno.home)
                (e to s).takeIf { TeamMatcher.similarity(m.away, cno.away) >= 0.8 && TeamMatcher.similarity(m.home, cno.home) >= 0.8 }
            }
            return scored.maxByOrNull { it.second }?.first?.takeIf { best -> scored.count { it.second == scored.maxOf { s -> s.second } } == 1 }
        }

        /** The one outcome that is this bet, or null when there isn't exactly one. */
        fun matchOutcome(row: CnoRow, event: Event, markets: List<Market>): Outcome? {
            val (who, line) = Picks.split(row.bet)
            val words = marketWords(row.market)
            val candidates = mutableListOf<Outcome>()
            for (m in markets) {
                if (!typeFits(m.type, words) && !threeWay(m.type, row.market)) continue
                for (o in m.outcomes) if (isThisBet(row, who, line, m, o, event)) candidates += o
            }
            return candidates.singleOrNull()
        }

        private fun isThisBet(row: CnoRow, who: String, line: String?, m: Market, o: Outcome, event: Event): Boolean {
            // Soccer's 3-way moneyline: Novig has a Yes/No market per team ("Real Salt Lake
            // MONEYLINE_3_WAY_WIN") and one for the draw; CNO says "Real Salt Lake No", "Draw Yes".
            if (threeWay(m.type, row.market)) {
                if (line == null || !o.name.equals(line, ignoreCase = true) || !(line.equals("Yes", true) || line.equals("No", true))) return false
                val draw = who.equals("Draw", ignoreCase = true) || who.equals("Tie", ignoreCase = true)
                return when (m.type) {
                    "MONEYLINE_3_WAY_DRAW" -> draw
                    "MONEYLINE_3_WAY_WIN" -> !draw && who.isNotEmpty() && namesTeam(m.description.trim().removeSuffix(m.type).trim(), who, event)
                    else -> false
                }
            }
            val total = line?.let { NovigText.parseTotalOutcome(it) }
            if (total != null) {
                // "Over 5.5": the outcome says the same; a player's or team's market names them.
                val mine = NovigText.parseTotalOutcome(o.name) ?: return false
                if (mine.first != total.first || mine.second != total.second) return false
                val subject = NovigText.subjectOf(m.description, m.type)
                return if (who.isEmpty()) subject == null || isGameTotal(m.type) else subject != null && samePerson(subject, who)
            }
            val spread = line?.let { l -> l.replace('−', '-').toDoubleOrNull()?.takeIf { l.startsWith("+") || l.startsWith("-") || l.startsWith("−") } }
            if (spread != null) {
                val (label, number) = NovigText.parseSpreadOutcome(o.name) ?: return false
                return number == spread && namesTeam(label, who, event)
            }
            if (line == null && who.isNotEmpty() && row.market.contains("moneyline", true) && !row.market.contains("3-way", true)) {
                return m.type == "MONEY" && namesTeam(o.name, who, event)
            }
            return false
        }

        /** A player's name as the two sites write it ("CJ Donaldson Jr." / "C.J. Donaldson"). */
        private fun samePerson(a: String, b: String) = PlayerNames.same(a, b) || norm(a) == norm(b)

        /** Whether a Novig outcome label ("NYG", "New York Giants") is the team CNO named, not the other one. */
        private fun namesTeam(label: String, team: String, event: Event): Boolean {
            val m = NovigText.parseMatchup(event.description) ?: return false
            val cnoSide = when {
                TeamMatcher.similarity(team, m.away) > TeamMatcher.similarity(team, m.home) -> m.away
                TeamMatcher.similarity(team, m.home) > TeamMatcher.similarity(team, m.away) -> m.home
                else -> return false
            }
            val away = TeamMatcher.labelIsAway(label, m.away, m.home) ?: return false
            return (if (away) m.away else m.home) == cnoSide
        }

        private fun isGameTotal(type: String) = type.startsWith("TOTAL")

        /** A 3-way moneyline market of Novig's for a CNO "Moneyline 3-way" bet (full game only). */
        private fun threeWay(type: String, cnoMarket: String): Boolean =
            type.startsWith("MONEYLINE_3_WAY") && cnoMarket.contains("3-way", ignoreCase = true) &&
                !Regex("(?i)half|1h|2h|period|quarter").containsMatchIn(cnoMarket)

        /** CNO's market name as words, the way Novig's market types are spelled ("1st Half" → "1h"). */
        fun marketWords(market: String): Set<String> = norm(
            market.replace(Regex("(?i)passing interceptions"), " interceptions thrown ")
                // Baseball and basketball wordings CNO uses that Novig spells shorter ("Earned Runs Allowed" is EARNED_RUNS).
                .replace(Regex("(?i)earned runs allowed"), " earned runs ")
                .replace(Regex("(?i)walks allowed"), " walks ")
                .replace(Regex("(?i)outs recorded"), " pitcher outs ")
                .replace(Regex("(?i)runs batted in"), " rbis ")
                .replace(Regex("(?i)reception yards"), " receiving yards ")
                .replace(Regex("(?i)\\b3[- ]?pointers?( made)?\\b|\\bthrees( made)?\\b"), " three pointers made ")
                .replace(Regex("(?i)1st half|first half"), " 1h ").replace(Regex("(?i)2nd half|second half"), " 2h ")
                .replace(Regex("(?i)point spread|run line|puck line|goal spread|spread"), " spread ")
                .replace(Regex("(?i)total points|total runs|total goals"), " total ")
                .replace(Regex("(?i)moneyline"), " money "),
        ).split(' ').filter { it.isNotEmpty() }.toSet()

        /**
         * Whether a Novig market type ("RECEIVING_YARDS", "PITCHER_STRIKEOUTS", "SPREAD_1H") is
         * CNO's market: the same words both ways (by their first five letters, so PITCHER is
         * Pitching), so "Rushing + Receiving Yards" is never plain "Rushing Yards", and a half is
         * only ever a half.
         */
        fun typeFits(type: String, words: Set<String>): Boolean {
            val parts = type.lowercase().split('_').filter { it.isNotEmpty() && it !in GENERIC }.toSet()
            val cno = words.filter { it !in GENERIC }.toSet()
            if (parts.isEmpty() || cno.isEmpty()) return false
            fun same(a: String, b: String) = a == b || a.removeSuffix("s") == b.removeSuffix("s") ||
                (a.length >= 5 && b.length >= 5 && a.take(5) == b.take(5))
            return parts.all { p -> cno.any { same(it, p) } } && cno.all { w -> parts.any { same(w, it) } }
        }

        /** Words that say nothing about which market it is. */
        private val GENERIC = setOf("player", "and", "the")

        private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().replace("&", " and ").replace("+", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}
