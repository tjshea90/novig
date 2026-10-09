package com.tjshea.vigilant.data.reference

import kotlin.math.roundToLong

/**
 * SGO's events as Vigilant's reference events (SPORTSGAMEODDS_API.md §3): each book's two sides of a market are paired into one [RefBookMarket] (moneyline, spread, game total, team total,
 * 1st-half and first-5/first-inning lines, player props), and every ALTERNATE line SGO sends (`includeAltLines`) becomes a market of its own at its own number, as Pinnacle's alternates
 * are elsewhere. A side with no price, an unavailable one, or no opposite side at the same number is left out: a fair line needs both sides of a book's price (RESEARCH.md §24).
 * Each market's age is the OLDER of its two sides' `lastUpdatedAt`, which the scan's freshness rule judges.
 */
object SgoConvert {
    const val PREFIX = "sgo:"

    /** Vigilant's period number for an SGO periodID, or null for one Novig has no market for. [totalOnly] ones are over/under lines only. */
    private fun period(periodId: String, baseball: Boolean): Pair<Int, Boolean>? = when (periodId) {
        "game" -> 0 to false
        "1h" -> 1 to false
        "1ix5" -> if (baseball) 1 to false else null
        "1i" -> if (baseball) RefBookMarket.PERIOD_FIRST_INNING to true else null
        else -> null
    }

    fun toRef(e: SgoEvent, sportKey: String, wanted: Set<String>, props: Boolean = true): RefEvent? {
        val start = e.startsMs ?: return null
        if (e.home.isBlank() || e.away.isBlank() || e.cancelled) return null
        val sport = Sport.of(e.leagueId)
        val byKey = e.odds.associateBy { key(it.statId, it.entityId, it.periodId, it.betType, it.sideId) }
        val markets = ArrayList<RefBookMarket>()
        for (a in e.odds) {
            val first = when {
                a.betType == "ml" || a.betType == "sp" -> a.sideId == "home"
                a.betType == "ou" || a.betType == "yn" -> a.sideId == "over" || a.sideId == "yes"
                else -> false
            }
            if (!first) continue
            val opp = (a.opposingOddId?.let { id -> e.odds.firstOrNull { it.oddId == id } }) ?: byKey[key(a.statId, if (a.betType == "ou" || a.betType == "yn") a.entityId else "away", a.periodId, a.betType, opposite(a.betType, a.sideId))] ?: continue
            val (p, totalOnly) = period(a.periodId, sport == Sport.BASEBALL) ?: continue
            val kind: LineKind
            var subject: String? = null
            var stat: String? = null
            when {
                a.statId == "points" && a.betType == "ml" && a.entityId == "home" -> { if (totalOnly) continue; kind = LineKind.MONEYLINE }
                a.statId == "points" && a.betType == "sp" && a.entityId == "home" -> { if (totalOnly) continue; kind = LineKind.SPREAD }
                a.statId == "points" && a.betType == "ou" && a.entityId == "all" -> kind = LineKind.TOTAL
                a.statId == "points" && a.betType == "ou" && (a.entityId == "home" || a.entityId == "away") -> {
                    if (p != 0) continue
                    kind = LineKind.TEAM_TOTAL
                    subject = if (a.entityId == "home") RefBookMarket.HOME else RefBookMarket.AWAY
                }
                props && p == 0 && (a.betType == "ou" || a.betType == "yn") && a.entityId !in setOf("home", "away", "all") -> {
                    stat = SgoProps.novigStat(sport, a.statId) ?: continue
                    kind = LineKind.PLAYER_PROP
                    subject = e.players[a.entityId]?.name ?: SgoProps.nameFromId(a.entityId) ?: continue
                }
                else -> continue
            }
            val yesNo = a.betType == "yn"
            for (book in (a.byBook.keys + opp.byBook.keys).toSet()) {
                if (book in SgoBooks.EXCLUDED || book !in wanted) continue
                val aLines = a.byBook[book].orEmpty().filter { it.available && it.american != null }
                val bLines = opp.byBook[book].orEmpty().filter { it.available && it.american != null }
                if (aLines.isEmpty() || bLines.isEmpty()) continue
                val appKey = SgoBooks.appKey(book)
                if (kind == LineKind.MONEYLINE) {
                    val x = aLines.first(); val y = bLines.first()
                    markets += RefBookMarket(appKey, SgoBooks.title(appKey), kind, listOf(RefQuote(Side.HOME, SgoParser.decimal(x.american) ?: continue, null), RefQuote(Side.AWAY, SgoParser.decimal(y.american) ?: continue, null)), older(x, y), period = p)
                    continue
                }
                val seen = HashSet<Long>()
                for (x in aLines) {
                    val pointX = (x.point ?: if (yesNo) 0.5 else null) ?: continue
                    // The other side's number: a spread mirrors (home -3.5 / away +3.5), a total or prop repeats.
                    val want = if (kind == LineKind.SPREAD) -pointX else pointX
                    val y = bLines.firstOrNull { l -> (l.point ?: if (yesNo) 0.5 else null)?.let { abs100(it - want) } == true } ?: continue
                    if (!seen.add(((pointX * 100.0).roundToLong()))) continue
                    val dx = SgoParser.decimal(x.american) ?: continue
                    val dy = SgoParser.decimal(y.american) ?: continue
                    val quotes = if (kind == LineKind.SPREAD) listOf(RefQuote(Side.HOME, dx, pointX), RefQuote(Side.AWAY, dy, -pointX))
                    else listOf(RefQuote(Side.OVER, dx, pointX), RefQuote(Side.UNDER, dy, pointX))
                    markets += RefBookMarket(appKey, SgoBooks.title(appKey), kind, quotes, older(x, y), period = p, subject = subject, stat = stat)
                }
            }
        }
        return RefEvent(PREFIX + e.eventId, sportKey, start, e.home, e.away, markets)
    }

