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
    /** ParlayAPI's degraded-mode check: books it says aren't keeping up are left out of its quotes (null: none left out). */
    val quality: ParlaySourceQuality? = null,
) : ReferenceSource {

    /** ParlayAPI's scans spend a day's share of the month at most ([CreditPace]); The Odds API's aren't paced. */
    private val pace: CreditPace? = feed.pace(pool.policy, background)

    /** ParlayAPI only: ask for alternate spreads and totals ([marketsFor]); the scan turns it off while a Pinnacle feed sends them. */
    @Volatile
    var alternates: Boolean = true

    override val id = feed.sourceId
    override val displayName = feed.title
    override val metered = true

    /** The books asked for: the feed's own list (ParlayAPI), else the reference books picked in Settings (their keys are The Odds API's). */
    fun booksFor(settings: ScanSettings): List<String> = if (settings.pinnacleOnly) ScanSettings.PINNACLE_BOOKS.toList() else feed.books ?: settings.referenceBooks

    /**
     * Tennis is keyed per tournament on The Odds API, never by the league key the app groups it under; ParlayAPI keys a whole tour
     * (`tennis_atp`, `tennis_wta`: PARLAY_API.md §6.11).
     */
    override fun supports(league: League) = league.oddsApiListed || (feed == OddsFeed.PARLAY && league.tennis)

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
        // Tennis' alternates are Pinnacle's set lines again (±1.5, 2.5): 2 credits a tour for nothing (PARLAY_API.md §6.11).
        val markets = marketsFor(settings.families, feed, alternates && !league.tennis)
        if (markets.isEmpty()) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = id)
        // ParlayAPI: only the games the scan can price (its window, plus a day for loose kickoff times), a smaller reply for the same credits.
        val until = if (feed == OddsFeed.ODDS_API) null else Planner.horizon(settings, clock()) + WINDOW_SLACK_MS
        // ParlayAPI's /odds leaves games under way out unless asked (its docs: include_live, no extra cost); The Odds API sends them anyway.
        val snap = fetch(league.oddsApiSportKey, booksFor(settings), markets, startsBeforeMs = until, includeLive = settings.includeLive && feed != OddsFeed.ODDS_API)
        return quality?.let { ParlaySourceQuality.without(snap, it.unsafeBooks()) } ?: snap
    }

    /** [fetch], without the books ParlayAPI says aren't keeping up ([ParlaySourceQuality]), as [odds] leaves them out of a scan. */
    suspend fun fetchCurrent(sportKey: String, bookmakers: List<String>, markets: List<String>, startsBeforeMs: Long? = null): RefSnapshot {
        val snap = fetch(sportKey, bookmakers, markets, startsBeforeMs)
        return quality?.let { ParlaySourceQuality.without(snap, it.unsafeBooks()) } ?: snap
    }

    private val spacing = Mutex()
    private var lastCallAt = 0L

    /** One sport's odds from the named [bookmakers]. Costs `markets.size` credits when anything comes back. */
    suspend fun fetch(
        sportKey: String, bookmakers: List<String>, markets: List<String> = ALL_MARKETS, startsBeforeMs: Long? = null, includeLive: Boolean = false,
    ): RefSnapshot {
        val books = pickBooks(bookmakers)
        val answer = call(
            path = "/sports/$sportKey/odds",
            params = listOf("bookmakers" to books.joinToString(","), "markets" to markets.joinToString(","), "oddsFormat" to "decimal") +
                listOfNotNull(startsBeforeMs?.let { "commenceTimeTo" to isoSeconds(it) }, ("include_live" to "true").takeIf { includeLive }),
            // Cost = markets asked for x 1 region (<=10 named books), so the pool can skip a key
            // that can't afford it before asking.
            cost = markets.size,
            what = sportKey,
            notFound = emptyList(),
            parse = { parseOdds(it, sportKey) },
            // Their rule: no events returned = no charge.
            charged = { if (it.isEmpty()) 0 else markets.size },
        )
        return RefSnapshot(sportKey, answer.value, clock(), answer.remaining, answer.used, id)
    }

    /** An /odds answer: on ParlayAPI's tennis tours, one event per singles match with set and games lines told apart ([ParlayTennis]). */
    private fun parseOdds(raw: String, sportKey: String): List<RefEvent> {
        val events = parseEvents(raw, json, seenByBook = feed == OddsFeed.PARLAY)
        if (feed != OddsFeed.PARLAY || !sportKey.startsWith(TENNIS_PREFIX)) return events
        val gapHours = com.tjshea.vigilant.data.scanner.Leagues.ALL.firstOrNull { it.oddsApiSportKey == sportKey }?.maxStartGapHours ?: 24
        return ParlayTennis.normalize(events, gapHours * 3_600_000L)
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
            parse = { parseEvent(it, json, seenByBook = feed == OddsFeed.PARLAY) },
            // Only a fallback: the x-requests-last header is the real charge.
            charged = { e -> e?.markets?.mapNotNull { it.stat }?.distinct()?.size ?: 0 },
        )
    }

    /**
     * [eventOdds] as the feed sent it (a tapped bet's sheet reads it with one-sided books kept: [OtherBooks]); null when the game is gone.
     * Costs one credit per market (x 1 region, ten books at most).
     */
    suspend fun eventOddsRaw(sportKey: String, eventId: String, bookmakers: List<String>, markets: List<String>): String? {
        require(markets.isNotEmpty()) { "No markets to ask for" }
        return call(
            path = "/sports/$sportKey/events/$eventId/odds",
            params = listOf("bookmakers" to pickBooks(bookmakers).joinToString(","), "markets" to markets.joinToString(","), "oddsFormat" to "decimal"),
            cost = markets.size,
            what = "$sportKey event $eventId (a bet's books)",
            notFound = null,
            parse = { it },
            charged = { raw -> if (raw == null) 0 else markets.count { m -> raw.contains("\"$m\"") } },
        ).value
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

    /** One of ParlayAPI's other endpoints' raw reply ([parlayGet]): 2xx, 404 (nothing to find) or 503 ([busy]). */
    class Reply(
        val code: Int,
        val body: String,
        /** How long a busy answer said to wait: its Retry-After header, else the body's `retry_after_seconds` (capped at [MAX_RETRY_AFTER_MS]). */
        val retryAfterMs: Long? = null,
    ) {
        /** ParlayAPI's "busy, retry shortly" (/verdict's `props_temporarily_busy`, /line-movement's `LINE_MOVEMENT_TIMEOUT`). */
        val busy: Boolean get() = code == 503
        val ok: Boolean get() = code in 200..299
    }

    /**
     * One of ParlayAPI's other endpoints (injuries, verdict, best-bets, movers, line-movement, period markets; PARLAY_API.md §6) through
     * the same key pool, pace and meter as a scan's calls. [cost] is what the call is charged; [busyCost] what a 503 "busy" answer is
     * charged (/line-movement's are, 2 credits each: PARLAY_API.md §1). A 503 comes back to the caller ([Reply.busy]), never retried here:
     * each endpoint says how long to wait. The credits left are read from the headers, else from the body's `credits.monthly_remaining`
     * (/verdict and /best-bets send no credit headers).
     */
    suspend fun parlayGet(path: String, params: List<Pair<String, String>>, cost: Int, what: String, busyCost: Int = 0): Answer<Reply> {
        check(feed == OddsFeed.PARLAY) { "ParlayAPI only" }
        spacing.withLock {
            val wait = lastCallAt + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastCallAt = System.currentTimeMillis()
        }
        return pool.execute(cost = cost, reserve = feed.reserve, pace = pace) { key ->
            val url = "$baseUrl$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val request = Request.Builder().url(url).get().header("X-API-Key", key).build()
            var attempt = 0
            while (true) {
                attempt++
                val response = try {
                    httpClient.newCall(request).await()
                } catch (e: java.io.IOException) {
                    if (attempt == 1) { delay(RETRY_AFTER_MS); continue }
                    throw e
                }
                // A gateway blip is worth one retry; a 503 is the endpoint saying it's busy, which the caller waits out its own way.
                if (attempt == 1 && (response.code == 502 || response.code == 504)) {
                    response.close()
                    delay(RETRY_AFTER_MS)
                    continue
                }
                return@execute response.use { r ->
                    val body = r.body?.string().orEmpty()
                    val credits = CreditHeaders.read({ r.header(it) }, clock())
                    val inBody = bodyCredits(body)
                    val remaining = credits.remaining ?: inBody?.first
                    val used = credits.used ?: inBody?.let { (left, limit) -> limit?.let { (it - left).coerceAtLeast(0) } }
                    when (r.code) {
                        429 -> KeyAttemptResult.RateLimited(r.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 2_000, reason = "HTTP 429")
                        401, 403 -> if (body.contains("credit_limit_exceeded") || body.contains("credit_exhausted") || body.contains("quota", true)) {
                            KeyAttemptResult.Depleted("monthly credits used up")
                        } else if (body.contains("HISTORICAL_LIMIT")) {
                            throw TheOddsApiException("${feed.title} can't reach that far back on this plan ($what)")
                        } else {
                            KeyAttemptResult.Invalid(reason = "HTTP ${r.code}" + errorCode(body)?.let { " $it" }.orEmpty())
                        }
                        404 -> KeyAttemptResult.Success(Answer(Reply(404, body), remaining, used), cost = credits.cost ?: 0, remaining = remaining, used = used)
                        503 -> {
                            val wait = retryAfterMs(r.header("Retry-After"))
                                ?: RETRY_SECONDS.find(body)?.groupValues?.get(1)?.let { retryAfterMs(it) }
                            KeyAttemptResult.Success(Answer(Reply(503, body, wait), remaining, used), cost = credits.cost ?: busyCost, remaining = remaining, used = used)
                        }
                        else -> {
                            if (!r.isSuccessful) throw TheOddsApiException("${feed.title} failed for $what: HTTP ${r.code} ${body.take(200)}")
                            KeyAttemptResult.Success(Answer(Reply(r.code, body), remaining, used), cost = credits.cost ?: cost, remaining = remaining, used = used, resetAtMs = credits.resetAtMs)
                        }
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("unreachable")
        }
    }

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
                    if (retryable && attempt < MAX_ATTEMPTS) { delay(RETRY_AFTER_MS * attempt); continue }
                    throw e
                }
                // A busy board ("props_temporarily_busy … Retry in a couple of seconds", as a 503 or inside a 200) is asked again twice, a second
                // and then two later (Tj's v0.52.0 file: NFL props failed 39 of 82 times with one retry, leaving the league's props without
                // ParlayAPI's books for that scan; a 200 "busy" read as an empty board).
                if (retryable && attempt < MAX_ATTEMPTS && (response.code in 502..504 || busyBody(response))) {
                    // ParlayAPI's best practices: a 503 with Retry-After says how long to wait; honor it (capped), else a second (then two).
                    val wait = retryAfterMs(response.header("Retry-After")) ?: (RETRY_AFTER_MS * attempt)
                    response.close()
                    delay(wait)
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

    /** A 200 whose small body is ParlayAPI's "busy, retry shortly" (`props_temporarily_busy`), read without using up the body. */
    private fun busyBody(response: okhttp3.Response): Boolean =
        response.code == 200 && runCatching { response.peekBody(BUSY_PEEK_BYTES).string().contains("temporarily_busy") }.getOrDefault(false)

    private fun errorCode(body: String): String? =
        Regex("\"error_code\"\\s*:\\s*\"([A-Z_]+)\"").find(body)?.groupValues?.get(1)

    private fun okhttp3.Response.intHeader(name: String): Int? = header(name)?.trim()?.toDoubleOrNull()?.toInt()

    companion object {
        const val ID = "oddsapi"
        private const val TENNIS_PREFIX = "tennis_"
        val ALL_MARKETS = listOf("h2h", "spreads", "totals")

        /** ParlayAPI's best practices: a 502 or a dropped connection is retried once, this much later. */
        const val RETRY_AFTER_MS = 1_000L

        /** ParlayAPI's calls go out at most this many times (two retries for a busy board or a gateway blip). */
        const val MAX_ATTEMPTS = 3

        /** How much of a 200's body is looked at for ParlayAPI's "busy" answer (a few hundred bytes; a real board is megabytes). */
        const val BUSY_PEEK_BYTES = 512L

        /** The longest Retry-After honored before a retry: past it the caller gives up and says it's busy. */
        const val MAX_RETRY_AFTER_MS = 20_000L

        private val RETRY_SECONDS = Regex("\"retry_after_seconds\"\\s*:\\s*(\\d+)")

        /** A Retry-After value in seconds ("15") as milliseconds, capped at [MAX_RETRY_AFTER_MS]; null when there's none or it isn't a number. */
        fun retryAfterMs(seconds: String?): Long? = seconds?.trim()?.toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 1000).toLong().coerceAtMost(MAX_RETRY_AFTER_MS) }

        private val MONTHLY_REMAINING = Regex("\"monthly_remaining\"\\s*:\\s*(\\d+)")
        private val MONTHLY_LIMIT = Regex("\"monthly_limit\"\\s*:\\s*(\\d+)")

        /** (credits left, the month's allowance) from an answer's `credits` object (/verdict, /best-bets), or null when it has none. */
        fun bodyCredits(body: String): Pair<Int, Int?>? {
            val left = MONTHLY_REMAINING.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return left to MONTHLY_LIMIT.find(body)?.groupValues?.get(1)?.toIntOrNull()
        }

        /** Past the scan's window, games still asked for: a feed's kickoff time can sit a while off Novig's. */
        const val WINDOW_SLACK_MS = 24 * 60 * 60_000L

        /** What a PropLine board covers for a game ([RefBookMarket.coverage]): its full-game lines. */
        private val GAME_LINES = setOf("MONEYLINE:0", "SPREAD:0", "TOTAL:0")
        private const val LIST_REUSE_MS = 5 * 60_000L
        private val FAMILY_MARKETS = mapOf(MarketFamily.MONEYLINE to "h2h", MarketFamily.SPREAD to "spreads", MarketFamily.TOTAL to "totals")

        /**
         * The markets a sport refresh buys, one credit each: the main lines, and on ParlayAPI (which serves them for a whole league in one
         * call, where The Odds API only sells them game by game) every alternate spread and total when [alternates], so Novig's alternate
         * lines are priced at their own numbers. ParlayAPI's alternates are Pinnacle's alone (Tj's key, 2026-09-30), which PinnWire and
         * pinnapi already send: with either on they'd be 2 credits a league for nothing.
         */
        fun marketsFor(families: Collection<MarketFamily>, feed: OddsFeed = OddsFeed.ODDS_API, alternates: Boolean = true): List<String> {
            val main = families.mapNotNull { FAMILY_MARKETS[it] }
            val alt = if (feed == OddsFeed.ODDS_API || !alternates) emptyList() else families.mapNotNull { ALT_MARKETS[it] }
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
        /**
         * The books a scan asks for, sharp first (Tj, 2026-09-30: "can I add more sports books to scan … Would it make the app more
         * accurate?"). PropLine reads every one of them at no extra cost; The Odds API, only PropLine's backup, asks the first
         * [MAX_BOOKMAKERS_ONE_REGION] (one credit per market per league). Every book here prices its own line: LowVig (BetOnline's
         * reduced-juice twin, the same line devigged) and betPARX/Unibet (BetRivers' Kambi line) would count one line twice (RESEARCH.md §46).
         */
        val DEFAULT_BOOKMAKERS = listOf(
            "pinnacle", "betonlineag", "draftkings", "fanduel", "betmgm", "betrivers",
            "hardrockbet", "bovada", "fliff", "williamhill_us", "fanatics", "espnbet",
        )

        /** Books added to the defaults in v0.35.0, for settings saved before ([ScanSettings.migrate]). */
        val ADDED_V35 = listOf("hardrockbet", "bovada", "fliff")

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
            "espnbet" to "theScore Bet",
            "fanatics" to "Fanatics",
            "betrivers" to "BetRivers",
            "hardrockbet" to "Hard Rock Bet",
            "fliff" to "Fliff",
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

        /**
         * [seenByBook]: date each quote by when the feed last saw its BOOK, not by its market's own `last_update`. ParlayAPI's market stamp is
         * when the price last moved (a steady Pinnacle total read 4 s ago carried a stamp 22 minutes old), and its bookmaker `last_update` is
         * "the freshest of (price-change, no-change verification heartbeat)" (its /odds docs; `verified_at` is per bookmaker too). Read the
         * market's stamp instead, a steady line aged out of the fair price minutes after it was last confirmed (Tj, 2026-10-02: "most odds say 9
         * minutes old", RESEARCH.md §63). The Odds API's market `last_update` is already "the last time our system saw odds for that market".
         */
        fun parseEvents(rawJson: String, json: Json, seenByBook: Boolean = false): List<RefEvent> =
            json.decodeFromString(ListSerializer(EventDto.serializer()), rawJson).map { it.toDomain(seenByBook) }

        /** One game from the per-event odds endpoint (an object, not a list). */
        fun parseEvent(rawJson: String, json: Json, seenByBook: Boolean = false): RefEvent =
            json.decodeFromString(EventDto.serializer(), rawJson).toDomain(seenByBook)

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
    fun toDomain(seenByBook: Boolean = false): RefEvent {
        val markets = bookmakers.flatMap { b ->
            val book = b.copy(key = TheOddsApiClient.canonicalBook(b.key), title = TheOddsApiClient.bookTitle(TheOddsApiClient.canonicalBook(b.key)).takeIf { it != b.key } ?: b.title)
            book.markets.flatMap { m -> m.toDomain(book, home_team, away_team, m.seenMs(book, seenByBook)) }
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
    /** ParlayAPI: [last_update] in milliseconds (read as a number of any shape: a decimal must not fail the whole answer). */
    val last_update_ms: Double? = null,
    val markets: List<MarketDto> = emptyList(),
) {
    /** When the feed last saw this book on this game (ParlayAPI: a price change or a poll that found the same price). */
    val seenMs: Long? get() = last_update_ms?.takeIf { it.isFinite() && it > 0 }?.toLong() ?: TheOddsApiClient.parseIsoMs(last_update)
}

@Serializable
private data class MarketDto(
    val key: String,
    val last_update: String? = null,
    val outcomes: List<OutcomeDto> = emptyList(),
) {
    /**
     * When the feed last saw this market's prices ([TheOddsApiClient.parseEvents]' `seenByBook`): its book's stamp, never older than the
     * market's own; else the market's own `last_update`, the book's when it has none.
     */
    fun seenMs(book: BookmakerDto, seenByBook: Boolean): Long? {
        val own = TheOddsApiClient.parseIsoMs(last_update)
        if (!seenByBook) return own ?: book.seenMs
        return listOfNotNull(own, book.seenMs).maxOrNull()
    }

    fun toDomain(book: BookmakerDto, home: String, away: String, updated: Long?): List<RefBookMarket> {
        PropStats.ODDS_API_MARKETS[key]?.let { stat -> return props(book, stat, updated) }
        if (key == "alternate_spreads" || key == "alternate_totals") return alternates(book, home, away, updated)
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
                lastUpdateMs = updated,
            ),
        )
    }

    /**
     * An alternate-lines market lists every number in one flat outcome list: a spread's home side at -6.5 pairs with the away side at
     * +6.5, a total's Over 44.5 with its Under 44.5. A number priced on one side only can't be devigged and is dropped.
     */
    private fun alternates(book: BookmakerDto, home: String, away: String, updated: Long?): List<RefBookMarket> {
        val spread = key == "alternate_spreads"
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
    private fun props(book: BookmakerDto, stat: String, updated: Long?): List<RefBookMarket> {
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
