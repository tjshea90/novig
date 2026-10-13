package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.OpProps
import com.tjshea.vigilant.data.reference.RefBookMarket

/** What a BoltOdds outcome is, from its name and shape alone (the team side is worked out in [BoltConvert], which knows the game's teams). */
data class BoltMarketRef(val kind: LineKind, val period: Int, val stat: String? = null)

/**
 * BoltOdds' market names against Vigilant's line kinds (BOLTODDS_API.md §3, §8). The docs show only "Moneyline", "Spread" and player-prop names such as "Goals" and "Home Runs"; every other name is read by
 * the words in it and ONLY when the outcome's shape agrees (a moneyline has no number and no Over/Under, a spread has a number and no Over/Under, a total, team total or prop has both), so a name this table
 * does not know is left unpriced, never guessed at. Test key lists every (sport, name) it saw with what became of it, so the table can be completed from the sample.
 */
object BoltMarkets {
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private val FIRST_HALF = Regex("\\b(1st|first)\\s*(half|h)\\b|\\b1h\\b|\\bh1\\b")
    private val FIRST_FIVE = Regex("\\b(1st|first)\\s*(5|five)(\\s*innings?)?\\b|\\bf5\\b")
    private val FIRST_INNING = Regex("\\b(1st|first)\\s+inning\\b")

    /** Any other part of a game: a market for it is not one Novig lists, so it is not priced. */
    private val OTHER_PERIOD = Regex(
        "\\b(2nd|second|3rd|third|4th|fourth)\\s*(half|quarter|period|inning|set)\\b|\\bq[1-4]\\b|\\b[1-4]q\\b|\\b(1st|first)\\s*(quarter|period|set|game|map|round)\\b|\\bset\\s*\\d\\b|\\bgame\\s*\\d\\b|" +
            "\\b(1st|first)\\s*(3|three|7|seven)\\b|\\binning\\s*\\d\\b|\\bhalftime\\b|\\bquarter\\b|\\bperiod\\b|\\bround\\s*\\d\\b|\\bmap\\s*\\d\\b|\\bregulation\\b|\\b3[- ]?way\\b|\\bthree[- ]way\\b",
    )

    private val MONEYLINE = setOf("moneyline", "ml", "matchwinner", "gamewinner", "winner", "headtohead", "h2h", "fightwinner", "moneylinegame", "twowaymoneyline", "2waymoneyline")
    private val SPREAD = setOf("spread", "pointspread", "runline", "puckline", "handicap", "pointhandicap", "spreadgame", "gamespread")
    private val TOTAL = setOf("total", "totalpoints", "totalruns", "totalgoals", "overunder", "gametotal", "totalgamepoints", "totalscore", "gametotalpoints", "totalgamegoals", "totalgameruns")
    private val TEAM_TOTAL = setOf("teamtotal", "teamtotalpoints", "teamtotalruns", "teamtotalgoals", "teamtotalscore")

    /** The words that mean "another line of the same market": the alternates are further lines of the same kind. */
    private fun withoutAlternate(b: String): String = when {
        b.startsWith("alternate") -> b.removePrefix("alternate")
        b.startsWith("alt") -> b.removePrefix("alt")
        else -> b
    }

    /** The period of [name], 0 (the game), 1 (first half / first five innings), [RefBookMarket.PERIOD_FIRST_INNING], or null for a part of the game Vigilant does not price. */
    fun period(name: String): Pair<Int, String>? {
        val lower = name.lowercase()
        if (OTHER_PERIOD.containsMatchIn(lower)) return null
        return when {
            FIRST_INNING.containsMatchIn(lower) -> RefBookMarket.PERIOD_FIRST_INNING to FIRST_INNING.replace(lower, " ")
            FIRST_FIVE.containsMatchIn(lower) -> 1 to FIRST_FIVE.replace(lower, " ")
            FIRST_HALF.containsMatchIn(lower) -> 1 to FIRST_HALF.replace(lower, " ")
            else -> 0 to lower
        }
    }

    private class Memo(val ref: BoltMarketRef?)

    /** league -> market name -> the answer for each shape of outcome (a number? an Over/Under?): see [classify]. */
    private val classifyMemo = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Array<Memo?>>>()
    private val usableMemo = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Array<Boolean?>>>()

    /** The most distinct names kept per league (the books use a few hundred): a flood of odd names is classified each time, never remembered without end. */
    private const val MAX_MEMO = 4_000

    /**
     * What [o] is in [league] (Novig's name), or null when it is not a market Vigilant prices.
     *
     * The answer depends only on the league, the market name and whether the outcome has a number and an Over/Under, so it is remembered by those: it was worked out with six regular
     * expressions, set unions and string copies for EVERY outcome, on every push and again on every pass (Tj's 2026-10-13 file: BoltOdds' 215,000 outcomes made its refresh passes take 25-45 s on
     * the phone). Same answers, the work done once per name.
     */
    fun classify(league: String, o: BoltOutcome): BoltMarketRef? {
        val shape = (if (o.line != null) 1 else 0) + (if (o.isOver || o.isUnder) 2 else 0)
        val byName = classifyMemo.getOrPut(league) { java.util.concurrent.ConcurrentHashMap() }
        val slot = byName[o.name] ?: arrayOfNulls<Memo>(4).also { if (byName.size < MAX_MEMO) byName[o.name] = it }
        slot[shape]?.let { return it.ref }
        return classifyUncached(league, o).also { slot[shape] = Memo(it) }
    }

