package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.JsonSplit
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads OddsPapi v4's `GET /v4/odds` (docs: get-odds): one game, EVERY book the plan carries in ONE request (`bookmakerOdds.<slug>.markets.<marketId>.outcomes.<outcomeId>.players.<playerId>`). The reply for a game
 * with all books and props is megabytes, so it is cut one book at a time ([JsonSplit.members]) and each book is turned into the v5-shaped [OpFixture] the converter already reads ([OpConvert]); the markets are
 * classified by the same catalogue, so v4 and v5 prices come out as the same reference events.
 */
object Op4Odds {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonObject.lng(k: String) = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull

    /** A game's header (no prices) and the text of each book's block, still unparsed. */
    class Game(val header: OpFixture, val books: List<Pair<String, String>>)

    fun parse(raw: String): Game? {
        val top = JsonSplit.members(raw) ?: return null
        val rest = top.filter { it.first != "bookmakerOdds" }
        val head = runCatching { json.parseToJsonElement("{" + rest.joinToString(",") { "\"${it.first}\":${it.second}" } + "}") }.getOrNull().obj() ?: return null
        val id = head.str("fixtureId") ?: return null
        val status = head.lng("statusId")?.toInt() ?: 0
        val header = OpFixture(
            id, head.lng("sportId")?.toInt(), head.lng("tournamentId"), head.str("tournamentName").orEmpty(), head.str("categoryName").orEmpty(),
            Op4Parser.iso(head.str("trueStartTime")) ?: Op4Parser.iso(head.str("startTime")), status, status == 1, head.lng("participant1Id"),
            head.str("participant1Name").orEmpty(), head.str("participant2Name").orEmpty(), (head["externalProviders"].obj())?.str("pinnacleId"), emptyMap(), emptyMap(), emptyList(),
        )
        val books = top.firstOrNull { it.first == "bookmakerOdds" }?.second?.let { JsonSplit.members(it) }.orEmpty()
        return Game(header, books)
    }

    /** One book's block as an [OpFixture] (its prices and its connection state); player names go into [names]. */
    fun book(game: Game, slug: String, text: String, names: MutableMap<Long, String>): OpFixture {
        val b = runCatching { json.parseToJsonElement(text) }.getOrNull().obj()
        val prices = ArrayList<OpPrice>()
        if (b != null) (b["markets"].obj())?.forEach { (marketId, mv) ->
            val m = mv.obj() ?: return@forEach
            val marketActive = m.bool("marketActive") ?: true
            (m["outcomes"].obj())?.forEach { (outcomeId, ov) ->
                (ov.obj()?.get("players").obj())?.forEach { (playerId, pv) ->
                    val p = pv.obj() ?: return@forEach
                    val pid = playerId.toLongOrNull() ?: 0L
                    p.str("playerName")?.let { if (pid != 0L) names[pid] = it }
                    prices += OpPrice(
                        slug, outcomeId.toLongOrNull() ?: return@forEach, pid, p.dbl("price"), p.bool("active") ?: true, marketActive, p.bool("mainLine") ?: false,
                        marketId.toLongOrNull(), p.dbl("limit"), Op4Parser.iso(p.str("changedAt")), Op4Parser.iso(p.str("bookmakerChangedAt")),
                    )
                }
            }
        }
        val active = b?.bool("bookmakerIsActive") ?: true
        val meta = OpBookMeta(hasOdds = active, stale = !active, suspended = b?.bool("suspended") ?: false, rotated = false)
        return game.header.copy(books = mapOf(slug to meta), prices = prices)
    }
}

/**
 * OddsPapi v4's fair-odds feed (Tj, 2026-10-11: "You made this for oddspapi v5 but I have v4"; "use as many [books] as possible"). The free plan is a small monthly allowance, so the feed spends it on the games that
 * matter NOW: each league's games in the scan window are listed (a cached billable request per 12 hours), and the nearest-starting ones are read with ONE `/v4/odds` request each (every book the plan carries), a game at most
 * every [READ_MS]. What it may spend is the user's own daily figure ([ApiGates.op4Fair]), never below the reserve the closing lines keep ([Op4Budget.mayBuyFair]). A game not read inside [KEEP_MS] gives no quote (the
 * app's freshness rule would drop it anyway). A game read is dated from when it was read.
 */
