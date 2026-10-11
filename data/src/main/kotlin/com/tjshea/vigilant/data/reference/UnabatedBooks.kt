package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League

/**
 * Unabated's sportsbooks and leagues against Vigilant's own keys (UNABATED_API.md §3). Sources come from `GET /market/{league}/{type}/sources` by NAME (their ids are discovered, never hard-coded: the
 * docs say not to), so the table is by letters-and-digits of the name ("theScoreBet" and "The Score Bet" are one book) and shares OddsPapi's translation (OpBooks) for the common books.
 */
object UnabatedBooks {
    /**
     * Never the fair line: Novig (the thing priced), Kalshi / Polymarket (read directly), the DFS apps (PrizePicks, DraftKings Pick6, Underdog and its prediction market: not bets), ProphetX (an exchange
     * Vigilant treats on its own terms, as SGO's).
     */
    private val EXCLUDED_PARTS = listOf("novig", "kalshi", "polymarket", "prizepicks", "pick6", "underdog", "prophetx", "prophetexchange", "sleeper", "parlayplay")

    /** Vigilant's book key for an Unabated source [name]; null for an excluded book. */
    fun appKey(name: String): String? {
        val n = OpBooks.norm(name)
        if (n.isEmpty() || EXCLUDED_PARTS.any { n.contains(it) }) return null
        return when (n) {
            "mgm", "betmgm", "mgmsportsbook" -> "betmgm"
            "thescorebet", "thescore", "scorebet" -> "espnbet"
            else -> OpBooks.appKey(n, name)
        }
    }

    fun title(appKey: String): String = OpBooks.title(appKey)

    /** The Unabated source ids (by [sources]) that are books Vigilant prices with: [wanted] keys only. */
    fun sourceMap(sources: List<UnSource>, wanted: Set<String>): Map<Long, String> =
        sources.filter { it.active && it.statusId != 3 }.mapNotNull { s -> appKey(s.name)?.takeIf { it in wanted }?.let { s.id to it } }.toMap()

    /** Unabated's league route name for a Vigilant league, from `GET /league` (lower-case route names such as `nfl`); null when the key's list does not carry it. */
    fun routeFor(league: League, leagues: List<UnLeague>): String? =
        leagues.firstOrNull { OpBooks.norm(it.name) == OpBooks.norm(league.novigName) }?.name
            ?: leagues.firstOrNull { OpBooks.norm(it.fullName) == OpBooks.norm(league.novigName) }?.name
}

/** Unabated's price (American) as decimal odds. */
internal fun americanToDecimal(price: Int): Double = if (price > 0) 1.0 + price / 100.0 else 1.0 + 100.0 / -price
