package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.novig.RateGate
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.PropStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    private val clock: () -> Long = System::currentTimeMillis,
    private val usage: UsageMeter? = null,
    sleep: suspend (Long) -> Unit = { delay(it) },
) : ReferenceSource {

    override val id = BOOK_KEY
    override val displayName = "Kalshi"

    override fun supports(league: League) = league.kalshiSeries.isNotEmpty()

    /**
     * Anonymous reads get throttled well below the documented 20/s once a burst adds up (seen live
     * 2026-09-25: 429 after ~130 requests in ~40s), so every request waits its turn here.
     */
    private val gate = RateGate(ratePerSecond = 2.0, burst = 4, sleep = sleep)

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val now = clock()
        val series = league.kalshiSeries.filter { s -> familyOf(s)?.let { it in settings.families } ?: false }
        val events = ArrayList<EventDto>()
        var failure: Exception? = null
        var fetched = 0
        for (s in series) {
            try {
                events += fetchSeries(s)
                fetched++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One series failing (or throttled) mustn't cost the others: keep what came back.
                if (failure == null) failure = e
                if (e is KalshiThrottled) break
            }
        }
        if (fetched == 0 && failure != null) throw failure
        return RefSnapshot(league.oddsApiSportKey, parse(events, league, settings.exchangeMaxSpread, now), now, provider = id)
    }

    private class KalshiThrottled : ReferenceException("Kalshi is limiting requests right now; it'll be back on the next scan.")

    private suspend fun fetchSeries(series: String): List<EventDto> {
        val out = ArrayList<EventDto>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val url = "$baseUrl/events".toHttpUrl().newBuilder().apply {
                addQueryParameter("series_ticker", series)
                addQueryParameter("status", "open")
                addQueryParameter("with_nested_markets", "true")
                addQueryParameter("limit", "200")
                cursor?.let { addQueryParameter("cursor", it) }
            }.build()
            var page: PageDto? = null
            for (attempt in 0..1) {
                gate.acquire()
                val result = http.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                    val body = response.body?.string().orEmpty()
                    usage?.countKeyless(QuotaPolicy.KALSHI, calls = 1, throttled = if (response.code == 429) 1 else 0)
                    when {
                        response.code == 429 -> {
                            val wait = response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000) ?: 2_000L
                            gate.pause(System.currentTimeMillis() + wait)
                            gate.slowDown()
                            null
                        }
                        !response.isSuccessful -> throw ReferenceException("Kalshi HTTP ${response.code}")
                        else -> json.decodeFromString(PageDto.serializer(), body)
                    }
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
        const val MAX_PAGES = 5
        private val ET: ZoneId = ZoneId.of("America/New_York")
        private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

        /** `26SEP27CARCLE` → date, optional HHMM, and the team letters that follow. */
        private val CODE = Regex("^(\\d{2})([A-Z]{3})(\\d{2})(\\d{4})?([A-Z].*)$")
        private val TEAM_CODE = Regex("^[A-Z][A-Z0-9]{1,4}$")

        fun familyOf(series: String): MarketFamily? = when {
            series in PropStats.KALSHI_SERIES -> MarketFamily.PLAYER_PROPS
            series.endsWith("TEAMTOTAL") -> MarketFamily.TEAM_TOTAL
            listOf("1HSPREAD", "1HTOTAL", "F5SPREAD", "F5TOTAL").any { series.endsWith(it) } -> MarketFamily.FIRST_HALF
            series.endsWith("SPREAD") -> MarketFamily.SPREAD
            series.endsWith("TOTAL") -> MarketFamily.TOTAL
            series.endsWith("GAME") || series.endsWith("FIGHT") -> MarketFamily.MONEYLINE
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
                val period = if (family == MarketFamily.FIRST_HALF) 1 else 0
                val stat = PropStats.KALSHI_SERIES[series]
                for (m in e.markets) {
                    if (!tradable(m)) continue
                    val strike = m.floor_strike ?: continue
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
