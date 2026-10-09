package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.tracker.BetGrader
import com.tjshea.vigilant.data.tracker.ParlayBooks
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Every other sportsbook's price for one player prop, from every odds source Vigilant has (Tj, 2026-09-30: "most of the time it says parlayapi
 * couldn't find other sports books with this bet. Is there a backup fall back provider or api that can be used so that I always see other
 * sports books odds for any bet when I click on it"). For a tapped bet's sheet only, never for fair odds.
 *
 * Why the sheet came up empty (measured with Tj's key 2026-09-30 on that day's MLB home-run props): the scans' readers keep a book's line only
 * when it prices both sides (a fair line needs both), but FanDuel, Caesars, Bovada and ProphetX list home runs "Over" only (138 of the 146
 * rows the scan's request got), and the books that price both (bet365, Hard Rock, Fliff, betPARX) weren't asked for. Here a one-sided book is
 * shown as CNO's game page shows it, and every real sportsbook a source carries is asked for (the pick'em apps' flat payouts left out).
 *
 * ParlayAPI's props (that bet's market only: 3 credits, shared [KEEP_MS] by every pick in the league and market) and PropLine's for the game
 * (1-2 of its free 1,000 a day) are read side by side; The Odds API's (about a credit) only when neither found another book. Each only while
 * it's on with a key ([on]). A book's price older than the freshness limit is kept apart ([Result.older]) and never counted.
 */
