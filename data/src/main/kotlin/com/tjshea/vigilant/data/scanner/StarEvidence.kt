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
 * the side (over / under for props and totals, favourite / underdog for the rest) and how long before the start it is taken.
 */
data class BetType(val sport: String, val kind: String, val side: String, val minToStart: Long?) {
    companion object {
        /** [league] as a scanner names it ("NBA", "ATP", ...), [kind] the bet's [BetKind], [cost] the price paid per $1 of payout (sets favourite / underdog). */
        fun of(league: String, kind: BetKind, selection: String, cost: Double?, startsAtMs: Long?, now: Long): BetType {
            val k = when (kind) {
                BetKind.PROP -> "prop"; BetKind.TOTAL -> "total"; BetKind.SPREAD -> "spread"; BetKind.MONEYLINE -> "moneyline"; BetKind.TEAM_TOTAL -> "team_total"
                BetKind.PERIOD -> "period"; BetKind.OTHER -> "other"
            }
            val s = when (ProfitProfile.sideOf(selection)) {
                "over" -> "over"
                "under" -> "under"
                else -> if ((cost ?: 0.5) >= 0.5) "fav" else "dog"
            }
            return BetType(sportOf(league), k, s, startsAtMs?.let { (it - now) / 60_000L })
        }

        /** The evidence table's sport for a scanner's league name; "*" (every sport) for one it has no history of. */
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
 * What the history says about each type of bet (SX): `star_evidence.json`, written by `tools/star_table.py` from the point-in-time backtest (`tools/star_backtest.py`: Novig's price
 * at a lead time against the fair Vigilant would have had then, from PropLine's price history of Novig, Pinnacle and the US books, scored on the average of Pinnacle's and Novig's
 * devigged closes and the real result). [BetRating] reads the most specific cell that has [MIN_USE_GAMES] games.
 */
object StarEvidence {

    /** One type's record: [clv] the mean CLV against the blended close (a fraction) and its standard error, [roi] the profit per $1 and its standard error, over [games] games. */
    data class Cell(val key: String, val games: Int, val bets: Int, val ev: Double, val clv: Double, val clvSe: Double, val roi: Double?, val roiSe: Double?) {
        /** How many standard errors the CLV sits above zero: the evidence that this type of bet makes money. */
        val z: Double get() = if (clvSe > 0.0) clv / clvSe else 0.0
    }

    class Table(val asOf: String, val cells: Map<String, Cell>) {
        /** The most specific cell with [minGames] games for [type] at edge [ev] (a fraction), or null when the edge is outside every band or nothing has the games. */
        fun lookup(type: BetType, ev: Double, minGames: Int = MIN_USE_GAMES): Cell? {
            val e = evBand(ev) ?: return null
            val lead = leadBand(type.minToStart)
            val keys = listOfNotNull(
                lead?.let { "${type.sport}|${type.kind}|${type.side}|$it|$e" },
                lead?.let { "*|${type.kind}|${type.side}|$it|$e" },
                "*|${type.kind}|${type.side}|*|$e",
                "*|${type.kind}|*|*|$e",
                "*|*|*|*|$e",
            )
            return keys.asSequence().mapNotNull { cells[it] }.firstOrNull { it.games >= minGames }
        }
    }

    /** A cell is read only with this many games behind it (fewer: the next coarser level answers). */
    const val MIN_USE_GAMES = 150

    fun evBand(ev: Double): String? = when {
        ev < 0.005 -> null
        ev < 0.01 -> "0.5-1"
        ev < 0.02 -> "1-2"
        ev < 0.03 -> "2-3"
        ev < 0.05 -> "3-5"
        ev < 0.08 -> "5-8"
        ev < 0.12 -> "8-12"
        ev <= 0.25 -> "12-25"
        else -> null
    }

    fun leadBand(minToStart: Long?): String? = when {
        minToStart == null -> null
        minToStart < 60 -> "0-1h"
        minToStart < 180 -> "1-3h"
        minToStart < 360 -> "3-6h"
        minToStart < 720 -> "6-12h"
        else -> "12h+"
    }

    fun parse(text: String): Table? = try {
        val root = Json.parseToJsonElement(text).jsonObject
        val cells = (root["cells"] as JsonObject).mapValues { (k, v) ->
            val o = v.jsonObject
            fun d(n: String) = o[n]?.jsonPrimitive?.doubleOrNull
            Cell(k, o["games"]!!.jsonPrimitive.intOrNull ?: 0, o["bets"]?.jsonPrimitive?.intOrNull ?: 0, d("ev") ?: 0.0, d("clv") ?: 0.0, d("clvSe") ?: 0.0, d("roi"), d("roiSe"))
        }
        Table(root["asOf"]?.jsonPrimitive?.content ?: "?", cells)
    } catch (e: Exception) {
        null
    }

    /** The table that ships in the app, or null when it can't be read (the rating then falls back to the edge alone). */
    val shipped: Table? by lazy { StarEvidence::class.java.getResourceAsStream("/star_evidence.json")?.bufferedReader()?.use { parse(it.readText()) } }
}
