package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.match.PlayerNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * What PropLine's Streaming Lite socket has said, laid over the boards the engine read by REST (PSL, Tj 2026-10-25: "take full advantage of all the features, speed, and accuracy, including full usage
 * of the webhooks and/or websocket"; research/PROPLINE_STREAMING_2026-10-25.md). Pure state, fed by [PropLineStream]; read by [PropLineClient] and [PropLinePropsSource] through [overlay].
 *
 * - A `line_movement` (or a `line_added` that brings a line back) replaces that outcome's price and point, matched by PropLine's own `outcome_id` (the board carries it with `includeBookIds`). A
 *   two-way market whose sides disagree after a move (one side's push not in yet) is left out until both are.
 * - A `line_pulled` takes the outcome's market out of the board; a `market_suspended` (a book pulled a player's props pregame) takes that book's markets for that player out; a later move on the same
 *   outcome brings it back.
 * - **Freshness:** the docs say "while read_age_seconds stays near reread_seconds, we are still reading that book and no event means no change". So while the socket has been connected without a
 *   gap since a board was read ([covers]), a market of a book the heartbeat lists as still being read is current as of that book's last read (`pregame_read_at`), not only as of the REST read.
 * - Books PropLine's `/v1/freshness` calls stale for a class (game lines or props) for [OUTAGE_S] or more are left out of that class ([PropLineFreshness]).
 *
 * Everything is in the app's names: PropLine's book keys through [PropLineClient.bookKey], its sport keys through [oddsApiSport].
 */
class PropLineLive(private val clock: () -> Long = System::currentTimeMillis) {
    /** The newest word on one outcome: its price and point, or pulled. [atMs] is when it reached us. */
    data class Move(val decimal: Double?, val point: Double?, val atMs: Long, val pulled: Boolean)

    /** One heartbeat entry: when PropLine last started reading a book's pregame board in a sport, and how often it reads it. */
    data class Read(val readAtMs: Long, val readAgeS: Double, val rereadS: Double, val heardAtMs: Long)

    private val moves = ConcurrentHashMap<String, Move>()
    private val reads = ConcurrentHashMap<String, Read>()
    private val scratches = ConcurrentHashMap<String, Long>()
    private val added = ConcurrentHashMap<String, Long>()

    /** A player most books pulled (`market_suspended`, books agreeing): sport|player -> (books, when, PropLine event id). */
    data class Scratch(val books: Int, val atMs: Long, val eventId: String, val player: String)
    private val scratchedPlayers = ConcurrentHashMap<String, Scratch>()

    /** Book (app key) -> (game lines stale, props stale) as PropLine's `/v1/freshness` last said, only for an outage of [OUTAGE_S] or more. */
    @Volatile var staleBooks: Map<String, Pair<Boolean, Boolean>> = emptyMap()

    /** When the socket's coverage began without a gap since (0 = not connected): a board read at or after this is kept current by the pushes. */
    @Volatile var since: Long = 0L
        private set
    @Volatile var lastFrameMs: Long = 0L
        private set

    @Volatile var applied = 0L
        private set
    @Volatile var pulls = 0L
        private set
    @Volatile var confirmed = 0L
        private set
    @Volatile var gaps = 0L
        private set

    /** The socket is connected and has spoken in the last [SILENT_MS]. */
    fun live(now: Long = clock()): Boolean = since > 0L && now - lastFrameMs < SILENT_MS

    /** Whether a board read at [readAtMs] is kept current by the pushes now. */
    fun covers(readAtMs: Long, now: Long = clock()): Boolean = live(now) && since <= readAtMs

    /** A line was added in [sport] (the app's key) after [t]: a new line the held board does not have, so it is read again. */
    fun addedSince(sport: String, t: Long): Boolean = (added[sport] ?: 0L) > t

    /** The socket answered `ready`: [continuous] when it resumed from our last sequence with nothing lost; otherwise coverage starts now. */
    fun onReady(continuous: Boolean, now: Long = clock()) {
        lastFrameMs = now
        if (!continuous || since == 0L) { if (since != 0L) gaps++; since = now }
    }

    /** The socket closed: coverage pauses (a resume without loss continues it, [onReady]). */
    fun onClosed() { lastFrameMs = 0L }

    /** Coverage is lost for good (the server said events were truncated, or the stream was switched off): the next board read starts it again. */
    fun reset() { if (since != 0L) gaps++; since = 0L; lastFrameMs = 0L }

    fun heard(now: Long = clock()) { lastFrameMs = now }

    /** One `line_movement` / `line_added` / `line_pulled` payload. */
    fun onLine(type: String, d: JsonObject, now: Long = clock()) {
        heard(now)
        val id = d.str("outcome_id") ?: return
        when (type) {
            "line_pulled" -> { moves[id] = Move(null, null, now, pulled = true); pulls++ }
            else -> {
                val cur = d["current"] as? JsonObject ?: return
                val dec = PropLineClient.decimal((cur["price_american"] as? JsonPrimitive)?.doubleOrNull) ?: return
                moves[id] = Move(dec, (cur["point"] as? JsonPrimitive)?.doubleOrNull, now, pulled = false)
                // A move on a player's market brings that book's pulled markets for the player back (no restore event exists: the returning price IS the restore).
                val book = d.str("bookmaker_key")?.let(PropLineClient::bookKey)
                val ev = (d["event"] as? JsonObject)?.str("id")
                val player = d.str("player_name")
                if (book != null && ev != null && player != null) scratches.remove(scratchKey(ev, book, player))
                if (type == "line_added") d.str("sport_key")?.let { added[oddsApiSport(it)] = now }
            }
        }
    }

