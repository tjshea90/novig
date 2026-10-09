package com.tjshea.vigilant.data.reference

/** What a market of OddsPapi's catalogue is in Vigilant's terms (ODDSPAPI_API.md §5). [first] is participant 1 / Over / Yes, [second] participant 2 / Under / No (outcome ids). */
data class OpClass(
    val marketId: Long,
    val kind: LineKind,
    /** 0 full game (OddsPapi's `result`: overtime included, as Novig settles), 1 first half / first 5 innings, [RefBookMarket.PERIOD_FIRST_INNING]. */
    val period: Int,
    val point: Double?,
    /** [LineKind.TEAM_TOTAL]: which participant (1 or 2). */
    val team: Int?,
    val stat: String?,
    val first: Long,
    val second: Long,
    val yesNo: Boolean,
    /** The market is a fallback label (an American-football 2-way winner on `fulltime`): used only when the book has no `result` market of the same line. */
    val fallback: Boolean = false,
)

/**
 * OddsPapi's `/markets` catalogue for one sport, read into [OpClass]es. The catalogue is "append-only, renames happen" (docs, changelog 2026-10-05), so nothing is keyed on a name: a market is
 * recognised by its `marketType` family (`moneyline`/`1x2` with two outcomes, `spreads`, `totals`, `teamtotals-team1|2`, `players-…`), its `period` and its outcomes' own names, and anything else
 * (three-way markets, European handicaps, odd/even, prop types Vigilant has no stat for) is left alone rather than guessed at.
 */
class OpMarkets(markets: List<OpMarket>, private val league: String) {
    private val byId = HashMap<Long, OpClass?>()
    private val byOutcome = HashMap<Long, Long>()
    private val raw = markets.associateBy { it.marketId }

    /** Prop `marketType`s seen that have no Vigilant stat, and the types read: what Test key reports so the stat table can be completed from a real answer. */
    val unmappedProps: Set<String> = markets.filter { isProp(it) && statOf(it) == null }.map { it.type }.toSet()
    val propTypes: Set<String> = markets.filter { isProp(it) }.map { it.type }.toSet()
    val size: Int get() = raw.size

    init {
        markets.forEach { m -> m.outcomes.forEach { (id, _) -> byOutcome[id] = m.marketId } }
    }

    /** The market an outcome id belongs to (a price that does not say its `marketId`). */
    fun marketOf(outcomeId: Long): Long? = byOutcome[outcomeId]

    fun classify(marketId: Long): OpClass? = byId.getOrPut(marketId) { raw[marketId]?.let { classifyOne(it) } }

    private fun isProp(m: OpMarket) = m.playerProp || m.type.startsWith("players-") || m.type.startsWith("playertotals-")

    private fun statOf(m: OpMarket): String? {
        val tail = m.type.substringAfter('-', "")
        return OpProps.stat(league, tail)
    }

    private fun classifyOne(m: OpMarket): OpClass? {
        if (m.outcomes.size != 2) return null
        val (a, b) = m.outcomes
        val an = a.second.trim().lowercase()
        val bn = b.second.trim().lowercase()
        val period = periodOf(m.period, league)
        if (isProp(m)) {
            val stat = statOf(m) ?: return null
            if (m.period != "result" || (!(an == "over" && bn == "under") && !(an == "yes" && bn == "no"))) return null
            val yn = an == "yes"
            val point = if (yn) 0.5 else m.handicap ?: return null
            return OpClass(m.marketId, LineKind.PLAYER_PROP, 0, point, null, stat, a.first, b.first, yn)
        }
        val type = m.type
        val sides12 = an == "1" && bn == "2"
        val overUnder = an == "over" && bn == "under"
        return when {
            (type == "moneyline" || type == "1x2") && sides12 -> when (m.period) {
                "result" -> OpClass(m.marketId, LineKind.MONEYLINE, 0, null, null, null, a.first, b.first, false)
                "fulltime" -> if (league in AMERICAN_FOOTBALL) OpClass(m.marketId, LineKind.MONEYLINE, 0, null, null, null, a.first, b.first, false, fallback = true) else null
                else -> null
            }
            type == "spreads" && sides12 && period != null && period != RefBookMarket.PERIOD_FIRST_INNING ->
                OpClass(m.marketId, LineKind.SPREAD, period, m.handicap ?: return null, null, null, a.first, b.first, false)
            type == "totals" && overUnder && period != null -> OpClass(m.marketId, LineKind.TOTAL, period, m.handicap ?: return null, null, null, a.first, b.first, false)
            (type == "teamtotals-team1" || type == "teamtotals-team2") && overUnder && m.period == "result" ->
                OpClass(m.marketId, LineKind.TEAM_TOTAL, 0, m.handicap ?: return null, if (type.endsWith("1")) 1 else 2, null, a.first, b.first, false)
            else -> null
        }
    }