class Op4Feed(
    private val client: OddsPapi4Client,
    private val catalog: Op4Catalog,
    private val budget: Op4Budget,
    private val store: Op4Store,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    class LeagueRead(val atMs: Long, val games: List<RefEvent>, val props: List<RefEvent>)

    private class GameRead(val atMs: Long, val game: RefEvent?, val props: RefEvent?)

    private val reads = ConcurrentHashMap<String, LeagueRead>()
    private val games = ConcurrentHashMap<String, GameRead>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** What each league last read brought (Diagnostics). */
    val lastReads = ConcurrentHashMap<String, String>()

    suspend fun read(league: League, settings: ScanSettings): LeagueRead {
        val key = league.novigName
        reads[key]?.takeIf { clock() - it.atMs < SHARE_MS }?.let { return it }
        return locks.getOrPut(key) { Mutex() }.withLock {
            reads[key]?.takeIf { clock() - it.atMs < SHARE_MS }?.let { return it }
            doRead(league, settings).also { reads[key] = it }
        }
    }

    private fun fairSpentToday(): Int = store.get("fair|${clock() / OddsPapi4Client.DAY_MS}", OddsPapi4Client.DAY_MS)?.toIntOrNull() ?: 0
    private fun countFair() = store.put("fair|${clock() / OddsPapi4Client.DAY_MS}", (fairSpentToday() + 1).toString())

    private suspend fun doRead(league: League, settings: ScanSettings): LeagueRead = try {
        read2(league, settings)
    } catch (e: com.tjshea.vigilant.data.keys.CreditsHeldBackException) {
        // The allowance is down to its reserve: no quotes from this feed now, and no failed scan for it.
        lastReads[league.novigName] = "stopped: ${e.message}"
        LeagueRead(clock(), emptyList(), emptyList())
    } catch (e: com.tjshea.vigilant.data.keys.AllKeysExhaustedException) {
        lastReads[league.novigName] = "stopped: monthly requests used up"
        LeagueRead(clock(), emptyList(), emptyList())
    }

    private suspend fun read2(league: League, settings: ScanSettings): LeagueRead {
        val now = clock()
        val sport = OpBooks.sportId(league) ?: return LeagueRead(now, emptyList(), emptyList())
        val acc = client.lastAccount ?: runCatching { client.account() }.getOrNull()
        if (acc != null && acc.sportIds.isNotEmpty() && sport !in acc.sportIds) return LeagueRead(now, emptyList(), emptyList())
        val tournament = catalog.tournamentId(league, sport) ?: throw ReferenceException("OddsPapi Free: no ${league.novigName} tournament")
        val markets = catalog.markets(league, sport)
        val horizon = Planner.horizon(settings, now)
        val list = catalog.fixturesBetween(tournament, now - 3_600_000L, horizon + 6 * 3_600_000L)
            .filter { f -> (f.trueStartMs ?: f.startMs)?.let { it <= horizon + 3_600_000L } == true && (settings.includeLive || f.statusId == 0) && f.statusId <= 1 }
            .sortedBy { it.trueStartMs ?: it.startMs ?: Long.MAX_VALUE }
        val every = settings.gates.opAllBooks
        val catalogBooks = acc?.books?.keys?.map { OpBookInfo(it, it, null, null) }.orEmpty()
        val wanted = if (every && catalogBooks.isNotEmpty()) OpBooks.everyBook(catalogBooks) else OpBooks.wanted(settings.referenceBooks, settings.opExtraBooks)
        val slugs = if (every) emptyList() else OpBooks.slugsFor(wanted, catalogBooks)
        val wantProps = MarketFamily.PLAYER_PROPS in settings.families
        val perDay = settings.gates.op4Fair
        var bought = 0
        var missing = 0
        var stopped: String? = null
        val sportKey = league.oddsApiSportKey
        for (f in list) {
            val have = games[f.id]?.takeIf { now - it.atMs < READ_MS }
            if (have != null) continue
            if (!budget.mayBuyFair(perDay, fairSpentToday())) { stopped = "daily share of $perDay used"; break }
            val params = buildList {
                add("fixtureId" to f.id); add("verbosity" to "3")
                if (slugs.isNotEmpty()) add("bookmakers" to slugs.joinToString(","))
            }
            val raw = try {
                client.billable("/v4/odds", params, reserve = Op4Budget.RESERVE)
            } catch (e: com.tjshea.vigilant.data.keys.AllKeysExhaustedException) { stopped = "monthly requests used up"; break }
            catch (e: com.tjshea.vigilant.data.keys.CreditsHeldBackException) { stopped = "reserve kept"; break }
            catch (e: ReferenceException) {
                // A listed game OddsPapi holds no odds for yet (404 FIXTURE_NOT_FOUND, Tj's screen 2026-10-11) is no price, never a failed scan: it is left alone for a while and the next game is read.
                countFair(); bought++
                if (e.message?.contains("FIXTURE_NOT_FOUND") == true) { games[f.id] = GameRead(clock(), null, null); missing++; continue }
                stopped = e.message?.take(80); break
            }
            countFair(); bought++
            val readAt = clock()
            val g = Op4Odds.parse(raw) ?: continue
            val names = HashMap<Long, String>()
            val gameMarkets = ArrayList<RefBookMarket>(); val propMarkets = ArrayList<RefBookMarket>()
            var head: RefEvent? = null
            for ((slug, text) in g.books) {
                val bf = Op4Odds.book(g, slug, text, names)
                val ref = OpConvert.toRef(bf, markets, sportKey, wanted, readAt, names, games = true, props = wantProps) ?: continue
                if (head == null) head = ref
                gameMarkets += ref.markets.filter { it.kind != com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP }
                propMarkets += ref.markets.filter { it.kind == com.tjshea.vigilant.data.reference.LineKind.PLAYER_PROP }
            }
            val h = head ?: RefEvent(OpConvert.PREFIX + f.id, sportKey, f.trueStartMs ?: f.startMs ?: continue, g.header.p1, g.header.p2, emptyList())
            games[f.id] = GameRead(readAt, h.copy(markets = gameMarkets).takeIf { gameMarkets.isNotEmpty() }, h.copy(markets = propMarkets).takeIf { propMarkets.isNotEmpty() })
        }
        val fresh = list.mapNotNull { games[it.id]?.takeIf { r -> now - r.atMs < KEEP_MS || r.atMs >= now - KEEP_MS } }
        val g = fresh.mapNotNull { it.game }
        val p = fresh.mapNotNull { it.props }
        lastReads[league.novigName] = "${list.size} games listed, $bought read now, ${g.size} with prices${if (missing > 0) ", $missing listed with no odds yet" else ""} (${g.sumOf { it.markets.size }} quotes, ${g.flatMap { it.markets }.map { it.bookKey }.toSet().size} books)" +
            (stopped?.let { " · stopped: $it" } ?: "") + " · fair requests today ${fairSpentToday()}/${if (perDay >= Int.MAX_VALUE) "all" else perDay}"
        return LeagueRead(now, g, p)
    }

    companion object {
        /** A game is read again no sooner than this. */
        const val READ_MS = 10 * 60_000L
        /** A game read longer ago than this gives no quotes. */
        const val KEEP_MS = 20 * 60_000L
        /** Scans closer together than this share one league read. */
        const val SHARE_MS = 60_000L
    }
}

