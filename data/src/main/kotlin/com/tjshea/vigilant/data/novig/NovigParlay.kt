package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.reference.LineKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Parlays on Novig (NC1, Tj 2026-11-05: "Figure out if parlays can be made using the novig api").
 *
 * What the API gives (research/ncaab_ats_2026-11-05.md; docs.novig.com/affiliates/parlay-pricing, probed live 2026-11-05):
 *  - PRICING, yes: `POST /rfq/v2/quote/unauthenticated` with 2 to 20 outcome ids needs no key and no signature and answers one implied probability for the whole parlay, good for about 30 seconds.
 *    1 request a second per address (a burst of 10). It is meant for showing a user the price before they click through.
 *  - PLACING, no: parlays are filled by liquidity providers answering RFQs, a separate LP-only section (W-9, a deposit); the trading key's `/v3/orders` takes single outcomes only. So the app prices a parlay
 *    and opens the slip in Novig's own app with every leg filled in ([deepLink]); the tap that places it is Tj's, in Novig.
 *  - Measured: the quote tracked the product of the legs' MIDDLE prices within half a point and ran 1.5 to 4 points of probability (4% to 16%) below stacking the legs' take prices (8 of 8 two-leg samples).
 */
object NovigParlay {
    const val MIN_LEGS = 2
    const val MAX_LEGS = 20
    const val PATH = "/rfq/v2/quote/unauthenticated"
    const val HOST = "https://api.novig.com"
    /** The documented limit is one request a second per address; a little over it keeps a tap-happy user from being refused. */
    private const val MIN_GAP_MS = 1_100L

    /** [price] is the parlay's implied probability: one dollar of payout costs that much. */
    data class Quote(val price: Double, val expiresAtMs: Long, val serverNowMs: Long) {
        /** What a $1 stake returns in all if every leg wins. */
        val decimalOdds: Double get() = 1.0 / price
        /** How long the price lasts from Novig's own clock (not the phone's). */
        val lifetimeMs: Long get() = expiresAtMs - serverNowMs
    }

    sealed interface Result {
        data class Priced(val quote: Quote) : Result
        /** 400: Novig will not combine these legs (the body says why). */
        data class Refused(val why: String) : Result
        /** 404: no price right now. Not an error: hide the parlay price and try later. */
        data object NoPrice : Result
        data class Limited(val retryAfterS: Int?) : Result
        data class Failed(val why: String) : Result
    }

    fun parse(status: Int, body: String?, retryAfter: String? = null): Result = when {
        status == 200 -> runCatching {
            val o = Json.parseToJsonElement(body.orEmpty()).jsonObject
            Result.Priced(Quote(o["price"]!!.jsonPrimitive.content.toDouble(), o["expiresAt"]!!.jsonPrimitive.long, o["serverNow"]!!.jsonPrimitive.long))
        }.getOrElse { Result.Failed("an answer that is not a parlay price") }
        status == 400 -> Result.Refused(body.orEmpty().trim().take(200).ifBlank { "Novig can't combine these legs" })
        status == 404 -> Result.NoPrice
        status == 429 -> Result.Limited(retryAfter?.trim()?.toIntOrNull())
        else -> Result.Failed("HTTP $status")
    }

    private val lock = Any()
    private var lastAt = 0L
    private val held = HashMap<List<String>, Quote>()

    /** The price of the parlay of [outcomeIds] (kept until about two seconds before it expires, so the same legs are not asked twice: Novig counts a repeat against the limit). */
    suspend fun quote(http: OkHttpClient, outcomeIds: List<String>, baseUrl: String = HOST, clock: () -> Long = System::currentTimeMillis): Result {
        if (outcomeIds.size < MIN_LEGS) return Result.Refused("A parlay needs at least $MIN_LEGS legs")
        if (outcomeIds.size > MAX_LEGS) return Result.Refused("A parlay has at most $MAX_LEGS legs")
        val key = outcomeIds.sorted()
        synchronized(lock) { held[key]?.takeIf { it.expiresAtMs - 2_000L > clock() }?.let { return Result.Priced(it) } }
        val wait = synchronized(lock) { val t = clock(); val slot = maxOf(t, lastAt + MIN_GAP_MS); lastAt = slot; slot - t }
        if (wait > 0) kotlinx.coroutines.delay(wait)
        val json = """{"outcomeIds":[${outcomeIds.joinToString(",") { "\"$it\"" }}]}"""
        val req = Request.Builder().url(baseUrl + PATH).header("Accept", "application/json").post(json.toRequestBody("application/json".toMediaType())).build()
        return runCatching {
            http.newCall(req).await().use { r -> parse(r.code, r.body?.string(), r.header("Retry-After")) }
        }.getOrElse { Result.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }.also { res ->
            if (res is Result.Priced) synchronized(lock) { if (held.size > 64) held.clear(); held[key] = res.quote }
        }
    }

    // ---- the combination rules, checked before asking (Novig's answer stays the authority) ----------------------------------------------------

    /** What the pre-check needs of a leg. */
    data class Leg(val outcomeId: String, val eventId: String, val kind: LineKind, val teams: List<String>)

    /**
     * Why these legs can't be a parlay, or null when none of Novig's documented rules (docs.novig.com/api-reference/rfq/eligibility) is broken: 2 to 20 distinct legs; from one game, never two legs on the same
     * kind of line (a spread, a moneyline or another spread; two totals); from different games, never a shared team.
     */
    fun problem(legs: List<Leg>): String? {
        if (legs.size < MIN_LEGS) return "Add at least $MIN_LEGS legs"
        if (legs.size > MAX_LEGS) return "A parlay has at most $MAX_LEGS legs"
        if (legs.map { it.outcomeId }.toSet().size != legs.size) return "The same bet is in the parlay twice"
        fun family(k: LineKind) = if (k == LineKind.TOTAL || k == LineKind.TEAM_TOTAL) "total" else "side"
        for (i in legs.indices) for (j in i + 1 until legs.size) {
            val a = legs[i]
            val b = legs[j]
            if (a.eventId == b.eventId) {
                if (family(a.kind) == family(b.kind)) return "Two bets on the same kind of line in one game can't be a parlay (a spread, a moneyline or a second spread; two totals)"
            } else if (a.teams.any { x -> b.teams.any { y -> TeamMatcher.similarity(x, y) >= 0.9 } }) {
                return "Two games that share a team can't be a parlay"
            }
        }
        return null
    }

    /** The slip with every leg filled in, in Novig's app (`novigapp://events/<id>,<id>/<partner>[/<amount>]`, docs.novig.com/affiliates/deeplinking); [stake] pre-fills the wager. */
    fun deepLink(outcomeIds: List<String>, stake: Double? = null): String =
        "novigapp://events/${outcomeIds.joinToString(",")}/${NovigLinks.PARTNER}" + (stake?.takeIf { it > 0 }?.let { "/" + NovigLinks.amountText(it) } ?: "")

    /** The cost of taking every leg as its own bet, multiplied (what a parlay of straights would cost): to set beside the parlay's own price. */
    fun stackedCost(takes: List<Double>): Double = takes.fold(1.0) { a, b -> a * b }
}
