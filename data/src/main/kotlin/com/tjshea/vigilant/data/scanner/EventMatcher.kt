package com.tjshea.vigilant.data.scanner

/**
 * Matches a Novig event to a reference-provider event by normalized team names. Deliberately
 * simple for this first pass — real event reconciliation (shared IDs, fuzzy name matching across
 * providers that spell teams differently) is a known simplification, not a solved problem; see
 * the module-level note in [EvScanner].
 */
object EventMatcher {

    fun normalize(name: String): String =
        name.lowercase().trim().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ")

    fun matches(novigHome: String, novigAway: String, refHome: String, refAway: String): Boolean =
        normalize(novigHome) == normalize(refHome) && normalize(novigAway) == normalize(refAway)
}
