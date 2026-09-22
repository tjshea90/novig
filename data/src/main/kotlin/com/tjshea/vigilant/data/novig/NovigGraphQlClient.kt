package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyRotator
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Real client for Novig's own internal GraphQL backend — reverse-engineered by a third-party,
 * MIT-licensed package (`novig-liquidity`, `github.com/Hurteau101/Novig_Liquidity_Template`) whose
 * actual source Tj supplied and this class was verified against directly (RESEARCH.md §4.4), not
 * just a summarized briefing. Endpoint, both query strings, and the field shapes below are copied
 * from that working code, not inferred from documentation.
 *
 * **Real, disclosed risk — read RESEARCH.md §4.4/§9 before assuming this is a normal API client.**
 * There is no authentication of any kind (so this can't get Tj's own Novig *account* banned — it
 * never touches one), but Novig requires requests to come through rotating proxies to avoid
 * IP-based rate-limiting/anti-bot blocking, which is a real ToS gray area now that Novig is a
 * CFTC-regulated exchange. This is why [proxyRotator] isn't optional plumbing: with zero proxies
 * configured, [com.tjshea.vigilant.app.ScannerViewModel] never constructs this class at all and
 * falls back to sample data, the same opt-in pattern every other provider in this app already
 * uses — never a silent default.
 *
 * Pregame-only, as shipped by the reference package (`status: "OPEN_PREGAME"` is hardcoded into
 * both queries) — matches this app's current scope (RESEARCH.md §4.4 open item: the live-market
 * status value is unknown and unconfirmed).
 */
