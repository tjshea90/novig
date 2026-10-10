package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.engine.Devig
import com.tjshea.vigilant.engine.DevigMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** A side of a Pinnacle line (its `designation`). */
enum class PinnSide {
    HOME, AWAY, DRAW, OVER, UNDER;

    companion object {
        fun of(designation: String?): PinnSide? = when (designation) {
            "home" -> HOME
            "away" -> AWAY
            "draw" -> DRAW
            "over" -> OVER
            "under" -> UNDER
            else -> null
        }
    }
}

enum class PinnLineType { MONEYLINE, SPREAD, TOTAL }

/** What a frame changed, for the runner to react to. */
data class PinnChange(val eventId: Long, val key: String?, val kind: Kind, val atMs: Long) {
    enum class Kind { PRICE, CLOSED, SCORE, VOLATILE, GONE }
}

/** One devigged reading of a line, kept a little while so a move over a window can be measured. */
class PinnSnap(val atMs: Long, val fair: Map<PinnSide, Double>)

/**
 * One Pinnacle line of one matchup, as the socket last said it. [points] is the HOME side's handicap for a spread and the line for a total. [changedAtMs] is the phone's clock at the
 * frame that last changed the PRICE (a re-sent unchanged market does not move it); [fair] is the devigged probability of each side by the book's method.
 */
class PinnLine(val eventId: Long, val key: String, val period: Int, val type: PinnLineType, val alternate: Boolean) {
    var points: Double? = null
    var american: Map<PinnSide, Double> = emptyMap()
    var fair: Map<PinnSide, Double> = emptyMap()
    var version: Long = Long.MIN_VALUE
    var changedAtMs: Long = 0L
    var open: Boolean = false
    var maxRisk: Double? = null
    var overround: Double = 0.0
    internal val history = ArrayDeque<PinnSnap>()

    /** [side]'s fair now less its fair as of [windowMs] ago (the oldest reading kept if the line is younger than the window); null with no reading to compare. */
    fun moveOver(side: PinnSide, windowMs: Long, nowMs: Long): Double? {
        val now = fair[side] ?: return null
        val cutoff = nowMs - windowMs
        var ref: PinnSnap? = null
        for (s in history) {
            if (s.atMs <= cutoff) ref = s else { if (ref == null) ref = s; break }
        }
        val before = ref?.fair?.get(side) ?: return null
        return now - before
    }
}

/** One Pinnacle matchup (a live child, or a prematch one) with its lines and its game state. */
class PinnEvent(val id: Long) {
    var parentId: Long? = null
    var home: String = ""
    var away: String = ""
    var league: String = ""

    /** Pinnodds' sport id (1 soccer, 2 tennis, 3 basketball, 4 hockey, 5 football, 6 baseball ...). */
    var sportId: Int = 0
    var startMs: Long = 0L
    var live: Boolean = false

    /** False for Pinnacle's special books on a match (corners and the like): their lines are not the game's. */
    var regular: Boolean = true

    /** Pinnacle's `units`: "Regular", or for tennis "Sets" (the match: its moneyline is the winner) and "Games" (its spread and total are in games). */
    var units: String = "Regular"

    /** One match's matchups share this (a live child's parent, or the prematch matchup itself): the tennis Sets and Games children of a match have the same one. */
    val groupId: Long get() = parentId ?: id
    var score: Pair<Int, Int>? = null

    /** The phone's clock when the score last CHANGED (0 = not seen to change). */
    var scoreAtMs: Long = 0L
    var clock: String? = null
    var lastFrameAtMs: Long = 0L
    var volatileUntilMs: Long = 0L
    val lines = HashMap<String, PinnLine>()
}

