package com.tjshea.vigilant.data.cno

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Tj's CrazyNinjaOdds view: the positive-EV page plus his filters. CNO's "Shared View → Copy Link"
 * puts every filter in the URL (`site_id=17` is Novig, `ev_min`, `odds_min`, `liq_min`,
 * `books_min`, …; RESEARCH.md §18.1), so the link alone reproduces his screen.
 */
object CnoView {
    const val PAGE = "https://crazyninjaodds.com/site/tools/positive-ev.aspx"
    const val NOVIG_SITE_ID = "17"

    /** Novig only, with CNO's own recommended minimums (3 books, 2 market sides). */
    const val DEFAULT = "$PAGE?site_id=$NOVIG_SITE_ID&books_min=3&sides_min=2"

    /**
     * The link to read for what Tj pasted: blank means [DEFAULT]; a link to another site or page
     * is null. Text around the link (a copied message) is ignored, `http` becomes `https`, and a
     * link without a book filter gets Novig's.
     */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return DEFAULT
        val found = Regex("""(?i)(https?://)?(www\.)?crazyninjaodds\.com/\S+""").find(text)?.value ?: return null
        val url = (if (found.startsWith("http", ignoreCase = true)) found else "https://$found").toHttpUrlOrNull() ?: return null
        if (!isCno(url) || !url.encodedPath.endsWith("/positive-ev.aspx", ignoreCase = true)) return null
        val builder = url.newBuilder().scheme("https")
        if (url.queryParameterValues("site_id").none { !it.isNullOrBlank() }) builder.setQueryParameter("site_id", NOVIG_SITE_ID)
        return builder.build().toString()
    }

    /** Whether the view includes live games (Novig charges takers a fee on those; pregame is free). */
    fun includesLive(link: String): Boolean = link.toHttpUrlOrNull()?.queryParameter("live") == "1"

    fun isCno(url: HttpUrl): Boolean = url.host == "crazyninjaodds.com" || url.host == "www.crazyninjaodds.com"

    private val books = mapOf(
        "17" to "Novig", "15" to "ProphetX", "31" to "Kalshi", "20" to "Pinnacle", "23" to "Circa",
        "2" to "DraftKings", "1" to "FanDuel", "4" to "BetMGM", "3" to "Caesars", "22" to "Fanatics",
        "21" to "Bet365", "11" to "BetRivers", "19" to "Fliff", "28" to "Bally Bet",
    )
    private val sports = mapOf("1" to "Baseball", "2" to "Football", "3" to "Hockey", "4" to "Basketball", "5" to "Soccer")
    private val leagues = mapOf(
        "1" to "MLB", "2" to "NFL", "3" to "NCAAF", "4" to "NHL", "5" to "NBA", "6" to "NCAAB", "7" to "WNBA",
        "8" to "NCAAW", "10" to "MLS", "16" to "Premier League",
    )

    /** "Novig · EV ≥ 2% · odds −200 to +300 · $10+ · 3+ books": the filters in a view, for Settings and the CNO tab. */
    fun describe(link: String): String {
        val url = link.toHttpUrlOrNull() ?: return link
        fun q(name: String): String? = url.queryParameter(name)?.replace("_PCT_", "%")?.trim()?.takeIf { it.isNotEmpty() }
        val parts = mutableListOf<String>()
        val ids = url.queryParameterValues("site_id").filterNotNull().filter { it.isNotBlank() && it != "0" }
        parts += if (ids.isEmpty()) "All books" else ids.joinToString(", ") { books[it] ?: "book $it" }
        q("league")?.takeIf { it != "0" }?.let { parts += leagues[it] ?: "league $it" }
            ?: q("sport")?.takeIf { it != "0" }?.let { parts += sports[it] ?: "sport $it" }
        q("ev_min")?.takeIf { it.trimEnd('%') != "0" }?.let { parts += "EV ≥ " + if (it.endsWith("%")) it else "$it%" }
        val lo = q("odds_min")
        val hi = q("odds_max")
        when {
            lo != null && hi != null -> parts += "odds $lo to $hi"
            lo != null -> parts += "odds ≥ $lo"
            hi != null -> parts += "odds ≤ $hi"
        }
        q("liq_min")?.takeIf { it.trimStart('$') != "0" }?.let { parts += (if (it.startsWith("$")) it else "$$it") + "+" }
        q("books_min")?.let { parts += "$it+ books" }
        if (q("main") == "1") parts += "main lines"
        if (q("live") == "1") parts += "live"
        q("starts_within_h")?.let { parts += "within ${it}h" }
        q("sv_title")?.let { parts.add(0, "“$it”") }
        return parts.joinToString(" · ")
    }
}
