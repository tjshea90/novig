package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.scanner.PropStats

/**
 * Grades a tracked bet from a game's final score (Tj, 2026-09-27: "keep track whether each bet was
 * a win or a loss"). Reads both wordings a bet can have: Vigilant's ("Spread" · "Dallas Cowboys
 * -3.5", "F5 Total" · "Over 4.5", "Passing Yards" · "Patrick Mahomes Over 233.5") and CNO's
 * ("Point Spread" · "Houston Texans -3.5", "Player Receiving Yards" · "Brock Bowers Under 4.5").
 * Anything it can't read for sure is left open (a tap settles it), never guessed.
 */
object BetGrader {

    /** GAME is the whole game (a tennis match's games); SETS a tennis match's sets; FIRST_SET its first set. */
    enum class Period { GAME, FIRST_HALF, FIRST_INNING, SETS, FIRST_SET }

    sealed interface Pick {
        data class Moneyline(val team: String) : Pick
        data class Spread(val team: String, val line: Double, val period: Period) : Pick
        data class Total(val over: Boolean, val line: Double, val period: Period) : Pick
        data class TeamTotal(val team: String, val over: Boolean, val line: Double) : Pick
        data class Prop(val player: String, val stat: String, val over: Boolean, val line: Double) : Pick

        /** Tennis: who wins the match's first set. */
        data class FirstSet(val player: String) : Pick
    }

    /**
     * How grading one bet came out ([grade] for just the status). [Waiting]: the feed will have it soon
     * (the game isn't over, the score isn't posted). [Manual]: nothing the feeds carry can settle it, so
     * it's a tap; [reason] is written for the card.
     */
    sealed interface Grade {
        data class Result(val status: BetStatus, val evidence: String) : Grade
        data class Waiting(val reason: String) : Grade
        data class Manual(val reason: String) : Grade
    }

    /** What [bet] is on, or null when its wording can't be read for certain. */
    fun pickOf(bet: TrackedBet): Pick? = pickOf(bet.marketLabel, bet.selection)

    /** What a bet on [marketLabel] ("Player Receptions", "Spread") and [selection] ("Brock Bowers Under 4.5") is. */
    fun pickOf(marketLabel: String, selection: String): Pick? {
        val market = marketLabel.trim()
        val lower = market.lowercase()
        val (who, line) = Picks.split(selection.trim())
        if (lower.contains("3-way") || lower.contains("3 way")) return null
        val period = when {
            FIRST_INNING_WORDS.containsMatchIn(lower) -> Period.FIRST_INNING
            FIRST_SET_WORDS.containsMatchIn(lower) -> Period.FIRST_SET
            SET_WORDS.containsMatchIn(lower) -> Period.SETS
            FIRST_HALF_WORDS.containsMatchIn(lower) -> Period.FIRST_HALF
            // Quarters, periods, 2nd halves: not graded here.
            OTHER_PERIOD_WORDS.containsMatchIn(lower) -> return null
            else -> Period.GAME
        }
        val total = line?.let { NovigText.parseTotalOutcome(it) }
        val yesNo = line?.trim()?.lowercase()?.takeIf { it == "yes" || it == "no" }
        val totalName = totalWording(lower)
        return when {
            // A tennis match's first set: "1st Set Winner", "1st Set Moneyline".
            period == Period.FIRST_SET -> if (who.isEmpty() || line != null) null else Pick.FirstSet(who)
            lower.contains("team total") || lower.contains("games won") -> {
                if (who.isEmpty() || total == null || period != Period.GAME) null else Pick.TeamTotal(who, total.first, total.second)
            }
            lower.contains("moneyline") || lower == "money" || lower.contains("to win") -> {
                if (line != null || who.isEmpty() || period != Period.GAME) null else Pick.Moneyline(who)
            }
            SPREAD_WORDS.containsMatchIn(lower) -> {
                val points = line?.replace('−', '-')?.trim()?.toDoubleOrNull()
                if (who.isEmpty() || points == null || period == Period.FIRST_INNING) null else Pick.Spread(who, points, period)
            }
            // A game total ("Total", "Total Points", "Alternate Total", "F5 Total", "Total Games", "Total Sets",
            // "1st 5 Innings Total Runs", "1st Inning Total"): no one named.
            GAME_TOTAL.matches(totalName) || (period == Period.FIRST_INNING && who.isEmpty()) -> {
                if (who.isNotEmpty() || total == null) null else Pick.Total(total.first, total.second, period)
            }
            else -> {
                if (period != Period.GAME || who.isEmpty()) return null
                val stat = statOf(market) ?: return null
                when {
                    total != null -> Pick.Prop(who, stat, total.first, total.second)
                    // "Anytime Touchdown: Yes" is Over 0.5.
                    yesNo != null -> Pick.Prop(who, stat, yesNo == "yes", 0.5)
                    else -> null
                }
            }
        }
    }

