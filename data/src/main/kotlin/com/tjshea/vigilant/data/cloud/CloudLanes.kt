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
        val have = near.mapTo(HashSet()) { it.outcome.id }
        return near + lastFull.filter { it.event.startsTs > cut && it.outcome.id !in have }
    }
}
