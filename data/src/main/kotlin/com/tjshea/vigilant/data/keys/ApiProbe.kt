package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.pinnodds.PinnKeyTest
import com.tjshea.vigilant.data.reference.Op4Parser
import com.tjshea.vigilant.data.reference.ParlayAccount
import com.tjshea.vigilant.data.reference.SgoUsage
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Asks each API, key by key, whether the key is alive and accepted, and, where the API offers a prompt that says how much is left, reads it and writes it straight into the usage meter (Tj, 2026-10-11:
 * "a small test button next to each api ... whether the key is active and accepted" and "if the apis offer a prompt to see how much usage is left, use that for the readings"). The prompts used, all free
 * of the allowance unless noted:
 *  - The Odds API: `GET /v4/sports` (does not count) and its `x-requests-remaining` / `x-requests-used` headers.
 *  - PropLine: any reply carries `X-Daily-Remaining` / `X-Daily-Used`; `GET /v1/sports` is used.
 *  - SportsGameOdds: `GET /v2/account/usage` (limits per interval and what is used).
 *  - ParlayAPI: `GET /v1/usage` (else `/v1/meta/api-key-check`) through [ParlayAccount].
 *  - OddsPapi v4 (free): `GET /v4/account` (unmetered): request_limit, request_count, valid_until.
 *  - OddsPapi v5: `GET /bookmakers` (accepted or not; v5 publishes no allowance, only per-second rate limits).
 *  - Unabated: `GET /league` (accepted or not; Free has no allowance, only one odds request per 5 s per key).
 *  - Pinnodds: `GET /panel/api/me` (plan, expiry, today's use).
 *  - PinnWire / pinnapi: no free prompt and no usage headers; the test makes ONE real request (it counts) and only the app's own count is shown.
 * A key refused by its API is marked refused in the meter, so the rotation skips it; a key found spent is held until its reset.
 */
class ApiProbe(
    private val http: OkHttpClient,
    private val meter: UsageMeter,
    private val parlayAccount: ParlayAccount?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val hosts: Hosts = Hosts(),
    private val pinnWireAccount: com.tjshea.vigilant.data.reference.PinnWireAccount? = null,
) {
    /** Where each API lives (overridden by tests). */
    data class Hosts(
        val oddsApi: String = "https://api.the-odds-api.com/v4",
        val propLine: String = "https://api.prop-line.com/v1",
        val sgo: String = "https://api.sportsgameodds.com/v2",
        val op5: String = "https://v5.oddspapi.io/en",
        val op4: String = "https://api.oddspapi.io",
        val unabated: String = "https://data.unabated.com",
        val sxbet: String = "https://api.sx.bet",
        val bolt: String = "https://spro.agency/api",
        val pinnodds: String = "https://pinnodds.com",
        val pinnwire: String = "https://pinnwire.com/kit/v1",
        val pinnapi: String = "https://pinnapi.com/kit/v1",
        val kalshi: String = "https://api.elections.kalshi.com/trade-api/v2",
        val polymarket: String = "https://gamma-api.polymarket.com",
    )

    /** One key's answer: [ok] = alive and accepted; [message] says so in words, with what is left when the API told. */
    data class Check(val masked: String, val ok: Boolean, val message: String)

    /** Every key of [provider], one at a time (the APIs' own limits are respected by the gap between keys). */
    suspend fun check(provider: ApiProvider, keys: List<String>): List<Check> {
        if (keys.isEmpty()) return listOf(Check("", false, "No key saved."))
        return keys.map { key ->
            val masked = UsageViews.mask(key)
            try {
                one(provider, key.trim()).let { (ok, msg) ->
                    // A key the API just accepted is no longer refused, whichever API it is: the rotation tries it again on the very next call.
                    if (ok) policyOf(provider)?.let { meter.recordAccepted(it, key.trim()) }
                    Check(masked, ok, msg)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Check(masked, false, "Could not be reached (${(e.message ?: e.javaClass.simpleName).take(80)}).")
            }
        }
    }

    /** The keyless feeds: reachable or not. */
    suspend fun reach(kalshi: Boolean): Check = try {
        val url = if (kalshi) "${hosts.kalshi}/exchange/status" else "${hosts.polymarket}/sports"
        val r = http.newCall(Request.Builder().url(url).get().build()).awaitText()
        if (r.isSuccessful) Check("no key", true, "Reachable (HTTP ${r.code}). Free, no key needed.") else Check("no key", false, "Answered HTTP ${r.code}.")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Check("no key", false, "Could not be reached (${(e.message ?: e.javaClass.simpleName).take(80)}).")
    }

    private suspend fun get(url: String, headers: List<Pair<String, String>>): com.tjshea.vigilant.data.HttpText =
        http.newCall(Request.Builder().url(url.toHttpUrl()).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build()).awaitText()

    private fun policyOf(p: ApiProvider): QuotaPolicy? = when (p) {
        ApiProvider.THE_ODDS_API -> QuotaPolicy.ODDS_API
        ApiProvider.PINNAPI -> QuotaPolicy.PINNAPI
        ApiProvider.PINNWIRE -> QuotaPolicy.PINNWIRE
        ApiProvider.PROPLINE -> QuotaPolicy.PROPLINE
        ApiProvider.PARLAY -> QuotaPolicy.PARLAY
        ApiProvider.SPORTSGAMEODDS -> QuotaPolicy.SGO
        ApiProvider.ODDSPAPI -> QuotaPolicy.ODDSPAPI
        ApiProvider.ODDSPAPI_FREE -> QuotaPolicy.ODDSPAPI_FREE
        ApiProvider.UNABATED -> QuotaPolicy.UNABATED
        ApiProvider.BOLTODDS -> QuotaPolicy.BOLTODDS
        ApiProvider.SXBET -> QuotaPolicy.SXBET
        ApiProvider.PINNODDS -> null
    }

    private fun refused(code: Int, what: String = "") = "Refused (HTTP $code): this key is not accepted${if (what.isNotEmpty()) " $what" else ""}."

    private suspend fun one(provider: ApiProvider, key: String): Pair<Boolean, String> = when (provider) {
        ApiProvider.THE_ODDS_API -> {
            val r = get("${hosts.oddsApi}/sports/?apiKey=$key", emptyList())
            val remaining = r.headers["x-requests-remaining"]?.trim()?.toDoubleOrNull()?.toInt()
            val used = r.headers["x-requests-used"]?.trim()?.toDoubleOrNull()?.toInt()
            when {
                r.code == 401 -> { meter.recordBalance(QuotaPolicy.ODDS_API, key, null, inactive = true); false to refused(401) }
                r.code == 429 -> { meter.recordBalance(QuotaPolicy.ODDS_API, key, 0, exhausted = true); true to "Accepted, but its credits are used up until the 1st." }
                r.isSuccessful -> {
                    if (remaining != null) meter.recordBalance(QuotaPolicy.ODDS_API, key, remaining, used = used)
                    true to "Active and accepted · ${remaining ?: "?"} of ${if (remaining != null && used != null) remaining + used else "?"} credits left this month."
                }
                else -> false to "Answered HTTP ${r.code}."
            }
        }
        ApiProvider.PROPLINE -> {
            val r = get("${hosts.propLine}/sports", listOf("X-API-Key" to key))
            val remaining = r.headers["X-Daily-Remaining"]?.trim()?.toIntOrNull()
            val used = r.headers["X-Daily-Used"]?.trim()?.toIntOrNull()
            when {
                r.code == 401 || r.code == 403 -> { meter.recordBalance(QuotaPolicy.PROPLINE, key, null, inactive = true); false to refused(r.code) }
                r.code == 429 -> { meter.recordBalance(QuotaPolicy.PROPLINE, key, 0, exhausted = true); true to "Accepted, but today's requests are used up (back at midnight UTC)." }
                r.isSuccessful || r.code == 404 -> {
                    // The plan is the key's own daily limit (Free 1,000, Hobby 5,000, Pro 25,000): a paid key unlocks +EV, closing lines, results, CLV and the exports, and goes first (TCL56).
                    val limit = r.headers["X-Daily-Limit"]?.trim()?.toIntOrNull()
                    if (remaining != null) meter.recordBalance(QuotaPolicy.PROPLINE, key, remaining, limit = limit, used = used)
                    val plan = limit?.let { com.tjshea.vigilant.data.reference.PropLineAccount.tierOf(it) }
                    true to "Active and accepted" + (plan?.let { " · ${it.replaceFirstChar { c -> c.uppercase() }} plan (${"%,d".format(limit)} a day)" + (if (com.tjshea.vigilant.data.reference.PropLineAccount.isPaid(limit)) ": history, +EV, closing lines, results and CLV are unlocked" else ": a paid plan unlocks history, +EV, closing lines, results and CLV") } ?: "") +
                        (remaining?.let { " · $it requests left today" + (used?.let { u -> " ($u used)" } ?: "") } ?: " (it did not say how many requests are left).")
                }
                else -> false to "Answered HTTP ${r.code}."
            }
        }
        ApiProvider.SPORTSGAMEODDS -> {
            val r = get("${hosts.sgo}/account/usage", listOf("x-api-key" to key, "Accept" to "application/json"))
            when {
                r.code == 401 -> { meter.recordBalance(QuotaPolicy.SGO, key, null, inactive = true); false to refused(401) }
                r.code == 403 -> false to refused(403, "(your plan does not include the usage prompt)")
                !r.isSuccessful -> false to "Answered HTTP ${r.code}."
                else -> {
                    val u = SgoUsage.parse(r.body)
                    val month = u?.intervals?.firstOrNull { it.name == "per-month" }
                    val left = if (month?.maxObjects != null && month.objects != null) (month.maxObjects - month.objects).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() else null
                    if (month != null) meter.recordBalance(QuotaPolicy.SGO, key, left, limit = month.maxObjects?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(), used = month.objects?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(), resetAtMs = month.endsAtMs, note = u.summary())
                    else meter.recordBalance(QuotaPolicy.SGO, key, null, note = u?.summary())
                    true to "Active and accepted · ${u?.summary() ?: "no limits listed"}"
                }
            }
        }
        ApiProvider.PARLAY -> {
            val pa = parlayAccount ?: return false to "ParlayAPI account reader is not available."
            pa.refresh(force = true)
            val c = pa.last[key]
            when {
                c == null -> false to "Could not read this key's account (no answer, or the key is not accepted)."
                c.valid == false -> false to "Not valid: ${c.reason ?: "refused"}."
                else -> true to "Active and accepted · ${c.remaining ?: "?"} of ${c.limit ?: "?"} credits left" + (c.tier?.let { " · plan $it" } ?: "")
            }
        }
        ApiProvider.ODDSPAPI_FREE -> {
            val r = get("${hosts.op4}/v4/account?apiKey=$key", emptyList())
            when {
                r.code == 401 || r.code == 403 -> {
                    meter.recordBalance(QuotaPolicy.ODDSPAPI_FREE, key, null, inactive = true)
                    val v5 = runCatching { get("${hosts.op5}/bookmakers", listOf("X-API-Key" to key)).isSuccessful }.getOrDefault(false)
                    false to (refused(r.code) + if (v5) " It is a v5 key: paste it under OddsPapi (v5)." else "")
                }
                !r.isSuccessful -> false to "Answered HTTP ${r.code}."
                else -> {
                    val a = Op4Parser.account(r.body)
                    meter.recordBalance(QuotaPolicy.ODDSPAPI_FREE, key, a.left, limit = a.limit, used = a.count, resetAtMs = a.validUntilMs, exhausted = a.active && a.left == 0, inactive = !a.active)
                    a.active to if (a.active) "Active and accepted · ${a.summary()}" else "The plan is not active."
                }
            }
        }
        ApiProvider.ODDSPAPI -> {
            val r = get("${hosts.op5}/bookmakers", listOf("X-API-Key" to key, "Accept" to "application/json"))
            when {
                r.code == 401 || r.code == 403 -> {
                    meter.recordBalance(QuotaPolicy.ODDSPAPI, key, null, inactive = true)
                    val v4 = runCatching { get("${hosts.op4}/v4/account?apiKey=$key", emptyList()).isSuccessful }.getOrDefault(false)
                    false to (refused(r.code) + if (v4) " It is a v4 (free) key: paste it under OddsPapi Free (v4)." else "")
                }
                r.code == 429 -> true to "Accepted, rate limited for the moment (HTTP 429)."
                r.isSuccessful -> true to "Active and accepted · OddsPapi v5 does not publish a request allowance${r.headers["X-RateLimit-Remaining"]?.let { " (rate limit left now: $it)" } ?: ""}."
                else -> false to "Answered HTTP ${r.code}."
            }
        }
        ApiProvider.SXBET -> {
            // The WebSocket token route accepts any valid key and is the cheapest read that proves the key (30 a minute); it carries no usage figure.
            val r = get("${hosts.sxbet}/user/realtime-token-v3/api-key", listOf("x-sx-api-key" to key, "Accept" to "application/json"))
            when {
                r.code == 401 || r.code == 403 -> { meter.recordBalance(QuotaPolicy.SXBET, key, null, inactive = true); false to refused(r.code) }
                r.code == 429 -> true to "Accepted, rate limited for the moment (HTTP 429)."
                r.isSuccessful -> { meter.recordAccepted(QuotaPolicy.SXBET, key); true to "Active and accepted · SX Bet publishes no allowance for market data (best odds 5,500 a minute)." }
                else -> false to "Answered HTTP ${r.code}."
            }
        }
        ApiProvider.UNABATED -> {
            val r = get("${hosts.unabated}/league", listOf("X-Api-Key" to key, "Accept" to "application/json"))
            when {
                r.code == 401 -> { meter.recordBalance(QuotaPolicy.UNABATED, key, null, inactive = true); false to refused(401, "(Free access can be revoked)") }
                r.code == 403 -> false to refused(403)
                r.isSuccessful -> true to "Active and accepted · Unabated Free has no allowance, only one odds request per 5 s per key."
                else -> false to "Answered HTTP ${r.code}."
            }
        }
        ApiProvider.BOLTODDS -> {
            // get_info names the sports and sportsbooks the key may subscribe to; it carries no usage figure (BOLTODDS_API.md §4): an invalid key answers 403 {"detail":"Invalid API token"}.
            val r = get("${hosts.bolt}/get_info?key=$key", listOf("Accept" to "application/json"))
            val detail = Regex("\"detail\"\\s*:\\s*\"([^\"]{1,80})\"").find(r.body)?.groupValues?.get(1)
            when {
                r.code == 401 || (r.code == 403 && r.body.contains("token", ignoreCase = true)) -> { meter.recordBalance(QuotaPolicy.BOLTODDS, key, null, inactive = true); false to refused(r.code, "(BoltOdds said: ${detail ?: "invalid token"})") }
                r.code == 403 -> false to "Refused (HTTP 403): ${detail ?: "your plan does not include this"}."
                r.code == 429 -> true to "Accepted, rate limited for the moment (HTTP 429: GET endpoints allow 12 a minute per IP)."
                r.isSuccessful -> {
                    meter.recordAccepted(QuotaPolicy.BOLTODDS, key)
                    val info = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(r.body) as? kotlinx.serialization.json.JsonObject }.getOrNull()
                    fun count(k: String) = (info?.get(k) as? kotlinx.serialization.json.JsonArray)?.size
                    true to "Active and accepted · ${count("sports") ?: "?"} sports, ${count("sportsbooks") ?: "?"} sportsbooks. BoltOdds publishes no usage figure: the odds come over its websocket (Pro: 3 connections at once)."
                }
                else -> false to "Answered HTTP ${r.code}."
            }
        }
        ApiProvider.PINNODDS -> when (val r = PinnKeyTest.run(http, key, hosts.pinnodds)) {
            is PinnKeyTest.Result.Ok -> true to "Active and accepted · ${r.summary(clock())}"
            is PinnKeyTest.Result.Bad -> false to r.message
        }
        ApiProvider.PINNWIRE, ApiProvider.PINNAPI -> {
            val wire = provider == ApiProvider.PINNWIRE
            val policy = if (wire) QuotaPolicy.PINNWIRE else QuotaPolicy.PINNAPI
            val r = get("${if (wire) hosts.pinnwire else hosts.pinnapi}/markets?sport_id=1&event_type=prematch", listOf((if (wire) "x-api-key" else "x-portal-apikey") to key))
            when {
                r.code == 401 || r.code == 403 -> { meter.recordBalance(policy, key, null, inactive = true); false to refused(r.code) }
                r.code == 429 -> { meter.recordDepleted(policy, key, "today's requests are used up", null); true to "Accepted, but today's requests are used up." }
                r.isSuccessful -> {
                    meter.recordCall(policy, key, 1)
                    val acct = if (wire) pinnWireAccount?.readOne(key) else null
                    val says = acct?.let { c -> c.remaining?.let { "PinnWire says $it requests left today" + (c.limit?.let { l -> " of $l" } ?: "") } ?: ("its account read held " + (c.figures.entries.take(8).joinToString(", ") { (k, v) -> "$k=${v.toLong()}" }.ifBlank { "no numbers" }) + ", no daily figure I could use") }
                    true to ("Active and accepted · this test used 1 request. " + (says?.plus(".") ?: "It sends no usage figure, so the meter shows the app's own count of 100 a day."))
                }
                else -> false to "Answered HTTP ${r.code}."
            }
        }
    }
}
