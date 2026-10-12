package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.reference.OpBooks
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * BoltOdds' sportsbook slugs and sport names against Vigilant's own keys (BOLTODDS_API.md §3, §8). The slugs are lower-case ("draftkings", "betmgm", "ballybet", "hardrock", "neobet") and the list is the key's
 * own `get_info`, so a slug is translated by the table OddsPapi and Unabated share ([OpBooks]: "hardrock" and "hardrockbet" are one book, a regional clone folds into its operator) and a book that table does not
 * know keeps its own letters-and-digits slug. The sport names are matched by their letters and digits against a list of aliases, so "NBA", "nba" and "NBA Preseason" are all the NBA.
 */
object BoltBooks {
    /** Vigilant's book key for a BoltOdds sportsbook [slug]; null for a book that is never the fair line (Novig, the exchanges read directly, the DFS apps). */
    fun appKey(slug: String): String? = OpBooks.appKey(slug)

    fun title(appKey: String): String = OpBooks.title(appKey)

    /** The books to price with: the reference books picked in Settings, or (null) every book BoltOdds lists that can price a fair line. */
    fun wanted(settings: ScanSettings): Set<String>? =
        if (settings.gates.boltAllBooks) null else settings.referenceBooks.mapNotNull { OpBooks.appKey(it) }.toSet() + (if (settings.opExtraBooks) OpBooks.EXTRA else emptyList())

    /** The BoltOdds slugs of [info] (`get_info`'s sportsbooks) that are one of the [wanted] books (all of them for null), in a stable order: the `sportsbooks` filter of a subscription. */
    fun slugsFor(wanted: Set<String>?, info: Collection<String>): List<String> =
        info.filter { slug -> appKey(slug)?.let { wanted == null || it in wanted } == true }.distinct().sorted()

    private val ALIASES: Map<String, List<String>> = mapOf(
        "NFL" to listOf("nfl"),
        "NCAAF" to listOf("ncaaf", "ncaafootball", "collegefootball", "ncaafbs"),
        "MLB" to listOf("mlb"),
        "NBA" to listOf("nba"),
        "NCAAB" to listOf("ncaab", "ncaabasketball", "collegebasketball", "ncaamensbasketball"),
        "WNBA" to listOf("wnba"),
        "NHL" to listOf("nhl"),
        "UFC" to listOf("ufc", "mma"),
        "Boxing" to listOf("boxing"),
    )

    /**
     * Parts of a season whose games Novig lists in the league's own market: BoltOdds names the preseason "NBA Pre" / "NFL Pre" (its `get_info`, 2026-10-12) and a "NBA Preseason" game is a NBA game too.
     * (Summer League, other competitions: not.)
     */
    private val SUFFIXES = listOf("", "pre", "preseason")

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun supports(league: League): Boolean = league.novigName in ALIASES

    /** Vigilant's league (Novig's name) for a BoltOdds sport name, or null when it is not one Vigilant prices. */
    fun leagueOfSport(sport: String): String? {
        val n = norm(sport)
        return ALIASES.entries.firstOrNull { (_, names) -> names.any { a -> SUFFIXES.any { n == a + it } } }?.key
    }

    /** The BoltOdds sport names of [league] among [available] (`get_info`'s sports); with the list unknown, the league's own name. */
    fun sportsFor(league: League, available: Collection<String>?): List<String> {
        if (!supports(league)) return emptyList()
        if (available.isNullOrEmpty()) return listOf(league.novigName)
        return available.filter { leagueOfSport(it) == league.novigName }.distinct().sorted()
    }
}
