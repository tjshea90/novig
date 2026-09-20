package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Real REST client for Novig's own official API (RESEARCH.md §4.1 — base URL
 * `https://api.novig.com/nbx/v2`, OAuth2 via [NovigTokenProvider]).
 *
 * IMPORTANT — the response DTOs and [parseOpenMarkets] below are a best-effort shape inferred
 * from documentation summaries (docs.novig.com was read via a doc-summarizing fetch, not a real
 * authenticated response — RESEARCH.md §10 item 5 flags this explicitly as unconfirmed). This
 * class is real, wired, and ready — but the parsing almost certainly needs adjusting against an
 * actual response the first time real credentials exist. [parseOpenMarkets] is a standalone pure
 * function specifically so that fix is a one-file change once we can see real JSON.
 */
class NovigApiClient(
    private val httpClient: OkHttpClient,
    private val tokenProvider: NovigTokenProvider,
    private val json: Json,
    private val baseUrl: String = "https://api.novig.com/nbx/v2",
) : NovigRepository {

    override suspend fun getOpenMarkets(limit: Int): List<NovigEvent> {
        val token = tokenProvider.getValidToken()
        val request = Request.Builder()
            .url("$baseUrl/markets/open?limit=$limit")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw NovigApiException("Novig open-markets request failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseOpenMarkets(body, json)
        }
    }

    companion object {
        fun parseOpenMarkets(rawJson: String, json: Json): List<NovigEvent> {
            val envelope = json.decodeFromString(OpenMarketsResponse.serializer(), rawJson)
            return envelope.events.map { it.toDomain() }
        }
    }
}

class NovigApiException(message: String) : Exception(message)

@Serializable
private data class OpenMarketsResponse(val events: List<EventDto> = emptyList())

@Serializable
private data class EventDto(
    val eventId: String,
    val homeTeam: String,
    val awayTeam: String,
    val startTime: String,
    val isLive: Boolean = false,
    val markets: List<MarketDto> = emptyList(),
) {
    fun toDomain(): NovigEvent = NovigEvent(
        eventId = eventId,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        startTimeIso = startTime,
        isLive = isLive,
        markets = markets.map { it.toDomain() },
    )
}

@Serializable
private data class MarketDto(
    val marketId: String,
    val marketType: String,
    val description: String,
    val outcomes: List<OutcomeDto>,
) {
    fun toDomain(): NovigMarket = NovigMarket(
        marketId = marketId,
        marketType = marketType,
        description = description,
        outcomes = outcomes.map { it.toDomain() },
    )
}

@Serializable
private data class OutcomeDto(
    val outcomeId: String,
    val label: String,
    val price: Double,
) {
    fun toDomain(): NovigOutcome = NovigOutcome(outcomeId = outcomeId, label = label, price = price)
}
