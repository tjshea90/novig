package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/**
 * PropLine (prop-line.com, RESEARCH.md §22): thirty sportsbooks' prices in one feed, the free key
 * allowing **1,000 requests a day** (reset at midnight UTC; burst 10, 5 a second). One request
 * returns a whole league's game lines from every book asked for ([odds]); a game's player props are
 * one request more ([eventOdds], spent by [PropLinePropsSource]).
 *
 * The reply is The Odds API's format with American prices, plus what makes a price trustworthy:
 * a market the book pulled carries `suspended_at`, and an outcome whose `last_seen_at` is older than
 * its market's `last_update` was withdrawn (the book stopped sending it). Both are dropped. Team
 * totals ride the `totals` key with `team` naming the team.
 *
 * Only real sportsbooks price the fair line: the reference books picked in Settings, in The Odds
 * API's names (so sharp-book and book-picker settings mean the same thing for both feeds). Novig (the
 * thing being priced), the exchanges Vigilant reads directly (Kalshi, Polymarket) and the DFS apps
 * (their prices aren't bets) are never asked for.
 *
 * Every reply carries the key's `X-Daily-Used`/`X-Daily-Remaining`, which feed the usage meter; a
 * key at its daily cap rests until PropLine's `Retry-After` (the next UTC midnight).
 *
 * **Not yet verified live** (2026-09-27: the public demo key was at its daily cap): the parser
 * follows PropLine's published schema (openapi.json).
 */