/**
 * Pinnacle's live and prematch books, rebuilt from the Pinnodds `/ws/feed` frames (docs: https://pinnodds.com/llms-full.txt, read 2026-10-08). The rules it follows are the feed's own:
 *  - a market is keyed by its matchup id + market `key`, never by team names; ordered and de-duplicated by `markets[i].version`, not `rec.version`;
 *  - `ld` (live delay) and `both` frames carry THE price; a `dz` (danger zone) frame is a volatility signal only (a goal threat, a break point, an imminent suspension), never a second
 *    price: it marks the event volatile for [VOLATILE_MS] and closes a line it says is no longer open;
 *  - a market whose `status` is not `open` is closed; a period that is not open closes all of its markets; an `op:"del"` drops the matchup;
 *  - `pre` frames are prematch prices pushed on the same stream.
 * Only the moneyline, the spread and the total of a matchup are kept (the lines Novig lists). Not thread safe: one consumer owns it.
 */
class PinnBook(var method: DevigMethod = DevigMethod.WORST_CASE) {
    val events = HashMap<Long, PinnEvent>()

    /**
     * Told of every market version this book takes in for the first time (matchup id, market key, version, the phone's clock): the feed race ([VersionRace]) times two feeds against each other
     * with it. Called from [apply]'s thread; keep it quick.
     */
    @Volatile var versionListener: ((matchupId: Long, key: String, version: Long, atMs: Long) -> Unit)? = null

    var frames = 0L
        private set

    /** Applies one server message (already parsed). Returns what changed. Pings, acks and unknown types change nothing. */
    fun apply(msg: JsonObject, nowMs: Long): List<PinnChange> {
        val type = (msg["type"] as? JsonPrimitive)?.contentOrNull ?: return emptyList()
        val out = ArrayList<PinnChange>(4)
        when (type) {
            "snapshot" -> {
                val sport = (msg["sport_id"] as? JsonPrimitive)?.intOrNull ?: 0
                (msg["events"] as? JsonArray)?.forEach { e -> (e as? JsonObject)?.let { applyRec(it, "ld", sport, nowMs, out) } }
            }
            "live" -> {
                frames++
                val rec = msg["rec"] as? JsonObject ?: return emptyList()
                val sport = (msg["sport_id"] as? JsonPrimitive)?.intOrNull ?: 0
                val channel = (msg["topic"] as? JsonPrimitive)?.contentOrNull?.substringAfterLast('/') ?: "ld"
                if ((msg["op"] as? JsonPrimitive)?.contentOrNull == "del") {
                    val id = (rec["id"] as? JsonPrimitive)?.longOrNull ?: return emptyList()
                    if (events.remove(id) != null) out += PinnChange(id, null, PinnChange.Kind.GONE, nowMs)
                } else {
                    applyRec(rec, channel, sport, nowMs, out)
                }
            }
        }
        return out
    }

