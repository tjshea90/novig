package com.tjshea.vigilant.data.scanner

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * A bet's TYPE as the star rating's evidence table knows it (SX, Tj 2026-10-24: "5 Star means the bet is a type that is proven profitable from data"): the sport, the kind of bet,
 * the side (over / under for props and totals, favourite / underdog for the rest) and the price paid ([cost], per $1 of payout: its band separates longshots from favourites).
 */
data class BetType(val sport: String, val kind: String, val side: String, val minToStart: Long?, val cost: Double? = null) {
    companion object {
        /** [league] as a scanner names it ("NBA", "ATP", ...), [kind] the bet's [BetKind], [cost] the price paid per $1 of payout. */
        fun of(league: String, kind: BetKind, selection: String, cost: Double?, startsAtMs: Long?, now: Long): BetType {
            val k = when (kind) {
                BetKind.PROP -> "prop"; BetKind.TOTAL -> "total"; BetKind.SPREAD -> "spread"; BetKind.MONEYLINE -> "moneyline"; BetKind.TEAM_TOTAL -> "team_total"
                BetKind.PERIOD -> "period"; BetKind.OTHER -> "other"
            }
            val s = when {
                OVER.containsMatchIn(selection) -> "over"
                UNDER.containsMatchIn(selection) -> "under"
                else -> if ((cost ?: 0.5) >= 0.5) "fav" else "dog"
            }
            return BetType(sportOf(league), k, s, startsAtMs?.let { (it - now) / 60_000L }, cost)
        }

        private val OVER = Regex("\\bover\\b", RegexOption.IGNORE_CASE)
        private val UNDER = Regex("\\bunder\\b", RegexOption.IGNORE_CASE)

        /** The evidence table's sport for a scanner's league name; "*" (every sport) for one it has no history of. tools/star_table.py sport_of is the same rule. */
        fun sportOf(league: String): String {
            val l = league.trim().uppercase(Locale.US)
            return when {
                l in setOf("NBA", "WNBA", "NCAAB", "NFL", "NCAAF", "NHL", "MLB") -> l
                l == "MLB (USA)" -> "MLB"
                l.startsWith("ATP") || l.startsWith("WTA") || l.contains("TENNIS") || l.startsWith("ITF") || l.startsWith("CHALLENGER") -> "TENNIS"
                l.contains("UFC") || l.contains("MMA") -> "MMA"
                else -> "*"
            }
        }
    }
}

/**
 * What the history says about each type of bet (SX): `star_evidence.json`, written by `tools/star_table.py` from (1) Vigilant's and CNO's own graded +EV lines scored on NOVIG'S OWN
 * close, from Novig's public trade archive (`tools/novig_trade_close.py`), and (2) the point-in-time backtest (`tools/star_backtest.py`: the app's worst-case fair on PropLine's price
 * history) scored on Novig's devigged close. Novig's close is the yardstick because it predicted the results better than Pinnacle's and CLV against it predicted ROI one for one
 * (research/star_rating_sx_2026-10-24.md). [BetRating] reads the most specific cell whose real record has [MIN_USE_GAMES] games.
 */
object StarEvidence {

    /**
     * One type's record. Real lines: [clv] the mean CLV against Novig's close (a fraction) with its standard error [clvSe] over [games] games, [roi] and [roiSe] the profit per $1.
     * Backtest: [btClv] over [btGames] games, [btZ] its standard errors from zero (null when the backtest has no such type).
     */
    data class Cell(
        val key: String, val games: Int, val clv: Double, val clvSe: Double, val roi: Double?, val roiSe: Double?,
        val btGames: Int? = null, val btClv: Double? = null, val btZ: Double? = null,
    ) {
        /** How many standard errors the real CLV sits above zero: the evidence that this type of bet makes money at Novig. */
        val z: Double get() = if (clvSe > 0.0) clv / clvSe else 0.0
        /** The real CLV's upper 95% bound. */
        val upper: Double get() = clv + 1.96 * clvSe
    }

    class Table(val asOf: String, val cells: Map<String, Cell>) {
        /** The most specific cell for [type] whose real record has [minGames] games, or null. */
        fun lookup(type: BetType, minGames: Int = MIN_USE_GAMES): Cell? {
            val p = priceBand(type.cost)
            val keys = listOfNotNull(
                p?.let { "${type.sport}|${type.kind}|${type.side}|$it" },
                p?.let { "*|${type.kind}|${type.side}|$it" },
                "${type.sport}|${type.kind}|${type.side}",
                "*|${type.kind}|${type.side}",
                p?.let { "*|*|*|$it" },
                "*|*|*",
            )
            return keys.asSequence().mapNotNull { cells[it] }.firstOrNull { it.games >= minGames }
        }
    }

    /** A cell is read only with this many games of real lines behind it (fewer: the next coarser level answers). */
    const val MIN_USE_GAMES = 100

    fun priceBand(cost: Double?): String? = when {
        cost == null -> null
        cost < 0.4 -> "+150 longer"
        cost < 0.5 -> "+100..+150"
        cost < 0.6 -> "-150..-100"
        else -> "-150 shorter"
    }

    fun parse(text: String): Table? = try {
        val root = Json.parseToJsonElement(text).jsonObject
        val cells = (root["cells"] as JsonObject).mapNotNull { (k, v) ->
            val o = v.jsonObject
            fun d(n: String) = o[n]?.jsonPrimitive?.doubleOrNull
            val games = o["games"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            k to Cell(k, games, d("clv") ?: 0.0, d("clvSe") ?: 0.0, d("roi"), d("roiSe"), o["btGames"]?.jsonPrimitive?.intOrNull, d("btClv"), d("btZ"))
        }.toMap()
        Table(root["asOf"]?.jsonPrimitive?.content ?: "?", cells)
    } catch (e: Exception) {
        null
    }

    /** The table that ships in the app, or null when it can't be read (every trusted bet is then 1 star: unproven). */
    val shipped: Table? by lazy { StarEvidence::class.java.getResourceAsStream("/star_evidence.json")?.bufferedReader()?.use { parse(it.readText()) } }
}
