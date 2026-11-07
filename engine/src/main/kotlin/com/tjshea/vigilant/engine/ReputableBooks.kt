package com.tjshea.vigilant.engine

/**
 * Reputable books only (Tj, 2026-11-07, BK1, verbatim: "remove and never show or use in the future any book which is very small and soft. only include reputable books in the scanning").
 * A book on [BANNED] never reaches a fair line ([FairValue.compute]) or the book lists: they are sweepstakes / social books (Fliff, Sportzino, Thrillzz), tiny offshore or crypto shops
 * (BetUS, MyBookie, 1xBet, Rebet), and small regional or African/Latin-American/Asian books with no documented sharpness whose lines the 2026-10-25 study (propline_books_2026-10-25.md) only
 * added for coverage. Kept: the big US books, Pinnacle/Circa/Bookmaker/BetOnline/Bovada, exchanges, state lotteries and major licensed European/Australian operators.
 * Research and the revert rule: research/book_reputation_2026-11-07.md. Add a book here only with a reason in that file.
 */
object ReputableBooks {
    private val BANNED: Set<String> = setOf(
        // sweepstakes / social
        "fliff", "sportzino", "thrillzz", "rebet", "strendus",
        // tiny offshore / crypto
        "betus", "mybookieag", "mybookie", "onexbet", "1xbet", "bet25", "tonybet", "bet99", "courtside", "jazzsports", "gtbets", "everygame", "fourwinds", "hotstreak", "lvbet",
        // small regional (Africa, LatAm, Asia, small AU/EU) with no sharpness record
        "betika", "sportybetng", "sportybet", "starsports", "soccerbet", "netbetro", "psk", "totalbet", "playup", "betrau", "synot", "sportium",
        "bet9ja", "supabets", "wplay", "meridianbet", "codere", "palmerbet", "teapuesto", "placard", "solverde", "olybet", "jouezsport", "pmu",
    )

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Whether the book [key] (or its display [title]) is on the banned list, tolerant of state/country tags ("sportybet_ng", "bet365.de"). */
    fun isBanned(key: String, title: String = ""): Boolean {
        val k = norm(key)
        if (k in BANNED) return true
        Regex("[_.\\-][a-z]{2}$").find(key.trim().lowercase())?.let { m -> if (norm(key.trim().lowercase().substring(0, m.range.first)) in BANNED) return true }
        return title.isNotEmpty() && norm(title) in BANNED
    }

    fun banned(): Set<String> = BANNED
}