class PropLineClient(
    private val http: OkHttpClient,
    private val pool: KeyPool,
    private val json: Json,
    private val baseUrl: String = "https://api.prop-line.com/v1",
    private val clock: () -> Long = System::currentTimeMillis,
    /** 4 a second: under PropLine's 5/s sustained rate, so its burst limit never trips. */
    private val minIntervalMs: Long = 250,
    /**
     * Ask for Novig's own prices alongside the books (they order Vigilant's Novig reads, §23.6). Off in
     * Vigilant MGM, which prices BetMGM and has no Novig reads to order.
     */
    private val relayNovig: Boolean = true,
    /**
     * Ask for each book's own event and selection ids and event page (`includeBookIds`, `includeLinks`):
     * Vigilant MGM builds BetMGM's bet-slip links from them. Off for Vigilant, whose requests stay as they were.
     */
    private val bookIds: Boolean = false,
    /**
     * The usage ledger, to read the plan of the key in use (`X-Daily-Limit`): a paid key (Hobby 5,000, Pro 25,000 a day) is paced at 10 requests a second instead of 4, and its props are asked for
     * every game in the window instead of the free key's few (Tj, 2026-10-16, TCL56: "make vigilant take full advantage and use of this pro tier"). Null: the free key's pace, as before.
     */
    private val meter: com.tjshea.vigilant.data.keys.UsageMeter? = null,
    /**
     * PropLine's Streaming Lite stream (PSL, Tj 2026-10-25): every answer is asked with `includeBookIds` so each outcome carries PropLine's `outcome_id` (the id `line_movement` pushes carry), and what the
     * engine holds is laid over by the socket's moves, pulls and scratches ([PropLineLive.overlay]). Null: no stream (the phone, or the socket off), the answers exactly as before.
     */
    private val live: PropLineLive? = null,
) : ReferenceSource {

    override val id = ID
    override val displayName = "PropLine"
    override val metered = true

    override fun supports(league: League) = league.oddsApiSportKey in SPORTS

    /**
     * PropLine's own pregame refresh is about a minute: scans closer together than this re-use the last board.
     */
    /** With the stream live, every scan asks (the answer comes from memory while the last read is young, [odds]); else PropLine's own minute-or-so refresh. */
    override fun reuseMs(settings: ScanSettings): Long = if (live?.live() == true) 0L else REUSE_MS

    /** With the stream live an ask is mostly a memory read (the pushes laid over the last board), so the hot refresh carries the pushes into pricing; never on a key without a stream. */
    override fun hotNow(): Boolean = live?.live() == true

    /** The last league boards read (sport -> read time, answer), laid over by the stream while it is live. */
    private val leagueBoards = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Answer<Board>>>()

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val markets = marketsFor(settings.families)
        val books = books(settings)
        val sport = league.oddsApiSportKey
        if (markets.isEmpty() || books.isEmpty()) return RefSnapshot(sport, emptyList(), clock(), provider = ID)
        val ask = "$markets|$books"
        // With the stream live and continuous since the last read, that read stays current (each move, pull and scratch is laid over it): read again only every [STREAM_REREAD_MS] (new games, new
        // books, a game's first board, which the stream does not send line by line) or when the stream says a line was added.
        live?.let { lv ->
            val have = leagueBoards["$sport|$ask"]
            if (have != null && lv.covers(have.first, clock()) && clock() - have.first < STREAM_REREAD_MS && !lv.addedSince(sport, have.first)) {
                val b = have.second
                return RefSnapshot(sport, lv.overlay(b.value.events, have.first, clock()), clock(), b.remaining, b.used, ID, novig = b.value.novig)
            }
        }
        // Novig rides along in the same request (no extra cost): its prices order the scan's own
        // Novig reads, and are kept apart from the books that make the fair line.
        val readAt = clock()
        val parts = bookParts(asked(books)).map { part ->
            call(
                "/sports/$sport/odds",
                listOf("markets" to markets.joinToString(","), "bookmakers" to part.joinToString(",")) + idParams(),
                notFound = Board(emptyList(), emptyList()),
            ) { parseBoard(it, json, sport) }
        }
        val answer = Answer(merge(parts.map { it.value }), parts.last().remaining, parts.last().used)
        remember(sport, answer.value.events)
        if (live != null) leagueBoards["$sport|$ask"] = readAt to answer
        val events = live?.overlay(answer.value.events, readAt, clock()) ?: answer.value.events
        return RefSnapshot(sport, events, clock(), answer.remaining, answer.used, ID, novig = answer.value.novig)
    }

    /**
     * One league's spreads and totals for one part of the game ([period]: PropLine's `h1`, `f5` ...), every fair-line book's, each market marked period 1 ([PropLinePeriodSource]); the stream's moves laid
     * over it like the full-game board's (its outcome ids are the same kind).
     */
    suspend fun periodOdds(sport: String, period: String, books: List<String>): Answer<List<RefEvent>> {
        val readAt = clock()
        val parts = bookParts(books).map { part ->
            call(
                "/sports/$sport/odds",
                listOf("markets" to "spreads,totals", "period" to period, "bookmakers" to part.joinToString(",")) + idParams(),
                notFound = Board(emptyList(), emptyList()),
            ) { parseBoard(it, json, sport, period) }
        }
        val board = merge(parts.map { it.value })
        val events = live?.overlay(board.events, readAt, clock()) ?: board.events
        return Answer(events.filter { it.markets.isNotEmpty() }, parts.last().remaining, parts.last().used)
    }

    /** The stream laid over [events] read at [readAtMs] (the props source's held boards); unchanged without a stream. */
    fun overlay(events: List<RefEvent>, readAtMs: Long): List<RefEvent> = live?.overlay(events, readAtMs, clock()) ?: events

    /** Whether the stream keeps a read made at [readAtMs] current now. */
    fun streamCovers(readAtMs: Long): Boolean = live?.covers(readAtMs, clock()) == true

    /**
     * The league's upcoming games (PropLine's ids, teams, start times): the last board's when it's
     * recent, else one request to `/events`.
     */
    suspend fun events(sportKey: String): List<RefEvent> {
        listed.withLock { boards[sportKey]?.takeIf { clock() - it.first < LIST_REUSE_MS }?.let { return it.second } }
        val answer = call("/sports/$sportKey/events", emptyList(), notFound = emptyList()) { parseEvents(it, json, sportKey) }
        remember(sportKey, answer.value)
        return answer.value
    }

    /** One game's [markets] (player props) from [books]. Null when PropLine no longer lists the game. */
    suspend fun eventOdds(sportKey: String, eventId: String, markets: List<String>, books: List<String>): RefEvent? =
        eventBoard(sportKey, eventId, markets, books)?.events?.singleOrNull()

    /**
     * [eventOdds] with Novig's own prices for the same game alongside (same request: [Board.novig]). With a paid key a long list of prop markets (NFL: 17, each with alternate ladders from many books) goes
     * in requests of at most [CHUNK] markets, so no one answer is heavy enough to time out (2026-10-16: "HTTP 504" on the first Pro scan); a free key's 1,000 a day keeps one request per game.
     */
    suspend fun eventBoard(sportKey: String, eventId: String, markets: List<String>, books: List<String>): Board? {
        require(markets.isNotEmpty()) { "No markets to ask for" }
        if (plan() == null || markets.size <= CHUNK) return eventBoardOnce(sportKey, eventId, markets, books)
        var first: Board? = null
        val ev = ArrayList<RefBookMarket>()
        val nv = ArrayList<RefBookMarket>()
        var gone = true
        for (part in markets.chunked(CHUNK)) {
            val b = eventBoardOnce(sportKey, eventId, part, books) ?: continue
            gone = false
            if (first == null) first = b
            b.events.singleOrNull()?.let { ev += it.markets }
            b.novig.singleOrNull()?.let { nv += it.markets }
        }
        if (gone || first == null) return null
        return Board(first.events.map { it.copy(markets = ev) }, first.novig.map { it.copy(markets = nv) })
    }

    /**
     * Player props for EVERY game of [sportKey] at once (bulk `/sports/{sport}/odds?markets=<prop keys>`): PropLine's docs (2026-10-22) say a slate is one call, not one per game, and send
     * `X-PropLine-Hint` to a key that reads several games one by one. Asked in parts of at most [CHUNK] markets (an answer heavy enough to time out was seen on one game's 17 NFL markets);
     * the parts' markets are merged per game. Throws when a part fails, so the caller can read the games one by one instead.
     */
    suspend fun bulkProps(sportKey: String, markets: List<String>, books: List<String>): Board {
        require(markets.isNotEmpty()) { "No markets to ask for" }
        val boards = ArrayList<Board>()
        for (part in markets.chunked(CHUNK)) for (bookPart in bookParts(asked(books))) {
            boards += call(
                "/sports/$sportKey/odds",
                listOf("markets" to part.joinToString(","), "bookmakers" to bookPart.joinToString(",")) + idParams(),
                notFound = Board(emptyList(), emptyList()),
            ) { parseBoard(it, json, sportKey) }.value
        }
        return merge(boards)
    }

    private suspend fun eventBoardOnce(sportKey: String, eventId: String, markets: List<String>, books: List<String>): Board? {
        val boards = bookParts(asked(books)).mapNotNull { part ->
            call(
                "/sports/$sportKey/events/$eventId/odds",
                listOf("markets" to markets.joinToString(","), "bookmakers" to part.joinToString(",")) + idParams(),
                notFound = null,
            ) { raw -> parseEventBoard(raw, json, sportKey) }.value
        }
        return if (boards.isEmpty()) null else merge(boards)
    }

    /**
     * One game's [markets] from [books] as PropLine sent it, one answer per part of at most [MAX_BOOKS] books (a tapped bet's sheet reads each with one-sided books kept: [OtherBooks]); empty when
     * PropLine no longer lists the game.
     */
    suspend fun eventRaw(sportKey: String, eventId: String, markets: List<String>, books: List<String>): List<String> {
        require(markets.isNotEmpty()) { "No markets to ask for" }
        return bookParts(books).mapNotNull { part ->
            call(
                "/sports/$sportKey/events/$eventId/odds",
                listOf("markets" to markets.joinToString(","), "bookmakers" to part.joinToString(",")),
                notFound = null,
            ) { raw -> raw }.value
        }
    }

    /**
     * PropLine's own +EV read for one game (`/events/{id}/ev`, Hobby+; cached 45 s on its side): every line's no-vig fair probability from the sharpest anchor and each asked book's EV% at the same
     * line. [books] narrows the PRICES (never the anchor): `novig` is the +EV at Novig. Null when PropLine no longer lists the game. One request.
     */
    suspend fun evRaw(sportKey: String, eventId: String, books: String = NOVIG): String? =
        call("/sports/$sportKey/events/$eventId/ev", listOf("bookmakers" to books), notFound = null) { raw -> raw }.value

    /** Every book's price for every line of one game (`/events/{id}/best-line`, Hobby+): the cross-check of a +EV line at Novig. [markets] narrows it. One request. */
    suspend fun bestLineRaw(sportKey: String, eventId: String, markets: List<String>): String? =
        call("/sports/$sportKey/events/$eventId/best-line", if (markets.isEmpty()) emptyList() else listOf("markets" to markets.joinToString(",")), notFound = null) { raw -> raw }.value

    /** How often a player went over his posted line (`/players/{name}/trends`, Hobby+): last 5/10/20/50 splits, streak, average actual, for [market]. One request; null when unknown. */
    suspend fun trendsRaw(sportKey: String, player: String, market: String): String? =
        call("/sports/$sportKey/players/${java.net.URLEncoder.encode(player, "UTF-8").replace("+", "%20")}/trends", listOf("market" to market), notFound = null) { raw -> raw }.value

    /** A sport's recent games with their scores (`/scores`, free): the PropLine grading source's day list. One request. */
    suspend fun scoresRaw(sportKey: String): String? =
        call("/sports/$sportKey/scores", listOf("daysFrom" to "3"), notFound = null) { raw -> raw }.value

    /** The sport's current injury report (`/sports/{sport}/injuries`, free on any key; ESPN's list refreshed every 10 minutes, conference reports for college). One request. */
    suspend fun injuriesRaw(sportKey: String): String? =
        call("/sports/${PropLineInjuries.sportOf(sportKey) ?: sportKey}/injuries", emptyList(), notFound = null) { raw -> raw }.value

    /** One game's official box score (`/events/{id}/stats`, free): player_name, stat_type, stat_value rows. One request. */
    suspend fun statsRaw(sportKey: String, eventId: String): String? =
        call("/sports/$sportKey/events/$eventId/stats", emptyList(), notFound = null) { raw -> raw }.value

    /** The books a call asks for: the picked books, and Novig's relay unless it's off. */
    private fun asked(books: List<String>): List<String> = if (relayNovig) books + NOVIG else books

    /** The book-id and event-page parameters, only when [bookIds] is on. */
    private fun idParams(): List<Pair<String, String>> = when {
        bookIds -> listOf("includeBookIds" to "true", "includeLinks" to "true")
        // The stream's moves are matched to the board by PropLine's own outcome id, which only comes with includeBookIds.
        live != null -> listOf("includeBookIds" to "true")
        else -> emptyList()
    }

    /** One reply read once: the fair-line books' games, and the same games with Novig's prices only. */
    class Board(val events: List<RefEvent>, val novig: List<RefEvent>)

    private val listed = Mutex()
    private val boards = HashMap<String, Pair<Long, List<RefEvent>>>()

    private suspend fun remember(sportKey: String, events: List<RefEvent>) = listed.withLock {
        boards[sportKey] = clock() to events.map { it.copy(markets = emptyList()) }
    }

    /** A call's result plus the key's daily figures. */
    class Answer<T>(val value: T, val remaining: Int?, val used: Int?)

    private val spacing = Mutex()
    private var lastCallAt = 0L

    /** The paid key's day: its daily limit and what it has used, from the ledger; null when no key with a limit above the free one is known. */
    data class Plan(val limit: Int, val used: Int, val tier: String)

    fun plan(): Plan? {
        val ks = meter?.flow?.value?.providers?.get(com.tjshea.vigilant.data.keys.QuotaPolicy.PROPLINE.id)?.keys ?: return null
        val best = ks.values.filter { u -> (u.limit ?: 0) > PropLineAccount.FREE_LIMIT && !u.refused }.maxByOrNull { it.limit ?: 0 } ?: return null
        val limit = best.limit ?: return null
        return Plan(limit, best.used, PropLineAccount.tierOf(limit))
    }

    /** Requests a second the plan allows us to use: about two thirds of the sustained rate (Streaming Lite 35 a second: 25; Pro 20: 10; Hobby 10: 6.7; free 5: 4). */
    private fun intervalMs(): Long = plan()?.let { if (it.limit >= 250_000) 40L else if (it.limit >= 25_000) 100L else 150L } ?: minIntervalMs

    /** How many games' props are read at once: PropLine allows 20 requests in flight a key and advises a pool of 8-10 (2026-10-24 docs); the free key one at a time. */
    fun width(): Int = plan()?.let { if (it.limit >= 250_000) 10 else 6 } ?: 1

    /**
     * Whether a paid key has room for a consumer taking up to [share] of the plan, now: what the key has used today is at or under the share of the day's requests that the engine's working hours have
     * run through ([activeDayFraction]) plus a 5% headroom. The quota day runs 00:00 to 00:00 UTC (8 pm ET), the engine works 08:00 to 01:00 ET, so a clock-linear day would hand the hours the engine sleeps
     * to the ones it works; this spends the day's requests only across the hours that read them (Tj, 2026-10-16: "pace the propline api so that I can use it throughout the day without it running out very fast").
     * Tiers: game lines are always read (a handful of requests); player props for every game while under [PROPS_SHARE] of that pace; the +EV sweep and its cross-check while under [EV_SHARE]. Over the
     * line a consumer falls back to its own small cap (props: the setting's games a scan) rather than stopping. Always false for a free key.
     */
    fun roomFor(share: Double, now: Long = clock()): Boolean {
        val p = plan() ?: return false
        return p.used <= p.limit * activeDayFraction(now) * share + p.limit * 0.05
    }

    /** Player props for every game in the window. */
    fun roomForEveryGame(now: Long = clock()): Boolean = roomFor(PROPS_SHARE, now)

    /** The +EV-at-Novig sweep (one request per game plus one for the cross-check of games that have lines). */
    fun roomForEv(now: Long = clock()): Boolean = roomFor(EV_SHARE, now)


    private suspend fun <T> call(path: String, params: List<Pair<String, String>>, notFound: T, parse: (String) -> T): Answer<T> {
        spacing.withLock {
            val wait = lastCallAt + intervalMs() - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastCallAt = System.currentTimeMillis()
        }
        // A heavy answer (a whole league's game lines from every book, a game's 500 prop markets) can outlast the app's 30-second call limit, and PropLine answers 502/503/504 under load (2026-10-16: the first
        // scan on the Pro key got "PropLine NFL: timeout" and "HTTP 504" and PropLine, the plan Tj pays for, delivered nothing that scan): the call gets a minute, and a timeout or a gateway error is tried again
        // twice (a second or two apart) before it is an error.
        var attempt = 0
        while (true) {
            try {
                return callOnce(path, params, notFound, parse)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                if (++attempt > RETRIES) throw e
            } catch (e: TransientHttp) {
                if (++attempt > RETRIES) throw ReferenceException("PropLine HTTP ${e.code}")
            }
            delay(1_500L * attempt)
        }
    }

    private class TransientHttp(val code: Int) : Exception("HTTP $code")

    /** The app's client with a minute for a call (the shared one gives 30 s), keeping its interceptors. */
    private val slowHttp: OkHttpClient by lazy {
        http.newBuilder().callTimeout(75, java.util.concurrent.TimeUnit.SECONDS).readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    private suspend fun <T> callOnce(path: String, params: List<Pair<String, String>>, notFound: T, parse: (String) -> T): Answer<T> {
        return pool.execute(cost = 1) { key ->
            val url = "$baseUrl$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            slowHttp.newCall(Request.Builder().url(url).header("X-API-Key", key).get().build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                val remaining = response.header("X-Daily-Remaining")?.trim()?.toIntOrNull()
                val used = response.header("X-Daily-Used")?.trim()?.toIntOrNull()
                val retryMs = response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)
                when {
                    response.code == 429 && body.contains("daily_limit_exceeded") ->
                        KeyAttemptResult.Depleted("daily limit reached", retryMs)
                    response.code == 429 -> KeyAttemptResult.RateLimited(retryMs ?: 1_000L, "burst limit")
                    // More than 20 requests in flight on this key: try again in a second.
                    response.code == 503 && retryMs != null -> KeyAttemptResult.RateLimited(retryMs, "too many at once")
                    response.code == 401 -> KeyAttemptResult.Invalid("HTTP 401, key refused")
                    response.code == 403 -> throw ReferenceException("PropLine: that needs a paid plan (HTTP 403)")
                    // A league out of season or a game that's gone: not the key's fault.
                    response.code == 404 -> KeyAttemptResult.Success(Answer(notFound, remaining, used), remaining = remaining, used = used)
                    response.code in 502..504 -> throw TransientHttp(response.code)
                    !response.isSuccessful -> throw ReferenceException("PropLine HTTP ${response.code}")
                    else -> KeyAttemptResult.Success(Answer(parse(body), remaining, used), remaining = remaining, used = used)
                }
            }
        }
    }

    companion object {
        const val ID = "propline"

        /** Leagues PropLine carries, by The Odds API's sport key (which PropLine accepts as an alias). */
        val SPORTS = setOf(
            "americanfootball_nfl", "americanfootball_ncaaf", "baseball_mlb", "basketball_wnba",
            "basketball_nba", "basketball_ncaab", "icehockey_nhl", "mma_mixed_martial_arts",
        )

        const val REUSE_MS = 2 * 60_000L

        /**
         * With the stream live, a board read is kept current by its moves and read again only this often: for new games, new books and a game's first board (PropLine sends those whole on `/odds`, never
         * line by line), and inside the 5-minute quote limit even for a book the heartbeat does not confirm. Streaming Lite's 250,000 requests a day cover this many times over.
         */
        // PS4 (Tj, 2026-10-25: "uses usage freely and liberally"): 90 s, inside PropLine's own 60-90 s pre-match re-read of the polled books (was 3 minutes; ~6 leagues x 40 an hour more of 250,000 a day).
        const val STREAM_REREAD_MS = 90_000L

        /** The shares of the day's pace the consumers may reach (the rest is headroom for game lines, rechecks, other books and research). */
        // Tj, 2026-10-17: "propline can use 25000 requests per day. it isn't getting anywhere near that. vigilant can use it liberally": props ran out of their 70% share at 3.6k used by 03:00Z and fell back to the small cap.
        const val PROPS_SHARE = 0.95
        const val EV_SHARE = 1.0

        /**
         * The share of the quota day (00:00 to 00:00 UTC) that the engine's working hours ([com.tjshea.vigilant.data.cloud.EngineHours]: 08:00 to 01:00 ET) have run through at [now]: 0 at the reset, 1 at
         * the end of the day, growing only while the engine works. Pure. Falls back to the plain clock when the window never meets the day.
         */
        fun activeDayFraction(now: Long): Double {
            val dayMs = 24 * 3_600_000L
            val start = (now / dayMs) * dayMs
            val end = start + dayMs
            val zone = com.tjshea.vigilant.data.cloud.EngineHours.ZONE
            val day = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            var total = 0L
            var elapsed = 0L
            for (d in -1L..1L) {
                val from = day.plusDays(d).atTime(com.tjshea.vigilant.data.cloud.EngineHours.OPEN).atZone(zone).toInstant().toEpochMilli()
                val to = day.plusDays(d + 1).atTime(com.tjshea.vigilant.data.cloud.EngineHours.CLOSE).atZone(zone).toInstant().toEpochMilli()
                val lo = maxOf(from, start)
                val hi = minOf(to, end)
                if (hi > lo) {
                    total += hi - lo
                    elapsed += (minOf(hi, now) - lo).coerceAtLeast(0L)
                }
            }
            return if (total <= 0L) (now - start).toDouble() / dayMs else elapsed.toDouble() / total
        }

        /** How long a game's props are re-used before they are bought again: 2 minutes for a game within 3 hours of its start (its lines move most), 5 minutes for a farther one (quotes are then 5 + PropLine's own 1.5 minutes old, inside the 10-minute limit for far games). */
        fun propsReuseMs(startsAtMs: Long, now: Long): Long =
            if (startsAtMs - now > com.tjshea.vigilant.data.scanner.Freshness.FAR_OFF_MS) 5 * 60_000L else com.tjshea.vigilant.data.scanner.Freshness.MAX_REUSE_MS

        /** The most prop markets one request asks for with a paid key. */
        const val CHUNK = 6

        /** A timeout or a 502/503/504 is tried again this many times. */
        const val RETRIES = 2

        /** Novig's key on PropLine: asked for alongside the books, never one of them. */
        const val NOVIG = "novig"
        private const val LIST_REUSE_MS = 5 * 60_000L

        /** PropLine's book keys where they differ from The Odds API's. */
        // BookMaker.eu, Betfair Exchange and theScore Bet under the names the rest of the app uses for them (SharpAPI's and The Odds API's), so the sharp-book list and the company groups mean the same
        // book whichever feed sent it (theScore is Penn's desk, one company with ESPN Bet: BookCompany).
        private val TO_PROPLINE = mapOf("hardrockbet" to "hardrock", "bookmaker" to "bookmaker_eu", "betfair_ex_eu" to "betfair_ex", "espnbet" to "thescorebet")
        private val FROM_PROPLINE = TO_PROPLINE.entries.associate { (k, v) -> v to k }

        /** Books PropLine lists that never feed a fair line here (see the class comment). */
        val EXCLUDED = setOf(
            "novig", "kalshi", "polymarket", "polymarket_us", "prophetx",
            "underdog", "prizepicks", "sleeper", "dabble", "parlayplay",
        )

        /** Every sportsbook and exchange PropLine lists, for a tapped bet's sheet ([OtherBooks]): its fair-line books, and ProphetX. */
        val DISPLAY_BOOKS: List<String> get() = KNOWN.toList() + "prophetx"

        /** Whether PropLine carries [book] (The Odds API's name) as a sportsbook that can price a fair line. */
        fun carries(book: String): Boolean = (TO_PROPLINE[book] ?: book).let { it in KNOWN && it !in EXCLUDED }

        /** PropLine books for the reference books picked in Settings (The Odds API's names). */
        /** Every sportsbook PropLine lists that can price a fair line ([ApiGates.propLineAllBooks]), else the picked ones. */
        fun books(settings: ScanSettings): List<String> =
            if (settings.gates.propLineAllBooks) KNOWN.filter { it !in EXCLUDED }.sorted() else books(settings.referenceBooks)

        fun books(referenceBooks: List<String>): List<String> =
            referenceBooks.map { TO_PROPLINE[it] ?: it }.filter { it !in EXCLUDED && it in KNOWN }.distinct()

        /**
         * PS2 (Tj, 2026-10-25: "even though they offer dozens of sports books odds, most of my scanned results only show odds for a few sports books"): books PropLine prices the US leagues with (24-81 of
         * the ~85 NFL/NCAAF/NBA/NHL games on 2026-10-25, research/propline_coverage_2026-10-25.md) whose trading desk is documented: Heritage, TAB (Tabcorp), Tipico, Winamax, Betclic and Betano/Stoiximan
         * trade their own lines; Paddy Power (Flutter), LiveScore Bet and Superbet (Kambi), Ladbrokes AU and SuperSport (Entain) and Betway ZA (Super Group) join their company's group in
         * [com.tjshea.vigilant.engine.BookCompany], so they fill a line the group's other books lack and are never counted twice. The rest of PropLine's list stays out until its desk is known.
         */
        private val ADDED_2026_10_25 = setOf(
            "heritage", "tab_au", "tipico", "winamax", "betclic_pt", "stoiximan",
            "paddypower", "livescorebet", "superbet_ro", "superbet_pl", "ladbrokes_au", "supersport", "betway_za",
        )

        /**
         * PX9 (Tj 2026-10-25: "Use as many sports books as possible for all markets including alternate lines ... not double counting same companies or same odds providers. This may need matching"): the books
         * PropLine prices the US leagues with that the PS2 list left out "until their desks are known" (research/propline_books_2026-10-25.md). `tools/propline_books.py` measured every one of PropLine's ~100
         * books on 2026-10-25 over NBA, NHL, NFL, NCAAF and MLB (about 12,000 two-way lines, alternates included): how many lines each posts, how far its no-vig chance sits from the other books' median (mean
         * under 1.3 points for all of these: as close as DraftKings or FanDuel), its margin (under 10%), its quote age (median under 5 minutes), and which other book it tracks (the same no-vig chance within 0.2
         * points on 150+ shared lines: 97-100% for BetRivers / Unibet / LiveScore Bet (Kambi), BetOnline / LowVig, Placard / Solverde, 80-83% for Superbet PL / Superbet RO / Napoleon, and no pair above 77%
         * among these). Each is its own desk under the 80% rule, except the two groups [com.tjshea.vigilant.engine.BookCompany] now names: Superbet with Napoleon, Fortuna CZ with Fortuna PL. Left out: books over
         * 10% margin (Placard, Solverde, Olybet, Jouez Sport, PMU, Teapuesto), books whose median quote is over 5 minutes old (Bet9ja, SupaBets, Marca Apuestas, Wplay, Meridianbet, Betathome, PalmerBet,
         * PlayAlberta, Codere MX) and SX Bet (its own source here).
         */
        private val ADDED_2026_10_25_MEASURED = setOf(
            "tonybet", "sts", "pamestoixima", "sportybet_ng", "rebet", "sportzino", "bet25", "thrillzz", "bet99", "fortuna_cz", "totalbet", "sportium", "psk", "danskespil",
            "miseojeu", "napoleon", "fortuna_pl", "playnow", "veikkaus", "admiralbet_de", "synot", "betr_au", "ladbrokes_be", "novibet", "sazka", "strendus", "soccerbet", "betika", "norsktipping",
            "starsports", "toto", "playup", "lvbet", "netbet_ro",
        )

        /**
         * PropLine's sportsbooks and exchanges that can price a fair line (its bookmaker list, 2026-09-27), plus the sharp books its 2026-10-24 list carries that Vigilant had not asked it for: SBOBET,
         * BookMaker.eu and Betfair Exchange (all three on the sharp-book list), and theScore Bet. The ~70 European/Latin American/African books (pregame soccer and main lines, several on one odds
         * provider each) and the sweepstakes books are left out until their desks are known ([com.tjshea.vigilant.engine.BookCompany]: books on one provider must never be counted twice).
         */
        private val KNOWN = setOf(
            "pinnacle", "draftkings", "fanduel", "betmgm", "fanatics", "betrivers", "hardrock", "bovada",
            "betonlineag", "lowvig", "betus", "unibet", "onexbet", "marathon", "fliff", "betway",
            "matchbook", "smarkets", "sbobet", "bookmaker_eu", "betfair_ex", "thescorebet",
        ) + ADDED_2026_10_25 + ADDED_2026_10_25_MEASURED

        fun marketsFor(families: Collection<MarketFamily>): List<String> = buildList {
            if (MarketFamily.MONEYLINE in families) add("h2h")
            if (MarketFamily.SPREAD in families) add("spreads")
            // Team totals ride the "totals" key.
            if (MarketFamily.TOTAL in families || MarketFamily.TEAM_TOTAL in families) add("totals")
        }

        /**
         * PropLine sends null for anything it has no value for (its game list's `bookmakers`, a
         * book's `title`, a market's `description`…; its schema allows it), so every field is read
         * as optional, and each game is read on its own: one odd game never costs the rest of the
         * board (Tj's phone, 2026-09-27: "Expected start of the array '[' … at path: $[0].bookmakers").
         */
        fun parseEvents(raw: String, json: Json, sportKey: String): List<RefEvent> = parseBoard(raw, json, sportKey).events

        /** A league board, read once: the books' games, and Novig's prices for them apart. */
        fun parseBoard(raw: String, json: Json, sportKey: String, period: String? = null): Board {
            val lenient = lenient(json)
            // One game at a time, never the whole board as a tree (about eight times the JSON's size; PropLine's answers average 4 MB: Tj, 2026-10-10, heap 91%).
            com.tjshea.vigilant.data.JsonSplit.elements(raw)?.let { split ->
                val events = ArrayList<PlEvent>(split.size)
                for (i in 0 until split.size) runCatching { lenient.decodeFromString(PlEvent.serializer(), split.element(i)) }.getOrNull()?.let { events += it }
                return Board(events.mapNotNull { it.toDomain(sportKey, period) }, if (period != null) emptyList() else events.mapNotNull { it.novigDomain(sportKey) })
            }
            val items = lenient.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonArray ?: return Board(emptyList(), emptyList())
            val events = items.mapNotNull { e -> runCatching { lenient.decodeFromJsonElement(PlEvent.serializer(), e) }.getOrNull() }
            return Board(events.mapNotNull { it.toDomain(sportKey, period) }, if (period != null) emptyList() else events.mapNotNull { it.novigDomain(sportKey) })
        }

        fun parseEvent(raw: String, json: Json, sportKey: String): RefEvent? = parseEventBoard(raw, json, sportKey).events.singleOrNull()

        fun parseEventBoard(raw: String, json: Json, sportKey: String): Board {
            val lenient = lenient(json)
            val e = runCatching { lenient.decodeFromJsonElement(PlEvent.serializer(), lenient.parseToJsonElement(raw)) }.getOrNull()
                ?: return Board(emptyList(), emptyList())
            return Board(listOfNotNull(e.toDomain(sportKey)), listOfNotNull(e.novigDomain(sportKey)))
        }

        private fun lenient(json: Json) = Json(from = json) { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

        internal fun ms(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        /** American odds to decimal: +150 -> 2.5, -200 -> 1.5. Null for 0 or anything not a price. */
        fun decimal(american: Double?): Double? = when {
            american == null || !american.isFinite() -> null
            american >= 100.0 -> 1.0 + american / 100.0
            american <= -100.0 -> 1.0 + 100.0 / -american
            else -> null
        }

        /** A PropLine book key in the app's words (BookMaker.eu is `bookmaker`, theScore Bet `espnbet`, ...). */
        fun bookKey(propLineKey: String): String = FROM_PROPLINE[propLineKey] ?: propLineKey
    }
}

/**
 * Player props from every sportsbook PropLine carries, one request per game (RESEARCH.md §22), for
 * the Novig games that list props, haven't started, and start within [ScanSettings.bookPropWindowHours],
 * soonest first. At most [ScanSettings.propLineGamesPerScan] games a scan (or every one), each re-used for [REUSE_MS] (PropLine
 * refreshes props every minute or so; the free 1,000 a day is the limit that matters). Only the prop
 * types Novig lists for a game are asked for.
 */
class PropLinePropsSource(private val client: PropLineClient) : ReferenceSource {

    override val id = ID
    override val propsOnly = true
    override val displayName = "PropLine props"
    override val metered = true
    override val needsCatalog = true
    override val extraPropTypes: Set<String> get() = PropLineProps.STATS

    override fun supports(league: League) = PropLineProps.marketsFor(league.oddsApiSportKey).isNotEmpty()

    /** Held games are laid over by the stream on every ask (only games past their re-use window are bought), so the hot refresh may ask while the stream is live. */
    override fun hotNow(): Boolean = client.hotNow()

    /** A game's props, bought at [atMs] with request [ask]; [novig] = Novig's own prices from the same reply. */
    private data class Bought(val novigEventId: String, val ref: RefEvent, val atMs: Long, val ask: String, val novig: RefEvent? = null)

    private val mutex = Mutex()
    private val bought = HashMap<String, Bought>()

    /** Bulk slate reads made and failed (each failure read the games one by one): the scan diagnostics' word on [PropLineClient.bulkProps]. */
    @Volatile var bulkReads = 0
        private set
    @Volatile var bulkFailures = 0
        private set
    private var scanAt = Long.MIN_VALUE
    private var toBuy: Map<String, List<String>> = emptyMap()

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = odds(league, settings, ScanContext())

    override suspend fun odds(league: League, settings: ScanSettings, context: ScanContext): RefSnapshot = mutex.withLock {
        val now = context.now
        val sport = league.oddsApiSportKey
        val books = PropLineClient.books(settings)
        val ask = books.sorted().toString()
        // The scanner calls once per league with the same clock reading: the first call of a scan
        // spends the budget across every league at once, soonest games first.
        if (now != scanAt) {
            scanAt = now
            bought.values.removeAll { now - it.atMs >= PropLineClient.propsReuseMs(it.ref.commenceMs, now) }
            toBuy = if (books.isEmpty()) emptyMap() else allocate(settings, context, ask)
        }
        val leagueEvents = context.novigEvents.filter { it.league == league.novigName }
        val buying = leagueEvents.filter { it.eventId in toBuy }
        var failure: Exception? = null
        if (buying.isNotEmpty()) {
            try {
                // A paid key reading several games of one sport: the whole slate in one bulk call per part of the markets (PropLine's advice, 2026-10-22), not one call per game.
                // A failed bulk read falls back to the games one by one below.
                val bulk: PropLineClient.Board? = if (client.plan() != null && buying.size >= BULK_MIN_GAMES) {
                    val markets = buying.flatMap { toBuy[it.eventId].orEmpty() }.distinct()
                    try { client.bulkProps(sport, markets, books) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { bulkFailures++; null }
                } else null
                val listed = bulk?.events ?: client.events(sport)
                val matches = com.tjshea.vigilant.data.scanner.Planner.matchEvents(buying, listOf(RefSnapshot(sport, listed, now, provider = ID)))
                if (bulk != null) {
                    bulkReads++
                    val novigOf = bulk.novig.associateBy { it.id }
                    for (m in matches) {
                        val ref = m.refEvent ?: continue
                        val wanted = toBuy[m.event.eventId].orEmpty().toSet()
                        val stats = PropLineProps.marketsFor(sport).filter { it.first in wanted }.mapTo(HashSet()) { it.second }
                        // Only the markets this game was asked for (the slate's union carries others), so a game's board is what its own read would have been.
                        fun keep(e: RefEvent?) = e?.markets.orEmpty().filter { mk -> mk.stat in stats }
                        bought[m.event.eventId] = Bought(m.event.eventId, ref.copy(markets = keep(ref)), now, ask, novig = novigOf[ref.id]?.let { n -> ref.copy(markets = keep(n)) })
                    }
                }
                val unread = if (bulk != null) emptyList() else matches
                // A paid key reads several games at once (the client's own spacing still sets the pace, and PropLine allows 20 in flight); the free key one at a time, as before.
                val width = client.width()
                val gate = kotlinx.coroutines.sync.Semaphore(width)
                val results: List<Triple<String, RefEvent, Result<PropLineClient.Board?>>> = kotlinx.coroutines.coroutineScope {
                    unread.sortedBy { it.event.startsTs }.mapNotNull { m ->
                        val ref = m.refEvent ?: return@mapNotNull null
                        val markets = toBuy[m.event.eventId].orEmpty()
                        if (markets.isEmpty()) return@mapNotNull null
                        val eventId = m.event.eventId
                        async {
                            gate.withPermit {
                                val r: Result<PropLineClient.Board?> = try {
                                    Result.success(client.eventBoard(sport, ref.id.removePrefix(PREFIX), markets, books))
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Result.failure(e)
                                }
                                Triple(eventId, ref, r)
                            }
                        }
                    }.map { it.await() }
                }
                for ((eventId, ref, r) in results) {
                    if (r.isFailure) { if (failure == null) failure = r.exceptionOrNull() as? Exception; continue }
                    val board = r.getOrNull()
                    // The listing's teams and time (the ones matched on) with the odds call's quotes.
                    bought[eventId] = Bought(
                        eventId, ref.copy(markets = board?.events?.singleOrNull()?.markets.orEmpty()), now, ask,
                        novig = board?.novig?.singleOrNull()?.let { ref.copy(markets = it.markets) },
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        val ids = leagueEvents.mapTo(HashSet()) { it.eventId }
        val current = bought.values.filter { it.novigEventId in ids && it.ask == ask && now - it.atMs < PropLineClient.propsReuseMs(it.ref.commenceMs, now) }
        // Each game's board as read, with the stream's moves, pulls and scratches since its read laid over it (PSL; unchanged without the stream).
        val events = current.filter { it.ref.markets.isNotEmpty() }.distinctBy { it.ref.id }.flatMap { b -> client.overlay(listOf(b.ref), b.atMs) }.filter { it.markets.isNotEmpty() }
        // Novig's own prices steer reads only while young: a game's props are re-used longer.
        val novig = current.filter { now - it.atMs <= NOVIG_MAX_AGE_MS }.mapNotNull { it.novig }.distinctBy { it.id }
        val snapshot = RefSnapshot(sport, events, now, provider = ID, novig = novig)
        failure?.let { e ->
            val message = when (e) {
                is com.tjshea.vigilant.data.keys.AllKeysExhaustedException, is ReferenceException -> e.message ?: displayName
                else -> "$displayName ${league.displayName}: ${readableError(e)}"
            }
            throw PartialReferenceException(snapshot, message)
        }
        snapshot
    }

    /** Novig event id -> PropLine prop markets to ask for, soonest games first, skipping fresh ones. */
    private fun allocate(settings: ScanSettings, context: ScanContext, ask: String): Map<String, List<String>> {
        if (MarketFamily.PLAYER_PROPS !in settings.families) return emptyMap()
        val now = context.now
        val horizon = now + settings.bookPropWindowHours * 3_600_000L
        val propTypes = context.novigMarkets
            .filter { it.isOpen && it.marketType in PropLineProps.STATS }
            .groupBy({ it.eventId }, { it.marketType })
        val out = LinkedHashMap<String, List<String>>()
        val games = context.novigEvents
            .filter { it.status == com.tjshea.vigilant.data.novig.NovigEvent.STATUS_PREGAME && it.startsTs > now && it.startsTs <= horizon && it.league in settings.leagues }
            .sortedBy { it.startsTs }
        for (e in games) {
            // A paid key with room in today's share reads every game in the window (Tj, 2026-10-16); the free key keeps the setting's cap.
            if (out.size >= (if (client.roomForEveryGame(context.now)) Int.MAX_VALUE else settings.propLineGamesPerScan.coerceAtLeast(1))) break
            val have = bought[e.eventId]
            if (have != null && have.ask == ask) continue
            val league = com.tjshea.vigilant.data.scanner.Leagues.byNovigName(e.league) ?: continue
            val types = propTypes[e.eventId]?.toSet() ?: continue
            val markets = PropLineProps.marketsFor(league.oddsApiSportKey).filter { it.second in types }.map { it.first }.distinct()
            if (markets.isNotEmpty()) out[e.eventId] = markets
        }
        return out
    }

    companion object {
        const val ID = "propline_props"

        /** From this many games of one sport to buy in a scan, a paid key reads them with the bulk call ([PropLineClient.bulkProps]). */
        const val BULK_MIN_GAMES = 2

        /** A game's props are re-used this long at most, so they're current through a scan (RESEARCH.md §24). */
        const val REUSE_MS = com.tjshea.vigilant.data.scanner.Freshness.MAX_REUSE_MS

        /** Novig prices relayed with a game's props are passed on (to order Novig reads) only this long. */
        const val NOVIG_MAX_AGE_MS = 3 * 60_000L
        internal const val PREFIX = "pl:"
    }
}

/** PropLine's player-prop market keys (its docs, 2026-09-27) for the stats Novig lists, per sport. */
object PropLineProps {
    private val SPORT_MARKETS: Map<String, List<Pair<String, String>>> = mapOf(
        "americanfootball_nfl" to listOf(
            "player_pass_yds" to "PASSING_YARDS",
            "player_rush_yds" to "RUSHING_YARDS",
            "player_reception_yds" to "RECEIVING_YARDS",
            "player_receptions" to "RECEPTIONS",
            "player_pass_tds" to "PASSING_TOUCHDOWNS",
            "player_anytime_td" to "TOUCHDOWNS",
            "player_pass_attempts" to "PASSING_ATTEMPTS",
            "player_pass_completions" to "PASSING_COMPLETIONS",
            "player_rush_attempts" to "RUSHING_ATTEMPTS",
            "player_pass_interceptions" to "INTERCEPTIONS_THROWN",
            "player_rush_reception_yds" to "RUSHING_AND_RECEIVING_YARDS",
            "player_pass_rush_yds" to "PASSING_AND_RUSHING_YARDS",
            "player_reception_longest" to "LONGEST_RECEPTION",
            "player_rush_longest" to "LONGEST_RUSH",
            "player_longest_completion" to "LONGEST_COMPLETION",
            "player_kicking_points" to "KICKING_POINTS",
            "player_field_goals_made" to "FIELD_GOALS_MADE",
        ),
        "baseball_mlb" to listOf(
            "batter_hits" to "HITS",
            "batter_total_bases" to "TOTAL_BASES",
            "pitcher_strikeouts" to "PITCHER_STRIKEOUTS",
            "batter_hits_runs_rbis" to "HITS_RUNS_RBIS",
            "batter_home_runs" to "HOME_RUNS",
            "batter_rbis" to "RBIS",
            "batter_runs" to "RUNS",
            "batter_stolen_bases" to "STOLEN_BASES",
            "batter_strikeouts" to "BATTING_STRIKEOUTS",
            "batter_walks" to "BATTING_WALKS",
            "pitcher_hits_allowed" to "HITS_ALLOWED",
            "pitcher_earned_runs" to "EARNED_RUNS",
            "pitcher_outs" to "PITCHER_OUTS",
            "pitcher_walks" to "WALKS",
        ),
        "basketball_wnba" to listOf(
            "player_points" to "POINTS",
            "player_rebounds" to "REBOUNDS",
            "player_assists" to "ASSISTS",
            "player_threes" to "THREE_POINTERS_MADE",
            "player_points_rebounds_assists" to "POINTS_REBOUNDS_ASSISTS",
            "player_double_double" to "DOUBLE_DOUBLE",
        ),
        // NBA and NHL (Tj, 2026-10-16, PropLine Pro: its docs' market keys; Novig lists both): the free key's 1,000 a day could not afford every game, a paid key's 5,000 to 25,000 can.
        "basketball_nba" to listOf(
            "player_points" to "POINTS",
            "player_rebounds" to "REBOUNDS",
            "player_assists" to "ASSISTS",
            "player_threes" to "THREE_POINTERS_MADE",
            "player_points_rebounds_assists" to "POINTS_REBOUNDS_ASSISTS",
            "player_points_assists" to "POINTS_ASSISTS",
            "player_points_rebounds" to "POINTS_REBOUNDS",
            "player_rebounds_assists" to "REBOUNDS_ASSISTS",
            "player_steals" to "STEALS",
            "player_blocks" to "BLOCKS",
            // Novig lists STEALS_BLOCKS again (changelog 2026-10-22); PropLine carries it as player_blocks_steals ("stocks", 2026-10-22 docs).
            "player_blocks_steals" to "STEALS_BLOCKS",
            "player_double_double" to "DOUBLE_DOUBLE",
        ),
        "icehockey_nhl" to listOf(
            "player_goals" to "PLAYER_GOALS",
            "player_shots_on_goal" to "SHOTS_ON_GOAL",
            "player_points" to "POINTS",
            "player_assists" to "ASSISTS",
            "goalie_saves" to "SAVES",
            "player_power_play_points" to "POWER_PLAY_POINTS",
        ),
    ).let { m -> m + ("americanfootball_ncaaf" to m.getValue("americanfootball_nfl")) }

    /** Yes/No markets: Yes is Over 0.5, No is Under 0.5. */
    val YES_NO: Set<String> = setOf("player_anytime_td", "player_double_double")

    val MARKETS: Map<String, String> = SPORT_MARKETS.values.flatten().toMap()
    val STATS: Set<String> = MARKETS.values.toSet()

    fun marketsFor(sportKey: String): List<Pair<String, String>> = SPORT_MARKETS[sportKey].orEmpty()
}

@Serializable
internal data class PlEvent(
    val id: String? = null,
    val home_team: String? = null,
    val away_team: String? = null,
    val commence_time: String? = null,
    val is_outright: Boolean? = null,
    val bookmakers: List<PlBook?>? = null,
) {
    /** The game with the fair-line books' quotes (never Novig, the exchanges read directly, or DFS). */
    fun toDomain(sportKey: String, period: String? = null): RefEvent? = domain(sportKey, period) { it !in PropLineClient.EXCLUDED }

    /** The same game with Novig's quotes only; null when PropLine had none for it. */
    fun novigDomain(sportKey: String): RefEvent? = domain(sportKey) { it == PropLineClient.NOVIG }?.takeIf { it.markets.isNotEmpty() }

    private fun domain(sportKey: String, period: String? = null, keep: (String) -> Boolean): RefEvent? {
        val id = id?.takeIf { it.isNotBlank() } ?: return null
        val home = home_team?.takeIf { it.isNotBlank() } ?: return null
        val away = away_team?.takeIf { it.isNotBlank() } ?: return null
        if (is_outright == true) return null
        val start = PropLineClient.ms(commence_time) ?: return null
        val markets = bookmakers.orEmpty().filterNotNull()
            .filter { b -> b.key?.let(keep) == true }
            .flatMap { b -> b.markets.orEmpty().filterNotNull().flatMap { it.toDomain(b, home, away, period) } }
        return RefEvent(PropLinePropsSource.PREFIX + id, sportKey, start, home = home, away = away, markets = markets)
    }
}

@Serializable
internal data class PlBook(
    val key: String? = null,
    val title: String? = null,
    val last_update: String? = null,
    val markets: List<PlMarket?>? = null,
    /** Only with `includeLinks`: the book's page for the game. */
    val link: String? = null,
    /** Only with `includeBookIds`: the book's own id for the game. */
    val book_event_id: String? = null,
)

@Serializable
internal data class PlMarket(
    val key: String? = null,
    val last_update: String? = null,
    val period: String? = null,
    val team: String? = null,
    val suspended_at: String? = null,
    val outcomes: List<PlOutcome?>? = null,
) {
    fun toDomain(book: PlBook, home: String, away: String, wantPeriod: String? = null): List<RefBookMarket> {
        val key = key ?: return emptyList()
        val bookKeyRaw = book.key ?: return emptyList()
        val outcomes = outcomes.orEmpty().filterNotNull().filter { it.name != null }
        // Pulled by the book, or a period line (only full games are asked for).
        if (suspended_at != null || !period.equals(wantPeriod, ignoreCase = true)) return emptyList()
        val updated = PropLineClient.ms(last_update ?: book.last_update)
        // An outcome the book stopped sending while still sending its market was withdrawn.
        val live = outcomes.filter { o -> val seen = PropLineClient.ms(o.last_seen_at); seen == null || updated == null || seen >= updated }
        val bookKey = PropLineClient.bookKey(bookKeyRaw)
        val title = TheOddsApiClient.KNOWN_BOOKMAKERS[bookKey] ?: book.title?.takeIf { it.isNotBlank() } ?: bookKey
        PropLineProps.MARKETS[key]?.let { stat -> return if (wantPeriod != null) emptyList() else props(live, bookKey, title, updated, stat, book) }
        val kind = when (key) {
            "h2h" -> LineKind.MONEYLINE
            "spreads" -> LineKind.SPREAD
            "totals" -> if (team == null) LineKind.TOTAL else LineKind.TEAM_TOTAL
            else -> return emptyList()
        }
        val subject = when {
            kind != LineKind.TEAM_TOTAL -> null
            team == home -> RefBookMarket.HOME
            team == away -> RefBookMarket.AWAY
            else -> return emptyList()
        }
        if (live.size != 2 || outcomes.size != 2) return emptyList()
        val quotes = live.map { o ->
            val side = when (kind) {
                LineKind.TOTAL, LineKind.TEAM_TOTAL -> when {
                    o.name.equals("Over", ignoreCase = true) -> Side.OVER
                    o.name.equals("Under", ignoreCase = true) -> Side.UNDER
                    else -> return emptyList()
                }
                else -> when {
                    o.side == "home" || (o.side == null && o.name == home) -> Side.HOME
                    o.side == "away" || (o.side == null && o.name == away) -> Side.AWAY
                    else -> return emptyList()
                }
            }
            RefQuote(side, PropLineClient.decimal(o.price) ?: return emptyList(), o.point, o.book_outcome_id?.takeIf { it.isNotBlank() }, feedOutcomeId = o.outcomeId)
        }
        if (quotes.map { it.side }.toSet().size != 2) return emptyList()
        if (kind != LineKind.MONEYLINE && quotes.any { it.point == null }) return emptyList()
        return listOf(RefBookMarket(bookKey, title, kind, quotes, seen(live) ?: updated, subject = subject, period = if (wantPeriod == null) 0 else 1, bookEventId = book.book_event_id?.takeIf { it.isNotBlank() }, link = book.link?.takeIf { it.isNotBlank() }))
    }

    /**
     * When PropLine last saw this price at the book: the older of the two sides' `last_seen_at` (PropLine
     * also sends `last_change_at`, when the price last moved; an unmoved price is still current), else the
     * market's `last_update`. The scan never prices one older than a few minutes (RESEARCH.md §24).
     */
    private fun seen(outcomes: List<PlOutcome>): Long? =
        outcomes.map { PropLineClient.ms(it.last_seen_at) ?: return null }.minOrNull()

    /**
     * Two-way player lines only (`point` set, Over/Under), and Yes/No markets as Over/Under 0.5. A
     * milestone rung ("3+ Total Bases") or a Yes-only list has no second side to devig and is left out.
     */
    private fun props(live: List<PlOutcome>, bookKey: String, title: String, updated: Long?, stat: String, book: PlBook): List<RefBookMarket> {
        val yesNo = this.key in PropLineProps.YES_NO
        data class Leg(val player: String, val point: Double, val over: Boolean, val price: Double, val seen: Long?, val id: String? = null, val feedId: String? = null)
        val legs = live.mapNotNull { o ->
            val player = o.description?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val over = when (o.name.orEmpty().trim().lowercase()) {
                "over", "yes" -> true
                "under", "no" -> false
                else -> return@mapNotNull null
            }
            val point = o.point ?: if (yesNo) 0.5 else return@mapNotNull null
            Leg(player, point, over, PropLineClient.decimal(o.price) ?: return@mapNotNull null, PropLineClient.ms(o.last_seen_at), o.book_outcome_id?.takeIf { it.isNotBlank() }, o.outcomeId)
        }
        return legs.groupBy { PlayerNames.key(it.player) to it.point }.values.mapNotNull { group ->
            val over = group.singleOrNull { it.over } ?: return@mapNotNull null
            val under = group.singleOrNull { !it.over } ?: return@mapNotNull null
            RefBookMarket(
                bookKey, title, LineKind.PLAYER_PROP,
                listOf(RefQuote(Side.OVER, over.price, over.point, over.id, feedOutcomeId = over.feedId), RefQuote(Side.UNDER, under.price, under.point, under.id, feedOutcomeId = under.feedId)),
                if (over.seen != null && under.seen != null) minOf(over.seen, under.seen) else updated, subject = over.player, stat = stat,
                bookEventId = book.book_event_id?.takeIf { it.isNotBlank() }, link = book.link?.takeIf { it.isNotBlank() },
            )
        }
    }
}

@Serializable
internal data class PlOutcome(
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val point: Double? = null,
    val last_seen_at: String? = null,
    val side: String? = null,
    /** Only with `includeBookIds`: the book's own id for this selection. */
    val book_outcome_id: String? = null,
    /** Only with `includeBookIds`: PropLine's own id for this selection, the one its pushes carry (a number; read as text). */
    val outcome_id: kotlinx.serialization.json.JsonPrimitive? = null,
) {
    val outcomeId: String? get() = outcome_id?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.takeIf { it.isNotBlank() }
}

/**
 * More books' 1st-half (and baseball's first-5-innings) lines from PropLine (PS9, Tj 2026-10-25, PropLine's recipes: "?period=h1"): Novig's `SPREAD_1H` / `TOTAL_1H` markets were priced by a median of ONE
 * book (the engine's study, 2026-10-24: ParlayAPI's period lines carry a handful of books), PropLine's `period=` board carries every book it reads. Beside ParlayAPI's 1st-half source, never instead
 * (not on an [com.tjshea.vigilant.data.scanner.ApiChain]): where both send a book the fresher quote wins and two same-time quotes far apart leave it out ([com.tjshea.vigilant.data.scanner.Pricing.chosenMarkets]).
 * One request a league, asked only while Novig lists a 1st-half line in it.
 */
class PropLinePeriodSource(private val client: PropLineClient) : ReferenceSource {
    override val id = ID
    override val displayName = "PropLine 1st half"
    override val metered = true
    override val needsCatalog = true

    override fun supports(league: League): Boolean = league.oddsApiSportKey in PERIODS

    override fun reuseMs(settings: ScanSettings): Long = PropLineClient.REUSE_MS

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = odds(league, settings, ScanContext())

    override suspend fun odds(league: League, settings: ScanSettings, context: ScanContext): RefSnapshot {
        val sport = league.oddsApiSportKey
        val skipped = RefSnapshot(sport, emptyList(), System.currentTimeMillis(), provider = id, skipped = true)
        val period = PERIODS[sport] ?: return skipped
        if (com.tjshea.vigilant.data.scanner.MarketFamily.FIRST_HALF !in settings.families) return skipped
        val events = context.novigEvents.filter { it.league == league.novigName }.mapTo(HashSet()) { it.eventId }
        if (context.novigMarkets.none { it.eventId in events && it.marketType in ParlayPeriodSource.NOVIG_TYPES }) return skipped
        val books = PropLineClient.books(settings)
        if (books.isEmpty()) return skipped
        val answer = client.periodOdds(sport, period, books)
        return RefSnapshot(sport, answer.value, System.currentTimeMillis(), answer.remaining, answer.used, id)
    }

    companion object {
        const val ID = "propline_1h"

        /** PropLine's period code for the part of the game Novig's `_1H` markets are, by sport (the same parts as [ParlayPeriodSource.PERIODS]). */
        val PERIODS = mapOf(
            "americanfootball_nfl" to "h1", "americanfootball_ncaaf" to "h1",
            "basketball_nba" to "h1", "basketball_wnba" to "h1", "basketball_ncaab" to "h1",
            "baseball_mlb" to "f5",
        )
    }
}
