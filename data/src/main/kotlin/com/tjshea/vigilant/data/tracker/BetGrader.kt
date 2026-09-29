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
    fun grade(pick: Pick, game: GameScore, players: List<PlayerLine>? = null): BetStatus? =
        (gradeDetailed(pick, game, players) as? Grade.Result)?.status

    /** [grade] with what the result rests on ("Final: Mets 7, Nationals 1"), or why there's none yet or ever. */
    fun gradeDetailed(pick: Pick, game: GameScore, players: List<PlayerLine>? = null): Grade {
        if (!game.final) return Grade.Waiting("The game isn't over yet")
        val home = game.homeScore ?: return Grade.Waiting("The final score isn't posted yet")
        val away = game.awayScore ?: return Grade.Waiting("The final score isn't posted yet")
        val final = finalLine(game)
        fun result(status: BetStatus?, detail: String = final): Grade =
            if (status == null) Grade.Manual("Couldn't compare the result") else Grade.Result(status, detail)
        return when (pick) {
            is Pick.Moneyline -> {
                val side = sideOf(pick.team, game) ?: return unknownSide(pick.team, game)
                val (mine, theirs) = if (side) away to home else home to away
                result(compare(mine.toDouble(), theirs.toDouble()))
            }
            is Pick.Spread -> {
                val side = sideOf(pick.team, game) ?: return unknownSide(pick.team, game)
                val (h, a) = score(game, pick.period) ?: return noPeriod(pick.period, game)
                val (mine, theirs) = if (side) a to h else h to a
                result(compare(mine + pick.line, theirs.toDouble()), periodLine(game, pick.period, h, a))
            }
            is Pick.Total -> {
                val (h, a) = score(game, pick.period) ?: return noPeriod(pick.period, game)
                result(overUnder((h + a).toDouble(), pick.over, pick.line), periodLine(game, pick.period, h, a) + " (total ${h + a})")
            }
            is Pick.TeamTotal -> {
                val side = sideOf(pick.team, game) ?: return unknownSide(pick.team, game)
                val (h, a) = score(game, Period.GAME) ?: return noPeriod(Period.GAME, game)
                val mine = if (side) a else h
                result(overUnder(mine.toDouble(), pick.over, pick.line), "${pick.team}: $mine")
            }
            is Pick.FirstSet -> {
                val side = sideOf(pick.player, game) ?: return unknownSide(pick.player, game)
                val (h, a) = score(game, Period.FIRST_SET) ?: return noPeriod(Period.FIRST_SET, game)
                val (mine, theirs) = if (side) a to h else h to a
                result(compare(mine.toDouble(), theirs.toDouble()), "1st set: ${game.away} $a, ${game.home} $h")
            }
            is Pick.Prop -> {
                if (game.tennis) return Grade.Manual("A tennis match's ${PropStats.displayName(pick.stat).lowercase()} isn't in the score feed: mark it yourself")
                val box = players ?: return Grade.Waiting("The box score isn't available yet")
                if (box.count { !it.inactive } < MIN_BOX) return Grade.Waiting("The box score isn't fully posted yet")
                val label = PropStats.displayName(pick.stat)
                val football = game.league in FOOTBALL
                val line = playerOf(pick.player, box)
                    ?: return when {
                        // Someone with nearly his name is in it: a spelling, not a player who sat out.
                        lookalike(pick.player, box) -> Grade.Manual("${pick.player} isn't in the box score under that name (a similar name is): mark it yourself")
                        // A football box score lists only players who recorded a stat: no line is no stat, and he still played.
                        football && pick.stat in FOOTBALL_ZERO_STATS ->
                            result(overUnder(0.0, pick.over, pick.line), "${pick.player}: no $label recorded (no line in the box score, counted as 0)")
                        football -> Grade.Manual("${pick.player} has no line in the box score for $label: mark it yourself")
                        // Every other box score lists everyone who played, so he didn't (Novig refunds a player who sat out).
                        else -> Grade.Result(BetStatus.VOID, "${pick.player} didn't play (not in the box score): void")
                    }
                if (line.inactive) return Grade.Result(BetStatus.VOID, "${line.name} was ruled out and didn't play: void")
                val recorded = line.stats[pick.stat]
                val value = recorded ?: if (football && pick.stat in FOOTBALL_ZERO_STATS) 0.0 else {
                    return Grade.Manual("The box score has no $label for ${pick.player}: mark it yourself")
                }
                val shown = if (recorded != null) "${trim(value)} $label" else "no $label recorded (counted as 0)"
                result(overUnder(value, pick.over, pick.line), "${line.name}: $shown")
            }
        }
    }

    private fun unknownSide(team: String, game: GameScore) =
        Grade.Manual("Couldn't tell which side \"$team\" is in ${game.away} @ ${game.home}: mark it yourself")

    private fun noPeriod(period: Period, game: GameScore) = Grade.Manual(
        when (period) {
            Period.FIRST_HALF -> "The score feed has no first-half score for this ${game.league} game: mark it yourself"
            Period.FIRST_INNING -> "The score feed has no first-inning score for this game: mark it yourself"
            Period.FIRST_SET -> "The score feed has no first-set score for this match: mark it yourself"
            Period.SETS -> "Sets only apply to a tennis match: mark it yourself"
            Period.GAME -> "The score feed has no game score to grade this with: mark it yourself"
        },
    )

    /** "Final: New York Mets 7, Washington Nationals 1" (a tennis match: sets and each set's games). */
    private fun finalLine(game: GameScore): String {
        if (game.tennis) {
            val sets = game.homePeriods.zip(game.awayPeriods) { h, a -> "$h-$a" }.joinToString(" ")
            return "Final: ${game.home} ${game.homeScore}-${game.awayScore} ${game.away} (sets), games $sets"
        }
        return "Final: ${game.away} ${game.awayScore}, ${game.home} ${game.homeScore}"
    }

    private fun periodLine(game: GameScore, period: Period, h: Int, a: Int): String = when (period) {
        Period.GAME -> if (game.tennis) "Games: ${game.home} $h, ${game.away} $a" else finalLine(game)
        Period.SETS -> "Sets: ${game.home} $h, ${game.away} $a"
        Period.FIRST_HALF -> "First half: ${game.away} $a, ${game.home} $h"
        Period.FIRST_INNING -> "1st inning: ${game.away} $a, ${game.home} $h"
        Period.FIRST_SET -> "1st set: ${game.away} $a, ${game.home} $h"
    }

    private fun trim(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    /** True: [team] is [game]'s away side; false: home; null: can't tell. */
    private fun sideOf(team: String, game: GameScore): Boolean? = TeamMatcher.labelIsAway(team, game.away, game.home)

    /**
     * (home, away) points in [period]: the whole game (a tennis match's games), the first half (5 innings in
     * baseball), the 1st inning, a tennis match's sets or its first set.
     */
    private fun score(game: GameScore, period: Period): Pair<Int, Int>? = when (period) {
        Period.GAME -> if (game.tennis) {
            if (game.homePeriods.isEmpty() || game.awayPeriods.isEmpty()) null else game.homePeriods.sum() to game.awayPeriods.sum()
        } else {
            (game.homeScore ?: return null) to (game.awayScore ?: return null)
        }
        Period.SETS -> if (!game.tennis) null else (game.homeScore ?: return null) to (game.awayScore ?: return null)
        Period.FIRST_SET -> if (!game.tennis || game.homePeriods.isEmpty() || game.awayPeriods.isEmpty()) null else game.homePeriods[0] to game.awayPeriods[0]
        Period.FIRST_HALF -> {
            val n = when (game.league) {
                "MLB" -> 5
                "NCAAB" -> 1
                "NHL", "ATP", "WTA" -> return null
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

    /** A box-score line whose name is close to [player]'s (same surname, first names starting alike) without being the same name. */
    private fun lookalike(player: String, lines: List<PlayerLine>): Boolean {
        val key = PlayerNames.key(player).split(' ')
        if (key.size < 2) return false
        return lines.any { l ->
            val k = PlayerNames.key(l.name).split(' ')
            k.size >= 2 && k.last() == key.last() && k.first().first() == key.first().first()
        }
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

    /** A box score with fewer players than this isn't posted yet (a real one has dozens): nobody is judged absent from it. */
    private const val MIN_BOX = 8

    private val FOOTBALL = setOf("NFL", "NCAAF")

    /**
     * Football stats ESPN lists only for a player who recorded one (a receiver with no carries has no rushing line): a player
     * found in the box score, or not in it at all, with none of these has zero. Longest plays are left out: no play is no market.
     */
    private val FOOTBALL_ZERO_STATS = setOf(
        "PASSING_YARDS", "PASSING_TOUCHDOWNS", "PASSING_COMPLETIONS", "PASSING_ATTEMPTS", "INTERCEPTIONS_THROWN",
        "RUSHING_YARDS", "RUSHING_ATTEMPTS", "RECEIVING_YARDS", "RECEPTIONS", "RUSHING_AND_RECEIVING_YARDS", "PASSING_AND_RUSHING_YARDS",
        "TOUCHDOWNS", "TACKLES_ASSISTS", "SACKS", "FIELD_GOALS_MADE", "KICKING_POINTS",
    )

    private val GAME_TOTAL = Regex("^total( points| runs| goals| games| sets)?$")
    private val FIRST_INNING_WORDS = Regex("1st inning|first inning|nrfi|yrfi")
    private val FIRST_SET_WORDS = Regex("1st set|first set|\\bset 1\\b")
    private val SET_WORDS = Regex("\\bsets? (spread|handicap)|total sets|sets total")
    private val PARENTHESES = Regex("\\([^)]*\\)")
    private val SPACES = Regex("\\s+")
    /** Words in a total's name that say nothing about which total it is ("Alternate Total", "Game Total", "Match Total"). */
    private val TOTAL_NOISE = Regex("\\b(alternate|alt|game|match|full|time|regulation|incl\\.?|including|overtime|ot)\\b")
    private val FIRST_SCORER = Regex("(first|last|next) (touchdown|td|goal|basket|scorer|team to score|to score)|\\bfirst basket|first (goal|touchdown) ?scorer")
    private val FIRST_HALF_WORDS = Regex("\\b1h\\b|1st half|first half|\\bf5\\b|first 5|1st 5")
    private val OTHER_PERIOD_WORDS = Regex("quarter|\\bq[1-4]\\b|period|2nd half|second half|\\b2h\\b|inning")
    private val SPREAD_WORDS = Regex("spread|run line|puck line|handicap")
    private val ANYTIME_TD = Regex("(?i)anytime (td|touchdown)( scorer)?")
    private val THREES = Regex("(?i)3-pointers made|3 pointers made|threes made|threes")
    private val ANYTIME_GOAL = Regex("(?i)anytime goal ?scorer|to score a goal")

    /**
     * Every Novig player-prop type a bet can be on (its `types/markets`, 2026-09-29; futures, awards and
     * first-scorer markets left out: no score feed carries them): the stats Vigilant prices plus the ones
     * only CrazyNinjaOdds' list shows (tackles, saves, shots, steals, blocks, turnovers, combos).
     */
    private val PROP_TYPES: List<String> = (
        PropStats.NOVIG_TYPES + listOf(
            "TACKLES_ASSISTS", "SAVES", "SHOTS_ON_GOAL", "PLAYER_GOALS", "GOALS_ASSISTS", "ASSISTS", "POINTS",
            "REBOUNDS", "POINTS_ASSISTS", "POINTS_REBOUNDS", "REBOUNDS_ASSISTS", "STEALS", "BLOCKS", "STEALS_BLOCKS",
            "TURNOVERS", "TRIPLE_DOUBLE", "DOUBLE_DOUBLE", "PLAYER_ACES",
        )
    ).distinct()

    /** A period in front of a market's name ("F5 Total", "1st 5 Innings Total Runs"). */
    private val PERIOD_PREFIX = Regex("^(1h|f5|1st half|first half|1st inning|first inning|1st 5 innings|first 5 innings|1st five innings)\\s+")

    /** A score feed's game and the bet's must start within this (Novig's placeholder times, late starts). */
    const val MAX_START_GAP_MS = 12 * 60 * 60_000L

    /** …and a tennis match's, whose start is only "after the previous match" (order of play). */
    const val TENNIS_START_GAP_MS = 24 * 60 * 60_000L
}
