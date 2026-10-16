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
) : ReferenceSource {

    override val id = ID
    override val displayName = "PropLine"
    override val metered = true

    override fun supports(league: League) = league.oddsApiSportKey in SPORTS

    /**
     * PropLine's own pregame refresh is about a minute: scans closer together than this re-use the last board.
     */
    override fun reuseMs(settings: ScanSettings): Long = REUSE_MS

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val markets = marketsFor(settings.families)
        val books = books(settings)
        if (markets.isEmpty() || books.isEmpty()) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        // Novig rides along in the same request (no extra cost): its prices order the scan's own
        // Novig reads, and are kept apart from the books that make the fair line.
        val answer = call(
            "/sports/${league.oddsApiSportKey}/odds",
            listOf("markets" to markets.joinToString(","), "bookmakers" to asked(books).joinToString(",")) + idParams(),
            notFound = Board(emptyList(), emptyList()),
        ) { parseBoard(it, json, league.oddsApiSportKey) }
        remember(league.oddsApiSportKey, answer.value.events)
        return RefSnapshot(league.oddsApiSportKey, answer.value.events, clock(), answer.remaining, answer.used, ID, novig = answer.value.novig)
    }

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

    private suspend fun eventBoardOnce(sportKey: String, eventId: String, markets: List<String>, books: List<String>): Board? {
        return call(
            "/sports/$sportKey/events/$eventId/odds",
            listOf("markets" to markets.joinToString(","), "bookmakers" to asked(books).distinct().joinToString(",")) + idParams(),
            notFound = null,
        ) { raw -> parseEventBoard(raw, json, sportKey) }.value
    }

    /**
     * One game's [markets] from [books] as PropLine sent it (a tapped bet's sheet reads it with one-sided books kept: [OtherBooks]); null when
     * PropLine no longer lists the game. One request.
     */
    suspend fun eventRaw(sportKey: String, eventId: String, markets: List<String>, books: List<String>): String? {
        require(markets.isNotEmpty()) { "No markets to ask for" }
        return call(
            "/sports/$sportKey/events/$eventId/odds",
            listOf("markets" to markets.joinToString(","), "bookmakers" to books.distinct().joinToString(",")),
            notFound = null,
        ) { raw -> raw }.value
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

    /** The books a call asks for: the picked books, and Novig's relay unless it's off. */
    private fun asked(books: List<String>): List<String> = if (relayNovig) books + NOVIG else books

    /** The book-id and event-page parameters, only when [bookIds] is on. */
    private fun idParams(): List<Pair<String, String>> =
        if (bookIds) listOf("includeBookIds" to "true", "includeLinks" to "true") else emptyList()

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

    /** Requests a second the plan allows us to use: half the sustained rate (Hobby 10, Pro 20 a second; free 5). */
    private fun intervalMs(): Long = plan()?.let { if (it.limit >= 25_000) 100L else 150L } ?: minIntervalMs

    /**
     * Whether a paid key has room to read every game's props this scan: its used share of the day so far is within the day's share plus a headroom (so a busy morning cannot spend the evening's
     * requests). Always false for a free key (its [propLineGamesPerScan] cap stays).
     */
    fun roomForEveryGame(now: Long = clock()): Boolean {
        val p = plan() ?: return false
        val dayMs = 24 * 3_600_000L
        val sinceMidnight = now - (now / dayMs) * dayMs
        val allowed = p.limit * (sinceMidnight.toDouble() / dayMs) + p.limit * 0.10
        return p.used <= allowed
    }

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

        /** The most prop markets one request asks for with a paid key. */
        const val CHUNK = 6

        /** A timeout or a 502/503/504 is tried again this many times. */
        const val RETRIES = 2

        /** Novig's key on PropLine: asked for alongside the books, never one of them. */
        const val NOVIG = "novig"
        private const val LIST_REUSE_MS = 5 * 60_000L

        /** PropLine's book keys where they differ from The Odds API's. */
        private val TO_PROPLINE = mapOf("hardrockbet" to "hardrock")
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

        /** PropLine's sportsbooks and exchanges that can price a fair line (its bookmaker list, 2026-09-27). */
        private val KNOWN = setOf(
            "pinnacle", "draftkings", "fanduel", "betmgm", "fanatics", "betrivers", "hardrock", "bovada",
            "betonlineag", "lowvig", "betus", "unibet", "onexbet", "marathon", "fliff", "betway",
            "matchbook", "smarkets",
        )

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
        fun parseBoard(raw: String, json: Json, sportKey: String): Board {
            val lenient = lenient(json)
            // One game at a time, never the whole board as a tree (about eight times the JSON's size; PropLine's answers average 4 MB: Tj, 2026-10-10, heap 91%).
            com.tjshea.vigilant.data.JsonSplit.elements(raw)?.let { split ->
                val events = ArrayList<PlEvent>(split.size)
                for (i in 0 until split.size) runCatching { lenient.decodeFromString(PlEvent.serializer(), split.element(i)) }.getOrNull()?.let { events += it }
                return Board(events.mapNotNull { it.toDomain(sportKey) }, events.mapNotNull { it.novigDomain(sportKey) })
            }
            val items = lenient.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonArray ?: return Board(emptyList(), emptyList())
            val events = items.mapNotNull { e -> runCatching { lenient.decodeFromJsonElement(PlEvent.serializer(), e) }.getOrNull() }
            return Board(events.mapNotNull { it.toDomain(sportKey) }, events.mapNotNull { it.novigDomain(sportKey) })
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

        internal fun bookKey(propLineKey: String) = FROM_PROPLINE[propLineKey] ?: propLineKey
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

    /** A game's props, bought at [atMs] with request [ask]; [novig] = Novig's own prices from the same reply. */
    private data class Bought(val novigEventId: String, val ref: RefEvent, val atMs: Long, val ask: String, val novig: RefEvent? = null)

    private val mutex = Mutex()
    private val bought = HashMap<String, Bought>()
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
            bought.values.removeAll { now - it.atMs >= REUSE_MS }
            toBuy = if (books.isEmpty()) emptyMap() else allocate(settings, context, ask)
        }
        val leagueEvents = context.novigEvents.filter { it.league == league.novigName }
        val buying = leagueEvents.filter { it.eventId in toBuy }
        var failure: Exception? = null
        if (buying.isNotEmpty()) {
            try {
                val listed = client.events(sport)
                val matches = com.tjshea.vigilant.data.scanner.Planner.matchEvents(buying, listOf(RefSnapshot(sport, listed, now, provider = ID)))
                // A paid key reads several games at once (the client's own spacing still sets the pace, and PropLine allows 20 in flight); the free key one at a time, as before.
                val width = if (client.plan() != null) 6 else 1
                val gate = kotlinx.coroutines.sync.Semaphore(width)
                val results: List<Triple<String, RefEvent, Result<PropLineClient.Board?>>> = kotlinx.coroutines.coroutineScope {
                    matches.sortedBy { it.event.startsTs }.mapNotNull { m ->
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
        val current = bought.values.filter { it.novigEventId in ids && it.ask == ask && now - it.atMs < REUSE_MS }
        val events = current.filter { it.ref.markets.isNotEmpty() }.map { it.ref }.distinctBy { it.id }
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
    fun toDomain(sportKey: String): RefEvent? = domain(sportKey) { it !in PropLineClient.EXCLUDED }

    /** The same game with Novig's quotes only; null when PropLine had none for it. */
    fun novigDomain(sportKey: String): RefEvent? = domain(sportKey) { it == PropLineClient.NOVIG }?.takeIf { it.markets.isNotEmpty() }

    private fun domain(sportKey: String, keep: (String) -> Boolean): RefEvent? {
        val id = id?.takeIf { it.isNotBlank() } ?: return null
        val home = home_team?.takeIf { it.isNotBlank() } ?: return null
        val away = away_team?.takeIf { it.isNotBlank() } ?: return null
        if (is_outright == true) return null
        val start = PropLineClient.ms(commence_time) ?: return null
        val markets = bookmakers.orEmpty().filterNotNull()
            .filter { b -> b.key?.let(keep) == true }
            .flatMap { b -> b.markets.orEmpty().filterNotNull().flatMap { it.toDomain(b, home, away) } }
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
    fun toDomain(book: PlBook, home: String, away: String): List<RefBookMarket> {
        val key = key ?: return emptyList()
        val bookKeyRaw = book.key ?: return emptyList()
        val outcomes = outcomes.orEmpty().filterNotNull().filter { it.name != null }
        // Pulled by the book, or a period line (only full games are asked for).
        if (suspended_at != null || period != null) return emptyList()
        val updated = PropLineClient.ms(last_update ?: book.last_update)
        // An outcome the book stopped sending while still sending its market was withdrawn.
        val live = outcomes.filter { o -> val seen = PropLineClient.ms(o.last_seen_at); seen == null || updated == null || seen >= updated }
        val bookKey = PropLineClient.bookKey(bookKeyRaw)
        val title = TheOddsApiClient.KNOWN_BOOKMAKERS[bookKey] ?: book.title?.takeIf { it.isNotBlank() } ?: bookKey
        PropLineProps.MARKETS[key]?.let { stat -> return props(live, bookKey, title, updated, stat, book) }
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
            RefQuote(side, PropLineClient.decimal(o.price) ?: return emptyList(), o.point, o.book_outcome_id?.takeIf { it.isNotBlank() })
        }
        if (quotes.map { it.side }.toSet().size != 2) return emptyList()
        if (kind != LineKind.MONEYLINE && quotes.any { it.point == null }) return emptyList()
        return listOf(RefBookMarket(bookKey, title, kind, quotes, seen(live) ?: updated, subject = subject, bookEventId = book.book_event_id?.takeIf { it.isNotBlank() }, link = book.link?.takeIf { it.isNotBlank() }))
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
        data class Leg(val player: String, val point: Double, val over: Boolean, val price: Double, val seen: Long?, val id: String? = null)
        val legs = live.mapNotNull { o ->
            val player = o.description?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val over = when (o.name.orEmpty().trim().lowercase()) {
                "over", "yes" -> true
                "under", "no" -> false
                else -> return@mapNotNull null
            }
            val point = o.point ?: if (yesNo) 0.5 else return@mapNotNull null
            Leg(player, point, over, PropLineClient.decimal(o.price) ?: return@mapNotNull null, PropLineClient.ms(o.last_seen_at), o.book_outcome_id?.takeIf { it.isNotBlank() })
        }
        return legs.groupBy { PlayerNames.key(it.player) to it.point }.values.mapNotNull { group ->
            val over = group.singleOrNull { it.over } ?: return@mapNotNull null
            val under = group.singleOrNull { !it.over } ?: return@mapNotNull null
            RefBookMarket(
                bookKey, title, LineKind.PLAYER_PROP,
                listOf(RefQuote(Side.OVER, over.price, over.point, over.id), RefQuote(Side.UNDER, under.price, under.point, under.id)),
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
)
