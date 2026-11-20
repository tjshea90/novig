package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.tracker.BetGrader
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.FreeScores
import com.tjshea.vigilant.data.tracker.ScoreSource

/**
 * The BUYER's result of one parlay leg from the game's final score (MK29, Tj 2026-11-20: "The parlay tracker is not properly displaying winning and losing legs. Some legs already ended but have no result"): Novig
 * grades a leg in its catalog only for a while and a graded market leaves the catalog, so the book had no result for most finished legs. The score feeds the Tracker already uses (ESPN, MLB) grade the game lines:
 * spread, moneyline, game total, team total. Props are left to Novig's catalog. Null = not final, not a game line, or not found.
 */
object ParlayLegScores {
    private val PREFIXES = listOf("alternate spread", "alternate total", "team total", "moneyline", "spread", "total")

    fun marketWord(type: String): String? = when (type.uppercase()) {
        "MONEY" -> "Moneyline"
        "SPREAD" -> "Spread"
        "TOTAL" -> "Total"
        "TEAM_TOTAL" -> "Team Total"
        else -> null
    }

    /** "Spread Phoenix Suns +5.5" -> market "Spread", selection "Phoenix Suns +5.5". */
    fun split(type: String, label: String): Pair<String, String>? {
        val word = marketWord(type) ?: return null
        var rest = label.trim()
        val low = rest.lowercase()
        PREFIXES.firstOrNull { low.startsWith(it) }?.let { rest = rest.substring(it.length).trim() }
        return if (rest.isEmpty()) null else word to rest
    }

    /** WIN / LOSS / PUSH for the buyer's leg once the game is final, else null. [now] and the start guard keep a game that may still be in play from being read. */
    suspend fun grade(leg: FillLeg, scores: ScoreSource, now: Long): String? {
        if (leg.startsMs <= 0L || now - leg.startsMs < 3 * 3_600_000L) return null
        val (market, selection) = split(leg.type, leg.label) ?: return null
        val pick = BetGrader.pickOf(market, selection) ?: return null
        val league = leg.league.trim().uppercase().takeIf { it.isNotEmpty() && scores.covers(it) } ?: return null
        val day = FreeScores.etDate(leg.startsMs)
        for (date in listOf(day, day.minusDays(1), day.plusDays(1))) {
            val games = scores.games(league, date) ?: continue
            val desc = cleanDesc(leg.desc)
            // Novig may list a game with the teams the other way round from ESPN (a neutral site: "West Virginia @ Florida" is ESPN's "Florida at West Virginia"); the pick names a team, so the other order grades the same.
            val game = BetGrader.gameOf(desc, leg.startsMs, games) ?: reversed(desc)?.let { BetGrader.gameOf(it, leg.startsMs, games) } ?: continue
            if (game.called || !game.final) return null
            val result = BetGrader.gradeDetailed(pick, game) as? BetGrader.Grade.Result ?: return null
            return when (result.status) { BetStatus.WON -> "WIN"; BetStatus.LOST -> "LOSS"; BetStatus.PUSH -> "PUSH"; else -> null }
        }
        return null
    }

    private val ROUND = Regex("""\s+(Round of \d+|Quarterfinals?|Semifinals?|Finals?|Qualifying.*|Qualification.*)$""", RegexOption.IGNORE_CASE)

    /** A tennis description carries its round ("Vignesh Gogineni @ Stefan Kozlov Round of 16"): ESPN's does not. */
    fun cleanDesc(desc: String): String = desc.trim().replace(ROUND, "").trim()

    /** "Away @ Home" -> "Home @ Away". */
    fun reversed(desc: String): String? {
        val parts = desc.split(" @ ")
        return if (parts.size == 2 && parts.all { it.isNotBlank() }) parts[1].trim() + " @ " + parts[0].trim() else null
    }
}
