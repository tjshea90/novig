package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
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
) : ReferenceSource {

    override val id = BOOK_KEY
    override val displayName = "Kalshi"

    override fun supports(league: League) = league.kalshiSeries.isNotEmpty()

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val now = clock()
        val series = league.kalshiSeries.filter { s -> familyOf(s)?.let { it in settings.families } ?: false }
        val events = ArrayList<EventDto>()
        for (s in series) events += fetchSeries(s)
        return RefSnapshot(league.oddsApiSportKey, parse(events, league, settings.exchangeMaxSpread, now), now, provider = id)
    }

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
            val page = http.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ReferenceException("Kalshi HTTP ${response.code}")
                json.decodeFromString(PageDto.serializer(), body)
            }
            out += page.events
            cursor = page.cursor?.takeIf { it.isNotBlank() }
            if (cursor == null || page.events.isEmpty()) return out
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

        fun familyOf(series: String): MarketFamily? = when {
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
            return if (a.isEmpty() || b.isEmpty() || ' ' in a || ' ' in b) null else a.uppercase() to b.uppercase()
        }

        private fun price(s: String?): Double? = s?.toDoubleOrNull()

        private fun tradable(m: MarketDto) = m.status == null || m.status == "active" || m.status == "open"

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
            val codes = group.firstNotNullOfOrNull { subTitleCodes(it.sub_title) } ?: return null
            val (awayCode, homeCode) = codes

            val gameEvent = group.firstOrNull { familyOf(it.series_ticker ?: it.event_ticker.substringBefore('-')) == MarketFamily.MONEYLINE }
            fun marketFor(code: String) = gameEvent?.markets?.firstOrNull { it.ticker.substringAfterLast('-').equals(code, true) }

            // Names: the game market's own names ("Carolina", "New York M", "Luis Hernandez"),
            // plus the nickname the spread/total titles add ("CAR Panthers" → "Carolina Panthers").
            val titled = group.firstNotNullOfOrNull { e -> titleTeams(e.title).takeIf { familyOf(e.series_ticker ?: "") != MarketFamily.MONEYLINE } }
            val gameTitled = gameEvent?.let { titleTeams(it.title) }
            fun name(code: String, index: Int): String? {
                val base = marketFor(code)?.yes_sub_title?.takeIf { it.isNotBlank() } ?: gameTitled?.toList()?.get(index)
                val nick = titled?.toList()?.get(index)?.let { t ->
                    val first = t.substringBefore(' ')
                    if (first.equals(code, true) && ' ' in t) t.substringAfter(' ').trim() else null
                }
                return when {
                    base == null -> titled?.toList()?.get(index)
                    nick != null && !base.contains(nick, true) -> "$base $nick"
                    else -> base
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
                val family = familyOf(e.series_ticker ?: e.event_ticker.substringBefore('-'))
                if (family != MarketFamily.SPREAD && family != MarketFamily.TOTAL) continue
                for (m in e.markets) {
                    if (!tradable(m)) continue
                    val strike = m.floor_strike ?: continue
                    // "Over X" loses at exactly X, unlike a sportsbook push, so only half points.
                    if (!PolymarketClient.isHalfPoint(strike)) continue
                    val (yes, no) = ExchangeQuote.toDecimal(price(m.yes_bid_dollars), price(m.yes_ask_dollars), maxSpread) ?: continue
                    if (family == MarketFamily.TOTAL) {
                        markets += RefBookMarket(BOOK_KEY, "Kalshi", LineKind.TOTAL, listOf(RefQuote(Side.OVER, yes, strike), RefQuote(Side.UNDER, no, strike)), now)
                    } else {
                        val teamCode = m.ticker.substringAfterLast('-').trimEnd { it.isDigit() }
                        val favorite = sideOfCode(teamCode) ?: continue
                        val dog = if (favorite == Side.AWAY) Side.HOME else Side.AWAY
                        markets += RefBookMarket(
                            BOOK_KEY, "Kalshi", LineKind.SPREAD,
                            listOf(RefQuote(favorite, yes, -strike), RefQuote(dog, no, strike)), now,
                        )
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
    )
}