    private fun abs100(d: Double) = kotlin.math.abs(d) < 0.005
    private fun older(x: SgoLine, y: SgoLine): Long? = if (x.updatedMs != null && y.updatedMs != null) minOf(x.updatedMs, y.updatedMs) else null
    private fun key(stat: String, entity: String, period: String, bet: String, side: String) = "$stat|$entity|$period|$bet|$side"
    private fun opposite(bet: String, side: String) = when (bet) {
        "ml", "sp" -> if (side == "home") "away" else "home"
        "ou" -> if (side == "over") "under" else "over"
        "yn" -> if (side == "yes") "no" else "yes"
        else -> side
    }

    /** Vigilant sports as SGO groups them (for the prop-stat names). */
    enum class Sport {
        FOOTBALL, BASEBALL, BASKETBALL, HOCKEY, OTHER;

        companion object {
            fun of(leagueId: String): Sport = when (leagueId) {
                "NFL", "NCAAF" -> FOOTBALL
                "MLB" -> BASEBALL
                "NBA", "NCAAB", "WNBA" -> BASKETBALL
                "NHL" -> HOCKEY
                else -> OTHER
            }
        }
    }
}

/** SGO statIDs (SPORTSGAMEODDS_API.md §3) as Novig's prop stat names. */
object SgoProps {
    private val FOOTBALL = mapOf(
        "passing_yards" to "PASSING_YARDS", "rushing_yards" to "RUSHING_YARDS", "receiving_yards" to "RECEIVING_YARDS", "receiving_receptions" to "RECEPTIONS",
        "passing_touchdowns" to "PASSING_TOUCHDOWNS", "touchdowns" to "TOUCHDOWNS", "passing_attempts" to "PASSING_ATTEMPTS", "passing_completions" to "PASSING_COMPLETIONS",
        "rushing_attempts" to "RUSHING_ATTEMPTS", "passing_interceptions" to "INTERCEPTIONS_THROWN", "rushing+receiving_yards" to "RUSHING_AND_RECEIVING_YARDS",
        "passing+rushing_yards" to "PASSING_AND_RUSHING_YARDS", "receiving_longestReception" to "LONGEST_RECEPTION", "rushing_longestRush" to "LONGEST_RUSH",
        "passing_longestCompletion" to "LONGEST_COMPLETION", "kicking_totalPoints" to "KICKING_POINTS", "fieldGoals_made" to "FIELD_GOALS_MADE",
    )
    private val BASEBALL = mapOf(
        "batting_hits" to "HITS", "batting_totalBases" to "TOTAL_BASES", "pitching_strikeouts" to "PITCHER_STRIKEOUTS", "batting_hits+runs+rbi" to "HITS_RUNS_RBIS",
        "batting_homeRuns" to "HOME_RUNS", "batting_RBI" to "RBIS", "batting_stolenBases" to "STOLEN_BASES", "batting_strikeouts" to "BATTING_STRIKEOUTS",
        "batting_basesOnBalls" to "BATTING_WALKS", "pitching_hits" to "HITS_ALLOWED", "pitching_earnedRuns" to "EARNED_RUNS", "pitching_outs" to "PITCHER_OUTS",
        "pitching_basesOnBalls" to "WALKS",
    )
    private val BASKETBALL = mapOf(
        "points" to "POINTS", "rebounds" to "REBOUNDS", "assists" to "ASSISTS", "threePointersMade" to "THREE_POINTERS_MADE",
        "points+rebounds+assists" to "POINTS_REBOUNDS_ASSISTS", "doubleDouble" to "DOUBLE_DOUBLE",
    )
    // In hockey `points` is GOALS and `goals+assists` is what the sport calls points.
    private val HOCKEY = mapOf(
        "points" to "PLAYER_GOALS", "goals+assists" to "POINTS", "assists" to "ASSISTS", "shots_onGoal" to "SHOTS_ON_GOAL", "goalie_saves" to "SAVES",
        "powerPlay_goals+assists" to "POWER_PLAY_POINTS",
    )

    fun novigStat(sport: SgoConvert.Sport, statId: String): String? = when (sport) {
        SgoConvert.Sport.FOOTBALL -> FOOTBALL[statId]
        SgoConvert.Sport.BASEBALL -> BASEBALL[statId]
        SgoConvert.Sport.BASKETBALL -> BASKETBALL[statId]
        SgoConvert.Sport.HOCKEY -> HOCKEY[statId]
        SgoConvert.Sport.OTHER -> null
    }

    /** SGO statIDs of a sport's priced props, for the `oddID` filter ("passing_yards-PLAYER_ID-game-ou-over"). */
    fun statIds(sport: SgoConvert.Sport): Set<String> = when (sport) {
        SgoConvert.Sport.FOOTBALL -> FOOTBALL.keys
        SgoConvert.Sport.BASEBALL -> BASEBALL.keys
        SgoConvert.Sport.BASKETBALL -> BASKETBALL.keys
        SgoConvert.Sport.HOCKEY -> HOCKEY.keys
        SgoConvert.Sport.OTHER -> emptySet()
    }

    /** Yes/No stats SGO sells as `yn` or as an over/under of 0.5 (anytime touchdown, double-double). */
    val YES_NO = setOf("touchdowns", "doubleDouble", "batting_homeRuns")

    /** "PATRICK_MAHOMES_1_NFL" -> "Patrick Mahomes" when SGO's `players` map did not name the player. */
    fun nameFromId(playerId: String): String? {
        val parts = playerId.split('_').filter { it.isNotEmpty() }
        val words = parts.takeWhile { !it.all(Char::isDigit) }
        if (words.size < 2) return null
        return words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }
}
