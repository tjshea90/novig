package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Real REST client for Novig's own official, credentialed API (RESEARCH.md §4.1 — base URL
 * `https://api.novig.com/nbx/v2`, OAuth2 via [NovigTokenProvider]) — the *sanctioned* path,
 * distinct from [com.tjshea.vigilant.data.novig.NovigGraphQlClient]'s unauthenticated, reverse-
 * engineered access to Novig's internal backend (RESEARCH.md §4.4). Tj's email to
 * developers@novig.com requesting real access is still unanswered as of §4.4 — this class stays
 * dormant, unwired into [com.tjshea.vigilant.app.ScannerViewModel], until that reply lands.
 *
 * IMPORTANT — the response DTOs and [parseOpenMarkets] below are a best-effort shape inferred
 * from documentation summaries. RESEARCH.md §4.4 found a second, independent, and more strongly
 * verified source (working code, not a doc-summarizing fetch) reporting that `docs.novig.com`'s
 * assumed OAuth/developer-portal system "does not hold up" — treat this class's very existence
 * (a real, official, credentialed API distinct from the internal GraphQL backend) as *unconfirmed
 * pending Novig's reply*, not just its field shapes. [parseOpenMarkets] is a standalone pure
 * function specifically so fixing it is a one-file change once real JSON — or a real answer about
 * whether this path exists at all — is in hand.
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
