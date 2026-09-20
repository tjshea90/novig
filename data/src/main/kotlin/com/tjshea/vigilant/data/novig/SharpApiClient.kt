package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyRotator
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Real client for SharpAPI's `/odds` endpoint, filtered to Novig — this is the source for the
 * Novig leg (RESEARCH.md §4.2/§4.3): SharpAPI's free tier is the one provider whose coverage
 * actually includes Novig among its ~40 books, at 60s-delayed refresh, $0/mo, 12 req/min. Real
 * endpoint/response shape confirmed against SharpAPI's own docs (docs.sharpapi.io) 2026-09-20 —
 * higher confidence than the Novig client's inferred shape (RESEARCH.md §4.1/§10 item 5).
 *
 * Novig's own quoting convention is a decimal probability (RESEARCH.md §2); SharpAPI instead
 * normalizes every book including Novig into standard odds. [Odds.impliedProbability] on the
 * decimal odds SharpAPI reports recovers that same probability, so no separate conversion path
 * is needed for "the Novig leg" vs. any other book's odds.
 */
class SharpApiClient(
    private val httpClient: OkHttpClient,
    private val keyRotator: KeyRotator,
    private val json: Json,
    private val baseUrl: String = "https://api.sharpapi.io/api/v1",
) : NovigRepository {

    override suspend fun getOpenMarkets(limit: Int): List<NovigEvent> {
        return keyRotator.execute("SharpAPI") { key ->
            val url = "$baseUrl/odds".toHttpUrl().newBuilder()
                .addQueryParameter("sportsbook", "novig")
                .addQueryParameter("limit", limit.coerceAtMost(200).toString())
                .build()
            val request = Request.Builder().url(url).header("X-API-Key", key).get().build()

            httpClient.newCall(request).await().use { response ->
                when (response.code) {
                    429 -> {
                        val retryAfterHeader = response.header("Retry-After")
                        val retryAfterMs = (retryAfterHeader?.toLongOrNull()?.times(1000))
                            ?: response.header("X-RateLimit-Reset")?.toLongOrNull()?.let { resetEpochSeconds ->
                                (resetEpochSeconds * 1000 - System.currentTimeMillis()).coerceAtLeast(1_000)
                            }
                            ?: 60_000
                        KeyAttemptResult.RateLimited(
                            retryAfterMs,
                            reason = "HTTP 429" + (retryAfterHeader?.let { ", Retry-After=${it}s" } ?: ""),
                        )
                    }
                    401, 403 -> KeyAttemptResult.Invalid(reason = "HTTP ${response.code}")
                    else -> {
                        if (!response.isSuccessful) {
                            throw SharpApiException("SharpAPI request failed: HTTP ${response.code} — ${response.body?.string()}")
                        }
                        KeyAttemptResult.Success(parseOddsResponse(response.body?.string().orEmpty(), json))
                    }
                }
            }
        }
    }

    companion object {
        fun parseOddsResponse(rawJson: String, json: Json): List<NovigEvent> {
            val envelope = json.decodeFromString(SharpOddsResponse.serializer(), rawJson)
            return envelope.data
                .groupBy { EventKey(it.sportsbook, it.home_team, it.away_team, it.market_type, it.is_live) }
                .mapNotNull { (eventKey, rows) ->
                    // Every Novig market is exactly 2 outcomes (RESEARCH.md §4.1) — a row group
                    // that doesn't fit that shape is a market we can't safely evaluate; skip it
                    // rather than guess, the same way EvScanner skips outcome-count mismatches.
                    if (rows.size != 2) return@mapNotNull null
                    NovigEvent(
                        eventId = "${eventKey.homeTeam}|${eventKey.awayTeam}|${eventKey.marketType}",
                        homeTeam = eventKey.homeTeam,
                        awayTeam = eventKey.awayTeam,
                        startTimeIso = "",
                        isLive = eventKey.isLive,
                        markets = listOf(
                            NovigMarket(
                                marketId = "${eventKey.homeTeam}-${eventKey.awayTeam}-${eventKey.marketType}",
                                marketType = mapMarketType(eventKey.marketType),
                                description = eventKey.marketType,
                                outcomes = rows.map { row ->
                                    NovigOutcome(
                                        outcomeId = row.id,
                                        label = row.selection,
                                        price = row.odds_probability ?: Odds.impliedProbability(row.odds_decimal),
                                    )
                                },
                            ),
                        ),
                    )
                }
                // One event can have multiple market types (moneyline, spread, total) split across
                // separate groups above — merge those belonging to the same match back together.
                .groupBy { it.homeTeam to it.awayTeam }
                .map { (_, events) ->
                    events.first().copy(markets = events.flatMap { it.markets })
                }
        }

        private fun mapMarketType(sharpApiMarketType: String): String = when (sharpApiMarketType.lowercase()) {
            "moneyline" -> "MONEY"
            "spread" -> "SPREAD"
            "total" -> "TOTAL"
            else -> sharpApiMarketType.uppercase()
        }
    }
}

class SharpApiException(message: String) : Exception(message)

private data class EventKey(val sportsbook: String, val homeTeam: String, val awayTeam: String, val marketType: String, val isLive: Boolean)

@Serializable
private data class SharpOddsResponse(val data: List<SharpOddsRow> = emptyList())

@Serializable
private data class SharpOddsRow(
    val id: String,
    val sportsbook: String,
    val sport: String,
    val home_team: String,
    val away_team: String,
    val market_type: String,
    val selection: String,
    val odds_american: Int? = null,
    val odds_decimal: Double,
    val odds_probability: Double? = null,
    val is_live: Boolean = false,
)