/** OddsPapi Free's game lines for the scan: every book of every game [Op4Feed] has read lately. */
class Op4GamesSource(private val feed: Op4Feed, private val clock: () -> Long = System::currentTimeMillis) : ReferenceSource {
    override val id = ID
    override val displayName = "OddsPapi Free"
    override val metered = true
    override fun supports(league: League) = OpBooks.supports(league)
    override fun reuseMs(settings: ScanSettings): Long = 60_000L
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val r = feed.read(league, settings)
        return RefSnapshot(league.oddsApiSportKey, r.games, r.atMs, provider = ID)
    }

    companion object { const val ID = "oddspapi_free" }
}

/** OddsPapi Free's player props for the same games (one read serves both). */
class Op4PropsSource(private val feed: Op4Feed, private val clock: () -> Long = System::currentTimeMillis) : ReferenceSource {
    override val id = ID
    override val displayName = "OddsPapi Free props"
    override val metered = true
    override val propsOnly = true
    override val extraPropTypes: Set<String> get() = OpProps.ALL_TYPES
    override fun supports(league: League) = OpBooks.supports(league)
    override fun reuseMs(settings: ScanSettings): Long = 60_000L
    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        if (MarketFamily.PLAYER_PROPS !in settings.families) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        val r = feed.read(league, settings)
        return RefSnapshot(league.oddsApiSportKey, r.props, r.atMs, provider = ID)
    }

    companion object { const val ID = "oddspapi_free-props" }
}
