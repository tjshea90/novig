package com.tjshea.vigilant.engine

/**
 * The wide-quote guard (Tj, 2026-10-09: "discount any quote from any exchange or book if the quote is too wide and should not be trusted as a source for fair odds"; on by default).
 * A book's two sides add up to more than 100% by its margin (hold); an exchange quoting both asks adds up by its spread. A quote whose hold is above a limit is thin or stale or
 * just careless (ProphetX at -140 / -113 on a total, 11.4%, while DraftKings was at 4.5%): devigging it gives a number that is mostly its spread, not a view of the price. Such a quote
 * is left out of every fair line. Limits: [MAX_HOLD_BOOK] (12%: a -125 / -125 prop is 11.1%, a -130 / -130 one 12.7%) and the stricter [MAX_HOLD_EXCHANGE] (10%: an exchange has no margin to hide behind; real ProphetX prop pages sit at about 8%, so 8% would have thrown out ordinary quotes).
 */
object WideQuotes {
    /** Master switch for the paths that have no settings in hand (CrazyNinjaOdds' book table, bid desk, veto); follows `ScanSettings.ignoreWideQuotes`. */
    @Volatile var enabled: Boolean = true

    const val MAX_HOLD_BOOK = 0.12
    const val MAX_HOLD_EXCHANGE = 0.10

    /** Bookmaker keys (The Odds API / ParlayAPI / PropLine names, lowercase) that are exchanges. */
    val EXCHANGE_KEYS: Set<String> = setOf("prophetx", "prophetexchange", "kalshi", "polymarket", "novig", "sporttrade", "betfair_ex_eu", "matchbook", "smarkets")

    fun isExchangeKey(bookKey: String): Boolean = bookKey.lowercase() in EXCHANGE_KEYS

    fun limit(exchange: Boolean): Double = if (exchange) MAX_HOLD_EXCHANGE else MAX_HOLD_BOOK

    /** Whether a two-sided quote's [hold] (sum of implied probabilities minus 1) is past the limit for its kind of book. */
    fun tooWide(hold: Double, exchange: Boolean): Boolean = hold > limit(exchange) + 1e-9

    /** Hold of a quote given as American odds of its two sides. */
    fun holdOfAmerican(a: Int, b: Int): Double = 1.0 / Odds.americanToDecimal(a) + 1.0 / Odds.americanToDecimal(b) - 1.0
}
