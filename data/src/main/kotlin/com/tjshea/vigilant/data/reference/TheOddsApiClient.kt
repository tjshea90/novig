package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.CreditHeaders
import com.tjshea.vigilant.data.keys.CreditPace
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The Odds API v4 (RESEARCH.md §4.3, §11), the optional sportsbook leg: Pinnacle plus the major
 * US books, on the free tier's 500 credits a month.
 *
 * Credit cost is `markets returned x regions`, and naming up to 10 bookmakers counts as ONE
 * region (their docs, verified 2026-09-25). So this always sends `bookmakers=` (at most 10)
 * instead of `regions=`, and asks only for the market families the user prices: moneyline only
 * is 1 credit per sport, all three are 3. Novig itself is never requested here: it's the thing
 * being priced, not a reference.
 *
 * Player props come from the per-game endpoint ([eventOdds], RESEARCH.md §14), which the
 * [OddsApiPropsSource] spends a strict per-scan budget on; the game list it matches against
 * ([events]) is free.
 *
 * Their docs ask clients to space requests out rather than burst (429 above 30 calls/s), so calls
 * are at least [minIntervalMs] apart (2 a second, 15x under their limit). [KeyPool] picks the key (Tj's rotation rule): each call's
 * `x-requests-remaining`/`x-requests-used`/`x-requests-last` headers feed the usage meter, a key
 * that can't afford the next call is skipped, and `OUT_OF_USAGE_CREDITS` rests a key until the 1st.
 */
class TheOddsApiClient(
    private val httpClient: OkHttpClient,
    private val pool: KeyPool,
    private val json: Json,
    private val baseUrl: String = OddsFeed.ODDS_API.base,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = 500,
    /** Which feed in The Odds API's format this is: The Odds API itself, or ParlayAPI's drop-in copy ([OddsFeed.PARLAY]). */
    val feed: OddsFeed = OddsFeed.ODDS_API,
    /** Background auto-scan's client: a paced feed leaves part of the day's share for Tj's own scans ([OddsFeed.pace]). */
    background: Boolean = false,
) : ReferenceSource {

    /** ParlayAPI's scans spend a day's share of the month at most ([CreditPace]); The Odds API's aren't paced. */
    private val pace: CreditPace? = feed.pace(pool.policy, background)

    override val id = feed.sourceId
    override val displayName = feed.title
    override val metered = true

    /** The books asked for: the feed's own list (ParlayAPI), else the reference books picked in Settings (their keys are The Odds API's). */
    fun booksFor(settings: ScanSettings): List<String> = feed.books ?: settings.referenceBooks

    /** Tennis is keyed per tournament there, never by the league key the app groups it under. */
    override fun supports(league: League) = league.oddsApiListed

    override fun reuseMs(settings: ScanSettings): Long = settings.oddsApiReuseMs

    /**
     * PropLine carries the same sportsbooks for 1 of 1,000 daily requests per league; this costs 3 of
     * 500 monthly credits. So with a PropLine key this is PropLine's fallback (RESEARCH.md §23).
     */
    override val fallbackFor: String? get() = if (feed == OddsFeed.ODDS_API) PropLineClient.ID else null

    /**
     * Worth credits only when PropLine didn't answer [league] this scan, when a book picked as sharp is
     * one only this feed carries, or when Novig lists games PropLine's board lacks and this feed's free
     * game list has at least one of them.
     */
    override suspend fun needed(league: League, settings: ScanSettings, context: ScanContext): Boolean {
        // ParlayAPI is a feed of its own (Pinnacle among its books), not PropLine's backup.
        if (feed != OddsFeed.ODDS_API) return true
        if (league.novigName !in context.firstAnswered) return true
        if (settings.referenceBooks.any { it in KNOWN_BOOKMAKERS && it in settings.sharpBooks && !PropLineClient.carries(it) }) return true
        val horizon = context.now + (settings.daysAhead.coerceAtLeast(1) + 1) * 86_400_000L
        val missing = context.novigEvents.filter { e ->
            e.league == league.novigName && e.startsTs <= horizon &&
                context.covered[e.eventId].orEmpty().none { it in GAME_LINES }
        }
        if (missing.isEmpty()) return false
        // PropLine did answer: if the free list can't be read either (network, keys resting), stand by
        // rather than raise an error for a backup that may not have been needed.
        val listed = try {
            listed(league.oddsApiSportKey, horizon)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return Planner.matchEvents(missing, listOf(RefSnapshot(league.oddsApiSportKey, listed, context.now, provider = id)))
            .any { it.refEvent != null }
    }

    private val lists = Mutex()
    private val listCache = HashMap<String, Pair<Long, List<RefEvent>>>()

    /** [events] for [needed]'s check, re-used for a few minutes (free either way, but each call waits its turn). */
    private suspend fun listed(sportKey: String, startsBeforeMs: Long): List<RefEvent> = lists.withLock {
        listCache[sportKey]?.takeIf { clock() - it.first < LIST_REUSE_MS }?.let { return it.second }
        events(sportKey, startsBeforeMs + 86_400_000L).value.also { listCache[sportKey] = clock() to it }
    }

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val markets = marketsFor(settings.families, feed)
        if (markets.isEmpty()) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = id)
        // ParlayAPI: only the games the scan can price (its window, plus a day for loose kickoff times), a smaller reply for the same credits.
        val until = if (feed == OddsFeed.ODDS_API) null else Planner.horizon(settings, clock()) + WINDOW_SLACK_MS
        return fetch(league.oddsApiSportKey, booksFor(settings), markets, startsBeforeMs = until)
    }

    private val spacing = Mutex()
    private var lastCallAt = 0L

    /** One sport's odds from the named [bookmakers]. Costs `markets.size` credits when anything comes back. */
    suspend fun fetch(sportKey: String, bookmakers: List<String>, markets: List<String> = ALL_MARKETS, startsBeforeMs: Long? = null): RefSnapshot {
        val books = pickBooks(bookmakers)
        val answer = call(
            path = "/sports/$sportKey/odds",
            params = listOf("bookmakers" to books.joinToString(","), "markets" to markets.joinToString(","), "oddsFormat" to "decimal") +
                listOfNotNull(startsBeforeMs?.let { "commenceTimeTo" to isoSeconds(it) }),
            // Cost = markets asked for x 1 region (<=10 named books), so the pool can skip a key
            // that can't afford it before asking.
            cost = markets.size,
            what = sportKey,
            notFound = emptyList(),
            parse = { parseEvents(it, json) },
            // Their rule: no events returned = no charge.
            charged = { if (it.isEmpty()) 0 else markets.size },
        )
        return RefSnapshot(sportKey, answer.value, clock(), answer.remaining, answer.used, id)
    }

    /**
     * The sport's upcoming games (ids, teams, start times), no odds. Free: The Odds API doesn't
     * charge credits for `/events`. [startsBeforeMs] trims the list to the games that matter.
     */
    suspend fun events(sportKey: String, startsBeforeMs: Long? = null): Answer<List<RefEvent>> =
        call(
            path = "/sports/$sportKey/events",
            params = listOfNotNull(startsBeforeMs?.let { "commenceTimeTo" to isoSeconds(it) }),
            cost = 0,
            what = sportKey,
            notFound = emptyList(),
            parse = { parseEvents(it, json) },
            charged = { 0 },
        )

    /**
     * One game's odds for [markets] (player props) from the named [bookmakers]. Costs one credit
     * per market that comes back (x 1 region); a market no book posts costs nothing. Null when
     * the game is gone (404).
     */
    suspend fun eventOdds(sportKey: String, eventId: String, bookmakers: List<String>, markets: List<String>): Answer<RefEvent?> {
        require(markets.isNotEmpty()) { "No markets to ask for" }
        val books = pickBooks(bookmakers)
        return call(
            path = "/sports/$sportKey/events/$eventId/odds",
            params = listOf("bookmakers" to books.joinToString(","), "markets" to markets.joinToString(","), "oddsFormat" to "decimal"),
            cost = markets.size,
            what = "$sportKey event $eventId",
            notFound = null,
            parse = { parseEvent(it, json) },
            // Only a fallback: the x-requests-last header is the real charge.
            charged = { e -> e?.markets?.mapNotNull { it.stat }?.distinct()?.size ?: 0 },
        )
    }

    /**
     * ParlayAPI's player props for a whole league in one call (3 credits, whatever the markets and books): every book's over/under for
     * every player, [offset] rows in ([ParlayProps.PAGE] rows a page). Only on [OddsFeed.PARLAY].
     */
    suspend fun bulkProps(sportKey: String, markets: List<String>, bookmakers: List<String>, offset: Int = 0): Answer<ParlayProps.Page> {
        check(feed == OddsFeed.PARLAY) { "Only ParlayAPI serves a league's props in one call" }
        return call(
            path = "/sports/$sportKey/props",
            params = listOf(
                "markets" to markets.joinToString(","), "bookmakers" to bookmakers.filter { it != "novig" }.distinct().joinToString(","),
                "oddsFormat" to "american", "limit" to ParlayProps.PAGE.toString(), "offset" to offset.toString(),
                // Rows older than the oldest a quote may be to price (RESEARCH.md §24) are left on the server: a smaller, faster reply.
                "maxAgeSec" to (Freshness.FAR_OFF_AGE_MS / 1000).toString(),
            ),
            cost = ParlayProps.COST,
            what = "$sportKey props",
            notFound = ParlayProps.Page(0, emptyList()),
            parse = { ParlayProps.parse(it, json, sportKey, clock()) },
            charged = { ParlayProps.COST },
        )
    }

    /** A call's result plus the key's credit headers. */
    class Answer<T>(val value: T, val remaining: Int?, val used: Int?)

    private fun pickBooks(bookmakers: List<String>): List<String> =
        bookmakers.filter { it != "novig" }.distinct().take(MAX_BOOKMAKERS_ONE_REGION)
            .also { require(it.isNotEmpty()) { "Pick at least one reference sportsbook" } }

    private suspend fun <T> call(
        path: String,
        params: List<Pair<String, String>>,
        cost: Int,
        what: String,
        notFound: T,
        parse: (String) -> T,
        charged: (T) -> Int,
    ): Answer<T> {
        spacing.withLock {
            val wait = lastCallAt + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastCallAt = System.currentTimeMillis()
        }
        return pool.execute(cost = cost, reserve = feed.reserve, pace = pace) { key ->
            val url = "$baseUrl$path".toHttpUrl().newBuilder()
                // ParlayAPI's best practices (2026-09-30): the key in the X-API-Key header, not the URL (query strings end up in logs).
                .apply { if (feed == OddsFeed.ODDS_API) addQueryParameter("apiKey", key) }
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .addQueryParameter("dateFormat", "iso")
                .build()
            val request = Request.Builder().url(url).get().apply { if (feed != OddsFeed.ODDS_API) header("X-API-Key", key) }.build()

            // ParlayAPI's best practices: a gateway blip (502/503/504) or a dropped connection is worth one retry a second later;
            // 400/401/403/404 never are.
            val retryable = feed != OddsFeed.ODDS_API
            var attempt = 0
            while (true) {
                attempt++
                val response = try {
                    httpClient.newCall(request).await()
                } catch (e: java.io.IOException) {
                    if (retryable && attempt == 1) { delay(RETRY_AFTER_MS); continue }
                    throw e
                }
                if (retryable && attempt == 1 && response.code in 502..504) {
                    response.close()
                    delay(RETRY_AFTER_MS)
                    continue
                }
                return@execute response.use { answer(it, what, notFound, parse, charged) }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("unreachable")
        }
    }

    /** One reply turned into what the key pool records: the provider's own credit figures ([CreditHeaders]) and the value. */
    private fun <T> answer(response: okhttp3.Response, what: String, notFound: T, parse: (String) -> T, charged: (T) -> Int): KeyAttemptResult<Answer<T>> {
        val body = response.body?.string().orEmpty()
        val credits = CreditHeaders.read({ response.header(it) }, clock())
        val remaining = credits.remaining
        val used = credits.used
        val last = credits.cost
        val requestNote = credits.requestId?.let { " (request $it)" }.orEmpty()
        return when (response.code) {
            429 -> {
                val retry = response.header("Retry-After")
                KeyAttemptResult.RateLimited(
                    retry?.toLongOrNull()?.times(1000) ?: 2_000,
                    reason = "HTTP 429" + (retry?.let { ", Retry-After=${it}s" } ?: ""),
                )
            }
            401, 403 -> when {
                body.contains("OUT_OF_USAGE_CREDITS") || body.contains("quota", ignoreCase = true) || body.contains("credit_limit_exceeded") ->
                    KeyAttemptResult.Depleted("monthly credits used up")
                else -> KeyAttemptResult.Invalid(reason = "HTTP ${response.code}" + errorCode(body)?.let { " $it" }.orEmpty() + requestNote)
            }
            // An out-of-season sport or a finished game isn't the key's fault, and costs nothing.
            404 -> KeyAttemptResult.Success(Answer(notFound, remaining, used), cost = last ?: 0, remaining = remaining, used = used, resetAtMs = credits.resetAtMs)
            else -> {
                if (!response.isSuccessful) {
                    throw TheOddsApiException("${feed.title} failed for $what: HTTP ${response.code}$requestNote ${body.take(200)}")
                }
                val value = parse(body)
                KeyAttemptResult.Success(Answer(value, remaining, used), cost = last ?: charged(value), remaining = remaining, used = used, resetAtMs = credits.resetAtMs)
            }
        }
    }

    private fun errorCode(body: String): String? =
        Regex("\"error_code\"\\s*:\\s*\"([A-Z_]+)\"").find(body)?.groupValues?.get(1)

    private fun okhttp3.Response.intHeader(name: String): Int? = header(name)?.trim()?.toDoubleOrNull()?.toInt()

    companion object {
        const val ID = "oddsapi"
        val ALL_MARKETS = listOf("h2h", "spreads", "totals")

        /** ParlayAPI's best practices: a 502 or a dropped connection is retried once, this much later. */
        const val RETRY_AFTER_MS = 1_000L

        /** Past the scan's window, games still asked for: a feed's kickoff time can sit a while off Novig's. */
        const val WINDOW_SLACK_MS = 24 * 60 * 60_000L

        /** What a PropLine board covers for a game ([RefBookMarket.coverage]): its full-game lines. */
        private val GAME_LINES = setOf("MONEYLINE:0", "SPREAD:0", "TOTAL:0")
        private const val LIST_REUSE_MS = 5 * 60_000L
        private val FAMILY_MARKETS = mapOf(MarketFamily.MONEYLINE to "h2h", MarketFamily.SPREAD to "spreads", MarketFamily.TOTAL to "totals")

        /**
         * The markets a sport refresh buys, one credit each: the main lines, and on ParlayAPI (which serves them for a whole league in one
         * call, where The Odds API only sells them game by game) every alternate spread and total, so Novig's alternate lines are priced
         * at their own numbers.
         */
        fun marketsFor(families: Collection<MarketFamily>, feed: OddsFeed = OddsFeed.ODDS_API): List<String> {
            val main = families.mapNotNull { FAMILY_MARKETS[it] }
            val alt = if (feed == OddsFeed.ODDS_API) emptyList() else families.mapNotNull { ALT_MARKETS[it] }
            return (main + alt).sorted()
        }

        private val ALT_MARKETS = mapOf(MarketFamily.SPREAD to "alternate_spreads", MarketFamily.TOTAL to "alternate_totals")
        const val CREDITS_REMAINING = "x-credits-remaining"
        const val CREDITS_COST = "x-credits-cost"
        const val REMAINING = "x-requests-remaining"
        const val USED = "x-requests-used"
        const val LAST = "x-requests-last"
        const val MAX_BOOKMAKERS_ONE_REGION = 10

        /** Ten books = one region = 3 credits per sport refresh. Pinnacle is the sharp anchor. */
        val DEFAULT_BOOKMAKERS = listOf(
            "pinnacle", "betonlineag", "lowvig", "draftkings", "fanduel",
            "betmgm", "williamhill_us", "espnbet", "fanatics", "betrivers",
        )

        /** Every book the settings screen offers. The Odds API bookmaker keys. */
        val KNOWN_BOOKMAKERS: Map<String, String> = linkedMapOf(
            "pinnacle" to "Pinnacle",
            "betonlineag" to "BetOnline.ag",
            "lowvig" to "LowVig.ag",
            "betfair_ex_eu" to "Betfair Exchange",
            "matchbook" to "Matchbook",
            "draftkings" to "DraftKings",
            "fanduel" to "FanDuel",
            "betmgm" to "BetMGM",
            "williamhill_us" to "Caesars",
            "espnbet" to "ESPN BET",
            "fanatics" to "Fanatics",
            "betrivers" to "BetRivers",
            "hardrockbet" to "Hard Rock Bet",
            "ballybet" to "Bally Bet",
            "bovada" to "Bovada",
            "mybookieag" to "MyBookie.ag",
            "betus" to "BetUS",
        )

        /** Display names for every book key the app can show, sportsbooks and exchanges alike. */
        fun bookTitle(key: String): String = when (key) {
            PolymarketClient.BOOK_KEY -> "Polymarket"
            KalshiClient.BOOK_KEY -> "Kalshi"
            "prophetx" -> "ProphetX"
            "bet365" -> "bet365"
            else -> KNOWN_BOOKMAKERS[key] ?: key
        }

        /**
         * One key per book whichever feed named it: ParlayAPI calls some books by other names than The Odds API (whose keys PropLine and
         * Settings use), and the same book under two keys would count twice in the fair price.
         */
        fun canonicalBook(key: String): String = PARLAY_BOOK_KEYS[key] ?: key

        private val PARLAY_BOOK_KEYS = mapOf("caesars" to "williamhill_us", "betonline" to "betonlineag", "hardrock" to "hardrockbet")

        fun parseEvents(rawJson: String, json: Json): List<RefEvent> =
            json.decodeFromString(ListSerializer(EventDto.serializer()), rawJson).map { it.toDomain() }

        /** One game from the per-event odds endpoint (an object, not a list). */
        fun parseEvent(rawJson: String, json: Json): RefEvent =
            json.decodeFromString(EventDto.serializer(), rawJson).toDomain()

        private fun isoSeconds(ms: Long): String = Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).toString()

        internal fun parseIsoMs(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }
}

class TheOddsApiException(message: String) : Exception(message)

@Serializable
private data class EventDto(
    val id: String,
    val sport_key: String = "",
    val commence_time: String,
    val home_team: String,
    val away_team: String,
    val bookmakers: List<BookmakerDto> = emptyList(),
) {
    fun toDomain(): RefEvent {
        val markets = bookmakers.flatMap { b ->
            val book = b.copy(key = TheOddsApiClient.canonicalBook(b.key), title = TheOddsApiClient.bookTitle(TheOddsApiClient.canonicalBook(b.key)).takeIf { it != b.key } ?: b.title)
            book.markets.flatMap { m -> m.toDomain(book, home_team, away_team) }
        }
        return RefEvent(
            id = id,
            sportKey = sport_key,
            commenceMs = TheOddsApiClient.parseIsoMs(commence_time) ?: 0L,
            home = home_team,
            away = away_team,
            markets = markets,
        )
    }
}

@Serializable
private data class BookmakerDto(
    val key: String,
    val title: String,
    val last_update: String? = null,
    val markets: List<MarketDto> = emptyList(),
)

@Serializable
private data class MarketDto(
    val key: String,
    val last_update: String? = null,
    val outcomes: List<OutcomeDto> = emptyList(),
) {
    fun toDomain(book: BookmakerDto, home: String, away: String): List<RefBookMarket> {
        PropStats.ODDS_API_MARKETS[key]?.let { stat -> return props(book, stat) }
        if (key == "alternate_spreads" || key == "alternate_totals") return alternates(book, home, away)
        val kind = when (key) {
            "h2h" -> LineKind.MONEYLINE
            "spreads" -> LineKind.SPREAD
            "totals" -> LineKind.TOTAL
            else -> return emptyList()
        }
        val quotes = outcomes.mapNotNull { o ->
            val side = when {
                kind == LineKind.TOTAL && o.name.equals("Over", true) -> Side.OVER
                kind == LineKind.TOTAL && o.name.equals("Under", true) -> Side.UNDER
                o.name == home -> Side.HOME
                o.name == away -> Side.AWAY
                else -> return@mapNotNull null
            }
            if (o.price <= 1.0) null else RefQuote(side, o.price, o.point)
        }
        if (quotes.size != outcomes.size || quotes.size < 2) return emptyList()
        return listOf(
            RefBookMarket(
                bookKey = book.key,
                bookTitle = book.title,
                kind = kind,
                quotes = quotes,
                lastUpdateMs = TheOddsApiClient.parseIsoMs(last_update ?: book.last_update),
            ),
        )
    }

    /**
     * An alternate-lines market lists every number in one flat outcome list: a spread's home side at -6.5 pairs with the away side at
     * +6.5, a total's Over 44.5 with its Under 44.5. A number priced on one side only can't be devigged and is dropped.
     */
    private fun alternates(book: BookmakerDto, home: String, away: String): List<RefBookMarket> {
        val spread = key == "alternate_spreads"
        val updated = TheOddsApiClient.parseIsoMs(last_update ?: book.last_update)
        val sided = outcomes.mapNotNull { o ->
            val point = o.point ?: return@mapNotNull null
            if (o.price <= 1.0 || !o.price.isFinite()) return@mapNotNull null
            val side = when {
                !spread && o.name.equals("Over", true) -> Side.OVER
                !spread && o.name.equals("Under", true) -> Side.UNDER
                spread && o.name == home -> Side.HOME
                spread && o.name == away -> Side.AWAY
                else -> return@mapNotNull null
            }
            RefQuote(side, o.price, point)
        }
        // Keyed by the home side's handicap (spreads) or the number (totals), like [RefBookMarket.line].
        val byLine = sided.groupBy { q -> if (spread && q.side == Side.AWAY) -q.point!! else q.point!! }
        return byLine.values.mapNotNull { qs ->
            val a = qs.singleOrNull { it.side == (if (spread) Side.HOME else Side.OVER) } ?: return@mapNotNull null
            val b = qs.singleOrNull { it.side == (if (spread) Side.AWAY else Side.UNDER) } ?: return@mapNotNull null
            RefBookMarket(book.key, book.title, if (spread) LineKind.SPREAD else LineKind.TOTAL, listOf(a, b), updated)
        }
    }

    /**
     * A prop market lists every player's line in one flat outcome list:
     * `{"name":"Over","description":"Josh Allen","price":1.87,"point":245.5}`. Each player and
     * number becomes one over/under line; a player the book prices on one side only (or twice on
     * one side) can't be devigged and is dropped. Yes/No markets are Over/Under 0.5.
     */
    private fun props(book: BookmakerDto, stat: String): List<RefBookMarket> {
        val yesNo = key in PropStats.YES_NO
        data class Leg(val player: String, val point: Double, val over: Boolean, val price: Double)
        val legs = outcomes.mapNotNull { o ->
            val player = o.description?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val over = when (o.name.trim().lowercase()) {
                "over", "yes" -> true
                "under", "no" -> false
                else -> return@mapNotNull null
            }
            val point = if (yesNo) 0.5 else o.point ?: return@mapNotNull null
            if (o.price <= 1.0 || !o.price.isFinite()) null else Leg(player, point, over, o.price)
        }
        val updated = TheOddsApiClient.parseIsoMs(last_update ?: book.last_update)
        return legs.groupBy { PlayerNames.key(it.player) to it.point }.values.mapNotNull { group ->
            val over = group.singleOrNull { it.over } ?: return@mapNotNull null
            val under = group.singleOrNull { !it.over } ?: return@mapNotNull null
            RefBookMarket(
                bookKey = book.key,
                bookTitle = book.title,
                kind = LineKind.PLAYER_PROP,
                quotes = listOf(RefQuote(Side.OVER, over.price, over.point), RefQuote(Side.UNDER, under.price, under.point)),
                lastUpdateMs = updated,
                subject = over.player,
                stat = stat,
            )
        }
    }
}

@Serializable
private data class OutcomeDto(
    val name: String,
    val price: Double,
    val point: Double? = null,
    /** Player props: the player's name. */
    val description: String? = null,
)

/**
 * A feed in The Odds API's format. ParlayAPI (Tj, 2026-09-30, RESEARCH.md §43) is "a drop-in replacement for the-odds-api: same endpoints, same
 * params, same response format" at `parlay-api.com/v1`, with Pinnacle, Novig, ProphetX, bet365 and the US books, player props included, at a
 * fraction of the price per credit; checked live on its free endpoint 2026-09-30 (15 books on an NFL game, Pinnacle's price seconds old).
 */
enum class OddsFeed(
    val sourceId: String,
    val propsId: String,
    val title: String,
    val base: String,
    val books: List<String>?,
    /** Credits a scan leaves on each key ([KeyPool.execute]'s reserve): ParlayAPI's last ones go to Pinnacle's closing lines for CLV. */
    val reserve: Int = 0,
    /** A key with this allowance or less (the free plan) isn't used for scans at all: too few credits to be worth more than the closes. */
    val freeLimit: Int = 0,
) {
    ODDS_API("oddsapi", "oddsapi_props", "The Odds API", "https://api.the-odds-api.com/v4", null),

    /** Its own ten books (its keys differ from The Odds API's for some): the sharp ones first, the Settings picker doesn't apply. */
    PARLAY(
        "parlay", "parlay_props", "ParlayAPI", "https://parlay-api.com/v1",
        listOf("pinnacle", "prophetx", "betonline", "bet365", "bovada", "draftkings", "fanduel", "caesars", "betmgm", "fanatics"),
        reserve = 300,
        freeLimit = 1_000,
    ),
    ;

    /** How this feed's scans spend a key's credits ([CreditPace]); null: as they come. [background]: auto-scan's, which leave half a day's share. */
    fun pace(policy: QuotaPolicy, background: Boolean = false): CreditPace? =
        if (reserve > 0) CreditPace(policy, reserve, freeLimit, keepOfDay = if (background) BACKGROUND_KEEP else 0.0) else null

    companion object {
        /** The part of a day's share background auto-scans leave for the scans Tj starts himself. */
        const val BACKGROUND_KEEP = 0.5
    }
}
