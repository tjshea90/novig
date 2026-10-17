package com.tjshea.vigilant.data.scanner

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * What Tj's own bet history says about each TYPE of bet and bid (Tj, 2026-10-12: "make the app have a toggle in the settings that prevents bets and bids ... on the types of bets and bids losing
 * money according to my bet history, and favors the types ... which have had the most profit and win rate ... Treat any type ... with relatively small sample size as neutral"; TASKS.md TPF).
 *
 * The numbers come from `profit_profile.json` (written by `tools/profit-profile.py` from the history files Tj shares; send more and Claude regenerates it, so the profile adapts as the history grows).
 * Per instrument (a taker `bet` or a filled `bid`) and per dimension (league|kind|side, league|kind, kind|side, league, kind, side, ev bucket, lead bucket) it holds n, ROI on stake, win rate and the
 * t-statistic of the per-bet ROI. Pure: [judge] is a lookup and a few comparisons, so every rule is a unit test.
 *
 * The rule (the cut-offs are in the file): a group counts only with n >= minN (a marginal dimension: ev, lead, side, kind alone, league alone, 2 x minN); it is a LOSER when its ROI is negative and
 * t <= -tCut, a WINNER when ROI is positive and t >= tCut, otherwise neutral. A candidate is BLOCKED by a loser unless a MORE SPECIFIC group of the same candidate is a winner (specific outranks
 * general); it is never blocked for lack of data. The score that ranks the rest is the ROI of the groups that count, shrunk by n/(n+SHRINK) (a thin group moves it little) and weighted by how specific the group is.
 *
 * ROI is noisy (the t-statistic is the guard), and CLV, not ROI, is what the app optimises: a type that lost with a positive CLV is probably variance. The profile is a filter on what HAS lost, said so
 * in the words of the refusal.
 */
