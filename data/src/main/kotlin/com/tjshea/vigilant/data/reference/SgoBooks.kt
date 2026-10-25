package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League

/**
 * SportsGameOdds (SGO) bookmaker ids and league ids against Vigilant's own keys (SPORTSGAMEODDS_API.md §5). Vigilant's reference books are named as The Odds API names them
 * (`williamhill_us`, `betonlineag`…), so every SGO id is translated both ways here and nowhere else.
 */
object SgoBooks {
    /** SGO id -> Vigilant's book key, where they differ. */
    // Measured on Tj\'s Pro key (2026-10-09): SGO\'s "caesars" is the US Caesars Sportsbook, which Vigilant\'s keys (The Odds API\'s) call "williamhill_us"; SGO\'s "williamhill" is the UK William Hill, a
    // different book. SGO\'s "hardrockbet", "espnbet" and "fanatics" are already Vigilant\'s keys.
    private val TO_APP = mapOf(
        "caesars" to "williamhill_us",
        "betonline" to "betonlineag",
        "mybookie" to "mybookieag",
        // PX9 (Tj 2026-10-25: "matched correctly"): the same books under the keys PropLine, ParlayAPI and the sharp-book list use, so SGO's Bookmaker.eu is a sharp book and Betfair Exchange and Marathon
        // are not counted a second time beside PropLine's.
        "bookmakereu" to "bookmaker",
        "betfairexchange" to "betfair_ex_eu",
        "marathonbet" to "marathon",
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

    /** Every bookmakerID SGO's docs list (data-types/bookmakers, checked 2026-10-09): the only ones sent in a `bookmakerID` filter, so a book SGO does not carry (Hard Rock) can never turn a request into a 400. */
    val KNOWN = setOf(
        "1xbet", "888sport", "ballybet", "barstool", "bet365", "betanysports", "betclic", "betfairexchange", "betfairsportsbook", "betfred", "betmgm", "betonline", "betparx", "betrivers", "betrsportsbook",
        "betsafe", "betsson", "betus", "betvictor", "betway", "bluebet", "bodog", "bookmakereu", "boombet", "bovada", "boylesports", "caesars", "casumo", "circa", "coolbet", "coral", "draftkings", "espnbet",
        "everygame", "fanatics", "fanduel", "fliff", "fourwinds", "foxbet", "grosvenor", "gtbets", "hardrockbet", "hotstreak", "kalshi", "ladbrokes", "leovegas", "livescorebet", "lowvig", "marathonbet", "matchbook",
        "mrgreen", "mybookie", "neds", "nordicbet", "northstarbets", "novig", "paddypower", "parlayplay", "pinnacle", "playup", "pointsbet", "polymarket", "primesports", "prizepicks", "prophetexchange", "si",
        "skybet", "sleeper", "sportsbet", "sportsbetting_ag", "sporttrade", "stake", "sugarhouse", "superbook", "suprabets", "tab", "tabtouch", "thescorebet", "tipico", "topsport", "underdog", "unibet",
        "virginbet", "williamhill", "windcreek", "wynnbet",
    )

    /** Every book SGO lists that can price a fair line (everything but [EXCLUDED]): what [com.tjshea.vigilant.data.scanner.ApiGates.sgoAllBooks] means. */
    fun everyBook(): Set<String> = KNOWN.filter { it !in EXCLUDED }.toSet()

    /** The `bookmakerID` filter for a request: [wanted] books SGO lists, sorted; empty = no filter (ask for every book). */
    fun filter(wanted: Set<String>): String = wanted.filter { it in KNOWN }.sorted().joinToString(",")

    /** SGO's leagueID for a Vigilant league, or null when SGO has no usable feed for it here (tennis, soccer: other feeds keep those). */
    fun leagueId(league: League): String? = LEAGUES[league.novigName]

    private val LEAGUES = mapOf(
        "NFL" to "NFL", "NCAAF" to "NCAAF", "MLB" to "MLB", "NHL" to "NHL", "NBA" to "NBA", "NCAAB" to "NCAAB", "WNBA" to "WNBA",
    )

    fun supports(league: League): Boolean = leagueId(league) != null

    /**
     * SGO's tennis leagues (Tj's Pro key, 2026-10-11: `/leagues?sportID=TENNIS` lists ATP and WTA, `teamType` SINGLE_PLAYER). Kept apart from [leagueId] on purpose: the closes and the grader still read tennis
     * from the other feeds, and no other API rests for it (Tj, 2026-10-11), so only the game-line read ([SgoGamesSource]) asks for these.
     */
    fun tennisLeagueId(league: League): String? = if (league.tennis) league.novigName.takeIf { it == "ATP" || it == "WTA" } else null
}

