package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.PropStats

/**
 * ParlayAPI's prop rows name their market the way each book does (Tj's key, 2026-09-30: asked for `player_reception_yds`, the NFL board
 * answered `player_rec_yds`, `player_receiving_yards` and `prophetx_player_total_receiving_yards`; MLB's `batter_hits` came back as
 * `player_hits`, its closes file says `player_bases` for total bases). This reads a key (and the row's label, for whose strikeouts or walks)
 * as words, abbreviations spelled out, and finds the one Novig stat of the sport with the same words: [statOf]. A key that names a part of
 * the game (1st half, 1st quarter), a threshold ladder ("milestones", "2 or more") or a first/last scorer is none of them.
 */
object ParlayMarkets {

    /** Words that say nothing about the stat. */
    private val FILLER = setOf("player", "players", "total", "totals", "prophetx", "o", "u", "ou", "prop", "props", "alt", "alternate", "made", "scored", "anytime", "scorer", "and", "number", "of", "recorded")

    /** Words that say who (pitcher or batter), used only to tell two stats with the same words apart. */
    private val ROLE = setOf("pitcher", "pitching", "batter", "batting", "hitter", "hitting", "thrown")

    /** A part of the game, a threshold ladder, an order of scoring: not a full-game over/under. */
    private val NOT_FULL_GAME = setOf("1h", "2h", "1st", "2nd", "3rd", "4th", "half", "quarter", "q1", "q2", "q3", "q4", "1q", "2q", "3q", "4q", "period", "inning", "innings", "f5", "first", "last", "milestones", "more", "exact", "range", "parlays", "correct", "margin", "spread", "moneyline", "winning")

    private val SPELLED = mapOf(
        "yds" to listOf("yards"), "yd" to listOf("yards"), "yard" to listOf("yards"),
        "rec" to listOf("receiving"), "reception" to listOf("receiving"),
        "rush" to listOf("rushing"), "pass" to listOf("passing"),
        "tds" to listOf("touchdowns"), "td" to listOf("touchdowns"), "touchdown" to listOf("touchdowns"),
        "pts" to listOf("points"), "pt" to listOf("points"), "rebs" to listOf("rebounds"), "reb" to listOf("rebounds"),
        "asts" to listOf("assists"), "ast" to listOf("assists"), "threes" to listOf("three", "pointers"), "3pt" to listOf("three", "pointers"),
        "3pm" to listOf("three", "pointers"), "3fgm" to listOf("three", "pointers"), "atts" to listOf("attempts"), "att" to listOf("attempts"),
        "ints" to listOf("interceptions"), "int" to listOf("interceptions"), "comp" to listOf("completions"), "comps" to listOf("completions"),
        "sog" to listOf("shots", "on", "goal"), "goalscorer" to listOf("goals"), "goal" to listOf("goal"), "save" to listOf("saves"),
        "k" to listOf("strikeouts"), "ks" to listOf("strikeouts"), "so" to listOf("strikeouts"), "rbi" to listOf("rbis"),
        "bases" to listOf("bases"), "run" to listOf("runs"), "hit" to listOf("hits"),
    )

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.flatMap { SPELLED[it] ?: listOf(it) }

    /** A stat's own words (Novig's name, spelled like a key). */
    private fun statWords(stat: String): Set<String> = words(stat.replace('_', ' ')).toSet() - FILLER - ROLE

    /**
     * The Novig stat [key] prices in [sportKey] (only a stat Vigilant prices there: [PropStats.parlayStats]), or null. [label] is the row's
     * own description ("Hitter Walks", "Strikeouts Thrown O/U"): it decides a pitcher's strikeouts or walks from a batter's.
     */
    fun statOf(sportKey: String, key: String, label: String? = null): String? {
        val stats = PropStats.parlayStats(sportKey)
        if (stats.isEmpty()) return null
        PropStats.ODDS_API_MARKETS[key]?.takeIf { it in stats }?.let { return it }
        val all = words(key)
        if (all.any { it in NOT_FULL_GAME } || words(label.orEmpty()).any { it in NOT_FULL_GAME && it != "spread" }) return null
        val core = all.toSet() - FILLER - ROLE
        if (core.isEmpty()) return null
        val text = "$key ${label.orEmpty()}".lowercase()
        val pitcher = listOf("pitcher", "pitching", "thrown", "allowed").any { it in text }
        val batter = listOf("batter", "batting", "hitter").any { it in text }
        fun pick(want: Set<String>): String? {
            val found = stats.filter { statWords(it) == want }
            return when (found.size) {
                0 -> null
                1 -> found.single()
                // Two stats with the same words (a pitcher's strikeouts and a batter's; walks allowed and walks drawn): who the row says.
                else -> found.firstOrNull { s ->
                    val p = s.startsWith("PITCHER") || s == "WALKS"
                    val b = s.startsWith("BATTING")
                    when {
                        batter -> b
                        pitcher -> p
                        // The books' plain "Strikeouts" is the pitcher's; plain "Walks" the batter's.
                        "strikeouts" in want -> p
                        else -> b
                    }
                }
            }
        }
        return pick(core) ?: if ("allowed" in core && "hits" !in core) pick(core - "allowed") else null
    }
}
