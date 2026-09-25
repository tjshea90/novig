package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
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
 * Their docs ask clients to space requests out rather than burst (429 above 30 calls/s), so calls
 * are at least [minIntervalMs] apart. [KeyPool] picks the key (Tj's rotation rule): each call's
 * `x-requests-remaining`/`x-requests-used`/`x-requests-last` headers feed the usage meter, a key
 * that can't afford the next call is skipped, and `OUT_OF_USAGE_CREDITS` rests a key until the 1st.
 */
class TheOddsApiClient(
    private val httpClient: OkHttpClient,
    private val pool: KeyPool,
    private val json: Json,
    private val baseUrl: String = "https://api.the-odds-api.com/v4",
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = 1_500,
) : ReferenceSource {

    override val id = ID
    override val displayName = "The Odds API"
    override val metered = true

    override fun reuseMs(settings: ScanSettings): Long = settings.oddsApiReuseMinutes.coerceAtLeast(0) * 60_000L

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val markets = settings.families.mapNotNull { FAMILY_MARKETS[it] }.sorted()
        if (markets.isEmpty()) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        return fetch(league.oddsApiSportKey, settings.referenceBooks, markets)
    }

    private val spacing = Mutex()
    private var lastCallAt = 0L

    /** One sport's odds from the named [bookmakers]. Costs `markets.size` credits when anything comes back. */
    suspend fun fetch(sportKey: String, bookmakers: List<String>, markets: List<String> = ALL_MARKETS): RefSnapshot {
        val books = bookmakers.filter { it != "novig" }.distinct().take(MAX_BOOKMAKERS_ONE_REGION)
        require(books.isNotEmpty()) { "Pick at least one reference sportsbook" }
        spacing.withLock {
            val wait = lastCallAt + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastCallAt = System.currentTimeMillis()
        }
        // Cost = markets asked for x 1 region (<=10 named books), so the pool can skip a key
        // that can't afford it before asking.
        return pool.execute(cost = markets.size) { key ->
            val url = "$baseUrl/sports/$sportKey/odds".toHttpUrl().newBuilder()
                .addQueryParameter("apiKey", key)
                .addQueryParameter("bookmakers", books.joinToString(","))
                .addQueryParameter("markets", markets.joinToString(","))
                .addQueryParameter("oddsFormat", "decimal")
                .addQueryParameter("dateFormat", "iso")
                .build()

            httpClient.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                val remaining = response.intHeader(REMAINING)
                val used = response.intHeader(USED)
                val last = response.intHeader(LAST)
                when (response.code) {
                    429 -> {
                        val retry = response.header("Retry-After")
                        KeyAttemptResult.RateLimited(
                            retry?.toLongOrNull()?.times(1000) ?: 2_000,
                            reason = "HTTP 429" + (retry?.let { ", Retry-After=${it}s" } ?: ""),
                        )
                    }
                    401, 403 -> when {
                        body.contains("OUT_OF_USAGE_CREDITS") || body.contains("quota", ignoreCase = true) ->
                            KeyAttemptResult.Depleted("monthly credits used up")
                        else -> KeyAttemptResult.Invalid(reason = "HTTP ${response.code}" + errorCode(body)?.let { " $it" }.orEmpty())
                    }
                    // An out-of-season or unknown sport isn't the key's fault, and costs nothing.
                    404 -> KeyAttemptResult.Success(
                        RefSnapshot(sportKey, emptyList(), clock(), remaining, used, ID),
                        cost = last ?: 0, remaining = remaining, used = used,
                    )
                    else -> {
                        if (!response.isSuccessful) {
                            throw TheOddsApiException("The Odds API failed for $sportKey: HTTP ${response.code} ${body.take(200)}")
                        }
                        val events = parseEvents(body, json)
                        KeyAttemptResult.Success(
                            RefSnapshot(
                                sportKey = sportKey,
                                events = events,
                                fetchedAtMs = clock(),
                                creditsRemaining = remaining,
                                creditsUsed = used,
                                provider = ID,
                            ),
                            // Their rule: no events returned = no charge.
                            cost = last ?: if (events.isEmpty()) 0 else markets.size,
                            remaining = remaining,
                            used = used,
                        )
                    }
                }
            }
        }
    }

    private fun errorCode(body: String): String? =
        Regex("\"error_code\"\\s*:\\s*\"([A-Z_]+)\"").find(body)?.groupValues?.get(1)

    private fun okhttp3.Response.intHeader(name: String): Int? = header(name)?.trim()?.toDoubleOrNull()?.toInt()

    companion object {
        const val ID = "oddsapi"
        val ALL_MARKETS = listOf("h2h", "spreads", "totals")
        private val FAMILY_MARKETS = mapOf(MarketFamily.MONEYLINE to "h2h", MarketFamily.SPREAD to "spreads", MarketFamily.TOTAL to "totals")
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
            else -> KNOWN_BOOKMAKERS[key] ?: key
        }

        fun parseEvents(rawJson: String, json: Json): List<RefEvent> =
            json.decodeFromString(ListSerializer(EventDto.serializer()), rawJson).map { it.toDomain() }

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
        val markets = bookmakers.flatMap { book ->
            book.markets.mapNotNull { m -> m.toDomain(book, home_team, away_team) }
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
    fun toDomain(book: BookmakerDto, home: String, away: String): RefBookMarket? {
        val kind = when (key) {
            "h2h" -> LineKind.MONEYLINE
            "spreads" -> LineKind.SPREAD
            "totals" -> LineKind.TOTAL
            else -> return null
        }
        val quotes = outcomes.mapNotNull { o ->
            val side = when {
                kind == LineKind.TOTAL && o.name.equals("Over", true) -> Side.OVER
                kind == LineKind.TOTAL && o.name.equals("Under", true) -> Side.UNDER
                o.name == home -> Side.HOME
                o.name == away -> Side.AWAY
                o.name.equals("Draw", true) -> Side.DRAW
                else -> return@mapNotNull null
            }
            if (o.price <= 1.0) null else RefQuote(side, o.price, o.point)
        }
        if (quotes.size != outcomes.size || quotes.size < 2) return null
        return RefBookMarket(
            bookKey = book.key,
            bookTitle = book.title,
            kind = kind,
            quotes = quotes,
            lastUpdateMs = TheOddsApiClient.parseIsoMs(last_update ?: book.last_update),
        )
    }
}

@Serializable
private data class OutcomeDto(
    val name: String,
    val price: Double,
    val point: Double? = null,
)