class NovigGraphQlClient(
    private val leagues: List<String>,
    private val proxyRotator: KeyRotator,
    private val json: Json,
    private val baseUrl: String = "https://gql.novig.us/v1/graphql",
) : NovigRepository {

    // Mirrors the reference package's own asyncio.Semaphore(5) — a deliberate politeness/
    // anti-detection cap, not just a performance knob (RESEARCH.md §4.4).
    private val requestSemaphore = Semaphore(5)

    override suspend fun getOpenMarkets(limit: Int): List<NovigEvent> {
        if (leagues.isEmpty()) return emptyList()
        val events = coroutineScope {
            leagues.map { league -> async { fetchLeague(league) } }.map { it.await() }
        }.flatten()
        return events.take(limit)
    }

    private suspend fun fetchLeague(league: String): List<NovigEvent> {
        // A league-query failure (proxies exhausted/invalid) is allowed to propagate — every
        // other league would fail identically, so surfacing one clear error beats silently
        // returning zero events with no explanation (matches ScannerViewModel's error handling).
        val eventIds = proxyRotator.execute("Novig (direct)") { proxyConfig ->
            executeGraphQl(proxyConfig, leagueRequestBody(league)) { raw -> parseLeagueResponse(raw) }
        }
        return coroutineScope {
            eventIds.map { eventId -> async { runCatching { fetchEvent(eventId) }.getOrNull() } }
                .map { it.await() }
        }.filterNotNull()
    }

    private suspend fun fetchEvent(eventId: String): NovigEvent? {
        return proxyRotator.execute("Novig (direct)") { proxyConfig ->
            executeGraphQl(proxyConfig, marketRequestBody(eventId)) { raw -> parseMarketResponse(raw) }
        }
    }

    private suspend fun <T> executeGraphQl(
        proxyConfig: String,
        requestBody: String,
        parse: (String) -> KeyAttemptResult<T>,
    ): KeyAttemptResult<T> {
        val parsedProxy = try {
            parseProxy(proxyConfig)
        } catch (e: IllegalArgumentException) {
            return KeyAttemptResult.Invalid(reason = "malformed proxy string: ${e.message}")
        }

        // A fresh client per attempt, deliberately — each proxy is its own connection config, and
        // this app's request volume (a handful of proxies, manual refresh) never justifies pooling
        // one client per proxy across scans.
        val client = OkHttpClient.Builder()
            .proxy(parsedProxy.proxy)
            .proxyAuthenticator(parsedProxy.authenticator)
            .build()
        val request = Request.Builder()
            .url(baseUrl)
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            requestSemaphore.withPermit {
                client.newCall(request).await().use { response ->
                    when {
                        response.code == 429 -> KeyAttemptResult.RateLimited(5_000, reason = "HTTP 429")
                        response.code == 401 || response.code == 403 ->
                            KeyAttemptResult.Invalid(reason = "HTTP ${response.code}")
                        !response.isSuccessful -> KeyAttemptResult.Invalid(reason = "HTTP ${response.code}")
                        else -> parse(response.body?.string().orEmpty())
                    }
                }
            }
        } catch (e: IOException) {
            // Dead/unreachable proxy, bad proxy auth, connect timeout, etc. — try the next proxy.
            KeyAttemptResult.Invalid(reason = (e::class.simpleName ?: "IOException") + (e.message?.let { ": $it" } ?: ""))
        }
    }

    private fun parseLeagueResponse(raw: String): KeyAttemptResult<List<String>> {
        val envelope = json.decodeFromString(LeagueResponseEnvelope.serializer(), raw)
        if (isTimeoutError(envelope.errors)) {
            return KeyAttemptResult.RateLimited(3_000, reason = "GraphQL time-limit-exceeded")
        }
        // Non-timeout GraphQL errors aren't this proxy's fault (matches the reference package's
        // own __default_caller, which returns an empty result rather than rotating away from a
        // working proxy over e.g. a bad league name).
        if (!envelope.errors.isNullOrEmpty()) return KeyAttemptResult.Success(emptyList())
        return KeyAttemptResult.Success(envelope.data?.event?.map { it.id } ?: emptyList())
    }

    private fun parseMarketResponse(raw: String): KeyAttemptResult<NovigEvent?> {
        val envelope = json.decodeFromString(MarketResponseEnvelope.serializer(), raw)
        if (isTimeoutError(envelope.errors)) {
            return KeyAttemptResult.RateLimited(3_000, reason = "GraphQL time-limit-exceeded")
        }
        if (!envelope.errors.isNullOrEmpty()) return KeyAttemptResult.Success(null)
        return KeyAttemptResult.Success(envelope.data?.event?.firstOrNull()?.let(::toNovigEvent))
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private const val LEAGUE_QUERY = """
            query MyQuery(${'$'}league: String!) {
              event(
                where: {
                  status: { _in: ["OPEN_PREGAME"] },
                  game: { league: { _eq: ${'$'}league } }
                }
              ) {
              game {
                  scheduled_start
                }
                id
                description
              }
            }
            """

        private const val MARKET_QUERY = """
                query (${'$'}eventId: uuid!) {
                  event(
                    where: {
                      _and: [
                        { id: { _eq: ${'$'}eventId } },
                        { _or: [
                          { status: { _eq: "OPEN_PREGAME" } }
                        ]}
                      ]
                    }
                  ) {
                    description
                    id
                    game {
                      scheduled_start
                    }
                    markets {
                      description
                      type
                      strike

                      player {
                        full_name
                      }

                      outcomes(
                        where: {
                          _or: [
                            { last: { _is_null: false } },
                            { available: { _is_null: false } },
                          ]
                        }
                      ) {
                        id
                        description
                        last
                        available
                        orders(
                          where: {
                            status: { _eq: "OPEN" },
                            currency: { _eq: "CASH" },
                          },
                          order_by: { price: desc }
                        ) {
                          status
                          qty
                          price
                          originalQty
                          created_at
                        }
                      }
                    }
                  }
                }
                """

        fun leagueRequestBody(league: String, json: Json = Json): String =
            json.encodeToString(LeagueRequestBody.serializer(), LeagueRequestBody(query = LEAGUE_QUERY, variables = LeagueVariables(league)))

        fun marketRequestBody(eventId: String, json: Json = Json): String =
            json.encodeToString(MarketRequestBody.serializer(), MarketRequestBody(query = MARKET_QUERY, variables = MarketVariables(eventId)))

        fun isTimeoutError(errors: List<GraphQlErrorDto>?): Boolean =
            errors?.any { it.extensions?.code == "time-limit-exceeded" } ?: false

        /**
         * Turns one GraphQL event into [NovigEvent], or null if it can't be evaluated at all
         * (no usable market survives [toNovigMarket], or no team-name pair could be derived —
         * see [deriveTeamNames]). Deliberately permissive at the market level (one bad market
         * doesn't sink the whole event) and strict at the event level (an event this app can't
         * name two teams for is useless to the scanner, not worth carrying through half-broken).
         */
        fun toNovigEvent(dto: MarketEventDto): NovigEvent? {
            val markets = dto.markets.mapNotNull(::toNovigMarket)
            if (markets.isEmpty()) return null
            val (teamA, teamB) = deriveTeamNames(dto) ?: return null
            return NovigEvent(
                eventId = dto.id,
                homeTeam = teamA,
                awayTeam = teamB,
                startTimeIso = dto.game?.scheduledStart.orEmpty(),
                isLive = false,
                markets = markets,
            )
        }

        /**
         * A market survives only with exactly two priced outcomes — same invariant [NovigMarket]
         * itself enforces (RESEARCH.md §4.1). Player props are out of scope for now (matches
         * [com.tjshea.vigilant.data.scanner.EvScanner]'s own doc comment) since Novig's `player`
         * field marks them explicitly and cleanly, unlike SharpAPI's flat rows.
         */
        fun toNovigMarket(dto: MarketDto): NovigMarket? {
            if (dto.player != null) return null
            val outcomes = dto.outcomes.mapNotNull(::toNovigOutcome)
            if (outcomes.size != 2) return null
            return NovigMarket(
                marketId = outcomes.joinToString("-") { it.outcomeId },
                marketType = classifyMarketType(dto, outcomes),
                description = dto.description,
                outcomes = outcomes,
            )
        }

        /**
         * Prefers the outcome's own `last` (most recent trade price) over the order book, since
         * it's the field Novig's schema dedicates to "the current price" rather than requiring a
         * guess about bid/ask semantics the reference package itself never had to resolve (its own
         * `highest_order` picks the largest-notional resting order for a liquidity summary, not a
         * best-execution price — a different problem than ours; RESEARCH.md §4.4 flags this
         * best-effort choice explicitly). Falls back to the best (highest-price) OPEN resting order
         * for a market with no trade history yet; an outcome with neither is skipped, same as any
         * other unparseable field in this client.
         */
        fun toNovigOutcome(dto: OutcomeDto): NovigOutcome? {
            val price = dto.last ?: dto.orders.filter { it.status == "OPEN" }.maxByOrNull { it.price }?.price
            if (price == null || price <= 0.0 || price >= 1.0) return null
            return NovigOutcome(outcomeId = dto.id, label = dto.description, price = price)
        }

        /**
         * Best-effort market-type classification — Novig's own `type` raw values aren't documented
         * anywhere (RESEARCH.md §4.4 open item). Tries the raw string first in case it's
         * human-readable, then falls back to strike/outcome-label heuristics modeled on the
         * reference package's own over/under keyword handling ([novig_base.py]'s `_get_line`).
         */
        fun classifyMarketType(dto: MarketDto, outcomes: List<NovigOutcome>): String {
            val rawType = dto.type?.lowercase().orEmpty()
            if ("moneyline" in rawType || rawType == "ml" || rawType == "h2h") return "MONEY"
            if ("spread" in rawType || rawType == "handicap") return "SPREAD"
            if ("total" in rawType || "over" in rawType || "under" in rawType) return "TOTAL"

            if (outcomes.any { isOverUnderLabel(it.label) }) return "TOTAL"
            return if (dto.strike == null) "MONEY" else "SPREAD"
        }

        private fun isOverUnderLabel(label: String): Boolean {
            val normalized = label.trim().lowercase()
            return normalized.startsWith("over") || normalized.startsWith("under")
        }

        /**
         * Novig's schema has no explicit home/away team fields (RESEARCH.md §4.4's central open
         * problem — "matching/normalization is the hard engineering problem, not the API calls").
         * Prefers a moneyline market's two outcome descriptions (almost certainly the team names
         * themselves), falling back to splitting the event's free-text `description` on a common
         * matchup separator. Order between the two returned names is not meaningful — see
         * [com.tjshea.vigilant.data.scanner.EventMatcher]'s order-independent matching.
         */
        fun deriveTeamNames(dto: MarketEventDto): Pair<String, String>? {
            val moneylineMarket = dto.markets.firstOrNull { market ->
                market.player == null &&
                    market.strike == null &&
                    market.outcomes.size == 2 &&
                    market.outcomes.none { isOverUnderLabel(it.description) } &&
                    market.outcomes.all { it.description.isNotBlank() }
            }
            if (moneylineMarket != null) {
                val (a, b) = moneylineMarket.outcomes
                return a.description.trim() to b.description.trim()
            }
            return parseMatchupDescription(dto.description)
        }

        private val MATCHUP_SEPARATORS = listOf(
            Regex("(?i)\\s+@\\s+"),
            Regex("(?i)\\s+at\\s+"),
            Regex("(?i)\\s+vs\\.?\\s+"),
        )

        fun parseMatchupDescription(description: String): Pair<String, String>? {
            if (description.isBlank()) return null
            for (separator in MATCHUP_SEPARATORS) {
                val parts = description.split(separator, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return parts[0].trim() to parts[1].trim()
                }
            }
            return null
        }

        /** `username:password@host:port`, HTTP Basic Auth to the proxy — same shape the verified package uses. */
        fun parseProxy(raw: String): ParsedProxy {
            val atIndex = raw.indexOf('@')
            require(atIndex > 0 && raw.indexOf('@', atIndex + 1) == -1) {
                "expected exactly one '@' in 'user:pass@host:port'"
            }
            val credPart = raw.substring(0, atIndex)
            val hostPart = raw.substring(atIndex + 1)

            val colonIndex = credPart.indexOf(':')
            require(colonIndex > 0) { "expected 'user:pass' before '@'" }
            val username = credPart.substring(0, colonIndex)
            val password = credPart.substring(colonIndex + 1)

            val portIndex = hostPart.lastIndexOf(':')
            require(portIndex > 0) { "expected 'host:port' after '@'" }
            val host = hostPart.substring(0, portIndex)
            val port = hostPart.substring(portIndex + 1).toIntOrNull()
                ?: throw IllegalArgumentException("invalid port in '$hostPart'")

            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
            val authenticator = Authenticator { _, response ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(username, password))
                    .build()
            }
            return ParsedProxy(proxy, authenticator)
        }
    }
}