    private fun applyRec(rec: JsonObject, channel: String, sport: Int, nowMs: Long, out: MutableList<PinnChange>) {
        val id = (rec["id"] as? JsonPrimitive)?.longOrNull ?: return
        val e = events.getOrPut(id) { PinnEvent(id) }
        e.lastFrameAtMs = nowMs
        if (sport != 0) e.sportId = sport
        (rec["parentId"] as? JsonPrimitive)?.longOrNull?.let { e.parentId = it }
        (rec["isLive"] as? JsonPrimitive)?.booleanOrNull?.let { e.live = it }
        (rec["units"] as? JsonPrimitive)?.contentOrNull?.let { u ->
            e.units = u
            // Tennis is booked as two children of one match: "Sets" (match winner) and "Games" (games spread and total). Both are the match's own lines, not specials (2026-10-08 tape).
            e.regular = u == "Regular" || (e.sportId == TENNIS_SPORT_ID && (u == "Sets" || u == "Games"))
        }
        (rec["startTime"] as? JsonPrimitive)?.contentOrNull?.let { s -> runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()?.let { e.startMs = it } }
        (rec["league"] as? JsonObject)?.get("name")?.let { (it as? JsonPrimitive)?.contentOrNull }?.let { e.league = it }
        val parts = rec["participants"] as? JsonArray
        parts?.forEach { p ->
            val o = p as? JsonObject ?: return@forEach
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            when ((o["alignment"] as? JsonPrimitive)?.contentOrNull) {
                "home" -> e.home = name.removeSuffix(GAMES_SUFFIX)
                "away" -> e.away = name.removeSuffix(GAMES_SUFFIX)
            }
        }
        readScore(rec)?.let { s ->
            if (s != e.score) {
                // The first sighting of a score (a snapshot, a new connection) is not a score being made.
                if (e.score != null) {
                    out += PinnChange(id, null, PinnChange.Kind.SCORE, nowMs)
                    e.scoreAtMs = nowMs
                    // A tennis game won moves the Sets child's match winner as much as the Games child's lines.
                    if (e.sportId == TENNIS_SPORT_ID) for (o in events.values) if (o.groupId == e.groupId) o.scoreAtMs = nowMs
                }
                e.score = s
            }
        }
        e.clock = clockText(rec) ?: e.clock
        if (channel == "dz") {
            e.volatileUntilMs = nowMs + VOLATILE_MS
            out += PinnChange(id, null, PinnChange.Kind.VOLATILE, nowMs)
        }
        // A period that is no longer open closes all of its markets.
        (rec["periods"] as? JsonArray)?.forEach { p ->
            val o = p as? JsonObject ?: return@forEach
            val status = (o["status"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            if (status == "open") return@forEach
            val period = (o["period"] as? JsonPrimitive)?.intOrNull ?: return@forEach
            for (l in e.lines.values) if (l.period == period && l.open) {
                l.open = false
                out += PinnChange(id, l.key, PinnChange.Kind.CLOSED, nowMs)
            }
        }
        (rec["markets"] as? JsonArray)?.forEach { m -> (m as? JsonObject)?.let { applyMarket(e, it, channel, nowMs, out) } }
    }

    private fun applyMarket(e: PinnEvent, m: JsonObject, channel: String, nowMs: Long, out: MutableList<PinnChange>) {
        val key = (m["key"] as? JsonPrimitive)?.contentOrNull ?: return
        val status = (m["status"] as? JsonPrimitive)?.contentOrNull ?: return   // the older participant-id shape has none: not read
        val type = when ((m["type"] as? JsonPrimitive)?.contentOrNull) {
            "moneyline" -> PinnLineType.MONEYLINE
            "spread" -> PinnLineType.SPREAD
            "total" -> PinnLineType.TOTAL
            else -> return
        }
        val period = (m["period"] as? JsonPrimitive)?.intOrNull ?: 0
        val version = (m["version"] as? JsonPrimitive)?.longOrNull
        val line = e.lines[key]
        if (status != "open") {
            if (line != null && line.open) {
                line.open = false
                out += PinnChange(e.id, key, PinnChange.Kind.CLOSED, nowMs)
            }
            return
        }
        // A danger-zone frame is not a price.
        if (channel == "dz") return
        if (line != null && version != null && version <= line.version) return
        if (version != null) versionListener?.invoke(e.id, key, version, nowMs)
        val sides = LinkedHashMap<PinnSide, Double>()
        var points: Double? = null
        (m["prices"] as? JsonArray)?.forEach { p ->
            val o = p as? JsonObject ?: return@forEach
            val side = PinnSide.of((o["designation"] as? JsonPrimitive)?.contentOrNull) ?: return@forEach
            val price = (o["price"] as? JsonPrimitive)?.doubleOrNull ?: return@forEach
            sides[side] = price
            val pts = (o["points"] as? JsonPrimitive)?.doubleOrNull
            if (pts != null && (type == PinnLineType.TOTAL || side == PinnSide.HOME)) points = pts
        }
        val shape = when (type) {
            PinnLineType.MONEYLINE -> sides.keys.containsAll(listOf(PinnSide.HOME, PinnSide.AWAY)) && sides.size in 2..3
            PinnLineType.SPREAD -> sides.keys == setOf(PinnSide.HOME, PinnSide.AWAY) && points != null
            PinnLineType.TOTAL -> sides.keys == setOf(PinnSide.OVER, PinnSide.UNDER) && points != null
        }
        if (!shape) return
        val raw = PinnSide.entries.filter { it in sides }.map { s -> impliedProbability(sides.getValue(s)) ?: return }
        val order = PinnSide.entries.filter { it in sides }
        val fairList = runCatching { Devig.devig(raw, method) }.getOrNull() ?: return
        val l = line ?: PinnLine(e.id, key, period, type, (m["isAlternate"] as? JsonPrimitive)?.booleanOrNull ?: false).also { e.lines[key] = it }
        val wasOpen = l.open
        val same = wasOpen && l.american == sides && l.points == points
        if (version != null) l.version = version
        l.open = true
        l.maxRisk = ((m["limits"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("amount")?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: l.maxRisk
        if (same) return
        l.american = sides
        l.points = points
        l.overround = raw.sum() - 1.0
        l.fair = order.zip(fairList).toMap()
        l.changedAtMs = nowMs
        l.history.addLast(PinnSnap(nowMs, l.fair))
        val keepMs = if (e.live) HISTORY_MS else PRE_HISTORY_MS
        while (l.history.size > HISTORY_MAX || (l.history.size > 1 && nowMs - l.history.first().atMs > keepMs)) l.history.removeFirst()
        out += PinnChange(e.id, key, PinnChange.Kind.PRICE, nowMs)
    }

    /** Both teams' scores: the matchup's own state (soccer, tennis) or else its parent's (basketball, hockey). */
    private fun readScore(rec: JsonObject): Pair<Int, Int>? {
        fun from(parts: JsonElement?): Pair<Int, Int>? {
            var h: Int? = null
            var a: Int? = null
            (parts as? JsonArray)?.forEach { p ->
                val o = p as? JsonObject ?: return@forEach
                val score = ((o["state"] as? JsonObject)?.get("score") as? JsonPrimitive)?.intOrNull ?: return@forEach
                when ((o["alignment"] as? JsonPrimitive)?.contentOrNull) {
                    "home" -> h = score
                    "away" -> a = score
                }
            }
            return if (h != null && a != null) h!! to a!! else null
        }
        return from(rec["participants"]) ?: from((rec["parent"] as? JsonObject)?.get("participants"))
    }

    private fun clockText(rec: JsonObject): String? {
        val own = (rec["state"] as? JsonObject)?.takeIf { it.isNotEmpty() }
        val parent = ((rec["parent"] as? JsonObject)?.get("state") as? JsonObject)?.takeIf { it.isNotEmpty() }
        return (own ?: parent)?.toString()
    }

    /** Drops matchups that have said nothing for [olderThanMs]: a kicked-off or pulled one stops being sent. */
    fun prune(nowMs: Long, olderThanMs: Long = 30 * 60_000L) {
        events.values.removeAll { nowMs - it.lastFrameAtMs > olderThanMs }
    }

    companion object {
        /** How long a danger-zone frame marks its matchup too jumpy to bet. */
        const val VOLATILE_MS = 3_000L
        const val TENNIS_SPORT_ID = 2
        const val GAMES_SUFFIX = " (Games)"
        const val HISTORY_MAX = 64
        const val HISTORY_MS = 120_000L

        /** A prematch line moves slowly: its readings are kept 20 minutes, not 2. */
        const val PRE_HISTORY_MS = 1_200_000L

        /** A Pinnacle American price as the probability it implies (vig in), or null for a price that can't be one. */
        fun impliedProbability(american: Double): Double? = when {
            american >= 100.0 -> 100.0 / (american + 100.0)
            american <= -100.0 -> -american / (-american + 100.0)
            else -> null
        }
    }
}

/** Parses one text frame, or null when it is not a JSON object. */
fun parsePinnFrame(text: String, json: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }): JsonObject? =
    runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
