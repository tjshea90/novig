package com.tjshea.vigilant.data.novig

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parsing for the display strings Novig's catalog uses, as observed live on 2026-09-25
 * (NOVIG_API.md §5 quirks). Novig says descriptions are display text, but they're the only place
 * team names and lines appear, so every parser here returns null rather than guessing.
 *
 *  - event:  "Baltimore Ravens @ Dallas Cowboys", "Alabama @ Mississippi State"
 *  - MONEY:  outcomes "DAL" / "BAL", or "L. Hernandez" / "S. Dumas"
 *  - SPREAD: outcomes "BOS -3.5" / "CHC +3.5", "Coventry City +1.5" / "Newcastle -1.5"
 *  - TOTAL:  outcomes "Over 10.5" / "Under 10.5"
 *  - 3-way:  description "Newcastle MONEYLINE_3_WAY_WIN", outcomes "Yes" / "No"
 */
object NovigText {

    private val SPREAD = Regex("""^(.*\S)\s+([+-]\d+(?:\.\d+)?)$""")
    private val TOTAL = Regex("""^(over|under)\s+(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)

    fun parseMatchup(description: String): Matchup? {
        val i = description.indexOf(" @ ")
        if (i <= 0) return null
        val away = description.substring(0, i).trim()
        val home = description.substring(i + 3).trim()
        return if (away.isEmpty() || home.isEmpty()) null else Matchup(away, home)
    }

    /** "DAL +20.5" -> ("DAL", 20.5). */
    fun parseSpreadOutcome(name: String): Pair<String, Double>? {
        val m = SPREAD.matchEntire(name.trim()) ?: return null
        return m.groupValues[1] to (m.groupValues[2].toDoubleOrNull() ?: return null)
    }

    /** "Over 47.5" -> (true, 47.5); "Under 47.5" -> (false, 47.5). */
    fun parseTotalOutcome(name: String): Pair<Boolean, Double>? {
        val m = TOTAL.matchEntire(name.trim()) ?: return null
        val over = m.groupValues[1].equals("over", ignoreCase = true)
        return over to (m.groupValues[2].toDoubleOrNull() ?: return null)
    }

    /** "Newcastle MONEYLINE_3_WAY_WIN" -> "Newcastle". */
    fun threeWayTeam(description: String): String? {
        val suffix = " MONEYLINE_3_WAY_WIN"
        return if (description.endsWith(suffix)) description.removeSuffix(suffix).trim().ifEmpty { null } else null
    }

    /**
     * A price string ("0.38", "0.615", "0.380") in thousandths. Parsed as a decimal, never a
     * float: Novig sends prices unpadded in books and padded in trades (NOVIG_API.md §5).
     */
    fun priceMilli(price: String): Int? = runCatching {
        BigDecimal(price.trim()).movePointRight(3).setScale(0, RoundingMode.HALF_UP).intValueExact()
    }.getOrNull()?.takeIf { it in 1..999 }
}
