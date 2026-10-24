package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.FarLane
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The engine's laned scan (Tj, 2026-10-21, CH50b: "build it now", after the odds were 13 minutes old because a full scan reads ~10,000 Novig books at 13 a second). A full scan reads every game out to the days-ahead
 * window; after one finishes, the engine runs QUICK scans of the next [FarLane.NEAR_HOURS] hours (about a fifth of the books, so the fair odds and Novig prices of the games that start soon are minutes
 * old, not 13) until [FarLane.FAR_EVERY_MS] has passed since the last full one, then a full scan again. The list the quick scan publishes carries the last full scan's rows for the games past its reach, each with its own stamps (honest ages). Pure.
 */
object CloudLanes {
    /** Whether the scan about to start is the quick one: a full scan finished less than [FarLane.FAR_EVERY_MS] ago under the same fetch settings, and the window reaches past the quick scan's hours. */
    fun nearOnly(s: ScanSettings, now: Long, lastFullAtMs: Long, fullKey: String?, key: String): Boolean =
        lastFullAtMs > 0L && fullKey == key && now - lastFullAtMs < FarLane.FAR_EVERY_MS && s.daysAhead.coerceAtLeast(1) * 24 > FarLane.NEAR_HOURS

    /** The last start a quick scan begun at [now] judges; a bid on a game past it is left alone, not taken down ([com.tjshea.vigilant.data.novig.trading.maker.MakerPlan]'s reach). */
    fun reachMs(now: Long): Long = now + (FarLane.NEAR_HOURS + 6) * 3_600_000L

    /** [near]'s rows plus the last full scan's rows for games starting after the quick scan's hours (a game the quick scan priced is never taken from the old list twice). */
    fun merge(near: List<Opportunity>, lastFull: List<Opportunity>, now: Long): List<Opportunity> {
        val cut = now + FarLane.NEAR_HOURS * 3_600_000L
        val have = near.mapTo(HashSet()) { it.outcome.outcomeId }
        return near + lastFull.filter { it.event.startsTs > cut && it.outcome.outcomeId !in have }
    }

    /** The longest a carried row's Novig price may have been read before the list shows it (the longest scan is about two minutes, so a row read last scan is at most about three). */
    const val CARRY_MAX_MS = 4 * 60_000L

    /**
     * The list a scan publishes while it is still reading: [partial] shows only the Novig prices read since the scan began, so at the start of every scan the list was nearly empty and filled again over a minute
     * (Tj, 2026-10-24: "all 13 disappear, then about 15 seconds later most or all show back up"; the phone's FEEDHIDE log: the feed at 0 right after each new list, 8 to 16 bets). A row the last list showed that this scan has not
     * read yet is carried on, with its own stamps, while its Novig price is under [CARRY_MAX_MS] old and the odds behind its fair price are not too old; a row the scan HAS read and found no longer listed is not carried.
     * [readThisScan] is the keys (outcome ids) of every line the scan has priced since it began, +EV or not. Pure.
     */
    fun carryUnread(shown: List<Opportunity>, previous: List<Opportunity>, readThisScan: Set<String>, now: Long): List<Opportunity> {
        val have = shown.mapTo(HashSet()) { it.outcome.outcomeId }
        val carried = previous.filter { o ->
            o.outcome.outcomeId !in have && o.outcome.outcomeId !in readThisScan && !o.fairIsOld(now) &&
                o.bookFetchedAtMs?.let { now - it <= CARRY_MAX_MS } == true && o.event.startsTs > now
        }
        return if (carried.isEmpty()) shown else shown + carried
    }
}
