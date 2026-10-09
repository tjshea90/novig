package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League

/**
 * OddsPapi bookmaker slugs and sport / tournament ids against Vigilant's own keys (ODDSPAPI_API.md §4, §8). Vigilant's reference books are named as The Odds API names them (`williamhill_us`,
 * `betonlineag`…); OddsPapi's slugs are not published as a list (`GET /bookmakers` is the key's own catalogue), so a slug or display name is matched here by its letters and digits alone
 * ("Hard Rock Bet" and "hardrockbet" are one book), and every book the catalogue lists is translated both ways in this file and nowhere else.
 */
object OpBooks {
    /** Normalised slug or name -> Vigilant's book key. A book not in this table keeps its own normalised slug (it can still join the fair line as an extra). */
    private val TO_APP = mapOf(
        "pinnacle" to "pinnacle", "circa" to "circa", "circasports" to "circa", "superbook" to "superbook", "bet365" to "bet365",
        "draftkings" to "draftkings", "fanduel" to "fanduel", "betmgm" to "betmgm", "betrivers" to "betrivers",
        "caesars" to "williamhill_us", "caesarssportsbook" to "williamhill_us", "williamhillus" to "williamhill_us",
        "hardrock" to "hardrockbet", "hardrockbet" to "hardrockbet", "espnbet" to "espnbet", "espn" to "espnbet", "thescorebet" to "espnbet", "fanatics" to "fanatics",
        "betonline" to "betonlineag", "betonlineag" to "betonlineag", "bovada" to "bovada", "mybookie" to "mybookieag", "mybookieag" to "mybookieag",
        "pointsbet" to "pointsbetus", "pointsbetus" to "pointsbetus", "wynnbet" to "wynnbet", "lowvig" to "lowvig", "ballybet" to "ballybet", "bally" to "ballybet",
        "betparx" to "betparx", "fliff" to "fliff", "unibet" to "unibet_us", "unibetus" to "unibet_us", "sportsbettingag" to "sportsbetting_ag", "betus" to "betus",
    )

    /**
     * Never the fair line: Novig (the thing being priced), the exchanges Vigilant reads directly (Kalshi, Polymarket), the DFS apps (their prices are not bets), and ProphetX / Sporttrade
     * (exchanges Vigilant treats on their own terms).
     */
    private val EXCLUDED = setOf(
        "novig", "novigus", "kalshi", "polymarket", "prizepicks", "underdog", "underdogfantasy", "sleeper", "parlayplay", "prophetx", "prophetexchange", "sporttrade", "dabble", "hotstreak", "primesports",
        "betfairexchange", "matchbook", "smarkets",
    )

    /** Books Vigilant's default list lacks (Circa, SuperBook, bet365): added to the fair line while OddsPapi is on and `ScanSettings.opExtraBooks`. */
    val EXTRA = listOf("circa", "superbook", "bet365")

    private val TITLES = mapOf("circa" to "Circa Sports", "superbook" to "SuperBook", "bet365" to "bet365", "pointsbetus" to "PointsBet", "unibet_us" to "Unibet")

    fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** Vigilant's book key for an OddsPapi [slug] / [name]; null for an excluded book. */
    fun appKey(slug: String, name: String? = null): String? {
        val n = norm(slug)
        val byName = name?.let { norm(it) }
        if (n in EXCLUDED || byName in EXCLUDED) return null
        return TO_APP[n] ?: byName?.let { TO_APP[it] } ?: n.takeIf { it.isNotEmpty() }
    }

    fun title(appKey: String): String = TITLES[appKey] ?: TheOddsApiClient.bookTitle(appKey)

    /** The Vigilant keys to price with: the reference books picked in Settings plus [EXTRA] when [extra]; never an excluded book. */
    fun wanted(referenceBooks: List<String>, extra: Boolean): Set<String> =
        (referenceBooks + (if (extra) EXTRA else emptyList())).filter { norm(it) !in EXCLUDED }.toSet()

    /**
     * The slugs of [catalog] (the key's `/bookmakers`) that are one of the [wanted] books, in a stable order, for the `bookmakers` filter of an odds request. Empty when the catalogue is unknown or
     * names none of them: the caller then asks with no filter and keeps only the [wanted] books itself, so a wrong guess about a slug can only cost reply size, never a book.
     */
    fun slugsFor(wanted: Set<String>, catalog: List<OpBookInfo>): List<String> =
        catalog.filter { b -> appKey(b.slug, b.name)?.let { it in wanted } == true }.map { it.slug }.distinct().sorted()

    /** OddsPapi's sportId for a Vigilant league, or null when OddsPapi is not asked for it (tennis, soccer, MMA: the other feeds keep those). */
    fun sportId(league: League): Int? = SPORTS[league.novigName]

    private val SPORTS = mapOf("NFL" to 14, "NCAAF" to 14, "NBA" to 11, "NCAAB" to 11, "WNBA" to 11, "MLB" to 13, "NHL" to 15)

    fun supports(league: League): Boolean = sportId(league) != null

    /** The tournament names OddsPapi may list a league under, best first (compared by [norm]); the first exact hit wins. */
    private val TOURNAMENT_NAMES = mapOf(
        "NFL" to listOf("nfl"),
        "NCAAF" to listOf("ncaaf", "ncaafootball", "ncaa", "collegefootball", "ncaafbs", "fbs"),
        "NBA" to listOf("nba"),
        "NCAAB" to listOf("ncaab", "ncaabasketball", "ncaa", "collegebasketball", "ncaamen", "ncaamens"),
        "WNBA" to listOf("wnba"),
        "MLB" to listOf("mlb"),
        "NHL" to listOf("nhl"),
    )

    private val NOT_THE_LEAGUE = Regex("women|wnba|\\(w\\)|\\bnit\\b|\\bcbi\\b|g league|gleague|preseason|pre-season|summer|exhibition|all-star|allstar|futures|special|\\bcup\\b|division ii|\\bd2\\b|\\bd3\\b|\\bfcs\\b")

    /**
     * The tournament of [tournaments] (a sport's `/tournaments`) that is [league]: category USA (or none), the name exactly one of the league's names, then the first name that merely contains one.
     * Null when none is a fit, so the league is skipped instead of reading a wrong competition.
     */
    fun tournamentFor(league: League, tournaments: List<OpTournament>): OpTournament? {
        val names = TOURNAMENT_NAMES[league.novigName] ?: return null
        val usa = tournaments.filter { it.category.isBlank() || norm(it.category) in setOf("usa", "us", "unitedstates", "northamerica", "usacanada") }
        val pool = usa.ifEmpty { tournaments }
        for (n in names) pool.firstOrNull { norm(it.name) == n || norm(it.slug) == n }?.let { return it }
        return pool.firstOrNull { t -> !NOT_THE_LEAGUE.containsMatchIn(t.name.lowercase()) && names.any { n -> norm(t.name).startsWith(n) } }
    }
}

/**
 * A feed that rests for every league OddsPapi carries (Tj, 2026-10-09: "turn redundant APIs off to save usage"). It is asked only for the leagues OddsPapi has no feed for (tennis, MMA, soccer…), so
 * nothing it sells is bought twice. Free feeds and Pinnodds are never wrapped.
 */
class OutsideOp(private val inner: ReferenceSource) : ReferenceSource by inner {
    override fun supports(league: League): Boolean = inner.supports(league) && !OpBooks.supports(league)
}
