package com.tjshea.vigilant.data.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What each free feed's reply means as game scores (and Polymarket's odds), pure, so each can be tested against a real payload (RESEARCH.md §99, §106). A [Reading] is one game's score in
 * one reply; [FeedRace.Tracker] turns readings into the changes the race compares. Tennis scores are the games of the CURRENT set with the set number folded in (set 2's 0-0 is not set 1's).
 */
object FeedParsers {

    class Reading(val gameId: String, val home: String, val away: String, val h: Int, val a: Int, val live: Boolean = true, val league: String? = null)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun parse(body: String): JsonElement? = runCatching { json.parseToJsonElement(body) }.getOrNull()

    /** Sofascore's `sport/<sport>/events/live`: each in-play event's score; tennis by games in the current set. */
    fun sofascore(body: String, sport: String): List<Reading> {
        val events = parse(body).obj()?.get("events") as? JsonArray ?: return emptyList()
        return events.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            val home = o["homeTeam"].obj()?.get("name").str() ?: return@mapNotNull null
            val away = o["awayTeam"].obj()?.get("name").str() ?: return@mapNotNull null
            val hs = o["homeScore"].obj() ?: return@mapNotNull null
            val aus = o["awayScore"].obj() ?: return@mapNotNull null
            var h = hs["current"].int()
            var a = aus["current"].int()
            if (sport == "tennis") {
                val sets = hs.keys.filter { Regex("^period\\d+$").matches(it) && it in aus }
                if (sets.isEmpty()) { h = 0; a = 0 } else {
                    val k = sets.maxBy { it.removePrefix("period").toInt() }
                    val n = k.removePrefix("period").toInt()
                    h = (n - 1) * 100 + (hs[k].int() ?: 0)
                    a = (n - 1) * 100 + (aus[k].int() ?: 0)
                }
            }
            if (h == null || a == null) return@mapNotNull null
            Reading(id, home, away, h, a, league = o["tournament"].obj()?.get("name").str())
        }
    }

    /** One Polymarket sports-socket frame: `{gameId, leagueAbbreviation, homeTeam, awayTeam, status, score: "4-1", period: "S1", live}`; null for a ping or a frame with no score. */
    fun polymarketScore(text: String): Reading? {
        val o = parse(text).obj() ?: return null
        val m = Regex("^(\\d+)-(\\d+)$").find(o["score"].str() ?: return null) ?: return null
        val home = o["homeTeam"].str() ?: return null
        val away = o["awayTeam"].str() ?: return null
        var h = m.groupValues[1].toInt()
        var a = m.groupValues[2].toInt()
        val league = o["leagueAbbreviation"].str()
        val set = Regex("^S(\\d+)$").find(o["period"].str() ?: "")?.groupValues?.get(1)?.toInt()
        if (set != null && league in TENNIS_LEAGUES) { h += (set - 1) * 100; a += (set - 1) * 100 }
        val id = o["gameId"].str() ?: o["slug"].str() ?: return null
        return Reading(id, home, away, h, a, live = (o["live"] as? JsonPrimitive)?.contentOrNull == "true", league = league)
    }

    private val TENNIS_LEAGUES = setOf("atp", "wta", "itf", "challenger")

    /** ESPN's `scoreboard`: each event with its score (live = state "in"). */
    fun espn(body: String): List<Reading> {
        val events = parse(body).obj()?.get("events") as? JsonArray ?: return emptyList()
        return events.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            val state = o["status"].obj()?.get("type").obj()?.get("state").str()
            val comp = (o["competitions"] as? JsonArray)?.firstOrNull().obj() ?: return@mapNotNull null
            val cs = (comp["competitors"] as? JsonArray)?.mapNotNull { it.obj() } ?: return@mapNotNull null
            val home = cs.firstOrNull { it["homeAway"].str() == "home" } ?: return@mapNotNull null
            val away = cs.firstOrNull { it["homeAway"].str() == "away" } ?: return@mapNotNull null
            Reading(
                id, home["team"].obj()?.get("displayName").str() ?: return@mapNotNull null, away["team"].obj()?.get("displayName").str() ?: return@mapNotNull null,
                home["score"].int() ?: 0, away["score"].int() ?: 0, live = state == "in",
            )
        }
    }

    /** The NHL's `v1/score/<day>`: each game with its score (live = LIVE or CRIT); a team's name is its place plus its name where given. */
    fun nhl(body: String): List<Reading> {
        val games = parse(body).obj()?.get("games") as? JsonArray ?: return emptyList()
        return games.mapNotNull { g ->
            val o = g.obj() ?: return@mapNotNull null
            val id = o["id"].str() ?: return@mapNotNull null
            fun team(k: String): String? = o[k].obj()?.let { t -> (t["placeName"].obj()?.get("default").str().orEmpty() + " " + t["name"].obj()?.get("default").str().orEmpty()).trim().ifBlank { null } }
            val home = team("homeTeam") ?: return@mapNotNull null
            val away = team("awayTeam") ?: return@mapNotNull null
            Reading(id, home, away, o["homeTeam"].obj()?.get("score").int() ?: 0, o["awayTeam"].obj()?.get("score").int() ?: 0, live = o["gameState"].str() in setOf("LIVE", "CRIT"))
        }
    }

    /** MLB's `schedule?hydrate=linescore`: each game with its runs (live = abstract state "Live"). */
    fun mlb(body: String): List<Reading> {
        val dates = parse(body).obj()?.get("dates") as? JsonArray ?: return emptyList()
        return dates.flatMap { d ->
            (d.obj()?.get("games") as? JsonArray).orEmpty().mapNotNull { g ->
                val o = g.obj() ?: return@mapNotNull null
                val id = o["gamePk"].str() ?: return@mapNotNull null
                val st = o["status"].obj()?.get("abstractGameState").str()
                val t = o["teams"].obj() ?: return@mapNotNull null
                val home = t["home"].obj() ?: return@mapNotNull null
                val away = t["away"].obj() ?: return@mapNotNull null
                Reading(
                    id, home["team"].obj()?.get("name").str() ?: return@mapNotNull null, away["team"].obj()?.get("name").str() ?: return@mapNotNull null,
                    home["score"].int() ?: 0, away["score"].int() ?: 0, live = st == "Live",
                )
            }
        }
    }

    /** One quote from Polymarket's CLOB market socket: the best bid and ask of [assetId], with the exchange's millisecond stamp. */
    class Quote(val assetId: String, val bid: Double, val ask: Double, val serverMs: Long?)

    /**
     * The best bid/ask changes in one CLOB frame (a frame is an object or an array of them): a `price_changes` list with `best_bid`/`best_ask` per asset, or a full `book` with
     * `bids` and `asks`. Anything else (ticks of the last trade, pings) gives none.
     */
    fun polymarketQuotes(text: String): List<Quote> {
        val root = parse(text) ?: return emptyList()
        val frames = (root as? JsonArray)?.mapNotNull { it.obj() } ?: listOfNotNull(root.obj())
        return frames.flatMap { fr ->
            val ts = fr["timestamp"].str()?.toLongOrNull()
            val changes = fr["price_changes"] as? JsonArray
            if (changes != null) {
                changes.mapNotNull { c ->
                    val co = c.obj() ?: return@mapNotNull null
                    val id = co["asset_id"].str() ?: return@mapNotNull null
                    val bid = co["best_bid"].str()?.toDoubleOrNull() ?: return@mapNotNull null
                    val ask = co["best_ask"].str()?.toDoubleOrNull() ?: return@mapNotNull null
                    Quote(id, bid, ask, ts)
                }
            } else {
                val id = fr["asset_id"].str() ?: return@flatMap emptyList()
                val bids = (fr["bids"] as? JsonArray)?.mapNotNull { it.obj()?.get("price").str()?.toDoubleOrNull() } ?: return@flatMap emptyList()
                val asks = (fr["asks"] as? JsonArray)?.mapNotNull { it.obj()?.get("price").str()?.toDoubleOrNull() } ?: return@flatMap emptyList()
                if (bids.isEmpty() || asks.isEmpty()) emptyList() else listOf(Quote(id, bids.max(), asks.min(), ts))
            }
        }
    }

    /** From `gamma-api.polymarket.com/events?game_id=`: the event's title and the match-winner market's tokens with their outcome names. */
    class Market(val title: String, val tokens: List<Pair<String, String>>)

    fun polymarketMarket(body: String): Market? {
        val ev = (parse(body) as? JsonArray)?.firstOrNull().obj() ?: return null
        val m = (ev["markets"] as? JsonArray)?.firstOrNull().obj() ?: return null
        val toks = parse(m["clobTokenIds"].str() ?: return null) as? JsonArray ?: return null
        val outs = parse(m["outcomes"].str() ?: return null) as? JsonArray ?: return null
        val pairs = toks.zip(outs).mapNotNull { (t, o) -> (t.str() ?: return@mapNotNull null) to (o.str() ?: return@mapNotNull null) }
        return if (pairs.size >= 2) Market(ev["title"].str().orEmpty(), pairs) else null
    }
}