    /** [lower] (a lowercase market name) as a plain "total ..." wording: no period, "alternate", "game" or parenthesis. */
    private fun totalWording(lower: String): String = lower
        .replace(PERIOD_PREFIX, "")
        .replace(PARENTHESES, " ")
        .replace(TOTAL_NOISE, " ")
        .replace(SPACES, " ").trim()

    /** Why [marketLabel] can't be graded from a score, for a bet [pickOf] couldn't read: written for the bet's card. */
    fun whyNot(marketLabel: String, selection: String): String {
        val lower = marketLabel.lowercase()
        return when {
            lower.contains("3-way") || lower.contains("3 way") || lower.contains("1x2") || lower.contains("draw") ->
                "3-way (draw) markets aren't graded automatically"
            FIRST_SCORER.containsMatchIn(lower) -> "\"$marketLabel\" needs play-by-play, which the score feeds don't carry"
            !FIRST_INNING_WORDS.containsMatchIn(lower) && !FIRST_SET_WORDS.containsMatchIn(lower) && !FIRST_HALF_WORDS.containsMatchIn(lower) &&
                OTHER_PERIOD_WORDS.containsMatchIn(lower) -> "Quarter, period and second-half markets aren't graded automatically"
            else -> "Couldn't read \"$marketLabel\" for \"${selection.trim()}\" well enough to grade it"
        }
    }

    /** Novig's stat for a market label ("Player Receiving Yards", "Passing Yards", "Pitcher Strikeouts"), when exactly one fits. */
    fun statOf(market: String): String? {
        val words = NovigBetFinder.marketWords(
            market.replace(ANYTIME_TD, "touchdowns")
                .replace(ANYTIME_GOAL, "player goals")
                .replace(THREES, "three pointers made"),
        )
        val fits = PROP_TYPES.filter { NovigBetFinder.typeFits(it, words) }
        return fits.singleOrNull()
            // "Strikeouts" alone: a pitcher's (the books' default), unless it says batter.
            ?: if (fits.toSet() == setOf("PITCHER_STRIKEOUTS", "BATTING_STRIKEOUTS") || (fits.isEmpty() && words == setOf("strikeouts"))) {
                if (market.contains("batter", true) || market.contains("batting", true)) "BATTING_STRIKEOUTS" else "PITCHER_STRIKEOUTS"
            } else {
                null
            }
    }

    /**
     * The game [bet] was on among [games]: both teams agree (either order written), nearest start wins. A
     * tennis match's two players are in no fixed home/away order, so either order counts there.
     */
    fun gameOf(bet: TrackedBet, games: List<GameScore>): GameScore? {
        val m = NovigText.parseMatchup(bet.eventName) ?: return null
        val scored = games.mapNotNull { g ->
            val awayAway = TeamMatcher.similarity(m.away, g.away)
            val homeHome = TeamMatcher.similarity(m.home, g.home)
            val awayHome = if (g.tennis) TeamMatcher.similarity(m.away, g.home) else 0.0
            val homeAway = if (g.tennis) TeamMatcher.similarity(m.home, g.away) else 0.0
            val straight = awayAway + homeHome
            val swapped = awayHome + homeAway
            val ok = if (swapped > straight) awayHome >= MIN_TEAM && homeAway >= MIN_TEAM else awayAway >= MIN_TEAM && homeHome >= MIN_TEAM
            val gap = if (g.tennis) TENNIS_START_GAP_MS else MAX_START_GAP_MS
            if (!ok || kotlin.math.abs(g.startMs - bet.startsTs) > gap) null else g to maxOf(straight, swapped)
        }
        if (scored.isEmpty()) return null
        val best = scored.maxOf { it.second }
        // Two games of the same pairing (a doubleheader): the one nearest the bet's start.
        return scored.filter { it.second == best }.minByOrNull { kotlin.math.abs(it.first.startMs - bet.startsTs) }?.first
    }

