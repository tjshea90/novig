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
 * general); it is never blocked for lack of data. The score that ranks the rest is the average ROI of the groups that count, shrunk by n/(n+SHRINK), so a thin group moves it little.
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
    data class Judgement(val blocked: String?, val score: Double, val favoured: String?) {
        companion object { val NEUTRAL = Judgement(null, 0.0, null) }
    }

    private data class Hit(val label: String, val g: Group, val specific: Int, val kind: Int)

    fun judge(instrument: Instrument, league: String, kind: String, selection: String, ev: Double?, leadMin: Long?): Judgement {
        val table = groups[instrument.key] ?: return Judgement.NEUTRAL
        val side = sideOf(selection)
        val keys = listOf(
            Triple("league|kind|side", "$league|$kind|$side", 3), Triple("league|kind", "$league|$kind", 2), Triple("kind|side", "$kind|$side", 2),
            Triple("league", league, 1), Triple("kind", kind, 1), Triple("side", side, 1),
            Triple("ev", evBucket(ev), 0), Triple("lead", leadBucket(leadMin), 0),
        )
        val hits = ArrayList<Hit>()
        for ((dim, key, specific) in keys) {
            val g = table[dim]?.get(key) ?: continue
            // A marginal dimension needs twice the sample: it is checked against every candidate, so it is where chance finds a "loser".
            val need = if (specific >= 2) minN else 2 * minN
            if (g.n < need) continue
            hits += Hit("${dimWords(dim)} ${key.replace('|', ' ').trim()}", g, specific, if (g.roi < 0 && g.t <= -tCut) -1 else if (g.roi > 0 && g.t >= tCut) 1 else 0)
        }
        if (hits.isEmpty()) return Judgement.NEUTRAL
        val losers = hits.filter { it.kind < 0 }
        val winners = hits.filter { it.kind > 0 }
        val blocker = losers.filter { l -> winners.none { it.specific > l.specific } }.maxByOrNull { it.specific * 1000 + it.g.n }
        val score = hits.sumOf { it.g.roi * it.g.n / (it.g.n + SHRINK) } / hits.size
        val blocked = blocker?.let { "your bet history says ${it.label} ${if (instrument == Instrument.BID) "bids" else "bets"} lose money (${it.g.n} settled, ROI ${pct(it.g.roi)}, win ${Math.round(it.g.win * 100)}%, t ${"%.1f".format(Locale.US, it.g.t)}) (Settings › Profit-proven only)" }
        val favoured = winners.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.label} ${pct(it.g.roi)} over ${it.g.n}" }
        return Judgement(blocked, score, favoured)
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
        val shipped: ProfitProfile? by lazy { ProfitProfile::class.java.getResourceAsStream("/profit_profile.json")?.bufferedReader()?.use { parse(it.readText()) } }
    }
}
