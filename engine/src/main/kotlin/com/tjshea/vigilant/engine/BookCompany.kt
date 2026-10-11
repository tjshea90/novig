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

    /** Normalised book key -> company. Only books of one operator that quote from one desk are here; a joint venture (BetMGM) is its own company. */
    private val PARENT: Map<String, String> = buildMap {
        fun group(company: String, vararg keys: String) = keys.forEach { put(it, company) }
        group("flutter", "fanduel", "fanduelyourway", "fdyw", "fd", "paddypower", "betfairsportsbook", "sportsbet", "skybet", "foxbet", "tombola", "sisal")
        group("draftkings", "draftkings", "dk", "dkpick6")
        group("mgm", "betmgm", "mgm", "betmgmuk")
        group("caesars", "williamhillus", "caesars", "caesarssportsbook", "czr", "williamhillnevada")
        group("evoke", "williamhill", "888sport", "888", "williamhilluk")
        group("entain", "ladbrokes", "coral", "neds", "sportingbet", "bwin", "gamebookers", "gala")
        group("rushstreet", "betrivers", "sugarhouse", "playsugarhouse", "rivers")
        group("penn", "espnbet", "espn", "barstool", "barstoolsportsbook", "thescore", "thescorebet", "scorebet")
        group("fanatics", "fanatics", "fanaticssportsbook", "pointsbet", "pointsbetus", "pointsbetau")
        group("hardrock", "hardrock", "hardrockbet", "hr")
        group("bet365", "bet365")
        group("bodog", "bovada", "bodog", "bookmakereu")
        group("digitalgaming", "betonlineag", "betonline", "lowvig", "sportsbettingag", "sportsbetting")
        group("betsson", "betsson", "betsafe", "nordicbet")
        group("kindred", "unibet", "unibetus", "unibeteu", "unibetuk", "unibetau", "unibetnl", "maria", "mariacasino", "32red")
        group("windcreek", "windcreek", "windcreekbet", "wynnbet")
        group("bally", "ballybet", "bally")
        group("sporttrade", "sporttrade", "st")
        group("pinnacle", "pinnacle", "pinnaclesports", "pn")
        group("circa", "circa", "circasports", "cs")
    }

    /** Companies whose members are exchanges, so a book of the same group is not folded into them. */
    private val EXCHANGES = setOf("betfairexchange", "betfairexeu", "betfairex", "matchbook", "smarkets", "kalshi", "polymarket", "novig", "prophetx", "prophetexchange")

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
}
