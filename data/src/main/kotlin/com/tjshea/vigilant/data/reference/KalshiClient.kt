package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
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
    private val gate = RateGate(
        ratePerSecond = START_RATE, burst = START_BURST, sleep = sleep, minRate = MIN_RATE,
        maxRate = MAX_RATE, rampEvery = RAMP_EVERY, rampStep = RAMP_STEP,
    )

    /**
     * Game lines first (Tj, 2026-09-28: "now it is reading the API very slow"): 57 series at 2/s is ~28 s, and since
     * v0.19.2 a league's bets wait for every source. A scan asks every league's game-line series first (29 of them,
     * ~15 s), then the props; a league's lines show as soon as its own are in.
     */
    override val linesFirst = true

    /** Series [lines] just read, for the [odds] call that follows: taken once, and only while young. */
    private val early = HashMap<String, Pair<Long, List<EventDto>>>()

    private fun selected(league: League, settings: ScanSettings) =
        league.kalshiSeries.filter { s -> familyOf(s)?.let { it in settings.families } ?: false }

    override suspend fun lines(league: League, settings: ScanSettings): RefSnapshot? {
        val now = clock()
        val series = selected(league, settings).filter { familyOf(it) != MarketFamily.PLAYER_PROPS }
        val events = ArrayList<EventDto>()
        var fetched = 0
        val read = readSeries(series)
        for (s in series) {
            // A failed read is left for [odds], which asks again for whatever didn't come and reports it.
            val got = read[s]?.getOrNull() ?: continue
            synchronized(early) { early[s] = now to got }
            events += got
            fetched++
        }
        if (fetched == 0) return null
        return RefSnapshot(league.oddsApiSportKey, parse(events, league, settings.exchangeMaxSpread, now), now, provider = id)
    }

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val now = clock()
        val series = selected(league, settings)
        val events = ArrayList<EventDto>()
        var failure: Exception? = null
        var fetched = 0
        // Every quote is stamped with the oldest read behind it: lines read by [lines] a few seconds ago never pass
        // for newer than they are (RESEARCH.md §24).
        var asOf = now
        // What [lines] just read stands in for asking again; only the rest is asked, several at a time.
        val already = series.associateWith { s -> synchronized(early) { early.remove(s) }?.takeIf { now - it.first in 0 until EARLY_MS } }
        val read = readSeries(series.filter { already[it] == null })
        for (s in series) {
            val kept = already[s]
            if (kept != null) {
                asOf = minOf(asOf, kept.first)
                events += kept.second
                fetched++
                continue
            }
            // One series failing (or throttled) mustn't cost the others: keep what came back. Null: skipped after a throttle.
            val got = read[s] ?: continue
            got.onSuccess { events += it; fetched++ }.onFailure { if (failure == null) failure = it as? Exception ?: RuntimeException(it) }
        }
        if (fetched == 0 && failure != null) throw failure
        return RefSnapshot(league.oddsApiSportKey, parse(events, league, settings.exchangeMaxSpread, asOf), asOf, provider = id)
    }

    private class KalshiThrottled : ReferenceException("Kalshi is limiting requests right now; it'll be back on the next scan.")

    /**
     * Each of [series] read, [PARALLEL] at a time (the [gate] still sets the pace: one request in flight was the real limit once the
     * pace was raised, a read takes ~0.2 s). Once Kalshi refuses, series not yet started are skipped (null) instead of asking a
     * limiting server more; a series that failed is its failure. Keyed by series.
     */
    private suspend fun readSeries(series: List<String>): Map<String, Result<List<EventDto>>?> = coroutineScope {
        val throttled = AtomicBoolean(false)
        val permits = Semaphore(PARALLEL)
        series.map { s ->
            s to async {
                permits.withPermit {
                    if (throttled.get()) return@withPermit null
                    try {
                        Result.success(fetchSeries(s))
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
                val url = "${host()}/events".toHttpUrl().newBuilder().apply {
                    addQueryParameter("series_ticker", series)
                    addQueryParameter("status", "open")
                    addQueryParameter("with_nested_markets", "true")
                    addQueryParameter("limit", "200")
                    cursor?.let { addQueryParameter("cursor", it) }
                }.build()
                gate.acquire()
                val result = try {
                    http.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                        val body = response.body?.string().orEmpty()
                        usage?.countKeyless(QuotaPolicy.KALSHI, calls = 1, throttled = if (response.code == 429) 1 else 0)
                        when {
                            response.code == 429 -> {
                                val wait = response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000) ?: 2_000L
                                gate.pause(System.currentTimeMillis() + wait)
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
        private fun tradable(m: MarketDto): Boolean {
            if (m.status != null && m.status != "active" && m.status != "open") return false
            val bid = m.yes_bid_size_fp?.toDoubleOrNull()
            val ask = m.yes_ask_size_fp?.toDoubleOrNull()
            return (bid == null || bid >= MIN_TOP_SIZE) && (ask == null || ask >= MIN_TOP_SIZE)
        }

        /** Contracts ($1 payout each) needed on each side of the best quote. */
        const val MIN_TOP_SIZE = 100.0

        /**
         * One [RefEvent] per game, merging its game, spread and total events (they share an event
         * code). Home and away follow Kalshi's "away vs home" titles.
         */
        fun parse(events: List<EventDto>, league: League, maxSpread: Double, now: Long): List<RefEvent> {
            val byGame = events.groupBy { it.event_ticker.substringAfter('-', "") }.filterKeys { it.isNotEmpty() }
            return byGame.mapNotNull { (code, group) -> game(code, group, league, maxSpread, now) }
        }

        private fun game(code: String, group: List<EventDto>, league: League, maxSpread: Double, now: Long): RefEvent? {
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

            fun sideOfCode(c: String): Side? = when {
                c.equals(awayCode, true) -> Side.AWAY
                c.equals(homeCode, true) -> Side.HOME
                else -> null
            }

            val markets = ArrayList<RefBookMarket>()
            // Moneyline: both teams' "wins" markets. Buying each team costs its own ask, so the
            // two asks form a two-way price with the spread as vig.
            if (gameEvent != null) {
                val a = marketFor(awayCode)?.takeIf(::tradable)
                val h = marketFor(homeCode)?.takeIf(::tradable)
                val quotes = when {
                    a != null && h != null -> {
                        val qa = ExchangeQuote.toDecimal(price(a.yes_bid_dollars), price(a.yes_ask_dollars), maxSpread)
                        val qh = ExchangeQuote.toDecimal(price(h.yes_bid_dollars), price(h.yes_ask_dollars), maxSpread)
                        if (qa != null && qh != null) listOf(RefQuote(Side.AWAY, qa.first, null), RefQuote(Side.HOME, qh.first, null)) else null
                    }
                    else -> (a ?: h)?.let { m ->
                        val q = ExchangeQuote.toDecimal(price(m.yes_bid_dollars), price(m.yes_ask_dollars), maxSpread) ?: return@let null
                        val yesSide = if (m === a) Side.AWAY else Side.HOME
                        val noSide = if (yesSide == Side.AWAY) Side.HOME else Side.AWAY
                        listOf(RefQuote(yesSide, q.first, null), RefQuote(noSide, q.second, null))
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
                    if (!tradable(m)) continue
                    val strike = if (firstInning) 0.5 else m.floor_strike ?: continue
                    // "Over X" loses at exactly X, unlike a sportsbook push, so only half points.
                    if (!PolymarketClient.isHalfPoint(strike)) continue
                    val (yes, no) = ExchangeQuote.toDecimal(price(m.yes_bid_dollars), price(m.yes_ask_dollars), maxSpread) ?: continue
                    val overUnder = listOf(RefQuote(Side.OVER, yes, strike), RefQuote(Side.UNDER, no, strike))
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
                            RefBookMarket(BOOK_KEY, "Kalshi", LineKind.SPREAD, listOf(RefQuote(favorite, yes, -strike), RefQuote(dog, no, strike)), now, period)
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