    companion object {
        val AMERICAN_FOOTBALL = setOf("NFL", "NCAAF")

        /**
         * Vigilant's period number for an OddsPapi `period`, or null for one Novig has no market for: `result` is the whole game (overtime included); the first half is `p1+p2` (a quarter sport: the first
         * two quarters) or `p1` where the league plays halves (NCAAB); baseball's first five innings are `p1+p2+p3+p4+p5` and its first inning `p1`.
         */
        fun periodOf(period: String, league: String): Int? = when (league) {
            "NFL", "NCAAF", "NBA", "WNBA" -> when (period) { "result" -> 0; "p1+p2" -> 1; else -> null }
            "NCAAB" -> when (period) { "result" -> 0; "p1" -> 1; else -> null }
            "MLB" -> when (period) { "result" -> 0; "p1+p2+p3+p4+p5" -> 1; "p1" -> RefBookMarket.PERIOD_FIRST_INNING; else -> null }
            "NHL" -> if (period == "result") 0 else null
            else -> null
        }
    }
}

/**
 * OddsPapi's player-prop `marketType` tails (`players-<tail>`) as Novig's stat names, by league. The docs name the hockey ones (`players-goals`, `players-points`, `players-assists`, `players-shotsongoal`,
 * `players-saves`, `players-powerplaypoints`); the football, basketball and baseball tails are the same words by the same convention and are NOT yet seen on a real answer: a tail not in the table is
 * not guessed at (the prop is left unpriced), and Test key lists every prop type the key carries so the table can be completed from the sample.
 */
object OpProps {
    private fun n(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private val FOOTBALL = mapOf(
        "passingyards" to "PASSING_YARDS", "rushingyards" to "RUSHING_YARDS", "receivingyards" to "RECEIVING_YARDS", "receptions" to "RECEPTIONS",
        "passingtouchdowns" to "PASSING_TOUCHDOWNS", "anytimetouchdown" to "TOUCHDOWNS", "anytimetouchdownscorer" to "TOUCHDOWNS", "passingattempts" to "PASSING_ATTEMPTS",
        "passingcompletions" to "PASSING_COMPLETIONS", "rushingattempts" to "RUSHING_ATTEMPTS", "interceptions" to "INTERCEPTIONS_THROWN", "passinginterceptions" to "INTERCEPTIONS_THROWN",
        "rushingandreceivingyards" to "RUSHING_AND_RECEIVING_YARDS", "passingandrushingyards" to "PASSING_AND_RUSHING_YARDS", "longestreception" to "LONGEST_RECEPTION",
        "longestrush" to "LONGEST_RUSH", "longestcompletion" to "LONGEST_COMPLETION", "fieldgoalsmade" to "FIELD_GOALS_MADE",
    )
    private val BASEBALL = mapOf(
        "hits" to "HITS", "totalbases" to "TOTAL_BASES", "pitcherstrikeouts" to "PITCHER_STRIKEOUTS", "strikeoutspitcher" to "PITCHER_STRIKEOUTS", "hitsrunsrbis" to "HITS_RUNS_RBIS",
        "homeruns" to "HOME_RUNS", "rbis" to "RBIS", "stolenbases" to "STOLEN_BASES", "battingstrikeouts" to "BATTING_STRIKEOUTS", "hitsallowed" to "HITS_ALLOWED",
        "earnedruns" to "EARNED_RUNS", "pitcherouts" to "PITCHER_OUTS", "outsrecorded" to "PITCHER_OUTS", "walksallowed" to "WALKS",
    )
    private val BASKETBALL = mapOf(
        "points" to "POINTS", "rebounds" to "REBOUNDS", "assists" to "ASSISTS", "threepointersmade" to "THREE_POINTERS_MADE", "threes" to "THREE_POINTERS_MADE",
        "pointsreboundsassists" to "POINTS_REBOUNDS_ASSISTS", "doubledouble" to "DOUBLE_DOUBLE",
    )
    private val HOCKEY = mapOf(
        "goals" to "PLAYER_GOALS", "points" to "POINTS", "assists" to "ASSISTS", "shotsongoal" to "SHOTS_ON_GOAL", "saves" to "SAVES", "powerplaypoints" to "POWER_PLAY_POINTS",
    )

    /** Yes/No stats read as over/under 0.5 (anytime touchdown, double-double, a home run). */
    val YES_NO = setOf("TOUCHDOWNS", "DOUBLE_DOUBLE", "HOME_RUNS")

    fun stat(league: String, tail: String): String? {
        val key = n(tail)
        return when (league) {
            "NFL", "NCAAF" -> FOOTBALL[key]
            "MLB" -> BASEBALL[key]
            "NBA", "NCAAB", "WNBA" -> BASKETBALL[key]
            "NHL" -> HOCKEY[key]
            else -> null
        }
    }

    /** Every Novig stat OddsPapi can price in any league: what the scan loads Novig's prop markets for ([ReferenceSource.extraPropTypes]). */
    val ALL_TYPES: Set<String> = (FOOTBALL.values + BASEBALL.values + BASKETBALL.values + HOCKEY.values).toSet()

    fun supports(league: String): Boolean = league in setOf("NFL", "NCAAF", "MLB", "NBA", "NCAAB", "WNBA", "NHL")

    /** "Jokic, Nikola" -> "Nikola Jokic". */
    fun displayName(name: String): String {
        val i = name.indexOf(',')
        return if (i > 0) (name.substring(i + 1).trim() + " " + name.substring(0, i).trim()).trim() else name.trim()
    }
}
