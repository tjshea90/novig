package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.engine.BookQuote
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Real client for The Odds API (RESEARCH.md §4.3 — free tier: 500 credits/mo, ~16 calls/day;
 * $30/mo for 20,000 credits; confirmed to include Pinnacle). This is the well-established public
 * v4 REST shape, unlike the Novig client's inferred one — this format is stable/widely documented.
 *
 * Requires an API key Tj signs up for himself at the-odds-api.com (free, no card needed for the
 * free tier) — this class does nothing useful without one. Not something this session can do on
 * Tj's behalf; see the top-level status message for the full "what you need to do" list.
 */
class TheOddsApiClient(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    private val json: Json,
    private val baseUrl: String = "https://api.the-odds-api.com/v4",
) : ReferenceOddsRepository {

    override suspend fun getOddsForSport(sportKey: String, marketKeys: List<String>): List<ReferenceEvent> {
        val url = "$baseUrl/sports/$sportKey/odds".toHttpUrl().newBuilder()
            .addQueryParameter("apiKey", apiKey)
            .addQueryParameter("regions", "us,us2,eu")
            .addQueryParameter("markets", marketKeys.joinToString(","))
            .addQueryParameter("oddsFormat", "decimal")
            .build()

        val request = Request.Builder().url(url).get().build()

        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw TheOddsApiException("The Odds API request failed: HTTP ${response.code} — ${response.body?.string()}")
            }
            val body = response.body?.string().orEmpty()
            return parseEvents(body, json)
        }
    }

    companion object {
        fun parseEvents(rawJson: String, json: Json): List<ReferenceEvent> {
            val events = json.decodeFromString(ListSerializer(EventDto.serializer()), rawJson)
            return events.map { it.toDomain() }
        }
    }
}

class TheOddsApiException(message: String) : Exception(message)

@Serializable
private data class EventDto(
    val id: String,
    val commence_time: String,
    val home_team: String,
    val away_team: String,
    val bookmakers: List<BookmakerDto> = emptyList(),
) {
    fun toDomain(): ReferenceEvent {
        val quotesByMarket = mutableMapOf<String, MutableList<BookQuote>>()
        for (bookmaker in bookmakers) {
            for (market in bookmaker.markets) {
                val decimalOdds = market.outcomes.map { it.price }
                quotesByMarket.getOrPut(market.key) { mutableListOf() }
                    .add(BookQuote(bookName = bookmaker.title, decimalOddsByOutcome = decimalOdds))
            }
        }
        return ReferenceEvent(
            homeTeam = home_team,
            awayTeam = away_team,
            commenceTimeIso = commence_time,
            quotesByMarket = quotesByMarket,
        )
    }
}

@Serializable
private data class BookmakerDto(
    val key: String,
    val title: String,
    val markets: List<MarketDto> = emptyList(),
)

@Serializable
private data class MarketDto(
    val key: String,
    val outcomes: List<OutcomeDto> = emptyList(),
)

@Serializable
private data class OutcomeDto(
    val name: String,
    val price: Double,
)