class OtherBooks(
    private val parlay: TheOddsApiClient?,
    private val propLine: PropLineClient?,
    private val oddsApi: TheOddsApiClient?,
    private val json: Json,
    private val on: suspend () -> Sources,
    private val clock: () -> Long = System::currentTimeMillis,
    /** SportsGameOdds Pro (Tj, 2026-10-09): while it is on it is the one source asked, with every book it carries (the paid ones rest: [Sources.sgo]). */
    private val sgo: SportsGameOddsClient? = null,
    /** OddsPapi (Tj, 2026-10-09): a league's player-prop events as OddsPapi has them now (the scan's own read, shared); while it is on it is the one source asked ([Sources.op]). */
    private val op: (suspend (League) -> List<RefEvent>)? = null,
) {
    /** Which sources may be asked now (on in Settings, with a key). */
    data class Sources(val parlay: Boolean, val propLine: Boolean, val oddsApi: Boolean, val sgo: Boolean = false, val op: Boolean = false)

    /** One book's price for the bet and its other side (American; null = not offered there), where it came from and when it was seen. */
    data class Line(val book: String, val odds: Int?, val otherOdds: Int?, val source: String, val seenAtMs: Long?) {
        val code: String get() = codeOf(book)
        val twoSided: Boolean get() = odds != null && otherOdds != null
    }

    /**
     * [view]: the books with a current price (what Vigilant's check counts), [older]: books whose last price is older than the freshness
     * limit (shown apart, never counted), [sources]: who answered, [why]: why there are none.
     */
    data class Result(val view: CnoBooksView?, val older: List<Line>, val sources: List<String>, val why: String?)

    private class Kept(val atMs: Long, val rows: List<Row>)

    /** One ParlayAPI /props row, kept for other picks in the same league and market. */
    internal data class Row(
        val book: String, val player: String, val stat: String?, val line: Double, val over: Int?, val under: Int?,
        val home: String, val away: String, val startsMs: Long?, val seenMs: Long?,
    )

    private val kept = HashMap<String, Kept>()
    private val mutex = Mutex()

    /** Calls made per source (tests, Diagnostics). */
    val calls = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * [selection] in [marketLabel] ("Player Home Runs", "Carson Kelly Over 0.5") in the game [eventName] ("Away @ Home") of [leagueName],
     * starting at [startsTs] (null: the soonest game of those teams); [marketKey]: the market's own name at ParlayAPI when known.
     */
    suspend fun view(leagueName: String, eventName: String, startsTs: Long?, marketLabel: String, selection: String, marketKey: String? = null): Result {
        val pick = BetGrader.pickOf(marketLabel, selection) as? BetGrader.Pick.Prop
            ?: return Result(null, emptyList(), emptyList(), "Only player props are looked up at the other books here")
        val league = Leagues.byNovigName(leagueName.trim())?.takeIf { it.oddsApiListed }
            ?: return Result(null, emptyList(), emptyList(), "No odds source Vigilant has carries $leagueName")
        val game = NovigText.parseMatchup(eventName) ?: return Result(null, emptyList(), emptyList(), "The game couldn't be read from \"$eventName\"")
        val sport = league.oddsApiSportKey
        val s = on()
        suspend fun attempt(source: String, read: suspend () -> List<Line>): Tried = try {
            calls.merge(source, 1, Int::plus)
            Tried(source, read(), null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Tried(source, null, readableError(e))
        }
        val first = coroutineScope {
            val a = if (s.parlay && parlay != null) async { attempt(PARLAY) { parlayLines(sport, pick, game.home, game.away, startsTs, marketKey) } } else null
            val b = if (s.propLine && propLine != null) async { attempt(PROPLINE) { propLineLines(sport, pick, game.home, game.away, startsTs) } } else null
            val c = if (s.sgo && sgo != null) async { attempt(SGO) { sgoLines(sport, pick, game.home, game.away, startsTs) } } else null
            val d = if (s.op && op != null) async { attempt(OP) { opLines(sport, pick, game.home, game.away, startsTs) } } else null
            listOfNotNull(a?.await(), b?.await(), c?.await(), d?.await())
        }
        val tried = first.toMutableList()
        // The Odds API costs credits per call: only when neither found another book.
        if (first.all { t -> t.lines.orEmpty().none { it.code != CnoBooks.NOVIG } } && s.oddsApi && oddsApi != null) {
            tried += attempt(ODDS_API) { oddsApiLines(sport, pick, game.home, game.away, startsTs) }
        }
        val now = clock()
        val lines = tried.flatMap { it.lines.orEmpty() }.filter { it.code != CnoBooks.NOVIG }
        val (fresh, older) = merge(lines, now, startsTs).partition { Freshness.fresh(it.seenAtMs, now, startsTs) }
        val answered = tried.filter { t -> t.lines.orEmpty().any { it.code != CnoBooks.NOVIG } }.map { it.source }
        val view = fresh.takeIf { it.isNotEmpty() }?.let { f ->
            CnoBooksView(
                bet = selection, otherBet = otherSide(selection),
                prices = f.map { CnoBookPrice(it.code, it.odds, null, it.otherOdds, null) },
                fetchedAtMs = f.mapNotNull { it.seenAtMs }.minOrNull() ?: now,
            )
        }
        val why = if (view != null) null else whyNone(tried, older, s, selection)
        return Result(view, older.sortedBy { -(it.seenAtMs ?: 0L) }, answered, why)
    }

    /** What one source said: its lines, or why it couldn't answer. */
    private class Tried(val source: String, val lines: List<Line>?, val error: String?)

    private fun whyNone(tried: List<Tried>, older: List<Line>, s: Sources, selection: String): String {
        if (tried.isEmpty()) return "No odds source is on with a key (ParlayAPI, PropLine or The Odds API in Settings)"
        if (older.isNotEmpty()) return "No book has a current price for $selection: only older ones (below)"
        val failed = tried.filter { it.error != null }.joinToString("; ") { "${it.source}: ${it.error}" }
        val asked = tried.joinToString(", ") { it.source }
        val off = listOfNotNull(PROPLINE.takeIf { !s.propLine }, ODDS_API.takeIf { !s.oddsApi })
        return "No other sportsbook prices $selection right now ($asked asked" +
            (if (off.isNotEmpty()) "; ${off.joinToString(" and ")} off or without a key" else "") + ")" +
            (if (failed.isNotEmpty()) ". $failed" else "")
    }

    // ---- ParlayAPI: that market's rows for the whole league, every real sportsbook ----------------------------------------------------------

    private suspend fun parlayLines(sport: String, pick: BetGrader.Pick.Prop, home: String, away: String, startsTs: Long?, marketKey: String?): List<Line> {
        val keys = (PropStats.parlayMarkets(sport).filter { it.second == pick.stat }.map { it.first } +
            listOfNotNull(marketKey, ParlayMarketKeys.keyFor(sport, pick.stat))).distinct()
        if (keys.isEmpty()) return emptyList()
        val cacheKey = "$sport|${keys.sorted().joinToString(",")}"
        val rows = mutex.withLock {
            kept[cacheKey]?.takeIf { clock() - it.atMs < KEEP_MS }?.rows ?: run {
                suspend fun ask() = parlay!!.parlayGet(
                    "/sports/$sport/props",
                    listOf(
                        "markets" to keys.joinToString(","), "oddsFormat" to "american", "limit" to ParlayProps.PAGE.toString(),
                        // An hour back: older prices are shown apart with their age, never counted.
                        "maxAgeSec" to (MAX_AGE_MS / 1000).toString(),
                    ),
                    cost = ParlayProps.COST, what = "$sport props (a pick's books)",
                ).value
                // "The props board is being rebuilt under load. Retry in a couple of seconds" (seen 2026-09-30): once, after the wait it names.
                val reply = ask().let { first -> if (first.busy) { kotlinx.coroutines.delay(first.retryAfterMs ?: BUSY_WAIT_MS); ask() } else first }
                if (reply.busy) throw ReferenceException("ParlayAPI's props are busy: try again in a minute")
                val parsed = if (reply.ok) parlayRows(reply.body, json, sport, clock()) else emptyList()
                kept[cacheKey] = Kept(clock(), parsed)
                parsed
            }
        }
        val game = gameOf(rows.map { Triple(it.home, it.away, it.startsMs) }, home, away, startsTs) ?: return emptyList()
        return rows.filter { r ->
            (r.stat == null || r.stat == pick.stat) && abs(r.line - pick.line) < 1e-6 && PlayerNames.same(r.player, pick.player) &&
                sameGame(r.home, r.away, r.startsMs, game)
        }.map { r -> Line(r.book, if (pick.over) r.over else r.under, if (pick.over) r.under else r.over, PARLAY, r.seenMs) }
            .filter { it.odds != null || it.otherOdds != null }
    }

    // ---- SportsGameOdds: that player's over/under at every book it carries, main and alternate lines -----------------------------------------

    private suspend fun sgoLines(sport: String, pick: BetGrader.Pick.Prop, home: String, away: String, startsTs: Long?): List<Line> {
        val client = sgo!!
        val league = Leagues.ALL.firstOrNull { it.oddsApiSportKey == sport && SgoBooks.supports(it) } ?: return emptyList()
        val leagueId = SgoBooks.leagueId(league) ?: return emptyList()
        val kind = SgoConvert.Sport.of(leagueId)
        val stat = SgoProps.statIds(kind).firstOrNull { SgoProps.novigStat(kind, it) == pick.stat } ?: return emptyList()
        val now = clock()
        val from = (startsTs ?: now) - SGO_WINDOW_MS
        val to = (startsTs ?: now) + (if (startsTs == null) 3 * 86_400_000L else SGO_WINDOW_MS)
        val events = client.eventsAll(
            listOf("leagueID" to leagueId, "startsAfter" to from.toString(), "startsBefore" to to.toString(), "limit" to SportsGameOddsClient.PAGE.toString(), "includeAltLines" to "true",
                "oddID" to "$stat-PLAYER_ID-game-ou-over,$stat-PLAYER_ID-game-ou-under"),
            maxPages = 2,
        ).events
        val game = gameOf(events.map { Triple(it.home, it.away, it.startsMs) }, home, away, startsTs) ?: return emptyList()
        val event = events.firstOrNull { sameGame(it.home, it.away, it.startsMs, game) } ?: return emptyList()
        val player = event.players.values.firstOrNull { PlayerNames.same(it.name, pick.player) } ?: return emptyList()
        val over = event.odds.firstOrNull { it.statId == stat && it.entityId == player.id && it.betType == "ou" && it.sideId == "over" }
        val under = event.odds.firstOrNull { it.statId == stat && it.entityId == player.id && it.betType == "ou" && it.sideId == "under" }
        fun SgoOdd?.at(book: String): SgoLine? = this?.byBook?.get(book)?.firstOrNull { it.available && it.american != null && it.point?.let { p -> abs(p - pick.line) < 1e-6 } == true }
        return (over?.byBook?.keys.orEmpty() + under?.byBook?.keys.orEmpty()).toSet().filter { it !in SgoBooks.EXCLUDED }.mapNotNull { book ->
            val o = over.at(book)
            val u = under.at(book)
            val mine = if (pick.over) o else u
            val other = if (pick.over) u else o
            if (mine == null && other == null) return@mapNotNull null
            val seen = listOfNotNull(mine?.updatedMs, other?.updatedMs).minOrNull()
            Line(SgoBooks.appKey(book), mine?.american?.let { it.roundToInt() }, other?.american?.let { it.roundToInt() }, SGO, seen)
        }
    }

    // ---- OddsPapi: that player's over/under at every book it carries, the scan's own read ---------------------------------------------------

    private suspend fun opLines(sport: String, pick: BetGrader.Pick.Prop, home: String, away: String, startsTs: Long?): List<Line> {
        val league = Leagues.ALL.firstOrNull { it.oddsApiSportKey == sport && OpBooks.supports(it) } ?: return emptyList()
        val events = op!!(league)
        val game = gameOf(events.map { Triple(it.home, it.away, it.commenceMs) }, home, away, startsTs) ?: return emptyList()
        val event = events.firstOrNull { sameGame(it.home, it.away, it.commenceMs, game) } ?: return emptyList()
        return event.markets.filter { m ->
            m.kind == LineKind.PLAYER_PROP && m.stat == pick.stat && m.line?.let { abs(it - pick.line) < 1e-6 } == true && m.subject?.let { PlayerNames.same(it, pick.player) } == true
        }.mapNotNull { m ->
            val over = m.quotes.firstOrNull { it.side == Side.OVER }?.decimalOdds?.let { Odds.decimalToAmerican(it) }
            val under = m.quotes.firstOrNull { it.side == Side.UNDER }?.decimalOdds?.let { Odds.decimalToAmerican(it) }
            val mine = if (pick.over) over else under
            val other = if (pick.over) under else over
            if (mine == null && other == null) null else Line(m.bookKey, mine, other, OP, m.lastUpdateMs)
        }
    }

    // ---- PropLine: the game's board for that market, every sportsbook it carries --------------------------------------------------------------

    private suspend fun propLineLines(sport: String, pick: BetGrader.Pick.Prop, home: String, away: String, startsTs: Long?): List<Line> {
        val client = propLine!!
        if (sport !in PropLineClient.SPORTS) return emptyList()
        val markets = PropLineProps.marketsFor(sport).filter { it.second == pick.stat }.map { it.first }.distinct()
        if (markets.isEmpty()) return emptyList()
        val listed = client.events(sport)
        val game = gameOf(listed.map { Triple(it.home, it.away, it.commenceMs) }, home, away, startsTs) ?: return emptyList()
        val event = listed.firstOrNull { sameGame(it.home, it.away, it.commenceMs, game) } ?: return emptyList()
        val body = client.eventRaw(sport, event.id.removePrefix(PropLinePropsSource.PREFIX), markets, PropLineClient.DISPLAY_BOOKS) ?: return emptyList()
        return eventLines(body, json, pick, markets.toSet(), american = true, source = PROPLINE, book = PropLineClient::bookKey)
    }

    // ---- The Odds API: the game's odds for that market (about a credit), the last resort -------------------------------------------------------

    private suspend fun oddsApiLines(sport: String, pick: BetGrader.Pick.Prop, home: String, away: String, startsTs: Long?): List<Line> {
        val client = oddsApi!!
        val markets = PropStats.oddsApiMarketsFor(sport, pick.stat)
        if (markets.isEmpty()) return emptyList()
        val listed = client.events(sport).value
        val game = gameOf(listed.map { Triple(it.home, it.away, it.commenceMs) }, home, away, startsTs) ?: return emptyList()
        val event = listed.firstOrNull { sameGame(it.home, it.away, it.commenceMs, game) } ?: return emptyList()
        val body = client.eventOddsRaw(sport, event.id, ODDS_API_BOOKS, markets) ?: return emptyList()
        return eventLines(body, json, pick, markets.toSet(), american = false, source = ODDS_API, book = { it })
    }

    companion object {
        const val SGO = "SportsGameOdds"
        const val OP = "OddsPapi"
        private const val SGO_WINDOW_MS = 6 * 3_600_000L
        const val PARLAY = "ParlayAPI"
        const val PROPLINE = "PropLine"
        const val ODDS_API = "The Odds API"

        /** A league's rows for one market are shared by every pick's sheet this long. */
        const val KEEP_MS = 2 * 60_000L

        /** A busy props board is asked again this much later (when it names no wait), once. */
        const val BUSY_WAIT_MS = 2_000L

        /** Prices up to this old are asked for; past the freshness limit they're shown apart, never counted. */
        const val MAX_AGE_MS = 60 * 60_000L

        /** The Odds API's US sportsbooks asked for (ten: one region's price). */
        val ODDS_API_BOOKS = listOf(
            "draftkings", "fanduel", "betmgm", "williamhill_us", "betrivers", "fanatics", "espnbet", "hardrockbet", "bovada", "betonlineag",
        )

        /** Pick'em apps: flat payouts, not a book's odds ("is_dfs_flat_payout"). */
        private val DFS = setOf(
            "prizepicks", "underdog", "pick6", "sleeper", "dabble", "parlayplay", "betr", "betr_picks", "chalkboard", "ownersbox", "boom",
            "splash", "hotstreak", "vivid", "epick", "draftkings_pick6",
        )

        /** A book's column code (CNO's where it has one, else its own title), as CNO's game page names them. */
        fun codeOf(book: String): String {
            val key = TheOddsApiClient.canonicalBook(book.lowercase())
            val code = ParlayBooks.codeOf(key)
            return if (code != key.uppercase()) code else TITLES[key] ?: TheOddsApiClient.KNOWN_BOOKMAKERS[key] ?: TheOddsApiClient.bookTitle(key)
        }

        /** Names for books CNO's page has no column code for. */
        private val TITLES = mapOf("parx" to "betPARX", "betparx" to "betPARX", "espnbet" to "theScore Bet", "ballybet" to "Bally Bet", "betway" to "Betway")

        /** "Carson Kelly Over 0.5" → "Carson Kelly Under 0.5" (the other side's name, as the table heads it). */
        fun otherSide(selection: String): String? = when {
            Regex("\\bOver\\b").containsMatchIn(selection) -> selection.replace(Regex("\\bOver\\b"), "Under")
            Regex("\\bUnder\\b").containsMatchIn(selection) -> selection.replace(Regex("\\bUnder\\b"), "Over")
            else -> null
        }

        private fun JsonElement?.obj() = this as? JsonObject
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()
        private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toBooleanStrictOrNull()

        /** An ISO time with Z or an offset ("…T14:00:00.000-04:00", as ParlayAPI's rows mix them). */
        internal fun isoMs(s: String?): Long? = s?.let {
            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
        }

        private fun american(v: Double?): Int? = when {
            v == null || !v.isFinite() -> null
            abs(v) >= 100 -> v.roundToInt()
            v > 1.0 && v < 100 -> Odds.decimalToAmerican(v)
            else -> null
        }

        /** ParlayAPI /props rows: full-game lines at real sportsbooks (one side or both), Novig and the pick'em apps left out. Pure. */
        internal fun parlayRows(body: String, json: Json, sport: String, now: Long): List<Row> {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
            val rows = ((root.obj()?.let { it["props"] ?: it["data"] ?: it["rows"] } ?: root) as? JsonArray).orEmpty().mapNotNull { it.obj() }
            return rows.mapNotNull { r ->
                val book = (r.str("bookmaker") ?: return@mapNotNull null).lowercase()
                if (book == "novig" || book in DFS || r.bool("is_dfs_flat_payout") == true || r.bool("dfs_normalized") == true) return@mapNotNull null
                r.str("period")?.let { if (!it.equals("FULL", true)) return@mapNotNull null }
                val market = r.str("market_key") ?: return@mapNotNull null
                val yesNo = market in PropStats.YES_NO || "anytime" in market
                val line = r.num("line") ?: r.num("point") ?: if (yesNo) 0.5 else return@mapNotNull null
                val over = american(r.num("over_price"))
                val under = american(r.num("under_price"))
                if (over == null && under == null) return@mapNotNull null
                Row(
                    book = book, player = (r.str("player") ?: r.str("player_name") ?: return@mapNotNull null).trim(),
                    stat = ParlayMarkets.statOf(sport, market, r.str("market")), line = line, over = over, under = under,
                    home = r.str("home_team") ?: return@mapNotNull null, away = r.str("away_team") ?: return@mapNotNull null,
                    startsMs = isoMs(r.str("commence_time")),
                    seenMs = r.num("age_seconds")?.let { now - (it * 1000).toLong() } ?: isoMs(r.str("last_update")),
                )
            }
        }

        /**
         * One game's answer in The Odds API's shape (PropLine's too): bookmakers → markets → outcomes (`name` Over/Under/Yes/No, `description`
         * the player, `point`, `price`). [pick]'s line at each book, one side or both. [american]: prices are American (PropLine), else decimal.
         */
        internal fun eventLines(
            body: String, json: Json, pick: BetGrader.Pick.Prop, markets: Set<String>, american: Boolean, source: String, book: (String) -> String,
        ): List<Line> {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull()?.obj() ?: return emptyList()
            val books = (root["bookmakers"] as? JsonArray).orEmpty().mapNotNull { it.obj() }
            return books.mapNotNull { b ->
                val key = book((b.str("key") ?: return@mapNotNull null).lowercase())
                if (key == "novig" || key in DFS) return@mapNotNull null
                var mine: Int? = null
                var other: Int? = null
                var seen: Long? = null
                for (m in (b["markets"] as? JsonArray).orEmpty().mapNotNull { it.obj() }) {
                    val mk = m.str("key") ?: continue
                    if (mk !in markets || m.str("suspended_at") != null || m.str("period") != null) continue
                    val yesNo = mk in PropStats.YES_NO || mk in PropLineProps.YES_NO
                    for (o in (m["outcomes"] as? JsonArray).orEmpty().mapNotNull { it.obj() }) {
                        if (!PlayerNames.same(o.str("description"), pick.player)) continue
                        val point = o.num("point") ?: if (yesNo) 0.5 else continue
                        if (abs(point - pick.line) > 1e-6) continue
                        val over = when (o.str("name")?.trim()?.lowercase()) {
                            "over", "yes" -> true
                            "under", "no" -> false
                            else -> continue
                        }
                        val raw = o.num("price")
                        val price = if (american) american(raw) else raw?.takeIf { it > 1.0 }?.let { Odds.decimalToAmerican(it) }
                        if (price == null) continue
                        if (over == pick.over) mine = price else other = price
                        seen = isoMs(m.str("last_update") ?: b.str("last_update")) ?: seen
                    }
                }
                if (mine == null && other == null) null else Line(key, mine, other, source, seen)
            }
        }

        /** The game among [games] (home, away, start) that is [home] vs [away]: the one nearest [startsTs], else the soonest. */
        private fun gameOf(games: List<Triple<String, String, Long?>>, home: String, away: String, startsTs: Long?): Triple<String, String, Long?>? =
            games.filter { (h, a, _) -> TeamMatcher.gameScore(home, away, h, a) > 0.0 }
                .filter { (_, _, s) -> startsTs == null || s == null || abs(s - startsTs) <= GAME_GAP_MS }
                .minByOrNull { (_, _, s) -> if (startsTs != null && s != null) abs(s - startsTs) else (s ?: Long.MAX_VALUE) }

        private fun sameGame(home: String, away: String, startsMs: Long?, game: Triple<String, String, Long?>): Boolean {
            val start = game.third
            return TeamMatcher.gameScore(home, away, game.first, game.second) > 0.0 &&
                (startsMs == null || start == null || abs(startsMs - start) <= GAME_GAP_MS)
        }

        /** A feed's start can differ from Novig's by a little; a doubleheader's second game is 3+ hours later. */
        private const val GAME_GAP_MS = 2 * 60 * 60_000L

        /**
         * One line per book: a current price first, then a two-sided one (it can be devigged), then the freshest (the sources were asked
         * ParlayAPI, PropLine, then The Odds API). Pinnacle and the exchanges lead, as on CNO's page, then the books pricing both sides. Pure.
         */
        fun merge(lines: List<Line>, now: Long, startsTs: Long?): List<Line> =
            lines.groupBy { it.code }.values.map { same ->
                same.sortedWith(
                    compareByDescending<Line> { Freshness.fresh(it.seenAtMs, now, startsTs) }.thenByDescending { it.twoSided }.thenByDescending { it.seenAtMs ?: 0L },
                ).first()
            }.sortedWith(compareBy<Line>({ LEAD.indexOf(it.code).let { i -> if (i < 0) LEAD.size else i } }, { !it.twoSided }, { it.code }))

        private val LEAD = listOf("PN", "CS", "PX", "KI")
    }
}
