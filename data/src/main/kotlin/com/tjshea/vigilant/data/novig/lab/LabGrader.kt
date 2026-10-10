package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.scanner.PropStats
import com.tjshea.vigilant.data.tracker.BetGrader
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.FreeScores
import com.tjshea.vigilant.data.tracker.GameScore
import com.tjshea.vigilant.data.tracker.ScoreSource

/**
 * Grades the paper lab from the game's final score (Tj, 2026-10-10: "Fix the grading. I think some of my apis have grading, maybe even sgo"). Novig drops a market once it settles, so the status the lab
 * waited for (BidLab, LabRecorder) never arrived: 0 GRADE events in 21 hours on the phone and none in the GitHub lab. The score feeds Vigilant already grades Tj's bets with (SportsGameOdds, OddsPapi,
 * ESPN, MLB: [ScoreSource], newest first) hold the result, and [BetGrader] reads it, so a would-be bet is graded the way a tracked one is. Read-only: it places and changes nothing.
 *
 * Wordings it reads: a pregame bid's selection ("Louisville -2.5", "Over 49.5", "Jonquel Jones Over 9.5", "Louisville Over 31.5": the kind says which market), a live ladder side ("Total 9.5 YES",
 * "ML TOR YES", "Spr TOR +2.5 NO": YES = the number ends above the threshold = Over / the reference team covers, NO the other side) and a lab record's label and side. A period market, a team Novig
 * names by letters no one team fits, a player not in the box score: left ungraded (null), never guessed.
 */
class LabGrader(private val scores: ScoreSource) {

    /** What to grade: the game ([event] "Away @ Home", [league] Novig's name, [startsTs]) and the side. [statType] is a prop's Novig market type (PLAYER_REBOUNDS...). */
    data class Target(val event: String, val league: String, val startsTs: Long, val kind: String, val selection: String, val statType: String? = null)

    /** WIN, LOSS or PUSH (a void is a refund: PUSH), or null while it can't be told. */
    suspend fun grade(t: Target): String? {
        NovigText.parseMatchup(t.event) ?: return null
        val league = t.league.uppercase()
        if (!scores.covers(league)) return null
        val game = findGame(t, league) ?: return null
        if (!game.final) return null
        val pick = pickOf(t, game) ?: return null
        val box = if (pick is BetGrader.Pick.Prop) scores.players(game) else null
        val status = BetGrader.grade(pick, game, box) ?: return null
        return when (status) {
            BetStatus.WON -> "WIN"
            BetStatus.LOST -> "LOSS"
            BetStatus.PUSH, BetStatus.VOID -> "PUSH"
            else -> null
        }
    }

    private suspend fun findGame(t: Target, league: String): GameScore? {
        val probe = com.tjshea.vigilant.data.tracker.TrackedBet(
            "lab", t.startsTs, league, t.event, t.startsTs, "", "", "", "", 0.5, 0.0, null, null, 0.0,
        )
        val day = FreeScores.etDate(t.startsTs)
        for (date in listOf(day, day.minusDays(1), day.plusDays(1))) {
            val games = scores.games(league, date) ?: continue
            BetGrader.gameOf(probe, games)?.let { return it }
        }
        return null
    }

    fun pickOf(t: Target, game: GameScore): BetGrader.Pick? {
        val sel = t.selection.trim()
        // A live ladder side: "Total 9.5 YES", "ML TOR NO", "Spr TOR +2.5 YES".
        LADDER.matchEntire(sel)?.let { return ladderPick(it.groupValues[1], it.groupValues[2].equals("YES", true), game) }
        return when (t.kind) {
            "MONEYLINE" -> BetGrader.pickOf("Moneyline", sel)
            "SPREAD" -> BetGrader.pickOf("Spread", sel)
            "TOTAL" -> BetGrader.pickOf("Total", sel)
            "TEAM_TOTAL" -> BetGrader.pickOf("Team Total", sel)
            "PROP" -> t.statType?.let { BetGrader.pickOf(PropStats.displayName(it), sel) }
            else -> null   // PERIOD (a half, an inning: the name does not say which) and anything new
        }
    }

    /** A lab record ([label] "Total 14.5" / "ML TOR" / "Spr TOR +2.5", [side] OVER, UNDER, YES or NO) as a target; null for a cover (it pays whatever happens). */
    fun pickOfRecord(label: String, side: String, game: GameScore): BetGrader.Pick? = when (side.uppercase()) {
        "OVER", "YES" -> ladderPick(label, true, game)
        "UNDER", "NO" -> ladderPick(label, false, game)
        else -> null
    }

    private fun ladderPick(label: String, yes: Boolean, game: GameScore): BetGrader.Pick? {
        TOTAL_LABEL.matchEntire(label.trim())?.let { return BetGrader.Pick.Total(yes, it.groupValues[1].toDouble(), BetGrader.Period.GAME) }
        ML_LABEL.matchEntire(label.trim())?.let { m ->
            val team = teamOf(m.groupValues[1], game) ?: return null
            return BetGrader.Pick.Moneyline(if (yes) team.first else team.second)
        }
        SPR_LABEL.matchEntire(label.trim())?.let { m ->
            val team = teamOf(m.groupValues[1], game) ?: return null
            val value = m.groupValues[2].replace('−', '-').toDouble()
            // YES = the reference team covers its number (X -k is k: it wins by more than k); NO = the other team covers the opposite number.
            return if (yes) BetGrader.Pick.Spread(team.first, value, BetGrader.Period.GAME) else BetGrader.Pick.Spread(team.second, -value, BetGrader.Period.GAME)
        }
        return null
    }

    /** (the named team, the other team) in [game], where [name] is Novig's name or abbreviation ("TOR", "Toronto Maple Leafs"); null unless exactly one team fits. */
    internal fun teamOf(name: String, game: GameScore): Pair<String, String>? {
        BetGrader.sideOf(name, game)?.let { away -> return if (away) game.away to game.home else game.home to game.away }
        val a = abbrFits(name, game.away)
        val h = abbrFits(name, game.home)
        return when {
            a && !h -> game.away to game.home
            h && !a -> game.home to game.away
            else -> null
        }
    }

    private fun abbrFits(abbr: String, team: String): Boolean {
        val k = abbr.filter { it.isLetter() }.uppercase()
        if (k.length < 2) return false
        val words = team.uppercase().split(' ', '-', '.').filter { it.isNotBlank() }
        val flat = words.joinToString("")
        if (words.joinToString("") { it.take(1) } == k) return true
        if (words.any { it.startsWith(k) } || flat.startsWith(k)) return true
        // "LAL": the first letters of its first word and its last ("LA Lakers"), "GSW", "NYK": the leading letters, then each next word's.
        if (words.size >= 2 && k.length == 3 && words.first().startsWith(k.take(2)) && words.last().startsWith(k.last())) return true
        if (words.size >= 2 && k.length == 3 && words.first().startsWith(k.take(1)) && words[1].startsWith(k[1]) && words.last().startsWith(k.last())) return true
        return false
    }

    private companion object {
        val LADDER = Regex("^(.+?)\\s+(YES|NO)$", RegexOption.IGNORE_CASE)
        val TOTAL_LABEL = Regex("^Total\\s+(\\d+(?:\\.\\d+)?)$")
        val ML_LABEL = Regex("^ML\\s+(.+)$")
        val SPR_LABEL = Regex("^Spr\\s+(.+?)\\s+([+\\-−]?\\d+(?:\\.\\d+)?)$")
    }
}
