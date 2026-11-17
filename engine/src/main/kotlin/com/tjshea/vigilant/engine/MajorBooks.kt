package com.tjshea.vigilant.engine

/**
 * Well-known books (Tj, 2026-10-31, chat: "at least one or two big name books like draft kings, betmgm, fanduel"): a fair line built only from small soft books nobody has heard of is not trusted.
 * Counted by COMPANY ([BookCompany]), so FanDuel and its sister sites are one. Used by the engine's trader (2+) and the bid desk (1+).
 */
object MajorBooks {
    private val COMPANIES = setOf("flutter", "draftkings", "caesars", "entain", "bet365", "fanatics", "penn", "hardrock", "pinnacle", "circa", "bookmaker")

    const val MIN_FOR_BETS = 2
    const val MIN_FOR_BIDS = 1

    fun isMajor(key: String): Boolean = BookCompany.of(key) in COMPANIES

    /** How many different major companies are among [books] (keys or titles). */
    fun count(books: Collection<String>): Int = books.map { BookCompany.of(it) }.filter { it in COMPANIES }.distinct().size

    fun reason(count: Int, need: Int): String? =
        if (count >= need) null else "needs $need well-known book${if (need == 1) "" else "s"} (DraftKings, FanDuel, BetMGM, Caesars, bet365, Pinnacle ...) in its fair odds; only $count"
}
