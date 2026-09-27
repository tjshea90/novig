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
        /** The exact outcome: `novigapp://events/<id>` opens Novig's bet slip on it. */
        data class Bet(val outcomeId: String, val eventId: String) : Found() {
            override val link: String get() = "novigapp://events/$outcomeId"
        }

        /** Only the game: `novigapp://event-markets/<id>` opens its markets in Novig. */
        data class Game(val eventId: String) : Found() {
            override val link: String get() = "novigapp://event-markets/$eventId"
        }

        abstract val link: String
    }

    /** One event of Novig's catalog. */
    data class Event(val id: String, val description: String, val startsTs: Long)

    /** One outcome of one market. */
    data class Outcome(val id: String, val name: String)

    /** One market of an event. */
    data class Market(val id: String, val type: String, val description: String, val strike: Double?, val outcomes: List<Outcome>)

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
        val list = marketsOf(event.id) ?: return Found.Game(event.id)
        return matchOutcome(row, event, list)?.let { Found.Bet(it.id, event.id) } ?: Found.Game(event.id)
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
        mutex.withLock { map[key] = Kept(value, clock()) }
        return value
    }

    private suspend fun get(url: String): JsonElement? {
        requests++
        return try {
            val reply = http.newCall(Request.Builder().url(url).get().build()).awaitText()
            if (!reply.isSuccessful) null else runCatching { json.parseToJsonElement(reply.body) }.getOrNull()
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        /** Catalog answers are kept this long (a tap on the next bet of the same game costs nothing). */
        const val KEEP_MS = 5 * 60_000L

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
            Market(id, o.str("marketType").orEmpty(), o.str("description").orEmpty(), o.str("strike")?.toDoubleOrNull(), outcomes)
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
                if (!typeFits(m.type, words)) continue
                for (o in m.outcomes) if (isThisBet(row, who, line, m, o, event)) candidates += o
            }
            return candidates.singleOrNull()
        }

        private fun isThisBet(row: CnoRow, who: String, line: String?, m: Market, o: Outcome, event: Event): Boolean {
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

        /** CNO's market name as words, the way Novig's market types are spelled ("1st Half" → "1h"). */
        fun marketWords(market: String): Set<String> = norm(
            market.replace(Regex("(?i)1st half|first half"), " 1h ").replace(Regex("(?i)2nd half|second half"), " 2h ")
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
