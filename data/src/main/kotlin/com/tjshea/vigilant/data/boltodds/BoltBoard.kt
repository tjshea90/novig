package com.tjshea.vigilant.data.boltodds

import java.util.concurrent.ConcurrentHashMap

/**
 * What BoltOdds has said, kept as the current state of every game at every book (BOLTODDS_API.md §3): `game_update` replaces a game's outcomes, `line_update` edits them (a price of none removes the outcome),
 * `game_removed` drops the game, `sport_clear` and `book_clear` drop everything of a sport at a book or of a book ("connection drops on our backend"). The snapshot ([applyInitial]) only fills what the
 * live updates have not already said. It is the whole difference between this feed and a polled one: a quote here is as current as the connection, and [aliveAt] says whether it is.
 *
 * Only outcomes [BoltMarkets] can classify are kept (the rest are counted in [unmapped], by sport and name, for Diagnostics and Test key), so the board stays as small as what the scan can use.
 * Thread-safe: two sockets feed it, a scan reads it.
 */
class BoltBoard(
    private val clock: () -> Long = System::currentTimeMillis,
    /** Which kinds of line this board keeps: the lines socket keeps every kind but player props, the props socket only those (their games share names, so each has a board of its own). */
    private val keeps: (com.tjshea.vigilant.data.reference.LineKind) -> Boolean = { true },
) {
    private val lock = Any()
    private val games = HashMap<BoltGameKey, BoltBookGame>()

    /** "sportsbook|sport" -> the newest message time for it: the book is talking about this sport. */
    private val heard = ConcurrentHashMap<String, Long>()

    /** "sport | outcome name" -> how many outcomes with that name were dropped as unclassifiable (capped at [MAX_UNMAPPED] names). */
    val unmapped = ConcurrentHashMap<String, Int>()

    /** Messages applied, by action (Diagnostics). */
    val counts = ConcurrentHashMap<String, Int>()

    @Volatile var lastMessageMs: Long = 0L
        private set

    private fun count(action: String) { counts.merge(action, 1, Int::plus) }

    /** Applies one socket message. Returns true when the board changed. */
    fun apply(m: BoltMessage): Boolean = applyMessage(m).also { if (it) changeSeq++ }

    /** Counts every change to the board (the streaming engine looks at it to know a push arrived; a read of the number costs nothing). */
    @Volatile var changeSeq: Long = 0L
        private set

    private fun applyMessage(m: BoltMessage): Boolean {
        lastMessageMs = clock()
        return when (m) {
            is BoltMessage.GameUpdate -> { count(if (m.initial) "initial_state" else "game_update"); put(m.data, m.atMs, replace = true, initial = m.initial) }
            is BoltMessage.LineUpdate -> { count("line_update"); put(m.data, m.atMs, replace = false, initial = false) }
            is BoltMessage.GameRemoved -> {
                count("game_removed")
                val key = BoltGameKey(m.data.sportsbook ?: return false, m.data.game ?: return false)
                synchronized(lock) { games.remove(key) } != null
            }
            is BoltMessage.SportClear -> {
                count("sport_clear")
                synchronized(lock) { games.values.removeAll { it.sportsbook == m.sportsbook && it.sport == m.sport } }
            }
            is BoltMessage.BookClear -> {
                count("book_clear")
                synchronized(lock) { games.values.removeAll { it.sportsbook == m.sportsbook } }
            }
            is BoltMessage.Ping -> { count("ping"); false }
            is BoltMessage.Unknown -> { count("other:" + m.action.take(40)); false }
            else -> false
        }
    }

    /** A snapshot's games, behind every live update already applied. Returns how many games it added or refreshed. */
    fun applyInitial(list: List<BoltMessage.GameUpdate>): Int = list.count { apply(it) }

    private fun put(d: BoltData, at: Long, replace: Boolean, initial: Boolean): Boolean {
        val book = d.sportsbook ?: return false
        val game = d.game ?: return false
        val sport = d.sport ?: return false
        val league = BoltBooks.leagueOfSport(sport)
        // A snapshot item's own time may be the time the game last CHANGED; what it proves is that the book was read just now.
        val heardTs = if (initial) maxOf(at, clock()) else at
        heard[heardKey(book, sport)] = maxOf(heard[heardKey(book, sport)] ?: 0L, heardTs)
        val kept = ArrayList<BoltOutcome>(d.outcomes.size)
        for (o in d.outcomes) {
            val ref = league?.let { BoltMarkets.classify(it, o) }
            if (ref != null) { if (keeps(ref.kind)) kept += o } else noteUnmapped(sport, o.name)
        }
        val key = BoltGameKey(book, game)
        synchronized(lock) {
            val old = games[key]
            // A snapshot is older than the live updates that landed while it was on its way: it never overwrites them.
            if (initial && old != null && old.updatedAtMs >= at) return false
            val outcomes: Map<String, BoltOutcome> = if (replace) kept.associateBy { it.label } else {
                val m = HashMap(old?.outcomes ?: emptyMap())
                d.removed.forEach { m.remove(it) }
                kept.forEach { m[it.label] = it }
                m
            }
            if (outcomes.isEmpty() && old == null) return false
            games[key] = BoltBookGame(
                sport = sport, sportsbook = book, game = game,
                home = d.home ?: old?.home ?: return false, away = d.away ?: old?.away ?: return false,
                startMs = d.startMs ?: old?.startMs, outcomes = outcomes, updatedAtMs = maxOf(at, old?.updatedAtMs ?: 0L),
            )
            return true
        }
    }

    private fun noteUnmapped(sport: String, name: String) {
        val k = "$sport | $name"
        if (unmapped.size < MAX_UNMAPPED || unmapped.containsKey(k)) unmapped.merge(k, 1, Int::plus)
    }

    private fun heardKey(book: String, sport: String) = "$book|$sport"

    /** When [book] last said anything about [sport]; null if it never has. */
    fun heardAt(book: String, sport: String): Long? = heard[heardKey(book, sport)]

    /** The games of [sports] (BoltOdds sport names), a copy. */
    fun gamesOf(sports: Collection<String>): List<BoltBookGame> = synchronized(lock) { games.values.filter { it.sport in sports } }

    /** How many games and outcomes are held, and which books, for Diagnostics. */
    fun summary(): Summary = synchronized(lock) {
        Summary(games.size, games.values.sumOf { it.outcomes.size }, games.values.groupingBy { it.sportsbook }.eachCount(), games.values.groupingBy { it.sport }.eachCount())
    }

    data class Summary(val games: Int, val outcomes: Int, val byBook: Map<String, Int>, val bySport: Map<String, Int>)

    fun clear() {
        synchronized(lock) { games.clear() }
        heard.clear()
    }

    companion object {
        const val MAX_UNMAPPED = 300
    }
}
