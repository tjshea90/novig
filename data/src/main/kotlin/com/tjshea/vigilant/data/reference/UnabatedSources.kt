package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * What Unabated's Free API gives a scan (UNABATED_API.md §3), kept as REFERENCE EVENTS (small), never as the raw board. One read of a league's board is ONE request through the client's single 5.2-second
 * gate, so a scan never waits behind it: boards are cached ([GAMES_TTL_MS] / [PROPS_TTL_MS]) and served stale-while-revalidate (the last board is returned at once and a background read refreshes it for the next scan);
 * only the very first read of a league waits. Each quote is dated from when its board was READ ([UnabatedConvert]), so a stale board is aged out by Vigilant's freshness rules, not trusted. Discovery lookups
 * (leagues, bet types, periods, sources, teams, people) are cached and never use the odds interval.
 */
class UnabatedFeed(
    private val client: UnabatedClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Kept<T>(val atMs: Long, val value: T)
    class Read(val events: List<RefEvent>, val readAtMs: Long, val summary: String)

    private val meta = ConcurrentHashMap<String, Kept<Any>>()
    private val reads = ConcurrentHashMap<String, Read>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    @Volatile var lastBackgroundError: String? = null
        private set

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, ttlMs: Long, load: suspend () -> T): T {
        meta[key]?.takeIf { clock() - it.atMs < ttlMs }?.let { return it.value as T }
        return locks.getOrPut("meta|$key") { Mutex() }.withLock {
            meta[key]?.takeIf { clock() - it.atMs < ttlMs }?.let { return@withLock it.value as T }
            // A failed refresh keeps serving what it had: discovery data changes rarely.
            val old = meta[key]?.value as T?
            try { load().also { meta[key] = Kept(clock(), it) } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { old ?: throw e }
        }
    }

    suspend fun leagues(): List<UnLeague> = cached("leagues", DAY_MS) { client.leagues() }
    suspend fun betTypes(): Map<Int, UnBetType> = cached("bettypes", DAY_MS) { client.betTypes().associateBy { it.id } }
    private suspend fun periods(route: String): Map<Int, UnPeriod> = cached("periods|$route", DAY_MS) { client.periods(route).associateBy { it.id } }
    /**
     * A league's book ids. Unabated's own server often fails this call for one league ("Unexpected error while retrieving market sources", 9-15 s, Tj's Diagnostics 2026-10-11: NFL, CFB and NBA never
     * got a board for it); the ids are the same books everywhere, so a failure falls back to the other board of the league, then to any list already read, before the league is given up.
     */
    private suspend fun sources(route: String, type: String): List<UnSource> = try {
        cached("sources|$route|$type", 6 * 3_600_000L) { client.sources(route, type) }
    } catch (e: UnabatedFailure) {
        val other = if (type == "props") "straight" else "props"
        @Suppress("UNCHECKED_CAST")
        (meta["sources|$route|$other"]?.value as? List<UnSource>)
            ?: (meta.entries.firstOrNull { it.key.startsWith("sources|") }?.value?.value as? List<UnSource>)
            ?: runCatching { cached("sources|$route|$other", 6 * 3_600_000L) { client.sources(route, other) } }.getOrNull()
            ?: throw e
    }
    private suspend fun teams(route: String, type: String): Map<Long, String> = cached("teams|$route|$type", DAY_MS) { client.teams(route, type) }
    private suspend fun people(route: String): Map<Long, String> = cached("people|$route", 30 * 60_000L) { client.people(route, "props") }

    /** The route name of [league] in this key's `/league` list, or null when Unabated does not carry it. */
    suspend fun route(league: League): String? = UnabatedBooks.routeFor(league, leagues())

    /**
     * [league]'s [type] (`straight` or `props`) board as reference events, for the books in [settings]; null when the key's league list lacks the league. Returns the cached read at once when there is one
     * (refreshing it in the background once it is older than its TTL); the first read of a league waits for the board.
     */
    suspend fun read(league: League, type: String, settings: ScanSettings): Read? {
        val route = route(league) ?: return null
        val deadKey = "$route|$type"
        dead[deadKey]?.takeIf { it > clock() }?.let { return Read(emptyList(), clock(), "Unabated has no $route $type board right now") }
        val sources = try { sources(route, type) } catch (e: UnabatedFailure) { dead[deadKey] = clock() + DEAD_MS; return Read(emptyList(), clock(), "Unabated has no $route $type board (${e.message?.take(80)})") }
        val books = UnabatedBooks.sourceMap(sources, settings.referenceBooks.toSet())
        if (books.isEmpty()) return Read(emptyList(), clock(), "no wanted book on Unabated's ${route} $type sources")
        val key = "$route|$type|${books.values.sorted().joinToString(",")}|${settings.families.map { it.name }.sorted().joinToString(",")}|${settings.includeLive}"
        // Every key has its own 5-second interval, so N keys can read boards N times as often: the re-read time shrinks with them, down to the 15-second delay of the data itself.
        val ttl = ((if (type == "props") PROPS_TTL_MS else GAMES_TTL_MS) / client.keyCount().coerceAtLeast(1)).coerceAtLeast(MIN_TTL_MS)
        val have = reads[key]
        val load: suspend () -> Read = { load(league, route, type, books, sources, settings) }
        if (have != null && clock() - have.readAtMs < ttl) return have
        if (have != null) {
            if (refreshing.add(key)) scope.launch {
                try { refresh(key, ttl, load) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { lastBackgroundError = e.message?.take(160) } finally { refreshing.remove(key) }
            }
            return have
        }
        return refresh(key, ttl, load)
    }

    private val dead = ConcurrentHashMap<String, Long>()

    private suspend fun refresh(key: String, ttl: Long, load: suspend () -> Read): Read =
        locks.getOrPut("read|$key") { Mutex() }.withLock {
            reads[key]?.takeIf { clock() - it.readAtMs < ttl }?.let { return@withLock it }
            // Unabated saying it cannot build this board ("Unexpected error while retrieving odds for the provided filter") is an empty board for a while, not a failed scan.
            val r = try { load() } catch (e: UnabatedFailure) { dead[key.substringBefore("|", "") + "|" + key.split("|").getOrElse(1) { "" }] = clock() + DEAD_MS; Read(emptyList(), clock(), "Unabated: ${e.message?.take(100)}") }
            r.also { reads[key] = it }
        }

    private suspend fun load(league: League, route: String, type: String, books: Map<Long, String>, sources: List<UnSource>, settings: ScanSettings): Read {
        val betTypes = betTypes()
        val periods = periods(route)
        // Team names are a nicety (the board's own event name carries them): a failing teams list never fails the read.
        val teams = try { teams(route, "straight") } catch (e: UnabatedFailure) { emptyMap() }
        val people = if (type == "props") people(route) else emptyMap()
        // Only the bet types Vigilant prices are asked for: an unfiltered NFL straight board is 14.7 MB and an unfiltered props board is refused.
        val ids = if (type == "props") UnabatedConvert.propBetTypeIds(betTypes.values, league) else UnabatedConvert.gameBetTypeIds(betTypes.values)
        if (type == "props" && ids.isEmpty()) return Read(emptyList(), clock(), "no prop bet type with a Vigilant stat on this key")
        val chunks = if (ids.isEmpty()) listOf(emptyList()) else ids.chunked(PROP_CHUNK).take(MAX_CHUNKS)
        var readAt = clock()
        var updated: Long? = null
        val parts = chunks.map { chunk ->
            val raw = client.oddsRaw(route, type, chunk)
            readAt = clock()
            val board = UnabatedParser.board(raw, settings.includeLive)
            updated = board.lastUpdatedMs ?: updated
            val ctx = UnabatedContext(
                league, books, sources.filter { if (type == "props") it.propsStatusId == 1 else it.statusId == 1 }.map { it.id }.toSet(), betTypes, periods, teams, people,
                settings.families, settings.includeLive, readAt, board.lastUpdatedMs,
            )
            UnabatedConvert.events(board, ctx)
        }
        val events = UnabatedConvert.merge(parts)
        val board = UnBoard(emptyList(), updated)
        val summary = describe(events, readAt) + (board.lastUpdatedMs?.let { ", board updated ${(readAt - it) / 1000L}s before the read" } ?: "")
        client.lastReads["${league.novigName} $type"] = summary
        return Read(events, readAt, summary)
    }

    companion object {
        const val DAY_MS = 24 * 3_600_000L

        /** A games board is re-read at most this often (the Free data is already 15 s behind and unchanged lines stay current). */
        const val GAMES_TTL_MS = 120_000L
        const val PROPS_TTL_MS = 180_000L
        const val MIN_TTL_MS = 20_000L
        const val DEAD_MS = 10 * 60_000L
        const val PROP_CHUNK = 15
        const val MAX_CHUNKS = 6
    }
}

/** The leagues Vigilant asks Unabated about; whether the key carries each is read from `/league`. */
private val LEAGUES = setOf("NFL", "NCAAF", "NBA", "NCAAB", "WNBA", "MLB", "NHL")

/** Unabated's game lines (moneyline, spread, total, team totals, 1st-half/F5/1st inning, alternate lines) for the scan: the LAST source in [com.tjshea.vigilant.data.scanner.Scanner.SOURCE_ORDER] (a fallback). */
class UnabatedGamesSource(private val feed: UnabatedFeed, private val clock: () -> Long = System::currentTimeMillis) : ReferenceSource {
    override val id = ID
    override val displayName = "Unabated"
    override fun supports(league: League) = league.novigName in LEAGUES
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val r = feed.read(league, "straight", settings) ?: return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        return RefSnapshot(league.oddsApiSportKey, r.events, r.readAtMs, provider = ID)
    }

    companion object { const val ID = "unabated" }
}

/** Unabated's player props (DraftKings, FanDuel, MGM, Caesars, theScore, Fanatics), the last props source: a fallback to the paid feeds. */
class UnabatedPropsSource(private val feed: UnabatedFeed, private val clock: () -> Long = System::currentTimeMillis) : ReferenceSource {
    override val id = ID
    override val displayName = "Unabated props"
    override val propsOnly = true
    override val extraPropTypes: Set<String> get() = OpProps.ALL_TYPES
    override fun supports(league: League) = league.novigName in LEAGUES
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        if (MarketFamily.PLAYER_PROPS !in settings.families) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        val r = feed.read(league, "props", settings) ?: return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        return RefSnapshot(league.oddsApiSportKey, r.events, r.readAtMs, provider = ID)
    }

    companion object { const val ID = "unabated-props" }
}
