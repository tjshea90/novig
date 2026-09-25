package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyRotator
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant

/**
 * The Odds API v4 (RESEARCH.md §4.3), the fair-odds leg: Pinnacle plus the major US books.
 *
 * Credit cost is `markets x regions`, and naming up to 10 bookmakers counts as ONE region (their
 * docs, verified 2026-09-25). So this always sends `bookmakers=` (at most 10) instead of
 * `regions=`, which makes a moneyline+spread+total call cost 3 credits instead of the 9 the old
 * `regions=us,us2,eu` call cost. Novig itself is never requested here: it's the thing being
 * priced, not a reference.
 *
 * [KeyRotator] switches keys automatically (Tj's 2026-09-20 request): 401 (quota used up or bad
 * key) retires a key, 429 cools it down.
 */
class TheOddsApiClient(
    private val httpClient: OkHttpClient,
    private val keyRotator: KeyRotator,
    private val json: Json,
    private val baseUrl: String = "https://api.the-odds-api.com/v4",
    private val clock: () -> Long = System::currentTimeMillis,
) : ReferenceSource {

    override suspend fun odds(sportKey: String, bookmakers: List<String>): RefSnapshot {
        val books = bookmakers.filter { it != "novig" }.distinct().take(MAX_BOOKMAKERS_ONE_REGION)
        require(books.isNotEmpty()) { "Pick at least one reference sportsbook" }
        return keyRotator.execute("The Odds API") { key ->
            val url = "$baseUrl/sports/$sportKey/odds".toHttpUrl().newBuilder()
                .addQueryParameter("apiKey", key)
                .addQueryParameter("bookmakers", books.joinToString(","))
                .addQueryParameter("markets", "h2h,spreads,totals")
                .addQueryParameter("oddsFormat", "decimal")
                .addQueryParameter("dateFormat", "iso")
                .build()

            httpClient.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    429 -> {
                        val retry = response.header("Retry-After")
                        KeyAttemptResult.RateLimited(
                            retry?.toLongOrNull()?.times(1000) ?: 60_000,
                            reason = "HTTP 429" + (retry?.let { ", Retry-After=${it}s" } ?: ""),
                        )
                    }
                    401 -> KeyAttemptResult.Invalid(reason = "HTTP 401" + quotaHint(body))
                    // An out-of-season or unknown sport isn't the key's fault.
                    404 -> KeyAttemptResult.Success(RefSnapshot(sportKey, emptyList(), clock(), response.intHeader(REMAINING), response.intHeader(USED)))
                    else -> {
                        if (!response.isSuccessful) {
                            throw TheOddsApiException("The Odds API failed for $sportKey: HTTP ${response.code} ${body.take(200)}")
                        }
                        KeyAttemptResult.Success(
                            RefSnapshot(
                                sportKey = sportKey,
                                events = parseEvents(body, json),
                                fetchedAtMs = clock(),
                                creditsRemaining = response.intHeader(REMAINING),
                                creditsUsed = response.intHeader(USED),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun okhttp3.Response.intHeader(name: String): Int? = header(name)?.trim()?.toDoubleOrNull()?.toInt()

    private fun quotaHint(body: String): String =
        if (body.contains("quota", ignoreCase = true)) " — monthly credits used up" else ""

    companion object {
        const val REMAINING = "x-requests-remaining"
        const val USED = "x-requests-used"
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