    /**
     * [pick]'s result in [game] ([players]: its box score, for props), or null while it can't be
     * told: the game isn't final, a period's score is missing, a player isn't in the box score.
     */
    fun grade(pick: Pick, game: GameScore, players: List<PlayerLine>? = null): BetStatus? {
        if (!game.final) return null
        val home = game.homeScore ?: return null
        val away = game.awayScore ?: return null
        return when (pick) {
            is Pick.Moneyline -> {
                val side = sideOf(pick.team, game) ?: return null
                val (mine, theirs) = if (side) away to home else home to away
                compare(mine.toDouble(), theirs.toDouble())
            }
            is Pick.Spread -> {
                val side = sideOf(pick.team, game) ?: return null
                val (h, a) = score(game, pick.period) ?: return null
                val (mine, theirs) = if (side) a to h else h to a
                compare(mine + pick.line, theirs.toDouble())
            }
            is Pick.Total -> {
                val (h, a) = score(game, pick.period) ?: return null
                overUnder((h + a).toDouble(), pick.over, pick.line)
            }
            is Pick.TeamTotal -> {
                val side = sideOf(pick.team, game) ?: return null
                overUnder((if (side) away else home).toDouble(), pick.over, pick.line)
            }
            is Pick.Prop -> {
                val line = players?.let { playerOf(pick.player, it) } ?: return null
                val value = line.stats[pick.stat] ?: return null
                overUnder(value, pick.over, pick.line)
            }
        }
    }

    /** True: [team] is [game]'s away side; false: home; null: can't tell. */
    private fun sideOf(team: String, game: GameScore): Boolean? = TeamMatcher.labelIsAway(team, game.away, game.home)

    /** (home, away) points in [period]: the whole game, the first half (5 innings in baseball), or the 1st inning. */
    private fun score(game: GameScore, period: Period): Pair<Int, Int>? = when (period) {
        Period.GAME -> (game.homeScore ?: return null) to (game.awayScore ?: return null)
        Period.FIRST_HALF -> {
            val n = when (game.league) {
                "MLB" -> 5
                "NCAAB" -> 1
                "NHL" -> return null
                else -> 2
            }
            if (game.homePeriods.size < n || game.awayPeriods.size < n) null
            else game.homePeriods.take(n).sum() to game.awayPeriods.take(n).sum()
        }
        Period.FIRST_INNING -> {
            if (game.league != "MLB" || game.homePeriods.isEmpty() || game.awayPeriods.isEmpty()) null
            else game.homePeriods[0] to game.awayPeriods[0]
        }
    }

    /** The box-score line for [player]: the same name ([PlayerNames]), else one unique "J. Surname". */
    fun playerOf(player: String, lines: List<PlayerLine>): PlayerLine? {
        lines.filter { PlayerNames.same(it.name, player) }.let { if (it.size == 1) return it.single() }
        val key = PlayerNames.key(player).split(' ')
        if (key.size < 2 || key.first().length != 1) return null
        val initial = key.first()
        val surname = key.drop(1)
        return lines.filter { l ->
            val k = PlayerNames.key(l.name).split(' ')
            k.size >= 2 && k.first().startsWith(initial) && k.drop(1) == surname
        }.singleOrNull()
    }

    private fun compare(mine: Double, theirs: Double): BetStatus = when {
        mine > theirs + 1e-9 -> BetStatus.WON
        mine < theirs - 1e-9 -> BetStatus.LOST
        else -> BetStatus.PUSH
    }

    private fun overUnder(value: Double, over: Boolean, line: Double): BetStatus = when {
        kotlin.math.abs(value - line) < 1e-9 -> BetStatus.PUSH
        (value > line) == over -> BetStatus.WON
        else -> BetStatus.LOST
    }

    private const val MIN_TEAM = 0.8

    private val GAME_TOTAL = Regex("^total( points| runs| goals)?$")
    private val FIRST_INNING_WORDS = Regex("1st inning|first inning|nrfi|yrfi")
    private val FIRST_HALF_WORDS = Regex("\\b1h\\b|1st half|first half|\\bf5\\b|first 5|1st 5")
    private val OTHER_PERIOD_WORDS = Regex("quarter|\\bq[1-4]\\b|period|2nd half|second half|\\b2h\\b|inning")
    private val SPREAD_WORDS = Regex("spread|run line|puck line|handicap")
    private val ANYTIME_TD = Regex("(?i)anytime (td|touchdown)( scorer)?")
    private val THREES = Regex("(?i)3-pointers made|3 pointers made|threes made|threes")

    /** A period in front of a market's name ("F5 Total", "1st 5 Innings Total Runs"). */
    private val PERIOD_PREFIX = Regex("^(1h|f5|1st half|first half|1st inning|first inning|1st 5 innings|first 5 innings|1st five innings)\\s+")

    /** A score feed's game and the bet's must start within this (Novig's placeholder times, late starts). */
    const val MAX_START_GAP_MS = 12 * 60 * 60_000L
}