class ProfitProfile(
    private val groups: Map<String, Map<String, Map<String, Group>>>,
    val minN: Int,
    val tCut: Double,
    val records: Int,
    val asOf: String,
) {
    data class Group(val n: Int, val roi: Double, val t: Double, val win: Double)

    enum class Instrument(val key: String) { BET("bet"), BID("bid") }

    /** [blocked] is the reason in words (null = not blocked); [score] ranks (higher first; 0 = neutral); [favoured] names the winning groups behind a positive score. */
    data class Judgement(val blocked: String?, val score: Double, val favoured: String?, val starPenalty: Double = 0.0) {
        companion object { val NEUTRAL = Judgement(null, 0.0, null) }
    }

    private data class Hit(val label: String, val g: Group, val specific: Int, val kind: Int)

    /**
     * [typeOnly]: judge by the TYPE of bet alone (league, kind, side), ignoring the edge and lead-time buckets. Those buckets are confounded with the kind (the 6-13 h bucket is mostly props) and the trap guard
     * already owns timing, so the engine's hard block uses the type groups only (Tj, 2026-10-17: "the types of bets and bids that lose").
     */
    fun judge(instrument: Instrument, league: String, kind: String, selection: String, ev: Double?, leadMin: Long?, typeOnly: Boolean = false): Judgement {
        val table = groups[instrument.key] ?: return Judgement.NEUTRAL
        val side = sideOf(selection)
        val keys = listOf(
            Triple("league|kind|side", "$league|$kind|$side", 3), Triple("league|kind", "$league|$kind", 2), Triple("kind|side", "$kind|$side", 2),
            Triple("league", league, 1), Triple("kind", kind, 1), Triple("side", side, 1),
            Triple("ev", evBucket(ev), 0), Triple("lead", leadBucket(leadMin), 0),
        ).filter { !typeOnly || (it.first == "league|kind|side" || it.first == "league|kind" || it.first == "kind|side") }
        val hits = ArrayList<Hit>()
        for ((dim, key, specific) in keys) {
            val g = table[dim]?.get(key) ?: continue
            // A marginal dimension needs twice the sample: it is checked against every candidate, so it is where chance finds a "loser".
            val need = if (typeOnly) maxOf(minN, TYPE_MIN_N) else if (specific >= 2) minN else 2 * minN
            if (g.n < need) continue
            hits += Hit(listOf(dimWords(dim), key.replace('|', ' ').trim()).filter { it.isNotBlank() }.joinToString(" "), g, specific, if (g.roi < 0 && g.t <= -tCut) -1 else if (g.roi > 0 && g.t >= tCut) 1 else 0)
        }
        if (hits.isEmpty()) return Judgement.NEUTRAL
        val losers = hits.filter { it.kind < 0 }
        val winners = hits.filter { it.kind > 0 }
        // typeOnly: a loser stands unless a MORE SPECIFIC group with a big enough sample is not itself a loser (a neutral NFL|props|under clears NFL|props), not only unless it is a winner.
        val blocker = if (typeOnly) losers.filter { l -> hits.none { it.specific > l.specific && it.kind >= 0 } }.maxByOrNull { it.specific * 1000 + it.g.n }
        else losers.filter { l -> winners.none { it.specific > l.specific } }.maxByOrNull { it.specific * 1000 + it.g.n }
        // The more specific the group the more it says about THIS candidate: weights 4, 3, 2, 1 for league|kind|side down to the bucket dimensions.
        val score = hits.sumOf { (it.specific + 1) * it.g.roi * it.g.n / (it.g.n + SHRINK) } / hits.sumOf { it.specific + 1 }
        val blocked = blocker?.let { "your bet history says ${it.label} ${if (instrument == Instrument.BID) "bids" else "bets"} lose money (${it.g.n} settled, ROI ${pct(it.g.roi)}, win ${Math.round(it.g.win * 100)}%, t ${"%.1f".format(Locale.US, it.g.t)}) (Settings › Profit-proven only)" }
        val favoured = winners.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.label} ${pct(it.g.roi)} over ${it.g.n}" }
        // While a minimum star rating decides the auto-bet it overrides the block (Tj, 2026-10-12), but a type that lost money has its stars lowered by how clearly it lost: half a star at the cut-off
        // (t = -tCut), another half star for each further point of t, at most two stars (the rating's own steps are halves).
        val penalty = blocker?.let { (STAR_BASE + STAR_PER_T * (kotlin.math.abs(it.g.t) - tCut)).coerceIn(STAR_BASE, STAR_MAX) } ?: 0.0
        return Judgement(blocked, score, favoured, penalty)
    }

    private fun dimWords(dim: String) = when (dim) { "ev" -> "an edge of"; "lead" -> "placed"; else -> "" }
    private fun pct(v: Double) = String.format(Locale.US, "%+.1f%%", v * 100)

    /** A line of words for Diagnostics and Settings: what it learned from and what it blocks and favours at the biggest samples. */
    fun summary(): String {
        val sb = StringBuilder("learned from $records settled bets and bid fills up to $asOf (a group counts from $minN, t >= ${"%.1f".format(Locale.US, tCut)})")
        for (inst in Instrument.entries) {
            val all = groups[inst.key].orEmpty().filterKeys { it != "_n" }.flatMap { (dim, m) -> m.map { (k, g) -> Triple(dim, k, g) } }.filter { (dim, _, g) -> g.n >= (if (dim.count { it == '|' } >= 1) minN else 2 * minN) }
            val lose = all.filter { it.third.roi < 0 && it.third.t <= -tCut }.sortedBy { it.third.t }.take(4)
            val win = all.filter { it.third.roi > 0 && it.third.t >= tCut }.sortedByDescending { it.third.t }.take(4)
            if (lose.isNotEmpty()) sb.append("; ${inst.key}s blocked: ").append(lose.joinToString(", ") { "${it.second.replace('|', ' ')} (${pct(it.third.roi)}, n=${it.third.n})" })
            if (win.isNotEmpty()) sb.append("; ${inst.key}s favoured: ").append(win.joinToString(", ") { "${it.second.replace('|', ' ')} (${pct(it.third.roi)}, n=${it.third.n})" })
        }
        return sb.toString()
    }

    companion object {
        /** A group's ROI counts as n / (n + SHRINK) of itself in the score: 100 settled bets count half. */
        const val SHRINK = 100.0

        /** The engine's hard block by type needs this many settled bets in the group (Tj, 2026-10-17: "big enough samples"). */
        const val TYPE_MIN_N = 100
        const val STAR_BASE = 0.5
        const val STAR_PER_T = 0.5
        const val STAR_MAX = 2.0

        private val OVER = Regex("\\bover\\b", RegexOption.IGNORE_CASE)
        private val UNDER = Regex("\\bunder\\b", RegexOption.IGNORE_CASE)

        /** "over", "under" or "other" (a spread, a moneyline): the same rule `tools/profit_types.py` uses. */
        fun sideOf(selection: String): String = when {
            selection.lowercase().startsWith("over ") || OVER.containsMatchIn(selection) -> "over"
            selection.lowercase().startsWith("under ") || UNDER.containsMatchIn(selection) -> "under"
            else -> "other"
        }

        fun evBucket(ev: Double?): String = when {
            ev == null -> "?"
            ev < .02 -> "<2%"
            ev < .04 -> "2-4%"
            ev < .06 -> "4-6%"
            else -> "6%+"
        }

        fun leadBucket(min: Long?): String = when {
            min == null -> "?"
            min < 60 -> "<1h"
            min < 180 -> "1-3h"
            min < 360 -> "3-6h"
            min < 780 -> "6-13h"
            else -> ">13h"
        }

        fun parse(text: String): ProfitProfile? = try {
            val root = Json.parseToJsonElement(text).jsonObject
            val g = HashMap<String, Map<String, Map<String, Group>>>()
            for ((inst, dims) in root["groups"]!!.jsonObject) {
                g[inst] = dims.jsonObject.filterKeys { it != "_n" }.mapValues { (_, byKey) ->
                    (byKey as JsonObject).mapValues { (_, v) ->
                        val o = v.jsonObject
                        Group(o["n"]!!.jsonPrimitive.intOrNull ?: 0, o["roi"]!!.jsonPrimitive.doubleOrNull ?: 0.0, o["t"]!!.jsonPrimitive.doubleOrNull ?: 0.0, o["win"]!!.jsonPrimitive.doubleOrNull ?: 0.0)
                    }
                }
            }
            ProfitProfile(g, root["minN"]!!.jsonPrimitive.intOrNull ?: 40, root["tCut"]!!.jsonPrimitive.doubleOrNull ?: 1.5, root["records"]!!.jsonPrimitive.intOrNull ?: 0, root["asOf"]!!.jsonPrimitive.content)
        } catch (e: Exception) {
            null
        }

        /** The profile that ships in the app (`profit_profile.json`), or null when it can't be read (the filter is then off: nothing is blocked on a profile that isn't there). */
        /**
         * The stars a minimum star rating is held against (Tj, 2026-10-13: "show the adjusted stars on the card and in the message"). With Settings › Profit-proven only ON, a type of bet
         * Tj's history says loses money has its stars lowered by [Judgement.starPenalty]; every other bet, and every bet with the switch off, keeps [stars]. The auto-bet and the cards both read this.
         */
        fun adjustedStars(profitProven: Boolean, stars: Double?, league: String, kind: String, selection: String, ev: Double?, startsAtMs: Long?, now: Long): Double? {
            if (stars == null || !profitProven) return stars
            val pf = shipped ?: return stars
            val lead = startsAtMs?.let { (it - now) / 60_000L }
            return (stars - pf.judge(Instrument.BET, league, kind, selection, ev, lead).starPenalty).coerceAtLeast(0.0)
        }

        val shipped: ProfitProfile? by lazy { ProfitProfile::class.java.getResourceAsStream("/profit_profile.json")?.bufferedReader()?.use { parse(it.readText()) } }
    }
}
