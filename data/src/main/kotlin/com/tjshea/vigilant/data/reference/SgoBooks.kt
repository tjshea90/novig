package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League

/**
 * SportsGameOdds (SGO) bookmaker ids and league ids against Vigilant's own keys (SPORTSGAMEODDS_API.md §5). Vigilant's reference books are named as The Odds API names them
 * (`williamhill_us`, `betonlineag`…), so every SGO id is translated both ways here and nowhere else.
 */
object SgoBooks {
    /** SGO id -> Vigilant's book key, where they differ. */
    private val TO_APP = mapOf(
        "williamhill" to "williamhill_us",
        "betonline" to "betonlineag",
        "hardrock" to "hardrockbet",
        "mybookie" to "mybookieag",
    )
    private val FROM_APP = TO_APP.entries.associate { (k, v) -> v to k }

    /**
     * Never the fair line: Novig (the thing being priced), the exchanges Vigilant reads directly (Kalshi, Polymarket), the DFS apps (their prices are not bets), SGO's own unattributable
     * "unknown" rows, and ProphetX (an exchange Vigilant treats on its own terms).
     */
    val EXCLUDED = setOf("novig", "kalshi", "polymarket", "prizepicks", "underdog", "sleeper", "parlayplay", "unknown", "prophetexchange", "dabble", "hotstreak", "primesports")

    /**
     * Books SGO's Pro plan carries that Vigilant's default list does not (Tj, 2026-10-09: "extra sports books"): Circa and SuperBook (Nevada sharp shops), bet365. Added to the
     * fair line while SGO Pro is on (`ScanSettings.sgoExtraBooks`); each still has to price both sides and be fresh, like every book.
     */
    val EXTRA = listOf("circa", "superbook", "bet365")

    private val TITLES = mapOf("circa" to "Circa Sports", "superbook" to "SuperBook", "bet365" to "bet365", "sporttrade" to "Sporttrade")

    fun appKey(sgoId: String): String = TO_APP[sgoId] ?: sgoId
    fun sgoId(appKey: String): String = FROM_APP[appKey] ?: appKey

    fun title(appKey: String): String = TITLES[appKey] ?: TheOddsApiClient.bookTitle(appKey)

    /** The SGO ids to ask for / keep, for the reference books picked in Settings plus [EXTRA] when [extra]. Never an excluded book. */
    fun wanted(referenceBooks: List<String>, extra: Boolean): Set<String> =
        (referenceBooks.map { sgoId(it) } + (if (extra) EXTRA else emptyList())).filter { it !in EXCLUDED }.toSet()

    /** SGO's leagueID for a Vigilant league, or null when SGO has no usable feed for it here (tennis, soccer: other feeds keep those). */
    fun leagueId(league: League): String? = LEAGUES[league.novigName]

    private val LEAGUES = mapOf(
        "NFL" to "NFL", "NCAAF" to "NCAAF", "MLB" to "MLB", "NHL" to "NHL", "NBA" to "NBA", "NCAAB" to "NCAAB", "WNBA" to "WNBA",
    )

    fun supports(league: League): Boolean = leagueId(league) != null
}

/**
 * A feed that rests for every league SportsGameOdds carries (Tj, 2026-10-09: "redundant apis that do the same thing as sportsgamesodds pro should be turned off to save their usage"). It is
 * asked only for the leagues SGO has no feed for (tennis), so nothing it sells is bought twice.
 */
class OutsideSgo(private val inner: ReferenceSource) : ReferenceSource by inner {
    override fun supports(league: League): Boolean = inner.supports(league) && !SgoBooks.supports(league)
}
