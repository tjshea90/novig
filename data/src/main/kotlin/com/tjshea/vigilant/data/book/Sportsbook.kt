package com.tjshea.vigilant.data.book

/**
 * The book an app build prices bets on (Tj, 2026-09-27: "make it also do the same exact functions to
 * find positive EV on betmgm … if smart, make this a totally separate app"). Vigilant prices Novig;
 * Vigilant MGM, built from the same code, prices BetMGM. One build = one book, fixed at build time,
 * so the Novig app never runs a BetMGM code path and nothing scans both at once.
 */
enum class Sportsbook(
    /** Stable id, as the build names it (`BuildConfig.BOOK`). */
    val id: String,
    /** How the app names it on screen: "Open in BetMGM". */
    val displayName: String,
    /** The book's key on PropLine and The Odds API ("betmgm"). */
    val feedKey: String,
    /** CrazyNinjaOdds' `site_id` for the book (its Positive EV page's book filter). */
    val cnoSiteId: String,
    /** CrazyNinjaOdds' column code for the book on a game page ("MGM"). */
    val cnoCode: String,
    /**
     * A peer-to-peer exchange (Novig): prices come from its own order books, bids can rest, a taker
     * fee can apply. A sportsbook's price is simply its posted odds.
     */
    val exchange: Boolean,
) {
    NOVIG("novig", "Novig", "novig", "17", "NV", exchange = true),
    BETMGM("betmgm", "BetMGM", "betmgm", "4", "MGM", exchange = false);

    companion object {
        /** The book for a build's id; Novig for anything unknown (the original app). */
        fun of(id: String?): Sportsbook = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NOVIG
    }
}
