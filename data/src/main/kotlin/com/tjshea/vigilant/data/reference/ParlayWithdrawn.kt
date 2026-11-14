package com.tjshea.vigilant.data.reference

/**
 * Books ParlayAPI no longer serves under the key Vigilant used to ask for them by (PA1, Tj 2026-11-14, from ParlayAPI's e-mail): `pinnacle`, withdrawn on 2026-11-09 because "its public prices now arrive up
 * to 15 minutes late". Checked against the live API on 2026-11-14 (GitHub probe, parlay.yml `closeprobe`): a call that names ONLY `pinnacle` is refused, HTTP 400 `UNKNOWN_BOOKMAKER` (`/odds`, `/closing-lines`);
 * one that names it beside a book it still serves is answered without it (`/odds` and `/props` with `pinnacle,draftkings` gave DraftKings alone, so the scans never noticed); the closes file holds no Pinnacle row
 * for any day since at least 2026-11-02; `/v1/bookmakers` lists `pinnacle_delayed` instead (1 to 16 minutes old, pregame game lines only, served by name and on its own: not asked, PARLAY_API.md §11).
 *
 * Every ParlayAPI request that names books goes through [without] (`TheOddsApiClient`'s `pickBooks` and `bulkProps`; the closes ask [com.tjshea.vigilant.data.tracker.ParlayCloses.BOOKS], which has none of
 * these), so a withdrawn key that slips back into a list (a settings file, a picker, a new feature) is dropped before the call instead of refusing it whole. `ParlayNoPinnacleTest` pins every request.
 * Not ParlayAPI's `/live/period_markets` (no book is named; it still carried Pinnacle's 1st-half lines of NFL and NCAAF on 2026-11-14) and not The Odds API, where `pinnacle` is a book it still serves.
 */
object ParlayWithdrawn {
    const val PINNACLE = "pinnacle"

    /** Lower-case keys ParlayAPI answers "unknown" for. */
    val BOOKS: Set<String> = setOf(PINNACLE)

    fun isWithdrawn(book: String): Boolean = book.trim().lowercase() in BOOKS

    /** [books] without the withdrawn ones, order kept. */
    fun without(books: List<String>): List<String> = books.filterNot { isWithdrawn(it) }
}
