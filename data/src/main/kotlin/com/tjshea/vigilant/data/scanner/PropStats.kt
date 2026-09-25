package com.tjshea.vigilant.data.scanner

/**
 * Player props Vigilant can price: Novig's stat names, their display names, and the Kalshi series
 * that quote the same stat (RESEARCH.md §13). Only props with a fair-odds source are listed; a
 * prop nothing else quotes can't be +EV-checked, so it isn't fetched.
 */
object PropStats {
    val KALSHI_SERIES: Map<String, String> = mapOf(
        // NFL
        "KXNFLPASSYDS" to "PASSING_YARDS",
        "KXNFLRSHYDS" to "RUSHING_YARDS",
        "KXNFLREC" to "RECEPTIONS",
        "KXNFLRECYDS" to "RECEIVING_YARDS",
        "KXNFLRRYDS" to "RUSHING_AND_RECEIVING_YARDS",
        "KXNFLTD" to "TOUCHDOWNS",
        "KXNFLPASSTDS" to "PASSING_TOUCHDOWNS",
        "KXNFLPASSATT" to "PASSING_ATTEMPTS",
        "KXNFLRSHATT" to "RUSHING_ATTEMPTS",
        "KXNFLPASSINT" to "INTERCEPTIONS_THROWN",
        "KXNFLLONGREC" to "LONGEST_RECEPTION",
        "KXNFLLONGRSH" to "LONGEST_RUSH",
        // MLB
        "KXMLBKS" to "PITCHER_STRIKEOUTS",
        "KXMLBTB" to "TOTAL_BASES",
        "KXMLBHIT" to "HITS",
        "KXMLBHR" to "HOME_RUNS",
        "KXMLBHRR" to "HITS_RUNS_RBIS",
        "KXMLBRBI" to "RBIS",
        "KXMLBSB" to "STOLEN_BASES",
        "KXMLBHA" to "HITS_ALLOWED",
        // WNBA
        "KXWNBAPTS" to "POINTS",
        "KXWNBAREB" to "REBOUNDS",
        "KXWNBAAST" to "ASSISTS",
        "KXWNBA3PT" to "THREE_POINTERS_MADE",
    )

    /** Novig market types fetched for the "Player props" family. */
    val NOVIG_TYPES: List<String> = KALSHI_SERIES.values.distinct()

    fun displayName(novigType: String): String =
        novigType.lowercase().split('_').joinToString(" ") { w ->
            when (w) {
                "rbis" -> "RBIs"
                "and" -> "+"
                else -> w.replaceFirstChar { it.uppercase() }
            }
        }
}
