package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What one scan reads from OddsPapi for a league, kept [TTL_MS] so the games source and the props source (and a bid cycle right after a scan) share ONE read (ODDSPAPI_API.md §6):
 * 1. `/fixtures/odds/main?tournamentId=` returns every game of the tournament with its main lines at every wanted book, in one request.
 * 2. When alternate lines or player props are wanted, `/fixtures/odds?fixtureId=` per game (10 requests a second keeps NFL's slate to a few seconds) brings the rest: alternates, team totals,
 *    halves and props. Player names come from `/players` once and are kept for good.
 * The book catalogue, tournaments and market catalogue are read once and kept (a day, six hours, a day). Each fixture is converted ([OpConvert]) the moment it arrives and dropped, so a league
 * never sits in memory as raw prices. A game whose own read fails keeps its main lines.
 */
class OddsPapiFeed(private val client: OddsPapiClient, private val clock: () -> Long = System::currentTimeMillis) {
    class LeagueRead(val atMs: Long, val games: List<RefEvent>, val props: List<RefEvent>)

    private class Held<T>(val atMs: Long, val value: T)

    private val catalog = java.util.concurrent.atomic.AtomicReference<Held<List<OpBookInfo>>?>(null)
    private val tournaments = java.util.concurrent.ConcurrentHashMap<Int, Held<List<OpTournament>>>()
    private val marketLists = java.util.concurrent.ConcurrentHashMap<Int, Held<List<OpMarket>>>()
    private val marketMaps = java.util.concurrent.ConcurrentHashMap<String, Pair<Held<List<OpMarket>>, OpMarkets>>()
    private val names = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val reads = java.util.concurrent.ConcurrentHashMap<String, LeagueRead>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    suspend fun bookCatalog(): List<OpBookInfo> {
        catalog.get()?.takeIf { clock() - it.atMs < DAY_MS }?.let { return it.value }
        val fresh = client.bookmakers()
        catalog.set(Held(clock(), fresh))
        return fresh
    }

    suspend fun tournamentFor(league: League): OpTournament? {
        val sport = OpBooks.sportId(league) ?: return null
        val held = tournaments[sport]?.takeIf { clock() - it.atMs < TOURNAMENT_MS } ?: Held(clock(), client.tournaments(sport)).also { tournaments[sport] = it }
        return OpBooks.tournamentFor(league, held.value)
    }

    suspend fun marketsFor(league: League): OpMarkets {
        val sport = OpBooks.sportId(league) ?: return OpMarkets(emptyList(), league.novigName)
        val held = marketLists[sport]?.takeIf { clock() - it.atMs < DAY_MS } ?: Held(clock(), client.markets(sport)).also { marketLists[sport] = it }
        val have = marketMaps[league.novigName]
        if (have != null && have.first === held) return have.second
        return OpMarkets(held.value, league.novigName).also { marketMaps[league.novigName] = held to it }
    }

    /** The slug -> Vigilant key table of the key's own catalogue (a name the table does not know is matched by the book's display name). */
    private fun keyTable(cat: List<OpBookInfo>): (String) -> String? {
        val map = cat.associate { it.slug to OpBooks.appKey(it.slug, it.name) }
        return { slug -> if (slug in map) map[slug] else OpBooks.appKey(slug) }
    }

    /** [league]'s read now, or the one made less than [TTL_MS] ago. Concurrent callers wait for the one in flight. */
    suspend fun read(league: League, settings: ScanSettings): LeagueRead {
        val key = league.novigName
        reads[key]?.takeIf { clock() - it.atMs < TTL_MS }?.let { return it }
        return locks.getOrPut(key) { Mutex() }.withLock {
            reads[key]?.takeIf { clock() - it.atMs < TTL_MS }?.let { return it }
            doRead(league, settings).also { reads[key] = it }
        }
    }

    private suspend fun doRead(league: League, settings: ScanSettings): LeagueRead {
        val now = clock()
        val tournament = tournamentFor(league) ?: throw ReferenceException("OddsPapi: no ${league.novigName} tournament in this key's catalogue")
        val markets = marketsFor(league)
        val wanted = OpBooks.wanted(settings.referenceBooks, settings.opExtraBooks)
        val cat = runCatching { bookCatalog() }.getOrDefault(emptyList())
        val slugs = OpBooks.slugsFor(wanted, cat)
        val appKeyOf = keyTable(cat)
        val bookParam = if (slugs.isEmpty()) emptyList() else listOf("bookmakers" to slugs.joinToString(","))
        val wantProps = MarketFamily.PLAYER_PROPS in settings.families
        val deep = settings.opAltLines || wantProps
        val until = Planner.horizon(settings, now) + 24 * 3_600_000L
        val main = OpParser.fixtures(client.get("/fixtures/odds/main", listOf("tournamentId" to tournament.id.toString()) + bookParam))
            .filter { f -> f.startMs?.let { it <= until } == true && (settings.includeLive || !f.started) }
        val games = ArrayList<RefEvent>()
        val props = ArrayList<RefEvent>()
        var deepRead = 0
        var deepFailed = 0
        for (m in main) {
            var source = m
            if (deep && deepRead < MAX_DEEP_GAMES) {
                deepRead++
                val full = try {
                    OpParser.fixtures(client.get("/fixtures/odds", listOf("fixtureId" to m.id) + bookParam)).firstOrNull()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    deepFailed++
                    null
                }
                if (full != null && full.prices.isNotEmpty()) source = full.copy(
                    // The main read knows the game's start and the books' states best; the deep one is the same game with more lines.
                    startMs = full.startMs ?: m.startMs, books = full.books.ifEmpty { m.books },
                )
            }
            if (wantProps) resolveNames(source)
            val sportKey = league.oddsApiSportKey
            val ref = OpConvert.toRef(source, markets, sportKey, wanted, now, names, games = true, props = wantProps, appKeyOf = appKeyOf) ?: continue
            val g = ref.copy(markets = ref.markets.filter { it.kind != LineKind.PLAYER_PROP && allowed(it, settings) && (settings.opAltLines || deep.not() || true) })
            val p = ref.copy(markets = ref.markets.filter { it.kind == LineKind.PLAYER_PROP && allowed(it, settings) })
            if (g.markets.isNotEmpty()) games += g
            if (p.markets.isNotEmpty()) props += p
        }
        client.lastReads["${league.novigName}"] = "${tournament.name} (id ${tournament.id}) · ${describe(games, now)}" +
            (if (wantProps) " · props: ${describe(props, now)}" else "") + (if (deepFailed > 0) " · $deepFailed game reads failed (main lines kept)" else "")
        return LeagueRead(now, games, props)
    }

    private suspend fun resolveNames(f: OpFixture) {
        val missing = f.prices.map { it.playerId }.filter { it != 0L && it !in names }.toSet()
        if (missing.isEmpty()) return
        try {
            names += client.players(missing)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The props of players without a name are left out; the next scan asks again.
        }
    }

    companion object {
        const val TTL_MS = 20_000L
        const val DAY_MS = 24 * 3_600_000L
        const val TOURNAMENT_MS = 6 * 3_600_000L

        /** Games a league reads in depth at most (a college slate is more than the phone needs in one scan). */
        const val MAX_DEEP_GAMES = 60

        /** Whether the scan's market families price [m] at all (a family switched off is not kept in memory). */
        fun allowed(m: RefBookMarket, s: ScanSettings): Boolean {
            val family = when (m.kind) {
                LineKind.MONEYLINE -> MarketFamily.MONEYLINE
                LineKind.SPREAD -> if (m.period == 0) MarketFamily.SPREAD else MarketFamily.FIRST_HALF
                LineKind.TOTAL -> if (m.period == 0) MarketFamily.TOTAL else MarketFamily.FIRST_HALF
                LineKind.TEAM_TOTAL -> MarketFamily.TEAM_TOTAL
                LineKind.PLAYER_PROP -> MarketFamily.PLAYER_PROPS
            }
            return family in s.families
        }
    }
}

/** OddsPapi's game lines for the scan (ODDSPAPI_API.md §6). A league OddsPapi has no feed for (tennis, MMA, soccer) is not [supports]ed: the other feeds keep it. */
class OpGamesSource(private val feed: OddsPapiFeed, private val clock: () -> Long = System::currentTimeMillis) : ReferenceSource {
    override val id = ID
    override val displayName = "OddsPapi"
    override val metered = true

    override fun supports(league: League) = OpBooks.supports(league)
    override fun reuseMs(settings: ScanSettings): Long = REUSE_MS

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val read = feed.read(league, settings)
        return RefSnapshot(league.oddsApiSportKey, read.games, read.atMs, provider = ID)
    }

    companion object {
        const val ID = "oddspapi"
        const val REUSE_MS = 20_000L
    }
}

/** OddsPapi's player props: the same read as the game lines (one request per game brings both), each book's over/under per player. */
class OpPropsSource(private val feed: OddsPapiFeed) : ReferenceSource {
    override val id = ID
    override val displayName = "OddsPapi props"
    override val metered = true
    override val propsOnly = true
    override val extraPropTypes: Set<String> get() = OpProps.ALL_TYPES

    override fun supports(league: League): Boolean = OpBooks.supports(league) && OpProps.supports(league.novigName)
    override fun reuseMs(settings: ScanSettings): Long = REUSE_MS

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        if (MarketFamily.PLAYER_PROPS !in settings.families) return RefSnapshot(league.oddsApiSportKey, emptyList(), System.currentTimeMillis(), provider = ID)
        val read = feed.read(league, settings)
        return RefSnapshot(league.oddsApiSportKey, read.props, read.atMs, provider = ID)
    }

    companion object {
        const val ID = "oddspapi-props"
        const val REUSE_MS = 30_000L
    }
}