    /** [classify] without the memory: the definition, and what the tests hold the remembered answers to. */
    internal fun classifyUncached(league: String, o: BoltOutcome): BoltMarketRef? {
        val (period, rest) = period(o.name) ?: return null
        val base = norm(rest)
        if (base.isEmpty()) return null
        val b = withoutAlternate(base).let { if (it.endsWith("s") && it.removeSuffix("s") in (MONEYLINE + SPREAD + TOTAL + TEAM_TOTAL)) it.removeSuffix("s") else it }
        val hasLine = o.line != null
        val hasOu = o.isOver || o.isUnder
        return when {
            b in MONEYLINE -> if (!hasLine && !hasOu && period == 0) BoltMarketRef(LineKind.MONEYLINE, 0) else null
            b in SPREAD -> if (hasLine && !hasOu && period != RefBookMarket.PERIOD_FIRST_INNING) BoltMarketRef(LineKind.SPREAD, period) else null
            b in TOTAL -> if (hasLine && hasOu) BoltMarketRef(LineKind.TOTAL, period) else null
            b in TEAM_TOTAL -> if (hasLine && hasOu && period == 0) BoltMarketRef(LineKind.TEAM_TOTAL, 0) else null
            // A player prop: a stat name with an Over/Under and a number, full game only.
            hasLine && hasOu && period == 0 -> stat(league, b)?.let { BoltMarketRef(LineKind.PLAYER_PROP, 0, it) }
            else -> null
        }
    }

    /**
     * The names BoltOdds' books really use (the 2026-10-12 sample's `get_markets`, BOLTODDS_API.md §8) for a stat the OddsPapi table words differently: only names that mean exactly one Novig stat. The ambiguous
     * ones are left out on purpose ("Strikeouts" is a pitcher's or a batter's, "Bases", "Runs" and "Touchdowns" are not "Total Bases", "Earned Runs" or "Anytime Touchdown"). Every stat here is also in
     * [OpProps.ALL_TYPES], so the scan already loads Novig's market for it.
     */
    private val BOLT_FOOTBALL = mapOf(
        "passcompletions" to "PASSING_COMPLETIONS", "passingrushingyards" to "PASSING_AND_RUSHING_YARDS", "rushingreceivingyards" to "RUSHING_AND_RECEIVING_YARDS",
        "receptionyards" to "RECEIVING_YARDS", "interceptionsthrown" to "INTERCEPTIONS_THROWN", "longestpasscompletion" to "LONGEST_COMPLETION", "longestrushingattempt" to "LONGEST_RUSH",
    )
    private val BOLT_BASEBALL = mapOf("strikeoutsthrown" to "PITCHER_STRIKEOUTS", "batterstrikeouts" to "BATTING_STRIKEOUTS", "earnedrunsallowed" to "EARNED_RUNS")
    private val BOLT_BASKETBALL = mapOf("pointsassistsrebounds" to "POINTS_REBOUNDS_ASSISTS")

    private fun boltStat(league: String, key: String): String? = when (league) {
        "NFL", "NCAAF" -> BOLT_FOOTBALL[key]
        "MLB" -> BOLT_BASEBALL[key]
        "NBA", "NCAAB", "WNBA" -> BOLT_BASKETBALL[key]
        else -> null
    }

    /** Novig's stat for a prop name: "Home Runs", "Player Points", "Rebounds" (the OddsPapi table, [OpProps], is the one list of them, with [boltStat] for BoltOdds' own wording). */
    fun stat(league: String, normalized: String): String? =
        OpProps.stat(league, normalized) ?: OpProps.stat(league, normalized.removePrefix("player")) ?: OpProps.stat(league, normalized.removePrefix("playertotal"))
            ?: OpProps.stat(league, normalized + "s") ?: OpProps.stat(league, normalized.removeSuffix("s")) ?: boltStat(league, normalized) ?: boltStat(league, normalized.removePrefix("player"))

    /** Whether [name] is a market name this table can use at all, whatever the shape of the outcome (for choosing the market filter from `get_markets`). */
    fun usableName(league: String, name: String, props: Boolean): Boolean {
        val byName = usableMemo.getOrPut(league) { java.util.concurrent.ConcurrentHashMap() }
        val slot = byName[name] ?: arrayOfNulls<Boolean>(2).also { if (byName.size < MAX_MEMO) byName[name] = it }
        val i = if (props) 1 else 0
        slot[i]?.let { return it }
        return usableNameUncached(league, name, props).also { slot[i] = it }
    }

    internal fun usableNameUncached(league: String, name: String, props: Boolean): Boolean {
        val (period, rest) = period(name) ?: return false
        val base = norm(rest)
        val b = withoutAlternate(base).let { if (it.endsWith("s") && it.removeSuffix("s") in (MONEYLINE + SPREAD + TOTAL + TEAM_TOTAL)) it.removeSuffix("s") else it }
        val isLine = b in MONEYLINE || b in SPREAD || b in TOTAL || b in TEAM_TOTAL
        return if (props) !isLine && period == 0 && stat(league, b) != null else isLine
    }
}
