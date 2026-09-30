package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.tracker.BetGrader
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * The prop market keys ParlayAPI's own props board uses, as last seen in `/props` rows (each book names a market its own way:
 * `player_passing_attempts`, `player_pass_attempts`): /verdict is asked with the one its board carries most, else Vigilant's own key for
 * the stat ([PropStats.parlayMarkets], the canonical names in ParlayAPI's `/v1/meta/markets`).
 */
object ParlayMarketKeys {
    private val seen = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    fun record(sportKey: String, stat: String, key: String) {
        seen.getOrPut("$sportKey|$stat") { ConcurrentHashMap() }.merge(key, 1, Int::plus)
    }

    /** The key to ask /verdict for [stat] in [sportKey], or null when Vigilant doesn't price it there. */
    fun keyFor(sportKey: String, stat: String): String? =
        seen["$sportKey|$stat"]?.maxByOrNull { it.value }?.key ?: PropStats.parlayMarkets(sportKey).firstOrNull { it.second == stat }?.first

    /** Tests. */
    fun clear() = seen.clear()
}

/** What a /verdict call is asked about: one bet, at its own price (American). */
data class VerdictQuery(
    val sportKey: String,
    /** `h2h`, `spreads`, `totals` or a player prop key. */
    val market: String,
    /** `home` / `away` (game lines) or `over` / `under` (totals, props). */
    val side: String,
    val home: String,
    val away: String,
    val player: String? = null,
    val line: Double? = null,
    val price: Int,
) {
    fun params(): List<Pair<String, String>> = listOfNotNull(
        "sport" to sportKey, "market" to market, "side" to side, "home" to home, "away" to away,
        player?.let { "player" to it }, line?.let { "line" to fmt(it) }, "price" to price.toString(),
        // Novig is where Tj bets: the best-price call is scoped to it, and Pinnacle anchors the fair line.
        "books" to "novig", "sharpBook" to "pinnacle",
    )

    companion object {
        private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

        /**
         * A query for [pick] in the game [event] ("Away @ Home"), at [american]. Null for what /verdict can't grade: a part of the game (1st
         * half, first 5 innings), a team total, a prop Vigilant has no ParlayAPI key for, a game it can't name.
         */
        fun of(sportKey: String, event: String, pick: BetGrader.Pick, american: Int): VerdictQuery? {
            val m = NovigText.parseMatchup(event) ?: return null
            fun sideOf(team: String): String? = TeamMatcher.labelIsAway(team, m.away, m.home)?.let { if (it) "away" else "home" }
            return when (pick) {
                is BetGrader.Pick.Moneyline -> sideOf(pick.team)?.let { VerdictQuery(sportKey, "h2h", it, m.home, m.away, price = american) }
                is BetGrader.Pick.Spread -> if (pick.period != BetGrader.Period.GAME) null
                else sideOf(pick.team)?.let { VerdictQuery(sportKey, "spreads", it, m.home, m.away, line = pick.line, price = american) }
                is BetGrader.Pick.Total -> if (pick.period != BetGrader.Period.GAME) null
                else VerdictQuery(sportKey, "totals", if (pick.over) "over" else "under", m.home, m.away, line = pick.line, price = american)
                is BetGrader.Pick.Prop -> ParlayMarketKeys.keyFor(sportKey, pick.stat)?.let { key ->
                    VerdictQuery(sportKey, key, if (pick.over) "over" else "under", m.home, m.away, player = pick.player, line = pick.line, price = american)
                }
                else -> null
            }
        }
    }
}

/**
 * ParlayAPI's call on one bet (`/v1/verdict`, PARLAY_API.md §6.4): BET / LEAN / FAIR / PASS / NO_DATA, its no-vig fair price, the best price
 * among the books asked about (Novig), and how many books it compared. Its `edge_pct` is a probability-point difference, not EV (§5), so
 * the screens show Vigilant's own EV at the bet's price from [fairProbability] instead.
 */
data class Verdict(
    val verdict: String,
    val summary: String?,
    val fairAmerican: Int?,
    /** 0–1. */
    val fairProbability: Double?,
    val fairSource: String?,
    val bestAmerican: Int?,
    val bestBook: String?,
    val booksCompared: Int?,
    val confidence: String?,
    val movementPp: Double?,
    val note: String?,
) {
    /** EV of a bet costing [cost] per $1 payout (Novig's price, fee included) against this fair line; null without one. */
    fun evAt(cost: Double): Double? = fairProbability?.takeIf { it in 0.0..1.0 && cost > 0 }?.let { it / cost - 1.0 }

    companion object {
        private fun JsonObject.obj(k: String) = this[k] as? JsonObject
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()

        /** A /verdict answer, or null when it isn't one. */
        fun parse(body: String, json: Json): Verdict? {
            val o = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val verdict = o.str("verdict") ?: return null
            val fair = o.obj("fair")
            val best = o.obj("best_available")
            val fairAmerican = fair?.num("price")?.toInt()
            // Its implied_prob is a percent ("48.6"): from the price when it's missing.
            val fairProb = fair?.num("implied_prob")?.let { it / 100.0 } ?: fairAmerican?.let { 1.0 / Odds.americanToDecimal(it) }
            return Verdict(
                verdict = verdict.uppercase(), summary = o.str("summary"),
                fairAmerican = fairAmerican, fairProbability = fairProb, fairSource = fair?.str("source"),
                bestAmerican = best?.num("price")?.toInt(), bestBook = best?.str("book"),
                booksCompared = o.num("books_compared")?.toInt(), confidence = o.str("confidence"),
                movementPp = o.num("movement_pp_since_open"), note = o.str("note"),
            )
        }
    }
}

/**
 * "Second opinion (ParlayAPI, 5 credits)" (Tj, 2026-09-30, PARLAY_API.md §6.4): one bet sent to `/v1/verdict`, only when Tj taps it, never
 * on its own. Through [TheOddsApiClient.parlayGet], so the key pool, the day's pace and the meter apply (the answer's
 * `credits.monthly_remaining` is recorded: it sends no credit headers). A "busy" 503 is retried once, [BUSY_WAIT_MS] later.
 */
class ParlayVerdicts(
    private val client: TheOddsApiClient,
    private val json: Json,
    private val active: suspend () -> Boolean,
) {
    sealed interface Result {
        data class Answered(val verdict: Verdict) : Result
        /** ParlayAPI's props board was busy twice in a row: try again in a minute. */
        data object Busy : Result
        data class Failed(val message: String) : Result
        /** ParlayAPI is off or has no key. */
        data object Off : Result
    }

    /** Calls made (tests, Diagnostics). */
    @Volatile
    var requests: Int = 0
        private set

    suspend fun ask(q: VerdictQuery): Result {
        if (!active()) return Result.Off
        repeat(2) { attempt ->
            requests++
            val reply = try {
                client.parlayGet("/verdict", q.params(), cost = COST, what = "verdict").value
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return Result.Failed(readableError(e))
            }
            when {
                reply.busy -> if (attempt == 0) delay(BUSY_WAIT_MS) else return Result.Busy
                reply.ok -> return Verdict.parse(reply.body, json)?.let { Result.Answered(it) } ?: Result.Failed("ParlayAPI's answer couldn't be read")
                else -> return Result.Failed("ParlayAPI couldn't grade this bet (HTTP ${reply.code})")
            }
        }
        return Result.Busy
    }

    companion object {
        const val COST = 5

        /** A busy answer ("Retry in a couple of seconds") is asked again this much later, once. */
        const val BUSY_WAIT_MS = 2_000L
    }
}
