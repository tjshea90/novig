package com.tjshea.vigilant.data.reference

/**
 * The "what's this actually worth" leg (RESEARCH.md §4.3) — sharp/consensus book odds to devig
 * and compare Novig's price against. Doesn't need to be sub-second (line consensus moves far
 * slower than an individual exchange order book), so a polling interface is fine here — see
 * RESEARCH.md §7 on why this leg should poll rather than stream.
 */
interface ReferenceOddsRepository {
    /** All events currently offered for one sport, each carrying whichever books responded. */
    suspend fun getOddsForSport(sportKey: String, marketKeys: List<String> = listOf("h2h")): List<ReferenceEvent>
}
