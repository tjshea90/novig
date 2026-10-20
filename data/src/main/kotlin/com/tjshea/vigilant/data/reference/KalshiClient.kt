package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.kalshi.KalshiSigner
import com.tjshea.vigilant.data.kalshi.KalshiStream
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.novig.RateGate
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.PropStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Kalshi's public market data (RESEARCH.md §11): a CFTC-regulated exchange with game winner,
 * spread and total markets for the big US leagues. Free, no key for reads. The basic tier allows
 * 20 reads a second; a scan makes one request per series (up to three per league).
 *
 * Shapes, verified live 2026-09-25:
 *  - Game: event "Carolina vs Cleveland" (away vs home), one market per team, ticker suffix =
 *    team code (`KXNFLGAME-26SEP27CARCLE-CAR`), `yes_sub_title` = that team's name.
 *  - Spread: "CAR Panthers wins by over 20.5 points?", ticker `...-CAR21`, `floor_strike` 20.5.
 *    Yes = that team −20.5, No = the other team +20.5.
 *  - Total: "Over 45.5 points scored", `floor_strike` 45.5. Yes = Over.
 *  - The event code carries the Eastern date and, for baseball, the start time:
 *    `26SEP27CARCLE` (date only), `26SEP251840PITDET` (6:40pm ET), `...CHCBOSG2` (game 2).
 */
class KalshiClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://api.elections.kalshi.com/trade-api/v2",
    /**
     * Kalshi's docs now name this host as the production one (docs.kalshi.com market-data quickstart, read 2026-10-05; RESEARCH.md §90.2) while [baseUrl] still answers.
     * If [baseUrl] stops (a retired host: 404 / 410 or a name that no longer resolves), reads switch to this one for the life of the app instead of failing every scan. Null: none
     * (the tests'; the app passes [ALT_URL]).
     */
    private val altBaseUrl: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val usage: UsageMeter? = null,
    sleep: suspend (Long) -> Unit = { delay(it) },
    /**
     * Try the faster pace ([TRIAL_RATE] a second, Tj, 2026-10-07, proposal 9)? Read on every request, so the switch takes effect at once. The test runs for about [TRIAL_REQUESTS]
     * requests; at the first refusal (a 429) Kalshi goes back to the measured-safe [START_RATE] for the rest of this session ([paceNote] says what happened).
     */
    private val fastPace: () -> Boolean = { false },
    /** How many requests at the faster pace count as passed ([TRIAL_REQUESTS]; the tests use a few). */
    private val trialRequests: Int = TRIAL_REQUESTS,
    /**
     * Tj's Kalshi key (Settings › Kalshi; KALSHI_API.md): with one every read is signed, goes to the recommended `external-api` host and is paced by the account's own read budget
     * (`GET /account/limits`) instead of the anonymous 2 a second. Null: the public, unsigned reads as before.
     */
    private val signer: () -> KalshiSigner? = { null },
    /**
     * The socket (Settings › APIs › Kalshi › Socket): while it is live and a series' catalog was read after its markets were subscribed, a scan asks Kalshi for nothing and reads the prices the
     * socket holds. Null, or [socketOn] false: REST every scan, as before.
     */
    private val stream: KalshiStream? = null,
    private val socketOn: () -> Boolean = { false },
) : ReferenceSource {

    override val id = BOOK_KEY
    override val displayName = "Kalshi"

    override fun supports(league: League) = league.kalshiSeries.isNotEmpty()

    /**
     * Every request waits its turn here. Kalshi documents 20 reads a second for a basic account, but a series read here is the
     * events route with nested markets: 0.5-0.8 MB a reply uncompressed, and it refused (429, no Retry-After) 2 of 24 requests at
     * 4 a second and 3 of 36 at 6 on 2026-09-29, though a light `limit=1` market read took 20 a second for 300 requests
     * (RESEARCH.md §36.2). So the pace stays at the measured-safe 2 a second; [PARALLEL] only hides a reply's own delay.
     */
    private val slowGate = RateGate(
        ratePerSecond = START_RATE, burst = START_BURST, sleep = sleep, minRate = MIN_RATE,
        maxRate = MAX_RATE, rampEvery = RAMP_EVERY, rampStep = RAMP_STEP,
    )

    /** The pace of the measured test ([fastPace]): [TRIAL_RATE] a second until the first refusal. */
    private val fastGate = RateGate(
        ratePerSecond = TRIAL_RATE, burst = START_BURST, sleep = sleep, minRate = MIN_RATE,
        maxRate = TRIAL_RATE, rampEvery = RAMP_EVERY, rampStep = RAMP_STEP,
    )

    /** How the pace test has gone this session. */
    private val sent = java.util.concurrent.atomic.AtomicInteger(0)
    private val refusedAt = java.util.concurrent.atomic.AtomicInteger(0)

    /** The gate this request waits at: the faster one while the test is on and nothing has been refused at it, else the measured-safe pace. */
    private fun activeGate(): RateGate = if (signed() != null) authedGate else if (fastPace() && refusedAt.get() == 0) fastGate else slowGate

    /**
     * The pace test in a line for Diagnostics: off, still running (requests so far), passed (requests at the faster pace with none refused) or failed (the request Kalshi
     * refused, back to [START_RATE] for the rest of the session).
     */
    fun paceNote(): String {
        val n = sent.get()
        val hit = refusedAt.get()
        val fast = "${TRIAL_RATE.toInt()} a second"
        return when {
            hit > 0 -> "Kalshi pace test: FAILED, Kalshi refused request $hit at $fast: back to ${START_RATE.toInt()} a second for the rest of this session (the test is the setting Settings › Where fair odds come from › Kalshi)"
            !fastPace() -> "Kalshi pace test: off (${START_RATE.toInt()} a second, the measured-safe pace)"
            n == 0 -> "Kalshi pace test: on, $fast, no request made yet this session"
            n < trialRequests -> "Kalshi pace test: running at $fast, $n of $trialRequests requests so far, none refused"
            else -> "Kalshi pace test: PASSED, $n requests at $fast and none refused: Kalshi stays at $fast this session (a refusal now would send it back to ${START_RATE.toInt()})"
        }
    }

    /**
     * Game lines first (Tj, 2026-09-28: "now it is reading the API very slow"): 57 series at 2/s is ~28 s, and since
     * v0.19.2 a league's bets wait for every source. A scan asks every league's game-line series first (29 of them,
     * ~15 s), then the props; a league's lines show as soon as its own are in.
     */
    override val linesFirst = true

    /** Series [lines] just read, for the [odds] call that follows: taken once, and only while young. */
    private val early = HashMap<String, Pair<Long, List<EventDto>>>()

    /** A series as last read from REST, and when the read began: the catalog the socket's prices are laid over. */
    private class Catalog(val readAtMs: Long, val events: List<EventDto>)
    private val catalog = java.util.concurrent.ConcurrentHashMap<String, Catalog>()

    /** The account's read budget (tokens a second, `GET /account/limits`), when it has been read; and the tier's name for Diagnostics. */
    @Volatile private var readRefill: Int? = null
    @Volatile private var tier: String? = null
    @Volatile private var limitsReadAt = 0L
    @Volatile private var limitsNote: String? = null
    @Volatile private var authedGate: RateGate = RateGate(ratePerSecond = AUTH_START_RATE, burst = AUTH_BURST, sleep = sleep, minRate = MIN_RATE, maxRate = AUTH_START_RATE, rampEvery = RAMP_EVERY, rampStep = RAMP_STEP)
    private val sleepFn = sleep

    private fun signed(): KalshiSigner? = signer()

    private fun streamOn(): Boolean = stream != null && signed() != null && socketOn()

    /** Prices, sizes and status of [events] as the socket holds them, and when they are as of: now if every market is current on the socket, else the REST read's own time. */
    private fun view(readAtMs: Long, events: List<EventDto>): Pair<List<EventDto>, Long> {
        val st = stream
        if (st == null || !streamOn()) return events to readAtMs
        var allTrusted = true
        val out = events.map { e ->
            e.copy(markets = e.markets.map { m ->
                when {
                    st.isClosed(m.ticker) -> { allTrusted = allTrusted && st.trusted(m.ticker, readAtMs); m.copy(status = "closed") }
                    // A push received after the read began is newer than it; one received before is already in it (so the read stands).
                    st.trusted(m.ticker, readAtMs) -> st.top(m.ticker)?.takeIf { it.atMs >= readAtMs }?.let { t ->
                        m.copy(
                            yes_bid_dollars = t.yesBid?.let { fmt(it) } ?: m.yes_bid_dollars, yes_ask_dollars = t.yesAsk?.let { fmt(it) } ?: m.yes_ask_dollars,
                            yes_bid_size_fp = t.bidSize?.let { fmt(it) } ?: m.yes_bid_size_fp, yes_ask_size_fp = t.askSize?.let { fmt(it) } ?: m.yes_ask_size_fp,
                        )
                    } ?: m
                    else -> { allTrusted = false; m }
                }
            })
        }
        return out to (if (allTrusted) clock() else readAtMs)
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.4f", v)

    /** A series whose catalog is young enough, and whose every market the socket vouches for since it was read: no request needed. */
    private fun fromCatalog(series: String, now: Long): Catalog? {
        val st = stream ?: return null
        if (!streamOn() || !st.live) return null
        val c = catalog[series] ?: return null
        if (now - c.readAtMs > CATALOG_TTL_MS) return null
        if (c.events.any { e -> e.markets.any { m -> !st.trusted(m.ticker, c.readAtMs) } }) return null
        return c
    }

    /** What the account's limits and the socket say, for Diagnostics. */
    fun report(now: Long = clock()): List<String> = buildList {
        val k = signed()
        add(if (k == null) "key: none (public reads, ${START_RATE.toInt()} a second)" else "key: ${k.algorithm} ${k.keyId.take(8)}… · host ${altBaseUrl ?: baseUrl} · signed reads at ~${"%.1f".format(authedRate())} a second")
        add("account: " + (tier?.let { "tier $it, read budget ${readRefill ?: "?"} tokens a second" } ?: (limitsNote ?: "not read yet")) + (limitsReadAt.takeIf { it > 0 }?.let { " (read ${(now - it) / 60_000} min ago)" } ?: ""))
        add("catalog: ${catalog.size} series held" + (stream?.let { if (streamOn()) ", socket on" else ", socket off" } ?: ", no socket"))
        stream?.report(now)?.forEach { add(it) }
    }

    /** Signed reads' pace: half the account's read budget at the default 10 tokens a request, between [AUTH_START_RATE] and [AUTH_MAX_RATE]. */
    private fun authedRate(): Double = readRefill?.let { (it / DEFAULT_COST * 0.5).coerceIn(AUTH_START_RATE, AUTH_MAX_RATE) } ?: AUTH_START_RATE

    /** `GET /account/limits` (signed), once an hour: the pace follows the tier. A failure keeps the start pace and says why in [report]. */
    private suspend fun refreshLimits() {
        val k = signed() ?: return
        val now = clock()
        if (now - limitsReadAt < LIMITS_TTL_MS) return
        limitsReadAt = now
        try {
            val url = "${hostNow()}/account/limits".toHttpUrl()
            val req = Request.Builder().url(url).get().apply { k.headers("GET", url, now).forEach { (h, v) -> header(h, v) } }.build()
            http.newCall(req).await().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) { limitsNote = "account limits: HTTP ${r.code}"; return }
                val o = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return
                tier = (o["usage_tier"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val refill = ((o["read"] as? kotlinx.serialization.json.JsonObject)?.get("refill_rate") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                if (refill != null && refill > 0) {
                    readRefill = refill
                    val rate = authedRate()
                    authedGate = RateGate(ratePerSecond = rate, burst = AUTH_BURST, sleep = sleepFn, minRate = MIN_RATE, maxRate = rate, rampEvery = RAMP_EVERY, rampStep = RAMP_STEP)
                }
                limitsNote = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            limitsNote = "account limits: ${e.message?.take(80) ?: e.javaClass.simpleName}"
        }
    }

    private fun selected(league: League, settings: ScanSettings) =
        league.kalshiSeries.filter { s -> familyOf(s)?.let { it in settings.families } ?: false }

    override suspend fun lines(league: League, settings: ScanSettings): RefSnapshot? {
        val now = clock()
        refreshLimits()
        val series = selected(league, settings).filter { familyOf(it) != MarketFamily.PLAYER_PROPS }
        val events = ArrayList<EventDto>()
        var fetched = 0
        var asOf = now
        val read = readSeries(series)
        for (s in series) {
            // A failed read is left for [odds], which asks again for whatever didn't come and reports it.
            val got = read[s]?.getOrNull() ?: continue
            synchronized(early) { early[s] = got.readAtMs to got.events }
            val (v, at) = view(got.readAtMs, got.events)
            asOf = minOf(asOf, at)
            events += v
            fetched++
        }
        if (fetched == 0) return null
        return RefSnapshot(league.oddsApiSportKey, parse(events, league, settings.exchangeMaxSpread, asOf), asOf, provider = id)
    }

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val now = clock()
        refreshLimits()
        val series = selected(league, settings)
        val events = ArrayList<EventDto>()
        var failure: Exception? = null
        var fetched = 0
        // Every quote is stamped with the oldest read behind it: lines read by [lines] a few seconds ago never pass
        // for newer than they are (RESEARCH.md §24). With the socket, a market it vouches for is as new as the last push.
        var asOf = now
        // What [lines] just read stands in for asking again; only the rest is asked, several at a time.
        val already = series.associateWith { s -> synchronized(early) { early.remove(s) }?.takeIf { now - it.first in 0 until EARLY_MS } }
        val read = readSeries(series.filter { already[it] == null })
        for (s in series) {
            val kept = already[s]
            if (kept != null) {
                val (v, at) = view(kept.first, kept.second)
                asOf = minOf(asOf, at)
                events += v
                fetched++
                continue
            }
            // One series failing (or throttled) mustn't cost the others: keep what came back. Null: skipped after a throttle.
            val got = read[s] ?: continue
            got.onSuccess { r ->
                val (v, at) = view(r.readAtMs, r.events)
                asOf = minOf(asOf, at)
                events += v
                fetched++
            }.onFailure { if (failure == null) failure = it as? Exception ?: RuntimeException(it) }
        }
        if (fetched == 0 && failure != null) throw failure
        return RefSnapshot(league.oddsApiSportKey, parse(events, league, settings.exchangeMaxSpread, asOf), asOf, provider = id)
    }

    /**
     * Kalshi as a place to bet (Vigilant's Kalshi side, KV2): the same series read as [odds] (the socket's prices laid over them, the catalog shared, so a Novig scan that just ran
     * makes this free), parsed in venue mode: each price is what a taker pays (yes ask / 1 - yes bid), carries its market ticker and side, and its size. Never feeds a fair line.
     * [families] limits the series read (the venue's own market-kind switches); `null` takes the scan's.
     */
    suspend fun venueOdds(league: League, settings: ScanSettings): RefSnapshot {
        val now = clock()
        refreshLimits()
        val series = selected(league, settings)
        val events = ArrayList<EventDto>()
        var failure: Exception? = null
        var fetched = 0
        var asOf = now
        val read = readSeries(series)
        for (s in series) {
            val got = read[s] ?: continue
            got.onSuccess { r ->
                val (v, at) = view(r.readAtMs, r.events)
                asOf = minOf(asOf, at)
                events += v
                fetched++
            }.onFailure { if (failure == null) failure = it as? Exception ?: RuntimeException(it) }
        }
        if (fetched == 0 && failure != null) throw failure
        return RefSnapshot(league.oddsApiSportKey, parse(events, league, settings.exchangeMaxSpread, asOf, venue = true), asOf, provider = VENUE_ID)
    }

    private class KalshiThrottled : ReferenceException("Kalshi is limiting requests right now; it'll be back on the next scan.")

    /**
     * Each of [series] read, [PARALLEL] at a time (the [gate] still sets the pace: one request in flight was the real limit once the
     * pace was raised, a read takes ~0.2 s). Once Kalshi refuses, series not yet started are skipped (null) instead of asking a
     * limiting server more; a series that failed is its failure. Keyed by series.
     */
    private class Read(val readAtMs: Long, val events: List<EventDto>)

    private suspend fun readSeries(series: List<String>): Map<String, Result<Read>?> = coroutineScope {
        val throttled = AtomicBoolean(false)
        val permits = Semaphore(if (signed() != null) AUTH_PARALLEL else PARALLEL)
        series.map { s ->
            s to async {
                // The socket vouches for this whole series since its catalog was read: nothing to ask Kalshi.
                fromCatalog(s, clock())?.let { c -> stream?.use(); return@async Result.success(Read(c.readAtMs, c.events)) }
                permits.withPermit {
                    if (throttled.get()) return@withPermit null
                    try {
                        // The read counts from when it began: a push that landed meanwhile may or may not be in it.
                        val startedAt = clock()
                        val events = fetchSeries(s)
                        if (streamOn()) catalog[s] = Catalog(startedAt, events) else catalog.remove(s)
                        if (streamOn()) stream?.want(s, events.flatMap { e -> e.markets.map { it.ticker } })
                        Result.success(Read(startedAt, events))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (e is KalshiThrottled) throttled.set(true)
                        Result.failure(e)
                    }
                }
            }
        }.associate { (s, job) -> s to job.await() }
    }

    /** The host reads go to: [baseUrl] until it fails, then [altBaseUrl]. */
    @Volatile private var onAlt = false

    private fun host(): String = if (onAlt && altBaseUrl != null) altBaseUrl else baseUrl

    /** With a key, the recommended `external-api` host (the dedicated one for API traders); without, the host that has always answered unsigned reads. */
    private fun hostNow(): String = if (signed() != null && altBaseUrl != null && !onAlt) altBaseUrl else host()

    /** The first host failed in a way that means it is gone, not busy: the other one, if there is one and it isn't already the one in use. */
    private fun switchHost(): Boolean {
        if (altBaseUrl == null || onAlt) return false
        onAlt = true
        return true
    }

    private suspend fun fetchSeries(series: String): List<EventDto> {
        val out = ArrayList<EventDto>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            var page: PageDto? = null
            for (attempt in 0 until ATTEMPTS) {
                val url = "${hostNow()}/events".toHttpUrl().newBuilder().apply {
                    addQueryParameter("series_ticker", series)
                    addQueryParameter("status", "open")
                    addQueryParameter("with_nested_markets", "true")
                    addQueryParameter("limit", "200")
                    cursor?.let { addQueryParameter("cursor", it) }
                }.build()
                val gate = activeGate()
                val fastRequest = gate === fastGate
                gate.acquire()
                if (fastRequest) sent.incrementAndGet()
                val result = try {
                    http.newCall(Request.Builder().url(url).get().apply { signed()?.headers("GET", url, clock())?.forEach { (h, v) -> header(h, v) } }.build()).await().use { response ->
                        val body = response.body?.string().orEmpty()
                        usage?.countKeyless(QuotaPolicy.KALSHI, calls = 1, throttled = if (response.code == 429) 1 else 0)
                        when {
                            response.code == 429 -> {
                                val wait = response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000) ?: 2_000L
                                // The first refusal at the faster pace ends the test for this session: both gates wait, and every request after goes at the safe pace.
                                if (fastRequest) refusedAt.compareAndSet(0, sent.get().coerceAtLeast(1))
                                slowGate.pause(System.currentTimeMillis() + wait)
                                if (fastRequest) fastGate.pause(System.currentTimeMillis() + wait)
                                if (signed() != null) authedGate.pause(System.currentTimeMillis() + wait)
                                gate.slowDown()
                                null
                            }
                            // A retired host: the other one is tried at once (the same attempt count, the next try on it).
                            (response.code == 404 || response.code == 410) && switchHost() -> null
                            !response.isSuccessful -> throw ReferenceException("Kalshi HTTP ${response.code}")
                            else -> json.decodeFromString(PageDto.serializer(), body).also { gate.success() }
                        }
                    }
                } catch (e: java.net.UnknownHostException) {
                    // The host no longer resolves (not a timeout or a dropped connection, which say nothing about the host): the other one, once; else the failure stands.
                    if (!switchHost()) throw e
                    null
                }
                if (result != null) {
                    page = result
                    break
                }
            }
            val got = page ?: throw KalshiThrottled()
            out += got.events
            cursor = got.cursor?.takeIf { it.isNotBlank() }
            if (cursor == null || got.events.isEmpty()) return out
        }
        return out
    }

    companion object {
        const val BOOK_KEY = "kalshi"

        /** The provider id a venue read's snapshot carries (never a fair-line source's id). */
        const val VENUE_ID = "kalshi-venue"

        /** The host Kalshi's docs name as production now ([altBaseUrl]). */
        const val ALT_URL = "https://external-api.kalshi.com/trade-api/v2"

        /** How long a series [lines] read stands in for reading it again in [odds]: within one scan. */
        const val EARLY_MS = 60_000L

        /** "Run in first inning" (KXMLBRFI): Yes = at least one run = over 0.5 (its strike reads 1). */
        private const val RFI = "RFI"
        const val MAX_PAGES = 5

        /** Requests a second (no ramp: the measured-safe pace for the nested-markets route), and the burst allowed after a quiet spell. */
        const val START_RATE = 2.0
        const val MAX_RATE = 2.0
        const val MIN_RATE = 1.0
        const val START_BURST = 4
        const val RAMP_EVERY = 40
        const val RAMP_STEP = 0.5

        /** The measured test (Tj, 2026-10-07, proposal 9): requests a second, and how many requests count as passed. */
        const val TRIAL_RATE = 3.0
        const val TRIAL_REQUESTS = 300

        /**
         * Signed reads (KALSHI_API.md): the pace before the account's limits are known, the most it ever goes (a read of the nested-markets route may cost more than the default 10 tokens, and the docs
         * give no 429 cooldown), the burst, and how many series are read at once. [DEFAULT_COST] is the docs' "most requests cost 10 tokens".
         */
        const val AUTH_START_RATE = 4.0
        const val AUTH_MAX_RATE = 8.0
        const val AUTH_BURST = 8
        const val AUTH_PARALLEL = 4
        const val DEFAULT_COST = 10.0

        /** How long a series read stands as the catalog the socket's prices are laid over (new markets appear in it), and how often the account's limits are read. */
        const val CATALOG_TTL_MS = 5 * 60_000L
        const val LIMITS_TTL_MS = 60 * 60_000L

        /** Series read at once, and tries per page (a 429 waits Retry-After, then tries again). */
        const val PARALLEL = 2
        const val ATTEMPTS = 3
        private val ET: ZoneId = ZoneId.of("America/New_York")
        private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

        /** `26SEP27CARCLE` → date, optional HHMM, and the team letters that follow. */
        private val CODE = Regex("^(\\d{2})([A-Z]{3})(\\d{2})(\\d{4})?([A-Z].*)$")
        private val TEAM_CODE = Regex("^[A-Z][A-Z0-9]{1,4}$")

        fun familyOf(series: String): MarketFamily? = when {
            series in PropStats.KALSHI_SERIES -> MarketFamily.PLAYER_PROPS
            series.endsWith("TEAMTOTAL") -> MarketFamily.TEAM_TOTAL
            listOf("1HSPREAD", "1HTOTAL", "F5SPREAD", "F5TOTAL", RFI).any { series.endsWith(it) } -> MarketFamily.FIRST_HALF
            series.endsWith("SPREAD") -> MarketFamily.SPREAD
            series.endsWith("TOTAL") -> MarketFamily.TOTAL
            // Tennis: "KXATPMATCH-26SEP27JACRUB" "Jacquet vs Rublev", one "wins" market per player.
            series.endsWith("GAME") || series.endsWith("FIGHT") || series.endsWith("MATCH") -> MarketFamily.MONEYLINE
            else -> null
        }

        data class GameCode(val date: LocalDate, val time: LocalTime?, val teams: String)

        fun parseCode(eventTicker: String): GameCode? {
            val code = eventTicker.substringAfter('-', "")
            val m = CODE.matchEntire(code) ?: return null
            val (yy, mon, dd, hhmm, teams) = m.destructured
            val month = MONTHS.indexOf(mon) + 1
            if (month == 0) return null
            val date = runCatching { LocalDate.of(2000 + yy.toInt(), month, dd.toInt()) }.getOrNull() ?: return null
            val time = hhmm.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.of(it.take(2).toInt(), it.drop(2).toInt()) }.getOrNull() }
            return GameCode(date, time, teams)
        }

        /** "Carolina vs Cleveland", "CAR Panthers vs CLE Browns: Spread", "Fight Night: A vs B". */
        internal fun titleTeams(title: String): Pair<String, String>? {
            val parts = title.split(": ").filter { " vs " in it }
            val core = parts.firstOrNull() ?: return null
            val a = core.substringBefore(" vs ").trim()
            val b = core.substringAfter(" vs ").trim()
            return if (a.isEmpty() || b.isEmpty()) null else a to b
        }

        /** "PIT vs DET (Sep 25)" → PIT, DET. */
        internal fun subTitleCodes(subTitle: String?): Pair<String, String>? {
            val core = subTitle?.substringBefore(" (")?.trim() ?: return null
            val a = core.substringBefore(" vs ", "").trim()
            val b = core.substringAfter(" vs ", "").trim()
            return if (TEAM_CODE.matches(a) && TEAM_CODE.matches(b)) a to b else null
        }

        private fun price(s: String?): Double? = s?.toDoubleOrNull()

        /**
         * Open, and with real size on both sides of the top of the book. A 1¢-wide quote with 4
         * contracts behind it (a quarter of tight college alt lines, seen live 2026-09-25) says
         * nothing about the fair price. Missing size fields are given the benefit of the doubt.
         */
        private fun tradable(m: MarketDto, venue: Boolean = false): Boolean {
            if (m.status != null && m.status != "active" && m.status != "open") return false
            val bid = m.yes_bid_size_fp?.toDoubleOrNull()
            val ask = m.yes_ask_size_fp?.toDoubleOrNull()
            // The venue read (Kalshi as a place to bet, not as a fair line): any real size is a price one can take.
            val min = if (venue) MIN_VENUE_SIZE else MIN_TOP_SIZE
            return (bid == null || bid >= min) && (ask == null || ask >= min)
        }

        /** The venue read keeps a market with at least one contract on each side of the top of the book. */
        const val MIN_VENUE_SIZE = 1.0

        /** Contracts ($1 payout each) needed on each side of the best quote. */
        const val MIN_TOP_SIZE = 100.0

        /**
         * One [RefEvent] per game, merging its game, spread and total events (they share an event
         * code). Home and away follow Kalshi's "away vs home" titles.
         */
        fun parse(events: List<EventDto>, league: League, maxSpread: Double, now: Long, venue: Boolean = false): List<RefEvent> {
            val byGame = events.groupBy { it.event_ticker.substringAfter('-', "") }.filterKeys { it.isNotEmpty() }
            // The venue read lists every market that can be bought, however wide its spread: the price is what the buyer pays.
            val spread = if (venue) 1.0 else maxSpread
            return byGame.mapNotNull { (code, group) -> game(code, group, league, spread, now, venue) }
        }

        private fun game(code: String, group: List<EventDto>, league: League, maxSpread: Double, now: Long, venue: Boolean = false): RefEvent? {
            val parsed = parseCode(group.first().event_ticker) ?: return null
            val gameEvent = group.firstOrNull { familyOf(it.series_ticker ?: it.event_ticker.substringBefore('-')) == MarketFamily.MONEYLINE }
            // Team codes, away first: the game markets' ticker suffixes in the order the event
            // code lists them ("CARCLE"), else the "CAR vs CLE (Sep 27)" subtitle.
            val codes = gameEvent?.markets?.map { it.ticker.substringAfterLast('-') }?.distinct()
                ?.takeIf { it.size == 2 && it.all { c -> c in parsed.teams } }
                ?.sortedBy { parsed.teams.indexOf(it) }?.let { it[0] to it[1] }
                ?: group.firstNotNullOfOrNull { subTitleCodes(it.sub_title) }
                ?: return null
            val (awayCode, homeCode) = codes
            fun marketFor(code: String) = gameEvent?.markets?.firstOrNull { it.ticker.substringAfterLast('-').equals(code, true) }

            // Names: the game market's own names ("Carolina", "New York M", "Luis Hernandez"),
            // plus the nickname the spread/total titles add ("CAR Panthers" → "Carolina Panthers").
            val titled = group.firstNotNullOfOrNull { e -> titleTeams(e.title).takeIf { familyOf(e.series_ticker ?: "") != MarketFamily.MONEYLINE } }
            val gameTitled = gameEvent?.let { titleTeams(it.title) }
            fun name(code: String, index: Int): String? {
                val base = marketFor(code)?.yes_sub_title?.takeIf { it.isNotBlank() } ?: gameTitled?.toList()?.get(index)
                val fromTitle = titled?.toList()?.get(index)
                val nick = fromTitle?.let { t ->
                    val first = t.substringBefore(' ')
                    if (first.equals(code, true) && ' ' in t) t.substringAfter(' ').trim() else null
                }
                return when {
                    base != null && nick != null && !base.contains(nick, true) -> "$base $nick"
                    base != null -> base
                    else -> nick ?: fromTitle
                }
            }
            val away = name(awayCode, 0) ?: return null
            val home = name(homeCode, 1) ?: return null

            // The venue read names each price's market and side (what an order is placed on) and the size at that price.
            fun rq(side: Side, decimal: Double, point: Double?, m: MarketDto, yes: Boolean): RefQuote =
                if (!venue) RefQuote(side, decimal, point)
                else RefQuote(side, decimal, point, "${m.ticker}|${if (yes) "yes" else "no"}", (if (yes) m.yes_ask_size_fp else m.yes_bid_size_fp)?.toDoubleOrNull())

            fun sideOfCode(c: String): Side? = when {
                c.equals(awayCode, true) -> Side.AWAY
                c.equals(homeCode, true) -> Side.HOME
                else -> null
            }

            val markets = ArrayList<RefBookMarket>()
            // Moneyline: both teams' "wins" markets. Buying each team costs its own ask, so the
            // two asks form a two-way price with the spread as vig.
            if (gameEvent != null) {
                val a = marketFor(awayCode)?.takeIf { tradable(it, venue) }
                val h = marketFor(homeCode)?.takeIf { tradable(it, venue) }
                val quotes = when {
                    a != null && h != null -> {
                        val qa = ExchangeQuote.toDecimal(price(a.yes_bid_dollars), price(a.yes_ask_dollars), maxSpread)
                        val qh = ExchangeQuote.toDecimal(price(h.yes_bid_dollars), price(h.yes_ask_dollars), maxSpread)
                        if (qa != null && qh != null) listOf(rq(Side.AWAY, qa.first, null, a, true), rq(Side.HOME, qh.first, null, h, true)) else null
                    }
                    else -> (a ?: h)?.let { m ->
                        val q = ExchangeQuote.toDecimal(price(m.yes_bid_dollars), price(m.yes_ask_dollars), maxSpread) ?: return@let null
                        val yesSide = if (m === a) Side.AWAY else Side.HOME
                        val noSide = if (yesSide == Side.AWAY) Side.HOME else Side.AWAY
                        listOf(rq(yesSide, q.first, null, m, true), rq(noSide, q.second, null, m, false))
                    }
                }
                if (quotes != null) markets += RefBookMarket(BOOK_KEY, "Kalshi", LineKind.MONEYLINE, quotes, now)
            }

            for (e in group) {
                val series = e.series_ticker ?: e.event_ticker.substringBefore('-')
                val family = familyOf(series) ?: continue
                if (family == MarketFamily.MONEYLINE) continue
                val firstInning = series.endsWith(RFI)
                val period = when {
                    firstInning -> RefBookMarket.PERIOD_FIRST_INNING
                    family == MarketFamily.FIRST_HALF -> 1
                    else -> 0
                }
                val stat = PropStats.KALSHI_SERIES[series]
                for (m in e.markets) {
                    if (!tradable(m, venue)) continue
                    val strike = if (firstInning) 0.5 else m.floor_strike ?: continue
                    // "Over X" loses at exactly X, unlike a sportsbook push, so only half points.
                    if (!PolymarketClient.isHalfPoint(strike)) continue
                    val (yes, no) = ExchangeQuote.toDecimal(price(m.yes_bid_dollars), price(m.yes_ask_dollars), maxSpread) ?: continue
                    val overUnder = listOf(rq(Side.OVER, yes, strike, m, true), rq(Side.UNDER, no, strike, m, false))
                    val teamCode = m.ticker.substringAfterLast('-').trimEnd { it.isDigit() }
                    markets += when {
                        // "Bryce Young: 150+ passing yards": Yes = over 149.5.
                        stat != null -> {
                            val player = (m.yes_sub_title ?: m.title)?.takeIf { ':' in it }?.substringBefore(':')?.trim()
                            if (player.isNullOrEmpty()) continue
                            RefBookMarket(BOOK_KEY, "Kalshi", LineKind.PLAYER_PROP, overUnder, now, 0, player, stat)
                        }
                        // "Boston over 2.5 runs scored", ticker suffix BOS3.
                        family == MarketFamily.TEAM_TOTAL -> {
                            val side = sideOfCode(teamCode) ?: continue
                            RefBookMarket(BOOK_KEY, "Kalshi", LineKind.TEAM_TOTAL, overUnder, now, 0, if (side == Side.AWAY) RefBookMarket.AWAY else RefBookMarket.HOME)
                        }
                        series.endsWith("SPREAD") -> {
                            // "CAR wins by over 2.5": Yes = that team -2.5, No = the other +2.5.
                            val favorite = sideOfCode(teamCode) ?: continue
                            val dog = if (favorite == Side.AWAY) Side.HOME else Side.AWAY
                            RefBookMarket(BOOK_KEY, "Kalshi", LineKind.SPREAD, listOf(rq(favorite, yes, -strike, m, true), rq(dog, no, strike, m, false)), now, period)
                        }
                        else -> RefBookMarket(BOOK_KEY, "Kalshi", LineKind.TOTAL, overUnder, now, period)
                    }
                }
            }

            val start = parsed.time?.let { ZonedDateTime.of(parsed.date, it, ET).toInstant().toEpochMilli() }
            return RefEvent(
                id = "kalshi:$code",
                sportKey = league.oddsApiSportKey,
                // Without a time in the code, noon Eastern on the game's date stands in for sorting.
                commenceMs = start ?: ZonedDateTime.of(parsed.date, LocalTime.NOON, ET).toInstant().toEpochMilli(),
                home = home,
                away = away,
                markets = markets,
                etDate = if (start == null) parsed.date.toString() else null,
            )
        }

        /** The Eastern-time calendar date of an instant, as `yyyy-MM-dd`. */
        fun etDate(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).atZone(ET).toLocalDate().toString()
    }

    @Serializable
    data class PageDto(val events: List<EventDto> = emptyList(), val cursor: String? = null)

    @Serializable
    data class EventDto(
        val event_ticker: String,
        val series_ticker: String? = null,
        val title: String = "",
        val sub_title: String? = null,
        val markets: List<MarketDto> = emptyList(),
    )

    @Serializable
    data class MarketDto(
        val ticker: String,
        val title: String? = null,
        val yes_sub_title: String? = null,
        val yes_bid_dollars: String? = null,
        val yes_ask_dollars: String? = null,
        val floor_strike: Double? = null,
        val status: String? = null,
        val yes_bid_size_fp: String? = null,
        val yes_ask_size_fp: String? = null,
    )
}
