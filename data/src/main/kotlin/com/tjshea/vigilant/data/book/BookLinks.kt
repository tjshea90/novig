package com.tjshea.vigilant.data.book

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A sportsbook's own ids for one bet, as the odds feed relayed them (PropLine's `includeBookIds` and
 * `includeLinks`): what the bet-slip link is built from. Any of them can be missing.
 */
data class BookRef(
    /** The book's id for the game (BetMGM's fixture id). */
    val eventId: String? = null,
    /** The book's id for this selection. */
    val outcomeId: String? = null,
    /** The book's page for the game, as the feed sent it. */
    val eventLink: String? = null,
)

/**
 * Where a tap on a BetMGM bet goes (Tj: "open the exact bet slip"). BetMGM's own deep-link format
 * (its Sports API docs, "Elements of a Deep Link", read 2026-09-27): a bet slip is
 * `https://sports.<state>.betmgm.com/en/sports?options=<fixtureId>-<marketId>-<optionId>`, a game is
 * `…/en/sports/events/<fixtureId>`. BetMGM's sites are per state, so the state Tj bets in is a
 * setting. In order:
 *  1. the bet slip, when the feed's ids spell out fixture, market and option;
 *  2. the game's page (the feed's link, else built from the fixture id);
 *  3. BetMGM's sportsbook home.
 * **Not verified live** (2026-09-27: PropLine's public demo key was at its daily cap): PropLine's
 * docs say BetMGM ships event links and its own ids, not their exact shape, so each id is read
 * defensively and anything unrecognised falls to the next step.
 */
object BetMgmLinks {

    /** BetMGM's US states (two-letter codes), for the Settings picker. */
    val STATES = listOf(
        "az", "co", "dc", "il", "in", "ia", "ks", "ky", "la", "ma", "md", "mi", "nc", "nj", "ny", "oh", "pa", "tn", "va", "wv", "wy",
    )

    /** BetMGM's home when nothing better is known (it asks for the state itself). */
    const val HOME = "https://sports.betmgm.com/en/sports"

    data class Link(val url: String, val exact: Boolean)

    /** Where [ref] opens for a player in [state] (two letters, blank = not picked yet). */
    fun link(ref: BookRef?, state: String): Link {
        val st = state.trim().lowercase().takeIf { it.matches(Regex("[a-z]{2}")) }
        val fixture = ref?.eventId?.let(::fixtureId)
        val host = st?.let { "https://sports.$it.betmgm.com" }
        if (host != null && fixture != null) {
            options(fixture, ref.outcomeId)?.let { return Link("$host/en/sports?options=$it&type=Single", exact = true) }
        }
        ref?.eventLink?.let { page -> withState(page, st)?.let { return Link(it, exact = false) } }
        if (host != null && fixture != null) return Link("$host/en/sports/events/$fixture", exact = false)
        return Link(host?.let { "$it/en/sports" } ?: HOME, exact = false)
    }

    /** BetMGM's fixture id out of the feed's game id ("2:17345678" or "17345678"). */
    internal fun fixtureId(raw: String): String? =
        raw.trim().substringAfterLast(':').takeIf { it.matches(Regex("\\d{4,}")) }

    /**
     * The `options` triple for a selection: the feed's id when it already is one ("fixture-market-option"
     * for this fixture), or market and option ("market-option", "market:option", "market_option").
     * Null when the id is only the option (no market id to pair it with) or unrecognised.
     */
    internal fun options(fixture: String, outcomeId: String?): String? {
        val id = outcomeId?.trim() ?: return null
        val parts = id.split('-', ':', '_', '|').filter { it.isNotEmpty() }
        if (parts.any { !it.all(Char::isDigit) }) return null
        return when (parts.size) {
            3 -> parts.takeIf { it[0] == fixture }?.joinToString("-")
            2 -> "$fixture-${parts[0]}-${parts[1]}"
            else -> null
        }
    }

    /**
     * A game page the feed sent, made usable: a `{state}` placeholder filled in (none picked yet: not
     * usable), only BetMGM's own https pages kept.
     */
    internal fun withState(page: String, state: String?): String? {
        val filled = when {
            page.contains("{state}", ignoreCase = true) -> state?.let { page.replace(Regex("\\{state}", RegexOption.IGNORE_CASE), it) } ?: return null
            else -> page
        }
        val url = filled.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || !(url.host == "betmgm.com" || url.host.endsWith(".betmgm.com"))) return null
        return url.toString()
    }
}