    /** One `market_suspended` payload: [book] pulled [subject]'s markets on that game. */
    fun onSuspended(d: JsonObject, now: Long = clock()) {
        heard(now)
        val book = d.str("bookmaker_key")?.let(PropLineClient::bookKey) ?: return
        val ev = (d["event"] as? JsonObject)?.str("id") ?: return
        val subject = d.str("subject") ?: return
        scratches[scratchKey(ev, book, subject)] = now
        val agree = (d["books_agreeing"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
        val sport = d.str("sport_key")?.let(::oddsApiSport) ?: return
        val k = "$sport|${PlayerNames.key(subject)}"
        val old = scratchedPlayers[k]
        if (old == null || agree >= old.books || now - old.atMs > SCRATCH_MS) scratchedPlayers[k] = Scratch(maxOf(agree, if (old != null && now - old.atMs <= SCRATCH_MS) old.books else 0), now, ev, subject)
    }

    /** One heartbeat's `books` list. */
    fun onHeartbeat(books: JsonArray?, now: Long = clock()) {
        heard(now)
        for (b in books.orEmpty()) {
            val o = b as? JsonObject ?: continue
            val book = o.str("bookmaker_key")?.let(PropLineClient::bookKey) ?: continue
            val sport = o.str("sport_key")?.let(::oddsApiSport) ?: continue
            val at = PropLineClient.ms(o.str("pregame_read_at")) ?: continue
            reads["$book|$sport"] = Read(at, (o["read_age_seconds"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, (o["reread_seconds"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, now)
        }
    }

    /**
     * Why a player's props must not be bet or bid on: [MIN_BOOKS] or more books pulled them on his game within [SCRATCH_MS] (PropLine: "3+ books pulling the same player on the same event is a late
     * scratch, minutes before it's announced"). Null when nothing says so.
     */
    fun scratched(sport: String, player: String, now: Long = clock()): String? {
        val s = scratchedPlayers["$sport|${PlayerNames.key(player)}"] ?: return null
        if (now - s.atMs > SCRATCH_MS || s.books < MIN_BOOKS) return null
        return "${s.books} books pulled ${s.player}'s props ${(now - s.atMs) / 60_000L} min ago (PropLine)"
    }

    /** When [book]'s board in [sport] is known current (the heartbeat, while the socket covers the read), or null. */
    private fun confirmedAt(book: String, sport: String, now: Long): Long? {
        val r = reads["$book|$sport"] ?: return null
        if (now - r.heardAtMs > HEARTBEAT_MAX_MS) return null
        // Still being read: the read is no older than about twice its usual gap (and never more than [READ_MAX_S]).
        if (r.readAgeS > maxOf(2.0 * r.rereadS, 120.0) || r.readAgeS > READ_MAX_S) return null
        return r.readAtMs.coerceAtMost(now)
    }

    /** [events] as read at [readAtMs], with every push since laid over them (see the class comment). */
    fun overlay(events: List<RefEvent>, readAtMs: Long, now: Long = clock()): List<RefEvent> {
        val cover = covers(readAtMs, now)
        val stale = staleBooks
        if (!cover && moves.isEmpty() && scratches.isEmpty() && stale.isEmpty()) return events
        prune(now)
        return events.map { e ->
            e.copy(markets = e.markets.mapNotNull { m -> market(e, m, readAtMs, now, cover, stale) })
        }
    }

    /**
     * One game as the engine's live lane holds it (PB19): every PropLine quote in [e] (its [RefBookMarket.source] is PropLine's) with the pushes since ITS OWN stamp laid over it, and dated by the heartbeat
     * while the socket has covered it since; the other feeds' quotes are left as they are. A quote with no stamp is taken as read [KEEP_MS] ago.
     */
    fun overlayEvent(e: RefEvent, now: Long = clock()): RefEvent {
        if (since == 0L && moves.isEmpty()) return e
        val stale = staleBooks
        var changed = false
        val markets = e.markets.mapNotNull { m ->
            if (m.source?.startsWith("propline") != true) return@mapNotNull m
            val readAt = m.lastUpdateMs ?: (now - KEEP_MS)
            val out = market(e, m, readAt, now, covers(readAt, now), stale)
            if (out !== m) changed = true
            out
        }
        return if (changed) e.copy(markets = markets) else e
    }

    private fun market(e: RefEvent, m: RefBookMarket, readAtMs: Long, now: Long, cover: Boolean, stale: Map<String, Pair<Boolean, Boolean>>): RefBookMarket? {
        val prop = m.kind == LineKind.PLAYER_PROP
        stale[m.bookKey]?.let { (lines, props) -> if (if (prop) props else lines) return null }
        if (prop && m.subject != null) scratches[scratchKey(e.id.removePrefix(PropLinePropsSource.PREFIX), m.bookKey, m.subject)]?.let { at -> if (at > readAtMs - SCRATCH_SLACK_MS) return null }
        var latest = m.lastUpdateMs ?: readAtMs
        var changed = false
        val quotes = m.quotes.map { q ->
            val mv = q.feedOutcomeId?.let { moves[it] }
            if (mv == null || !(cover || mv.atMs > readAtMs)) q
            else {
                if (mv.pulled) return null
                changed = true
                latest = maxOf(latest, mv.atMs)
                q.copy(decimalOdds = mv.decimal ?: q.decimalOdds, point = if (m.kind == LineKind.MONEYLINE) q.point else (mv.point ?: q.point))
            }
        }
        if (changed && !consistent(m.kind, quotes)) return null
        if (changed) applied++
        if (cover) confirmedAt(m.bookKey, e.sportKey, now)?.let { at -> if (at > latest) { latest = at; confirmed++ } }
        return if (!changed && latest == (m.lastUpdateMs ?: readAtMs)) m else m.copy(quotes = quotes, lastUpdateMs = latest)
    }

    /** A two-way market's sides still describe one line: a spread's handicaps are opposite, a total's or prop's numbers equal. */
    private fun consistent(kind: LineKind, q: List<RefQuote>): Boolean {
        if (q.size != 2) return true
        val (a, b) = q[0].point to q[1].point
        return when (kind) {
            LineKind.MONEYLINE -> true
            LineKind.SPREAD -> a != null && b != null && kotlin.math.abs(a + b) < 1e-9
            else -> a != null && b != null && kotlin.math.abs(a - b) < 1e-9
        }
    }

    private var prunedAt = 0L
    private fun prune(now: Long) {
        if (now - prunedAt < 60_000L) return
        prunedAt = now
        moves.entries.removeIf { now - it.value.atMs > KEEP_MS }
        scratches.entries.removeIf { now - it.value > SCRATCH_MS }
        scratchedPlayers.entries.removeIf { now - it.value.atMs > SCRATCH_MS }
    }

    /** One line for the engine's log and status. */
    fun describe(now: Long = clock()): String =
        "${moves.size} outcomes moved (${applied} laid over, ${pulls} pulled), ${reads.size} book boards on the heartbeat, ${scratchedPlayers.values.count { it.books >= MIN_BOOKS && now - it.atMs <= SCRATCH_MS }} players scratched, ${confirmed} markets confirmed current, ${gaps} gaps" +
            (staleBooks.filterValues { it.first || it.second }.keys.takeIf { it.isNotEmpty() }?.let { " · stale on PropLine: ${it.sorted().joinToString(",")}" } ?: "")

    /** Each book's last heartbeat read, for the status: book -> seconds since PropLine last started reading it (the oldest sport). */
    fun readAges(now: Long = clock()): Map<String, Int> =
        reads.entries.groupBy { it.key.substringBefore('|') }.mapValues { (_, v) -> v.maxOf { ((now - it.value.readAtMs) / 1000L).toInt().coerceAtLeast(0) } }

    companion object {
        /** No frame for this long: the socket is taken for dead (PropLine pings every 25 s and sends a heartbeat every 25 s). */
        const val SILENT_MS = 70_000L
        /** Moves are kept this long (a board is read again well within it). */
        const val KEEP_MS = 20 * 60_000L
        /** A scratch is believed this long. */
        const val SCRATCH_MS = 6 * 3_600_000L
        /** A scratch heard up to this long before a read still takes the read's markets out (a read can be served from PropLine's cache of a few seconds). */
        const val SCRATCH_SLACK_MS = 60_000L
        /** Books agreeing for a late scratch (PropLine's own advice). */
        const val MIN_BOOKS = 3
        /** A heartbeat older than this confirms nothing. */
        const val HEARTBEAT_MAX_MS = 90_000L
        /** A book read longer ago than this is never "current". */
        const val READ_MAX_S = 300.0
        /** A book PropLine has written nothing for in a class this long is out of that class (an outage, not a quiet board). */
        const val OUTAGE_S = 15 * 60

        private val TO_ODDS_API = mapOf("football_nfl" to "americanfootball_nfl", "football_ncaaf" to "americanfootball_ncaaf", "hockey_nhl" to "icehockey_nhl", "mma_ufc" to "mma_mixed_martial_arts")
        private val TO_NATIVE = TO_ODDS_API.entries.associate { (k, v) -> v to k }

        /** PropLine's sport key in the app's (The Odds API's) words. */
        fun oddsApiSport(native: String): String = TO_ODDS_API[native] ?: native
        /** The app's sport key in PropLine's own words (its filters accept both; its heartbeats speak its own). */
        fun nativeSport(oddsApi: String): String = TO_NATIVE[oddsApi] ?: oddsApi

        private fun scratchKey(eventId: String, book: String, player: String) = "$eventId|$book|${PlayerNames.key(player)}"

        private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.takeIf { it.isNotBlank() }
    }
}
