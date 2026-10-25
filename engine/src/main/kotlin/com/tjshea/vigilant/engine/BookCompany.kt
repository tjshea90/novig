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
        group("entain", "ladbrokes", "coral", "neds", "sportingbet", "bwin", "gamebookers", "gala", "eurobet", "sportsinteraction", "si", "partysports", "party", "betcity", "crystalbet", "supersport", "betmgm", "mgm", "betmgmuk", "borgata", "borgataonline", "borgatacasino")
        // Kambi's one trading desk (Tj, 2026-10-11, six "agreeing" books with identical odds: Bally Bet, betPARX, Casumo, Grosvenor, TABtouch, Unibet): these are DIFFERENT owners (Bally's, Greenwood/Parx, Casumo, Rank, RWWA, Kindred) who all
        // run Kambi's sportsbook, whose traders compile one line for them all, so they quote one line and are one opinion, not many (RESEARCH.md §46, §134). Sources: Kambi's and the operators' own announcements.
        // Rush Street's BetRivers/SugarHouse/RushBet, Greenwood's betPARX/Parx, Bally's Bally Bet, Kindred's Unibet/Maria/32Red, Casumo, Rank's Grosvenor/Enracha/YoSports, RWWA's TABtouch, LiveScore Bet / Virgin Bet,
        // LeoVegas' BetUK / Expekt, Svenska Spel, ATG, BetPlay.
        group(
            "kambi", "betrivers", "sugarhouse", "playsugarhouse", "rivers", "rushbet", "betparx", "parx", "ballybet", "bally", "unibet", "unibetus", "unibeteu", "unibetuk", "unibetau", "unibetnl", "maria", "mariacasino", "32red",
            "casumo", "grosvenor", "grosvenorsport", "grosvenorcasinos", "enracha", "yosports", "tabtouch", "rwwa", "livescorebet", "livescore", "virginbet", "betuk", "expekt", "leovegas", "svenskaspel", "atg", "betplay",
            // Odds Feed+ clients take Kambi's traded odds as their own line (Kambi, 2025): Coolbet (and Kindred/FDJ's Unibet, above). Superbet was here too, on Kambi's announcement; measured on PropLine's US-league
            // lines (2026-10-25, tools/propline_books.py, 900+ shared lines each) Superbet PL and RO track each other (83%) and Napoleon (80%) and no Kambi book above 25%, so it is its own desk (below).
            "coolbet",
        )
        // Superbet's own desk: Superbet PL and RO and Napoleon (Superbet Group's Belgian brand) quote the same no-vig chance within 0.2 points on 80-83% of 550-920 shared lines.
        group("superbet", "superbet", "napoleon", "napoleonsports", "napoleongames")
        // Fortuna Entertainment Group's Czech and Polish books (71% of 498 shared lines, one operator).
        group("fortuna", "fortuna", "fortunacz", "fortunapl", "fortunask", "fortunahr", "fortunaro")
        group("penn", "espnbet", "espn", "barstool", "barstoolsportsbook", "thescore", "thescorebet", "scorebet")
        group("fanatics", "fanatics", "fanaticssportsbook", "pointsbet", "pointsbetus")
        group("hardrock", "hardrock", "hardrockbet", "hr")
        group("bet365", "bet365")
        // Bodog became Ozoon in Canada on 2026-02-16 with the same sportsbook and the same accounts (Covers, LCB), and Bovada is the Bodog brand for the US (Tj, 2026-10-22: "treat bovada and ozoon as a single sports book").
        group("bodog", "bovada", "bodog", "ozoon", "ozooncanada", "bodog88", "bodogeu")
        group("digitalgaming", "betonlineag", "betonline", "lowvig", "sportsbettingag", "sportsbetting")
        group("betsson", "betsson", "betsafe", "nordicbet")
        group("windcreek", "windcreek", "windcreekbet")
        group("supergroup", "betway")
        group("sporttrade", "sporttrade", "st")
        // The same book under two feeds' keys (PX9, Tj 2026-10-25: "matched correctly"): SGO's "bookmakereu" and "marathonbet" are PropLine's / the app's "bookmaker" and "marathon", and counted as two companies.
        group("bookmaker", "bookmaker", "bookmakereu", "bookmakereuropean")
        group("marathon", "marathon", "marathonbet")
        group("pinnacle", "pinnacle", "pinnaclesports", "pn")
        group("circa", "circa", "circasports", "cs")
        // 1xBet: PropLine/SharpAPI send it as "onexbet", SGO and others as "1xbet" (Tj, 2026-10-24, a screenshot: "1xBet" and "1xbet" counted as 2 of 2 books agreeing).
        group("1xbet", "1xbet", "onexbet", "1xbetcom")
        // Kaizen Gaming trades Betano and Stoiximan (its Greek/Cypriot brand) on one desk. Placard = Solverde and Marca Apuestas = Wplay quoted identical prices on 95% of 736 and 88% of 330 shared
        // game-line quotes on PropLine (PS2, 2026-10-25, research/propline_coverage_2026-10-25.md: the measured 80%-of-100+ rule).
        group("kaizen", "betano", "stoiximan")
        group("placard", "placard", "solverde")
        group("marcaapuestas", "marcaapuestas", "wplay")
    }

    /** One exchange under the keys the feeds give it: PropLine's "betfair_ex" (the app's "betfair_ex_eu"), SGO's "betfairexchange", The Odds API's "betfair_ex_eu": one company, not two. */
    private val EXCHANGE_ALIAS: Map<String, String> = mapOf("betfairexeu" to "betfairexchange", "betfairex" to "betfairexchange", "prophetexchange" to "prophetx")

    /** Companies whose members are exchanges, so a book of the same group is not folded into them. */
    private val EXCHANGES = setOf("betfairexchange", "betfairexeu", "betfairex", "matchbook", "smarkets", "kalshi", "polymarket", "sxbet", "novig", "prophetx", "prophetexchange")

    private val TAIL = Regex("[_.\\-]([a-z]{2})$")

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** The company of the book [key] (a feed's key or a book's title): the same string for books of one operator, the key itself for a book that has none. */
    fun of(key: String): String {
        val low = key.trim().lowercase()
        val whole = norm(low)
        if (whole in EXCHANGES) return EXCHANGE_ALIAS[whole] ?: whole
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