data class ParsedProxy(val proxy: Proxy, val authenticator: Authenticator)

@Serializable
data class GraphQlErrorExtensionsDto(val code: String? = null)

@Serializable
data class GraphQlErrorDto(val message: String = "", val extensions: GraphQlErrorExtensionsDto? = null)

@Serializable
private data class LeagueVariables(val league: String)

@Serializable
private data class LeagueRequestBody(
    val operationName: String = "MyQuery",
    val query: String,
    val variables: LeagueVariables,
)

@Serializable
private data class MarketVariables(val eventId: String)

@Serializable
private data class MarketRequestBody(val query: String, val variables: MarketVariables)

@Serializable
data class LeagueEventDto(val id: String, val description: String = "")

@Serializable
data class LeagueDataDto(val event: List<LeagueEventDto> = emptyList())

@Serializable
data class LeagueResponseEnvelope(val data: LeagueDataDto? = null, val errors: List<GraphQlErrorDto>? = null)

@Serializable
data class GameDto(@SerialName("scheduled_start") val scheduledStart: String = "")

@Serializable
data class PlayerDto(@SerialName("full_name") val fullName: String = "")

@Serializable
data class OrderDto(
    val status: String = "",
    val qty: Double = 0.0,
    val price: Double = 0.0,
    val originalQty: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class OutcomeDto(
    val id: String,
    val description: String = "",
    val last: Double? = null,
    val available: Double? = null,
    val orders: List<OrderDto> = emptyList(),
)

@Serializable
data class MarketDto(
    val description: String = "",
    val type: String? = null,
    val strike: Double? = null,
    val player: PlayerDto? = null,
    val outcomes: List<OutcomeDto> = emptyList(),
)

@Serializable
data class MarketEventDto(
    val id: String,
    val description: String = "",
    val game: GameDto? = null,
    val markets: List<MarketDto> = emptyList(),
)

@Serializable
data class MarketDataDto(val event: List<MarketEventDto> = emptyList())

@Serializable
data class MarketResponseEnvelope(val data: MarketDataDto? = null, val errors: List<GraphQlErrorDto>? = null)
