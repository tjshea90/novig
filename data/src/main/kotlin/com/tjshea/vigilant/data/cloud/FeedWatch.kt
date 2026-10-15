package com.tjshea.vigilant.data.cloud

/**
 * Names a fair-odds feed that has answered nothing for [LIMIT] finished scans in a row (TCL21f): `0+0` leagues, no error is not needed, but a feed that was only
 * standing by, skipped for want of games, or held back by its credit pacing is quiet on purpose and is not silent. The streak is carried in memory across scans.
 */
class FeedWatch {
    private val streak = HashMap<String, Int>()

    /** Feeds the last scan listed that were silent for [LIMIT]+ scans, as "id (n scans in a row)", oldest streak first. */
    fun observe(lines: List<FeedLine>): List<String> {
        val seen = lines.map { it.id }.toSet()
        streak.keys.retainAll(seen)
        for (l in lines) {
            val silent = l.fetched + l.reused == 0 && l.standingBy == 0 && l.skipped == 0 && l.heldBack == null
            if (silent) streak[l.id] = (streak[l.id] ?: 0) + 1 else streak.remove(l.id)
        }
        return streak.filterValues { it >= LIMIT }.entries.sortedByDescending { it.value }.map { "${it.key} (${it.value} scans in a row)" }
    }

    companion object { const val LIMIT = 3 }
}
