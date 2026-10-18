package com.tjshea.vigilant.engine

/**
 * The company behind a book (Tj, 2026-10-11: "consolidate same company books and do not count them for their odds more than once. SGO carries many books, but a lot of them are the same
 * parent company with identical odds"). One company prices from one trading desk, so its sites are one opinion, not several: the fair line is built from one book a company
 * ([FairValue.compute]), and the lists show one of them.
 *
 * Works on any feed's book key ("williamhill_us", "hardrockbet", "fanduel_yourway", "bet365.de", "HR-FL"): the key is cut to its letters and digits, looked up in [PARENT], and failing
 * that a trailing state or country tag ("_nj", "-fl", ".uk") is cut off and the rest looked up. A book the table does not know is its own company, so an unknown key is never merged
 * with anything by mistake. Exchanges are never merged with a sportsbook of the same group (a Betfair exchange price is not Paddy Power's).
 */
object BookCompany {

    /** Normalised book key -> company. Only books of one operator that quote from one desk are here; a joint venture that quotes its partner's line (BetMGM and Entain) is one desk. */
    private val PARENT: Map<String, String> = buildMap {
        fun group(company: String, vararg keys: String) = keys.forEach { put(it, company) }
        group("flutter", "fanduel", "fanduelyourway", "fdyw", "fd", "paddypower", "betfairsportsbook", "sportsbet", "skybet", "foxbet", "tombola", "sisal")
        group("draftkings", "draftkings", "dk", "dkpick6")
        group("caesars", "williamhillus", "caesars", "caesarssportsbook", "czr", "williamhillnevada")
        group("evoke", "williamhill", "888sport", "888", "mrgreen", "williamhilluk")
        // Entain's one trading desk (Tj, 2026-10-12, a screenshot: Dalton Kincaid Over 56.5, bwin / partysports / BetMGM / sportsinteraction all -120 / -111 with the same hold, "4 of 4 books agree"):
        // Sports Interaction (Canada; Entain bought its owner Avid Gaming in 2022), Party Sports (Entain's Party brands, launched in Ontario with bwin in April 2022) and bwin are Entain's own; BetMGM is
        // Entain's joint venture with MGM Resorts and quoted the same line. Sources: research/book_company_2026-10-12.md.
        group("entain", "ladbrokes", "coral", "neds", "sportingbet", "bwin", "gamebookers", "gala", "eurobet", "sportsinteraction", "si", "partysports", "party", "betcity", "crystalbet", "supersport", "betmgm", "mgm", "betmgmuk")
        // Kambi's one trading desk (Tj, 2026-10-11, six "agreeing" books with identical odds: Bally Bet, betPARX, Casumo, Grosvenor, TABtouch, Unibet): these are DIFFERENT owners (Bally's, Greenwood/Parx, Casumo, Rank, RWWA, Kindred) who all
        // run Kambi's sportsbook, whose traders compile one line for them all, so they quote one line and are one opinion, not many (RESEARCH.md §46, §134). Sources: Kambi's and the operators' own announcements.
        // Rush Street's BetRivers/SugarHouse/RushBet, Greenwood's betPARX/Parx, Bally's Bally Bet, Kindred's Unibet/Maria/32Red, Casumo, Rank's Grosvenor/Enracha/YoSports, RWWA's TABtouch, LiveScore Bet / Virgin Bet,
        // LeoVegas' BetUK / Expekt, Svenska Spel, ATG, BetPlay.
        group(
            "kambi", "betrivers", "sugarhouse", "playsugarhouse", "rivers", "rushbet", "betparx", "parx", "ballybet", "bally", "unibet", "unibetus", "unibeteu", "unibetuk", "unibetau", "unibetnl", "maria", "mariacasino", "32red",
            "casumo", "grosvenor", "grosvenorsport", "grosvenorcasinos", "enracha", "yosports", "tabtouch", "rwwa", "livescorebet", "livescore", "virginbet", "betuk", "expekt", "leovegas", "svenskaspel", "atg", "betplay",
            // Odds Feed+ clients take Kambi's traded odds as their own line (Kambi, 2025): Coolbet, Superbet (and Kindred/FDJ's Unibet, above).
            "coolbet", "superbet",
        )
        group("penn", "espnbet", "espn", "barstool", "barstoolsportsbook", "thescore", "thescorebet", "scorebet")
        group("fanatics", "fanatics", "fanaticssportsbook", "pointsbet", "pointsbetus")
        group("hardrock", "hardrock", "hardrockbet", "hr")
        group("bet365", "bet365")
        group("bodog", "bovada", "bodog")
        group("digitalgaming", "betonlineag", "betonline", "lowvig", "sportsbettingag", "sportsbetting")
        group("betsson", "betsson", "betsafe", "nordicbet")
        group("windcreek", "windcreek", "windcreekbet")
        group("supergroup", "betway")
        group("sporttrade", "sporttrade", "st")
        group("pinnacle", "pinnacle", "pinnaclesports", "pn")
        group("circa", "circa", "circasports", "cs")
    }

    /** Companies whose members are exchanges, so a book of the same group is not folded into them. */
    private val EXCHANGES = setOf("betfairexchange", "betfairexeu", "betfairex", "matchbook", "smarkets", "kalshi", "polymarket", "sxbet", "novig", "prophetx", "prophetexchange")

    private val TAIL = Regex("[_.\\-]([a-z]{2})$")

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** The company of the book [key] (a feed's key or a book's title): the same string for books of one operator, the key itself for a book that has none. */
    fun of(key: String): String {
        val low = key.trim().lowercase()
        val whole = norm(low)
        if (whole in EXCHANGES) return whole
        PARENT[whole]?.let { return it }
        // "HR-FL", "bet365.de", "fanduel_nj": drop the state or country tag and try again.
        TAIL.find(low)?.let { m ->
            val base = norm(low.substring(0, m.range.first))
            if (base.isNotEmpty() && base !in EXCHANGES) PARENT[base]?.let { return it }
        }
        // A title: "Hard Rock Bet", "BetMGM", "Caesars Sportsbook".
        val words = norm(low.replace(Regex("\\(.*?\\)"), ""))
        PARENT[words]?.let { return it }
        return whole.ifEmpty { low }
    }

    /** Whether [a] and [b] are books of one company. */
    fun same(a: String, b: String): Boolean = of(a) == of(b)

    /**
     * One devigged book a company: of a company's books the sharp one first (a sharp flag is the user's own choice), then the one read most recently, then the one with the least
     * hold (its odds are the closest to the true line), then by key so the choice never flips between scans. Books of different companies are all kept, in their order.
     */
    fun onePerCompany(books: List<BookFair>): List<BookFair> {
        if (books.size < 2) return books
        val pick = compareByDescending<BookFair> { it.isSharp }
            .thenByDescending { it.book.lastUpdateMs ?: Long.MIN_VALUE }
            .thenBy { it.hold }
            .thenBy { it.book.bookKey }
        val chosen = books.groupBy { of(it.book.bookKey) }.mapValues { (_, group) -> group.minWithOrNull(pick)!! }
        return books.filter { chosen[of(it.book.bookKey)] === it }
    }
}
