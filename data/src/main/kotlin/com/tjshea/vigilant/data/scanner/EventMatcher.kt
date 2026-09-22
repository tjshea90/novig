package com.tjshea.vigilant.data.scanner

/**
 * Matches a Novig event to a reference-provider event by normalized team names. Deliberately
 * simple for this first pass — real event reconciliation (shared IDs, fuzzy name matching across
 * providers that spell teams differently) is a known simplification, not a solved problem; see
 * the module-level note in [EvScanner].
 *
 * Order-independent on purpose (RESEARCH.md §4.4): which team a provider calls "home" vs. "away"
 * is a convention, and two providers aren't guaranteed to agree on it. This matters concretely for
 * [com.tjshea.vigilant.data.novig.NovigGraphQlClient], whose team names come from a moneyline
 * market's two outcomes in whatever order Novig's API returns them — not a documented home/away
 * field — so requiring exact positional agreement would silently drop real matches.
 */
object EventMatcher {

    fun normalize(name: String): String =
        name.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ")

    fun matches(novigHome: String, novigAway: String, refHome: String, refAway: String): Boolean =
        setOf(normalize(novigHome), normalize(novigAway)) == setOf(normalize(refHome), normalize(refAway))
}
